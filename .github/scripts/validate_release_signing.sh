#!/usr/bin/env bash
set -euo pipefail

keystore_file="${1:-}"
store_password="${ANDROID_KEYSTORE_PASSWORD:-}"
key_alias="${ANDROID_KEY_ALIAS:-}"
key_password="${ANDROID_KEY_PASSWORD:-}"

fail() { printf '::error::%s\n' "$*" >&2; exit 1; }

[[ -f "$keystore_file" ]] || fail 'Decoded Yokuli OS release keystore is missing.'
[[ -n "$store_password" ]] || fail 'ANDROID_KEYSTORE_PASSWORD is empty.'
[[ -n "$key_alias" ]] || fail 'ANDROID_KEY_ALIAS is empty.'
[[ -n "$key_password" ]] || fail 'ANDROID_KEY_PASSWORD is empty.'
command -v keytool >/dev/null 2>&1 || fail 'keytool is unavailable.'

export YOKULI_RELEASE_STORE_PASSWORD="$store_password"
if ! keytool -list -keystore "$keystore_file" -storepass:env YOKULI_RELEASE_STORE_PASSWORD -alias "$key_alias" >/dev/null 2>&1; then
  fail 'The keystore or alias cannot be opened with the configured release secrets.'
fi

verification_dir="$(mktemp -d)"
trap 'rm -rf "$verification_dir"' EXIT
export YOKULI_RELEASE_KEY_PASSWORD="$key_password"
export YOKULI_VERIFICATION_PASSWORD='yokuli-os-verification-only'
if ! keytool -importkeystore -noprompt \
  -srckeystore "$keystore_file" \
  -srcstorepass:env YOKULI_RELEASE_STORE_PASSWORD \
  -srcalias "$key_alias" \
  -srckeypass:env YOKULI_RELEASE_KEY_PASSWORD \
  -destkeystore "$verification_dir/key-verification.p12" \
  -deststoretype PKCS12 \
  -deststorepass:env YOKULI_VERIFICATION_PASSWORD >/dev/null 2>&1; then
  fail "ANDROID_KEY_PASSWORD cannot recover alias '$key_alias'."
fi

printf 'Release keystore, alias, and private key are valid.\n'
