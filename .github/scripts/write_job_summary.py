#!/usr/bin/env python3
"""Write gate outcomes and aggregate Gradle JUnit XML to GITHUB_STEP_SUMMARY."""

from __future__ import annotations

import html
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def test_totals(repo_root: Path) -> tuple[int, int, int, int]:
    totals = [0, 0, 0, 0]
    for report in sorted(repo_root.glob("**/build/test-results/**/TEST-*.xml")):
        try:
            suite = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        for index, key in enumerate(("tests", "failures", "errors", "skipped")):
            totals[index] += int(suite.attrib.get(key, 0))
    return tuple(totals)  # type: ignore[return-value]


def render(title: str, outcomes: list[tuple[str, str]], totals: tuple[int, int, int, int]) -> str:
    lines = [f"## {title}", "", "| Gate | Result |", "|---|---|"]
    for label, result in outcomes:
        marker = "✅" if result == "success" else "⏭️" if result == "skipped" else "❌"
        lines.append(f"| {html.escape(label)} | {marker} `{html.escape(result)}` |")
    tests, failures, errors, skipped = totals
    if tests:
        lines.extend(["", f"JUnit: **{tests}** total · **{failures}** failed · **{errors}** errors · **{skipped}** skipped"])
    return "\n".join(lines) + "\n"


def main() -> int:
    if len(sys.argv) < 2:
        raise SystemExit("usage: write_job_summary.py TITLE [LABEL=RESULT ...]")
    outcomes = []
    for raw in sys.argv[2:]:
        label, separator, result = raw.partition("=")
        if not separator:
            raise SystemExit(f"invalid outcome: {raw}")
        outcomes.append((label, result))
        if result == "failure":
            print(f"::error title={label} failed::Open the job summary and downloadable FAILURE artifact for evidence.")
    repo_root = Path(__file__).resolve().parents[2]
    markdown = render(sys.argv[1], outcomes, test_totals(repo_root))
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write(markdown)
    else:
        print(markdown, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
