import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class ChartC03ContractTest(unittest.TestCase):
    def test_obsolete_provider_ui_contract_is_removed(self):
        path = ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartUiContract.kt"
        contract = path.read_text() if path.exists() else ""
        self.assertNotIn("ChartSurfaceKind", contract)
        self.assertNotIn("GOOGLE_MAPS", contract)
        self.assertNotIn("mapConfigured", contract)

    def test_surface_tool_and_transient_are_distinct_domain_planes(self):
        domain = ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain"
        model = "\n".join(path.read_text() for path in domain.glob("*.kt"))
        self.assertIn("sealed interface MapSurface", model)
        self.assertIn("enum class MapTool { BROWSE, MEASURE, MANUAL_ROUTE }", model)
        self.assertIn("sealed interface MapTransient", model)
        self.assertIn("data class MapEditGesture", model)

    def test_root_is_map_first_and_query_port_reaches_crosshair(self):
        workspace = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        self.assertNotIn('testTag("map-tool-panel")', workspace)
        self.assertIn('testTag("map-root-command-bar")', workspace)
        self.assertIn("MapCrosshairResolver.screenPoint", workspace)
        self.assertIn("port.unproject(target)", workspace)
        self.assertIn("onQueryPortChanged", workspace)
        self.assertIn("onQueryPortChanged", graph)

    def test_map_clicks_create_candidates_instead_of_direct_points(self):
        adapter = (ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt").read_text()
        self.assertIn("MapAction.MapTapped", adapter)
        self.assertIn("MapAction.MapLongPressed", adapter)
        self.assertNotIn("MapAction.AddPoint(point.toDomainPoint())", adapter)

    def test_android_compose_and_virtual_input_share_feature_first_router(self):
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        router = (ROOT / "ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppInputRouter.kt").read_text()
        self.assertIn("internalAppInputRouter.dispatch(input)", activity)
        self.assertIn("dispatchInput(ShellInput.BACK)", activity)
        self.assertIn("class InternalAppInputRouter", router)
        self.assertIn("BindInternalAppInputHandler", router)

    def test_gesture_and_feature_back_have_executable_contracts(self):
        test = (ROOT / "core/map-domain/src/test/kotlin/com/yokuli/marine/map/domain/MapInteractionContractTest.kt").read_text()
        self.assertIn("feature Back consumes exactly one", test)
        self.assertIn("final frame commits once", test)
        self.assertIn("stale gesture frames", test)


if __name__ == "__main__":
    unittest.main()
