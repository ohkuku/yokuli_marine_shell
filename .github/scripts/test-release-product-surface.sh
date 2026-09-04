#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
apk="${1:-$repo_root/app-shell/build/outputs/apk/standalone/release/app-shell-standalone-release-unsigned.apk}"

fail() {
  printf 'Release product-surface contract failed: %s\n' "$*" >&2
  exit 1
}

[[ -f "$apk" ]] || fail "missing standalone release APK: ${apk#"$repo_root/"}"

sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_dir" && -f "$repo_root/local.properties" ]]; then
  sdk_dir="$(sed -n 's/^sdk\.dir=//p' "$repo_root/local.properties" | head -n 1)"
fi

apkanalyzer_bin=""
if command -v apkanalyzer >/dev/null 2>&1; then
  apkanalyzer_bin="$(command -v apkanalyzer)"
elif [[ -n "$sdk_dir" && -x "$sdk_dir/cmdline-tools/latest/bin/apkanalyzer" ]]; then
  apkanalyzer_bin="$sdk_dir/cmdline-tools/latest/bin/apkanalyzer"
fi
[[ -n "$apkanalyzer_bin" ]] || fail "apkanalyzer is unavailable"

manifest="$($apkanalyzer_bin manifest print "$apk")"
grep -Fq 'com.yokuli.marine.shell.ShellActivity' <<<"$manifest" || fail "release manifest is missing ShellActivity"
for forbidden in \
  'com.yokuli.marine.feature.shell.lab.ShellLabActivity' \
  'com.yokuli.marine.feature.cockpit' \
  'com.yokuli.marine.feature.library' \
  'com.yokuli.marine.feature.system'; do
  grep -Fq "$forbidden" <<<"$manifest" && fail "release manifest contains $forbidden"
done

require_class() {
  "$apkanalyzer_bin" dex code --class "$1" "$apk" >/dev/null 2>&1 || fail "release APK is missing $1"
}

forbid_class() {
  if "$apkanalyzer_bin" dex code --class "$1" "$apk" >/dev/null 2>&1; then
    fail "release APK contains $1"
  fi
}

require_class 'com.yokuli.marine.shell.ProductionShellGraphKt'
require_class 'com.yokuli.marine.feature.chart.ChartWorkspaceKt'
require_class 'com.yokuli.marine.feature.settings.SettingsWorkspaceKt'
forbid_class 'com.yokuli.marine.feature.shell.lab.ShellLabActivity'
forbid_class 'com.yokuli.marine.feature.cockpit.CockpitShellContribution'
forbid_class 'com.yokuli.marine.feature.library.LibraryShellContribution'
forbid_class 'com.yokuli.marine.feature.system.SystemShellContribution'

printf 'Release APK product-surface contract passed: Chart + Settings; Shell Lab absent\n'
