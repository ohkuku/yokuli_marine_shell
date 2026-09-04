# Yokuli OS

[![Android CI](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml)
[![Nightly Compatibility](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml)
[![Release](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml)

## 中文（主文）

Yokuli OS 把 Windows Phone 8 的完整交互语言用于成熟海图产品架构。应用启动到克制的五磁贴 Start Screen；浏览、导航、锚泊和测深共享同一个海图工作区，不被伪装成互不相关的地图应用。

当前阶段只完善 UI/UX 和大的功能边界，不接入 GPS、NMEA、Anchor、Trip、Sonar 或真实海图运行时。所有航海数字都是明确的 `*UiFixtures`。每个 feature 只渲染不可变 `UiState` 并发布封闭 `UiAction`；未来以 Kotlin Flow 的 `StateFlow`/`SharedFlow` 接入功能，不允许功能模块重新定义主题、文案、间距或动效。

中文是未限定资源中的默认语言，英文位于 `values-en`。System → Display 可以在中文和 English 之间切换；显式选择会写入应用偏好，并与 Android 应用级 locale 同步。Launcher 的标题、glyph 和磁贴展示内容归 `feature:desktop` 所有，不进入领域模型。

上一版 launcher-first 原型保留在 `codex/launcher-foundation` 供对照，不是当前产品基线。

本地门禁：

```text
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-secrets-manager.sh
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

核心文档：

- [UI／功能隔离与双语需求](docs/requirements/UI_FUNCTION_I18N_REQUIREMENTS.md)
- [UI／响应式模块架构](docs/UI_REACTIVE_ARCHITECTURE.md)
- [Chart-first 产品方向](docs/CHART_FIRST_PRODUCT_DIRECTION.md)
- [旧工作流审计](docs/LEGACY_WORKFLOW_AUDIT.md)
- [TDD 开发规范](docs/TDD_PLAYBOOK.md)
- [TDD 执行日志](docs/TDD_LOG.md)
- [WP8 UI 需求合同](docs/requirements/WP8_UI_SYSTEM_REQUIREMENTS.md)
- [WP8 UI 强制范式](docs/WP8_UI_PATTERN.md)
- [GitHub 交付与发布](docs/GITHUB_DELIVERY.md)
- [本地密钥保险库](docs/SECRETS_MANAGEMENT.md)
- [密钥保险库需求合同](docs/requirements/SECRETS_MANAGEMENT_REQUIREMENTS.md)
- [变更记录](CHANGELOG.md)

## English translation

Yokuli OS applies the full Windows Phone 8 interaction language to a mature chartplotter architecture. It opens on a focused five-tile Start Screen; Browse, Navigate, Anchor, and Survey remain modes of one shared chart workspace.

This phase completes UI/UX and module boundaries only. Marine values are explicit fixtures, not connected data. Features render immutable `UiState` and publish sealed `UiAction`; later runtime work will use Kotlin Flow UDF without allowing runtime modules to redefine theme, copy, spacing, or motion.

Simplified Chinese is the default unqualified resource and English lives in `values-en`. System → Display persists the explicit choice and keeps it synchronized with Android per-app locales. Launcher copy and glyphs are UI-owned rather than domain-owned. The commands and document links above are the normative development and delivery entry points.
