#!/usr/bin/env python3
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class NmeaSourcesP5Contract(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_both_features_own_catalog_and_three_size_visual_contributions(self):
        for folder, package, prefix in [
            ("nmea-input", "nmeainput", "NmeaInput"),
            ("data-sources", "datasources", "DataSources"),
        ]:
            base = f"feature/{folder}/src/main/java/com/yokuli/marine/feature/{package}"
            shell = self.read(f"{base}/{prefix}ShellContribution.kt")
            visual = self.read(f"{base}/{prefix}LauncherPresentation.kt")
            self.assertIn("LauncherCatalogContribution", shell)
            for size in ["ICON_1X1", "STANDARD_2X2", "WIDE_4X2"]:
                self.assertIn(size, shell)
                self.assertIn(size, visual)

    def test_production_registry_keeps_the_four_p5_apps_after_later_installations(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        for contribution in (
            "ChartShellContribution",
            "SettingsShellContribution",
            "NmeaInputShellContribution",
            "DataSourcesShellContribution",
        ):
            self.assertIn(contribution, graph)

    def test_default_start_stays_chart_and_settings_only(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        document = graph[graph.index("val defaultStartDocument"):]
        self.assertNotIn("NmeaInputDestinations.EntryId", document)
        self.assertNotIn("DataSourcesDestinations.EntryId", document)
        self.assertIn("ChartDestinations.EntryId", document)
        self.assertIn("SettingsDestinations.EntryId", document)

    def test_tiles_and_status_are_pure_projectors_without_runtime_commands(self):
        files = [
            self.read("feature/nmea-input/src/main/java/com/yokuli/marine/feature/nmeainput/NmeaInputLauncherProjection.kt"),
            self.read("feature/data-sources/src/main/java/com/yokuli/marine/feature/datasources/DataSourcesLauncherProjection.kt"),
        ]
        for source in files:
            self.assertIn("object", source)
            self.assertNotIn("execute(", source)
            self.assertNotIn("Socket", source)
            self.assertNotIn("PhoneLocationCommand", source)

    def test_status_strip_has_two_independent_typed_entries(self):
        strip = self.read("feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStatusStrip.kt")
        activity = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt")
        self.assertIn("WpStatusStripItem", strip)
        self.assertIn("nmeaStatus", activity)
        self.assertIn("dataSourcesStatus", activity)

    def test_product_keeps_portrait_launcher_only_and_never_home(self):
        manifest = self.read("app-shell/src/main/AndroidManifest.xml")
        self.assertIn('android:screenOrientation="portrait"', manifest)
        self.assertNotIn("android.intent.category.HOME", manifest)
        production = "\n".join(
            p.read_text(encoding="utf-8") for p in (ROOT / "app-shell/src/main").rglob("*.kt")
        )
        self.assertNotIn("ACTION_HOME_SETTINGS", production)

    def test_p5_named_matrix_scenarios_exist(self):
        tests = "\n".join(p.read_text(encoding="utf-8") for p in [
            ROOT / "feature/nmea-input/src/test/kotlin/com/yokuli/marine/feature/nmeainput/NmeaInputLauncherProjectorTest.kt",
            ROOT / "feature/data-sources/src/test/kotlin/com/yokuli/marine/feature/datasources/DataSourcesLauncherProjectorTest.kt",
            ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/NmeaSourcesShellStoryTest.kt",
        ])
        for name in [
            "udpListeningWithoutDatagramsIsWaitingNotConnectedOrReceiving",
            "interruptionOutranksASecondReceivingConnectionAndTargetsTheProblem",
            "phoneOnlySelectionIsAHealthyFirstClassSource",
            "needsSelectionAndSelectedStaleAreDistinctAttentionFacts",
            "launcherDisplayFreezesDecorativeChangesDuringEditButNeverHidesAttention",
            "bothAppsAreDiscoverableAndRootBackReturnsToTheInAppStart",
            "bothFeatureOwnedRendererSetsSurviveThreeSizesTwoThemesAndLargeType",
        ]:
            self.assertIn(name, tests)

    def test_launcher_copy_exists_in_chinese_primary_english_and_chinese_qualified_resources(self):
        for feature, names in {
            "nmea-input": [
                "launcher_nmea_unconfigured",
                "launcher_nmea_waiting",
                "launcher_nmea_receiving",
                "launcher_status_nmea_expanded",
            ],
            "data-sources": [
                "launcher_sources_waiting",
                "launcher_sources_using",
                "launcher_sources_attention",
                "launcher_status_sources_expanded",
            ],
        }.items():
            for folder in ["values", "values-en", "values-zh-rCN"]:
                resource = self.read(f"feature/{feature}/src/main/res/{folder}/strings.xml")
                for name in names:
                    self.assertIn(f'name="{name}"', resource, f"{feature}/{folder} misses {name}")


if __name__ == "__main__":
    unittest.main()
