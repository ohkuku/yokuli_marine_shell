#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
standalone_apk="$repo_root/app-shell/build/outputs/apk/standalone/release/app-shell-standalone-release-unsigned.apk"
home_apk="$repo_root/app-shell/build/outputs/apk/home/release/app-shell-home-release-unsigned.apk"

fail() {
  printf 'Release product-surface contract failed: %s\n' "$*" >&2
  exit 1
}

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

require_class() {
  local apk="$1"
  local class_name="$2"
  "$apkanalyzer_bin" dex code --class "$class_name" "$apk" >/dev/null 2>&1 ||
    fail "${apk##*/} is missing $class_name"
}

forbid_class() {
  local apk="$1"
  local class_name="$2"
  if "$apkanalyzer_bin" dex code --class "$class_name" "$apk" >/dev/null 2>&1; then
    fail "${apk##*/} contains $class_name"
  fi
}

extract_manifest_names() {
  local manifest="$1"
  local elements="$2"
  awk -v elements="$elements" '
    $0 ~ "<(" elements ")([[:space:]>])" { capture = 1 }
    capture && /android:name="/ {
      value = $0
      sub(/^.*android:name="/, "", value)
      sub(/".*$/, "", value)
      print value
      capture = 0
    }
  ' <<<"$manifest" | sort -u
}

inspect_apk() {
  local flavor="$1"
  local apk="$2"
  local manifest="$3"
  [[ -f "$apk" ]] || fail "missing $flavor release APK: ${apk#"$repo_root/"}"

  grep -Fq 'com.yokuli.marine.shell.ShellActivity' <<<"$manifest" ||
    fail "$flavor release manifest is missing ShellActivity"
  grep -Fq 'android.intent.category.LAUNCHER' <<<"$manifest" ||
    fail "$flavor release manifest is missing the launcher category"

  for forbidden in \
    'com.yokuli.marine.feature.shell.lab.ShellLabActivity' \
    'com.yokuli.marine.feature.cockpit' \
    'com.yokuli.marine.feature.library' \
    'com.yokuli.marine.feature.system'; do
    grep -Fq "$forbidden" <<<"$manifest" && fail "$flavor release manifest contains $forbidden"
  done

  if [[ "$flavor" == "home" ]]; then
    grep -Fq 'android.intent.category.HOME' <<<"$manifest" ||
      fail "home release manifest is missing the HOME category"
    grep -Fq 'android.intent.category.DEFAULT' <<<"$manifest" ||
      fail "home release manifest is missing the DEFAULT category"
  else
    grep -Fq 'android.intent.category.HOME' <<<"$manifest" &&
      fail "standalone release manifest unexpectedly contains the HOME category"
    grep -Fq 'android.intent.category.DEFAULT' <<<"$manifest" &&
      fail "standalone release manifest unexpectedly contains the DEFAULT category"
  fi

  require_class "$apk" 'com.yokuli.marine.shell.ProductionShellGraphKt'
  require_class "$apk" 'com.yokuli.marine.feature.chart.ChartWorkspaceKt'
  require_class "$apk" 'com.yokuli.marine.feature.settings.SettingsWorkspaceKt'
  forbid_class "$apk" 'com.yokuli.marine.feature.shell.lab.ShellLabActivity'
  forbid_class "$apk" 'com.yokuli.marine.feature.cockpit.CockpitShellContribution'
  forbid_class "$apk" 'com.yokuli.marine.feature.library.LibraryShellContribution'
  forbid_class "$apk" 'com.yokuli.marine.feature.system.SystemShellContribution'

  printf '%s release APK passed: Chart + Settings; Shell Lab absent\n' "$flavor"
}

[[ -f "$standalone_apk" ]] || fail "missing standalone release APK: ${standalone_apk#"$repo_root/"}"
[[ -f "$home_apk" ]] || fail "missing home release APK: ${home_apk#"$repo_root/"}"
standalone_manifest="$($apkanalyzer_bin manifest print "$standalone_apk")"
home_manifest="$($apkanalyzer_bin manifest print "$home_apk")"

inspect_apk standalone "$standalone_apk" "$standalone_manifest"
inspect_apk home "$home_apk" "$home_manifest"

standalone_components="$(extract_manifest_names "$standalone_manifest" 'activity|service|receiver|provider')"
home_components="$(extract_manifest_names "$home_manifest" 'activity|service|receiver|provider')"
[[ "$standalone_components" == "$home_components" ]] ||
  fail "standalone and home release component sets differ"

standalone_actions="$(extract_manifest_names "$standalone_manifest" 'action')"
home_actions="$(extract_manifest_names "$home_manifest" 'action')"
[[ "$standalone_actions" == "$home_actions" ]] ||
  fail "standalone and home release intent actions differ"

standalone_categories="$(extract_manifest_names "$standalone_manifest" 'category')"
home_base_categories="$(extract_manifest_names "$home_manifest" 'category' | sed '/^android\.intent\.category\.HOME$/d; /^android\.intent\.category\.DEFAULT$/d')"
[[ "$standalone_categories" == "$home_base_categories" ]] ||
  fail "home release has an unexpected category difference beyond HOME/DEFAULT"

printf 'Both Release flavors passed product-surface inspection; HOME adds only HOME/DEFAULT launch categories\n'
