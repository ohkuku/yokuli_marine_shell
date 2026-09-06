from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW04ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_one_real_data_app_replaces_two_visible_protocol_apps(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        installed = re.findall(r"catalogContribution\s*=\s*([A-Z][A-Za-z]+ShellContribution)", graph)
        self.assertEqual(
            ["ChartShellContribution", "SettingsShellContribution", "DataShellContribution", "ChartLibraryShellContribution"],
            installed,
        )
        self.assertNotIn("catalogContribution = NmeaInputShellContribution", graph)
        self.assertNotIn("catalogContribution = DataSourcesShellContribution", graph)
        self.assertIn("NmeaInputWorkspace(", graph)
        self.assertIn("embedded = true", graph)

    def test_data_has_real_five_section_ui_and_only_projects_runtime_evidence(self):
        workspace = self.read("feature/data/src/main/java/com/yokuli/marine/feature/data/DataWorkspace.kt")
        domain = self.read("feature/data/src/main/java/com/yokuli/marine/feature/data/DataDomain.kt")
        for token in ("Overview", "Sources", "Flow", "Diagnostics", "WpLiveField", "WpLiveConsole"):
            self.assertIn(token, workspace)
        for evidence in ("nmea.sentenceCatalog.entries", "nmea.rawPreview.entries", "sources.resolvedData.items"):
            self.assertIn(evidence, domain)
        for fake in ("6.2 kn", "COG 184", "MOTUIHE", "FakeNmea"):
            self.assertNotIn(fake, workspace + domain)

    def test_phone_is_process_owned_selection_demand_not_a_ui_toggle(self):
        application = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ShellApplication.kt")
        runtime = self.read("feature/data/src/main/java/com/yokuli/marine/feature/data/DataPhoneDemandRuntime.kt")
        ui = self.read("feature/data/src/main/java/com/yokuli/marine/feature/data/DataUiContract.kt")
        self.assertIn("dataPhoneDemandRuntime", application)
        self.assertIn("scope = applicationScope", application)
        self.assertIn("PhoneLocationDemandPolicy.resolve", runtime)
        self.assertIn("sources.revision == 0L", runtime)
        self.assertIn("SourceGroupSelectionAdapter.select", runtime)
        self.assertNotIn("EnablePhone", ui)
        self.assertNotIn("DisablePhone", ui)

    def test_legacy_tokens_redirect_without_legacy_catalog_entries_and_migration_activates(self):
        destination = self.read("feature/data/src/main/java/com/yokuli/marine/feature/data/DataShellContribution.kt")
        tests = self.read("feature/data/src/test/java/com/yokuli/marine/feature/data/DataAppContractTest.kt")
        migration = self.read("app-shell/src/test/java/com/yokuli/marine/shell/YokuliProductModelTest.kt")
        for token in ("nmea.root", "nmea.connection.", "sources.root", "sources.attention", "sources.connection."):
            self.assertIn(token, destination)
        self.assertIn("currentAndLegacyTokensResolveIntoOneDataApp", tests)
        self.assertIn("currentCompositionRootMigratesLegacyMarineTilesOnlyAfterDataHasARealHost", migration)

    def test_dependencies_keep_feature_boundary_and_chinese_english_resources(self):
        data_build = self.read("feature/data/build.gradle.kts")
        app_build = self.read("app-shell/build.gradle.kts")
        self.assertIn('project(":core:marine-data")', data_build)
        for forbidden in ("feature:nmea-input", "feature:data-sources", "adapter:marine-data-android"):
            self.assertNotIn(forbidden, data_build)
        self.assertNotIn('implementation(project(":feature:data-sources"))', app_build)
        base = self.read("feature/data/src/main/res/values/strings.xml")
        english = self.read("feature/data/src/main/res/values-en/strings.xml")
        qualified = self.read("feature/data/src/main/res/values-zh-rCN/strings.xml")
        self.assertIn("数据", base)
        self.assertIn("Data", english)
        self.assertIn("zh-CN", qualified)

    def test_acceptance_tests_and_truthful_documents_exist(self):
        tests = "\n".join(path.read_text(encoding="utf-8") for path in ROOT.rglob("*Test.kt"))
        for story in (
            "dataJourneyMovesInputsToSourcesToOverviewAndRootBackReturnsToStart",
            "dataAppOwnsPinningAndItsDeclaredThreeSizeCycle",
            "dataRendererSurvivesThreeSizesTwoThemesAndLargeType",
            "selectedPhoneDemandSurvivesUiAndNoDemandStopsOnlyAfterSourceInitialization",
            "missingPermissionIsReturnedAsTypedRecoveryInsteadOfPretendingPhoneIsLive",
        ):
            self.assertIn(story, tests)
        contract = self.read("docs/phases/os-redesign/work-packages/W04_PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/os-redesign/W04_REPORT.md")
        lock = json.loads(self.read("docs/phases/os-redesign/W04_BASELINE_LOCK.json"))
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 领域边界",
            "Opportunities / 可发展空间", "Non-negotiables / 不可协商边界",
            "Forbidden outcomes / 禁止结果", "Acceptance stories", "Evidence required",
            "Implementation freedom / 实现自由", "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertEqual(["chart", "settings", "data", "chart_library"], lock["productionApps"])
        self.assertEqual("GITHUB_ACTION_PENDING", lock["verification"])


if __name__ == "__main__":
    unittest.main()
