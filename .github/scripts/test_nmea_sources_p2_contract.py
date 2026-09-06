from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "core/marine-data"
ADAPTER = ROOT / "adapter/marine-data-android"
FEATURE = ROOT / "feature/nmea-input"


def kotlin_source(root: Path, source_set: str = "main") -> str:
    source_root = root / "src" / source_set
    if not source_root.exists():
        return ""
    return "\n".join(path.read_text(encoding="utf-8") for path in source_root.rglob("*.kt"))


class NmeaSourcesP2ContractTest(unittest.TestCase):
    def text(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_modules_and_dependency_direction_are_explicit(self):
        settings = self.text("settings.gradle.kts")
        self.assertIn('":adapter:marine-data-android"', settings)
        self.assertIn('":feature:nmea-input"', settings)

        adapter_build = self.text("adapter/marine-data-android/build.gradle.kts")
        feature_build = self.text("feature/nmea-input/build.gradle.kts")
        self.assertIn('project(":core:marine-data")', adapter_build)
        self.assertIn('project(":core:marine-data")', feature_build)
        self.assertNotIn('project(":adapter:marine-data-android")', feature_build)
        self.assertNotIn('project(":app-shell")', feature_build)
        self.assertNotIn('project(":feature:data-sources")', feature_build)

    def test_runtime_contract_keeps_intent_transport_and_input_truth_separate(self):
        source = kotlin_source(CORE)
        for symbol in (
            "NmeaConnectionConfig",
            "NmeaEndpoint",
            "ConnectionRunIntent",
            "ConnectionTransportState",
            "ConnectionInputState",
            "SessionToken",
            "NmeaRuntimeSnapshot",
            "NmeaInputRuntimePort",
            "NmeaRuntimeCommand",
            "NmeaRuntimeFailure",
        ):
            self.assertIn(symbol, source)
        self.assertNotRegex(source, r"\bconnected\s*:\s*Boolean")

    def test_retry_health_rate_and_ingress_are_bounded_and_typed(self):
        source = kotlin_source(CORE)
        for symbol in (
            "RetryPolicy",
            "1_000L",
            "2_000L",
            "4_000L",
            "8_000L",
            "16_000L",
            "30_000L",
            "InputHealthPolicy",
            "10_000L",
            "FiveSecondFrameRate",
            "NmeaInboundPipeline",
            "MAX_INGRESS_FRAMES_PER_CONNECTION",
            "MAX_STRUCTURED_INCIDENTS",
        ):
            self.assertIn(symbol, source)

    def test_android_adapter_owns_real_tcp_udp_persistence_and_process_runtime(self):
        source = kotlin_source(ADAPTER)
        for symbol in (
            "AndroidNmeaInputRuntime",
            "TcpNmeaClient",
            "UdpNmeaListener",
            "ProtoDataStoreConnectionRepository",
            "MarineDataRuntimeOwner",
            "NmeaInputForegroundService",
            "NetworkAvailabilityPort",
            "DatagramSocket",
            "Socket",
        ):
            self.assertIn(symbol, source)
        for forbidden in ("EventBus", "ServiceLocator", "GlobalScope"):
            self.assertNotIn(forbidden, source)

    def test_service_manifest_is_private_truthful_and_does_not_change_shell_identity(self):
        manifest = ET.parse(ADAPTER / "src/main/AndroidManifest.xml").getroot()
        ns = "{http://schemas.android.com/apk/res/android}"
        permissions = {item.attrib[ns + "name"] for item in manifest.findall("uses-permission")}
        self.assertIn("android.permission.FOREGROUND_SERVICE", permissions)
        self.assertIn("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE", permissions)
        self.assertIn("android.permission.POST_NOTIFICATIONS", permissions)
        services = {
            service.attrib[ns + "name"]: service
            for service in manifest.findall("application/service")
        }
        # P2 sealed the private NMEA service. P3 later adds a separate private
        # location service; the current executable gate must validate both truths.
        self.assertEqual(
            {".service.NmeaInputForegroundService", ".service.PhoneLocationForegroundService"},
            set(services),
        )
        self.assertEqual("false", services[".service.NmeaInputForegroundService"].attrib[ns + "exported"])
        self.assertEqual(
            "connectedDevice",
            services[".service.NmeaInputForegroundService"].attrib[ns + "foregroundServiceType"],
        )
        self.assertEqual("false", services[".service.PhoneLocationForegroundService"].attrib[ns + "exported"])
        self.assertEqual(
            "location",
            services[".service.PhoneLocationForegroundService"].attrib[ns + "foregroundServiceType"],
        )

        shell_manifest = self.text("app-shell/src/main/AndroidManifest.xml")
        self.assertIn('android:screenOrientation="portrait"', shell_manifest)
        self.assertNotIn("android.intent.category.HOME", shell_manifest)
        self.assertNotIn("android.intent.category.DEFAULT", shell_manifest)
        self.assertNotIn("ACTION_HOME_SETTINGS", shell_manifest)

    def test_feature_is_state_action_projector_workspace_without_socket_ownership(self):
        source = kotlin_source(FEATURE)
        for symbol in (
            "NmeaInputUiState",
            "NmeaInputUiAction",
            "NmeaInputProjector",
            "NmeaInputCoordinator",
            "NmeaInputWorkspace",
            "ConnectionHeadlineUi",
            "TCP_CONNECTED_WAITING_FOR_DATA",
            "UDP_LISTENING_WAITING_FOR_DATAGRAM",
            "INPUT_WITHOUT_VALID_NMEA",
            "RECEIVING_VALID_NMEA",
        ):
            self.assertIn(symbol, source)
        for forbidden in ("java.net.", "DatagramSocket", "ServerSocket", "Socket("):
            self.assertNotIn(forbidden, source)

    def test_feature_resources_are_chinese_first_and_fully_translated(self):
        default = FEATURE / "src/main/res/values/strings.xml"
        chinese = FEATURE / "src/main/res/values-zh-rCN/strings.xml"
        english = FEATURE / "src/main/res/values-en/strings.xml"
        for path in (default, chinese, english):
            self.assertTrue(path.is_file(), f"missing localized resource: {path}")

        def keys(path: Path) -> set[str]:
            return {
                item.attrib["name"]
                for item in ET.parse(path).getroot()
                if item.tag in {"string", "plurals"}
            }

        self.assertEqual(keys(default), keys(chinese))
        self.assertEqual(keys(default), keys(english))
        default_text = default.read_text(encoding="utf-8")
        english_text = english.read_text(encoding="utf-8")
        self.assertIn("NMEA 输入", default_text)
        self.assertIn("NMEA input", english_text)

    def test_named_behavior_tests_cover_real_loopback_lifecycle_and_ui_truth(self):
        tests = "\n".join(
            kotlin_source(module, "test") + "\n" + kotlin_source(module, "androidTest")
            for module in (CORE, ADAPTER, FEATURE, ROOT / "app-shell")
        )
        for test_class in (
            "ConnectionConfigReducerTest",
            "NmeaInboundPipelineTest",
            "RetryPolicyTest",
            "ConnectionPersistenceTest",
            "TcpNmeaClientIntegrationTest",
            "UdpNmeaListenerIntegrationTest",
            "NmeaRuntimeLifecycleTest",
            "NmeaInputProjectionTest",
            "NmeaInputCoordinatorTest",
            "NmeaInputWorkspaceStoryTest",
        ):
            self.assertIn(test_class, tests)
        for scenario in (
            "tcpHandshakeWithoutDataIsNotReceiving",
            "udpBindWithoutDatagramIsListeningNotConnected",
            "windOnlyValidInputIsReceiving",
            "badChecksumInputNeverBecomesReceiving",
            "repeatedStartIsIdempotent",
            "stopCancelsPendingReconnect",
            "staleSessionCallbackCannotMutateReplacement",
            "leavingWorkspaceDoesNotStopRuntime",
            "networkRestoreDoesNotDuplicateSocket",
            "savingAndStartingUsesRealLoopback",
        ):
            self.assertIn(scenario, tests)

    def test_p2_deferral_is_preserved_while_p5_installs_without_default_pinning(self):
        report = self.text("docs/phases/nmea-sources/P2_REPORT.md")
        graph = self.text("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        self.assertIn("正式安装属于 P5", report)
        self.assertIn("NmeaInputShellContribution", graph)
        self.assertIn("DataSourcesShellContribution", graph)
        default_document = graph[graph.index("val defaultStartDocument"):]
        self.assertNotIn("tile-nmea", default_document)
        self.assertNotIn("tile-data-sources", default_document)
        self.assertIn('":feature:data-sources"', self.text("settings.gradle.kts"))


if __name__ == "__main__":
    unittest.main()
