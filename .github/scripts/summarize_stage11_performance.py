#!/usr/bin/env python3
"""Create a non-gating, machine-readable Stage 11 emulator trend summary."""

from __future__ import annotations

import argparse
import datetime as dt
import json
from pathlib import Path


EXPECTED_JOURNEYS = {
    "coldStartToStart",
    "warmStartToStart",
    "startToAllApps",
    "openChartAndReturn",
    "startVerticalScroll60Tiles",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--search", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--require-journeys", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source_files = sorted(args.search.rglob("*-benchmarkData.json"))
    if not source_files:
        raise SystemExit(f"No AndroidX benchmark data found below {args.search}")

    results: list[dict] = []
    contexts: list[dict] = []
    for source in source_files:
        payload = json.loads(source.read_text())
        context = payload.get("context")
        if isinstance(context, dict):
            contexts.append(context)
        for benchmark in payload.get("benchmarks", []):
            name = benchmark.get("name")
            if not isinstance(name, str):
                continue
            results.append(
                {
                    "name": name,
                    "className": benchmark.get("className"),
                    "metrics": benchmark.get("metrics", {}),
                    "sampledMetrics": benchmark.get("sampledMetrics", {}),
                    "source": str(source),
                }
            )

    present = {result["name"] for result in results}
    missing = sorted(EXPECTED_JOURNEYS - present)
    if args.require_journeys and missing:
        raise SystemExit(f"Missing Stage 11 journeys: {', '.join(missing)}")

    summary = {
        "schemaVersion": 1,
        "status": "EMULATOR_TREND_ONLY",
        "generatedAtUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "hardPerformanceGate": False,
        "interpretation": (
            "Android emulator measurements detect relative regressions only; they do not "
            "verify physical 60/90/120 Hz devices or Samsung square hardware."
        ),
        "expectedJourneys": sorted(EXPECTED_JOURNEYS),
        "missingJourneys": missing,
        "contexts": contexts,
        "results": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    print(f"STAGE11_PERFORMANCE_SUMMARY=PASS journeys={len(present)} status=EMULATOR_TREND_ONLY")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
