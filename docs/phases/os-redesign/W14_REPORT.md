# W14 报告 / OS Live Tiles V2

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`2d649380498867e4c8495633fb62f175169d9453`

## 产品结果

- Chart 提供 AUTO/MAP/NAVIGATION/POSITION/STATIC 五种 typed 模式，默认 AUTO 按真实导航、地图快照和位置事实确定性降级；活动导航与地图快照以五秒节奏轮换。
- 真实 Google/MapLibre renderer 最多每五秒生成一次 320×180 范围内的 JPEG 缩略图；进程级单槽 cache 拒绝旧请求、空内容、超尺寸和超字节结果。
- Chart Tile 不创建地图 SDK，不再使用 route draft mini-map；关键写入/renderer 告警优先于用户选择的展示帧。
- Chart Library 展示当前 display plan 的可见 layer 数、最多三个名称及 omitted/issue warning。
- Data 从系统最终 resolved values 中选择最多三个位置/运动/艏向/水深/风值，不展示 raw NMEA 流。
- Navigation 活动时呈现 next waypoint、DTW、BTW、XTE；未活动时呈现最近保存航线摘要，并服从全局显示单位。
- 普通数值统一使用一秒 StartTile cadence；编辑态冻结装饰变化但不冻结告警进入/清除。Preferences 磁贴保持静态。
- Preferences App Tiles 现在根据安装 App 的双语 typed definition 渲染并保存 Chart 磁贴模式。

## DESIGN DECISIONS

- 复用 W13 的 installed-app preference seam，没有让 Preferences import Chart；Chart 自己声明 key、合法选项和双语标签，Shell 只组合 registry。
- 复用两个真实 Chart renderer 的 SDK snapshot callback，没有在桌面复制地图 lifecycle，也没有新增 snapshot repository 或磁盘缓存。
- snapshot 使用单槽 process cache；它是可丢失的展示事实，进程恢复后诚实退化，不持久化位图。
- 各 Feature 保留纯 projector；cadence 只放在 Compose 展示边界，domain/runtime 仍按原速接收和计算。
- 最近路线沿用 Navigation library 的稳定顺序取最后保存项，没有新增“最近使用”伪字段。

## 刻意未实施

- 不把地图快照可用解释为 Google 网络/API/key 已验证。
- 不持久化磁贴位图，不缓存多帧历史，不让磁贴自己扫描海图或建立 socket。
- 不增加用户可选刷新频率；当前固定边界优先保证性能和一致性。
- 不在 W14 删除历史 Settings/NMEA/Data Sources 模块或入口兼容代码；由 W15 单独审计。

## Pipeline 证据

- 新增 W14 static contract 与 map snapshot、Chart policy、Chart Library、Data、Navigation、App preference 测试源码。
- 提交后只运行受影响源码/测试源码的窄 compile；完整 test/lint/emulator/release 由 W16 GitHub Actions 承担。

## English translation

W14 delivers app-owned, bounded live tiles backed only by existing runtime truth. Chart consumes a renderer-produced single-slot thumbnail, Data consumes selected resolved values, Chart Library consumes the display plan, Navigation consumes active or saved-route facts, and all routine tile updates are bounded without delaying structural alerts.
