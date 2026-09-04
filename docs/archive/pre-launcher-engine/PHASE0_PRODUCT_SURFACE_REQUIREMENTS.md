# Yokuli OS Phase 0A 生产表面需求合同

归档状态：`IMPLEMENTED_BASELINE_SUPERSEDED`。实现结果保留在起始提交 `ca84ef9`；阶段与后续施工由 Master Spec 统一定义。

> English: `IMPLEMENTED_BASELINE_SUPERSEDED`. The implementation remains in starting commit `ca84ef9`; the Master Spec now owns staging and future construction.

## 1. 生产 Registry

Release 与普通生产 composition root 只安装两个应用：

```text
chart
settings
```

当前不安装任何 shortcut。Anchor、Cockpit、Library/Logbook、Navigation、Survey、Trips、Anchorages、NMEA、Data Sources 和独立 Diagnostics 入口必须同时从 Registry、All Apps、默认 Start、路由 host 与生产依赖图中消失。删除入口不表示删除未来需求；只有对应真实垂直切片满足安装门禁后，才能通过新的 `ShellFeatureContribution` 加回。

## 2. 默认 Start

默认文档版本为 1、四列，只固定：

| Entry | WP8 尺寸 | Cell |
|---|---|---|
| `chart` | `WIDE_4X2` | `(0, 0)` |
| `settings` | `SMALL_1X1` | `(0, 2)` |

空白是用户意图，不做 top-left 全局重排。以后安装新 contribution 只进入 All Apps，不自动 pin、不覆盖用户坐标。默认文档只用于首次安装、损坏后无法修复的 fallback 和用户明确“恢复默认”。

## 3. 假数据禁令

生产 `src/main` 不得包含未标注的假船位、航速、航向、目的地、锚警、测深、Trip、NMEA 或设备健康事实。Chart 只开放 Browse：

- 已配置 Maps：显示真实 Google Map，并说明船位尚未启用；
- 未配置 Maps：永久显示 `DEMO MAP / 演示地图` 与 `地图未配置`，fallback 不绘制船、路线或航海状态；
- Feature App Bar 不显示 Home，也不显示 Navigate/Anchor/Survey 模式按钮。

Start 的 Chart Tile 只显示真实配置事实；Settings Tile 只显示当前真实主题摘要。Status Strip 只显示系统时间和真实电池信息。

## 4. Settings

用户名为 `SETTINGS / 设置`。当前只允许：Appearance、Start Screen、Map、Language、About & Diagnostics。它们分别展示或修改真实主题、当前桌面文档、Google Maps 配置、应用语言、版本/提交/构建变体。Connections、Sources、Devices、Safety、NMEA、Phone Sensors、Storage/Runtime health 不得以 fixture 页面出现。

## 5. 模块和 Debug 隔离

生产依赖包含 `feature:chart` 与 `feature:settings`，不包含 `feature:cockpit`、`feature:library` 或旧 `feature:system`。`feature:shell-lab` 只通过 `debugImplementation` 进入 debug APK；Release 依赖和 manifest 均不得包含它。Lab 中的压力数据必须永久显示 `DEMO`。

## 6. 安装门禁

新入口进入生产 Registry 前必须有真实用户故事、成功/取消/错误/权限路径、恢复策略、真实数据 owner、不可变 UiState/UiAction、中英文、方屏/大字体与端到端测试。Tile 不启动 runtime，关闭 UI 不误停 runtime。不满足时只能存在于分支、Debug Shell Lab、文档或 roadmap。

## 7. 自动合同

`.github/scripts/test_phase0_surface_contract.py` 检查模块图、贡献式 Registry、两个默认 entry、WP8 标准尺寸、fixture 禁令、Chart/Settings 边界和 Debug Lab 隔离。JVM 与 Activity tests 检查空间文档、恢复策略和实际生产 UI。

## English translation

Phase 0A exposes only `chart` and `settings` through the production contribution graph, with no production shortcut. The default four-column desktop pins a WP8 Wide Chart at `(0,0)` and a WP8 Small Settings tile at `(0,2)`, preserving intentional whitespace. Production main sources must not contain unlabeled marine fixtures. Chart exposes Browse only; a missing Maps key shows a permanent `DEMO MAP` and unconfigured message without a fake vessel or route. Settings exposes only implemented Appearance, Start Screen, Map, Language, and About/Diagnostics facts. Cockpit, Library, and the old System module are removed. Shell Lab is debug-only and its generated content is permanently labeled `DEMO`. New entries enter production only with complete real user flows, ownership, recovery, bilingual UI, and end-to-end evidence.
