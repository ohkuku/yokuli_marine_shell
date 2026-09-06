# W11 报告 / Active Navigation Runtime

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`a1a0d48bf5b1703b17db979c83a9ce913c0d287e`

## 产品结果

- 保存路线现在可以显式开始真实导航会话；会话包含 exact route revision、active leg、arrival radius、policy 与状态。
- process-owned runtime 从 OS resolved marine-data 只读 Position/SOG/COG，串行处理 start/pause/resume/next/previous/stop。
- WGS84 solution 提供 DTW、BTW true、signed XTE、ETA、leg/route progress；无可用实时输入时不生成假 solution。
- ACTIVE 进程恢复会降为 PAUSED；session 独立持久化，不保存 fix、solution、坐标或 endpoint。
- Chart 的 W09 seam 只在真实 session 存在时注入导航条；路线预览新增显式开始操作。

## DESIGN DECISIONS

- 复用 W10 的同一 Room route truth，仅为轻量 session 使用独立 DataStore，避免 map session 保存覆盖导航会话。
- 在 composition root 做 marine-data → Navigation 的只读投影，保持纯 Navigation domain 不依赖 NMEA/Phone/Android。
- 同一 frame 才组合 SOG/COG；ETA 只使用已选择且有效的 SOG，不用 planned speed 填补。
- 恢复为 PAUSED 是显式安全边界：持久化意图仍在，但旧进程的 live observation 不会被冒充为当前事实。
- W11 只新增真实 Chart strip，不提前安装 W12 Navigation App，也不删除 Chart 兼容管理 surface。

## 暂不实施

- 不实现 Navigation 独立 App、完整 route editor/waypoint/GPX 工作空间；属于 W12。
- 不实现 autopilot、NMEA output、steering command 或自动选源。
- 不宣称真船、锁屏/OEM 或物理重启恢复已经验证；这些由 W16 人工证据确认。

## 测试源交付给 Pipeline

- core：WGS84/ETA/XTE/progress、freshness、serialized controls、arrival advance、route revision 与 process restore。
- adapter：session proto exact round-trip、future/invalid schema rejection。
- composition：resolved source projection 的 atomic motion 与 opaque source identity。
- API 34：真实 strip 与 typed stop control。
- W11 static：ownership、禁止输出、持久化、Chart injection 与文档锁。

## English translation

W11 delivers a real process-owned active-navigation session backed by an exact W10 route revision and OS-resolved marine data. It provides truthful WGS84 navigation facts, explicit controls and arrival policy, safe PAUSED process restore, non-destructive lightweight persistence, and real Chart-strip injection without installing the W12 app or adding any vessel-control output.
