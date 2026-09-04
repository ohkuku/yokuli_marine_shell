# Yokuli OS

[![Android CI](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml)
[![Nightly Compatibility](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml)
[![Release](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml)

## 中文（主文）

Yokuli OS 当前只施工一个与应用解耦、可验证、可持久化并能高帧率运行的 Windows Phone 8 Classic 风格 Launcher Shell。唯一施工规范是 [Launcher Shell Engine Master Construction Spec](docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md)。必须按 Stage 0–11 逐阶段完成；每个 Stage 通过 Gate、提交、报告后立即停止，等待人工审核。

当前分支：

```text
branch: codex/launcher-engine
stage: 1 — Product Surface Reduction
starting tag: launcher-engine-stage0-approved-v1.1
starting SHA: 16b0e5cd1c8fa2e5f4b78aefadf3fa7c012698b2
approval: PENDING_HUMAN_REVIEW
```

Stage 0 已由仓库所有者批准：annotated tag `launcher-engine-stage0-approved-v1.1` 指向 `16b0e5c…`，批准 evidence 是 GitHub Actions run `33854599910`。Stage 1 必须且已经从这个不可混淆的批准点开始，只审计 Product Surface Reduction。

`ca84ef9` 已提前包含 Chart + Settings 候选产品面，但不继承为 Stage 1 批准。本阶段重新用静态合同、API 34 精确节点数和 standalone/HOME 两个 release APK 二进制检查证明：Release Start 与 All Apps 恰好只有 Chart + Settings，Chart 只开放 Browse，没有 Coming Soon、未来路线图、船位状态或假海事值，Shell Lab 只在 debug。地图只报告是否配置了非占位密钥，不宣称服务已验证或就绪。

在 Shell Engine 全部完成人工验收前，禁止继续接入 GPS、NMEA、Anchor、Trip、Navigation、Survey、OpenSeaMap、MBTiles、AIS、Weather、Tide 或海事前台 Runtime。

当前文档入口：

- [施工主文档](docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md)
- [WP8 Reference Lab](docs/reference/wp8/README.md)
- [Launcher Engine TDD 规范](docs/TDD_PLAYBOOK.md)
- [当前 Stage TDD 日志](docs/TDD_LOG.md)
- [Stage 0 正式报告](docs/stages/stage-0/REPORT.md)
- [Stage 1 产品表面审计](docs/stages/stage-1/PRODUCT_SURFACE_AUDIT.md)
- [Stage 1 正式报告](docs/stages/stage-1/REPORT.md)
- [历史需求与 Slice 归档](docs/archive/pre-launcher-engine/README.md)
- [GitHub 交付](docs/GITHUB_DELIVERY.md)
- [本地密钥保险库](docs/SECRETS_MANAGEMENT.md)

当前本地合同：

```text
python3 -m pip install --requirement .github/requirements/stage0-schema.txt
python3 .github/scripts/test_launcher_stage0_contract.py
python3 .github/scripts/test_launcher_stage1_contract.py
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-release-product-surface.sh
```

完整构建 Gate 仍由现有 Android CI 执行。未运行的 Golden、Macrobenchmark、刷新率、Samsung 方屏和实船项目必须写 `NOT_YET_MEASURED` 或 `UNVERIFIED_HARDWARE`。

## English translation

Yokuli OS is currently constructing only an app-agnostic, verifiable, durable, high-frame-rate WP8 Classic-style Launcher Shell. The owner approved Stage 0 commit `16b0e5c…` with evidence run `33854599910`; annotated tag `launcher-engine-stage0-approved-v1.1` is the exact Stage 1 starting point.

Stage 1 revalidates the pre-existing product-surface candidate instead of inheriting its result. Static contracts, the exact API 34 All Apps count, and binary inspection of both standalone and HOME release APKs prove that release contains exactly Chart and Settings, Chart is Browse-only, placeholder and roadmap copy are absent, and Shell Lab remains debug-only. Map state reports key configuration only, not operational readiness. Stage 2 is not started.
