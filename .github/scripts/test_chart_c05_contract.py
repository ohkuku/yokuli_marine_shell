import json
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class ChartC05ContractTest(unittest.TestCase):
    def test_place_model_actions_and_route_provenance_are_explicit(self):
        model = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapModel.kt").read_text()
        reducer = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapReducer.kt").read_text()
        for token in (
            "data class SavedPlace(",
            "val notes: String",
            "val category: PlaceCategory",
            "val tags: List<String>",
            "val createdAtMillis: Long",
            "val updatedAtMillis: Long",
            "val revision: Long",
            "waypointPlaceReferences: Map<Int, PlaceRevisionReference>",
            "RoutePlaceSourceState.MISSING",
        ):
            self.assertIn(token, model)
        for action in (
            "data class CreatePlace(",
            "data class UpdatePlace(",
            "data class BeginPlaceMove(",
            "data class PreviewPlaceMove(",
            "data object ConfirmPlaceMove",
            "data class RequestDeletePlace(",
            "data object UndoDeletePlace",
        ):
            self.assertIn(action, reducer)
        self.assertIn("unavailableIds", reducer)

    def test_local_search_and_ui_are_real_data_surfaces(self):
        search = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/PlaceSearch.kt").read_text()
        workspace = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        chinese = (ROOT / "feature/chart/src/main/res/values/strings.xml").read_text()
        english = (ROOT / "feature/chart/src/main/res/values-en/strings.xml").read_text()
        self.assertIn("filterAndSort", search)
        self.assertIn("searchAliases", search)
        for tag in (
            "map-places-search-field",
            "map-place-row-${place.id}",
            "map-place-detail-$id",
            "map-place-move-editor",
            "map-place-delete-confirmation",
            "map-place-export-$id",
        ):
            self.assertIn(tag, workspace)
        self.assertIn("只搜索本机资料，不访问网络", chinese)
        self.assertIn("Searches this device only. No network request", english)
        self.assertNotIn("online geocoder", workspace.lower())

    def test_room_v1_to_v2_schema_is_packaged_without_destructive_fallback(self):
        database = (ROOT / "adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/MapLibraryDatabase.kt").read_text()
        persistence = (ROOT / "adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/RoomMapPersistence.kt").read_text()
        build = (ROOT / "adapter/map-storage/build.gradle.kts").read_text()
        schemas = ROOT / "adapter/map-storage/schemas/com.yokuli.marine.map.storage.MapLibraryDatabase"
        one = json.loads((schemas / "1.json").read_text())
        two = json.loads((schemas / "2.json").read_text())
        self.assertEqual(1, one["database"]["version"])
        self.assertEqual(2, two["database"]["version"])
        self.assertIn("MIGRATION_1_2", database)
        self.assertIn(".addMigrations(MIGRATION_1_2", persistence)
        self.assertIn('assets.srcDir("$projectDir/schemas")', build)
        combined = database + persistence
        self.assertNotIn("fallbackToDestructiveMigration", combined)

    def test_place_export_stays_sdk_free_until_android_composition_root(self):
        export = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapPlaceExport.kt").read_text()
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        core_main = "\n".join(
            path.read_text()
            for path in (ROOT / "core/map-domain/src/main").rglob("*.kt")
        )
        self.assertIn("yokuli-place", export)
        self.assertIn("fun encode(place: SavedPlace)", export)
        self.assertIn("ActivityResultContracts.CreateDocument(MapPlaceExport.MIME_TYPE)", activity)
        self.assertIn("onExportPlace", activity)
        self.assertNotIn("import android.", core_main)

    def test_c05_risk_matrix_has_executable_domain_storage_and_ui_evidence(self):
        domain_tests = "\n".join(
            path.read_text()
            for path in (ROOT / "core/map-domain/src/test").rglob("*Test.kt")
        )
        storage_test = (
            ROOT / "adapter/map-storage/src/androidTest/java/com/yokuli/marine/map/storage/RoomMapPersistenceTest.kt"
        ).read_text()
        ui_test = (
            ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/PlaceWorkspaceStoryTest.kt"
        ).read_text()
        for phrase in (
            "ordinary camera changes cannot move a place",
            "deleting a referenced place preserves route snapshots",
            "delete undo restores the same id",
            "a place id is not reused immediately after deletion",
            "local search covers Chinese English",
            "single-place document preserves the complete stable place record",
        ):
            self.assertIn(phrase, domain_tests)
        self.assertIn("versionOnePlacesMigrateWithoutDestructiveFallback", storage_test)
        self.assertIn("durableLibraryReopensWithTheSameIdsAndRevisions", storage_test)
        self.assertIn("emptyDuplicateAndNoResultSurfacesStayReachableAtOnePointFiveFontScale", ui_test)


if __name__ == "__main__":
    unittest.main()
