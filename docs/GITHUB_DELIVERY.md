# Yokuli OS GitHub Delivery

## 中文（主文）

本文是 PR、CI 制品、失败诊断和发布的操作合同。工作流借鉴成熟的旧 Boat Watch 仓库，但只声明 Yokuli OS 当前实际存在的能力。

### 工作流

| 工作流 | 触发 | 必须证明 | 输出 |
|---|---|---|---|
| `android.yml` | PR、`main`／`codex/**` push、手动 | CI helper、版本/拓扑合同、JVM 测试、lint、双 debug APK、API 34 完整故事、API 36 reduced-motion smoke | 报告、candidate／`UNVERIFIED`、全部门禁后的 `VERIFIED` APK |
| `nightly.yml` | 周二/周五、手动 | JVM 回归及 API 34/36 全 UI 故事 | 30 天兼容性报告或失败证据 |
| `release.yml` | 语义 tag、手动 | metadata、签名预检、API 36 UI 合同、测试、lint、APK/AAB 签名校验 | 90 天签名制品和不可覆盖 GitHub Release |

旧 runtime soak 在 Anchor/NMEA/backup runtime 尚未迁入前不得复制并冒充覆盖；届时再恢复逻辑时钟、故障注入和 wall-clock soak。

### GitHub 反馈与制品可信度

每个质量边界必须是独立命名 job；job summary 汇总结果；失败以 `::error` 注解；HTML/XML、Gradle 设备日志和有限范围 `FAILURE-*` 包可下载。build 中的 `continue-on-error` 只用于收集全部证据，最后的 enforce step 必须使任一失败门禁导致 job 失败。

- `yokuli-os-debug-candidate-*`：JVM/lint/build 已过，设备门禁未完。
- `UNVERIFIED-yokuli-os-debug-*`：仅供诊断，至少一个质量门禁失败。
- `VERIFIED-yokuli-os-debug-*`：build、API 34 和 API 36 均通过。
- `VERIFIED-yokuli-os-vX.Y.Z-signed`：已校验签名的 APK/AAB 与 checksums。

`main` 分支应要求 build、API 34 stories、API 36 smoke 三个 check；不要在 PR 要求只对 push/manual 运行的 verified artifact job。

### 发布

普通 PR 在没有地图 secret 时仍可构建，并明确使用 fixture Chart surface。要让 push/manual CI 产出的 APK 使用真实 Google Maps，进入仓库 **Settings → Secrets and variables → Actions → New repository secret**，新增 `GOOGLE_MAPS_ANDROID_API_KEY`。个人加密 vault 的密文可以提交到 GitHub，但 Actions 不持有主口令、不会解密它，也不会自动把密文变成 Actions Secret。

签名发布需要 `GOOGLE_MAPS_ANDROID_API_KEY`，以及同一签名库产生的四个 secret：`ANDROID_SIGNING_KEY_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。preflight 必须先确认地图 key 非空，再实际打开 keystore 并恢复私钥。诊断收集使用窄 allow-list，不能包含构建配置、环境转储、地图 key 或签名材料。

支持 `v1.2.3-alpha.1`、`v1.2.3-beta.1`、`v1.2.3`。alpha 可以来自 `codex/*` 或 `main`，beta 来自 `codex/release/*` 或 `main`，stable 只能来自 `main`。已有 tag 必须指向当前提交；已有 Release 不覆盖，必须使用新版本。

本地更改 CI 前运行：

```text
python3 -m pip install --requirement .github/requirements/stage0-schema.txt
python3 -m unittest discover .github/scripts 'test_*.py'
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
| `android.yml` | PR, `main`/`codex/**` push, manual | CI helper, release-metadata and workflow contracts; JVM tests; lint; two debug variants; API 34 full stories; API 36 reduced-motion smoke | reports; candidate/`UNVERIFIED`; post-gate `VERIFIED` APKs |
| `nightly.yml` | Tue/Fri schedule, manual | JVM regression plus all UI stories on API 34 and 36 | 30-day compatibility reports/failure evidence |
| `release.yml` | semantic tag, manual | metadata/topology, signing preflight, API 36 UI contract, tests, lint, signed APK/AAB verification | 90-day signed artifact and immutable GitHub Release |

The old runtime soak suite is intentionally not copied yet. It depended on Anchor/NMEA/backup runtime modules that are not present in this clean-slate repository. `nightly.yml` provides real compatibility coverage without reporting fictional marine-runtime verification. Restore logical-time, fault-injection, and wall-clock soak jobs when those runtime modules land.

## Feedback in GitHub

The workflows provide feedback at four levels:

1. Check status: each quality boundary is a separate named job, so branch protection can require it.
2. Job summary: `.github/scripts/write_job_summary.py` writes gate outcomes and aggregate JUnit counts to `GITHUB_STEP_SUMMARY`.
3. Annotation: failed gates and parsed instrumented failures emit `::error` annotations visible in the run and PR checks.
4. Evidence: HTML/XML reports, captured Gradle device log, and a bounded `FAILURE-*` bundle remain downloadable.

The build job uses `continue-on-error` only to gather all independent results and publish evidence. CI helper tests, release metadata, workflow topology, JVM tests, lint, and assembly have separate step IDs and summary rows. Its final `Enforce build quality gate` step fails the job if any required result is not `success`.

Artifacts carry trust in their name:

- `yokuli-os-debug-candidate-*`: JVM/lint/build passed; device gates still pending.
- `UNVERIFIED-yokuli-os-debug-*`: installable diagnostic only; a quality gate failed.
- `VERIFIED-yokuli-os-debug-*`: build, API 34 stories, and API 36 smoke all passed.
- `VERIFIED-yokuli-os-vX.Y.Z-signed`: signature-checked release APK/AAB assets plus checksums.

GitHub supports job summaries through `GITHUB_STEP_SUMMARY`, workflow commands such as `::error`, and artifacts for transferring outputs between jobs and retaining test evidence. See [workflow commands](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands) and [workflow artifacts](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflow-artifacts).

## Branch protection

For `main`, require these checks before merge:

```text
TDD contract, unit, lint, and dual APKs
WP8 shell stories on API 34
Android 16 / API 36 reduced-motion smoke
```

Do not require `Publish fully verified debug APKs` on pull requests; it intentionally runs only for push/manual events.

## Release secrets

Configure the Maps key as a repository Actions secret if push/manual artifacts must use the real Google provider. Pull requests without it deliberately compile the fixture fallback. Committing the encrypted personal vault does not expose or decrypt it in Actions:

```text
GOOGLE_MAPS_ANDROID_API_KEY
```

Releases require that Maps secret plus these four signing secrets, which must come from the same local signing vault:

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

Supported tags:

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
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
./gradlew assembleStandaloneRelease
bash .github/scripts/test-release-product-surface.sh
```

The contract uses the pinned `jsonschema` package as a real Draft 2020-12 validator for Stage 0 fixtures. Stage 1 additionally assembles the standalone release APK and uses `apkanalyzer` to require Chart/Settings code while rejecting Shell Lab from the release binary. It also verifies current action majors, gate dependencies, emulator/KVM wrappers, explicit verified/unverified labels, summaries, failure annotations, bounded diagnostic upload pairs, release signature/checksum commands, and the nightly schedule.

Current upstream choices follow the official projects: [Gradle setup action v6](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md), [checkout v6](https://github.com/actions/checkout), [setup-java v5](https://github.com/actions/setup-java), [upload-artifact v7](https://github.com/actions/upload-artifact), [download-artifact v8](https://github.com/actions/download-artifact), and [Android Emulator Runner v2.38.0](https://github.com/ReactiveCircus/android-emulator-runner/releases/tag/v2.38.0).
