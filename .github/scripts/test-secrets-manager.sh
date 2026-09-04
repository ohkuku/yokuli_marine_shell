#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tool="$repo_root/scripts/secrets/yokuli-secrets.sh"
test_root="$(mktemp -d "${TMPDIR:-/tmp}/yokuli-secrets-test.XXXXXX")"

cleanup() {
  find "$test_root" -type f -delete 2>/dev/null || true
  find "$test_root" -depth -type d -exec rmdir {} \; 2>/dev/null || true
}
trap cleanup EXIT

fail() {
  printf 'Secrets manager contract failed: %s\n' "$*" >&2
  exit 1
}

expect_failure() {
  if "$@" >/dev/null 2>&1; then
    fail "command unexpectedly succeeded: $*"
  fi
}

[[ -x "$tool" ]] || fail "missing executable scripts/secrets/yokuli-secrets.sh"

fake_bin="$test_root/bin"
mkdir -p "$fake_bin"

cat > "$fake_bin/age-keygen" <<'FAKE_KEYGEN'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "--version" ]]; then
  printf 'fake age-keygen 1.0\n'
elif [[ "${1:-}" == "-o" && -n "${2:-}" ]]; then
  printf 'AGE-SECRET-KEY-1TESTONLY\n' > "$2"
elif [[ "${1:-}" == "-y" && -n "${2:-}" ]]; then
  grep -q '^AGE-SECRET-KEY-' "$2"
  printf 'age1testrecipient\n'
else
  exit 64
fi
FAKE_KEYGEN

cat > "$fake_bin/age" <<'FAKE_AGE'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "--version" ]]; then
  printf 'fake age 1.0\n'
  exit 0
fi
output=''
input=''
decrypt=0
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -o|--output|-i|--identity|-r|--recipient)
      if [[ "$1" == '-o' || "$1" == '--output' ]]; then output="$2"; fi
      shift 2
      ;;
    -d|--decrypt)
      decrypt=1
      shift
      ;;
    -a|--armor|-e|--encrypt|-p|--passphrase)
      shift
      ;;
    *)
      input="$1"
      shift
      ;;
  esac
done
[[ -n "$input" && -f "$input" ]] || exit 65
if [[ -n "$output" ]]; then
  if [[ "$decrypt" -eq 1 ]]; then
    sed '1d;$d' "$input" > "$output"
  else
    {
      printf '%s\n' '-----BEGIN AGE ENCRYPTED FILE-----'
      command cat "$input"
      printf '%s\n' '-----END AGE ENCRYPTED FILE-----'
    } > "$output"
  fi
else
  if [[ "$decrypt" -eq 1 ]]; then
    sed '1d;$d' "$input"
  else
    command cat "$input"
  fi
fi
FAKE_AGE
chmod +x "$fake_bin/age" "$fake_bin/age-keygen"

cat > "$fake_bin/copy" <<'FAKE_COPY'
#!/usr/bin/env bash
set -euo pipefail
command cat > "$YOKULI_CLIPBOARD_OUTPUT"
FAKE_COPY
chmod +x "$fake_bin/copy"

export YOKULI_AGE_BIN="$fake_bin/age"
export YOKULI_AGE_KEYGEN_BIN="$fake_bin/age-keygen"
export YOKULI_JQ_BIN="$(command -v jq)"
export YOKULI_CLIPBOARD_BIN="$fake_bin/copy"
export YOKULI_CLIPBOARD_OUTPUT="$test_root/clipboard"
export YOKULI_REPO_ROOT="$test_root/repo"
export YOKULI_SECRETS_DIR="$YOKULI_REPO_ROOT/secrets"
mkdir -p "$YOKULI_SECRETS_DIR"

doctor_before="$($tool doctor)"
grep -q 'UNINITIALIZED' <<< "$doctor_before" || fail 'doctor must identify an empty vault'

"$tool" init >/dev/null
for artifact in identity.age recipient.txt vault.json.age; do
  [[ -s "$YOKULI_SECRETS_DIR/$artifact" ]] || fail "init did not create $artifact"
done
[[ ! -e "$YOKULI_SECRETS_DIR/identity.txt" ]] || fail 'plaintext identity survived init'
[[ ! -e "$YOKULI_SECRETS_DIR/vault.json" ]] || fail 'plaintext vault survived init'
"$tool" doctor >/dev/null

cp "$YOKULI_SECRETS_DIR/vault.json.age" "$test_root/vault-backup.age"
printf 'not encrypted\n' > "$YOKULI_SECRETS_DIR/vault.json.age"
expect_failure "$tool" doctor
mv "$test_root/vault-backup.age" "$YOKULI_SECRETS_DIR/vault.json.age"

printf 'demo-value\n' | "$tool" set MAP_TOKEN >/dev/null
[[ "$("$tool" list)" == 'MAP_TOKEN' ]] || fail 'list did not expose the key name'
[[ "$("$tool" get MAP_TOKEN)" == 'demo-value' ]] || fail 'get did not return the selected value'
"$tool" run -- /bin/bash -c '[[ "${MAP_TOKEN:-}" == "demo-value" ]]' || fail 'run did not inject the value'
complex_value='space $HOME ; $(exit 99) "quotes"'
printf '%s\n' "$complex_value" | "$tool" set COMPLEX_SECRET >/dev/null
[[ "$("$tool" get COMPLEX_SECRET)" == "$complex_value" ]] || fail 'shell metacharacters changed during storage'
"$tool" copy COMPLEX_SECRET >/dev/null || fail 'copy command failed'
[[ "$(command cat "$YOKULI_CLIPBOARD_OUTPUT")" == "$complex_value" ]] || fail 'copy changed the selected value'
EXPECTED_COMPLEX="$complex_value" "$tool" run -- /bin/bash -c '[[ "$COMPLEX_SECRET" == "$EXPECTED_COMPLEX" ]]' \
  || fail 'run reinterpreted shell metacharacters'
expect_failure "$tool" set 'invalid-key'
if printf 'unsafe-value\n' | "$tool" set PATH >/dev/null 2>&1; then
  fail 'set accepted a process-control environment variable'
fi

recipient_before="$(cksum "$YOKULI_SECRETS_DIR/recipient.txt")"
"$tool" rotate >/dev/null
recipient_after="$(cksum "$YOKULI_SECRETS_DIR/recipient.txt")"
[[ "$recipient_before" == "$recipient_after" ]] || fail 'rotate changed the vault recipient'
[[ "$("$tool" get MAP_TOKEN)" == 'demo-value' ]] || fail 'rotate made the vault unreadable'

"$tool" remove MAP_TOKEN >/dev/null
"$tool" remove COMPLEX_SECRET >/dev/null
[[ -z "$("$tool" list)" ]] || fail 'remove left the key behind'
expect_failure "$tool" get MAP_TOKEN

partial_root="$test_root/partial"
mkdir -p "$partial_root/secrets"
printf 'partial\n' > "$partial_root/secrets/recipient.txt"
expect_failure env YOKULI_REPO_ROOT="$partial_root" YOKULI_SECRETS_DIR="$partial_root/secrets" "$tool" doctor

tracked_root="$test_root/tracked"
mkdir -p "$tracked_root/secrets"
git -C "$tracked_root" init -q
printf '{}\n' > "$tracked_root/secrets/vault.json"
git -C "$tracked_root" add secrets/vault.json
expect_failure env YOKULI_REPO_ROOT="$tracked_root" YOKULI_SECRETS_DIR="$tracked_root/secrets" "$tool" doctor

example_root="$test_root/example"
mkdir -p "$example_root/secrets"
git -C "$example_root" init -q
printf 'MAP_API_KEY=replace_me\n' > "$example_root/.env.example"
git -C "$example_root" add .env.example
env YOKULI_REPO_ROOT="$example_root" YOKULI_SECRETS_DIR="$example_root/secrets" "$tool" doctor >/dev/null \
  || fail 'doctor rejected the intentionally tracked placeholder .env.example'

if grep -Eq '(^|[^[:alnum:]_])(eval|source)[[:space:]]|AGE_PASSPHRASE' "$tool"; then
  fail 'implementation may not evaluate plaintext or accept a passphrase through the environment'
fi

printf 'Secrets manager workflow contracts passed\n'
