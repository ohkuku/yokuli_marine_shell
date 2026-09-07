# Navionics Research V3 — 实施总报告

状态：`R00–R09 IMPLEMENTED — HOSTED CI / R10 HUMAN ACCEPTANCE PENDING`

本报告把 `Yokuli_OS_Chart_Navigation_Navionics_Research_V3_Bundle.zip` 当作产品与研究输入，而不是像素规范或强制内部架构。Yokuli 采用其中经仓库现状验证的交互语义，没有复制第三方视觉资产或页面结构。

## Work-package disposition

| V3 package | 实际落点 | 状态 |
|---|---|---|
| R00 Presentation/Test Reset | Product Recovery Window `4e88a3e` + gate correction `79e2255` | 完成；旧 presentation shape 非权威 |
| R01 Target + Quick Mark | Chart Interaction Recovery `af893a9` | 已实现，待真机手势验收 |
| R02 A/B Measure | Chart Interaction Recovery `af893a9` | 已实现，待真机拖动验收 |
| R03 Direct Route Editor | Navigation Spatial `8231930` | 已实现，待真机地图编辑验收 |
| R04 Global Navigation Runtime | `5a405fe` | 已实现；替换必须显式确认 |
| R05 Navigation Camera | `9d3fe4b` | 已实现；Follow/Look Ahead/Next WP/Overview/Browse 分离 |
| R06 Track Recorder | `73f5281` | 已实现；进程级、可恢复、actual track 独立 |
| R07 Navigation History | `27d8238` | 已实现；passage evidence 与 track association 持久化 |
| R08 Map Content Integration | Map Content/View `e1a554d` + `7fa622c` | 已实现；逻辑 View 与 MBTiles 资源管理分离 |
| R09 Shell Activity / Consumer | `2ac2c1f` | 已实现；Shell/Future Cockpit 共用同一 snapshot |
| R10 Human Acceptance | 需要本轮 CI APK | `PENDING` |
| R11 Test Re-lock | 只有 R10 人工批准后允许 | `NOT STARTED` |

## Cross-package invariants

- Chart 以地图对象和直接操作为中心；Chart Library 管资源，不回流到 Chart 主交互。
- Planned Route、Active Navigation、Actual Track、Passage History 和 Map Content 是五种不同真相。
- Active Navigation 与 Track Recorder 都由进程级 runtime 持有；页面、Start Tile、Shell、未来 Cockpit 不得建立副本。
- Navigation camera 只改变视图，不改变 route/session；切换 map content 不停止 navigation 或 track。
- 恢复只恢复已经持久化的事实；不能编造 position、waypoint passage、完成状态或连续航迹。
- R10 之前不新增“按钮/字符串/Composable 必须长这样”的 presentation gate；底层数学、持久化、迁移、并发和运行时安全继续是硬 gate。

## Hosted evidence boundary

- 本轮只执行了受影响模块的定向 JVM/compile gate，结果记录在各 R04–R09 报告。
- GitHub Actions 必须验证完整 Product Recovery machine gate、API 34/36、MBTiles/Room migrations、release surface 和 Google Maps configuration-only evidence。
- CI 绿色仍不等于 R10 产品批准。真机地图手势、视觉层次、湿手/单手可用性和真实 Google tile load 只能由人工验收决定。
- R11 严格等待 R10；当前不会把概念预览、文案或临时布局固化成新的 presentation authority。

## English summary

NAVREF R00 through R09 are implemented by reusing the accepted Product Recovery foundations and adding one global active-navigation runtime contract, independent navigation camera modes, a process-owned track recorder, truthful passage history, and one shared Shell/future-consumer activity port. Hosted CI and R10 physical product acceptance remain pending; R11 presentation re-lock has not started.
