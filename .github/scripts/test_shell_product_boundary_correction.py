import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
PHASE = ROOT / "docs/phases/shell-product-boundary-correction"
STARTING_SHA = "0bd086fc4923730207e75e8047655b6695b08c5c"


class ShellProductBoundaryCorrectionTest(unittest.TestCase):
    def test_baseline_and_bilingual_contract_are_locked(self):
        lock = json.loads((PHASE / "BASELINE_LOCK.json").read_text())
        requirements = (PHASE / "REQUIREMENTS.md").read_text()
        self.assertEqual(STARTING_SHA, lock["startingSha"])
        self.assertTrue(lock["supersedesConflictingHistoricalClauses"])
        for phrase in (
            "不是 Android 桌面替代品",
            "Back 是无副作用的幂等动作",
            "默认只允许竖屏",
            "not an Android Home replacement",
            "idempotent no-op",
            "portrait-only",
        ):
            self.assertIn(phrase, requirements)

    def test_android_desktop_settings_escape_is_absent(self):
        paths = (
            "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt",
            "app-shell/src/main/java/com/yokuli/marine/shell/ShellViewModel.kt",
            "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt",
            "feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsUiContract.kt",
            "feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsWorkspace.kt",
            "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherRecoverySurface.kt",
        )
        source = "\n".join((ROOT / path).read_text() for path in paths)
        for forbidden in (
            "RequestAndroidSettings",
            "OpenAndroidSettings",
            "openAndroidSettings",
            "Settings.ACTION_SETTINGS",
            "settings-open-android-settings",
            "recovery-open-android-settings",
        ):
            self.assertNotIn(forbidden, source)
        self.assertIn("Settings.ACTION_APPLICATION_DETAILS_SETTINGS", source)

        resource_files = [
            *ROOT.glob("feature/settings/src/main/res/values*/strings.xml"),
            *ROOT.glob("feature/desktop/src/main/res/values*/strings.xml"),
        ]
        resources = "\n".join(path.read_text() for path in resource_files)
        for forbidden in (
            "open_android_settings",
            "recovery_android_settings",
            "Android 桌面设置",
            "Android Home settings",
        ):
            self.assertNotIn(forbidden, resources)

    def test_desktop_back_is_bounded_inside_the_shell(self):
        reducer = (ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt").read_text()
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        jvm = (ROOT / "core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/LauncherNavigationTest.kt").read_text()
        stories = (ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt").read_text()
        self.assertNotIn("RequestHostExit", reducer + activity)
        self.assertNotIn("finishAfterTransition", activity)
        self.assertIn("backAtStartStaysInsideShellWithoutEffect", jvm)
        self.assertIn("backAtShellDesktopNeverFinishesHost", stories)

    def test_product_activities_are_portrait_only(self):
        app_manifest = (ROOT / "app-shell/src/main/AndroidManifest.xml").read_text()
        lab_manifest = (ROOT / "feature/shell-lab/src/main/AndroidManifest.xml").read_text()
        chart = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        stories = (ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt").read_text()
        self.assertIn('android:screenOrientation="portrait"', app_manifest)
        self.assertIn('android:screenOrientation="portrait"', lab_manifest)
        self.assertNotIn("fullSensor", app_manifest + lab_manifest)
        self.assertNotIn("configuration.orientation", chart)
        self.assertIn("shellActivityIsPortraitOnly", stories)


if __name__ == "__main__":
    unittest.main()
