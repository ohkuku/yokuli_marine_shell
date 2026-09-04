# Yokuli OS

[![Android CI](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml)
[![Nightly Compatibility](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml)
[![Release](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml)

## 中文（主文）

Yokuli OS 当前只施工一个与应用解耦、可验证、可持久化并能高帧率运行的 Windows Phone 8 Classic 风格 Launcher Shell。唯一施工规范是 [Launcher Shell Engine Master Construction Spec](docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md)。必须按 Stage 0–11 逐阶段完成；每个 Stage 通过 Gate、提交、报告后立即停止，等待人工审核。

当前分支：

```text
branch: codex/launcher-engine
stage: 2 — Engine Contract Extraction
starting tag: launcher-engine-stage1-approved-v1
starting SHA: df371fbfcb4cd467bccc43dd850e23d9bd7d0e85
approval: PENDING_HUMAN_REVIEW
```

Stage 1 已由仓库所有者批准：annotated tag `launcher-engine-stage1-approved-v1` 指向 `df371fb…`，批准 evidence 是 GitHub Actions run `33861223067`。Stage 2 必须且已经从这个不可混淆的批准点开始，只执行 Engine Contract Extraction。

本阶段把 Launcher 从 Android、Compose 与 Marine 应用模型中抽离：`shell-contract` 和 `shell-engine` 是 Kotlin/JVM 边界，Feature 只贡献 opaque ID/token catalog，Compose host 与 Android adapter 位于外层，`app-shell` 只组合 Chart + Settings。现有 UI、手势与产品面保持不变；Stage 2.5 WP8 Reference 获取尚未开始。

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
- [历史需求与 Slice 归档](docs/archive/pre-launcher-engine/README.md)
- [GitHub 交付](docs/GITHUB_DELIVERY.md)
- [本地密钥保险库](docs/SECRETS_MANAGEMENT.md)

当前本地合同：

```text
python3 -m pip install --requirement .github/requirements/stage0-schema.txt
python3 .github/scripts/test_launcher_stage0_contract.py
python3 .github/scripts/test_launcher_stage1_contract.py
python3 .github/scripts/test_launcher_stage2_contract.py
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-release-product-surface.sh
```

完整构建 Gate 仍由现有 Android CI 执行。未运行的 Golden、Macrobenchmark、刷新率、Samsung 方屏和实船项目必须写 `NOT_YET_MEASURED` 或 `UNVERIFIED_HARDWARE`。

## English translation

Yokuli OS is currently constructing only an app-agnostic, verifiable, durable, high-frame-rate WP8 Classic-style Launcher Shell. The owner approved Stage 1 commit `df371fb…` with evidence run `33861223067`; annotated tag `launcher-engine-stage1-approved-v1` is the exact Stage 2 starting point.

Stage 2 extracts pure Kotlin contract and engine modules, opaque IDs/tokens, feature catalog contributions, a Compose internal-host boundary, and an Android host adapter. Only `app-shell` composes Chart and Settings. Existing UI and interactions remain unchanged; Stage 2.5 reference acquisition is not started.
