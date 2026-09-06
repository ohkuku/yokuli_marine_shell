from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW03ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_data_domain_has_semantic_sections_groups_and_real_evidence(self):
        domain = self.read("feature/data/src/main/java/com/yokuli/marine/feature/data/DataDomain.kt")
        for name in ("OVERVIEW", "INPUTS", "SOURCES", "FLOW", "DIAGNOSTICS"):
            self.assertIn(name, domain)
        for name in ("POSITION_AND_MOTION", "HEADING", "DEPTH", "WIND"):
            self.assertIn(name, domain)
        for evidence in ("dataKeys", "sentenceIds", "formatters", "phoneProviders", "availabilityByKey"):
            self.assertIn(evidence, domain)

    def test_group_selection_is_one_core_atomic_transaction(self):
        command = self.read("core/marine-data/src/main/kotlin/com/yokuli/marine/data/source/DefaultMarineSourceRuntime.kt")
        reducer = self.read("core/marine-data/src/main/kotlin/com/yokuli/marine/data/source/SourceSelectionReducer.kt")
        domain = self.read("feature/data/src/main/java/com/yokuli/marine/feature/data/DataDomain.kt")
        tests = self.read("core/marine-data/src/test/kotlin/com/yokuli/marine/data/source/AtomicSourceSelectionTest.kt")
        self.assertIn("ApplyAtomically", command)
        self.assertIn("ApplyAtomically", reducer)
        self.assertIn("SourceSelectionCommand.ApplyAtomically(preferences)", domain)
        self.assertIn("unavailableMemberRejectsTheWholeTransactionWithoutPartialSelection", tests)
        self.assertIn("groupPreferencesAdvanceOneRevisionAndPersistAsOneTransition", tests)

    def test_no_silent_failover_group_evidence_and_phone_demand_have_behavior_tests(self):
        tests = self.read("feature/data/src/test/java/com/yokuli/marine/feature/data/DataDomainProjectorTest.kt")
        for story in (
            "groupCandidateProjectionKeepsSemanticCoverageAndActualEvidence",
            "groupSelectionPlanIsOneAtomicCommandAndDisablesUnsupportedKeys",
            "selectedUnavailableSourceNeverSilentlyFailsOverToLiveBackup",
            "mixedLegacyPerKeySelectionsRemainVisibleInsteadOfInventingOneGroupSource",
            "phoneDemandExistsOnlyWhenPhoneIsActuallySelectedForOsData",
        ):
            self.assertIn(story, tests)
        demand = self.read("core/marine-data/src/main/kotlin/com/yokuli/marine/data/phone/PhoneLocationDemand.kt")
        self.assertIn("decision.selectedSource == PHONE_SYSTEM_LOCATION_SOURCE", demand)

    def test_new_domain_reuses_core_and_does_not_depend_on_legacy_features_or_adapter(self):
        build = self.read("feature/data/build.gradle.kts")
        domain = self.read("feature/data/src/main/java/com/yokuli/marine/feature/data/DataDomain.kt")
        self.assertIn('project(":core:marine-data")', build)
        self.assertNotIn("feature:nmea-input", build)
        self.assertNotIn("feature:data-sources", build)
        self.assertNotIn("adapter:marine-data-android", build)
        self.assertNotIn("feature.nmeainput", domain)
        self.assertNotIn("feature.datasources", domain)

    def test_successors_install_only_after_their_complete_vertical_slices_exist(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        installed = re.findall(r"catalogContribution\s*=\s*([A-Z][A-Za-z]+ShellContribution)", graph)
        self.assertEqual(5, len(installed))
        self.assertIn("DataShellContribution", installed)
        self.assertIn("NavigationShellContribution", installed)
        self.assertTrue((ROOT / "feature/data/src/main/java/com/yokuli/marine/feature/data/DataWorkspace.kt").is_file())

    def test_report_and_baseline_are_truthful(self):
        contract = self.read("docs/phases/os-redesign/work-packages/W03_PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/os-redesign/W03_REPORT.md")
        baseline = json.loads(self.read("docs/phases/os-redesign/W03_BASELINE_LOCK.json"))
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 领域边界",
            "Opportunities / 可发展空间", "Non-negotiables / 不可协商边界",
            "Forbidden outcomes / 禁止结果", "Acceptance stories", "Evidence required",
            "Implementation freedom / 实现自由", "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertFalse(baseline["dataAppInstalled"])
        self.assertEqual("GITHUB_ACTION_PENDING", baseline["verification"])


if __name__ == "__main__":
    unittest.main()
