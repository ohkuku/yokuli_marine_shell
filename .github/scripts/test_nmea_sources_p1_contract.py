from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "core/marine-data"


class NmeaSourcesP1ContractTest(unittest.TestCase):
    def text(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def module_source(self) -> str:
        return "\n".join(
            path.read_text(encoding="utf-8")
            for path in (MODULE / "src/main/kotlin").rglob("*.kt")
        )

    def test_pure_module_is_registered_without_android_dependencies(self):
        settings = self.text("settings.gradle.kts")
        build = self.text("core/marine-data/build.gradle.kts")
        self.assertIn('":core:marine-data"', settings)
        self.assertIn("libs.plugins.kotlin.jvm", build)
        for forbidden in ("com.android", "androidx", "compose", "project(\":feature"):
            self.assertNotIn(forbidden, build.lower())

    def test_identity_and_observation_contracts_are_typed(self):
        source = self.module_source()
        for symbol in (
            "ConnectionId",
            "SessionGeneration",
            "SourceIdentity",
            "ObservationOrigin",
            "DataKey",
            "MarineObservation",
            "ObservationValidity",
            "MonotonicClock",
        ):
            self.assertIn(symbol, source)
        self.assertNotIn("System.currentTimeMillis()", source)
        self.assertNotIn("System.nanoTime()", source)

    def test_protocol_is_typed_strict_and_bounded(self):
        source = self.module_source()
        for symbol in (
            "NmeaChecksum",
            "TcpNmeaFramer",
            "UdpNmeaDatagramFramer",
            "Nmea0183Parser",
            "ChecksumFailure",
            "Unsupported",
            "Malformed",
            "ExplicitInvalid",
            "MAX_FRAME_BYTES",
        ):
            self.assertIn(symbol, source)
        for sentence in (
            "RMC",
            "GGA",
            "GLL",
            "VTG",
            "ZDA",
            "HDG",
            "HDM",
            "HDT",
            "DPT",
            "DBT",
            "MWD",
            "MWV",
        ):
            self.assertIn(f'"{sentence}"', source)

    def test_catalog_freshness_selection_and_bounds_exist(self):
        source = self.module_source()
        for symbol in (
            "SentenceCatalog",
            "ObservationCatalog",
            "FreshnessPolicy",
            "Freshness.LIVE",
            "Freshness.HELD",
            "Freshness.STALE",
            "Freshness.INVALID",
            "SourceSelectionReducer",
            "SelectionPersistence",
            "ResolvedDataSnapshot",
            "needsReview",
            "MAX_SENTENCE_KEYS",
            "MAX_RAW_ENTRIES",
            "MAX_RAW_BYTES",
        ):
            self.assertIn(symbol, source)

    def test_p1_has_behavioral_tests_for_every_boundary(self):
        tests = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (MODULE / "src/test/kotlin").rglob("*.kt")
        )
        for scenario in (
            "strictChecksumRejectsMissingAndBadChecksums",
            "tcpFramingHandlesHalfPacketsGlueAndDisconnectFragments",
            "udpNeverCombinesFragmentsAcrossDatagramsOrSenders",
            "allRequiredSentenceTypesProduceTypedResults",
            "blankFieldsDoNotRefreshObservations",
            "unknownLegalSentenceRemainsVisible",
            "freshnessAgesWithoutNewPackets",
            "sameOriginRmcAndGgaAreNotSeparateDevices",
            "deterministicSentencePriorityBeatsLastWriter",
            "firstMultipleCandidatesRequireSelection",
            "existingSelectionNeverSilentlyFailsOver",
            "persistenceFailureKeepsPreviousResolvedSnapshot",
            "catalogAndRawPreviewRemainBounded",
        ):
            self.assertIn(scenario, tests)

    def test_no_global_bus_or_platform_leak_enters_pure_core(self):
        source = self.module_source()
        for forbidden in (
            "EventBus",
            "ServiceLocator",
            "android.",
            "androidx.",
            "java.net.",
            "java.io.",
        ):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
