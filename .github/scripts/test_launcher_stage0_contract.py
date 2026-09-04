from hashlib import sha256
import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
MASTER = ROOT / "docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md"
REFERENCE = ROOT / "docs/reference/wp8"
ARCHIVE = ROOT / "docs/archive/pre-launcher-engine"


class LauncherStage0ContractTest(unittest.TestCase):
    def test_master_spec_is_frozen_verbatim(self):
        self.assertTrue(MASTER.is_file(), "missing normative launcher master specification")
        self.assertEqual(
            "f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0",
            sha256(MASTER.read_bytes()).hexdigest(),
            "the imported normative specification must remain byte-for-byte traceable",
        )

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

    def test_measurement_schema_is_machine_readable_and_requires_provenance(self):
        schema_path = REFERENCE / "WP8_REFERENCE_MEASUREMENTS.schema.json"
        self.assertTrue(schema_path.is_file(), "missing WP8 measurement JSON schema")
        schema = json.loads(schema_path.read_text())
        self.assertEqual("https://json-schema.org/draft/2020-12/schema", schema["$schema"])
        self.assertFalse(schema["additionalProperties"])
        for required in ("schemaVersion", "profileId", "status", "provenance", "viewport", "measurements"):
            self.assertIn(required, schema["required"])
        self.assertEqual(
            ["NOT_YET_MEASURED", "MEASURED", "HUMAN_REVIEWED"],
            schema["properties"]["status"]["enum"],
        )
        measurement_fields = schema["$defs"]["measurements"]["properties"]
        for field in (
            "outerInsetsPx",
            "smallTileBoundsPx",
            "mediumTileBoundsPx",
            "wideTileBoundsPx",
            "seamPx",
            "statusStripHeightPx",
            "titleBaselinePx",
            "glyphOpticalBoxPx",
            "longPressMillis",
            "pageSettleMillis",
            "appOpenMillis",
            "pressScale",
            "pressTiltDegrees",
        ):
            self.assertIn(field, measurement_fields)
        self.assertIn("captureFiles", schema["$defs"]["provenance"]["required"])

    def test_only_master_and_supporting_security_contract_remain_active(self):
        active = {
            path.name
            for path in (ROOT / "docs/requirements").glob("*.md")
        }
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
        self.assertIn("Stage 0", current_log)
        self.assertIn("ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7", current_log)
        self.assertNotIn("## Slice ", current_log)

    def test_ci_exposes_the_stage_zero_contract_as_a_named_gate(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        ci_contract = (ROOT / ".github/scripts/test-ci-contract.sh").read_text()
        self.assertIn("id: launcher_stage0_contract", workflow)
        self.assertIn("python3 .github/scripts/test_launcher_stage0_contract.py", workflow)
        self.assertIn("LAUNCHER_STAGE0_CONTRACT_RESULT", workflow)
        self.assertIn("launcher_stage0_contract", ci_contract)

    def test_tdd_playbook_uses_master_stage_gates_not_the_old_milestone(self):
        playbook = (ROOT / "docs/TDD_PLAYBOOK.md").read_text()
        self.assertNotIn("Chart-first Shell Foundation 的第一批测试顺序", playbook)
        self.assertIn("STOPPED AT STAGE GATE", playbook)
        self.assertIn("LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md", playbook)


if __name__ == "__main__":
    unittest.main()
