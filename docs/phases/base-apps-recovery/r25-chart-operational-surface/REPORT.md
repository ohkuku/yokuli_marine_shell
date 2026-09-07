# R25 — Chart Operational Surface Cleanup

状态：`IMPLEMENTATION + FOCUSED MACHINE GATE COMPLETE — HOSTED CI PENDING`

## Baseline

- 基线 HEAD：`bbbdfd1`
- 实现代码 HEAD：`4c383af6303722e48cac411717b4feb0420c0064`
- Execution directive SHA-256：`36f520ea6232b152554dd1d23d224e18b08009af89157ca046b1381a9dd68ac5`

## 产品结果

- Chart 根页继续以地图为主体，固定一级操作为方向／缩放／GPS、MARK、ROUTE、MEASURE、MAP；没有新增资源管理首页。
- Target HUD 只保留坐标、可用时的距离／真方位，以及 Direct-To、Mark、Measure、Cancel；不再显示旧 ChartPackage／MBTiles 身份或挤入无关的复制动作。
- 已保存 waypoint 的深度管理明确跳转到 Navigation，而不是重新打开 Chart 内旧 Place 管理页。
- MAP 面板在存在用户 View 时只显示 View 与 logical Layer 快捷可见性；不暴露 SAF、folder、asset、验证或优先级。
- 零用户 View 是正常状态：用户可以两步内切换 Marine／Standard／Satellite fallback，选择写入既有 session preference；catalog refresh 不会再把它偷偷重置为 Satellite，也不会显示“缺失 View”假错误。
- Chart 顶部不再把本地图包健康／验证状态当作长期主信息；真正 renderer 错误和在线底图未配置仍会如实提示。

## Red / Green / Correction

R25 新增零 View fallback 的行为合同，并继续运行现有 map-first、Target、A/B Measure、direct route 与 display ownership 合同。首轮 Green 暴露测试 fake 的 action list 会被异步 observer 同时遍历／写入，出现 `ConcurrentModificationException`；修正为线程安全 evidence recorder，没有改产品断言。

| 定向 Gate | 结果 |
| --- | --- |
| zero-View / View ownership coordinator | PASS (`4/4`) |
| map-first operational domain journeys | PASS (`10/10`) |
| target / transient Measure journeys | PASS (`5/5`) |
| app-shell production composition compile | PASS |
| `git diff --check` | PASS |

完整 test/lint/build/device Gate 只在 R26 hosted CI 执行。

## 过渡与未完成

- 旧 Chart Page composables 和 legacy `MapSurface` 类型暂时保留为兼容代码，但正常 Chart root、搜索 deep link 与 waypoint 管理不再进入这些资源／资料管理页面。删除必须等 R26 replacement journeys 绿后进行。
- Route point gesture、A/B drag、触控命中区与湿手／手套可用性仍需真实 APK 人工验收；JVM 合同不冒充触感。
- exact user MBTiles 仍未提供给本地测试，因此该文件的真实显示继续标记未验证。

## CI

- 报告前缀：`CODEX-CI-REPORT-`
- Hosted run：`PENDING_PUSH`

## English summary

R25 leaves Chart as an operational map rather than a resource manager. Target actions are compact and truthful, waypoint administration hands off to Navigation, and MAP exposes only user Views, logical Layer visibility, or a persisted no-View basemap fallback. Catalog refresh no longer overwrites that fallback choice. Existing direct-manipulation domain journeys and production composition pass focused gates; hosted CI and physical interaction remain pending.
