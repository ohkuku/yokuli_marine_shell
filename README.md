# Yokuli OS

[![Android CI](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml)
[![Nightly Compatibility](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml)
[![Release](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml)

Yokuli OS combines a faithful Windows Phone 8-style shell with the functional architecture of a mature marine chartplotter. It launches into a focused five-tile WP8 Start Screen. Browse, Navigate, Anchor and Survey share one marine chart surface rather than becoming separate map applications.

The previous launcher-first prototype is preserved on `codex/launcher-foundation` for comparison, but is not the product baseline.

Current first pass includes the WP8 Start/All Apps shell, editable four-column Live Tiles, typed deep links, four workspace hosts, phone and square-screen checks, and standalone/HOME debug APKs. The shared design engine now provides one Shell-wide dark/light and accent theme, one-color Start tiles on a repeated 6dp seam, depth-aware Turnstile/Slide/Swivel plans, whole-plane pointer-position tilt, large upper-left app titles, and the classic bottom Application Bar. Marine data is fake by design in this slice.

```text
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
```

Documents:

- [Chart-first product direction](docs/CHART_FIRST_PRODUCT_DIRECTION.md)
- [Legacy workflow audit](docs/LEGACY_WORKFLOW_AUDIT.md)
- [TDD playbook](docs/TDD_PLAYBOOK.md)
- [TDD execution log](docs/TDD_LOG.md)
- [WP8 UI requirement contract](docs/requirements/WP8_UI_SYSTEM_REQUIREMENTS.md)
- [Canonical WP8 UI pattern](docs/WP8_UI_PATTERN.md)
- [WP8 theme and tile detail audit](docs/research/WP8_THEME_TILE_AUDIT.md)
- [Compose motion engine decision](docs/decisions/0001-compose-wp8-motion-engine.md)
- [GitHub delivery and release operations](docs/GITHUB_DELIVERY.md)
- [Changelog](CHANGELOG.md)
