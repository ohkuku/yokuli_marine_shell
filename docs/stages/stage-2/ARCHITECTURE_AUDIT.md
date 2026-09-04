# Stage 2 架构边界审计

状态：`PENDING_HUMAN_REVIEW`。本审计从 annotated tag `launcher-engine-stage1-approved-v1` 指向的 `df371fbfcb4cd467bccc43dd850e23d9bd7d0e85` 开始，只覆盖 Engine Contract Extraction。

## 模块与依赖方向

| 模块 | 本 Stage 职责 | 允许依赖 | 禁止内容 |
| --- | --- | --- | --- |
| `:core:shell-contract` | opaque ID、catalog snapshot/contribution、tile snapshot、`LauncherHostPort` | Kotlin、Coroutines Flow | Android、Compose、Feature、资源 ID、海事模型 |
| `:core:shell-engine` | catalog 组合与校验、匿名 token 导航；承载但不扩展冻结的候选 layout/geometry/interaction | `:core:shell-contract` | Android、Compose、Feature、Google Maps、`core:model`、海事术语 |
| `:ui:shell-compose` | `InternalAppHost` 和 `InternalAppHostResolver` 渲染边界 | contract、Compose | Feature 依赖和 Engine 内部实现 |
| `:adapter:shell-android` | 静态 host port 与内部 Compose host resolver | contract、shell-compose | Chart/Settings 类、海事领域解释 |
| `:feature:chart` / `:feature:settings` | 提供自身 catalog contribution 与 UI | contract、各自 UI 依赖 | shell-engine 内部实现 |
| `:app-shell` | 组合两个 contribution、token→app 映射和两个内部 UI host | 上述公开边界与 Features | 在 Engine 内硬编码 Feature |

依赖方向由 `.github/scripts/test_launcher_stage2_contract.py` 独立检查。`core:shell-engine` 已改为 Kotlin/JVM module，不再具有 Android manifest，也不再依赖 `core:model`。

## Opaque 合同

```text
MarineAppId       -> LauncherAppId
DestinationId     -> LaunchToken
LaunchTarget      -> LaunchToken
TileId            -> TileInstanceId
fixed Registry    -> LauncherCatalogContribution + LauncherCatalog.compose
feature switch    -> InternalAppHostResolver.hostFor(appId)
```

Engine 只解析 `LaunchToken` 得到 `LaunchResolution`，不知道 `Chart`、`Settings`、地图、NMEA、GPS 或 Android destination。生产 catalog 仍严格由 `ChartShellContribution` 与 `SettingsShellContribution` 组合；默认 Start 仍严格只有 Chart Wide 和 Settings Small。

## 现有交互保持

Stage 2 只替换跨层合同和接线。现有 Start、All Apps、Chart、Settings、Back、主题、语言、地图适配器、调试 Shell Lab 以及当前动画/手势代码保持原有用户行为。Chart Browse token 与六个 Settings token 经 `StaticLauncherHostPort` 解析，再由 `DefaultInternalAppHostResolver` 找到内部 Compose host；`ShellActivity` 不再以 `when (task.appId)` 选择 Feature。

冻结基线中已经提前存在的 `WpStartGeometryCalculator`、`DesktopDocument`、`DesktopLayoutEditor` 和 `StartInteractionState` 仅做类型/包迁移以通过新边界，不能作为 Stage 3/5/7 的批准证据。

冻结基线中未接入任何调用方的 `ShellStore`、`DesktopLayoutStore`、`ShellPreferencesStore` 临时接口已清除。它们既不是本 Stage 的 `LauncherHostPort`，也没有实现持久化；真正的响应式 Store、reducer 与恢复必须等各自后续 Stage，不能用旧空接口冒充完成。

## 明确未实现

```text
Stage 2.5 WP8 Reference acquisition/review: NOT STARTED
Stage 3 reference-derived geometry/document: NOT STARTED
pure LauncherReducer / controller / persistence: NOT STARTED
unified pager / gesture arena / direct-manipulation drag: NOT STARTED
live tile runtime, recovery, benchmark, physical square-device verification: NOT STARTED
GPS/NMEA/Anchor/Trip/Navigation/Survey or other marine runtime: NOT STARTED
```

## English translation

Status is `PENDING_HUMAN_REVIEW`. Stage 2 starts exactly from the approved Stage 1 tag and extracts an app-agnostic boundary: a pure Kotlin contract module, a pure Kotlin engine module, a Compose host boundary, and an Android host adapter. Opaque IDs and tokens replace marine-specific routing types; Chart and Settings contribute the release catalog and are connected only in `app-shell`. The engine imports no Android, Compose, Feature, Google Maps, `core:model`, or marine-domain classes. Existing UI and gestures are preserved. Candidate geometry/layout/interaction code is only type-migrated and is not evidence for later stages. Stage 2.5 and all subsequent work remain unstarted.
