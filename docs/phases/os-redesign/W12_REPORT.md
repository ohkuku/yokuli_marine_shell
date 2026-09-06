# W12 报告 / Navigation App

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`284fc47e828ea0b939f0ab2f69b299088fcfd151`

## 产品结果

- Navigation 已成为第五个真实生产 App，提供 Overview、Waypoints、Routes、route editor、GPX 与 Active navigation 工作空间。
- 航点和航线 CRUD 使用 W10 同一 Room `NavigationLibraryPort`，按 library revision 与 entity revision 拒绝过期写入；没有新增平行数据库。
- 航线详情给出有界的路线几何预览、WGS84 总距和逐 leg 真方位；明确操作才会启动 W11 runtime。
- Active 页面投影真实 Next/DTW/BTW/XTE/ETA/progress，并保留 pause/resume/leg/stop typed commands。
- “在地图中显示”通过 Shell 传递 route id 给 Chart；Navigation 和 Chart 不共享 Feature UI state。
- 失效航线 token 显示不可用并留在 Navigation；活动航线删除在 UI 和 Coordinator 两层阻止。
- App 自己贡献 WP 小/中/宽磁贴、图标与有界搜索；默认 Start 不自动 pin 新 App。
- W01/W03/W04 的历史锁保持原样；其“当前生产 App 数量”静态断言由 W12 显式更新为五个已完成功能 App。

## DESIGN DECISIONS

- 复用 W10 的 `NavigationLibraryPort` 与 W11 的 `ActiveNavigationRuntimePort`，没有增加 Navigation 专属存储或第二个 runtime。
- 选择轻量路线几何图而不嵌入 GoogleMap：详情需要帮助用户理解航线，不应为管理页创建网络 renderer 或伪造海图语义。
- 动态 route token 使用有界 UTF-8 十六进制编码；删除后的 token 仍能安全路由到明确的 unavailable 页面。
- GPX 暂时由 Shell 注入既有、已验证的 Chart GPX 文档 surface；依赖箭头没有从 Navigation 指向 Chart，共享真相仍是同一 Room library。W15 会在删除 Chart 旧管理 surface 时迁移其 UI ownership。
- 活动磁贴和 strip 使用用户可读航线点序号或已保存航点名，不把内部 UUID 暴露为产品文案。
- 未强行规定新的 repository/class 拆分；当前边界已经能由既有端口表达。

## 暂不实施

- 不在 W12 删除 Chart 旧路线/航点/GPX surface；兼容移除属于 W15。
- 不实现 autopilot、NMEA output、自动选源、天气路由或云同步。
- 不宣称真船、真实 GNSS、Google Satellite 网络或物理设备已验证；由 W16 证据账本处理。

## Pipeline 证据

- W12 static contract：产品表面、依赖、安装、hand-off、失效 deep link、测试故事与文档。
- `feature:navigation` JVM/UI tests：CRUD、冲突、活动删除保护、命令、五工作区、route preview 与 three-size tile contract。
- app-shell tests：五 App 单点安装、host/launch resolution、默认 Start 不自动 pin Navigation。
- 本地仅运行提交后的受影响源码编译；完整测试、lint、设备、performance 和 Release 审计留在 W16 Actions。

## English translation

W12 installs a real fifth production app for passage planning and execution. It reuses the W10 Room library and W11 active runtime, delivers revision-safe waypoint/route workflows, truthful route and active-navigation projections, safe stale deep links, Navigation-owned tiles/search, and an explicit route-content handoff to Chart. Full authoritative verification remains pending GitHub Actions.
