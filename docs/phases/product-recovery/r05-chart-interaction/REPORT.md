# R05 — Chart Interaction Recovery 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI / HUMAN ACCEPTANCE PENDING`

## Product outcome

- Chart 以真实地图作为主工作空间，保留一级 `MARK / ROUTE / MEASURE / MAP`、独立 GPS Recenter/Follow、缩放与朝向控制。
- 地图空白处点击会在同一次交互生成可见 Target，并给出坐标、距船距离与真方位；Target 可直接 Go To、Quick Mark 或开始测距。
- Quick Mark 单动作创建自动命名的 `WP nnn`，立即显示 pin，不要求先填写 name/category/tag/note 表单。
- Measure 是严格的两点 A/B ruler。A/B、连线、距离与方位现在共同消费同一份 drag-preview geometry；拖动中 HUD 不再停留在松手前的旧值。
- Route Quick Edit 继续在地图上直接 add / drag / leg insert / delete，并提供 Save / Go / Discard；关闭含点 draft 时使用 Save / Discard / Cancel。
- Chart 的 Active Navigation HUD 消费 process-owned navigation runtime，提供 DTW、BTW、XTE、ETA、进度、Previous/Next 与一级 Stop。
- 已保存 waypoint 的地图选中卡片补齐坐标与距船 distance/bearing，不再只有一个资料名称。

## Replaced presentation

- 废弃“renderer 画 preview、HUD 读 durable draft”的双真相：拖动时所有地图与数值反馈统一读取 `visibleMeasurementPoints`。
- Measure 的旧 insert/delete/undo/redo/convert-to-route 能力仍只作为兼容 domain API 保留，不出现在默认地图 Measure 交互，不构成产品入口。
- Waypoint 的 metadata 编辑仍可作为创建后的 secondary detail，但不再阻塞基础 Mark。

## Preserved runtime/domain

- WGS84 geodesic 距离/真方位、route revision、undo history、MapStore 串行 dispatch、renderer generation 与 active-navigation runtime 均复用。
- A/B preview 不在每帧持久化；只有 drag commit 写 durable point，取消手势恢复原 geometry。
- saved waypoint、saved route、chart catalog 与 active navigation 不因 Chart UI session close 被删除。

## Design decisions

- 把 preview geometry 放在 `MapState` 的只读派生值，而不是另建 UI 状态或让两个 renderer 各自推导，避免 Google / offline / HUD 三套结果漂移。
- 保留现有地图手势与 query ports；本轮只修正可观察产品真相，没有重写 renderer lifecycle。
- 继续使用 Place 作为兼容存储模型，但生产交互用 Mark/Waypoint 心智；完整命名迁移留给持久兼容有证据的后续切片。

## Known gaps

- CH-12/CH-13 仍需在真实 APK 上分别拖 A 与 B，确认 marker 命中半径、手指遮挡、边缘拖动与实时数值节奏。
- 地图 marine glyph family、湿手/手套可用性和不同密度真机视觉仍是 Human Acceptance 项，自动 compile 不能替代。
- Navigation 的完整空间首页与 route preview 属于 R06；Data 与 Shell 修复分别属于 R07/R08。

## Human stories

待验收：`CH-01`–`CH-17`。本轮新增的关键人工路径是：Target → Quick Mark；Measure → 拖 A → 拖 B → 观察 line/distance/bearing 同帧更新；Route → add/drag/insert/delete → Discard → reopen 无 ghost；Direct-To → 验证 DTW/BTW/XTE/Stop。

## Tests

- 新 `ChartInteractionRecoveryTest`：4/4 PASS，覆盖 preview 单一显示真相、严格 A/B 与退出清理、无表单 Quick Mark、点击即 visible Target。
- `:feature:chart:testDebugUnitTest`：30/30 PASS。
- `:adapter:chart-google:compileDebugKotlin`：PASS。
- `:adapter:map-offline:compileDebugKotlin`：PASS。
- 没有恢复已被人工否决的旧 presentation tests；完整 lint/assemble/device gate 交给 GitHub workflow。

## Git

- R05 implementation：`af893a97670c267be3b1a081e3356cf314a24b71`
- workflow / `CODEX-CI-REPORT-*` / installable APK：本批 push 后登记。

## English summary

R05 keeps Chart map-first and makes Target, one-action Mark, the two-point ruler, route quick editing, and active-navigation context direct map interactions. The principal correction is one preview geometry truth shared by both renderers and the measurement HUD, so distance and bearing update while a handle is dragged. CI and real-device acceptance remain pending.
