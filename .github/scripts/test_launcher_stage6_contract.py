import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-6"
ENGINE_LAYOUT = ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout"
STARTING_SHA = "0b39797a1cffb387e78f1ee4fbf0b5607d90af2a"


class LauncherStage6SpatialGridContractTest(unittest.TestCase):
    def test_stage_baseline_is_locked_to_stage_five_commit(self):
        lock = json.loads((STAGE / "BASELINE_LOCK.json").read_text())
        self.assertEqual(6, lock["stage"])
        self.assertEqual(STARTING_SHA, lock["startingSha"])
        self.assertEqual("Interactive Start / All Apps Pager", lock["requiredCompletedStage"])

    def test_engine_has_occupancy_and_local_deterministic_collision_solver(self):
        sources = "\n".join(path.read_text() for path in ENGINE_LAYOUT.rglob("*.kt"))
        for symbol in (
            "class StartOccupancyIndex",
            "interface TileCollisionSolver",
            "class LocalTileCollisionSolver",
            "sealed interface SpatialLayoutProposal",
            "data class Accepted",
            "data class Rejected",
        ):
            self.assertIn(symbol, sources)
        self.assertNotIn("globalReflow", sources)
        self.assertNotIn("topLeftPack", sources)

    def test_move_and_resize_use_the_spatial_solver(self):
        editor = (ENGINE_LAYOUT / "StartLayoutEditor.kt").read_text()
        self.assertIn("LocalTileCollisionSolver", editor)
        self.assertIn("solver.propose", editor)
        self.assertNotIn("StartDocumentRepair.repair(proposed", editor)

    def test_renderer_uses_custom_layout_and_proposed_graphics_translation(self):
        renderer = (ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSpatialStartLayout.kt").read_text()
        screen = (ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt").read_text()
        self.assertIn("fun WpSpatialStartLayout", renderer)
        self.assertIn("Layout(", renderer)
        self.assertIn("graphicsLayer", renderer)
        self.assertIn("proposedDocument", renderer)
        self.assertIn("WpSpatialStartLayout(", screen)
        self.assertIn("proposedDocument", screen)

    def test_jvm_tests_lock_locality_whitespace_and_sixty_tile_stability(self):
        tests = "\n".join(path.read_text() for path in (ROOT / "core/shell-engine/src/test").rglob("*.kt"))
        for scenario in (
            "movingOneTilePreservesEveryUnaffectedCoordinate",
            "proposalIsDeterministicAndPreservesWhitespace",
            "sixtySyntheticTilesRemainValidAndStable",
            "occupancyIndexAnswersExplicitCells",
        ):
            self.assertIn(scenario, tests)

    def test_stage_report_and_named_ci_gate_exist(self):
        report = (STAGE / "REPORT.md").read_text()
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("Stage 6 — Custom Spatial Grid", report)
        self.assertIn("PENDING_HUMAN_REVIEW", report)
        self.assertIn("60", report)
        self.assertIn("launcher_stage6_contract", workflow)
        self.assertIn("python3 .github/scripts/test_launcher_stage6_contract.py", workflow)


if __name__ == "__main__":
    unittest.main()
