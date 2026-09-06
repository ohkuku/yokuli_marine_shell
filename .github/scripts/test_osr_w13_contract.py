#!/usr/bin/env python3
from pathlib import Path
import json
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW13ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_preferences_is_the_only_installed_system_settings_identity(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        installed = re.findall(r"catalogContribution\s*=\s*([A-Z][A-Za-z]+ShellContribution)", graph)
        self.assertEqual(
            ["ChartShellContribution", "PreferencesShellContribution", "DataShellContribution", "ChartLibraryShellContribution", "NavigationShellContribution"],
            installed,
        )
        self.assertNotIn("catalogContribution = SettingsShellContribution", graph)
        default = graph[graph.index("val defaultStartDocument"):]
        self.assertIn('tileId = TileInstanceId("tile-settings")', default)
        self.assertIn("entryId = PreferencesDestinations.EntryId", default)
        self.assertIn("size = MarineTileSize.ICON_1X1", default)

    def test_sections_are_complete_and_resource_administration_is_absent(self):
        ui = self.read("feature/preferences/src/main/java/com/yokuli/marine/feature/preferences/PreferencesUiContract.kt")
        workspace = self.read("feature/preferences/src/main/java/com/yokuli/marine/feature/preferences/PreferencesWorkspace.kt")
        enum = ui.split("enum class PreferencesSection", 1)[1].split("}", 1)[0]
        self.assertEqual(
            ["OVERVIEW", "APPEARANCE", "LANGUAGE", "UNITS", "MOTION", "START", "APP_TILES", "ABOUT"],
            re.findall(r"\b[A-Z][A-Z_]+\b", enum),
        )
        for forbidden in ("ChartPackage", "Nmea", "DataSource", "GoogleMap", "OpenChartLibrary"):
            self.assertNotIn(forbidden, ui + workspace)
        build = self.read("feature/preferences/build.gradle.kts")
        for forbidden in (":feature:chart", ":feature:data", ":feature:navigation", ":adapter:"):
            self.assertNotIn(forbidden, build)

    def test_schema_and_app_registry_are_typed_bounded_and_persisted(self):
        state = self.read("core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt")
        proto = self.read("adapter/shell-storage/src/main/proto/launcher_state.proto")
        contract = self.read("core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt")
        binding = self.read("ui/shell-compose/src/main/java/com/yokuli/shell/compose/InstalledAppBinding.kt")
        self.assertIn("CURRENT_LAUNCHER_PERSISTENCE_SCHEMA = 4", state)
        for value in ("measurement_unit_system", "motion_preference", "app_preferences"):
            self.assertIn(value, proto)
        for value in ("AppPreferenceDefinition", "AppPreferenceRegistry", "installedAppIds", "Duplicate app preference key"):
            self.assertIn(value, contract)
        self.assertIn("appPreferenceRegistry", binding)
        self.assertIn(".take(64)", state)

    def test_migration_motion_and_acceptance_stories_are_executable(self):
        product = self.read("app-shell/src/main/java/com/yokuli/marine/shell/YokuliProductModel.kt")
        activity = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt")
        tests = "\n".join(path.read_text(encoding="utf-8") for path in ROOT.rglob("*Test.kt"))
        self.assertIn('legacyEntryIds = setOf(LauncherEntryId("settings"))', product)
        self.assertIn("motionPreference == MotionPreference.REDUCED", activity)
        for story in (
            "registry accepts only installed owners and globally unique typed keys",
            "all current sections have stable tokens and legacy Settings links remain readable",
            "appearance language units motion Start tiles and About are real settings sections",
            "units and motion actions are typed and App Tiles uses installed app declarations",
            "currentCompositionRootMigratesLegacyMarineTilesOnlyAfterDataHasARealHost",
        ):
            self.assertIn(story, tests)

    def test_contract_report_lock_and_state_preserve_scope(self):
        contract = self.read("docs/phases/os-redesign/work-packages/W13_PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/os-redesign/W13_REPORT.md")
        lock = json.loads(self.read("docs/phases/os-redesign/W13_BASELINE_LOCK.json"))
        state = json.loads(self.read("docs/phases/os-redesign/EXECUTION_STATE.json"))
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 领域关系",
            "Opportunities / 可发展空间", "Non-negotiables / 不可协商边界",
            "Forbidden outcomes / 禁止结果", "Acceptance stories", "Evidence required",
            "Existing code landmarks", "Implementation freedom / 实现自由", "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertTrue(lock["preferencesAppRegistered"])
        self.assertFalse(lock["settingsAppRegistered"])
        self.assertEqual("IMPLEMENTED_CI_PENDING", state["workPackages"]["W13"]["status"])


if __name__ == "__main__":
    unittest.main()
