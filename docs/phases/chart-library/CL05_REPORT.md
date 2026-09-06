# CL05 报告 / Layered validation and shared tile reads

状态：`PASS — API34_VALIDATION_TESTED`

起点：`b0ba6bc1a8c91242a32cd2451b267d19760b8461`

## 实现

- BASIC inspection 只读最多 256 条有界 metadata 与最多 3 个真实 tile 样本；验证
  SQLite header、table/view schema、坐标、PNG/JPEG/WebP、256/512 尺寸、TMS/XYZ
  和 metadata/payload 一致性。它不计算全文件 hash、不统计全库，也不标 FULL。
- 用户显式 FULL verification 以 32 条为页检查全部实际 tile，检测无效/重复坐标、
  混合尺寸和编码，并以 1 MiB 块计算 SHA-256。全程有进度和取消点；验证前后
  revision 不同即拒绝发布，取消/异常不可能成为 FULL_VERIFIED。
- reader 在把 blob 放入 CursorWindow 前先用 `length` + `CASE` 拒绝超过 16 MiB 的
  payload；metadata 单项限 4096 字符。外部 SQLite 仍只读，不创建索引、journal、
  sidecar 或归一化行号。
- Renderer gateway 与 coverage index 都消费同一个 `ChartReadSession`，并通过唯一
  `ChartTileCoordinateMapper` 做 query-time TMS/XYZ 行号转换。
- Catalog v2 只新增可空 raster MIME 事实，并提供 v1→v2 非破坏 migration；没有
  回写或重解释 CL03 的历史记录。

## TDD 与验证证据

- Red/纠错：共享 read session 扩展后，旧 gateway fake 未实现新合同而编译失败；
  迁移测试首次因 schema 未打包进 androidTest assets 失败。补齐直接消费者与 schema
  source set 后转绿。
- `:core:map-domain:test` 与 `:adapter:map-offline:testDebugUnitTest` 通过。
- `:app-shell:compileStandaloneDebugKotlin` 通过，确认共享合同的直接消费者仍可编译。
- API 34 `ChartValidationAndroidTest` 8/8；`ChartCatalogMigrationTest` 1/1。
- CL05 静态合同检查验证分层、revision/cancel、读预算、共享 mapping、只读 SQL 与
  要求的回归场景。

## 真实性边界

- BASIC_READABLE 表示抽样可读，不等于所有 tile 完整、不等于 provider 离线。
- FULL_VERIFIED 只绑定被检查的 content revision，不代表海图新鲜度或航行安全。
- 真实厂商 provider、真实多 GB 文件、离线断网和 16 KiB page-size 设备仍为
  `NOT_RUN_PHYSICAL_DEVICE`；CL12 不会把 emulator 结果冒充真机结果。
