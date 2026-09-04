# Stage 11 — Performance & Fidelity Gate

状态：`COMPLETE_PROVISIONAL`。自动化施工和模拟器回归已收口，但以下两项只能由仓库所有者和真实硬件关闭：

```text
Golden: CANDIDATE_PENDING_HUMAN_REVIEW
physical WP8 fidelity: PENDING_HUMAN_REVIEW
Samsung square: UNVERIFIED_HARDWARE
physical 60/90/120 Hz: UNVERIFIED_HARDWARE
```

这不是把未完成写成通过；它表示 Stage 11 已生成可复审的代码、profile、trace 路径和候选图，最终 Definition of Done 仍由人工／真机证据决定。

## 基线与 TDD

```text
branch: codex/launcher-engine-stage11
starting SHA: 1192d0bf9cee42266fe8430fd7ba59c424c03c56
reviewed measurement hash: af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5
```

Stage 11 静态合同最初在缺失 benchmark 模块、profile、候选图、报告和 CI job 时进入 Red。自审又增加“生成的 baseline/startup profile 必须实际存在、非空且只包含 Yokuli 产品规则”的 Red，防止只提交一个 generator 壳子。

Green 新增独立 `:benchmark:shell` 与 `:baselineprofile:shell`。Macrobenchmark 使用 release-like、profileable、不可调试的目标 APK，记录 cold/warm Start、Start→All Apps、Chart→Back、以及 Debug/Benchmark-only Shell Lab 的 60 Tile 垂直滚动；AndroidX 原始 JSON 和 trace 由 CI 原样上传。物理设备使用精确 Perfetto `FrameTimingMetric`，没有 RenderThread slice 的 emulator 使用 AndroidX `FrameTimingGfxInfoMetric` 采集真实目标帧。趋势汇总器保留原始 metric，不设置模拟器硬阈值。

Baseline/Startup Profile 覆盖 Start、All Apps、Edit、Unpin、Context Pin、Chart launch 和 Back to Start。生成器在每个采样循环清理的只有目标测试包数据，以免 Stage 10 的持久页、用户 Pin 状态或 crash-loop Recovery 污染下一轮；它不会清理仓库、密钥或外部设备数据。自审修正了误 force-stop instrumentation、错误 applicationId、HOME 隐式解析、持久状态污染、startup profile 只等 package 导致 0 行，以及动态菜单物理 tap 偶发丢失。最终启动路径等待真实 `start-screen`，Context Pin 使用 accessibility action 并断言最终页面与磁贴。规则过滤也从错误的 `startsWith` 改为包含产品 descriptor，因此不会漏掉带 H/S/P flag 的热方法。

默认 15 轮 profile 收集对两个 flavor 会产生不必要的长等待；这不是性能门限测量。最终生成器使用最多 3 轮、连续 2 轮稳定即收敛，真实性能仍由独立 5-repeat Macrobenchmark 和后续真机校准承担。自审还发现 `nonMinifiedRelease` profile harness 与 benchmark 一样会主动 force-stop，现仅对这两个 harness build type 关闭 crash-loop 记账并强制健康 Start；Debug/Release 的生产恢复语义不变。最终重新生成得到 Baseline 1824 条、Startup 1464 条产品规则，两个 Release APK 均包含编译后的 `assets/dexopt/baseline.prof` / `.profm`。

## Fidelity 候选与按键边界

候选集合包含 Master 固定的八个场景和额外 360×360 方屏，共九张。稳定场景由 API 34 模拟器在 160 dpi、font scale 1.0、中文下通过 `adb screencap` 取得；启动平面从动画比例 1.0 的 Android `screenrecord` 提取真实透视中间帧，并无裁切缩放为 360×640。`GOLDEN_CANDIDATES.json` 锁定 PNG signature、路径、SHA-256、bytes、dimensions、viewport、locale、theme 和采集方法；validator 要求精确场景集合并强制 `PENDING_HUMAN_REVIEW`，不允许 Codex 自签 reviewer 或批准时间。Reference comparison 只引用人工批准的 Stage 2.5 hash，明确候选不是 WP8 原图，也不证明三星方屏。

首次 All Apps 捕获没有画出三枚 glyph。层级审计证明按键栏和 Back/Start/Search 都存在且可点击，等待五秒仍会丢图，因此不是简单截图过早。修正为独立 graphics layer，并在 Engine surface/transient 变化时重新建立 key-bar 绘制层；随后 `adb screencap` 在 All Apps、Context Menu 与 Alphabet Jump 均稳定显示三键。Activity story 继续锁定语义、可见性和三段白色 glyph 像素，但不把 Compose capture 误称为复现了 SurfaceFlinger Red。

虚拟 Back 自审又暴露 Alphabet Jump 原先只是 Compose 局部状态，会直接离开 All Apps。新增 reducer 与 Activity Red 后，字母层已成为 `LauncherTransient.AlphabetJump`；打开、关闭以及虚拟／Android Back 全部进入既有串行 Engine input path，Back 先关层且保持 All Apps。该增量不改变之前动作的语义，也没有编造按键灯效。

按键能力保持 Stage 9/10 的真实边界：虚拟 Back/Start/Search、Android Back 以及 Activity 实际收到的键盘／硬件 `BACK/ESCAPE/HOME/SEARCH` 都进入同一个 `LauncherInput -> LauncherAction -> LauncherEngine.dispatch` 串行通道。普通 Android Activity 不能可靠截获系统保留的物理 HOME，因此 Home flavor 依靠 HOME/DEFAULT intent、singleTask 和 onNewIntent；不声称获得不存在的平台权限。Stage 2.5 录像只证明 glyph family，WP8 按键灯、触感与输入 latency 仍是 `NOT_OBSERVED`。

## 自动化 Gate

```text
Stage 0–11 Python/helper contracts                  PASS
Golden candidate semantic validator                 PASS
Baseline + Startup Profile generation                PASS
Macrobenchmark representative emulator journeys     PASS (trend only)
60-Tile deterministic Shell Lab JVM test             PASS
320×320 / 360×360 simulated viewport stories         PASS
All Apps virtual-key continuity Activity story       PASS
Gradle test + lint + dual Debug/Release + androidTest PASS
API 34 real ShellActivity stories                    PASS
Standalone/Home Release product-surface audit        PASS
Release metadata / CI / secrets contracts            PASS
WP8 Stage 2.5 approved-reference validation           PASS
```

本地 API 34 设备门为 `26/26`。最终五类模拟器趋势为：cold Start TTID median `670.31 ms`、warm Start TTID median `282.05 ms`、Start→All Apps gfx frame P50/P90 `29/53 ms`、Chart→Back `57/65 ms`、60 Tile scroll `25/40 ms`。这些数值只用于同环境回归趋势，尤其 Chart 动画数字不能冒充 60/90/120 Hz 真机通过。当前本机没有 API 36 AVD；API 36 reduced-motion smoke 由 hosted CI Gate 执行。

第一次 hosted run `33919498098` 的 build/API 36 已通过；API 34 捕获了测试 reset 竞态，performance 失败却只有通用 process annotation。Stage 11 correction 将 reset 后的 Home 与完整 Start 前置条件锁定，并让 performance job 直接上报 benchmark XML、把 benchmark/profile 报告纳入受限诊断包；cold/warm 启动改用显式 Activity intent，慢速 hosted emulator 等待窗口扩为 20 秒。Red 复现同时证明 setup 中预启动 Activity 会与 AndroidX cold/warm 生命周期控制竞争，因此 startup setup 只回 Android Home，目标 Activity 只在 measure block 启动。修正后的本地失败 story、26/26 全故事和 5/5 Macrobenchmark 均通过，最终接受以 correction commit 对应的 hosted run 为准。

第二次 hosted run `33922516174` 的 API 34 首条 story 进一步暴露 Compose 点击与串行 Engine queue 的边界：点击完成不等于异步 Surface 已完成转场。Activity stories 现等待目标页面／Transient 的语义节点真实可见或移除后再断言，不改变生产动画、不添加固定 sleep，也不把 Engine 改回同步；下一轮 hosted 仍须执行完整故事集。

该 run 的 performance annotation 同时记录四个 Start tag timeout 与 60 Tile `no renderthread slices`。CI 原先强制使用已被当前 Android Emulator 弃用的 `swiftshader_indirect`；三个 emulator job 现统一使用官方推荐的 `-gpu auto`，但 API 34 仍启用动画、performance job 仍采集 AndroidX 帧指标，并继续明确禁止把 emulator 数字当作物理设备结论。

第三次 hosted run `33923989525` 已使 API 34 完整 stories 与 API 36 smoke 通过；performance 进一步确认 fresh-install 语言重建与软件 renderer trace 是独立问题。Harness 现跳过首次 `LocaleManager` 重建，避免把平台重启记作启动性能；emulator 交互帧改由 AndroidX gfxinfo metric 观测，物理设备的 Perfetto frame metric 保持不变。最终 hosted 接受仍须五条 journey 全部执行成功。

CI 新增 `stage11-performance` job。它执行真实 `connectedStandaloneBenchmarkAndroidTest`，上传 `stage11-performance-reports`，并把 emulator 结果标为 `EMULATOR_TREND_ONLY`；verified debug APK 必须等待该 job、API 34 stories 和 API 36 smoke 全部成功。两个 Release flavor 继续只有 Chart + Settings，Shell Lab 仅进入 debug/benchmark classpath。

## 人工最终审核清单

仓库所有者需要：逐张接受或拒绝候选 Golden；在指定中端 60 Hz、90 Hz、120 Hz 真机校准帧门限；在真实三星方屏验证布局、触摸、旋转与按键区；把设备型号、构建、trace 和判定写回，不得只回复“看起来顺”。在这些证据进入仓库前，`Macrobenchmark 达标`、`Golden 人工通过`、`真实三星方屏通过` 三项最终 DoD 保持未勾选。

## English translation

Stage 11 is `COMPLETE_PROVISIONAL`: its automated construction, profiles, emulator trends, simulated-square coverage, and content-addressed renderer candidates are complete, while Golden acceptance, physical WP8 fidelity, physical 60/90/120 Hz performance, and Samsung-square hardware remain explicitly pending.

Separate Macrobenchmark and Baseline Profile modules exercise representative startup, paging, Chart return, and 60-tile stress journeys against a release-like target. Generated Startup/Baseline rules cover Start, All Apps, Edit, Unpin, Context Pin, Chart launch, and Back, are versioned in the app, and are restricted to Yokuli product descriptors. CI archives raw AndroidX metrics and traces and labels emulator output as trend-only instead of applying physical-device thresholds.

Nine API 34 emulator images cover all eight Master scenes plus an extra 360-square scene. Settled scenes are direct screenshots; the launch-plane image is extracted from a real normal-speed screen recording. Signatures, hashes, sizes, dimensions, metadata, exact scene coverage, and the approved Stage 2.5 reference hash are validated. Device screenshots exposed missing glyph layers over dynamic planes; isolated key-bar composition fixed All Apps, Context Menu, and Alphabet Jump. Alphabet Jump is now an Engine transient, so Back dismisses it before leaving All Apps.

Virtual Back/Start/Search and deliverable Android or keyboard events still share the serialized Engine input path. Android physical HOME remains OS-reserved; the HOME flavor intent is the truthful integration boundary. No WP8 key-light, haptic, latency, physical refresh-rate, or Samsung hardware result is invented. Human and device evidence is required before final acceptance.
