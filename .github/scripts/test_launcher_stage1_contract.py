import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-1"
APPROVED_TAG = "launcher-engine-stage0-approved-v1.1"
APPROVED_SHA = "16b0e5cd1c8fa2e5f4b78aefadf3fa7c012698b2"


class LauncherStage1ProductSurfaceContractTest(unittest.TestCase):
    def test_stage_starts_exactly_at_the_approved_stage_zero_point(self):
        lock_path = STAGE / "BASELINE_LOCK.json"
        self.assertTrue(lock_path.is_file(), "missing Stage 1 baseline lock")
        lock = json.loads(lock_path.read_text())
        self.assertEqual(1, lock["stage"])
        self.assertEqual(APPROVED_TAG, lock["approvedStage0Tag"])
        self.assertEqual(APPROVED_SHA, lock["startingSha"])
        self.assertEqual("Product Surface Reduction", lock["scope"])
        self.assertEqual("PENDING_HUMAN_REVIEW", lock["approvalStatus"])

    def test_release_catalog_and_start_document_are_exactly_chart_and_settings(self):
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        contributions = re.search(
            r"productionContributions\s*=\s*listOf\((.*?)\)",
            graph,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(contributions)
        self.assertEqual(
            ["ChartShellContribution", "SettingsShellContribution"],
            re.findall(r"\b[A-Z][A-Za-z]+ShellContribution\b", contributions.group(1)),
        )
        self.assertEqual(2, graph.count("TilePlacement("))
        self.assertEqual(
            ["ChartDestinations.EntryId", "SettingsDestinations.EntryId"],
            re.findall(r"entryId\s*=\s*([A-Za-z]+Destinations\.EntryId)", graph),
        )

    def test_removed_feature_modules_have_no_active_source_or_dependency(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        app = (ROOT / "app-shell/build.gradle.kts").read_text()
        for name in ("cockpit", "library", "system"):
            module = f":feature:{name}"
            with self.subTest(module=module):
                self.assertNotIn(f'"{module}"', settings)
                self.assertNotIn(f'project("{module}")', app)
                self.assertFalse((ROOT / f"feature/{name}/src").exists())
                self.assertFalse((ROOT / f"feature/{name}/build.gradle.kts").exists())

    def test_chart_exposes_one_browse_destination_and_no_runtime_navigation_modes(self):
        contribution = (
            ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartShellContribution.kt"
        ).read_text()
        contract = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartUiContract.kt").read_text()
        workspace = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        self.assertEqual(["Browse"], re.findall(r"val\s+(\w+)\s*=\s*DestinationId\(", contribution))
        self.assertIn('DestinationId("chart.browse")', contribution)
        self.assertIn('testTag("chart-workspace-browse")', workspace)
        for forbidden in (
            "ChartMode",
            "TRACKING",
            "NAVIGATION",
            "ANCHOR",
            "TRIP",
            "SURVEY",
            "courseOverGround",
            "speedOverGround",
            "activeRoute",
            "vesselPosition",
        ):
            self.assertNotIn(forbidden, contribution + contract + workspace)

    def test_settings_contains_only_truthful_shell_configuration_and_build_facts(self):
        contract = (
            ROOT / "feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsUiContract.kt"
        ).read_text()
        workspace = (
            ROOT / "feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsWorkspace.kt"
        ).read_text()
        enum_body = contract.split("enum class SettingsSection", 1)[1].split("}", 1)[0]
        self.assertEqual(
            ["OVERVIEW", "APPEARANCE", "START_SCREEN", "MAP", "LANGUAGE", "ABOUT"],
            re.findall(r"\b[A-Z][A-Z_]+\b", enum_body),
        )
        for fact in (
            "mapConfigured",
            "pinnedTileCount",
            "desktopDocumentVersion",
            "versionName",
            "buildVariant",
            "gitSha",
            "debugShellLabAvailable",
        ):
            self.assertIn(fact, contract + workspace)
        for forbidden in ("CONNECTIONS", "DATA_SOURCES", "DEVICES", "SAFETY", "NMEA"):
            self.assertNotIn(forbidden, contract + workspace)

    def test_release_copy_contains_no_coming_soon_or_fake_marine_values(self):
        release_modules = (
            "app-shell",
            "feature/desktop",
            "feature/chart",
            "feature/settings",
        )
        text = "\n".join(
            path.read_text()
            for module in release_modules
            for path in (ROOT / module / "src/main").rglob("*")
            if path.is_file() and path.suffix in {".kt", ".xml"}
        )
        for forbidden in (
            "Coming Soon",
            "COMING SOON",
            "即将推出",
            "MOTUIHE",
            "6.2 kn",
            "6.2 节",
            "COG 184",
            "SOG 6.2",
            "HDG 184",
            "32 / 60",
            "NOT ARMED",
            "SURVEY READY",
            "27 TRIPS",
        ):
            self.assertNotIn(forbidden, text)
        self.assertNotRegex(text, r"\b(?:SOG|COG)\s*[:=]?\s*\d")

    def test_shell_lab_is_debug_only_in_source_and_release_apk_gate(self):
        app = (ROOT / "app-shell/build.gradle.kts").read_text()
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        ci_contract = (ROOT / ".github/scripts/test-ci-contract.sh").read_text()
        apk_contract = ROOT / ".github/scripts/test-release-product-surface.sh"
        self.assertIn('debugImplementation(project(":feature:shell-lab"))', app)
        self.assertNotIn('releaseImplementation(project(":feature:shell-lab"))', app)
        self.assertNotRegex(app, r"(?m)^\s*implementation\(project\(\":feature:shell-lab\"\)\)")
        self.assertTrue(apk_contract.is_file(), "missing release APK binary surface inspection")
        self.assertIn("id: launcher_stage1_contract", workflow)
        self.assertIn("test_launcher_stage1_contract.py", workflow)
        self.assertIn("test-release-product-surface.sh", workflow)
        self.assertIn("LAUNCHER_STAGE1_CONTRACT_RESULT", workflow)
        self.assertIn("launcher_stage1_contract", ci_contract)

    def test_stage_audit_and_report_record_candidate_revalidation_and_stop(self):
        audit_path = STAGE / "PRODUCT_SURFACE_AUDIT.md"
        report_path = STAGE / "REPORT.md"
        self.assertTrue(audit_path.is_file(), "missing Stage 1 product-surface audit")
        self.assertTrue(report_path.is_file(), "missing Stage 1 report")
        audit = audit_path.read_text()
        report = report_path.read_text()
        for evidence in (
            "Chart + Settings",
            "Chart Browse",
            "debugImplementation",
            "ShellLabActivity",
            "Coming Soon",
            "SAFE/SOG/COG/Trip/NMEA",
        ):
            self.assertIn(evidence, audit)
        for section in ("Baseline", "Scope", "Architecture", "Interaction", "Tests", "Hardware", "Stop"):
            self.assertIn(f"## {section}", report)
        self.assertIn("Stage 2: NOT STARTED", report)
        self.assertIn("PENDING_HUMAN_REVIEW", report)
        self.assertTrue(
            report.endswith(
                "STOPPED AT STAGE GATE.\nAWAITING HUMAN REVIEW BEFORE NEXT STAGE.\n"
            )
        )


if __name__ == "__main__":
    unittest.main()
