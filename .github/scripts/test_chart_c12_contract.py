from pathlib import Path
import json
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ChartC12DeliveryContract(unittest.TestCase):
    def text(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_all_device_mode_really_runs_shell_offline_and_storage(self):
        runner = self.text(".github/scripts/run_device_tests.sh")
        for task in (
            ":app-shell:connectedStandaloneDebugAndroidTest",
            ":adapter:map-offline:connectedDebugAndroidTest",
            ":adapter:map-storage:connectedDebugAndroidTest",
        ):
            self.assertIn(task, runner)
        self.assertIn('all_device_tasks=(', runner)
        self.assertIn('gradle_args+=("${all_device_tasks[@]}")', runner)
        self.assertIn(
            "android.testInstrumentationRunnerArguments.notClass="
            "com.yokuli.marine.shell.ChartC12ProcessRestartProbeTest",
            runner,
        )

    def test_ci_names_the_c12_gate_and_uploads_all_device_reports(self):
        workflow = self.text(".github/workflows/android.yml")
        self.assertIn("id: chart_c12_contract", workflow)
        self.assertIn("python3 .github/scripts/test_chart_c12_contract.py", workflow)
        self.assertIn("CHART_C12_CONTRACT_RESULT", workflow)
        for report_root in (
            "app-shell/build/outputs/androidTest-results/**",
            "adapter/map-offline/build/outputs/androidTest-results/**",
            "adapter/map-storage/build/outputs/androidTest-results/**",
        ):
            self.assertIn(report_root, workflow)

    def test_verified_alpha_waits_for_every_required_gate(self):
        workflow = self.text(".github/workflows/android.yml")
        self.assertIn("needs: [build, integration, api-compatibility, stage11-performance]", workflow)
        self.assertIn("VERIFIED-yokuli-os-alpha-${{ github.sha }}", workflow)
        self.assertIn("MBTiles + Room + Shell", workflow)

    def test_j01_to_j06_are_real_instrumented_journeys(self):
        journey = self.text(
            "app-shell/src/androidTest/java/com/yokuli/marine/shell/ChartC12JourneyTest.kt"
        )
        for journey_id in ("J01", "J02", "J03", "J04", "J05", "J06"):
            self.assertIn(f"fun journey{journey_id}", journey)
        for boundary in (
            "navigationActive",
            "OfflineMapInstanceMetrics",
        ):
            self.assertIn(boundary, journey)
        process_driver = self.text(".github/scripts/run_c12_process_restore.sh")
        self.assertIn("am force-stop com.yokuli.marine", process_driver)
        self.assertNotIn("ActivityScenario.recreate", process_driver)
        self.assertIn("seedDurableStateForExternalProcessRestart", process_driver)
        self.assertIn("verifyDurableStateAfterExternalProcessRestart", process_driver)

    def test_final_gate_records_process_restart_and_release_manifest_audit(self):
        gate = self.text(".github/scripts/run_marine_shell_final_gate.sh")
        self.assertIn("run_c12_process_restore.sh", gate)
        self.assertIn("test-release-product-surface.sh", gate)
        self.assertIn("CHART_C12_GATE=CORE_MACHINE_READY", gate)

    def test_temporary_refinement_side_paths_are_removed(self):
        for path in (
            ".github/workflows/refinement-source-snapshot.yml",
            ".github/workflows/refinement-jvm.yml",
            ".github/workflows/refinement-patch.yml",
            ".github/refinement/apply.py",
        ):
            self.assertFalse((ROOT / path).exists(), path)

    def test_final_report_and_lock_keep_human_hardware_truth_separate(self):
        report = self.text("docs/phases/chart-wp8-refinement/c12/REPORT.md")
        lock = json.loads(
            self.text("docs/phases/chart-wp8-refinement/c12/BASELINE_LOCK.json")
        )
        self.assertIn("CORE_MACHINE_READY", report)
        self.assertIn("HUMAN_ACCEPTED: PENDING", report)
        self.assertIn("SAMSUNG_SQUARE_DEVICE: PENDING_OWNER", report)
        self.assertEqual("CORE_MACHINE_READY", lock["machineStatus"])
        self.assertEqual("PENDING", lock["humanAccepted"])
        self.assertEqual("PENDING_OWNER", lock["samsungSquareDevice"])
        self.assertEqual([], lock["unresolvedP0P1"])


if __name__ == "__main__":
    unittest.main()
