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
    "desktopModuleListRoundTrip",
    "searchToChart",
    "dragAcrossThirtyMixedTiles",
    "resizeStandardTileToLarge",
    "rounded320Viewport",
    "settingsScroll",
}

STARTUP_JOURNEYS = {"coldStartToStart", "warmStartToStart"}
INTERACTION_JOURNEYS = EXPECTED_JOURNEYS - STARTUP_JOURNEYS


def has_observed_frames(result: dict) -> bool:
    """Reject gfxinfo's empty-window sentinel while accepting physical frame samples."""
    metrics = result.get("metrics", {})
    gfx_count = metrics.get("gfxFrameTotalCount")
    if isinstance(gfx_count, dict):
        values = [gfx_count.get("maximum"), gfx_count.get("median")]
        values.extend(gfx_count.get("runs", []))
        return any(isinstance(value, (int, float)) and value > 0 for value in values)

    for metric_name in ("frameDurationCpuMs", "frameOverrunMs"):
        metric = metrics.get(metric_name)
        if isinstance(metric, dict):
            runs = metric.get("runs", [])
            if runs or any(isinstance(metric.get(key), (int, float)) for key in ("median", "maximum")):
                return True

    sampled = result.get("sampledMetrics", {})
    return any(
        metric_name in sampled and bool(sampled[metric_name])
        for metric_name in ("frameDurationCpuMs", "frameOverrunMs")
    )


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
    empty_frame_journeys = sorted(
        result["name"]
        for result in results
        if result["name"] in INTERACTION_JOURNEYS and not has_observed_frames(result)
    )
    if args.require_journeys and empty_frame_journeys:
        raise SystemExit(
            "Stage 11 interaction journeys observed no target frames: "
            + ", ".join(empty_frame_journeys)
        )

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
        "emptyFrameJourneys": empty_frame_journeys,
        "contexts": contexts,
        "results": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    print(f"STAGE11_PERFORMANCE_SUMMARY=PASS journeys={len(present)} status=EMULATOR_TREND_ONLY")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
