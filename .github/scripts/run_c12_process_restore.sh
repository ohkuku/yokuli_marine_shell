#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
target_package='com.yokuli.marine'
test_runner='com.yokuli.marine.test/androidx.test.runner.AndroidJUnitRunner'
probe_class='com.yokuli.marine.shell.ChartC12ProcessRestartProbeTest'

resolve_adb() {
  if [[ -n "${ADB_BIN:-}" && -x "$ADB_BIN" ]]; then
    printf '%s\n' "$ADB_BIN"
    return
  fi
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi
  local sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -z "$sdk_dir" && -f "$repo_root/local.properties" ]]; then
    sdk_dir="$(sed -n 's/^sdk\.dir=//p' "$repo_root/local.properties" | head -n 1)"
  fi
  if [[ -n "$sdk_dir" && -x "$sdk_dir/platform-tools/adb" ]]; then
    printf '%s\n' "$sdk_dir/platform-tools/adb"
    return
  fi
  printf 'adb was not found; set ADB_BIN or configure the Android SDK\n' >&2
  return 127
}

adb_bin="$(resolve_adb)"

cd "$repo_root"
mkdir -p build
process_log='build/ci-c12-process-restore.log'
: > "$process_log"
./gradlew --no-daemon \
  :app-shell:assembleStandaloneDebug \
  :app-shell:assembleStandaloneDebugAndroidTest \
  --stacktrace

"$adb_bin" install -r app-shell/build/outputs/apk/standalone/debug/app-shell-standalone-debug.apk
"$adb_bin" install -r app-shell/build/outputs/apk/androidTest/standalone/debug/app-shell-standalone-debug-androidTest.apk
[[ "$("$adb_bin" shell pm clear "$target_package" | tr -d '\r')" == 'Success' ]]

run_probe() {
  local method="$1"
  local output
  output="$(mktemp -t yokuli-c12-process.XXXXXX)"
  "$adb_bin" shell am instrument -w \
    -e class "$probe_class#$method" \
    "$test_runner" | tee "$output" | tee -a "$process_log"
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$output" || ! grep -Fq 'OK (1 test)' "$output"; then
    printf 'C12 process-restart probe failed: %s\n' "$method" >&2
    rm -f "$output"
    return 1
  fi
  rm -f "$output"
}

printf 'C12 external process-restart probe\n' | tee -a "$process_log"
run_probe seedDurableStateForExternalProcessRestart
"$adb_bin" shell am force-stop com.yokuli.marine
run_probe verifyDurableStateAfterExternalProcessRestart
printf 'C12 external force-stop persistence probe passed\n' | tee -a "$process_log"
