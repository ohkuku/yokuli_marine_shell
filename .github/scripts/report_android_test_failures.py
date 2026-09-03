#!/usr/bin/env python3
"""Expose instrumented failures as GitHub annotations and a compact summary."""

from __future__ import annotations

import html
import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
RESULT_ROOT = REPO_ROOT / "app-shell" / "build" / "outputs" / "androidTest-results"
DEVICE_LOG = REPO_ROOT / "build" / "ci-device-tests.log"
MAX_FAILURES = 30


def github_escape(value: str) -> str:
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def compact(value: str, limit: int = 1600) -> str:
    value = re.sub(r"\x1b\[[0-9;]*m", "", value)
    value = re.sub(r"[ \t]+", " ", value)
    value = re.sub(r"\n{3,}", "\n\n", value).strip()
    return value[:limit] + ("…" if len(value) > limit else "")


def xml_failures(root_path: Path = RESULT_ROOT) -> list[tuple[str, str]]:
    failures: list[tuple[str, str]] = []
    if not root_path.exists():
        return failures
    for result_file in sorted(root_path.rglob("*.xml")):
        try:
            root = ET.parse(result_file).getroot()
        except ET.ParseError:
            continue
        for test_case in root.iter("testcase"):
            nodes = list(test_case.findall("failure")) + list(test_case.findall("error"))
            if not nodes:
                continue
            title = f"{test_case.attrib.get('classname', 'instrumented test')}.{test_case.attrib.get('name', 'unknown')}"
            details = "\n".join(
                filter(None, (node.attrib.get("message", "").strip() or (node.text or "").strip() for node in nodes))
            )
            failures.append((title, compact(details or "Instrumented test failed without a message.")))
            if len(failures) >= MAX_FAILURES:
                return failures
    return failures


def log_failure_excerpt() -> str:
    if not DEVICE_LOG.exists():
        return "No Android test XML or captured Gradle device log was produced."
    lines = DEVICE_LOG.read_text(encoding="utf-8", errors="replace").splitlines()
    interesting = [
        line for line in lines
        if re.search(r"( FAILED$|FAILURE:|What went wrong|INSTRUMENTATION_(FAILED|ABORTED)|Process crashed|Exception|Error:)", line, re.I)
    ]
    return compact("\n".join((interesting[-24:] if interesting else lines[-40:])), limit=3000)


def main() -> int:
    failures = xml_failures()
    if failures:
        for title, details in failures:
            print(f"::error title={github_escape(title)}::{github_escape(details)}")
    else:
        print(f"::error title=Android device test failed::{github_escape(log_failure_excerpt())}")
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write("\n## Android device-test failure details\n\n")
            if failures:
                summary.write("| Test | Failure |\n|---|---|\n")
                for title, details in failures:
                    summary.write(f"| `{html.escape(title)}` | {html.escape(details.splitlines()[0])} |\n")
            else:
                summary.write(f"```text\n{log_failure_excerpt()}\n```\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
