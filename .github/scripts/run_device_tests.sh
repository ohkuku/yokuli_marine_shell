#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
mode="${1:-}"
gradle_args=(--no-daemon :app-shell:connectedStandaloneDebugAndroidTest --stacktrace)

case "$mode" in
  all)
    ;;
  smoke)
    gradle_args+=(
      '-Pandroid.testInstrumentationRunnerArguments.class=com.yokuli.marine.shell.ShellActivityStoryTest#chartTileOpensBrowseOnlySurfaceAndSystemBackReturnsToStart'
    )
    ;;
  ui-contract)
    gradle_args+=(
      '-Pandroid.testInstrumentationRunnerArguments.class=com.yokuli.marine.shell.ShellActivityStoryTest#productionShellExposesOnlyChartAndSettingsWithReusableLargeTitles'
    )
    ;;
  *)
    printf 'Usage: %s all | smoke | ui-contract\n' "$0" >&2
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
