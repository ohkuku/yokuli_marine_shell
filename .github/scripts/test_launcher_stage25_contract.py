import json
from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "docs/reference/wp8"
STAGE = ROOT / "docs/stages/stage-2.5"
APPROVED_STAGE_2_TAG = "launcher-engine-stage2-approved-v1"
APPROVED_STAGE_2_SHA = "5386da0575046f1f9a59742a4a0f5c78523fa5e6"


class LauncherStage25ReferenceContractTest(unittest.TestCase):
    def test_stage_starts_exactly_at_the_approved_stage_two_point(self):
        lock_path = STAGE / "BASELINE_LOCK.json"
        self.assertTrue(lock_path.is_file(), "missing Stage 2.5 baseline lock")
        lock = json.loads(lock_path.read_text())
        self.assertEqual("2.5", str(lock["stage"]))
        self.assertEqual(APPROVED_STAGE_2_TAG, lock["approvedStage2Tag"])
        self.assertEqual(APPROVED_STAGE_2_SHA, lock["startingSha"])
        self.assertEqual("WP8 Reference Acquisition & Human Approval", lock["scope"])
        self.assertFalse(lock["nextStageStarted"])

    def test_reference_package_contains_source_measurements_method_and_rights(self):
        for relative in (
            "WP8_REFERENCE_MEASUREMENTS.json",
            "SOURCE_MANIFEST.json",
            "MEASUREMENT_METHOD.md",
            "THIRD_PARTY_NOTICES.md",
            "../../stages/stage-2.5/FULLSCREEN_NAVIGATION_DECISION.md",
        ):
            with self.subTest(relative=relative):
                self.assertTrue((REFERENCE / relative).is_file(), f"missing {relative}")

        source = json.loads((REFERENCE / "SOURCE_MANIFEST.json").read_text())
        self.assertEqual(1, source["schemaVersion"])
        recordings = source["recordings"]
        self.assertEqual(1, len(recordings), "all visual evidence must trace to one owner-supplied recording")
        recording = recordings[0]
        self.assertEqual("EMULATOR_SCREEN_RECORDING", recording["acquisitionKind"])
        self.assertEqual("video/mp4", recording["mediaType"])
        self.assertEqual(1920, recording["dimensions"]["widthPx"])
        self.assertEqual(1080, recording["dimensions"]["heightPx"])
        self.assertEqual(60, recording["nominalFrameRateFps"])
        self.assertGreater(recording["durationMillis"], 400_000)
        self.assertNotIn("EXTERNAL_CAMERA", json.dumps(source))
        self.assertNotIn("HANDHELD", json.dumps(source))

    def test_every_visual_capture_is_an_exact_timestamped_extraction(self):
        source = json.loads((REFERENCE / "SOURCE_MANIFEST.json").read_text())
        measurements = json.loads((REFERENCE / "WP8_REFERENCE_MEASUREMENTS.json").read_text())
        extractions = {item["captureId"]: item for item in source["extractions"]}
        self.assertGreaterEqual(len(measurements["captures"]), 8)
        for capture in measurements["captures"]:
            with self.subTest(capture=capture["id"]):
                self.assertEqual("VIDEO_FRAME", capture["sourceType"])
                self.assertIn(capture["id"], extractions)
                extraction = extractions[capture["id"]]
                self.assertEqual(capture["frameTimestampMillis"], extraction["frameTimestampMillis"])
                self.assertEqual("NONE", extraction["visualTransform"])
                self.assertFalse(capture["isCropped"])

    def test_measurements_cover_start_all_apps_and_observable_motion(self):
        data = json.loads((REFERENCE / "WP8_REFERENCE_MEASUREMENTS.json").read_text())
        scenarios = {item["scenarioId"] for item in data["measurementSets"]}
        for required in (
            "START_PRIMARY_GEOMETRY",
            "START_TO_ALL_APPS",
            "APP_OPEN_TRANSITION",
            "LIVE_TILE_CYCLE",
        ):
            self.assertIn(required, scenarios)

        interactions = {
            item["motionEvidence"]["interaction"]
            for item in data["measurementSets"]
            if "motionEvidence" in item
        }
        self.assertTrue({"PAGE_SWIPE", "APP_OPEN", "LIVE_TILE_CYCLE"} <= interactions)

        manifest = json.loads((REFERENCE / "SOURCE_MANIFEST.json").read_text())
        coverage = {item["scenario"]: item["status"] for item in manifest["coverage"]}
        for scenario in (
            "START",
            "ALL_APPS",
            "EDIT_MODE",
            "SLOW_PAGE_SWIPE",
            "FAST_FLING",
            "PIN",
            "LONG_PRESS_DRAG",
            "RESIZE",
            "UNPIN",
            "APP_OPEN_AND_BACK",
            "LIVE_TILE_CYCLE",
            "TILE_PRESS_FEEDBACK",
            "VIRTUAL_HARDWARE_KEYS",
        ):
            self.assertIn(scenario, coverage)
        self.assertIn("NOT_OBSERVED", set(coverage.values()), "missing evidence must remain explicit")

    def test_future_fullscreen_and_virtual_key_ownership_is_locked_without_claiming_runtime(self):
        decision = (STAGE / "FULLSCREEN_NAVIGATION_DECISION.md").read_text()
        for required in (
            "DECIDED_FOR_LATER_STAGES",
            "沉浸式全屏",
            "Back",
            "Start",
            "Search",
            "LauncherAction",
            "LauncherEngine.dispatch(action)",
            "one serialized `LauncherEngine.dispatch(action)` path",
            "Stage 2.5  记录证据、缺口与产品决定；不改 runtime",
            "Stage 3",
            "Stage 4",
            "Stage 5",
            "Stage 9",
            "Stage 10",
            "English translation",
        ):
            self.assertIn(required, decision)
        self.assertNotIn("IMPLEMENTED", decision, "decision document must not claim runtime completion")

    def test_semantic_validator_enforces_content_addressing_and_human_review(self):
        validator = ROOT / ".github/scripts/validate_wp8_reference.py"
        self.assertTrue(validator.is_file(), "missing semantic validator")
        completed = subprocess.run(
            [sys.executable, str(validator), "--require-human-review"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        self.assertIn("WP8_REFERENCE_VALIDATION=PASS", completed.stdout)

    def test_schema_names_live_tile_motion_instead_of_mislabeling_it(self):
        schema = json.loads((REFERENCE / "WP8_REFERENCE_MEASUREMENTS.schema.json").read_text())
        interactions = schema["$defs"]["motionEvidence"]["properties"]["interaction"]["enum"]
        self.assertIn("LIVE_TILE_CYCLE", interactions)

    def test_ci_runs_stage_two_point_five_after_prior_stage_gates(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        ci_contract = (ROOT / ".github/scripts/test-ci-contract.sh").read_text()
        self.assertIn("id: launcher_stage25_contract", workflow)
        self.assertIn("test_launcher_stage25_contract.py", workflow)
        self.assertIn("validate_wp8_reference.py --require-human-review", workflow)
        self.assertIn("LAUNCHER_STAGE25_CONTRACT_RESULT", workflow)
        self.assertIn("launcher_stage25_contract", ci_contract)

    def test_report_is_bilingual_truthful_and_stops_before_stage_three(self):
        report_path = STAGE / "REPORT.md"
        self.assertTrue(report_path.is_file(), "missing Stage 2.5 report")
        report = report_path.read_text()
        for section in (
            "Baseline",
            "Scope",
            "Evidence",
            "Measurements",
            "Tests",
            "Hardware",
            "Human Review",
            "Stop",
        ):
            self.assertIn(f"## {section}", report)
        self.assertIn("English translation", report)
        self.assertIn("Stage 3: NOT STARTED", report)
        self.assertIn("NOT_OBSERVED", report)
        self.assertTrue(
            report.endswith(
                "STOPPED AT STAGE GATE.\nAWAITING HUMAN REVIEW BEFORE NEXT STAGE.\n"
            )
        )


if __name__ == "__main__":
    unittest.main()
