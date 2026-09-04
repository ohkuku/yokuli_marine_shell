import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-2"
APPROVED_TAG = "launcher-engine-stage1-approved-v1"
APPROVED_SHA = "df371fbfcb4cd467bccc43dd850e23d9bd7d0e85"


def kotlin_text(root: Path) -> str:
    return "\n".join(path.read_text() for path in root.rglob("*.kt"))


class LauncherStage2EngineContractTest(unittest.TestCase):
    def test_stage_starts_exactly_at_the_approved_stage_one_point(self):
        lock_path = STAGE / "BASELINE_LOCK.json"
        self.assertTrue(lock_path.is_file(), "missing Stage 2 baseline lock")
        lock = json.loads(lock_path.read_text())
        self.assertEqual(2, lock["stage"])
        self.assertEqual(APPROVED_TAG, lock["approvedStage1Tag"])
        self.assertEqual(APPROVED_SHA, lock["startingSha"])
        self.assertEqual("Engine Contract Extraction", lock["scope"])
        self.assertEqual("PENDING_HUMAN_REVIEW", lock["approvalStatus"])
        self.assertFalse(lock["nextStageStarted"])

    def test_required_module_boundaries_exist(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        for module in (
            ":core:shell-contract",
            ":core:shell-engine",
            ":ui:shell-compose",
            ":adapter:shell-android",
        ):
            self.assertIn(f'"{module}"', settings)
        self.assertNotRegex(settings, r'"?:core:shell"?[,)]')

        contract_build = (ROOT / "core/shell-contract/build.gradle.kts").read_text()
        engine_build = (ROOT / "core/shell-engine/build.gradle.kts").read_text()
        self.assertIn("libs.plugins.kotlin.jvm", contract_build)
        self.assertIn("libs.plugins.kotlin.jvm", engine_build)
        self.assertNotIn("libs.plugins.android", contract_build + engine_build)
        self.assertIn('project(":core:shell-contract")', engine_build)
        self.assertNotIn('project(":core:model")', engine_build)
        self.assertFalse((ROOT / "core/shell-engine/src/main/AndroidManifest.xml").exists())

    def test_contract_is_opaque_and_platform_agnostic(self):
        contract = kotlin_text(ROOT / "core/shell-contract/src/main")
        for declaration in (
            "value class LauncherAppId",
            "value class LauncherEntryId",
            "value class LaunchToken",
            "value class TileInstanceId",
            "data class LauncherCatalogSnapshot",
            "data class LauncherAppDescriptor",
            "data class LauncherEntryDescriptor",
            "interface LauncherCatalogContribution",
            "interface LauncherHostPort",
            "suspend fun resolveLaunch(token: LaunchToken): LaunchResolution",
        ):
            self.assertIn(declaration, contract)
        for forbidden in (
            "MarineAppId",
            "LaunchTarget",
            "DestinationId",
            "android.",
            "androidx.",
            "Composable",
            "NavController",
            "ViewModel",
            "Context",
        ):
            self.assertNotIn(forbidden, contract)

    def test_engine_has_no_platform_feature_or_marine_dependencies(self):
        engine = kotlin_text(ROOT / "core/shell-engine/src/main")
        for forbidden in (
            "import android.",
            "import androidx.",
            "com.yokuli.marine",
            "com.yokuli.marine.feature",
            "com.google.android",
            "com.yokuli.marine.core.model",
            "MarineAppId",
            "LaunchTarget",
            "DestinationId",
            "ChartMode",
            "NMEA",
            "GPS",
            "R.string",
            "R.drawable",
        ):
            self.assertNotIn(forbidden, engine)

    def test_release_catalog_is_composed_from_opaque_feature_contributions(self):
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        chart = (
            ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartShellContribution.kt"
        ).read_text()
        settings = (
            ROOT / "feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsShellContribution.kt"
        ).read_text()
        self.assertRegex(
            graph,
            r"productionContributions\s*=\s*listOf\(ChartShellContribution, SettingsShellContribution\)",
        )
        self.assertIn("LauncherCatalog.compose", graph)
        for feature in (chart, settings):
            self.assertIn("LauncherCatalogContribution", feature)
            self.assertIn("LauncherAppId", feature)
            self.assertIn("LaunchToken", feature)
            self.assertNotIn("MarineAppId", feature)
            self.assertNotIn("LaunchTarget", feature)
            self.assertNotIn("DestinationId", feature)

    def test_compose_host_and_android_resolver_keep_features_out_of_engine(self):
        app_build = (ROOT / "app-shell/build.gradle.kts").read_text()
        compose = kotlin_text(ROOT / "ui/shell-compose/src/main")
        android_adapter = kotlin_text(ROOT / "adapter/shell-android/src/main")
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        self.assertIn('project(":ui:shell-compose")', app_build)
        self.assertIn('project(":adapter:shell-android")', app_build)
        self.assertIn("class InternalAppHost", compose)
        self.assertIn("interface InternalAppHostResolver", compose)
        self.assertIn("class DefaultInternalAppHostResolver", android_adapter)
        self.assertIn("class StaticLauncherHostPort", android_adapter)
        self.assertIn("hostFor(task.appId)", activity)
        self.assertNotRegex(activity, r"when\s*\(task\.(?:appId|target\.appId)\)")

    def test_legacy_launcher_domain_types_are_removed(self):
        production = "\n".join(
            kotlin_text(ROOT / module / "src/main")
            for module in ("core/model", "feature/desktop", "feature/chart", "feature/settings", "app-shell")
        )
        for forbidden in ("MarineAppId", "LaunchTarget", "ShellFeatureContribution"):
            self.assertNotIn(forbidden, production)

    def test_ci_runs_an_independent_stage_two_boundary_gate(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        ci_contract = (ROOT / ".github/scripts/test-ci-contract.sh").read_text()
        self.assertIn("id: launcher_stage2_contract", workflow)
        self.assertIn("test_launcher_stage2_contract.py", workflow)
        self.assertIn("LAUNCHER_STAGE2_CONTRACT_RESULT", workflow)
        self.assertIn("launcher_stage2_contract", ci_contract)

    def test_device_gates_execute_current_chart_and_settings_stories(self):
        device_runner = (ROOT / ".github/scripts/run_device_tests.sh").read_text()
        stories = (
            ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt"
        ).read_text()
        for story in (
            "chartTileOpensBrowseOnlySurfaceAndSystemBackReturnsToStart",
            "productionShellExposesOnlyChartAndSettingsWithReusableLargeTitles",
        ):
            self.assertIn(story, stories)
            self.assertIn(story, device_runner)
        self.assertNotIn("anchorTileOpensSharedChartInAnchorModeAndHomeReturnsToStart", device_runner)
        self.assertNotIn("everyCoreAppUsesTheReusableLargeTopLeftTitleContract", device_runner)

    def test_report_records_boundaries_and_stops_before_stage_two_point_five(self):
        report_path = STAGE / "REPORT.md"
        self.assertTrue(report_path.is_file(), "missing Stage 2 report")
        report = report_path.read_text()
        for section in ("Baseline", "Scope", "Architecture", "Interaction", "Tests", "Hardware", "Stop"):
            self.assertIn(f"## {section}", report)
        self.assertIn("Engine Contract Extraction", report)
        self.assertIn("Stage 2.5: NOT STARTED", report)
        self.assertIn("PENDING_HUMAN_REVIEW", report)
        self.assertTrue(
            report.endswith(
                "STOPPED AT STAGE GATE.\nAWAITING HUMAN REVIEW BEFORE NEXT STAGE.\n"
            )
        )


if __name__ == "__main__":
    unittest.main()
