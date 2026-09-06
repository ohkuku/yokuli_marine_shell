# W12 Product & Engineering Contract — Navigation App

## Intent / 产品愿景

Navigation 是 Yokuli 中独立的航程规划与执行空间。用户进入它时，应当感觉自己在整理一次真实航行：保存要去的地方，把地点编成有顺序的航线，理解每一航段，然后明确开始、暂停、继续或结束航行。它不是 Chart 的侧栏，也不是把若干地图按钮换一个标题。

Navigation 可以逐步长成航行计划、航程版本、共享与复盘的中心；本轮只把已有可靠数据能力组成一个可用闭环，不虚构自动驾驶、船位、天气或联网服务。

## Experience / 用户体验

- 概览呈现库的真实规模、最近保存的航线和当前航行入口；没有内容时直接说明为空。
- 航点可以新建、编辑和删除；坐标必须通过领域校验，失败不得产生半条记录。
- 航线由至少两个确定位置组成。编辑器可以从已保存航点添加、调整顺序、移除、填写计划航速与备注。
- 航线详情同时给出轻量几何预览和每一航段的 WGS84 距离、真方位；它不是伪造的海图底图。
- “开始导航”是明确的用户动作。打开 Navigation、打开 Active 页面或打开旧深链都不得自动开始。
- Active 页面优先展示 Next、DTW、BTW、XTE、ETA 和全程进度；没有可用实时位置时必须显示等待，而不是缓存值冒充 live。
- “在地图中显示”把指定航线内容交给 Chart，并保留返回关系；它不是从一个管理页跳到另一个管理页。
- GPX 使用当前已经验证的文档选择、预览、重复检测和原子导入流程。W12 在 Shell composition root 注入这段能力，Navigation feature 本身不依赖 Chart feature。
- App 内 Back 先退出编辑/详情，再回到概览；概览根部才交给 Shell 返回 Start，永不退出 Yokuli OS。

## Domain / 领域关系

- `NavigationLibrary` 是航点、航线、导入轨迹与 GPX receipt 的持久真相；W12 不创建第二份库。
- 航线保存点位快照，并可携带创建时的航点 revision 引用。后续航点被修改或删除不能静默改变既有航线几何。
- `ActiveNavigationRuntimePort` 是活动会话与实时解算的唯一真相；Feature 只投影状态并发送 typed command。
- Launcher 安装关系由一个 `InstalledAppBinding` 贡献目录、LaunchToken、视觉、搜索和 host；动态航线 token 只标识内容。
- Chart handoff 的对象是 route id；Chart 从同一持久库解析它，不复制航线，也不让两个 Feature 共享 UI state。

## Opportunities / 可发展空间

后续可以在不破坏这些合同的前提下加入航点分类/搜索、航线反转和复制、路线风险提示、航行日志、GPX 导出、更丰富但有界的地图快照，以及按 vessel profile 计算的计划信息。任何未来能力都必须说明输入来源和数据新鲜度。

## Non-negotiables / 不可协商边界

- 所有写操作必须经过 revision-checked `NavigationLibraryPort`；存储冲突不得覆盖新版本。
- 操作和 effect 队列有界，命令串行；Compose 不直接修改数据库或活动 runtime。
- 活动航线不能从库中删除；必须先停止活动会话。
- 失效/已删除 route token 留在 Navigation 并显示不可用，不崩溃、不启动导航。
- Feature 不依赖 Android adapter、app-shell、feature:chart 或其他 Feature；跨 App 能力由 composition root 注入。
- GPX 导入沿用同一 Room library 与原子提交语义；读取失败不能清空已有库。
- 不改变 Shell 的竖屏、全屏、虚拟实体键和 Back 合同。
- Navigation 支持 WP 三种磁贴尺寸，内容由 Navigation 自己贡献；生产默认 Start 不替用户自动 pin 新 App。
- 中文为主并提供完整英文资源；生产 UI 不出现 roadmap/coming soon 文案。

## Forbidden outcomes / 禁止结果

- 禁止让 Navigation UI 拥有 socket、GNSS permission、GoogleMap 或文件数据库。
- 禁止使用 planned speed 假装实时 SOG，或用 route point UUID 当作用户可读的下一点名称。
- 禁止自动开始、自动恢复为 ACTIVE、静默切换数据源或发出 autopilot/NMEA output。
- 禁止把 route preview 宣称为海图，禁止创建 Google Maps 实例只为磁贴或列表。
- 禁止注册空入口，禁止重新注册独立 NMEA Input/Data Sources，禁止提前注册 Preferences。
- 禁止改写 W01/W03/W04 历史 BASELINE_LOCK；当前生产 App 数变化由 W12 显式接管。

## Acceptance stories

1. 用户创建两个航点，将它们组成航线、调整顺序并保存；重开详情后看见相同点位、总距和逐航段真方位。
2. 另一个写入者先推进 library revision 时，当前编辑保存显示冲突，绝不覆盖新数据。
3. 用户明确开始保存航线后进入 Active；暂停、继续、上一/下一航段和停止均发送 typed command。
4. 活动航线的删除入口不可用；即使直接发删除 action，Coordinator 仍拒绝。
5. 用户选择“在地图中显示”后 Chart 预览同一个 route id；Back 可回到 Navigation 调用关系。
6. 用户打开已经删除的航线深链，看见“航线不可用”，不会崩溃、不会开始导航。
7. 用户从 GPX 页面选择文档并完成原子导入；Navigation 刷新同一 library 后看见新内容。
8. Navigation 的小/中/宽磁贴在统一主题色中分别呈现图标、库摘要或真实活动导航摘要，不创建地图 renderer。
9. Navigation 的编辑/详情 Back 由 App 消费；概览根 Back 返回 Start。

## Evidence required

- JVM：目的地 token、三尺寸贡献、CRUD/route editor、冲突、活动航线删除保护、typed controls、Chart effect 与 Back 合同。
- API 34：五个工作区、路线几何/leg、显式开始与 handoff、失效深链。
- Composition：生产只有五个已完成 App，Navigation 只注册一次，默认 Start 仍由用户控制。
- Static：Feature 依赖方向、禁止输出/假数据/peer Feature、合同/报告/lock 与 W12 workflow gate。
- W16 才执行完整 unit/lint/API34/API36/performance/restore/release surface；本轮仅提交后做受影响源码编译。

## Existing code landmarks

- `core/navigation-domain`：W10/W11 冻结的库、路线数学和活动会话合同。
- `adapter/map-storage`：同一 Room library 的导航端口实现与 legacy 兼容映射。
- `feature/navigation`：W11 的真实 Active strip 与 W12 的 App surface。
- `feature/chart`：当前经验证的 GPX 文档流程和临时保留的兼容管理 surface。
- `app-shell/ProductionShellGraph.kt`：唯一 composition root 和内容 handoff。

## Implementation freedom / 实现自由

在以上体验、不变量、禁止结果和证据合同内，具体状态组织、Compose 拆分、队列实现、预览绘制和测试夹具均由实现者根据现有代码选择。不得仅为匹配文档而新增平行 repository、runtime 或数据库。

## English translation

W12 defines Navigation as Yokuli's independent passage-planning and execution workspace. It owns truthful waypoint and route management, revision-safe editing, explicit active-navigation controls, bounded app state, GPX import composition, three WP tile sizes, and an object-level Show in Chart handoff. The implementation may evolve freely inside strict data, safety, dependency, migration, and evidence boundaries.
