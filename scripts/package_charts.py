#!/usr/bin/env python3
"""Package display charts as .yklcharts, data as .yklgeodata, or combine as .yklpkg.

The archive preserves source bytes. It does not confer data accuracy, currency,
redistribution permission, or navigational approval. See chart-library/.
"""

import argparse
import contextlib
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
import unicodedata
import zipfile


MAX_FILES = 2000
MAX_FILE_BYTES = 32_000_000_000
MAX_TOTAL_BYTES = 64_000_000_000
MAX_PATH_BYTES = 240
MAX_MANIFEST_BYTES = 1_048_576
CHUNK_BYTES = 1024 * 1024
FORMAT = "yokuli.chart-package"
COMPANIONS = {
    "manifest.json", "catalogue.json", "readme", "readme.md", "readme.txt",
    "使用说明.md", "license", "license.md", "license.txt", "licence",
    "licence.md", "licence.txt", "attribution.md", "attribution.txt",
}
EXTENSIONS = {
    ".gpkg": "gpkg", ".mbtiles": "mbtiles",
    ".tif": "gebco", ".tiff": "gebco", ".asc": "gebco", ".ascii": "gebco",
    ".yklcharts": "charts", ".yklgeodata": "geodata",
}


class PackageError(Exception):
    pass


def safe_path(relative):
    """Use a portable, normalized ZIP name; never an extraction instruction."""
    value = relative.as_posix()
    if (not value or value != unicodedata.normalize("NFC", value)
            or any(c in value for c in "\\:")
            or any(unicodedata.category(c).startswith("C") for c in value)
            or any(p in ("", ".", "..") or p.startswith(".") for p in value.split("/"))
            or len(("files/" + value).encode("utf-8")) > MAX_PATH_BYTES):
        raise PackageError("Unsafe or overlong relative file name: " + repr(value))
    return "files/" + value


def file_format(path):
    extension = path.suffix.lower()
    if re.fullmatch(r"\.[0-9]{3}", extension) and path.name.casefold() != "catalog.031":
        return "s57"
    result = EXTENSIONS.get(extension)
    if result is None:
        raise PackageError("Unsupported file; move it outside the source collection: " + path.name)
    return result


def fingerprint(info):
    return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def check_parents(source, path):
    current = path.parent
    while current != source:
        if current == current.parent or current.is_symlink() or not current.is_dir():
            raise PackageError("Source path changed or contains a symbolic link")
        current = current.parent
    if source.is_symlink() or not source.is_dir():
        raise PackageError("Source directory changed or is a symbolic link")


def check_database_sidecars(path):
    if path.suffix.lower() in (".gpkg", ".mbtiles"):
        for suffix in ("-wal", "-shm", "-journal"):
            if os.path.lexists(str(path) + suffix):
                raise PackageError("Database has a transaction sidecar; close/checkpoint it first: " + path.name)


@contextlib.contextmanager
def open_source(source, entry):
    path = source / entry["relative"]
    check_parents(source, path)
    check_database_sidecars(path)
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
    if path.is_symlink():
        raise PackageError("Symbolic link is not a source file")
    descriptor = os.open(path, flags)
    with os.fdopen(descriptor, "rb") as stream:
        before = os.fstat(stream.fileno())
        if not stat.S_ISREG(before.st_mode) or fingerprint(before) != entry["fingerprint"]:
            raise PackageError("Source file changed while packaging: " + entry["relative"])
        yield stream
        after = os.fstat(stream.fileno())
        if fingerprint(after) != entry["fingerprint"]:
            raise PackageError("Source file changed while packaging: " + entry["relative"])
    check_parents(source, path)
    check_database_sidecars(path)
    if fingerprint(path.lstat()) != entry["fingerprint"]:
        raise PackageError("Source file replaced while packaging: " + entry["relative"])


def collect_files(source, kind):
    entries = []
    total = 0
    seen = set()
    # Hidden trees (including .private) are never opened or descended into.
    pending = [source]
    while pending:
        directory = pending.pop()
        with os.scandir(directory) as children:
            for child in children:
                if child.name.startswith("."):
                    continue
                if child.is_symlink():
                    raise PackageError("Symbolic links are not supported: " + child.name)
                path = Path(child.path)
                relative = path.relative_to(source)
                archive_path = safe_path(relative)
                if child.is_dir(follow_symlinks=False):
                    pending.append(path)
                    continue
                info = child.stat(follow_symlinks=False)
                if not stat.S_ISREG(info.st_mode):
                    raise PackageError("Source contains a non-regular file: " + child.name)
                if child.name.casefold() in COMPANIONS:
                    continue
                format_name = file_format(path)
                if ((kind == "atlas" and format_name not in ("charts", "geodata"))
                        or (kind != "atlas" and (format_name in ("charts", "geodata")
                            or (kind == "charts") != (format_name == "mbtiles")))):
                    raise PackageError("Collection mixes data and display charts, or --kind is wrong: " + child.name)
                check_database_sidecars(path)
                key = archive_path.casefold()
                if key in seen:
                    raise PackageError("Case-insensitive duplicate archive path: " + archive_path)
                seen.add(key)
                if not 0 < info.st_size <= MAX_FILE_BYTES:
                    raise PackageError("Payload is empty or exceeds 32,000,000,000 bytes: " + child.name)
                total += info.st_size
                if len(entries) >= MAX_FILES or total > MAX_TOTAL_BYTES:
                    raise PackageError("Collection exceeds 2,000 files or 64,000,000,000 bytes")
                entries.append({"relative": relative.as_posix(), "path": archive_path,
                                "bytes": info.st_size, "format": format_name,
                                "fingerprint": fingerprint(info)})
    if not entries:
        raise PackageError("No supported payload files in the source collection")
    if kind == "atlas":
        if len(entries) > 2 or len({entry["format"] for entry in entries}) != len(entries):
            raise PackageError("An atlas contains one charts package, one geodata package, or both")
        for entry in entries:
            validate_child_package(source, entry)
    entries.sort(key=lambda entry: entry["relative"])
    for priority, entry in enumerate(entries):
        entry["priority"] = priority
    return entries


def check_signature(entry, header):
    format_name = entry["format"]
    valid = False
    if format_name in ("charts", "geodata"):
        valid = header.startswith(b"PK\x03\x04")
    elif format_name in ("gpkg", "mbtiles"):
        valid = len(header) >= 100 and header.startswith(b"SQLite format 3\x00")
    elif format_name == "s57":
        valid = (len(header) >= 24 and header[:5].isdigit()
                 and header[12:17].isdigit() and header[6:7] in (b"L", b"D"))
    elif Path(entry["relative"]).suffix.lower() in (".tif", ".tiff"):
        valid = header[:4] in (b"II*\x00", b"MM\x00*", b"II+\x00", b"MM\x00+")
    else:
        valid = re.match(rb"\s*ncols\s+[0-9]+\s+nrows\s+[0-9]+\s", header, re.IGNORECASE) is not None
    if not valid:
        raise PackageError("File header does not match its data format: " + entry["relative"])


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise PackageError("Duplicate JSON field in child manifest")
        result[key] = value
    return result


def validate_child_package(source, entry):
    """Validate all declared child bytes without extraction or unbounded decompression."""
    expected = "charts" if entry["format"] == "charts" else "data"
    with open_source(source, entry) as stream, zipfile.ZipFile(stream) as archive:
        infos = archive.infolist()
        if not infos or infos[0].filename != "manifest.json" or infos[0].file_size > MAX_MANIFEST_BYTES:
            raise PackageError("Child manifest must be first and at most 1 MiB")
        with archive.open(infos[0]) as manifest_stream:
            encoded = manifest_stream.read(MAX_MANIFEST_BYTES + 1)
        if len(encoded) > MAX_MANIFEST_BYTES:
            raise PackageError("Child manifest exceeds 1 MiB")
        manifest = json.loads(encoded.decode("utf-8"), object_pairs_hook=unique_object,
                              parse_constant=lambda _: (_ for _ in ()).throw(PackageError("Non-finite JSON number")))
        required = {"format", "version", "id", "name", "kind", "createdAt", "provider", "license", "attribution", "files"}
        if not isinstance(manifest, dict) or not required.issubset(manifest) or set(manifest) - required - {"metadata"}:
            raise PackageError("Invalid child manifest fields")
        if (manifest["format"] != FORMAT or type(manifest["version"]) is not int
                or manifest["version"] not in (1, 2) or manifest["kind"] != expected):
            raise PackageError("Child format or kind does not match its extension")
        if not isinstance(manifest["id"], str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", manifest["id"]):
            raise PackageError("Invalid child identity")
        for key, limit in (("name", 512), ("provider", 512), ("license", 512), ("attribution", 8192)):
            value = manifest[key]
            if not isinstance(value, str):
                raise PackageError("Invalid child text field")
            if value or key == "name" or manifest["version"] == 1:
                text_field(value, key, limit)
        if not isinstance(manifest["createdAt"], str) or not manifest["createdAt"].endswith("Z"):
            raise PackageError("Child timestamp must be UTC")
        created_at(manifest["createdAt"])
        files = manifest["files"]
        if not isinstance(files, list) or not 1 <= len(files) <= MAX_FILES or len(infos) != len(files) + 1:
            raise PackageError("Child payload count is invalid")
        def metadata(value):
            if not isinstance(value, dict) or len(value) > 64:
                raise PackageError("Invalid child metadata")
            total = 0
            for key, item in value.items():
                if (not isinstance(key, str) or not isinstance(item, str) or not key.strip()
                        or key != key.strip() or len(key) > 80 or len(item) > 8192
                        or any(unicodedata.category(c).startswith("C") for c in key)
                        or any((c == "\0" or (ord(c) < 32 and c not in "\n\r\t")) for c in item)):
                    raise PackageError("Invalid child metadata field")
                total += len(key.encode("utf-8")) + len(item.encode("utf-8"))
            if total > 131072:
                raise PackageError("Child metadata exceeds 128 KiB")
        if "metadata" in manifest:
            if manifest["version"] == 1:
                raise PackageError("Child v1 cannot contain metadata")
            metadata(manifest["metadata"])
        declared, seen, previous, total = {}, set(), -1, 0
        for member in files:
            fields = {"path", "bytes", "sha256", "format", "priority"}
            if not isinstance(member, dict) or not fields.issubset(member) or set(member) - fields - {"metadata", "rasterProduct"}:
                raise PackageError("Invalid child payload fields")
            path = member["path"]
            if not isinstance(path, str) or not path.startswith("files/") or safe_path(Path(path[6:])) != path:
                raise PackageError("Unsafe child payload path")
            if path.casefold() in seen:
                raise PackageError("Duplicate child payload path")
            seen.add(path.casefold())
            size, priority = member["bytes"], member["priority"]
            if type(size) is not int or not 0 < size <= MAX_FILE_BYTES or type(priority) is not int or not previous < priority <= 2147483647:
                raise PackageError("Invalid child payload size or priority")
            previous = priority; total += size
            if total > MAX_TOTAL_BYTES or not isinstance(member["sha256"], str) or not re.fullmatch(r"[0-9a-fA-F]{64}", member["sha256"]):
                raise PackageError("Invalid child payload size or digest")
            actual_format = file_format(Path(path))
            if actual_format != member["format"] or actual_format in ("charts", "geodata") or (expected == "charts") != (actual_format == "mbtiles"):
                raise PackageError("Invalid child payload format")
            if "metadata" in member:
                if manifest["version"] == 1:
                    raise PackageError("Child v1 cannot contain metadata")
                metadata(member["metadata"])
            product = member.get("rasterProduct")
            if product is not None and (manifest["version"] == 1 or actual_format != "gebco" or not isinstance(product, str) or not re.fullmatch(r"GEBCO_20[0-9]{2}_Grid", product)):
                raise PackageError("Invalid child raster product")
            declared[path] = member
        for path in seen:
            parent = path.rpartition("/")[0]
            while parent:
                if parent in seen:
                    raise PackageError("Child file/directory path collision")
                parent = parent.rpartition("/")[0]
        found = set()
        for info in infos[1:]:
            member = declared.get(info.filename)
            mode = info.external_attr >> 16
            if (member is None or info.filename.casefold() in found or info.is_dir()
                    or info.flag_bits & 1 or (stat.S_IFMT(mode) not in (0, stat.S_IFREG))
                    or info.file_size != member["bytes"]):
                raise PackageError("Invalid or undeclared child ZIP member")
            found.add(info.filename.casefold())
            digest, count = hashlib.sha256(), 0
            with archive.open(info) as payload:
                while True:
                    chunk = payload.read(CHUNK_BYTES)
                    if not chunk:
                        break
                    count += len(chunk)
                    if count > member["bytes"]:
                        raise PackageError("Child payload size mismatch")
                    digest.update(chunk)
            if count != member["bytes"] or digest.hexdigest() != member["sha256"].lower():
                raise PackageError("Child payload hash mismatch")


def hash_source(source, entry, destination=None):
    digest = hashlib.sha256()
    size = 0
    with open_source(source, entry) as stream:
        while True:
            chunk = stream.read(CHUNK_BYTES)
            if not chunk:
                break
            if size == 0:
                check_signature(entry, chunk[:8192])
            size += len(chunk)
            if size > entry["bytes"]:
                raise PackageError("Source file grew while packaging: " + entry["relative"])
            digest.update(chunk)
            if destination is not None:
                destination.write(chunk)
    if size != entry["bytes"]:
        raise PackageError("Source file size changed while packaging: " + entry["relative"])
    return digest.hexdigest()


def text_field(value, name, limit):
    if (not value.strip() or value != value.strip() or len(value.encode("utf-8")) > limit
            or any(unicodedata.category(c).startswith("C") for c in value)):
        raise PackageError(name + " must be nonempty trimmed text within " + str(limit) + " UTF-8 bytes")
    return value


def created_at(value):
    if value is None:
        moment = dt.datetime.now(dt.timezone.utc)
    else:
        try:
            moment = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            raise PackageError("--created-at must be an ISO 8601 timestamp with a timezone") from None
        if moment.tzinfo is None:
            raise PackageError("--created-at must include a timezone")
    return moment.astimezone(dt.timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def zip_info(name):
    # Stable ZIP metadata; createdAt is the meaningful package timestamp.
    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3
    info.external_attr = (stat.S_IFREG | 0o644) << 16
    return info


def build_package(args):
    raw_source = Path(args.source).expanduser().absolute()
    if raw_source.is_symlink() or not raw_source.is_dir():
        raise PackageError("--source must be a real directory, not a symbolic link")
    source = raw_source.resolve(strict=True)
    if any(part.startswith(".") for part in source.parts):
        raise PackageError("Hidden source directories are not accepted")
    output = Path(args.output).expanduser().absolute()
    extension = {"charts": ".yklcharts", "data": ".yklgeodata", "atlas": ".yklpkg"}[args.kind]
    if output.suffix.lower() != extension:
        raise PackageError("--output for " + args.kind + " must have the " + extension + " extension")
    if output.is_symlink():
        raise PackageError("--output cannot be a symbolic link")
    if output.resolve().is_relative_to(source):
        raise PackageError("Place --output outside --source")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", args.id):
        raise PackageError("--id must be 1–80 letters, digits, dots, underscores or hyphens, starting with a letter or digit")
    manifest = {"format": "yokuli.atlas-package" if args.kind == "atlas" else FORMAT, "version": 1, "id": args.id,
                "name": text_field(args.name, "--name", 512), "kind": args.kind,
                "createdAt": created_at(args.created_at),
                "provider": text_field(args.provider, "--provider", 512),
                "license": text_field(args.license, "--license", 512),
                "attribution": text_field(args.attribution, "--attribution", 8192)}
    entries = collect_files(source, args.kind)
    for index, entry in enumerate(entries, 1):
        print("Hashing %d/%d: %s" % (index, len(entries), entry["relative"]), file=sys.stderr)
        entry["sha256"] = hash_source(source, entry)
    manifest["files"] = [{key: entry[key] for key in ("path", "bytes", "sha256", "format", "priority")} for entry in entries]
    encoded = (json.dumps(manifest, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")
    if len(encoded) > MAX_MANIFEST_BYTES:
        raise PackageError("Manifest exceeds 1 MiB")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w+b", prefix="." + output.name + ".", suffix=".part",
                                         dir=output.parent, delete=False) as stream:
            temporary = Path(stream.name)
            with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED, allowZip64=True) as archive:
                archive.writestr(zip_info("manifest.json"), encoded)
                for index, entry in enumerate(entries, 1):
                    print("Packing %d/%d: %s" % (index, len(entries), entry["relative"]), file=sys.stderr)
                    info = zip_info(entry["path"])
                    info.file_size = entry["bytes"]
                    with archive.open(info, "w", force_zip64=entry["bytes"] >= 2_000_000_000) as target:
                        actual = hash_source(source, entry, target)
                    if actual != entry["sha256"]:
                        raise PackageError("Source hash changed while packaging: " + entry["relative"])
            stream.flush()
            os.fchmod(stream.fileno(), 0o644)
            os.fsync(stream.fileno())
        os.replace(temporary, output)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
    digest = hashlib.sha256()
    with output.open("rb") as stream:
        for chunk in iter(lambda: stream.read(CHUNK_BYTES), b""):
            digest.update(chunk)
    return {"path": str(output), "bytes": output.stat().st_size, "sha256": digest.hexdigest(),
            "files": len(entries), "payloadBytes": sum(entry["bytes"] for entry in entries),
            "createdAt": manifest["createdAt"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, help="Directory containing one complete collection")
    parser.add_argument("--output", required=True, help="Atomic .yklcharts, .yklgeodata or .yklpkg output outside the source directory")
    parser.add_argument("--id", required=True, help="Stable collection identifier")
    parser.add_argument("--name", required=True, help="Human-readable collection name")
    parser.add_argument("--kind", required=True, choices=("data", "charts", "atlas"))
    parser.add_argument("--provider", required=True)
    parser.add_argument("--license", required=True, help="Confirmed data licence, or explicit pending-review status for local preparation")
    parser.add_argument("--attribution", required=True)
    parser.add_argument("--created-at", help="Optional ISO 8601 timestamp, including timezone")
    args = parser.parse_args()
    try:
        result = build_package(args)
    except (PackageError, OSError, ValueError, zipfile.BadZipFile, zipfile.LargeZipFile) as error:
        parser.exit(1, "package_charts: " + str(error) + "\n")
    except KeyboardInterrupt:
        parser.exit(130, "package_charts: interrupted; incomplete output removed\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
