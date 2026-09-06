#!/usr/bin/env python3
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW14ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_chart_modes_are_app_owned_typed_and_truthfully_fallback(self):
        contribution = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartShellContribution.kt")
        policy = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartLiveTile.kt")
        preferences = self.read("feature/preferences/src/main/java/com/yokuli/marine/feature/preferences/PreferencesWorkspace.kt")
        for mode in ("AUTO", "MAP", "NAVIGATION", "POSITION", "STATIC"):
            self.assertIn(mode, contribution)
        self.assertIn('AppPreferenceKey("chart.tile.mode")', contribution)
        self.assertIn("AppPreferenceDefinition.Choice", contribution)
        self.assertIn("ChartTileFrameKind.NAVIGATION, ChartTileFrameKind.MAP", policy)
        self.assertIn("CHART_TILE_ROTATION_MILLIS = 5_000L", policy)
        self.assertIn("definition.options.forEach", preferences)
        self.assertNotIn(":feature:chart", self.read("feature/preferences/build.gradle.kts"))

    def test_real_renderers_produce_one_bounded_snapshot_and_tiles_do_not_own_a_map(self):
        contract = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapTileSnapshot.kt")
        chart = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartLauncherPresentation.kt")
        google = self.read("adapter/chart-google/src/main/java/com/yokuli/marine/adapter/chart/google/GoogleMarineChartSurface.kt")
        offline = self.read("adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt")
        app = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ShellApplication.kt")
        for value in (
            "BoundedMapTileSnapshotRuntime", "encodedBytes.copyOf()", "request != currentRequest",
            "DEFAULT_MAXIMUM_TILE_SNAPSHOT_BYTES = 256 * 1024",
        ):
            self.assertIn(value, contract)
        for renderer in (google, offline):
            self.assertIn("readyMap.snapshot", renderer)
            self.assertIn("TILE_SNAPSHOT_CAPTURE_INTERVAL_MILLIS = 5_000L", renderer)
            self.assertIn("TILE_SNAPSHOT_WIDTH = 320", renderer)
            self.assertIn("TILE_SNAPSHOT_HEIGHT = 180", renderer)
        self.assertIn("BoundedMapTileSnapshotRuntime()", app)
        for forbidden in ("GoogleMap", "MapView", "MapLibreMap", "ChartRouteMiniMap"):
            self.assertNotIn(forbidden, chart)

    def test_other_tiles_use_existing_truth_with_bounded_presentation(self):
        library = self.read("feature/chart-library/src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryLauncherPresentation.kt")
        data = self.read("feature/data/src/main/java/com/yokuli/marine/feature/data/DataLauncherPresentation.kt")
        navigation = self.read("feature/navigation/src/main/java/com/yokuli/marine/feature/navigation/NavigationLauncherPresentation.kt")
        preferences = self.read("feature/preferences/src/main/java/com/yokuli/marine/feature/preferences/PreferencesLauncherPresentation.kt")
        for value in ("visibleLayerCount", "visibleLayerNames", "displayWarningCount", "ChartDisplayPlan"):
            self.assertIn(value, library)
        for value in ("importantValues", "DataKey.Position", "DataKey.SpeedOverGround", ".take(3)"):
            self.assertIn(value, data)
        for forbidden in ("rawPreview", "SentenceCatalog", "RawPreview"):
            self.assertNotIn(forbidden, data)
        for value in (
            "NavigationTileMode.ACTIVE", "NavigationTileMode.RECENT_ROUTE",
            "distanceToWaypointNauticalMiles", "bearingToWaypointTrueDegrees",
            "crossTrackErrorNauticalMiles", "NavigationRouteMath.summarize",
        ):
            self.assertIn(value, navigation)
        self.assertIn("PresentationCadence.StartTile", library)
        self.assertIn("PresentationCadence.StartTile", data)
        self.assertIn("PresentationCadence.StartTile", navigation)
        self.assertNotIn("rememberCadencedLiveValue", preferences)

    def test_acceptance_tests_and_documents_lock_the_product_contract(self):
        tests = "\n".join(path.read_text(encoding="utf-8") for path in ROOT.rglob("*Test.kt"))
        for story in (
            "late and oversized renderer snapshots cannot replace the bounded latest image",
            "auto rotates navigation and real map while deterministic fallbacks stay truthful",
            "chart owns a bounded typed preference for every supported tile mode",
            "visible layer names and display warnings come from the current display plan",
            "tile projects bounded resolved values rather than raw sentence traffic",
            "active navigation projects next waypoint dtw btw and xte without route draft UI",
            "inactive tile describes the most recent saved route and edit freeze yields to alerts",
        ):
            self.assertIn(story, tests)
        contract = self.read("docs/phases/os-redesign/work-packages/W14_PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/os-redesign/W14_REPORT.md")
        lock = json.loads(self.read("docs/phases/os-redesign/W14_BASELINE_LOCK.json"))
        state = json.loads(self.read("docs/phases/os-redesign/EXECUTION_STATE.json"))
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 领域关系",
            "Opportunities / 可发展空间", "Non-negotiables / 不可协商边界",
            "Forbidden outcomes / 禁止结果", "Acceptance stories", "Evidence required",
            "Existing code landmarks", "Implementation freedom / 实现自由", "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertEqual(1000, lock["numericCadenceMillis"])
        self.assertEqual(5000, lock["rotationCadenceMillis"])
        self.assertEqual("IMPLEMENTED_CI_PENDING", state["workPackages"]["W14"]["status"])


if __name__ == "__main__":
    unittest.main()
