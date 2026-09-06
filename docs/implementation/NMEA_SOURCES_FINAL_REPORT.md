# NMEA 输入与数据来源最终实施报告 / NMEA Input and Data Sources Final Report

状态：`MACHINE_VERIFIED`

规范哈希：`a5a38f08f8606d230952dcec8e8f521615efc9ec1a4d30ab4a3821f5943b3348`

实施基线：`codex/shell-map-contract@69bfd4d0ed29f27450351df530b4a8b1e8e2c6a6`

P7 最后门禁修正提交：`05588ad3be64860d9764b731f69ec2555c374801`

## 交付结论

本轮把 NMEA Input 和 Data Sources 做成两个独立的 Yokuli OS Shell App，而不是两个 APK、Chart 的设置页或旧 Anchor Alarm 的延伸。两个 Feature 只经 `core:marine-data` 的 typed port、不可变 snapshot、`StateFlow` 与 action 协作；Android socket、DataStore、位置和前台服务实现位于 adapter；`app-shell` 是唯一 composition root。

P0 冻结真实仓库、参考和 Android 边界；P1 建立有界纯 NMEA 数据核心；P2 建立多连接真实 TCP／UDP runtime 与 NMEA Input；P3 建立唯一 OS 选源与手机系统位置；P4 完成 Data Sources 管理闭环；P5 单点安装两个 App、三尺寸磁贴与独立状态入口；P6 把唯一采用结果只读接入 Chart，并验证服务丢失、损坏与外部进程恢复；P7 只负责全仓、Release、CI、双语、隐私和证据封口。

## 实际已支持范围

- 多个 NMEA 0183 TCP client 与本地 UDP listener；UDP 保留实际 sender provenance，不跨 datagram 或发送方拼帧。
- 严格校验和默认策略及显式 missing-checksum 兼容策略；RMC、GGA、GLL、VTG、ZDA、HDG、HDM、HDT、DPT、DBT、MWD、MWV 共 12 类 typed parser，以及合法未知句型目录。
- 连接配置持久化、串行 runtime actor、session generation 隔离、显式 Start／Stop／Retry、退避、5 秒速率、10 秒输入中断和全部有界 catalog／raw／incident／queue。
- 单一 Source Catalog、按 DataKey 的明确选择事务、3 秒首次发现窗口、不静默抢占、不静默 failover、删除/消失/持久化失败的真实状态。
- Android 系统位置候选；权限、系统位置开关与前台服务是独立事实，拒绝手机权限不会破坏 NMEA。
- NMEA Input 与 Data Sources 各自独立页面、All Apps 注册、1×1／2×2／4×2 动态磁贴、状态条入口和不透明受控深链。
- Chart 只从一个 composition adapter 消费已采用来源的 Position、accuracy/source time、Heading、COG/SOG；不跨来源或跨 frame 拼成虚假完整值。
- 普通竖屏沉浸式 Android 应用；没有 Android HOME／DEFAULT 或桌面设置入口；虚拟/系统 Back 最远回到应用内 Start，不退出 Yokuli。
- 生产地图是离线 MapLibre + 用户导入的兼容 MBTiles 海图包；GitHub 的 Google Maps key 不参与此渲染链路。

## 旧逻辑复用位置

旧参考仓 `ohkuku/yokuli_nmea_anchor_alarm@codex/develop` 只读用于核对 checksum、stream framing、DataStore、foreground service 与 freshness 的技术经验。实现重新建立在当前多连接 actor、typed provenance、generation、统一选源和 Shell composition contracts 上，没有搬入旧 Anchor／Trip／Sonar 业务、单连接 repository 或旧 UI。

明确未复制三类已知问题：跨 UDP sender 共享 stream splitter；异步 callback 在处理时读取可变 active profile／generation；失败 connect 先覆盖当前配置。当前 runtime 给每个连接不可变 `SessionToken`，UDP datagram 独立 framing，所有输入由中央 actor 串行提交。

## E01–E26 证据账本

| ID | 用户故事与实际证据 | 状态 |
|---|---|---|
| E01 | 首开空态：NMEA Input／Data Sources projector、Compose 与 Shell Activity stories | MACHINE_TESTED |
| E02 | phone-only：permission reducer、phone runtime 与 API 34 manifest/story；真实 GNSS 尚未跑 | MACHINE_TESTED / PHYSICAL_UNVERIFIED |
| E03 | TCP 已连无数据：真实 localhost TCP integration + truthful UI projection | MACHINE_TESTED |
| E04 | UDP 监听无 sender：真实 localhost UDP integration + truthful UI projection | MACHINE_TESTED |
| E05 | 仅风／水深：MWV/MWD/DPT/DBT parser、catalog 与 Data Sources UI | MACHINE_TESTED |
| E06 | 单一位置源：3 秒 discovery reducer 与 resolved consumer | JVM_TESTED |
| E07 | 首次手机 + NMEA 双源：不猜选的 selection reducer 与 UI | JVM/COMPOSE_TESTED |
| E08 | 已采用 A 后新增 B：保持 A 并标记 needsReview | JVM_TESTED |
| E09 | 明确选择 B：先持久化再原子发布 snapshot | JVM_TESTED |
| E10 | B 断流而 A 正常：monotonic freshness，保留 B，不 failover | JVM_TESTED |
| E11 | B 恢复／坏 checksum：checksum、invalid barrier、health timer | JVM_TESTED |
| E12 | 同连接 RMC/GGA：同 origin 候选、确定性字段仲裁、LIVE 优先 HELD | JVM_TESTED |
| E13 | 两连接 GPRMC／改名：稳定 identity 与持久 selection | JVM_TESTED |
| E14 | 无法区分同一上游设备：UI 只陈述 connection/origin 事实，不猜 talker=设备 | PROJECTOR_TESTED |
| E15 | MWV/HDT/HDM/depth reference：typed reference、unit 与范围 parser 表 | JVM_TESTED |
| E16 | 未知合法句型：typed unsupported result 仍进入 sentence catalog/UI | JVM/COMPOSE_TESTED |
| E17 | 空字段：不发布零值、不刷新该字段 freshness | JVM_TESTED |
| E18 | 离开 UI／取消固定／配置重建：runtime 由进程拥有；Activity stories | MACHINE_TESTED |
| E19 | 明确停止：取消 socket/reconnect 并持久化 STOPPED_BY_USER | JVM/LOOPBACK_TESTED |
| E20 | 拒权／撤销／后台受限：reducer、adapter 与模拟器路径；OEM 后台尚未跑 | MACHINE_TESTED / PHYSICAL_UNVERIFIED |
| E21 | 进程重启：API 34 两进程 force-stop 探针；真实升级/重装迁移尚未跑 | MACHINE_TESTED / PHYSICAL_PARTIAL |
| E22 | 选源写失败／来源消失：事务不发布假成功，UI 返回 typed effect | JVM/COMPOSE_TESTED |
| E23 | 删除已采用连接：确认文案先说明变为“来源已删除”且不自动切换 | MACHINE_TESTED |
| E24 | 高流量／畸形：有界压力测试 + 3 秒、4 连接、100 句/秒 sender smoke；完整 30 分钟应用 soak 未跑 | PARTIAL / NOT_RUN_30_MIN |
| E25 | 两个磁贴／状态条：pure projectors + Shell discovery/pin/resize/back stories | MACHINE_TESTED |
| E26 | 中英资源 parity、large type、三尺寸、黑白主题 Compose matrix；三星方屏与人工视觉尚未跑 | MACHINE_TESTED / PHYSICAL_UNVERIFIED |

## 实际命令与退出码

施工阶段遵循用户的 Gate 成本约束，只运行修改所属的 Python 合同、模块编译或定向 JVM／Activity tests。P0–P6 的实际 scoped 命令和计数分别记录于同目录的 `P0_REPORT.md` 至 `P6_REPORT.md`。

P7 唯一完整命令已执行：

```text
PYTHON_BIN=/private/tmp/yokuli-p7-python/bin/python \
  bash .github/scripts/run_marine_shell_final_gate.sh --with-device
exit: 0
```

实际结果：Python 合同 `287/287`；Gradle JVM 测试执行 `668/668`；构建、Lint、Debug／Release 与测试 APK 共 `1477` 个 task 成功；Release 产品面审计通过；API 34 的 marine-data、map-offline、map-storage 与 app-shell 共 `92/92`；C12 与 NMEA force-stop 外部进程探针共 `4/4`；性能旅程 `11/11`，证据等级严格为 `EMULATOR_TREND_ONLY`。最终输出：

```text
MARINE_SHELL_FINAL_GATE=MACHINE_VERIFIED CHART_C12_GATE=CORE_MACHINE_READY
```

托管 Android CI、可下载 Alpha artifact 与签名 Release 必须等待本次封口提交 push 后的远端实际结果；本地 `MACHINE_VERIFIED` 不冒充 hosted CI。

## 截图／录屏索引

- 自动 Compose／Activity 证据：`adapter/marine-data-android` 2/2、`adapter/map-offline` 17/17、`adapter/map-storage` 7/7、`app-shell` 66/66 的 `build/outputs/androidTest-results` 与 `build/reports/androidTests`（不作为人工像素批准）。
- 独立进程日志：`build/ci-c12-process-restore.log` 与 `build/ci-nmea-sources-process-restore.log`，各自两次独立 instrumentation invocation 通过。
- 性能索引：`build/marine-shell-final-correction/performance-summary.json`，11/11 且仅为 `EMULATOR_TREND_ONLY`。
- P6 sender smoke 数字：`docs/phases/nmea-sources/P6_REPORT.md`。
- NMEA Sources 专项人工截图／录屏：`NOT_RUN`。
- 三星方屏实机与 WP8 主观还原批准：`UNVERIFIED_PHYSICAL_DEVICE`。

## 未执行项

- 真机锁屏／熄屏、Doze 和 OEM 后台策略。
- 真实 Wi-Fi／蜂窝切换、网络 portal／热点异常与真实 GNSS。
- 功耗、温升、三星方屏触控与物理 WP8 手感。
- 完整 30 分钟、4 连接、合计 100 句/秒的应用级内存／队列／socket 资源曲线。
- 实际升级安装、系统回收后的长时恢复，以及 NMEA Sources 专项人工截图／录屏审查。

## 剩余限制

不支持 NMEA 输出／转发、NMEA 2000 原生总线、USB、Bluetooth、Signal K、AIS、mock location、主动导航、自动舵或船网控制；Anchor、Trip、Survey 仍没有生产 runtime。选中来源变旧、删除或消失时不会静默切换；用户必须明确选择。当前地图不会读取 Google Maps Actions secret，只显示已导入的离线 MBTiles 内容。

## English translation

The phase delivers two independently installed in-Shell apps over one process-owned, typed and bounded marine-data runtime. Real TCP/UDP input, twelve NMEA 0183 formatter families, catalogs, explicit per-key source selection, Android system-location candidacy, dynamic tiles/status entries, and the read-only Chart consumer are implemented. P0–P6 retain their scoped evidence; the single P7 repository/device/release gate passed and is `MACHINE_VERIFIED`. The E01–E26 ledger still distinguishes JVM, loopback, emulator, partial and physical evidence. Hosted CI remains pending until push, and no claim is made for OEM background behavior, real GNSS/radio/power, Samsung-square interaction, manual visual approval, or the full 30-minute app soak.
