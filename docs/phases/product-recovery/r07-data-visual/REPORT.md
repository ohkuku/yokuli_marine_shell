# R07 — Data Visual Rewrite 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI / HUMAN ACCEPTANCE PENDING`

## Product outcome

- Overview 现在以稳定的航海仪表卡片表达 Position、SOG/COG、Heading、Depth、Apparent Wind，以及有真实值时的 True Wind；每张卡同时显示来源、live/held/stale 健康和数据年龄。
- Apparent Wind 与 True Wind 已成为两个独立语义来源组，不再把不同含义的风数据绑定为一次选择。
- Inputs 仍在 Data 内使用 process-owned TCP/UDP runtime，创建、编辑、连接、rate、sentence inventory 和 raw console 不需要跳转到另一个独立 App。
- Flow 已改为真正绘制的 node/edge/branch 路径：Input source → semantic group → OS data → Chart / Navigation。active path 使用主题色，standby 路径保留但不会被描述为自动接管。
- Flow 的 source/group/output/consumer node 都可点击并进入相应的来源或概览细节，不是静态文字说明。

## Replaced presentation

- 废弃用纵向 settings rows 表达所有 live values 的 Overview。
- 废弃 `source › group › output` 字符串箭头。Flow 不再依赖文本符号冒充 topology。
- 废弃把全部 wind candidates 合并为一个 `Wind` 配置组的产品形状。

## Preserved runtime/domain

- P1/P2 NMEA parser、bounded catalogs、session/generation isolation、TCP/UDP runtime、raw preview、freshness 与 source-selection transaction 全部原样复用。
- Data UI 不拥有 socket；Inputs 页面继续消费 composition root 注入的 NMEA workspace。
- Phone location 仍由 consumer selection 产生 demand；permission failure 是明确 action-required，不启用 silent fallback。
- `PresentationCadence` 保持 Overview 250ms、rate 1s、raw 250ms 的 bounded presentation cadence。

## Design decisions

- Instrument 直接消费 `ResolvedDatum`，因此 value/source/freshness/health 只有一个 runtime truth；没有为 UI 再造一份 live cache。
- Topology 只从 runtime 已观察到的 candidates 和真实 selection decision 生成；未观察到关系时显示 empty state，不画假设备。
- 点击 source node 使用其稳定 `ConnectionId` 聚焦 Sources；点击 group/output/consumer 返回语义来源或当前 OS output，而不是进入协议内部对象页面。

## Known gaps

- 真机仍需用真实 TCP/UDP 数据确认三秒可读性、湿手触控大小、长名称换行与高频更新稳定性。
- Chart/Navigation consumer node 当前表达 composition root 中已存在的共享 resolved-data consumers；未来加入更多 consumer 时应由运行时 consumer registry 扩展，而不是继续硬编码产品猜测。
- 人工认可视觉层级前，不新增锁死卡片尺寸、tab 文案或 node 坐标的 presentation tests。

## Human stories

待验收：`DATA-01`–`DATA-09`。使用真实 TCP/UDP 输入完成 connect → overview live → raw live → semantic source selection → topology active path → 点击 node 查看细节；再中断所选来源，确认不会静默切换。

## Tests

- 新增 apparent/true wind 独立语义合同，先因缺失 enum Red，后 Green。
- `:feature:data:testDebugUnitTest`：12/12 PASS；保留原子组选择、无 silent fallback、Phone demand 与 Back policy 测试。
- `:app-shell:compileStandaloneDebugKotlin`：PASS，证明 production composition root 能消费新 UI/domain shape。
- 未恢复旧 W04 的固定五页面、固定字符串或文本 Flow presentation gate。

## Git

- R07 implementation：`99fcea21e644bfd8acef6394446cce585d01b196`
- workflow / `CODEX-CI-REPORT-*` / installable APK：本批 push 后登记。

## English summary

R07 turns Data into a stable instrument dashboard, separates apparent and true wind source policy, keeps inputs inside the process-owned NMEA runtime, and replaces textual arrows with clickable drawn topology paths. CI and live NMEA device acceptance remain pending.
