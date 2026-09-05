#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
mode="${1:---host-only}"
python_bin="${PYTHON_BIN:-python3}"

case "$mode" in
  --host-only|--with-device) ;;
  *)
    printf 'Usage: %s [--host-only|--with-device]\n' "$0" >&2
    exit 2
    ;;
esac

cd "$repo_root"

"$python_bin" -m unittest discover -s .github/scripts -p 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-secrets-manager.sh
"$python_bin" .github/scripts/validate_wp8_reference.py --require-human-review
"$python_bin" .github/scripts/validate_stage11_fidelity.py

./gradlew --no-daemon \
  test \
  lintStandaloneDebug \
  assembleStandaloneDebug \
  assembleStandaloneDebugAndroidTest \
  assembleStandaloneRelease \
  :benchmark:shell:assembleStandaloneBenchmark \
  --stacktrace
bash .github/scripts/test-release-product-surface.sh

if [[ "$mode" == "--with-device" ]]; then
  bash .github/scripts/run_device_tests.sh all
  bash .github/scripts/run_device_tests.sh performance
  "$python_bin" .github/scripts/summarize_stage11_performance.py \
    --search benchmark/shell/build \
    --output build/marine-shell-final-correction/performance-summary.json \
    --require-journeys
  printf 'MARINE_SHELL_FINAL_GATE=MACHINE_VERIFIED\n'
else
  printf 'MARINE_SHELL_FINAL_GATE=HOST_GATE_PASS device_stories=NOT_RUN_BY_THIS_INVOCATION\n'
fi

git diff --check
