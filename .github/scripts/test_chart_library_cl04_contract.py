from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ChartLibraryCl04ContractTest(unittest.TestCase):
    def test_scan_contract_keeps_missing_truth_behind_complete_enumeration(self):
        contract = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartScanContracts.kt").read_text()
        for required in (
            "OpenTree", "OpenDocument", "ChartPickerSelection", "ChartEnumerationResult",
            "ChartSourceScanPlanner", "ChartScanStatus.COMPLETE", "ChartAssetAccessState.MISSING",
            "ChartGrantState.REVOKED", "ChartAssetAccessState.PERMISSION_LOST",
        ):
            self.assertIn(required, contract)
        self.assertIn("enumeration is ChartEnumerationResult.Complete", contract)
        self.assertNotRegex(contract, r"\b(?:android|androidx)\.")

    def test_android_scan_is_bounded_and_never_opens_product_workspaces(self):
        enumerator = (ROOT / "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartDocumentEnumerator.kt").read_text()
        controller = (ROOT / "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartSourceController.kt").read_text()
        for required in ("MAX_DOCUMENTS = 10_000", "MAX_DEPTH = 32", "TIME_BUDGET_MILLIS = 30_000L", "visited"):
            self.assertIn(required, enumerator)
        self.assertIn("Semaphore(2)", controller)
        self.assertIn("takePersistableUriPermission", controller)
        self.assertIn("releasePersistableUriPermission", controller)
        self.assertIn("operationEpoch", controller)
        combined = enumerator + controller
        for forbidden in ("ChartActivity", "MapView", "Nmea", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"):
            self.assertNotIn(forbidden, combined)

    def test_tests_cover_picker_scan_failure_cancel_identity_and_grants(self):
        planner = (ROOT / "core/map-domain/src/test/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartSourceScanPlannerTest.kt").read_text()
        enumerator = (ROOT / "adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/AndroidChartDocumentEnumeratorTest.kt").read_text()
        controller = (ROOT / "adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/AndroidChartSourceControllerTest.kt").read_text()
        for required in (
            "onlySuccessfulCompleteEnumerationCanMarkUnseenAssetsMissing",
            "stableDocumentRenamePreservesIdentityPreferencesAndValidation",
            "changedRevisionIsVisibleAndCannotRetainVerifiedState",
            "permissionLossIsNotMisreportedAsMissing",
            "recursivelyEnumeratesOnlyCandidatesAndKeepsProviderUnknownSize",
            "singleDocumentUsesSameEnumeratorAndCancellationNeverClaimsComplete",
            "failedSubtreeIsPartialAndDoesNotHideSuccessfulSiblings",
            "explicitCancelPublishesCancelledAndLateEnumerationCannotOverwriteIt",
            "repeatedPickerIsIdempotentAndRemovingOneGrantDoesNotReleaseAnother",
        ):
            self.assertIn(required, planner + enumerator + controller)


if __name__ == "__main__":
    unittest.main()
