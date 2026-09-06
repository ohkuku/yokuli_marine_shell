# NMEA_SOURCES P6 报告 / P6 Report

状态：`PASS — MACHINE_VERIFIED`

物理设备状态：`UNVERIFIED_PHYSICAL_DEVICE`

起点提交：`e05b01a44c667409905bade1cb9e732e79741b7c`

## 范围与结果

P6 用 composition-root 适配器把 OS 唯一 `MarineSourceSnapshot` 接入 Chart 已有的
`ReadOnlyPositionPort`，没有让 Chart 依赖 NMEA runtime、socket、权限或来源选择规则。只有已明确采用且
处于 LIVE／HELD 的位置会建立 Chart source；所选位置变旧或不可用时先断开并保留历史显示，正常的备选
来源不会被静默切入。

适配器同时投影同一来源的真实／磁罗经艏向和 COG/SOG。Position accuracy、磁差、source time、COG 与
SOG 只有在来源和 frame group 都相同时才组合；不同帧的 course/speed 只传递较新的独立字段，绝不拼成
一个看似完整的运动向量。Source ID 经过进程内 epoch 与不可逆摘要处理，不把连接名、主机或端口交给
Chart。

前台服务被单独终止时，Android-only lifecycle 信号进入同一个 runtime actor：所有 socket/retry task
释放，transport 变为 `PlatformStartRequired`，但用户保存的 `ENABLED` 意图不被系统事件改写。用户随后
可用现有 Retry 明确恢复。正常的“全部由用户停止”路径不会被误报成服务丢失。

独立 API 34 force-stop 探针先通过真实 localhost TCP 收到一条合法 RMC，并用正式 selection transaction
采用位置；宿主随后执行 `am force-stop com.yokuli.marine`。第二个进程证明连接配置和采用策略仍在，但
byte/legal-frame counters、observation catalog 和 resolved live value 均从零开始。探针结束后清除测试
应用数据，不把状态污染到普通 device suite。

连接存储增加两条故障证据：单条语义损坏只 quarantine 该条并保留合法连接；整个 protobuf wire 损坏会
发布 load failure，原始字节不被空库覆盖。

## TDD 与自查纠错

- 初始 Red `d71437ddbbca11f3c9d387370cbfceed700136d4` 在跨应用适配器、30 分钟 sender 和报告缺失时失败。
- `0908bd778f0b58f737601937ce5a93a1cc2a87f8` 建立 selected-position bridge；端口合同 3/3 首次 Green。
- 自审 Red `ae474f2ce284de565b050a928f8161ca8ddc84ee` 的两条跨应用故事均因缺少 Heading/CourseSpeed action
  精确失败；`6a58048` 实现同源同帧投影，`5281506` 修正 Kotlin 跨模块 smart-cast 后 5/5 Green。
- 生命周期 Red `f277c695cd8cbcf3d3f6eddf3f22e0efd60cb50c` 得到 `5 pass / 1 failure / 2 errors`；行为 Red 又因
  `ForegroundServiceLossListener` 不存在而在 test compile 失败。`1d0c90f` 加入 actor 化释放语义，三条
  定向 adapter 测试通过。
- 第一次真实进程探针在已收到位置后等待自动采用并于 12 秒超时。`1f3a927` 没有增加 sleep，而是从真实
  catalog 获取候选并执行正式 `Select` 事务；重跑的两个独立 instrumentation 进程均 `OK (1 test)`。

## P6 最小 Gate

```text
./gradlew :app-shell:testStandaloneDebugUnitTest \
  --tests '*MarineSourcePositionPortTest' \
  --tests '*MarineDataCrossAppStoryTest'                              PASS 5/5

./gradlew :core:marine-data:test \
  --tests '*SourceSelectionReducerTest' \
  --tests '*SourceSelectionRuntimeTest'                               PASS 12/12

./gradlew :adapter:marine-data-android:testDebugUnitTest \
  --tests '*NmeaRuntimeLifecycleTest' \
  --tests '*ConnectionPersistenceTest' \
  --tests '*PhoneLocationRuntimeTest'                                 PASS 15/15

bash .github/scripts/run_nmea_sources_process_restore.sh              PASS 2/2, API 34

python3 tools/nmea_soak_sender.py --duration 3 --connections 4 \
  --total-rate 100 --silent-every 2 --silent-for 1 \
  --bad-frame-every 17 --self-connect                                 PASS
  elapsed=3.002s, accepted/peak=4/4, valid=254, bad=14,
  silent-windows=1, sent-bytes=18492, write-failures=0
```

`tools/nmea_soak_sender.py` 的冻结默认值是 30 分钟、4 连接、合计 100 句/秒，并支持坏帧与静默窗口。
本次只执行了 3 秒生成器／四客户端 machine smoke；没有把它写成“30 分钟应用耐久已通过”。高水位、
队列、catalog/raw/incident 上限、重复 socket 与 stale generation 由 P1–P3 有界/故障测试验证；真实
30 分钟 wall-clock 应用资源曲线仍为 `NOT_RUN`。

遵照 owner 指令，本阶段没有执行全仓 test、Lint、全量 Debug/Release assemble 或全部设备 suite；它们
统一留到 P7。

## 证据边界

以下只能由明确设备、系统版本和日志批准，本报告不冒充通过：锁屏／熄屏、Doze、OEM 后台策略、真实
Wi-Fi／蜂窝切换、真实 GNSS、功耗、三星方屏触控，以及 30 分钟应用级资源曲线。状态统一为
`UNVERIFIED_PHYSICAL_DEVICE`。模拟器 force-stop、host loopback 和 JVM actor 测试只记
`MACHINE_VERIFIED`。

## English translation

P6 passes its scoped machine gate. A composition adapter projects the one OS-selected marine source
into Chart's existing read-only observation boundary, including position, same-frame accuracy/time,
heading, and atomic course/speed without source mixing or failover. Foreground-service loss now
releases sockets while preserving durable enabled intent. An API 34 external force-stop probe proves
that configuration and source policy survive a process while live observations and counters do not.
Semantic and wire corruption paths are also covered. Physical background/radio/GNSS/power/square-
screen behavior and a full 30-minute app-level resource run remain explicitly unverified; the full
repository/release gate is deferred to P7.
