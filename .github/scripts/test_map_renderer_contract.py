import hashlib
import json
from pathlib import Path
import re
import sqlite3
import unittest


ROOT = Path(__file__).resolve().parents[2]
FIXTURE_ROOT = ROOT / "adapter/map-offline/src/androidTest/assets/fixtures"


class MapRendererContractTest(unittest.TestCase):
    def test_maplibre_is_the_only_production_renderer(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        app_build = (ROOT / "app-shell/build.gradle.kts").read_text()
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        manifest = (ROOT / "app-shell/src/main/AndroidManifest.xml").read_text()

        self.assertIn('implementation(project(":adapter:map-offline"))', app_build)
        self.assertNotIn('implementation(project(":adapter:chart-google"))', app_build)
        self.assertIn("OfflineMarineChartSurface", graph)
        self.assertNotIn("GoogleMarineChartSurface", graph)
        self.assertNotIn("GOOGLE_MAPS_ANDROID_API_KEY", app_build + manifest)
        self.assertNotIn("GOOGLE_MAPS_CONFIGURED", app_build + graph)

        # Historical code remains build-isolated and cannot become a second production path.
        self.assertIn('\":adapter:chart-google\"', settings)
        self.assertTrue((ROOT / "adapter/chart-google/build.gradle.kts").is_file())

    def test_renderer_protocol_is_sdk_free_and_distinguishes_readiness_from_coverage(self):
        contract = (
            ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapRendererContract.kt"
        ).read_text()
        reducer = (
            ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapReducer.kt"
        ).read_text()
        for required in (
            "MapRendererGeneration",
            "MapCameraCommandId",
            "MapCameraCommand",
            "MapViewportInsets",
            "MapRendererQueryPort",
            "MapRendererReadiness",
            "MapTileCoverageStatus",
            "MapOverlayId",
            "RendererHostReady",
            "RendererReady",
            "RendererCameraIdle",
        ):
            self.assertIn(required, contract + reducer)
        self.assertNotRegex(contract, r"\b(?:android|androidx|maplibre|google)\.")

    def test_adapter_is_local_only_and_owns_real_mapview_lifecycle(self):
        surface = (
            ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt"
        ).read_text()
        for required in (
            "MapView(context)",
            "RasterSource(CHART_SOURCE, activePackage.localUri",
            'uri.scheme == "mbtiles"',
            "isScrollGesturesEnabled = true",
            "isZoomGesturesEnabled = true",
            "isRotateGesturesEnabled = true",
            "isTiltGesturesEnabled = false",
            "mapView.onLowMemory()",
            "mapView.onDestroy()",
        ):
            self.assertIn(required, surface)
        self.assertNotRegex(surface, r'(?i)https?://')
        self.assertEqual(1, surface.count("AndroidView(factory = { mapView }"))

    def test_real_renderer_tests_assert_pixels_not_only_view_presence(self):
        test = (
            ROOT / "adapter/map-offline/src/androidTest/java/com/yokuli/marine/map/offline/MapLibreMbTilesRenderTest.kt"
        ).read_text()
        for required in (
            "map.snapshot",
            "localMbTilesActuallyRendersDirectionalPixelsAndStableOverlayWithoutNetworkStyle",
            "tracedNoaaSubsetRendersRecognisableChartPaletteFromLocalMbTiles",
            "bitmap.getPixel",
            "near(expected",
            "mbtiles://",
        ):
            self.assertIn(required, test)
        self.assertNotIn("assertNotNull(activity.mapView)", test)

    def test_noaa_subset_and_each_copied_tile_are_hash_bound(self):
        provenance_path = FIXTURE_ROOT / "NOAA_NCDS_21_SOURCE.json"
        fixture_path = FIXTURE_ROOT / "noaa_ncds21_real_chart_subset.mbtiles"
        provenance = json.loads(provenance_path.read_text())
        self.assertEqual(
            provenance["fixtureSha256"],
            hashlib.sha256(fixture_path.read_bytes()).hexdigest(),
        )
        self.assertEqual("none", provenance["processing"]["pixelTransformation"])
        self.assertIn("not an official NOAA chart", provenance["useRestriction"])
        self.assertTrue(provenance["officialLandingPage"].startswith("https://distribution.charts.noaa.gov/"))

        expected = {
            (tile["z"], tile["x"], tile["tmsY"]): tile["sha256"]
            for tile in provenance["processing"]["tiles"]
        }
        with sqlite3.connect(fixture_path) as database:
            rows = database.execute(
                "SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles"
            ).fetchall()
        actual = {
            (z, x, y): hashlib.sha256(data).hexdigest()
            for z, x, y, data in rows
        }
        self.assertEqual(expected, actual)

    def test_no_google_key_value_or_workflow_dependency_is_tracked(self):
        google_key = re.compile(r"AIza[0-9A-Za-z_-]{35}")
        offenders = []
        for path in ROOT.rglob("*"):
            if not path.is_file() or ".git" in path.parts or "build" in path.parts:
                continue
            if path.suffix in {".age", ".png", ".jpg", ".jpeg", ".webp", ".apk", ".aab", ".mbtiles"}:
                continue
            try:
                text = path.read_text()
            except UnicodeDecodeError:
                continue
            if google_key.search(text):
                offenders.append(str(path.relative_to(ROOT)))
        self.assertEqual([], offenders, "tracked-looking text contains a Google API key value")

        workflow_text = "\n".join(path.read_text() for path in (ROOT / ".github/workflows").glob("*.yml"))
        self.assertNotIn("GOOGLE_MAPS_ANDROID_API_KEY", workflow_text)


if __name__ == "__main__":
    unittest.main()
