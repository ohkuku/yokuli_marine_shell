#!/usr/bin/env python3
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class NmeaSourcesP6Contract(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_chart_consumes_the_selected_os_position_through_a_read_only_adapter(self):
        adapter = self.read("app-shell/src/main/java/com/yokuli/marine/shell/MarineSourcePositionPort.kt")
        app = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ShellApplication.kt")
        self.assertIn("ReadOnlyPositionPort", adapter)
        self.assertIn("MarineSourceSnapshot", adapter)
        self.assertIn("MarineSourcePositionPort", app)
        self.assertNotIn("val positionPort = NoSourcePositionPort", app)

    def test_chart_does_not_gain_a_second_location_runtime_or_permission_flow(self):
        chart = "\n".join(
            p.read_text(encoding="utf-8") for p in (ROOT / "feature/chart/src/main").rglob("*.kt")
        )
        self.assertNotIn("LocationManager", chart)
        self.assertNotIn("requestPermissions", chart)
        self.assertNotIn("PhoneLocationCommand", chart)

    def test_cross_app_safety_scenarios_are_executable_tests(self):
        tests = self.read("app-shell/src/test/java/com/yokuli/marine/shell/MarineSourcePositionPortTest.kt")
        cross_app = self.read("app-shell/src/test/java/com/yokuli/marine/shell/MarineDataCrossAppStoryTest.kt")
        for name in [
            "selectedLivePositionFlowsToChartWithMonotonicIdentityAndSameSourceAccuracy",
            "selectedStaleSourceDisconnectsWithoutSilentlySwitchingToLiveAlternative",
            "sourceSwitchIsOrderedAndNeverMixesAccuracyFromAnotherSelectedSourceOrRepeatsAFrame",
        ]:
            self.assertIn(name, tests)
        self.assertIn("selectedPositionHeadingAndAtomicCourseSpeedReachTheExistingChartConsumer", cross_app)
        self.assertIn("chartBridgeNeverCombinesCourseAndSpeedFromDifferentFrames", cross_app)

    def test_existing_lifecycle_and_fault_contracts_remain_named_evidence(self):
        paths = [
            "app-shell/src/androidTest/java/com/yokuli/marine/shell/NmeaInputWorkspaceStoryTest.kt",
            "adapter/marine-data-android/src/test/kotlin/com/yokuli/marine/data/android/NmeaRuntimeLifecycleTest.kt",
            "core/marine-data/src/test/kotlin/com/yokuli/marine/data/source/SourceSelectionReducerTest.kt",
            "core/marine-data/src/test/kotlin/com/yokuli/marine/data/source/SourceSelectionRuntimeTest.kt",
        ]
        tests = "\n".join(self.read(path) for path in paths)
        for name in [
            "leavingWorkspaceDoesNotStopRuntime",
            "networkRestoreDoesNotDuplicateSocket",
            "stopCancelsPendingReconnect",
            "selectedSourceStalesWithoutSilentlyFailingOverAndRecoversInPlace",
            "restartRestoresPreferenceButNeverResurrectsLiveValue",
        ]:
            self.assertIn(name, tests)

    def test_reusable_soak_sender_defaults_to_the_frozen_acceptance_load(self):
        sender = self.read("tools/nmea_soak_sender.py")
        self.assertIn("DEFAULT_DURATION_SECONDS = 30 * 60", sender)
        self.assertIn("DEFAULT_CONNECTIONS = 4", sender)
        self.assertIn("DEFAULT_TOTAL_RATE = 100", sender)
        self.assertIn("bad-frame", sender)
        self.assertIn("silent", sender)

    def test_process_restore_is_an_external_force_stop_probe_not_two_methods_in_one_process(self):
        driver = self.read(".github/scripts/run_nmea_sources_process_restore.sh")
        probe = self.read(
            "app-shell/src/androidTest/java/com/yokuli/marine/shell/NmeaP6ProcessRestartProbeTest.kt"
        )
        self.assertIn("am force-stop com.yokuli.marine", driver)
        self.assertIn("seedNmeaStateBeforeExternalProcessRestart", driver)
        self.assertIn("verifyPolicySurvivesButLiveValuesDoNot", driver)
        self.assertIn("seedNmeaStateBeforeExternalProcessRestart", probe)
        self.assertIn("verifyPolicySurvivesButLiveValuesDoNot", probe)

    def test_service_loss_and_configuration_corruption_have_behavioral_evidence(self):
        lifecycle = self.read(
            "adapter/marine-data-android/src/test/kotlin/com/yokuli/marine/data/android/NmeaRuntimeLifecycleTest.kt"
        )
        persistence = self.read(
            "adapter/marine-data-android/src/test/kotlin/com/yokuli/marine/data/android/ConnectionPersistenceTest.kt"
        )
        self.assertIn("foregroundServiceLossClosesSocketsWithoutChangingEnabledIntent", lifecycle)
        self.assertIn("semanticCorruptionIsQuarantinedWithoutDroppingValidConnections", persistence)
        self.assertIn("wireCorruptionIsReportedWithoutReplacingTheOriginalFile", persistence)

    def test_physical_gate_cannot_be_declared_by_emulator_or_host_soak(self):
        report = self.read("docs/phases/nmea-sources/P6_REPORT.md")
        self.assertIn("MACHINE_VERIFIED", report)
        self.assertIn("UNVERIFIED_PHYSICAL_DEVICE", report)
        self.assertNotIn("PHYSICAL_DEVICE: PASS", report)


if __name__ == "__main__":
    unittest.main()
