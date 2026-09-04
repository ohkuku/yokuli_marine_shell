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
  'gradle/actions/setup-gradle@v6' \
  'actions/upload-artifact@v7'; do
  grep -Fq "$action" "${workflows[@]}" || fail "current action major not found: $action"
done
grep -Fq 'actions/download-artifact@v8' "$android" || fail 'verified artifact must be transferred with digest checking'

for job in 'build:' 'integration:' 'api-compatibility:' 'verified-debug:'; do
  grep -Fq "  $job" "$android" || fail "Android CI missing job $job"
done
for check_id in 'ci_helpers' 'release_metadata' 'ci_contract' 'secrets_contract'; do
  grep -Fq "id: $check_id" "$android" || fail "build feedback must expose the $check_id gate independently"
done
grep -Fq 'bash .github/scripts/test-secrets-manager.sh' "$android" || fail 'encrypted secrets workflow contract must run in CI'
grep -Fq 'GOOGLE_MAPS_ANDROID_API_KEY: ${{ secrets.GOOGLE_MAPS_ANDROID_API_KEY }}' "$android" || fail 'debug artifacts must consume the repository Maps key when configured'
grep -Fq 'GOOGLE_MAPS_ANDROID_API_KEY: ${{ secrets.GOOGLE_MAPS_ANDROID_API_KEY }}' "$release" || fail 'release artifacts must consume the repository Maps key'
grep -Fq 'ANDROID_KEY_PASSWORD GOOGLE_MAPS_ANDROID_API_KEY' "$release" || fail 'release preflight must reject a missing Maps key'
grep -Fq 'needs: [build, integration, api-compatibility]' "$android" || fail 'verified artifact must wait for every required gate'
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
