# Yokuli OS TDD Log

## Slice 1 — Simplified launcher architecture

### Red

Command:

```text
./gradlew :core:shell:testDebugUnitTest
```

Observed failures before implementation:

```text
defaultLayoutContainsFiveTiles FAILED
defaultLayoutRejectsOverlap FAILED
shortcutsOpenTheirOwningWorkspace FAILED
coreAppRegistryContainsExactlyFourApps FAILED
```

`launcherEntryIdsAreUnique` already passed against the empty registry. The failures were assertion-level behavior failures, not compilation or environment failures.

### Green target

- Exactly four core apps: Chart, Cockpit, Library, System.
- Exactly five default pinned entries: Chart, Anchor, Cockpit, Library, System.
- Anchor, Navigation and Survey target typed Chart modes.
- Trips target Library; Data Sources target System.
- Invalid overlapping layouts are rejected.

Green result: `BUILD SUCCESSFUL` with all five architecture tests passing.

## Slice 2 — WP8 Shell navigation

### Red

Command:

```text
./gradlew :core:shell:testDebugUnitTest --tests '*ShellNavigatorTest'
```

All four intended behaviors failed before implementation:

```text
anchorShortcutOpensChartTaskInAnchorMode FAILED
chartModesReuseOneChartUiTask FAILED
homeReturnsStartWithoutClosingUiTasks FAILED
allAppsAndBackFollowWindowsPhoneSemantics FAILED
```

### Green target

- Deep links open their owning core app task.
- Chart modes reuse one Chart UI task.
- Home returns to Start without deleting UI tasks.
- Back from All Apps returns to Start.

Green result: `BUILD SUCCESSFUL` with all four navigation tests passing.

## Slice 3 — Editable four-column Start grid

### Red

Command:

```text
./gradlew :core:shell:testDebugUnitTest --tests '*DesktopLayoutEditorTest'
```

All four behavior tests failed against the inert editor seam:

```text
resizeCyclesOnlyThroughSizesSupportedByTheEntryAndReflows FAILED
unpinRemovesOnlyTheSelectedTile FAILED
pinAddsAnAllAppsShortcutAtTheNextFreeGridPosition FAILED
movingATileBeforeAnotherReflowsWithoutOverlap FAILED
```

### Green

- Resize follows each entry's supported-size cycle.
- Unpin removes only the selected tile.
- All Apps can pin a shortcut at the next valid grid position.
- Move, resize, pin and unpin always repack without overlap.

Green result: `BUILD SUCCESSFUL`; all editor and existing shell unit tests pass.

## Slice 4 — Real ShellActivity stories and square layout

### Red

The first instrumented run failed all three stories because the UI had no stable test semantics:

```text
anchorTileOpensSharedChartInAnchorModeAndHomeReturnsToStart FAILED
allAppsEntryOpensAlphabeticalCoreAppsAndShortcuts FAILED
square320StartScreenKeepsChartVisibleAndSystemReachable FAILED
```

After those stories were green, the All Apps pinning extension failed at `tile-anchorages`, proving that the long-press path was still inert.

### Green

Command:

```text
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

Four real-activity Compose stories now pass:

- Anchor tile opens `ChartMode.ANCHOR`, and Home returns to Start.
- All Apps contains core apps and shortcuts and can pin Anchorages.
- A constrained 320×320 dp Start Screen keeps Chart visible and System reachable by vertical scroll.
- Edit mode moves, resizes and unpins a tile without opening its app.

The test uses an API 34 emulator. This is `VERIFIED_DEVICE_EMULATOR`, not real Samsung hardware or vessel verification.

## Current verification gate

```text
./gradlew test assembleStandaloneDebug assembleHomeDebug lintStandaloneDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

Real GNSS, NMEA, Anchor runtime, alerts and vessel behavior remain intentionally unconnected in this UI slice.

## Slice 5 — WP8 motion policy and reusable page contract

Requirement source: [`docs/requirements/WP8_UI_SYSTEM_REQUIREMENTS.md`](requirements/WP8_UI_SYSTEM_REQUIREMENTS.md), contracts `UI-001` through `UI-008`.

### Red

Command:

```text
./gradlew :core:design:testDebugUnitTest --tests '*WpMotionPolicyTest'
```

Observed result: five tests compiled and ran; four failed against the inert policy seam and the safety-immediate default passed.

```text
siblingSurfacesUseDirectionalHorizontalSlides FAILED
deeperNavigationUsesPerspectiveTurnstileWithInverseBackMotion FAILED
transientUiUsesSwivel FAILED
reducedMotionPreservesContextWithoutPerspective FAILED
safetyCriticalPresentationIsImmediate PASSED
```

The failures were assertion-level behavior failures, not missing dependencies, compilation errors, or emulator failures.

### Green target

- Start ↔ All Apps uses directional sibling slide.
- App enter/return uses inverse 3D Turnstile plans.
- Transient UI uses Swivel.
- Reduced motion becomes a short non-perspective fade.
- Safety-critical presentation remains immediate.
- Every core app uses the reusable upper-left application title and fixed Application Bar pattern.
- Tiles and actions retain semantic click behavior while adding pointer-position tilt feedback.

### Green result

```text
./gradlew :core:design:testDebugUnitTest --tests '*WpMotionPolicyTest'
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

- All five deterministic motion-policy tests pass.
- All five real `ShellActivity` stories pass on the local API 34 emulator, including the four-core-app title contract.
- Visual inspection confirms the `chart / ANCHOR` hierarchy, chart workspace, and outlined five-action Application Bar after motion settles.
- The tests prove state, navigation, and semantic end conditions; they do not constitute frame-perfect compositor or physical-device verification.

## Slice 6 — GitHub build, test, compatibility, and signed release delivery

Requirement source: `UI-009` and `UI-010`.

### Red

Commands:

```text
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-resolve-release-metadata.sh
```

Observed failures before the production scripts/workflows were added:

```text
CI contract failed: missing .github/workflows/release.yml
resolve_release_metadata.sh: No such file or directory
```

These failures prove that the existing single workflow does not yet provide the required gate topology or release metadata boundary.

### Green target

- Independent unit, lint, and dual-variant build results with an explicit final gate.
- API 34 real-Activity stories plus API 36 compatibility smoke.
- Candidate/`UNVERIFIED` artifacts before gates and a `VERIFIED` debug package only after all gates.
- Nightly API matrix without claiming the absent marine-runtime soak behavior.
- Deterministic semantic version metadata and signed standalone/HOME APK/AAB release assets.
- GitHub job summaries, annotations, reports, and bounded secret-free failure bundles.

### Green result

```text
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
```

Results: three Python helper tests pass; release metadata cases pass; the workflow topology/feedback/signing contract passes; all workflow and Issue Form YAML parses locally.

A temporary two-day local JKS test key was used to exercise the production Gradle signing boundary:

```text
assembleStandaloneRelease assembleHomeRelease bundleStandaloneRelease bundleHomeRelease
```

Both APKs passed `apksigner verify` using APK Signature Scheme v2, and both AABs passed `jarsigner -verify`. This proves the build/signature plumbing with disposable local material; repository-secret availability and GitHub-hosted execution remain pending until the pushed Actions run.

## Current verification gate — WP8 UI and delivery slice

```text
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
```

- JVM/unit: PASS.
- Android lint: PASS.
- standalone/HOME debug builds: PASS.
- API 34 real-Activity stories: 5/5 PASS.
- local disposable-key standalone/HOME APK/AAB signing and signature checks: PASS.
- GitHub-hosted API 34/API 36 jobs: NOT RUN until push.
- Samsung square hardware: `UNVERIFIED_HARDWARE`.
- Vessel/GNSS/NMEA/Anchor runtime: `UNVERIFIED_VESSEL` and deliberately not connected.

## Slice 7 — Remote CI feedback correction

### Red

GitHub Actions run [#1](https://github.com/ohkuku/yokuli_marine_shell/actions/runs/33759444919) correctly refused to publish a verified artifact: the combined CI self-test step had a failed outcome while unit, lint, and both debug builds passed. It published only `UNVERIFIED-*`, build reports, and `FAILURE-build-*` evidence.

The old combined step did not expose which of its three commands had failed. A new workflow-contract assertion was added first:

```text
CI contract failed: build feedback must expose the ci_helpers gate independently
```

### Green target

- CI helper unit tests, release metadata, and workflow topology have independent step IDs.
- Candidate and verified artifacts require all three outcomes.
- Job summary, failure-bundle decision, and final enforcement name every outcome.

### Green result

```text
bash .github/scripts/test-ci-contract.sh
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
```

All three local contracts pass and the updated workflow YAML parses. The correction is not considered remotely green until its pushed Actions run identifies and clears every required gate.
