# Yokuli Shell Engine Phase 0 基础需求合同

归档状态：`SUPERSEDED_BY_MASTER`。其中实现证据属于 `ca84ef9` 基线，旧 S0–S8 编号不再是当前施工顺序。

> English: `SUPERSEDED_BY_MASTER`. Its implementation evidence belongs to baseline `ca84ef9`; the old S0–S8 numbering is no longer the active construction order.

## 1. 技术与所有权

Shell 使用原生 Android 与 Jetpack Compose，不使用 Unity。`core:shell-engine` 只拥有几何、空间布局、交互状态、事务和 store 端口；`core:shell` 拥有 contribution registry 与无 UI 的导航 reducer；`app-shell` 是 composition root。任何 shell 模块不得拥有 Google Map、GPS、NMEA、Anchor、Trip、Navigation 或 Survey runtime。

## 2. WP8 几何

标准 Tile 只有：

```text
SMALL_1X1
MEDIUM_2X2
WIDE_4X2
```

不把 2×1 或 `HERO` 冒充 WP8 尺寸。`WpStartGeometry` 分开计算左右外边距与内部 seam：初始校准取可用宽度 5% 和 2.3%，再求四列 cell；所有结果在物理像素上取整。320 方屏与 360 portrait 是首批固定 viewport，参考图只比较几何、节奏和层级，不复制 Microsoft 资产。

## 3. 空间桌面文档

`DesktopDocument(version, columns, placements)` 是事实来源。每个 `TilePlacement` 持有稳定 `TileId`、`LauncherEntryId`、标准 `TileSize` 和显式 `GridCell(column,row)`。未知 entry 在恢复时移除并记录 incident；越界或重叠先尝试最小修复，无法修复才回默认。合法空白不得被压缩。

所有改变通过 `LayoutTransaction(before, after, reason)` 表达，proposed 状态不能持久化。Store 基础端口为 `ShellStore`、`DesktopLayoutStore` 和 `ShellPreferencesStore`；具体持久化实现属于后续 S2 完整切片。

## 4. 单一交互状态

`StartInteractionState` 至少表达 Idle、Paging、EditIdle、Dragging、Resizing、Settling 与 Launching。任一时刻只有一个主交互状态。Paging 保留 0–1 progress 和 velocity；Dragging 保留 pointer、grab offset、visual offset、proposed document 与 auto-scroll 速度。

## 5. 层级清理

返回 Start 是 Shell 导航行为，不是 Feature action。Chart 与 Settings 不包含 Home action。All Apps 不显示 `core app`、`shortcut` 或 `hold to pin` 等内部术语；长按菜单只能针对已安装 entry 提供固定/取消固定和应用信息。图标使用统一 Canvas/Vector `MarineIcon`，禁止依赖 Unicode/emoji fallback。

## 6. 本轮边界与后续 Red

本轮完成 S0 reference token、S1 hierarchy cleanup 和 S2 状态模型/端口基础。以下仍为 Phase 0B，不在本轮伪报完成：

- S3：Start ↔ Apps 逐帧 HorizontalPager、速度 settle、方向锁与中断；
- S4/S5：实时碰撞、邻居让位、pending pin、auto-scroll、cancel/undo；
- S6：交互 motion、Tile transition plane 与 reduced-motion 完整接管；
- S7：Macrobenchmark、Baseline/Startup Profile 与 release-like jank 门禁；
- S8：HOME `onNewIntent`、Safe Mode、crash-loop recovery 和 Android Settings 逃生入口。

相关测试名称现在进入规范和待实现清单，但不得用 Debug 手感或普通 Compose UI test 冒充性能测量。

## 7. 当前验收

本轮必须通过：模型/Registry/几何/文档修复 JVM tests、Phase 0 静态合同、双语资源合同、lint、standalone/home debug assembly、API 34 Activity stories。Golden 与 Macrobenchmark 若未运行，完成报告必须写 `NOT YET MEASURED`。

## English translation

Yokuli Shell remains native Android and Compose; Unity is not used. This slice implements the Phase 0 document's explicit first-round scope: S0 geometry tokens, S1 hierarchy cleanup, and the S2 spatial document, interaction-state, transaction, and store-port foundation. Only WP8 Small 1x1, Medium 2x2, and Wide 4x2 sizes exist. Geometry uses separate 5% outer and 2.3% seam ratios with physical-pixel snapping. Desktop state stores explicit cells and preserves gaps. Returning to Start is Shell navigation rather than a feature action, All Apps hides internal architecture labels, and launcher icons use a controlled Canvas icon set. Interactive pager, live collision editing, complete pending Pin/Undo, macrobenchmarks/profiles, and HOME hardening remain explicitly unmeasured Phase 0B work; they must not be claimed complete in this slice.
