#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
standalone_unsigned_apk="$repo_root/app-shell/build/outputs/apk/standalone/release/app-shell-standalone-release-unsigned.apk"
standalone_signed_apk="$repo_root/app-shell/build/outputs/apk/standalone/release/app-shell-standalone-release.apk"
if [[ -f "$standalone_signed_apk" ]]; then
  standalone_apk="$standalone_signed_apk"
else
  standalone_apk="$standalone_unsigned_apk"
fi

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
  grep -Fq 'android:screenOrientation="portrait"' <<<"$manifest" ||
    fail "$flavor release manifest is not locked to portrait"

  for forbidden in \
    'com.yokuli.marine.feature.shell.lab.ShellLabActivity' \
    'com.yokuli.marine.feature.cockpit' \
    'com.yokuli.marine.feature.library' \
    'com.yokuli.marine.feature.system'; do
    grep -Fq "$forbidden" <<<"$manifest" && fail "$flavor release manifest contains $forbidden"
  done

  grep -Fq 'android.intent.category.HOME' <<<"$manifest" &&
    fail "standalone release manifest unexpectedly contains the HOME category"
  grep -Fq 'android.intent.category.DEFAULT' <<<"$manifest" &&
    fail "standalone release manifest unexpectedly contains the DEFAULT category"

  require_class "$apk" 'com.yokuli.marine.shell.ProductionShellGraphKt'
  require_class "$apk" 'com.yokuli.marine.feature.chart.ChartWorkspaceKt'
  require_class "$apk" 'com.yokuli.marine.feature.settings.SettingsWorkspaceKt'
  require_class "$apk" 'com.yokuli.marine.feature.nmeainput.NmeaInputWorkspaceKt'
  require_class "$apk" 'com.yokuli.marine.feature.datasources.DataSourcesWorkspaceKt'
  require_class "$apk" 'com.yokuli.marine.data.android.runtime.AndroidNmeaInputRuntime'
  require_class "$apk" 'com.yokuli.marine.data.android.runtime.AndroidMarineSourceRuntime'
  require_class "$apk" 'com.yokuli.marine.data.android.service.NmeaInputForegroundService'
  require_class "$apk" 'com.yokuli.marine.data.android.service.PhoneLocationForegroundService'
  forbid_class "$apk" 'com.yokuli.marine.feature.shell.lab.ShellLabActivity'
  forbid_class "$apk" 'com.yokuli.marine.feature.cockpit.CockpitShellContribution'
  forbid_class "$apk" 'com.yokuli.marine.feature.library.LibraryShellContribution'
  forbid_class "$apk" 'com.yokuli.marine.feature.system.SystemShellContribution'
  forbid_class "$apk" 'com.yokuli.marine.data.android.FakeNmeaTransport'
  forbid_class "$apk" 'com.yokuli.marine.data.android.DemoNmeaSender'
  forbid_class "$apk" 'com.yokuli.marine.data.android.NmeaSoakSender'
  forbid_class "$apk" 'com.yokuli.marine.data.android.TestSender'

  printf '%s release APK passed: four installed apps, production runtimes present, debug/demo code absent\n' "$flavor"
}

[[ -f "$standalone_apk" ]] || fail "missing standalone release APK: ${standalone_apk#"$repo_root/"}"
standalone_manifest="$($apkanalyzer_bin manifest print "$standalone_apk")"

inspect_apk standalone "$standalone_apk" "$standalone_manifest"
printf 'Standalone in-app Shell Release passed product-surface and no-HOME inspection\n'
