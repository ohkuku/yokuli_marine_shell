from hashlib import sha256
import json
from pathlib import Path
import unittest

from jsonschema import Draft202012Validator, FormatChecker


ROOT = Path(__file__).resolve().parents[2]
MASTER = ROOT / "docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md"
REFERENCE = ROOT / "docs/reference/wp8"
FIXTURES = REFERENCE / "fixtures"
STAGE = ROOT / "docs/stages/stage-0"
ARCHIVE = ROOT / "docs/archive/pre-launcher-engine"
PREVIOUS_MASTER_SHA256 = "f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0"
MASTER_REVIEWED_SHA = "943d85276e4a042092f87090aa0d23da9a7cbbc6"
SELECTED_START_SHA = "ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7"
STAGE_0_END_SHA = "98121412893d5331b22d4327463794993a4a4eff"


class LauncherStage0ContractTest(unittest.TestCase):
    def test_master_v11_and_baseline_lock_are_mutually_traceable(self):
        self.assertTrue(MASTER.is_file(), "missing normative launcher master specification")
        lock_path = STAGE / "BASELINE_LOCK.json"
        self.assertTrue(lock_path.is_file(), "missing Stage 0 baseline lock")
        lock = json.loads(lock_path.read_text())

        self.assertEqual(MASTER_REVIEWED_SHA, lock["masterReviewedSha"])
        self.assertEqual(SELECTED_START_SHA, lock["actualSelectedStartingSha"])
        self.assertEqual(STAGE_0_END_SHA, lock["stage0EndingSha"])
        self.assertEqual(SELECTED_START_SHA, lock["preExistingImplementationCommit"])
        self.assertEqual("PENDING_HUMAN_REVIEW", lock["approvalStatus"])
        self.assertGreater(len(lock["baselineOverrideReason"]), 30)
        self.assertEqual(PREVIOUS_MASTER_SHA256, lock["previousMasterSpecSha256"])
        self.assertEqual(sha256(MASTER.read_bytes()).hexdigest(), lock["masterSpecSha256"])

        master = MASTER.read_text()
        self.assertIn("version: 1.1", master)
        self.assertIn(PREVIOUS_MASTER_SHA256, master)
        acquisition = master.index("Stage 2.5 — WP8 Reference Acquisition & Human Approval")
        geometry = master.index("Stage 3 — WP Geometry & Start Document")
        self.assertLess(acquisition, geometry)
        stage_three = master[geometry:master.index("Stage 4 —", geometry)]
        self.assertIn("status = HUMAN_REVIEWED", stage_three)

    def test_reference_lab_and_artifact_directories_are_explicit(self):
        for relative in (
            "README.md",
            "screenshots/README.md",
            "golden/README.md",
            "artifacts/README.md",
        ):
            path = REFERENCE / relative
            with self.subTest(path=relative):
                self.assertTrue(path.is_file(), f"missing WP8 reference contract: {relative}")
                text = path.read_text()
                self.assertIn("NOT_YET_MEASURED", text)
                self.assertIn("English translation", text)

    def test_measurement_schema_is_a_valid_draft_2020_12_contract(self):
        schema_path = REFERENCE / "WP8_REFERENCE_MEASUREMENTS.schema.json"
        self.assertTrue(schema_path.is_file(), "missing WP8 measurement JSON schema")
        schema = json.loads(schema_path.read_text())
        Draft202012Validator.check_schema(schema)
        self.assertEqual("https://json-schema.org/draft/2020-12/schema", schema["$schema"])
        self.assertFalse(schema["additionalProperties"])
        self.assertEqual(
            ["schemaVersion", "profileId", "status"],
            schema["required"],
            "NOT_YET_MEASURED must not require invented captures or measurements",
        )
        self.assertIn("captures", schema["properties"])
        self.assertIn("measurementSets", schema["properties"])
        self.assertIn("review", schema["properties"])
        capture_required = schema["$defs"]["capture"]["required"]
        for field in (
            "path",
            "sha256",
            "byteSize",
            "mediaType",
            "dimensions",
            "sourceType",
            "ownershipLicenseNote",
        ):
            self.assertIn(field, capture_required)
        review_required = schema["$defs"]["review"]["required"]
        for field in (
            "reviewedBy",
            "reviewedAtUtc",
            "decision",
            "notes",
            "reviewedMeasurementHash",
        ):
            self.assertIn(field, review_required)

    def test_real_validator_accepts_valid_and_rejects_invalid_fixtures(self):
        schema = json.loads((REFERENCE / "WP8_REFERENCE_MEASUREMENTS.schema.json").read_text())
        validator = Draft202012Validator(schema, format_checker=FormatChecker())
        valid_names = (
            "valid_not_yet_measured.json",
            "valid_measured.json",
            "valid_human_reviewed.json",
        )
        invalid_names = (
            "invalid_missing_capture_hash.json",
            "invalid_human_reviewed_without_reviewer.json",
            "invalid_measured_without_measurements.json",
            "invalid_unknown_property.json",
        )
        for name in valid_names:
            instance = json.loads((FIXTURES / name).read_text())
            with self.subTest(valid=name):
                errors = sorted(validator.iter_errors(instance), key=lambda error: list(error.path))
                self.assertFalse(errors, "\n".join(error.message for error in errors))
        for name in invalid_names:
            instance = json.loads((FIXTURES / name).read_text())
            with self.subTest(invalid=name):
                self.assertTrue(list(validator.iter_errors(instance)), f"fixture unexpectedly valid: {name}")

        reviewed = json.loads((FIXTURES / "valid_human_reviewed.json").read_text())
        canonical_measurements = json.dumps(
            reviewed["measurementSets"],
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        ).encode()
        self.assertEqual(
            sha256(canonical_measurements).hexdigest(),
            reviewed["review"]["reviewedMeasurementHash"],
            "review fixture must sign the canonical measurementSets JSON",
        )

    def test_baseline_reconciliation_dispositions_every_engine_artifact(self):
        reconciliation_path = STAGE / "BASELINE_RECONCILIATION.md"
        self.assertTrue(reconciliation_path.is_file(), "missing baseline reconciliation")
        reconciliation = reconciliation_path.read_text()
        for disposition in (
            "ACCEPTED_CANDIDATE",
            "PROVISIONAL",
            "NON_COMPLIANT_REPLACE",
            "DEFERRED",
        ):
            self.assertIn(disposition, reconciliation)
        for relative in (
            "core/shell-engine/build.gradle.kts",
            "core/shell-engine/src/main/AndroidManifest.xml",
            "geometry/WpStartGeometry.kt",
            "interaction/StartInteractionState.kt",
            "layout/DesktopDocument.kt",
            "layout/DesktopDocumentPolicy.kt",
            "layout/DesktopLayoutEditor.kt",
            "persistence/ShellStores.kt",
            "DesktopDocumentTest.kt",
            "WpStartGeometryTest.kt",
        ):
            with self.subTest(artifact=relative):
                self.assertIn(relative, reconciliation)
        for known_defect in (
            "Android Library",
            "MarineAppId",
            "LaunchTarget",
            "OuterRatio",
            "SeamRatio",
            "UUID.randomUUID",
            "ShellStore",
        ):
            self.assertIn(known_defect, reconciliation)
        self.assertIn("不得视为任何后续 Stage Gate 已通过", reconciliation)

    def test_stage_zero_report_is_complete_and_stops_at_human_gate(self):
        report_path = STAGE / "REPORT.md"
        self.assertTrue(report_path.is_file(), "missing Stage 0 report")
        report = report_path.read_text()
        positions = []
        for section in ("Baseline", "Scope", "Architecture", "Interaction", "Tests", "Hardware", "Stop"):
            heading = f"## {section}"
            self.assertIn(heading, report)
            positions.append(report.index(heading))
        self.assertEqual(sorted(positions), positions)
        self.assertIn("33850770612", report)
        self.assertIn("GitHub Actions: PASS", report)
        self.assertIn("Reference measurements: NOT_YET_MEASURED", report)
        self.assertIn("Samsung square: UNVERIFIED_HARDWARE", report)
        self.assertTrue(
            report.endswith(
                "STOPPED AT STAGE GATE.\nAWAITING HUMAN REVIEW BEFORE NEXT STAGE.\n"
            ),
            "Stage report must end with the exact human-review stop declaration",
        )

    def test_only_master_and_supporting_security_contract_remain_active(self):
        active = {path.name for path in (ROOT / "docs/requirements").glob("*.md")}
        self.assertEqual(
            {
                "LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md",
                "SECRETS_MANAGEMENT_REQUIREMENTS.md",
            },
            active,
        )

    def test_replaced_requirements_and_tdd_history_are_archived(self):
        archive_index = ARCHIVE / "README.md"
        self.assertTrue(archive_index.is_file(), "missing pre-launcher archive index")
        index = archive_index.read_text()
        for filename in (
            "PHASE0_PRODUCT_SURFACE_REQUIREMENTS.md",
            "SHELL_ENGINE_REQUIREMENTS.md",
            "WP8_UI_SYSTEM_REQUIREMENTS.md",
            "UI_FUNCTION_I18N_REQUIREMENTS.md",
            "CHART_SOURCE_IMPORT_REQUIREMENTS.md",
            "CHART_FIRST_PRODUCT_DIRECTION.md",
            "UI_REACTIVE_ARCHITECTURE.md",
            "WP8_UI_PATTERN.md",
            "WP8_THEME_TILE_AUDIT.md",
            "LEGACY_WORKFLOW_AUDIT.md",
            "TDD_LOG_PRE_LAUNCHER_ENGINE.md",
        ):
            with self.subTest(filename=filename):
                self.assertTrue((ARCHIVE / filename).is_file(), f"missing archived file: {filename}")
                self.assertIn(filename, index)

        for stale in (
            "docs/CHART_FIRST_PRODUCT_DIRECTION.md",
            "docs/UI_REACTIVE_ARCHITECTURE.md",
            "docs/WP8_UI_PATTERN.md",
            "docs/LEGACY_WORKFLOW_AUDIT.md",
            "docs/research/WP8_THEME_TILE_AUDIT.md",
            "docs/images",
        ):
            self.assertFalse((ROOT / stale).exists(), f"stale active path remains: {stale}")
        self.assertFalse(
            (ROOT / ".github/scripts/test_chart_source_contract.py").exists(),
            "deferred chart-source plan still runs as a current CI contract",
        )

        current_log = (ROOT / "docs/TDD_LOG.md").read_text()
        self.assertIn("Stage 0 correction", current_log)
        self.assertIn(SELECTED_START_SHA, current_log)
        self.assertNotRegex(current_log, r"(?m)^## Slice ")
        self.assertIn("Marine Shell Final Product-Model Correction", current_log)

    def test_ci_installs_and_runs_real_schema_validator_as_named_gate(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        ci_contract = (ROOT / ".github/scripts/test-ci-contract.sh").read_text()
        requirements = (ROOT / ".github/requirements/stage0-schema.txt").read_text()
        self.assertIn("jsonschema==", requirements)
        self.assertIn("python3 -m pip install --requirement .github/requirements/stage0-schema.txt", workflow)
        self.assertIn("id: launcher_stage0_contract", workflow)
        self.assertIn("python3 .github/scripts/test_launcher_stage0_contract.py", workflow)
        self.assertIn("LAUNCHER_STAGE0_CONTRACT_RESULT", workflow)
        self.assertIn("stage0-schema.txt", ci_contract)
        self.assertIn("launcher_stage0_contract", ci_contract)

    def test_tdd_playbook_uses_master_stage_gates_not_the_old_milestone(self):
        playbook = (ROOT / "docs/TDD_PLAYBOOK.md").read_text()
        self.assertNotIn("Chart-first Shell Foundation 的第一批测试顺序", playbook)
        self.assertIn("Stage 2.5", playbook)
        self.assertIn("STOPPED AT STAGE GATE", playbook)
        self.assertIn("LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md", playbook)


if __name__ == "__main__":
    unittest.main()
