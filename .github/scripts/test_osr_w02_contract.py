from pathlib import Path
import json
import unittest


ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW02ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_live_foundation_is_presentation_only_and_has_explicit_cadences(self):
        live = self.read("core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt")
        for symbol in (
            "PresentationCadence",
            "CadencedPresentation",
            "WpLiveField",
            "WpLiveConsole",
            "LatestWinsBatchBuffer",
            "STRUCTURAL,",
            "SAFETY,",
        ):
            self.assertIn(symbol, live)
        for period in ("200L", "250L", "1_000L"):
            self.assertIn(period, live)
        self.assertNotIn("marine.data", live)
        self.assertNotIn("map.domain", live)

    def test_clock_injected_acceptance_tests_cover_rate_safety_stale_and_bounds(self):
        tests = self.read("core/design/src/test/java/com/yokuli/marine/core/design/PresentationCadenceTest.kt")
        for story in (
            "twentyHertzInputHasAtMostFourVisibleLiveUpdatesPerSecond",
            "disconnectAndOtherSafetyTruthBypassCadenceImmediately",
            "staleBoundaryIsAnImmediateDefinedStructuralTransition",
            "rawPreviewIsBoundedAndLatestSnapshotWins",
        ):
            self.assertIn(story, tests)

    def test_existing_live_surfaces_use_shared_primitives_without_raw_frame_entrance(self):
        nmea = self.read("feature/nmea-input/src/main/java/com/yokuli/marine/feature/nmeainput/NmeaInputWorkspace.kt")
        data = self.read("feature/data-sources/src/main/java/com/yokuli/marine/feature/datasources/DataSourcesWorkspace.kt")
        chart = self.read("feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt")
        self.assertGreaterEqual(nmea.count("WpLiveField("), 2)
        self.assertIn("WpLiveConsole(", nmea)
        self.assertGreaterEqual(data.count("WpLiveField("), 3)
        self.assertIn("WpLiveConsole(", data)
        self.assertGreaterEqual(chart.count("WpLiveField("), 3)
        self.assertNotIn("wpEntrance(entry.receivedAtMillis", nmea)
        self.assertIn('testTag("map-position-coordinate")', chart)
        self.assertIn('testTag("map-position-motion")', chart)

    def test_chart_live_labels_have_complete_chinese_and_english_resources(self):
        names = (
            "map_position_coordinate",
            "map_position_true_heading_value",
            "map_position_course_speed_value",
        )
        for resource in (
            "feature/chart/src/main/res/values/strings.xml",
            "feature/chart/src/main/res/values-en/strings.xml",
            "feature/chart/src/main/res/values-zh-rCN/strings.xml",
        ):
            text = self.read(resource)
            for name in names:
                self.assertIn(f'name="{name}"', text)

    def test_contract_baseline_and_report_preserve_solution_space(self):
        contract = self.read("docs/phases/os-redesign/work-packages/W02_PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/os-redesign/W02_REPORT.md")
        baseline = json.loads(self.read("docs/phases/os-redesign/W02_BASELINE_LOCK.json"))
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
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertTrue(baseline["presentationOnly"])
        self.assertFalse(baseline["runtimeOwnershipChanged"])
        self.assertEqual("GITHUB_ACTION_PENDING", baseline["verification"])


if __name__ == "__main__":
    unittest.main()
