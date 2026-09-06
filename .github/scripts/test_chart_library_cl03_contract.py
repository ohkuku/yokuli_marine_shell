from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ChartLibraryCl03ContractTest(unittest.TestCase):
    def test_core_catalog_contract_is_platform_free_and_layered(self):
        contract = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartCatalogContracts.kt").read_text()
        for required in (
            "ChartLibrarySourceKind",
            "ChartDocumentIdentity",
            "ChartContentRevision",
            "ChartAssetAccessState",
            "ChartAssetValidationState",
            "DISCOVERED",
            "BASIC_READABLE",
            "FULL_VERIFIED",
            "ChartCatalogTransaction",
            "ChartCatalogPage",
        ):
            self.assertIn(required, contract)
        self.assertNotRegex(contract, r"\b(?:android|androidx|room)\.")
        self.assertIn("MAX_CATALOG_PAGE_SIZE = 100", contract)

    def test_room_is_the_only_writable_catalog_and_stores_no_chart_blob(self):
        database = (ROOT / "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/ChartCatalogDatabase.kt").read_text()
        repository = (ROOT / "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/RoomChartCatalogRepository.kt").read_text()
        self.assertIn("@Database", database)
        self.assertIn("@Upsert", database)
        self.assertIn("database.withTransaction", repository)
        self.assertIn("ChartCatalogTransactionEntity", database + repository)
        self.assertNotIn("tile_data", database.lower())
        self.assertNotRegex(database, r"ByteArray|\bBLOB\b")

    def test_catalog_device_tests_cover_identity_restart_paging_and_failures(self):
        test = (ROOT / "adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/RoomChartCatalogRepositoryTest.kt").read_text()
        for required in (
            "transactionIsAtomicPagedAndSurvivesRestart",
            "confirmedDocumentIdentityDeduplicatesMembershipAliases",
            "validationUnknownFactsAndLegacyMappingRoundTripIdempotently",
            "optimisticConflictAndClosedDatabaseFailureDoNotPublishPartialState",
        ):
            self.assertIn(required, test)


if __name__ == "__main__":
    unittest.main()
