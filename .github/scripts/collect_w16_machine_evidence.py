#!/usr/bin/env python3
"""Collect exact W16 machine evidence without promoting hardware claims."""

from __future__ import annotations

import argparse
import hashlib
import json
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


ALLOWED_STATUSES = {"PASS", "FAIL", "BLOCKED", "NOT_RUN"}


@dataclass(frozen=True)
class TestRequirement:
    identifier: str
    class_name: str
    test_name: str
    source: str
    migration: bool = False


JVM_REQUIREMENTS = (
    TestRequirement(
        "launcher_alias_collapse",
        "com.yokuli.shell.engine.LauncherProductMigrationTest",
        "twoLegacyDataTilesCollapseToTheEarliestRankAndKeepItsIdentitySizeAndGroup",
        "core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/LauncherProductMigrationTest.kt",
        True,
    ),
    TestRequirement(
        "launcher_migration_idempotence",
        "com.yokuli.shell.engine.LauncherProductMigrationTest",
        "migrationIsIdempotentAndDoesNotTouchSpacersOrUnrelatedTiles",
        "core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/LauncherProductMigrationTest.kt",
        True,
    ),
    TestRequirement(
        "launcher_store_schema_migration",
        "com.yokuli.shell.storage.ProtoDataStoreLauncherPersistenceTest",
        "legacySchemaIsMigratedRecordedAndCommitted",
        "adapter/shell-storage/src/test/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistenceTest.kt",
        True,
    ),
    TestRequirement(
        "launcher_product_migration_transaction",
        "com.yokuli.shell.storage.ProtoDataStoreLauncherPersistenceTest",
        "productMigrationVersionAndCollapsedTileAreCommittedAtomically",
        "adapter/shell-storage/src/test/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistenceTest.kt",
        True,
    ),
    TestRequirement(
        "final_product_composition_migration",
        "com.yokuli.marine.shell.YokuliProductModelTest",
        "currentCompositionRootMigratesLegacyMarineTilesOnlyAfterDataHasARealHost",
        "app-shell/src/test/java/com/yokuli/marine/shell/YokuliProductModelTest.kt",
        True,
    ),
    TestRequirement(
        "legacy_settings_resource_redirect",
        "com.yokuli.marine.shell.YokuliProductModelTest",
        "legacySettingsResourcePageFallsBackToPreferencesOverview",
        "app-shell/src/test/java/com/yokuli/marine/shell/YokuliProductModelTest.kt",
        True,
    ),
    TestRequirement(
        "legacy_chart_display_selection",
        "com.yokuli.marine.feature.chart.ChartDisplayCoordinatorTest",
        "legacy active package migrates once and explicit none is never stolen back",
        "feature/chart/src/test/java/com/yokuli/marine/feature/chart/ChartDisplayCoordinatorTest.kt",
        True,
    ),
    TestRequirement(
        "legacy_map_session_read",
        "com.yokuli.marine.map.storage.MapProtoMapperTest",
        "legacy session keeps its package id and exposes display migration as pending",
        "adapter/map-storage/src/test/java/com/yokuli/marine/map/storage/MapProtoMapperTest.kt",
        True,
    ),
    TestRequirement(
        "nmea_high_rate_virtual_soak",
        "com.yokuli.marine.data.runtime.NmeaInboundPipelineSoakTest",
        "virtualThirtyMinuteHundredHertzFourConnectionSoakRemainsBounded",
        "core/marine-data/src/test/kotlin/com/yokuli/marine/data/runtime/NmeaInboundPipelineSoakTest.kt",
    ),
)


API34_REQUIREMENTS = (
    TestRequirement(
        "map_database_v1_v2",
        "com.yokuli.marine.map.storage.RoomMapPersistenceTest",
        "versionOnePlacesMigrateWithoutDestructiveFallback",
        "adapter/map-storage/src/androidTest/java/com/yokuli/marine/map/storage/RoomMapPersistenceTest.kt",
        True,
    ),
    TestRequirement(
        "map_database_v2_v3",
        "com.yokuli.marine.map.storage.RoomMapPersistenceTest",
        "versionTwoRoutesMigrateWithoutInventingSpeedOrLosingIdentity",
        "adapter/map-storage/src/androidTest/java/com/yokuli/marine/map/storage/RoomMapPersistenceTest.kt",
        True,
    ),
    TestRequirement(
        "map_database_v3_v4",
        "com.yokuli.marine.map.storage.RoomMapPersistenceTest",
        "versionThreeAddsEmptySegmentedTrackAndImportRecordTablesWithoutTouchingExistingRows",
        "adapter/map-storage/src/androidTest/java/com/yokuli/marine/map/storage/RoomMapPersistenceTest.kt",
        True,
    ),
    TestRequirement(
        "chart_catalog_v1_v2",
        "com.yokuli.marine.chart.library.android.ChartCatalogMigrationTest",
        "catalogV1MigratesToV2WithoutDestructiveReset",
        "adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/ChartCatalogMigrationTest.kt",
        True,
    ),
    TestRequirement(
        "chart_catalog_v2_v3",
        "com.yokuli.marine.chart.library.android.ChartCatalogMigrationTest",
        "catalogV2MigratesToV3WithManagedCopyRelations",
        "adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/ChartCatalogMigrationTest.kt",
        True,
    ),
    TestRequirement(
        "chart_catalog_full_chain",
        "com.yokuli.marine.chart.library.android.ChartCatalogMigrationTest",
        "catalogV1MigratesThroughV3WithoutDestructiveReset",
        "adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/ChartCatalogMigrationTest.kt",
        True,
    ),
)


JVM_RESULT_ROOTS = (
    "core/marine-data/build/test-results",
    "core/shell-engine/build/test-results",
    "adapter/shell-storage/build/test-results",
    "adapter/map-storage/build/test-results",
    "feature/chart/build/test-results",
    "app-shell/build/test-results",
)
API34_RESULT_ROOTS = (
    "adapter/map-storage/build/outputs/androidTest-results",
    "adapter/chart-library-android/build/outputs/androidTest-results",
)
BUILD_STEPS = (
    ("unit_test_gate", "unit-tests"),
    ("lint_gate", "lint"),
    ("debug_release_assembly", "assemble"),
    ("release_surface_audit", "launcher-stage1-contract"),
    ("w16_static_contract", "osr-w16-contract"),
    ("maps_build_configuration", "google-maps-evidence"),
)
PROCESS_LOG_CHECKS = (
    (
        "chart_process_restore",
        "build/ci-c12-process-restore.log",
        "C12 external force-stop persistence probe passed",
    ),
    (
        "nmea_process_restore",
        "build/ci-nmea-sources-process-restore.log",
        "NMEA Sources P6 policy persisted and live values stayed process-local",
    ),
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def overall_status(checks: list[dict], required_only: bool = False) -> str:
    selected = [item for item in checks if not required_only or item.get("required", True)]
    statuses = {item["status"] for item in selected}
    if "FAIL" in statuses:
        return "FAIL"
    if "BLOCKED" in statuses:
        return "BLOCKED"
    if "NOT_RUN" in statuses:
        return "NOT_RUN"
    return "PASS"


def load_cases(repo_root: Path, roots: tuple[str, ...]) -> tuple[dict[tuple[str, str], list[dict]], list[str]]:
    cases: dict[tuple[str, str], list[dict]] = {}
    warnings: list[str] = []
    for relative_root in roots:
        root = repo_root / relative_root
        if not root.exists():
            continue
        for path in sorted(root.rglob("*.xml")):
            try:
                xml_root = ET.parse(path).getroot()
            except (OSError, ET.ParseError) as error:
                warnings.append(f"{path.relative_to(repo_root).as_posix()}: {error}")
                continue
            for case in xml_root.iter("testcase"):
                class_name = case.attrib.get("classname", "")
                test_name = case.attrib.get("name", "")
                if not class_name or not test_name:
                    continue
                outcome = "PASS"
                if case.find("failure") is not None or case.find("error") is not None:
                    outcome = "FAIL"
                elif case.find("skipped") is not None:
                    outcome = "NOT_RUN"
                cases.setdefault((class_name, test_name), []).append(
                    {
                        "status": outcome,
                        "report": path.relative_to(repo_root).as_posix(),
                    },
                )
    return cases, warnings


def test_checks(repo_root: Path, requirements: tuple[TestRequirement, ...], roots: tuple[str, ...]) -> tuple[list[dict], list[str]]:
    cases, warnings = load_cases(repo_root, roots)
    checks: list[dict] = []
    for requirement in requirements:
        matches = cases.get((requirement.class_name, requirement.test_name), [])
        status = "NOT_RUN" if not matches else overall_status(matches)
        source = repo_root / requirement.source
        checks.append(
            {
                "id": requirement.identifier,
                "kind": "migration" if requirement.migration else "soak",
                "status": status,
                "required": True,
                "test": f"{requirement.class_name}#{requirement.test_name}",
                "reports": [item["report"] for item in matches],
                "source": requirement.source,
                "sourceSha256": sha256(source) if source.is_file() else None,
            },
        )
    return checks, warnings


def build_checks(repo_root: Path, allow_unconfigured_maps: bool) -> list[dict]:
    checks: list[dict] = []
    for identifier, label in BUILD_STEPS:
        path = repo_root / "build" / "codex-ci" / "steps" / f"{label}.json"
        if not path.is_file():
            status = "NOT_RUN"
        else:
            try:
                outcome = json.loads(path.read_text(encoding="utf-8")).get("outcome")
                status = "PASS" if outcome == "success" else "FAIL"
            except (OSError, json.JSONDecodeError):
                status = "BLOCKED"
        checks.append(
            {
                "id": identifier,
                "kind": "build",
                "status": status,
                "required": True,
                "evidence": path.relative_to(repo_root).as_posix(),
            },
        )

    maps_path = repo_root / "build" / "google-maps" / "configuration.json"
    maps = next(item for item in checks if item["id"] == "maps_build_configuration")
    maps["required"] = not allow_unconfigured_maps
    if maps["status"] == "PASS":
        try:
            payload = json.loads(maps_path.read_text(encoding="utf-8"))
            consistent = payload.get("configurationConsistent") is True
            configured = payload.get("buildConfigConfigured") is True and payload.get("manifestConfigured") is True
            maps["status"] = "PASS" if consistent and configured else ("NOT_RUN" if allow_unconfigured_maps and consistent else "FAIL")
            maps["meaning"] = payload.get("meaning")
            maps["applicationId"] = payload.get("applicationId")
        except (OSError, json.JSONDecodeError):
            maps["status"] = "BLOCKED"
        maps["evidence"] = maps_path.relative_to(repo_root).as_posix()
    return checks


def api34_checks(repo_root: Path) -> list[dict]:
    checks: list[dict] = []
    for identifier, relative_path, marker in PROCESS_LOG_CHECKS:
        path = repo_root / relative_path
        if not path.is_file():
            status = "NOT_RUN"
        else:
            try:
                status = "PASS" if marker in path.read_text(encoding="utf-8", errors="replace") else "FAIL"
            except OSError:
                status = "BLOCKED"
        checks.append(
            {
                "id": identifier,
                "kind": "process_restore",
                "status": status,
                "required": True,
                "evidence": relative_path,
            },
        )
    return checks


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def collect(repo_root: Path, scope: str, allow_unconfigured_maps: bool = False) -> tuple[dict, dict]:
    if scope == "build":
        test_evidence, warnings = test_checks(repo_root, JVM_REQUIREMENTS, JVM_RESULT_ROOTS)
        checks = test_evidence + build_checks(repo_root, allow_unconfigured_maps)
    else:
        test_evidence, warnings = test_checks(repo_root, API34_REQUIREMENTS, API34_RESULT_ROOTS)
        checks = test_evidence + api34_checks(repo_root)
    migration_checks = [item for item in checks if item["kind"] == "migration"]
    evidence = {
        "schemaVersion": 1,
        "workPackage": "W16",
        "scope": scope,
        "status": overall_status(checks, required_only=True),
        "checks": checks,
        "warnings": warnings,
    }
    migration = {
        "schemaVersion": 1,
        "workPackage": "W16",
        "scope": scope,
        "status": overall_status(migration_checks),
        "checks": migration_checks,
        "warnings": warnings,
    }
    return evidence, migration


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--scope", choices=("build", "api34"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--migration-output", type=Path, required=True)
    parser.add_argument("--allow-unconfigured-maps", action="store_true")
    parser.add_argument("--require-complete", action="store_true")
    args = parser.parse_args()
    evidence, migration = collect(args.repo_root.resolve(), args.scope, args.allow_unconfigured_maps)
    write_json(args.output, evidence)
    write_json(args.migration_output, migration)
    if args.require_complete and evidence["status"] != "PASS":
        failed = ", ".join(item["id"] for item in evidence["checks"] if item["required"] and item["status"] != "PASS")
        print(f"::error title=W16 {args.scope} evidence incomplete::{failed}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
