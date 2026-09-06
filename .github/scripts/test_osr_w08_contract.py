#!/usr/bin/env python3
from pathlib import Path
import json
import unittest


ROOT = Path(__file__).resolve().parents[2]


class OsRedesignW08ContractTest(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_explicit_connected_views_use_google_while_marine_uses_the_local_plan(self):
        surface = self.read("adapter/chart-google/src/main/java/com/yokuli/marine/adapter/chart/google/GoogleMarineChartSurface.kt")
        graph = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt")
        self.assertIn("GoogleMap.MAP_TYPE_SATELLITE", surface)
        self.assertIn("GoogleMap.MAP_TYPE_NORMAL", surface)
        self.assertIn("chartSurfaceKind(state.mapViewMode, BuildConfig.GOOGLE_MAPS_CONFIGURED)", graph)
        self.assertIn("BuildConfig.GOOGLE_MAPS_CONFIGURED", graph)
        self.assertNotIn("ChartDisplaySelection.None &&", graph)
        self.assertIn("chartLibraryAccess = runtime.chartLibraryAccess", graph)

    def test_native_overlays_preserve_plan_controls_and_revision_cleanup(self):
        surface = self.read("adapter/chart-google/src/main/java/com/yokuli/marine/adapter/chart/google/GoogleMarineChartSurface.kt")
        session = self.read("adapter/chart-google/src/main/java/com/yokuli/marine/adapter/chart/google/GoogleChartOverlaySession.kt")
        for token in ("TileOverlayOptions", "tileProvider", "transparency", "zIndex", "clearTileCache", "remove()"):
            self.assertIn(token, surface)
        for token in ("plan.fingerprint", "layer.tileScheme", "@Synchronized", "session.close()"):
            self.assertIn(token, session)
        self.assertNotIn("googleMap?.apply {\n            clear()", surface)

    def test_google_content_is_not_scraped_or_cached_outside_the_sdk(self):
        source = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (ROOT / "adapter/chart-google/src/main").rglob("*.kt")
        )
        for forbidden in ("googleapis.com/maps/vt", "mt.google.com", "UrlTileProvider", "GoogleTileCache"):
            self.assertNotIn(forbidden, source)

    def test_renderer_keeps_base_and_local_overlay_truth_independent(self):
        contract = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapRendererContract.kt")
        reducer = self.read("core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapReducer.kt")
        tests = self.read("core/map-domain/src/test/kotlin/com/yokuli/marine/map/domain/MapRendererContentStateTest.kt")
        for token in ("BASE_UNAVAILABLE", "BASE_LOADING", "BASE_READY", "OVERLAY_READY", "OVERLAY_DEGRADED"):
            self.assertIn(token, contract)
        self.assertIn("RendererContentChanged", reducer)
        self.assertIn("base and overlays advance independently", tests)
        self.assertIn("stale renderer callbacks are ignored", tests)

    def test_acceptance_tests_and_product_contract_are_present(self):
        tests = self.read("adapter/chart-google/src/test/java/com/yokuli/marine/adapter/chart/google/GoogleChartOverlaySessionTest.kt")
        for story in (
            "prepared overlays retain order opacity and one revision-pinned session per layer",
            "tile reads preserve scheme reject invalid coordinates and become unavailable after close",
            "one rejected source degrades the set without discarding readable overlays",
        ):
            self.assertIn(story, tests)
        contract = self.read("docs/phases/os-redesign/work-packages/W08_PRODUCT_ENGINEERING_CONTRACT.md")
        report = self.read("docs/phases/os-redesign/W08_REPORT.md")
        lock = json.loads(self.read("docs/phases/os-redesign/W08_BASELINE_LOCK.json"))
        for section in (
            "Intent / 产品愿景", "Experience / 用户体验", "Domain / 领域边界",
            "Opportunities / 可发展空间", "Non-negotiables / 不可协商边界",
            "Forbidden outcomes / 禁止结果", "Acceptance stories", "Evidence required",
            "Existing code landmarks", "Implementation freedom / 实现自由", "English translation",
        ):
            self.assertIn(section, contract)
        self.assertIn("DESIGN DECISIONS", report)
        self.assertEqual("GOOGLE_MAPS_SATELLITE_SDK_ONLY", lock["connectedBase"])
        self.assertFalse(lock["googleFillsLocalCoverage"])


if __name__ == "__main__":
    unittest.main()
