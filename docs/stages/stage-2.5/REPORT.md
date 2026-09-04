# Stage 2.5 — WP8 Reference Acquisition & Human Approval Report

状态：`HUMAN_REVIEWED`／`APPROVED`。仓库所有者 kuku 已核对并批准 canonical measurement hash；本报告仍停止在 Stage 2.5，不启动 Stage 3。

## Baseline

```text
stage: 2.5 — WP8 Reference Acquisition & Human Approval
starting tag: launcher-engine-stage2-approved-v1
starting SHA: 5386da0575046f1f9a59742a4a0f5c78523fa5e6
Stage 2 evidence run: 33864829489
ending SHA: commit containing the human-approved reference package
branch: codex/launcher-engine-stage2.5-rebuild
approval: APPROVED by kuku at 2026-09-04T13:41:36Z
nextStageStarted: false
```

本 Stage 从正式批准的 Stage 2 tag 开始，没有从被否决的 Stage 2.5 候选继承媒体、测量值或完成结论。

## Scope

```text
implemented:
- owner-supplied WP8.1 emulator recording as the sole visual source
- 16 exact uncropped timestamped PNG extractions
- content-addressed source/capture manifest and rights boundary
- scenario-specific geometry and observable-motion measurements
- truthful OBSERVED / PARTIALLY_OBSERVED / VISUAL_ONLY / NOT_OBSERVED coverage
- deterministic extraction script and pinned acquisition dependencies
- semantic evidence validator and named Stage 2.5 CI gate
- bilingual measurement method, report, notices and fullscreen/navigation decision

explicitly not implemented:
- Stage 3 geometry/runtime types or replacement of candidate geometry
- reducer, state, effects, transactions, persistence or action queue
- pager/gesture/drag/resize/pin/unpin/press animation implementation
- Android immersive fullscreen or virtual Back/Start/Search runtime
- Golden renderer output, benchmark or physical Samsung-square validation
- marine capability or production UI changes
```

## Architecture

```text
production modules changed: NONE
reference input: docs/reference/wp8/screenshots/owner-emulator-recording/
machine source truth: SOURCE_MANIFEST.json
machine measurement truth: WP8_REFERENCE_MEASUREMENTS.json
schema truth: WP8_REFERENCE_MEASUREMENTS.schema.json
semantic gate: .github/scripts/validate_wp8_reference.py
acquisition tool: .github/scripts/extract_wp8_reference_frames.py
engine forbidden imports: NOT CHANGED
Stage 3: NOT STARTED
```

Schema 的 Stage 2.5 变化是向后兼容的真实性扩展：`densityDpi` 在模拟器没有物理密度证据时允许 `null`，并增加 `BACK_RETURN`、`LIVE_TILE_CYCLE` 与自主 cycle 事件。Stage 0 fixtures 与 Draft 2020-12 validator 仍是回归 Gate。

## Interaction

```text
state transitions added to product: NONE
cancel behavior added to product: NONE
back/home behavior changed: NONE
Start -> All Apps visible settle: measured about 700 ms
Start -> app visible interval: measured about 1000 ms
app -> Start visible settle: measured about 750 ms
People Live Tile visible content-change window: measured about 1250 ms
input latency / pointer timing: NOT_OBSERVED
edit / drag / resize / pin / unpin / fast fling: NOT_OBSERVED
virtual Back/Start/Search activation: VISUAL_ONLY glyphs; activation NOT_OBSERVED
```

上面的时间只描述录屏中可见画面的区间，不是 touch latency、进程启动时长或 Live Tile 数据刷新周期。没有 pointer overlay 的 input timeline 只作为推断 trigger bracket，不能成为后续手势阈值。

产品所有者新增的“默认沉浸式全屏 + 壳内虚拟 Back/Start/Search”决定已写入 `FULLSCREEN_NAVIGATION_DECISION.md`。Stage 4 必须让所有输入进入同一串行 `LauncherEngine.dispatch(action)`；Stage 2.5 不提前实现它。

## Evidence

```text
visual source: repository-owner-supplied kuku.mp4
stored source SHA-256: 114d8acdb2ae4b8f9e35e5a84bd3077ec52ba7e647ed7e4fc54fd159ea62b94c
stored source bytes: 28760248
source video: 1920x1080, H.264/yuv420p, nominal 60 fps, 408044 ms
exact extracted frames: 16
crop/resize/annotation: NONE
external-camera or handheld visual references: NONE
Microsoft documents: 5 primary/official corroborating sources
```

Microsoft 文档用于确认四列 Start、可调整 Live Tile、向左进入 Apps list、内容优先语言、480×800 逻辑基准与 transition/tilt 的设计语义；所有像素和毫秒数仍只来自用户提供的视频。权利边界见 `THIRD_PARTY_NOTICES.md`。

## Measurements

```text
profile: WP8_CLASSIC_PHONE_4COL
viewport: 480 x 800 logical px; physical DPI unknown/null
side insets: 24 logical px
first visible tile top: 57 logical px
seam: 12 logical px
small: 99 x 99 logical px
medium: 210 x 210 logical px
wide: 432 x 210 logical px
status strip: 32 logical px
large title baseline: about 158 ±3 logical px
canonical measurementSets SHA-256:
af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5
```

Tile edge uncertainty is approximately ±1 logical pixel. `bottom=0` means no fixed bottom boundary was visible in the vertically continuous Start document; it is not a measured bottom inset. Missing evidence remains `NOT_OBSERVED` rather than being replaced with current Yokuli output or estimated constants.

## Tests

```text
Stage 2.5 Red: EXPECTED FAILURE — Ran 8 tests; failures=9, errors=3
pre-review semantic validation: PASS (status=MEASURED; captures=16; measurementSets=5)
deterministic re-extraction check: PASS (16000 ms frame hash reproduced exactly)
Stage 0 contract: PASS (10/10)
Stage 1 contract: PASS (9/9)
Stage 2 contract: PASS (10/10)
Stage 2.5 contract: PASS (9/9; human review and hash verified)
all Python contracts: PASS (64/64)
Bash CI/release/secrets contracts: PASS
unit/lint/debug/release/AndroidTest assemblies: PASS (954 Gradle tasks)
dual Release product-surface APK audit: PASS (standalone + HOME)
golden/benchmark: NOT RUN
```

Red 先建立了缺失 baseline/source/measurement/method/rights/fullscreen decision/semantic validator/CI/report 的合同；第一次执行精确失败于这些 Stage 2.5 产物尚不存在。`--require-human-review` 在所有者批准前正确失败；批准记录写入后必须由最终 Gate 重新验证。

## Hardware

```text
WP8.1 emulator recording: VERIFIED_SOURCE_EMULATOR_VIDEO
exact emulator image/build: NOT EXPOSED BY RECORDING
physical WP8 device: NOT USED
API 34 emulator: NOT RUN IN THIS DOCUMENT-ONLY STAGE YET
API 36 emulator: NOT RUN IN THIS DOCUMENT-ONLY STAGE YET
Samsung square: UNVERIFIED_HARDWARE
refresh rate/input latency: NOT YET MEASURED
vessel: UNVERIFIED_HARDWARE
```

## Human Review

仓库所有者 kuku 于 `2026-09-04T13:41:36Z` 明确批准 Stage 2.5、profile revision 1 与 canonical measurement hash `af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5`。Measurement status 已提升为 `HUMAN_REVIEWED`，baseline approval 为 `APPROVED`；reviewer、time、decision、notes、revision 与 reviewed hash 已由机器合同绑定。

## English translation

Stage 2.5 starts from the approved Stage 2 tag and rebuilds the reference package without inheriting rejected candidate evidence. The sole visual source is the repository-owner-supplied WP8.1 emulator recording. Sixteen full, untransformed frames are bound to exact timestamps, byte sizes, dimensions, and SHA-256 hashes. Five official Microsoft documents corroborate product semantics only; every numeric geometry and timing value comes from the recording.

The measured 480×800 logical profile has 24-pixel side insets, a 12-pixel seam, and 99×99, 210×210, and 432×210 tile bounds. Visible intervals are approximately 700 ms for Start-to-All-Apps settle, 1000 ms for app open, 750 ms for app-to-Start settle, and 1250 ms for one Live Tile content transition. The video does not expose pointer input, key activation, edit mode, fast fling, pin, drag, resize, or unpin; these remain `NOT_OBSERVED` or `VISUAL_ONLY`, not inferred implementation constants.

The semantic validator checks schema state, source/capture hashes, byte sizes, signatures, dimensions, references, viewport, timeline ordering and deltas, geometry, scenario coverage, and the hash-bound review. Production modules and runtime behavior are unchanged. The new immersive-fullscreen and shell-owned virtual Back/Start/Search requirement is recorded for Stages 3/4/5/9/10 and is not claimed as implemented. Stage 3: NOT STARTED.

## Stop

Stage 2.5 is `HUMAN_REVIEWED` and `APPROVED` by repository owner kuku. This report stops before Stage 3, which has not started.

STOPPED AT STAGE GATE.
AWAITING HUMAN REVIEW BEFORE NEXT STAGE.
