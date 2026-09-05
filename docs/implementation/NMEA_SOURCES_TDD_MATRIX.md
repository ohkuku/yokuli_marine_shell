# NMEA_SOURCES TDD 矩阵 / TDD Matrix

## 阶段门禁

| 阶段 | 先失败的合同 | Green 与门禁 |
|---|---|---|
| P0 | 真实分支／SHA、旧符号、模块方向、Android 36 方案和冲突覆盖文件不存在 | hash-bound baseline + 全部既有 JVM/lint/Debug/Release 基线 |
| P1 | pure identifiers、framer/checksum、13 类句型、typed parse result、catalog、freshness、selection reducer 缺失 | `core:marine-data` 纯 JVM 全绿；无 Android/Compose/socket/feature 依赖 |
| P2 | 多连接持久化、TCP/UDP actor、generation、重连／停止、诊断上限、NMEA Input state/action/UI 缺失 | 真实 loopback socket + Feature tests；页面退出不停止；计数来自 socket |
| P3 | 单一 SourceCatalog/Selection/ResolvedData、3 秒发现窗、事务、手机定位／权限缺失 | 无 UI 消费同一原子 snapshot；不静默 failover；拒权不破坏 NMEA |
| P4 | 数据／语句双目录、搜索筛选、详情选源、停用、未知句型、受控深链缺失 | Compose/Activity stories 完整管理闭环；没有第二套 GPS 设置页 |
| P5 | 两个 InstalledAppBinding、三尺寸 tile projector、双 status entry 缺失 | All Apps 精确四项；默认 Start 不变；页面／磁贴／状态条同源 |
| P6 | 跨应用、重建／进程、故障、配置损坏、30 分钟负载证据缺失 | machine gate 与 physical gate 分离；无 ANR/无界增长/重复 socket/静默换源 |
| P7 | 完整回归、Release 审计、双语、隐私和证据账本缺失 | 所有已执行 gate 绿；未执行实机项保留 `UNVERIFIED_PHYSICAL_DEVICE` |

## 核心自动测试组

- P1：`MarineDataIdentifiersTest`、`NmeaStreamFramerTest`、`NmeaDatagramFramerTest`、`NmeaChecksumTest`、`Nmea0183ParserTest`、`SentenceInventoryTest`、`ObservationCatalogReducerTest`、`FreshnessPolicyTest`、`SourceSelectionReducerTest`。
- P2：`ConnectionConfigReducerTest`、`ConnectionPersistenceTest`、`TcpNmeaClientIntegrationTest`、`UdpNmeaListenerIntegrationTest`、`NmeaRuntimeLifecycleTest`、`NmeaInputProjectionTest`、`NmeaInputWorkspaceStoryTest`。
- P3：`SourceSelectionTransactionTest`、`ResolvedDataPortTest`、`PhoneLocationPermissionReducerTest`、`AndroidLocationAdapterAndroidTest`。
- P4：`DataSourcesWorkspaceStoryTest`，从空态、phone-only、unknown、wind/depth-only 到多源选择失败／成功和深链。
- P5：`NmeaInputTileProjectionTest`、`DataSourcesTileProjectionTest`、`MarineRuntimeStatusProjectionTest`、`ProductionLaunchRegistryTest` 与 Shell Activity stories。
- P6：`MarineDataCrossAppStoryTest`、独立 ADB process-restore driver、30 分钟四连接 100 sentences/s soak；实机后台矩阵单列。

## E01–E26 证据归属

| 故事 | 首要证据层 |
|---|---|
| E01 首开空态 | Compose + Activity |
| E02 phone-only | permission reducer + emulator；真实定位为 physical |
| E03 TCP 已连无数据 | loopback TCP + UI projection |
| E04 UDP 监听无 sender | loopback UDP + UI projection |
| E05 仅风／水深 | parser + loopback + catalog UI |
| E06 单一位置源 | discovery-window reducer + resolved consumer |
| E07 首次手机和 NMEA 双源 | selection reducer + UI |
| E08 已用 A 后新增 B | selection reducer + UI needsReview |
| E09 明确选择 B | persistence transaction + atomic resolved snapshot |
| E10 B 断流、A 正常 | monotonic freshness + no-failover reducer |
| E11 B 恢复／坏 checksum | checksum + invalidation + timer |
| E12 同连接 RMC/GGA | deterministic origin/sentence reducer |
| E13 两连接 GPRMC／改名 | stable identity + persistence |
| E14 无法区分上游设备 | source-label projector + truthful copy |
| E15 MWV/HDT/HDM/depth reference | typed DataKey parser table |
| E16 未知合法句型 | typed unsupported result + sentence catalog UI |
| E17 空字段 | parser + per-field age test |
| E18 退出 UI／取消固定／重建 | runtime lifecycle + Activity stories |
| E19 明确停止 | actor/reconnect cancellation + persistence |
| E20 权限拒绝／撤销／后台受限 | reducer + emulator；OEM behavior 为 physical |
| E21 进程重启／升级 | independent ADB probe + schema migration |
| E22 选源写失败／源消失 | transaction reducer + UI effect |
| E23 删除已采用连接 | impact confirmation + deleted-source state |
| E24 高流量／畸形／30 分钟 | fast bounded tests + real wall-clock soak |
| E25 磁贴／状态条 | pure projectors + Shell Activity stories |
| E26 双语／字体／长文／三尺寸 | resource + Compose matrix；三星方屏为 physical |

## 物理证据边界

API 34/36 emulator 和 host loopback 可以成为 `MACHINE_VERIFIED`，不能成为真机 GNSS、锁屏／熄屏、OEM 后台、真实 Wi-Fi／蜂窝切换、功耗或三星方屏触控证据。没有物理设备时 P6/P7 必须保留 `UNVERIFIED_PHYSICAL_DEVICE`；这不会被测试文件存在或编译成功改写。

## English translation

The matrix enforces Red-before-Green for P0 through P7 and maps E01–E26 to the lowest honest evidence layer. Pure JVM tests own parser, identity, freshness and selection semantics; loopback sockets own transport claims; Compose/Activity tests own user workflows; independent ADB probes own process semantics. Emulator evidence remains machine evidence only, while GNSS, OEM background behavior, radio switching, power and Samsung-square interaction require physical-device evidence.
