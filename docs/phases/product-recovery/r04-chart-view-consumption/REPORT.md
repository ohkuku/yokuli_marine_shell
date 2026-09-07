# R04 — Chart View Consumption 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI / HUMAN ACCEPTANCE PENDING`

## Product outcome

- Chart display coordinator 不再读取或维护 Asset pin、SourceSet、hidden Asset 或 per-file opacity；它只读取 catalog 的 Active View、logical Layers 和底层 renderable Assets。
- `MAP` picker 现在快速切换用户命名的 View，而不是另存一份 Marine/Standard/Satellite UI preference。
- 活动 View 的 built-in base 自动映射到 renderer base；View 内 Layer 顺序、显隐、opacity 通过同一个 `ChartViewDisplayPlanner` 解析。
- Quick Layers 只显示 logical Layer 名、健康、显隐和 opacity；用户不会在 Chart 看到 MBTiles 文件名或 `priority=N`。
- View 激活和 Quick Layer 调整均为带 expected catalog revision 的串行事务；冲突/失败保留当前显示并给出 typed notice。

## Replaced presentation

- 删除旧 `ChartDisplaySourceUi`、`ChartDisplayAssetUi`、`ChartDisplaySelection` 用户表面和 Chart Library→Chart Asset bridge。
- 删除锁定旧 Quick Layers 按钮结构的 `ChartDisplayWorkspaceStoryTest`；它已在 Product Recovery registry 中明确 superseded。
- 保留旧持久 `ChartDisplayPreferences` 的读取兼容字段，但生产 coordinator 不再消费或写入它，避免成为第二份活动显示真相。

## Preserved runtime/domain

- renderer 仍只接收 immutable per-Asset `ChartReadRequest`，lease、revision、generation、TMS/XYZ 与 fallback 语义不变。
- MapStore 继续拥有相机、手势和 renderer lifecycle；catalog 只拥有地图内容组合。
- 旧 route/place/navigation/persistence/math 没有在 R04 中重写。

## Design decisions

- 复用现有 actor coordinator，所有 View action 与 catalog reload 串行，避免 Compose coroutine 覆盖次序。
- UI 使用 LayerId；planner 最后才展开到 AssetId，保持用户模型与文件模型边界。
- base style 属于 View。MapState 的 MapViewMode 只是 renderer 投影，不再是独立用户选择真相。

## Known gaps

- R05 才负责 Chart 的完整 Target、Quick Mark、A/B Measure、Route Quick Edit 与 Active Navigation 交互恢复。
- 真机仍需确认 Google/离线 surface 在快速切换 View 时无旧 generation 闪回、无 preview/Chart 相机串扰。
- 旧持久 display-preference 字段的最终 schema 清理应在有迁移证据后做，不能静默删除。

## Human stories

待验收：`CH-01`、`CH-02`、`CH-12`–`CH-17`，以及 `CL-24`、`CL-25`。需要核对活动 View 名、底图、Layer 顺序/透明度在 Chart 与 Chart Library Preview 完全一致。

## Tests

- 新 `ChartDisplayCoordinatorTest` 覆盖：Active View 唯一来源、logical name 不泄露文件名、View activation 持久事务、base 同步、Quick Layer opacity/visibility 写回 View、旧 ChartDisplayPreferences 不再写。
- 本地 `:feature:chart:testDebugUnitTest`：30/30 PASS。
- 本地 `:app-shell:compileStandaloneDebugKotlin`：PASS。
- 全量 lint/assemble/device story 留给本批 GitHub workflow，符合 Product Recovery 测试策略。

## Git

- R04 implementation：`726769e4a47ee10968000e6a0e60a7413c2bd345`
- localized compile correction：`9dec59a`
- shared production renderer extraction：`aff99c6e936fc9270167fb38b6a584c057fd9483`
- CI report/artifact：本批 push 后填写。

## English summary

R04 makes the catalog Active View the sole production display source for Chart. View switching and logical-layer visibility/opacity are catalog transactions; Assets remain renderer inputs only and no longer appear in Chart UI. CI and human renderer acceptance remain pending.
