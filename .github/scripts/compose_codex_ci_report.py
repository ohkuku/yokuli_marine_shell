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


def acceptance_status(value: str) -> str:
    return {
        "success": "PASS",
        "failure": "FAIL",
        "cancelled": "FAIL",
        "skipped": "BLOCKED",
        "unknown": "NOT_RUN",
    }.get(value.lower(), "BLOCKED")


def combine_acceptance(statuses: list[str]) -> str:
    values = set(statuses)
    if "FAIL" in values:
        return "FAIL"
    if "BLOCKED" in values:
        return "BLOCKED"
    if "NOT_RUN" in values:
        return "NOT_RUN"
    return "PASS"


def read_json(path: Path) -> dict | None:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return value if isinstance(value, dict) else None


def attached_check(output: Path, job: str, filename: str, identifier: str, job_result: str) -> str:
    if job_result != "success":
        return acceptance_status(job_result)
    payload = read_json(output / "jobs" / job / filename)
    if payload is None:
        return "BLOCKED"
    for check in payload.get("checks", []):
        if check.get("id") == identifier and check.get("status") in {"PASS", "FAIL", "BLOCKED", "NOT_RUN"}:
            return check["status"]
    return "BLOCKED"


def write_w16_reports(output: Path, results: dict[str, str], event_name: str) -> None:
    migration_payloads = [
        read_json(output / "jobs" / "build" / "w16-migration-build.json"),
        read_json(output / "jobs" / "api34" / "w16-migration-api34.json"),
    ]
    migration_checks: list[dict] = []
    migration_statuses: list[str] = []
    for job, payload in zip(("build", "api34"), migration_payloads):
        if results[job] != "success":
            migration_statuses.append(acceptance_status(results[job]))
            continue
        if payload is None:
            migration_statuses.append("BLOCKED")
            continue
        status = payload.get("status", "BLOCKED")
        migration_statuses.append(status if status in {"PASS", "FAIL", "BLOCKED", "NOT_RUN"} else "BLOCKED")
        migration_checks.extend(payload.get("checks", []))
    migration_status = combine_acceptance(migration_statuses)
    migration = {
        "schemaVersion": 1,
        "workPackage": "W16",
        "status": migration_status,
        "checks": migration_checks,
        "sourceReports": [
            "jobs/build/w16-migration-build.json",
            "jobs/api34/w16-migration-api34.json",
        ],
    }
    (output / "MIGRATION_REPORT.json").write_text(
        json.dumps(migration, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    migration_lines = [
        "# W16 迁移报告 / Migration report",
        "",
        f"状态 / Status: **{migration_status}**",
        "",
        "| 证据 / Evidence | 状态 / Status |",
        "|---|---|",
    ]
    migration_lines.extend(
        f"| `{item.get('id', 'unknown')}` | {item.get('status', 'BLOCKED')} |" for item in migration_checks
    )
    (output / "MIGRATION_REPORT.md").write_text("\n".join(migration_lines) + "\n", encoding="utf-8")

    machine_items = [
        {
            "id": "build_contract_unit_lint_release",
            "nameZh": "合同、单测、Lint、Debug/Release 构建",
            "nameEn": "Contracts, unit tests, lint, Debug/Release builds",
            "status": acceptance_status(results["build"]),
            "evidence": "jobs/build/JOB_REPORT.md",
        },
        {
            "id": "release_surface_audit",
            "nameZh": "Release 产品表面审计",
            "nameEn": "Release product-surface audit",
            "status": attached_check(output, "build", "w16-build-evidence.json", "release_surface_audit", results["build"]),
            "evidence": "jobs/build/w16-build-evidence.json",
        },
        {
            "id": "nmea_high_rate_soak",
            "nameZh": "四连接、100 Hz、虚拟 30 分钟有界数据 soak",
            "nameEn": "Four-connection, 100 Hz, virtual 30-minute bounded data soak",
            "status": attached_check(output, "build", "w16-build-evidence.json", "nmea_high_rate_virtual_soak", results["build"]),
            "evidence": "jobs/build/w16-build-evidence.json",
        },
        {
            "id": "google_maps_build_configuration",
            "nameZh": "Google Maps 密钥构建注入（仅配置，不代表图块就绪）",
            "nameEn": "Google Maps key build injection (configuration only, not tile readiness)",
            "status": attached_check(output, "build", "w16-build-evidence.json", "maps_build_configuration", results["build"]),
            "evidence": "jobs/build/google-maps-configuration.json",
        },
        {
            "id": "api34_device_stories",
            "nameZh": "API 34 全部活动 App 与适配器设备故事",
            "nameEn": "API 34 active-app and adapter device stories",
            "status": acceptance_status(results["api34"]),
            "evidence": "jobs/api34/JOB_REPORT.md",
        },
        {
            "id": "chart_process_restore",
            "nameZh": "Chart 外部 force-stop 恢复",
            "nameEn": "Chart external force-stop restore",
            "status": attached_check(output, "api34", "w16-api34-evidence.json", "chart_process_restore", results["api34"]),
            "evidence": "jobs/api34/w16-api34-evidence.json",
        },
        {
            "id": "nmea_process_restore",
            "nameZh": "NMEA 策略恢复且不复活实时值",
            "nameEn": "NMEA policy restore without resurrecting live values",
            "status": attached_check(output, "api34", "w16-api34-evidence.json", "nmea_process_restore", results["api34"]),
            "evidence": "jobs/api34/w16-api34-evidence.json",
        },
        {
            "id": "migration_fixtures",
            "nameZh": "Launcher、地图、海图库迁移夹具",
            "nameEn": "Launcher, map, and chart-library migration fixtures",
            "status": migration_status,
            "evidence": "MIGRATION_REPORT.md",
        },
        {
            "id": "api36_compatibility",
            "nameZh": "API 36 兼容性",
            "nameEn": "API 36 compatibility",
            "status": acceptance_status(results["api36"]),
            "evidence": "jobs/api36/JOB_REPORT.md",
        },
        {
            "id": "emulator_performance_trend",
            "nameZh": "模拟器性能趋势（不冒充硬件帧率）",
            "nameEn": "Emulator performance trend (not a hardware frame-rate claim)",
            "status": acceptance_status(results["performance"]),
            "evidence": "jobs/performance/JOB_REPORT.md",
        },
    ]
    machine_status = combine_acceptance([item["status"] for item in machine_items])
    manual_status = machine_status if event_name == "workflow_dispatch" else "NOT_RUN"
    operator_items = [
        {
            "id": "manual_full_actions_run",
            "nameZh": "人工触发同一完整 Actions 门禁",
            "nameEn": "Manually dispatched full Actions gate",
            "status": manual_status,
            "evidence": f"event={event_name}",
        },
    ]
    physical_items = [
        ("samsung_square_device", "三星方屏真机", "Samsung square physical device"),
        ("gnss_permission_background", "真实 GNSS 权限与后台行为", "Real GNSS permission and background behavior"),
        ("boat_tcp_udp", "真实船载 TCP/UDP", "Real boat TCP/UDP"),
        ("google_satellite_runtime", "Google Satellite 真机网络、授权与图块加载", "Google Satellite device network, authorization, and tile loading"),
        ("large_real_mbtiles", "大型真实 MBTiles 文件夹", "Large real MBTiles folders"),
    ]
    physical = [
        {"id": identifier, "nameZh": zh, "nameEn": en, "status": "NOT_RUN", "evidence": "HUMAN_OR_PHYSICAL_EVIDENCE_REQUIRED"}
        for identifier, zh, en in physical_items
    ]
    ledger = {
        "schemaVersion": 1,
        "workPackage": "W16",
        "machineStatus": machine_status,
        "manualRunStatus": manual_status,
        "humanPhysicalStatus": "NOT_RUN",
        "decision": "AWAITING_HUMAN_REVIEW" if machine_status == "PASS" else "MACHINE_GATE_NOT_PASSED",
        "items": {
            "machine": machine_items,
            "operator": operator_items,
            "humanPhysical": physical,
        },
    }
    (output / "FINAL_ACCEPTANCE_LEDGER.json").write_text(
        json.dumps(ledger, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    ledger_lines = [
        "# W16 最终验收台账 / Final acceptance ledger",
        "",
        f"机器门禁 / Machine gate: **{machine_status}**",
        f"人工触发完整运行 / Manual full run: **{manual_status}**",
        "人工与物理设备 / Human and physical: **NOT_RUN**",
        f"决策 / Decision: **{ledger['decision']}**",
        "",
        "| 项目 / Item | 状态 / Status | 证据 / Evidence |",
        "|---|---|---|",
    ]
    for section in (machine_items, operator_items, physical):
        ledger_lines.extend(
            f"| {item['nameZh']} / {item['nameEn']} | {item['status']} | `{item['evidence']}` |" for item in section
        )
    (output / "FINAL_ACCEPTANCE_LEDGER.md").write_text("\n".join(ledger_lines) + "\n", encoding="utf-8")


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
    event_name: str = "unknown",
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
        "eventName": event_name,
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
    write_w16_reports(output, results, event_name)
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
    parser.add_argument("--event-name", default="unknown")
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
        args.event_name,
    )
    return 0


def re_full_sha(value: str) -> bool:
    return len(value) == 40 and all(character in "0123456789abcdef" for character in value)


if __name__ == "__main__":
    raise SystemExit(main())
