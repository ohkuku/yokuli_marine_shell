# CL08 报告 / Chart display consumption, multi-chart rendering and coverage

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`908ac9d64d7fc042f053aa45eab63d03ec754fc6`

## 实现

- Chart 不再拥有 MBTiles 文件 picker、复制导入、扫描、删除或启用资源的维护流程；其“海图”页面
  只读取 `ChartCatalogReadPort`，控制“不使用本机图 / 固定单张 / 已选来源集合”、overlay 显隐和
  当前显示透明度。旧受管包 reader 仅作为 CL09 迁移前的兼容路径保留。
- `ChartDisplayPlanner` 用 catalog revision、内容 revision、来源 scan generation、视野和 zoom 生成
  有 fingerprint 的有界计划。自动来源集合排除未知 bounds；手动固定仍允许未知 bounds。候选按
  底图后叠加、显式 priority、稳定 ID 决定，最多 8 个来源和 8 个活跃图层。
- MapLibre 只打开计划中的资源，每个资源保持自己的 256/512 tileSize、TMS/XYZ scheme、zoom 和
  opacity，并通过进程内 loopback gateway 使用与 coverage 相同的只读 session。切换 revision 时先
  撤掉旧 style/session，旧 plan generation 不能覆盖新状态。
- Google 只在没有本机显示选择时成为在线底图；一旦用户选择本地图，本地图缺口保持可见，不会
  用 Google 填洞。
- 航线离线检查对计划中的合格底图做真实 tile-key 并集；overlay 不贡献底图覆盖，metadata bounds
  只筛候选，fingerprint 不一致直接成为 stale。

## TDD 与 GitHub 证据

- core 场景覆盖固定选择不被新图抢占、确定性层序、多区域视野、日期变更线、未知 bounds、原生
  zoom 边界、图层上限、overlay 不补洞和旧 revision 拒绝。
- adapter 场景覆盖相同 reader 的 TMS/XYZ 查询、多底图 key 并集、不同 tileSize、部分资源不可读
  和全部 session 释放；Compose story 锁定 Chart 只控制显示且不存在导入入口。
- 新增独立 `chart_library_cl08_contract` Action step并纳入最终 build enforcement 与 Codex report。
- 按用户指定的 CI-first 工作流，本地不运行 Gradle；提交后读取
  `CODEX-CI-REPORT-<sha12>-<run_id>-<attempt>`，失败项与后续 CL 提交一起批量纠错。

## DESIGN DECISIONS

- 复用现有 `MapStore`、catalog runtime、MapLibre surface 和 loopback gateway，没有再创建平行的
  地图仓库或第二套 renderer。
- Display Plan 是运行时事实，不在 CL08 偷做持久化；旧活动图选择和受管包登记的幂等迁移属于 CL09。
- 没有在 Chart 复制海图库的永久角色、优先级或文件维护操作；CL10 才建立跨应用“管理海图库”深链。
- 当前航线覆盖对“此刻确定的 display plan”保守检查：缺少候选只会产生缺失/未知，不会假报完整。

## 未验证边界

- GitHub Action 尚未回传 JVM、lint、APK 编译与 API34 多图像素证据，因此本报告不写 PASS。
- provider 在相同 size/mtime 下静默替换且不发通知仍只能由用户强制重新检查发现；UI 会如实说明限制。
