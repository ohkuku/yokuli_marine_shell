import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


def load(name: str):
    path = Path(__file__).with_name(name + ".py")
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


job_report = load("build_codex_job_report")
unified_report = load("compose_codex_ci_report")


class CodexJobReportTest(unittest.TestCase):
    def write_capture(self, root: Path, label: str, body: str, exit_code: int = 1) -> None:
        logs = root / "build" / "codex-ci" / "logs"
        steps = root / "build" / "codex-ci" / "steps"
        logs.mkdir(parents=True, exist_ok=True)
        steps.mkdir(parents=True, exist_ok=True)
        (logs / f"{label}.log").write_text(body, encoding="utf-8")
        (steps / f"{label}.json").write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "label": label,
                    "outcome": "failure" if exit_code else "success",
                    "exitCode": exit_code,
                    "startedAt": "2026-09-06T00:00:00Z",
                    "finishedAt": "2026-09-06T00:00:01Z",
                    "durationSeconds": 1,
                    "log": f"build/codex-ci/logs/{label}.log",
                }
            ),
            encoding="utf-8",
        )

    def test_compile_failure_is_reported_without_junit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_capture(root, "assemble", "e: feature/data/src/main/Foo.kt:17:9 Unresolved reference: bar\n")
            report = job_report.build_report(root, root / "out", "build", "failure", "a" * 40)
            self.assertEqual(1, report["counts"]["compile"])
            markdown = (root / "out" / "JOB_REPORT.md").read_text(encoding="utf-8")
            self.assertIn("feature/data/src/main/Foo.kt:17", markdown)
            self.assertNotIn("EVIDENCE_INSUFFICIENT", markdown)

    def test_junit_and_lint_failures_keep_module_and_location(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tests = root / "feature" / "data" / "build" / "test-results" / "test"
            tests.mkdir(parents=True)
            (tests / "TEST-flow.xml").write_text(
                '<testsuite><testcase classname="DataFlowTest" name="resolvesSource">'
                '<failure message="wrong candidate">trace</failure></testcase></testsuite>',
                encoding="utf-8",
            )
            lint = root / "feature" / "data" / "build" / "reports"
            lint.mkdir(parents=True)
            (lint / "lint-results-standaloneDebug.xml").write_text(
                '<issues><issue id="UnsafeOptInUsageError" severity="Error" message="Unsafe use">'
                '<location file="feature/data/src/main/Data.kt" line="23"/></issue></issues>',
                encoding="utf-8",
            )
            report = job_report.build_report(root, root / "out", "build", "failure", "b" * 40)
            self.assertEqual(1, report["counts"]["tests"])
            self.assertEqual(1, report["counts"]["lint"])
            self.assertEqual("feature/data", report["failureGroups"][0]["items"][0]["module"])

    def test_success_report_is_compact_and_machine_readable(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = job_report.build_report(root, root / "out", "api36", "success", "c" * 40)
            self.assertTrue(report["evidenceSufficient"])
            self.assertIn("No blocking evidence", (root / "out" / "JOB_REPORT.md").read_text(encoding="utf-8"))
            self.assertTrue((root / "out" / "SHA256SUMS.txt").is_file())

    def test_malformed_xml_is_a_warning_not_a_report_crash(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            results = root / "app-shell" / "build" / "test-results" / "test"
            results.mkdir(parents=True)
            (results / "TEST-broken.xml").write_text("<testsuite", encoding="utf-8")
            report = job_report.build_report(root, root / "out", "build", "failure", "d" * 40)
            self.assertTrue(any("Malformed XML" in warning for warning in report["warnings"]))

    def test_allowlist_excludes_secret_files_and_redacts_captured_tokens(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "local.properties").write_text("GOOGLE_MAPS_ANDROID_API_KEY=AIza" + "x" * 30, encoding="utf-8")
            self.write_capture(root, "contract", "token=ghp_" + "a" * 30 + "\ncontract failed\n")
            evidence = job_report.allowlisted_evidence(root, "build")
            self.assertNotIn(root / "local.properties", evidence)
            job_report.build_report(root, root / "out", "build", "failure", "e" * 40)
            output_text = "\n".join(
                path.read_text(encoding="utf-8", errors="replace")
                for path in (root / "out").rglob("*")
                if path.is_file()
            )
            self.assertNotIn("ghp_" + "a" * 30, output_text)
            self.assertNotIn("AIza" + "x" * 30, output_text)


class UnifiedCodexReportTest(unittest.TestCase):
    def test_manifest_and_artifact_name_are_bound_to_exact_head(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_root = root / "input"
            head_sha = "f" * 40
            for job in unified_report.JOBS:
                source = input_root / f"CODEX-JOB-{job}"
                source.mkdir(parents=True)
                (source / "job.json").write_text(
                    json.dumps(
                        {
                            "schemaVersion": 1,
                            "job": job,
                            "status": "success",
                            "headSha": head_sha,
                            "evidenceSufficient": True,
                            "failureGroups": [],
                        }
                    ),
                    encoding="utf-8",
                )
                (source / "JOB_REPORT.md").write_text("green\n", encoding="utf-8")
            manifest = unified_report.compose(
                input_root,
                root / "out",
                "ohkuku/yokuli_marine_shell",
                "Yokuli OS Android CI",
                head_sha,
                "codex/shell-map-contract",
                123,
                2,
                {job: "success" for job in unified_report.JOBS},
            )
            self.assertEqual(head_sha, manifest["headSha"])
            self.assertEqual(head_sha[:12], manifest["sha12"])
            self.assertEqual("SUCCESS", manifest["overall"])
            self.assertTrue((root / "out" / "jobs" / "build" / "job.json").is_file())
            ledger = json.loads((root / "out" / "FINAL_ACCEPTANCE_LEDGER.json").read_text())
            self.assertEqual("PRODUCT_RECOVERY", ledger["workPackage"])
            self.assertEqual("AWAITING_HUMAN_ACCEPTANCE", ledger["decision"])
            self.assertFalse(ledger["legacyPresentationContractsAuthoritative"])
            self.assertIn(
                "HUMAN-ACCEPTANCE-PENDING-yokuli-os",
                (root / "out" / "RAW_ARTIFACTS.md").read_text(),
            )

    def test_missing_failed_job_requests_only_the_matching_raw_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            head_sha = "1" * 40
            results = {job: "success" for job in unified_report.JOBS}
            results["api34"] = "failure"
            unified_report.compose(
                root / "empty",
                root / "out",
                "ohkuku/yokuli_marine_shell",
                "Yokuli OS Android CI",
                head_sha,
                "codex/test",
                99,
                1,
                results,
            )
            markdown = (root / "out" / "CODEX_REPORT.md").read_text(encoding="utf-8")
            self.assertIn("EVIDENCE_INSUFFICIENT", markdown)
            self.assertIn(f"yokuli-os-api34-reports-{head_sha}", markdown)
            self.assertNotIn(f"yokuli-os-build-reports-{head_sha}\n", markdown)


if __name__ == "__main__":
    unittest.main()
