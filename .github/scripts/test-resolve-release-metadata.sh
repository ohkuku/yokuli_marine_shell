#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
resolver="$script_dir/resolve_release_metadata.sh"

stable="$(GITHUB_OUTPUT= RELEASE_EVENT=push RELEASE_REF=refs/tags/v1.2.3 "$resolver")"
grep -q '^channel=stable$' <<< "$stable"
grep -q '^version_name=1.2.3$' <<< "$stable"
grep -q '^version_code=102039000$' <<< "$stable"

alpha="$(GITHUB_OUTPUT= RELEASE_EVENT=push RELEASE_REF=refs/tags/v1.2.4-alpha.7 "$resolver")"
grep -q '^channel=alpha$' <<< "$alpha"
grep -q '^version_code=102041007$' <<< "$alpha"

beta="$(GITHUB_OUTPUT= RELEASE_EVENT=workflow_dispatch RELEASE_TAG_INPUT=v1.2.4-beta.2 "$resolver")"
grep -q '^channel=beta$' <<< "$beta"
grep -q '^version_code=102045002$' <<< "$beta"

if GITHUB_OUTPUT= RELEASE_EVENT=push RELEASE_REF=refs/tags/not-a-version "$resolver" >/dev/null 2>&1; then
  printf 'Invalid release tag unexpectedly resolved\n' >&2
  exit 1
fi

actions_output="$(mktemp)"
trap 'rm -f "$actions_output"' EXIT
GITHUB_OUTPUT="$actions_output" RELEASE_EVENT=push RELEASE_REF=refs/tags/v2.0.0-alpha.1 "$resolver"
grep -q '^tag=v2.0.0-alpha.1$' "$actions_output"
grep -q '^version_name=2.0.0-alpha.1$' "$actions_output"
grep -q '^version_code=200001001$' "$actions_output"
grep -q '^channel=alpha$' "$actions_output"

printf 'release metadata checks passed\n'
