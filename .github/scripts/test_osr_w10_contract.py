#!/usr/bin/env python3
from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW10ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_navigation_domain_is_ui_and_android_free(self):
        settings = self.read("settings.gradle.kts")
        build = self.read("core/navigation-domain/build.gradle.kts")
        source = "\n".join(path.read_text(encoding="utf-8") for path in (ROOT / "core/navigation-domain/src/main").rglob("*.kt"))
        self.assertIn('":core:navigation-domain"', settings)
        self.assertIn("kotlin.jvm", build)
        for forbidden in ("com.android", "androidx.", "android.", "compose", "feature.chart"):
            self.assertNotIn(forbidden, source + build)

    def test_domain_contains_owned_assets_math_and_revision_checked_changes(self):
        model = self.read("core/navigation-domain/src/main/kotlin/com/yokuli/marine/navigation/domain/NavigationLibrary.kt")
        port = self.read("core/navigation-domain/src/main/kotlin/com/yokuli/marine/navigation/domain/NavigationLibraryPort.kt")
        math = self.read("core/navigation-domain/src/main/kotlin/com/yokuli/marine/navigation/domain/NavigationRouteMath.kt")
        for token in ("data class Waypoint", "data class RouteDraft", "data class RoutePlan", "data class NavigationTrack", "data class NavigationLibrary", "data class OfflineCoveragePlan"):
            self.assertIn(token, model)
        for token in ("expectedLibraryRevision", "ITEM_CONFLICT", "DUPLICATE_IMPORT", "NavigationLibraryEditor"):
            self.assertIn(token, port)
        self.assertIn("data class RouteLeg", math)
        self.assertIn("data class PassageSummary", math)
        self.assertIn("Geodesic.WGS84.Inverse", math)

    def test_existing_room_library_remains_the_navigation_asset_store_after_forward_migrations(self):
        persistence = self.read("adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/RoomMapPersistence.kt")
        mapper = self.read("adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/NavigationLegacyMapper.kt")
        database = self.read("adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/MapLibraryDatabase.kt")
        baseline = json.loads(self.read("docs/phases/os-redesign/W10_BASELINE_LOCK.json"))
        self.assertIn("MapPersistencePort, NavigationLibraryPort", persistence)
        self.assertIn("libraryMutex.withLock", persistence)
        self.assertIn("NavigationLegacyMapper.toNavigation", persistence)
        self.assertIn("NavigationLegacyMapper.toLegacy", persistence)
        self.assertIn("MapLibrarySnapshot", mapper)
        current_version = int(re.search(r"version\s*=\s*(\d+)", database).group(1))
        self.assertGreaterEqual(current_version, baseline["databaseVersion"])
        for version in range(baseline["databaseVersion"], current_version):
            self.assertIn(f"MIGRATION_{version}_{version + 1}", database)
        self.assertFalse((ROOT / "adapter/navigation-storage").exists())

    def test_crud_gpx_legacy_and_room_stories_are_real_tests(self):
        editor = self.read("core/navigation-domain/src/test/kotlin/com/yokuli/marine/navigation/domain/NavigationLibraryEditorTest.kt")
        route = self.read("core/navigation-domain/src/test/kotlin/com/yokuli/marine/navigation/domain/NavigationRouteMathTest.kt")
        mapper = self.read("adapter/map-storage/src/test/java/com/yokuli/marine/map/storage/NavigationLegacyMapperTest.kt")
        room = self.read("adapter/map-storage/src/androidTest/java/com/yokuli/marine/map/storage/RoomMapPersistenceTest.kt")
        for story in (
            "crud requires exact entity revisions and advances library once",
            "GPX import is atomic and duplicate digest cannot partially append",
        ):
            self.assertIn(story, editor)
        self.assertIn("passage uses WGS84 legs true bearings and optional planned speed", route)
        self.assertIn("existing GPX parser output is readable by Navigation", mapper)
        self.assertIn("navigationPortReadsAndWritesTheExistingRoomLibraryWithOptimisticRevision", room)

    def test_contract_report_and_lock_freeze_non_destructive_scope(self):
        contract = self.read("docs/phases/os-redesign/work-packages/W10_PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/os-redesign/W10_REPORT.md")
        lock = json.loads(self.read("docs/phases/os-redesign/W10_BASELINE_LOCK.json"))
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 领域关系",
            "Opportunities / 可发展空间", "Non-negotiables / 不可协商边界",
            "Forbidden outcomes / 禁止结果", "Acceptance stories", "Evidence required",
            "Existing code landmarks", "Implementation freedom / 实现自由", "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertEqual(4, lock["databaseVersion"])
        self.assertFalse(lock["navigationAppRegistered"])
        self.assertFalse(lock["legacyChartDataRemoved"])


if __name__ == "__main__":
    unittest.main()
