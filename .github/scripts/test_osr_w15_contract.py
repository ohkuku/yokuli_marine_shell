#!/usr/bin/env python3
from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW15ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_production_has_exactly_five_goal_owned_apps(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        installed = re.findall(r"catalogContribution\s*=\s*([A-Z][A-Za-z]+ShellContribution)", graph)
        self.assertEqual(
            ["ChartShellContribution", "PreferencesShellContribution", "DataShellContribution", "ChartLibraryShellContribution", "NavigationShellContribution"],
            installed,
        )
        for legacy in ("NmeaInputShellContribution", "DataSourcesShellContribution", "SettingsShellContribution"):
            self.assertNotIn(f"catalogContribution = {legacy}", graph)

    def test_legacy_apps_leave_active_build_graph_without_removing_internal_nmea(self):
        settings = self.read("settings.gradle.kts")
        app = self.read("app-shell/build.gradle.kts")
        for legacy in (":feature:settings", ":feature:data-sources"):
            self.assertNotIn(f'"{legacy}"', settings)
            self.assertNotIn(f'project("{legacy}")', app)
        self.assertIn('":feature:nmea-input"', settings)
        self.assertIn('implementation(project(":feature:nmea-input"))', app)

    def test_chart_product_reachability_is_map_measure_layers_and_handoffs_only(self):
        workspace = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt")
        command = workspace.split("private fun MapRootCommandBar(", 1)[1].split("private fun MapCommandButton", 1)[0]
        self.assertEqual(4, command.count("MapCommandButton("))
        for required in ("map-tool-mark", "map-tool-route", "map-tool-measure", "map-open-view-picker"):
            self.assertIn(required, command)
        for forbidden in (
            "map-open-places", "map-open-routes", "map-open-quick-layers",
            "MapSurface.Places", "MapSurface.Routes", "MapSurface.ChartPackages",
        ):
            self.assertNotIn(forbidden, command)
        root_summary = workspace.split("private fun MapRootSummary(", 1)[1].split("private fun SelectedObjectSummary", 1)[0]
        for required in ("map-candidate-go-to", "map-candidate-mark", "map-candidate-measure"):
            self.assertIn(required, root_summary)
        for forbidden in ("map-candidate-save", "map-candidate-route", "map-save-place"):
            self.assertNotIn(forbidden, root_summary)
        measure = workspace.split("private fun MeasurementRootSummary(", 1)[1].split("private fun CrosshairAction", 1)[0]
        self.assertNotIn("map-measure-convert", measure)

    def test_handoffs_are_read_only_and_do_not_duplicate_navigation(self):
        workspace = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt")
        place = workspace.split("private fun PlaceDetailPage(", 1)[1].split("private fun MapFormTextField", 1)[0]
        self.assertIn("map-place-view", place)
        for forbidden in ("map-place-edit", "map-place-move", "map-place-delete", "map-place-export", "map-place-route-from"):
            self.assertNotIn(forbidden, place)
        reducer = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapReducer.kt")
        preview = reducer.split("private fun previewRoutePlan(", 1)[1].split("private fun beginRoutePlanEdit", 1)[0]
        self.assertIn("surface = MapSurface.Root", preview)
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        chart_binding = graph.split("catalogContribution = ChartShellContribution", 1)[1].split("catalogContribution = PreferencesShellContribution", 1)[0]
        self.assertNotIn("searchContributions", chart_binding)

    def test_navigation_owns_gpx_ui_and_shell_only_adapts_typed_facts(self):
        navigation = self.read("feature/navigation/src/main/java/com/yokuli/marine/feature/navigation/NavigationGpxWorkspace.kt")
        workspace = self.read("feature/navigation/src/main/java/com/yokuli/marine/feature/navigation/NavigationWorkspace.kt")
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        for symbol in ("NavigationGpxUiState", "NavigationGpxUiAction", "NavigationGpxWorkspace"):
            self.assertIn(symbol, navigation + workspace)
        self.assertIn("toNavigationGpxState", graph)
        self.assertIn("toDocumentAction", graph)
        self.assertNotIn("NavigationGpxExchangeSurface", graph)
        build = self.read("feature/navigation/build.gradle.kts")
        self.assertNotIn('project(":feature:chart")', build)
        self.assertNotIn('project(":adapter:', build)

    def test_migration_release_audit_and_evidence_are_preserved(self):
        migration = self.read("app-shell/src/main/java/com/yokuli/marine/shell/YokuliProductModel.kt")
        for value in ('LauncherEntryId("nmea-input")', 'LauncherEntryId("data-sources")', 'LauncherEntryId("settings")'):
            self.assertIn(value, migration)
        release = self.read(".github/scripts/test-release-product-surface.sh")
        for required in ("PreferencesWorkspaceKt", "DataWorkspaceKt", "ChartLibraryWorkspaceKt", "NavigationWorkspaceKt", "NmeaInputWorkspaceKt"):
            self.assertIn(required, release)
        for forbidden in ("SettingsWorkspaceKt", "SettingsShellContribution", "DataSourcesShellContribution"):
            self.assertIn(forbidden, release)
        contract = self.read("docs/phases/os-redesign/work-packages/W15_PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/os-redesign/W15_REPORT.md")
        lock = json.loads(self.read("docs/phases/os-redesign/W15_BASELINE_LOCK.json"))
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 领域关系", "Opportunities / 可发展空间",
            "Non-negotiables / 不可协商边界", "Forbidden outcomes / 禁止结果", "Acceptance stories",
            "Evidence required", "Existing code landmarks", "Implementation freedom / 实现自由", "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertEqual(5, len(lock["productionApps"]))
        self.assertEqual(["measure", "quick_layers", "crosshair"], lock["chartRootActions"])
        self.assertTrue(lock["legacyTokenMigrationRetained"])


if __name__ == "__main__":
    unittest.main()
