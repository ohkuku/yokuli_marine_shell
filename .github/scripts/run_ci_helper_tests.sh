#!/usr/bin/env bash
set -euo pipefail

python3 .github/scripts/test_ci_helpers.py
python3 .github/scripts/test_codex_ci_report.py
python3 .github/scripts/test_google_maps_configuration_evidence.py
python3 .github/scripts/test_summarize_stage11_performance.py
