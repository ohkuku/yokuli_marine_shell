#!/usr/bin/env python3
"""Build a bounded, secret-redacted report for one Yokuli CI job."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable


MODULE_ROOTS = (
    "app-shell",
    "benchmark/shell",
    "baselineprofile/shell",
    "core/design",
    "core/model",
    "core/marine-data",
    "core/shell-contract",
    "core/shell-engine",
    "core/navigation",
    "core/chart-library",
    "adapter/marine-data-android",
    "adapter/map-offline",
    "adapter/map-storage",
    "feature/chart",
    "feature/chart-library",
    "feature/data",
    "feature/data-sources",
    "feature/desktop",
    "feature/navigation",
    "feature/nmea-input",
    "feature/preferences",
    "feature/settings",
)
PROCESS_LOGS = (
    "build/ci-device-tests.log",
    "build/ci-c12-process-restore.log",
    "build/ci-nmea-sources-process-restore.log",
)
MAX_FAILURES_PER_GROUP = 50
MAX_EXCERPT_LINES = 60
MAX_EXCERPT_CHARS = 24_000

ANSI = re.compile(r"\x1b\[[0-9;]*m")
SECRET_PATTERNS = (
    (re.compile(r"AIza[0-9A-Za-z_-]{20,}"), "[REDACTED_GOOGLE_API_KEY]"),
    (re.compile(r"gh[pousr]_[0-9A-Za-z_]{20,}"), "[REDACTED_GITHUB_TOKEN]"),
    (
        re.compile(r"(?i)\b(api[_-]?key|token|password|secret)\s*[:=]\s*[^\s]+"),
        r"\1=[REDACTED]",
    ),
)
COMPILE_PATTERNS = (
    re.compile(r"^(?:e: )?(?:file://)?(?P<path>[^:\n]+\.(?:kt|kts|java)):(?P<line>\d+)(?::\d+)?:?\s*(?P<message>.+)$"),
    re.compile(r"^(?P<path>[^:\n]+\.java):(?P<line>\d+):\s*error:\s*(?P<message>.+)$"),
)
PROCESS_PATTERN = re.compile(
    r"(FAILURE:|What went wrong|INSTRUMENTATION_(?:FAILED|ABORTED)|Process crashed|"
    r"emulator.*(?:timeout|failed)|timed out|Exception|Error:)",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class Finding:
    category: str
    module: str
    name: str
    message: str
    evidence: str
    sourcePath: str | None = None
    line: int | None = None
    rawEvidence: str | None = None


def redact(value: str) -> str:
    result = ANSI.sub("", value).replace("\x00", "")
    for pattern, replacement in SECRET_PATTERNS:
        result = pattern.sub(replacement, result)
    return result


def compact(value: str, limit: int = 4_000) -> str:
    value = redact(value)
    value = re.sub(r"[ \t]+", " ", value)
    value = re.sub(r"\n{3,}", "\n\n", value).strip()
    return value[:limit] + ("…" if len(value) > limit else "")


def relative(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.name


def module_for(path: Path, root: Path) -> str:
    rel = relative(path, root)
    for module in sorted(MODULE_ROOTS, key=len, reverse=True):
        if rel == module or rel.startswith(module + "/"):
            return module
    return "ci"


def allowlisted_evidence(repo_root: Path, job: str) -> list[Path]:
    """Return only build evidence paths explicitly approved by W00."""
    paths: set[Path] = set()
    capture_root = repo_root / "build" / "codex-ci"
    paths.update(path for path in (capture_root / "logs").glob("*.log") if path.is_file())
    paths.update(path for path in (capture_root / "steps").glob("*.json") if path.is_file())

    for module in MODULE_ROOTS:
        module_root = repo_root / module / "build"
        for pattern in (
            "test-results/**/TEST-*.xml",
            "outputs/androidTest-results/**/*.xml",
            "reports/lint-results-*.xml",
        ):
            paths.update(path for path in module_root.glob(pattern) if path.is_file())

    if job in {"api34", "api36", "performance"}:
        paths.update(
            repo_root / item for item in PROCESS_LOGS if (repo_root / item).is_file()
        )
    return sorted(paths)


def load_steps(repo_root: Path) -> list[dict]:
    steps: list[dict] = []
    step_root = repo_root / "build" / "codex-ci" / "steps"
    for path in sorted(step_root.glob("*.json")):
        try:
            item = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if isinstance(item, dict) and isinstance(item.get("label"), str):
            item["metadataPath"] = relative(path, repo_root)
            steps.append(item)
    return steps


def junit_findings(repo_root: Path, evidence: Iterable[Path]) -> tuple[list[Finding], list[str]]:
    findings: list[Finding] = []
    warnings: list[str] = []
    for path in evidence:
        if "test-results" not in path.as_posix() and "androidTest-results" not in path.as_posix():
            continue
        try:
            xml_root = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as error:
            warnings.append(f"Malformed XML ignored: {relative(path, repo_root)}: {compact(str(error), 300)}")
            continue
        for case in xml_root.iter("testcase"):
            nodes = list(case.findall("failure")) + list(case.findall("error"))
            for node in nodes:
                test_name = ".".join(
                    part for part in (case.attrib.get("classname"), case.attrib.get("name")) if part
                ) or "unnamed test"
                message = node.attrib.get("message") or node.text or "Test failed without a message."
                findings.append(
                    Finding(
                        category="test",
                        module=module_for(path, repo_root),
                        name=test_name,
                        message=compact(message),
                        evidence=relative(path, repo_root),
                    )
                )
                if len(findings) >= MAX_FAILURES_PER_GROUP:
                    return findings, warnings
    return findings, warnings


def lint_findings(repo_root: Path, evidence: Iterable[Path]) -> tuple[list[Finding], list[str]]:
    findings: list[Finding] = []
    warnings: list[str] = []
    for path in evidence:
        if not path.name.startswith("lint-results-"):
            continue
        try:
            xml_root = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as error:
            warnings.append(f"Malformed lint XML ignored: {relative(path, repo_root)}: {compact(str(error), 300)}")
            continue
        for issue in xml_root.iter("issue"):
            severity = issue.attrib.get("severity", "")
            if severity.lower() not in {"error", "fatal"}:
                continue
            location = issue.find("location")
            source_path = location.attrib.get("file") if location is not None else None
            line_text = location.attrib.get("line") if location is not None else None
            findings.append(
                Finding(
                    category="lint",
                    module=module_for(path, repo_root),
                    name=issue.attrib.get("id", "Android lint"),
                    message=compact(issue.attrib.get("message", "Lint error without a message.")),
                    evidence=relative(path, repo_root),
                    sourcePath=source_path,
                    line=int(line_text) if line_text and line_text.isdigit() else None,
                )
            )
            if len(findings) >= MAX_FAILURES_PER_GROUP:
                return findings, warnings
    return findings, warnings


def log_findings(repo_root: Path, evidence: Iterable[Path]) -> tuple[list[Finding], list[Finding], dict[str, str]]:
    compile_errors: list[Finding] = []
    process_errors: list[Finding] = []
    excerpts: dict[str, str] = {}
    for path in evidence:
        if path.suffix != ".log":
            continue
        try:
            lines = redact(path.read_text(encoding="utf-8", errors="replace")).splitlines()
        except OSError:
            continue
        interesting_indexes: list[int] = []
        excerpt_path = f"relevant-log-excerpts/{path.stem}.txt"
        raw_path = relative(path, repo_root)
        for index, line in enumerate(lines):
            matched_compile = False
            for pattern in COMPILE_PATTERNS:
                match = pattern.match(line.strip())
                if not match:
                    continue
                source_path = match.group("path").removeprefix(str(repo_root) + "/")
                compile_errors.append(
                    Finding(
                        category="compile",
                        module=module_for(repo_root / source_path, repo_root),
                        name="Compilation",
                        message=compact(match.group("message"), 1_500),
                        evidence=excerpt_path,
                        sourcePath=source_path,
                        line=int(match.group("line")),
                        rawEvidence=raw_path,
                    )
                )
                interesting_indexes.append(index)
                matched_compile = True
                break
            if not matched_compile and PROCESS_PATTERN.search(line):
                process_errors.append(
                    Finding(
                        category="process",
                        module=module_for(path, repo_root),
                        name=path.stem,
                        message=compact(line, 1_500),
                        evidence=excerpt_path,
                        rawEvidence=raw_path,
                    )
                )
                interesting_indexes.append(index)

        if interesting_indexes:
            selected: list[str] = []
            seen: set[int] = set()
            for index in interesting_indexes:
                for line_index in range(max(0, index - 3), min(len(lines), index + 7)):
                    if line_index not in seen:
                        selected.append(lines[line_index])
                        seen.add(line_index)
                    if len(selected) >= MAX_EXCERPT_LINES:
                        break
                if len(selected) >= MAX_EXCERPT_LINES:
                    break
            excerpts[path.stem + ".txt"] = compact("\n".join(selected), MAX_EXCERPT_CHARS)
    return (
        compile_errors[:MAX_FAILURES_PER_GROUP],
        process_errors[:MAX_FAILURES_PER_GROUP],
        excerpts,
    )


def owner_guess(finding: Finding) -> str:
    return finding.module if finding.module != "ci" else "CI harness"


def finding_markdown(identifier: str, title: str, finding: Finding) -> list[str]:
    lines = [
        f"## {identifier} — {title}",
        "",
        f"Owner guess: {owner_guess(finding)}",
        f"Evidence: `{finding.evidence}`",
    ]
    if finding.sourcePath:
        suffix = f":{finding.line}" if finding.line else ""
        lines.append(f"Likely file: `{finding.sourcePath}{suffix}`")
    if finding.rawEvidence:
        lines.append(f"Raw evidence: `{finding.rawEvidence}`")
    lines.extend([f"Failure: `{finding.name}`", "", finding.message, ""])
    return lines


def write_inventory(output: Path) -> None:
    contents = sorted(
        path.relative_to(output).as_posix()
        for path in output.rglob("*")
        if path.is_file() and path.name not in {"CONTENTS.txt", "SHA256SUMS.txt"}
    )
    (output / "CONTENTS.txt").write_text("\n".join(contents) + "\n", encoding="utf-8")
    hash_targets = sorted(
        path for path in output.rglob("*") if path.is_file() and path.name != "SHA256SUMS.txt"
    )
    sums = [
        f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(output).as_posix()}"
        for path in hash_targets
    ]
    (output / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="utf-8")


def build_report(repo_root: Path, output: Path, job: str, status: str, head_sha: str) -> dict:
    if output.exists():
        shutil.rmtree(output)
    (output / "relevant-log-excerpts").mkdir(parents=True)

    evidence = allowlisted_evidence(repo_root, job)
    attachments: list[str] = []
    cl11_evidence = repo_root / "build" / "chart-library-cl11" / "evidence.json"
    if job == "api34" and cl11_evidence.is_file():
        destination = output / "chart-library-cl11-evidence.json"
        shutil.copyfile(cl11_evidence, destination)
        attachments.append(destination.name)
    steps = load_steps(repo_root)
    tests, test_warnings = junit_findings(repo_root, evidence)
    lint, lint_warnings = lint_findings(repo_root, evidence)
    compile_errors, process_errors, excerpts = log_findings(repo_root, evidence)
    for name, content in excerpts.items():
        (output / "relevant-log-excerpts" / name).write_text(content + "\n", encoding="utf-8")

    failed_steps = [step for step in steps if step.get("outcome") == "failure"]
    contract_failures = [
        Finding(
            category="contract",
            module="ci",
            name=str(step["label"]),
            message=f"Captured step exited with code {step.get('exitCode', 'unknown')}.",
            evidence=str(step.get("log", step.get("metadataPath", "unknown"))),
        )
        for step in failed_steps
        if any(token in str(step["label"]) for token in ("contract", "ci-helpers", "metadata", "secrets"))
    ]

    groups: list[tuple[str, str, list[Finding]]] = [
        ("Compile", "compilation/build errors", compile_errors),
        ("Tests", "failing tests", tests),
        ("Lint", "lint errors", lint),
        ("Contracts", "contract failures", contract_failures),
        ("Process", "timeout/emulator/process failures", process_errors),
    ]
    evidence_sufficient = status == "success" or any(findings for _, _, findings in groups)
    report = {
        "schemaVersion": 1,
        "job": job,
        "status": status,
        "headSha": head_sha,
        "evidenceSufficient": evidence_sufficient,
        "steps": steps,
        "counts": {key.lower(): len(findings) for key, _, findings in groups},
        "failureGroups": [
            {"kind": key, "items": [asdict(item) for item in findings]}
            for key, _, findings in groups
            if findings
        ],
        "warnings": test_warnings + lint_warnings,
        "attachments": attachments,
    }
    (output / "job.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (output / "failed-tests.json").write_text(
        json.dumps([asdict(item) for item in tests], indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (output / "lint-findings.json").write_text(
        json.dumps([asdict(item) for item in lint], indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (output / "compile-errors.txt").write_text(
        "\n".join(
            f"{item.sourcePath or item.module}:{item.line or '-'}: {item.message}" for item in compile_errors
        )
        + ("\n" if compile_errors else ""),
        encoding="utf-8",
    )

    lines = [
        f"# Codex job report — {job}",
        "",
        f"HEAD: `{head_sha}`",
        f"RESULT: **{status.upper()}**",
        "",
    ]
    if status == "success":
        lines.extend(["No blocking evidence was found.", ""])
    elif not evidence_sufficient:
        lines.extend(
            [
                "EVIDENCE_INSUFFICIENT",
                "",
                "The job failed without a recognized compile, test, lint, contract, or process record.",
                "Use the raw artifact named by the unified report.",
                "",
            ]
        )
    finding_index = 1
    for key, title, findings in groups:
        for finding in findings:
            lines.extend(finding_markdown(f"F{finding_index}", title, finding))
            finding_index += 1
    for warning in report["warnings"]:
        lines.extend(["## Parser warning", "", warning, ""])
    (output / "JOB_REPORT.md").write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    write_inventory(output)
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--job", required=True, choices=("build", "api34", "api36", "performance"))
    parser.add_argument("--status", required=True)
    parser.add_argument("--head-sha", required=True)
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    build_report(args.repo_root.resolve(), args.output.resolve(), args.job, args.status.lower(), args.head_sha)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
