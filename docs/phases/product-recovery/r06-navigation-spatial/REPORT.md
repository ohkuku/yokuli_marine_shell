# R06 — Navigation Spatial Rewrite 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI / HUMAN ACCEPTANCE PENDING`

## Product outcome

- `NEW ROUTE` 现在直接进入与 Chart 相同的生产地图渲染器；点击地图添加 point，拖动 point 实时改线，点击 leg 直接插入 point，点击 point 可删除。
- 新路线自动命名为 `Route nnn`，不再要求先填写名称、航速、备注或从 waypoint list 拼装 geometry。
- 地图底部只保留 Back、Save、Save & Start、Discard；Save & Start 先按 revision transaction 写入完全相同的 geometry，再用提交后的 route revision 启动导航。
- Back 遇到非空 draft 会显示 Save / Discard / Cancel。Discard 清除本次 UI draft，重新创建路线不会复活旧点。
- saved route 打开后显示真实地图、完整 geometry 和 Start/Edit；route cards 有空间 geometry thumbnail，不再以文字 CRUD 行作为唯一理解方式。
- Active Navigation 在 Navigation 自己的真实地图中显示 vessel、完整 route、当前 active leg 和共享 HUD；Chart 同时读取相同 process runtime。
- 当前 active leg 在 Google 与 offline renderer 中作为独立高亮线渲染，不再只靠 HUD 文本推断当前航段。

## Replaced presentation

- 生产路径废弃 RouteEditor 的 name/speed/notes 表单、point list、上下移动按钮和“从已保存航点添加”作为主编辑器。
- 删除“航线只能回 Chart 编辑”的产品心智；Navigation 自己就是 spatial route planner。
- 无 renderer 的非生产/fallback 路径只诚实显示地图不可用，不恢复旧列表编辑器。

## Preserved runtime/domain

- `NavigationLibraryPort` revision compare-and-commit、`NavigationRouteMath`、saved `RoutePlan`、waypoint revision reference、GPX 与 `ActiveNavigationRuntimePort` 全部保留。
- Feature 仍只发 typed actions；socket、position selection 和地图文件不由 Navigation UI 拥有。
- 地图渲染复用相同 Active View/ChartDisplayPlan、position truth、Google/offline adapters；没有建立 Navigation 专属地图内容目录。

## Design decisions

- 复用 MapReducer 的 point/leg hit-test 与 drag transaction 作为 UI-session 编辑器，然后把完整、ordered、stable-ID geometry 投影回 Navigation coordinator；持久权威仍只有 Navigation library。
- 真实 renderer state 留在当前 Navigation UI session，route 数据留在 coordinator/library；地图 camera 不反向覆盖 Chart camera。
- route thumbnail 使用有界 Canvas 画 geometry，避免列表里同时创建多个昂贵 native map runtime；route detail/editor/active 页面使用真实地图。
- shared units 在 route card 与 spatial HUD 使用同一个 OS preference。

## Known gaps

- Recents 直接 Close Navigation 时对未保存 draft 的跨 Shell Save/Discard/Cancel 协调属于 R08；当前 Back 已完整处理，Close 会清 UI session 且不会产生 ghost durable draft。
- Route thumbnail 当前表达 geometry，不启动 native basemap；是否需要缓存 map snapshot 由 Human Acceptance 决定，不能为卡片创建多个昂贵 runtime。
- 真机仍需验证 point hit radius、drag 手指遮挡、leg insertion、相机 fit、Google/离线两种 surface 以及 active-leg z-order。

## Human stories

待验收：`NAV-01`–`NAV-12`。关键路径：NEW ROUTE → tap 4 次 → drag → tap leg insert → select/delete → Save；reopen geometry；Start → active map/active leg；Chart 同步；Stop。另测 Back → Cancel 保留 editor、Back → Discard 后无 ghost。

## Tests

- `NavigationCoordinatorTest` 新增 3 个空间行为场景：自动命名/地图 geometry、save-before-start exact revision、显式 discard 无 resurrection。
- `:feature:navigation:testDebugUnitTest`：12/12 PASS。
- `ChartInteractionRecoveryTest` 增加完整 route 与 active leg 分离场景；定向 PASS。
- Google/offline renderer 与 `:app-shell:compileStandaloneDebugKotlin`：PASS。
- 未恢复旧 W12/RouteSketch presentation gate；真实手势 instrumentation 在人工认可交互后再锁入 R10。

## Git

- R06 implementation：`b6625b4`
- compile correction：`823193092cc7bd5092630bf234882f89cfa8e44a`
- workflow / `CODEX-CI-REPORT-*` / installable APK：本批 push 后登记。

## English summary

R06 replaces the CRUD-first Navigation route editor with the production map surface. Routes are created, dragged, inserted, deleted, saved, and started spatially; active navigation shares the same route/position runtime as Chart and now exposes a separately rendered active leg. CI and real-device gesture acceptance remain pending.
