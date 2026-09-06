# NMEA_SOURCES P7 报告 / P7 Report

状态：`PASS — MACHINE_VERIFIED`

物理设备状态：`UNVERIFIED_PHYSICAL_DEVICE`

起点提交：`2f889800c035413dee5f4a34c668e243be1a690c`

## 本阶段封口范围

P7 不再增加 NMEA、选源或 Shell 产品功能。它把 P0–P6 已完成的能力接入唯一终局质量门：全量静态合同、JVM、Lint、Standalone Debug／Release、Release APK 产品面审计、API 34 设备故事、两个独立进程恢复探针和既有性能旅程。

生产产品面现在严格为四个 All Apps 入口：Chart、Settings、NMEA Input、Data Sources；全新 Start Document 仍只放 Chart 与 Settings。Release 必须包含两个正式 runtime 与两个私有前台服务，且不得包含 Shell Lab、旧假应用、fake/demo/soak/test sender。应用保持竖屏、普通 `MAIN + LAUNCHER`、不注册 Android `HOME`／`DEFAULT`，Back 最远只回到应用内 Start。

## P7 Red、Green 与自查

- Red `6e2d9e1af353e0e8a8724cac5d2610d332095a42` 要求四应用二进制审计、P0–P7 独立 CI 结果、Release 发布前审计、依赖方向、双语 parity、隐私扫描和 E01–E26 证据账本；初次运行因这些合同与最终文档缺失而失败。
- `b0c86fdf4ee3e1df02a300b1020c9f7c99791fc8` 将 Android CI、Release workflow、最终 Gate 和 APK 审计统一到当前四应用产品面，并加入 API 34 NMEA 外部进程探针。
- `fa62de9b7930d3921994b3af955fee5fb1930877` 修正四应用 Activity story 的真实 LazyColumn 滚动编译边界。
- 自查 Red／Green `5e61e72…` → `13c6cb7…` 要求删除已采用连接前明确说明影响：删除后进入“来源已删除”，不会静默切换。
- 自查 Red／Green `9796a8e…` → `3cccb8c…` 把系统定位权限所有权从已废弃的 Chart 采集假设迁到 marine-data composition boundary；Chart 只消费只读端口。
- 自查 Red／Green `a7fdb1a…` → `909bb38…` 清除仍把历史 Chart + Settings 基线当作当前生产事实的可执行 Gate，同时不改写历史报告。
- 最终 Gate 首轮自查继续捕获三个真实交付缺口：Android 13+ 前台通知权限未声明、APK analyzer 输出的竖屏枚举检查写错，以及 C12 设备故事仍断言旧的 `NoSourcePositionPort`。对应 Red／Green 提交为 `d987ca9…` → `7df967a…`、`8c8196a…` → `8ae2a8f…`、`05588ad…`；修正没有放宽产品、安全或二进制合同。
- 首次 hosted API 36 run `34010119590` 又暴露模块过滤错误：同一 Gradle invocation 把 app-shell 的正向 class filter 也交给 adapter test APK，导致 `ClassNotFoundException`。`69cb78b…` 先锁住 Red，`29c758b…` 再把 adapter 2 条测试与 app-shell 1 条 smoke 分成两个 runner invocation；定向 P7 `12/12` 和真实两步设备 smoke `2/2 + 1/1` 通过后才重推。
- P7 lock 只有在 `fullRepositoryGate` 仍为 `PENDING_EXECUTION` 时才允许 `PENDING_MACHINE_GATE`；只有实际退出码为 0 后才允许升级为 `MACHINE_VERIFIED`。

## 唯一完整 Gate 与实际证据

```text
PYTHON_BIN=/private/tmp/yokuli-p7-python/bin/python \
  bash .github/scripts/run_marine_shell_final_gate.sh --with-device
exit: 0
```

最终封口运行得到：Python 合同 `287/287`；Gradle JVM 测试结果 `668/668`（含构建变体）；完整构建／Lint／Debug／Release Gate `1477` 个 task 成功；Release APK 四应用、双 runtime、双私有服务、竖屏、无 HOME／DEFAULT、无 debug/demo sender 审计通过；API 34 adapter/map/app 设备套件 `92/92`；C12 与 NMEA 两个独立 force-stop 探针合计 `4/4`；性能旅程 `11/11`，仅标记 `EMULATOR_TREND_ONLY`。hosted runner 修正后的增量 P7 合同为 `12/12`、两步设备 smoke 为 `2/2 + 1/1`。最终本地输出为 `MARINE_SHELL_FINAL_GATE=MACHINE_VERIFIED CHART_C12_GATE=CORE_MACHINE_READY`。

施工中的小修改只运行所属静态或模块测试，没有反复消耗完整 Gate。GitHub hosted CI 和 Alpha artifact 仍需在本提交 push 后由远端实际结果补证，不能由本地通过代替。

## 明确不冒充的证据

锁屏／熄屏、Doze、OEM 后台限制、真实 Wi-Fi／蜂窝切换、真实 GNSS、功耗、三星方屏触控，以及完整 30 分钟应用级资源曲线都没有物理证据。它们保持 `UNVERIFIED_PHYSICAL_DEVICE` 或 `NOT_RUN`，不能由 JVM、host loopback、模拟器、测试存在或三秒 sender smoke 替代。

## English translation

P7 is the delivery gate, not another feature stage. The one final repository/device command exited successfully: 287 Python checks, 668 JVM test executions across build variants, the 1477-task build/lint/package gate, the four-app Release binary audit, 92 API 34 device tests, four independent process-probe executions, and 11 emulator trend journeys all passed. This is `MACHINE_VERIFIED`, not hosted-CI, physical-device, or subjective-visual approval. Physical background, radio, GNSS, power, Samsung-square interaction, manual visual review, and the full 30-minute app soak remain explicitly unverified or not run.
