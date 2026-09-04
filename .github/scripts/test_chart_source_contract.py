from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
REQUIREMENTS = ROOT / "docs/requirements/CHART_SOURCE_IMPORT_REQUIREMENTS.md"


class ChartSourceContractTest(unittest.TestCase):
    def requirements_text(self) -> str:
        self.assertTrue(REQUIREMENTS.is_file(), "missing chart source/import requirements")
        return REQUIREMENTS.read_text()

    def test_phase_one_has_one_runtime_credential_without_environment_split(self):
        text = self.requirements_text()
        inventory = re.search(
            r"<!-- phase1-runtime-credentials:start -->(.*?)"
            r"<!-- phase1-runtime-credentials:end -->",
            text,
            re.DOTALL,
        )
        self.assertIsNotNone(inventory, "missing machine-checkable runtime credential inventory")
        names = re.findall(r"`([A-Z][A-Z0-9_]+)`", inventory.group(1))
        self.assertEqual(["GOOGLE_MAPS_ANDROID_API_KEY"], names)
        self.assertIn("不按 dev/prod 拆分", text)
        self.assertNotIn("LINZ_API_KEY", text)
        self.assertNotIn("LINZ_HYDRO_TILE_TEMPLATE", text)

    def test_phase_one_sources_keep_default_and_imported_charts_keyless(self):
        text = self.requirements_text()
        self.assertIn("Google Maps Android SDK", text)
        self.assertIn("OpenSeaMap seamark overlay", text)
        self.assertIn("OpenSeaMap 不需要 API key", text)
        self.assertIn("本地导入不需要供应商 API key", text)
        self.assertIn("LINZ 不在本版本范围内", text)

    def test_import_format_scope_is_explicit_and_does_not_overpromise_opencpn(self):
        text = self.requirements_text()
        expected = {
            "Raster MBTiles": "MVP 支持",
            "BSB/KAP": "后续切片",
            "S-57 ENC": "未来能力",
            "S-63 ENC": "本版本不支持",
            "oeSENC / oeRNC / CM93": "本版本不支持",
        }
        for chart_format, status in expected.items():
            with self.subTest(chart_format=chart_format):
                self.assertRegex(
                    text,
                    rf"\|\s*{re.escape(chart_format)}\s*\|[^\n]*\|\s*{re.escape(status)}\s*\|",
                )
        self.assertIn("不能替代官方海图", text)


if __name__ == "__main__":
    unittest.main()
