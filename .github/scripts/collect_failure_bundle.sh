#!/usr/bin/env bash

# Creates a bounded, allow-listed diagnostics directory. It intentionally never
# captures build configuration, environment dumps, signing files, or credentials.
set -u

job_label="${1:-unknown-job}"
safe_label="$(printf '%s' "$job_label" | tr -cs 'A-Za-z0-9._-' '-')"
bundle_root=".github-failure-bundle/${safe_label}"
mkdir -p "$bundle_root"

{
  echo "job=${job_label}"
  echo "repository=${GITHUB_REPOSITORY:-local}"
  echo "workflow=${GITHUB_WORKFLOW:-local}"
  echo "run_id=${GITHUB_RUN_ID:-local}"
  echo "run_attempt=${GITHUB_RUN_ATTEMPT:-local}"
  echo "sha=${GITHUB_SHA:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}"
  echo "ref=${GITHUB_REF:-local}"
  echo "runner_os=${RUNNER_OS:-$(uname -s)}"
  echo "created_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$bundle_root/context.txt"

git status --short > "$bundle_root/git-status.txt" 2>&1 || true
java -version > "$bundle_root/java-version.txt" 2>&1 || true

run_bounded() {
  duration="$1"
  shift
  if command -v timeout >/dev/null 2>&1; then
    timeout "$duration" "$@"
  elif command -v gtimeout >/dev/null 2>&1; then
    gtimeout "$duration" "$@"
  else
    "$@"
  fi
}

run_bounded 10s adb devices -l > "$bundle_root/adb-devices.txt" 2>&1 ||
  echo "adb devices timed out or was unavailable" >> "$bundle_root/adb-devices.txt"
run_bounded 20s adb logcat -d -v threadtime > "$bundle_root/device-logcat.txt" 2>&1 ||
  echo "adb logcat timed out or was unavailable" >> "$bundle_root/device-logcat.txt"

copy_path() {
  source_path="$1"
  if [[ -e "$source_path" ]]; then
    target_parent="$bundle_root/$(dirname "$source_path")"
    mkdir -p "$target_parent"
    cp -R "$source_path" "$target_parent/"
  fi
}

for module in app-shell benchmark/shell baselineprofile/shell core/design core/model core/shell feature/chart feature/cockpit feature/desktop feature/library feature/system; do
  copy_path "$module/build/reports"
  copy_path "$module/build/test-results"
  copy_path "$module/build/outputs/androidTest-results"
  copy_path "$module/build/outputs/logs"
done

for diagnostic_path in build/ci-device-tests.log build/reports build/test-results verified-release SHA256SUMS.txt; do
  copy_path "$diagnostic_path"
done

find "$bundle_root" -type f -print | sort > "$bundle_root/CONTENTS.txt"
