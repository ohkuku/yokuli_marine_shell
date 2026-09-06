from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ChartLibraryCl05ContractTest(unittest.TestCase):
    def test_validation_is_layered_revision_bound_and_cancellable(self):
        contract = "\n".join(
            (ROOT / f"core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/{name}").read_text()
            for name in ("ChartValidationContracts.kt", "ChartReadContracts.kt")
        )
        for required in (
            "ChartBasicInspector", "ChartFullVerifier", "ChartRevisionProbe", "ChartVerificationProgress",
            "ChartBasicInspectionResult", "ChartFullVerificationResult", "REVISION_CHANGED", "Cancelled",
            "MessageDigest", "MAX_VALIDATION_PAGE_SIZE",
        ):
            self.assertIn(required, contract)
        self.assertIn("contentSha256 = sha", contract)
        self.assertIn("shouldCancel()", contract)

    def test_reader_limits_payloads_and_uses_shared_coordinate_mapping(self):
        read = (ROOT / "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartResourceAccess.kt").read_text()
        mapping = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartValidationContracts.kt").read_text()
        gateway = (ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/ChartLoopbackTileGateway.kt").read_text()
        coverage = (ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/ChartLibraryCoverageIndex.kt").read_text()
        self.assertIn("type='table' OR type='view'", read)
        self.assertIn("CASE WHEN length(tile_data)<=?", read)
        self.assertIn("MAX_TILE_BYTES", read)
        self.assertIn("ChartTileCoordinateMapper.storageRow", (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartReadContracts.kt").read_text())
        self.assertIn("session.readTile", gateway)
        self.assertIn("session.hasTile", coverage)
        self.assertIn("MapTileScheme.MBTILES_TMS", mapping)
        self.assertNotRegex(read, r"\b(?:CREATE|UPDATE|DELETE|VACUUM|ATTACH)\s+(?:TABLE|tiles|DATABASE)")

    def test_device_and_core_tests_cover_required_formats_views_and_safety(self):
        android = (ROOT / "adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/ChartValidationAndroidTest.kt").read_text()
        core = (ROOT / "core/map-domain/src/test/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartValidationTest.kt").read_text()
        migration = (ROOT / "adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/ChartCatalogMigrationTest.kt").read_text()
        for required in (
            "basicInspectionSupportsPngJpegWebpAnd256Or512Tiles",
            "tilesViewAndXyzAreQueriedWithoutRewritingTheOriginal",
            "unknownSchemeIsRejectedAndBasicNeverClaimsFullVerification",
            "explicitFullVerificationReadsAllTilesAndBindsShaToTheObservedRevision",
            "oversizedRasterIsRejectedBeforeBlobMaterialization",
            "controllerPublishesBasicThenFullAsSeparateCatalogTruth",
            "fullVerificationCancellationNeverHashesOrPublishesVerified",
            "revisionChangeDuringFullVerificationRejectsResult",
            "fullVerificationRejectsDuplicateAndInvalidCoordinates",
            "catalogV1MigratesToV2WithoutDestructiveReset",
        ):
            self.assertIn(required, android + core + migration)


if __name__ == "__main__":
    unittest.main()
