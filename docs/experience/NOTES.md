# Experience rebuild — 2026-09-09

The owner requested a new WP8 consumer experience based on marine_shell, with Anchor Watch as a source of proven map/NMEA behaviour. This branch starts from the current `codex/shell-map-contract` baseline. Original working directories were not edited.

## Implementation

- One compiled Android application, separated into Shell, Chart/Library, Navigation, Data/NMEA and shared Metro components by package. `StateFlow` publishes immutable vessel observations; UI screens never create transports.
- Native Google and MapLibre gestures own map navigation. A native overlay intercepts only a handle's initial DOWN and retains that gesture until UP/CANCEL. The Shell pager never wraps the map.
- MBTiles inspection verifies real raster samples and tile keys. Centre metadata is used only when a real corresponding tile exists. TMS rows are inverted once. Sparse levels use the correct ancestor crop, with a bounded memory tile cache and bounded loopback workers.
- A real Android 34 file-provider failure was reproduced: SAF access succeeded, but SQLite resolved `/proc/self/fd` to a storage path it could not open. A versioned, app-owned compatibility copy now bridges that failure. Imports are staged and inspected before installation. Originals are never edited or deleted.
- The library retains source URI/version provenance. Unknown or dishonest provider modification metadata is a remaining limitation; this prototype does not hash every large chart on each scan. Forgotten compatibility copies are retained, so storage reclamation is a follow-up.
- NMEA checksum/framing and per-field age rules follow the older app's approach, with strict coordinate validation. Invalid fix status clears the position; blank fields do not refresh an instrument's timestamp. Phone position is the only automatic upstream output; peer readings are never automatically returned to that same input socket.
- Socket ownership is scoped to connection generations. Sharing is bounded to eight clients, with bounded queues and echo memory. Data older than ten seconds is excluded from generated output. Process restart does not claim sessions are still connected or silently restart output.
- The original age vault, recipient and encrypted identity are byte-for-byte unchanged. Google key injection and signing environment variable names remain the same.

## Checks performed

- Java 17 / SDK 36 / existing Gradle wrapper: `assembleStandaloneDebug` succeeded.
- Installed and launched on the existing API 34 arm64 emulator; no AndroidRuntime crash during the checked flows.
- Actual horizontal gestures: Start to app list; Chart Library charts/folders pivot.
- SAF folder picker: granted a read-only test folder; scanned the existing repository's four-tile NOAA NCDS21 renderer fixture and displayed its real pixels. This sparse fixture is test data, not a complete or current navigation chart.
- A/B measurement: dragged A while B and map geography remained stationary; displayed distance changed from 66.11 nm to 92.81 nm.
- Temporary host TCP server: received and parsed RMC, DPT, MWV and HDT over the real emulator network, including 4.2 kn / 12.4 m / 11.8 kn synthetic values. These values are not built into the app.
- A real client connected through an ADB loopback forward to the app's sharing server and received checksum-bearing DBT/HDT/MWV and, after source selection, RMC.
- Paused the temporary input, allowed all observations to expire, and observed zero output on a fresh sharing client connection. A manually reviewed `$PTEST,YOKULI` console message arrived on the original TCP server with the generated checksum `*67`.
- Switched from English to Chinese in the running UI. Saved one mark and a three-point route through the real chart toolbar; after a force-stop/relaunch, the mark and route remained. Exported through Android's document picker and parsed the resulting GPX: one waypoint, one route, three route points.

## Captures

[Chinese Start](screenshots/start-zh.png) · [Chinese Settings](screenshots/settings-zh.png) · [Data receiving a synthetic network fixture](screenshots/data-fixture-en.png). These are emulator captures, not design mockups or built-in demo states.

This record deliberately distinguishes compilation and a few practical checks from comprehensive regression testing. Old CI gates remain disabled for this experience branch; the existing human-test APK job stays enabled. Real vessel/network/GNSS testing and WP8 motion/fidelity acceptance remain human review.

## Sources

- Existing `yokuli_marine_shell` map adapters, SAF reader, vault and build files.
- Existing Anchor Watch `OfflineMbTiles.kt`, `NmeaCore.kt`, and NMEA sharing implementation were inspected for compatibility and lifecycle lessons; the old app's business features were not copied wholesale.
- [MapLibre Android API/examples](https://maplibre.org/maplibre-native/android/examples/)
- [OpenStreetMap tile policy](https://operations.osmfoundation.org/policies/tiles/): visible attribution, named User-Agent, no prefetch/download feature; the renderer owns normal HTTP tile caching.
