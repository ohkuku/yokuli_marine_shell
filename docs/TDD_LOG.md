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
