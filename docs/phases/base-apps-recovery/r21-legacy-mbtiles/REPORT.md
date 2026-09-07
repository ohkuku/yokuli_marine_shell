# R21 — Legacy MBTiles Compatibility Recovery

状态：`IMPLEMENTED_LOCAL_MACHINE_GREEN — STOP-SHIP OPEN`  
下一包：`R22 FORBIDDEN UNTIL HOSTED CI AND EXACT USER FILE HUMAN GATE PASS`

## Baseline

- Recovery baseline HEAD：`442741a414b554226a6b34b910617239556f3ba9`
- Resulting code HEAD：`83c76263f77a76cd0f809b4be3a545b65f65ec28`
- Execution directive SHA-256：`36f520ea6232b152554dd1d23d224e18b08009af89157ca046b1381a9dd68ac5`
- 分支：`codex/shell-map-contract`

## Traced failure path

历史 catalog 中同 revision asset 为 `DIRECT_READ_UNSUPPORTED` 时，原链路在现有 safe fallback 之前被永久截断：

1. `AndroidChartLibraryRuntime.requestIsCurrent()` 把该状态对 `VALIDATION` 也判为无效，Basic inspector 无法调用 delegate。
2. runtime 的 Basic worker 只接受 `DISCOVERED + UNCHECKED/CHANGED`，不会接纳历史 poison。
3. runtime 启动不枚举历史 poisoned rows。
4. same-revision rescan 的 `ChartSourceScanPlanner` 原样保留 poison，因此 refresh 仍不排 Basic。
5. asset 不能发布为 `READABLE + BASIC_READABLE`，`ChartViewDisplayPlanner` 必然过滤它，MapLibre 永远收不到 tile request。

`AndroidChartResourceAccess` 本身已经能在 seekable descriptor 不可用时通过 provider stream 建立 immutable local fallback；根因不是缺少第二个 reader，而是历史 capability observation 被错误当成永久 content verdict。

## Exact behavior changed

- `DIRECT_READ_UNSUPPORTED` 对普通 Render/Coverage 仍不可用；只有 Validation 可以穿过该历史状态进行重新探测。
- process runtime 启动后在后台枚举现有 poisoned assets，并逐项执行现有 Basic inspector。
- 枚举从末页向首页处理，过滤集合在成功后缩小时不会因 offset 漂移跳项；不把任意数量历史 rows 塞入 256 项 queue。
- same-revision rescan 将旧 provider-capability poison 归一成 `UNCHECKED + DISCOVERED`，清除旧 access mode，然后走既有后台 Basic 队列。
- 成功 fallback 后，现有 validation controller 原子发布 `READABLE + BASIC_READABLE + LOCAL_FALLBACK` 及 tile-derived facts。
- catalog asset page 的 rows、memberships 与 total 改为同一个 Room transaction，避免后台 validation 发布时 UI/readers 看到不可能的 page。
- 无数据库 schema rewrite；恢复可重复执行，失败资产保留原 catalog 身份和 revision，下一次启动或 rescan 可再次探测。
- 外部 MBTiles 没有被修改、重命名或删除；local fallback 仍由既有 revision-keyed cache 管理。

## Reused architecture

- `AndroidChartResourceAccess` direct-read + stream/local fallback。
- `AndroidChartLibraryRuntime` 的 process scope、read budget 与 Basic inspector。
- `AndroidChartValidationController` 的 per-asset lock、epoch 与原子 catalog publish。
- `RoomChartCatalogRepository`、现有 catalog schema 与 source generation。
- `ChartViewDisplayPlanner`、`ChartLoopbackTileGateway` 与现有 MapLibre raster surface。
- 没有新建 reader、catalog、Layer/View model 或 renderer。

## Transitional paths

- `DIRECT_READ_UNSUPPORTED` enum 必须保留，因为旧数据库可能仍包含该值；删除它会破坏反序列化兼容。
- 它仍然阻止未经重新检查的 Render，这是安全门，不是永久 poison。
- legacy asset/source display planner 没有在 R21 中删除；它属于后续产品收敛范围，当前不形成新的写入真相。
- 没有开始 R22 的 Layer/View presentation 修改。

## TDD and correction evidence

### Red 1 — same revision

`sameRevisionDirectReadPoisonIsResetForCapabilityReprobe` 首次运行按预期失败：planner 原样保留 `DIRECT_READ_UNSUPPORTED`。

### Green 1 — one real vertical journey

从关闭并重新打开的 Room catalog 中 seed 一个 poisoned same-revision asset，provider 只暴露 pipe stream。测试要求：

```text
runtime initialize
→ automatic Basic
→ LOCAL_FALLBACK
→ READABLE + BASIC_READABLE
→ active logical View/Layer display plan
→ runtime tile session
→ loopback raster gateway
→ MapLibre screenshot contains expected chart pixels
```

外部原文件前后 SHA-256 相同。

### Red 2 — bounded batch self-review

300 个 poisoned assets 的自查首次触发 `ChartCatalogPage` invariant：validation worker 在 rows 与 total 两次读取之间发布 catalog 更新。这同时证明原升序 offset 恢复可能跳项、queue overflow 可能遗留 poison。

Green 将 asset page 读变为原子 Room transaction，并改为有界后台逐项恢复。最终 300/300 均成为 `BASIC_READABLE`。

### CI evidence correction

原 legacy evidence extractor 没有要求 poisoned journey，且没有读取 UTP 实际保存 `println` 的 `logcat-*.txt`。现在它有界扫描这些 adapter-owned logs，并要求三个场景全部存在。

## Local gates

| Gate | Result |
| --- | --- |
| `ChartSourceScanPlannerTest` | `8/8 PASS` |
| poisoned catalog batch recovery | `1/1 PASS` (`300/300`) |
| poisoned same-revision → MapLibre pixels | `1/1 PASS` |
| SAF reader class + poisoned pixel journey | `7/7 PASS` |
| legacy evidence extraction | `3/3 COMPLETE`, `0 rejected` |
| Android test compilation | `PASS` |
| `git diff --check` | `PASS` |

本轮没有重复执行全仓 test/lint/assemble/release。push 后由现有 CI-first workflow 运行完整 core/adapter gate、全部 API 34 chart-library tests、evidence extraction 和 APK 构建。

## Legacy v1.0.2 comparison

本地旧仓 `codex/develop@a845d3d` 的 `OfflineMbTiles` 会把用户输入 stream 复制到 app-private `current.mbtiles`，再以 SQLite read-only 打开；它不会把不可 seek provider 当成内容损坏。当前的 `LOCAL_FALLBACK` 是这一能力的非破坏替代，并保留外部原件。

旧版只检查 PNG/JPEG/WEBP signature，`MbTilesTileProvider` 对返回 tile 固定声明 `256×256`，没有验证 encoded raster 的真实尺寸。当前 renderer contract 只接纳 square `256/512`。因为真实回归文件尚未提供给本轮自动测试，不能声称这一尺寸差异已经被真实文件排除；如果人验仍失败，必须记录实际 tile dimension/encoding，再支持或给出具体 renderer/safety 证据，不能返回笼统 `FILE UNAVAILABLE`。

## Hosted CI artifact

- Report prefix：`CODEX-CI-REPORT-`
- Exact artifact：push 后按最终 delivery HEAD 与 GitHub run id 生成，并在本轮交付消息中给出。
- API 34 raw evidence（仅统一报告明确要求时）：`yokuli-os-api34-reports-<delivery-head>`。

## Remaining human stop-ship gate

必须使用用户那份旧 v1.0.2 已知可用的原始 MBTiles，在不清数据、不删除 Source、不重新导入、不重建文件的条件下验证：

```text
升级/启动或 Rescan
→ 自动 Basic READY
→ Layer READY
→ selected View
→ Chart
→ 实际海图像素可见
```

记录：APK SHA、CI run、原 catalog 是否已经 poisoned、恢复入口、Basic 状态、Layer/View 名、实际像素截图。如仍失败，保留 source/file/reason/recovery 证据。

在此真实文件 Gate 与 hosted CI 都通过以前，R21 保持 `OPEN`，禁止开始 R22。

## English summary

R21 removes the permanent poison behavior without replacing the existing architecture. Historical `DIRECT_READ_UNSUPPORTED` rows can now enter validation, are re-inspected automatically on startup, and are normalized for a same-revision rescan. A stream-only fixture recovers through the existing local fallback, becomes Basic-readable, reaches the logical View planner, and renders actual MapLibre pixels without changing the external file. Batch recovery and catalog paging were hardened after a concurrency Red. Local machine gates pass, but R21 remains stop-ship open until hosted CI and the user's exact old-v1.0.2 MBTiles file pass the real APK journey.
