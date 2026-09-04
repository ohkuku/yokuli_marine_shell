import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-4"
ENGINE = ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine"
STARTING_SHA = "f0315cf1336991ebaaf7ba15f1f81ef9956d3b18"


class LauncherStage4EngineStateContractTest(unittest.TestCase):
    def test_stage_baseline_is_locked_to_stage_three_commit(self):
        lock = json.loads((STAGE / "BASELINE_LOCK.json").read_text())
        self.assertEqual(4, lock["stage"])
        self.assertEqual(STARTING_SHA, lock["startingSha"])
        self.assertEqual("WP Geometry & Start Document", lock["requiredCompletedStage"])
        self.assertEqual("PENDING_HUMAN_REVIEW", lock["approvalStatus"])

    def test_engine_exposes_state_actions_effects_and_persistence_port(self):
        sources = "\n".join(path.read_text() for path in ENGINE.rglob("*.kt"))
        for symbol in (
            "interface LauncherReducer",
            "data class LauncherReducerContext",
            "data class LauncherReduction",
            "data class LauncherEngineState",
            "sealed interface LauncherAction",
            "sealed interface LauncherEffect",
            "interface LauncherEngine",
            "class DefaultLauncherEngine",
            "interface LauncherPersistencePort",
            "class InMemoryLauncherPersistence",
            "data class LayoutTransaction",
        ):
            self.assertIn(symbol, sources)

    def test_unresolved_launch_is_non_crashing_and_incident_driven(self):
        sources = "\n".join(path.read_text() for path in ENGINE.rglob("*.kt"))
        tests = "\n".join(path.read_text() for path in (ROOT / "core/shell-engine/src/test").rglob("*.kt"))
        self.assertIn("LaunchResolution.Unresolved", sources)
        self.assertIn("LauncherEffect.LogIncident", sources)
        self.assertIn("UnresolvedLaunchToken", sources)
        self.assertNotIn("require(resolution is LaunchResolution.Internal)", sources)
        self.assertIn("unresolvedTokenKeepsCurrentSurfaceAndLogsIncident", tests)

    def test_controller_serializes_actions_and_catalog_flow(self):
        controller = (ENGINE / "LauncherEngine.kt").read_text()
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        tests = "\n".join(path.read_text() for path in (ROOT / "core/shell-engine/src/test").rglob("*.kt"))
        self.assertIn("Channel<LauncherAction>", controller)
        self.assertIn("for (action in actions)", controller)
        self.assertIn("hostPort.catalog.collect", controller)
        self.assertIn("rapidOpenBackAndHomeActionsAreSerialized", tests)
        self.assertNotIn("productionCatalog.snapshot", activity)
        self.assertNotIn("scope.launch { navigation", activity)
        self.assertIn("engine.dispatch", activity)
        self.assertIn("collectAsState", activity)

    def test_transactions_support_cancel_undo_and_deterministic_identity(self):
        sources = "\n".join(path.read_text() for path in ENGINE.rglob("*.kt"))
        tests = "\n".join(path.read_text() for path in (ROOT / "core/shell-engine/src/test").rglob("*.kt"))
        self.assertIn("CancelLayoutTransaction", sources)
        self.assertIn("UndoLayout", sources)
        self.assertIn("nextTransactionId", sources)
        self.assertNotIn("UUID.randomUUID", sources)
        self.assertIn("cancelAndUndoRestoreTheExactPreviousDocument", tests)
        self.assertIn("corruptPersistedDocumentUsesDeterministicFallback", tests)

    def test_activity_owns_no_navigation_or_start_document_truth(self):
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        view_model = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellViewModel.kt").read_text()
        stories = (ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt").read_text()
        self.assertNotIn("mutableStateOf(ShellNavigationState", activity)
        self.assertNotIn("mutableStateOf(defaultStartDocument", activity)
        self.assertIn("viewModel<ShellViewModel>", activity)
        self.assertIn("class ShellViewModel", view_model)
        self.assertIn("viewModelScope", view_model)
        self.assertIn("activityRecreationRetainsTheEngineDocument", stories)

    def test_stage_report_and_named_ci_gate_exist(self):
        report = (STAGE / "REPORT.md").read_text()
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("Stage 4 — Engine State, Effects & Persistence Ports", report)
        self.assertIn("PENDING_HUMAN_REVIEW", report)
        self.assertIn("unresolved", report.lower())
        self.assertIn("serialized", report.lower())
        self.assertIn("launcher_stage4_contract", workflow)
        self.assertIn("python3 .github/scripts/test_launcher_stage4_contract.py", workflow)


if __name__ == "__main__":
    unittest.main()
