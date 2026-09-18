#!/usr/bin/env python3
"""校验 ROM HOME APK 并生成可放入 AOSP vendor/yokuli 的集成包；不是 ROM 镜像。"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

ROM = Path(__file__).resolve().parents[1]
REPO = ROM.parent


def run(*args: str) -> str:
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        raise ValueError(f"{Path(args[0]).name} failed: {result.stdout[-3000:]}")
    return result.stdout


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest() if hasattr(hashlib, "file_digest") else hashlib.sha256(stream.read()).hexdigest()


def inspect_apk(apk: Path, sdk: Path, allow_debug: bool) -> dict:
    versions = sorted((sdk / "build-tools").glob("*"), key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.name)), reverse=True)
    tools = next((p for p in versions if all((p / tool).is_file() for tool in ("aapt2", "apksigner", "zipalign"))), None)
    if tools is None:
        raise ValueError("Android SDK build-tools with aapt2, apksigner and zipalign are required")
    signature = run(str(tools / "apksigner"), "verify", "--print-certs", str(apk))
    badging = run(str(tools / "aapt2"), "dump", "badging", str(apk))
    manifest = run(str(tools / "aapt2"), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml")
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not package or package.group(1) != "com.yokuli.marine":
        raise ValueError("APK package must be com.yokuli.marine")
    for required in ("android.intent.category.HOME", "android.intent.category.DEFAULT", "com.yokuli.marine.shell.rebuild.MainActivity"):
        if required not in manifest:
            raise ValueError(f"Not the ROM HOME variant: missing {required}")
    debug = "application-debuggable" in badging
    if debug and not allow_debug:
        raise ValueError("Debug APK requires --development-apk and can only be used in an R0 userdebug image")
    # R0 is 64-bit x86 Cuttlefish. A phone-only APK must fail before a multi-hour build.
    with zipfile.ZipFile(apk) as archive:
        abis = sorted({entry.split("/")[1] for entry in archive.namelist() if entry.startswith("lib/") and entry.endswith(".so")})
        if any(entry.filename.startswith("lib/") and entry.filename.endswith(".so") and entry.compress_type != zipfile.ZIP_STORED for entry in archive.infolist()):
            raise ValueError("Preprocessed AOSP APK must contain uncompressed JNI libraries")
    if "x86_64" not in abis:
        raise ValueError("APK has no x86_64 native libraries for the configured Cuttlefish target")
    run(str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk))
    certs = re.findall(r"certificate SHA-256 digest: ([0-9a-f]+)", signature)
    return {"package": package.group(1), "version_code": int(package.group(2)), "version_name": package.group(3),
            "debuggable": debug, "abis": abis, "sha256": sha256(apk), "certificate_sha256": certs,
            "verified": ["apk_signature", "home_intent", "package_identity", "x86_64_native_libs", "uncompressed_jni", "zip_alignment_16k"]}


def package(apk: Path, sdk: Path, output: Path, allow_debug: bool) -> dict:
    if output.exists():
        raise ValueError(f"Output already exists; choose a new version directory: {output}")
    info = inspect_apk(apk.resolve(), sdk.resolve(), allow_debug)
    target = json.loads((ROM / "targets/cuttlefish-x86_64.json").read_text())
    commit = run("git", "-C", str(REPO), "rev-parse", "HEAD").strip()
    dirty = bool(run("git", "-C", str(REPO), "status", "--porcelain").strip())
    metadata = {"schema_version": 1, "artifact_kind": "aosp-product-integration-bundle", "is_rom_image": False,
                "target": target, "source_commit": commit, "source_dirty": dirty, "apk": info,
                "verification": {"aosp_build": "NOT_RUN", "cuttlefish_boot": "NOT_RUN", "physical_device": "UNSUPPORTED"}}
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=".yokuli-rom-", dir=output.parent))
    try:
        for source in (ROM / "aosp").iterdir():
            if source.is_file():
                shutil.copy2(source, temporary / source.name)
        (temporary / "prebuilts").mkdir()
        shutil.copy2(apk, temporary / "prebuilts/YokuliHome.apk")
        (temporary / "yokuli-build.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
        # This marker includes every managed file. Integrating never overwrites an unrelated vendor tree.
        files = {str(p.relative_to(temporary)): sha256(p) for p in temporary.rglob("*") if p.is_file()}
        (temporary / ".yokuli-managed.json").write_text(json.dumps({"schema_version": 1, "files": files}, indent=2) + "\n")
        temporary.rename(output)
    except BaseException:
        shutil.rmtree(temporary)
        raise
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "~/Library/Android/sdk"))).expanduser())
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--development-apk", action="store_true")
    args = parser.parse_args()
    try:
        result = package(args.apk, args.sdk, args.output.resolve(), args.development_apk)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        parser.exit(2, f"ERROR: {error}\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
