# Yokuli OS

[![Android CI](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml)
[![Nightly Compatibility](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml)
[![Release](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml)

## 中文（主文）

Yokuli OS 当前在已经完成的 Windows Phone 8 Classic 风格应用内 Shell 上，施工离线优先、无定位也可独立规划的地图 V1。当前有效产品合同由 [WP8 三档磁贴与地图 V1 收尾规范](docs/phases/chart-wp8-refinement/CODEX_FINAL_PHASE_WP8_CHART_COMPLETION.md)和[最新 Shell 产品边界修正](docs/phases/shell-product-boundary-correction/REQUIREMENTS.md)共同组成；后者覆盖旧文档中关于 Android 桌面、最外层 Back 和横屏的冲突条款。执行真值只记录在 [EXECUTION_STATE.json](docs/phases/chart-wp8-refinement/EXECUTION_STATE.json)。

当前分支：

```text
branch: codex/shell-map-contract
phase: WP8_CHART_COMPLETION
work packages: C00–C12
status: see docs/phases/chart-wp8-refinement/EXECUTION_STATE.json
```

Stage 2.5 的 WP8 Reference measurement hash 已由仓库所有者 kuku 批准。Stage 3–10 在各自独立 commit 中完成几何／Start Document、Reducer、逐帧分页、Press/Tilt、编辑拖动、Pin/Context、全屏虚拟键导航以及持久化与应用内恢复。生产目录仍严格只有 Chart + Settings；Shell Lab 只在 debug/benchmark classpath。

正式磁贴只允许宽×高 1×1、2×2、4×2；2×1、2×4、4×4 只作为旧持久化值在边界迁移，不得回到生产 UI。地图、设置仍是唯二一级入口；地点、路线、海图包和导入轨迹都是地图内部页面。

Yokuli OS 默认沉浸式全屏且只允许竖屏；方屏仍属于适配范围，横屏不属于当前产品能力。壳内虚拟 Back／Start／Search，以及 Activity 实际收到的 Android Back 和可交付键盘／硬件事件，统一进入串行 Launcher Engine。Back 的最远终点是应用内 Shell 桌面，不结束 Yokuli；应用不注册 Android HOME／DEFAULT，也不提供 Android 桌面设置入口。

本 Phase 允许 MapLibre raster MBTiles、地点、测量、手工路线、GPX、离线覆盖与只读观测消费；仍禁止生产 NMEA/GNSS 采集、活动导航、自动舵/船网输出、Anchor/Trip/Survey 等 Runtime。模拟器结果不能替代三星方屏、物理刷新率或实船结论。

当前文档入口：

- [当前地图收尾规范](docs/phases/chart-wp8-refinement/CODEX_FINAL_PHASE_WP8_CHART_COMPLETION.md)
- [当前任务索引](docs/phases/chart-wp8-refinement/TASK_PLAN.json)
- [当前执行状态](docs/phases/chart-wp8-refinement/EXECUTION_STATE.json)
- [当前工作日志](docs/phases/chart-wp8-refinement/WORK_LOG.md)
- [Shell 产品边界修正](docs/phases/shell-product-boundary-correction/REQUIREMENTS.md)
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

Yokuli OS is completing an offline-first Map V1 on top of the existing app-agnostic WP8 Classic-style in-app Shell. The active product contract is the C00–C12 completion phase. Production tiles are exactly 1×1, 2×2, and 4×2; retired shapes are migration inputs only. Map and Settings remain the only top-level apps. Production NMEA/GNSS acquisition, active navigation, and vessel-network output remain out of scope, while physical Samsung-square and refresh-rate acceptance remain owner gates.

The portrait-only immersive shell routes virtual Back/Start/Search and deliverable Android or keyboard input through the serialized Launcher Engine. Back stops at the in-app Shell Desktop and never exits Yokuli. The app does not register Android HOME/DEFAULT or expose Android Home settings; square layouts remain supported, while landscape is outside the current product contract.
