import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-9"
STARTING_SHA = "8aa88557e16ca3c2848df94ed8faf076e5678ea8"


class LauncherStage9NavigationContractTest(unittest.TestCase):
    def test_stage_baseline_is_locked_to_stage_eight_commit(self):
        lock = json.loads((STAGE / "BASELINE_LOCK.json").read_text())
        self.assertEqual(9, lock["stage"])
        self.assertEqual(STARTING_SHA, lock["startingSha"])
        self.assertEqual("Pin / Unpin / Context Menu", lock["requiredCompletedStage"])

    def test_engine_owns_search_recents_home_and_internal_back_stack(self):
        state = (ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt").read_text()
        reducer = (ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt").read_text()
        for symbol in ("Search", "OpenSearch", "UpdateSearchQuery", "ShowRecents", "ActivateTask"):
            self.assertIn(symbol, state + reducer)
        self.assertIn("backStack: List<LaunchToken>", state)
        self.assertIn("RequestHostExit", reducer)

    def test_virtual_and_android_keys_share_one_typed_input_path(self):
        contract = (ROOT / "core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellInput.kt").read_text()
        adapter = (ROOT / "adapter/shell-android/src/main/java/com/yokuli/shell/android/AndroidShellKeyAdapter.kt").read_text()
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        key_bar = (ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSystemKeyBar.kt").read_text()
        for key in ("BACK", "DESKTOP", "SEARCH", "RECENTS"):
            self.assertIn(key, contract)
        self.assertIn("AndroidShellKeyAdapter", activity)
        self.assertIn("ShellInput", key_bar)
        self.assertIn("BackHandler(enabled = true)", activity)
        self.assertIn("onNewIntent", activity)
        self.assertNotRegex(activity, r"onNewIntent[\s\S]{0,300}ShellInput\.DESKTOP")

    def test_fullscreen_is_reasserted_and_platform_home_boundary_is_documented(self):
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        manifest = (ROOT / "app-shell/src/main/AndroidManifest.xml").read_text()
        report = (STAGE / "REPORT.md").read_text()
        self.assertIn("onWindowFocusChanged", activity)
        self.assertIn("BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE", activity)
        self.assertIn('android:launchMode="singleTask"', manifest)
        self.assertIn("Android reserves the physical HOME key", report)
        self.assertIn("DERIVED_UNVERIFIED", report)

    def test_motion_uses_reviewed_timings_reduced_motion_and_delayed_map_mount(self):
        motion = (ROOT / "core/design/src/main/java/com/yokuli/marine/core/design/WpMotion.kt").read_text()
        policy = (ROOT / "core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt").read_text()
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        self.assertIn("reducedMotion", motion)
        self.assertIn("heavyContentReady", graph)
        self.assertIn("WpMotionTimings", policy)
        self.assertIn("appOpenMillis", policy)
        self.assertIn("backReturnMillis", policy)
        self.assertIn("pageSettleMillis", policy)

    def test_jvm_and_activity_stories_cover_navigation_and_keys(self):
        jvm = "\n".join(path.read_text() for path in (ROOT / "core/shell-engine/src/test").rglob("*.kt"))
        activity = (ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt").read_text()
        for scenario in (
            "internalBackPopsOpaqueRouteBeforeReturningToStart",
            "desktopCommandPreservesInternalTasks",
            "searchIsAFirstClassSurfaceAndBackReturnsToItsSource",
            "searchResultLaunchTransitionsDirectlyFromSearchToModule",
            "recentsCanResumeAnExistingTask",
        ):
            self.assertIn(scenario, jvm)
        for scenario in (
            "virtualBridgeReturnsFromSettingsWithoutDestroyingItsTask",
            "searchResultLaunchHasNoIntermediateSurface",
            "virtualBackLongPressOpensRecents",
            "androidBackAndDeliveredHardwareKeysUseTheUnifiedInputPath",
            "appRelaunchDoesNotForceDesktopOrRecreateActivity",
        ):
            self.assertIn(scenario, activity)

    def test_named_ci_gate_and_bilingual_accessible_labels_exist(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("launcher_stage9_contract", workflow)
        self.assertIn("python3 .github/scripts/test_launcher_stage9_contract.py", workflow)
        for locale in ("values", "values-en", "values-zh-rCN"):
            strings = (ROOT / f"feature/desktop/src/main/res/{locale}/strings.xml").read_text()
            for name in ("system_back", "system_bridge", "system_search", "recents_title", "search_title"):
                self.assertIn(f'name="{name}"', strings)


if __name__ == "__main__":
    unittest.main()
