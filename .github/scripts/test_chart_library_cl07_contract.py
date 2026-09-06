from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
FEATURE = ROOT / "feature/chart-library"


class ChartLibraryCl07ContractTest(unittest.TestCase):
    def test_feature_is_independent_and_only_consumes_core_ports(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        build = (FEATURE / "build.gradle.kts").read_text()
        manifest = (FEATURE / "src/main/AndroidManifest.xml").read_text()
        source = "\n".join(path.read_text() for path in (FEATURE / "src/main/java").rglob("*.kt"))

        self.assertIn('":feature:chart-library"', settings)
        self.assertIn('project(":core:map-domain")', build)
        for forbidden in ('project(":feature:chart")', 'project(":adapter:', "MapView", "<activity", "HOME"):
            self.assertNotIn(forbidden, build + manifest + source)

    def test_ui_contract_covers_source_first_management_and_truthful_progress(self):
        contract = (FEATURE / "src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryUiContract.kt").read_text()
        coordinator = (FEATURE / "src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryCoordinator.kt").read_text()
        workspace = (FEATURE / "src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryWorkspace.kt").read_text()
        projector = (FEATURE / "src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryProjector.kt").read_text()
        combined = contract + coordinator + workspace + projector
        for required in (
            "ChartLibraryUiState", "ChartLibraryUiAction", "ChartLibraryProjector", "ChartLibraryCoordinator",
            "AddFolder", "AddSingleFile", "RefreshSource", "CancelSourceScan", "RepairPermission",
            "SetSelectedEnabled", "SetAssetRole", "MoveAssetPriority", "InspectBasic", "VerifyFull",
            "CancelValidation", "ChartLibraryStorageUi", "ChartManagedCopyProgress", "managedCopyAvailable",
            "BindInternalAppInputHandler", "ChartLibraryBackPolicy",
        ):
            self.assertIn(required, combined)
        self.assertIn("MAX_PENDING_EVENTS = 64", coordinator)
        self.assertIn("MAX_SELECTED_ASSETS = 1_000", coordinator)
        self.assertIn("MAX_LOADED_ITEMS = 10_000", coordinator)
        self.assertIn("source.locator.providerAuthority()", projector)
        self.assertNotIn("locator = source.locator.value", projector)

    def test_runtime_exposes_repair_transactions_and_copy_capability_without_faking_copy(self):
        core_runtime = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartLibraryRuntimeContracts.kt").read_text()
        scan = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartScanContracts.kt").read_text()
        android_runtime = (ROOT / "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartLibraryRuntime.kt").read_text()
        source_controller = (ROOT / "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartSourceController.kt").read_text()
        self.assertIn("ChartManagedCopyCapability", core_runtime)
        self.assertIn("ChartManagedCopyPort", core_runtime)
        self.assertIn("suspend fun repair(sourceId: ChartSourceId, selection: ChartPickerSelection)", scan)
        self.assertIn("source.copy(locator = selection.locator", source_controller)
        self.assertIn("releaseIfUnused(source.locator)", source_controller)
        self.assertIn("ChartManagedCopyFailure.NOT_AVAILABLE", android_runtime)

    def test_bilingual_resources_and_tests_lock_the_independent_host_stories(self):
        zh = (FEATURE / "src/main/res/values/strings.xml").read_text()
        en = (FEATURE / "src/main/res/values-en/strings.xml").read_text()
        unit = "\n".join(path.read_text() for path in (FEATURE / "src/test").rglob("*.kt"))
        android = "\n".join(path.read_text() for path in (FEATURE / "src/androidTest").rglob("*.kt"))
        for required in ("海图库", "原地只读", "不会删除或修改外部文件", "重新授权"):
            self.assertIn(required, zh)
        for required in ("Chart Library", "read in place", "does not delete or modify external files", "reauthorize"):
            self.assertIn(required, en)
        for required in (
            "sourceFirstProjectionDoesNotExposeOpaqueLocator",
            "onePhysicalAssetInTwoExternalSourcesCountsStorageOnlyOnce",
            "selectedAssetMutationIsSerializedThroughCatalogTransaction",
            "pickerEffectMustReturnMatchingOpaqueOperationBeforeSourceIsAccepted",
            "emptyStandaloneHostCanAddSourcesWithoutChart",
            "permissionLostSourceOffersExplicitRepair",
            "rootBackIsOwnedByShellAndNeverExitsAndroid",
        ):
            self.assertIn(required, unit + android)

    def test_source_removal_copy_is_explicitly_non_destructive(self):
        controller = (ROOT / "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartSourceController.kt").read_text()
        self.assertIn("ChartCatalogMutation.RemoveSource", controller)
        for forbidden in ("deleteDocument", "DocumentsContract.deleteDocument", "File.delete("):
            self.assertNotIn(forbidden, controller)


if __name__ == "__main__":
    unittest.main()
