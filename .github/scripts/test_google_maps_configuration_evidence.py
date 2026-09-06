from pathlib import Path
from tempfile import TemporaryDirectory
import json
import sys
import unittest


sys.path.insert(0, str(Path(__file__).resolve().parent))
from emit_google_maps_configuration_evidence import inspect  # noqa: E402


class GoogleMapsConfigurationEvidenceTest(unittest.TestCase):
    def fixture(self, configured: bool, manifest_value: str):
        temporary = TemporaryDirectory()
        root = Path(temporary.name)
        config = root / "generated/source/buildConfig/standalone/release/com/yokuli/marine/shell/BuildConfig.java"
        config.parent.mkdir(parents=True)
        config.write_text(
            'public final class BuildConfig {\n'
            ' public static final String APPLICATION_ID = "com.yokuli.marine";\n'
            f' public static final boolean GOOGLE_MAPS_CONFIGURED = {str(configured).lower()};\n'
            '}\n',
            encoding="utf-8",
        )
        manifest = root / "intermediates/merged_manifests/standaloneRelease/process/AndroidManifest.xml"
        manifest.parent.mkdir(parents=True)
        manifest.write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            '<application><meta-data android:name="com.google.android.geo.API_KEY" '
            f'android:value="{manifest_value}" /></application></manifest>',
            encoding="utf-8",
        )
        return temporary, root

    def test_reports_configured_without_copying_the_key(self):
        temporary, root = self.fixture(True, "test-value-never-emit")
        self.addCleanup(temporary.cleanup)
        result = inspect(root)
        self.assertTrue(result["configurationConsistent"])
        self.assertTrue(result["buildConfigConfigured"])
        self.assertNotIn("test-value-never-emit", json.dumps(result))

    def test_reports_an_optional_unconfigured_build_truthfully(self):
        temporary, root = self.fixture(False, "MAPS_API_KEY_NOT_CONFIGURED")
        self.addCleanup(temporary.cleanup)
        result = inspect(root)
        self.assertTrue(result["configurationConsistent"])
        self.assertFalse(result["manifestConfigured"])

    def test_exposes_inconsistent_injection_without_revealing_values(self):
        temporary, root = self.fixture(True, "MAPS_API_KEY_NOT_CONFIGURED")
        self.addCleanup(temporary.cleanup)
        result = inspect(root)
        self.assertFalse(result["configurationConsistent"])


if __name__ == "__main__":
    unittest.main()
