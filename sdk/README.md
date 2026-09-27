# Yokuli extension SDK 2

[中文 / English 开发者文档](../docs/developers/index.html)

SDK 2 gives `.ykl` applications canonical marine data, real device discovery, permission-scoped source/connection/sharing/recording controls, the shared monochrome UI library, namespaced storage and explicit Shell navigation. SDK 1 manifests remain supported. JavaScript and Kotlin/JS compile to the same `.ykl` format. Kotlin/JS is **not** native Kotlin APK or Compose plugin execution.

- `web/`: authoritative JavaScript API + Yokuli UI CSS; host serves them as `/_sdk/yokuli.js` and `/_sdk/yokuli.css`.
- `examples/departure`: real persisted pre-departure checklist.
- `examples/watch`: SDK 1 read-only overview using source-labelled vessel readings.
- `examples/helm`: SDK 2 boat controls using actual device/source/connection/sharing/voyage services and persistent command receipts.
- `templates/javascript`: no-build starter.
- `templates/kotlin-js`: Kotlin 2.1.20 typed external declarations, executable app and `packageYokuli` build task.
- `tools/package_extensions.py`: deterministic archive packaging and regeneration of bundled SDK/catalog/docs assets.

Package a JS directory:

```sh
python3 sdk/tools/package_extensions.py sdk/templates/javascript --output /tmp/myboat.ykl
```

Compile/package the Kotlin/JS starter from the repository root:

```sh
./gradlew -p sdk/templates/kotlin-js packageYokuli
```

Refresh the bundled catalog packages, shared SDK and offline docs after editing their sources:

```sh
python3 sdk/tools/package_extensions.py
```

Generated runtime assets belong to `app-shell/src/main/assets/extensions/`. Commit them together with their authoritative sources. This explicit packaging command does not add a CI job or modify existing build protection.

Each extension must declare its permissions, preserve timestamps, units and provenance, and release subscriptions when hidden. Extensions cannot register arbitrary data sources, open sockets, send arbitrary NMEA sentences, modify anchor/navigation sessions, run background alarms or load native code. SDK 2 controls call the existing Core services only, in the foreground and after explicit user actions. `system.services()` distinguishes declared/granted/available capabilities. Voyage commands persist their request ID and reconcile UNKNOWN results with the original receipt. Long-lived marine capabilities belong to the existing Marine Core.

The generated `docs/developers/yokuli-sdk-2.zip` is a downloadable SDK source bundle (with Gradle wrapper and pinned Kotlin/JS npm lock, without generated build caches or node_modules). App Center exports the same archive through Android document storage.

Current device backends are REAL only. Virtual clock, system replay, a scenario engine, third-party drivers/HAL and arbitrary background scripts are not implemented; this is not a complete virtual machine.

The static documentation is ready for GitHub Pages but publishing remains a separate repository operation. No deployed public URL is implied by this source tree.
