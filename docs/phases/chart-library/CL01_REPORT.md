# CL01 报告 / SAF FD Reader Report

状态：`PASS — API34_PROVIDER_TESTED`

起点：`4ab1d2b`

## 实现

- `ChartReadContracts` 冻结 opaque source/asset/locator、非 SHA revision、0–24
  坐标、TMS/XYZ 统一行转换、typed open/read failure 与只读 session。
- `AndroidSafRandomAccessReader` 只以 `"r"` 打开 provider descriptor；用
  `lseek` 做能力探测，以 `pread` 支持 Long offset。pipe 不可 seek 时返回
  `DIRECT_READ_UNSUPPORTED`，不创建兼容副本。
- `AndroidChartResourceAccess` 检查 SQLite header、只接受 metadata/tiles
  table 或 view，并只执行应用定义的 bounded 参数查询。瓦片 payload 限制为
  16 MiB，只接受真实解码为 256/512 方形 PNG/JPEG/WebP 的内容。
- 当前桥只对完整 seekable descriptor 开放。它经实际 API 34 DocumentsProvider
  测试验证；没有把该能力宣传为所有云盘/provider 都支持。

## TDD 证据

- Red：core 与 adapter 测试因 `ChartReadContracts`、SAF reader 与 XYZ mapping
  缺失而编译失败，exit 1。
- Green：`:core:map-domain:test` 与 adapter androidTest 编译通过。
- 设备：`:adapter:chart-library-android:connectedDebugAndroidTest`，API 34，
  4/4，exit 0。
- 场景：seekable provider 原件读 tile 且 hash/文件清单不变；pipe 明确拒绝；
  4 GiB 以上随机 offset；short-read 与 closed-session typed failure。

## 偏离与限制

- 没有引入新的 native SQLite/VFS。当前使用进程内 `/proc/self/fd/<n>` 只读打开
  已验证的完整 seekable FD，并由原 descriptor 保持生命周期；因此 physical
  provider、arm64 厂商实现和 Android 16 KiB 页证据仍为 `NOT_RUN`，留到 CL11/12。
- 不支持 AssetFileDescriptor 子区间，能力探测必须拒绝，不能误读底层整文件。
- 本工单只证明读取，不声明 MapLibre 已显示；显示闭环属于 CL02。

