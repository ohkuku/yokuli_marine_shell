from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW01ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_final_identity_model_is_explicit_and_only_completed_successors_are_installed(self):
        model = self.read("app-shell/src/main/java/com/yokuli/marine/shell/YokuliProductModel.kt")
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        self.assertIn('identity("data", "data", "data.overview")', model)
        self.assertIn('identity("navigation", "navigation", "navigation.overview")', model)
        self.assertIn('identity("preferences", "preferences", "preferences.overview")', model)
        installed = re.findall(r"catalogContribution\s*=\s*([A-Z][A-Za-z]+ShellContribution)", graph)
        self.assertEqual(5, len(installed))
        self.assertIn("DataShellContribution", installed)
        self.assertIn("NavigationShellContribution", installed)
        self.assertIn("PreferencesShellContribution", installed)

    def test_storage_and_product_versions_are_separate_and_durable(self):
        persistence = self.read("core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt")
        proto = self.read("adapter/shell-storage/src/main/proto/launcher_state.proto")
        mapper = self.read("adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt")
        self.assertIn("CURRENT_LAUNCHER_PERSISTENCE_SCHEMA = 4", persistence)
        self.assertIn("productModelVersion", persistence)
        self.assertIn("product_model_version = 10", proto)
        self.assertIn("setProductModelVersion", mapper)

    def test_migration_is_install_aware_deterministic_and_covered_by_behavior_tests(self):
        implementation = self.read("core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt")
        tests = self.read("core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/LauncherProductMigrationTest.kt")
        app_test = self.read("app-shell/src/test/java/com/yokuli/marine/shell/YokuliProductModelTest.kt")
        self.assertIn("step.targetEntryId !in installedEntryIds", implementation)
        self.assertIn("thenBy { it.tileId.value }", implementation)
        for story in (
            "twoLegacyDataTilesCollapseToTheEarliestRankAndKeepItsIdentitySizeAndGroup",
            "unavailableReplacementStopsTheVersionChainAndLeavesUserLayoutUntouched",
            "stepsActivateOnlyWhenRealTargetsArriveAndLaunchTokensFollowTheSameBoundary",
            "migrationIsIdempotentAndDoesNotTouchSpacersOrUnrelatedTiles",
            "currentCompositionRootMigratesLegacyMarineTilesOnlyAfterDataHasARealHost",
        ):
            self.assertIn(story, tests + app_test)

    def test_contract_uses_broad_solution_space_and_strict_product_boundaries(self):
        contract = self.read("docs/phases/os-redesign/PRODUCT_ENGINEERING_CONTRACT.md")
        work_package = self.read("docs/phases/os-redesign/work-packages/W01_PRODUCT_ENGINEERING_CONTRACT.md")
        for section in (
            "Intent / 产品愿景",
            "Experience / 用户体验",
            "Domain / 领域边界",
            "Opportunities / 可发展空间",
            "Non-negotiables / 不可协商边界",
            "Forbidden outcomes / 禁止结果",
            "Acceptance stories",
            "Evidence required",
            "Implementation freedom / 实现自由",
            "English translation",
        ):
            self.assertIn(section, contract + work_package)

    def test_baseline_and_execution_state_are_truthful(self):
        baseline = json.loads(self.read("docs/phases/os-redesign/W01_BASELINE_LOCK.json"))
        state = json.loads(self.read("docs/phases/os-redesign/EXECUTION_STATE.json"))
        self.assertEqual(
            ["chart", "chart_library", "data", "navigation", "preferences"],
            baseline["finalProductAppIds"],
        )
        self.assertFalse(baseline["emptyFutureAppsInstalled"])
        self.assertEqual("GITHUB_ACTION_PENDING", baseline["verification"])
        self.assertEqual("IMPLEMENTED_CI_PENDING", state["workPackages"]["W01"]["status"])


if __name__ == "__main__":
    unittest.main()
