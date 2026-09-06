#!/usr/bin/env python3
"""Compose per-job Yokuli reports into the one artifact Codex consumes."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path


JOBS = ("build", "api34", "api36", "performance")
RAW_ARTIFACTS = {
    "build": "yokuli-os-build-reports-{sha}",
    "api34": "yokuli-os-api34-reports-{sha}",
    "api36": "yokuli-os-api36-reports-{sha}",
    "performance": "stage11-performance-reports-{sha}",
}


def parse_job_results(values: list[str]) -> dict[str, str]:
    results: dict[str, str] = {}
    for value in values:
        job, separator, result = value.partition("=")
        if not separator or job not in JOBS:
            raise ValueError(f"invalid --job-result: {value}")
        results[job] = result.lower()
    return {job: results.get(job, "unknown") for job in JOBS}


def overall_result(results: dict[str, str]) -> str:
    values = set(results.values())
    if "failure" in values or "skipped" in values or "unknown" in values:
        return "FAILURE"
    if "cancelled" in values:
        return "CANCELLED"
    return "SUCCESS"


def sha256_inventory(output: Path) -> None:
    targets = sorted(path for path in output.rglob("*") if path.is_file() and path.name != "SHA256SUMS.txt")
    lines = [
        f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(output).as_posix()}"
        for path in targets
    ]
    (output / "SHA256SUMS.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")


def compose(
    input_root: Path,
    output: Path,
    repository: str,
    workflow: str,
    head_sha: str,
    branch: str,
    run_id: int,
    run_attempt: int,
    results: dict[str, str],
) -> dict:
    if output.exists():
        shutil.rmtree(output)
    jobs_output = output / "jobs"
    jobs_output.mkdir(parents=True)

    reports: dict[str, dict] = {}
    for report_path in sorted(input_root.rglob("job.json")) if input_root.exists() else []:
        try:
            report = json.loads(report_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        job = report.get("job")
        if job not in JOBS or report.get("headSha") != head_sha or job in reports:
            continue
        reports[job] = report
        destination = jobs_output / job
        shutil.copytree(report_path.parent, destination)

    overall = overall_result(results)
    manifest = {
        "schemaVersion": 1,
        "repository": repository,
        "workflow": workflow,
        "headSha": head_sha,
        "sha12": head_sha[:12],
        "branch": branch,
        "runId": run_id,
        "runAttempt": run_attempt,
        "overall": overall,
        "jobs": results,
    }
    output.mkdir(parents=True, exist_ok=True)
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    read_first: list[str] = []
    failure_sections: list[str] = []
    extra_artifacts: list[str] = []
    failure_number = 1
    for job in JOBS:
        result = results[job]
        report = reports.get(job)
        if result == "success":
            continue
        if report is None:
            read_first.append(f"{job}: {result}; per-job report missing")
            failure_sections.extend(
                [
                    f"## F{failure_number} — {job} job report missing",
                    "Owner guess: CI harness",
                    f"Evidence: GitHub job result `{result}`",
                    "EVIDENCE_INSUFFICIENT",
                    "",
                ]
            )
            extra_artifacts.append(RAW_ARTIFACTS[job].format(sha=head_sha))
            failure_number += 1
            continue
        groups = report.get("failureGroups", [])
        if not groups:
            read_first.append(f"{job}: {result}; recognized evidence missing")
            failure_sections.extend(
                [
                    f"## F{failure_number} — {job} evidence insufficient",
                    "Owner guess: CI harness",
                    f"Evidence: `jobs/{job}/JOB_REPORT.md`",
                    "EVIDENCE_INSUFFICIENT",
                    "",
                ]
            )
            extra_artifacts.append(RAW_ARTIFACTS[job].format(sha=head_sha))
            failure_number += 1
            continue
        for group in groups:
            for item in group.get("items", []):
                name = item.get("name", group.get("kind", "Failure"))
                module = item.get("module", "ci")
                evidence = item.get("evidence", f"jobs/{job}/JOB_REPORT.md")
                if str(evidence).startswith("relevant-log-excerpts/"):
                    evidence = f"jobs/{job}/{evidence}"
                raw_evidence = item.get("rawEvidence")
                source = item.get("sourcePath")
                line = item.get("line")
                read_first.append(f"{job}: {group.get('kind', 'failure')} in {module}")
                failure_sections.extend(
                    [
                        f"## F{failure_number} — {group.get('kind', 'Failure')}",
                        f"Owner guess: {module if module != 'ci' else 'CI harness'}",
                        f"Evidence: `{evidence}`",
                    ]
                )
                if source:
                    failure_sections.append(f"Likely file: `{source}{':' + str(line) if line else ''}`")
                if raw_evidence:
                    failure_sections.append(f"Raw evidence: `{raw_evidence}`")
                failure_sections.extend([f"Failure: `{name}`", "", str(item.get("message", "No message.")), ""])
                failure_number += 1

    report_lines = [
        "# Codex CI Repair Report",
        "",
        f"HEAD: {head_sha}",
        f"SHA12: {head_sha[:12]}",
        f"RUN_ID: {run_id}",
        f"ATTEMPT: {run_attempt}",
        f"RESULT: {overall}",
        "",
        "READ FIRST:",
    ]
    if read_first:
        report_lines.extend(f"{index}. {item}" for index, item in enumerate(dict.fromkeys(read_first), 1))
    else:
        report_lines.append("1. All required jobs succeeded; no repair is requested.")
    report_lines.extend(
        [
            "",
            "DO NOT:",
            "- rerun the full suite locally",
            "- re-audit unrelated modules",
            "",
        ]
    )
    report_lines.extend(failure_sections)
    if extra_artifacts:
        report_lines.extend(["## Extra raw evidence required", ""])
        for artifact in dict.fromkeys(extra_artifacts):
            report_lines.extend(["EXTRA_ARTIFACT_REQUIRED:", artifact, ""])
    (output / "CODEX_REPORT.md").write_text("\n".join(report_lines).rstrip() + "\n", encoding="utf-8")

    raw_lines = [
        "# Raw artifacts",
        "",
        "Only download one of these when `CODEX_REPORT.md` explicitly requests it.",
        "",
    ]
    raw_lines.extend(f"- `{template.format(sha=head_sha)}`" for template in RAW_ARTIFACTS.values())
    raw_lines.append(f"- `VERIFIED-yokuli-os-alpha-{head_sha}`")
    (output / "RAW_ARTIFACTS.md").write_text("\n".join(raw_lines) + "\n", encoding="utf-8")
    sha256_inventory(output)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--workflow", required=True)
    parser.add_argument("--head-sha", required=True)
    parser.add_argument("--branch", required=True)
    parser.add_argument("--run-id", type=int, required=True)
    parser.add_argument("--run-attempt", type=int, required=True)
    parser.add_argument("--job-result", action="append", default=[])
    args = parser.parse_args()
    if not re_full_sha(args.head_sha):
        raise SystemExit("--head-sha must be 40 lowercase hexadecimal characters")
    compose(
        args.input.resolve(),
        args.output.resolve(),
        args.repository,
        args.workflow,
        args.head_sha,
        args.branch,
        args.run_id,
        args.run_attempt,
        parse_job_results(args.job_result),
    )
    return 0


def re_full_sha(value: str) -> bool:
    return len(value) == 40 and all(character in "0123456789abcdef" for character in value)


if __name__ == "__main__":
    raise SystemExit(main())
