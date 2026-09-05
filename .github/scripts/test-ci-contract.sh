#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
android="$repo_root/.github/workflows/android.yml"
release="$repo_root/.github/workflows/release.yml"
nightly="$repo_root/.github/workflows/nightly.yml"

fail() {
  printf 'CI contract failed: %s\n' "$*" >&2
  exit 1
}

for required in "$android" "$release" "$nightly"; do
  [[ -f "$required" ]] || fail "missing ${required#"$repo_root/"}"
done

workflows=("$android" "$release" "$nightly")
for action in \
  'actions/checkout@v6' \
  'actions/setup-java@v5' \
  'actions/setup-python@v6' \
  'gradle/actions/setup-gradle@v6' \
  'actions/upload-artifact@v7'; do
  grep -Fq "$action" "${workflows[@]}" || fail "current action major not found: $action"
done
grep -Fq 'actions/download-artifact@v8' "$android" || fail 'verified artifact must be transferred with digest checking'

for job in 'build:' 'integration:' 'api-compatibility:' 'stage11-performance:' 'verified-debug:'; do
  grep -Fq "  $job" "$android" || fail "Android CI missing job $job"
done
for check_id in 'ci_helpers' 'release_metadata' 'ci_contract' 'secrets_contract'; do
  grep -Fq "id: $check_id" "$android" || fail "build feedback must expose the $check_id gate independently"
done
grep -Fq 'bash .github/scripts/test-secrets-manager.sh' "$android" || fail 'encrypted secrets workflow contract must run in CI'
grep -Fq 'id: launcher_stage0_contract' "$android" || fail 'Launcher Stage 0 needs an independent named CI gate'
grep -Fq 'python3 -m pip install --requirement .github/requirements/stage0-schema.txt' "$android" || fail 'Stage 0 must install its pinned Draft 2020-12 validator'
grep -Fq 'jsonschema==' "$repo_root/.github/requirements/stage0-schema.txt" || fail 'Stage 0 schema validator must be version-pinned'
grep -Fq 'python3 .github/scripts/test_launcher_stage0_contract.py' "$android" || fail 'Launcher Stage 0 contract must run in CI'
grep -Fq 'LAUNCHER_STAGE0_CONTRACT_RESULT' "$android" || fail 'Launcher Stage 0 result must participate in final enforcement'
grep -Fq 'id: launcher_stage1_contract' "$android" || fail 'Launcher Stage 1 needs an independent named CI gate'
grep -Fq 'python3 .github/scripts/test_launcher_stage1_contract.py' "$android" || fail 'Launcher Stage 1 static product-surface contract must run in CI'
grep -Fq 'bash .github/scripts/test-release-product-surface.sh' "$android" || fail 'Launcher Stage 1 must inspect the assembled release APK'
grep -Fq 'assembleStandaloneDebug assembleStandaloneRelease' "$android" || fail 'Marine Shell CI must assemble standalone Debug and Release for inspection'
if grep -Eq 'assembleHome|bundleHome|app-shell/build/outputs/apk/home' "$android" "$release"; then
  fail 'in-app Marine Shell workflows must not build or publish a HOME flavor'
fi
grep -Fq 'LAUNCHER_STAGE1_CONTRACT_RESULT' "$android" || fail 'Launcher Stage 1 result must participate in final enforcement'
grep -Fq 'id: launcher_stage2_contract' "$android" || fail 'Launcher Stage 2 needs an independent named CI gate'
grep -Fq 'python3 .github/scripts/test_launcher_stage2_contract.py' "$android" || fail 'Launcher Stage 2 architecture boundary contract must run in CI'
grep -Fq 'LAUNCHER_STAGE2_CONTRACT_RESULT' "$android" || fail 'Launcher Stage 2 result must participate in final enforcement'
grep -Fq 'id: launcher_stage25_contract' "$android" || fail 'Launcher Stage 2.5 needs an independent named CI gate'
grep -Fq 'python3 .github/scripts/test_launcher_stage25_contract.py' "$android" || fail 'Launcher Stage 2.5 contract must run in CI'
grep -Fq 'validate_wp8_reference.py --require-human-review' "$android" || fail 'Stage 2.5 CI must require the hash-bound human review'
grep -Fq 'LAUNCHER_STAGE25_CONTRACT_RESULT' "$android" || fail 'Launcher Stage 2.5 result must participate in final enforcement'
grep -Fq 'id: launcher_stage10_contract' "$android" || fail 'Launcher Stage 10 needs an independent named CI gate'
grep -Fq 'python3 .github/scripts/test_launcher_stage10_contract.py' "$android" || fail 'Launcher Stage 10 durable recovery contract must run in CI'
grep -Fq 'LAUNCHER_STAGE10_CONTRACT_RESULT' "$android" || fail 'Launcher Stage 10 result must participate in final enforcement'
grep -Fq 'id: launcher_stage11_contract' "$android" || fail 'Launcher Stage 11 needs an independent named CI gate'
grep -Fq 'python3 .github/scripts/test_launcher_stage11_contract.py' "$android" || fail 'Launcher Stage 11 contract must run in CI'
grep -Fq 'python3 .github/scripts/validate_stage11_fidelity.py' "$android" || fail 'Stage 11 candidate Goldens need semantic validation'
grep -Fq 'LAUNCHER_STAGE11_CONTRACT_RESULT' "$android" || fail 'Launcher Stage 11 result must participate in final enforcement'
grep -Fq 'bash .github/scripts/run_device_tests.sh performance' "$android" || fail 'Stage 11 Macrobenchmark must use the diagnostic wrapper'
grep -Fq ':benchmark:shell:connectedStandaloneBenchmarkAndroidTest' "$repo_root/.github/scripts/run_device_tests.sh" || fail 'Stage 11 wrapper must run the real benchmark task'
grep -Fq 'stage11-performance-reports' "$android" || fail 'Stage 11 measurements and traces must be downloadable'
if grep -Fq 'GOOGLE_MAPS_ANDROID_API_KEY' "$android" "$release" "$nightly"; then
  fail 'the offline-first production renderer must not require or consume a Google Maps key'
fi
grep -Fq 'ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD' "$release" || fail 'release preflight must still reject missing signing secrets'
grep -Fq 'needs: [build, integration, api-compatibility, stage11-performance]' "$android" || fail 'verified artifact must wait for every required gate'
grep -Fq 'UNVERIFIED-' "$android" || fail 'partial build artifacts must be visibly unverified'
grep -Fq 'VERIFIED-' "$android" || fail 'post-gate artifact must be visibly verified'

runner_count="$(grep -h -c 'uses: reactivecircus/android-emulator-runner' "${workflows[@]}" | awk '{ total += $1 } END { print total + 0 }')"
kvm_count="$(grep -h -c 'run: bash .github/scripts/enable_kvm.sh' "${workflows[@]}" | awk '{ total += $1 } END { print total + 0 }')"
device_script_count="$(grep -h -c 'bash .github/scripts/run_device_tests.sh' "${workflows[@]}" | awk '{ total += $1 } END { print total + 0 }')"
[[ "$runner_count" -ge 3 ]] || fail 'CI, compatibility, release, and nightly workflows need emulator coverage'
[[ "$runner_count" -eq "$kvm_count" ]] || fail 'every emulator runner must explicitly enable KVM'
[[ "$runner_count" -eq "$device_script_count" ]] || fail 'every emulator runner must use the diagnostic wrapper'

collect_count="$(grep -h -c 'collect_failure_bundle.sh' "${workflows[@]}" | awk '{ total += $1 } END { print total + 0 }')"
failure_artifact_count="$(grep -h -c 'name: FAILURE-' "${workflows[@]}" | awk '{ total += $1 } END { print total + 0 }')"
[[ "$collect_count" -ge 4 ]] || fail 'fallible jobs need bounded diagnostic collection'
[[ "$collect_count" -eq "$failure_artifact_count" ]] || fail 'each collected failure bundle must be uploaded once'

grep -Fq 'GITHUB_STEP_SUMMARY' "$repo_root/.github/scripts/write_job_summary.py" || fail 'job summary writer is missing'
grep -Fq '::error' "$repo_root/.github/scripts/report_android_test_failures.py" || fail 'device failures need GitHub annotations'
grep -Fq 'device-logcat.txt' "$repo_root/.github/scripts/collect_failure_bundle.sh" || fail 'failure bundle must capture bounded logcat'
if grep -Eq 'local\.properties|gradle\.properties|ANDROID_SIGNING_KEY|KEYSTORE_PASSWORD' "$repo_root/.github/scripts/collect_failure_bundle.sh"; then
  fail 'failure bundle allow-list references credential-bearing data'
fi

grep -Fq 'permissions:' "$release" || fail 'release permissions must be explicit'
grep -Fq 'contents: write' "$release" || fail 'release publication needs contents write only in release workflow'
grep -Fq 'apksigner_path" verify' "$release" || fail 'release APK signatures must be verified'
grep -Fq 'sha256sum' "$release" || fail 'release artifacts need SHA-256 checksums'
grep -Fq 'gh release create' "$release" || fail 'release workflow must publish through GitHub CLI'
grep -Fq 'schedule:' "$nightly" || fail 'nightly compatibility workflow needs a schedule'

printf 'CI and release workflow contracts passed\n'
