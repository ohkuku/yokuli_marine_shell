#!/usr/bin/env python3
"""C11 production contract: useful low-cost Chart tiles and narrow Shell integration."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ChartC11ContractTest(unittest.TestCase):
    def test_chart_declares_exactly_three_owned_sizes(self):
        source = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartShellContribution.kt").read_text()
        for size in ("ICON_1X1", "STANDARD_2X2", "WIDE_4X2"):
            self.assertIn(f"MarineTileSize.{size}", source)

    def test_launcher_projection_has_truth_priority_and_pure_route_preview(self):
        source = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartLauncherProjection.kt").read_text()
        for term in ("WRITE_FAILURE", "UNSAVED", "EDITING_DRAFT", "SELECTED_PLAN", "LAST_VIEW", "ENTRY"):
            self.assertIn(term, source)
        self.assertIn("TILES_AVAILABLE_CONTENT_UNVERIFIED", source)
        self.assertNotIn("NavigationSuitability.SUITABLE", source)

    def test_launcher_presentation_never_creates_a_map_renderer_or_network_client(self):
        source = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartLauncherPresentation.kt").read_text()
        for forbidden in ("MapView(", "MapLibreMap(", "HttpClient", "OkHttpClient", "LocationManager"):
            self.assertNotIn(forbidden, source)
        self.assertIn("ChartRouteMiniMap", source)

    def test_search_and_dynamic_tokens_stay_generic_at_shell_boundary(self):
        presentation = (ROOT / "ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt").read_text()
        self.assertIn("LauncherSearchResultContribution", presentation)
        for forbidden in ("SavedPlace", "SavedRoute", "GeoPoint", "MapState"):
            self.assertNotIn(forbidden, presentation)
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        self.assertIn("dynamicLaunchTokenMatcher", graph)
        self.assertIn("searchContributions", graph)

    def test_route_secondary_pin_is_not_faked(self):
        source = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartShellContribution.kt").read_text()
        self.assertNotIn("route-secondary", source)
        self.assertNotIn("PinRoute", source)


if __name__ == "__main__":
    unittest.main()
