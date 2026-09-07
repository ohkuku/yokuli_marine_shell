from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ChartLibraryCl08ContractTest(unittest.TestCase):
    def test_display_plan_is_revision_bound_bounded_and_deterministic(self):
        contract = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartDisplayContracts.kt").read_text()
        for required in (
            "PinnedAsset", "SourceSet", "catalogRevision", "fingerprint", "request.revision.cacheKey",
            "MAX_SELECTED_CHART_SOURCES = 8", "MAX_ACTIVE_CHART_LAYERS = 8",
            "UNKNOWN_BOUNDS_UNFILTERED", "NO_NATIVE_ZOOM", "LAYER_LIMIT_REACHED",
            "compareBy<ChartAsset> { if (it.role == ChartAssetRole.BASE) 0 else 1 }",
            "longitudeSegments", "tileSize", "tileScheme", "opacity",
        ):
            self.assertIn(required, contract)

    def test_chart_consumes_catalog_but_does_not_own_resource_maintenance(self):
        workspace = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        coordinator = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartDisplayCoordinator.kt").read_text()
        shell = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        renderer = (ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt").read_text()
        for required in (
            "ChartCatalogReadPort", "catalog.snapshot.collect", "ChartDisplayPlanner.plan",
            "MapAction.ChartDisplayPlanChanged", "ChartDisplayViewportChanged",
            "MAX_CATALOG_ITEMS = 10_000", "ChartLayersPage",
        ):
            self.assertIn(required, coordinator + workspace + renderer)
        for forbidden in (
            "ChartImportUiAction", "ChartImportUiState", "ChartPackageCoordinator",
            "chartDocumentPicker", "map-import-chart", "map-coverage-import",
        ):
            self.assertNotIn(forbidden, workspace)
        self.assertNotIn("ChartImportUi", shell)
        self.assertIn("ChartLibraryPickerEffect.OpenDocument", shell)

    def test_renderer_opens_only_planned_resources_and_google_never_fills_local_holes(self):
        preparer = (ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/ChartDisplayPreparer.kt").read_text()
        renderer = (ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt").read_text()
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        for required in (
            "plan.layers.forEach", "ChartReadPurpose.RENDER", "gateway.register",
            "prepared.registration.toRasterSource", "rasterOpacity(prepared.planLayer.opacity)",
            "preparedDisplay?.rejectedLayerCount",
            "val librarySelectionActive = displayPlan.selection !is ChartDisplaySelection.None",
        ):
            self.assertIn(required, preparer + renderer + graph)
        self.assertNotIn("GoogleMarineChartSurface", renderer)

    def test_coverage_uses_same_reader_unions_base_layers_and_rejects_stale_results(self):
        coverage = (ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/ChartLibraryCoverageIndex.kt").read_text()
        contract = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartDisplayContracts.kt").read_text()
        coordinator = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/OfflineCoverageCoordinator.kt").read_text()
        for required in (
            "ChartAssetRole.BASE", "index.probe", "expectedFingerprint != plan.fingerprint",
            "ChartDisplayCoverageStatus.STALE", "checked.flatMapTo", "coverage.evaluate",
        ):
            self.assertIn(required, coverage + contract + coordinator)

    def test_regression_stories_cover_multiregion_dateline_holes_and_session_release(self):
        tests = "\n".join(path.read_text() for path in ROOT.rglob("*Test.kt"))
        for required in (
            "pinnedAssetIsNotStolenByNewCatalogAssetAndCameraIsNotPartOfTheMutation",
            "viewportFiltersDistantRegionsWithoutOpeningEveryCatalogAsset",
            "datelineIntersectionUsesTwoLongitudeSegments",
            "unknownBoundsNeverTurnRenderableContentIntoAnExcludedLayer",
            "missingMetadataZoomRangeUsesSafeRendererRangeInsteadOfBlockingDisplay",
            "overzoomNeverClaimsNativeSourceSetCoverage",
            "coverage unions real base keys and never uses overlay keys to fill a hole",
            "preparer opens only planned revisions honors each tile size and releases every session",
            "quickLayersControlDisplayOnlyAndNeverExposeLibraryOrNavigationManagement",
        ):
            self.assertIn(required, tests)


if __name__ == "__main__":
    unittest.main()
