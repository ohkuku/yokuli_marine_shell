#!/usr/bin/env python3
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW12ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_navigation_is_one_complete_installed_app_with_real_destinations(self):
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        contribution = self.read("feature/navigation/src/main/java/com/yokuli/marine/feature/navigation/NavigationShellContribution.kt")
        contract = self.read("feature/navigation/src/main/java/com/yokuli/marine/feature/navigation/NavigationUiContract.kt")
        self.assertEqual(1, graph.count("catalogContribution = NavigationShellContribution"))
        self.assertIn("dynamicLaunchTokenMatcher = NavigationDestinations::accepts", graph)
        for section in ("OVERVIEW", "WAYPOINTS", "ROUTES", "GPX", "ACTIVE"):
            self.assertIn(section, contract)
        for size in ("ICON_1X1", "STANDARD_2X2", "WIDE_4X2"):
            self.assertIn(size, contribution)

    def test_navigation_owns_workflow_but_reuses_shared_truth_and_shell_handoff(self):
        build = self.read("feature/navigation/build.gradle.kts")
        coordinator = self.read("feature/navigation/src/main/java/com/yokuli/marine/feature/navigation/NavigationCoordinator.kt")
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        activity = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt")
        self.assertIn("NavigationLibraryPort", coordinator)
        self.assertIn("ActiveNavigationRuntimePort", coordinator)
        self.assertIn("Channel<Request>(32)", coordinator)
        for forbidden in (":feature:chart", ":adapter:", ":app-shell"):
            self.assertNotIn(forbidden, build)
        self.assertIn("NavigationGpxExchangeSurface", graph)
        self.assertIn("NavigationEffect.ShowRouteInChart", activity)
        self.assertIn("ChartDestinations.route(effect.routeId)", activity)

    def test_truth_and_safety_acceptance_stories_are_executable(self):
        tests = "\n".join(path.read_text(encoding="utf-8") for path in ROOT.rglob("*Test.kt"))
        for story in (
            "waypoint and route editing commit through the one revision checked library",
            "stale write remains visible as conflict and never overwrites the newer library",
            "active route deletion is blocked while typed start stop and Chart handoff stay explicit",
            "Navigation owns its back stack before Shell returns from overview root",
            "route preview exposes geometry legs explicit start and content handoff",
            "stale route deep link explains unavailable content without starting navigation",
        ):
            self.assertIn(story, tests)
        workspace = self.read("feature/navigation/src/main/java/com/yokuli/marine/feature/navigation/NavigationWorkspace.kt")
        visual = self.read("feature/navigation/src/main/java/com/yokuli/marine/feature/navigation/NavigationLauncherPresentation.kt")
        self.assertIn("NavigationRouteMath.summarize", workspace)
        self.assertNotIn("GoogleMap", workspace + visual)
        self.assertNotIn("autopilot", (workspace + visual).lower())

    def test_historical_locks_remain_immutable_while_current_surface_is_explicitly_superseded(self):
        w04_lock = json.loads(self.read("docs/phases/os-redesign/W04_BASELINE_LOCK.json"))
        w12_report = self.read("docs/phases/os-redesign/W12_REPORT.md")
        self.assertEqual(["chart", "settings", "data", "chart_library"], w04_lock["productionApps"])
        self.assertIn("W01/W03/W04", w12_report)
        self.assertIn("五个", w12_report)

    def test_contract_report_baseline_and_execution_state_are_truthful(self):
        contract = self.read("docs/phases/os-redesign/work-packages/W12_PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/os-redesign/W12_REPORT.md")
        lock = json.loads(self.read("docs/phases/os-redesign/W12_BASELINE_LOCK.json"))
        state = json.loads(self.read("docs/phases/os-redesign/EXECUTION_STATE.json"))
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 领域关系",
            "Opportunities / 可发展空间", "Non-negotiables / 不可协商边界",
            "Forbidden outcomes / 禁止结果", "Acceptance stories", "Evidence required",
            "Existing code landmarks", "Implementation freedom / 实现自由", "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertTrue(lock["navigationAppRegistered"])
        self.assertFalse(lock["defaultStartPinsNavigation"])
        self.assertFalse(lock["legacyChartManagementRemoved"])
        self.assertEqual("IMPLEMENTED_CI_PENDING", state["workPackages"]["W12"]["status"])


if __name__ == "__main__":
    unittest.main()
