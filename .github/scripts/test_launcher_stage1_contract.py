import json
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-1"
APPROVED_TAG = "launcher-engine-stage0-approved-v1.1"
APPROVED_SHA = "16b0e5cd1c8fa2e5f4b78aefadf3fa7c012698b2"


class LauncherStage1ProductSurfaceContractTest(unittest.TestCase):
    def test_stage_starts_exactly_at_the_approved_stage_zero_point(self):
        lock_path = STAGE / "BASELINE_LOCK.json"
        self.assertTrue(lock_path.is_file(), "missing Stage 1 baseline lock")
        lock = json.loads(lock_path.read_text())
        self.assertEqual(1, lock["stage"])
        self.assertEqual(APPROVED_TAG, lock["approvedStage0Tag"])
        self.assertEqual(APPROVED_SHA, lock["startingSha"])
        self.assertEqual("Product Surface Reduction", lock["scope"])
        self.assertEqual("PENDING_HUMAN_REVIEW", lock["approvalStatus"])

    def test_release_catalog_and_start_document_are_exactly_chart_and_settings(self):
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        self.assertEqual(
            ["ChartShellContribution", "SettingsShellContribution"],
            re.findall(r"catalogContribution\s*=\s*([A-Z][A-Za-z]+ShellContribution)", graph),
        )
        self.assertIn("InstalledAppRegistry(productionInstalledApps)", graph)
        self.assertIn("productionInstalledAppRegistry.catalogContributions", graph)
        self.assertEqual(2, graph.count("TilePlacement("))
        self.assertEqual(
            ["ChartDestinations.EntryId", "SettingsDestinations.EntryId"],
            re.findall(r"entryId\s*=\s*([A-Za-z]+Destinations\.EntryId)", graph),
        )

    def test_removed_feature_modules_have_no_active_source_or_dependency(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        app = (ROOT / "app-shell/build.gradle.kts").read_text()
        for name in ("cockpit", "library", "system"):
            module = f":feature:{name}"
            with self.subTest(module=module):
                self.assertNotIn(f'"{module}"', settings)
                self.assertNotIn(f'project("{module}")', app)
                self.assertFalse((ROOT / f"feature/{name}/src").exists())
                self.assertFalse((ROOT / f"feature/{name}/build.gradle.kts").exists())

    def test_chart_exposes_one_browse_destination_and_no_runtime_navigation_modes(self):
        contribution = (
            ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartShellContribution.kt"
        ).read_text()
        contract = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartUiContract.kt").read_text()
        workspace = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        self.assertEqual(["Browse"], re.findall(r"val\s+(\w+)\s*=\s*LaunchToken\(", contribution))
        self.assertIn('LaunchToken("chart.browse")', contribution)
        self.assertIn('testTag("chart-workspace-browse")', workspace)
        for forbidden in (
            "ChartMode",
            "TRACKING",
            "NAVIGATION",
            "ANCHOR",
            "TRIP",
            "SURVEY",
            "courseOverGround",
            "speedOverGround",
            "activeRoute",
            "vesselPosition",
        ):
            self.assertNotIn(forbidden, contribution + contract + workspace)

    def test_settings_contains_only_truthful_shell_configuration_and_build_facts(self):
        contract = (
            ROOT / "feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsUiContract.kt"
        ).read_text()
        workspace = (
            ROOT / "feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsWorkspace.kt"
        ).read_text()
        enum_body = contract.split("enum class SettingsSection", 1)[1].split("}", 1)[0]
        self.assertEqual(
            ["OVERVIEW", "APPEARANCE", "START_SCREEN", "MAP", "LANGUAGE", "ABOUT"],
            re.findall(r"\b[A-Z][A-Z_]+\b", enum_body),
        )
        for fact in (
            "mapConfigured",
            "pinnedTileCount",
            "startDocumentVersion",
            "versionName",
            "buildVariant",
            "gitSha",
            "debugShellLabAvailable",
        ):
            self.assertIn(fact, contract + workspace)
        for forbidden in ("CONNECTIONS", "DATA_SOURCES", "DEVICES", "SAFETY", "NMEA"):
            self.assertNotIn(forbidden, contract + workspace)

    def test_release_copy_contains_no_coming_soon_or_fake_marine_values(self):
        release_modules = (
            "app-shell",
            "feature/desktop",
            "feature/chart",
            "feature/settings",
        )
        text = "\n".join(
            path.read_text()
            for module in release_modules
            for path in (ROOT / module / "src/main").rglob("*")
            if path.is_file() and path.suffix in {".kt", ".xml"}
        )
        for forbidden in (
            "Coming Soon",
            "COMING SOON",
            "即将推出",
            "MOTUIHE",
            "6.2 kn",
            "6.2 节",
            "COG 184",
            "SOG 6.2",
            "HDG 184",
            "32 / 60",
            "NOT ARMED",
            "SURVEY READY",
            "27 TRIPS",
        ):
            self.assertNotIn(forbidden, text)
        self.assertNotRegex(text, r"\b(?:SOG|COG)\s*[:=]?\s*\d")

    def test_release_user_visible_resources_are_current_and_truthful(self):
        release_modules = (
            "app-shell",
            "feature/desktop",
            "feature/chart",
            "feature/settings",
        )
        visible_strings = []
        for module in release_modules:
            resource_root = ROOT / module / "src/main/res"
            for path in resource_root.glob("values*/strings.xml"):
                resources = ET.parse(path).getroot()
                visible_strings.extend(
                    "".join(entry.itertext())
                    for entry in resources
                    if entry.tag in {"string", "plurals", "string-array"}
                )
        text = "\n".join(visible_strings)
        for forbidden in (
            "MAP READY",
            "地图已就绪",
            "position disabled",
            "VESSEL POSITION NOT ENABLED",
            "船位未启用",
            "船位尚未启用",
            "将在后续阶段实现",
            "尚未实现",
            "later phase",
            "not implemented",
            "About & diagnostics",
            "about & diagnostics",
            "关于与诊断",
        ):
            self.assertNotIn(forbidden, text)
        for required in (
            "MAP CONFIGURED",
            "地图已配置",
            "BROWSE ONLY",
            "仅浏览",
            "Map content and trademarks belong to their providers.",
            "地图内容与商标归其提供方所有。",
            "Shows current build, map configuration, and Start document facts.",
            "显示当前构建、地图配置和桌面文档信息。",
        ):
            self.assertIn(required, text)

        settings_zh = ET.parse(
            ROOT / "feature/settings/src/main/res/values/strings.xml"
        ).getroot()
        settings_en = ET.parse(
            ROOT / "feature/settings/src/main/res/values-en/strings.xml"
        ).getroot()
        zh_detail = settings_zh.find("string[@name='map_configured_detail']")
        en_detail = settings_en.find("string[@name='map_configured_detail']")
        self.assertIsNotNone(zh_detail)
        self.assertIsNotNone(en_detail)
        self.assertIn("已配置 Android 地图密钥", zh_detail.text)
        self.assertIn("不表示", zh_detail.text)
        self.assertIn("An Android Maps key is configured", en_detail.text)
        self.assertIn("not validated", en_detail.text)

    def test_shell_lab_is_debug_only_in_source_and_release_apk_gate(self):
        app = (ROOT / "app-shell/build.gradle.kts").read_text()
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        ci_contract = (ROOT / ".github/scripts/test-ci-contract.sh").read_text()
        apk_contract = ROOT / ".github/scripts/test-release-product-surface.sh"
        self.assertIn('debugImplementation(project(":feature:shell-lab"))', app)
        self.assertNotIn('releaseImplementation(project(":feature:shell-lab"))', app)
        self.assertNotRegex(app, r"(?m)^\s*implementation\(project\(\":feature:shell-lab\"\)\)")
        self.assertTrue(apk_contract.is_file(), "missing release APK binary surface inspection")
        apk_contract_text = apk_contract.read_text()
        self.assertNotIn("assembleHomeRelease", workflow)
        self.assertIn("app-shell-standalone-release-unsigned.apk", apk_contract_text)
        self.assertNotIn("app-shell-home-release-unsigned.apk", apk_contract_text)
        self.assertIn("android.intent.category.LAUNCHER", apk_contract_text)
        self.assertIn("android.intent.category.HOME", apk_contract_text)
        self.assertIn("android.intent.category.DEFAULT", apk_contract_text)
        self.assertIn("unexpectedly contains", apk_contract_text)
        self.assertIn("id: launcher_stage1_contract", workflow)
        self.assertIn("test_launcher_stage1_contract.py", workflow)
        self.assertIn("test-release-product-surface.sh", workflow)
        self.assertIn("LAUNCHER_STAGE1_CONTRACT_RESULT", workflow)
        self.assertIn("launcher_stage1_contract", ci_contract)

    def test_stage_audit_and_report_record_candidate_revalidation_and_stop(self):
        audit_path = STAGE / "PRODUCT_SURFACE_AUDIT.md"
        report_path = STAGE / "REPORT.md"
        self.assertTrue(audit_path.is_file(), "missing Stage 1 product-surface audit")
        self.assertTrue(report_path.is_file(), "missing Stage 1 report")
        audit = audit_path.read_text()
        report = report_path.read_text()
        for evidence in (
            "Chart + Settings",
            "Chart Browse",
            "debugImplementation",
            "ShellLabActivity",
            "Coming Soon",
            "SAFE/SOG/COG/Trip/NMEA",
        ):
            self.assertIn(evidence, audit)
        for section in ("Baseline", "Scope", "Architecture", "Interaction", "Tests", "Hardware", "Stop"):
            self.assertIn(f"## {section}", report)
        self.assertIn("Stage 2: NOT STARTED", report)
        self.assertIn("Stage 1 correction", report)
        self.assertIn("both Release flavors", report)
        self.assertIn("PENDING_HUMAN_REVIEW", report)
        self.assertTrue(
            report.endswith(
                "STOPPED AT STAGE GATE.\nAWAITING HUMAN REVIEW BEFORE NEXT STAGE.\n"
            )
        )


if __name__ == "__main__":
    unittest.main()
