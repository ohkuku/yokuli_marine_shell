# WP8 sizes and chart workbench correction

Baseline: `8a30f55a879f39da072cfd76eecbf9ec317fc8a9`
Branch: `codex/shell-map-contract`
Authorization: owner requested implementation directly on this branch; no merge or release approval.

## Scope

- Restore the classic width-by-height WP8 sizes: 1x1, 2x2, 4x2. Chart supports all three; Settings supports small and medium. Decode retired sizes without erasing tile IDs or order.
- Preserve the existing main-screen drag, resize, floating controls, and app-owned installation contract.
- Complete the chart workbench's actual user operations rather than adding demo surfaces: selection, places, measuring, editable route drafts and saved routes, local charts, and responsive map tools.
- Preserve offline/provider-free truth boundaries. No NMEA publishing, fake position, automatic route safety claim, or default Android launcher.

## Execution and evidence

Inspect the current source and existing tests first. Add behavioral regressions for the reported failures, then implement and run focused JVM tests and hosted Android compilation/device tests. The temporary source-snapshot workflow only exports committed source to an artifact so this session can inspect and test a consistent revision; it grants no write permission and will be removed before completion.

Status: IN_PROGRESS. No tests or human-device verification claimed yet.
