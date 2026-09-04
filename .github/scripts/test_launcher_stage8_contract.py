import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-8"
ENGINE = ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine"
DESKTOP = ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop"
STARTING_SHA = "f5d59ba81267f7c3ce9f2120bda20c09a0386a14"


class LauncherStage8PinUnpinContractTest(unittest.TestCase):
    def test_stage_baseline_is_locked_to_stage_seven_commit(self):
        lock = json.loads((STAGE / "BASELINE_LOCK.json").read_text())
        self.assertEqual(8, lock["stage"])
        self.assertEqual(STARTING_SHA, lock["startingSha"])
        self.assertEqual("Complete Edit / Drag / Resize", lock["requiredCompletedStage"])

    def test_engine_owns_context_pin_unpin_reveal_and_transient_actions(self):
        reducer = (ENGINE / "LauncherReducer.kt").read_text()
        state = (ENGINE / "LauncherState.kt").read_text()
        for action in (
            "OpenEntryContextMenu",
            "DismissTransient",
            "PinEntry",
            "UnpinTile",
            "AcknowledgeStartReveal",
        ):
            self.assertIn(action, reducer)
        self.assertIn("data class ContextMenu(val entryId: LauncherEntryId)", state)
        self.assertIn("data class UndoLayout", state)
        self.assertIn("data class Notice", state)
        self.assertIn("reveal: StartReveal?", state)

    def test_all_apps_has_no_local_context_menu_or_silent_toggle_pin(self):
        apps = (DESKTOP / "WpAppList.kt").read_text()
        self.assertNotIn("var contextEntry by remember", apps)
        self.assertIn("state.transient", apps)
        self.assertIn("OpenEntryContextMenu", apps)
        self.assertIn("PinEntry", apps)
        self.assertIn("UnpinTile", apps)
        self.assertNotIn("TogglePin", apps)

    def test_pin_reveal_and_undo_are_rendered_with_bilingual_copy(self):
        start = (DESKTOP / "WpStartScreen.kt").read_text()
        feedback = (DESKTOP / "WpLauncherFeedback.kt").read_text()
        ui = (DESKTOP / "LauncherUiContract.kt").read_text()
        self.assertIn("state.reveal", start)
        self.assertIn("animateScrollTo", start)
        self.assertIn("AcknowledgeStartReveal", start)
        self.assertIn("WpLauncherFeedback", start)
        self.assertIn("launcher-undo", feedback)
        self.assertIn("transient: LauncherTransient?", ui)
        for locale in ("values", "values-en", "values-zh-rCN"):
            strings = (ROOT / f"feature/desktop/src/main/res/{locale}/strings.xml").read_text()
            for name in ("undo", "tile_pinned", "tile_unpinned", "already_pinned", "pin_unavailable"):
                self.assertIn(f'name="{name}"', strings)

    def test_jvm_stories_lock_launcher_grade_pin_contract(self):
        tests = "\n".join(path.read_text() for path in (ROOT / "core/shell-engine/src/test").rglob("*.kt"))
        for scenario in (
            "pinOpensContextMenuFirst",
            "pinReturnsToStartAndRequestsReveal",
            "pinDoesNotDuplicateEntry",
            "unpinDoesNotDeleteApp",
            "undoPinRestoresDocument",
            "undoUnpinRestoresDocument",
            "catalogAdditionDoesNotAutoPin",
            "catalogRemovalPreservesUnrelatedRank",
        ):
            self.assertIn(scenario, tests)

    def test_activity_stories_cover_visible_context_pin_unpin_and_undo(self):
        stories = (ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt").read_text()
        for scenario in (
            "allAppsLongPressOpensContextWithoutChangingStart",
            "pinReturnsToStartRevealsTileAndCanUndo",
            "unpinKeepsEntryInstalledAndCanUndo",
        ):
            self.assertIn(scenario, stories)

    def test_stage_report_and_named_ci_gate_exist(self):
        report = (STAGE / "REPORT.md").read_text()
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("Stage 8 — Pin / Unpin / Context Menu", report)
        self.assertIn("PENDING_HUMAN_REVIEW", report)
        self.assertIn("launcher_stage8_contract", workflow)
        self.assertIn("python3 .github/scripts/test_launcher_stage8_contract.py", workflow)


if __name__ == "__main__":
    unittest.main()
