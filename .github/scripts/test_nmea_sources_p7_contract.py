#!/usr/bin/env python3
import json
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
SPEC_SHA256 = "a5a38f08f8606d230952dcec8e8f521615efc9ec1a4d30ab4a3821f5943b3348"


class NmeaSourcesP7Contract(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def string_names(self, path: str) -> set[str]:
        root = ET.parse(ROOT / path).getroot()
        return {entry.attrib["name"] for entry in root if "name" in entry.attrib}

    def test_current_product_gate_supersedes_the_historical_two_app_surface(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        installed = re.findall(
            r"catalogContribution\s*=\s*([A-Z][A-Za-z]+ShellContribution)", graph
        )
        self.assertEqual(
            [
                "ChartShellContribution",
                "SettingsShellContribution",
                "NmeaInputShellContribution",
                "DataSourcesShellContribution",
            ],
            installed,
        )
        default_document = graph[graph.index("val defaultStartDocument"):]
        self.assertEqual(2, default_document.count("TilePlacement("))
        self.assertIn("ChartDestinations.EntryId", default_document)
        self.assertIn("SettingsDestinations.EntryId", default_document)
        self.assertNotIn("NmeaInputDestinations.EntryId", default_document)
        self.assertNotIn("DataSourcesDestinations.EntryId", default_document)

        for path in (
            ".github/scripts/test_launcher_stage1_contract.py",
            ".github/scripts/test_launcher_stage2_contract.py",
            ".github/scripts/run_device_tests.sh",
        ):
            current_gate = self.read(path)
            self.assertNotIn("productionShellExposesOnlyChartAndSettings", current_gate)

    def test_release_apk_gate_requires_both_new_apps_and_rejects_debug_or_demo_code(self):
        gate = self.read(".github/scripts/test-release-product-surface.sh")
        for required in (
            "NmeaInputWorkspaceKt",
            "DataSourcesWorkspaceKt",
            "AndroidNmeaInputRuntime",
            "AndroidMarineSourceRuntime",
            "NmeaInputForegroundService",
            "PhoneLocationForegroundService",
        ):
            self.assertIn(required, gate)
        for forbidden in (
            "ShellLabActivity",
            "FakeNmea",
            "DemoNmea",
            "NmeaSoak",
            "TestSender",
        ):
            self.assertIn(forbidden, gate)
        self.assertIn("android:screenOrientation=\"portrait\"", gate)
        self.assertIn("android.intent.category.HOME", gate)
        self.assertIn("android.intent.category.DEFAULT", gate)
        self.assertNotIn("Chart + Settings;", gate)

    def test_all_phase_contracts_and_external_process_probe_are_first_class_ci_gates(self):
        workflow = self.read(".github/workflows/android.yml")
        ci_contract = self.read(".github/scripts/test-ci-contract.sh")
        for phase in range(8):
            gate_id = f"nmea_sources_p{phase}_contract"
            command = f"python3 .github/scripts/test_nmea_sources_p{phase}_contract.py"
            result = f"NMEA_SOURCES_P{phase}_CONTRACT_RESULT"
            for text in (workflow, ci_contract):
                self.assertIn(gate_id, text)
                self.assertIn(command, text)
                self.assertIn(result, text)
        self.assertIn("bash .github/scripts/run_nmea_sources_process_restore.sh", workflow)
        self.assertIn("build/ci-nmea-sources-process-restore.log", workflow)

    def test_release_path_runs_the_final_contract_and_binary_audit_before_publication(self):
        release = self.read(".github/workflows/release.yml")
        publish = release[release.index("  publish:"):]
        release_create = publish.index("gh release create")
        self.assertLess(publish.index("test_nmea_sources_p7_contract.py"), release_create)
        self.assertLess(publish.index("test-release-product-surface.sh"), release_create)
        self.assertIn("assembleStandaloneRelease bundleStandaloneRelease", publish)

    def test_feature_dependencies_stay_directional_and_composition_owned(self):
        nmea = self.read("feature/nmea-input/build.gradle.kts")
        sources = self.read("feature/data-sources/build.gradle.kts")
        adapter = self.read("adapter/marine-data-android/build.gradle.kts")
        app = self.read("app-shell/build.gradle.kts")
        for feature in (nmea, sources):
            self.assertIn('project(":core:marine-data")', feature)
            self.assertNotIn('project(":adapter:marine-data-android")', feature)
            self.assertNotIn('project(":app-shell")', feature)
        self.assertNotIn('project(":feature:', adapter)
        for module in (
            ':adapter:marine-data-android',
            ':feature:nmea-input',
            ':feature:data-sources',
        ):
            self.assertIn(f'project("{module}")', app)

    def test_chinese_primary_english_and_qualified_chinese_resources_have_key_parity(self):
        modules = (
            "feature/nmea-input",
            "feature/data-sources",
            "adapter/marine-data-android",
        )
        for module in modules:
            variants = {
                folder: self.string_names(f"{module}/src/main/res/{folder}/strings.xml")
                for folder in ("values", "values-en", "values-zh-rCN")
            }
            self.assertEqual(variants["values"], variants["values-en"], module)
            self.assertEqual(variants["values"], variants["values-zh-rCN"], module)
            self.assertGreater(len(variants["values"]), 0, module)

    def test_production_logs_and_foreground_notifications_do_not_expose_marine_payloads(self):
        roots = (
            ROOT / "core/marine-data/src/main",
            ROOT / "adapter/marine-data-android/src/main",
            ROOT / "feature/nmea-input/src/main",
            ROOT / "feature/data-sources/src/main",
        )
        kotlin = "\n".join(
            path.read_text(encoding="utf-8")
            for root in roots
            for path in root.rglob("*.kt")
        )
        for forbidden in ("android.util.Log", "Log.d(", "Log.i(", "println(", "printStackTrace("):
            self.assertNotIn(forbidden, kotlin)

        services = "\n".join(
            self.read(path)
            for path in (
                "adapter/marine-data-android/src/main/java/com/yokuli/marine/data/android/service/NmeaInputForegroundService.kt",
                "adapter/marine-data-android/src/main/java/com/yokuli/marine/data/android/service/PhoneLocationForegroundService.kt",
            )
        )
        for forbidden in (
            ".displayName",
            ".host",
            ".port",
            "rawSentence",
            "rawLine",
            "latitude",
            "longitude",
        ):
            self.assertNotIn(forbidden, services)

    def test_delete_confirmation_explains_selected_source_impact_before_commit(self):
        chinese = self.read("feature/nmea-input/src/main/res/values/strings.xml")
        english = self.read("feature/nmea-input/src/main/res/values-en/strings.xml")
        source_behavior = self.read(
            "core/marine-data/src/test/kotlin/com/yokuli/marine/data/source/SourceSelectionRuntimeTest.kt"
        )
        self.assertIn("如果这条连接正被采用", chinese)
        self.assertIn("不会自动切换到其他来源", chinese)
        self.assertIn("If this connection is selected", english)
        self.assertIn("will not switch to another source automatically", english)
        self.assertIn("renamingADescriptorDoesNotLosePreferenceButDeletingSourceIsTruthful", source_behavior)

    def test_final_report_and_lock_are_hash_bound_and_ledger_every_required_story(self):
        report = self.read("docs/implementation/NMEA_SOURCES_FINAL_REPORT.md")
        stage_report = self.read("docs/phases/nmea-sources/P7_REPORT.md")
        lock = json.loads(self.read("docs/phases/nmea-sources/P7_BASELINE_LOCK.json"))
        self.assertEqual("NMEA_SOURCES", lock["phase"])
        self.assertEqual("P7", lock["stage"])
        self.assertEqual(SPEC_SHA256, lock["sourceSpecSha256"])
        self.assertEqual("MACHINE_VERIFIED", lock["status"])
        self.assertEqual("UNVERIFIED_PHYSICAL_DEVICE", lock["physicalDevice"])
        for phase in range(8):
            self.assertIn(f"P{phase}", report)
        for story in range(1, 27):
            story_id = f"E{story:02d}"
            self.assertIn(story_id, report)
        for required in (
            "实际命令与退出码",
            "旧逻辑复用位置",
            "截图／录屏索引",
            "未执行项",
            "实际已支持范围",
            "剩余限制",
        ):
            self.assertIn(required, report)
        self.assertIn("UNVERIFIED_PHYSICAL_DEVICE", stage_report)
        self.assertIn("fullRepositoryGate", lock["evidence"])


if __name__ == "__main__":
    unittest.main()
