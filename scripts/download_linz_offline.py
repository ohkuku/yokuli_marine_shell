#!/usr/bin/env python3
"""Download selected LINZ WFS layers into one resumable GeoPackage per scale.

Uses Python's standard library only. Credentials stay in the supplied key file;
the output is reference data, not an official ENC or navigational safety claim.
Run again with the same catalogue and output directory to resume interrupted work.
"""

import argparse
import datetime as dt
import decimal
import hashlib
import http.client
import json
import math
import os
from pathlib import Path
import re
import shutil
import sqlite3
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET


PAGE_SIZE = 1000
TIMEOUT = 120
RETRIES = 4
MIN_FREE = 350 * 1024 * 1024
HOST = "data.linz.govt.nz"
FORMAT_VERSION = 1
LIMITATIONS = [
    "Reference-only LINZ LDS hydrographic data; not for navigation or proof of a safe route.",
    "LINZ states LDS chart vector and GeoTIFF updates paused from May 2024; download time is not source currency.",
    "WFS counts and feature identities are checked; the service does not pin all paged requests to one source revision.",
    "Per-layer licence and source metadata were not independently authenticated; preserve and review official layer metadata before redistribution.",
    "Full source layer coverage is retained, including offshore islands and any Pacific/Antarctic coverage in the selected layers; not clipped to the NZ EEZ.",
    "Horizontal geometry is stored as 2D EPSG:4326 without clipping, simplification or longitude wrapping; any source Z/M ordinates are not retained.",
    "GeoPackage conversion does not recreate original S-57 topology, edition or update chains.",
]
SOURCES = {
    "hydrographicData": "https://www.linz.govt.nz/products-services/data/types-linz-data/hydrographic-data",
    "attribution": "https://www.linz.govt.nz/products-services/data/licensing-and-using-data/attributing-linz-data",
    "paging": "https://www.linz.govt.nz/guidance/data-service/linz-data-service-guide/web-services/timeouts",
}
WGS84_WKT = ('GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",'
             '6378137,298.257223563]],PRIMEM["Greenwich",0],UNIT["degree",'
             '0.0174532925199433],AUTHORITY["EPSG","4326"]]')


class DownloadError(Exception):
    """A safe, locally generated error message, never a remote response body."""


class Paused(DownloadError):
    pass


def utc_now():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def json_text(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


def qident(name):
    if not isinstance(name, str) or not name or "\x00" in name:
        raise DownloadError("Invalid source attribute name")
    return '"' + name.replace('"', '""') + '"'


def check_space(path, extra=0):
    if shutil.disk_usage(path).free < MIN_FREE + extra:
        raise Paused("Disk reserve reached; completed files and resumable .part data retained")


def canonical_layer(raw):
    try:
        layer = {name: raw[name] for name in ("id", "title", "band", "rank", "acronym", "count", "bounds")}
        layer["id"] = int(str(layer["id"]).removeprefix("layer-"))
        layer["rank"] = int(layer["rank"])
        layer["count"] = int(layer["count"])
    except (KeyError, TypeError, ValueError):
        raise DownloadError("Catalogue layer requires id/title/band/rank/acronym/count/bounds") from None
    if layer["id"] <= 0 or layer["rank"] not in range(1, 6) or layer["count"] < 0:
        raise DownloadError("Catalogue layer ID, count or rank is invalid")
    if not all(isinstance(layer[name], str) and layer[name] for name in ("title", "band", "acronym")):
        raise DownloadError("Catalogue layer labels must be nonempty strings")
    return layer


def load_catalogue(path):
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        raw = data.get("layers") if isinstance(data, dict) else data
        if not isinstance(raw, list) or not raw:
            raise DownloadError("Catalogue must be a layer array or an object containing layers")
        layers = [canonical_layer(item) for item in raw]
        if len({item["id"] for item in layers}) != len(layers):
            raise DownloadError("Duplicate catalogue layer ID")
        layers.sort(key=lambda item: (item["rank"], item["id"]))
        for rank in {item["rank"] for item in layers}:
            if len({item["band"] for item in layers if item["rank"] == rank}) != 1:
                raise DownloadError("A rank cannot combine different scale bands")
        return layers
    except (OSError, json.JSONDecodeError, TypeError, ValueError):
        raise DownloadError("Cannot read a valid catalogue") from None


def fingerprint(layers):
    return hashlib.sha256(json_text({"format": FORMAT_VERSION, "layers": layers}).encode()).hexdigest()


class SameHostRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        target = urllib.parse.urlsplit(newurl)
        if target.scheme != "https" or target.hostname != HOST or target.port not in (None, 443):
            raise DownloadError("Refused redirect outside the official HTTPS data host")
        if target.username or target.password:
            raise DownloadError("Refused redirect with user information")
        return super().redirect_request(request, fp, code, message, headers, newurl)


class WfsClient:
    def __init__(self, key):
        self.key = key
        self.opener = urllib.request.build_opener(SameHostRedirect())

    def page(self, layer, offset):
        params = {
            "service": "WFS", "version": "2.0.0", "request": "GetFeature",
            "typeNames": "layer-" + str(layer["id"]), "outputFormat": "json",
            "SRSNAME": "EPSG:4326", "STARTINDEX": offset, "COUNT": PAGE_SIZE,
            "sortBy": "fidn",
        }
        body = self.request(params, "application/json")
        try:
            page = json.loads(body, parse_float=decimal.Decimal)
        except (ValueError, UnicodeError):
            raise DownloadError("WFS response is not valid JSON; remote body omitted") from None
        self.validate_page(page, layer, offset)
        return page["features"], len(body)

    def hits(self, layer):
        body = self.request({"service": "WFS", "version": "2.0.0", "request": "GetFeature",
                             "typeNames": "layer-" + str(layer["id"]), "resultType": "hits"}, "application/xml")
        try:
            root = ET.fromstring(body)
            if root.tag != "{http://www.opengis.net/wfs/2.0}FeatureCollection":
                raise DownloadError("WFS hits response is not a FeatureCollection")
            number = root.get("numberMatched", "")
            if not number.isdigit():
                raise DownloadError("WFS hits response lacks an exact numberMatched")
            count = int(number)
        except (ET.ParseError, ValueError):
            raise DownloadError("WFS hits response is invalid; remote XML omitted") from None
        if count != layer["count"]:
            raise DownloadError("WFS hits differs from catalogue for layer " + str(layer["id"]))
        return count

    def request(self, params, accept):
        url = "https://" + HOST + "/services;key=" + self.key + "/wfs?" + urllib.parse.urlencode(params)
        for attempt in range(RETRIES + 1):
            try:
                request = urllib.request.Request(url, headers={"Accept": accept, "User-Agent": "Yokuli-LINZ-Offline/1"})
                with self.opener.open(request, timeout=TIMEOUT) as response:
                    if response.status != 200:
                        raise DownloadError("Unexpected WFS response status")
                    body = response.read()
                return body
            except urllib.error.HTTPError as exc:
                code = exc.code
                delay = exc.headers.get("Retry-After", "") if exc.headers else ""
                exc.close()
                if code not in (408, 429, 500, 502, 503, 504) or attempt == RETRIES:
                    raise DownloadError("WFS HTTP status " + str(code)) from None
                time.sleep(min(60, int(delay)) if delay.isdigit() else min(30, 2 ** (attempt + 1)))
            except (urllib.error.URLError, TimeoutError, ConnectionError, OSError, http.client.HTTPException):
                if attempt == RETRIES:
                    raise DownloadError("WFS network request failed after bounded retries") from None
                time.sleep(min(30, 2 ** (attempt + 1)))
        raise DownloadError("WFS retries exhausted")

    @staticmethod
    def validate_page(page, layer, offset):
        if not isinstance(page, dict) or page.get("type") != "FeatureCollection":
            raise DownloadError("WFS did not return a FeatureCollection")
        raw_total = page.get("totalFeatures")
        if raw_total != "unknown":
            try:
                total = int(raw_total)
            except (TypeError, ValueError):
                raise DownloadError("WFS totalFeatures is neither an exact count nor unknown") from None
            if total != layer["count"]:
                raise DownloadError("WFS totalFeatures differs from catalogue for layer " + str(layer["id"]))
        features = page.get("features")
        expected = min(PAGE_SIZE, layer["count"] - offset)
        if not isinstance(features, list) or len(features) != expected:
            raise DownloadError("WFS page length is incomplete or inconsistent for layer " + str(layer["id"]))
        if "numberReturned" in page and page["numberReturned"] != len(features):
            raise DownloadError("WFS numberReturned disagrees with page content")
        ids = []
        for item in features:
            if not isinstance(item, dict) or item.get("type") != "Feature" or not isinstance(item.get("id"), (str, int)):
                raise DownloadError("WFS feature is missing its stable source ID")
            ids.append(str(item["id"]))
        if any(not item for item in ids) or len(set(ids)) != len(ids):
            raise DownloadError("WFS page has empty or duplicate source IDs")


def coordinate(value):
    if not isinstance(value, (list, tuple)) or len(value) < 2:
        raise DownloadError("Invalid source coordinate")
    try:
        x, y = float(value[0]), float(value[1])
    except (TypeError, ValueError, OverflowError):
        raise DownloadError("Nonnumeric source coordinate") from None
    if not math.isfinite(x) or not math.isfinite(y) or not -180 <= x <= 360 or not -90 <= y <= 90:
        raise DownloadError("Source coordinate outside supported LINZ longitude/latitude range")
    return x, y


def encode_geometry(geometry):
    """Return standard little-endian GP binary with a 2D ISO WKB payload."""
    if geometry is None:
        return None, None, 0
    bounds = [math.inf, math.inf, -math.inf, -math.inf]
    vertices = 0

    def point(value):
        nonlocal vertices
        x, y = coordinate(value)
        vertices += 1
        bounds[0] = min(bounds[0], x)
        bounds[1] = min(bounds[1], y)
        bounds[2] = max(bounds[2], x)
        bounds[3] = max(bounds[3], y)
        return struct.pack("<dd", x, y)

    def geometry_wkb(kind, coords):
        types = {"Point": 1, "LineString": 2, "Polygon": 3, "MultiPoint": 4, "MultiLineString": 5, "MultiPolygon": 6}
        if kind not in types or not isinstance(coords, list):
            raise DownloadError("Unsupported or malformed source geometry")
        header = struct.pack("<BI", 1, types[kind])
        if kind == "Point":
            return header + (point(coords) if coords else struct.pack("<dd", math.nan, math.nan))
        if kind == "LineString":
            if len(coords) == 1:
                raise DownloadError("Source line has only one point")
            return header + struct.pack("<I", len(coords)) + b"".join(point(item) for item in coords)
        if kind == "Polygon":
            rings = []
            for ring in coords:
                if not isinstance(ring, list) or len(ring) < 4 or coordinate(ring[0]) != coordinate(ring[-1]):
                    raise DownloadError("Source polygon has an invalid or unclosed ring")
                rings.append(struct.pack("<I", len(ring)) + b"".join(point(item) for item in ring))
            return header + struct.pack("<I", len(rings)) + b"".join(rings)
        child = {"MultiPoint": "Point", "MultiLineString": "LineString", "MultiPolygon": "Polygon"}[kind]
        return header + struct.pack("<I", len(coords)) + b"".join(geometry_wkb(child, item) for item in coords)

    if not isinstance(geometry, dict):
        raise DownloadError("Source geometry is malformed")
    payload = geometry_wkb(geometry.get("type"), geometry.get("coordinates"))
    empty = bounds[0] == math.inf
    # Flags: little endian, no envelope, standard geometry, optional empty bit.
    return b"GP" + bytes((0, 1 | (16 if empty else 0))) + struct.pack("<i", 4326) + payload, (None if empty else bounds), vertices


def attribute_text(value):
    if value is None or isinstance(value, str):
        return value
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, decimal.Decimal)):
        return str(value)
    # LDS attributes are scalar; unexpected arrays/objects must not be guessed.
    raise DownloadError("Non-scalar source attribute cannot be preserved as TEXT")


def open_package(path, layers):
    conn = sqlite3.connect(path, timeout=30)
    conn.execute("PRAGMA journal_mode=DELETE")
    conn.execute("PRAGMA synchronous=FULL")
    conn.execute("PRAGMA foreign_keys=ON")
    if not conn.execute("SELECT 1 FROM sqlite_master WHERE name='linz_download_state'").fetchone():
        conn.executescript("""
            BEGIN IMMEDIATE;
            PRAGMA application_id=1196444487;
            PRAGMA user_version=10300;
            CREATE TABLE gpkg_spatial_ref_sys (
              srs_name TEXT NOT NULL, srs_id INTEGER NOT NULL PRIMARY KEY,
              organization TEXT NOT NULL, organization_coordsys_id INTEGER NOT NULL,
              definition TEXT NOT NULL, description TEXT);
            CREATE TABLE gpkg_contents (
              table_name TEXT NOT NULL PRIMARY KEY, data_type TEXT NOT NULL,
              identifier TEXT UNIQUE, description TEXT DEFAULT '',
              last_change DATETIME NOT NULL, min_x DOUBLE, min_y DOUBLE,
              max_x DOUBLE, max_y DOUBLE, srs_id INTEGER,
              FOREIGN KEY(srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id));
            CREATE TABLE gpkg_geometry_columns (
              table_name TEXT NOT NULL, column_name TEXT NOT NULL,
              geometry_type_name TEXT NOT NULL, srs_id INTEGER NOT NULL,
              z TINYINT NOT NULL, m TINYINT NOT NULL,
              PRIMARY KEY(table_name,column_name),
              FOREIGN KEY(table_name) REFERENCES gpkg_contents(table_name),
              FOREIGN KEY(srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id));
            CREATE TABLE linz_download_state (name TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE linz_download_progress (
              layer_id INTEGER PRIMARY KEY, next_index INTEGER NOT NULL DEFAULT 0,
              expected_count INTEGER NOT NULL, received_bytes INTEGER NOT NULL DEFAULT 0,
              vertex_count INTEGER NOT NULL DEFAULT 0,
              max_geometry_vertices INTEGER NOT NULL DEFAULT 0,
              max_geometry_bytes INTEGER NOT NULL DEFAULT 0,
              started_utc TEXT NOT NULL, updated_utc TEXT NOT NULL,
              completed INTEGER NOT NULL DEFAULT 0);
        """)
        with conn:
            conn.executemany("INSERT INTO gpkg_spatial_ref_sys VALUES (?,?,?,?,?,?)", [
                ("Undefined Cartesian", -1, "NONE", -1, "undefined", "Undefined Cartesian coordinate reference system"),
                ("Undefined Geographic", 0, "NONE", 0, "undefined", "Undefined geographic coordinate reference system"),
                ("WGS 84", 4326, "EPSG", 4326, WGS84_WKT, "Longitude/latitude; source longitudes retained without wrapping"),
            ])
            conn.execute("INSERT INTO linz_download_state VALUES ('catalogue_hash',?)", (fingerprint(layers),))
            conn.execute("INSERT INTO linz_download_state VALUES ('created_utc',?)", (utc_now(),))
    actual = conn.execute("SELECT value FROM linz_download_state WHERE name='catalogue_hash'").fetchone()
    if not actual or actual[0] != fingerprint(layers):
        conn.close()
        raise DownloadError("Existing package belongs to a different catalogue; use a new output directory")
    return conn


def ensure_layer(conn, layer, features):
    table = "layer_" + str(layer["id"])
    existing = conn.execute("SELECT name FROM sqlite_master WHERE type='table' AND name=?", (table,)).fetchone()
    if not existing:
        conn.execute("CREATE TABLE " + qident(table) + " (_fid INTEGER PRIMARY KEY, source_id TEXT NOT NULL UNIQUE, _geom BLOB)")
        source = "https://data.linz.govt.nz/layer/" + str(layer["id"]) + "/"
        conn.execute("INSERT INTO gpkg_contents (table_name,data_type,identifier,description,last_change,srs_id) VALUES (?,?,?,?,?,4326)",
                     (table, "features", layer["title"], "LINZ LDS reference data; source " + source + "; scale " + layer["band"], utc_now()))
        conn.execute("INSERT INTO gpkg_geometry_columns VALUES (?, '_geom', 'GEOMETRY',4326,0,0)", (table,))
        conn.execute("INSERT INTO linz_download_progress (layer_id,expected_count,started_utc,updated_utc) VALUES (?,?,?,?)",
                     (layer["id"], layer["count"], utc_now(), utc_now()))
    columns = {row[1].lower(): row[1] for row in conn.execute("PRAGMA table_info(" + qident(table) + ")")}
    for feature in features:
        properties = feature.get("properties")
        if not isinstance(properties, dict):
            raise DownloadError("WFS feature properties are missing")
        if len({name.lower() for name in properties}) != len(properties):
            raise DownloadError("Case-insensitive source attribute collision")
        for name in properties:
            if name.lower() in ("_fid", "source_id", "_geom"):
                raise DownloadError("Source attribute collides with package identity/geometry field")
            if name.lower() not in columns:
                conn.execute("ALTER TABLE " + qident(table) + " ADD COLUMN " + qident(name) + " TEXT")
                columns[name.lower()] = name
            elif columns[name.lower()] != name:
                raise DownloadError("Source attribute casing changed between pages")
    return table


def save_page(conn, layer, offset, features, size):
    with conn:
        conn.execute("BEGIN IMMEDIATE")
        table = ensure_layer(conn, layer, features)
        total_vertices = max_vertices = max_bytes = 0
        for index, feature in enumerate(features):
            properties = feature["properties"]
            geometry, bounds, vertices = encode_geometry(feature.get("geometry"))
            total_vertices += vertices
            max_vertices = max(max_vertices, vertices)
            max_bytes = max(max_bytes, len(geometry) if geometry else 0)
            names = ["_fid", "source_id", "_geom"] + list(properties)
            values = [offset + index + 1, str(feature["id"]), geometry] + [attribute_text(v) for v in properties.values()]
            try:
                conn.execute("INSERT INTO " + qident(table) + " (" + ",".join(map(qident, names)) + ") VALUES (" + ",".join("?" for _ in names) + ")", values)
            except sqlite3.IntegrityError:
                raise DownloadError("Duplicate source feature ID or inconsistent persisted page in layer " + str(layer["id"])) from None
            if bounds:
                conn.execute("""UPDATE gpkg_contents SET
                    min_x=CASE WHEN min_x IS NULL THEN ? ELSE min(min_x,?) END,
                    min_y=CASE WHEN min_y IS NULL THEN ? ELSE min(min_y,?) END,
                    max_x=CASE WHEN max_x IS NULL THEN ? ELSE max(max_x,?) END,
                    max_y=CASE WHEN max_y IS NULL THEN ? ELSE max(max_y,?) END
                    WHERE table_name=?""", (bounds[0], bounds[0], bounds[1], bounds[1], bounds[2], bounds[2], bounds[3], bounds[3], table))
        next_index = offset + len(features)
        conn.execute("""UPDATE linz_download_progress SET next_index=?,received_bytes=received_bytes+?,
                     vertex_count=vertex_count+?,max_geometry_vertices=max(max_geometry_vertices,?),
                     max_geometry_bytes=max(max_geometry_bytes,?),updated_utc=?,completed=? WHERE layer_id=?""",
                     (next_index, size, total_vertices, max_vertices, max_bytes, utc_now(), 0, layer["id"]))
        conn.execute("UPDATE gpkg_contents SET last_change=? WHERE table_name=?", (utc_now(), table))


def validate_layer(conn, layer):
    progress = conn.execute("SELECT next_index,expected_count,completed FROM linz_download_progress WHERE layer_id=?", (layer["id"],)).fetchone()
    if not progress or progress != (layer["count"], layer["count"], 1):
        raise DownloadError("Package layer is not complete: " + str(layer["id"]))
    table = qident("layer_" + str(layer["id"]))
    actual, unique = conn.execute("SELECT count(*),count(DISTINCT source_id) FROM " + table).fetchone()
    if actual != layer["count"] or unique != actual:
        raise DownloadError("Package source feature count or uniqueness check failed")


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_json(path, data):
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8") as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)


def package_manifest(path, conn, layers):
    entries = []
    for layer in layers:
        validate_layer(conn, layer)
        started, downloaded, received, vertices, max_vertices, max_bytes = conn.execute("SELECT started_utc,updated_utc,received_bytes,vertex_count,max_geometry_vertices,max_geometry_bytes FROM linz_download_progress WHERE layer_id=?", (layer["id"],)).fetchone()
        entries.append(dict(layer, table="layer_" + str(layer["id"]), sourceUrl="https://data.linz.govt.nz/layer/" + str(layer["id"]) + "/",
                            startedUtc=started, downloadUtc=downloaded, receivedBytes=received, storedCount=layer["count"],
                            vertexCount=vertices, maxGeometryVertices=max_vertices, maxGeometryBytes=max_bytes))
    coverage_vertices = sum(item["vertexCount"] for item in entries if item["acronym"] == "M_COVR")
    total_vertices = sum(item["vertexCount"] for item in entries)
    total_features = sum(item["count"] for item in entries)
    max_vertices = max(item["maxGeometryVertices"] for item in entries)
    max_bytes = max(item["maxGeometryBytes"] for item in entries)
    return {"file": path.name, "band": layers[0]["band"], "rank": layers[0]["rank"], "featureCount": sum(item["count"] for item in layers),
            "vertexCount": total_vertices, "coverageVertexCount": coverage_vertices,
            "maxGeometryVertices": max_vertices, "maxGeometryBytes": max_bytes,
            "appSizeLimits": {"coverageVertices": 100000, "geometryVertices": 200000, "geometryBytes": 8000000,
                              "packageVertices": 20000000, "packageFeatures": 2000000,
                              "withinReportedGeometryAndFeatureLimits": coverage_vertices <= 100000 and max_vertices <= 200000 and max_bytes <= 8000000 and total_vertices <= 20000000 and total_features <= 2000000,
                              "note": "These size statistics do not establish import eligibility; topology, fields, text limits, coverage count and source rules are also enforced by the app."},
            "bytes": path.stat().st_size, "sha256": sha256_file(path), "downloadUtc": max(item["downloadUtc"] for item in entries), "layers": entries}


def download_band(client, output, layers):
    final = output / (str(layers[0]["rank"]).zfill(2) + "-linz-hydro.gpkg")
    partial = final.with_name(final.name + ".part")
    if final.exists() and partial.exists():
        raise DownloadError("Both final and partial packages exist; refusing ambiguous resume")
    check_space(output)
    conn = open_package(final if final.exists() else partial, layers)
    try:
        if not final.exists():
            for layer in layers:
                row = conn.execute("SELECT next_index,expected_count,completed FROM linz_download_progress WHERE layer_id=?", (layer["id"],)).fetchone()
                offset = row[0] if row else 0
                if row and (row[1] != layer["count"] or offset > layer["count"]):
                    raise DownloadError("Saved progress disagrees with catalogue")
                if row and row[2]:
                    validate_layer(conn, layer)
                    continue
                if row:
                    count = conn.execute("SELECT count(*) FROM " + qident("layer_" + str(layer["id"]))).fetchone()[0]
                    if count != offset:
                        raise DownloadError("Saved page progress disagrees with stored features")
                client.hits(layer)
                while offset < layer["count"] or (layer["count"] == 0 and not row):
                    check_space(output)
                    features, size = client.page(layer, offset)
                    check_space(output, size * 2)
                    save_page(conn, layer, offset, features, size)
                    offset += len(features)
                    print("layerId=" + str(layer["id"]) + " count=" + str(offset) + "/" + str(layer["count"]) + " bytes=" + str(partial.stat().st_size), flush=True)
                    if layer["count"] == 0:
                        break
                # An exact catalogue count is insufficient when GeoJSON reports
                # totalFeatures=unknown. Confirm no hidden tail and count again.
                tail, _ = client.page(layer, layer["count"])
                if tail:
                    raise DownloadError("WFS returned features beyond the expected final offset")
                client.hits(layer)
                with conn:
                    conn.execute("UPDATE linz_download_progress SET completed=1,updated_utc=? WHERE layer_id=?",
                                 (utc_now(), layer["id"]))
                validate_layer(conn, layer)
            if conn.execute("PRAGMA quick_check").fetchone() != ("ok",):
                raise DownloadError("SQLite package integrity check failed")
            for layer in layers:
                validate_layer(conn, layer)
            conn.close()
            conn = None
            with partial.open("rb") as stream:
                os.fsync(stream.fileno())
            partial.replace(final)
            conn = sqlite3.connect(final)
        return package_manifest(final, conn, layers)
    finally:
        if conn is not None:
            conn.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--key-file", type=Path, required=True, help="File containing only the LDS API key")
    parser.add_argument("--catalogue", type=Path, required=True, help="Selected full-coverage layer JSON catalogue")
    parser.add_argument("--output", type=Path, required=True, help="Output directory for complete per-scale GeoPackages")
    args = parser.parse_args()
    key = ""
    try:
        key = args.key_file.read_text(encoding="utf-8").strip()
        if not re.fullmatch(r"[A-Za-z0-9_-]{16,128}", key):
            raise DownloadError("API key file has an invalid format")
        layers = load_catalogue(args.catalogue)
        args.output.mkdir(parents=True, exist_ok=True)
        manifest_path = args.output / "manifest.json"
        manifest = {"formatVersion": FORMAT_VERSION, "status": "in_progress", "startedUtc": utc_now(),
                    "catalogueSha256": fingerprint(layers), "expectedLayers": len(layers),
                    "expectedFeatures": sum(item["count"] for item in layers), "source": "LINZ Data Service",
                    "referenceOnly": True, "limitations": LIMITATIONS, "officialGuidance": SOURCES,
                    "download": {"pageSize": PAGE_SIZE, "timeoutSeconds": TIMEOUT, "retries": RETRIES, "sortBy": "fidn", "srs": "EPSG:4326", "clipped": False,
                                 "verification": "Catalogue count checked by WFS hits before and after each layer, page counts and unique source IDs, plus empty page at final offset."},
                    "packages": []}
        client = WfsClient(key)
        # An advisory process lock prevents concurrent writers sharing this output.
        import fcntl
        with (args.output / ".download.lock").open("a") as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise DownloadError("Another downloader owns this output directory") from None
            if manifest_path.exists():
                old = json.loads(manifest_path.read_text(encoding="utf-8"))
                if old.get("catalogueSha256") != manifest["catalogueSha256"]:
                    raise DownloadError("Existing output manifest belongs to another catalogue")
                manifest["startedUtc"] = old.get("startedUtc", manifest["startedUtc"])
                manifest["packages"] = old.get("packages", [])
            atomic_json(manifest_path, manifest)
            for rank in sorted({item["rank"] for item in layers}):
                package = download_band(client, args.output, [item for item in layers if item["rank"] == rank])
                manifest["packages"] = sorted([item for item in manifest["packages"] if item["rank"] != rank] + [package], key=lambda item: item["rank"])
                manifest["updatedUtc"] = utc_now()
                atomic_json(manifest_path, manifest)
            manifest["status"] = "complete"
            manifest["completedUtc"] = utc_now()
            atomic_json(manifest_path, manifest)
        return 0
    except KeyboardInterrupt:
        print("Paused by interruption; resumable data retained", file=sys.stderr, flush=True)
        return 130
    except Exception as exc:
        # Never stringify arbitrary urllib/SQLite exceptions: they can contain
        # URLs, remote text, or provider attribute values. Only local safe errors.
        message = str(exc) if isinstance(exc, DownloadError) else "Download stopped (" + type(exc).__name__ + "); resumable data retained"
        if key:
            message = message.replace(key, "[REDACTED]")
        message = re.sub(r"(?i)(key[= :]+)[^\s/&?]+", r"\1[REDACTED]", message)
        print(message, file=sys.stderr, flush=True)
        return 75 if isinstance(exc, Paused) else 1


if __name__ == "__main__":
    sys.exit(main())
