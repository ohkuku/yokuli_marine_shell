#!/usr/bin/env python3
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class NmeaSourcesP3Contract(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_one_selection_policy_owns_all_data_keys(self):
        source = self.read("core/marine-data/src/main/kotlin/com/yokuli/marine/data/source/SourceSelectionReducer.kt")
        self.assertIn("sealed interface SourceSelectionAction", source)
        self.assertNotIn("GpsSourceManager", "\n".join(p.read_text(encoding="utf-8", errors="ignore") for p in ROOT.rglob("*.kt")))
        self.assertNotIn("InstrumentSourceManager", "\n".join(p.read_text(encoding="utf-8", errors="ignore") for p in ROOT.rglob("*.kt")))

    def test_selection_and_resolved_data_are_one_atomic_snapshot(self):
        models = self.read("core/marine-data/src/main/kotlin/com/yokuli/marine/data/source/SourceModels.kt")
        self.assertIn("data class MarineSourceSnapshot", models)
        self.assertIn("val resolvedData: ResolvedDataSnapshot", models)
        self.assertIn("val selectionRevision: Long", models)

    def test_real_phone_adapter_and_permission_states_exist(self):
        adapter = self.read("adapter/marine-data-android/src/main/java/com/yokuli/marine/data/android/location/AndroidPhoneLocationRuntime.kt")
        platform = self.read("adapter/marine-data-android/src/main/java/com/yokuli/marine/data/android/location/AndroidLocationManagerPlatform.kt")
        self.assertIn("LocationManager", platform)
        self.assertIn("PhoneLocationPermission", adapter)
        self.assertNotIn("mock", platform.lower())

    def test_manifest_requests_foreground_location_but_never_background_location(self):
        merged_inputs = self.read("adapter/marine-data-android/src/main/AndroidManifest.xml") + self.read("app-shell/src/main/AndroidManifest.xml")
        self.assertIn("android.permission.ACCESS_COARSE_LOCATION", merged_inputs)
        self.assertIn("android.permission.ACCESS_FINE_LOCATION", merged_inputs)
        self.assertIn("android.permission.FOREGROUND_SERVICE_LOCATION", merged_inputs)
        self.assertNotIn("android.permission.ACCESS_BACKGROUND_LOCATION", merged_inputs)
        self.assertNotIn('android.intent.category.HOME', merged_inputs)

    def test_no_phone_location_is_started_by_opening_a_feature(self):
        app = self.read("app-shell/src/main/java/com/yokuli/marine/shell/ShellApplication.kt")
        self.assertIn("phoneLocationRuntime", app)
        self.assertNotIn("PhoneLocationCommand.Enable", app)

    def test_p3_named_behavior_scenarios_are_real_tests(self):
        tests = "\n".join(p.read_text(encoding="utf-8") for p in [
            ROOT / "core/marine-data/src/test/kotlin/com/yokuli/marine/data/source/SourceSelectionReducerTest.kt",
            ROOT / "core/marine-data/src/test/kotlin/com/yokuli/marine/data/source/SourceSelectionRuntimeTest.kt",
            ROOT / "adapter/marine-data-android/src/test/kotlin/com/yokuli/marine/data/android/PhoneLocationRuntimeTest.kt",
        ])
        for name in [
            "uniqueCandidateIsOnlyAdoptedAfterTheThreeSecondDiscoveryWindow",
            "firstMultipleCandidatesNeverUseArrivalOrderAsSelection",
            "selectedSourceStalesWithoutSilentlyFailingOverAndRecoversInPlace",
            "persistenceFailureKeepsTheOldSelectionAndResolvedValueAtomically",
            "restartRestoresPreferenceButNeverResurrectsLiveValue",
            "enableWithDeniedPermissionRequestsPermissionWithoutStartingPlatform",
        ]:
            self.assertIn(name, tests)


if __name__ == "__main__":
    unittest.main()
