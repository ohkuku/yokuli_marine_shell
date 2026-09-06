from pathlib import Path
import json
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / ".github/scripts/extract_chart_library_cl11_evidence.py"


class ChartLibraryCl11EvidenceTest(unittest.TestCase):
    def test_complete_manifest_keeps_only_named_typed_measurements(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            log = root / "build/ci-device-tests.log"
            log.parent.mkdir(parents=True)
            log.write_text("\n".join(
                f'CL11_EVIDENCE {{"scenario":"{scenario}","value":1}}'
                for scenario in ("zero-copy-read", "runtime-bounds", "catalog-1000")
            ) + "\n", encoding="utf-8")
            output = root / "evidence.json"
            completed = subprocess.run(
                ["python3", str(SCRIPT), "--root", str(root), "--output", str(output), "--require-complete"],
                check=False,
            )
            result = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(0, completed.returncode)
            self.assertEqual("COMPLETE", result["status"])
            self.assertEqual([], result["missingScenarios"])

    def test_missing_evidence_is_explicit_and_fails_required_mode(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "evidence.json"
            completed = subprocess.run(
                ["python3", str(SCRIPT), "--root", str(root), "--output", str(output), "--require-complete"],
                check=False,
            )
            result = json.loads(output.read_text(encoding="utf-8"))
            self.assertNotEqual(0, completed.returncode)
            self.assertEqual("PARTIAL", result["status"])
            self.assertEqual(3, len(result["missingScenarios"]))


if __name__ == "__main__":
    unittest.main()
