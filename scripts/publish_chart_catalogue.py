#!/usr/bin/env python3
"""Publish the single maintained chart catalogue to the website and Android asset.

This is a release content generator, not a downloader or a CI/test runner. Large
payloads stay in Git LFS. A release is downloadable only when status=published;
changing that state is the maintainer's responsibility after the LFS upload.
"""
from __future__ import annotations

import json
import os
import re
import shutil
from pathlib import Path, PurePosixPath
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "chart-library/catalogue.json"
DESTINATIONS = (
    ROOT / "docs/charts/catalogue.json",
    ROOT / "runtime/marine-local/src/main/assets/chart-store/catalogue.json",
)
STATES = {"published", "uploading", "withdrawn", "draft"}
CONTENT_TYPES = {"charts", "data", "navigation", "terrain", "sources"}


def publish() -> None:
    catalogue = json.loads(SOURCE.read_text(encoding="utf-8"))
    if catalogue.get("format") != "yokuli.chart-catalogue" or catalogue.get("version") != 1:
        raise ValueError("Unsupported catalogue format or version")
    ids: set[str] = set()
    releases: set[tuple[str, str]] = set()
    for entry in catalogue["collections"]:
        identity = entry["id"]
        if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{0,127}", identity) or identity in ids:
            raise ValueError(f"Invalid or duplicate release id: {identity}")
        ids.add(identity)
        release = (entry["collectionId"], entry["releaseVersion"])
        if release in releases:
            raise ValueError(f"Duplicate series/version: {release}")
        releases.add(release)
        for field in ("name", "nameEn", "description", "descriptionEn", "provider", "license", "attribution"):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                raise ValueError(f"{identity}: missing {field}")
        if entry["status"] not in STATES:
            raise ValueError(f"{identity}: invalid status")
        if not entry["contentTypes"] or not set(entry["contentTypes"]) <= CONTENT_TYPES:
            raise ValueError(f"{identity}: invalid contentTypes")
        if not isinstance(entry["bytes"], int) or entry["bytes"] <= 0:
            raise ValueError(f"{identity}: invalid byte count")
        if not re.fullmatch(r"[a-f0-9]{64}", entry["sha256"]):
            raise ValueError(f"{identity}: invalid SHA-256")
        file_name = entry["fileName"]
        if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9._-]{0,180}\.yklpkg", file_name):
            raise ValueError(f"{identity}: unsafe .yklpkg filename")
        path = PurePosixPath(entry["downloadSubdirectory"])
        if not path.parts or path.is_absolute() or any(part in {"..", "."} for part in path.parts) or "\\" in str(path):
            raise ValueError(f"{identity}: unsafe download directory")
        url = urlparse(entry["downloadUrl"])
        if url.scheme != "https" or url.hostname not in {"github.com", "media.githubusercontent.com"} or url.username:
            raise ValueError(f"{identity}: download must use the public GitHub LFS host")
        if entry["distribution"] != "git-lfs":
            raise ValueError(f"{identity}: unsupported distribution")
        location = entry["location"]
        for field in ("continent", "continentName", "continentNameEn", "country", "countryName", "countryNameEn", "region", "regionName", "regionNameEn"):
            if not location.get(field):
                raise ValueError(f"{identity}: missing location.{field}")
    # Deterministic output: source timestamps describe content; generation never
    # manufactures a new release date or claims a pending payload is published.
    data = (json.dumps(catalogue, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    for destination in DESTINATIONS:
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = destination.with_suffix(destination.suffix + ".tmp")
        try:
            with temporary.open("wb") as output:
                output.write(data)
                output.flush()
                os.fsync(output.fileno())
            temporary.replace(destination)
        finally:
            temporary.unlink(missing_ok=True)
        print(destination.relative_to(ROOT))
    # Website marks follow the same existing brand masters as the app.
    shutil.copyfile(ROOT / "design/brand/yokuli-mark.svg", ROOT / "docs/charts/mark.svg")
    shutil.copyfile(ROOT / "design/brand/yokuli-os.svg", ROOT / "docs/charts/wordmark.svg")


if __name__ == "__main__":
    publish()
