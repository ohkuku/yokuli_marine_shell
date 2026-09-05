# C05 地点库与本地搜索报告

> English translation: C05 delivers durable complete Place records, local multilingual search, explicit coordinate moves, route-reference-aware deletion, revision-safe undo and single-place export. It does not claim completed saved-route planning, package jobs, GPX exchange, coverage/downloads, or live position.

## 状态

- package：C05
- baseline：`c15d3d17f832608b328d8722f475ff5fed694e59`
- implementation candidate：`76341a6a5d7fdc5c10160769cb093e6ab646d1f1`
- cumulative verified SHA：`76341a6a5d7fdc5c10160769cb093e6ab646d1f1`
- status：`VERIFIED_LOCAL`
- hosted CI：留给 C12 的最终同 SHA Gate；本报告不把本地 Gate 冒充托管结果

## 交付

1. `SavedPlace` 以稳定 ID、WGS84 坐标、名称、备注、分类、标签、创建/更新时间和 revision 作为完整资料；时间来自注入式 `MapClock`，不把墙钟隐式藏进 reducer。
2. 候选点的“保存地点”先进入完整编辑器。列表、详情、编辑、显式移动、删除确认、撤销与单地点导出使用同一对象身份；普通地图 pan 不改变地点坐标。
3. 本地搜索覆盖中英文名称、备注、分类别名、标签和显式坐标，统一 Unicode/大小写后匹配，提供确定性名称/更新时间排序；空库、重名和无结果不会退化为联网地理编码。
4. 地点移动和删除不静默改写正式路线。路线保留有序坐标快照与地点 revision 来源引用，并可解释 `CURRENT`、`CHANGED`、`MISSING`。
5. 删除前显示引用路线数量；撤销只恢复被删除时捕获的 revision，不覆盖后来创建、编辑或导入的数据。新建时拒绝当前、最近删除和路线来源仍占用的 ID。
6. Room schema 升至 v2，以规范化 `place_tags` 保存标签，并通过显式 `MIGRATION_1_2` 保留 v1 地点；生产未启用 destructive migration。合法记录与坏记录可分离，单条损坏不会清库。
7. 单地点导出采用 SDK-free JSON 编码；Android `CreateDocument` 只留在 app composition root。取消或写失败不伪报成功。
8. 保存可见状态仍服从 C01 的 durable revision ack；关闭并重开数据库后，同一已确认 ID、revision、完整字段和路线来源均可恢复。

## Red、修正与自查

- Red 覆盖完整字段 CRUD、普通 pan 隔离、移动确认/取消、路线快照不变、删除撤销、ID 不复用、本地多语言搜索、完整单地点导出、Room v1→v2 与进程边界重开。
- 自查发现中文分类别名不全，以及随机 ID 可能与刚删除或路线来源 ID 重合；追加失败测试后修正生成约束。
- 设备 Gate 前发现 Room schema 没有进入 androidTest assets、Compose test harness 复用了上一轮初始输入；两者均修正为确定性测试条件，没有绕过真实迁移或状态恢复。
- 历史 Stage 1 扫描曾把地图内部 `ANCHORAGE` 分类误当成 Launcher 的 Anchor 应用。扫描现限定到 production contribution/registry；Chart + Settings 发布面和禁止假入口的合同没有改变。
- Host Gate 的 baseline/startup profile 对若干旧符号给出非阻塞 stale 警告；C05 不以地点功能提交偷偷改性能基线，C09 必须完成真实 profile 复核。

## 聚焦与累计证据

- C05 repository contract：5 passed。
- `core:map-domain`、`adapter:map-storage`、`feature:chart` 聚焦测试与 Android test compilation：passed。
- API 34 C05 Shell stories：完整创建/导出/搜索/返回、1.5 字体空库/重名/无结果、显式移动/引用删除/路线快照/撤销全部 passed。
- API 34 Room：完整字段 round-trip、坏记录隔离、durable reopen、v1→v2 migration 等 5 tests passed。
- 当前 SHA 完整 Host Gate：180 个 Python repository contracts；全仓 test、lint、Standalone Debug/Release、benchmark 与产品表面审计通过，共 1207 Gradle tasks。
- JVM XML：280 tests，0 failure/error/skipped。
- 当前 SHA API 34：offline renderer 6、Room 5、app-shell 48，全部通过。
- Debug APK SHA-256：`456a51a14c87c8875134b17162ebdc805805983ace7d5215800d5837ea02dc9e`。
- unsigned Release APK SHA-256：`44e11239c0c5eef993c80b1db401570a2956d8ff17d8ccf7a202eed983311b2c`。

## 未宣称完成

- C06 的正式路线创建、命名、保存、预览、有序航点再编辑与取消边界尚未完成。
- C07–C10 的海图包作业/恢复、GPX、覆盖/下载和位置观测尚未完成。
- C11/C12 的最终证据矩阵、托管同 SHA CI、Alpha 产物和三星方屏实体机审核仍未完成。
