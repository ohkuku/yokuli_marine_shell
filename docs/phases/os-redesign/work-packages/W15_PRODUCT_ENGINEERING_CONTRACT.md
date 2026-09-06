# W15 Product & Engineering Contract — One Product Surface per User Goal

版本：1 · 2026-09-07  
状态：施工候选

## Intent / 产品愿景

Yokuli OS 不应把曾经用于分阶段验证的每个技术模块都留成一个 App，也不应让 Chart 继续充当所有海事资料的管理中心。
用户从 All Apps 看到的五个入口，必须分别对应五个清楚的目标：看地图、管理地图内容、理解数据、规划航行、调整偏好。

W15 收敛的是产品可达面，而不是破坏性删除历史数据。已经迁到 Data、Navigation、Chart Library 和 Preferences 的工作流只保留一个
正式入口；旧 token、数据库 schema 与读取夹具继续承担升级兼容职责。

## Experience / 用户体验

- All Apps 永远只显示 Chart、Chart Library、Data、Navigation、Preferences 五个真实 App。
- Chart 打开就是实时地图；底部只保留测距、Quick Layers 和十字准星，不再出现地点库、航线库或 GPX 管理捷径。
- 在地图上选点可以测距或复制坐标；要创建航点/航线，用户进入 Navigation，避免两个地方维护同一对象。
- Navigation 的“在地图中显示”直接打开同一条航线的地图几何，不先落入 Chart 的第二套路由详情/编辑器。
- “在 Chart 中显示航点”可以提供只读上下文和定位，但编辑、删除、导出仍只在 Navigation。
- GPX 页由 Navigation 自己呈现选择、预览、重复摘要和原子导入反馈；Shell 只适配已有文档 runtime，不注入 Chart UI。
- 升级后旧 Settings/NMEA Input/Data Sources 磁贴和 token 迁到新 App，不崩溃、不清库，也不重新露出旧 App。

## Domain / 领域关系

- `YokuliProductModel.finalApps` 是产品身份集合；`productionInstalledApps` 是唯一运行时安装集合。
- NMEA Input 是 Data 的内部 Inputs 工作流，不是一等 App；其 process runtime 与连接数据不因入口收敛而删除。
- 旧 map library 仍是 W10 Navigation compatibility adapter 的 durable truth；删除 Chart 管理入口不等于删除地点、航线、轨迹或 GPX receipt。
- Chart 接受对象 handoff 后只投影视口/几何。对象管理事务属于 Navigation；海图资源事务属于 Chart Library。
- Feature 之间不互相依赖。Shell composition root 可以适配端口和 UI facts，但不能形成第二份业务状态。

## Opportunities / 可发展空间

将来可以让 Search 更完整地聚合 Navigation 航点、轨迹和 Chart Library 内容，也可以为 Chart handoff 加入更丰富的只读位置卡片。
这些发展应继续遵循单一 owner 与对象级 handoff；本轮不为未来想法预建跨 Feature coordinator 或通用页面框架。

## Non-negotiables / 不可协商边界

- 生产 registry 恰好五个 App；旧 Settings、NMEA Input、Data Sources 不得注册或打包成独立产品入口。
- `feature:settings` 与 `feature:data-sources` 不再进入活动 Gradle 图；历史源码可留作只读迁移证据，不能被 Release 依赖。
- `feature:nmea-input` 保留为 Data 内部能力；不得为了“移除旧 App”而删 socket、profile、runtime 或诊断数据。
- Chart root 不得提供 place/route/GPX/track/offline-coverage/source 管理入口。
- Chart place handoff 只读；route handoff 必须落到地图预览且不得启动导航。
- Navigation GPX UI 不依赖 Chart Feature；任何 Feature 不依赖 peer Feature 或 Android adapter。
- 旧 token migration、Room schema、兼容 mapper 与读取 fixture 必须保持。
- Release 二进制审计必须要求五个正式 workspace、内部 NMEA workflow 与生产 runtime，并拒绝旧 Settings/Data Sources contribution。

## Forbidden outcomes / 禁止结果

- 通过删除数据库表、清空 DataStore 或丢弃 legacy alias 来“清理”旧入口。
- Chart 和 Navigation 同时提供航点/航线 CRUD，或 Chart 和 Chart Library 同时提供文件管理。
- 从 Chart 管理页跳到另一个管理 App；正常切换仍通过 Start、All Apps 或 Search。
- 因移除独立 NMEA App 而停止后台连接或让页面生命周期拥有 runtime。
- 复制 GPX 数据到第二个库，或让 Shell 保存另一份 preview/selection 真相。
- 为满足旧 UI 测试而保留已被新产品合同替代的入口。

## Compatibility / 兼容策略

产品迁移版本继续把 `nmea-input`/`data-sources` 映射到 Data，把 `settings` 映射到 Preferences。旧 Chart 的领域 action、schema 和
兼容 mapper 可以继续存在，保证历史库可读并给 Navigation adapter 使用；没有正常生产 UI 能再进入其管理页面。Release 只从活动
Gradle 图打包当前五 App，旧模块源码不会参与 APK。

## Acceptance stories

1. 新安装用户在 All Apps 只看到五个正式 App；Start 仍只保留用户合同规定的默认磁贴。
2. 用户在 Chart 选中海面点，只能测距、复制或关闭；不会在 Chart 创建航点/路线。
3. 用户在 Navigation 打开保存航线并选择“在地图中显示”，Chart 直接显示其几何；导航不会自动开始。
4. 用户通过航点 handoff 打开 Chart，只能查看名称、分类、备注、标签、坐标并定位，不能编辑、移动、删除或导出。
5. 用户在 Navigation 的 GPX 页预览项目、处理重复文件并确认；页面和 action 合同均由 Navigation 提供。
6. 升级设备上旧 Settings/NMEA/Data Sources token 迁往新 owner，旧数据库内容仍可读。
7. Release APK 包含 Preferences/Data/Chart Library/Navigation/Chart 与内部 NMEA runtime，不包含旧 Settings/Data Sources UI class。

## Evidence required

- JVM：route handoff 落到 Root 且不启动导航、旧 token migration、Navigation GPX state/action。
- API 34：Navigation GPX 核心故事、Chart 三操作根面、route/place handoff。
- Static：五 App registry、活动 Gradle 图、Feature 依赖方向、Chart 可达面、Release 二进制 allow/deny list。
- W16 Actions：完整 unit/lint/API34/API36/process restore/soak/release surface；本轮不在本地重复执行。

## Existing code landmarks

优先审阅生产安装表、产品迁移计划、Chart root command/对象 handoff、Navigation GPX composition、活动 Gradle 图和 Release APK 审计。
这些是可能相关的现有入口，不规定最终类名、文件数量或内部组织方式。

## Implementation freedom / 实现自由

可以隐藏、移动或重组旧 UI，只要正式用户路径只有单一 owner、兼容数据不被破坏、Feature 依赖方向保持、验收故事可执行。
不要求物理删除所有历史模型，也不允许仅靠改文案掩盖仍可达的重复工作流。

## English translation

W15 makes each first-class app correspond to one user goal. Production exposes exactly Chart, Chart Library, Data, Navigation, and Preferences. Chart becomes a focused map with measure, Quick Layers, and crosshair; route handoff lands directly on the map, waypoint context is read-only, and Navigation owns the GPX product UI. Legacy modules leave the active build graph while token migration, data schema, compatible adapters, and read fixtures remain intact. The solution may choose its internal structure freely inside strict product reachability, dependency, binary, and non-destructive migration boundaries.
