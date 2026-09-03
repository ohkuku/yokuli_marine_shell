# Contributing to Yokuli OS

All product work follows the repository’s [TDD playbook](docs/TDD_PLAYBOOK.md) and [canonical WP8 UI pattern](docs/WP8_UI_PATTERN.md).

## Required sequence

1. Create or update a requirement with Given/When/Then and prohibited side effects.
2. Add the smallest behavior test and run it red for the missing behavior.
3. Implement the minimum Green behavior.
4. Refactor only while the affected gate remains green.
5. Record commands/results in `docs/TDD_LOG.md` and user-visible impact in `CHANGELOG.md`.

Do not report emulator coverage as Samsung hardware or vessel verification. Use `UNVERIFIED_HARDWARE` and `UNVERIFIED_VESSEL` until those checks actually happen.

## UI requirement

New pages must use `WpPageHeader`, `WpApplicationBar`, and a tested `WpNavigationIntent`. Any exception needs an ADR explaining why the shared language cannot express the user task. Safety-critical presentation must not be delayed by decorative motion.

## Local gate

```text
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```
