from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ChartLibraryCl11ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_provider_enumeration_is_bounded_before_materialization(self):
        source = self.read("adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartDocumentEnumerator.kt")
        tests = self.read("adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/AndroidChartDocumentEnumeratorTest.kt")
        self.assertIn("maximumRows", source)
        self.assertIn("children.truncated", source)
        self.assertIn("TIME_BUDGET_REACHED", source)
        self.assertIn("providerRowsAreBoundedBeforeTheyCanBuildAnUnboundedChildList", tests)

    def test_runtime_read_sessions_and_waiters_are_bounded_and_measured(self):
        runtime = self.read("adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartLibraryRuntime.kt")
        contract = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartLibraryRuntimeContracts.kt")
        tests = self.read("adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/ChartLibraryRuntimeAndroidTest.kt")
        for required in ("MAX_OPEN_READ_SESSIONS = 12", "MAX_PENDING_READ_REQUESTS = 12", "ChartReadFailure.RESOURCE_LIMIT"):
            self.assertIn(required, runtime)
        self.assertIn("permitTransferred", runtime)
        self.assertIn("openedSession?.close()", runtime)
        for required in ("sourceBytesRead", "tileQueries", "rejectedReadRequests", "queuedReadHighWater"):
            self.assertIn(required, contract)
        self.assertIn("pendingReadQueueRejectsOverflowWithoutCreatingUnboundedSessions", tests)
        self.assertIn("closedSessionsPublishBoundedReadEvidence", tests)

    def test_loopback_gateway_has_no_unbounded_executor_or_registration_table(self):
        gateway = self.read("adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/ChartLoopbackTileGateway.kt")
        tests = self.read("adapter/map-offline/src/test/java/com/yokuli/marine/map/offline/ChartLoopbackTileGatewayTest.kt")
        self.assertIn("ArrayBlockingQueue(MAX_QUEUED_REQUESTS)", gateway)
        self.assertIn("MAX_REGISTERED_ASSETS = 8", gateway)
        self.assertNotIn("Executors.newFixedThreadPool", gateway)
        self.assertIn("registrationTableAndReportedWorkerStateStayBounded", tests)

    def test_source_failure_does_not_poison_another_membership(self):
        planner = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartScanContracts.kt")
        tests = self.read("core/map-domain/src/test/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartSourceScanPlannerTest.kt")
        self.assertIn("membershipsToRemove", planner)
        self.assertIn("completedScanRemovesOnlyItsMembershipWhenAnotherSourceStillOwnsTheAsset", tests)
        self.assertIn("revokedSourceDoesNotInvalidateAnAssetStillReadableThroughAnotherGrant", tests)

    def test_validation_and_scan_coordination_use_fixed_lock_stripes(self):
        validation = self.read("adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartValidationController.kt")
        source = self.read("adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartSourceController.kt")
        self.assertIn("Array(LOCK_STRIPES)", validation)
        self.assertIn("Array(LOCK_STRIPES)", source)
        self.assertNotIn("computeIfAbsent(assetId.value) { Mutex() }", validation)
        self.assertNotIn("computeIfAbsent(sourceId.value) { Mutex() }", source)
        self.assertIn("MAX_SCAN_MUTATIONS_PER_TRANSACTION = 1_000", source)
        self.assertIn("contentMutations.chunked", source)
        source_tests = self.read("adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/AndroidChartSourceControllerTest.kt")
        self.assertIn("largeScanPublishesInBoundedTransactionsAndNeverRemainsRunning", source_tests)

    def test_ci_emits_a_machine_readable_measurement_manifest(self):
        workflow = self.read(".github/workflows/android.yml")
        extractor = self.read(".github/scripts/extract_chart_library_cl11_evidence.py")
        report_builder = self.read(".github/scripts/build_codex_job_report.py")
        self.assertIn("extract_chart_library_cl11_evidence.py", workflow)
        self.assertIn("build/chart-library-cl11/evidence.json", workflow)
        self.assertIn("--require-complete", workflow)
        self.assertIn("chart-library-cl11-evidence.json", report_builder)
        for scenario in ("zero-copy-read", "runtime-bounds", "catalog-1000"):
            self.assertIn(scenario, extractor)

    def test_report_and_baseline_do_not_claim_unrun_physical_evidence(self):
        report = self.read("docs/phases/chart-library/CL11_REPORT.md")
        baseline = self.read("docs/phases/chart-library/CL11_BASELINE_LOCK.json")
        self.assertIn("GITHUB_ACTION_PENDING", report)
        self.assertIn("NOT_RUN_PHYSICAL_DEVICE", report)
        self.assertIn('"physical_30_minute_soak_verified": false', baseline)
        self.assertNotIn('"verification": "PASS"', baseline)


if __name__ == "__main__":
    unittest.main()
