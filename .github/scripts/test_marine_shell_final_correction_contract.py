from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class MarineShellFinalCorrectionContract(unittest.TestCase):
    def text(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_phase_baseline_and_tdd_contract_are_versioned(self):
        phase = ROOT / "docs/phases/marine-shell-final-correction"
        self.assertTrue((phase / "BASELINE_LOCK.json").is_file())
        self.assertTrue((phase / "REQUIREMENTS.md").is_file())
        self.assertTrue((phase / "TDD_MATRIX.md").is_file())

    def test_android_home_product_surface_is_absent(self):
        build = self.text("app-shell/build.gradle.kts")
        manifest = self.text("app-shell/src/main/AndroidManifest.xml")
        self.assertNotIn('create("home")', build)
        self.assertNotIn("SHELL_HOME_MODE", build)
        self.assertFalse((ROOT / "app-shell/src/home/AndroidManifest.xml").exists())
        self.assertNotIn("android.intent.category.HOME", manifest)
        self.assertNotIn("android.intent.category.DEFAULT", manifest)

    def test_existing_task_is_not_forced_to_desktop(self):
        activity = self.text("app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt")
        self.assertNotRegex(activity, r"onNewIntent[\s\S]{0,300}ShellInput\.DESKTOP")
        self.assertNotIn("Settings.ACTION_HOME_SETTINGS", activity)

    def test_search_is_a_visual_surface_and_has_atomic_transition_model(self):
        state = self.text("core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt")
        self.assertRegex(state, r"sealed interface ShellVisualSurface")
        self.assertRegex(state, r"data class Search\(")
        self.assertTrue((ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/ShellTransitionResolver.kt").is_file())

    def test_motion_consumes_exact_transition_kind_and_visible_windows_are_not_generic_intents(self):
        activity = self.text("app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt")
        motion = self.text("core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt")
        resolver = self.text("core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/ShellTransitionResolver.kt")
        self.assertIn("transitionRequest?.kind", activity)
        self.assertIn("WpSurfaceTransitionKind", motion)
        self.assertIn("appOpenVisibleWindowMillis", motion)
        self.assertNotIn("toLegacyIntent", resolver)

    def test_center_key_is_bridge_not_windows_start(self):
        contract = self.text("core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellInput.kt")
        key_bar = self.text("feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSystemKeyBar.kt")
        self.assertIn("DESKTOP", contract)
        self.assertNotRegex(key_bar, r"Windows|StartGlyph|four-pane")
        self.assertIn("Compass", key_bar)

    def test_safe_window_metrics_are_real_inputs(self):
        adapter_path = "adapter/shell-android/src/main/java/com/yokuli/marine/adapter/shell/android/ShellWindowMetrics.kt"
        self.assertTrue((ROOT / adapter_path).is_file())
        adapter = self.text(adapter_path)
        activity = self.text("app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt")
        for token in ("safeDrawing", "displayCutout", "systemGestures", "ime"):
            self.assertIn(token, activity + adapter)
        self.assertIn("roundedCorners", adapter)
        self.assertIn("rememberShellWindowMetrics", activity)
        stories = self.text("app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt")
        self.assertIn("awaitHostWindowChrome(Color.WHITE)", stories)

    def test_marine_tile_contract_has_all_six_sizes(self):
        tile_contract = self.text("core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt")
        for token in ("ICON_1X1", "COMPACT_2X1", "STANDARD_2X2", "WIDE_4X2", "TALL_2X4", "LARGE_4X4"):
            self.assertIn(token, tile_contract)
        self.assertIn("TilePresentationKind", tile_contract)
        renderer = self.text("feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt")
        for token in ("ICON_1X1", "COMPACT_2X1", "STANDARD_2X2", "WIDE_4X2", "TALL_2X4", "LARGE_4X4"):
            self.assertIn(token, renderer)

    def test_adaptive_packer_is_rank_and_insertion_based(self):
        packer = self.text("core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt")
        self.assertIn("insertionIndex", packer)
        self.assertIn("rank", packer)
        self.assertIn("Spacer", packer)

    def test_direct_editing_keeps_pointer_frames_out_of_engine(self):
        engine = self.text("core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt")
        reducer = self.text("core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt")
        interaction = self.text(
            "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt"
        )
        screen = self.text("feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt")
        self.assertNotIn("Channel.UNLIMITED", engine)
        self.assertIn("MAX_PENDING_ACTIONS", engine)
        self.assertIn("InsertionTargetChanged", reducer)
        self.assertNotIn("UpdateTileDrag", reducer)
        self.assertNotIn("visualOffsetPx", interaction)
        self.assertIn("LocalTileDrag", screen)
        self.assertIn("tile-insertion-marker", self.text(
            "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSpatialStartLayout.kt"
        ))
        self.assertIn("YokuliMetrics.MinTouch", screen)
        self.assertNotRegex(screen, r"withFrameNanos\s*\{\s*\}\s*\n\s*latestAction\(LauncherUiAction.CommitTileResize")

    def test_settings_avoids_accent_bullets_and_blanket_tilt(self):
        settings = self.text("feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsWorkspace.kt")
        self.assertNotIn("AccentBullet", settings)
        self.assertNotIn(".wpTilt(", settings)
        self.assertIn("CompactAccentSwatch", settings)

    def test_release_surface_remains_chart_and_settings_only(self):
        graph = self.text("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        self.assertIn("ChartShellContribution", graph)
        self.assertIn("SettingsShellContribution", graph)
        for forbidden in ("Anchor", "Trip", "Nmea", "Navigation", "Survey"):
            self.assertNotIn(forbidden, graph)

    def test_correction_performance_gate_covers_every_normative_journey(self):
        benchmark = self.text("benchmark/shell/src/main/java/com/yokuli/marine/benchmark/shell/ShellMacrobenchmark.kt")
        for journey in (
            "desktopModuleListRoundTrip",
            "searchToChart",
            "dragAcrossThirtyMixedTiles",
            "resizeStandardTileToLarge",
            "rounded320Viewport",
            "settingsScroll",
        ):
            self.assertIn(f"fun {journey}()", benchmark)
        lab = self.text("feature/shell-lab/src/main/java/com/yokuli/marine/feature/shell/lab/ShellLabActivity.kt")
        self.assertIn("EXTRA_TILE_COUNT", lab)
        self.assertIn("EXTRA_VIEWPORT_DP", lab)
        self.assertIn("shell-lab-rounded-viewport-$viewportDp", lab)

    def test_benchmark_start_reset_is_explicit_and_cannot_change_release_resume_semantics(self):
        benchmark = self.text(
            "benchmark/shell/src/main/java/com/yokuli/marine/benchmark/shell/ShellMacrobenchmark.kt"
        )
        activity = self.text("app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt")
        view_model = self.text("app-shell/src/main/java/com/yokuli/marine/shell/ShellViewModel.kt")
        self.assertIn("EXTRA_PREPARE_BENCHMARK_START", benchmark)
        self.assertIn("EXTRA_PREPARE_BENCHMARK_START", activity)
        self.assertRegex(
            activity,
            r'BuildConfig\.BUILD_TYPE\s*==\s*"benchmark"[\s\S]{0,180}prepareBenchmarkStart',
        )
        self.assertIn("fun prepareBenchmarkStart()", view_model)
        self.assertIn("LauncherAction.ShowDesktop", view_model)
        self.assertIn("InMemoryLauncherPersistence(defaultStartDocument)", view_model)

    def test_generated_profiles_do_not_reference_removed_motion_contracts(self):
        for name in ("baseline-prof.txt", "startup-prof.txt"):
            profile = self.text(f"app-shell/src/main/generated/baselineProfiles/{name}")
            self.assertNotIn("WpNavigationIntent", profile)
            self.assertNotIn("LauncherTransitionIntent", profile)
            self.assertIn("WpSurfaceTransitionKind", profile)
            self.assertIn("ShellTransitionRequest", profile)

    def test_final_machine_gate_and_truthful_report_are_versioned(self):
        gate = ROOT / ".github/scripts/run_marine_shell_final_gate.sh"
        report = ROOT / "docs/phases/marine-shell-final-correction/REPORT.md"
        self.assertTrue(gate.is_file())
        self.assertTrue(report.is_file())
        gate_text = gate.read_text(encoding="utf-8")
        for command in (
            "test-release-product-surface.sh",
            "run_device_tests.sh all",
            "run_device_tests.sh performance",
            "--require-journeys",
            "MARINE_SHELL_FINAL_GATE=MACHINE_VERIFIED",
        ):
            self.assertIn(command, gate_text)
        lock = json.loads(self.text("docs/phases/marine-shell-final-correction/BASELINE_LOCK.json"))
        self.assertEqual("MACHINE_VERIFIED", lock["machineStatus"])
        self.assertEqual("PENDING", lock["humanFidelityStatus"])
        self.assertEqual("PENDING", lock["physicalDeviceStatus"])
        report_text = report.read_text(encoding="utf-8")
        self.assertIn("MACHINE_VERIFIED", report_text)
        self.assertIn("HUMAN_FIDELITY_PENDING", report_text)
        self.assertIn("PHYSICAL_DEVICE_PENDING", report_text)
        self.assertNotIn("HUMAN_FIDELITY_PASS", report_text)
        self.assertNotIn("PHYSICAL_DEVICE_PASS", report_text)


if __name__ == "__main__":
    unittest.main()
