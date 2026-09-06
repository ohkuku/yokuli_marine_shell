# Yokuli OS

[![Android CI](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml)
[![Nightly Compatibility](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml)
[![Release](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml)

## 中文（主文）

Yokuli OS 当前同时包含 Windows Phone 8 Classic 风格应用内 Shell、离线优先地图、统一 Data 应用与独立海图库。Data 把 NMEA 输入、来源选择、真实流向与有界诊断组织在一个产品里，底层仍复用进程持有、类型化且有界的 marine-data runtime；Chart Library 管理只读外部海图来源、目录和验证，Chart 只负责选择与显示。当前有效合同由 [OS Redesign 产品合同](docs/phases/os-redesign/PRODUCT_ENGINEERING_CONTRACT.md)、[Chart Library 产品合同](docs/phases/chart-library/REQUIREMENTS.md)、[NMEA_SOURCES 产品合同](docs/phases/nmea-sources/REQUIREMENTS.md)和[最新 Shell 产品边界修正](docs/phases/shell-product-boundary-correction/REQUIREMENTS.md)共同组成。历史 Stage 与阶段报告保留为证据，不覆盖后续明确的产品演进。

当前分支：

```text
branch: codex/shell-map-contract
phase: OS Redesign CI-first
work package: W12
status: W12 implementation candidate; hosted CI and physical review remain separate
```

Stage 2.5 的 WP8 Reference measurement hash 已由仓库所有者 kuku 批准。Stage 3–10 在各自独立 commit 中完成几何／Start Document、Reducer、逐帧分页、Press/Tilt、编辑拖动、Pin/Context、全屏虚拟键导航以及持久化与应用内恢复。当前生产 All Apps 精确为 Chart、Settings、Data、Chart Library、Navigation 五项；全新 Start Document 仍只放 Chart 与 Settings。Shell Lab 只在 debug/benchmark classpath。

正式磁贴只允许宽×高 1×1、2×2、4×2；2×1、2×4、4×4 只作为旧持久化值在边界迁移，不得回到生产 UI。Navigation 使用同一持久库管理航点、航线、GPX 导入与活动航行，并只通过明确的内容 handoff 让 Chart 显示指定航线；海图文件夹、单文件来源、受管副本与验证只在 Chart Library 维护。Data 内部的 Inputs 管理连接，Sources 保存可解释的 OS 选源决定，Overview、Flow 与 Diagnostics 只展示真实运行时证据。

Yokuli OS 默认沉浸式全屏且只允许竖屏；方屏仍属于适配范围，横屏不属于当前产品能力。壳内虚拟 Back／Start／Search，以及 Activity 实际收到的 Android Back 和可交付键盘／硬件事件，统一进入串行 Launcher Engine。Back 的最远终点是应用内 Shell 桌面，不结束 Yokuli；应用不注册 Android HOME／DEFAULT，也不提供 Android 桌面设置入口。

本 Phase 在保留地图能力的基础上实现真实 NMEA 0183 TCP／UDP 输入、手机系统定位候选、统一来源目录、按数据语义选源和本地活动导航；仍禁止 NMEA 输出／转发、自动舵/船网控制、Anchor/Trip/Survey Runtime。模拟器结果不能替代三星方屏、真机 GNSS、OEM 后台行为或实船结论。

当前 Chart 有两条解耦渲染链路：配置了 `GOOGLE_MAPS_ANDROID_API_KEY` 时，未选择本地海图会显示 Google 在线底图；用户选择本地海图后由 MapLibre 离线渲染。密钥存在只表示“已配置”，不证明 API 授权、账单、签名限制、网络或图块加载已经成功；本地海图覆盖状态也不会被 Google 底图冒充。

当前文档入口：

- [当前地图收尾规范](docs/phases/chart-wp8-refinement/CODEX_FINAL_PHASE_WP8_CHART_COMPLETION.md)
- [Chart Library 产品合同](docs/phases/chart-library/REQUIREMENTS.md)
- [Chart Library 当前支持矩阵](docs/phases/chart-library/CL12_SUPPORT_MATRIX.md)
- [Chart Library CL12 报告](docs/phases/chart-library/CL12_REPORT.md)
- [OS Redesign Product & Engineering Contract](docs/phases/os-redesign/PRODUCT_ENGINEERING_CONTRACT.md)
- [OS Redesign W01 合同](docs/phases/os-redesign/work-packages/W01_PRODUCT_ENGINEERING_CONTRACT.md)
- [OS Redesign W12 Navigation 合同](docs/phases/os-redesign/work-packages/W12_PRODUCT_ENGINEERING_CONTRACT.md)
- [OS Redesign 执行状态](docs/phases/os-redesign/EXECUTION_STATE.json)
- [NMEA_SOURCES 产品合同](docs/phases/nmea-sources/REQUIREMENTS.md)
- [NMEA_SOURCES P0 基线](docs/implementation/NMEA_SOURCES_P0_BASELINE.md)
- [NMEA_SOURCES P1 字段映射](docs/implementation/NMEA_SOURCES_P1_FIELD_MAPPING.md)
- [NMEA_SOURCES P1 候选报告](docs/phases/nmea-sources/P1_REPORT.md)
- [NMEA_SOURCES P2 报告](docs/phases/nmea-sources/P2_REPORT.md)
- [NMEA_SOURCES P3 报告](docs/phases/nmea-sources/P3_REPORT.md)
- [NMEA_SOURCES P4 报告](docs/phases/nmea-sources/P4_REPORT.md)
- [NMEA_SOURCES P5 报告](docs/phases/nmea-sources/P5_REPORT.md)
- [NMEA_SOURCES P6 报告](docs/phases/nmea-sources/P6_REPORT.md)
- [NMEA_SOURCES P7 报告](docs/phases/nmea-sources/P7_REPORT.md)
- [NMEA_SOURCES 最终实施报告](docs/implementation/NMEA_SOURCES_FINAL_REPORT.md)
- [NMEA_SOURCES TDD 矩阵](docs/implementation/NMEA_SOURCES_TDD_MATRIX.md)
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
python3 .github/scripts/test_osr_w12_contract.py
python3 .github/scripts/test_nmea_sources_p0_contract.py
python3 .github/scripts/test_nmea_sources_p1_contract.py
python3 .github/scripts/test_nmea_sources_p2_contract.py
python3 .github/scripts/test_nmea_sources_p3_contract.py
python3 .github/scripts/test_nmea_sources_p4_contract.py
python3 .github/scripts/test_nmea_sources_p5_contract.py
python3 .github/scripts/test_nmea_sources_p6_contract.py
python3 .github/scripts/test_nmea_sources_p7_contract.py
python3 .github/scripts/test_chart_library_cl12_contract.py
python3 .github/scripts/test_google_maps_configuration_evidence.py
python3 .github/scripts/validate_wp8_reference.py --require-human-review
python3 .github/scripts/validate_stage11_fidelity.py
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-release-product-surface.sh
```

P7 本地完整 Gate 已通过；Android CI 会在 push 后重新执行托管门禁并生成可下载候选包。Golden 候选是 `CANDIDATE_PENDING_HUMAN_REVIEW`；刷新率、Samsung 方屏和物理 WP8 设备保持 `UNVERIFIED_HARDWARE`／`PENDING_HUMAN_REVIEW`。

## English translation

Yokuli OS currently combines its WP8 Classic in-app Shell with offline-first Chart, unified Data, independent Chart Library, and a real Navigation app. The production All Apps surface is exactly Chart, Settings, Data, Chart Library, and Navigation; the default Start document remains Chart + Settings. Navigation manages the shared waypoint/route library, explicit active-navigation sessions, and route-content handoff to Chart. Data contains connection inputs, explainable source selection, actual runtime flow, and bounded diagnostics. Hosted CI and physical review remain separate; NMEA output/forwarding, autopilot, and vessel-network control remain out of scope.

The portrait-only immersive shell routes virtual Back/Start/Search and deliverable Android or keyboard input through the serialized Launcher Engine. Back stops at the in-app Shell Desktop and never exits Yokuli. The app does not register Android HOME/DEFAULT or expose Android Home settings; square layouts remain supported, while landscape is outside the current product contract.

Chart has two decoupled render adapters. With `GOOGLE_MAPS_ANDROID_API_KEY` configured, Google supplies the connected base map when no local chart is selected; MapLibre renders user-selected local charts offline. Key presence means configured only—it does not prove authorization, billing, application restrictions, connectivity, or tile delivery, and it never counts as offline coverage.
