# Yokuli Launcher Engine TDD Log

## NMEA_SOURCES — P0 Repository Alignment

### Baseline

```text
working branch: codex/shell-map-contract
starting SHA: 69bfd4d0ed29f27450351df530b4a8b1e8e2c6a6
source specification SHA-256: a5a38f08f8606d230952dcec8e8f521615efc9ec1a4d30ab4a3821f5943b3348
reference: codex/develop@a845d3d734d3b573a2b53952e66e5f800e944205 (read-only)
```

### Red

先加入 `.github/scripts/test_nmea_sources_p0_contract.py`，要求真实 branch/SHA、旧代码符号映射、四模块依赖方向、Android 36 foreground-service／定位方案、P0–P7/E01–E26 矩阵和最新 Shell 产品边界。首次运行 `6/6 ERROR`，全部因为版本化 baseline、requirements、lock 和 TDD matrix 尚不存在；不是环境或拼写失败。

### Green candidate

新增 `docs/phases/nmea-sources` 与 `docs/implementation/NMEA_SOURCES_*`，把附件中的过期分支、旧仓 `main` 和 `assembleHomeDebug` 与当前事实分开。旧仓只读审计确认单连接 manager、跨 sender UDP framing、generation-free line flow、旧 AUTO 来源策略和 blank heartbeat retention 均不能迁移；纯 parser 语义和测试 fixture 只作为重写依据。

提交后首轮完整 Gate 发现一个真实回归：旧 Stage 0 合同要求 TDD Playbook 继续包含 `LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md` 的历史追溯入口，新首段误删了该链接。Gradle 全量基线已成功；Python 在安装仓库锁定的 `jsonschema==4.25.1` 后为 `229 PASS / 1 FAIL`。独立纠错恢复历史入口且不削弱新 Phase 覆盖关系，最终证据为：P0 `6/6`、全仓 Python `230/230`、Gradle `BUILD SUCCESSFUL`（1151 tasks）、CI topology、Release surface、release metadata、secrets contract 与 `git diff --check` 全部 PASS。

## English translation

P0 begins from the real clean Shell SHA and the owner-selected read-only `codex/develop` reference. A meaningful six-error Red proved that the versioned baseline did not yet exist. The Green candidate records exact integration symbols, reuse/rewrite/non-migration decisions, the minimum four-module plan, Android 36 lifecycle constraints, and the P0–P7/E01–E26 evidence matrix; full gate results remain pending until actually run.

## NMEA_SOURCES — P1 Pure Marine Data Contracts

### Red

起点：`32602b7…`。先加入 P1 静态合同，要求纯 JVM 模块、typed identity/provenance、严格且有界的 NMEA framing/parser、Sentence/Observation catalog、单调老化和本阶段具名行为测试。首次运行结果为 `4 FAIL / 1 ERROR / 1 PASS`：模块、实现和测试尚不存在，失败与 P1 缺口精确对应。自查随即移除误放进 P1 的 selection reducer／transaction 断言；它们严格留给 P3，不用提前实现冒充后续阶段完成。

随后只建立 `core:marine-data` scaffold、公共 `ConnectionId`／`SessionGeneration`／`SourceIdentity`／`ObservationOrigin`／`DataKey`／`MarineObservation` 与 `MonotonicClock`，并用 6 项 identifier/semantic reference tests 固定 talker 不是设备、UDP sender 分离和数据参考系不可合并。协议、catalog、freshness 和 selection 的 Red/Green 继续在独立文件中完成；本提交不包含 Socket、Android 或 UI。

### Review-driven correction

第一轮 Green 达到 45 项 JVM 测试，但独立只读审查仍判定 `FAIL`。审查发现 10 类会破坏后续安全语义的问题：session 仅由首包推进、generation map 可绕过目录上限、跨 formatter invalid 被旧 valid 遮蔽、sentence key 丢失 talker/MWV R-T、checksum trust 丢失、UDP 临时端口进入持久身份、RMC/GLL/GGA/DPT 有效性边界错误、LIVE/HELD 仲裁错误，以及没有 frame group identity。

纠错先扩展静态合同为 7 项并加入具名 JVM 场景；当 `ActiveSessionRegistry`、typed trust/group 和新 identity 尚不存在时，静态合同保持 Red，catalog tests 在 API 迁移期间也按预期编译失败。Green 重写为显式且有界的 connection-session admission、稳定 UDP origin policy、细粒度 sentence inventory、更新 invalid 屏障、freshness-first 仲裁和逐字段 parser 证据。第二轮自查又补上 end 后同 generation 不得复活，以及同一 monotonic millis 内用 group sequence 决定 invalid/recovery 顺序。首个推送候选 `4dff57c…` 的 targeted 结果为 `66/66 JVM PASS`，但仍未获准。

推送后的两轮独立反例审查继续以 `FAIL` 拒绝候选：会话 admission 与 catalog commit 存在 TOCTOU；invalid barrier 会在同 formatter 恢复时丢失；active 容量与 inactive tombstone 容量混淆；Sentence 丢失 same-millis frame sequence；checksum 接受 `+1`；数字换算可溢出抛异常；HOST_ADDRESS 下 UDP sender port 无法从 catalog 恢复；candidate 内部 streams 可无界增长；`MarineObservation` 可构造 key/value/unit 矛盾；经纬度会把 decimal degrees 错解为 packed degrees/minutes，ZDA 的日月词法也不严格。每一项都先加精确 Red；另加 fixed-seed、12 formatter、3000 组 bounded printable-field no-throw 测试做异常安全反查。修正后 targeted 证据为 `83/83 JVM PASS`、P1 `7/7 PASS`和 `git diff --check` PASS；完整仓库 Gate 仍待 correction commit 后执行。

## English translation

P1 starts with a meaningful failing static contract and a platform-neutral scaffold. A first green implementation and its first pushed correction candidate were both rejected by counterexample review. Session linearization, split capacity, nested bounds, retained invalidity, frame ordering, UDP provenance, strict checksum/numeric/date/coordinate lexemes, conversion overflow and value invariants now have failing-before-green contracts. The current correction candidate passes 83 JVM tests and all seven P1 static contracts. Transport, Android, selection transactions and UI remain outside this phase.

## NMEA_SOURCES — P2 Real Input Runtime and NMEA Input Feature

### Red

起点：`d746910…`。先加入 P2 静态合同，锁定两条新模块依赖方向、进程级 runtime 所有权、用户意图／传输／输入健康三条正交事实、有界重试与诊断、private connected-device foreground service、双语 Feature、真实 loopback 和页面退出不停止等具名场景。首次运行结果为 `7 FAIL / 1 ERROR / 1 PASS`；唯一立即通过的是 P2 不得提前安装磁贴／Data Sources，其他失败均由模块、runtime、UI 和测试尚不存在造成。

行为 Red 分三组并行建立：pure connection/runtime policy、生产 socket/DataStore integration，以及 Feature projector/coordinator。P2 不修改 `productionInstalledApps`、`productionCatalog`、`productionHostPort` 或默认 Start Document；可独立 host 的 Workspace 不等于已在 All Apps 正式安装，正式注册严格留给 P5。

### English translation

P2 begins at the corrected P1 commit with a nine-part static contract. Its first run is meaningfully red at seven failures, one missing-file error and one passing phase-boundary assertion. Separate failing behavior suites freeze pure runtime policy, real TCP/UDP and persistence integration, and the NMEA Input feature projection/coordinator. Production Shell registration, tiles, status strip and Data Sources remain outside P2.

Candidate Green 先只运行本阶段最小门禁：P2 static 9/9、core 100/100、真实 localhost adapter 15/15、Feature 16/16，以及 app instrumentation compile。自查先新增失败用例再修复 FGS 拒绝后仍开 socket、无包时不会主动老化、actor 被单帧异常杀死和 DataStore owner 未完全释放四个问题。API 34/36 设备故事留在本阶段设备 Gate，整仓 test/lint/release 只在 P7 运行。

## Marine Shell Final Product-Model Correction

### Baseline

```text
working branch: codex/marine-shell-final-correction
starting SHA: 6555153ae193a75187cd1207ab31adc5aba3291f
Final Launcher attachment SHA-256: 947f06421ac439cd9c02cdaea4b514759a651fcde6cf68e56eda8c7f5125d82d
Product Correction attachment SHA-256: 036245e7192c1fdb5c38e3afe80ff25584cda0ba1e6105ff13fbe875d0f1e958
```

第二份附件对默认 Android Launcher、HOME flavor、导航语义、磁贴模型和 Shell UI 的冲突要求具有覆盖力；第一份附件中不冲突的 Engine 边界、持久化、TDD、性能与机器验证要求继续有效。文档里的审查点 `85d968a…` 已被测试串行化修正 `6555153…` 超越，实际基线不得倒退。

### Red

先建立 `.github/scripts/test_marine_shell_final_correction_contract.py`：

```text
Ran 10 tests
FAILED (failures=6, errors=2)
```

8 项未满足合同精确对应现存 HOME flavor、恢复 task 强制回 Desktop、Search overlay、Windows 中键、缺少 WindowMetrics、Marine Tile 六尺寸、自适应 packer 和旧 Settings 表面；2 项立即通过，证明基线文档和 Chart + Settings-only 产品面已经成立。这批失败是后续可审查 Slice 的施工清单，不是构建或环境错误。

### Slice 1 — Product Identity Correction

删除 `home` flavor、HOME/DEFAULT manifest overlay、`SHELL_HOME_MODE` 和 `ACTION_HOME_SETTINGS`。`onNewIntent(ACTION_MAIN)` 只更新 Android task intent，不再强制内部 Desktop；Recovery 保留普通 `ACTION_SETTINGS`。Release/CI 只构建、审计和发布 standalone in-app Shell APK/AAB。

Green：

```text
Stage 1 contract                                      PASS (9/9)
Stage 9 contract                                      PASS (7/7)
Stage 10 contract                                     PASS (8/8)
CI/release topology contract                          PASS
./gradlew test lintStandaloneDebug
  assembleStandaloneDebug assembleStandaloneRelease  PASS (994 tasks)
standalone Release Chart + Settings / no HOME audit   PASS
```

生成的旧 Baseline Profile 仍包含已删除 home variant 的符号并产生 D8 warning；它不是运行时 HOME 注册，但必须在最终性能 Slice 重新生成，不得作为最终候选遗留。

### Slice 2 — First-class Surfaces & Atomic Transitions

Red 先加入 `ShellTransitionResolverTest` 和 Search Surface reducer 测试，编译因缺少 `ShellVisualSurface`、`ShellTransitionRequest`、resolver 与 state 字段失败。Green 将 Desktop、Module List、Search、Recents、Module 设为一等 Engine Surface；Search 不再是 `LauncherTransient`，并在 `WpSurfaceTransitionHost` 内作为保留的离场 plane 渲染。

`ShellTransitionResolver` 现在以 `from + to + trigger` 唯一解析 Pager、Module、Search 和 Recents 动画族。Search 点击 Chart 产生单个 `Search → Module` request；Activity story 在点击后拒绝中间 Start/Module List 节点。

```text
Shell transition unit tests                           PASS
core:shell-engine full tests                          PASS
Stage 9 navigation contract                           PASS (7/7)
./gradlew test lintStandaloneDebug
  assembleStandaloneDebug                            PASS (795 tasks)
```

### Slice 3 — Marine Bridge Navigation

Red/contract 将旧 `LauncherInput.START`、Windows 四窗格 glyph 和 Android Home-launcher 文案标为非法，并锁定两个来源相关的 Bridge 过渡：Module List 返回 Desktop 必须是 `PAGER_BACK`，Module 返回 Desktop 必须是 `MODULE_TO_DESKTOP`。

Green 将统一输入重命名为 `ShellInput`，中键语义改为仅限应用内部的 `DESKTOP`，Android adapter 明确说明系统物理 Home 仍归 Android；虚拟中键现在绘制四向罗经花并使用“船桥 / bridge”无障碍标签。Android Back、虚拟键和可投递键盘按键仍进入同一个串行 Engine 队列。

```text
Stage 9 navigation contract                           PASS (7/7)
Marine correction contract                            Bridge slice PASS
./gradlew :core:shell-engine:test
  :adapter:shell-android:test
  :app-shell:compileStandaloneDebugKotlin              PASS (192 tasks)
```

### Slice 4 — Safe Window Chrome

Red 先加入纯 JVM viewport 合同，缺少 `ShellWindowMetrics` / `ShellSafeBands` 时编译失败。合同覆盖 320×320/36px 与 360×360/44px 圆角、左右系统手势、边缘 cutout 和 IME 独立抬升。

Green 把宽高、density、safe drawing、display cutout、Android 12+ rounded corners、IME 与 system gestures 从 Android adapter 映射到平台无关合同。Activity 监听真实 WindowInsets 和布局变化；状态条按顶部/左右安全带动态增高，底部 Bridge 条把视觉 glyph 保持在圆角与手势区内，并在 IME 显示时整体抬升而不把键盘高度混入导航安全 padding。

```text
ShellWindowMetrics JVM contract                         PASS (4/4)
Marine correction contract                              Safe chrome slice PASS
./gradlew :core:shell-contract:test
  :adapter:shell-android:test
  :app-shell:compileStandaloneDebugKotlin                PASS (192 tasks)
```

### Slice 5 — Marine Tile Contract

Red 先加入尺寸、内容布局和 Safety cycle 合同；由于 `MarineTileSize`、`TilePresentationKind` 和六个独立 layout 尚不存在而编译失败。

Green 用 `MarineTileSize` 替代三尺寸 WP 类型，支持 1×1、2×1、2×2、4×2、2×4、4×4，并为每一尺寸绑定不同内容布局。Chart 只声明 2×2/4×2/4×4，Settings 只声明 1×1/2×1/2×2；Catalog 同时声明 presentation kind，Safety 明确不可自动翻走。Desktop Renderer 对六种尺寸分别排版，不再把同一内容单纯缩放裁切。

```text
MarineTile contract tests                               PASS (3/3)
core:shell-engine tests                                 PASS
adapter:shell-storage tests                             PASS
app-shell standalone Debug Kotlin compile               PASS (193 tasks)
```

状态：`STAGE_2_5_HUMAN_REVIEWED_APPROVED`。当前日志从 Master Construction Spec 重新编号；旧 Slice 1–14 已保存在 [`archive/pre-launcher-engine/TDD_LOG_PRE_LAUNCHER_ENGINE.md`](archive/pre-launcher-engine/TDD_LOG_PRE_LAUNCHER_ENGINE.md)，只作历史证据。

## Stage 0 — Freeze & Reference Contract

### Baseline

```text
branch: codex/launcher-engine
starting SHA: ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7
master v1.0 attachment SHA-256: f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0
```

Master 附件记录的 reviewed SHA 是 `943d852`，但仓库所有者随后明确要求从最新提交开始。分支先从旧 SHA 创建，随后在没有 Stage 0 改动时纯 fast-forward 到 `ca84ef9`；没有 reset、丢提交或改写 Master 正文。

### Contract

Given `ca84ef9` 是冻结实现基线，When Stage 0 完成，Then 仓库必须逐字保存 Master、建立带 provenance 的 WP8 Reference Lab/schema、明确 screenshots/golden/artifacts 所有权，并让 CI 单独显示该合同；And 不得修改 UI、Registry、Google Map 或 Feature 行为。

旧 Phase 0A/S0–S2、Chart-source、WP8/UI ACTIVE 文档不再留在当前 requirements 目录；旧 TDD 证据归档而不删除，避免既丢历史又让旧编号继续指挥施工。

### Red

先增加 `.github/scripts/test_launcher_stage0_contract.py`，随后运行：

```text
python3 .github/scripts/test_launcher_stage0_contract.py
Ran 7 tests
FAILED (failures=10)
```

失败点精确对应：Master 尚未纳入、Reference Lab/schema 与三个 artifact 目录合同缺失、五份旧 requirement 仍活跃、归档索引和新 Stage 日志缺失、CI 没有命名 gate、TDD Playbook 仍列旧 Chart-first milestone。没有 Android 或环境失败。

### Green

- Master 附件逐字保存，SHA-256 为 `f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0`；
- Reference Lab 建立 provenance、measurement schema、screenshots、Golden 与 artifact 边界，均明确为 `NOT_YET_MEASURED`；
- 当前 requirements 只保留 Master 与不参与施工排序的 secrets supporting contract；
- 旧需求、旧 TDD 和旧 v0.1 实现截图进入 `pre-launcher-engine` 归档；
- Android CI 增加独立 `launcher_stage0_contract`，并参与候选包、失败诊断、Summary 与最终 Gate；
- 与 starting SHA 比较，生产源码和 Gradle 模块图没有变化。

实际 Gate：

```text
python3 .github/scripts/test_launcher_stage0_contract.py                         PASS (7/7)
python3 -m unittest discover .github/scripts 'test_*.py'                         PASS (33/33)
bash .github/scripts/test-ci-contract.sh                                         PASS
bash .github/scripts/test-resolve-release-metadata.sh                            PASS
bash .github/scripts/test-secrets-manager.sh                                     PASS
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug     PASS
git diff --name-only ca84ef9 -- app-shell/src core feature adapter settings.gradle.kts gradle
                                                                                PASS (no output)
```

Stage 0 没有运行 Golden、Macrobenchmark 或新硬件采集；它们是 `NOT_YET_MEASURED`。Hosted GitHub Actions run `33850770612` 随后重跑并通过 Build、API 34 与 API 36。Samsung 方屏仍为 `UNVERIFIED_HARDWARE`。

## Stage 0 correction — Reference and Baseline Contracts

### Baseline

```text
correction starting SHA: 98121412893d5331b22d4327463794993a4a4eff
actual selected Stage 0 starting SHA: ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7
master v1.0 SHA-256: f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0
master v1.1 SHA-256: b0aeb000012283d56cf7e8eb343e2a026366e150b2f9e3d9969f45ed12f4bb40
approval: PENDING_HUMAN_REVIEW
```

### Contract

Given 人工审查拒绝把初始 Stage 0 批准进入 Stage 1，When 纠偏完成，Then 必须锁定新旧基线与 Master 哈希、逐文件对账提前实现、让 schema 的三个状态拥有不同必填证据、用真实 Draft 2020-12 validator 验证正反 fixtures、在 Master 加入 Stage 2.5，并输出完整报告；And 不得改生产 UI、Registry、Maps、Feature、Shell Engine 或海事行为。

### Red

先更新 `.github/scripts/test_launcher_stage0_contract.py`，在 `/private/tmp` 隔离环境安装固定 `jsonschema==4.25.1` 后运行：

```text
Ran 10 tests
FAILED (failures=7, errors=1)
```

失败精确来自缺少 `BASELINE_LOCK.json`、`BASELINE_RECONCILIATION.md`、schema fixtures、正式 Stage report、CI validator 安装、Master v1.1/Stage 2.5 以及旧 Reference/TDD 合同；不是拼写、路径、Android 或功能回归。

### Green

状态相关 schema、内容寻址 capture、场景 measurement sets、direct-manipulation timeline、hash-bound review、七份正反 fixtures、Master v1.1/Stage 2.5、baseline lock/reconciliation 与正式报告完成。`WP8_REFERENCE_MEASUREMENTS.json` 仍不存在，Reference 状态保持 `NOT_YET_MEASURED`；fixture 中的合成值只验证 schema，不是产品测量。

实际本地 Gate：

```text
/private/tmp/yokuli-stage0-schema-venv/bin/python .github/scripts/test_launcher_stage0_contract.py
                                                                                PASS (10/10)
/private/tmp/yokuli-stage0-schema-venv/bin/python -m unittest discover .github/scripts 'test_*.py'
                                                                                PASS (36/36)
bash .github/scripts/test-ci-contract.sh                                         PASS
bash .github/scripts/test-resolve-release-metadata.sh                            PASS
bash .github/scripts/test-secrets-manager.sh                                     PASS
./gradlew --no-daemon test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug --stacktrace
                                                                                PASS (BUILD SUCCESSFUL; 715 tasks)
git diff --name-only 98121412893d5331b22d4327463794993a4a4eff -- app-shell/src core feature adapter settings.gradle.kts gradle
                                                                                PASS (no output)
```

Hosted run `33850770612` 的 Build、API 34 与 API 36 均为 PASS；纠偏提交推送后的 hosted CI 在最终交付中单独记录。Samsung 方屏仍为 `UNVERIFIED_HARDWARE`。

## English translation

Status is `STAGE_0_CORRECTION_PENDING_HUMAN_REVIEW`. The initial Stage 0 started from owner-selected commit `ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7`; hosted run `33850770612` passed build, API 34, and API 36. Human review then required a correction from `98121412893d5331b22d4327463794993a4a4eff`. The correction versions Master v1.1 while retaining the v1.0 hash, adds the mandatory Stage 2.5 reference approval gate, locks and reconciles the baseline, and replaces shallow field inspection with real Draft 2020-12 validation of positive and negative fixtures. No production source or module graph changes. Measurements, Goldens, refresh-rate data, and Samsung square hardware remain unmeasured or unverified; no later Stage has begun.

## Stage 1 — Product Surface Reduction

### Baseline

```text
approved Stage 0 tag: launcher-engine-stage0-approved-v1.1
starting SHA: 16b0e5cd1c8fa2e5f4b78aefadf3fa7c012698b2
Stage 0 approval evidence run: 33854599910
scope: Product Surface Reduction only
approval: PENDING_HUMAN_REVIEW
```

Stage 0 只通过 annotated tag 封口，没有 Correction 2 或文件修改。`ca84ef9…` 已有的 Chart + Settings 实现只作为候选，Stage 1 重新跑 Gate。

### Contract

Given Stage 0 已获批准，When 执行 Stage 1，Then release Start、All Apps、catalog 和 APK 必须恰好只有 Chart + Settings，Chart 只开放 Browse，Coming Soon 与假 SAFE/SOG/COG/Trip/NMEA 值必须缺席，Shell Lab 必须只在 debug；And 不得进入 Stage 2。

### Red

先新增 `.github/scripts/test_launcher_stage1_contract.py`：

```text
Ran 8 tests
FAILED (failures=3)
```

其中 5 项立即通过，重新证明现有 candidate 的 catalog、Start、模块移除、Browse-only、真实 Settings 与无假值合同；3 项只因缺少 Stage 1 baseline/audit/report、release APK 二进制检查和命名 CI Gate 而失败。

### Green

Green 只补审计证据、release binary guard 和精确 All Apps 节点数测试，不重写已经满足产品面 Gate 的 UI。实际本地 Gate：

```text
/private/tmp/yokuli-stage0-schema-venv/bin/python .github/scripts/test_launcher_stage1_contract.py
                                                                                PASS (8/8)
/private/tmp/yokuli-stage0-schema-venv/bin/python -m unittest discover .github/scripts 'test_*.py'
                                                                                PASS (44/44)
bash .github/scripts/test-ci-contract.sh                                         PASS
bash .github/scripts/test-resolve-release-metadata.sh                            PASS
bash .github/scripts/test-secrets-manager.sh                                     PASS
bash .github/scripts/test-release-product-surface.sh                             PASS
./gradlew --no-daemon test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug assembleStandaloneRelease assembleStandaloneDebugAndroidTest
                                                                                PASS (BUILD SUCCESSFUL; 923 tasks)
```

Compose/API 34 与 API 36 等待本 Stage 提交后的 hosted CI；Golden、benchmark、刷新率、Samsung 方屏与实船仍分别保持 `NOT_YET_MEASURED` 或 `UNVERIFIED_HARDWARE`。

## English translation — Stage 1

Stage 1 starts exactly from the annotated Stage 0 approval tag and is limited to Product Surface Reduction. Its eight-test Red immediately passed five candidate product checks and failed three missing audit/report, release-binary, and CI-gate contracts. Green passes all 8 Stage 1 contracts, all 44 Python contracts, the Bash gates, release-APK inspection, unit tests, lint, both debug APKs, the standalone release audit APK, and AndroidTest APK assembly. Hosted emulator results remain pending until push. No compliant production behavior is rewritten, and Stage 2 is not started.

## Stage 1 correction — Release truthfulness

### Contract

Given `GOOGLE_MAPS_CONFIGURED` 只表示提供了非占位密钥，When 修正 Stage 1，Then Release UI 只能报告“地图已配置 / MAP CONFIGURED”和“仅浏览 / BROWSE ONLY”，不得出现船位状态、未来功能或不存在的诊断暗示；And standalone/HOME 两个 Release APK 都必须包含 ShellActivity、Chart、Settings，并排除 Shell Lab 与旧 feature；And 不得修改 Engine、手势、持久化、reducer、海事能力或 Stage 2。

### Red

从 `8914fc81034de250ba7870a019549d9521c581a3` 先扩充 Stage 1 contract，第一次运行：

```text
Ran 9 tests
FAILED (failures=3)
```

失败精确来自 production 用户可见资源仍含 readiness/position/roadmap 文案、CI 未构建和检查 Home Release、Stage 1 报告未记录 correction scope。

### Green

Green 仅修正文案及资源标识、双 Release APK 审计和对应文档。实际本地 Gate：

```text
/private/tmp/yokuli-stage0-schema-venv/bin/python .github/scripts/test_launcher_stage0_contract.py
                                                                                PASS (10/10)
/private/tmp/yokuli-stage0-schema-venv/bin/python .github/scripts/test_launcher_stage1_contract.py
                                                                                PASS (9/9)
/private/tmp/yokuli-stage0-schema-venv/bin/python -m unittest discover .github/scripts 'test_*.py'
                                                                                PASS (45/45)
bash .github/scripts/test-ci-contract.sh                                         PASS
bash .github/scripts/test-resolve-release-metadata.sh                            PASS
bash .github/scripts/test-secrets-manager.sh                                     PASS
bash .github/scripts/test-release-product-surface.sh                             PASS (standalone + HOME)
./gradlew --no-daemon test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug assembleStandaloneRelease assembleHomeRelease assembleStandaloneDebugAndroidTest
                                                                                PASS (BUILD SUCCESSFUL; 952 tasks)
```

提交后的 API 34/API 36 hosted 结果在最终人工交付中记录；报告保持 `PENDING_HUMAN_REVIEW`，不自行批准 Stage 1。

## English translation — Stage 1 correction

The correction starts from `8914fc8…`. Its nine-test Red fails exactly three missing truthfulness-copy, HOME release-audit, and correction-report contracts. Green passes Stage 0 (10/10), Stage 1 (9/9), all Python contracts (45/45), all Bash gates, unit/lint/assemblies (952 Gradle tasks), and binary inspection of both Release flavors. Stage 2 remains stopped.

## Stage 2 — Engine Contract Extraction

### Baseline

```text
approved Stage 1 tag: launcher-engine-stage1-approved-v1
starting SHA: df371fbfcb4cd467bccc43dd850e23d9bd7d0e85
Stage 1 approval evidence run: 33861223067
scope: Engine Contract Extraction only
approval: PENDING_HUMAN_REVIEW
```

### Contract

Given Stage 1 已获人工批准，When 执行 Stage 2，Then 必须建立 `shell-contract`、纯 Kotlin `shell-engine`、`shell-compose`、`shell-android`，以 opaque ID/token、contribution/catalog、`LauncherHostPort` 和 `InternalAppHostResolver` 解耦 Engine；And Release catalog 仍只由 Chart/Settings 组合且两者可打开；And 不得修改手势、动画、布局语义、持久化或海事能力，不得进入 Stage 2.5。

### Red

先新增 `.github/scripts/test_launcher_stage2_contract.py`，第一次运行：

```text
Ran 9 tests
FAILED (failures=9)
```

九项失败分别来自 Stage 2 baseline/report、四模块边界、opaque contract、Engine 禁止依赖、contribution catalog、Compose/Android host adapter、旧 Marine 路由类型和命名 CI Gate 均尚未满足；不是拼写、错误路径或环境故障。

### Green

Green 只抽取合同、迁移类型/依赖和重新接线现有 Chart/Settings。冻结候选中的 geometry/layout/interaction 仅迁移到新的纯 Engine 边界，不扩展行为、不作为后续 Stage 证据。实际本地 Gate：

```text
/private/tmp/yokuli-stage0-schema-venv/bin/python .github/scripts/test_launcher_stage0_contract.py
                                                                                PASS (10/10)
/private/tmp/yokuli-stage0-schema-venv/bin/python .github/scripts/test_launcher_stage1_contract.py
                                                                                PASS (9/9)
/private/tmp/yokuli-stage0-schema-venv/bin/python .github/scripts/test_launcher_stage2_contract.py
                                                                                PASS (10/10)
/private/tmp/yokuli-stage0-schema-venv/bin/python -m unittest discover .github/scripts 'test_*.py'
                                                                                PASS (55/55)
bash .github/scripts/test-ci-contract.sh                                         PASS
bash .github/scripts/test-resolve-release-metadata.sh                            PASS
bash .github/scripts/test-secrets-manager.sh                                     PASS
bash .github/scripts/test-release-product-surface.sh                             PASS (standalone + HOME)
./gradlew --no-daemon test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug assembleStandaloneRelease assembleHomeRelease assembleStandaloneDebugAndroidTest
                                                                                PASS (BUILD SUCCESSFUL; 954 tasks)
bash .github/scripts/run_device_tests.sh all                                     PASS (API 34; 8/8)
```

提交后的 hosted API 34/API 36 结果在最终交付中记录；Samsung 方屏、Golden、benchmark、刷新率、输入延迟和真实 WP8 Reference 保持未验证或未测量。

## English translation — Stage 2

Stage 2 starts exactly from the approved Stage 1 tag. All nine initial architecture tests fail meaningfully before implementation because every Stage 2 boundary is absent; the final Green suite adds one current-device-story guard. Green is limited to pure contract/engine extraction, opaque identifiers and tokens, contribution/catalog composition, host ports/adapters, and rewiring the existing Chart/Settings UI. Stage 0/1/2 static gates, all 55 Python contracts, Bash gates, 954 Gradle tasks, both Release APK inspections, and eight local API 34 real-Activity stories pass. Existing UI, gestures, animations, and candidate layout behavior are not expanded. Stage 2.5 remains unstarted.

## Stage 2.5 — WP8 Reference Acquisition & Human Approval

### Baseline

```text
approved Stage 2 tag: launcher-engine-stage2-approved-v1
starting SHA: 5386da0575046f1f9a59742a4a0f5c78523fa5e6
Stage 2 approval evidence run: 33864829489
branch: codex/launcher-engine-stage2.5-rebuild
scope: WP8 Reference Acquisition & Human Approval only
approval: APPROVED by kuku at 2026-09-04T13:41:36Z
```

### Contract

Given 仓库所有者拒绝先前 Stage 2.5 候选中手持／倾斜手机图片并提供 `kuku.mp4` 作为模拟器真机录屏，When 重建 Stage 2.5，Then 所有视觉像素与运动证据必须只来自该视频的完整无变换帧，并由 SHA-256、字节数、尺寸、时间戳和引用关系锁定；And 文档搜索只用于语义佐证；And 未出现的 edit、fast fling、pin、long-press drag、resize、unpin、press/key activation 必须保留 `NOT_OBSERVED`／`VISUAL_ONLY`；And 人工批准前不得开始 Stage 3。

新增产品决定同样进入合同：Yokuli OS 未来默认沉浸式全屏，使用壳内虚拟 Back／Start／Search；所有虚拟、Android 和未来物理输入必须汇入同一串行 `LauncherEngine.dispatch(action)`，但 Stage 2.5 只记录边界，不修改 runtime。

### Red

先新增 `.github/scripts/test_launcher_stage25_contract.py`，第一次运行：

```text
Ran 8 tests
FAILED (failures=9, errors=3)
```

失败精确来自 Stage 2.5 baseline、source manifest、measurements、measurement method、rights notice、fullscreen decision、semantic validator、named CI gate 与 report 尚不存在。它不是 Android、网络、拼写或错误路径造成的假 Red。

### Green preparation

- 用户提供的 408044 ms、1920×1080、nominal 60 fps H.264 视频成为唯一视觉 source；
- 固定 PyAV/Pillow acquisition 环境，按 16 个时间戳解码完整 PNG，不裁剪、缩放、标注或调色；
- `SOURCE_MANIFEST.json` 锁定视频及逐帧 hash/bytes/dimensions/reference/coverage/rights；
- 以 Microsoft 公开资料的 480×800 逻辑基准，将录屏内 `(706,62,506,844)` 显示矩形独立归一化；
- 测得 side inset 24、seam 12、Small 99×99、Medium 210×210、Wide 432×210 logical px；
- 记录 Start→All Apps、app open、app→Start 和 Live Tile visible windows，同时明确输入不可见与数值语义边界；
- 新增语义 validator，验证 schema、signature、path containment、hash、bytes、dimensions、references、timeline、geometry、coverage 与 review hash；
- schema 只向后兼容地允许未知 physical DPI 为 `null`，并为 Back/Live Tile 增加真实 interaction 名称；Stage 0 fixtures 继续回归；
- Stage 2.5 CI Gate 使用 `--require-human-review`，因此在所有者批准前有意保持关闭。

准备态验证：

```text
python3 .github/scripts/validate_wp8_reference.py
WP8_REFERENCE_VALIDATION=PASS status=MEASURED captures=16 measurementSets=5
canonicalMeasurementHash=af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5
```

准备态完整回归结果：Stage 0 `10/10`、Stage 1 `9/9`、Stage 2 `10/10`、Bash CI/release/secrets、双 Release APK 产品面审计及 954 个 Gradle tasks 全部通过。批准前 Stage 2.5 为 `8/9`、全部 Python 为 `63/64`；唯一失败都是语义 validator 正确拒绝尚未签署的 `HUMAN_REVIEWED`。仓库所有者 kuku 随后批准 profile revision 1 与 canonical hash；review record 不是 Codex 自签。

批准后最终 Green：Stage 2.5 `9/9`、全部 Python `64/64`、Stage 0–2、Bash CI/release/secrets、双 Release APK 审计和 954-task Gradle Gate 全部通过。Production module diff 为空，Stage 3 未开始。

### English translation — Stage 2.5

Stage 2.5 starts from the approved Stage 2 tag and discards the rejected handheld/angled candidate imagery. The repository-owner-supplied `kuku.mp4` is the only visual source. Sixteen exact, uncropped frames and five scenario measurement sets are content-addressed; official Microsoft documents corroborate semantics but contribute no pixel or timing values. Unseen edit, fling, pin, drag, resize, unpin, press, and key-activation behavior remains `NOT_OBSERVED` or `VISUAL_ONLY`.

The meaningful Red failed because all Stage 2.5 artifacts and its CI gate were absent. Green adds deterministic frame extraction, source and rights manifests, normalized 480×800 geometry, observable-motion timelines, a semantic validator, and the bilingual report. The owner's immersive-fullscreen and virtual Back/Start/Search requirement is recorded as a later-stage input boundary, with one serialized engine action path. Production code is unchanged. Repository owner kuku approved profile revision 1 and the canonical measurement hash, so the package is `HUMAN_REVIEWED`; Stage 3 has not started.

## Batch A foundation — Installed app composition binding

### Contract and Red

在 Stage 3 之前执行仓库所有者要求的无额外人工 Gate 基础提交。新增 `.github/scripts/test_installed_app_binding_contract.py`，要求一个 `InstalledAppBinding` 同时拥有 catalog contribution、token registrations、visual contributions 与 internal host，并要求所有 runtime registry 均由唯一 binding 列表派生。第一次运行四项全部失败，因为原实现仍维护分散的 catalog/token/host 表并按产品 ID 构造视觉状态。

```text
Ran 4 tests
FAILED (failures=4)
```

### Green

Green 将 Chart/Settings 各注册一次，从 `productionInstalledApps` 派生 Catalog、HostPort、视觉 Registry 和 `InternalAppHostResolver`；动态主题、语言、地图配置和设置回调通过 composition-local runtime 注入，Engine 不获得任何产品或 Compose 依赖。原 Stage 1/2 静态 Gate 只调整结构识别方式，仍精确要求 Chart + Settings 且保持全部旧拒绝项。

```text
installed-app binding contract                  PASS (4/4)
all Python contracts with pinned jsonschema    PASS (68/68)
CI/release metadata/secrets Bash contracts     PASS
Standalone/Home debug Kotlin compilation       PASS
feature desktop JVM tests                      PASS
full Gradle test/lint/debug/release/androidTest PASS (954 tasks)
dual Release binary product-surface audit      PASS
Stage 2.5 semantic validator                   PASS (HUMAN_REVIEWED)
```

第一次完整 Gradle 回归在 AndroidTest 编译处发现方屏 story 仍调用旧视觉函数签名；没有忽略该失败。修正为显式注入由安装 binding 派生的 `productionVisualContributions` 后，先重跑失败目标，再重跑全部 954-task Gate 通过。

本基础提交不新增 Stage、REPORT 或 BASELINE_LOCK，不改变几何、Reducer、手势、持久化、产品表面或 Stage 2.5 证据；Stage 3 必须在其独立 commit 中开始。

### English translation — Batch A foundation

Before Stage 3, one small owner-requested foundation commit consolidates each installed app's catalog contribution, launch registrations, visual contribution, and internal Compose host into one composition-root binding. A four-test Red first proves the registries were distributed. Green derives every registry from the two Chart/Settings bindings, removes product-ID visual branching and Activity-owned host tables, and preserves all Stage 0–2.5 behavior and boundaries. This is not a Stage and therefore has no Stage report or baseline lock.

## Stage 3 — WP Geometry & Start Document

### Baseline and contract

Stage 3 从 `launcher-engine-stage2.5-approved-v1`、人工批准 measurement hash `af4ed6d…` 及独立 foundation commit `53a239c…` 开始。合同要求 measured profile、整数像素 viewport geometry、标准三尺寸、二维 `StartDocument`、确定性 validator/repair、320/360 方屏 bounds、有意留白和 Chart Wide + Settings Small 默认布局；不得进入 reducer、pager、gesture、persistence 或实体键实现。

### Red

先新增 `.github/scripts/test_launcher_stage3_contract.py`：

```text
Ran 6 tests
FAILED (failures=2, errors=4)
```

缺口精确对应 baseline/report、reference profile、viewport geometry、StartDocument、默认文档和命名 CI Gate 尚不存在。

### Green and refactor

实现 `WpReferenceProfile` 时只带入录屏测得值；`longPressMillis`、`pressScale`、`fastFlingThreshold` 保持 `null / NOT_OBSERVED`。480×800 精确还原 24/12/99/210/432 与状态条 32；320/360 通过相同比例、整数 snapping 和尾端 remainder 分配完整消费宽度。`SQUARE_4COL` 明确为派生 profile，不冒充三星真机证据。

`StartDocument` 以 schema/profile/default-layout 三个版本字段和 explicit cell 为真值；重排 placement 列表不改变位置。修复流程可确定性移除未知/重复 Entry、替换不支持尺寸、重定位越界/重叠项，并在 profile/文档损坏时回到默认文档。旧 UI 的离散编辑入口只迁移类型，transaction identity/Undo 仍留给 Stage 4。

### English translation — Stage 3

Stage 3 is driven by the human-approved Stage 2.5 hash rather than guessed ratios. It adds revisioned reference profiles, integer-pixel viewport geometry, standard WP tile formulae, and a versioned spatial Start document with explicit cells and deterministic validation/repair. Phone geometry is measured; square profiles are derived and remain hardware-unverified. Unseen interaction parameters remain null. Reducer, effects, serialized dispatch, persistence, pager, gestures, and shell key routing are outside this commit.

最终回归：Stage 0 `10/10`、Stage 1 `9/9`、Stage 2 `10/10`、Stage 2.5 `9/9`、Stage 3 `6/6`、全部 Python `74/74`、完整 Gradle `954 tasks`、双 Release 二进制产品面审计和 Reference semantic validator 全部通过。Stage 3 Gate 通过后才允许创建 Stage 3 commit；Stage 4 尚未写入该 commit。

## Stage 4 — Engine State, Effects & Persistence Ports

先写 7 项 Stage 4 合同并得到真实 Red（3 failures、4 errors），随后建立纯 Reducer、串行 Channel controller、Engine state/effects、确定性 layout transaction、cancel/undo、persistence port 与 ViewModel。失效 LaunchToken 保持当前 surface 并产生 `LogIncident`；HostPort catalog flow 成为 Engine 和 UI 的唯一 runtime catalog 真值。Activity 不再以 `remember` 持有 navigation 或 StartDocument，配置重建 story 验证同一 ViewModel 的文档保持。完整结果记录于 `docs/stages/stage-4/REPORT.md`。

### English translation — Stage 4

Seven contract tests first failed on the absent Stage 4 architecture and evidence. Green introduces a pure reducer, serialized controller, state/effects, deterministic transactions, cancel/undo, persistence port, and ViewModel ownership. Unresolved launch tokens no longer crash, and the HostPort catalog flow is the only runtime catalog rendered by the UI. The Activity recreation story locks the lifecycle boundary; process-death durability remains Stage 10.

## Stage 5 — Interactive Start / All Apps Pager

6 项静态合同先以 2 failures/4 errors 证明旧 `SwipeSurface`、缺失 pager、缺失 stories 和 stage evidence。Green 用 Foundation `HorizontalPager` 建立 direct manipulation，并将 settled page 与 Engine 双向连接；edit mode 关闭 page user scroll。第一次窄编译找出 `snapshotFlow` import 错误，第二次找出 AndroidTest touch scope API 错误，均修正后通过。首次 13-story 模拟器 Gate 又发现旧语言 story 错误假设 Activity 重建会回 Start；Stage 4 已正确保留 Engine task，测试改为先验证当前语言页再按确定性 Back 层级返回。修正期间还处理了双页并存导致的重复文本语义，最终单测复跑和全部 13 条 API 34 stories 均通过。没有用自定常数填补 Stage 2.5 未观察到的 fling/press 数据。

### English translation — Stage 5

Six contracts first fail on the fixed-distance swipe implementation and missing evidence. Green installs a Foundation-backed direct-manipulation pager, synchronizes settled pages with the Engine, and disables paging during edit mode. Two compile-time API mistakes were corrected by narrow reruns. No unobserved WP8 fling or press constants are introduced.

## Stage 6 — Custom Spatial Grid

6 项合同先在缺少 occupancy/solver/custom Layout/JVM stories/report/CI gate 时进入 Red。Green 新增显式占用索引、只处理直接冲突的确定性 solver，以及从 Engine `StartDocument` 渲染的像素对齐 Layout。提案通过独立 `proposedDocument` 驱动，浮动项仍直接跟手，邻居只在 graphics layer 移动，提交仍由 Stage 4 transaction 完成。

窄回归第一次找出错误的 Compose Constraints API，并证明 60 项测试原先对“释放出的原位置”写了错误预期；分别修正实现 API 和测试期望后，引擎测试与 desktop 编译通过。测试同时锁定未受影响项坐标、用户空白、文档顺序和重复求解一致性。

### English translation — Stage 6

Six contracts begin red while occupancy, collision solving, the custom renderer, JVM stories, evidence, and CI gate are absent. Green adds explicit cell lookup, deterministic local collision resolution, and a pixel-snapped Compose Layout driven by StartDocument plus a transient proposed document. Narrow tests caught one invalid Constraints API call and corrected a mistaken test assumption about the moving tile's newly freed cell. Unrelated coordinates, intentional whitespace, placement order, and repeatability are now locked.

## Stage 7 — Complete Edit / Drag / Resize

7 项合同先得到 4 failures / 3 errors。Green 把编辑状态和所有文档相关输入迁入串行 Engine；Renderer 只保留 pointer plane 的输入采集。第一次窄回归暴露 Stage 4 effect 测试订阅 race 和 Shell Lab positional constructor 兼容问题：前者改为 `CoroutineStart.UNDISPATCHED` 确保先订阅再派发，后者通过有默认值的尾参数保持 debug-only Lab API。两者均没有改变 Stage 4 行为合同。

随后加入 provisional resize transaction、原位 placeholder、Back/pause/viewport/catalog cancel、派生的 hysteresis/edge-scroll policy、Android 标准 haptic effect host 与中英文无障碍方向动作。JVM tests 锁定 grab offset、pre-drop neighbor proposal、hysteresis、auto-scroll compensation、invalid/cancel safety、catalog cancellation 和 Small/Medium/Wide 循环；真实 Activity stories 锁定 Back 优先级和 Resize 视觉尺寸循环。

### English translation — Stage 7

Seven contracts begin with four failures and three errors. Green moves edit and document-affecting pointer actions into the serialized Engine while retaining only pointer-plane collection in the renderer. Narrow validation also removes a race from the Stage 4 effect test and preserves Shell Lab constructor compatibility. Provisional resize, origin placeholder, cancellation boundaries, derived hysteresis/edge scrolling, host haptics, and bilingual accessibility movement actions are covered by JVM and real-Activity stories. Unobserved tuning is never presented as WP8 reference evidence.

## Stage 8 — Pin / Unpin / Context Menu

静态合同先以 `5 failures / 2 errors` 进入 Red；JVM Red 随后因 typed context、Pin/Unpin、reveal 与反馈状态不存在而编译失败。Green 将 All Apps 长按菜单从 Compose 本地状态迁入串行 Engine，并建立显式 `PinEntry` / `UnpinTile` transaction、返回 Start、定位高亮、双向 Undo、重复/不可用反馈和 catalog revision 失效规则。

Stage 2.5 未观察 Pin/Unpin 动效，因此实现不填写 WP8 时间常数。Reveal 使用默认 spring，在动画收敛后由 Renderer 派发确认；轻微视觉幅度标为 `DERIVED_UNVERIFIED`。Unpin 只改变 StartDocument；Catalog 新增不自动 Pin，删除 Entry 不移动其余 placement。

### English translation — Stage 8

The meaningful Red failed on the absent Engine-owned context, explicit Pin/Unpin actions, reveal state, feedback, evidence, and CI gate. Green makes Pin/Unpin deterministic transactions with exact Undo, returns successful Pin to Start, reveals the new tile, preserves installed entries during Unpin, and prevents catalog additions or removals from silently rebuilding the desktop. Unobserved Pin motion remains explicitly derived rather than WP8 evidence.

## Stage 9 — Navigation / Motion / Immersive / Virtual Keys

7 项静态合同先以 `4 failures / 3 errors` 进入 Red，JVM/Android adapter Red 同时因统一 Input、Search、Recents、内部 route stack 和 host-exit effect 不存在而编译失败。Green 把虚拟键、Android Back 与 Activity 实际收到的键事件统一为 typed `LauncherInput -> LauncherAction -> serialized Engine`；HOME flavor 使用 `singleTask/onNewIntent`，没有谎称普通 App 能截获系统保留的实体 HOME。

22 条真实 Activity story 第一次即通过虚拟 Start、Catalog Search、长按 Back Recents、Android Back/硬件键和 HOME intent。自审再增加 provisional layout Red，修复 Search/Recents 离开 Start 时的取消边界，并修复 catalog 更新后的悬空 Recents return surface。动效取自 Stage 2.5 已批准可见区间，MapView 延迟到轻量 plane settle 后挂载；虚拟键 54dp 区域和平台默认触感标为 `DERIVED_UNVERIFIED`，不伪造 WP8 灯效或输入 latency。

### English translation — Stage 9

Seven static contracts begin with four failures and three errors, while JVM and Android adapter Reds fail on absent typed input, Search, Recents, opaque route history, and host-exit semantics. Green routes every virtual and deliverable platform key through the serialized Engine, reuses the HOME Activity task, provides real catalog search and internal-task recents, binds motion to approved visible intervals, supports reduced motion, and delays MapView mounting. Self-review adds and fixes a provisional-layout cancellation regression and stale Recents return target.

## Stage 10 — Durable Storage & Recovery

8 项 Stage 10 静态合同先以 `FEFF.E.F`（4 failures / 2 errors / 2 pass）进入 Red；Core Red 同时因 durable state、migration、recovery policy 与 restore action 尚不存在而无法编译。Green 新增单一 Proto DataStore snapshot、原子 document/preferences/health 写入、schema migration、corruption fallback、确定性 Start repair、三次短窗口启动失败 Safe Mode、Reset Start 和 Android 默认桌面设置出口。

第一次 API 34 targeted story 暴露了 Activity 重建期间可能同时创建两个同文件 DataStore 的真实错误，修正为 Application-scope 单例。原先尝试用外部 Activity monitor 验证 Settings intent 在现代系统上不可观测，测试改为注入平台 intent boundary 并精确断言 `ACTION_HOME_SETTINGS`。自审继续以 Red 锁定：恢复等待期间 Catalog 变化不得丢失；migration/repair/failure 必须进入串行 Engine Incident；Safe Mode 只能忽略自定义布局，不能偷偷覆盖布局或主题；启动健康计数不能被慢恢复定时器提前清零。所有问题均在累计 Gate 前修正。

最终本地 Gate：Stage 0–10 Python 合同 `122/122`；Gradle `test`、`lintStandaloneDebug`、双 flavor Debug/Release 和 instrumentation APK 共 1052 tasks；API 34 Activity stories `23/23`；双 Release APK 仍严格为 Chart + Settings 且只有 Home 增加 HOME/DEFAULT；release metadata、CI、secrets、WP8 approved hash 和 `git diff --check` 全部通过。Stage 11 尚未开始。

### English translation — Stage 10

Stage 10 makes the committed launcher snapshot durable through one atomic Proto DataStore, with migration, corruption fallback, deterministic document repair, crash-loop recovery, a non-destructive Safe Mode, reset, and Android Home-settings escape. Self-review fixed a duplicate-DataStore lifecycle error, a startup-health race, lost catalog updates during restore, missing incident recording, and destructive Safe Mode behavior. Android physical HOME remains OS-reserved; HOME intent reuse and the recovery surface are the truthful integration path.

## Stage 11 — Performance & Fidelity Gate

### Red

Stage 11 初始静态合同在 benchmark/profile 模块、60 Tile、320/360 方屏、Golden candidate、CI performance job、BASELINE_LOCK 与 REPORT 均不存在时得到 `3 failures / 3 errors`。实现中自审再加入生成产物合同，部分 Green 状态重新得到 `1 failure / 2 errors`：`baseline-prof.txt` 尚未生成，Stage 文档也未完成。这样避免“generator 能编译”被误报成“Profile 已交付”。

### Green 与逐轮纠错

建立 `:benchmark:shell`、`:baselineprofile:shell`、release-like benchmark target、AndroidX Macrobenchmark、Baseline/Startup Profile、60 Tile Shell Lab、模拟方屏 story、候选图语义 validator、趋势汇总器和独立 CI job。第一次 cold-start benchmark 通过前，依次修正了未签名 test APK、self-instrumenting 配置和 Compose test-tag 查询方式。

Baseline Profile 的 Red 更有价值：`targetContext` 在 self-instrumenting 模式下指向测试包，第一次生成器把自己 force-stop；修成显式 flavor applicationId 后又发现 HOME 不能依赖隐式 launcher 解析；后续测试暴露持久 All Apps、已 Unpin 文档和 Stage 10 crash-loop Recovery 都会破坏固定前置状态。最终每轮只清理目标测试包数据，用显式 component 启动，并执行真实 Start、Edit、Unpin、All Apps、Context Pin、Chart 和 Back 路径。启动 profile 首次只等待 package，得到 0 行；改为等待真实 `start-screen` 后才生成非空规则。动态 context 菜单上的物理 tap 偶发丢失，最终使用 accessibility `ACTION_CLICK`，同时用 Start／Tile 结果断言，避免“脚本跑完但旅程没发生”。自审还把 product filter 从 `startsWith("Lcom/yokuli/")` 改为 `contains`，保留带 H/S/P flag 的热方法规则。

九张候选覆盖 Master 的八个固定场景和额外 360×360 方屏。稳定场景使用 API 34 模拟器 `adb screencap`；启动平面从正常动画比例下的 Android `screenrecord` 提取真实透视帧。第一次 All Apps 截图在 accessibility hierarchy 已有三键时仍未画出 glyph；等待五秒也没有修复，证明不是简单采样时序。最终为按键栏建立独立 graphics layer，并在 surface/transient 变化时重新建立绘制层，`adb screencap` 才稳定显示三枚 glyph。Compose root capture 不会复现这个 SurfaceFlinger 现象，因此保留 Activity 像素故事作为附加保护，同时把真实 Red 明确记录为设备截图。

继续用虚拟 Back 检查字母跳转时，又发现该层原先只存在于 Compose 局部状态：Back 会直接离开 All Apps。新增 reducer Red 后，将字母层提升为 `LauncherTransient.AlphabetJump`；现在 group tap、overlay dismiss 和虚拟／Android Back 都经过同一串行 Engine 通道，Back 会先关闭字母层并保持 All Apps。候选 manifest 和 comparison 明确不把模拟方屏、物理刷新率、WP8 key-light/haptic/latency 或 Android 系统保留 HOME 编造成通过。

封口 Gate 又产生三次有效 Red。第一，60 Tile Macrobenchmark 无法从 UiAutomator 发现 Compose 根节点，Shell Lab 增加 `testTagsAsResourceId` 后通过。第二，连续 journey 的 harness force-stop 被 Stage 10 当作生产崩溃并进入 Safe Mode；先隔离 `benchmark` 后，Baseline Profile 的 `nonMinifiedRelease` 也复现同一问题，因此最终仅对两个 harness build type 中和健康状态，Debug/Release 恢复合同保持原样。第三，新增 Alphabet Activity story 的自定义 matcher 直接读取缺失的 `TestTag` 导致测试自身异常；改为先 `contains` 后读取，单测复现和完整 `26/26` 设备故事均通过。

Profile 生成最初使用 AndroidX 默认最多 15 轮，在两个 flavor 上耗时过长。新增 Red 固定最多 3 轮、连续 2 轮稳定收敛；它只负责收集热规则，不取代 5-repeat Macrobenchmark 或真机门。最终源码重新生成 Baseline `1824` 条、Startup `1464` 条产品规则，模拟器动画倍率随后恢复到原值 `0`。

第一次 hosted Stage 11 run `33919498098` 的主门与 API 36 通过，但 API 34 暴露 `@Before` 的异步 reset 竞态：旧 Chart Tile 使单条件等待提前返回，Settings 尚未恢复便开始 Pin/Undo。修正后 reset 明确回到 Home，测试等待 Start + Chart + Settings 完整前置状态；本地失败 case 与完整 26 条故事通过。该 run 的性能 job 也失败，但原 workflow 没把 benchmark XML 转成 annotation；failure reporter 现接受显式 result root，诊断 bundle 纳入 benchmark/profile 模块。启动 benchmark 改用显式 ShellActivity intent 并给 hosted emulator 20 秒 tag 窗口。Red 复现又发现 warm-start setup 提前启动目标 Activity 会和 AndroidX 控制的启动生命周期竞争，产生 `Target package ... is not running`；setup 现只执行 `pressHome()`，唯一被计时的启动留在 measure block。最终本地五条 journey 全部通过，后续全绿 hosted run 才能作为 correction 接受证据。

第二次 hosted run `33922516174` 证明 reset 前置状态已稳定且 API 36 继续通过，但首条 API 34 story 在点击 Chart 后立即断言目标页面，快于串行 Engine queue 完成，暴露 `chart-workspace-browse` 尚未显示。Red 不是通过关闭动画或增加 sleep 掩盖：真实 Activity stories 对每个跨 Engine Surface/Transient 的动作等待目标语义节点实际可见（消失路径等待节点实际移除），仍保留最终可见性断言和完整 WP 动画。接受条件保持完整 26/26，而不是只重跑失败 case。

同一 hosted run 的五个性能 case 都提供了明确 annotation：四个生产 journey 未观察到 Start tag，60 Tile trace 则报告没有 RenderThread slice。三个 emulator job 当时强制使用 `swiftshader_indirect`；该后端已被当前 Android Emulator 官方弃用，且与启用动画／FrameTiming 的失败形态一致。CI 改为官方推荐的 `-gpu auto`，让 runner 根据宿主能力选择硬件或软件后端；没有移除 `FrameTimingMetric`、没有关闭 API 34 动画、也没有把失败改成 continue-on-error。

第三次 hosted run `33923989525` 让 API 34 完整 stories 与 API 36 smoke 都通过，证明图形后端和 Engine transition 等待修正有效；performance 仍精确复现 fresh-install Start timeout 与软件 renderer 无 RenderThread slice。最终修正把 harness 强制 stop/reinstall 与用户语言生命周期隔离，避免首次 `LocaleManager` 重建污染被测启动。交互帧指标按执行环境选择：emulator 使用 AndroidX `FrameTimingGfxInfoMetric` 读取目标进程 `dumpsys gfxinfo`，物理设备仍使用 Perfetto `FrameTimingMetric`。两者都采集真实帧；emulator 仍只作为趋势，物理 60/90/120 Hz Gate 仍未被替代。

第四次 hosted run `33925469495` 证明 gfxinfo 分支已消除 60 Tile 的 RenderThread trace 失败，但 fresh target 仍可能在持久化冷加载窗口显示 Recovery；harness 现于 ViewModel 建立后立即排队默认文档恢复与 Home，不等待会被 benchmark process control 干扰的生产 crash-loop 时序。该 run 还捕获到四条 Activity story 的随机操作丢失；上一轮相同代码 26/26 表明它是 `resetLauncher()` coroutine 尚未入队／出队便开始下一条手势。reset 现返回 `Job`，测试等待其完成，再通过 `SAFE_MODE → NORMAL/Start` 状态往返作为串行 action queue barrier；没有使用固定 sleep，也没有改变生产用户流程。

### Green Gate

最终本地 Gate 包括 Stage 0–11 Python/helper contracts、Golden validator、版本化 Baseline/Startup Profile、5/5 Macrobenchmark 代表性 emulator journeys、完整 Gradle test/lint/双 flavor Debug+Release+androidTest、API 34 Activity stories `26/26`、双 Release 产品面、CI/release/secrets 合同与 Stage 2.5 approved hash。模拟器 metrics 只写 `EMULATOR_TREND_ONLY`；本机只有 API 34，API 36 reduced-motion smoke 留给 hosted CI。

### English translation — Stage 11

The Stage 11 contract first fails on absent benchmark/profile modules, 60-tile and square coverage, candidate Goldens, CI integration, and stage evidence. A self-review Red additionally requires generated, non-empty, product-scoped Baseline and Startup Profile files. Green adds real AndroidX Macrobenchmark and Baseline Profile infrastructure, nine content-addressed emulator candidates, a non-gating trend summary, and a dedicated CI job. Iterative failures corrected self-process force-stop, flavor IDs, implicit HOME launch resolution, persistent/recovery state contamination, empty startup profiles, unreliable dynamic-menu taps, and an overly narrow profile-rule filter. A real adb screenshot—not Compose capture—exposed missing key glyph layers over dynamic launcher planes; an isolated graphics layer and re-key fix made them visible. A second Red moved Alphabet Jump out of local Compose state into an Engine transient so Back dismisses it before leaving All Apps. Physical refresh rates, Samsung square hardware, Golden approval, WP8 key behavior, and Android's OS-reserved HOME remain truthful human/hardware gates.

## Marine Shell Final Correction — Adaptive Tile Packer

### Red

先加入 `AdaptiveTilePackerTest`，编译因 `AdaptiveTilePacker`、`TileDocumentEntry`、`Spacer` 和 rank 文档不存在而失败。测试覆盖混合六尺寸确定性、4/6 列复用同一持久文档、insertion 重排、显式 Spacer、释放空间回填，以及固定随机种子的 100 组 × 30 Tile 性质检查。

### Green and self-review

持久模型由绝对 row/column 改为 rank/size/group；cell 只存在于 `AdaptivePackedLayout` 的 viewport 结果。旧的 local downward collision solver 与其测试已删除，生产 `WpSpatialStartLayout` 直接消费 packer 结果并动画邻居 reflow。Proto 外层 schema 升到 2，保留的文档只写 rank/group 和显式 Spacer，不再写 column/row。

第一次窄 Gate 让 core/storage 旧测试因 `GridCell` 构造失败，相关测试被改为验证 rank 与 packed cell 的边界，而不是通过兼容字段把绝对坐标偷偷带回 durable model。自审又补上 Spacer 参与 insertion index、Spacer/tile ID 冲突验证和 schema 升版。最终 core engine、storage、desktop 与 app compile 共 197 tasks 全绿；Stage 0–11 历史合同仅在被本轮规范明确覆盖的空间模型断言上更新。

### English translation — adaptive packing

The Red fails on absent ranked document, adaptive packer, and explicit spacer types. Green removes durable cells and the downward-only collision solver, derives cells per viewport, persists rank/size/group at schema 2, and connects the production renderer to deterministic reflow. Seeded mixed-size properties, four/six-column repacking, insertion, explicit whitespace, and Proto round-trip are machine-tested. High-frequency pointer dispatch and cancelable resize remain the next direct-editing slice.

## Marine Shell Final Correction — Desktop Direct Editing

### Red

Reducer 测试先引用 `InsertionTargetChanged` 并因该语义 Action 不存在而编译失败；同时加入 unchanged target 不产生新 state 和 Resize 可取消测试。Activity stories 将原先的一帧自动 Resize 改为显式预览/取消/确认，并新增小磁贴实际 hit bounds 至少 44dp。

### Green and correction

`LocalTileDrag` 在 Compose Renderer 本地持有逐帧 visual offset、grab offset、edge auto-scroll velocity 与 hysteresis target。只有 insertion index 改变时才派发 Engine Action，Reducer 只计算 proposed rank document；相同 insertion target 原样返回 state。Engine 的 `Channel.UNLIMITED` 改为 256 项有界顺序队列加 conflated wake signal，不丢语义顺序，也不再允许 pointer delta 制造无限 backlog。

Resize 不再通过 `withFrameNanos` 自动提交。第一次点击建立 provisional transaction，桌面显示 48dp 的取消/确认触控面；取消恢复 before，确认才持久化。拖拽时显示 insertion marker，邻居按 proposed document reflow。第一次 API 34 story 在用初始像素高度判断取消恢复时超时；分层断言证明 Engine 已恢复 `WIDE_4X2`，测试遂改为比较 large preview 前后高度，避免把测试启动时的异步视觉采样误当模型事实。随后 Resize story 与 44dp hit-target story 均通过。

### English translation — direct editing

Pointer-frame state now stays local to Compose, while the serialized Engine receives only begin, insertion-target, drop, cancel, and resize semantics. Repeated insertion indices are no-ops, the action queue is bounded, resize requires explicit confirmation and can be cancelled, and 48dp invisible hit areas preserve compact WP-like visual disks. Two API 34 stories verify the real transaction and small-tile touch bounds.

## Marine Shell Final Correction — Typographic Settings

### Red

最终修正合同先锁定 Settings 不得使用逐行 accent bullet、大色卡或 `.wpTilt(...)`，并要求紧凑 `CompactAccentSwatch`。在旧实现中该检查有意失败：普通设置行、命令和色卡都还套用了 3D 倾斜，accent 选择器也是撑满整行的大色块。

### Green and device story

总览收紧为黑/白底上的大标题文字列表，仅保留稀疏副文字和最小化 chevron；普通选择行与命令只使用平面入场，不再逐行倾斜。Accent 区使用 4×N 的 30dp 方形色块，外层保持 48dp 触控面，选中态只用细边框与对勾。

API 34 真实 Activity story 验证了排版型总览存在、无 accent bullet、首行四色块对齐、每个触控面为 44–48dp，且只有一个 accent 具有 selected 语义。最终修正静态合同 `11/11` 通过。

### English translation — typographic Settings

The Red rejects per-row accent bullets, oversized color cards, and 3D tilt on ordinary Settings rows. Green restores a sparse black/white typographic overview, limits accent to actionable controls, and renders a four-column grid of 30dp square swatches inside 48dp semantic touch targets. An API 34 Activity story verifies four-column alignment, 44–48dp hit bounds, the absence of decorative bullets, and exactly one selected accent.

## Marine Shell Final Correction — Motion, Search 与虚拟实体键

### Red

先把动效测试改成只接受 Engine 的精确过渡类型、分解后的离场/进场/稳定阶段、证据等级和 reduced-motion 计划；旧的 `WpNavigationIntent` 深浅层抽象因无法表达 Desktop、Module List、Search、Module route、Recents 的不同语义而编译失败。Reducer Red 同时要求同一 Module 内 forward/back route 有独立过渡，并要求 Search 结果的重复 dispatch 不再启动第二次 Launch transaction。Android Red 要求 Engine 实际发出的三种触感映射不同，Search 聚焦后仍可操作虚拟 Back/Bridge。

### Green 与自审纠错

Renderer 现在直接消费 `ShellTransitionRequest.kind`，不再通过 legacy deeper/sibling intent 二次猜测。Stage 2.5 的 `700/1000/750ms` 只命名为录屏中的可见区间，并分解为 content exit、target delay、target entrance 与 settle；没有录屏证据的 Search、内部 route、Recents 和触感明确标为 `DERIVED_UNVERIFIED`。Desktop 与 Module List 的横向移动继续由 Foundation Pager 独占，外层 transition 对 Pager 返回 `NONE`，避免叠加两套位移。Search result 原子进入 Module，同 token 的队列重复动作原样返回 state，不产生第二个 Launch effect。Engine haptic 通过 Android 边界分别映射 selection、long-press 和 drop；虚拟按键为了即时反馈只在控件本地发送一次 platform virtual-key 触感，不进入 Engine 队列。

第一次完整 API 34 story 回归暴露两类有效问题。1×1 Tile 上两个 48dp 对角控件实际重叠，Unpin 点击会命中后绘制的 Resize；compact 控件收紧到 44dp 后，Unpin、Resize 与重建保持测试全部通过。虚拟键像素故事仍按屏幕底部固定 54dp 采样，与新 bottom safe inset 冲突；测试改为逐个读取 Back/Bridge/Search 的真实语义边界后，单例故事和完整 `29/29` Activity stories 均通过。修正没有加入固定 sleep，也没有把模拟器结果冒充真机手感批准。

### English translation — Motion, Search, and virtual hardware keys

The Red removes the lossy depth-only navigation intent and requires exact Engine transition kinds, decomposed timing phases, evidence labels, reduced-motion behavior, distinct internal-route transitions, duplicate Search-launch suppression, Android haptic mapping, and keyboard-safe virtual controls. Green makes the exact transition request the renderer's sole input. Approved Stage 2.5 values remain visible recording windows rather than input latency or universal constants; unobserved product motion and haptics are explicitly `DERIVED_UNVERIFIED`. Foundation Pager exclusively owns Desktop/Module List movement, while Search enters a Module atomically without an intermediate surface or duplicate launch effect.

Full API 34 review then found overlapping 48dp edit controls on a 1×1 tile and a pixel test that ignored the newly modeled bottom safe inset. Compact controls now use the allowed 44dp bound, and the glyph story samples each real semantic key bound. The focused Search stories, affected edit stories, and the complete `29/29` Activity suite pass without fixed sleeps. Physical-device feel and subjective WP fidelity remain pending.

## Chart C10 — NoSource 与只读观测质量

Red 测试先要求 source epoch、观测 identity、boot-scoped monotonic clock、独立 Position/Heading/COG-SOG/accuracy、断流历史样式和显式 Follow；旧简单位置模型无法编译。Green 建立只读 port，并让生产装配固定为 `NoSourcePositionPort`，因此没有 GPS 权限、Fake/Replay、NMEA writer、自动舵输出或空闲 freshness timer。

自审增加同坐标新样本、缓存重复、source 切换、UTC 回拨、不同 boot、历史快照、低速 COG、未知精度、MapLibre 五种 feature plane 与生产无源 UI 测试。累计 Gate 还捕获并修正两条过时的合法 COG 字面禁令和一个隔离 adapter 的旧字段引用。冻结点 `260a343940c49bfa1564de544dea5881b9d8bf3a` 本地通过 206 Python、357 JVM、17 MapLibre、52 Shell 和 7 Room tests；托管同 SHA 证据仍留给 C12。

### English translation — Chart C10

The Red required epoch-aware identities, a boot-scoped monotonic clock, independent position/heading/COG-SOG/accuracy quality, historical disconnect rendering, and explicit Follow intent. Green adds a read-only port while production remains NoSource with no collection permission, fake provider, vessel output, or idle timer. Adversarial tests cover dedupe, source changes, clock changes, rendering planes, camera ownership, and historical persistence boundaries.

## NMEA Sources P2 — 真实输入运行时与独立 Feature

### Red

P2 先写 36 个行为测试与 9 项静态合同，分别锁定 pure runtime、真实 localhost TCP/UDP、持久化、前台服务边界、NMEA Input 投影／协调器及“离开 workspace 不停止 runtime”。缺少生产合同时分别在 core、adapter 和 Feature 编译阶段按预期失败；没有用 fake transport 冒充 socket 证据。

### Green 与设备 Gate

进程持有的串行 runtime 统一管理多连接、session generation、重连／取消、有界诊断和前台服务；Feature 只消费 core port，不持有 socket。最小 P2 Gate 为 static `9/9`、core `100/100`、adapter `15/15`、Feature `16/16`。API 34 AVD 上 adapter merged-service `1/1`、Feature Compose `3/3`、app-shell 真实 loopback/lifecycle `2/2` 通过。过程中的唯一设备编译修正是将 `ServiceInfo` 改为正确的 `android.content.pm` 类型；没有放宽断言。锁屏、Doze、OEM 后台和真机 GNSS 仍记为 `UNVERIFIED_PHYSICAL_DEVICE`，留给 P6。

### English translation — NMEA Sources P2

P2 begins with 36 behavioral Reds and nine static contracts across the pure runtime, real localhost TCP/UDP, persistence, foreground-service boundaries, Feature projection/coordinator behavior, and workspace-independent ownership. Green provides one process-owned serialized runtime and a Feature that only consumes the core port. The scoped gate passes static 9/9, core 100/100, adapter 15/15, Feature 16/16, plus API 34 device stories 1/1, 3/3, and app-shell real-loopback lifecycle 2/2. Physical background and GNSS behavior remains explicitly unverified for P6.

## NMEA Sources P3 — OS 统一选源与手机系统定位

### Red

P3 先写 reducer、runtime、catalog projector、selection persistence 和 phone runtime 测试。静态合同首跑 `2 failures / 3 errors / 1 pass`；core 因全部 P3 类型缺失而在 `compileTestKotlin` 失败。行为合同要求 3 秒窗口、首次多源不猜选、新源不抢占、断流不 failover、持久化失败不发布假成功、重启不复活 live，以及手机可选字段不补0。

### Green 与纠错

一个 `MarineSourceSnapshot` 同时携带 catalog、decision、resolved value 和 selection revision；选择先落盘再原子发布。真实 Android `LocationManager` 仅在用户意图、权限、系统开关和前台服务都允许时启动，只投影平台存在的字段。首轮 Green 暴露 exhaustive key 缺口；设备 Red 又暴露 target 26 兼容语义的隐式后台定位，测试包对齐 target 36 后确认不申请 background location。最终 P3 static `6/6`、core `115/115`、adapter `21/21`、API 34 merged-manifest `2/2` 和 app composition compile 通过。

### English translation — NMEA Sources P3

The P3 Reds define the single source-policy reducer/runtime, atomic persistence, catalog projection, and real phone-location boundary. Green publishes catalog, decisions, resolved values, and selection revision together; keeps explicit source choice across stale/missing states without failover; and starts Android system location only after explicit intent and platform gates. Follow-up Reds exposed an exhaustive-key gap and target-26 implicit permission compatibility, both corrected before the scoped gate passed.

## NMEA Sources P4 — 数据来源应用完整闭环

### Red

独立 Data Sources 模块先以 7 项静态合同、核心受控深链、projector/coordinator JVM 合同和 3 条 Compose story 进入 Red。静态首跑为 `4 errors / 3 pass`，Feature 因全部生产类型缺失而编译失败。合同锁定同一真实 catalog、风／水深-only、未选候选可见、未知合法句型、实际选择事务、手机授权独立失败和总览 Back 交回 Shell。

### Green 与自审

Green 提供 WP8 排版型数据／句型视图、搜索筛选、候选详情、选择／停用、20 条 raw 证据上限和不透明双向深链。Feature 只读 runtime StateFlow 并调用类型化端口，不持有 socket 或第二份偏好。首轮最小 Gate 抓到超长 token 会在解析前抛异常及设备测试缺显式主题，两者分别修正且未弱化断言。

自审 Red 再覆盖 phone-only、单一已选来源、运行时新增句型、raw 上限、永久拒绝和系统位置关闭；它精确因恢复 action/effect 不存在而编译失败。Green 增加权限请求、应用权限设置与系统位置设置三个明确出口，同时保持 NMEA 不受影响。最终 P4 static `8/8`、core `117/117`、Data Sources `16/16`、NMEA Input `17/17`、Shell composition compile 与 API 34 Compose `3/3` 通过；整仓门禁按约定留 P7，物理权限/GNSS/后台仍未验证。

### English translation — NMEA Sources P4

P4 begins with independent static, core-link, projector/coordinator, and Compose Reds. Green renders the one real runtime catalog as searchable data and sentence views, preserves every candidate, commits source choices through the OS port, bounds raw evidence, and uses opaque links. Self-review Reds add phone-only, runtime sentence discovery, bounded evidence, and recoverable permanent-denial/system-location states. The scoped gate passes; production installation remains P5, the full repository gate remains P7, and physical-device behavior is not claimed.

## NMEA Sources P5 — Shell 安装、磁贴与状态条

### Red

P5 先以静态合同、两组纯 projector 测试和生产安装测试进入 Red，锁定四个生产应用、默认 Start 仍只有 Chart + Settings、两个新应用各自拥有三种尺寸、状态条两个独立入口，以及磁贴不得执行 runtime 命令。首跑 static 为 `2 failures / 2 errors / 3 pass`，Feature 测试因生产合同缺失而编译失败。

### Green 与自审

Green 从同一 `InstalledAppBinding` 派生目录、动态 token、视觉与内部宿主；页面、磁贴和状态条直接投影进程 runtime snapshot。首轮编译抓到两个作用域错误并用小型提交修正。API 34 story 随后验证 All Apps、根 Back、固定和 Medium → Wide → Small 尺寸循环。

自审 Red 又捕获混合连接下“等待被接收掩盖”的状态错误；修正后异常 > 等待 > 接收 > 停止／未配置的产品语义成立，并补了全部停止回归。最终 static `8/8`，NMEA P5 projector `7/7`，Data Sources Feature `20/20`，Shell unit `7/7`，composition compile 与 API 34 P5 stories `3/3` 通过。完整质量门禁按用户要求留到 P7，真机后台／网络／GNSS 仍未验证。

### English translation — NMEA Sources P5

P5 begins with failing installation, projection, status, and three-size contracts. Green installs both apps from one binding each, keeps the existing Start document unchanged, and projects the same process-owned runtime truth into pages, tiles, and two independent status entries. A self-review Red exposed mixed receiving/waiting truth and was corrected before the scoped static, JVM, composition, and API 34 visual/navigation gates passed. Full repository gates remain deferred to P7; physical-device behavior is not claimed.

## NMEA Sources P6 — 跨应用、生命周期与故障边界

初始 Red 缺少跨应用 adapter、耐久 sender 与阶段证据。Green 从 composition root 把唯一 OS 采用结果接入 Chart 既有只读 port；自审 Red 随后证明首版只投影 Position，Heading 与 COG/SOG 没有抵达消费者。修正严格要求同源同 frame group 才组合 accuracy、time、variation 与 course/speed，不同帧不拼成虚假向量，所选位置不可用时断开且不切备用源。

第二轮 Red 覆盖前台服务单独丢失、语义／wire 配置损坏和独立进程恢复。runtime 现在由同一 actor 在服务丢失时释放 socket/retry，保留用户 `ENABLED` 意图并要求明确恢复；损坏 entry 被隔离，wire 失败不清空原文件。API 34 探针先经真实 TCP/RMC 与正式 `Select` 建立 live，再由宿主 force-stop；新进程只恢复配置/策略，live/counters/catalog 不复活。第一次探针依赖自动采用时间并超时，修正为正式选择事务而非加 sleep 后 2/2 通过。

本阶段定向证据为跨应用 5/5、选源 12/12、lifecycle/corruption/permission 15/15、外部进程 2/2，以及四连接 100 句/秒的 3 秒 sender smoke。冻结 sender 默认 30 分钟，但完整应用级 30 分钟资源曲线没有执行，明确记 `NOT_RUN`；锁屏、Doze、OEM、真实网络/GNSS、功耗与三星方屏为 `UNVERIFIED_PHYSICAL_DEVICE`。全仓 test/lint/release 只在 P7 执行。

### English translation — NMEA Sources P6

P6 connects the single selected marine-data truth to Chart's existing read-only port, then uses follow-up Reds to prevent partial motion projection, cross-frame assembly, foreground-service orphan sockets, destructive corruption recovery, and live-state resurrection across an external force-stop. Scoped JVM and API 34 process evidence passes. The reusable sender defaults to the required 30-minute/four-connection/100-sentence profile; only its short smoke was run, so the full app soak and all physical-device background, GNSS, radio, power, and square-screen claims remain explicitly unverified. Full repository gates remain P7-only.

## NMEA Sources P7 — 终局交付门禁

### Red

P7 合同先要求当前四应用产品面、两个正式 runtime/FGS、P0–P7 独立 CI 结果、Release 发布前二进制审计、依赖方向、三份双语资源 parity、生产日志隐私和 E01–E26 证据账本。旧 Gate 仍把 Stage 1 的 Chart + Settings 历史基线当作当前产品事实，因此 Red 继续要求所有可执行 Gate 与新 Phase 一致，而不是静默改写旧批准报告。

后续自审分别加入三类能真实失败的合同：删除已采用连接前必须解释不会自动换源；手机定位权限必须由 marine-data 边界而非 Chart 持有；所有残留“生产精确两应用”的静态 Gate 必须被拒绝。

### Green 与待封口

Android CI、Release workflow、最终 Gate 和 Release APK 审计现在都认识 Chart、Settings、NMEA Input、Data Sources 四个生产 App，同时坚持默认 Start 只有 Chart + Settings、不含 Android HOME/DEFAULT、只允许竖屏且 Release 不含 debug/demo/test sender。删除确认、位置权限所有权和旧可执行合同也已纠正。

遵照用户的测试成本约束，施工中的每个小提交只运行所属静态／模块 Gate；不会反复执行 `test + lint + assemble + device`。P7 报告和 lock 先保持 `PENDING_MACHINE_GATE`，唯一完整命令 `run_marine_shell_final_gate.sh --with-device` 实际退出 0 后，才会写入最终计数并升级为 `MACHINE_VERIFIED`。30 分钟应用 soak 与全部真机项不会被终局机器 Gate 冒充。

### English translation — NMEA Sources P7

The P7 Red rejects stale two-app executable assumptions and requires the current four-app release surface, all P0–P7 CI results, dependency and privacy boundaries, bilingual parity, and a complete E01–E26 evidence ledger. Follow-up Reds cover truthful selected-source deletion and correct phone-location ownership. Construction uses scoped tests only; the full test/lint/build/device gate runs once at final sealing, and the report cannot claim `MACHINE_VERIFIED` before its real exit code is zero.
