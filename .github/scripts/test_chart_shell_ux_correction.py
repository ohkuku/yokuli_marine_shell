#!/usr/bin/env python3
import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
PHASE = ROOT / "docs/phases/chart-shell-ux-correction"


class ChartShellUxCorrectionContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_bilingual_product_contract_and_baseline_are_locked(self):
        contract = self.read("docs/phases/chart-shell-ux-correction/PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/chart-shell-ux-correction/REPORT.md")
        lock = json.loads(self.read("docs/phases/chart-shell-ux-correction/BASELINE_LOCK.json"))
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 领域关系",
            "Opportunities / 可发展空间", "Non-negotiables / 不可协商边界",
            "Forbidden outcomes / 禁止结果", "Acceptance stories", "Evidence required",
            "Existing code landmarks", "Implementation freedom / 实现自由", "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertEqual(["mark", "route", "measure", "map_view"], lock["chartRootActions"])
        self.assertEqual(2, lock["measurementMaximumPoints"])
        self.assertTrue(lock["supersedesConflictingHistoricalChartRootClauses"])

    def test_chart_root_is_map_first_with_exact_direct_actions(self):
        workspace = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt")
        command = workspace.split("private fun MapRootCommandBar(", 1)[1].split("private fun MapCommandButton", 1)[0]
        self.assertEqual(4, command.count("MapCommandButton("))
        for required in ("map-tool-mark", "map-tool-route", "map-tool-measure", "map-open-view-picker"):
            self.assertIn(required, command)
        for forbidden in ("map-open-places", "map-open-routes", "map-open-quick-layers", "map-open-chart-packages"):
            self.assertNotIn(forbidden, command)
        for direct_control in ("map-follow", "map-zoom-in", "map-zoom-out", "map-compass"):
            self.assertIn(direct_control, workspace)

    def test_target_measure_waypoint_and_route_states_are_visible_and_bounded(self):
        model = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapModel.kt")
        reducer = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapReducer.kt")
        workspace = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt")
        google = self.read("adapter/chart-google/src/main/java/com/yokuli/marine/adapter/chart/google/GoogleMarineChartSurface.kt")
        offline = self.read("adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt")
        self.assertIn('require(points.size <= 2) { "Measurement is a two-point A/B ruler" }', model)
        self.assertIn('if (index == 0) "A" else "B"', google)
        self.assertIn('if (index == 0) "A" else "B"', offline)
        self.assertIn('"WP %03d".format(nextOrdinal)', reducer)
        self.assertIn("MapAction.FocusSavedPlace", reducer)
        self.assertIn("MapTransient.SelectedObject", reducer)
        self.assertIn("MapAction.BeginMeasurement(vessel, target", workspace)
        measure_ui = workspace.split("private fun MeasurementPage(", 1)[1].split("private fun MapSelectionModePage", 1)[0]
        for forbidden in ("InsertMeasurementPoint", "DeleteMeasurementPoint", "UndoMeasurementEdit", "RedoMeasurementEdit", "map-measure-convert"):
            self.assertNotIn(forbidden, measure_ui)
        save = reducer.split("private fun saveRoutePlan(", 1)[1].split("private fun duplicateRoutePlan", 1)[0]
        discard = reducer.split("private fun discardRouteDraft(", 1)[1].split("private fun requestDeleteRoutePlan", 1)[0]
        for block in (save, discard):
            self.assertIn("surface = MapSurface.Root", block)
            self.assertIn("transient = null", block)

    def test_search_close_and_navigation_share_explicit_draft_guard(self):
        projector = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartLauncherProjection.kt")
        view_model = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ShellViewModel.kt")
        self.assertIn("ChartDestination.Place -> MapAction.FocusSavedPlace", projector)
        self.assertIn("PendingRouteDecision.CloseTask", view_model)
        self.assertIn("PendingRouteDecision.StartNavigation", view_model)
        self.assertIn("MapAction.RequestCloseRouteDraft", view_model)
        for decision in ("UnsavedRouteDecision.SAVE", "UnsavedRouteDecision.DISCARD", "UnsavedRouteDecision.CANCEL"):
            self.assertIn(decision, view_model)
        self.assertIn("LauncherAction.CloseTask", view_model)

    def test_selected_map_view_owns_renderer_and_configuration_truth(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        workspace = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt")
        readme = self.read("README.md")
        self.assertIn("chartSurfaceKind(state.mapViewMode, BuildConfig.GOOGLE_MAPS_CONFIGURED)", graph)
        self.assertIn("connectedBaseConfigured = BuildConfig.GOOGLE_MAPS_CONFIGURED", graph)
        self.assertIn("mode == MapViewMode.MARINE || connectedBaseConfigured", workspace)
        self.assertIn("map_connected_view_not_configured", workspace)
        self.assertIn("配置只表示", readme)
        for resources in ROOT.glob("feature/chart/src/main/res/values*/strings.xml"):
            self.assertIn("map_connected_view_not_configured", resources.read_text(encoding="utf-8"))

    def test_shell_session_spatial_status_locale_and_manifest_invariants(self):
        packer_test = self.read("core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/AdaptiveTilePackerTest.kt")
        status = self.read("feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStatusStrip.kt")
        recents = self.read("feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSystemKeyBar.kt")
        app_list = self.read("feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpAppList.kt")
        chart_visual = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartLauncherPresentation.kt")
        library_visual = self.read("feature/chart-library/src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryLauncherPresentation.kt")
        manifest = self.read("app-shell/src/main/AndroidManifest.xml")
        self.assertIn("two one by one tiles can be vertical or horizontal neighbours", packer_test)
        self.assertNotIn("clickable", status)
        self.assertNotIn("combinedClickable", status)
        self.assertIn("onClose", recents)
        self.assertIn('chineseIndex = \'H\'', chart_visual)
        self.assertIn('chineseIndex = \'H\'', library_visual)
        self.assertIn('locale.language == "zh"', app_list)
        self.assertIn('android:screenOrientation="portrait"', manifest)
        self.assertNotIn("android.intent.category.HOME", manifest)

    def test_chart_library_keeps_basic_full_and_failed_scan_truth_separate(self):
        validation = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartValidationContracts.kt")
        scan = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartScanContracts.kt")
        strings = self.read("feature/chart-library/src/main/res/values-en/strings.xml")
        self.assertIn("suspend fun inspectBasic", validation)
        self.assertIn("suspend fun verifyFull", validation)
        self.assertIn("BASIC_SAMPLE_LIMIT", validation)
        complete_only = re.search(
            r"val missing = if \(enumeration is ChartEnumerationResult\.Complete\)(.*?)else emptySet\(\)",
            scan,
            re.S,
        )
        self.assertIsNotNone(complete_only)
        self.assertIn("lastSuccessfulGeneration", scan)
        for truth in ("read-only permission available", "permission lost", "resource remains registered", "retry"):
            self.assertIn(truth, strings.lower())

    def test_correction_gate_is_captured_and_enforced_by_ci(self):
        workflow = self.read(".github/workflows/android.yml")
        ci_contract = self.read(".github/scripts/test-ci-contract.sh")
        self.assertIn("id: chart_shell_ux_correction", workflow)
        self.assertIn("test_chart_shell_ux_correction.py", workflow)
        self.assertIn("CHART_SHELL_UX_CORRECTION_RESULT", workflow)
        self.assertIn("chart-shell-ux-correction", workflow)
        self.assertIn("test_chart_shell_ux_correction.py", ci_contract)


if __name__ == "__main__":
    unittest.main()
