#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
android="$repo_root/.github/workflows/android.yml"
release="$repo_root/.github/workflows/release.yml"
nightly="$repo_root/.github/workflows/nightly.yml"
device="$repo_root/.github/scripts/run_device_tests.sh"
schema_requirements="$repo_root/.github/requirements/stage0-schema.txt"

fail() {
  printf 'CI contract failed: %s\n' "$*" >&2
  exit 1
}

for required in "$android" "$release" "$nightly" "$device" "$schema_requirements"; do
  [[ -f "$required" ]] || fail "missing ${required#"$repo_root/"}"
done
grep -Eq '^jsonschema==[0-9]+\.[0-9]+\.[0-9]+$' "$schema_requirements" || \
  fail 'Stage 0 schema validator must remain exactly pinned'
grep -Fq 'python3 -m pip install --requirement .github/requirements/stage0-schema.txt' "$android" || \
  fail 'Android CI must install the pinned Stage 0 schema validator'

workflows=("$android" "$release" "$nightly")
for action in actions/checkout@v6 actions/setup-java@v5 gradle/actions/setup-gradle@v6 actions/upload-artifact@v7; do
  grep -Fq "$action" "${workflows[@]}" || fail "current action major not found: $action"
done
grep -Fq 'actions/download-artifact@v8' "$android" || fail 'artifact transfer must verify the server digest'

for job in 'build:' 'integration:' 'api-compatibility:' 'stage11-performance:' 'codex-report:' 'verified-debug:'; do
  grep -Fq "  $job" "$android" || fail "Android CI missing job $job"
done

# Recovery authority is explicit: helper discovery and rejected product-shape
# contracts must never become authoritative by filename convention.
grep -Fq 'id: product_recovery_policy' "$android" || fail 'Product Recovery policy gate is missing'
grep -Fq 'bash .github/scripts/run_ci_helper_tests.sh' "$android" || fail 'CI helper allow-list is missing'
grep -Fq 'bash .github/scripts/run_product_recovery_unit_tests.sh' "$android" || fail 'protected unit-test allow-list is missing'
if grep -Fq "unittest discover .github/scripts 'test_*.py'" "$android"; then
  fail 'broad Python test discovery would revive rejected presentation contracts'
fi

for retired in \
  nmea_sources_p2_contract nmea_sources_p4_contract nmea_sources_p5_contract nmea_sources_p6_contract nmea_sources_p7_contract \
  launcher_stage1_contract launcher_stage5_contract launcher_stage6_contract launcher_stage7_contract \
  launcher_stage8_contract launcher_stage9_contract launcher_stage10_contract launcher_stage11_contract shell_app_contract \
  chart_c12_contract chart_library_cl07_contract chart_library_cl08_contract chart_library_cl09_contract \
  chart_library_cl10_contract chart_library_cl12_contract osr_w02_contract osr_w04_contract osr_w09_contract \
  osr_w12_contract osr_w13_contract osr_w14_contract osr_w15_contract osr_w16_contract chart_shell_ux_correction; do
  RETIRED_ID="$retired" ANDROID_WORKFLOW="$android" python3 - <<'PY'
import os
from pathlib import Path
text = Path(os.environ["ANDROID_WORKFLOW"]).read_text()
step = text.split("id: " + os.environ["RETIRED_ID"], 1)[1].split("- name:", 1)[0]
raise SystemExit(0 if "if: false" in step else 1)
PY
done

for active in \
  launcher_stage0_contract nmea_sources_p1_contract nmea_sources_p3_contract launcher_stage2_contract launcher_stage25_contract \
  launcher_stage3_contract launcher_stage4_contract chart_library_cl11_contract osr_w01_only_contract \
  osr_w03_contract osr_w08_contract osr_w10_contract osr_w11_contract; do
  grep -Fq "id: $active" "$android" || fail "protected gate is missing: $active"
done
grep -Fq 'id: release_surface_audit' "$android" || fail 'release manifest/code audit must stay active'

# The device matrix protects runtime, storage, MBTiles and process restoration;
# rejected Feature presentation suites are deliberately absent.
for task in \
  ':adapter:marine-data-android:connectedDebugAndroidTest' \
  ':adapter:chart-library-android:connectedDebugAndroidTest' \
  ':adapter:map-offline:connectedDebugAndroidTest' \
  ':adapter:map-storage:connectedDebugAndroidTest'; do
  grep -Fq "$task" "$device" || fail "protected device task missing: $task"
done
for rejected in ':feature:chart-library:connectedDebugAndroidTest' ':feature:navigation:connectedDebugAndroidTest'; do
  if grep -Fq "$rejected" "$device"; then fail "rejected presentation suite remains authoritative: $rejected"; fi
done
grep -Fq 'run_c12_process_restore.sh' "$android" || fail 'Chart persistence process restore must remain protected'
grep -Fq 'run_nmea_sources_process_restore.sh' "$android" || fail 'NMEA process restore must remain protected'
grep -Fq -- '--profile product-recovery' "$android" || fail 'performance job must use the recovery startup profile'

for workflow in "$android" "$release" "$nightly"; do
  grep -Fq 'GOOGLE_MAPS_ANDROID_API_KEY: ${{ secrets.GOOGLE_MAPS_ANDROID_API_KEY }}' "$workflow" || \
    fail "$(basename "$workflow") must inject the optional Google Maps Android key"
done
grep -Fq -- '--require-configured' "$android" || fail 'trusted recovery APKs must reject a missing Maps key'
grep -Fq 'if: false # PRODUCT_RECOVERY: signed product releases resume only after explicit human acceptance.' "$release" || \
  fail 'signed release must remain blocked until human acceptance'

grep -Fq 'PRODUCT-RECOVERY-yokuli-os-debug-${{ github.sha }}' "$android" || fail 'recovery candidate artifact is missing'
grep -Fq 'HUMAN-ACCEPTANCE-PENDING-yokuli-os-${{ github.sha }}' "$android" || fail 'pending acceptance artifact is missing'
if grep -Fq 'VERIFIED-yokuli-os-alpha-' "$android"; then fail 'CI must not claim product acceptance'; fi
grep -Fq 'CODEX-CI-REPORT-' "$android" || fail 'commit-bound Codex repair report is missing'
grep -Fq 'FINAL_ACCEPTANCE_LEDGER.json' "$repo_root/.github/scripts/compose_codex_ci_report.py" || fail 'unified evidence ledger is missing'

runner_count="$(grep -h -c 'uses: reactivecircus/android-emulator-runner' "${workflows[@]}" | awk '{ total += $1 } END { print total + 0 }')"
kvm_count="$(grep -h -c 'run: bash .github/scripts/enable_kvm.sh' "${workflows[@]}" | awk '{ total += $1 } END { print total + 0 }')"
[[ "$runner_count" -ge 3 ]] || fail 'protected runtime/device coverage is too narrow'
[[ "$runner_count" -eq "$kvm_count" ]] || fail 'every emulator runner must explicitly enable KVM'

collect_count="$(grep -h -c 'collect_failure_bundle.sh' "${workflows[@]}" | awk '{ total += $1 } END { print total + 0 }')"
failure_artifact_count="$(grep -h -c 'name: FAILURE-' "${workflows[@]}" | awk '{ total += $1 } END { print total + 0 }')"
[[ "$collect_count" -ge 4 ]] || fail 'fallible jobs need bounded diagnostic collection'
[[ "$collect_count" -eq "$failure_artifact_count" ]] || fail 'each collected failure bundle must be uploaded once'

grep -Fq 'GITHUB_STEP_SUMMARY' "$repo_root/.github/scripts/write_job_summary.py" || fail 'job summaries are missing'
grep -Fq '::error' "$repo_root/.github/scripts/report_android_test_failures.py" || fail 'device annotations are missing'
if grep -Eq 'local\.properties|gradle\.properties|ANDROID_SIGNING_KEY|KEYSTORE_PASSWORD' "$repo_root/.github/scripts/collect_failure_bundle.sh"; then
  fail 'failure bundle allow-list references credential-bearing data'
fi

printf 'CI contract passed: Product Recovery keeps core evidence hard and presentation acceptance human-owned.\n'
