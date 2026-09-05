# Marine Shell Final Correction TDD Log

## Machine Gate

### Red

最终质量 Slice 首先加入三个会因真实缺口失败的合同：规范要求的六条新增性能旅程不存在；生成的 Baseline/Startup Profile 仍含已删除的旧 motion 类型；最终 Gate、报告与机器／人工／真机状态尚未封口。首次运行得到 3 个失败。

自审完整 11 条 Macrobenchmark 后又发现 `settingsScroll` 虽然返回成功，但 `gfxFrameTotalCount=0`。新增汇总器合同拒绝任何零目标帧的交互旅程；旧结果因此按预期进入 Red，而不是被写进通过报告。

### Green

- Shell Lab 现在以真实 Reducer 驱动 30/60 个六尺寸混合 Tile，支持直接长按、拖拽、Resize 和 320dp 圆角视口。
- Macrobenchmark 覆盖 Desktop ↔ Module List、Search → Chart、30 Tile 拖拽、2×2 → 4×4 Resize、320dp 圆角视口和 Settings 导航／滚动测量；连同原 Stage 11 场景共 11 条，每条 5 次迭代。
- Settings 测量窗口包含 Settings Surface 进入，修正了高屏幕上紧凑列表无需滚动而产生 0 帧的假阳性；定向复测观察到每轮 2 个目标帧。
- Baseline Profile 重新生成 1984 条规则，Startup Profile 重新生成 1605 条规则；旧 motion 类型已消失，当前 exact surface transition 类型已进入 Profile。
- 最终 Gate 固化 host 与 device 两种模式，完整调用单元测试、lint、构建、Release 产品表面审计、Activity stories、性能旅程与趋势汇总。

所有模拟器数据只用于同环境回归趋势。Golden 人工判断、三星方屏、普通真机和 60/90/120 Hz 仍未由机器关闭。

### Hosted correction

提交 `5e9d741` 的 GitHub run `33934748331` 中，build、29 条 API 34 stories 与 API 36 smoke 通过，但 Hosted Macrobenchmark 的 7 条生产 `ShellActivity` 旅程都在等待 `start-screen` 时超时；4 条独立 Shell Lab 旅程没有相同失败。产品纠错要求普通 task relaunch 保持当前内部 Surface，而旧 benchmark setup 仍把显式 Activity launch 当作 Desktop reset，这在 Hosted 的 task 复用方式下不成立。

新增 Red 合同要求 benchmark intent、Activity build-type guard 和 ViewModel 串行 reset 三者同时存在。Green 使用只在 `BuildConfig.BUILD_TYPE == "benchmark"` 生效的显式 handshake，经 Engine queue 依次恢复默认文档、退出 Safe Mode 并显示 Desktop。普通 Debug/Release intent 没有该能力，`onNewIntent` 的生产“保留当前 Surface”语义不变。

## English translation

The quality slice began Red with three real gaps: the six correction performance journeys were absent, generated profiles still referenced removed motion types, and the final gate/report/status lock did not exist. The first contract run failed three tests.

Self-review then found a false positive: `settingsScroll` completed while `gfxFrameTotalCount` was zero. The summarizer now rejects every interaction journey that observes no target frames. The Settings measurement window includes entry into the Settings surface, and its focused rerun observed two target frames per iteration.

The real reducer-driven Shell Lab supports 30/60 mixed six-size tiles, direct long-press/drag/resize, and a 320dp rounded viewport. Eleven five-iteration Macrobenchmark journeys cover the correction and retained Stage 11 paths. Regenerated Baseline and Startup Profiles contain 1984 and 1605 rules respectively and no removed motion contracts. Emulator results remain regression trends only; human Golden review, Samsung-square hardware, ordinary physical devices, and 60/90/120 Hz validation remain pending.

Hosted run `33934748331` passed build, all 29 API 34 stories, and API 36 smoke, but seven production-Activity benchmarks timed out at `start-screen` while all four standalone Shell Lab journeys avoided that failure. A new Red contract now requires an explicit benchmark-only start rendezvous. The Green implementation restores the default document, exits Safe Mode, and shows Desktop through the serialized Engine queue only when the target build type is `benchmark`; normal task relaunch continues to preserve the current Release surface.

Hosted correction run `33936328836` 的 build 与 API 36 通过，但 `lightThemeUpdatesHostWindowChromeOutsideCompose` 暴露测试在异步持久化与 Compose `SideEffect` 完成前直接读取 window color。生产代码没有失败证据；测试从固定的 `waitForIdle()` 改为最多 10 秒轮询 Activity window 的真实 status/navigation bar 颜色，命中后仍执行完整 icon appearance 断言。该同步等待不改变产品动画或主题保存流程。

Hosted correction run `33936328836` passed build and API 36, while `lightThemeUpdatesHostWindowChromeOutsideCompose` exposed that the story read window colors before asynchronous persistence and the Compose `SideEffect` completed. The product path had no failure evidence. The story now waits, for up to ten seconds, for the real Activity status/navigation bar colors before retaining the full icon-appearance assertion; no production animation or theme persistence behavior changed.

最新 run `33937077179` 的 build、API 34 stories 与 API 36 全绿，但 7 条生产 Activity 性能旅程仍在全新进程的 `start-screen` 前超时，证明仅靠 intent handshake 无法消除首次异步 Proto restore。进一步 Red 合同要求 harness Engine 显式使用 `InMemoryLauncherPersistence(defaultStartDocument)`。Green 将 `benchmark` 与 `nonMinifiedRelease` 的 Engine 初态改为同步默认文档；真实 `debug/release` Engine 仍使用 Proto DataStore、迁移、修复和 Recovery。真实偏好流仍来自 Proto，性能 harness 不会把模拟器反复 force-stop 记作用户恢复事件。

The latest run `33937077179` passed build, API 34 stories, and API 36, but all seven production-Activity performance journeys still timed out before `start-screen` in a fresh process. This proved that the intent handshake alone could not remove first-load Proto restore latency. A further Red contract requires the harness Engine to use `InMemoryLauncherPersistence(defaultStartDocument)`. Benchmark and non-minified profile builds now begin from a synchronous default Engine document, while real Debug/Release builds retain Proto DataStore migration, repair, and Recovery. Persisted preference flows still come from Proto, and harness force-stops are not recorded as user recovery events.

Run `33938475676` 再次通过 build、API 34 stories 与 API 36，但 Hosted 性能 job 仍有相同 7 条 `start-screen` 超时，本地完整 Gate 则为 11/11。现有 annotation 没有 Activity／进程／可见 Surface 证据，无法负责任地区分渲染卡住、Activity 丢失或进程异常。新的 Red 合同要求超时断言输出 current package、target PID、resumed Activity、已知 Compose Surface tag 与 AndroidRuntime 摘要；根 Compose Host 增加 `shell-host` 测试标记。此提交只提升失败证据，不改变产品或性能通过条件。

Run `33938475676` again passed build, API 34 stories, and API 36, while the Hosted performance job retained the same seven `start-screen` timeouts and the full local gate passed 11/11. Existing annotations did not expose Activity, process, or visible-Surface state, so they could not responsibly distinguish stalled rendering, a lost Activity, or a process failure. A new Red contract requires timeout assertions to include the current package, target PID, resumed Activity, known Compose Surface tags, and an AndroidRuntime excerpt; the root Compose host gains a `shell-host` test marker. This commit improves failure evidence only and does not alter product behavior or the performance pass condition.

诊断 run `33939536453` 给出了决定性证据：7 次超时时 `ShellActivity` 都是 top-resumed、目标进程存活，但 `currentPackage=android` 且包括 `shell-host` 在内的全部应用 tag 都被遮挡。本地模拟器的 `secure immersive_mode_confirmations=confirmed`，Hosted 全新模拟器则首次触发平台沉浸式全屏教育覆盖层。因此上一轮 Proto restore 只是尚未证实的假设，现由运行时证据推翻。新的 Red 合同要求 benchmark 在测量前确认平台 immersive education，并只在 Android/SystemUI 位于前景且目标 `ShellActivity` 仍 resumed 时允许一次 Back fallback。普通应用代码、全屏策略、实体／虚拟键语义和 Gate 标准均未改变。

Diagnostic run `33939536453` supplied decisive evidence: for all seven timeouts, `ShellActivity` was top-resumed and the target process was alive, while `currentPackage=android` and every application tag including `shell-host` was obscured. The local emulator already had `secure immersive_mode_confirmations=confirmed`; the fresh Hosted emulator triggered Android's first-run immersive education overlay. The earlier Proto-restore explanation was therefore an unconfirmed hypothesis and is superseded by runtime evidence. A new Red contract requires the benchmark to confirm platform immersive education before measurement and permits a single Back fallback only when Android/SystemUI is foreground while the target `ShellActivity` remains resumed. Product code, fullscreen policy, physical/virtual key semantics, and gate thresholds are unchanged.

修复 run `33940532006` 的 Macrobenchmark 执行 step 已 success，证明 11 条旅程与首次沉浸提示修复在 Hosted 环境通过；失败仅来自后置汇总 step。汇总器原本保留了严格的缺旅程／零目标帧拒绝合同，却只抛出普通 `SystemExit`，GitHub 公共 Checks 无法显示具体原因。新增 Red 测试要求错误必须同时生成 GitHub annotation 并继续抛出失败；Green 对无数据、缺旅程和零帧三类错误统一输出可见 annotation，未放宽任何接受标准。

Fix run `33940532006` completed the Macrobenchmark execution step successfully, proving that all eleven journeys and the immersive-education correction pass in the Hosted environment; only the post-processing summary step failed. The summarizer retained its strict missing-journey and zero-target-frame rejection contract but raised only a plain `SystemExit`, leaving the public Checks view without the specific reason. A new Red test requires every such error to emit a GitHub annotation and still fail. The Green implementation annotates no-data, missing-journey, and empty-frame failures without relaxing any acceptance criterion.

带 annotation 的 run `33941553833` 精确指出唯一空帧旅程是 `startVerticalScroll60Tiles`；11 条测试仍全部执行成功。该旅程原先用 `displayHeight - 80` 到固定 `120` 的整屏坐标，不能保证 Hosted Pixel 2 的手势落在 Start Grid 而非设备／虚拟键区域。新增 Red 合同要求从真实 `start-grid` 语义节点读取 bounds。Green 在该 bounds 内由底到顶执行相同三次 20-step Swipe，因此仍测量 60 Tile 垂直滚动，不添加无关导航或动画来伪造帧。

Annotated run `33941553833` identified the sole empty-frame journey as `startVerticalScroll60Tiles`; all eleven tests still executed successfully. That journey used screen coordinates from `displayHeight - 80` to a fixed `120`, which did not guarantee that the Hosted Pixel 2 gesture landed inside the Start Grid rather than its device/virtual-key region. A new Red contract requires bounds from the real `start-grid` semantics node. The Green journey performs the same three 20-step bottom-to-top swipes inside those bounds, so it still measures sixty-tile vertical scrolling and does not add unrelated navigation or animation to manufacture frames.
