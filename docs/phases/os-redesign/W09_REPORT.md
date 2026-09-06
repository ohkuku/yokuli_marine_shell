# W09 报告 / Chart V2 simplification

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`33596e8ea378f62dc51c7de436a6c47a321b8d08`

## 产品结果

- Chart 根页收敛为测量、地点、路线、快速图层和十字准星五个触控入口。
- Quick Layers 只显示已交给 Chart 的图层，提供显隐和可选 opacity；不再暴露来源、扫描、刷新、文件导入或海图库跳转。
- GPX 与 imported tracks 临时归入 Routes 入口，直到 W10–W12 的 Navigation 真实接管完成，旧数据未删除。
- 新增真实 active-navigation content seam；未注入时没有占位导航状态。
- 图层显隐是有界、可持久化的 Chart session preference，不改变 Chart Library catalog。

## DESIGN DECISIONS

- 复用现有 `ChartDisplayPreferences` 与 planner，追加稳定 asset identity 的 hidden set；没有新建第二个图层 repository。
- Quick Layers 使用 coordinator 投影出的 `quickLayers`，UI 不读取 source list 或自行解释 catalog。
- 保留 `PinAsset`/`ToggleSource` 作为 composition-root handoff 与兼容能力，但从生产 Quick Layers UI 移除。
- 不提前删除 MapSurface 的路线/地点/GPX/轨迹类型：目标 Navigation owner 尚未完成，删除会违反数据可达性。
- active navigation 采用 nullable content seam，不定义假 session model；W11 可直接注入真实投影。

## 兼容与真实性

- Map session schema 由 4 追加到 5；旧 schema 的 hidden set 为空，不清理 selection、camera、measurement 或用户 library。
- Chart 根页不再有 direct coordinate button；坐标能力仍保留在 precise-edit 工作流和兼容 surface 中。
- hosted CI 尚未回传，因此 API 34 story、完整 unit/lint/build 和 APK 均不写 PASS。

## 测试源交付给 Pipeline

- core：隐藏图层不改变 durable selection，display plan 确定排除。
- feature：显隐动作串行写入 session preference，catalog mutation 始终为零。
- storage：schema 5 round trip 保留 hidden asset identity。
- API 34：Quick Layers 禁止管理入口、根页简化、旧数据入口与真实 navigation seam。

## English translation

W09 restores Chart as a focused live map. Five primary root actions replace the crowded command bar. Quick Layers changes only visibility and opacity of already-selected content, while source/file administration remains in Chart Library. Legacy navigation data stays reachable until its new owner exists, and a nullable seam accepts only real active-navigation content. Hosted evidence is pending.
