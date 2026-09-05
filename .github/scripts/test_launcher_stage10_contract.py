import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT / "docs/stages/stage-10"
STARTING_SHA = "abca537bea55ac67c33a3383adfadcee6345c45a"


class LauncherStage10DurabilityContractTest(unittest.TestCase):
    def test_stage_baseline_is_locked_to_stage_nine_commit(self):
        lock = json.loads((STAGE / "BASELINE_LOCK.json").read_text())
        self.assertEqual(10, lock["stage"])
        self.assertEqual(STARTING_SHA, lock["startingSha"])
        self.assertEqual("Navigation / Motion / Immersive / Virtual Keys", lock["requiredCompletedStage"])
        self.assertFalse(lock["nextStageStarted"])

    def test_one_proto_datastore_owns_the_durable_launcher_snapshot(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        catalog = (ROOT / "gradle/libs.versions.toml").read_text()
        module = (ROOT / "adapter/shell-storage/build.gradle.kts").read_text()
        proto = (ROOT / "adapter/shell-storage/src/main/proto/launcher_state.proto").read_text()
        store = "\n".join(
            path.read_text() for path in (ROOT / "adapter/shell-storage/src/main").rglob("*.kt")
        )
        self.assertIn('":adapter:shell-storage"', settings)
        self.assertIn("protobuf", catalog + module)
        self.assertIn("androidx.datastore", catalog + module)
        self.assertIn("message LauncherStateProto", proto)
        for field in (
            "start_document",
            "theme_mode",
            "accent",
            "language_tag",
            "layout_locked",
            "last_launcher_page",
            "last_foreground_token",
            "recovery",
        ):
            self.assertIn(field, proto)
        self.assertIn("DataStore<LauncherStateProto>", store)
        self.assertIn("ReplaceFileCorruptionHandler", store)

    def test_persistence_contract_supports_load_save_reset_and_migration(self):
        persistence = (
            ROOT / "core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt"
        ).read_text()
        for symbol in (
            "data class LauncherPersistedState",
            "suspend fun load()",
            "suspend fun save(state: LauncherPersistedState)",
            "suspend fun reset()",
            "object LauncherPersistedStateMigration",
            "val incidents: Flow<LauncherPersistenceIncident>",
            "CORRUPT_DATA_REPLACED",
        ):
            self.assertIn(symbol, persistence)

    def test_safe_mode_is_policy_driven_and_stays_inside_yokuli(self):
        engine = "\n".join(
            path.read_text() for path in (ROOT / "core/shell-engine/src/main").rglob("*.kt")
        )
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        recovery = (
            ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherRecoverySurface.kt"
        ).read_text()
        self.assertIn("object LauncherRecoveryPolicy", engine)
        self.assertIn("LauncherRecoveryMode.SAFE_MODE", engine)
        self.assertNotIn("LauncherEffect.OpenAndroidSettings", engine + activity)
        self.assertNotIn("Settings.ACTION_SETTINGS", activity)
        self.assertNotIn("Settings.ACTION_HOME_SETTINGS", activity)
        self.assertNotIn("recovery-open-android-settings", recovery)
        self.assertIn("recovery-open-chart", recovery)
        self.assertIn("recovery-open-settings", recovery)
        self.assertIn("recovery-reset-start", recovery)

    def test_process_restore_corruption_and_crash_loop_are_covered(self):
        tests = "\n".join(path.read_text() for path in ROOT.rglob("*Test.kt"))
        stories = (
            ROOT / "app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt"
        ).read_text()
        for scenario in (
            "aFreshDataStoreRestoresTheCommittedSnapshot",
            "corruptProtoFallsBackWithoutCrashing",
            "thirdConsecutiveStartupAttemptEntersSafeMode",
            "futureSchemaFallsBackDeterministically",
            "legacySchemaIsMigratedRecordedAndCommitted",
            "atomicUpdatesPreserveTheEntireLauncherSnapshot",
            "persistenceMigrationIncidentsAreRecordedByTheSerializedEngine",
            "safeModeUsesDefaultDocumentWithoutSilentlyOverwritingTheSavedLayout",
        ):
            self.assertIn(scenario, tests)
        for scenario in (
            "activityRecreationRetainsTheEngineDocument",
            "recoverySurfaceStaysInsideYokuliShell",
        ):
            self.assertIn(scenario, stories)

    def test_theme_accent_language_and_reset_are_not_compose_local_truth(self):
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        view_model = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellViewModel.kt").read_text()
        self.assertNotIn("var themeModeName by rememberSaveable", activity)
        self.assertNotIn("var accentName by rememberSaveable", activity)
        self.assertIn("persistedPreferences", view_model)
        self.assertIn("savePreferences", view_model)
        self.assertIn("resetLauncher", view_model)

    def test_named_ci_gate_and_bilingual_recovery_copy_exist(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("launcher_stage10_contract", workflow)
        self.assertIn("python3 .github/scripts/test_launcher_stage10_contract.py", workflow)
        for locale in ("values", "values-en", "values-zh-rCN"):
            strings = (ROOT / f"feature/desktop/src/main/res/{locale}/strings.xml").read_text()
            for name in ("recovery_title", "recovery_explanation", "recovery_reset"):
                self.assertIn(f'name="{name}"', strings)
            self.assertNotIn('name="recovery_android_settings"', strings)

    def test_report_records_scope_platform_boundary_and_complete_gate(self):
        report = (STAGE / "REPORT.md").read_text()
        self.assertIn("Stage 10 — Durable Storage & Recovery", report)
        self.assertIn("PENDING_HUMAN_REVIEW", report)
        self.assertIn("Proto DataStore", report)
        self.assertIn("Android physical HOME", report)
        self.assertIn("Stage 11 尚未开始", report)


if __name__ == "__main__":
    unittest.main()
