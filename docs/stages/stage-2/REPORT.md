# Stage 2 — Engine Contract Extraction Report

状态：`PENDING_HUMAN_REVIEW`。本报告只覆盖 Stage 2，不批准或启动 Stage 2.5。

## Baseline

```text
stage: 2 — Engine Contract Extraction
starting tag: launcher-engine-stage1-approved-v1
starting SHA: df371fbfcb4cd467bccc43dd850e23d9bd7d0e85
ending SHA: commit containing this report
branch: codex/launcher-engine
approval: PENDING_HUMAN_REVIEW
```

Stage 1 approval evidence 是 GitHub Actions run `33861223067`。本 Stage 从该批准点直接开始，没有继承或启动后续 Stage。

## Scope

```text
implemented:
- pure Kotlin/JVM :core:shell-contract
- pure Kotlin/JVM :core:shell-engine
- :ui:shell-compose internal app host boundary
- :adapter:shell-android host port and host resolver
- opaque LauncherAppId, LauncherEntryId, LaunchToken and TileInstanceId
- contribution/catalog composition with duplicate and ownership validation
- app-agnostic token navigation through LauncherHostPort
- Chart/Settings composition and host wiring in app-shell
- removal of unused provisional persistence-port stubs that belong to later stages
- named Stage 2 CI architecture gate

explicitly not implemented:
- WP8 reference acquisition or human review
- new geometry, pager, gesture, drag, animation or layout behavior
- reducer/controller, persistence, recovery, live tiles or performance work
- any marine runtime capability
- Stage 2.5 or any later Stage
```

## Architecture

```text
core:shell-contract: Kotlin/JVM; platform and product agnostic
core:shell-engine: Kotlin/JVM; depends only on shell-contract
ui:shell-compose: owns InternalAppHost/Resolver rendering boundary
adapter:shell-android: owns StaticLauncherHostPort and resolver implementation
features: contribute opaque catalog descriptors; no engine-internal dependency
app-shell: composition root for Chart + Settings contributions and hosts
legacy core:shell module: removed
legacy MarineAppId/DestinationId/LaunchTarget: removed from production
```

完整依赖审计见 `ARCHITECTURE_AUDIT.md`。

## Interaction

```text
Chart tile/list entry -> Chart Browse host: PRESERVED
Settings tile/list entry -> Settings Overview host: PRESERVED
Chart map-settings action -> Settings Map host: PRESERVED
Back/Start/All Apps behavior: NOT CHANGED
theme/language/map adapter behavior: NOT CHANGED
gesture and animation implementation: NOT CHANGED
Stage 2.5: NOT STARTED
```

## Tests

```text
Stage 2 Red: EXPECTED FAILURE — 9 tests; failures=9
Stage 0 contract: PASS (10/10)
Stage 1 contract: PASS (9/9)
Stage 2 contract: PASS (10/10)
shell-contract JVM tests: PASS
shell-engine JVM tests: PASS
shell-android adapter unit tests: PASS
app-shell standalone Debug Kotlin compile: PASS
all Python contracts: PASS (55/55)
Bash CI/release/secrets contracts: PASS
unit/lint/debug/release/AndroidTest assemblies: PASS (954 Gradle tasks)
dual Release product-surface APK audit: PASS
API 34 local real-Activity stories: PASS (8/8)
API 34 hosted real-Activity stories: PENDING_HOSTED_CI
API 36 reduced-motion smoke: PENDING_HOSTED_CI
golden/benchmark/refresh rate: NOT_YET_MEASURED
```

## Hardware

```text
API 34 local emulator: VERIFIED_DEVICE_EMULATOR (8/8 stories)
API 34 hosted emulator: PENDING_HOSTED_CI
API 36 emulator: PENDING_HOSTED_CI
Samsung square: UNVERIFIED_HARDWARE
physical WP8 reference device: NOT_YET_MEASURED
refresh rate/input latency: NOT_YET_MEASURED
vessel: UNVERIFIED_HARDWARE
```

## English translation

Stage 2 begins exactly at the approved Stage 1 tag and remains pending human review. It extracts pure Kotlin contract and engine modules, a Compose internal-host boundary, and an Android host adapter. Opaque IDs/tokens and feature contributions replace marine-specific routing and the fixed registry. `app-shell` remains the only composition root for Chart and Settings. Existing product UI, gestures, animations, map adapter, and behaviors are preserved. Reference acquisition, geometry, reducer, persistence, direct manipulation, performance work, marine runtime, and Stage 2.5 are explicitly not started.

## Stop

Stage 2 remains `PENDING_HUMAN_REVIEW`. Stage 2.5 is stopped.

STOPPED AT STAGE GATE.
AWAITING HUMAN REVIEW BEFORE NEXT STAGE.
