# CL03 报告 / Catalog model and persistence report

状态：`PASS — API34_ROOM_TESTED`

起点：`fcfbb42`

## 实现

- 新的纯 Kotlin catalog 合同分开表达来源 UUID、TREE/SINGLE_DOCUMENT/MANAGED、
  文档身份、membership、外部 content revision、可空事实及 provenance、用户角色/
  优先级/启用、访问状态与 DISCOVERED/BASIC/FULL 验证状态。
- `ChartSourceId.USER_IMPORT/NOAA_NCDS` 的 publisher 枚举没有被复用为文件夹 ID；
  外部来源/资产均为持久 UUID。只有真实存在 SHA 的 revision 才能标 FULL_VERIFIED。
- 专用 Room v1 库用 source、asset、membership、legacy mapping、catalog metadata 与
  有界事务记录组成唯一可写目录；不存 chart blob/tile bytes。查询在数据库分页，
  初始/最大页均为 100。
- 写入在一个 Room transaction 内做 optimistic revision 检查。确认相同 provider
  authority/documentId 时合并 membership；同名/同大小但身份不同不会合并。
  `UPSERT` 避免 REPLACE 删除父行并级联丢失 membership。
- 最近 256 个 transaction ID 持久去重；失败/冲突不发布新的 StateFlow snapshot。

## TDD 与验证证据

- Red：Room repository 缺失时 androidTest 编译失败。
- 第一次设备 Green 反证出 SQLite `ESCAPE` 字符错误和 `REPLACE` 级联丢 membership；
  改为单字符 escape 与 `@Upsert` 后通过。
- `:core:map-domain:test` 通过；API 34 `RoomChartCatalogRepositoryTest` 4/4，覆盖
  原子提交、分页、重启、身份别名、单 membership 移除、未知事实、分层验证、
  legacy mapping、幂等事务、乐观冲突与已关闭 DB 失败一致性。
- CL03 静态合同 3/3，确认 core 无 Android/Room、目录 DB 无 BLOB、关键场景存在。

## 真实性边界

- 这是目录与持久化，不包含 SAF picker/扫描、FULL verification worker、process
  runtime 或 UI；这些分别属于 CL04–CL07。
- Room schema v1 是新库，无历史版本迁移；现有 `map_packages` 数据迁移属于 CL09。
