import importlib.util
import json
import sys
import tempfile
from typing import Optional
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


evidence = load("collect_w16_machine_evidence")
unified = load("compose_codex_ci_report")


class W16MachineEvidenceTest(unittest.TestCase):
    def write_sources(self, root: Path, requirements) -> None:
        for requirement in requirements:
            path = root / requirement.source
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(requirement.identifier + "\n", encoding="utf-8")

    def write_xml(self, root: Path, relative_root: str, requirements, failed: Optional[str] = None) -> None:
        path = root / relative_root / "TEST-w16.xml"
        path.parent.mkdir(parents=True, exist_ok=True)
        cases = []
        for requirement in requirements:
            child = '<failure message="broken"/>' if requirement.identifier == failed else ""
            cases.append(
                f'<testcase classname="{requirement.class_name}" name="{requirement.test_name}">{child}</testcase>'
            )
        path.write_text("<testsuite>" + "".join(cases) + "</testsuite>", encoding="utf-8")

    def write_steps_and_maps(self, root: Path, configured: bool = True) -> None:
        step_root = root / "build/codex-ci/steps"
        step_root.mkdir(parents=True)
        for _, label in evidence.BUILD_STEPS:
            (step_root / f"{label}.json").write_text(json.dumps({"outcome": "success"}), encoding="utf-8")
        maps = root / "build/google-maps/configuration.json"
        maps.parent.mkdir(parents=True)
        maps.write_text(
            json.dumps(
                {
                    "configurationConsistent": True,
                    "buildConfigConfigured": configured,
                    "manifestConfigured": configured,
                    "meaning": "CONFIGURATION_ONLY_NOT_RUNTIME_READINESS",
                    "applicationId": "com.yokuli.marine",
                }
            ),
            encoding="utf-8",
        )

    def test_build_evidence_requires_soak_migrations_release_audit_and_maps_injection(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_sources(root, evidence.JVM_REQUIREMENTS)
            self.write_xml(root, evidence.JVM_RESULT_ROOTS[0], evidence.JVM_REQUIREMENTS)
            self.write_steps_and_maps(root)

            report, migration = evidence.collect(root, "build")

            self.assertEqual("PASS", report["status"])
            self.assertEqual("PASS", migration["status"])
            self.assertEqual(
                "PASS",
                next(item for item in report["checks"] if item["id"] == "nmea_high_rate_virtual_soak")["status"],
            )

    def test_missing_maps_key_fails_distribution_but_is_non_blocking_for_untrusted_pr(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_sources(root, evidence.JVM_REQUIREMENTS)
            self.write_xml(root, evidence.JVM_RESULT_ROOTS[0], evidence.JVM_REQUIREMENTS)
            self.write_steps_and_maps(root, configured=False)

            distribution, _ = evidence.collect(root, "build")
            pull_request, _ = evidence.collect(root, "build", allow_unconfigured_maps=True)

            self.assertEqual("FAIL", distribution["status"])
            self.assertEqual("PASS", pull_request["status"])
            maps = next(item for item in pull_request["checks"] if item["id"] == "maps_build_configuration")
            self.assertEqual("NOT_RUN", maps["status"])
            self.assertFalse(maps["required"])

    def test_api34_requires_all_database_migrations_and_both_external_restore_probes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_sources(root, evidence.API34_REQUIREMENTS)
            self.write_xml(root, evidence.API34_RESULT_ROOTS[0], evidence.API34_REQUIREMENTS)
            for _, relative, marker in evidence.PROCESS_LOG_CHECKS:
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(marker + "\n", encoding="utf-8")

            report, migration = evidence.collect(root, "api34")

            self.assertEqual("PASS", report["status"])
            self.assertEqual("PASS", migration["status"])

    def test_failed_fixture_is_never_reported_as_complete(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_sources(root, evidence.API34_REQUIREMENTS)
            self.write_xml(
                root,
                evidence.API34_RESULT_ROOTS[0],
                evidence.API34_REQUIREMENTS,
                failed="map_database_v2_v3",
            )
            for _, relative, marker in evidence.PROCESS_LOG_CHECKS:
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(marker + "\n", encoding="utf-8")

            report, migration = evidence.collect(root, "api34")

            self.assertEqual("FAIL", report["status"])
            self.assertEqual("FAIL", migration["status"])


class W16UnifiedLedgerTest(unittest.TestCase):
    def test_unified_report_contains_exact_machine_and_truthful_physical_statuses(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_root = root / "input"
            head_sha = "a" * 40
            for job in unified.JOBS:
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
            build = input_root / "CODEX-JOB-build"
            api34 = input_root / "CODEX-JOB-api34"
            (build / "w16-build-evidence.json").write_text(
                json.dumps(
                    {
                        "checks": [
                            {"id": "release_surface_audit", "status": "PASS"},
                            {"id": "nmea_high_rate_virtual_soak", "status": "PASS"},
                            {"id": "maps_build_configuration", "status": "PASS"},
                        ]
                    }
                ),
                encoding="utf-8",
            )
            for destination, scope in ((build, "build"), (api34, "api34")):
                (destination / f"w16-migration-{scope}.json").write_text(
                    json.dumps({"status": "PASS", "checks": [{"id": scope, "status": "PASS"}]}),
                    encoding="utf-8",
                )
            (api34 / "w16-api34-evidence.json").write_text(
                json.dumps(
                    {
                        "checks": [
                            {"id": "chart_process_restore", "status": "PASS"},
                            {"id": "nmea_process_restore", "status": "PASS"},
                        ]
                    }
                ),
                encoding="utf-8",
            )

            unified.compose(
                input_root,
                root / "out",
                "ohkuku/yokuli_marine_shell",
                "Yokuli OS Android CI",
                head_sha,
                "codex/shell-map-contract",
                7,
                1,
                {job: "success" for job in unified.JOBS},
                "push",
            )
            ledger = json.loads((root / "out/FINAL_ACCEPTANCE_LEDGER.json").read_text(encoding="utf-8"))

            self.assertEqual("PASS", ledger["machineStatus"])
            self.assertEqual("NOT_RUN", ledger["manualRunStatus"])
            self.assertEqual("NOT_RUN", ledger["humanPhysicalStatus"])
            self.assertTrue(all(item["status"] == "NOT_RUN" for item in ledger["items"]["humanPhysical"]))
            self.assertTrue((root / "out/MIGRATION_REPORT.md").is_file())


if __name__ == "__main__":
    unittest.main()
