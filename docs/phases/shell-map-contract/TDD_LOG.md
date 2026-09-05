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

