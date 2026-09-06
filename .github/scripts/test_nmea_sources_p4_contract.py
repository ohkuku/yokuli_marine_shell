#!/usr/bin/env python3
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class NmeaSourcesP4Contract(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_data_sources_is_independent_from_nmea_feature_and_adapter(self):
        build = self.read("feature/data-sources/build.gradle.kts")
        self.assertIn('project(":core:marine-data")', build)
        self.assertNotIn('project(":feature:nmea-input")', build)
        self.assertNotIn('project(":adapter:marine-data-android")', build)

    def test_data_and_sentence_views_share_one_projector(self):
        contract = self.read("feature/data-sources/src/main/java/com/yokuli/marine/feature/datasources/DataSourcesUiContract.kt")
        projector = self.read("feature/data-sources/src/main/java/com/yokuli/marine/feature/datasources/DataSourcesProjector.kt")
        self.assertIn("enum class DataSourcesViewMode", contract)
        self.assertIn("sentenceRows", contract)
        self.assertIn("sourceCatalog", projector)

    def test_selection_uses_os_port_not_feature_local_preferences(self):
        coordinator = self.read("feature/data-sources/src/main/java/com/yokuli/marine/feature/datasources/DataSourcesCoordinator.kt")
        self.assertIn("MarineSourceRuntimePort", coordinator)
        self.assertIn("SourceSelectionCommand.Select", coordinator)
        self.assertNotIn("SharedPreferences", coordinator)

    def test_phone_location_is_explicit_and_permission_is_an_effect(self):
        contract = self.read("feature/data-sources/src/main/java/com/yokuli/marine/feature/datasources/DataSourcesUiContract.kt")
        self.assertIn("EnablePhoneLocation", contract)
        self.assertIn("RequestPhoneLocationPermission", contract)

    def test_cross_feature_navigation_uses_bounded_opaque_tokens(self):
        links = self.read("core/marine-data/src/main/kotlin/com/yokuli/marine/data/source/MarineFeatureLinks.kt")
        self.assertIn("MarineFeatureLinkToken", links)
        self.assertIn("MAX_LINK_PAYLOAD_BYTES", links)
        self.assertNotIn("host", links.lower())

    def test_feature_manifest_declares_no_activity_or_permissions(self):
        manifest = self.read("feature/data-sources/src/main/AndroidManifest.xml")
        self.assertNotIn("<activity", manifest)
        self.assertNotIn("uses-permission", manifest)

    def test_p4_named_scenarios_exist(self):
        tests = "\n".join(p.read_text(encoding="utf-8") for p in [
            ROOT / "feature/data-sources/src/test/kotlin/com/yokuli/marine/feature/datasources/DataSourcesProjectorTest.kt",
            ROOT / "feature/data-sources/src/test/kotlin/com/yokuli/marine/feature/datasources/DataSourcesCoordinatorTest.kt",
        ])
        for name in [
            "windAndDepthOnlyInputProducesACompleteDataCatalogWithoutPosition",
            "unselectedCandidatesRemainVisibleAndMultipleSourceRowNeedsSelection",
            "unknownLegalSentenceAndBoundedRawEvidenceRemainVisibleInSentenceView",
            "failedSelectionNeverShowsSuccessOrMutatesAParallelLocalSelection",
            "deniedPhoneEnableProducesOnePermissionEffectAndDoesNotAffectNmea",
        ]:
            self.assertIn(name, tests)


if __name__ == "__main__":
    unittest.main()
