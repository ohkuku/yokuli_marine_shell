from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ChartLibraryCl09ContractTest(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text()

    def test_existing_managed_store_is_catalogued_without_a_second_payload_store(self):
        contract = self.read(
            "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/ChartPackageRepository.kt"
        )
        repository = self.read(
            "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/AndroidMbTilesRepository.kt"
        )
        bridge = self.read(
            "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/ManagedChartCatalogBridge.kt"
        )
        runtime = self.read(
            "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartLibraryRuntime.kt"
        )
        for required in (
            "ManagedChartPackageStoreSnapshot",
            "activeByLogicalId",
            "historyByLogicalId",
            "managedStoreSnapshot",
            "ManagedChartCatalogBridge",
            "yokuli-managed://",
            'File(context.filesDir, "map_packages")',
        ):
            self.assertIn(required, contract + repository + bridge + runtime)
        self.assertNotIn("copySource(", bridge)

    def test_managed_reads_are_revision_bound_read_only_and_lease_protected(self):
        access = self.read(
            "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartResourceAccess.kt"
        )
        reader = self.read(
            "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidSafRandomAccessReader.kt"
        )
        tests = self.read(
            "adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/SafFdMbTilesReaderTest.kt"
        )
        for required in (
            "managedRoot",
            "acquireManagedLease",
            "contentSha256",
            "canonicalFile",
            "MODE_READ_ONLY",
            "onClose",
            "managedLocatorOpensOnlyInsidePrivateStoreAndHoldsLeaseUntilSessionClose",
        ):
            self.assertIn(required, access + reader + tests)

    def test_copy_is_explicit_bounded_cancellable_and_never_selects_itself(self):
        runtime = self.read(
            "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartLibraryRuntime.kt"
        )
        coordinator = self.read(
            "feature/chart-library/src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryCoordinator.kt"
        )
        workspace = self.read(
            "feature/chart-library/src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryWorkspace.kt"
        )
        map_reducer = self.read(
            "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapReducer.kt"
        )
        for required in (
            "SaveManagedCopyConfirmation",
            "ConfirmManagedCopy",
            "MAX_ACTIVE_COPY_JOBS = 1",
            "MAX_QUEUED_COPY_JOBS = 8",
            "copyBudget.withPermit",
            "cancelManagedCopy",
            "INSUFFICIENT_SPACE",
            "ChartDisplayPreferencesChanged",
            "copy_confirm_safety",
        ):
            self.assertIn(required, runtime + coordinator + workspace + map_reducer)
        self.assertNotIn("ChartDisplayPreferencesChanged", runtime)

    def test_copy_relationship_and_delete_confirmation_are_durable_catalog_truth(self):
        database = self.read(
            "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/ChartCatalogDatabase.kt"
        )
        catalog = self.read(
            "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartCatalogContracts.kt"
        )
        coordinator = self.read(
            "feature/chart-library/src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryCoordinator.kt"
        )
        for required in (
            "ChartManagedCopyRelation",
            "PutManagedCopyRelation",
            "chart_managed_copy_relations",
            "CHART_CATALOG_MIGRATION_2_3",
            "DeleteManagedCopyConfirmation",
            "ConfirmationRequired",
            "ACTIVE_LEASE",
        ):
            self.assertIn(required, database + catalog + coordinator)

    def test_legacy_display_choice_migrates_once_but_explicit_none_wins_forever(self):
        model = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapModel.kt")
        proto = self.read("adapter/map-storage/src/main/proto/map_state.proto")
        coordinator = self.read(
            "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartDisplayCoordinator.kt"
        )
        tests = "\n".join(path.read_text() for path in ROOT.rglob("*Test.kt"))
        for required in (
            "chartDisplayPreferencesInitialized",
            "chart_display_preferences_initialized",
            "resolveLegacyAsset",
            "legacy active package migrates once and explicit none is never stolen back",
            "managedStoreSnapshotPreservesActiveVersionAndHistoryWithoutRecopyingPublishedPayloads",
            "everyInstallJournalBoundaryRecoversOneCompleteLogicalPackage",
            "everyCatalogMigrationCheckpointResumesWithoutCopyOrMissingLegacyChoice",
            "copyFailureKeepsOldPackageAndLeavesNoStagingDirectory",
            "managedCopyDoesNotStartBeforeExplicitWholeFileConfirmation",
        ):
            self.assertIn(required, model + proto + coordinator + tests)

    def test_cl09_does_not_modify_unrelated_user_collections_or_android_launcher_role(self):
        manifest = self.read("app-shell/src/main/AndroidManifest.xml")
        changed_contracts = "\n".join(
            self.read(path)
            for path in (
                "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/ChartPackageRepository.kt",
                "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartCatalogContracts.kt",
                "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartLibraryRuntimeContracts.kt",
            )
        )
        for forbidden in ("CATEGORY_HOME", "NmeaRuntimeCommand", "SavedPlace", "SavedRoute", "RecordedTrack"):
            self.assertNotIn(forbidden, manifest + changed_contracts)
        self.assertIn('android:screenOrientation="portrait"', manifest)


if __name__ == "__main__":
    unittest.main()
