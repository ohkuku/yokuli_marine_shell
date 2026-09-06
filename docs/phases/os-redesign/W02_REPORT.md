# W02 报告 / Live UI Foundation

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`3b3117f119f9c27976f92d21888d0f7f301e03a7`

## 业务结果

- 新增统一 `WpLiveField` 与 `WpLiveConsole`：稳定最小宽度、monospace 数字/控制台、无实时帧入场动画。
- `PresentationCadence` 明确 Chart 5 Hz、Data/raw 4 Hz、NMEA rate/age 1 Hz；routine frame latest-wins。
- structural/safety key 变化绕过 cadence，因此断连、invalid/stale、过载和来源变化立即呈现。
- NMEA connection rate/age 和 raw preview、Data resolved/candidate value 与 sentence count/raw evidence、Chart
  position/heading/course/speed 已接入统一 primitive。

## 测试源交付给 Pipeline

- 注入时钟的 20 Hz → 4 Hz 上限与最终 latest value。
- cadence 窗口内 disconnect 立即呈现。
- stale boundary 作为结构变化立即呈现。
- raw latest snapshot 固定容量且呈现次数有界。
- `osr_w02_contract` 检查三个 Feature 接入、禁止 raw per-frame entrance、双语资源和 CI evidence。

## DESIGN DECISIONS

- 复用现有 StateFlow/coordinator，cadence 只放在 presentation 层；没有新增业务 repository 或第二套 runtime truth。
- 用显式 structural key 区分安全/结构变化与 routine live frame；这比从具体文案猜测重要性更可测试。
- raw preview 接收完整的有界 snapshot 并 latest-wins，不为每条输入创建待播放队列。
- Chart 仅显示真实 `PositionRenderPolicy` 输出；heading 与 COG/SOG 保持不同标签，没有新增位置来源或权限逻辑。

## 刻意未做

- 未降低 socket/parser/catalog 处理频率，未改变 freshness、selection 或持久化。
- 未实现 W03 Data 合并、Start Live Tile 或 Navigation instrument。
- 未声明物理设备帧时稳定性；本轮由托管门禁验证代码与 emulator 行为。

## 本地与待验

遵循 CI-first：本地只运行 W02 静态合同、XML/JSON/YAML/diff sanity，不运行完整 Gradle、lint 或 emulator。
托管结论等待 `CODEX-CI-REPORT-<W02_SHA12>-<run_id>-<attempt>`。

## English translation

W02 adds a reusable presentation-only cadence and stable WP live field/console. Routine values are latest-wins; structural and safety changes remain immediate. Existing NMEA Input, Data Sources, and Chart surfaces consume it without changing ingestion, runtime ownership, selection, persistence, or navigation behavior.
