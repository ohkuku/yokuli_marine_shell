# Yokuli Launcher Engine TDD Log

状态：`STAGE_0_CORRECTION_PENDING_HUMAN_REVIEW`。当前日志从 Master Construction Spec 重新编号；旧 Slice 1–14 已保存在 [`archive/pre-launcher-engine/TDD_LOG_PRE_LAUNCHER_ENGINE.md`](archive/pre-launcher-engine/TDD_LOG_PRE_LAUNCHER_ENGINE.md)，只作历史证据。

## Stage 0 — Freeze & Reference Contract

### Baseline

```text
branch: codex/launcher-engine
starting SHA: ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7
master v1.0 attachment SHA-256: f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0
```

Master 附件记录的 reviewed SHA 是 `943d852`，但仓库所有者随后明确要求从最新提交开始。分支先从旧 SHA 创建，随后在没有 Stage 0 改动时纯 fast-forward 到 `ca84ef9`；没有 reset、丢提交或改写 Master 正文。

### Contract

Given `ca84ef9` 是冻结实现基线，When Stage 0 完成，Then 仓库必须逐字保存 Master、建立带 provenance 的 WP8 Reference Lab/schema、明确 screenshots/golden/artifacts 所有权，并让 CI 单独显示该合同；And 不得修改 UI、Registry、Google Map 或 Feature 行为。

旧 Phase 0A/S0–S2、Chart-source、WP8/UI ACTIVE 文档不再留在当前 requirements 目录；旧 TDD 证据归档而不删除，避免既丢历史又让旧编号继续指挥施工。

### Red

先增加 `.github/scripts/test_launcher_stage0_contract.py`，随后运行：

```text
python3 .github/scripts/test_launcher_stage0_contract.py
Ran 7 tests
FAILED (failures=10)
```

失败点精确对应：Master 尚未纳入、Reference Lab/schema 与三个 artifact 目录合同缺失、五份旧 requirement 仍活跃、归档索引和新 Stage 日志缺失、CI 没有命名 gate、TDD Playbook 仍列旧 Chart-first milestone。没有 Android 或环境失败。

### Green

- Master 附件逐字保存，SHA-256 为 `f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0`；
- Reference Lab 建立 provenance、measurement schema、screenshots、Golden 与 artifact 边界，均明确为 `NOT_YET_MEASURED`；
- 当前 requirements 只保留 Master 与不参与施工排序的 secrets supporting contract；
- 旧需求、旧 TDD 和旧 v0.1 实现截图进入 `pre-launcher-engine` 归档；
- Android CI 增加独立 `launcher_stage0_contract`，并参与候选包、失败诊断、Summary 与最终 Gate；
- 与 starting SHA 比较，生产源码和 Gradle 模块图没有变化。

实际 Gate：

```text
python3 .github/scripts/test_launcher_stage0_contract.py                         PASS (7/7)
python3 -m unittest discover .github/scripts 'test_*.py'                         PASS (33/33)
bash .github/scripts/test-ci-contract.sh                                         PASS
bash .github/scripts/test-resolve-release-metadata.sh                            PASS
bash .github/scripts/test-secrets-manager.sh                                     PASS
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug     PASS
git diff --name-only ca84ef9 -- app-shell/src core feature adapter settings.gradle.kts gradle
                                                                                PASS (no output)
```

Stage 0 没有运行 Golden、Macrobenchmark 或新硬件采集；它们是 `NOT_YET_MEASURED`。Hosted GitHub Actions run `33850770612` 随后重跑并通过 Build、API 34 与 API 36。Samsung 方屏仍为 `UNVERIFIED_HARDWARE`。

## Stage 0 correction — Reference and Baseline Contracts

### Baseline

```text
correction starting SHA: 98121412893d5331b22d4327463794993a4a4eff
actual selected Stage 0 starting SHA: ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7
master v1.0 SHA-256: f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0
master v1.1 SHA-256: b0aeb000012283d56cf7e8eb343e2a026366e150b2f9e3d9969f45ed12f4bb40
approval: PENDING_HUMAN_REVIEW
```

### Contract

Given 人工审查拒绝把初始 Stage 0 批准进入 Stage 1，When 纠偏完成，Then 必须锁定新旧基线与 Master 哈希、逐文件对账提前实现、让 schema 的三个状态拥有不同必填证据、用真实 Draft 2020-12 validator 验证正反 fixtures、在 Master 加入 Stage 2.5，并输出完整报告；And 不得改生产 UI、Registry、Maps、Feature、Shell Engine 或海事行为。

### Red

先更新 `.github/scripts/test_launcher_stage0_contract.py`，在 `/private/tmp` 隔离环境安装固定 `jsonschema==4.25.1` 后运行：

```text
Ran 10 tests
FAILED (failures=7, errors=1)
```

失败精确来自缺少 `BASELINE_LOCK.json`、`BASELINE_RECONCILIATION.md`、schema fixtures、正式 Stage report、CI validator 安装、Master v1.1/Stage 2.5 以及旧 Reference/TDD 合同；不是拼写、路径、Android 或功能回归。

### Green

状态相关 schema、内容寻址 capture、场景 measurement sets、direct-manipulation timeline、hash-bound review、七份正反 fixtures、Master v1.1/Stage 2.5、baseline lock/reconciliation 与正式报告完成。`WP8_REFERENCE_MEASUREMENTS.json` 仍不存在，Reference 状态保持 `NOT_YET_MEASURED`；fixture 中的合成值只验证 schema，不是产品测量。

实际本地 Gate：

```text
/private/tmp/yokuli-stage0-schema-venv/bin/python .github/scripts/test_launcher_stage0_contract.py
                                                                                PASS (10/10)
/private/tmp/yokuli-stage0-schema-venv/bin/python -m unittest discover .github/scripts 'test_*.py'
                                                                                PASS (36/36)
bash .github/scripts/test-ci-contract.sh                                         PASS
bash .github/scripts/test-resolve-release-metadata.sh                            PASS
bash .github/scripts/test-secrets-manager.sh                                     PASS
./gradlew --no-daemon test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug --stacktrace
                                                                                PASS (BUILD SUCCESSFUL; 715 tasks)
git diff --name-only 98121412893d5331b22d4327463794993a4a4eff -- app-shell/src core feature adapter settings.gradle.kts gradle
                                                                                PASS (no output)
```

Hosted run `33850770612` 的 Build、API 34 与 API 36 均为 PASS；纠偏提交推送后的 hosted CI 在最终交付中单独记录。Samsung 方屏仍为 `UNVERIFIED_HARDWARE`。

## English translation

Status is `STAGE_0_CORRECTION_PENDING_HUMAN_REVIEW`. The initial Stage 0 started from owner-selected commit `ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7`; hosted run `33850770612` passed build, API 34, and API 36. Human review then required a correction from `98121412893d5331b22d4327463794993a4a4eff`. The correction versions Master v1.1 while retaining the v1.0 hash, adds the mandatory Stage 2.5 reference approval gate, locks and reconciles the baseline, and replaces shallow field inspection with real Draft 2020-12 validation of positive and negative fixtures. No production source or module graph changes. Measurements, Goldens, refresh-rate data, and Samsung square hardware remain unmeasured or unverified; no later Stage has begun.
