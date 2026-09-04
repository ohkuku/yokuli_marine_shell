# Stage 0 基线实现对账

状态：`PENDING_HUMAN_REVIEW`。本文件只审计 `ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7` 已经存在的候选实现；不修改、批准或继承其功能完成度。

判定词只有四个：

- `ACCEPTED_CANDIDATE`：数据或边界方向可在对应 Stage 重新以 Red 开始审计，不能直接宣告 Gate 通过。
- `PROVISIONAL`：仅为探索性骨架，必须用该 Stage 的规范与测试重新证明并重构。
- `NON_COMPLIANT_REPLACE`：已知违反 Master 的目标边界，进入对应 Stage 时必须替换或拆分。
- `DEFERRED`：当前不进入施工或验收，保留原状直到拥有它的 Stage。

任何下表内容都不得视为任何后续 Stage Gate 已通过；每个未来 Stage 仍必须从独立 Red、最小 Green、完整 Gate 和人工审核开始。

## `core/shell-engine` 全量文件对账

| 现有 artifact | 对应未来 Stage | 判定 | 已知差距与处置 |
| --- | ---: | --- | --- |
| `core/shell-engine/build.gradle.kts` | Stage 2 | `NON_COMPLIANT_REPLACE` | 当前使用 Android Library 与 Kotlin Android 插件，并依赖 `core:model`；最终 engine 必须是无 Android、Compose、Feature 与 Marine Domain import 的纯 Kotlin/JVM 边界。 |
| `core/shell-engine/src/main/AndroidManifest.xml` | Stage 2 | `NON_COMPLIANT_REPLACE` | 空 Manifest 仍证明这是 Android Library；完成 contract extraction 后纯 engine 不应需要 Android manifest。 |
| `core/shell-engine/src/main/java/com/yokuli/marine/core/shell/engine/geometry/WpStartGeometry.kt` | Stage 2.5、Stage 3 | `PROVISIONAL` | `OuterRatio = 0.05f` 与 `SeamRatio = 0.023f` 是未经 Reference Lab 测量的猜测；必须在 `HUMAN_REVIEWED` 证据后由 `WpReferenceProfile` 驱动，当前数值不是规范。 |
| `core/shell-engine/src/main/java/com/yokuli/marine/core/shell/engine/interaction/StartInteractionState.kt` | Stage 2、Stage 4 | `NON_COMPLIANT_REPLACE` | Engine 直接引用应用模型 `LaunchTarget`、`TileId` 与 `TileSize`；必须迁移到 opaque `LaunchToken` 和 app-agnostic contract，并由 reducer 拥有确定性状态。 |
| `core/shell-engine/src/main/java/com/yokuli/marine/core/shell/engine/layout/DesktopDocument.kt` | Stage 3、Stage 4 | `ACCEPTED_CANDIDATE` | 显式 cell/placement/document 的数据方向可审计复用；命名、版本迁移、token 类型和 transaction 身份仍须按 Master 重做测试，不能直接算二维文档 Gate 通过。 |
| `core/shell-engine/src/main/java/com/yokuli/marine/core/shell/engine/layout/DesktopDocumentPolicy.kt` | Stage 3、Stage 10 | `PROVISIONAL` | 当前 validator/repair 是候选算法，但依赖 app descriptor、固定四列且缺少正式 migration/corruption policy；必须用 approved profile 与确定性 recovery fixtures 重新证明。 |
| `core/shell-engine/src/main/java/com/yokuli/marine/core/shell/engine/layout/DesktopLayoutEditor.kt` | Stage 4、Stage 6、Stage 7、Stage 8 | `NON_COMPLIANT_REPLACE` | `UUID.randomUUID()` 使 reducer 输出不可复现；move/resize/pin 只是离散修复，没有规范的 transaction identity、cancel/undo、continuous direct manipulation、live reflow 与 auto-scroll。 |
| `core/shell-engine/src/main/java/com/yokuli/marine/core/shell/engine/persistence/ShellStores.kt` | Stage 2、Stage 4、Stage 10 | `NON_COMPLIANT_REPLACE` | `ShellStore`、`DesktopLayoutStore`、`ShellPreferencesStore` 只是临时接口；直接引用 `MarineAppId`、`AppLanguage`、coroutines `StateFlow` 和 provisional interaction state，不满足 app-agnostic persistence/effect ports 或 durable restore Gate。 |
| `core/shell-engine/src/test/java/com/yokuli/marine/core/shell/engine/DesktopDocumentTest.kt` | Stage 2、Stage 3、Stage 4 | `PROVISIONAL` | 测试覆盖候选 repair/editor，但 fixtures 仍构造 `MarineAppId`/`LaunchTarget`，且没有 reducer determinism、cancel/undo、migration 或 approved reference profile 证据；未来 Stage 必须先写新的 Red。 |
| `core/shell-engine/src/test/java/com/yokuli/marine/core/shell/engine/WpStartGeometryTest.kt` | Stage 2.5、Stage 3 | `PROVISIONAL` | 当前只证明猜测公式内部自洽，不证明 WP8 fidelity；必须由人工批准的 capture 与 measurement sets 派生期望值。 |

## 相邻实现对账

这些文件不属于 `core/shell-engine` 目录，但会影响之后拆分，因而也不能被漏算为“已经完成”：

| 现有 artifact | 对应未来 Stage | 判定 | 已知差距与处置 |
| --- | ---: | --- | --- |
| `core/model/src/main/java/com/yokuli/marine/core/model/ShellModels.kt` | Stage 2 | `NON_COMPLIANT_REPLACE` | `MarineAppId`、`LaunchTarget`、Marine descriptor 与 shell contract 混合；改为 opaque launcher identifiers/tokens，并把 Android app host resolution 留给 adapter。 |
| `core/shell/src/main/java/com/yokuli/marine/core/shell/LauncherRegistry.kt` | Stage 1、Stage 2 | `PROVISIONAL` | contribution/catalog 方向可审计，但 Registry 仍绑定 Marine 模型；Stage 1 先重跑产品面 Gate，Stage 2 再抽出 app-agnostic catalog。 |
| `core/shell/src/main/java/com/yokuli/marine/core/shell/ShellNavigator.kt` | Stage 2、Stage 4、Stage 9 | `NON_COMPLIANT_REPLACE` | 当前 navigator 直接消费 `LaunchTarget`，任务 id 由 app id 推导，且不具备 Master 的 reducer/effect、Back/Home priority 与恢复语义。 |
| `feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt` | Stage 5、Stage 6、Stage 7、Stage 8 | `DEFERRED` | 当前 Compose 本地 `remember`、drag offset 与提交时 repair 只是旧 renderer 行为；不作为 gesture arbitration、continuous drag、live reflow、resize/unpin fidelity 的验收证据。 |
| `feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt` | Stage 2、Stage 4、Stage 5 | `PROVISIONAL` | UI contract 仍暴露 `LaunchTarget`；未来必须只渲染 engine render model 并发送 app-agnostic action。 |
| `app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt` | Stage 4、Stage 9、Stage 10 | `DEFERRED` | Activity 当前拥有 `remember` 状态与简化横向 drag；之后只允许组合 controller、platform effect 与 host resolver，当前行为不证明 lifecycle/Home/recovery Gate。 |
| `feature/shell-lab` module | Stage 1、Stage 5 以后 | `ACCEPTED_CANDIDATE` | debug-only 边界可保留为场景载体，但每个 scenario 必须在所属 Stage 才增加；不得进入 release 或冒充 Reference/硬件证据。 |

## 明确不继承的完成结论

- 当前 `core:shell-engine` 是 Android Library，不满足 Stage 2 纯 Kotlin/JVM Gate。
- `MarineAppId` 和 `LaunchTarget` 耦合不满足 app-agnostic engine contract。
- `OuterRatio`、`SeamRatio` 等猜测比例不满足 Stage 2.5 Reference Gate，也不能成为 Stage 3 geometry truth。
- `UUID.randomUUID()` 不满足 deterministic reducer/transaction 身份要求。
- 当前 `ShellStore` 等 provisional stores 既不是最终 ports，也没有持久化、migration、repair 或 crash recovery 的完成证据。
- 当前拖拽/reflow 只会在释放后调用旧 editor；不满足连续 direct manipulation、collision preview、cancel、auto-scroll 或 live reflow。

本纠偏提交保留以上生产文件原样。对应 Stage 到来时只能“审计后迁移”或“删除后替换”，不能因为类名已经存在而跳过 Red 或 Gate。

## English translation

Status is `PENDING_HUMAN_REVIEW`. This reconciliation inventories every file in the pre-existing `core/shell-engine` module and the adjacent code that can otherwise be mistaken for completed future work. An accepted candidate is only eligible for a fresh audit; provisional code requires new evidence; non-compliant code must be replaced or split; deferred UI behavior remains untouched until its owning stage. The Android-library engine, Marine identifiers and launch targets, guessed geometry ratios, random UUID transactions, provisional stores, and release-time drag repair explicitly fail later Master gates. No listed artifact pre-approves any future stage.
