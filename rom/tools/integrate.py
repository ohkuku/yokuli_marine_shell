#!/usr/bin/env python3
"""把已校验产品包放入 AOSP；严格拒绝覆盖别人的 vendor/yokuli 或本地改动。"""
import argparse
import hashlib
import json
import shutil
from pathlib import Path


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(directory: Path) -> dict:
    marker = directory / ".yokuli-managed.json"
    if not marker.is_file() or directory.is_symlink():
        raise ValueError(f"Not a managed Yokuli bundle: {directory}")
    manifest = json.loads(marker.read_text())
    files = manifest["files"]
    actual = {str(p.relative_to(directory)) for p in directory.rglob("*") if p.is_file() and p != marker}
    if actual != set(files) or any(p.is_symlink() for p in directory.rglob("*")):
        raise ValueError(f"Unexpected files/symlinks in bundle: {directory}")
    for name, expected in files.items():
        path = Path(name)
        if path.is_absolute() or ".." in path.parts or digest(directory / path) != expected:
            raise ValueError(f"Changed or unsafe managed file: {name}")
    return manifest


def integrate(bundle: Path, aosp: Path):
    source = bundle.resolve()
    incoming = verify(source)
    metadata = json.loads((source / "yokuli-build.json").read_text())
    if not (aosp / "build/envsetup.sh").is_file() or not (aosp / metadata["target"]["upstream_product"]).is_file():
        raise ValueError("Expected the synchronized AOSP Cuttlefish source tree")
    destination = aosp / "vendor/yokuli"
    if destination.exists():
        previous = verify(destination)
        if previous == incoming:
            return "already integrated"
        # Do not remove old files or overwrite an in-progress tree. Keep it for rollback.
        raise ValueError("Another bundle is present. Move verified vendor/yokuli to a backup directory outside AOSP, then integrate again.")
    if destination.is_symlink() or (aosp / "vendor").is_symlink():
        raise ValueError("Refusing vendor symlink")
    destination.parent.mkdir(exist_ok=True)
    shutil.copytree(source, destination)
    return str(destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--aosp", type=Path, required=True)
    args = parser.parse_args()
    try:
        print(integrate(args.bundle, args.aosp.resolve()))
    except (OSError, ValueError, KeyError) as error:
        parser.exit(2, f"ERROR: {error}\n")


if __name__ == "__main__":
    main()
