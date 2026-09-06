#!/usr/bin/env python3
"""Emit non-secret evidence that a built APK variant received Maps configuration."""

from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path


ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
PLACEHOLDER = "${GOOGLE_MAPS_ANDROID_API_KEY}"
UNAVAILABLE = "MAPS_API_KEY_NOT_CONFIGURED"


def exactly_one(paths: list[Path], description: str) -> Path:
    if len(paths) != 1:
        raise ValueError(f"expected one {description}, found {len(paths)}")
    return paths[0]


def inspect(build_root: Path) -> dict:
    build_config = exactly_one(
        list(build_root.glob("generated/source/buildConfig/standalone/release/**/BuildConfig.java")),
        "standalone release BuildConfig",
    )
    manifest = exactly_one(
        list(build_root.glob("intermediates/merged_manifests/standaloneRelease/**/AndroidManifest.xml")),
        "standalone release merged manifest",
    )

    config_text = build_config.read_text(encoding="utf-8")
    configured_match = re.search(
        r"public static final boolean GOOGLE_MAPS_CONFIGURED\s*=\s*(true|false);",
        config_text,
    )
    application_match = re.search(
        r'public static final String APPLICATION_ID\s*=\s*"([A-Za-z0-9_.]+)";',
        config_text,
    )
    if configured_match is None or application_match is None:
        raise ValueError("release BuildConfig is missing the typed Maps configuration contract")

    root = ET.parse(manifest).getroot()
    application = root.find("application")
    if application is None:
        raise ValueError("merged manifest has no application element")
    values = [
        node.attrib.get(ANDROID_NS + "value", "")
        for node in application.findall("meta-data")
        if node.attrib.get(ANDROID_NS + "name") == "com.google.android.geo.API_KEY"
    ]
    if len(values) != 1:
        raise ValueError(f"expected one Google Maps metadata entry, found {len(values)}")
    manifest_configured = values[0] not in {"", PLACEHOLDER, UNAVAILABLE}
    build_configured = configured_match.group(1) == "true"
    return {
        "schemaVersion": 1,
        "variant": "standaloneRelease",
        "applicationId": application_match.group(1),
        "buildConfigConfigured": build_configured,
        "manifestConfigured": manifest_configured,
        "configurationConsistent": build_configured == manifest_configured,
        "meaning": "CONFIGURATION_ONLY_NOT_RUNTIME_READINESS",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--build-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = inspect(args.build_root.resolve())
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"::error title=Google Maps build evidence::{error}")
        return 1
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if not result["configurationConsistent"]:
        print("::error title=Google Maps build evidence::BuildConfig and merged manifest disagree")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
