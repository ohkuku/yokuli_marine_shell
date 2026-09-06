#!/usr/bin/env bash

# Capture one fallible CI command without changing its exit status. Only the
# allow-listed log and small step metadata are written; commands and environment
# variables are deliberately not recorded.
set -uo pipefail

if [[ "$#" -lt 3 || "$2" != "--" ]]; then
  echo "usage: run_ci_capture.sh STEP_LABEL -- COMMAND [ARG ...]" >&2
  exit 64
fi

step_label="$1"
shift 2

if [[ -z "$step_label" || "$step_label" == *[!A-Za-z0-9._-]* ]]; then
  echo "step label must contain only A-Z, a-z, 0-9, dot, underscore, or dash" >&2
  exit 64
fi

capture_root="build/codex-ci"
log_path="$capture_root/logs/$step_label.log"
step_path="$capture_root/steps/$step_label.json"
mkdir -p "$(dirname "$log_path")" "$(dirname "$step_path")"

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
started_epoch="$(date +%s)"

set +e
"$@" 2>&1 | tee "$log_path"
command_status="${PIPESTATUS[0]}"
set -e

finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
finished_epoch="$(date +%s)"
duration_seconds="$((finished_epoch - started_epoch))"
if [[ "$command_status" -eq 0 ]]; then
  outcome="success"
else
  outcome="failure"
fi

printf '{\n  "schemaVersion": 1,\n  "label": "%s",\n  "outcome": "%s",\n  "exitCode": %d,\n  "startedAt": "%s",\n  "finishedAt": "%s",\n  "durationSeconds": %d,\n  "log": "%s"\n}\n' \
  "$step_label" "$outcome" "$command_status" "$started_at" "$finished_at" \
  "$duration_seconds" "$log_path" > "$step_path"

exit "$command_status"
