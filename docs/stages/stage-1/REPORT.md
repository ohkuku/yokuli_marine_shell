# Stage 1 — Product Surface Reduction Report

状态：`PENDING_HUMAN_REVIEW`。本报告只覆盖 Stage 1，不批准或启动 Stage 2。

## Baseline

```text
stage: 1 — Product Surface Reduction
starting tag: launcher-engine-stage0-approved-v1.1
starting SHA: 16b0e5cd1c8fa2e5f4b78aefadf3fa7c012698b2
ending SHA: commit containing this report
branch: codex/launcher-engine
approval: PENDING_HUMAN_REVIEW
```

Stage 0 approval evidence 是 GitHub Actions run `33854599910`。`ca84ef9…` 只提供候选实现，Stage 1 重新从 Red 和完整 Gate 审计。

## Stage 1 correction

```text
correction starting SHA: 8914fc81034de250ba7870a019549d9521c581a3
scope: truthfulness copy and both Release flavors audit only
approval: PENDING_HUMAN_REVIEW
```

人工复核发现 `GOOGLE_MAPS_CONFIGURED` 只证明提供了非占位密钥，不能证明 SDK、网络、API 授权、账单、应用限制或图块加载就绪。本 correction 因此把 Release 文案收紧为“地图已配置 / MAP CONFIGURED”和“仅浏览 / BROWSE ONLY”，移除船位状态、未来路线图与不存在的诊断暗示。Settings 只陈述当前构建、地图配置与桌面文档事实。

Release 二进制 Gate 同时构建并检查 standalone 与 HOME APK。二者必须包含 ShellActivity、Chart、Settings，必须排除 ShellLabActivity、Cockpit、Library 与旧 System feature；HOME 只额外要求预期的 HOME/DEFAULT 启动类别。Stage 0 contracts、当前 Google Maps adapter、Shell Engine candidate、手势、布局、持久化与 reducer 均保持不变。

## Scope

```text
implemented:
- exact Chart + Settings production catalog and Start-document audit
- exact All Apps API story assertion
- removed-feature source/dependency audit
- Chart Browse-only and truthful Settings audit
- Coming Soon and fabricated marine-value rejection
- both Release flavors binary inspection for Chart + Settings inclusion and removed-feature exclusion
- named Stage 1 GitHub Actions gate

explicitly not implemented:
- new product UI or feature behavior
- Shell Engine contract extraction
- geometry, reducer, persistence or renderer refactor
- WP8 Reference acquisition
- any marine runtime capability
- Stage 2
```

## Architecture

```text
production modules changed: feature:desktop, feature:chart, feature:settings (copy/resource identifiers only)
release catalog: Chart + Settings
debug-only module: feature:shell-lab
standalone/home release ShellLabActivity: ABSENT
dependency direction changed: NONE
Stage 2: NOT STARTED
```

## Interaction

```text
state transitions added: NONE
cancel behavior: NOT CHANGED
back/home behavior: NOT CHANGED
gesture or animation behavior: NOT CHANGED
```

## Tests

```text
Stage 1 Red: EXPECTED FAILURE — 8 tests; failures=3
Stage 1 correction Red: EXPECTED FAILURE — 9 tests; failures=3
Stage 1 static contract: PASS (8/8)
Stage 1 correction static contract: PASS (9/9)
both Release flavors product-surface contract: PASS (Chart + Settings; Shell Lab and removed features absent)
all Python contracts: PASS (45/45)
Bash CI/release/secrets contracts: PASS
unit: PASS
compose/API 34: PENDING_HOSTED_CI
golden: NOT_YET_MEASURED
benchmark: NOT_YET_MEASURED
lint: PASS
assemble standalone debug: PASS
assemble home debug: PASS
assemble standalone release audit APK: PASS
assemble home release audit APK: PASS
assemble standalone debug AndroidTest APK: PASS
```

## Hardware

```text
API 34 emulator: PENDING_HOSTED_CI
API 36 emulator: PENDING_HOSTED_CI
Samsung square: UNVERIFIED_HARDWARE
refresh rate: NOT_YET_MEASURED
physical WP8 reference device: NOT_YET_MEASURED
vessel: UNVERIFIED_HARDWARE
```

## English translation

Stage 1 starts exactly from the Stage 0 approval tag and remains pending human review. The correction starts from `8914fc8…` and is limited to truthful release copy plus binary inspection of both Release flavors. Map configuration no longer claims readiness, no vessel-position state or roadmap copy is exposed, and About no longer claims diagnostics. Standalone and HOME must both contain ShellActivity, Chart, and Settings while excluding Shell Lab and removed features. Shell Engine extraction and Stage 2 are explicitly not started.

## Stop

Stage 1 remains `PENDING_HUMAN_REVIEW`. Stage 2 is stopped.

STOPPED AT STAGE GATE.
AWAITING HUMAN REVIEW BEFORE NEXT STAGE.
