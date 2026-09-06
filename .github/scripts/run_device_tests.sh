#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
mode="${1:-}"
all_device_tasks=(
  :adapter:marine-data-android:connectedDebugAndroidTest
  :adapter:map-offline:connectedDebugAndroidTest
  :adapter:map-storage:connectedDebugAndroidTest
  :app-shell:connectedStandaloneDebugAndroidTest
)
gradle_args=(--no-daemon --stacktrace)

case "$mode" in
  all)
    gradle_args+=("${all_device_tasks[@]}")
    # This two-process probe is driven separately so a real force-stop occurs between methods.
    gradle_args+=(
      '-Pandroid.testInstrumentationRunnerArguments.notClass=com.yokuli.marine.shell.ChartC12ProcessRestartProbeTest,com.yokuli.marine.shell.NmeaP6ProcessRestartProbeTest'
    )
    ;;
  smoke)
    # A positive class filter is interpreted by every instrumentation task in
    # one Gradle invocation. Keep the adapter and app runners separate so the
    # adapter APK is never asked to load an app-shell test class.
    cd "$repo_root"
    mkdir -p build
    set +e
    ./gradlew --no-daemon \
      :adapter:marine-data-android:connectedDebugAndroidTest \
      --stacktrace 2>&1 | tee build/ci-device-tests.log
    adapter_status="${PIPESTATUS[0]}"
    if [[ "$adapter_status" -ne 0 ]]; then
      set -e
      exit "$adapter_status"
    fi
    ./gradlew --no-daemon \
      :app-shell:connectedStandaloneDebugAndroidTest \
      '-Pandroid.testInstrumentationRunnerArguments.class=com.yokuli.marine.shell.ShellActivityStoryTest#chartTileOpensBrowseOnlySurfaceAndSystemBackReturnsToStart' \
      --stacktrace 2>&1 | tee -a build/ci-device-tests.log
    app_status="${PIPESTATUS[0]}"
    set -e
    exit "$app_status"
    ;;
  ui-contract)
    gradle_args+=(
      :app-shell:connectedStandaloneDebugAndroidTest
      '-Pandroid.testInstrumentationRunnerArguments.class=com.yokuli.marine.shell.ShellActivityStoryTest#productionShellExposesFourAppsWhileDefaultStartStaysMapFirst'
    )
    ;;
  performance)
    gradle_args=(--no-daemon :benchmark:shell:connectedStandaloneBenchmarkAndroidTest --stacktrace)
    ;;
  *)
    printf 'Usage: %s all | smoke | ui-contract | performance\n' "$0" >&2
    exit 2
    ;;
esac

cd "$repo_root"
mkdir -p build
set +e
./gradlew "${gradle_args[@]}" 2>&1 | tee build/ci-device-tests.log
gradle_status="${PIPESTATUS[0]}"
set -e
exit "$gradle_status"
