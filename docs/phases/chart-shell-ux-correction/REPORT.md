# Chart / Navigation / Shell UX 修正报告

状态：`IMPLEMENTED — GITHUB_ACTIONS_PENDING`

基线：`e594189e5ef3eee55825ce024d4cbf5b9426649a`

## 产品结果

- Chart 首页已收敛为地图主体与 `MARK · ROUTE · MEASURE · MAP` 四个直接动作；Recenter/Follow、Zoom 和 Compass 保持地图边缘一级入口。
- Measure 已锁为最多两个 A/B handle，默认 UI 不再提供插点、删点、undo/redo 或 convert-to-route。
- 搜索 waypoint 现在回到地图 root 并产生可见 selected object，而不是打开资料型 Place 页面。
- 非空 route draft 在切换工具、Back、Recents Close 或启动另一导航前都会进入 Save / Discard / Cancel；Save/Discard 清除对应 transient 并回地图 root。
- Standard/Satellite 与 Marine 的 renderer 由所选 Map View 决定，旧本地图层选择不再遮蔽在线底图；未配置 Google key 时在线视图禁用并显示配置事实。
- Shell 已具备二维 1x1 tile 排布、可 Close 的 Recents、只读 status strip、竖屏/no-HOME 边界和 Latin 拼音索引。
- Chart Library 继续独立负责 source、scan、Basic/Full 与 recovery；不完整扫描只更新健康状态，不批量摧毁旧 catalog。

## TDD 与自查

- `6eb66b3` 的 Red/Green 回归覆盖 A/B 第三点拒绝、Measure/Route 互斥、route draft 显式决定、Discard 无 ghost、搜索结果回地图和生产 Android stories。
- `e594189` 的 renderer policy test 覆盖 Standard/Satellite 选择 Google、Marine 选择 offline，以及未配置 key 时不得假装 Google renderer 可用。
- 本修正新增一个累计静态合同，锁定产品主路径、Shell 不变量、Chart Library 保护和 CI 接线；本地合同 `8/8 PASS`、CI 接线合同 `PASS`、Chart 与 app-shell 增量 Kotlin 编译 `BUILD SUCCESSFUL`。完整 JVM/lint/APK/API 34/API 36 由 GitHub Actions 执行。
- 用户提供的最新旧报告 `ba2728b / 34032105597` 已证明 manifest 与 BuildConfig 收到非占位 key；该证据只等于配置成功，不等于 Google 服务授权或真实图块加载。

## DESIGN DECISIONS

- 复用现有 Map reducer、renderer adapter、Chart Library runtime 与 Shell session 管理，没有为了需求文档另造平行真相。
- 保留历史深层管理页面源码以避免本轮扩大迁移风险，但从生产 Chart 主路径移除其入口，并由合同检查根操作的精确表面。
- 未保存 route 的决策集中在 Shell ViewModel 的单一 guard，关闭 session 与启动 navigation 共享同一套原子结果，避免各 Compose 页面各自 launch。
- Maps key 缺失被投影为不可选的 connected views，而不是让用户进入一个注定不能工作的页面；key 存在仍不冒充 runtime readiness。
- W16 没有新增产品代码，本轮不重复执行它的本地全门禁；最终 CI 继续保留 W16 machine evidence。

## 待 GitHub Actions 证明

本地只运行与改动相称的窄测试。最终结论必须来自与最新提交 SHA 完全匹配的 `CODEX-CI-REPORT-<sha12>-<run>-<attempt>`。三星方屏、真实 Google 图块、真实 GNSS 和大型用户 MBTiles 仍属于物理/人工证据，CI 不得冒充。

## English translation

The correction makes Chart map-first, caps Measure at two visible A/B handles, prevents route-draft ghost state across tool, session and navigation boundaries, routes waypoint search back to the map, and makes the selected Map View control the renderer. Standard and Satellite are unavailable when the Google key is absent; key presence remains configuration-only evidence. Existing Shell spatial layout, closeable sessions, display-only status, portrait/no-HOME boundary, pinyin indexing, and Chart Library scan protection are locked into one cumulative CI contract. Full authority remains the commit-bound GitHub Actions report.
