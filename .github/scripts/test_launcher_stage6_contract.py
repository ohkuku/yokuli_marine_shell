import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-6"
ENGINE_LAYOUT = ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout"
STARTING_SHA = "0b39797a1cffb387e78f1ee4fbf0b5607d90af2a"


class LauncherStage6AdaptiveGridContractTest(unittest.TestCase):
    def test_stage_baseline_is_locked_to_stage_five_commit(self):
        lock = json.loads((STAGE / "BASELINE_LOCK.json").read_text())
        self.assertEqual(6, lock["stage"])
        self.assertEqual(STARTING_SHA, lock["startingSha"])
        self.assertEqual("Interactive Start / All Apps Pager", lock["requiredCompletedStage"])

    def test_engine_has_ranked_adaptive_packer(self):
        sources = "\n".join(path.read_text() for path in ENGINE_LAYOUT.rglob("*.kt"))
        for symbol in (
            "data class TileDocumentEntry",
            "data class Spacer",
            "object AdaptiveTilePacker",
            "fun insert(",
            "insertionIndex",
            "rank",
        ):
            self.assertIn(symbol, sources)
        self.assertNotIn("LocalTileCollisionSolver", sources)

    def test_move_and_resize_use_rank_and_reflow(self):
        editor = (ENGINE_LAYOUT / "StartLayoutEditor.kt").read_text()
        self.assertIn("AdaptiveTilePacker.insertionIndexForCell", editor)
        self.assertIn("AdaptiveTilePacker.insert", editor)
        self.assertNotIn("solver.propose", editor)

    def test_renderer_uses_custom_layout_and_proposed_pixel_placement(self):
        renderer = (ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSpatialStartLayout.kt").read_text()
        screen = (ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt").read_text()
        self.assertIn("fun WpSpatialStartLayout", renderer)
        self.assertIn("Layout(", renderer)
        # Layout offsets keep both hit geometry and pixels in sync; a graphics-only transform is not required.
        self.assertIn(".offset {", renderer)
        self.assertIn(".zIndex(", renderer)
        self.assertIn("heldPosition.lastDrawn", renderer)
        self.assertIn("proposedDocument", renderer)
        self.assertIn("WpSpatialStartLayout(", screen)
        self.assertIn("proposedDocument", screen)

    def test_jvm_tests_lock_repacking_spacers_and_mixed_tile_stability(self):
        tests = "\n".join(path.read_text() for path in (ROOT / "core/shell-engine/src/test").rglob("*.kt"))
        for scenario in (
            "mixed marine tiles pack deterministically without overlap",
            "one durable document repacks for four six and eight columns",
            "only explicit spacer reserves intentional whitespace",
            "seeded mixed documents always remain bounded and collision free",
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
