#!/usr/bin/env python3
"""CL00 executable baseline and user-override contract."""

import hashlib
import json
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
PHASE = ROOT / "docs/phases/chart-library"


class ChartLibraryCl00ContractTest(unittest.TestCase):
    def test_document_pack_is_present_and_unchanged(self) -> None:
        state = json.loads((PHASE / "EXECUTION_STATE.json").read_text())
        digest = hashlib.sha256((PHASE / "REQUIREMENTS.md").read_bytes()).hexdigest()
        self.assertEqual(state["requirements_sha256"], digest)
        for task in range(13):
            self.assertTrue((PHASE / f"tasks/CL{task:02}.md").is_file())

    def test_actual_baseline_is_locked_without_rewinding(self) -> None:
        lock = json.loads((PHASE / "CL00_BASELINE_LOCK.json").read_text())
        self.assertEqual(lock["branch"], "codex/shell-map-contract")
        self.assertEqual(lock["actual_start_head"], "8d2376bd76c38bd5d6e559d08b00a59d7261c837")
        self.assertEqual(lock["build_flavors"], ["standalone"])
        self.assertEqual(lock["production_apps_before_chart_library"], [
            "chart", "settings", "nmea_input", "data_sources",
        ])
        self.assertFalse(lock["android_home_registered"])

    def test_direct_user_requirements_override_conflicting_pack_lines(self) -> None:
        override = (PHASE / "USER_OVERRIDES.md").read_text()
        for required in (
            "GOOGLE_MAPS_ANDROID_API_KEY",
            "Google 在线底图",
            "本地 MBTiles",
            "左右边缘避让",
            "不得整体下移",
            "完整 SHA",
            "逐瓦片解码",
        ):
            self.assertIn(required, override)
        self.assertIn("direct user request", override)

    def test_current_product_contract_keeps_completed_apps(self) -> None:
        graph = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt").read_text()
        for contribution in (
            "ChartShellContribution",
            "SettingsShellContribution",
            "DataShellContribution",
        ):
            self.assertIn(contribution, graph)
        manifest = (ROOT / "app-shell/src/main/AndroidManifest.xml").read_text()
        self.assertNotIn("android.intent.category.HOME", manifest)


if __name__ == "__main__":
    unittest.main()
