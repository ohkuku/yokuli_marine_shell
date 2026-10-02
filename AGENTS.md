# Yokuli OS 开发入口

- 先核对当前 HEAD、未提交修改、目录级规则与 `app-shell/build.gradle.kts` 的 sourceSets。当前手机与 ROM HOME 共用 `app-shell/src/rebuild/java`、`src/rebuild/res` 和 rebuild Manifest；旧 `src/main/java` 页面不是生产入口。
- 主实施规则：[YOKULI_MASTER_EXECUTION](docs/product/YOKULI_MASTER_EXECUTION.md)。真实边界：[系统接入现状](docs/os/10-INPROCESS-SYSTEM-BOUNDARIES.md)；新功能沿 [领域接入指导](docs/os/02-DOMAIN-AND-CONTRACTS.md#新功能接入路径) 实施。
- 当前交互基线为 [Windows 10 Mobile / MDL2](docs/product/WINDOWS_10_MOBILE_DESIGN.md)，以当期官方资料及共享语义控件实现。WP8 资料保留作历史参考；用户明确保留经典应用开合/Home 翻转与倾斜磁贴，控件仍为 W10M。不要复原超大普通标题、圆圈动作按钮或套用 Win11 Fluent 样式。大字号只保留给实际关键读数。
- 2026-09-27 用户已替换旧品牌：当前为小写 yokuli + 次级 os 字标、十六格像素帆船、黑白扁平与文字主导；OS 标识使用 `core/design/YokuliBrand` 与 `design/brand` SVG 母版；更新母版后运行 `scripts/generate_brand_assets.py`。遵守[品牌接入规则](docs/product/WINDOWS_10_MOBILE_DESIGN.md#yokuli-品牌标记与字标2026-09-25)，不在业务页堆品牌、不改变 Home/通知含义；冷启动帧时钟品牌动画只播一次、可略过，桌面和 Core 首帧照常工作。
- 默认进程为唯一 Marine Core，MainActivity/Shell 位于 `:shell`，通知位于 `:notifications`。UI 只通过 `BinderMarineSystem` 与窄端口访问领域，不能在子进程构造本地 Room/DataStore/控制器。新端口同步注册固定 IPC 表、DTO codec、初始快照和死亡释放；恢复及命令幂等见 [生命周期](docs/os/03-LIFECYCLE-AND-RECOVERY.md#当前-core-恢复屏障与幂等执行)。
- UI 只拥有展示与本次访问状态；来源、导航、记录、守锚、AIS、内容、通知各有唯一所有者。禁止页面直连 DAO/控制器、重复采集或新增平行业务状态。现存兼容桥不能成为新功能的捷径。
- 图册只以 `.yklpkg` 资料包为顶层；包可先为空，再加入海图或数据。`ChartBundleStore` 只保存绑定和事务，海图仍由 `ChartLibrary`、数据仍由 Core `ChartDataService` 唯一拥有。活动包统一供给地图/准星/规划；底图、卫星、包内海图只是显示模式，切换不换数据。每包一个数据目录，内部文件可排序，包之间无叠加优先级。原始数据只在准星显示参数；覆盖预览只画边界。海图生成必须用户明确发起，持有真实数据快照输出 MBTiles，登记回原包后供地图切换；不随拖图实时绘制数据。旧独立目录迁入包，保留原件和已选来源。规则及流程见 [海图契约](docs/product/CHART_INTERACTION_CONTRACT.md#数据图册与自动规划2026-09-25)。航线编辑仍只用 `OsStore.draftRoute`，导航由 `MarineSystem.navigation` 单写，保存或接受规划不隐式开始导航。
- 2026-09-26 后续显示规则覆盖旧文档中“保存后自动预览”的描述：正式保存只写入我的航行，回到正常地图，不把收藏默认常驻地图。`RoutePreviewCard` 为主动预览提供地图上的 X；`ChartScreen` 的“我的航线”直接打开 `places:routes` 页签。关闭预览、保留并退出以及保存成功后的清理，均通过 Shell 同步结束编辑展示与返回快照中的编辑标记，不能只删路线却留下空编辑栏。草稿内容与编辑展示分开，保留草稿不意味着保持编辑模式；活动导航仍由原会话决定。
- 对象动作必须带稳定 ID 与来路；点击捕获时刻后才能编辑描述，成功提示等待真实落盘，同 ID 重试。原生地图/三维场景保持单实例，覆盖面板与退出页不能抢输入。隐藏航线预览统一调用 `WpShellRuntime.hideChartRoutePreview`，同时清理当前预览和返回快照中的对应路线；`finishChartRouteEditing` 只结束展示、不删除草稿。不得只清 `displayedRouteId` 后让 Back 恢复它，也不得用停止导航实现隐藏。
- LINZ 缺基准资料仅在显式参考草稿模式参与粗略找路，不补造 datum；实际路径涉及它时保留 `draftOnly / INSUFFICIENT`。预览和编辑不授予导航资格，Core 校验候选引用；完整分析仍用严格模式，见[资料边界](docs/GEOPACKAGE_CHART_PROFILE.md#能浏览不等于能自动规划)。
- 离线容器采用 [`.yklpkg` / `.yklcharts` / `.yklgeodata` 规范](chart-library/package-format.md)：外层包含一种或两种子包，`.ykl` 仍只用于应用。后缀、清单类别、原始格式严格校验。新包完整就绪才发布，失败/取消清理本事务拥有的内容，原件和旧可读版本保留；恢复按持久 requestId 对账。进度分文件和当前对象量，不伪装整体百分比。原生数据/海图优先关联 SAF 原件，只生成查询索引，提供方限制时缓存；压缩包完整校验解包。原件变更/权限丢失不能继续混用旧索引。导出冻结真实来源，不能用索引冒充原件；文件 metadata 与包级 metadata 独立。资料通过 [离线资料库](chart-library/README.md) 与 Git LFS 分发，不塞入 APK 或普通 Git 对象。包格式与渲染不能赋予数据导航资格。
- 单位统一使用 `MarineUnitPreferences` / `MarineUnitFormats`，禁止地图或页面按数值自行换单位；显示平滑不改传感器时间、质量与业务依据。
- 海事运行时产物按 [当前产物接入](docs/os/02-DOMAIN-AND-CONTRACTS.md#海事运行时产物接入2026-10-03) 演进：Core 拥有可重建几何块与规划工作集；海图/AIS 复用同一展示缓存和原生地形/交通层。暖网格须经 `validateDisplayProduct` 核验来源，不能绕过权限/修订或把显示产物当可航证据。
- 三维海图沿 `ChartDataService.acquireDisplaySnapshot` 的局部只读租约建模，不能把全国覆盖几何放进常驻目录流或显示 IPC。导航仍读 Core 会话，预览不启航；船首向、航迹向、姿态和资料地形各保留真实依据，缺测不补造。跟随、固定地图中心、自由观察只属相机状态；规则见 [三维海图契约](docs/product/CHART_INTERACTION_CONTRACT.md#三维海图与相机)。
- 动画用系统帧时钟，连续位移/旋转在绘制层读状态，不按帧重组整页；历史图不补间原始点，测量时间、来源连续段与固定回看轴遵循 [来源契约](docs/product/DATA_CENTER_CONTRACT.md) 及 [仪表契约](docs/product/INSTRUMENT_TILE_CONTRACT.md)。
- 磁贴按规范内容绑定去重、按 tileId 编辑。新固定只从应用列表或磁贴工坊发起；桌面长按只管理既有实例，不在业务 App 内放固定入口。工坊管内容，桌面管布局，应用提供真实内容；共用 Shell 临时编辑会话，不另建工坊任务。提交/撤销走原 Proto 原子回执，预览复用实际排布与渲染器；参见 [磁贴契约](docs/product/INSTRUMENT_TILE_CONTRACT.md)、[用户故事](docs/phases/tile-workshop/IMPLEMENTATION.md) 与 [生产渲染接线](docs/phases/tile-workshop/RENDERING.md)。读数图形由 `ReadingTileContent / ReadingTileFace` 承接；不要向旧 `TileFrame` 传入不存在的字段。
- 大磁贴为真实 4×4，重要航行内容用关系图及实际状态呈现；复合磁贴只能引用 `TileCompositePolicy` 的 2–4 个内容，按成员规范身份去重，标题/顺序/打开目标不另造内容身份。目的地限内部白名单，点击不隐式启停任务。列数沿 `StartDocument.profileId` 支持 4/6，通过 `SetStartColumns` 原子重排保存；桌面、拖拽、编辑预览必须使用同一 profile。
- 开始屏幕不是自由画布：`AdaptiveTilePacker` 保留列位置和局部横向空位，但收拢没有真实磁贴的整行。旧的远距离行锚点在读取排布时归整，不删除或重置磁贴；显式移动将实际格位写回，碰撞只向下让位。手势目标以拖动开始时的冻结布局为边界，不能随候选布局增高而无限扩展。UP 后 `WpStartScreen` 立即结束指针浮动，以同一 packer 的最终候选吸附落位；实际无变化的落点也必须释放临时状态，不能等一个不会产生的文档更新。悬浮视觉仍与真实网格、持久化结果分离，不能用像素坐标替代布局位置。
- Service 启动确认先于依赖图/存储恢复；后台初始化不占主线程。`hardwareLab` 恢复控制不能依赖被注入故障的业务恢复。Binder UI 超时只取消等待，已接受写命令仍按原 requestId 对账。
- 常驻采集归 `MarineSystem.residency` 与原资源协调器；Home/锁屏不释放系统采集，彻底退出必须经显式确认和持久停止闩锁。Android 常驻前台服务通知呈现真实后台任务；Yokuli 内部任务按图册准备/导出、海图导航、日志记录等所有者展示，禁止泛化“OS 在后台运行”。准星数据查询与显示设置沿用同一所选数据快照，不把图上水深变成实时传感器。通知只镜像已落盘消息，位置失联按宽限/冷却聚合，不能每次过期刷屏或自动换源。自动维护/权限刷新不发用户操作失败通知；明确命令必须带动作和真实目标。回调瞬时失败不删活会话，仅 CALL 明确未接受时才允许同 ID 附着修复重送，见生命周期文档。
- 保持 [来源契约](docs/product/DATA_CENTER_CONTRACT.md)、[导航返回契约](docs/product/APP_NAVIGATION_CONTRACT.md)、[通知契约](docs/product/NOTIFICATION_CENTER_CONTRACT.md)。Home 不停止任务；通知已读/清除不确认警报；历史/规划不能冒充实时。
- 普通 APK 与 ROM 共用领域实现；如变更进程、权限或 IPC，更新 [生命周期](docs/os/03-LIFECYCLE-AND-RECOVERY.md)、[安全](docs/os/05-SECURITY-AND-UPDATES.md) 与 [ROM 入口](rom/README.md)。同 UID 子进程不是安全沙箱，HOME APK 不是已构建 ROM。
- 本轮代码优先：不新增、修改或运行测试，不改 CI，不制作截图、录屏或验收报告。允许必要编译，保留现有构建保护。异常、持久化、权限、返回关系和资源释放必须完整。未有新授权不 push、发布、部署、刷机或破坏性清理；提交使用命令行。
- 更新受影响权威文档及 [API 索引](docs/product/API_INDEX.md)，不要再造互相竞争的“最终版”。完整索引只在完整源码 checkout 生成，不用部分副本覆盖。完成状态区分：接入生产、编译状态、IPC、ROM 配置、镜像、设备运行，不能混写。
- 可安装应用遵守 [.ykl / SDK 2 实际边界](docs/os/10-INPROCESS-SYSTEM-BOUNDARIES.md#installable-apps) 与 [开发者指南](docs/developers/index.html)。内置应用不可卸载；扩展只能走包管理、授权桥和原任务栈，不能直连 Core 私有 IPC/DAO 或加载 DEX。公共 SDK 改动同步 JS、Kotlin/JS、站点及 APK 离线资源；运行 `sdk/tools/package_extensions.py` 生成，禁止手改生成副本。每次授权/版本变化使旧会话失效。

- 可安装包正式扩展名 `.ykl`，JS 与 Kotlin/JS 共用协议；原生 APK 不在支持方向内。SDK 方法必须注册 `ExtensionSdkContract`，控制经原 Core 唯一所有者，记录用应用命名空间 requestId 查询完整持久账本。设备目录共用 `MarineSystem.devices`，演练控制共用 `MarineSystem.hardwareLab`；真实/模拟/回放经 `MarineDeviceBus` 到原解析与仲裁，不在页面制造观测。内置 `.ykl` 仅引用 APK 编译的 host-kotlin 白名单，外部包只能是 JS/Kotlin/JS web。

- 虚拟环境遵循[接入规则](docs/os/02-DOMAIN-AND-CONTRACTS.md#虚拟设备时间与持久化接入)与[真实边界](docs/os/10-INPROCESS-SYSTEM-BOUNDARIES.md#虚拟海事运行环境2026-09-27)：业务年龄/采样/等待用 `MarineTime`，宿主 IO/Binder/权限/动画保留真实时间；帧保留 backend/epoch/generation/原测量时刻。world 存储与通知隔离、重启暂停、物理输出封锁，录制审计命令不自动重执行。存储故障只覆盖已登记边界，禁止泛称全文件系统已虚拟化。
