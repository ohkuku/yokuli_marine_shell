from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class GoogleMapsAdapterContractTest(unittest.TestCase):
    def test_google_maps_sdk_is_isolated_in_an_adapter_module(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        catalog = (ROOT / "gradle/libs.versions.toml").read_text()
        adapter_build = ROOT / "adapter/chart-google/build.gradle.kts"
        adapter_source = ROOT / (
            "adapter/chart-google/src/main/java/com/yokuli/marine/adapter/chart/google/"
            "GoogleMarineChartSurface.kt"
        )
        feature_sources = "\n".join(
            path.read_text()
            for path in (ROOT / "feature/chart/src/main/java").rglob("*.kt")
        )

        self.assertIn('\":adapter:chart-google\"', settings)
        self.assertRegex(catalog, r'playServicesMaps\s*=\s*"20\.0\.0"')
        self.assertTrue(adapter_build.is_file(), "missing Google chart adapter Gradle module")
        self.assertTrue(adapter_source.is_file(), "missing Google chart surface adapter")
        self.assertIn("libs.play.services.maps", adapter_build.read_text())
        self.assertIn("com.google.android.gms.maps", adapter_source.read_text())
        self.assertIn("syncTo(lifecycle.currentState)", adapter_source.read_text())
        self.assertNotIn("com.google.android", feature_sources)

    def test_api_key_flows_from_vault_environment_to_manifest_without_buildconfig_value(self):
        gradle = (ROOT / "app-shell/build.gradle.kts").read_text()
        manifest = (ROOT / "app-shell/src/main/AndroidManifest.xml").read_text()

        self.assertIn('providers.environmentVariable("GOOGLE_MAPS_ANDROID_API_KEY")', gradle)
        self.assertIn('manifestPlaceholders["GOOGLE_MAPS_ANDROID_API_KEY"]', gradle)
        self.assertIn('buildConfigField("boolean", "GOOGLE_MAPS_CONFIGURED"', gradle)
        self.assertNotRegex(
            gradle,
            r'buildConfigField\(\s*"String"\s*,\s*"GOOGLE_MAPS_ANDROID_API_KEY"',
        )
        self.assertIn('android:name="com.google.android.geo.API_KEY"', manifest)
        self.assertIn('android:value="${GOOGLE_MAPS_ANDROID_API_KEY}"', manifest)

    def test_missing_ci_key_uses_an_explicit_provider_free_workbench(self):
        gradle = (ROOT / "app-shell/build.gradle.kts").read_text()
        shell = "\n".join(
            path.read_text()
            for path in (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell").glob("*.kt")
        )
        chart = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()

        self.assertIn("MAPS_API_KEY_NOT_CONFIGURED", gradle)
        self.assertIn("BuildConfig.GOOGLE_MAPS_CONFIGURED", shell)
        self.assertIn("GoogleMarineChartSurface", shell)
        self.assertIn("OfflineMarineChartSurface", shell)
        self.assertIn('testTag("chart-surface-google")', shell)
        self.assertIn('testTag("chart-surface-offline-empty")', shell)
        self.assertNotIn("MarineChartDemoSurface", shell + chart)
        self.assertNotIn('testTag("chart-surface-demo")', shell)
        self.assertNotIn("GoogleMap", chart)

    def test_tracked_text_contains_no_google_api_key_value(self):
        google_key = re.compile(r"AIza[0-9A-Za-z_-]{35}")
        offenders = []
        for path in ROOT.rglob("*"):
            if not path.is_file() or ".git" in path.parts or "build" in path.parts:
                continue
            if path.suffix in {".age", ".png", ".jpg", ".jpeg", ".webp", ".apk", ".aab"}:
                continue
            try:
                text = path.read_text()
            except UnicodeDecodeError:
                continue
            if google_key.search(text):
                offenders.append(str(path.relative_to(ROOT)))
        self.assertEqual([], offenders, "tracked-looking text contains a Google API key value")

    def test_github_builds_consume_only_the_repository_maps_secret(self):
        android = (ROOT / ".github/workflows/android.yml").read_text()
        release = (ROOT / ".github/workflows/release.yml").read_text()
        secret_binding = "GOOGLE_MAPS_ANDROID_API_KEY: ${{ secrets.GOOGLE_MAPS_ANDROID_API_KEY }}"

        self.assertIn(secret_binding, android)
        self.assertIn(secret_binding, release)
        self.assertIn("Missing required Actions secret: $name", release)


if __name__ == "__main__":
    unittest.main()
