#!/usr/bin/env python3
"""Extract the real provider/SQLite legacy MBTiles compatibility scenarios."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


MARKER = "LEGACY_MBTILES_EVIDENCE "
REQUIRED = {
    "stream-fallback-render",
    "optional-metadata-render",
    "poisoned-same-revision-fallback-render",
}
MAX_FILES = 256
MAX_FILE_BYTES = 8 * 1024 * 1024


def extract(root: Path) -> dict:
    candidates: set[Path] = set()
    for pattern in (
        "build/ci-device-tests.log",
        "adapter/chart-library-android/build/outputs/androidTest-results/**/*.xml",
    ):
        candidates.update(path for path in root.glob(pattern) if path.is_file())
    scenarios: dict[str, dict] = {}
    sources: dict[str, str] = {}
    rejected = 0
    for path in sorted(candidates)[:MAX_FILES]:
        raw = path.read_bytes()[:MAX_FILE_BYTES].decode("utf-8", errors="replace")
        for line_number, line in enumerate(raw.splitlines(), 1):
            marker = line.find(MARKER)
            if marker < 0:
                continue
            match = re.match(r"(\{.*?\})(?:\s*</|$)", line[marker + len(MARKER):].strip())
            if not match:
                rejected += 1
                continue
            try:
                value = json.loads(match.group(1))
            except json.JSONDecodeError:
                rejected += 1
                continue
            scenario = value.get("scenario") if isinstance(value, dict) else None
            if scenario not in REQUIRED or value.get("result") != "PASS":
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
        "evidence": {key: scenarios[key] for key in sorted(scenarios)},
        "sources": {key: sources[key] for key in sorted(sources)},
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--require-complete", action="store_true")
    args = parser.parse_args()
    result = extract(args.root.resolve())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 1 if args.require_complete and result["status"] != "COMPLETE" else 0


if __name__ == "__main__":
    raise SystemExit(main())
