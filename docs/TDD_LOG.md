# Yokuli OS TDD Log

文档规则：新切片使用中文主文＋英文翻译；早期英文证据保持原样，避免改写历史命令和失败输出。
> English: New slices are Chinese-first with English translation. Earlier evidence stays unchanged so historical commands and failures remain exact.

## Slice 1 — Simplified launcher architecture

### Red

Command:

```text
./gradlew :core:shell:testDebugUnitTest
```

Observed failures before implementation:

```text
defaultLayoutContainsFiveTiles FAILED
defaultLayoutRejectsOverlap FAILED
shortcutsOpenTheirOwningWorkspace FAILED
coreAppRegistryContainsExactlyFourApps FAILED
```

`launcherEntryIdsAreUnique` already passed against the empty registry. The failures were assertion-level behavior failures, not compilation or environment failures.

### Green target

- Exactly four core apps: Chart, Cockpit, Library, System.
- Exactly five default pinned entries: Chart, Anchor, Cockpit, Library, System.
- Anchor, Navigation and Survey target typed Chart modes.
- Trips target Library; Data Sources target System.
- Invalid overlapping layouts are rejected.

Green result: `BUILD SUCCESSFUL` with all five architecture tests passing.

## Slice 2 — WP8 Shell navigation

### Red

Command:

```text
./gradlew :core:shell:testDebugUnitTest --tests '*ShellNavigatorTest'
```

All four intended behaviors failed before implementation:

```text
anchorShortcutOpensChartTaskInAnchorMode FAILED
chartModesReuseOneChartUiTask FAILED
homeReturnsStartWithoutClosingUiTasks FAILED
allAppsAndBackFollowWindowsPhoneSemantics FAILED
```

### Green target

- Deep links open their owning core app task.
- Chart modes reuse one Chart UI task.
- Home returns to Start without deleting UI tasks.
- Back from All Apps returns to Start.

Green result: `BUILD SUCCESSFUL` with all four navigation tests passing.

## Slice 3 — Editable four-column Start grid

### Red

Command:

```text
./gradlew :core:shell:testDebugUnitTest --tests '*DesktopLayoutEditorTest'
```

All four behavior tests failed against the inert editor seam:

```text
resizeCyclesOnlyThroughSizesSupportedByTheEntryAndReflows FAILED
unpinRemovesOnlyTheSelectedTile FAILED
pinAddsAnAllAppsShortcutAtTheNextFreeGridPosition FAILED
movingATileBeforeAnotherReflowsWithoutOverlap FAILED
```

### Green

- Resize follows each entry's supported-size cycle.
- Unpin removes only the selected tile.
- All Apps can pin a shortcut at the next valid grid position.
- Move, resize, pin and unpin always repack without overlap.

Green result: `BUILD SUCCESSFUL`; all editor and existing shell unit tests pass.

## Slice 4 — Real ShellActivity stories and square layout

### Red

The first instrumented run failed all three stories because the UI had no stable test semantics:

```text
anchorTileOpensSharedChartInAnchorModeAndHomeReturnsToStart FAILED
allAppsEntryOpensAlphabeticalCoreAppsAndShortcuts FAILED
square320StartScreenKeepsChartVisibleAndSystemReachable FAILED
```

After those stories were green, the All Apps pinning extension failed at `tile-anchorages`, proving that the long-press path was still inert.

### Green

Command:

```text
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

Four real-activity Compose stories now pass:

- Anchor tile opens `ChartMode.ANCHOR`, and Home returns to Start.
- All Apps contains core apps and shortcuts and can pin Anchorages.
- A constrained 320×320 dp Start Screen keeps Chart visible and System reachable by vertical scroll.
- Edit mode moves, resizes and unpins a tile without opening its app.

The test uses an API 34 emulator. This is `VERIFIED_DEVICE_EMULATOR`, not real Samsung hardware or vessel verification.

## Current verification gate

```text
./gradlew test assembleStandaloneDebug assembleHomeDebug lintStandaloneDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

Real GNSS, NMEA, Anchor runtime, alerts and vessel behavior remain intentionally unconnected in this UI slice.

## Slice 5 — WP8 motion policy and reusable page contract

Requirement source: [`docs/requirements/WP8_UI_SYSTEM_REQUIREMENTS.md`](requirements/WP8_UI_SYSTEM_REQUIREMENTS.md), contracts `UI-001` through `UI-008`.

### Red

Command:

```text
./gradlew :core:design:testDebugUnitTest --tests '*WpMotionPolicyTest'
```

Observed result: five tests compiled and ran; four failed against the inert policy seam and the safety-immediate default passed.

```text
siblingSurfacesUseDirectionalHorizontalSlides FAILED
deeperNavigationUsesPerspectiveTurnstileWithInverseBackMotion FAILED
transientUiUsesSwivel FAILED
reducedMotionPreservesContextWithoutPerspective FAILED
safetyCriticalPresentationIsImmediate PASSED
```

The failures were assertion-level behavior failures, not missing dependencies, compilation errors, or emulator failures.

### Green target

- Start ↔ All Apps uses directional sibling slide.
- App enter/return uses inverse 3D Turnstile plans.
- Transient UI uses Swivel.
- Reduced motion becomes a short non-perspective fade.
- Safety-critical presentation remains immediate.
- Every core app uses the reusable upper-left application title and fixed Application Bar pattern.
- Tiles and actions retain semantic click behavior while adding pointer-position tilt feedback.

### Green result

```text
./gradlew :core:design:testDebugUnitTest --tests '*WpMotionPolicyTest'
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

- All five deterministic motion-policy tests pass.
- All five real `ShellActivity` stories pass on the local API 34 emulator, including the four-core-app title contract.
- Visual inspection confirms the `chart / ANCHOR` hierarchy, chart workspace, and outlined five-action Application Bar after motion settles.
- The tests prove state, navigation, and semantic end conditions; they do not constitute frame-perfect compositor or physical-device verification.

## Slice 6 — GitHub build, test, compatibility, and signed release delivery

Requirement source: `UI-009` and `UI-010`.

### Red

Commands:

```text
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-resolve-release-metadata.sh
```

Observed failures before the production scripts/workflows were added:

```text
CI contract failed: missing .github/workflows/release.yml
resolve_release_metadata.sh: No such file or directory
```

These failures prove that the existing single workflow does not yet provide the required gate topology or release metadata boundary.

### Green target

- Independent unit, lint, and dual-variant build results with an explicit final gate.
- API 34 real-Activity stories plus API 36 compatibility smoke.
- Candidate/`UNVERIFIED` artifacts before gates and a `VERIFIED` debug package only after all gates.
- Nightly API matrix without claiming the absent marine-runtime soak behavior.
- Deterministic semantic version metadata and signed standalone/HOME APK/AAB release assets.
- GitHub job summaries, annotations, reports, and bounded secret-free failure bundles.

### Green result

```text
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
```

Results: three Python helper tests pass; release metadata cases pass; the workflow topology/feedback/signing contract passes; all workflow and Issue Form YAML parses locally.

A temporary two-day local JKS test key was used to exercise the production Gradle signing boundary:

```text
assembleStandaloneRelease assembleHomeRelease bundleStandaloneRelease bundleHomeRelease
```

Both APKs passed `apksigner verify` using APK Signature Scheme v2, and both AABs passed `jarsigner -verify`. This proves the build/signature plumbing with disposable local material; repository-secret availability and GitHub-hosted execution remain pending until the pushed Actions run.

## Current verification gate — WP8 UI and delivery slice

```text
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
```

- JVM/unit: PASS.
- Android lint: PASS.
- standalone/HOME debug builds: PASS.
- API 34 real-Activity stories: 5/5 PASS.
- local disposable-key standalone/HOME APK/AAB signing and signature checks: PASS.
- GitHub-hosted API 34/API 36 jobs: NOT RUN until push.
- Samsung square hardware: `UNVERIFIED_HARDWARE`.
- Vessel/GNSS/NMEA/Anchor runtime: `UNVERIFIED_VESSEL` and deliberately not connected.

## Slice 7 — Remote CI feedback correction

### Red

GitHub Actions run [#1](https://github.com/ohkuku/yokuli_marine_shell/actions/runs/33759444919) correctly refused to publish a verified artifact: the combined CI self-test step had a failed outcome while unit, lint, and both debug builds passed. It published only `UNVERIFIED-*`, build reports, and `FAILURE-build-*` evidence.

The old combined step did not expose which of its three commands had failed. A new workflow-contract assertion was added first:

```text
CI contract failed: build feedback must expose the ci_helpers gate independently
```

### Green target

- CI helper unit tests, release metadata, and workflow topology have independent step IDs.
- Candidate and verified artifacts require all three outcomes.
- Job summary, failure-bundle decision, and final enforcement name every outcome.

### Green result

```text
bash .github/scripts/test-ci-contract.sh
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
```

All three local contracts pass and the updated workflow YAML parses. The correction is not considered remotely green until its pushed Actions run identifies and clears every required gate.

### Remote Red — isolated by run #2

GitHub Actions run [#2](https://github.com/ohkuku/yokuli_marine_shell/actions/runs/33760552231) named the exact failed outcome:

```text
RELEASE_METADATA_RESULT failed
```

Actions predefines `GITHUB_OUTPUT` for every step. The release resolver therefore wrote its correct metadata to that file, while the shell test expected stdout and read an empty string. The failure was in the test harness boundary, not in tag parsing or version-code calculation.

### Green correction

- Stdout-mode cases explicitly set `GITHUB_OUTPUT=`.
- A dedicated case supplies a temporary Actions output file and asserts `tag`, `version_name`, `version_code`, and `channel` there.
- The test is executed locally with an outer non-empty `GITHUB_OUTPUT` to reproduce the hosted-run environment.

At this point the remote status remained red until the corrected commit completed all build and device jobs.

### Remote Green — run #3

Corrected commit `0a023aaf214a9664856898c5deb422422bd9b927` completed [GitHub Actions run #3](https://github.com/ohkuku/yokuli_marine_shell/actions/runs/33761042952) successfully:

```text
TDD contract, unit, lint, and dual APKs                  PASS
WP8 shell stories on API 34                             PASS
Android 16 / API 36 reduced-motion smoke                PASS
Publish fully verified debug APKs                       PASS
```

The run retained build/API 34/API 36 reports, the pre-device-test candidate, and the final `VERIFIED-yokuli-os-debug-0a023aaf...` artifact. No `UNVERIFIED-*` or `FAILURE-*` artifact was emitted by the green run.

## Slice 8 — WP8 Shell theme and tile-detail fidelity

Requirement source: `UI-011` through `UI-013` and [`research/WP8_THEME_TILE_AUDIT.md`](research/WP8_THEME_TILE_AUDIT.md).

### Red — pure theme contract

Command:

```text
./gradlew :core:design:testDebugUnitTest --tests '*WpThemePolicyTest'
```

Five tests compiled and ran against an inert theme seam. Four failed at assertions:

```text
everyAccentResolvesToTheUserSelectedShellColor FAILED
lightAndDarkModesInvertCanvasButKeepTheSelectedAccent FAILED
everyAccentChoosesReadableTileForeground FAILED
startCanvasUsesOneRepeatedSeamToken FAILED
safetyPaletteDoesNotReplaceTheSelectedAccent PASSED
```

This captures the actual pre-change defects: hard-coded cyan ignored the selected accent, no light canvas existed, contrast was not chosen per accent, and the Start outer gutter differed from its tile seam.

> 纠正 / Correction (Slice 10): “按 accent 自动选择对比前景”是把现代通用可读性算法误当成 WP Phone 行为。页面主前景应随黑/白主题反转，但 Phone 8.1 磁贴前景始终为 light（纯白）。后续结论以 Slice 10 为准。

### Green target

- One Shell-owned `WpThemeSpec` drives background, foreground, chrome, accent, and tile foreground. The original contrast-selection target is superseded by Slice 10's fixed Phone tile foreground.
- Every Start tile plane inherits the same accent; safety/freshness color is a subordinate indicator only.
- System → Display changes background and accent across the current Shell.
- The 6 dp Start seam is reused at the canvas edge and in every compound tile calculation.
- Perspective press feedback transforms background and content as one plane.
- Edit, app-list, and alphabet-jump actions expose at least 48 dp touch targets.
- A real-Activity story changes the theme and verifies all five default tiles expose the same selected accent.

### Red — press geometry and Settings deep link

Commands:

```text
./gradlew :core:design:testDebugUnitTest --tests '*WpMotionPolicyTest'
./gradlew :core:shell:testDebugUnitTest --tests '*LauncherArchitectureTest'
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

Observed assertion-level failures:

```text
centeredPressDepressesWithoutTilting FAILED
cornerPressTiltsTowardTheFingerWithinFiveDegrees FAILED
shortcutsOpenTheirOwningWorkspace FAILED (Settings opened System overview)
systemDisplayThemePropagatesOneAccentToEveryDefaultTile FAILED (Display had no theme controls)
```

The Android story compiled and reached System before failing on the absent `theme-accent-magenta` semantic node; this was a product behavior failure, not an emulator or dependency failure.

### Green result

```text
./gradlew :core:design:testDebugUnitTest --tests '*WpMotionPolicyTest'
./gradlew :core:shell:testDebugUnitTest --tests '*LauncherArchitectureTest'
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

- Theme policy and motion policy tests pass, including seven accent selections, light/dark inversion, one repeated seam token, center/corner press geometry, clamping, and exact release-to-rest. The contrast-selection assertion recorded at that time is superseded by Slice 10.
- Settings now deep-links to System → Display; System rows are real 64dp touch targets rather than inert decoration.
- The real API 34 `ShellActivity` suite is 6/6 PASS. Its new story selects magenta and light, returns Home, and verifies that all five default tiles expose the same accent plus the Start canvas exposes the selected mode.
- Visual inspection completed for the dark/cyan Start and System → Display palette. Static screenshots cannot prove animation frames; the geometry/timing policy and interaction end states are the automated evidence.
- Cold-launch persistence, physical Samsung square hardware, and vessel behavior remain outside this slice: `UNVERIFIED_HARDWARE`, `UNVERIFIED_VESSEL`.

### Remote Green — theme and tile refinement

Implementation commit `df69fcbce1b85a268afcd83c3ddc259d612ddd25` completed [GitHub Actions run #33764978254](https://github.com/ohkuku/yokuli_marine_shell/actions/runs/33764978254) successfully:

```text
TDD contract, unit, lint, and dual APKs                  PASS
WP8 shell stories on API 34                             PASS
Android 16 / API 36 reduced-motion smoke                PASS
Publish fully verified debug APKs                       PASS
```

The run published final artifact `VERIFIED-yokuli-os-debug-df69fcb...` (artifact ID `9897363683`) only after both emulator jobs passed. It also retained build/API 34/API 36 reports and the pre-device candidate. No `UNVERIFIED-*` or `FAILURE-*` artifact was emitted.

## Slice 9 — 中文优先、多语言与响应式 UI 边界

需求来源：[`requirements/UI_FUNCTION_I18N_REQUIREMENTS.md`](requirements/UI_FUNCTION_I18N_REQUIREMENTS.md) 与 [`UI_REACTIVE_ARCHITECTURE.md`](UI_REACTIVE_ARCHITECTURE.md)。本切片只建立 UI，不接入海事 runtime。

### Red

先添加 `.github/scripts/test_ui_architecture.py`，再运行：

```text
python3 .github/scripts/test_ui_architecture.py
```

得到 10 个合同失败：7 个 UI 模块没有中文默认/英文资源对；Launcher descriptor 仍包含 `title`/`symbol`；Launcher 缺少 `UiState`/`UiAction` 合同；5 处用户可见 `WpText` 仍硬编码。失败来自缺失产品边界，不是测试环境或编译错误。

### Green

- 给 app-shell、core:design 和五个 feature 建立 key 完全相同的 `values`／`values-en`。
- `AppLanguage` 成为跨模块值；显式选择持久化并与 Android/AndroidX per-app locale 同步，中文是默认 locale。
- 所有 workspace 改为只接收不可变 `UiState` 并发布封闭 `UiAction`；fixture 明确标注不是实时船舶数据。
- Launcher 的展示标题、glyph、拼音索引和磁贴样例进入 UI catalog，`core:model` 只保留 typed launch metadata。
- 导航故事使用稳定 tag；新增真实 Activity 中 English/中文往返切换故事。

### 设备 Red 与安全 Red

API 34 首次运行语言故事时是 6/7 PASS：English 成功，但切回中文超时。把语言标签统一成 `zh-CN` 后，framework 已报告 `[zh-CN]`，界面仍显示英文。`aapt2 dump resources` 证明 APK 只有默认资源和 `(en)`，Gradle 的 `localeFilters = ["zh", "en"]` 错误裁掉了 `(zh-rCN)`。改为精确的 `zh-rCN`，并把该过滤器加入架构合同后，APK 同时含 `()`、`(en)`、`(zh-rCN)`。

视觉验收又复现了覆盖安装边界：framework app-locale 为空，而旧的一次性初始化标记仍存在，系统英文因此接管界面。新的 Red 合同要求持久化 `selected_language_tag`；实现改为保存显式选择、同步非空 platform locale，并在 platform 为空时恢复保存值（首次缺省为中文）。覆盖安装实测从 `[]` 恢复为 `[zh-CN]`。第一次抽取统一入口后，完整设备门禁又发现 API 34 仪器进程经 AppCompatDelegate 没有触发重建；最终边界明确为 Android 13+ 直接调用 `LocaleManager`、Android 12 及以下调用 AndroidX，定向故事和完整 7 条故事随后都通过。

海图样例还会把缺失 COG/SOG/航路数值替换成 `0`，严重诊断只能使用 stale 色。安全 UI 合同先失败，再改为明确“数据不可用 / DATA UNAVAILABLE”，并增加独立 alarm tone；没有把业务 runtime 偷渡进本切片。

### 本地 Green 证据

```text
python3 .github/scripts/test_ui_architecture.py                 PASS (8/8)
python3 -m unittest discover .github/scripts 'test_*.py'        PASS (11/11)
./gradlew testDebugUnitTest lintDebug assembleStandaloneDebug assembleHomeDebug
                                                               PASS
./gradlew :app-shell:compileStandaloneDebugAndroidTestKotlin test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug
                                                               PASS
./gradlew :app-shell:connectedStandaloneDebugAndroidTest        PASS (7/7, API 34)
```

中文首页、中文 System 列表、中文 Display/语言设置和 English 首页已在 1080×2400 API 34 模拟器视觉检查，无文字裁切或布局溢出。静态图不能证明动效帧；动效由现有策略单测和 Activity 终态故事覆盖。此时 API 34/36 hosted stories 仍在等待 push，结果记录在下方远端 Green；真实 Samsung 方屏与实船仍为 `UNVERIFIED_HARDWARE`、`UNVERIFIED_VESSEL`。

### Refactor

参考旧 Boat Watch 的显式语言枚举、选择持久化和只读 Flow 发布方式；没有迁移页面内 `localized()`／`tr()` 双语硬编码。架构确定为函数式核心＋端口化副作用＋Kotlin Flow 发布/订阅；不采用纯面向过程的全局组织，也不增加 RxJava 或无所有者 EventBus。

### English translation

The Red architecture test found ten initial contract gaps: seven missing Chinese/English resource pairs, visual title/glyph leakage into the launcher domain descriptor, a missing launcher state/action contract, and five hardcoded visible text sites. Device Red then exposed a stripped `zh-rCN` resource configuration, and visual update testing exposed drift between an empty framework locale and a stale one-time marker. Green introduced key-complete resources, an exact locale filter, a persisted explicit choice synchronized with platform locales, stateless feature workspaces, UI-owned launcher visuals, stable locale-independent tests, and a real-Activity bilingual story. A separate safety Red prevents missing marine values from rendering as zero and gives critical states an alarm tone.

The legacy app's explicit language choice, persistence, and read-only Flow publication were retained conceptually; manual bilingual literals were not. The long-term architecture is a functional core with port-isolated side effects and Kotlin Flow UDF, not an app-wide procedural call graph, RxJava dependency, or global event bus. Local API 34 stories pass 7/7 and both Chinese and English layouts were visually inspected. Hosted evidence was still pending at this point in the sequence and is recorded in the Remote Green section below; hardware and vessel checks remain unverified.

### 远端 Green — 双语响应式 UI

实现提交 `cf94f94b4df1591bbf49e3cce796992ce9b7d225` 的 [GitHub Actions run #33815853153](https://github.com/ohkuku/yokuli_marine_shell/actions/runs/33815853153) 全部成功：

```text
TDD contract, unit, lint, and dual APKs                  PASS
WP8 shell stories on API 34                             PASS
Android 16 / API 36 reduced-motion smoke                PASS
Publish fully verified debug APKs                       PASS
```

最终测试包是 [`VERIFIED-yokuli-os-debug-cf94f94...`](https://github.com/ohkuku/yokuli_marine_shell/actions/runs/33815853153/artifacts/9916738048)（artifact ID `9916738048`，约 18.1 MB）。该 run 还保留 build、API 34、API 36 报告和设备测试前 candidate；没有生成 `UNVERIFIED-*` 或 `FAILURE-*` artifact。

> English: Commit `cf94f94b4df1591bbf49e3cce796992ce9b7d225` passed all four hosted gates in run #33815853153. The final downloadable artifact is `VERIFIED-yokuli-os-debug-cf94f94...` (ID `9916738048`, about 18.1 MB); build and both device reports plus the pre-device candidate were retained, with no unverified or failure artifact.

## Slice 10 — WP Phone 精确黑白主题与磁贴前景纠正

需求来源：用户对当前视觉结果的复核、`UI-011`，以及 [`research/WP8_THEME_TILE_AUDIT.md`](research/WP8_THEME_TILE_AUDIT.md)。本切片只纠正 UI 主题真值，不实现海事功能，也不以流水线状态代替本地 TDD 与视觉验收。

### Research correction

Microsoft 的归档 Phone 文档明确说明默认主题为白字黑底，用户选择浅色主题后反转为黑字白底；`PhoneForegroundBrush`／`PhoneBackgroundBrush` 是应用应使用的系统资源。Microsoft 的 `SecondaryTileVisualElements.ForegroundText` 文档另行规定：Windows Phone 8.1 忽略该选择，手机 secondary tile 的前景始终为 light。

因此必须区分两层规则：

- 页面 canvas、主文字和 chrome：Dark = `#000000` / `#FFFFFF`，Light = `#FFFFFF` / `#000000`；
- Start accent 磁贴：背景继承所选 accent，glyph 与文字始终为 `#FFFFFF`。

此前使用亮度/WCAG 算法在青色磁贴上自动选择黑字，是错误地把现代通用对比策略套进 WP Phone 视觉语义；不是 WP8 复刻规则。

### Red

先把主题策略测试改成精确颜色合同，再运行：

```text
./gradlew :core:design:testDebugUnitTest --tests '*WpThemePolicyTest'
```

结果为 5 条测试、2 条失败：

```text
darkIsPureBlackWithWhiteTextAndLightIsPureWhiteWithBlackText FAILED
phoneTilesKeepPureWhiteForegroundAcrossThemesAndAccents FAILED
```

失败准确暴露两处实现偏差：canvas 使用近黑/近白值，accent 磁贴按亮度选择了黑色前景。

### Green

- `WpThemePolicy` 改为精确 `Color.Black` / `Color.White` 主题对；
- `onAccent` 固定为 `Color.White`，覆盖 Dark/Light 与全部七种 accent；
- 删除亮度对比选择器，避免后续回归；
- `YokuliColors.White` 与启动 glyph 统一为纯白；
- 需求、研究、范式和 Changelog 同步记录中英文结论。

同一条定向命令随后为 5/5 PASS。完整构建、真实 Activity 回归和深浅色截图验收记录在本切片后续验证结果中。

第一次浅色截图又暴露 Android 宿主窗口在显示挖孔安全区仍保留固定黑带。先增加真实 Activity 合同，要求 Dark/Light 同步 status/navigation bar 颜色、浅色系统图标模式和 short-edge cutout 布局。测试依次产生两个有效 Red：先得到 `expected white but was black`，同步宿主颜色后又得到 `expected SHORT_EDGES but was DEFAULT`。Green 用同一主题背景同步宿主 chrome，并让页面背景覆盖显示挖孔短边；最终浅色截图从屏幕顶边到底部均为白底黑字，accent 磁贴和色板勾选仍为白色前景。

### 最终本地 Green

```text
python3 -m unittest discover .github/scripts 'test_*.py'        PASS (11/11)
./gradlew testDebugUnitTest lintDebug assembleStandaloneDebug assembleHomeDebug
          :app-shell:compileStandaloneDebugAndroidTestKotlin    PASS
./gradlew :app-shell:connectedStandaloneDebugAndroidTest        PASS (8/8, API 34)
```

API 34 Pixel 7 模拟器完成 Dark Start、Light Display 与 Light Start 目视验收：Dark 是整屏纯黑与白色主前景；Light 是整屏纯白与黑色主前景；两种模式的 cyan 磁贴均保持白色 glyph、数值与入口名。颜色精确值由 JVM 合同断言，截图用于验证组合、覆盖范围和视觉方向。按用户要求，本切片不监控上一轮远端流水线。

### English translation

The user review exposed a real fidelity error. Microsoft documents the Phone page theme as white-on-black by default and black-on-white in Light, while the Windows Phone 8.1 secondary-tile API says tile foreground is always light. The Red contract failed 2 of 5 tests against the former near-black/near-white canvas and luminance-selected black tile text. Green now uses exact black/white page pairs and an exact white foreground for every accent tile in both modes; the generic contrast chooser was removed. A subsequent real-device Red found that the Android host window still left a fixed black display-cutout strip in Light. Host bar colors, dark-icon appearance, and short-edge cutout coverage now follow the same Shell mode.

## Slice 11 — 可提交的本地加密密钥保险库

需求来源：用户需要用一个只由本人掌握的主口令维护未来的多组 API key，同时让维护脚本和密文可以进入 GitHub。安全合同见 [`requirements/SECRETS_MANAGEMENT_REQUIREMENTS.md`](requirements/SECRETS_MANAGEMENT_REQUIREMENTS.md)，操作手册见 [`SECRETS_MANAGEMENT.md`](SECRETS_MANAGEMENT.md)。

### 安全判断

整体方向合理，但用户在当前对话中给出的示例口令已经进入共享介质，而且长度和可预测性都不足，因此被明确判定为不可使用。本切片没有把它复制到代码、命令、测试、日志或 vault。个人 vault 必须由仓库所有者稍后在本机用一个全新的强口令交互初始化。

实现不自创密码学：`age -p` 保护随机 identity，identity 对应的 recipient 加密整个 JSON vault。仓库只允许提交 `identity.age`、`recipient.txt` 和 `vault.json.age`；CI 永远不拿个人主口令解锁它们。

### Red 1 — 缺失工作流

先添加 `.github/scripts/test-secrets-manager.sh`，再运行：

```text
bash .github/scripts/test-secrets-manager.sh
Secrets manager contract failed: missing executable scripts/secrets/yokuli-secrets.sh
```

这是预期的需求 Red。测试使用临时目录、假 `age`／`age-keygen` 和演示值，不测试或记录真实凭据。

### Green 1 — 保险库命令

新增 `scripts/secrets/yokuli-secrets.sh`，覆盖：

- `doctor/init/set/remove/list/get/copy/run/rotate`；
- 隐藏 stdin 写入、严格 key/value schema、无 `.env` 默认落盘；
- 权限受限临时目录、退出清理、密文后写替换和并发锁；
- 三件套残缺状态、Git 已跟踪明文文件和过宽权限检查；
- 子进程环境注入前清理临时明文；
- 主口令包装轮换与上游 API key 撤销边界。

原合同随即通过。

### Red 2 — 子进程控制变量

威胁复核发现，虽然 key 满足 POSIX 名称格式，但恶意或误写的 `PATH`、动态加载器和语言运行时选项可以改变 `run` 启动的进程。先增加拒绝 `PATH` 的测试，得到：

```text
Secrets manager contract failed: set accepted a process-control environment variable
```

Green 将名称验证拆为语法检查与注入安全检查；`set` 和 `run` 拒绝进程控制变量，同时保留 `remove` 清理异常 key 的能力。

### Red 3 — 密文形状与 placeholder 例外

`doctor` 起初只检查文件是否存在，测试把 `vault.json.age` 换成普通文本后仍然成功：

```text
Secrets manager contract failed: command unexpectedly succeeded: ... doctor
```

Green 增加 armored age header 和完整 recipient 格式检查，并在每次解密前复用。另一条先失败的合同证明 `.env.*` 检查会误伤允许提交的 `.env.example`；Git pathspec 现在明确排除该 placeholder，但仍阻断真实 dotenv、明文 vault、identity 和签名材料。

### 门禁发现与 Green

双语公开文档合同先发现操作手册缺少规范的 `English translation` 标题，修正后 Python 合同恢复 11/11。完整 Android lint 又发现上一 UI 切片的 cutout 调用无条件引用 API 28，而工程 minSdk 是 26；增加 `Build.VERSION.SDK_INT >= P` 保护后 lint 恢复。

最终本地证据：

```text
bash -n scripts/secrets/yokuli-secrets.sh                    PASS
bash .github/scripts/test-secrets-manager.sh                 PASS
bash .github/scripts/test-ci-contract.sh                     PASS
bash .github/scripts/test-resolve-release-metadata.sh        PASS
python3 -m unittest discover .github/scripts 'test_*.py'     PASS (11/11)
age 1.3.2 recipient encrypt/decrypt smoke                    PASS
./gradlew test lintStandaloneDebug
          assembleStandaloneDebug assembleHomeDebug          PASS
./scripts/secrets/yokuli-secrets.sh doctor                   PASS (UNINITIALIZED)
```

本机已安装官方 `age` 1.3.2；`jq` 为 1.7.1。`UNINITIALIZED` 是刻意保留的用户控制点，不是假 Green：测试证明系统行为，个人主口令和真实 vault 则只能由所有者亲自在交互终端创建。

### English translation

The first Red proved the requested executable did not exist. Green added an age-backed encrypted identity and JSON vault with doctor, init, hidden-input set, remove, key-only list, explicit-output get, clipboard copy, trusted-child run, and passphrase-wrapper rotation. A second threat-model Red proved that process-control names such as `PATH` were accepted; Green now rejects control variables before storage or injection. Existing bilingual-document and Android lint gates also caught a heading-contract issue and an unguarded API 28 cutout call on minSdk 26, both fixed. Fake-crypto workflow tests, the real age CLI smoke, all Python and Bash contracts, unit tests, lint, and both debug APK assemblies pass. The personal vault remains intentionally uninitialized until the owner chooses a brand-new unshared passphrase interactively.

## Slice 12 — 单 key 海图来源与 OpenCPN-like 导入边界

需求来源：用户取消 Phase 1 LINZ，希望基础地图不按环境拆 key，只保留 Google Maps、默认开放海图与用户自行导入的贴图能力。

### 调研结论

Google 当前价目表将原生 `Maps SDK` 基础地图加载列为 unlimited/no charge，但 Android SDK 文档仍要求项目绑定 billing；因此记录成“当前基础加载不收费”，不承诺永久免费。一个 Android-restricted key 可以登记多个相同平台的 package/SHA-1 对，本版本用一把 `GOOGLE_MAPS_ANDROID_API_KEY` 覆盖 standalone/home 的 debug/release 身份，并把 API restriction 限制为 Maps SDK for Android。

OpenSeaMap 的公开 seamark tile 不要求 API key，但必须保留 OpenSeaMap/OpenStreetMap attribution，也不能被描述为官方海图替代品。用户本地导入不产生供应商凭据。

OpenCPN 官方资料表明其格式横跨 MBTiles、BSB/KAP、S-57、S-63 和插件／专有格式。为了避免“类似工作流”变成“宣称完整兼容”，首个格式只选择 raster MBTiles；其余能力逐项立需求和 fixtures。

### Red — 范围不可执行

先新增 `.github/scripts/test_chart_source_contract.py`，要求精确凭据清单、三个来源、LINZ 排除和格式状态矩阵。首次运行得到 3 个失败：

```text
python3 -m unittest discover .github/scripts 'test_chart_source_contract.py'
FAILED (failures=3)
missing chart source/import requirements
```

### Green — 双语需求合同

新增 [`requirements/CHART_SOURCE_IMPORT_REQUIREMENTS.md`](requirements/CHART_SOURCE_IMPORT_REQUIREMENTS.md)，固定：

- 运行期只有 `GOOGLE_MAPS_ANDROID_API_KEY`，不按 dev/prod 拆分；
- Google 底图、OpenSeaMap seamark 与本地海图共享一个 Chart surface；
- LINZ provider、key 和 URL override 不进入 Phase 1；
- raster MBTiles 是 MVP，BSB/KAP、S-57、S-63 与专有格式不得被提前标为支持；
- 导入采用 SAF、bounded staging、内容/schema 校验、只读 SQLite、hash 与原子激活；
- 地图或 tile 故障不能停止 Anchor、Navigation、Trip、Survey 或 NMEA runtime。

定向 Green：

```text
python3 -m unittest discover .github/scripts 'test_chart_source_contract.py'
Ran 3 tests
OK
```

完整本地 Green：

```text
python3 -m unittest discover .github/scripts 'test_*.py'     PASS (14/14)
bash .github/scripts/test-secrets-manager.sh                 PASS
bash .github/scripts/test-ci-contract.sh                     PASS
bash .github/scripts/test-resolve-release-metadata.sh        PASS
./gradlew test lintStandaloneDebug
          assembleStandaloneDebug assembleHomeDebug          PASS
```

### English translation

The scope was reduced to one Android-restricted `GOOGLE_MAPS_ANDROID_API_KEY` shared by the standalone/home package and signing-certificate pairs, with no dev/prod key split. Google Maps is the connected base map, OpenSeaMap seamarks are the keyless default nautical overlay, and local imports are keyless. LINZ is explicitly excluded. A Red contract first failed three tests because no executable requirements existed; Green added the bilingual provider, credential, safety, and format matrix. Raster MBTiles is the only import MVP, while BSB/KAP and vector/encrypted/proprietary formats remain separately scoped future or unsupported work.
