# W09 Product & Engineering Contract — Chart as the Live Situation Map

版本：1 · 2026-09-06  
状态：施工候选

## Intent / 产品愿景

Chart 是用户航行时持续停留的实时态势地图，而不是海图文件管理器、路线数据库和导入工具的集合页。用户进入
Chart 后首先看到卫星/本地海图、船位事实、跟随或浏览状态以及当前位置附近的空间语境；常用操作必须在单手可达
的大目标中完成，不能再用七个狭小按钮暴露内部数据结构。

## Experience / 用户体验

- 根页保持地图最大可视面积，直接提供测量、地点、路线、快速图层和十字准星五个明确入口。
- 快速图层只调整已经交给 Chart 的图层：显示/隐藏，以及当前可见图层的透明度。
- 用户不会从 Chart 被推入文件权限、扫描、来源管理或 Chart Library 管理页；资源选择由 Chart Library 的明确
  “在 Chart 查看”内容 handoff 发起。
- 路线、地点、GPX 和导入轨迹在 Navigation 接管前仍可读写，不因为信息架构调整而消失。
- 根页允许后续真实 Active Navigation 内容注入；没有 active session 时不显示假导航条。

## Domain / 领域关系

- Chart 拥有地图会话、相机、follow/browse、测量和当前 display preferences。
- Chart Library 拥有文件、SAF grant、扫描、校验、来源与 catalog；Chart 只消费 revisioned display plan。
- Navigation 最终拥有路线、航点、GPX、轨迹和离线覆盖规划。W09 只准备边界，W10–W12 完成迁移前不删旧数据。
- `hiddenAssetIds` 是 Chart 的显示偏好，不改变 catalog、资源 enabled 状态或外部文件。

## Opportunities / 可发展空间

后续可以加入按航区组织的图层组、航行模式下更紧凑的图层 flyout、手势式透明度调整以及 active navigation strip。
这些机会不得把 source management 搬回 Chart，也不要求采用预设的 coordinator/repository/class 图。

## Non-negotiables / 不可协商边界

- Quick Layers 不请求文件权限、不扫描存储、不修改 source 或 asset catalog。
- 图层显隐和透明度必须持久化，并在 catalog revision 更新后仍按稳定 asset identity 生效。
- Back 在 Chart 内一次只退出最上层交互面；Chart 根页 Back 由 Shell 接管并回到 Start，不退出应用。
- follow/browse 与位置真实性不被 UI 简化覆盖；没有有效位置时不得声称正在跟随。
- active navigation seam 只接受真实业务内容，不创建占位 session、距离、ETA 或进度。

## Forbidden outcomes / 禁止结果

- 在 Chart Quick Layers 中出现“管理海图库”、来源开关、扫描、刷新 catalog 或文件导入入口。
- 为了减少根页按钮而删除/清空旧 route/place/GPX/track 数据。
- 让隐藏图层从 durable selection 中消失，或用 catalog mutation 实现显示开关。
- 从 Chart 直接打开 Android 桌面设置、注册 HOME intent，或让 Back 退出进程。
- 用未来 Navigation 的静态假数据填充根页。

## Compatibility / 兼容策略

现有 Chart Library → Chart asset handoff、CL07–CL12 catalog/runtime、旧 managed chart migration 与所有地图业务数据继续
可读。Map session schema 只追加有界的 hidden asset identity；schema 4 会自然恢复为空集合，不清库、不重写用户资源。
旧 Chart surfaces 在 Navigation 完成前保留为内部兼容入口，但不再混入 Quick Layers。

## Acceptance stories

1. 用户在 Chart Library 选择“在 Chart 查看”后回到 Chart；Quick Layers 只出现该选择的显示开关和透明度。
2. 用户隐藏一个图层后 catalog 与文件不变；重启后该显示偏好恢复，重新显示也不需要重扫来源。
3. Chart 根页只有五个宽松触控目标，不再直接启动 manual-route tool 或坐标表单；路线创建仍能从 Routes 完成。
4. GPX 与 imported tracks 在 Navigation 尚未安装时仍从 Routes 可达，旧 fixture 继续读取。
5. Quick Layers 中不存在 Chart Library 跳转、source 管理、catalog refresh、GPX 或 track 管理。
6. 注入真实 active navigation composable 时它出现在地图 chrome；未注入时没有假导航内容。
7. Quick Layers/子页 Back 回到地图根页；地图根页 Back 回到 Shell Start。

## Evidence required

- JVM：display preference 显隐、确定 plan、catalog 零 mutation、schema 4/5 兼容、follow/back 合同。
- API 34：五入口根页、Quick Layers 操作与禁止入口、旧 Routes/GPX/track 可达、active-navigation seam。
- 静态：生产 Chart 不再接收 `onOpenChartLibrary` peer callback；Quick Layers 不引用管理 actions。
- W16 人工：真机方屏触控尺寸、真实地图长时浏览、物理 Back → Start。

## Existing code landmarks

优先审阅 `ChartWorkspace`、`ChartDisplayCoordinator`、`ChartDisplayPlanner`、Map session persistence、
Chart Library 的 `OpenAssetInChart` handoff 和现有 Back policy。这些只是入口提示，不限制最终内部结构。

## Implementation freedom / 实现自由

可以复用现有 reducer/coordinator，也可以在确有必要时调整状态投影；不得为了匹配文档标题增加空架构层。实现选择只要
保持 ownership、持久化、兼容、真实性和验收故事即可。

## English translation

Chart is the live situation map, not a file or source manager. Its root exposes five high-value controls; Quick Layers only changes visibility and opacity for content explicitly handed to Chart. Chart Library retains file/catalog ownership, Navigation migration must finish before legacy planning data is removed, Back returns to Shell Start, and the active-navigation seam renders only real session content. Implementation structure is deliberately open while ownership, safety, compatibility, and evidence are strict.
