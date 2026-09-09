# Marine capabilities inside Yokuli OS

This module carries the complete production source and resources of
`ohkuku/yokuli_nmea_anchor_alarm` (local reference HEAD
`a845d3d734d3b573a2b53952e66e5f800e944205`) into the existing Shell build.
It is an Android library, with no separate launcher or Application. The Shell
owns navigation, language, theme and the Activity-scoped `MainViewModel`.

| Shell destination | Existing functionality retained | Main implementation |
| --- | --- | --- |
| `anchor` | Known/estimated anchor centre, adjustable radius, pause/resume/lift, alarms/snooze, depth/wind guards, preflight/health, 24-hour trail, completed-watch analysis | `runtime/anchor`, `domain/anchor`, `ui/watch`, `ui/history` |
| `trip`, `instruments` | Start/pause/resume/end recording, optional calibrated phone attitude, NAV/sailing/motion/weather, named dashboards, discovered NMEA field binding and custom recording | `runtime/trip`, `data/trip`, `ui/watch/WatchWorkspaceScreen.kt` |
| `voyages` | Saved voyages, reports, coloured replay, events/waypoints, CSV/GPX/KML/KMZ/snapshot/AI ZIP exports | `domain/report`, `data/export`, `ui/history` |
| `anchorages` | Private saved anchorages, regions/collections, notes/photos/protection/visits, QR cards/scanning/import, approach guidance, map planning | `data/anchorage`, `ui/anchor/anchorages`, `ui/anchorage` |
| `nmea`, `output` | TCP/UDP input, parsing/checksums/raw health, independent phone-to-boat publishing and local TCP server, loop prevention, pressure/heading/attitude/ROT/derived-wind publication | `data/nmea`, `data/sharing`, `runtime/output`, `ui/data`, `ui/settings` |
| `sonar` | NMEA-server-matched position/depth sampling, personal depth maps, manual/automatic LINZ tide correction, offline corrected history and survey CSV | `data/sonar`, `data/tide`, `domain/sonar`, `map` |
| `sources` | Field provenance/freshness, heading selection, GNSS, phone IMU/barometer, mock-location proxy, explicit demo controls | `data/vessel`, `location`, `ui/data` |
| `marine-settings` | Vessel profile, sensor mounting/alignment, alarm sound/notifications, depth offsets, background settings, backup/restore, storage/support/privacy/export | `ui/settings`, `data/backup`, `data/diagnostics` |

Integration changes:

- `LegacyMarineScreen` embeds destinations under WP8 titles and swipe pivots;
  the common theme uses the Shell accent, light/dark colours, square controls
  and light type. The old Material bottom navigation is not used.
- One process owns input connections, recording and output. The old
  `AnchorForegroundService` and all its Hilt repositories are retained.
- `GpsDataSource.NONE` allows both position sources to be off. Selecting Phone
  starts location updates; turning it off releases the UI's request. NMEA
  position selection requires an existing transport. Arming still validates
  fresh, qualified positioning. Active watches retain their recorded source.
- The Vessel Data Hub follows that explicit position choice. Viewing live
  instruments no longer starts phone positioning implicitly.
- `LegacyMarineAlerts` is placed above the whole Shell, so alarms remain
  actionable while the user is in Chart or Start.
- The embedded back dispatcher allows the Shell's virtual Back button to close
  a nested settings/replay/anchorage page before leaving its application.
- All nine Google map surfaces use `SharedChartLayers`: the same folder layers
  and file-priority compositor as Chart. Legacy single-file import is hidden
  when embedded; its management button opens Chart Library.
- Notifications reopen the Shell launcher. The library does not declare an
  app icon, label, Google key, or launcher activity.
- API keys come from the Shell's existing environment/vault workflow. No old
  `local.properties`, credentials, build output, or test fixture is imported.

The copied business workflows are real implementations, not placeholder
states. A successful Android compilation validates integration, not physical
boat behaviour: alarm audibility/background survival, long-lived gateway I/O,
phone sensor mounting, QR camera use and tide providers still need the user's
manual device/boat acceptance. This iteration does not add a new test matrix.
