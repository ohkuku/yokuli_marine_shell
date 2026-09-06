from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ChartLibraryCl12ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_current_product_truth_is_five_apps_but_default_start_stays_two(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        readme = self.read("README.md")
        installed = re.findall(r"catalogContribution\s*=\s*([A-Z][A-Za-z]+ShellContribution)", graph)
        self.assertEqual(
            [
                "ChartShellContribution",
                "SettingsShellContribution",
                "NmeaInputShellContribution",
                "DataSourcesShellContribution",
                "ChartLibraryShellContribution",
            ],
            installed,
        )
        default = graph[graph.index("val defaultStartDocument"):]
        self.assertEqual(2, default.count("TilePlacement("))
        self.assertIn("Chart Library 五项", readme)
        self.assertIn("Chart, Settings, NMEA Input, Data Sources, and Chart Library", readme)

    def test_all_acceptance_ids_exist_once_and_unrun_items_are_not_preapproved(self):
        acceptance = self.read("docs/phases/chart-library/ACCEPTANCE_RESULTS_TEMPLATE.md")
        rows = re.findall(r"^\| (A\d{2}) \| ([A-Z_]+) \|", acceptance, flags=re.MULTILINE)
        self.assertEqual([f"A{index:02d}" for index in range(1, 43)], [item[0] for item in rows])
        allowed = {"PASS", "FAIL", "BLOCKED", "NOT_RUN", "CI_PENDING", "PARTIAL_CI_PENDING"}
        self.assertTrue(all(status in allowed for _, status in rows))
        baseline = json.loads(self.read("docs/phases/chart-library/CL12_BASELINE_LOCK.json"))
        self.assertEqual(42, baseline["acceptance_ids"]["count"])
        self.assertEqual("GITHUB_ACTION_PENDING", baseline["verification"])
        self.assertFalse(baseline["full_local_gate_run"])

    def test_support_matrix_separates_capability_from_claims(self):
        matrix = self.read("docs/phases/chart-library/CL12_SUPPORT_MATRIX.md")
        for required in (
            "SAF 文件夹树",
            "seek",
            "PNG/JPEG/WebP",
            "TMS/XYZ",
            "pipe/虚拟文档",
            "google-maps-configuration.json",
            "English translation",
        ):
            self.assertIn(required, matrix)
        self.assertIn("不证明 API 授权", self.read("README.md"))

    def test_final_machine_gate_covers_build_device_compatibility_and_performance(self):
        workflow = self.read(".github/workflows/android.yml")
        device = self.read(".github/scripts/run_device_tests.sh")
        for required in (
            "./gradlew --no-daemon test --stacktrace",
            "lintStandaloneDebug",
            "assembleStandaloneDebug assembleStandaloneRelease",
            "bash .github/scripts/run_device_tests.sh all",
            "api-level: 36",
            "stage11-performance",
            "test-release-product-surface.sh",
            "chart_library_cl12_contract",
        ):
            self.assertIn(required, workflow)
        for module in (
            ":adapter:chart-library-android:connectedDebugAndroidTest",
            ":adapter:map-offline:connectedDebugAndroidTest",
            ":feature:chart-library:connectedDebugAndroidTest",
            ":app-shell:connectedStandaloneDebugAndroidTest",
        ):
            self.assertIn(module, device)

    def test_secret_free_maps_and_cl11_measurements_reach_codex_report(self):
        workflow = self.read(".github/workflows/android.yml")
        builder = self.read(".github/scripts/build_codex_job_report.py")
        maps = self.read(".github/scripts/emit_google_maps_configuration_evidence.py")
        for required in (
            "emit_google_maps_configuration_evidence.py",
            "build/google-maps/configuration.json",
            "google-maps-configuration.json",
            "chart-library-cl11-evidence.json",
        ):
            self.assertIn(required, workflow + builder)
        self.assertNotIn('"apiKey"', maps)
        self.assertNotIn('"keyValue"', maps)
        self.assertIn("CONFIGURATION_ONLY_NOT_RUNTIME_READINESS", maps)

    def test_platform_and_data_safety_contracts_remain(self):
        manifest = self.read("app-shell/src/main/AndroidManifest.xml")
        self.assertIn('android:screenOrientation="portrait"', manifest)
        self.assertNotIn("android.intent.category.HOME", manifest)
        baseline = self.read("docs/phases/chart-library/CL12_BASELINE_LOCK.json")
        self.assertIn('"external_source_write_bytes": 0', baseline)
        self.assertIn('"implicit_full_copy_bytes": 0', baseline)


if __name__ == "__main__":
    unittest.main()
