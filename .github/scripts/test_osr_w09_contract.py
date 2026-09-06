#!/usr/bin/env python3
from pathlib import Path
import json
import unittest


ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW09ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def chart_layers_body(self) -> str:
        workspace = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt")
        return workspace.split("private fun ChartLayersPage(", 1)[1].split("private fun chartDisplayIssueText", 1)[0]

    def test_root_is_a_focused_four_action_live_map_surface_after_product_correction(self):
        workspace = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt")
        command_bar = workspace.split("private fun MapRootCommandBar(", 1)[1].split("private fun MapCommandButton", 1)[0]
        for tag in ("map-tool-mark", "map-tool-route", "map-tool-measure", "map-open-view-picker"):
            self.assertIn(tag, command_bar)
        self.assertEqual(4, command_bar.count("MapCommandButton("))
        self.assertNotIn("map-open-places", command_bar)
        self.assertNotIn("map-open-routes", command_bar)
        self.assertNotIn("map-tool-manual_route", command_bar)
        self.assertNotIn("map-coordinate-input", command_bar)
        self.assertNotIn("map-open-quick-layers", command_bar)

    def test_quick_layers_do_not_expose_peer_or_resource_management(self):
        body = self.chart_layers_body()
        self.assertIn("ChartDisplayUiAction.SetLayerVisible", body)
        self.assertIn("ChartDisplayUiAction.SetOpacity", body)
        for forbidden in (
            "onOpenChartLibrary", "map-open-chart-library", "ToggleSource", "PinAsset", 
            "ChartDisplayUiAction.Refresh", "map-open-gpx", "map-open-imported-tracks",
        ):
            self.assertNotIn(forbidden, body)
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        chart_call = graph.split("ChartWorkspace(", 1)[1].split("chartSurface = chartSurface", 1)[0]
        self.assertNotIn("onOpenChartLibrary", chart_call)

    def test_visibility_is_a_durable_display_preference_not_a_catalog_mutation(self):
        domain = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartDisplayContracts.kt")
        coordinator = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartDisplayCoordinator.kt")
        proto = self.read("adapter/map-storage/src/main/proto/map_state.proto")
        tests = self.read("feature/chart/src/test/java/com/yokuli/marine/feature/chart/ChartDisplayCoordinatorTest.kt")
        self.assertIn("hiddenAssetIds", domain)
        self.assertIn("hiddenAssetIds", coordinator)
        self.assertIn("chart_display_hidden_asset_ids", proto)
        self.assertIn("quick layer visibility changes display preferences without changing catalog ownership", tests)
        self.assertIn("assertEquals(0, catalog.mutationCount)", tests)

    def test_old_navigation_models_remain_readable_but_are_no_longer_chart_entry_points(self):
        workspace = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt")
        routes = workspace.split("private fun RoutesPage(", 1)[1].split("private fun RouteDetailPage", 1)[0]
        command_bar = workspace.split("private fun MapRootCommandBar(", 1)[1].split("private fun MapCommandButton", 1)[0]
        self.assertNotIn("MapSurface.Routes", command_bar)
        self.assertNotIn("MapSurface.Places", command_bar)
        interaction = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapInteraction.kt")
        for token in ("data object Places", "data object Routes", "data object GpxExchange", "data object ImportedTracks"):
            self.assertIn(token, interaction)

    def test_real_navigation_seam_and_product_evidence_are_present(self):
        workspace = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt")
        stories = self.read("app-shell/src/androidTest/java/com/yokuli/marine/shell/ChartDisplayWorkspaceStoryTest.kt")
        self.assertIn("activeNavigationStrip: (@Composable () -> Unit)? = null", workspace)
        self.assertIn("chartRootAcceptsRealNavigationContentWithoutInventingNavigationState", stories)
        contract = self.read("docs/phases/os-redesign/work-packages/W09_PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/os-redesign/W09_REPORT.md")
        lock = json.loads(self.read("docs/phases/os-redesign/W09_BASELINE_LOCK.json"))
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 领域关系",
            "Opportunities / 可发展空间", "Non-negotiables / 不可协商边界",
            "Forbidden outcomes / 禁止结果", "Acceptance stories", "Evidence required",
            "Existing code landmarks", "Implementation freedom / 实现自由", "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertEqual(5, len(lock["chartRootActions"]))
        self.assertFalse(lock["quickLayersOwnSourceManagement"])
        self.assertFalse(lock["legacyNavigationDataRemoved"])


if __name__ == "__main__":
    unittest.main()
