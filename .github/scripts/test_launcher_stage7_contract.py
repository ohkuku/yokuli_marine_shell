import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-7"
ENGINE = ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine"
DESKTOP = ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop"
STARTING_SHA = "f1652ee6f10b833cec9220ade5ef98ab86739749"


class LauncherStage7EditDragResizeContractTest(unittest.TestCase):
    def test_stage_baseline_is_locked_to_stage_six_commit(self):
        lock = json.loads((STAGE / "BASELINE_LOCK.json").read_text())
        self.assertEqual(7, lock["stage"])
        self.assertEqual(STARTING_SHA, lock["startingSha"])
        self.assertEqual("Custom Spatial Grid", lock["requiredCompletedStage"])

    def test_serial_engine_owns_edit_drag_drop_resize_and_cancel_actions(self):
        reducer = (ENGINE / "LauncherReducer.kt").read_text()
        for action in (
            "EnterStartEdit",
            "ExitStartEdit",
            "BeginTileDrag",
            "UpdateTileDrag",
            "AutoScrollTileDrag",
            "DropTile",
            "CancelTileOperation",
            "ResizeTile",
            "MoveTileBy",
        ):
            self.assertIn(action, reducer)

    def test_drag_policy_has_hysteresis_and_edge_auto_scroll_without_wp8_claim(self):
        policy = (ENGINE / "interaction/DragInteractionPolicy.kt").read_text()
        self.assertIn("class DragCellHysteresis", policy)
        self.assertIn("class EdgeAutoScrollPolicy", policy)
        self.assertIn("DERIVED_UNVERIFIED", policy)
        self.assertNotIn("HUMAN_REVIEWED", policy)

    def test_renderer_consumes_engine_interaction_and_exposes_accessibility_moves(self):
        screen = (DESKTOP / "WpStartScreen.kt").read_text()
        ui = (DESKTOP / "LauncherUiContract.kt").read_text()
        self.assertIn("interaction: StartInteractionState", ui)
        self.assertIn("state.interaction", screen)
        self.assertIn("CustomAccessibilityAction", screen)
        self.assertIn("customActions", screen)
        self.assertNotIn("remember { mutableStateOf(StartInteractionState.Idle) }", screen)

    def test_jvm_tests_lock_complete_edit_interaction_contract(self):
        tests = "\n".join(path.read_text() for path in (ROOT / "core/shell-engine/src/test").rglob("*.kt"))
        for scenario in (
            "grabOffsetIsPreserved",
            "neighborMovesBeforeDrop",
            "cellHysteresisPreventsThrash",
            "autoScrollKeepsTileUnderFinger",
            "invalidDropReturnsOrigin",
            "pointerCancelRestoresCommittedDocument",
            "catalogChangeCancelsDragSafely",
            "smallMediumWideResizeCycleIsExact",
        ):
            self.assertIn(scenario, tests)

    def test_bilingual_accessibility_labels_exist(self):
        zh = (ROOT / "feature/desktop/src/main/res/values/strings.xml").read_text()
        en = (ROOT / "feature/desktop/src/main/res/values-en/strings.xml").read_text()
        for name in ("move_tile_left", "move_tile_right", "move_tile_up", "move_tile_down"):
            self.assertIn(f'name="{name}"', zh)
            self.assertIn(f'name="{name}"', en)

    def test_stage_report_and_named_ci_gate_exist(self):
        report = (STAGE / "REPORT.md").read_text()
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("Stage 7 — Complete Edit / Drag / Resize", report)
        self.assertIn("PENDING_HUMAN_REVIEW", report)
        self.assertIn("launcher_stage7_contract", workflow)
        self.assertIn("python3 .github/scripts/test_launcher_stage7_contract.py", workflow)


if __name__ == "__main__":
    unittest.main()
