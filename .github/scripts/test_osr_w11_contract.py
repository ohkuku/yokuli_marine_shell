#!/usr/bin/env python3
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW11ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_runtime_is_pure_serial_and_has_no_vessel_output_contract(self):
        build = self.read("core/navigation-domain/build.gradle.kts")
        runtime = self.read("core/navigation-domain/src/main/kotlin/com/yokuli/marine/navigation/domain/DefaultActiveNavigationRuntime.kt")
        models = self.read("core/navigation-domain/src/main/kotlin/com/yokuli/marine/navigation/domain/ActiveNavigationModels.kt")
        self.assertIn("kotlin.jvm", build)
        self.assertIn("private val mutex = Mutex()", runtime)
        self.assertIn("routeRevision", models)
        self.assertIn("NavigationSessionState", models)
        production = (runtime + models).lower()
        for forbidden in ("autopilot", "steeringcommand", "nmeaoutput", "transmitnmea"):
            self.assertNotIn(forbidden, production)

    def test_solution_and_restore_contracts_have_behavior_tests(self):
        solver = self.read("core/navigation-domain/src/main/kotlin/com/yokuli/marine/navigation/domain/ActiveNavigationSolver.kt")
        tests = self.read("core/navigation-domain/src/test/kotlin/com/yokuli/marine/navigation/domain/DefaultActiveNavigationRuntimeTest.kt")
        self.assertIn("Geodesic.WGS84", solver)
        for story in (
            "process restore pauses an active session and waits for a fresh process input",
            "arrival policy advances once and stale revisions cannot move the active leg",
            "missing or changed restored route is rejected without inventing a session",
        ):
            self.assertIn(story, tests)

    def test_input_is_os_resolved_read_only_and_identity_is_opaque(self):
        adapter = self.read("app-shell/src/main/java/com/yokuli/marine/shell/MarineSourceNavigationInputPort.kt")
        self.assertIn("resolvedData.items[DataKey.Position]", adapter)
        self.assertIn("resolvedData.items[DataKey.SpeedOverGround]", adapter)
        self.assertIn("resolvedData.items[DataKey.CourseOverGround]", adapter)
        self.assertIn("SHA-256", adapter)
        for forbidden in ("startNmea", "requestPermission", "SourcePreference", "PhoneLocation"):
            self.assertNotIn(forbidden, adapter)

    def test_session_persistence_and_chart_injection_are_truthful(self):
        store = self.read("adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/ProtoDataStoreActiveNavigationSessionStore.kt")
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        app = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ShellApplication.kt")
        self.assertNotIn("ReplaceFileCorruptionHandler", store)
        self.assertIn("activeNavigationState.session == null", graph)
        self.assertIn("ActiveNavigationStrip", graph)
        self.assertIn("activeNavigationRuntime", app)
        self.assertIn("applicationScope.launch", app)

    def test_contract_report_and_lock_preserve_scope(self):
        contract = self.read("docs/phases/os-redesign/work-packages/W11_PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/os-redesign/W11_REPORT.md")
        lock = json.loads(self.read("docs/phases/os-redesign/W11_BASELINE_LOCK.json"))
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 领域关系",
            "Opportunities / 可发展空间", "Non-negotiables / 不可协商边界",
            "Forbidden outcomes / 禁止结果", "Acceptance stories", "Evidence required",
            "Existing code landmarks", "Implementation freedom / 实现自由", "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertEqual("PAUSED", lock["restoreActiveAs"])
        self.assertFalse(lock["autopilotOutput"])
        self.assertFalse(lock["navigationAppRegistered"])


if __name__ == "__main__":
    unittest.main()
