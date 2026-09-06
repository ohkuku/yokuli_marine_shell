#!/usr/bin/env python3
"""Extract bounded, non-secret CL11 measurements from Android/JUnit logs."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


MARKER = "CL11_EVIDENCE "
REQUIRED = {"zero-copy-read", "runtime-bounds", "catalog-1000"}
MAX_FILES = 512
MAX_FILE_BYTES = 8 * 1024 * 1024


def evidence_files(root: Path) -> list[Path]:
    candidates: set[Path] = set()
    for pattern in (
        "build/ci-device-tests.log",
        "**/build/outputs/androidTest-results/**/*.xml",
        "**/build/test-results/**/TEST-*.xml",
    ):
        candidates.update(path for path in root.glob(pattern) if path.is_file())
    return sorted(candidates)[:MAX_FILES]


def extract(root: Path) -> dict:
    scenarios: dict[str, dict] = {}
    sources: dict[str, str] = {}
    rejected = 0
    for path in evidence_files(root):
        try:
            raw = path.read_bytes()[:MAX_FILE_BYTES].decode("utf-8", errors="replace")
        except OSError:
            continue
        for line_number, line in enumerate(raw.splitlines(), 1):
            marker = line.find(MARKER)
            if marker < 0:
                continue
            encoded = line[marker + len(MARKER):].strip()
            # XML may append a closing element on the same physical line.
            match = re.match(r"(\{.*?\})(?:\s*</|$)", encoded)
            if not match:
                rejected += 1
                continue
            try:
                value = json.loads(match.group(1))
            except json.JSONDecodeError:
                rejected += 1
                continue
            scenario = value.get("scenario") if isinstance(value, dict) else None
            if not isinstance(scenario, str) or scenario not in REQUIRED:
                rejected += 1
                continue
            scenarios[scenario] = value
            sources[scenario] = f"{path.relative_to(root).as_posix()}:{line_number}"
    missing = sorted(REQUIRED - scenarios.keys())
    return {
        "schemaVersion": 1,
        "status": "COMPLETE" if not missing else "PARTIAL",
        "requiredScenarios": sorted(REQUIRED),
        "missingScenarios": missing,
        "rejectedRecords": rejected,
        "measurements": {key: scenarios[key] for key in sorted(scenarios)},
        "sources": {key: sources[key] for key in sorted(sources)},
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--require-complete", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    result = extract(root)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 1 if args.require_complete and result["status"] != "COMPLETE" else 0


if __name__ == "__main__":
    raise SystemExit(main())
