#!/usr/bin/env bash
set -euo pipefail

if [[ ! -e /dev/kvm ]]; then
  printf '::error::/dev/kvm is unavailable; the Android emulator cannot use hardware acceleration.\n' >&2
  exit 1
fi

sudo chmod 0666 /dev/kvm
if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
  printf '::error::The runner cannot read and write /dev/kvm after permission setup.\n' >&2
  ls -l /dev/kvm >&2 || true
  exit 1
fi

printf 'KVM hardware acceleration is available.\n'
