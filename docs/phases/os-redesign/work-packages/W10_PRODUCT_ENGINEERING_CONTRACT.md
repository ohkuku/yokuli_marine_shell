# W10 Product & Engineering Contract — Navigation Owns the Passage Library

版本：1 · 2026-09-06  
状态：施工候选

## Intent / 产品愿景

Navigation 应当成为用户规划和保管航程资产的唯一业务所有者。用户看到的是 waypoint、route、passage 和 track，
不是 Chart 内部的绘图状态或某张数据库表。W10 先建立这个领域真相和兼容读写边界，不急着更换已经可靠工作的
Room schema，也不提前发布一个功能不完整的 Navigation App。

## Experience / 用户体验

- 以前保存的地点在 Navigation 中保留名称、分类、标签、备注、坐标、revision 与时间，并获得 waypoint 语义。
- route draft、saved route、place reference、GPX receipt 与 imported track 的 segment boundary 完整保留。
- 更新使用乐观 revision；旧页面或并发任务不能覆盖更新后的 library。
- GPX 导入仍是一笔原子事务：重复 digest 默认拒绝；明确 import-as-copy 且使用新 ID 时允许，任何冲突都不会产生半条路线、孤立 waypoint 或无内容 receipt。
- passage summary 使用 WGS84 距离与 true bearing；没有计划航速时不编造 ETA。

## Domain / 领域关系

- Navigation Library 包含 Waypoint、RouteDraft、RoutePlan、NavigationTrack 与 GPX receipt。
- RoutePoint 可保留来源 waypoint revision；来源变化或删除不会偷偷移动路线几何。
- RouteLeg 与 PassageSummary 是从不可变 RoutePlan 派生的事实，不是 autopilot 或 steering command。
- OfflineCoveragePlan 是 Navigation 对路线范围的规划意图；图块 catalog/read 仍归 Chart Library/renderer。
- 现有 Room map library 暂时作为兼容 storage；同一个 process-scoped adapter 同时实现旧 Map port 与新 Navigation port。

## Opportunities / 可发展空间

后续可以增加 waypoint 集合、route version history、共享/同步、passage weather context、arrival policy 和更丰富的 GPX
扩展。它们必须建立在稳定 identity/revision 和显式兼容策略上；W10 不为这些未来想法预建 repository 层级。

## Non-negotiables / 不可协商边界

- 不清库、不复制整库、不修改 Room table version；旧 schema 与 fixture 必须原位可读。
- 所有写入必须串行通过共享 adapter，并以 library revision 与 entity revision 拒绝陈旧更新。
- mapper 必须双向保留坐标、顺序、segment、notes、tags、category、speed 和 source reference。
- 删除 waypoint 不级联删除或改写 route point；引用状态以后由 Navigation 明确解释。
- Imported track 保持只读来源语义；显示抽稀不得写回 source points。
- W10 不注册 Navigation App、不从 Chart 删除管理入口、不创建 active navigation 假状态。

## Forbidden outcomes / 禁止结果

- 新建第二个数据库并让旧数据靠一次性复制“迁移”。
- 用同名 typealias 假装 ownership 已迁移，却仍让 Navigation 依赖 Chart UI。
- last-write-wins 覆盖未知的新 revision。
- 把 COG 当 heading、把 derived route bearing 当传感器 heading 或控制输出。
- GPX 冲突时部分写入；解析失败时删除上一次有效 library。

## Compatibility / 兼容策略

新 Navigation domain 不依赖 Android、Compose、Chart feature 或 map storage。Android storage adapter 负责旧 `MapLibrarySnapshot`
与新 `NavigationLibrary` 的双向转换，并复用原 Room transaction。Chart 在 W12 完成前继续读取旧投影；数据只有一个持久化事实。

## Acceptance stories

1. 含 marina、route draft、route plan、place revision reference、双 segment track 和 GPX digest 的旧 fixture 双向转换后完全相同。
2. 新 Navigation waypoint 通过 port 写入后，旧 Map port 立即能以相同 ID/revision/坐标读取。
3. expected library revision 落后一版时写入返回 Conflict，数据库不变。
4. entity revision 不连续时 mutation 被拒绝；正确更新和删除各只增加一次 library revision。
5. 同一 GPX digest 第二次普通导入被整体拒绝；明确 import-as-copy 且所有 ID 唯一时整体写入。
6. route summary 给出逐 leg WGS84 distance/true bearing；零长 leg bearing 为空；无 speed 时 duration 为空。

## Evidence required

- JVM：领域 CRUD/revision、GPX atomicity、route math、旧模型双向 mapper 与 GPX parser compatibility。
- Android Room：新 Navigation port 与旧 Map port 读取同一数据库、冲突不写、commit revision 一致。
- 静态：模块依赖方向、无 Android/Compose、数据库 version 未提升、没有 Navigation production registration。
- W16 人工：真实历史库与大 GPX/track 集合另行验证，不由小 fixture 冒充。

## Existing code landmarks

优先审阅现有 `MapLibrarySnapshot`、Room map storage、Place/Route revision transaction、GPX reader/writer 和 WGS84 math。
这些是可复用事实与兼容入口，不要求保留旧命名或照指定 class 图施工。

## Implementation freedom / 实现自由

实现可以选择移动模型、增加独立纯领域模块或建立兼容 adapter；只要新 domain 不依赖 UI、旧数据只有一个持久化真相、
写入有冲突保护并满足验收故事即可。不得为了“层次完整”增加无行为的 repository/coordinator。

## English translation

W10 makes Navigation the semantic owner of waypoints, drafts, route plans, imported tracks, GPX receipts, passage math, and offline-coverage intent. The existing Room library remains the single durable truth through a bidirectional compatibility adapter; no database copy or destructive migration is allowed. Writes are revision-checked and atomic, route math is WGS84/true-bearing fact, and no Navigation UI or active-session fiction is introduced yet.
