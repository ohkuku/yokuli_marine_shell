# W14 Product & Engineering Contract — OS Live Tiles V2

## Intent / 产品愿景

Start 是 Yokuli OS 的航海摘要面，不是把五个 App 的页面缩成小卡片。每个 App 自己决定磁贴能表达什么；Shell 只提供统一尺寸、主题、编辑状态和刷新边界。用户抬眼应看到下一步航行、已采用的数据、当前地图世界和真实问题，同时不会因为高频 NMEA 或地图渲染拖慢桌面。

## Experience / 用户体验

- Chart 的 AUTO 模式在活动导航时缓慢轮换“下一航段”和最近真实地图快照；无导航时依次退化为地图、位置、静态图标。
- 用户可在 Preferences 的 App Tiles 中选择 Chart 的 AUTO、MAP、NAVIGATION、POSITION 或 STATIC；缺少所需事实时诚实回到静态状态。
- Chart Library 宽磁贴说清当前可见图层数量、最多三个名称和警告，而不是重复文件扫描控制台。
- Data 展示系统最终采用的位置、航迹/航速、艏向、水深或风等最多三个重要值；不滚动原始句子。
- Navigation 活动时展示 next waypoint、DTW、BTW、XTE；未活动时展示最近保存航线摘要。
- Preferences 磁贴保持静态。用户编辑 Start 时，装饰性实时刷新冻结，但新的安全告警立即可见。
- 所有普通数字最多每秒呈现一次；Chart 的多帧轮换固定五秒，不制造不断跳动的桌面。

## Domain / 领域关系

- App preference 由安装 App 通过 typed、bounded contribution 声明，Preferences 只投影合同并保存合法值。
- 地图缩略图属于进程级、单槽、有界的 `MapTileSnapshot` 事实，由当前 Chart renderer 产生；Tile 只解码这份快照。
- Chart、Chart Library、Data、Navigation 的投影各自只读已有 domain/runtime snapshot，不拥有 socket、地图 SDK、文件扫描或导航会话。
- presentation cadence 只节流 UI 呈现，不修改、延迟或伪造 domain truth。

## Opportunities / 可发展空间

后续 App 可以在同一 bounded contract 中贡献新的 glance mode，Chart 快照可增加明确的过期标识，Navigation 可按空间允许展示 ETA，Data 可让用户选择三项重点数据。扩展必须保留来源可解释性、刷新上限和缺失事实的诚实降级。

## Non-negotiables / 不可协商边界

- Tile 不得创建 GoogleMap、MapView、MapLibreMap 或新的地图 lifecycle。
- 快照 payload 必须有字节和尺寸上限、defensive copy，并拒绝晚到 renderer callback 覆盖新请求。
- Chart AUTO 决策必须确定：active navigation 优先 NAVIGATION（有快照时与 MAP 轮换），否则 MAP、POSITION、STATIC。
- route draft mini-map 不再是 Chart 磁贴默认内容。
- Data 只显示 resolved selected values，不得把候选、raw NMEA 或 sentence inventory 冒充 OS 输出。
- Chart Library 的可见图层来自当前 display plan，不从文件数推断。
- Navigation 数值遵守 Preferences 的显示单位，不改变底层海里数学。
- 结构/安全告警立即呈现；普通数值采用一秒 latest-wins cadence；帧轮换只能在 4–6 秒。
- live content 关闭或 Start 编辑时冻结装饰更新，但告警出现和清除均不能被冻结。

## Forbidden outcomes / 禁止结果

- 禁止磁贴自己联网、打开数据库、扫描 folder、建立 socket 或启动导航。
- 禁止把 Google Maps 配置状态当成地图快照可用或图块加载成功。
- 禁止为了动画缓存无限位图、帧队列、NMEA 行或历史值。
- 禁止 Chart MAP/NAVIGATION 显式模式在事实缺失时编造内容。
- 禁止 live tile 偏好使用无类型字符串开关或让 Preferences 依赖 Feature runtime。
- 禁止高频输入触发整个 Start topology 重建动画。

## Acceptance stories

1. 活动导航且已有 renderer 快照时，Chart AUTO 在 NAVIGATION 与 MAP 两帧间每五秒轮换；无快照时只显示导航。
2. 无导航但有地图快照时显示地图；无快照但有新鲜位置时显示位置；都没有时显示静态 Chart。
3. 用户在 Preferences 选择 STATIC 后，保存并重建 Activity 仍保持静态；非法持久值恢复 AUTO。
4. Chart 页面产生的真实 SDK 快照被缩放、JPEG 编码并放入单一有界 slot；旧请求和超限 payload 不替换当前有效快照。
5. Chart 写入失败等结构告警立即覆盖任何偏好帧，不等一秒或五秒。
6. Chart Library 显示 display plan 的 layer names/omitted/issues；扫描计数不冒充可见图层。
7. Data 只从 resolved data 选取最多三个重要值，不在磁贴展示 raw sentence。
8. Navigation 活动时呈现 next/DTW/BTW/XTE，未活动时呈现最近保存路线的点数和距离。
9. Preferences 仍是静态磁贴；Start 编辑冻结普通变化而不冻结新的或已清除的告警。

## Evidence required

- core JVM：snapshot 的大小、尺寸、复制和 stale-request 边界；app preference 类型与 owner 边界。
- feature JVM：Chart mode policy、Chart Library display plan、Data resolved-only、Navigation active/recent、edit freeze/alert。
- compile：Google 与 MapLibre renderer 都能产出同一 snapshot contract；Shell composition root 注入唯一 runtime。
- static：Tile source 无地图 SDK；1s/5s cadence；Preferences 无 peer Feature/runtime 依赖；双语资源、报告、lock/state/workflow 完整。
- 完整 unit/lint/device/API34/API36/soak/release gate 统一留给 W16 Actions。

## Existing code landmarks

- `LauncherCatalogContribution.appPreferences` 和 W13 Preferences 已提供 App-owned typed preference seam。
- `rememberCadencedLiveValue`、`PresentationCadence.StartTile` 已提供 latest-wins UI cadence。
- Google 与 MapLibre surface 已由 Chart host 管理真实 renderer lifecycle。
- `MapState.chartDisplayPlan`、`MarineSourceSnapshot.resolvedData`、`ActiveNavigationSnapshot` 是现有事实来源。

## Implementation freedom / 实现自由

在遵守有界内存、事实来源、刷新节奏、Feature 依赖和验收故事的前提下，实现者可自由决定投影类型、Compose 布局、快照编码位置和 composition-root wiring。不得为了符合示例类名建立平行 repository、第二套导航状态或第二套地图 lifecycle。

## English translation

W14 makes Start a bounded glance surface owned by each installed app. Chart uses truthful auto/explicit modes and a renderer-produced cached thumbnail; Chart Library reports visible layers, Data reports selected resolved values, Navigation reports active guidance or the recent saved route, and Preferences remains static. Routine numeric presentation is latest-wins at no more than 1 Hz, frame rotation is five seconds, and structural safety alerts remain immediate.
