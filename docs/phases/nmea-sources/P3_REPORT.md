# NMEA_SOURCES P3 报告 / P3 Report

状态：`PASS`

起点提交：`03e4d01`

## 范围与结果

P3 建立了一套作用于所有 `DataKey` 的 OS 选源策略，没有另建 GPS 和仪表选源器。`SourceCatalog`、持久化 `SourcePreference`、`SourceDecision` 与 `ResolvedDataSnapshot` 由同一串行 runtime 发布为一个不可变 `MarineSourceSnapshot`，因此不能出现 B 标签配 A 数值。

选源规则依规范实现：

- 冷启动首次发现有可测的 3 秒聚合窗口；窗口内不发布全局值。
- 窗口结束只有一个 LIVE 候选时自动采用；有多个时必须人工选择，不使用到包顺序猜测。
- 已自动采用 A 后发现 B，保留 A 并置 `needsReview`；用户明确选过 A 时不反复打扰。
- A 中断但 B 正常时仍保留 A 的陈旧／不可用状态，不静默切换；A 恢复后按已保存选择恢复。
- “暂不使用”是可持久化策略；写入失败时旧选择和旧解析值原子保留。
- 连接改名不改变 identity；删除／重启后保留“所选来源缺失”的可解释状态，不复活 live 值。

P1 的 RMC/GGA/GLL 同源确定性合并直接投影为一个候选。手机位置使用 Android `LocationManager` 的真实系统 provider，仅发布平台真正提供的 position、speed、bearing 和 accuracy，缺失字段不补0。权限区分未请求、拒绝、永久拒绝、大致和精确；系统定位关闭也是独立事实。仅用户明确启用后才启动 listener 和 private location foreground service，没有 `ACCESS_BACKGROUND_LOCATION`、mock output 或 Android 桌面 HOME 能力。

## TDD 与自查纠错

Red 提交：`dd191f959c87a475a49fda6f98c672c9ef5f420d`。

- P3 静态合同首跑：`6 tests; 2 failures / 3 errors / 1 pass`。
- core 首跑在 `compileTestKotlin` 因 SourceCatalog、reducer、runtime、phone contract 全部不存在而失败。
- Green 首跑又暴露 `PositionAccuracy` 没有加入 P1 的 exhaustive sentence-priority `when`，修正后再跑。
- 设备故事第一次失败暴露 library test APK 默认 target 26 会使系统以兼容语义报告隐式后台定位。测试 APK 改为与 app-shell 一致的 target 36，重跑后确认只有 coarse/fine + foreground-location，没有 background-location。
- 自查又去掉了跨 runtime 共享的默认内存意图，将 Android provider 选择改为确定性单 provider，并补了永久拒绝和 accuracy 类型边界测试。

## 最小 P3 Gate

```text
python3 .github/scripts/test_nmea_sources_p3_contract.py                PASS 6/6
./gradlew :core:marine-data:test                                        PASS 115/115
./gradlew :adapter:marine-data-android:testDebugUnitTest                PASS 21/21
./gradlew :app-shell:compileStandaloneDebugKotlin                       PASS
./gradlew :adapter:marine-data-android:connectedDebugAndroidTest        PASS 2/2 (API 34 AVD)
processDebugAndroidTestManifest targetSdk inspection                    PASS target=36
git diff --check                                                        PASS
```

按用户要求，没有在 P3 重复执行整仓 lint、assemble 和所有设备套件；完整门禁留在 P7。实机 GNSS、权限对话、锁屏/Doze/OEM 后台证据仍是 `UNVERIFIED_PHYSICAL_DEVICE`，留给 P4/P6 的 UI 与物理设备闭环。

## English translation

P3 passes its scoped gate. One OS-wide policy now owns every `DataKey`: a deterministic three-second discovery window, unique-source auto adoption, explicit multi-source choice, no silent failover, persisted disable, atomic persistence-before-publication, restart-safe missing-source truth, and a single immutable catalog/decision/resolved snapshot. Real Android system location is adapted without invented speed, bearing, accuracy, mock output, background-location permission, or HOME behavior. Static 6/6, core 115/115, adapter 21/21, app composition compile, and two target-36 manifest device stories pass; physical GNSS/background behavior remains explicitly unverified.
