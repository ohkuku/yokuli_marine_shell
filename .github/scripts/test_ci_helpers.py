import importlib.util
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


def load(name: str):
    path = Path(__file__).with_name(name + ".py")
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


reporter = load("report_android_test_failures")
summary = load("write_job_summary")


class CiHelpersTest(unittest.TestCase):
    def test_instrumented_failure_becomes_named_annotation_input(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "TEST-device.xml").write_text(
                '<testsuite><testcase classname="ShellStory" name="opensChart">'
                '<failure message="title absent"/></testcase></testsuite>',
                encoding="utf-8",
            )
            self.assertEqual([("ShellStory.opensChart", "title absent")], reporter.xml_failures(root))

    def test_summary_aggregates_module_test_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / "core" / "build" / "test-results" / "testDebugUnitTest"
            report.mkdir(parents=True)
            (report / "TEST-policy.xml").write_text(
                '<testsuite tests="5" failures="1" errors="0" skipped="2"/>', encoding="utf-8"
            )
            self.assertEqual((5, 1, 0, 2), summary.test_totals(root))

    def test_github_command_escaping(self):
        self.assertEqual("a%25b%0Ac", reporter.github_escape("a%b\nc"))

    def test_failure_reporter_accepts_a_benchmark_result_root(self):
        with tempfile.TemporaryDirectory() as directory:
            original_argv = sys.argv
            try:
                sys.argv = ["report_android_test_failures.py", "--result-root", directory]
                with redirect_stdout(io.StringIO()):
                    self.assertEqual(0, reporter.main())
            finally:
                sys.argv = original_argv


if __name__ == "__main__":
    unittest.main()
