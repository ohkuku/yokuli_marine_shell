import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-3"
ENGINE = ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine"
REFERENCE_HASH = "af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5"
STARTING_SHA = "53a239cd735a5db9c9727b047e67d605f46045a4"


class LauncherStage3GeometryContractTest(unittest.TestCase):
    def test_stage_baseline_is_locked_to_approved_reference_and_foundation(self):
        lock = json.loads((STAGE / "BASELINE_LOCK.json").read_text())
        self.assertEqual(3, lock["stage"])
        self.assertEqual(STARTING_SHA, lock["startingSha"])
        self.assertEqual("launcher-engine-stage2.5-approved-v1", lock["approvedStage25Tag"])
        self.assertEqual(REFERENCE_HASH, lock["reviewedMeasurementHash"])
        self.assertEqual("HUMAN_REVIEWED", lock["referenceStatus"])
        self.assertEqual("PENDING_HUMAN_REVIEW", lock["approvalStatus"])

    def test_reference_profile_is_revisioned_and_hash_bound(self):
        source = (ENGINE / "geometry/WpReferenceProfile.kt").read_text()
        for symbol in (
            "data class WpReferenceProfile",
            "value class ProfileId",
            "PHONE_PORTRAIT_4COL",
            "SQUARE_4COL",
            REFERENCE_HASH,
            "referenceRevision = 1",
            "referenceWidthPx = 480",
            "outerInsetPx = 24",
            "seamPx = 12",
            "smallCellPx = 99",
            "mediumTilePx = 210",
            "wideTileWidthPx = 432",
        ):
            self.assertIn(symbol, source)

    def test_geometry_has_explicit_viewport_output_and_integer_bounds(self):
        source = (ENGINE / "geometry/WpStartGeometry.kt").read_text()
        tests = (ROOT / "core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/WpStartGeometryTest.kt").read_text()
        for symbol in (
            "data class StartViewport",
            "data class ResolvedStartGeometry",
            "data class IntInsets",
            "data class IntRect",
            "contentBounds",
            "statusStripHeightPx",
        ):
            self.assertIn(symbol, source)
        for width in ("320", "360", "480"):
            self.assertIn(width, tests)
        self.assertIn("fontScaleDoesNotChangeTileGeometry", tests)
        self.assertNotIn("OuterRatio = 0.05f", source)
        self.assertNotIn("SeamRatio = 0.023f", source)

    def test_start_document_is_spatial_versioned_and_repairable(self):
        sources = "\n".join(path.read_text() for path in (ENGINE / "layout").glob("*.kt"))
        tests = (ROOT / "core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/StartDocumentTest.kt").read_text()
        for symbol in (
            "data class StartDocument",
            "schemaVersion:",
            "profileId:",
            "defaultLayoutVersion:",
            "data class TilePlacement",
            "data class GridCell",
            "object StartDocumentValidator",
            "object StartDocumentRepair",
        ):
            self.assertIn(symbol, sources)
        self.assertNotIn("data class DesktopDocument", sources)
        self.assertIn("intentionalWhitespaceIsAValidPartOfTheDocument", tests)
        self.assertIn("placementOrderDoesNotDefinePosition", tests)
        self.assertIn("repairDropsUnknownAndDuplicateEntriesDeterministically", tests)

    def test_default_start_is_exactly_chart_wide_and_settings_small(self):
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        self.assertIn("val defaultStartDocument = StartDocument(", graph)
        self.assertIn("profileId = WpReferenceProfiles.PHONE_PORTRAIT_4COL.id", graph)
        self.assertEqual(2, graph.count("TilePlacement("))
        self.assertIn("entryId = ChartDestinations.EntryId", graph)
        self.assertIn("size = MarineTileSize.WIDE_4X2", graph)
        self.assertIn("entryId = SettingsDestinations.EntryId", graph)
        self.assertIn("size = MarineTileSize.ICON_1X1", graph)

    def test_stage_report_and_named_ci_gate_exist(self):
        report = (STAGE / "REPORT.md").read_text()
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("Stage 3 — WP Geometry & Start Document", report)
        self.assertIn("PENDING_HUMAN_REVIEW", report)
        self.assertIn("HUMAN_REVIEWED", report)
        self.assertIn(REFERENCE_HASH, report)
        self.assertIn("launcher_stage3_contract", workflow)
        self.assertIn("python3 .github/scripts/test_launcher_stage3_contract.py", workflow)


if __name__ == "__main__":
    unittest.main()
