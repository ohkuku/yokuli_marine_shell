import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class ChartC04ContractTest(unittest.TestCase):
    def test_geographiclib_is_version_pinned_and_spherical_shortcut_is_removed(self):
        versions = (ROOT / "gradle/libs.versions.toml").read_text()
        build = (ROOT / "core/map-domain/build.gradle.kts").read_text()
        model = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapModel.kt").read_text()
        self.assertIn('geographiclib = "2.1"', versions)
        self.assertIn("libs.geographiclib", build)
        self.assertNotIn("greatCircleNauticalMiles", model)

    def test_coordinate_measurement_and_dateline_contracts_are_executable(self):
        tests = "\n".join(
            path.read_text()
            for path in (ROOT / "core/map-domain/src/test").rglob("*Test.kt")
        )
        for phrase in (
            "equator degree matches",
            "official GeographicLib difficult inverse sample",
            "negative zero retains",
            "short antimeridian segment",
            "insert delete move clear and undo redo",
            "measurement conversion copies coordinates",
        ):
            self.assertIn(phrase, tests)

    def test_renderer_has_distinct_measurement_handles_and_geodesic_parts(self):
        renderer = (
            ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt"
        ).read_text()
        self.assertIn("MEASUREMENT_POINTS", renderer)
        self.assertIn("Wgs84Polyline.build", renderer)
        self.assertIn("BeginPointDrag", renderer)
        self.assertIn("CommitPointDrag", renderer)

    def test_chart_exposes_real_measurement_results_and_precise_coordinate_entry(self):
        workspace = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        strings = (ROOT / "feature/chart/src/main/res/values/strings.xml").read_text()
        self.assertIn("MeasurementMath.summarize", workspace)
        self.assertIn("CoordinateCodec.parse", workspace)
        self.assertIn("map_measure_total", strings)
        self.assertIn("map_coordinate_input", strings)
        self.assertNotIn("count.toString()", workspace)


if __name__ == "__main__":
    unittest.main()
