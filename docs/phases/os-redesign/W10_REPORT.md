# W10 报告 / Navigation domain migration

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`7a30e5eed4a7a873024ef4a01b6fdccb95766b8b`

## 产品结果

- 新增纯 JVM Navigation domain：Waypoint、RouteDraft、RoutePlan、RouteLeg、PassageSummary、Track、GPX receipt 与 OfflineCoveragePlan。
- 现有 Room library 同时提供旧 Map port 与新 Navigation port；没有新数据库、复制任务或 schema version 变化。
- Navigation mutation 具有 library revision 与 entity revision 冲突保护；GPX batch 保持原子并保留明确 import-as-copy。
- 旧 Place/Route/GPX/Track 模型可双向无损映射，旧 Map 读取与未来 Navigation 读取指向同一持久化事实。
- route summary 使用 GeographicLib WGS84 inverse，输出 true bearing 事实和可选计划时长。

## DESIGN DECISIONS

- 选择独立纯领域模块，因为 ownership 需要脱离 Chart/Compose；只增加一个模块，没有按每类资产创建 repository。
- 复用 `RoomMapPersistence` 作为过渡 composition adapter，让旧/new port 共享同一个 mutex 和 transaction。
- 没有重写 Room schema：当前记录已包含所需 identity/revision/metadata，双向 mapper 比复制迁移更安全。
- 使用 typed change + optimistic revision，而不是暴露 `replaceAll` 给 Feature，避免旧 UI 静默覆盖新数据。
- 删除 waypoint 不级联更改 route geometry，保留“来源 waypoint 已变化/缺失”可以被解释的语义。

## 暂不实施

- 不注册 Navigation launcher entry，不添加 UI，不从 Chart 删除旧入口。
- 不实现 active navigation、arrival/advance、NMEA output 或 autopilot。
- 不宣称物理历史库已验证；本轮只交付 fixture、Room instrumentation 与 CI 证据源。

## 测试源交付给 Pipeline

- core：CRUD/revision、atomic GPX、WGS84 leg/summary。
- adapter JVM：完整 legacy round trip 与现有 GPX parser output compatibility。
- adapter Android：同库双 port、revision conflict、commit 可见性。
- W10 static：依赖方向、单库/DB version、contracts/reports/locks。

## English translation

W10 introduces a UI-free Navigation domain and a revision-safe port over the existing Room map library. A bidirectional mapper preserves legacy waypoints, routes, GPX receipts, and track segments without a database rewrite. Mutations are atomic and optimistic, and passage summaries use WGS84 facts. UI registration and active navigation deliberately remain for later work packages.
