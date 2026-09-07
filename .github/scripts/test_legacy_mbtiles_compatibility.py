#!/usr/bin/env python3
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class LegacyMbTilesCompatibilityTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_stream_only_provider_has_a_real_local_render_path(self):
        access = self.read(
            "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartResourceAccess.kt"
        )
        runtime = self.read(
            "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartLibraryRuntime.kt"
        )
        tests = self.read(
            "adapter/chart-library-android/src/androidTest/java/com/yokuli/marine/chart/library/android/SafFdMbTilesReaderTest.kt"
        )
        for required in (
            "STREAM_FALLBACK_FAILURES",
            "resolver.openInputStream(uri)",
            "ChartReadAccessMode.LOCAL_FALLBACK",
            "StandardCopyOption.ATOMIC_MOVE",
            "MIN_FREE_AFTER_FALLBACK_BYTES",
            "Array(FALLBACK_LOCK_STRIPES)",
        ):
            self.assertIn(required, access)
        self.assertIn('File(context.cacheDir, "chart-library-access")', runtime)
        self.assertIn("streamOnlyProviderUsesSafeLocalAccessAndRendersWithoutChangingOriginal", tests)
        self.assertNotIn("pipeIsRejectedWithoutCreatingAnImplicitCopy", tests)

    def test_metadata_is_optional_and_zoom_is_derived_from_tiles(self):
        access = self.read(
            "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/AndroidChartResourceAccess.kt"
        )
        validation = self.read(
            "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartValidationContracts.kt"
        )
        tests = self.read(
            "core/map-domain/src/test/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartValidationTest.kt"
        )
        self.assertIn('if ("tiles" !in objects)', access)
        self.assertIn('return "metadata" in objects', access)
        self.assertIn("override fun readZoomRange()", access)
        self.assertIn("ChartCompatibilityWarning.METADATA_MISSING", validation)
        self.assertIn("metadataZoom ?: derivedZoom", validation)
        self.assertIn("legacyRasterWithoutMetadataIsBasicReadableAndDerivesZoom", tests)
        self.assertIn("fullVerificationCanVerifyLegacyRasterWithoutMetadataTable", tests)

    def test_metadata_quality_never_silently_becomes_renderability(self):
        display = self.read(
            "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartDisplayContracts.kt"
        )
        tests = self.read(
            "core/map-domain/src/test/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartDisplayPlannerTest.kt"
        )
        self.assertIn("UNKNOWN_BOUNDS_UNFILTERED", display)
        self.assertNotIn("UNKNOWN_BOUNDS_EXCLUDED", display)
        self.assertNotIn("facts.minZoom != null && facts.maxZoom != null", display)
        self.assertIn("unknownBoundsNeverTurnRenderableContentIntoAnExcludedLayer", tests)
        self.assertIn("missingMetadataZoomRangeUsesSafeRendererRangeInsteadOfBlockingDisplay", tests)

    def test_access_content_and_verification_are_separate_truths(self):
        catalog = self.read(
            "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/chartlibrary/ChartCatalogContracts.kt"
        )
        ui = self.read(
            "feature/chart-library/src/main/java/com/yokuli/marine/feature/chartlibrary/ChartLibraryWorkspace.kt"
        )
        migration = self.read(
            "adapter/chart-library-android/src/main/java/com/yokuli/marine/chart/library/android/ChartCatalogDatabase.kt"
        )
        self.assertIn("val accessMode: ChartReadAccessMode?", catalog)
        self.assertIn("val compatibilityWarnings: Set<ChartCompatibilityWarning>", catalog)
        self.assertIn("fact_access_mode", ui)
        self.assertIn("fact_warnings", ui)
        self.assertIn("CHART_CATALOG_MIGRATION_3_4", migration)


if __name__ == "__main__":
    unittest.main()
