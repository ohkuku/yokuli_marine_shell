# Shell–App / Map TDD Log

## 2026-09-05 — Intake and audit

### User-visible failures reproduced from code

- `ResizeTile` creates `StartInteractionState.Resizing`; production UI then replaces resize with a checkmark and requires `CommitTileResize`.
- The 1×1 edit affordance has a 44 dp semantic target but only a 17 dp visible disk and 12–13 dp glyph.
- Drag handling exists only after the tile is already selected in edit mode, so the initial long-press gesture cannot reliably continue into the same drag.
- `MarineTileContent` lives in Desktop and switches on all sizes, so App code does not own size-specific content.
- `InstalledAppBinding` unifies registration tables, but visual definitions still live in `app-shell` and token mappings are manually duplicated.

### Evidence classification

- WP8 geometry and non-edit motion: `HUMAN_REVIEWED` Stage 2.5 evidence.
- WP8 edit timing and affordance dimensions: `NOT_OBSERVED`; Android values remain `DERIVED_UNVERIFIED`.
- Product requirement for immediate resize and continuous long-press drag: owner correction in this phase.

### Planned Red tests

- Catalog rejects missing/default/duplicate supported-size declarations.
- Visual registry rejects renderer-key mismatch and proves distinct size renderers are App-provided.
- Reducer proves one `ResizeTile` action commits/persists and never exposes a confirmation state.
- Reducer cycles only through the App-declared ordered sizes and suppresses resize for a one-size Entry.
- Activity proves one click changes size with no commit/cancel UI.
- Activity proves the same down/long-press/move/up sequence reorders and persists a tile.
- Binding graph proves catalog, tokens, visuals and hosts derive from one exact App set.

## 2026-09-05 — First Green implementation and device correction

- Focused Engine run: the four new resize tests failed against the old preview/confirm reducer, providing the expected Red evidence.
- Green implementation removes `Resizing` and `CommitTileResize`; one `ResizeTile` action now commits, persists, records undo, emits selection haptic, and remains in edit selection.
- App-owned Compose SPI and installation registry unit tests pass; Chart and Settings now provide exact size-specific renderers from their own feature modules.
- First API 34 Activity run intentionally failed all three new stories. Review found two incorrect test assumptions: wide and standard tiles share height, and the drag target crossed only one packed row. Assertions now compare width and cross two rows.
- The compact-tile unpin path is being retested with an actual pointer click on the unmerged child target, not a direct semantics action. It remains unverified until that device run passes.

## 2026-09-05 — Compact edit hit-test Red

- The corrected API 34 run passed same-gesture long-press reorder and direct one-tap resize.
- It exposed one real remaining defect: after resizing Settings from 1x1 to 2x1, the visible 48dp unpin control did not receive an injected pointer click even though its semantics action existed.
- The selected-tile drag detector had inferred edit-control bounds from its modifier-local `size`, while the controls are laid out from the tile's declared WP geometry inside a content inset. It now excludes the exact declared tile edge plus its inset from drag capture.
- The bounds-only correction stayed Red on API 34. Edit controls therefore own their pointer consumption and explicit accessibility click semantics; the selected tile owns only its move gesture. This removes pointer arbitration between resize/unpin and drag instead of relying on inferred geometry alone.
- The first isolated-control rerun stopped before exercising the control: Compose's `longClick()` helper duration sat on the device threshold. The story now uses the already-proven physical sequence (`down`, 650ms hold, `up`) used by the reorder test, so subsequent evidence isolates the edit control itself.
- Self-review then found a separate production defect: drop committed the Engine transaction but retained `localTileDrag`, leaving the reordered tile visually translated off-screen. Commit now clears the renderer-only drag offset before dispatching Drop.
- The reorder story remains the single physical long-press/drag/drop proof. The compact-control story enters edit through the exposed accessibility long-click action, then uses a real pointer click on the 48dp child; this prevents a duplicate long-press precondition from masking the control result.
- Engine order and selected edit state were true before Compose rendered the relocated control, so the reorder story now waits for the post-drop UI instead of asserting synchronously. The control-only story establishes `EnterStartEdit` directly through the Engine and keeps its user-level pointer click as the behavior under test.
- Custom pointer plus explicit accessibility semantics caused Compose's `assertIsDisplayed` helper to remain false even after the Engine state and node were present. The control proof now checks placement existence, exact pixel bounds, viewport containment, and a real pointer outcome rather than treating that helper as the sole visibility oracle.
- Both stories now explicitly require `EditIdle(selectedTile=tile-settings)` before asking the renderer for controls. The first diagnostic accidentally compared a Tile instance ID with an invented Entry-like value (`settings-primary`); correcting that mix-up reinforces the App/Entry/Tile identity boundary the installation contract is meant to preserve.
- Engine selection then passed while the default merged semantics tree could not find the controls. The custom input semantics had been placed inside `testTag`; the control now owns a merge-descendants accessibility node and the tag lives inside it, restoring one shared target for automation, accessibility, and pointer input.
- With the unified node, physical long-press reorder passes. The remaining compact-control failure clicked during the 1x1-to-2x1 recompose window, using the old top-left unpin coordinate while the new control belongs at top-right. The story now waits for wide geometry and right-half control placement before injecting the user tap.
- Even after stable right-side placement, a physical click stayed Red: parent drag, parent clickable, and child pointer recognizers were competing. Shell-owned edit controls now route through the tile's single pointer stream; that stream consumes a completed tap in the exact unpin/resize region, while the control node retains one explicit accessibility click action. There is no second child pointer recognizer to race it.
- The first single-owner run exposed an accessibility correctness bug: invoking the node tagged Resize unpinned Settings. The selected Tile's clickable semantics had merged both edit descendants and crossed their actions. A selected editing Tile no longer registers its normal open/select clickable; its edit controls stay independent semantic actions while the one tile gesture stream continues to own touch.
- Resize still resolved as Unpin because a higher clickable ancestor wrapped the entire spatial grid to implement blank-area exit and merged descendants. Blank exit is now a same-size background sibling behind `WpSpatialStartLayout`; tiles and edit controls are no longer descendants of that clickable semantics node.
- The unmerged tree proved the final cause: independent 48dp controls still overlapped heavily because the whole Tile, including its edit overlay, had been inset for App content. Only the App renderer is now inset; edit controls occupy the full Tile corners. Any residual 1x1 overlap is resolved by nearest control center (and compact horizontal tiles by nearest vertical half), never a hard-coded Unpin priority.
- Compact edit geometry/touch and both direct resize steps pass together. The drag story was intermittent because one synthetic jump did not model the continuous path through insertion hysteresis; it now emits an intermediate row crossing before the final three-row target, without weakening the production threshold.
- Joint regression showed the drag still cancelled at the long-press state transition. Conditionally removing the Tile clickable changed the modifier chain while the same pointer was still down, cancelling the coroutine before move/drop. The clickable node is structurally stable again; the tile gesture owner consumes edit-control taps, and full-corner nearest-center routing prevents their prior overlap error.
