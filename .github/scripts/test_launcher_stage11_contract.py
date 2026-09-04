import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-11"
STARTING_SHA = "1192d0bf9cee42266fe8430fd7ba59c424c03c56"
REFERENCE_HASH = "af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5"


class LauncherStage11PerformanceContractTest(unittest.TestCase):
    def test_stage_baseline_is_locked_to_stage_ten_commit(self):
        lock = json.loads((STAGE / "BASELINE_LOCK.json").read_text())
        self.assertEqual(11, lock["stage"])
        self.assertEqual(STARTING_SHA, lock["startingSha"])
        self.assertEqual(REFERENCE_HASH, lock["reviewedMeasurementHash"])
        self.assertFalse(lock["nextStageStarted"])

    def test_real_macrobenchmark_and_baseline_profile_modules_exist(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        catalog = (ROOT / "gradle/libs.versions.toml").read_text()
        self.assertIn(":benchmark:shell", settings)
        self.assertIn(":baselineprofile:shell", settings)
        self.assertIn("androidx.benchmark", catalog)
        benchmark = "\n".join(path.read_text() for path in (ROOT / "benchmark/shell").rglob("*.kt"))
        profile = "\n".join(path.read_text() for path in (ROOT / "baselineprofile/shell").rglob("*.kt"))
        for symbol in ("MacrobenchmarkRule", "StartupTimingMetric", "FrameTimingMetric"):
            self.assertIn(symbol, benchmark)
        self.assertIn("BaselineProfileRule", profile)
        self.assertIn("includeInStartupProfile = true", profile)
        self.assertIn("PROFILE_MAX_ITERATIONS = 3", profile)
        self.assertIn("PROFILE_STABLE_ITERATIONS = 2", profile)

    def test_generated_profiles_are_versioned_and_product_scoped(self):
        profile_dir = ROOT / "app-shell/src/main/generated/baselineProfiles"
        for name in ("baseline-prof.txt", "startup-prof.txt"):
            path = profile_dir / name
            self.assertTrue(path.is_file(), f"missing generated {name}")
            rules = [line for line in path.read_text().splitlines() if line.strip()]
            self.assertGreater(len(rules), 10, f"{name} is unexpectedly empty")
            self.assertTrue(all("com/yokuli/" in rule for rule in rules), f"{name} contains non-product rules")
            self.assertTrue(any("->" in rule for rule in rules), f"{name} contains no hot method rules")

    def test_benchmark_journeys_include_start_pager_apps_and_sixty_tiles(self):
        benchmark = "\n".join(path.read_text() for path in (ROOT / "benchmark/shell").rglob("*.kt"))
        for journey in (
            "coldStartToStart",
            "warmStartToStart",
            "startToAllApps",
            "openChartAndReturn",
            "startVerticalScroll60Tiles",
        ):
            self.assertIn(journey, benchmark)
        lab = (ROOT / "feature/shell-lab/src/main/java/com/yokuli/marine/feature/shell/lab/ShellLabActivity.kt").read_text()
        self.assertIn("PERFORMANCE(60)", lab)
        view_model = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellViewModel.kt").read_text()
        self.assertIn('setOf("benchmark", "nonMinifiedRelease")', view_model)
        self.assertIn("persistence.markLaunchHealthy()", view_model)
        self.assertIn("LauncherAction.ExitSafeMode", view_model)
        self.assertIn("WAIT_MILLIS = 20_000L", benchmark)
        self.assertIn('startActivityAndWait(shellIntent())', benchmark)
        self.assertIn("setupBlock = { pressHome() }", benchmark)
        self.assertNotIn("normalizeStartAndPressHome", benchmark)

    def test_golden_candidates_are_content_addressed_and_reference_bound(self):
        manifest = json.loads((ROOT / "docs/reference/wp8/golden/GOLDEN_CANDIDATES.json").read_text())
        self.assertEqual("CANDIDATE_PENDING_HUMAN_REVIEW", manifest["status"])
        self.assertEqual(REFERENCE_HASH, manifest["reviewedMeasurementHash"])
        expected = {
            "wp8_start_360_dark",
            "wp8_start_360_light",
            "wp8_start_320_square",
            "wp8_all_apps_360",
            "wp8_edit_medium",
            "wp8_context_menu",
            "wp8_alphabet_jump",
            "wp8_tile_launch_plane",
            "wp8_start_360_square",
        }
        self.assertEqual(expected, {capture["sceneId"] for capture in manifest["captures"]})
        for capture in manifest["captures"]:
            self.assertEqual(64, len(capture["sha256"]))
            self.assertTrue((ROOT / capture["path"]).is_file())
        validator = (ROOT / ".github/scripts/validate_stage11_fidelity.py").read_text()
        self.assertIn("GOLDEN_CANDIDATE_VALIDATION=PASS", validator)

    def test_square_profiles_and_performance_trend_have_automated_gates(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("launcher_stage11_contract", workflow)
        self.assertIn("stage11-performance", workflow)
        self.assertIn("run_device_tests.sh performance", workflow)
        self.assertIn("--result-root benchmark/shell/build/outputs/androidTest-results", workflow)
        wrapper = (ROOT / ".github/scripts/run_device_tests.sh").read_text()
        self.assertIn("connectedStandaloneBenchmarkAndroidTest", wrapper)
        self.assertIn("validate_stage11_fidelity.py", workflow)
        self.assertIn("stage11-performance-reports", workflow)
        tests = "\n".join(path.read_text() for path in ROOT.rglob("*Test.kt"))
        self.assertIn("square320UsesTheSameExplicitSpatialDocument", tests)
        self.assertIn("square360UsesTheSameExplicitSpatialDocument", tests)
        self.assertIn("sixtyTileLayoutRemainsDeterministic", tests)
        self.assertIn("virtualSystemKeysRemainAvailableOnAllApps", tests)
        self.assertIn("virtualBackDismissesAlphabetJumpBeforeLeavingAllApps", tests)

    def test_hardware_and_human_limits_remain_explicit(self):
        report = (STAGE / "REPORT.md").read_text()
        self.assertIn("COMPLETE_PROVISIONAL", report)
        self.assertIn("Samsung square: UNVERIFIED_HARDWARE", report)
        self.assertIn("physical WP8 fidelity: PENDING_HUMAN_REVIEW", report)
        self.assertIn("Golden: CANDIDATE_PENDING_HUMAN_REVIEW", report)
        self.assertNotIn("Samsung square: PASS", report)
        self.assertNotIn("physical WP8 fidelity: PASS", report)


if __name__ == "__main__":
    unittest.main()
