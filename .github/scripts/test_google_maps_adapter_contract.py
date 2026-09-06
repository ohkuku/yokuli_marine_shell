from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class GoogleMapsAdapterContractTest(unittest.TestCase):
    def test_key_flows_from_environment_to_manifest_without_exposing_value(self):
        gradle = (ROOT / "app-shell/build.gradle.kts").read_text()
        manifest = (ROOT / "app-shell/src/main/AndroidManifest.xml").read_text()
        self.assertIn('providers.environmentVariable("GOOGLE_MAPS_ANDROID_API_KEY")', gradle)
        self.assertIn('manifestPlaceholders["GOOGLE_MAPS_ANDROID_API_KEY"]', gradle)
        self.assertIn('buildConfigField("boolean", "GOOGLE_MAPS_CONFIGURED"', gradle)
        self.assertNotRegex(gradle, r'buildConfigField\(\s*"String"\s*,\s*"GOOGLE_MAPS_ANDROID_API_KEY"')
        self.assertIn('android:name="com.google.android.geo.API_KEY"', manifest)
        self.assertIn('android:value="${GOOGLE_MAPS_ANDROID_API_KEY}"', manifest)

    def test_google_surface_is_an_optional_composition_adapter(self):
        app = (ROOT / "app-shell/build.gradle.kts").read_text()
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        feature = "\n".join(p.read_text() for p in (ROOT / "feature/chart/src/main/java").rglob("*.kt"))
        adapter = (ROOT / "adapter/chart-google/src/main/java/com/yokuli/marine/adapter/chart/google/GoogleMarineChartSurface.kt").read_text()
        self.assertIn('implementation(project(":adapter:chart-google"))', app)
        self.assertIn("BuildConfig.GOOGLE_MAPS_CONFIGURED", graph)
        self.assertIn("GoogleMarineChartSurface", graph)
        self.assertNotIn("com.google.android.gms", feature)
        for required in (
            "MapAction.RendererReady",
            "MapAction.RendererCameraIdle",
            "MapAction.MapTapped",
            "MapAction.MapLongPressed",
            "MapRendererQueryPort",
            "pendingCameraCommand",
            "viewportInsets.leftPx",
        ):
            self.assertIn(required, adapter)
        self.assertNotIn("96.dp", adapter)
        self.assertNotIn("150.dp", adapter)

    def test_all_build_paths_bind_the_repository_secret(self):
        binding = "GOOGLE_MAPS_ANDROID_API_KEY: ${{ secrets.GOOGLE_MAPS_ANDROID_API_KEY }}"
        for workflow in ("android.yml", "release.yml", "nightly.yml"):
            self.assertIn(binding, (ROOT / ".github/workflows" / workflow).read_text())

    def test_no_tracked_google_key_value(self):
        pattern = re.compile(r"AIza[0-9A-Za-z_-]{35}")
        offenders = []
        for path in ROOT.rglob("*"):
            if not path.is_file() or ".git" in path.parts or "build" in path.parts:
                continue
            if path.suffix in {".age", ".png", ".jpg", ".jpeg", ".webp", ".apk", ".aab"}:
                continue
            try:
                if pattern.search(path.read_text()):
                    offenders.append(str(path.relative_to(ROOT)))
            except UnicodeDecodeError:
                pass
        self.assertEqual([], offenders)


if __name__ == "__main__":
    unittest.main()
