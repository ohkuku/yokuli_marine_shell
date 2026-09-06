from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ChartLibraryCl10ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_one_binding_derives_catalog_visual_search_and_host(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        contribution = self.read(
            "feature/chart-library/src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryShellContribution.kt"
        )
        presentation = self.read(
            "feature/chart-library/src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryLauncherPresentation.kt"
        )
        self.assertEqual(1, graph.count("catalogContribution = ChartLibraryShellContribution"))
        for required in (
            "chartLibraryLauncherVisualContribution",
            "chartLibrarySearchContributions",
            "ChartLibraryWorkspace",
            "dynamicLaunchTokenMatcher = ChartLibraryDestinations::accepts",
        ):
            self.assertIn(required, graph)
        for size in ("ICON_1X1", "STANDARD_2X2", "WIDE_4X2"):
            self.assertIn(size, presentation + contribution)

    def test_tokens_are_opaque_and_default_start_is_unchanged(self):
        contribution = self.read(
            "feature/chart-library/src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryShellContribution.kt"
        )
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        self.assertIn('LaunchToken("chart_library.browse")', contribution)
        self.assertIn("decodeId", contribution)
        for forbidden in ("content://", "file://", "ChartOpaqueLocator"):
            self.assertNotIn(forbidden, contribution)
        default = graph[graph.index("val defaultStartDocument"):]
        self.assertEqual(2, default.count("TilePlacement("))
        self.assertNotIn("ChartLibraryDestinations.EntryId", default)

    def test_chart_settings_and_library_use_linked_shell_navigation(self):
        chart = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt")
        preferences = self.read("feature/preferences/src/main/java/com/yokuli/marine/feature/preferences/PreferencesWorkspace.kt")
        activity = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt")
        reducer = self.read("core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt")
        tests = self.read("core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/LauncherNavigationTest.kt")
        for required in (
            "preserveCaller = true",
            "LinkedTaskReturn",
            "linkedCrossAppRouteReturnsDirectlyToItsCallerContext",
            "ChartLibraryDestinations",
        ):
            self.assertIn(required, chart + preferences + activity + reducer + tests)
        self.assertNotIn("map-open-chart-library", chart)
        self.assertNotIn("preferences-open-chart-library", preferences)

    def test_rounded_edges_do_not_consume_full_vertical_bands(self):
        policy = self.read("core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt")
        tests = self.read("core/shell-contract/src/test/kotlin/com/yokuli/shell/contract/ShellWindowMetricsTest.kt")
        self.assertIn("top = metrics.safeInsets.top", policy)
        self.assertIn("bottom = maxOf(metrics.safeInsets.bottom, metrics.systemGestureInsets.bottom)", policy)
        self.assertIn("assertEquals(0, bands.status.top)", tests)
        self.assertIn("assertEquals(20, bands.navigation.bottom)", tests)

    def test_google_is_selected_by_explicit_map_view_not_chart_library_presence(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        google_branch = graph.split("val chartSurface", 1)[1].split("GoogleMarineChartSurface(", 1)[0].rsplit("if (", 1)[-1]
        self.assertIn("chartSurfaceKind", google_branch)
        self.assertIn("GOOGLE_MAPS_CONFIGURED", google_branch)
        self.assertNotIn("ChartDisplaySelection", google_branch)
        self.assertNotIn("activeChartPackageId", google_branch)

    def test_current_product_keeps_every_completed_app_and_portrait_shell(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        installed = re.findall(r"catalogContribution\s*=\s*([A-Z][A-Za-z]+ShellContribution)", graph)
        self.assertEqual(
            ["ChartShellContribution", "PreferencesShellContribution", "DataShellContribution",
             "ChartLibraryShellContribution", "NavigationShellContribution"],
            installed,
        )
        manifest = self.read("app-shell/src/main/AndroidManifest.xml")
        self.assertIn('android:screenOrientation="portrait"', manifest)
        self.assertNotIn("android.intent.category.HOME", manifest)

    def test_named_ui_and_navigation_stories_exist(self):
        tests = "\n".join(path.read_text(encoding="utf-8") for path in ROOT.rglob("*Test.kt"))
        for required in (
            "threeAppOwnedTileSizesRenderInBothThemesAndLargeType",
            "libraryIsDiscoverableButNotAutoPinnedAndRootBackStopsAtStart",
            "shellDestinationRestoresAttentionFilterAndOpaqueAssetDetail",
            "linkedCrossAppRouteReturnsDirectlyToItsCallerContext",
        ):
            self.assertIn(required, tests)


if __name__ == "__main__":
    unittest.main()
