import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class ChartC07ContractTest(unittest.TestCase):
    def test_domain_separates_logical_package_version_and_content_identity(self):
        repository = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/ChartPackageRepository.kt").read_text()
        model = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapModel.kt").read_text()
        self.assertIn("ChartPackageLogicalId", repository + model)
        self.assertIn("ChartPackageVersionId", repository + model)
        self.assertIn("ChartPackageValidationLevel", repository)
        self.assertIn("FULL_TILE_DECODED", repository)
        self.assertIn("logicalId", model)
        self.assertIn("versionId", model)

    def test_repository_has_progress_recovery_version_and_lease_contracts(self):
        contract = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/ChartPackageRepository.kt").read_text()
        implementation = (ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/AndroidMbTilesRepository.kt").read_text()
        for symbol in (
            "ChartPackageOperationId",
            "ChartPackageInspectProgress",
            "suspend fun reconcile()",
            "suspend fun rollback(",
            "fun acquireLease(",
            "InstallCheckpoint",
            "install-journal",
        ):
            self.assertIn(symbol, contract + implementation)
        self.assertIn("ensureActive()", implementation)
        self.assertNotIn("raw.copyTo(output)", implementation)
        renderer = (ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt").read_text()
        self.assertIn("acquirePackageLease", renderer)
        self.assertIn("lease?.close()", renderer)

    def test_validation_supports_views_derivation_and_png_jpeg_only(self):
        implementation = (ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/AndroidMbTilesRepository.kt").read_text()
        metadata = (ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/MbTilesMetadata.kt").read_text()
        tests = (ROOT / "adapter/map-offline/src/androidTest/java/com/yokuli/marine/map/offline/AndroidMbTilesRepositoryTest.kt").read_text()
        self.assertIn("type IN ('table','view')", implementation)
        self.assertIn("deriveTileFacts", implementation)
        self.assertEqual('setOf("png", "jpg", "jpeg")', metadata.split("rasterFormats = ", 1)[1].splitlines()[0].strip())
        for phrase in (
            "viewsSchemaAndMissingRecommendedMetadataAreDerived",
            "invalidCoordinatesDuplicatesAndCorruptPayloadsAreRejected",
            "crashJournalReconciliationKeepsAnUsableVersion",
        ):
            self.assertIn(phrase, tests)

    def test_coordinator_generation_makes_cancel_and_latest_selection_authoritative(self):
        contract = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartImportUiContract.kt").read_text()
        coordinator = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartPackageCoordinator.kt").read_text()
        tests = (ROOT / "feature/chart/src/test/java/com/yokuli/marine/feature/chart/ChartPackageCoordinatorTest.kt").read_text()
        for state in ("Copying", "Inspecting", "ReadyToInstall", "Installing", "Cancelled", "Failed"):
            self.assertIn(f"data class {state}", contract)
        self.assertIn("operationGeneration", coordinator)
        self.assertIn("activeJob?.cancel()", coordinator)
        self.assertIn("lateInspectCompletionCannotReplaceNewerSelection", tests)
        self.assertIn("cancelIsNotReportedAsFailure", tests)

    def test_release_copy_is_truthful_about_local_validation_and_unknown_legal_facts(self):
        chinese = (ROOT / "feature/chart/src/main/res/values/strings.xml").read_text()
        english = (ROOT / "feature/chart/src/main/res/values-en/strings.xml").read_text()
        self.assertIn("完整解码验证", chinese)
        self.assertIn("full tile decode validation", english)
        self.assertIn("未知（可由用户声明）", chinese)
        self.assertIn("Unknown (may be user-declared)", english)
        self.assertNotIn("官方认证", chinese)
        self.assertNotIn("officially certified", english.lower())


if __name__ == "__main__":
    unittest.main()
