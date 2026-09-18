#!/usr/bin/env python3
"""只读检查 ROM 构建/运行宿主；不会下载 AOSP、安装软件或修改设备。"""
import argparse
import json
import os
import platform
import shutil
from pathlib import Path

ROM = Path(__file__).resolve().parents[1]


def assess(path: Path, source_present: bool = False) -> dict:
    target = json.loads((ROM / "targets/cuttlefish-x86_64.json").read_text())
    probe = path.resolve()
    while not probe.exists():
        probe = probe.parent
    free = shutil.disk_usage(probe).free / 1024 ** 3
    ram = None
    if Path("/proc/meminfo").exists():
        for line in Path("/proc/meminfo").read_text().splitlines():
            if line.startswith("MemTotal:"):
                ram = int(line.split()[1]) / 1024 ** 2
    required = target["minimum_build_disk_gib" if source_present else "minimum_fresh_disk_gib"]
    checks = {
        "linux_x86_64": platform.system() == "Linux" and platform.machine() in ("x86_64", "amd64"),
        "disk": free >= required,
        # Kernel-reserved memory means a nominal 64 GiB host reports slightly less.
        "memory": ram is not None and ram >= target["recommended_ram_gib"] * .95,
        "repo": shutil.which("repo") is not None,
        "git": shutil.which("git") is not None,
    }
    if source_present:
        checks["source_tree"] = (path / "build/envsetup.sh").is_file() and (path / target["upstream_product"]).is_file()
    kvm = os.access("/dev/kvm", os.R_OK | os.W_OK)
    return {
        "host": f"{platform.system()} {platform.machine()}",
        "path": str(path.resolve()), "free_gib": round(free, 1),
        "ram_gib": round(ram, 1) if ram is not None else None,
        "required_free_gib": required, "checks": checks,
        "can_build": all(checks.values()), "kvm_accessible": kvm,
        "can_run_cuttlefish": checks["linux_x86_64"] and kvm,
        "notes": "APP APK 编译不等于 ROM 编译；完整镜像与首次启动须在符合条件的 Linux 主机验证。",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--path", type=Path, default=Path.cwd())
    parser.add_argument("--source-present", action="store_true", help="源码已同步，检查额外构建空间而不是完整首次空间")
    args = parser.parse_args()
    report = assess(args.path, args.source_present)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["can_build"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
