# Yokuli OS

[![Android CI](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml)
[![Nightly Compatibility](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml)
[![Release](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml)

## 中文（主文）

Yokuli OS 当前只施工一个与应用解耦、可验证、可持久化的 Windows Phone 8 Classic 风格 Launcher Shell。唯一施工规范是 [Launcher Shell Engine Master Construction Spec](docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md)。Stage 0–10 已按独立 Gate 和 commit 完成；Stage 11 自动化部分正在最终收口，真机性能与视觉结论仍必须人工批准。

当前分支：

```text
branch: codex/launcher-engine-stage11
stage: 11 — Performance & Fidelity Gate
starting SHA: 1192d0bf9cee42266fe8430fd7ba59c424c03c56
status: COMPLETE_PROVISIONAL
```

Stage 2.5 的 WP8 Reference measurement hash 已由仓库所有者 kuku 批准。Stage 3–10 在各自独立 commit 中完成几何／Start Document、Reducer、逐帧分页、Press/Tilt、编辑拖动、Pin/Context、全屏虚拟键导航以及持久化／HOME Recovery。生产目录仍严格只有 Chart + Settings；Shell Lab 只在 debug/benchmark classpath。

Stage 11 提供 Macrobenchmark、Baseline/Startup Profile、60 Tile 压测、API 34/36 回归路径、320×320／360×360 模拟方屏以及内容寻址的 Golden 候选。模拟器结果只用于趋势；`Golden`、真实三星方屏、物理 60/90/120 Hz、WP8 视觉手感和输入延迟均不得由 Codex 宣称通过。

Yokuli OS 默认沉浸式全屏。壳内虚拟 Back／Start／Search，以及 Activity 实际收到的 Android Back 和可交付键盘／硬件事件，统一进入串行 Launcher Engine；Android 保留的物理 HOME 不能由普通应用可靠拦截，Home flavor 通过 HOME/DEFAULT intent 承接启动语义。

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
- [Stage 11 自动化与人工待验报告](docs/stages/stage-11/REPORT.md)
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
python3 .github/scripts/test_launcher_stage11_contract.py
python3 .github/scripts/validate_wp8_reference.py --require-human-review
python3 .github/scripts/validate_stage11_fidelity.py
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-release-product-surface.sh
```

完整构建 Gate 仍由 Android CI 执行。Golden 候选是 `CANDIDATE_PENDING_HUMAN_REVIEW`；刷新率、Samsung 方屏和物理 WP8 设备保持 `UNVERIFIED_HARDWARE`／`PENDING_HUMAN_REVIEW`。

## English translation

Yokuli OS is constructing an app-agnostic, verifiable, durable WP8 Classic-style Launcher Shell. Stages 0–10 are separated by commits and gates. Stage 11 is `COMPLETE_PROVISIONAL`: automated profiles, emulator trends, simulated-square coverage, and content-addressed visual candidates are delivered, while Golden acceptance, physical refresh-rate performance, WP8 feel, and Samsung-square hardware remain human/device gates.

The immersive shell routes virtual Back/Start/Search and deliverable Android or keyboard input through the serialized Launcher Engine. Android's reserved physical HOME key is not falsely claimed as interceptable; the Home flavor integrates through the platform HOME/DEFAULT intent contract.
