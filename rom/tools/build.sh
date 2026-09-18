#!/usr/bin/env bash
# Only builds an already synchronized Linux source tree. Never downloads, flashes or unlocks a device.
set -eo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ $# -ne 1 ]]; then
    echo 'Usage: bash rom/tools/build.sh /absolute/path/to/aosp' >&2
    exit 2
fi
aosp_dir="$(cd "$1" && pwd)"
# R0 fixes output to this source tree. Refuse ambient variables pointing into another build.
if [[ -n "${OUT_DIR:-}" || -n "${OUT_DIR_COMMON_BASE:-}" || -n "${DIST_DIR:-}" ]]; then
    echo 'Unset OUT_DIR, OUT_DIR_COMMON_BASE and DIST_DIR; R0 uses the selected AOSP tree out/ directory.' >&2
    exit 2
fi
python3 "$script_dir/doctor.py" --path "$aosp_dir" --source-present
python3 - "$aosp_dir/vendor/yokuli" "$script_dir" <<'PY'
import json,sys
import subprocess
from pathlib import Path
sys.path.insert(0,sys.argv[2])
from integrate import verify
root=Path(sys.argv[1]); verify(root)
metadata=json.loads((root/'yokuli-build.json').read_text())
if metadata['target']['variant'] != 'userdebug':
    raise SystemExit('R0 tooling permits only the development userdebug product')
actual=subprocess.check_output(['git','-C',str(root.parent.parent/'.repo/manifests'),'rev-parse','HEAD'],text=True).strip()
if actual != metadata['target']['aosp_manifest_commit']:
    raise SystemExit('AOSP manifest HEAD does not match the pinned release; refusing mislabeled build')
manifest_root=root.parent.parent/'.repo/manifests'
if subprocess.check_output(['git','-C',str(manifest_root),'status','--porcelain'],text=True).strip():
    raise SystemExit('AOSP manifest has local changes')
if list((root.parent.parent/'.repo/local_manifests').glob('*.xml')):
    raise SystemExit('R0 does not allow extra local manifests; use the fixed release and staged vendor/yokuli bundle')
PY
cd "$aosp_dir"
# Every managed AOSP project must match the manifest, without local tracked edits.
# Yokuli's own product is an explicit bundle under vendor/yokuli, outside that manifest.
repo forall -e -c 'test -z "$(git status --porcelain)" && test "$(git rev-parse HEAD)" = "$(git rev-parse "$REPO_LREV^{commit}")"'
# Keep the full manifest revision lock next to build outputs, not just a mutable branch name.
mkdir -p out/yokuli-evidence
repo manifest -r -o out/yokuli-evidence/aosp-manifest.xml
source build/envsetup.sh
lunch yokuli_cf_x86_64_phone-trunk_staging-userdebug
m -j"${YOKULI_ROM_JOBS:-8}" dist target-files-package otatools
cp vendor/yokuli/yokuli-build.json out/yokuli-evidence/
echo 'Build command finished. Images are NOT boot-verified; follow docs/os/07-BUILD-AND-VALIDATION.md.'
