#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
adb_bin="${ADB_BIN:-adb}"
target_package='com.yokuli.marine'
test_runner='com.yokuli.marine.test/androidx.test.runner.AndroidJUnitRunner'
probe_class='com.yokuli.marine.shell.ChartC12ProcessRestartProbeTest'

cd "$repo_root"
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
    "$test_runner" | tee "$output"
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$output" || ! grep -Fq 'OK (1 test)' "$output"; then
    printf 'C12 process-restart probe failed: %s\n' "$method" >&2
    return 1
  fi
}

run_probe seedDurableStateForExternalProcessRestart
"$adb_bin" shell am force-stop com.yokuli.marine
run_probe verifyDurableStateAfterExternalProcessRestart
printf 'C12 external force-stop persistence probe passed\n'
