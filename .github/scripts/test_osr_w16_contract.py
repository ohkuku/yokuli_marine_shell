#!/usr/bin/env python3
from pathlib import Path
import json
import unittest


ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW16ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_product_contract_keeps_vision_rich_and_evidence_boundaries_strict(self):
        contract = self.read("docs/phases/os-redesign/work-packages/W16_PRODUCT_ENGINEERING_CONTRACT.md")
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 证据关系", "Opportunities / 可发展空间",
            "Non-negotiables / 不可协商边界", "Forbidden outcomes / 禁止结果", "Compatibility / 兼容策略",
            "Acceptance stories", "Evidence required", "Existing code landmarks", "Implementation freedom / 实现自由",
            "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("PASS、FAIL、BLOCKED 或 NOT_RUN", contract)
        self.assertIn("configuration", contract)
        self.assertNotIn("基本完成", self.read("docs/phases/os-redesign/W16_REPORT.md"))

    def test_soak_is_executable_against_real_bounded_pipeline_contract(self):
        soak = self.read("core/marine-data/src/test/kotlin/com/yokuli/marine/data/runtime/NmeaInboundPipelineSoakTest.kt")
        for required in (
            "virtualThirtyMinuteHundredHertzFourConnectionSoakRemainsBounded",
            "DURATION_MILLIS = 30 * 60 * 1_000",
            "TOTAL_RATE_HZ = 100",
            "CONNECTIONS = 4",
            "BAD_FRAME_INTERVAL = 97",
            "SILENT_WINDOW = 300_000L until 312_000L",
            "MAX_RAW_ENTRIES",
            "MAX_RAW_BYTES",
            "MAX_STRUCTURED_INCIDENTS",
        ):
            self.assertIn(required, soak)

    def test_evidence_collector_uses_executed_xml_exact_cases_and_process_markers(self):
        collector = self.read(".github/scripts/collect_w16_machine_evidence.py")
        for required in (
            "ET.parse", "sourceSha256", "NmeaInboundPipelineSoakTest", "LauncherProductMigrationTest",
            "ProtoDataStoreLauncherPersistenceTest", "RoomMapPersistenceTest", "ChartCatalogMigrationTest",
            "C12 external force-stop persistence probe passed",
            "NMEA Sources P6 policy persisted and live values stayed process-local",
            "--require-complete", "--allow-unconfigured-maps",
        ):
            self.assertIn(required, collector)

    def test_ci_runs_every_active_feature_and_emits_final_evidence(self):
        workflow = self.read(".github/workflows/android.yml")
        device = self.read(".github/scripts/run_device_tests.sh")
        for task in (
            ":feature:chart-library:connectedDebugAndroidTest",
            ":feature:navigation:connectedDebugAndroidTest",
            ":feature:nmea-input:connectedDebugAndroidTest",
            ":feature:preferences:connectedDebugAndroidTest",
        ):
            self.assertIn(task, device)
        for required in (
            "id: osr_w16_contract", "test_osr_w16_contract.py", "id: osr_w16_build_evidence",
            "collect_w16_machine_evidence.py", "w16-build-evidence.json", "w16-migration-build.json",
            "id: osr_w16_api34_evidence", "w16-api34-evidence.json", "w16-migration-api34.json",
            "--event-name '${{ github.event_name }}'", "build/w16/**",
        ):
            self.assertIn(required, workflow)

    def test_trusted_alpha_requires_maps_injection_but_runtime_stays_human_evidence(self):
        workflow = self.read(".github/workflows/android.yml")
        emitter = self.read(".github/scripts/emit_google_maps_configuration_evidence.py")
        composer = self.read(".github/scripts/compose_codex_ci_report.py")
        self.assertIn("--require-configured", workflow)
        self.assertIn("--require-configured", emitter)
        self.assertIn("google_maps_build_configuration", composer)
        self.assertIn("google_satellite_runtime", composer)
        self.assertIn("CONFIGURATION_ONLY_NOT_RUNTIME_READINESS", emitter)

    def test_unified_report_contains_migration_and_discrete_acceptance_ledger(self):
        composer = self.read(".github/scripts/compose_codex_ci_report.py")
        for required in (
            "MIGRATION_REPORT.json", "MIGRATION_REPORT.md", "FINAL_ACCEPTANCE_LEDGER.json",
            "FINAL_ACCEPTANCE_LEDGER.md", "AWAITING_HUMAN_REVIEW", "MACHINE_GATE_NOT_PASSED",
            "samsung_square_device", "gnss_permission_background", "boat_tcp_udp",
            "large_real_mbtiles", '"NOT_RUN"',
        ):
            self.assertIn(required, composer)

    def test_report_lock_and_execution_state_are_truthful(self):
        report = self.read("docs/phases/os-redesign/W16_REPORT.md")
        lock = json.loads(self.read("docs/phases/os-redesign/W16_BASELINE_LOCK.json"))
        state = json.loads(self.read("docs/phases/os-redesign/EXECUTION_STATE.json"))
        self.assertIn("DESIGN DECISIONS", report)
        self.assertEqual(["PASS", "FAIL", "BLOCKED", "NOT_RUN"], lock["acceptanceStatuses"])
        self.assertEqual("f61b86e5eb09a088303f9635ec3abf4bd1a22bb7", lock["baseCommit"])
        self.assertFalse(lock["mapsConfigurationMeansRuntimeReady"])
        self.assertEqual("NOT_RUN", lock["humanPhysicalDefault"])
        self.assertEqual("ba2728b2e7d0bd9a5470a74873f042626c873170", state["workPackages"]["W15"]["head"])
        self.assertEqual("W16", state["currentWorkPackage"])


if __name__ == "__main__":
    unittest.main()
