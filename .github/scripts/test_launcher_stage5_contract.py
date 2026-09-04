import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-5"
STARTING_SHA = "4b7522dce88c633a9c49025b14b75861e8ee9f46"


class LauncherStage5InteractivePagerContractTest(unittest.TestCase):
    def test_stage_baseline_is_locked_to_stage_four_commit(self):
        lock = json.loads((STAGE / "BASELINE_LOCK.json").read_text())
        self.assertEqual(5, lock["stage"])
        self.assertEqual(STARTING_SHA, lock["startingSha"])
        self.assertEqual("Engine State, Effects & Persistence Ports", lock["requiredCompletedStage"])
        self.assertEqual("PENDING_HUMAN_REVIEW", lock["approvalStatus"])

    def test_direct_manipulation_pager_replaces_release_swipe_threshold(self):
        pager = (ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/InteractiveLauncherPager.kt").read_text()
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        self.assertIn("fun InteractiveLauncherPager", pager)
        self.assertIn("HorizontalPager", pager)
        self.assertIn("rememberPagerState", pager)
        self.assertIn("settledPage", pager)
        self.assertIn("userScrollEnabled", pager)
        self.assertIn("overscrollEffect = null", pager)
        self.assertNotIn("fun SwipeSurface", activity)
        self.assertNotIn("detectHorizontalDragGestures", activity)
        self.assertNotIn("size.width * .18f", activity)

    def test_engine_surface_and_pager_settle_are_bidirectionally_connected(self):
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        pager = (ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/InteractiveLauncherPager.kt").read_text()
        self.assertIn("InteractiveLauncherPager(", activity)
        self.assertIn("LauncherPagerPage.START", activity)
        self.assertIn("LauncherPagerPage.ALL_APPS", activity)
        self.assertIn("LauncherAction.ShowStart", activity)
        self.assertIn("LauncherAction.ShowAllApps", activity)
        self.assertIn("snapshotFlow", pager)

    def test_edit_mode_disables_page_swipe(self):
        start = (ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt").read_text()
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        self.assertIn("onEditModeChanged", start)
        self.assertIn("userScrollEnabled = !startEditing", activity)

    def test_activity_stories_cover_pager_boundaries_and_arbitration(self):
        stories = (ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt").read_text()
        for scenario in (
            "pageTracksFingerOneToOneAndLongDragCompletes",
            "shortSlowDragCancels",
            "verticalIntentDoesNotPage",
            "editModeDisablesPageSwipe",
            "systemBackFromAllAppsReturnsToStart",
        ):
            self.assertIn(scenario, stories)

    def test_stage_report_and_named_ci_gate_exist(self):
        report = (STAGE / "REPORT.md").read_text()
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("Stage 5 — Interactive Start / All Apps Pager", report)
        self.assertIn("PENDING_HUMAN_REVIEW", report)
        self.assertIn("direct manipulation", report.lower())
        self.assertIn("launcher_stage5_contract", workflow)
        self.assertIn("python3 .github/scripts/test_launcher_stage5_contract.py", workflow)


if __name__ == "__main__":
    unittest.main()
