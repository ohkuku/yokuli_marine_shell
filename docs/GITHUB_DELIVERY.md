# Yokuli OS GitHub Delivery

## 中文（主文）

本文是 PR、CI 制品、失败诊断和发布的操作合同。工作流借鉴成熟的旧 Boat Watch 仓库，但只声明 Yokuli OS 当前实际存在的能力。

### 工作流

| 工作流 | 触发 | 必须证明 | 输出 |
|---|---|---|---|
| `android.yml` | PR、`main`／`codex/**` push、手动 | 显式 core/adapter 门禁、lint、standalone debug/release audit、API 34 runtime/MBTiles/迁移/恢复、API 36 宿主安全、冷/热启动信号 | 统一 Codex 报告、raw reports、`PRODUCT-RECOVERY` candidate 与 `HUMAN-ACCEPTANCE-PENDING` APK |
| `nightly.yml` | 周二/周五、手动 | 受保护的 domain/runtime/storage 回归及 API 34/36 adapter stories | 30 天兼容性报告或失败证据 |
| `release.yml` | 语义 tag、手动 | Product Recovery Window 内暂停 | 不发布签名产品 Release |

Chart、Chart Library、Navigation 和 Data visualization 在人工批准前，旧 presentation tests 不具有产品权威性。domain/math、NMEA、source selection、persistence/migration、MBTiles 读取、runtime lifecycle、并发和安全测试仍是硬门禁。详见 `docs/phases/base-apps-human-reset/PRODUCT_RECOVERY_WINDOW.md`。

### GitHub 反馈与制品可信度

每个质量边界必须是独立命名 job；job summary 汇总结果；失败以 `::error` 注解；HTML/XML、Gradle 设备日志和有限范围 `FAILURE-*` 包可下载。build 中的 `continue-on-error` 只用于收集全部证据，最后的 enforce step 必须使任一失败门禁导致 job 失败。

- `PRODUCT-RECOVERY-yokuli-os-debug-*`：受保护的单元测试、lint 与编译通过，供后续设备门禁传递。
- `HUMAN-ACCEPTANCE-PENDING-yokuli-os-*`：全部当前机器门禁通过，但明确尚未获得产品人工批准。
- Product Recovery Window 内禁止生成名称包含 `VERIFIED` 的产品制品。

### CI-first Codex 返工报告

每次 `codex/**` push 后，无论门禁成功、失败或部分 job 被依赖关系跳过，`codex-report` job 都尝试上传一个提交绑定的制品：

```text
CODEX-CI-REPORT-<sha12>-<run_id>-<attempt>
```

这是继续同一个实现工作的默认唯一输入。下载并交回整个 artifact（GitHub 会以 zip 下载），不要只复制网页日志。Codex 先核对 `manifest.json.headSha`，然后读取 `CODEX_REPORT.md` 定点返工。只有报告出现 `EXTRA_ARTIFACT_REQUIRED` 时，才额外下载它明确点名的 raw artifact。

四个原始报告仍可独立下载：

- `yokuli-os-build-reports-<full sha>`
- `yokuli-os-api34-reports-<full sha>`
- `yokuli-os-api36-reports-<full sha>`
- `stage11-performance-reports-<full sha>`

统一报告及 per-job Codex 报告保留 30 天；常规 unit/lint/API raw reports 保留 14 天；启动性能 trace 和 human-acceptance-pending APK 保留 30 天。捕获器不记录命令参数或环境变量，汇总器也不会读取 `local.properties`、Gradle properties、签名材料或个人 vault。

`main` 分支应要求 build、API 34 stories、API 36 smoke 三个 check；不要在 PR 要求只对 push/manual 运行的 verified artifact job。

### 发布

所有构建都会读取 Repository Secret `GOOGLE_MAPS_ANDROID_API_KEY` 并通过 Android Manifest 注入。GitHub 不向不受信任 PR 提供 secret，因此 PR
允许 keyless 构建并只验证本地 MapLibre 链路；`codex/**`/`main` push 与手动分发构建若没有同时在 BuildConfig 和 merged manifest 看到非占位
key，配置 Gate 会失败且不发布 recovery APK。这个证据仍只表示配置已注入，不证明 Google API 授权、账单、包名/签名限制、网络或图块加载成功。
个人加密 vault 的密文可以提交到 GitHub，但 Actions 不持有主口令、不会解密它，也不会自动把密文变成 Actions Secret。

签名发布只需要同一签名库产生的四个 secret：`ANDROID_SIGNING_KEY_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。preflight 必须实际打开 keystore 并恢复私钥。诊断收集使用窄 allow-list，不能包含构建配置、环境转储或签名材料。

语义 tag 规则保留，但签名发布在 Product Recovery Window 内暂停。恢复发布必须先有仓库所有者明确的人工作品批准，再解除 workflow 中的恢复期阻断。

本地更改 CI 前运行：

```text
python3 -m pip install --requirement .github/requirements/stage0-schema.txt
python3 .github/scripts/test_product_recovery_window.py
bash .github/scripts/run_ci_helper_tests.sh
python3 .github/scripts/test_launcher_stage2_contract.py
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
./gradlew assembleStandaloneRelease
bash .github/scripts/test-release-product-surface.sh
```

## English translation

This document is the operating contract for pull requests, CI artifacts, diagnostics, and releases. The workflows are adapted from the mature Boat Watch repository, but their claims match the code that exists in Yokuli OS today.

## Workflow map

| Workflow | Trigger | Required proof | Output |
|---|---|---|---|
| `android.yml` | PR, `main`/`codex/**` push, manual | explicit core/adapter gates, lint, Debug/Release audit, API 34 runtime/MBTiles/migration/restore, API 36 host safety, startup signal | unified Codex report, raw reports, `PRODUCT-RECOVERY` candidate and `HUMAN-ACCEPTANCE-PENDING` APK |
| `nightly.yml` | Tue/Fri schedule, manual | protected domain/runtime/storage regression and adapter stories on API 34/36 | 30-day compatibility reports/failure evidence |
| `release.yml` | semantic tag, manual | suspended during Product Recovery | no signed product release |

Until human approval, old presentation tests for Chart, Chart Library, Navigation and Data visualization are not product-authoritative. Domain/math, NMEA, source selection, persistence/migration, MBTiles reading, lifecycle, concurrency and safety remain hard gates.

## Feedback in GitHub

The workflows provide feedback at four levels:

1. Check status: each quality boundary is a separate named job, so branch protection can require it.
2. Job summary: `.github/scripts/write_job_summary.py` writes gate outcomes and aggregate JUnit counts to `GITHUB_STEP_SUMMARY`.
3. Annotation: failed gates and parsed instrumented failures emit `::error` annotations visible in the run and PR checks.
4. Evidence: HTML/XML reports, captured Gradle device log, and a bounded `FAILURE-*` bundle remain downloadable.

The build job uses `continue-on-error` only to gather all independent results and publish evidence. CI helper tests, release metadata, workflow topology, JVM tests, lint, and assembly have separate step IDs and summary rows. Its final `Enforce build quality gate` step fails the job if any required result is not `success`.

Artifacts carry trust in their name:

- `PRODUCT-RECOVERY-yokuli-os-debug-*`: protected tests, lint and assembly passed; used to transfer the candidate.
- `HUMAN-ACCEPTANCE-PENDING-yokuli-os-*`: current machine gates passed, but product acceptance is explicitly pending.
- No product artifact may use `VERIFIED` during this recovery window.

### CI-first Codex repair artifact

Every `codex/**` push produces, on both success and failure, one commit-bound artifact named `CODEX-CI-REPORT-<sha12>-<run_id>-<attempt>`. Return that complete download to Codex for the next repair round. Codex first verifies `manifest.json.headSha`, then follows `CODEX_REPORT.md`; raw artifacts are needed only when the report explicitly names one with `EXTRA_ARTIFACT_REQUIRED`.

The original `yokuli-os-build-reports-<full sha>`, `yokuli-os-api34-reports-<full sha>`, `yokuli-os-api36-reports-<full sha>`, and `stage11-performance-reports-<full sha>` remain available. The bounded unified/per-job reports are retained for 30 days. Capture metadata excludes command arguments and environment variables, and the report allow-list excludes local/Gradle properties, signing material, and the personal vault.

GitHub supports job summaries through `GITHUB_STEP_SUMMARY`, workflow commands such as `::error`, and artifacts for transferring outputs between jobs and retaining test evidence. See [workflow commands](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands) and [workflow artifacts](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflow-artifacts).

## Branch protection

For `main`, require these checks before merge:

```text
TDD contract, unit, lint, and standalone in-app Shell APKs
WP8 shell stories on API 34
Android 16 / API 36 reduced-motion smoke
```

Do not require `Publish automation-passed Product Recovery APK` on pull requests; it intentionally runs only for push/manual events and never represents human product acceptance.

## Release secrets

The offline MapLibre path reads local raster charts without a map secret. Untrusted pull requests remain allowed to build keyless because GitHub withholds secrets. Trusted push/manual builds must inject a non-placeholder `GOOGLE_MAPS_ANDROID_API_KEY` into both BuildConfig and the merged manifest before a recovery APK can be published. This proves configuration only; API authorization, billing, package/signature restrictions, network access, and real tile loading remain a separate physical acceptance item. Actions never decrypts the personal vault. Signed releases are suspended during Product Recovery; when restored they require these four secrets from the same local signing vault:

```text
ANDROID_SIGNING_KEY_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Create the base64 value locally without adding the keystore to Git:

```text
base64 < yokuli-os-release.jks | tr -d '\n'
```

The preflight opens the keystore and recovers the private key before allocating a release build. The publish job passes the decoded runner-temp path into Gradle; the diagnostics collector uses a narrow allow-list that excludes build configuration, environment dumps, and signing material.

## Version and channel policy

The semantic tag rules are retained, but signed publishing is suspended during Product Recovery. Restoring release publication requires explicit repository-owner product acceptance before removing the workflow block. The retained tag shapes are:

```text
v1.2.3-alpha.1
v1.2.3-beta.1
v1.2.3
```

`resolve_release_metadata.sh` derives a deterministic Android version code where alpha < beta < stable for the same semantic version.

| Channel | Allowed source |
|---|---|
| alpha | `codex/*` or `main` |
| beta | `codex/release/*` or `main` |
| stable | `main` only |

Existing tags must resolve to the current commit. Existing GitHub Releases are not overwritten or uploaded with `--clobber`; a duplicate is a failure that needs an intentional new version.

## Local workflow contract

Run this before changing CI or release files:

```text
python3 -m pip install --requirement .github/requirements/stage0-schema.txt
python3 .github/scripts/test_product_recovery_window.py
bash .github/scripts/run_ci_helper_tests.sh
python3 .github/scripts/test_launcher_stage2_contract.py
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
./gradlew assembleStandaloneRelease
bash .github/scripts/test-release-product-surface.sh
```

The contract uses the pinned `jsonschema` package as a real Draft 2020-12 validator for Stage 0 fixtures. The current product model assembles only the standalone in-app Shell APK/AAB and uses `apkanalyzer` to require Chart/Settings code, reject Shell Lab, and reject Android HOME/DEFAULT registration. Stage 2 independently enforces the pure Kotlin engine boundary, opaque contracts, contribution composition, and host adapters. CI also verifies current action majors, gate dependencies, emulator/KVM wrappers, explicit verified/unverified labels, summaries, failure annotations, bounded diagnostic upload pairs, release signature/checksum commands, and the nightly schedule.

Current upstream choices follow the official projects: [Gradle setup action v6](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md), [checkout v6](https://github.com/actions/checkout), [setup-java v5](https://github.com/actions/setup-java), [upload-artifact v7](https://github.com/actions/upload-artifact), [download-artifact v8](https://github.com/actions/download-artifact), and [Android Emulator Runner v2.38.0](https://github.com/ReactiveCircus/android-emulator-runner/releases/tag/v2.38.0).
