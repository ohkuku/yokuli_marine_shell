from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
LOCALIZED_MODULES = (
    "app-shell",
    "core/design",
    "feature/desktop",
    "feature/chart",
    "feature/settings",
    "feature/shell-lab",
)


def string_keys(path: Path) -> set[str]:
    tree = ET.parse(path)
    return {item.attrib["name"] for item in tree.getroot() if item.tag in {"string", "plurals"}}


def string_values(path: Path) -> dict[str, str]:
    tree = ET.parse(path)
    return {item.attrib["name"]: "".join(item.itertext()) for item in tree.getroot() if item.tag == "string"}


class UiArchitectureContractTest(unittest.TestCase):
    def test_chinese_default_and_english_resources_have_identical_keys(self):
        for module in LOCALIZED_MODULES:
            with self.subTest(module=module):
                default = ROOT / module / "src/main/res/values/strings.xml"
                english = ROOT / module / "src/main/res/values-en/strings.xml"
                chinese = ROOT / module / "src/main/res/values-zh-rCN/strings.xml"
                self.assertTrue(default.is_file(), f"{module} is missing Chinese default strings")
                self.assertTrue(english.is_file(), f"{module} is missing English translations")
                self.assertTrue(chinese.is_file(), f"{module} is missing explicit zh-CN resources")
                self.assertEqual(string_keys(default), string_keys(english), f"{module} translations drifted")
                self.assertEqual(string_values(default), string_values(chinese), f"{module} Chinese fallback drifted")

    def test_launcher_domain_descriptor_contains_no_visual_copy_or_glyph(self):
        model = (ROOT / "core/model/src/main/java/com/yokuli/marine/core/model/ShellModels.kt").read_text()
        descriptor = model.split("data class LauncherEntryDescriptor(", 1)[1].split(")", 1)[0]
        self.assertNotIn("title", descriptor)
        self.assertNotIn("symbol", descriptor)

    def test_each_feature_exposes_state_and_action_contract(self):
        contracts = {
            "feature/desktop": "LauncherUiContract.kt",
            "feature/chart": "ChartUiContract.kt",
            "feature/settings": "SettingsUiContract.kt",
        }
        for module, filename in contracts.items():
            matches = list((ROOT / module / "src/main/java").rglob(filename))
            self.assertEqual(1, len(matches), f"{module} must expose {filename}")
            source = matches[0].read_text()
            self.assertRegex(source, r"data class \w+UiState")
            self.assertRegex(source, r"sealed interface \w+UiAction")

    def test_wp_text_has_no_hardcoded_alphabetic_user_copy(self):
        offenders = []
        pattern = re.compile(r'WpText\(\s*"[^"\n]*[A-Za-z\u4e00-\u9fff]')
        for module in ("feature/desktop", "feature/chart", "feature/settings", "feature/shell-lab"):
            for path in (ROOT / module / "src/main/java").rglob("*.kt"):
                for number, line in enumerate(path.read_text().splitlines(), start=1):
                    if pattern.search(line):
                        offenders.append(f"{path.relative_to(ROOT)}:{number}")
        self.assertEqual([], offenders, "hard-coded WpText copy: " + ", ".join(offenders))

    def test_feature_workspaces_render_state_and_emit_actions(self):
        workspaces = {
            "feature/chart": ("ChartWorkspace.kt", "ChartUiState", "ChartUiAction"),
            "feature/settings": ("SettingsWorkspace.kt", "SettingsUiState", "SettingsUiAction"),
        }
        for module, (filename, state, action) in workspaces.items():
            path = next((ROOT / module / "src/main/java").rglob(filename))
            source = path.read_text()
            with self.subTest(module=module):
                self.assertRegex(source, rf"fun \w+Workspace\(\s*state: {state},\s*onAction: \({action}\) -> Unit")
                self.assertNotIn("initialSection:", source)
                self.assertNotIn("onHome: () -> Unit", source)

    def test_platform_locale_bootstrap_and_androidx_persistence_are_declared(self):
        properties = (ROOT / "app-shell/src/main/res/resources.properties").read_text()
        gradle = (ROOT / "app-shell/build.gradle.kts").read_text()
        manifest = (ROOT / "app-shell/src/main/AndroidManifest.xml").read_text()
        application = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellApplication.kt").read_text()
        self.assertIn("unqualifiedResLocale=zh-CN", properties)
        self.assertIn('localeFilters += listOf("zh-rCN", "en")', gradle)
        self.assertIn("autoStoreLocales", manifest)
        self.assertIn('LANGUAGE_SELECTION = "selected_language_tag"', application)
        self.assertIn("persistAppLanguage", application)
        self.assertIn('CHINESE_LANGUAGE_TAG = "zh-CN"', application)
        self.assertIn("LocaleManager", application)

    def test_phase_zero_ui_does_not_render_unimplemented_marine_runtime_values(self):
        chart = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt").read_text()
        self.assertNotRegex(chart, r"\?:\s*0(?:\.0)?")
        self.assertNotIn("vesselPosition", chart)
        self.assertNotIn("activeRoute", chart)
        self.assertIn("MarineChartDemoSurface", chart)

    def test_public_documents_are_chinese_first_with_english_translation(self):
        paths = [ROOT / "README.md", ROOT / "CONTRIBUTING.md", ROOT / "CHANGELOG.md"]
        paths += list((ROOT / "docs").rglob("*.md"))
        for path in paths:
            text = path.read_text()
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertRegex(text, r"[\u4e00-\u9fff]", "missing Chinese primary text")
                if path.name == "LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md":
                    self.assertIn("NORMATIVE / 施工主文档", text)
                else:
                    self.assertRegex(text, r"(?i)English translation|> English:", "missing English translation")


if __name__ == "__main__":
    unittest.main()
