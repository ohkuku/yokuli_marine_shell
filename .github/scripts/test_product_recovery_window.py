#!/usr/bin/env python3
import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/android.yml"
PHASE = ROOT / "docs/phases/base-apps-human-reset"


class ProductRecoveryWindowTest(unittest.TestCase):
    def test_human_override_and_protected_test_families_are_frozen(self):
        lock = json.loads((PHASE / "BASELINE_LOCK.json").read_text(encoding="utf-8"))
        policy = (PHASE / "PRODUCT_RECOVERY_WINDOW.md").read_text(encoding="utf-8")
        self.assertEqual("PRODUCT_RECOVERY_WINDOW", lock["status"])
        self.assertFalse(lock["ciGreenMeansProductAccepted"])
        self.assertFalse(lock["legacyPresentationContractsAuthoritative"])
        self.assertEqual(
            "4f935c31240ae886b260ab6b80fb5439bffcb295b8a9fb404a4939b919068b54",
            lock["implementationContractSha256"],
        )
        superseded = (ROOT / "docs/SUPERSEDED_PRODUCT_TESTS.md").read_text(encoding="utf-8")
        for required in ("chart_c12_contract", "chart_library_cl07_contract", "osr_w12_contract", "DATA-01–09"):
            self.assertIn(required, superseded)
        for required in (
            "domain model 与数学", "NMEA parser", "persistence", "MBTiles/SAF",
            "runtime lifecycle", "Shell Engine", "Kotlin/JVM tests",
        ):
            self.assertIn(required, policy)

    def test_ci_uses_an_explicit_helper_allowlist(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        helper = (ROOT / ".github/scripts/run_ci_helper_tests.sh").read_text(encoding="utf-8")
        self.assertNotIn("unittest discover .github/scripts 'test_*.py'", workflow)
        self.assertIn("run_ci_helper_tests.sh", workflow)
        for required in (
            "test_ci_helpers.py", "test_codex_ci_report.py",
            "test_google_maps_configuration_evidence.py",
        ):
            self.assertIn(required, helper)
        for forbidden in ("test_chart_c", "test_chart_library_cl", "test_osr_w04", "test_osr_w09", "test_osr_w12"):
            self.assertNotIn(forbidden, helper)

        protected_units = (ROOT / ".github/scripts/run_product_recovery_unit_tests.sh").read_text(encoding="utf-8")
        for required in (":core:model:testDebugUnitTest", ":core:design:testDebugUnitTest",
                         ":core:map-domain:test", ":core:marine-data:test", ":core:navigation-domain:test",
                         ":adapter:map-storage:testDebugUnitTest", ":adapter:map-offline:testDebugUnitTest"):
            self.assertIn(required, protected_units)
        for forbidden in (":feature:chart", ":feature:chart-library", ":feature:data", ":feature:navigation"):
            self.assertNotIn(forbidden, protected_units)

        legacy_final = (ROOT / ".github/scripts/run_marine_shell_final_gate.sh").read_text(encoding="utf-8")
        self.assertNotIn("unittest discover", legacy_final)
        self.assertNotIn("validate_stage11_fidelity.py", legacy_final)
        self.assertIn("HUMAN_ACCEPTANCE=PENDING", legacy_final)

    def test_rejected_presentation_gates_are_explicitly_retired(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        retired = (
            "nmea_sources_p2_contract", "nmea_sources_p4_contract", "nmea_sources_p5_contract",
            "nmea_sources_p6_contract", "nmea_sources_p7_contract",
            "launcher_stage1_contract", "launcher_stage5_contract", "launcher_stage6_contract",
            "launcher_stage7_contract", "launcher_stage8_contract", "launcher_stage9_contract",
            "launcher_stage10_contract", "shell_app_contract",
            "chart_c12_contract", "chart_library_cl07_contract", "chart_library_cl10_contract",
            "chart_library_cl08_contract", "chart_library_cl09_contract", "chart_library_cl12_contract",
            "osr_w02_contract", "osr_w04_contract", "osr_w09_contract", "osr_w12_contract",
            "osr_w03_contract",
            "osr_w13_contract", "osr_w14_contract", "osr_w15_contract", "osr_w16_contract",
            "osr_w16_build_evidence", "launcher_stage11_contract", "chart_shell_ux_correction",
        )
        for step_id in retired:
            block = workflow.split(f"id: {step_id}", 1)[1].split("- name:", 1)[0]
            self.assertIn("if: false", block, step_id)

        cumulative = workflow.split("id: osr_w01_contract", 1)[1].split("- name:", 1)[0]
        self.assertNotIn("OSR_W03_RESULT", cumulative)

    def test_core_and_runtime_gates_remain_active(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        active = (
            "nmea_sources_p1_contract", "nmea_sources_p3_contract",
            "launcher_stage2_contract", "launcher_stage25_contract", "launcher_stage3_contract",
            "launcher_stage4_contract", "chart_library_cl11_contract", "osr_w01_only_contract",
            "legacy_mbtiles_compatibility",
            "osr_w08_contract", "osr_w10_contract", "osr_w11_contract",
            "release_surface_audit", "unit_tests", "lint", "assemble",
        )
        for step_id in active:
            block = workflow.split(f"id: {step_id}", 1)[1].split("- name:", 1)[0]
            self.assertNotIn("if: false", block, step_id)

    def test_artifacts_do_not_claim_human_acceptance(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("YOKULI-OS-DEBUG", workflow)
        self.assertIn("HUMAN-ACCEPTANCE-PENDING", workflow)
        self.assertNotIn("VERIFIED-yokuli-os-alpha", workflow)
        self.assertNotIn("Fully verified Yokuli OS", workflow)

        composer = (ROOT / ".github/scripts/compose_codex_ci_report.py").read_text(encoding="utf-8")
        release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
        self.assertIn("YOKULI-OS-DEBUG-", composer)
        self.assertNotIn("VERIFIED-yokuli-os-alpha", composer)
        self.assertIn("PRODUCT_RECOVERY: signed product releases resume only", release)

    def test_device_and_performance_gates_do_not_reintroduce_rejected_ui(self):
        device = (ROOT / ".github/scripts/run_device_tests.sh").read_text(encoding="utf-8")
        workflow = WORKFLOW.read_text(encoding="utf-8")
        for required in (":adapter:marine-data-android:connectedDebugAndroidTest",
                         ":adapter:map-offline:connectedDebugAndroidTest",
                         "backAtShellDesktopNeverFinishesHost", "shellActivityIsPortraitOnly"):
            self.assertIn(required, device)
        for forbidden in (":feature:chart-library:connectedDebugAndroidTest",
                          ":feature:navigation:connectedDebugAndroidTest",
                          "chartTileOpensBrowseOnlySurfaceAndSystemBackReturnsToStart"):
            self.assertNotIn(forbidden, device)
        self.assertIn("--profile product-recovery", workflow)


if __name__ == "__main__":
    unittest.main()
