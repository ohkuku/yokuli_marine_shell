from pathlib import Path
import json
import unittest


ROOT = Path(__file__).resolve().parents[2]
PHASE = ROOT / "docs/phases/shell-map-contract"


class ShellAppTileContractTest(unittest.TestCase):
    def test_phase_is_locked_to_the_verified_stage_11_baseline(self):
        lock = json.loads((PHASE / "BASELINE_LOCK.json").read_text())
        self.assertEqual("shell-map-contract", lock["phase"])
        self.assertEqual("a144de02657b5dd778d329f26ee5f2443370af01", lock["startingSha"])
        self.assertEqual(1, lock["approvedReferenceProfileRevision"])

    def test_app_owned_presentation_requires_exact_size_renderers(self):
        contract = (
            ROOT
            / "ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt"
        ).read_text()
        self.assertIn("tileRenderers: Map<MarineTileSize, LauncherTileRenderer>", contract)
        self.assertIn("visual.tileRenderers.keys == descriptor.supportedSizes.toSet()", contract)
        self.assertIn("Shell never scales another size as fallback", contract)

    def test_installed_binding_derives_every_shell_registry(self):
        binding = (
            ROOT
            / "ui/shell-compose/src/main/java/com/yokuli/shell/compose/InstalledAppBinding.kt"
        ).read_text()
        graph = (
            ROOT
            / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt"
        ).read_text()
        for owned in (
            "catalogContributions",
            "launchRegistrations",
            "internalAppHosts",
            "visualContributions",
        ):
            self.assertIn(owned, binding)
        self.assertIn("InstalledAppRegistry(productionInstalledApps)", graph)
        self.assertNotIn("launchRegistrations = mapOf", graph)
        self.assertNotIn("LauncherEntryUiState(", graph)

    def test_each_feature_owns_its_tile_content(self):
        chart = (
            ROOT
            / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartLauncherPresentation.kt"
        ).read_text()
        settings = (
            ROOT
            / "feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsLauncherPresentation.kt"
        ).read_text()
        desktop = (
            ROOT
            / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt"
        ).read_text()
        self.assertIn("chartLauncherVisualContribution", chart)
        self.assertIn("ChartStandardTile", chart)
        self.assertIn("ChartWideTile", chart)
        self.assertIn("ChartLargeTile", chart)
        self.assertIn("settingsLauncherVisualContribution", settings)
        self.assertIn("SettingsIconTile", settings)
        self.assertIn("SettingsCompactTile", settings)
        self.assertIn("SettingsStandardTile", settings)
        self.assertNotIn("MarineTileContent", desktop)
        self.assertNotIn('entryId.value == "chart"', desktop)
        self.assertNotIn('entryId.value == "settings"', desktop)

    def test_resize_is_atomic_and_drag_begins_on_the_same_long_press(self):
        reducer = (
            ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt"
        ).read_text()
        interaction = (
            ROOT
            / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt"
        ).read_text()
        screen = (
            ROOT
            / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt"
        ).read_text()
        self.assertNotIn("CommitTileResize", reducer)
        self.assertNotIn("data class Resizing", interaction)
        self.assertNotIn("commit-tile-resize", screen)
        self.assertNotIn("cancel-tile-resize", screen)
        self.assertIn("awaitLongPressOrCancellation", screen)
        self.assertIn("latestMoveStart(dragStart.id.value", screen)

    def test_red_green_scenarios_remain_executable(self):
        engine_tests = (
            ROOT
            / "core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/EditInteractionTest.kt"
        ).read_text()
        activity_tests = (
            ROOT
            / "app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt"
        ).read_text()
        for scenario in (
            "oneResizeActionCommitsAndPersistsWithoutConfirmationState",
            "resizeFollowsOnlyTheAppsDeclaredOrder",
            "singleSizeEntryDoesNotCreateAResizeTransaction",
            "chartResizeCommitsOnOneTapWithoutConfirmationUi",
            "sameLongPressGestureCanLiftReorderAndDropATile",
            "smallTileEditControlsAreVisiblyUsableAndHave48DpHitTargets",
        ):
            self.assertIn(scenario, engine_tests + activity_tests)

    def test_map_domain_and_app_workflow_stay_out_of_shell_contracts(self):
        domain_files = list((ROOT / "core/map-domain/src/main").rglob("*.kt"))
        domain = "\n".join(path.read_text() for path in domain_files)
        for forbidden in ("android.", "androidx.", "compose", "LauncherAction", "ShellVisualSurface"):
            self.assertNotIn(forbidden, domain)
        coordinator = (
            ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartPackageCoordinator.kt"
        ).read_text()
        self.assertIn("ChartPackageRepository", coordinator)
        self.assertIn("MapAction.ChartPackagesChanged", coordinator)
        self.assertIn("MapAction.SelectChartPackage", coordinator)
        self.assertNotIn("ContentResolver", coordinator)

    def test_offline_package_install_is_validated_atomic_and_legally_described(self):
        repository = (
            ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/AndroidMbTilesRepository.kt"
        ).read_text()
        metadata = (
            ROOT / "adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/MbTilesMetadata.kt"
        ).read_text()
        for required in ('"metadata"', '"tiles"', "MessageDigest.getInstance(\"SHA-256\")", "renameTo(destination)"):
            self.assertIn(required, repository)
        for legal_field in ("source", "license", "attribution", "version"):
            self.assertIn(legal_field, repository)
        self.assertIn('setOf("png", "jpg", "jpeg", "webp")', metadata)
        self.assertIn("UNSUPPORTED_FORMAT", metadata)

    def test_map_runtime_is_truthful_persisted_and_has_device_stories(self):
        model = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapModel.kt").read_text()
        reducer = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapReducer.kt").read_text()
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        activity_tests = (
            ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt"
        ).read_text()
        self.assertIn("positionObservation = null", model)
        self.assertIn("navigationActive = false", model)
        self.assertIn("PositionAvailability.UNAVAILABLE", model)
        self.assertIn("UnknownChartPackage", reducer)
        self.assertIn("OfflineMarineChartSurface", graph)
        self.assertIn("mapAppKeepsPlanningToolsInternalAndPositionTruthExplicit", activity_tests)


if __name__ == "__main__":
    unittest.main()
