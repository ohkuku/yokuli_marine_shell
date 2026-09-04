from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class LauncherFrozenBaselineContractTest(unittest.TestCase):
    def test_module_graph_removes_fake_features_and_isolates_shell_lab(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        app = (ROOT / "app-shell/build.gradle.kts").read_text()

        for module in (":core:shell-engine", ":feature:settings", ":feature:shell-lab"):
            self.assertIn(f'"{module}"', settings)
        for module in (":feature:cockpit", ":feature:library", ":feature:system"):
            self.assertNotIn(f'"{module}"', settings)
            self.assertNotIn(f'project("{module}")', app)
        self.assertIn('implementation(project(":feature:settings"))', app)
        self.assertIn('debugImplementation(project(":feature:shell-lab"))', app)
        self.assertNotIn('implementation(project(":feature:shell-lab"))', app)

    def test_registry_is_contribution_based_and_production_has_two_entries(self):
        identifiers = (ROOT / "core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherIdentifiers.kt").read_text()
        catalog = (ROOT / "core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherCatalogContract.kt").read_text()
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()

        self.assertIn("value class LauncherAppId", identifiers)
        self.assertNotIn("enum class LauncherAppId", identifiers)
        self.assertIn("value class LaunchToken", identifiers)
        self.assertIn("interface LauncherCatalogContribution", catalog)
        self.assertIn("ChartShellContribution", graph)
        self.assertIn("SettingsShellContribution", graph)
        self.assertIn("productionInstalledApps = listOf(", graph)
        self.assertIn("catalogContribution = ChartShellContribution", graph)
        self.assertIn("catalogContribution = SettingsShellContribution", graph)
        self.assertIn("productionInstalledApps.map { it.catalogContribution }", graph)
        for removed in ("CockpitShellContribution", "LibraryShellContribution", "AnchorShortcutContribution"):
            self.assertNotIn(removed, graph)

    def test_only_wp8_standard_tile_sizes_remain(self):
        model = (ROOT / "core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherIdentifiers.kt").read_text()
        block = model.split("enum class WpTileSize", 1)[1].split("}", 1)[0]
        names = re.findall(r"^\s*([A-Z][A-Z0-9_]*)\(", block, flags=re.MULTILINE)
        self.assertEqual(["SMALL_1X1", "MEDIUM_2X2", "WIDE_4X2"], names)
        self.assertNotIn("WIDE_2X1", model)
        self.assertNotIn("HERO_4X2", model)

    def test_production_main_contains_no_fixture_objects_or_fake_marine_facts(self):
        production_modules = ("app-shell", "core/model", "core/shell-contract", "core/shell-engine", "feature/desktop", "feature/chart", "feature/settings")
        text = "\n".join(
            path.read_text()
            for module in production_modules
            for path in (ROOT / module / "src/main").rglob("*")
            if path.is_file() and path.suffix in {".kt", ".xml"}
        )
        self.assertNotRegex(text, r"\b(?:Launcher|Chart|Cockpit|Library|System)UiFixtures\b")
        for forbidden in ("MOTUIHE", "6.2 kn", "6.2 节", "COG 184", "HDG 184", "32 / 60", "NOT ARMED", "SURVEY READY", "27 TRIPS"):
            self.assertNotIn(forbidden, text)

    def test_chart_exposes_browse_only_and_labels_unconfigured_demo(self):
        contract = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartUiContract.kt").read_text()
        workspace = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        default_strings = (ROOT / "feature/chart/src/main/res/values/strings.xml").read_text()
        english_strings = (ROOT / "feature/chart/src/main/res/values-en/strings.xml").read_text()

        for removed in ("ChartMode", "courseOverGround", "speedOverGround", "destination", "anchorArmed", "surveyDepth"):
            self.assertNotIn(removed, contract)
        self.assertNotIn("WpApplicationBar", workspace)
        self.assertNotIn("Home", contract)
        self.assertIn("地图未配置", default_strings)
        self.assertIn("DEMO MAP", english_strings)

    def test_settings_surface_contains_only_implemented_sections(self):
        contract = (ROOT / "feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsUiContract.kt").read_text()
        workspace = (ROOT / "feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsWorkspace.kt").read_text()

        for section in ("APPEARANCE", "START_SCREEN", "MAP", "LANGUAGE", "ABOUT"):
            self.assertIn(section, contract)
        for forbidden in ("CONNECTIONS", "DATA_SOURCES", "DEVICES", "SAFETY", "NMEA"):
            self.assertNotIn(forbidden, contract + workspace)
        self.assertNotIn("UiFixtures", contract)
        self.assertNotIn("Home", contract)

    def test_desktop_has_explicit_state_and_hides_internal_metadata(self):
        start = (ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt").read_text()
        apps = (ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpAppList.kt").read_text()
        contract = (ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt").read_text()
        resources = (ROOT / "feature/desktop/src/main/res/values-en/strings.xml").read_text()

        self.assertRegex(start, r"fun YokuliStartScreen\(\s*state: LauncherUiState,")
        self.assertRegex(apps, r"fun WpAppList\(\s*state: LauncherUiState,")
        self.assertNotIn("LauncherUiFixtures", contract)
        self.assertNotIn("LauncherEntryKind", apps)
        self.assertNotIn("core app", resources)
        self.assertNotIn("shortcut", resources)
        self.assertNotIn("hold to pin", resources)

    def test_shell_engine_foundation_is_explicit_and_spatial(self):
        sources = "\n".join(path.read_text() for path in (ROOT / "core/shell-engine/src/main").rglob("*.kt"))
        for symbol in (
            "data class ResolvedStartGeometry",
            "data class StartDocument",
            "data class GridCell",
            "sealed interface StartInteractionState",
            "data class LayoutTransaction",
            "class LauncherCatalog",
            "class ShellNavigator",
        ):
            self.assertIn(symbol, sources)
        self.assertIn("schemaVersion:", sources)
        self.assertIn("cell:", sources)

    def test_shell_lab_is_debug_only_and_release_manifest_free(self):
        app = (ROOT / "app-shell/build.gradle.kts").read_text()
        manifest = (ROOT / "feature/shell-lab/src/main/AndroidManifest.xml").read_text()
        lab_source = "\n".join(path.read_text() for path in (ROOT / "feature/shell-lab/src/main/java").rglob("*.kt"))

        self.assertIn('debugImplementation(project(":feature:shell-lab"))', app)
        self.assertNotIn('releaseImplementation(project(":feature:shell-lab"))', app)
        self.assertIn("ShellLabActivity", manifest)
        self.assertIn("30", lab_source)
        self.assertIn("DEMO", lab_source)

    def test_master_is_current_and_phase0_evidence_is_archived(self):
        master = (ROOT / "docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md").read_text()
        self.assertIn("NORMATIVE / 施工主文档", master)
        self.assertIn("Stage 0 — Freeze & Reference Contract", master)
        for filename in (
            "PHASE0_PRODUCT_SURFACE_REQUIREMENTS.md",
            "SHELL_ENGINE_REQUIREMENTS.md",
        ):
            text = (ROOT / "docs/archive/pre-launcher-engine" / filename).read_text()
            self.assertRegex(text, r"[\u4e00-\u9fff]")
            self.assertIn("English", text)


if __name__ == "__main__":
    unittest.main()
