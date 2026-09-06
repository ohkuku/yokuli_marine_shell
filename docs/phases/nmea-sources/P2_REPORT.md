# NMEA_SOURCES P2 报告 / P2 Report

状态：`CANDIDATE_GREEN`（设备 Gate 待运行）

起点提交：`d746910f731d251a88c87817eed8a1d29a197026`

## 当前范围

P2 只实现真实 NMEA 0183 TCP client／UDP listener、多连接持久化与运行时，以及可独立 host 的 NMEA 输入 Feature。运行时由进程所有，不由 Activity、ViewModel、Composable、订阅者或磁贴拥有。

本阶段刻意不修改生产 `InstalledAppBinding`、All Apps、Start Document、磁贴或状态条；正式安装属于 P5。P2 也不实现全局选源、手机定位或数据来源应用，它们属于 P3/P4。

## Red 证据

- P2 静态合同首次运行：`9 tests; 7 failures / 1 error / 1 pass`。
- 通过项仅证明 P2 尚未偷跑生产安装；其余失败精确对应缺失的模块、typed runtime、Android adapter/service、双语 UI 与具名行为测试。
- pure 行为 Red：3 个类、11 个测试，`:core:marine-data:compileTestKotlin` 因 P2 typed API 尚未实现而失败。
- adapter 行为 Red：4 个类、13 个测试，`:adapter:marine-data-android:compileDebugUnitTestKotlin` 因 persistence、socket transport 与 runtime 尚未实现而失败；测试使用真实 localhost TCP/UDP，不把 fake transport 当网络证据。
- Feature 行为 Red：2 个类、12 个测试，`:feature:nmea-input:compileDebugUnitTestKotlin` 因 projector/coordinator/UI 合同尚未实现而失败。Feature 直接依赖 core port，不建立平行 runtime。
- 三组合计 36 个先失败的行为测试；Green 结果不得预填。

Red 合同提交：`357c293210ae63d3991db792a8b3aabc67a9fc61`。

## Candidate Green

- 纯 core 建立连接配置 reducer、typed runtime contract、5 秒有界速率、10 秒主动健康时钟、session/token/generation 隔离，以及 parser/catalog 的唯一 ingress pipeline。
- Android adapter 使用真实 `java.net.Socket`／`DatagramSocket`、Proto DataStore、process-owned serialized runtime、private `connectedDevice` foreground service 与明确的 Stop all 通知操作。
- NMEA Input Feature 只消费 core `StateFlow`/command port，提供 Overview、TCP/UDP Editor、Detail/diagnostics、替换/重复 TCP/删除确认与 WP8 页面；Back 在 Feature 根交还 Shell。
- `ShellApplication` 持有 runtime；`ShellViewModel` 只持有 Feature coordinator，因此离开页面或 ViewModel 不拥有/停止 socket。
- 自查纠错包括：拒绝 FGS 启动时禁止偷偷打开 socket；无新包时由 1 Hz ticker 发布 10 秒中断；boat Wi-Fi 不再错误要求 Internet capability；stale callback 不创建残留 framer；actor 捕获 typed internal incident 后继续运行；DataStore restart 测试等待旧 owner 完整取消。

当前最小 Gate：

```text
python3 .github/scripts/test_nmea_sources_p2_contract.py              PASS 9/9
./gradlew :core:marine-data:test                                      PASS 100/100
./gradlew :adapter:marine-data-android:testDebugUnitTest              PASS 15/15
./gradlew :feature:nmea-input:testDebugUnitTest                       PASS 16/16
./gradlew :app-shell:compileStandaloneDebugAndroidTestKotlin          PASS
git diff --check                                                      PASS
```

API 34/36 的真实 Compose loopback story 和 merged foreground-service instrumentation 已写入，但尚未在本提交前运行；因此本报告现在不宣称 P2 最终 PASS。

## Android 与人工边界

本阶段机器门禁覆盖 API 34/36 service lifecycle、真实 localhost sockets 与配置恢复。锁屏、Doze、OEM 杀进程、真实网络切换、功耗和三星方屏仍属于 P6 物理设备证据；未执行时必须写 `UNVERIFIED_PHYSICAL_DEVICE`，不能由 emulator 代替。

## English translation

P2 is candidate-green at the machine/JVM boundary: its static contract is 9/9, core is 100/100, the real-loopback Android adapter is 15/15, the Feature is 16/16, and the app instrumentation sources compile. Process ownership, truthful TCP/UDP state, bounded diagnostics, foreground-service refusal, active no-input aging and Feature separation are implemented. API 34/36 device stories are written but not yet executed, so final P2 PASS is intentionally not claimed.
