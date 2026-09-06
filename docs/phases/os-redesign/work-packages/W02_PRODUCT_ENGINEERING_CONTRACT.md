# W02 · Live UI Foundation — Product & Engineering Contract

## Intent / 产品愿景

Yokuli 的实时页面应该像稳定的航海仪表：数值持续前进，但页面结构不抖、不反复入场，也不会因为 20 Hz
输入制造 20 次肉眼更新。W02 建立统一的实时呈现语言，而不是改变任何数据源、选源或安全判断。

## Experience / 用户体验

- NMEA 状态、消息速率和 age 稳定更新；连接断开、输入无效或过载立即出现。
- Data Sources 的当前值保持对齐，数字位数变化不会让周围文字左右跳动。
- raw preview 像一个安静的控制台：批量刷新、最新优先、有上限，没有每句入场动画。
- Chart 的位置、航向/航迹和航速在地图上清楚可读；状态变化立即，数值帧最多按地图所需节奏显示。
- 页面导航、过滤、选择、来源状态等结构变化不等待 live cadence。

## Domain / 领域边界

- source truth 与 presentation truth 分离。cadence 可以丢弃中间显示帧，不得丢弃或改写 runtime 数据。
- structural/safety change 与 live frame 是不同输入语义；前者立即呈现，后者 latest-wins。
- 数值几何、控制台批次和更新节奏是设计系统能力，不属于 NMEA socket、Map runtime 或 Feature 生命周期。

## Opportunities / 可发展空间

相同能力可供后续 Data topology、Navigation instrument、Start Live Tile 使用。具体消费者可以选择不同 cadence，
也可以在性能证据支持时调整频率；不要求所有实时内容共用一个全局 ticker。

## Non-negotiables / 不可协商边界

- Chart position 最多 5 Hz；Data overview/raw preview 最多 4 Hz；NMEA rate/age 最多 1 Hz。
- disconnect、invalid、stale、overload、selection/availability change 不得被 routine throttle 延迟。
- raw presentation 只保留有界的最新 snapshot，不建立逐帧 UI 队列。
- live 数值不得使用 `wpEntrance`；数字采用稳定最小宽度与 monospace/tabular-feeling geometry。
- Feature UI 不拥有 runtime，不因页面离开而 stop socket，也不改变 freshness/selection 语义。

## Forbidden outcomes / 禁止结果

- 通过降低 NMEA/position ingestion 速率来“修复”UI。
- 断连仍显示 receiving，或 stale transition 等到下一个 routine cadence。
- 每条 raw sentence 新建动画、无限排队或推动整个页面 re-enter。
- Chart 把 COG 显示成 heading，或在没有有效 position 时生成数值。

## Compatibility / 兼容策略

W02 只替换现有 NMEA Input、Data Sources 和 Chart 的显示 primitive。所有 coordinator、runtime、持久化、
launch token、Back/Start 与 portrait/fullscreen 合同保持不变。W03 合并 Data 时可以直接复用此 presentation contract。

## Acceptance stories

1. 注入单调时钟并输入 20 Hz 数值，一秒内 Data 可见 live 更新不超过 4 次，最终显示最新值。
2. 在 cadence 窗口内收到 disconnect，UI 立即显示断连，不等待下一 tick。
3. freshness 到达 stale 边界时立即切换结构状态；旧值只能以历史语义显示。
4. raw preview 面对持续输入仍保持固定容量，并在下一批次显示最新 snapshot。
5. NMEA rate/age、Data resolved/candidate value 与 Chart position 数字使用同一 live primitive，且源码不把实时帧作为 entrance key。
6. 中英文 APK 中 Chart 的位置/航向/航迹/航速语义完整对等。

## Evidence required

纯时钟 cadence 测试、四类行为故事、三个 Feature 的使用合同、资源翻译检查、现有完整 unit/lint/build/device gate，
以及精确 SHA 的 `CODEX-CI-REPORT`。W02 不把 emulator 结果冒充物理设备性能结论。

## Existing code landmarks

`core:design` WP primitive、NMEA Input workspace、Data Sources workspace、Chart position truth strip 与当前
StateFlow coordinators 是相关入口提示。最终内部组织由施工者根据现有依赖方向决定。

## Implementation freedom / 实现自由

可采用纯状态机、Compose effect、Flow operator 或其它满足测试的仓库内方案；不强制新增 module/class/repository。
实现不得为了匹配文档名建立平行数据真相。

## English translation

W02 makes live UI feel like a stable marine instrument. Structural and safety facts are immediate; routine numeric frames are latest-wins and cadence-bounded. Chart position is capped at 5 Hz, Data and raw preview at 4 Hz, and NMEA rate/age at 1 Hz. The policy changes presentation only: it does not throttle ingestion, own runtimes, reinterpret marine data, or delay disconnect/invalid/stale truth.
