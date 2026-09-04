# Yokuli Launcher Engine TDD Log

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
