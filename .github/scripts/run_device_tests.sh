#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
mode="${1:-}"
all_device_tasks=(
  :adapter:map-offline:connectedDebugAndroidTest
  :adapter:map-storage:connectedDebugAndroidTest
  :app-shell:connectedStandaloneDebugAndroidTest
)
gradle_args=(--no-daemon --stacktrace)

case "$mode" in
  all)
    gradle_args+=("${all_device_tasks[@]}")
    ;;
  smoke)
    gradle_args+=(
      :app-shell:connectedStandaloneDebugAndroidTest
      '-Pandroid.testInstrumentationRunnerArguments.class=com.yokuli.marine.shell.ShellActivityStoryTest#chartTileOpensBrowseOnlySurfaceAndSystemBackReturnsToStart'
    )
    ;;
  ui-contract)
    gradle_args+=(
      :app-shell:connectedStandaloneDebugAndroidTest
      '-Pandroid.testInstrumentationRunnerArguments.class=com.yokuli.marine.shell.ShellActivityStoryTest#productionShellExposesOnlyChartAndSettingsAndMapRootStaysMapFirst'
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
