# Yokuli OS

Yokuli OS combines a faithful Windows Phone 8-style shell with the functional architecture of a mature marine chartplotter. It launches into a focused five-tile WP8 Start Screen. Browse, Navigate, Anchor and Survey share one marine chart surface rather than becoming separate map applications.

The previous launcher-first prototype is preserved on `codex/launcher-foundation` for comparison, but is not the product baseline.

Current first pass includes the WP8 Start/All Apps shell, editable four-column Live Tiles, typed deep links, four workspace hosts, phone and square-screen checks, and standalone/HOME debug APKs. Marine data is fake by design in this slice.

```text
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

Documents:

- [Chart-first product direction](docs/CHART_FIRST_PRODUCT_DIRECTION.md)
- [Legacy workflow audit](docs/LEGACY_WORKFLOW_AUDIT.md)
- [TDD playbook](docs/TDD_PLAYBOOK.md)
- [TDD execution log](docs/TDD_LOG.md)
- [Changelog](CHANGELOG.md)
