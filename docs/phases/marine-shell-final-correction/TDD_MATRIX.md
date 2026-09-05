# Marine Shell Final Correction TDD Matrix

状态：`MACHINE_VERIFIED`；人工视觉与真机验证仍为 `PENDING`。

| Slice | Red 合同 | Green 证据 |
|---|---|---|
| Product identity | HOME flavor/intent、`SHELL_HOME_MODE`、`ACTION_HOME_SETTINGS` 必须消失；恢复 task 不强制 Desktop | Gradle variant、merged manifest、Activity stories |
| Surface model | Search 为一等 Surface；Transition Resolver 由 from/to/trigger 唯一决定 | reducer/unit + real Activity story |
| Bridge | 中键为 Desktop/Bridge；Module List 使用 pager-back，Module 使用 exit | unit + semantics + screenshot candidate |
| Safe chrome | rounded/cutout/IME/system-gesture metrics 有纯模型与 Android adapter | viewport unit + API 34/36 stories |
| Marine tiles | 六种尺寸、size-specific content、presentation kind | `MarineTileContractTest` + renderer branch contract；Green |
| Adaptive packing | rank/insertion/spacer；混合尺寸无重叠、确定性、跨 viewport | `AdaptiveTilePackerTest` 100 seeded documents × 4/6 columns + Proto round trip；Green |
| Direct editing | local pointer offset、semantic target changes、44dp hit target、cancelable resize | Reducer tests + API 34 `chartResizeRequiresExplicitCommitAndCanCancel` / `smallTileEditControlsHaveAtLeast44DpHitTargets`；Green |
| Settings | 无逐行 accent bullet/tilt；排版型总览；4×N 紧凑 swatch | final-correction static contract 11/11 + API 34 `settingsUsesTypographicOverviewAndCompactFourColumnAccentSwatches`；Green |
| Motion/Search | Engine exact transition kind 是唯一输入；Search 直达 Module；录屏只作为可见窗口证据；未观察参数必须标记 derived | design/engine unit tests + haptic mapper unit tests + API 34 Search stories + 完整 29/29 stories；Green |
| Quality | 单测、lint、Debug/Release、APK surface、benchmark/profile/golden；交互旅程不得以 0 个目标帧假通过；Benchmark task 复用必须显式复位且不得改变 Release resume | 11/11 API 34 emulator journeys、重新生成的 Baseline/Startup Profile、累计 machine gate；Green |

每个功能 Slice 先加入能因缺失行为失败的测试，再实现 Green；修正不得静默改写已通过的旧安全合同。最终状态只可写 `MACHINE_VERIFIED / HUMAN_FIDELITY_PENDING / PHYSICAL_DEVICE_PENDING`。

## English translation

| Slice | Red contract | Green evidence |
|---|---|---|
| Product identity | Remove HOME flavor/intent, `SHELL_HOME_MODE`, and `ACTION_HOME_SETTINGS`; task restore must not force Desktop | Gradle variant, merged manifest, Activity stories |
| Surface model | Search is first-class; transition resolution depends only on from/to/trigger | Reducer/unit tests and real Activity story |
| Bridge | Center key means Desktop/Bridge; Module List uses pager-back and Module uses exit | Unit, semantics, and screenshot candidate |
| Safe chrome | Pure model and Android adapter cover rounded/cutout/IME/gesture metrics | Viewport unit tests and API 34/36 stories |
| Marine tiles | Six sizes with size-specific content and presentation kind | `MarineTileContractTest` and renderer contract; Green |
| Adaptive packing | Rank/insertion/spacer with deterministic, non-overlapping mixed sizes across viewports | 100 seeded `AdaptiveTilePackerTest` documents for 4/6 columns plus Proto round trip; Green |
| Direct editing | Local pointer offset, semantic target changes, 44dp hit targets, cancelable resize | Reducer tests and two API 34 edit stories; Green |
| Settings | No row accent bullet or tilt; typographic overview; compact 4-by-N swatches | Final-correction static contract 11/11 and API 34 Settings story; Green |
| Motion/Search | The exact Engine transition kind is the sole motion input; Search enters Module directly; recording values are visible windows only; unobserved parameters stay derived | Design/Engine and haptic-mapper unit tests, API 34 Search stories, and the complete 29/29 Activity suite; Green |
| Quality | Unit, lint, Debug/Release, APK surface, benchmark/profile/golden; interaction journeys cannot pass with zero target frames; benchmark task reuse resets explicitly without changing Release resume | 11/11 API 34 emulator journeys, regenerated Baseline/Startup Profiles, and the cumulative machine gate; Green |

Each slice starts with a meaningful failing test before Green implementation. Corrections must not silently rewrite an earlier safety contract. Final status is limited to `MACHINE_VERIFIED / HUMAN_FIDELITY_PENDING / PHYSICAL_DEVICE_PENDING`.
