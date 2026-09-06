# NMEA_SOURCES P1 报告 / P1 Report

状态：`CANDIDATE_PENDING_FULL_GATE`

基线提交：`32602b7…`

Red 合同提交：`068da34…`

## 结果

- 新增纯 Kotlin/JVM `core:marine-data`；没有 Android、Compose、socket、Feature 或 Map 依赖。
- 12 类最低句型均有 typed parser；strict checksum 默认开启，显式兼容策略的未校验值贯穿标记为 `UNVERIFIED_ALLOWED`。
- TCP framing 有 1024-byte 上限并处理半包、粘包、断线残片和非法编码；UDP 每个 datagram 独立，不跨包或 sender 拼接。
- `ConnectionId`、稳定 UDP origin policy、observed sender provenance、`SessionGeneration`、`ObservationGroupId`、checksum trust 和 DataKey reference 分层，不把 talker 或临时 source port 冒充设备身份。
- 有界 `ActiveSessionRegistry` 在首帧前显式接纳 generation，静默新 session 也能立即拒绝旧 callback；end 后保留有界 tombstone，删除配置才显式 forget。
- Sentence inventory 保留 source、talker、formatter 和 MWV R/T semantic instance；Observation catalog 聚合同源互补句型，但不合并不同来源或不同 marine meaning。
- freshness 由注入的单调时钟在无包时继续老化；跨句型 invalid、新 valid 恢复、LIVE/HELD/STALE 和 formatter rank 有确定顺序。
- [字段映射](../../implementation/NMEA_SOURCES_P1_FIELD_MAPPING.md) 明确风、深度、true/magnetic、有效性、空字段、单位和未支持句型边界。

## TDD 与自审

1. 初始静态 Red 为 `4 FAIL / 1 ERROR / 1 PASS`，缺口精确对应纯模块、parser、catalog 和测试。
2. 第一轮 Green 达到 45 项 JVM 测试后，独立只读审查仍判定 `FAIL`，发现 session 首帧竞态、可绕过的 identity cache 上限、跨 formatter invalid、丢失的 sentence instance/checksum trust、UDP 持久身份、GGA/DPT/mode、LIVE/HELD 和 frame grouping 共 10 类问题。
3. 每项审查问题先进入新的静态或 JVM Red，再修正模型；纠错后的当前证据为 `66/66 JVM PASS`、P1 静态合同 `7/7 PASS`、CI topology PASS、`git diff --check` PASS。
4. 当前只记录 targeted candidate 证据。按用户要求先提交以触发 hosted CI，再运行完整 P0–P1 repository gate；最终 commit/run 由后续 evidence-only correction 写回，不在这里预先伪造。

P1 没有实现 TCP/UDP 连接、配置保存、Android 服务、来源采用事务、手机定位或 UI；这些分别属于 P2/P3/P4/P5。

## English translation

P1 now provides a platform-neutral, bounded and provenance-preserving NMEA data core. Twelve sentence families, strict framing/checksum behavior, explicit checksum trust, stable source identity, frame grouping, bounded session admission, detailed sentence inventory, deterministic complementary evidence, explicit invalidity and monotonic freshness are covered by 66 passing JVM tests. An independent review rejected the first green candidate and all ten classes of findings were converted into tests before correction. Full repository and hosted evidence remain pending until the candidate commit is created; no socket, Android runtime, source-selection transaction or UI is claimed here.
