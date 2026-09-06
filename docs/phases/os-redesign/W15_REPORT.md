# W15 报告 / Legacy Product Surface Removal

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`893ea217947f9b8350d9a164a522bafd0c16435f`

## 产品结果

- 生产目录保持且锁定为 Chart、Preferences、Data、Chart Library、Navigation 五个 App。
- Chart 根操作收敛为测距、Quick Layers、十字准星；选点不再创建航点或航线，测距不再转换成 Chart 航线。
- Navigation→Chart 的 route handoff 直接落到 Root 地图并选择同一 route geometry，不打开第二个路线管理页，也不启动导航。
- Chart 的 waypoint handoff 只读；编辑、移动、删除、GPX/export 和 route-from-here 均不再出现。
- Navigation 提供自己的 GPX state/action/workspace；Shell 只把已有、受限且事务性的文档 runtime 适配给它。
- Chart 的 Search contribution 从生产 composition 移除，航点/航线搜索由 Navigation 单一 owner 提供。
- `feature:settings` 与 `feature:data-sources` 离开活动 Gradle 图；`feature:nmea-input` 作为 Data 的内部 Inputs 工作流保留。
- Release APK gate 改为要求 Preferences 并拒绝旧 Settings/Data Sources classes，同时继续要求内部 NMEA 与生产 runtimes。

## DESIGN DECISIONS

- 选择收敛“正式可达路径”并保留底层旧领域模型：W10 的兼容 adapter 仍要读取同一 Room schema，物理删除会制造迁移风险而没有产品收益。
- 复用已有 GPX parser/coordinator 与单一 durable library，只把产品 UI owner 移到 Navigation；没有创建第二个 importer 或数据库。
- route handoff 复用现有 `PreviewRoutePlan` 语义，但把结果 surface 改为地图 Root；这是对象级 handoff，不是 App 间共享 UI state。
- 保留 `feature:nmea-input` 模块，因为它已是 Data 的内部真实工作流；“不再是独立 App”不等于删除能力。
- 没有引入抽象 repository 或跨 Feature 框架，composition root 只做有限的 typed UI 投影。

## 兼容与未验证项

- 旧 Settings/NMEA/Data Sources token alias、Room schema、mapper 和 fixture 均保留。
- 历史模块源码暂留作审计证据，但不再被 settings/app-shell Gradle 图打包；源码存在不代表产品注册。
- 本轮只交付测试源码和 W15 CI gate；完整执行、Release dex 审计与设备故事统一由 W16 Actions 完成。
- 真实历史数据库升级与物理设备仍需 W16 人工账本，不由静态合同冒充。

## English translation

W15 reduces production to one surface per user goal: five installed apps, a focused Chart, Navigation-owned GPX UI, direct route-to-map handoff, read-only waypoint context, and no active Gradle dependency on legacy Settings or Data Sources. Existing schemas, aliases, runtimes, and compatibility fixtures remain intact. Full authoritative execution is pending GitHub Actions.
