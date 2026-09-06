from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ChartLibraryCl06ContractTest(unittest.TestCase):
    def test_runtime_is_process_owned_bounded_and_has_no_fgs(self):
        runtime = (ROOT / "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartLibraryRuntime.kt").read_text()
        app = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellApplication.kt").read_text()
        manifest = (ROOT / "adapter/chart-library-android/src/main/AndroidManifest.xml").read_text()
        for required in ("MAX_OPEN_READ_SESSIONS = 12", "BASIC_WORKERS = 2", "BASIC_QUEUE_CAPACITY = 256", "Semaphore"):
            self.assertIn(required, runtime)
        self.assertIn("ChartLibraryRuntimeOwner", app)
        self.assertIn("chartLibraryRuntime", app)
        for forbidden in ("foregroundService", "Nmea", "MapView", "Activity"):
            self.assertNotIn(forbidden, manifest + runtime)

    def test_revision_generation_and_usage_invalidate_sessions(self):
        runtime = (ROOT / "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartLibraryRuntime.kt").read_text()
        contract = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartReadContracts.kt").read_text()
        for required in (
            "requestIsCurrent", "invalidateChangedSessions", "sourceGeneration", "revision.cacheKey",
            "ChartReadPurpose", "ChartReadPurpose.VALIDATION", "readSessionHighWater",
        ):
            self.assertIn(required, runtime + contract)

    def test_runtime_tests_cover_lifecycle_restart_budget_and_stale_results(self):
        tests = (ROOT / "adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/ChartLibraryRuntimeAndroidTest.kt").read_text()
        for required in (
            "closingFeatureObserverDoesNotCloseChartReadLease",
            "catalogRevisionOrSourceGenerationInvalidatesOldSessionBeforeNewTilesCanReturn",
            "globalReadBudgetBlocksThirteenthSessionUntilOneLeaseCloses",
            "processRestartRestoresRunningValidationAsInterruptedWithoutRestoringFd",
            "changedAssetCanBeValidatedButCannotRenderUntilValidationPublishes",
        ):
            self.assertIn(required, tests)


if __name__ == "__main__":
    unittest.main()
