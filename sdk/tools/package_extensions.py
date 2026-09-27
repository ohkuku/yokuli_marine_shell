#!/usr/bin/env python3
"""Build reproducible Yokuli packages and refresh bundled SDK/catalog/docs assets.

Source authority: sdk/web, sdk/examples and docs/developers. No Gradle/CI hooks.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[2]
LIMIT_FILE = 8 * 1024 * 1024
LIMIT_TOTAL = 32 * 1024 * 1024
SDK1_PERMISSIONS = {"marine.read", "nmea.read", "navigation.open"}
PERMISSIONS = SDK1_PERMISSIONS | {"devices.read", "sources.read", "sources.control", "nmea.control", "sharing.read", "sharing.control", "voyage.read", "voyage.control"}


def package(source: Path, output: Path) -> dict:
    source = source.resolve()
    if output.suffix != ".ykl":
        raise ValueError("Installable Yokuli packages use the .ykl extension")
    manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
    app_id = manifest.get("id", "")
    if (not isinstance(app_id, str) or len(app_id) > 120 or
        not re.fullmatch(r"[a-z][a-z0-9-]*(?:\.[a-z][a-z0-9-]*)+", app_id) or
        any(len(part) > 40 or part.endswith("-") for part in app_id.split(".")) or
        app_id == "com.yokuli" or app_id.startswith("com.yokuli.")):
        raise ValueError("id must be a lowercase reverse-domain name outside the reserved com.yokuli namespace")
    entry = manifest.get("entry", "index.html")
    if type(manifest.get("sdk")) is not int or manifest.get("sdk") not in (1, 2) or not isinstance(entry, str) or not entry.endswith(".html"):
        raise ValueError("Packages require sdk=1 or sdk=2 and a local HTML entry")
    if type(manifest.get("version")) is not int or manifest["version"] < 1:
        raise ValueError("version must be a positive integer")
    for field in ("name", "nameEn", "description"):
        if not isinstance(manifest.get(field), str) or not manifest[field].strip():
            raise ValueError(f"{field} is required")
    for field, limit in (("name", 48), ("nameEn", 64), ("description", 600), ("descriptionEn", 600)):
        value = manifest.get(field, "")
        if not isinstance(value, str) or len(value) > limit or any(ord(ch) < 32 and ch != "\n" for ch in value):
            raise ValueError(f"Invalid {field}")
    if output.resolve() == source or source in output.resolve().parents:
        raise ValueError("Write the output archive outside the application source folder")
    permissions = manifest.get("permissions", [])
    if not isinstance(permissions, list) or any(item not in PERMISSIONS for item in permissions):
        raise ValueError("Unknown SDK permission")
    if manifest["sdk"] == 1 and any(item not in SDK1_PERMISSIONS for item in permissions):
        raise ValueError("System-control permissions require sdk=2")
    files = sorted(path for path in source.rglob("*") if path.is_file())
    if len(files) > 256 or not any(path.relative_to(source).as_posix() == entry for path in files):
        raise ValueError("Package needs its declared HTML entry and at most 256 files")
    total = 0
    for path in files:
        if path.is_symlink() or source not in path.resolve().parents:
            raise ValueError("Symlinks and paths outside the package are not allowed")
        relative = path.relative_to(source).as_posix()
        if (len(relative) > 240 or any(part.startswith(".") for part in path.relative_to(source).parts)
            or relative == "_sdk" or relative.startswith("_sdk/")
            or any(ch in "\\:?#%" or ord(ch) < 32 for ch in relative)):
            raise ValueError("Hidden, ambiguous and reserved SDK paths are not allowed")
        size = path.stat().st_size
        if size > LIMIT_FILE:
            raise ValueError(f"File exceeds 8 MiB: {relative}")
        total += size
    if total > LIMIT_TOTAL:
        raise ValueError("Package exceeds 32 MiB uncompressed")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            info = zipfile.ZipInfo(path.relative_to(source).as_posix(), date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())
    if output.stat().st_size > LIMIT_TOTAL:
        output.unlink()
        raise ValueError("Compressed package exceeds 32 MiB")
    return {**manifest, "sha256": hashlib.sha256(output.read_bytes()).hexdigest(), "bytes": output.stat().st_size}


def package_sdk(output: Path) -> None:
    """A self-contained source archive, usable from the website and Android SAF export."""
    sources = [ROOT / "sdk/README.md", ROOT / "sdk/tools/package_extensions.py", ROOT / "gradlew", ROOT / "gradlew.bat"]
    for directory in ["sdk/web", "sdk/examples", "sdk/templates/javascript", "sdk/templates/kotlin-js/src", "gradle/wrapper"]:
        sources.extend(path for path in (ROOT / directory).rglob("*") if path.is_file())
    sources.extend(ROOT / "sdk/templates/kotlin-js" / name for name in ["settings.gradle.kts", "build.gradle.kts", "gradle.properties", "manifest.json", "kotlin-js-store/package-lock.json"])
    sources.extend(ROOT / "docs/developers" / name for name in ["index.html", "site.css", "site.js"])
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(sources):
            if not path.is_file():
                continue
            info = zipfile.ZipInfo(path.relative_to(ROOT).as_posix(), date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if path.name == "gradlew" else 0o100644) << 16
            archive.writestr(info, path.read_bytes())


def refresh_bundled() -> None:
    assets = ROOT / "app-shell/src/main/assets/extensions"
    (assets / "sdk").mkdir(parents=True, exist_ok=True)
    for file in (ROOT / "sdk/web").iterdir():
        if file.is_file():
            shutil.copy2(file, assets / "sdk" / file.name)
    catalog = []
    for source in sorted((ROOT / "sdk/examples").iterdir()):
        if source.is_dir() and (source / "manifest.json").exists():
            manifest = json.loads((source / "manifest.json").read_text())
            relative = f"catalog/{manifest['id']}.ykl"
            entry = package(source, assets / relative)
            entry["asset"] = relative
            catalog.append(entry)
    (assets / "catalog.json").write_text(json.dumps({"sdk": 2, "apps": catalog}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    package_sdk(ROOT / "docs/developers/yokuli-sdk-2.zip")
    # Remove obsolete generated file names only; installed SDK 1 user data is untouched.
    for obsolete in [ROOT / "docs/developers/yokuli-sdk-1.zip", assets / "developers/yokuli-sdk-1.zip"]:
        obsolete.unlink(missing_ok=True)
    for app in catalog:
        (assets / "catalog" / (app["id"] + ".yokuli.zip")).unlink(missing_ok=True)
    docs = assets / "developers"
    docs.mkdir(parents=True, exist_ok=True)
    for file in (ROOT / "docs/developers").iterdir():
        if file.is_file() and file.suffix in {".html", ".css", ".js", ".zip"}:
            shutil.copy2(file, docs / file.name)
    print(f"Bundled {len(catalog)} applications, SDK 2 and developer documentation")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", nargs="?", type=Path, help="Directory containing manifest.json/index.html")
    parser.add_argument("--output", type=Path, help="Destination .ykl")
    args = parser.parse_args()
    if args.source:
        if not args.output:
            parser.error("--output is required with source")
        result = package(args.source, args.output)
        print(f"Packaged {result['id']} version {result['version']}: {args.output}")
    elif args.output:
        parser.error("source is required with --output")
    else:
        refresh_bundled()
