# NMEA_SOURCES P1 报告 / P1 Report

状态：`CORRECTION_CANDIDATE_PENDING_FULL_GATE`

基线提交：`32602b7…`

Red 合同提交：`068da34…`

## 结果

- 新增纯 Kotlin/JVM `core:marine-data`；没有 Android、Compose、socket、Feature 或 Map 依赖。
- 12 类最低句型均有 typed parser；strict checksum 默认开启，显式兼容策略的未校验值贯穿标记为 `UNVERIFIED_ALLOWED`。
- TCP framing 有 1024-byte 上限并处理半包、粘包、断线残片和非法编码；UDP 每个 datagram 独立，不跨包或 sender 拼接。
- `ConnectionId`、稳定 UDP origin policy、observed sender provenance、`SessionGeneration`、`ObservationGroupId`、checksum trust 和 DataKey reference 分层，不把 talker 或临时 source port 冒充设备身份。
- 有界 `ActiveSessionRegistry` 在首帧前显式接纳 generation，并把 inbound admission 与 catalog commit 线性化；32 个 active slots 与 256 个 known/tombstone slots 分开计数，静默新 session 也能立即拒绝旧 callback。
- Sentence inventory 保留 source、talker、formatter、MWV R/T semantic instance、frame group 与实际 UDP sender endpoint；Observation catalog 聚合同源互补句型，但不合并不同来源或不同 marine meaning。
- Observation candidates 除了全局 256 项上限，每项内部 evidence stream 也限为 32；压力下的 eviction/rejection/high-water 均可观察，且 invalid barrier 不因 stream 替换或淘汰丢失。
- `MarineObservation` 构造边界验证 key/value/unit/range；checksum 后缀只接受两位 ASCII hex，日期与单位换算的极端输入只产生 typed error，不向 runtime 抛算术异常。
- freshness 由注入的单调时钟在无包时继续老化；跨句型 invalid、新 valid 恢复、LIVE/HELD/STALE 和 formatter rank 有确定顺序。
- [字段映射](../../implementation/NMEA_SOURCES_P1_FIELD_MAPPING.md) 明确风、深度、true/magnetic、有效性、空字段、单位和未支持句型边界。

## TDD 与自审

1. 初始静态 Red 为 `4 FAIL / 1 ERROR / 1 PASS`，缺口精确对应纯模块、parser、catalog 和测试。
2. 第一轮 Green 达到 45 项 JVM 测试后，独立只读审查仍判定 `FAIL`，发现 session 首帧竞态、可绕过的 identity cache 上限、跨 formatter invalid、丢失的 sentence instance/checksum trust、UDP 持久身份、GGA/DPT/mode、LIVE/HELD 和 frame grouping 共 10 类问题。
3. 首个推送候选 `4dff57c…` 后再做两轮独立反例审查，又拒绝了 admission/commit TOCTOU、同 formatter 恢复丢 invalid barrier、active/tombstone 容量混淆、同毫秒帧排序、checksum 语法、numeric overflow、UDP endpoint provenance、candidate 内部无界 streams 以及名义 typed value 等问题。
4. 上述问题全部先加精确 Red，再实现 Green；当前 targeted 证据为 `83/83 JVM PASS`、P1 静态合同 `7/7 PASS`、`git diff --check` PASS。完整 P0–P1 repository gate 将在 correction commit 后用实际命令写回，不预先伪造。

P1 没有实现 TCP/UDP 连接、配置保存、Android 服务、来源采用事务、手机定位或 UI；这些分别属于 P2/P3/P4/P5。

## English translation

P1 now provides a platform-neutral, bounded and provenance-preserving NMEA data core. The first pushed candidate remained deliberately unapproved: two further counterexample reviews found session linearization, invalid-barrier retention, split active/tombstone capacity, same-millisecond frame ordering, checksum grammar, numeric-overflow, strict coordinate/date lexemes, UDP endpoint provenance, nested-stream bounds and typed-value invariant gaps. Each is now represented by a failing-before-green JVM contract. The corrected targeted gate is 83/83 JVM and 7/7 static tests; the full repository gate remains pending the correction commit. No socket, Android runtime, source-selection transaction or UI is claimed here.
