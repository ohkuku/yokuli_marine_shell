# Stage 0 — Freeze & Reference Contract Report

状态：`PENDING_HUMAN_REVIEW`。本报告覆盖初始 Stage 0 以及从 `98121412893d5331b22d4327463794993a4a4eff` 开始的严格纠偏，不批准或启动 Stage 1。

## Baseline

```text
stage: 0 correction
master reviewed SHA: 943d85276e4a042092f87090aa0d23da9a7cbbc6
actual selected starting SHA: ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7
initial Stage 0 ending SHA: 98121412893d5331b22d4327463794993a4a4eff
correction starting SHA: 98121412893d5331b22d4327463794993a4a4eff
ending SHA: commit containing this report
branch: codex/launcher-engine
approval: PENDING_HUMAN_REVIEW
```

Master v1.1 的当前 SHA-256 是 `b0aeb000012283d56cf7e8eb343e2a026366e150b2f9e3d9969f45ed12f4bb40`；前一版哈希 `f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0` 已保留。覆盖理由和完整值由 `BASELINE_LOCK.json` 锁定。

## Scope

```text
implemented:
- baseline lock and pre-existing implementation reconciliation
- state-aware Draft 2020-12 WP8 capture/measurement/review schema
- 3 valid and 4 invalid schema fixtures
- pinned jsonschema validator in the named Android CI gate
- Master v1.1 mandatory Stage 2.5 human Reference gate
- corrected bilingual Reference/TDD/delivery records

explicitly not implemented:
- Stage 1 product work or any later Stage implementation
- production UI, Registry, Google Maps adapter or feature behavior changes
- shell-engine production-code changes
- marine functionality
- WP8 captures, measurements, Golden baselines or performance claims
```

## Architecture

```text
modules changed: NONE
production source changed: NONE
dependency direction changed: NONE
engine forbidden imports fixed: NOT IN STAGE 0; recorded NON_COMPLIANT_REPLACE
```

`BASELINE_RECONCILIATION.md` 逐文件记录 Android Library engine、Marine model coupling、猜测 geometry、随机 UUID、临时 stores 和旧 renderer 的未来处置。现存类名不能作为任何后续 Gate 的通过证据。

## Interaction

```text
state transitions added: NONE
cancel behavior: NOT CHANGED
back/home behavior: NOT CHANGED
gesture or animation behavior: NOT CHANGED
Reference measurements: NOT_YET_MEASURED
```

## Tests

```text
Stage 0 correction Red: EXPECTED FAILURE — 10 tests; failures=7, errors=1
Draft 2020-12 schema and fixtures: PASS — 10/10 Stage 0 contracts
all Python contracts: PASS — 36/36
Bash CI/release/secrets contracts: PASS
unit: PASS
compose: NOT RUN LOCALLY; production UI unchanged
golden: NOT_YET_MEASURED
benchmark: NOT_YET_MEASURED
lint: PASS — lintStandaloneDebug
assemble standalone: PASS — assembleStandaloneDebug
assemble home: PASS — assembleHomeDebug
GitHub Actions run: 33850770612
GitHub Actions: PASS
Build: PASS
API 34: PASS
API 36: PASS
```

本地 `./gradlew --no-daemon test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug --stacktrace` 完成 715 个可执行任务并 `BUILD SUCCESSFUL`。Run `33850770612` 是初始 Stage 0 提交的 hosted evidence。纠偏提交的 hosted CI 结果在推送后由最终交付回报；若失败，本 Stage 保持不通过。

## Hardware

```text
API 34 emulator: PASS — GitHub Actions run 33850770612
API 36 emulator: PASS — GitHub Actions run 33850770612
Samsung square: UNVERIFIED_HARDWARE
refresh rate: NOT_YET_MEASURED
physical WP8 reference device: NOT_YET_MEASURED
vessel: UNVERIFIED_HARDWARE
```

## English translation

This Stage 0 correction starts from `98121412893d5331b22d4327463794993a4a4eff` and remains pending human review. It versions the Master, locks the reviewed and actual baselines, reconciles every pre-existing shell-engine artifact, replaces the flattened evidence schema with state-aware content-addressed captures and scenario measurements, adds signed human review, and runs positive and negative fixtures through a real Draft 2020-12 validator. It changes no production module, UI, Registry, map adapter, feature, engine behavior, or marine function. Hosted run `33850770612` passed build, API 34, and API 36; real WP8 evidence, Golden results, refresh-rate data, and Samsung square hardware remain unmeasured or unverified.

## Stop

Stage 0 remains `PENDING_HUMAN_REVIEW`. Stage 1 and every later Stage are stopped.

STOPPED AT STAGE GATE.
AWAITING HUMAN REVIEW BEFORE NEXT STAGE.
