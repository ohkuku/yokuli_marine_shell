# NMEA_SOURCES P0 报告 / P0 Report

状态：`PASS`

起始 SHA：`69bfd4d0ed29f27450351df530b4a8b1e8e2c6a6`

基线候选提交：`aa5acd17f1f8025da603a04586d6d18eb44c849f`

## 结果

- 当前 Shell 与旧参考仓的真实 branch/SHA、dirty state、Gradle flavor、SDK 和接入符号已经 hash-bound。
- 旧仓 `codex/develop@a845d3d…` 全程只读；parser/freshness 测试思想可抽取，单连接 runtime、旧业务 selector、UDP framing、Anchor/Trip ownership 不迁移。
- 新 Phase 使用 `core:marine-data`、`adapter:marine-data-android`、`feature:nmea-input`、`feature:data-sources` 和现有 `app-shell` composition root。
- 最新产品边界仍为 portrait-only、沉浸式应用内 Shell、Back 不退出、无 Android HOME；两个新 App 后续进入 All Apps，但不自动固定到 Start。
- Android target 36 的 connected-device/location foreground-service 与前台权限边界已经记录；实机后台证据保持 `UNVERIFIED_PHYSICAL_DEVICE`。

## TDD 与 Gate

- 初始 P0 静态合同：`6/6 ERROR`，原因均为要求的 P0 文件不存在。
- Green 后 P0 合同：`6/6 PASS`。
- 首轮完整 Python：`229 PASS / 1 FAIL`，发现 TDD Playbook 丢失历史 Master 追溯入口；纠错后 `230/230 PASS`。
- `./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleStandaloneRelease`：`BUILD SUCCESSFUL`，1151 tasks。
- CI topology、release product surface、release metadata、secrets manager 与 `git diff --check`：全部 PASS。

P0 只锁定实施事实和边界，没有添加生产 socket、位置权限、Feature 或假数据。P1 可以开始纯领域 Red。

## English translation

P0 passed from the real Shell and reference commits. It records precise reuse, rewrite and non-migration decisions; a minimal four-module implementation boundary; target-36 lifecycle constraints; and the latest portrait-only, bounded-Back in-app Shell contract. A full regression found and corrected one lost historical documentation link. All 230 Python contracts, the 1151-task Gradle gate, CI topology, Release surface, metadata, secrets and diff checks passed. No production runtime or fixture was added in P0; physical-device behavior remains unverified.
