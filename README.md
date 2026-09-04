# Yokuli OS

[![Android CI](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml)
[![Nightly Compatibility](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml)
[![Release](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml)

## 中文（主文）

Yokuli OS 当前只施工一个与应用解耦、可验证、可持久化并能高帧率运行的 Windows Phone 8 Classic 风格 Launcher Shell。唯一施工规范是 [Launcher Shell Engine Master Construction Spec](docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md)。必须按 Stage 0–11 逐阶段完成；每个 Stage 通过 Gate、提交、报告后立即停止，等待人工审核。

当前分支：

```text
branch: codex/launcher-engine
stage: 0 correction — Reference & Baseline Contracts
starting SHA: ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7
correction starting SHA: 98121412893d5331b22d4327463794993a4a4eff
approval: PENDING_HUMAN_REVIEW
```

仓库所有者明确要求从最新提交 `ca84ef9` 开始，覆盖 Master 附件中的旧 reviewed SHA。人工审查后，Master 在 Stage 0 获批前升级为 v1.1：保留 v1.0 哈希，并在 Stage 2 与 Geometry 之间加入必须达到 `HUMAN_REVIEWED` 的 Stage 2.5 Reference Gate。覆盖关系记录在 [Stage 0 baseline lock](docs/stages/stage-0/BASELINE_LOCK.json)、[实现对账](docs/stages/stage-0/BASELINE_RECONCILIATION.md)、[WP8 Reference Lab](docs/reference/wp8/README.md) 和 [TDD 日志](docs/TDD_LOG.md)。

`ca84ef9` 已经是冻结实现基线：生产面只有 Chart 与 Settings；Google Maps Adapter、双语资源、standalone/home 构建、CI，以及第一版空间文档/几何代码均保留。但这些既有实现不自动等于 Master 后续 Stage 已通过。Stage 0 不修改 UI、Registry、Google Map 或 Feature 行为，只锁定规范、Reference schema、截图/Golden/Artifact 所有权和 CI 合同。

在 Shell Engine 全部完成人工验收前，禁止继续接入 GPS、NMEA、Anchor、Trip、Navigation、Survey、OpenSeaMap、MBTiles、AIS、Weather、Tide 或海事前台 Runtime。

当前文档入口：

- [施工主文档](docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md)
- [WP8 Reference Lab](docs/reference/wp8/README.md)
- [Launcher Engine TDD 规范](docs/TDD_PLAYBOOK.md)
- [当前 Stage TDD 日志](docs/TDD_LOG.md)
- [Stage 0 正式报告](docs/stages/stage-0/REPORT.md)
- [历史需求与 Slice 归档](docs/archive/pre-launcher-engine/README.md)
- [GitHub 交付](docs/GITHUB_DELIVERY.md)
- [本地密钥保险库](docs/SECRETS_MANAGEMENT.md)

Stage 0 本地合同：

```text
python3 -m pip install --requirement .github/requirements/stage0-schema.txt
python3 .github/scripts/test_launcher_stage0_contract.py
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-ci-contract.sh
```

完整构建 Gate 仍由现有 Android CI 执行。未运行的 Golden、Macrobenchmark、刷新率、Samsung 方屏和实船项目必须写 `NOT_YET_MEASURED` 或 `UNVERIFIED_HARDWARE`。

## English translation

Yokuli OS is currently constructing only an app-agnostic, verifiable, durable, high-frame-rate WP8 Classic-style Launcher Shell. The linked Master Construction Spec is the sole stage authority. The owner selected commit `ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7` as the starting baseline. Before Stage 0 approval, Master v1.1 retained the v1.0 hash and added mandatory Stage 2.5 acquisition and human approval before geometry.

The frozen baseline already contains the Chart-and-Settings production reduction, Google Maps adapter, bilingual resources, standalone/home variants, CI, and an initial spatial-document foundation. Existing code is not treated as proof that later Master stages passed. Stage 0 correction changes documentation and reference/CI contracts only, and remains `PENDING_HUMAN_REVIEW`. No marine capability may be added until the full Launcher Engine passes human review, and all unrun reference, golden, benchmark, refresh-rate, square-device, and vessel checks remain explicitly unmeasured or unverified.
