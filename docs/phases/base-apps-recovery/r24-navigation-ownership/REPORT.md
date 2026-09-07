# R24 — Navigation Semantic Ownership Consolidation

状态：`IMPLEMENTATION + FOCUSED MACHINE GATE COMPLETE — HOSTED CI PENDING`

## Baseline

- 基线 HEAD：`c83c874`
- 实现代码 HEAD：`240bb98`
- Execution directive SHA-256：`36f520ea6232b152554dd1d23d224e18b08009af89157ca046b1381a9dd68ac5`

## 产品结果

- `NavigationLibrary` / `NavigationLibraryPort` 是 Waypoint、RoutePlan 与 GPX 的唯一耐久产品真相；没有创建第二个数据库或 Feature 间同步层。
- Chart 的 Quick Mark、Waypoint 编辑／移动／删除、Route Save／Save As Copy／删除与 GPX 最终导入均通过 Shell composition root 提交 Navigation 事务。
- Chart route draft 仍可作为地图空间编辑 scratch；保存时一次转换为 canonical `RoutePlan`，成功后再投影回地图并关闭 scratch。
- `MapState` 中的 waypoint、saved route、track 和 GPX 记录现在由 Navigation library 投影；它们不再是 Chart 独立写入的新耐久权威。
- Navigation 自己的 UI 操作与来自 Chart 的写入共用同一个串行 coordinator queue、revision 和 conflict 语义。
- 旧 Room schema 与 `NavigationLegacyMapper` 保留为安全兼容桥；本阶段没有冒险重写数据库。

## Red / Green

新增行为合同先要求 canonical numbering、exact geometry、conflict／Save As Copy、Navigation → Chart 投影和跨宿主串行提交。Green 后定向结果：

| 定向 Gate | 结果 |
| --- | --- |
| Navigation → Map projection | PASS (`2/2`) |
| Chart → Navigation commit planner | PASS (`3/3`) |
| GPX canonical import | PASS (`5/5`) |
| shared Navigation coordinator queue | PASS (`8/8`) |
| app-shell production composition compile | PASS |
| `git diff --check` | PASS |

完整质量门禁交给 hosted CI。

## 过渡与未完成

- 旧 Map reducer 的 durable-shaped actions 暂时仍存在，供兼容读取和空间 scratch 使用；正常产品写入已在 Shell 边界拦截并转为 Navigation transaction。R26 在替代旅程绿后才能安全删除死路径。
- Active navigation 继续使用既有 application-scoped `DefaultActiveNavigationRuntime`；本阶段没有创建第二个 live session。
- 真机地图直接操作、关闭 UI 后 active navigation 的人工感知仍属于最终 Human Acceptance，不由 JVM/compile gate 冒充。

## CI

- 报告前缀：`CODEX-CI-REPORT-`
- Hosted run：`PENDING_PUSH`

## English summary

R24 makes the existing Navigation library the sole durable owner of waypoints, routes, and GPX content. Chart operational writes now cross one Shell composition boundary and share Navigation's serialized revisioned transaction queue. MapState remains a renderer projection and route-edit scratch model, while the legacy Room schema remains only as a compatibility bridge. Focused behavior and production composition gates pass; hosted CI and human device journeys remain pending.
