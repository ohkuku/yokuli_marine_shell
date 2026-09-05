import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class ChartC06ContractTest(unittest.TestCase):
    def test_route_plan_and_draft_contracts_are_distinct_and_nullable_speed_is_explicit(self):
        model = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapModel.kt").read_text()
        self.assertIn("typealias RouteDraft = ManualRouteDraft", model)
        self.assertIn("typealias RoutePlan = SavedRoute", model)
        self.assertIn("val plannedSpeedKnots: Double? = null", model)
        self.assertIn("val basePlanId: String? = null", model)
        self.assertIn("val basePlanRevision: Long? = null", model)
        self.assertIn("val waypointIds: List<String>", model)

    def test_route_actions_cover_real_edit_save_conflict_and_delete_flows(self):
        reducer = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapReducer.kt").read_text()
        for action in (
            "data class CreateRouteDraft(",
            "data class ActivateRouteDraft(",
            "data class InsertRouteWaypoint(",
            "data class ReorderRouteWaypoint(",
            "data class PreviewRoutePlan(",
            "data class BeginRoutePlanEdit(",
            "data object SaveRoutePlan",
            "data class SaveRoutePlanAsCopy(",
            "data class DuplicateRoutePlan(",
            "data class DiscardRouteDraft(",
            "data class RequestDeleteRoutePlan(",
            "data object UndoDeleteRoutePlan",
            "RouteRevisionConflict",
        ):
            self.assertIn(action, reducer)

    def test_route_workspace_is_a_real_product_surface_without_navigation_claims(self):
        workspace = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        chinese = (ROOT / "feature/chart/src/main/res/values/strings.xml").read_text()
        english = (ROOT / "feature/chart/src/main/res/values-en/strings.xml").read_text()
        for tag in (
            "map-route-create",
            "map-route-drafts-section",
            "map-route-plans-section",
            "map-route-editor-",
            "map-route-preview-",
            "map-route-save",
            "map-route-save-copy",
            "map-route-discard",
            "map-route-delete-confirmation",
        ):
            self.assertIn(tag, workspace)
        self.assertIn("填写计划船速后估算", chinese)
        self.assertIn("Set a planning speed to estimate", english)
        self.assertNotIn("开始导航", chinese)
        self.assertNotIn("START NAVIGATION", english)

    def test_room_v3_migration_preserves_route_identity_and_metadata(self):
        database = (ROOT / "adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/MapLibraryDatabase.kt").read_text()
        persistence = (ROOT / "adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/RoomMapPersistence.kt").read_text()
        self.assertRegex(database, r"version = (?:[3-9]|[1-9][0-9]+)")
        self.assertIn("MIGRATION_2_3", database)
        self.assertRegex(
            persistence,
            r"addMigrations\([^)]*MIGRATION_1_2[^)]*MIGRATION_2_3[^)]*\)",
        )
        self.assertNotIn("fallbackToDestructiveMigration", database + persistence)

    def test_c06_risk_matrix_has_executable_domain_storage_and_ui_evidence(self):
        domain = (
            ROOT / "core/map-domain/src/test/kotlin/com/yokuli/marine/map/domain/RoutePlanningContractTest.kt"
        ).read_text()
        for phrase in (
            "new route saves without invented speed",
            "preview is read only",
            "multiple drafts survive activation",
            "waypoint identity survives insert move reorder delete reverse undo and redo",
            "revision conflict and write failure keep the draft",
            "never emit navigation",
        ):
            self.assertIn(phrase, domain)
        storage = (
            ROOT / "adapter/map-storage/src/androidTest/java/com/yokuli/marine/map/storage/RoomMapPersistenceTest.kt"
        ).read_text()
        ui = (
            ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/RouteWorkspaceStoryTest.kt"
        ).read_text()
        self.assertIn("versionTwoRoutesMigrateWithoutInventingSpeedOrLosingIdentity", storage)
        self.assertIn("drawSaveFindPreviewEditAndSaveSamePlan", ui)


if __name__ == "__main__":
    unittest.main()
