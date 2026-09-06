# NMEA_SOURCES P2 报告 / P2 Report

状态：`RED_DEFINED`

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

## Android 与人工边界

本阶段机器门禁覆盖 API 34/36 service lifecycle、真实 localhost sockets 与配置恢复。锁屏、Doze、OEM 杀进程、真实网络切换、功耗和三星方屏仍属于 P6 物理设备证据；未执行时必须写 `UNVERIFIED_PHYSICAL_DEVICE`，不能由 emulator 代替。

## English translation

P2 is currently Red-defined. It will deliver the real multi-connection NMEA TCP/UDP runtime, durable user intent and an independently hostable NMEA Input feature, while deliberately leaving formal Shell installation, tiles and status-strip integration to P5. Initial static evidence is seven failures, one missing-file error and one passing phase-boundary check. Thirty-six behavior tests across pure core, real-loopback adapter and feature layers fail to compile on the deliberately absent P2 contracts. No Green, service, socket, persistence or UI claim is made yet.
