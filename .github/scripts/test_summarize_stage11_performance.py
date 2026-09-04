import json
from pathlib import Path
import tempfile
import unittest

import summarize_stage11_performance as subject


class Stage11PerformanceSummaryTest(unittest.TestCase):
    def test_preserves_androidx_metrics_and_marks_emulator_limit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "shell-benchmarkData.json"
            source.write_text(
                json.dumps(
                    {
                        "context": {"build": {"model": "emulator"}},
                        "benchmarks": [
                            {
                                "name": name,
                                "className": "ShellMacrobenchmark",
                                "metrics": {"frameDurationCpuMs": {"median": 4.2}},
                            }
                            for name in sorted(subject.EXPECTED_JOURNEYS)
                        ],
                    }
                )
            )
            output = root / "summary.json"

            previous = subject.parse_args
            try:
                subject.parse_args = lambda: type(
                    "Args",
                    (),
                    {"search": root, "output": output, "require_journeys": True},
                )()
                self.assertEqual(0, subject.main())
            finally:
                subject.parse_args = previous

            summary = json.loads(output.read_text())
            self.assertEqual("EMULATOR_TREND_ONLY", summary["status"])
            self.assertFalse(summary["hardPerformanceGate"])
            self.assertEqual([], summary["missingJourneys"])
            self.assertEqual(5, len(summary["results"]))


if __name__ == "__main__":
    unittest.main()
