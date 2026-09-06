# NMEA_SOURCES P7 报告 / P7 Report

状态：`PENDING_MACHINE_GATE`

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
- P7 lock 只有在 `fullRepositoryGate` 仍为 `PENDING_EXECUTION` 时才允许 `PENDING_MACHINE_GATE`；只有实际退出码为 0 后才允许升级为 `MACHINE_VERIFIED`。

## 待执行的唯一完整 Gate

```text
bash .github/scripts/run_marine_shell_final_gate.sh --with-device
```

该命令会在 P7 封口时执行一次。施工中的小修改只运行所属静态或模块测试，不重复消耗完整 Gradle Gate。当前报告不会预填成功数字；最终退出码、测试结果、APK 审计、设备/进程证据和 hosted CI 将在实际完成后写回 lock 与最终报告。

## 明确不冒充的证据

锁屏／熄屏、Doze、OEM 后台限制、真实 Wi-Fi／蜂窝切换、真实 GNSS、功耗、三星方屏触控，以及完整 30 分钟应用级资源曲线都没有物理证据。它们保持 `UNVERIFIED_PHYSICAL_DEVICE` 或 `NOT_RUN`，不能由 JVM、host loopback、模拟器、测试存在或三秒 sender smoke 替代。

## English translation

P7 is the delivery gate, not another feature stage. It aligns executable CI, release and APK audits with the current four-app product while preserving the two-tile default Start document, portrait-only in-app Shell, bounded Back behavior, and no Android Home capability. The lock remains `PENDING_MACHINE_GATE` until the one final full repository/device command actually exits successfully. Physical background, radio, GNSS, power, Samsung-square interaction, manual visual review, and the full 30-minute app soak remain explicitly unverified or not run.
