# Yokuli OS GitHub Delivery

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

Configure these repository Actions secrets. They must come from the same local signing vault:

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
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
```

The contract verifies current action majors, gate dependencies, emulator/KVM wrappers, explicit verified/unverified labels, summaries, failure annotations, bounded diagnostic upload pairs, release signature/checksum commands, and the nightly schedule.

Current upstream choices follow the official projects: [Gradle setup action v6](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md), [checkout v6](https://github.com/actions/checkout), [setup-java v5](https://github.com/actions/setup-java), [upload-artifact v7](https://github.com/actions/upload-artifact), [download-artifact v8](https://github.com/actions/download-artifact), and [Android Emulator Runner v2.38.0](https://github.com/ReactiveCircus/android-emulator-runner/releases/tag/v2.38.0).
