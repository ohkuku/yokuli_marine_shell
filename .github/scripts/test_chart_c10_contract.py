import json
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class ChartC10ContractTest(unittest.TestCase):
    def test_c10_report_and_baseline_lock_are_sealed(self):
        report = (ROOT / "docs/phases/chart-wp8-refinement/c10/REPORT.md").read_text()
        lock = json.loads((ROOT / "docs/phases/chart-wp8-refinement/c10/BASELINE_LOCK.json").read_text())
        self.assertEqual("C10", lock["package"])
        self.assertEqual("VERIFIED_LOCAL", lock["status"])
        self.assertEqual("NoSourcePositionPort", lock["productionPositionPort"])
        self.assertEqual("READ_ONLY_OBSERVATION", lock["capabilityBoundary"])
        self.assertIn("C10 NoSource", report)
        self.assertIn("English translation", report)
        self.assertIn("C11", report)

    def test_production_uses_read_only_no_source_port(self):
        contract = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/PositionObservation.kt").read_text()
        app = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellApplication.kt").read_text()
        self.assertIn("interface ReadOnlyPositionPort", contract)
        self.assertIn("NoSourcePositionPort", contract)
        self.assertIn("NoSourcePositionPort", app)
        self.assertNotIn("ReplayPosition", app)
        self.assertNotIn("FakePosition", app)

    def test_quality_components_and_monotonic_identity_are_explicit(self):
        source = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/PositionObservation.kt").read_text()
        for symbol in (
            "ObservationSource",
            "sourceEpoch",
            "observationId",
            "sequence",
            "MonotonicTime",
            "sampledAtUtcMillis",
            "SampleTimeConfidence",
            "HeadingReference",
            "CourseSpeedObservation",
            "horizontalAccuracyMeters",
        ):
            self.assertIn(symbol, source)

    def test_renderer_has_distinct_history_heading_course_and_accuracy_planes(self):
        contract = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/PositionObservation.kt").read_text()
        renderer = (
            ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt"
        ).read_text()
        for symbol in ("VesselMarkerStyle", "HISTORICAL", "trueHeadingDegrees", "courseVector", "accuracyMeters"):
            self.assertIn(symbol, contract)
        for overlay in ("POSITION_HISTORY", "TRUE_HEADING", "COURSE_OVER_GROUND", "POSITION_ACCURACY"):
            self.assertIn(overlay, renderer)

    def test_release_has_no_position_collection_or_vessel_output_permission(self):
        manifest = (ROOT / "app-shell/src/main/AndroidManifest.xml").read_text()
        production = "\n".join(
            p.read_text(errors="ignore")
            for base in (
                ROOT / "app-shell/src/main",
                ROOT / "feature/chart/src/main",
                ROOT / "adapter/map-offline/src/main",
            )
            for p in base.rglob("*")
            if p.is_file() and p.suffix in {".kt", ".xml"}
        )
        self.assertIn('android:name="android.permission.ACCESS_FINE_LOCATION" tools:node="remove"', manifest)
        self.assertIn('android:name="android.permission.ACCESS_COARSE_LOCATION" tools:node="remove"', manifest)
        self.assertNotIn("NmeaWriter", production)
        self.assertNotIn("Autopilot", production)
        self.assertNotIn("ReplayPosition", production)

    def test_ui_states_no_source_and_does_not_offer_an_unusable_follow_action(self):
        workspace = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        chinese = (ROOT / "feature/chart/src/main/res/values/strings.xml").read_text()
        english = (ROOT / "feature/chart/src/main/res/values-en/strings.xml").read_text()
        self.assertIn("无位置数据源", chinese)
        self.assertIn("NO POSITION SOURCE", english)
        self.assertIn("map-position-follow", workspace)
        self.assertIn("PositionSourceStatus.NoSource", workspace)
        self.assertNotIn("位置已启用", chinese)
        self.assertNotIn("POSITION ENABLED", english)


if __name__ == "__main__":
    unittest.main()
