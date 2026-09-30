#!/usr/bin/env python3
"""Produce regional LINZ GeoPackages without altering the downloaded originals.

Requires Shapely 2.1. The supplied polygon is a geographic selection mask, not
new survey evidence. Dateline ambiguity is retained as an explicit unknown area.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import sqlite3
import struct

from shapely import from_wkb, to_wkb, prepare, intersects, covers
from shapely.geometry import shape, Polygon, MultiPolygon, MultiLineString, MultiPoint
from shapely.ops import transform, unary_union


def ident(name):
    return '"' + name.replace('"', '""') + '"'


def read_geometry(blob):
    if blob is None:
        return None
    if blob[:3] != b'GP\0':
        raise ValueError('Unsupported GeoPackage geometry header')
    count = {0: 0, 1: 4, 2: 6, 3: 6, 4: 8}[(blob[3] >> 1) & 7]
    return from_wkb(blob[8 + count * 8:])


def write_geometry(geometry):
    # Normalize only at the exchange boundary; all clipping uses one continuous
    # longitude branch. The Android reader restores this same local branch.
    geometry = transform(lambda x, y, z=None: (((x + 180) % 360) - 180, y), geometry)
    return b'GP\0\x01' + struct.pack('<i', 4326) + to_wkb(geometry, byte_order=1, output_dimension=2)


def same_dimension(geometry, source):
    family = 'Polygon' if 'Polygon' in source.geom_type else 'LineString' if 'LineString' in source.geom_type else 'Point'
    parts = []
    def visit(value):
        if value.is_empty:
            return
        if value.geom_type == family:
            parts.append(value)
        elif hasattr(value, 'geoms'):
            for part in value.geoms:
                visit(part)
    visit(geometry)
    if not parts:
        return None
    return parts[0] if len(parts) == 1 else {'Polygon': MultiPolygon, 'LineString': MultiLineString, 'Point': MultiPoint}[family](parts)


def clip_file(source, destination, region, boundary_hash):
    if destination.exists():
        raise ValueError('Output already exists; choose a new destination directory')
    partial = destination.with_suffix('.gpkg.part')
    if partial.exists():
        raise ValueError('Unfinished output exists; preserve or remove it before retrying')
    original = sqlite3.connect(source.as_uri() + '?mode=ro', uri=True)
    output = sqlite3.connect(partial)
    stats = {'file': destination.name, 'sourceFeatures': 0, 'retainedFeatures': 0,
             'clippedFeatures': 0, 'removedFeatures': 0, 'unknownAreas': 0, 'layers': []}
    unknown = []
    now = dt.datetime.now(dt.timezone.utc).isoformat()
    try:
        # Rebuild instead of copying a large database and deleting most records.
        output.execute('PRAGMA application_id=1196444487')
        output.execute('PRAGMA user_version=10300')
        for table in ('gpkg_spatial_ref_sys', 'gpkg_contents', 'gpkg_geometry_columns'):
            ddl = original.execute('SELECT sql FROM sqlite_master WHERE type="table" AND name=?', (table,)).fetchone()[0]
            output.execute(ddl)
            rows = original.execute('SELECT * FROM ' + ident(table))
            for row in rows:
                output.execute('INSERT INTO ' + ident(table) + ' VALUES (' + ','.join('?' for _ in row) + ')', row)
        tables = original.execute('SELECT table_name,column_name FROM gpkg_geometry_columns').fetchall()
        for table, column in tables:
            ddl = original.execute('SELECT sql FROM sqlite_master WHERE type="table" AND name=?', (table,)).fetchone()[0]
            output.execute(ddl)
            cursor = original.execute('SELECT * FROM ' + ident(table))
            columns = [d[0] for d in cursor.description]
            geom_index = columns.index(column)
            statement = 'INSERT INTO ' + ident(table) + ' VALUES (' + ','.join('?' for _ in columns) + ')'
            kept = changed = removed = total = 0
            extent = None
            for row in cursor:
                total += 1
                raw = read_geometry(row[geom_index])
                if raw is None or raw.is_empty:
                    # A missing location must not become implicitly safe water.
                    output.execute(statement, row); kept += 1
                    continue
                geometry = transform(lambda x, y, z=None: (x % 360, y), raw)
                if not intersects(region.envelope, geometry.envelope):
                    removed += 1; continue
                if not geometry.is_valid:
                    if raw.geom_type != 'MultiPolygon' or not raw.is_valid or raw.bounds[2] - raw.bounds[0] <= 180:
                        raise ValueError('Unexplained source geometry in ' + table + ': ' + str(row[0]))
                    valid = []
                    for part in geometry.geoms:
                        if part.is_valid:
                            valid.append(part)
                        else:
                            area = part.envelope.intersection(region)
                            if not area.is_empty:
                                unknown.append((table, str(row[0]), area))
                    geometry = unary_union(valid)
                if not intersects(region, geometry):
                    removed += 1; continue
                if covers(region, geometry):
                    clipped = geometry
                else:
                    clipped = same_dimension(geometry.intersection(region), geometry)
                    changed += 1
                if clipped is None or clipped.is_empty:
                    removed += 1; continue
                if not clipped.is_valid:
                    raise ValueError('Invalid regional clipping result')
                values = list(row); values[geom_index] = write_geometry(clipped)
                output.execute(statement, values); kept += 1
                box = clipped.bounds
                extent = box if extent is None else (min(extent[0], box[0]), min(extent[1], box[1]), max(extent[2], box[2]), max(extent[3], box[3]))
            # The regional exchange permits a clipped MultiPolygon/Line even if
            # the source happened to use one primitive per row.
            output.execute('UPDATE gpkg_geometry_columns SET geometry_type_name="GEOMETRY" WHERE table_name=?', (table,))
            # Date-line extents in GPKG metadata are ordinary WGS84 bounds.
            bounds = None if extent is None else ((extent[0] if extent[2] < 180 else -180), extent[1], (extent[2] if extent[2] < 180 else 180), extent[3])
            output.execute('UPDATE gpkg_contents SET min_x=?,min_y=?,max_x=?,max_y=?,last_change=?,description=description||? WHERE table_name=?',
                           (*(bounds or (None, None, None, None)), now, '; clipped to LINZ NZ EEZ outer-limit selection mask ' + boundary_hash, table))
            stats['sourceFeatures'] += total; stats['retainedFeatures'] += kept
            stats['clippedFeatures'] += changed; stats['removedFeatures'] += removed
            stats['layers'].append({'table': table, 'source': total, 'retained': kept, 'clipped': changed, 'removed': removed})
            output.commit()
        if unknown:
            table = 'yokuli_uncertain_areas'
            output.execute('CREATE TABLE yokuli_uncertain_areas (fid INTEGER PRIMARY KEY, geom BLOB, kind TEXT, object_class TEXT, YOKULI_GEOMETRY_STATUS TEXT, source_table TEXT, source_fid TEXT, description TEXT)')
            output.execute('INSERT INTO gpkg_geometry_columns VALUES (?,"geom","GEOMETRY",4326,0,0)', (table,))
            output.execute('INSERT INTO gpkg_contents(table_name,data_type,identifier,description,last_change,srs_id) VALUES (?,"features",?, ?, ?,4326)', (table, 'Uncertain source geometry', 'Conservative unknown regions; no depth or coverage evidence', now))
            for index, (source_table, source_fid, geometry) in enumerate(unknown, 1):
                output.execute('INSERT INTO yokuli_uncertain_areas VALUES (?,?,?,?,?,?,?,?)',
                               (index, write_geometry(geometry), 'OTHER', 'GPKG_UNCERTAIN', 'UNCERTAIN', source_table, source_fid, 'Source date-line polygon is ambiguous; this envelope must remain unknown'))
            stats['unknownAreas'] = len(unknown)
        output.commit()
        output.close(); output = None
        partial.replace(destination)
        return stats
    finally:
        original.close()
        if output is not None:
            output.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--boundary', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    boundary_bytes = args.boundary.read_bytes()
    boundary = json.loads(boundary_bytes)
    region = shape(boundary['geometry'])
    if not region.is_valid or region.is_empty or region.geom_type not in ('Polygon', 'MultiPolygon'):
        raise ValueError('A valid polygonal selection boundary is required')
    prepare(region)
    digest = hashlib.sha256(boundary_bytes).hexdigest()
    args.output.mkdir(parents=True, exist_ok=True)
    files = sorted(args.source.resolve().glob('*.gpkg'))
    if not files:
        raise ValueError('No source GeoPackages found')
    manifest = {'region': boundary['properties'], 'boundarySha256': digest, 'packages': []}
    for file in files:
        stats = clip_file(file, args.output / file.name, region, digest)
        manifest['packages'].append(stats)
        (args.output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({k: v for k, v in stats.items() if k != 'layers'}), flush=True)


if __name__ == '__main__':
    main()
