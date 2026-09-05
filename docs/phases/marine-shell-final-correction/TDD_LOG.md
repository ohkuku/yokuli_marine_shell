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
