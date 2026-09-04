from pathlib import Path
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

    def test_marine_tile_contract_has_all_six_sizes(self):
        tile_contract = self.text("core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/MarineTile.kt")
        for token in ("ICON_1X1", "COMPACT_2X1", "STANDARD_2X2", "WIDE_4X2", "TALL_2X4", "LARGE_4X4"):
            self.assertIn(token, tile_contract)

    def test_adaptive_packer_is_rank_and_insertion_based(self):
        packer = self.text("core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt")
        self.assertIn("insertionIndex", packer)
        self.assertIn("rank", packer)
        self.assertIn("Spacer", packer)

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


if __name__ == "__main__":
    unittest.main()
