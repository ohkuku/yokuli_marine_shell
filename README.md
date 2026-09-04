# Yokuli OS

[![Android CI](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml)
[![Nightly Compatibility](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml)
[![Release](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml)

## 中文（主文）

Yokuli OS 当前只施工一个与应用解耦、可验证、可持久化并能高帧率运行的 Windows Phone 8 Classic 风格 Launcher Shell。唯一施工规范是 [Launcher Shell Engine Master Construction Spec](docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md)。必须按 Stage 0–11 逐阶段完成；每个 Stage 通过 Gate、提交、报告后立即停止，等待人工审核。

当前分支：

```text
branch: codex/launcher-engine-stage2.5-rebuild
stage: 2.5 — WP8 Reference Acquisition & Human Approval
starting tag: launcher-engine-stage2-approved-v1
starting SHA: 5386da0575046f1f9a59742a4a0f5c78523fa5e6
approval: HUMAN_REVIEWED / APPROVED
```

Stage 2 已由仓库所有者批准：annotated tag `launcher-engine-stage2-approved-v1` 指向 `5386da0…`，批准 evidence 是 GitHub Actions run `33864829489`。Stage 2.5 从这个不可混淆的批准点开始，只执行 WP8 Reference 获取、测量与人工审批 Gate。

本阶段只使用仓库所有者提供的 `kuku.mp4` WP8.1 模拟器录屏作为视觉来源，保存完整时间戳帧并建立内容哈希、几何／运动测量和缺口清单。仓库所有者 kuku 已批准 canonical measurement hash，状态为 `HUMAN_REVIEWED`；Stage 3 尚未开始。Yokuli OS 后续默认沉浸式全屏并使用壳内虚拟 Back／Start／Search 的决定已经记录，但本阶段不改生产 runtime。

在 Shell Engine 全部完成人工验收前，禁止继续接入 GPS、NMEA、Anchor、Trip、Navigation、Survey、OpenSeaMap、MBTiles、AIS、Weather、Tide 或海事前台 Runtime。

当前文档入口：

- [施工主文档](docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md)
- [WP8 Reference Lab](docs/reference/wp8/README.md)
- [Launcher Engine TDD 规范](docs/TDD_PLAYBOOK.md)
- [当前 Stage TDD 日志](docs/TDD_LOG.md)
- [Stage 0 正式报告](docs/stages/stage-0/REPORT.md)
- [Stage 1 产品表面审计](docs/stages/stage-1/PRODUCT_SURFACE_AUDIT.md)
- [Stage 1 正式报告](docs/stages/stage-1/REPORT.md)
- [Stage 2 架构边界审计](docs/stages/stage-2/ARCHITECTURE_AUDIT.md)
- [Stage 2 正式报告](docs/stages/stage-2/REPORT.md)
- [Stage 2.5 正式报告](docs/stages/stage-2.5/REPORT.md)
- [沉浸式全屏与虚拟实体键决定](docs/stages/stage-2.5/FULLSCREEN_NAVIGATION_DECISION.md)
- [历史需求与 Slice 归档](docs/archive/pre-launcher-engine/README.md)
- [GitHub 交付](docs/GITHUB_DELIVERY.md)
- [本地密钥保险库](docs/SECRETS_MANAGEMENT.md)

当前本地合同：

```text
python3 -m pip install --requirement .github/requirements/stage0-schema.txt
python3 .github/scripts/test_launcher_stage0_contract.py
python3 .github/scripts/test_launcher_stage1_contract.py
python3 .github/scripts/test_launcher_stage2_contract.py
python3 .github/scripts/test_launcher_stage25_contract.py
python3 .github/scripts/validate_wp8_reference.py --require-human-review
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-release-product-surface.sh
```

完整构建 Gate 仍由现有 Android CI 执行。未运行的 Golden、Macrobenchmark、刷新率、Samsung 方屏和实船项目必须写 `NOT_YET_MEASURED` 或 `UNVERIFIED_HARDWARE`。

## English translation

Yokuli OS is currently constructing only an app-agnostic, verifiable, durable, high-frame-rate WP8 Classic-style Launcher Shell. The owner approved Stage 2 commit `5386da0…` with evidence run `33864829489`; annotated tag `launcher-engine-stage2-approved-v1` is the exact Stage 2.5 starting point.

Stage 2.5 uses only the owner-supplied WP8.1 emulator recording as visual evidence, with exact full frames, content hashes, geometry and observable-motion measurements, and explicit evidence gaps. Repository owner kuku approved the canonical measurement hash, so it is `HUMAN_REVIEWED`; Stage 3 has not started. The future game-like immersive host and shell-owned virtual Back/Start/Search requirement is recorded without changing production runtime.
