import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
IMPLEMENTATION = ROOT / "docs/implementation"
PHASE = ROOT / "docs/phases/nmea-sources"


class NmeaSourcesP0ContractTest(unittest.TestCase):
    def text(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_hash_bound_baseline_uses_the_real_worktrees(self):
        baseline = self.text(IMPLEMENTATION / "NMEA_SOURCES_P0_BASELINE.md")
        lock = json.loads(self.text(PHASE / "P0_BASELINE_LOCK.json"))

        self.assertEqual("codex/shell-map-contract", lock["shell"]["branch"])
        self.assertEqual(
            "69bfd4d0ed29f27450351df530b4a8b1e8e2c6a6",
            lock["shell"]["startingSha"],
        )
        self.assertEqual("codex/develop", lock["reference"]["branch"])
        self.assertEqual(
            "a845d3d734d3b573a2b53952e66e5f800e944205",
            lock["reference"]["sha"],
        )
        self.assertEqual(
            "a5a38f08f8606d230952dcec8e8f521615efc9ec1a4d30ab4a3821f5943b3348",
            lock["sourceSpecSha256"],
        )
        for phrase in (
            "附件中的过期事实",
            "codex/chart-first-foundation",
            "assembleHomeDebug",
            "只读技术参考",
            "read-only technical reference",
        ):
            self.assertIn(phrase, baseline)

    def test_active_contract_preserves_latest_shell_product_boundary(self):
        requirements = self.text(PHASE / "REQUIREMENTS.md")
        for phrase in (
            "两个独立的 Shell 内部应用",
            "不是两个 APK",
            "默认仅竖屏",
            "Back 的终点是应用内 Shell 桌面",
            "不注册 Android HOME",
            "two independent in-Shell apps",
            "portrait-only",
        ):
            self.assertIn(phrase, requirements)

    def test_p0_records_real_symbols_and_a_non_migration_decision(self):
        baseline = self.text(IMPLEMENTATION / "NMEA_SOURCES_P0_BASELINE.md")
        for phrase in (
            "InstalledAppBinding",
            "ProductionShellGraph",
            "ReadOnlyPositionPort",
            "NmeaConnectionManager",
            "NmeaStreamSplitter",
            "Nmea0183Parser",
            "NmeaFieldRepository",
            "NmeaSourceInvalidation",
            "VesselSourceRegistry",
            "FakeNmeaEndpoints",
            "跨 UDP sender 拼接",
            "不迁移 Anchor",
            "不迁移 Trip",
            "不迁移 NMEA 输出",
        ):
            self.assertIn(phrase, baseline)

    def test_module_and_dependency_direction_are_explicit(self):
        baseline = self.text(IMPLEMENTATION / "NMEA_SOURCES_P0_BASELINE.md")
        matrix = self.text(IMPLEMENTATION / "NMEA_SOURCES_TDD_MATRIX.md")
        for phrase in (
            "core:marine-data",
            "adapter:marine-data-android",
            "feature:nmea-input",
            "feature:data-sources",
            "app-shell",
            "全局 EventBus",
            "StateFlow",
        ):
            self.assertIn(phrase, baseline + matrix)
        for phase in range(8):
            self.assertIn(f"P{phase}", matrix)
        for story in range(1, 27):
            self.assertIn(f"E{story:02d}", matrix)

    def test_android_platform_plan_is_truthful_for_target_36(self):
        baseline = self.text(IMPLEMENTATION / "NMEA_SOURCES_P0_BASELINE.md")
        for phrase in (
            "compileSdk 36",
            "targetSdk 36",
            "minSdk 26",
            "connectedDevice",
            "location",
            "FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "FOREGROUND_SERVICE_LOCATION",
            "ACCESS_FINE_LOCATION",
            "前台可见状态",
            "强制停止",
            "UNVERIFIED_PHYSICAL_DEVICE",
        ):
            self.assertIn(phrase, baseline)

    def test_old_evidence_is_retained_but_the_new_phase_supersedes_conflicts(self):
        requirements = self.text(PHASE / "REQUIREMENTS.md")
        readme = self.text(ROOT / "README.md")
        playbook = self.text(ROOT / "docs/TDD_PLAYBOOK.md")
        for text in (requirements, readme, playbook):
            self.assertIn("NMEA_SOURCES", text)
            self.assertIn("覆盖", text)
        self.assertIn("不改写历史", requirements)


if __name__ == "__main__":
    unittest.main()
