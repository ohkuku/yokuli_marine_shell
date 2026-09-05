import json
import io
from contextlib import redirect_stdout
from pathlib import Path
import tempfile
import unittest

import summarize_stage11_performance as subject


class Stage11PerformanceSummaryTest(unittest.TestCase):
    def test_abort_emits_a_github_annotation_without_relaxing_the_failure(self):
        output = io.StringIO()
        with redirect_stdout(output), self.assertRaises(SystemExit):
            subject.abort_with_annotation("empty: settingsScroll")

        self.assertIn(
            "::error title=Stage 11 performance summary::empty: settingsScroll",
            output.getvalue(),
        )

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
                                "metrics": (
                                    {"timeToInitialDisplayMs": {"median": 280.0}}
                                    if name in subject.STARTUP_JOURNEYS
                                    else {"gfxFrameTotalCount": {"maximum": 12.0, "runs": [10.0, 12.0]}}
                                ),
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
            self.assertEqual([], summary["emptyFrameJourneys"])
            self.assertEqual(len(subject.EXPECTED_JOURNEYS), len(summary["results"]))

    def test_rejects_an_interaction_journey_with_zero_observed_frames(self):
        results = {
            "name": "settingsScroll",
            "metrics": {"gfxFrameTotalCount": {"maximum": 0.0, "runs": [0.0]}},
            "sampledMetrics": {},
        }
        self.assertFalse(subject.has_observed_frames(results))


if __name__ == "__main__":
    unittest.main()
