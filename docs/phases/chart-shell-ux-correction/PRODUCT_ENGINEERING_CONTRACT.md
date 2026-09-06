# Chart / Navigation / Shell UX 产品与工程合同

## Intent / 产品愿景

Yokuli Chart 是以地图为操作界面的航海工具，不是地图资料目录。Chart Library 管理地图资源；Chart 消费其最终图层组合并完成看船位、选点、测距、标记、规划和导航。

本合同约束用户可观察结果、数据真相与危险边界，不规定类名、模块、数据库或实现步骤。历史 W09、W15 与 Chart Library 报告继续保留为审计记录；其中与本合同冲突的旧 Chart 首页动作或地图视图路由，以本合同为准。

## Experience / 用户体验

- Chart 打开后地图占据内容区；Compass、Zoom +/−、GPS/Recenter/Follow 与 `MARK · ROUTE · MEASURE · MAP` 都是地图上的一级动作。
- 点图或搜索结果必须得到可见的准星、pin 或地图对象，并在紧凑 HUD 中提供坐标与可用动作。
- Measure 是临时 A/B ruler：最多两个带 `A`、`B` 标记的可拖点、一条线、距离和明确的真/磁方位。退出即清除，不产生路线或 waypoint。
- Mark 在目标点或最新有效船位一步保存 `WP 001` 形式的 waypoint；名称、图标和备注是保存后的可选编辑。
- Route Edit 直接在地图增点、拖点、删点和插点；Save、Go、Discard 是明确结局。存在未保存 geometry 时，离开、关闭 Chart 或启动另一导航都必须先 Save / Discard / Cancel。
- `MAP` 只切换 Marine、Standard、Satellite。Chart Library 中的 folder、MBTiles、validation、permission 和 scan 不得回到 Chart 主地图。
- Marine 使用 Chart Library 的本地图层；Standard/Satellite 仅在 Google Android key 已配置时可选。配置只证明非占位 key 被注入，不证明 API、账单、签名限制、网络或图块加载可用。

## Domain / 领域关系

```text
Chart Library -- shared chart display plan --> Marine View --> Chart
Phone/NMEA position -------------------------> Follow/DTW/BTW
Saved waypoint/route ------------------------> durable user data
Chart UI session ----------------------------> target/measure/draft editor
```

`Map View`、保存数据、导航运行时与 Chart UI session 是不同状态。关闭 UI session 清除 transient target/measure/selection，但不得删除 waypoint、saved route、Chart Library、language 或 preferences。只要保存 route draft，就必须同时完整恢复它的 pins、line、HUD 和 edit mode；否则必须完整丢弃。

## Opportunities / 可发展空间

未来可以增加磁方位、航海对象类型、更多海图组合和高级路线信息，但不得让这些扩展阻塞一键 Mark、两点测距、基础路线和 Direct-To。可视化实现、持久化技术与内部抽象由当前代码结构决定。

## Non-negotiables / 不可协商边界

- Chart 生产首页仅有四个底部地图动作：Mark、Route、Measure、Map View。
- Target、measurement point、route point 与 waypoint 均不得成为 invisible selection。
- A/B measurement 永远不得包含第三点；Route 与 Measure 互斥。
- 搜索 waypoint 的结果必须回到地图选中对象，不得跳进资料管理页。
- Save/Discard route 后必须回到地图 root，并清除对应 transient editor state。
- Recents 的 Close 只关闭 UI session；有非空 route draft 时必须先得到显式决定。
- 两个 1x1 tile 可横向或纵向相邻，位置由二维 Start Document 决定。
- 顶部 status strip 只显示时间、电量和状态，不是隐藏的 Preferences 入口。
- Shell Back 的终点是应用内 Desktop；生产 Activity 竖屏且不注册 Android HOME。
- Effective language 是唯一语言真相；中文 App 名使用显式 Latin 拼音索引，海图与海图库均属于 `H`。
- Chart Library Basic 回答“当前能否读取并用于地图”；Full 是可选深入验证。失败、取消、权限丢失或 partial scan 不得批量抹掉上一次有效 catalog。

## Forbidden outcomes / 禁止结果

- Measure 变成多段路线编辑器，或把 undo/redo/convert-to-route 暴露为默认测距流程。
- Route geometry 留在状态中，但 pins、line 或 edit HUD 消失；Discard 后旧 geometry 复活。
- Chart 主地图出现 folder URI、MBTiles 文件表、Basic/Full、SAF permission 或 resource admin。
- Standard/Satellite 被旧本地海图选择暗中遮蔽，或缺少 key 时仍假装可用。
- 整个 status strip 可点击进入 Settings；Recents 只能切换不能 Close。
- 1x1 tile 被组件强制成横向二连；中文索引出现汉字；Preference 与首帧实际语言不一致。
- Full Validation 成为可读海图的使用前提；不完整 scan 把旧资源整体标成 missing。

## Acceptance stories

- `MAP-01`：进入 Chart，地图是主体；四个底部动作、GPS、Zoom 与 Compass 直接可见。
- `MAPSEL-01`：在 Standard/Satellite/Marine 之间切换时使用所选视图；没有 key 时在线视图不可选且文案只说未配置。
- `TARGET-01`：点地图或打开搜索 waypoint，同一地图交互中出现可见对象与坐标。
- `MEASURE-01`：有船位时点击 Measure 立即出现 A/B；拖点实时更新线、距离与带 `T/M` 的 bearing；第三点被拒绝。
- `MARK-01`：不填 category、tag 或 notes 即可生成并显示自动编号 waypoint。
- `ROUTE-01`：地图点击立即增加 route point 和 leg；Save/Discard 后无 ghost state。
- `NAV-01`：Direct-To 或 Go 启动前不会静默吞掉未保存 route；Active Navigation 有一级 Stop。
- `LIB-01`：同一个已知良好 MBTiles 可通过 Basic 和 Full；Basic PASS 即可参与 Marine view。
- `LIB-02`：partial/failure/cancelled scan 保留上次有效资产；错误包含对象、原因和恢复动作。
- `SHELL-01`：两个 1x1 tile 可在同一列上下摆放；Recents 可关闭 session；status strip 只读。
- `LANG-01`：中文 preference 在冷启动首帧生效，海图库位于 `H`，alphabet overlay 只显示 A-Z。

## Evidence required

- 本合同的静态 Gate 必须参与 Android CI 最终质量门禁并进入统一 `CODEX-CI-REPORT`。
- Core/Feature 的窄 JVM 测试验证 A/B 上限、互斥模式、搜索回地图、route ghost 清理、二维磁贴和视图路由。
- APK/Activity Gate 继续验证 portrait、无 HOME、Back 不退出、真实地图 adapter 与 release surface。
- Google Maps configuration evidence 只能报告配置一致性；真实图块加载仍需用户设备与 Google Console 限制匹配后的人工证据。

## Existing code landmarks

重点入口包括 Map reducer/state、Chart workspace、Google/MapLibre renderer、Shell ViewModel、Start layout、Chart Library scan/validation 与生产 composition root。它们是审查线索，不是强制的最终组织结构。

## Implementation freedom / 实现自由

实现可以复用或重构现有边界，也可以减少旧页面和抽象；只要上述 invariants、forbidden outcomes 与 acceptance stories 全部成立，内部 solution space 保持开放。

## English translation

Yokuli Chart is a map-first marine chartplotter, while Chart Library owns resource administration. The product must keep target, two-handle measurement, waypoint, route editing and active navigation visible and directly manipulable on the map. UI-session state must never outlive its matching editor as ghost geometry. Shell sessions are closeable without deleting durable data, Start tiles use a real two-dimensional document, the status strip is display-only, Back stops at the in-app Desktop, and language/index presentation has one truth. Engineering structure is intentionally open; observable behavior, safety boundaries and CI evidence are not.
