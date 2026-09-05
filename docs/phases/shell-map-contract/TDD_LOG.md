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
