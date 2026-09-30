#!/usr/bin/env python3
"""Package one offline chart collection as .yklchart using only the stdlib.

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
                if (kind == "charts") != (format_name == "mbtiles"):
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
    entries.sort(key=lambda entry: entry["relative"])
    for priority, entry in enumerate(entries):
        entry["priority"] = priority
    return entries


def check_signature(entry, header):
    format_name = entry["format"]
    valid = False
    if format_name in ("gpkg", "mbtiles"):
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
    if output.suffix.lower() != ".yklchart":
        raise PackageError("--output must have the .yklchart extension")
    if output.is_symlink():
        raise PackageError("--output cannot be a symbolic link")
    if output.resolve().is_relative_to(source):
        raise PackageError("Place --output outside --source")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", args.id):
        raise PackageError("--id must be 1–80 letters, digits, dots, underscores or hyphens, starting with a letter or digit")
    manifest = {"format": FORMAT, "version": 1, "id": args.id,
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
    parser.add_argument("--output", required=True, help="Atomic .yklchart output outside the source directory")
    parser.add_argument("--id", required=True, help="Stable collection identifier")
    parser.add_argument("--name", required=True, help="Human-readable collection name")
    parser.add_argument("--kind", required=True, choices=("data", "charts"))
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
