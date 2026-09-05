import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class ChartC09ContractTest(unittest.TestCase):
    def test_domain_separates_tiles_content_and_navigation_truth(self):
        source = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/OfflineCoverage.kt").read_text()
        for symbol in (
            "OfflineCoverageRequest",
            "OfflineCoverageFingerprint",
            "TileAvailability",
            "ContentFootprint",
            "NavigationSuitability.NOT_ASSESSED",
            "MAX_REQUIRED_TILE_KEYS",
        ):
            self.assertIn(symbol, source)

    def test_real_index_checks_exact_target_zoom_not_package_bounds(self):
        source = (
            ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/AndroidChartCoverageIndex.kt"
        ).read_text()
        self.assertIn("tile_column", source)
        self.assertIn("tile_row", source)
        self.assertIn("zoom_level", source)
        self.assertIn("MBTILES_TMS", source)
        self.assertNotIn("coverage.contains", source)

    def test_coordinator_owns_one_job_and_rejects_stale_fingerprint(self):
        source = (
            ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/OfflineCoverageCoordinator.kt"
        ).read_text()
        for symbol in ("activeJob", "generation", "fingerprint", "Cancelled", "TooLarge"):
            self.assertIn(symbol, source)

    def test_ui_never_calls_it_a_safety_route_check(self):
        chinese = (ROOT / "feature/chart/src/main/res/values/strings.xml").read_text()
        english = (ROOT / "feature/chart/src/main/res/values-en/strings.xml").read_text()
        for phrase in ("离线资料检查", "适航性未评估", "有效图面未核验", "导入离线包"):
            self.assertIn(phrase, chinese)
        for forbidden in ("安全航线检查", "SAFE ROUTE", "navigation safe"):
            self.assertNotIn(forbidden, chinese + english)

    def test_provider_capability_does_not_offer_unimplemented_download(self):
        source = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/ChartSourceCatalog.kt").read_text()
        workspace = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        self.assertIn("IMPORT_ONLY", source)
        self.assertIn("BLOCKED_EXTERNAL", source)
        self.assertIn("NOAA_NCDS", source)
        self.assertNotIn("tile.openstreetmap.org", source + workspace)
        self.assertNotIn("map-coverage-download", workspace)


if __name__ == "__main__":
    unittest.main()
