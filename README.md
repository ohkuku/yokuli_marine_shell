# Yokuli OS

[![Android CI](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml)
[![Nightly Compatibility](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml)
[![Release](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml)

## 中文（主文）

Yokuli OS 已在 Windows Phone 8 Classic 风格应用内 Shell 和离线优先地图 V1 上完成 `NMEA_SOURCES` P0–P7 本地机器门禁。NMEA Input 与 Data Sources 是两个独立 Shell App，共享一套进程持有、类型化且有界的 marine-data runtime。当前有效合同由 [NMEA_SOURCES 产品合同](docs/phases/nmea-sources/REQUIREMENTS.md)、[P0 实施基线](docs/implementation/NMEA_SOURCES_P0_BASELINE.md)和[最新 Shell 产品边界修正](docs/phases/shell-product-boundary-correction/REQUIREMENTS.md)共同组成。它们覆盖旧文档中“只允许 Chart + Settings／禁止生产 NMEA/GNSS”以及 Android 桌面、最外层 Back 和横屏的冲突条款；历史 Stage 与 Map C00–C12 报告保持原样，不能作为当前产品面的可执行合同。

当前分支：

```text
branch: codex/shell-map-contract
phase: NMEA_SOURCES
work packages: P0–P7
status: P0–P7 local machine gates passed; hosted CI and physical review remain separate
```

Stage 2.5 的 WP8 Reference measurement hash 已由仓库所有者 kuku 批准。Stage 3–10 在各自独立 commit 中完成几何／Start Document、Reducer、逐帧分页、Press/Tilt、编辑拖动、Pin/Context、全屏虚拟键导航以及持久化与应用内恢复。当前生产 All Apps 精确为 Chart、Settings、NMEA Input、Data Sources 四项；全新 Start Document 仍只放 Chart 与 Settings。Shell Lab 只在 debug/benchmark classpath。

正式磁贴只允许宽×高 1×1、2×2、4×2；2×1、2×4、4×4 只作为旧持久化值在边界迁移，不得回到生产 UI。地点、路线、海图包和导入轨迹仍是地图内部页面；NMEA Input 只管理连接，Data Sources 只展示实际接收结果并保存 OS 选源决定。

Yokuli OS 默认沉浸式全屏且只允许竖屏；方屏仍属于适配范围，横屏不属于当前产品能力。壳内虚拟 Back／Start／Search，以及 Activity 实际收到的 Android Back 和可交付键盘／硬件事件，统一进入串行 Launcher Engine。Back 的最远终点是应用内 Shell 桌面，不结束 Yokuli；应用不注册 Android HOME／DEFAULT，也不提供 Android 桌面设置入口。

本 Phase 在保留地图能力的基础上实现真实 NMEA 0183 TCP／UDP 输入、手机系统定位候选、统一来源目录和按数据语义选源；仍禁止 NMEA 输出／转发、活动导航、自动舵/船网控制、Anchor/Trip/Survey Runtime。模拟器结果不能替代三星方屏、真机 GNSS、OEM 后台行为或实船结论。

当前 Chart 使用离线 MapLibre，并只渲染用户导入的兼容 MBTiles 海图包。GitHub Actions 中配置 Google Maps key 不会让地图出现，因为当前生产构建没有 Google Maps SDK／在线图块消费链路；没有导入本地海图时应显示真实空态，而不是假装在线地图已就绪。

当前文档入口：

- [当前地图收尾规范](docs/phases/chart-wp8-refinement/CODEX_FINAL_PHASE_WP8_CHART_COMPLETION.md)
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
python3 .github/scripts/test_nmea_sources_p0_contract.py
python3 .github/scripts/test_nmea_sources_p1_contract.py
python3 .github/scripts/test_nmea_sources_p2_contract.py
python3 .github/scripts/test_nmea_sources_p3_contract.py
python3 .github/scripts/test_nmea_sources_p4_contract.py
python3 .github/scripts/test_nmea_sources_p5_contract.py
python3 .github/scripts/test_nmea_sources_p6_contract.py
python3 .github/scripts/test_nmea_sources_p7_contract.py
python3 .github/scripts/validate_wp8_reference.py --require-human-review
python3 .github/scripts/validate_stage11_fidelity.py
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-release-product-surface.sh
```

P7 本地完整 Gate 已通过；Android CI 会在 push 后重新执行托管门禁并生成可下载候选包。Golden 候选是 `CANDIDATE_PENDING_HUMAN_REVIEW`；刷新率、Samsung 方屏和物理 WP8 设备保持 `UNVERIFIED_HARDWARE`／`PENDING_HUMAN_REVIEW`。

## English translation

Yokuli OS has completed the local P0–P7 machine gates of `NMEA_SOURCES`. The current production All Apps surface is exactly Chart, Settings, NMEA Input, and Data Sources; the default Start document remains Chart + Settings. Both new apps use one typed, bounded, process-owned runtime. Hosted CI and physical review remain separate; NMEA output/forwarding, active navigation, autopilot, and vessel-network control remain out of scope.

The portrait-only immersive shell routes virtual Back/Start/Search and deliverable Android or keyboard input through the serialized Launcher Engine. Back stops at the in-app Shell Desktop and never exits Yokuli. The app does not register Android HOME/DEFAULT or expose Android Home settings; square layouts remain supported, while landscape is outside the current product contract.

Chart currently renders imported offline MBTiles through MapLibre. A Google Maps Actions secret is intentionally not consumed by this production path and therefore cannot make an online basemap appear.
