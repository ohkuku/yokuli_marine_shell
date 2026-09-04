# Yokuli OS

[![Android CI](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/android.yml)
[![Nightly Compatibility](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/nightly.yml)
[![Release](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml/badge.svg)](https://github.com/ohkuku/yokuli_marine_shell/actions/workflows/release.yml)

## 中文（主文）

Yokuli OS 正在以 Windows Phone 8 的 Shell 交互语言构建一个诚实、可扩展的海图产品。当前 Phase 0A 不是功能演示墙：生产构建只安装已经具备真实入口和明确状态的 `Chart` 与 `Settings`。

默认 Start 是四列空间文档：`Chart` 使用标准 `WIDE_4X2` 并位于 `(0,0)`，`Settings` 使用 `SMALL_1X1` 并位于 `(0,2)`；其余位置是有意保留的留白。所有主题色磁贴共享同一 accent，深色页面为黑底白字，浅色页面为白底黑字，磁贴前景始终为白色。图标由受控 Canvas 绘制，不依赖 Unicode 字体。

Chart 当前只有 Browse。配置 Google Maps Android key 时显示真实 Google 底图；未配置时永久显示 `DEMO MAP / 地图未配置` 的非导航背景，绝不伪造船位、路线、航速或安全状态。Settings 当前只包含外观、开始屏幕、地图、语言、关于与诊断，并且只呈现真实配置和构建事实。

Anchor、Trip、NMEA、Navigation、Sonar、Anchorages、Data Sources 等领域语义没有被否定，但在对应真实垂直切片达到安装门禁前，不进入 Registry、All Apps、Start 或生产依赖图。用于布局压力测试的 30 项 `DEMO` 数据只存在于 debug-only `Shell Lab`，release manifest 与依赖不包含它。

架构采用功能贡献注册表、不可变 `UiState`／封闭 `UiAction`、函数式 reducer 和响应式端口。`core:shell-engine` 已落下像素对齐几何、显式空间桌面文档、修复策略、事务、交互状态机类型与 `StateFlow` store 端口。完整交互式 pager、拖动自动滚动、碰撞预览、撤销和持久化实现属于后续 S3–S8，不在本轮完成声明中。

本地质量门禁：

```text
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-secrets-manager.sh
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

从个人加密 vault 注入 Google Maps key：

```text
./scripts/secrets/yokuli-secrets.sh run -- ./gradlew installStandaloneDebug
```

该命令只在交互终端请求主口令，不创建明文 `.env`。个人 vault 密文不会自动变成 GitHub Actions Secret。

核心文档：

- [Phase 0A 产品面收敛需求](docs/requirements/PHASE0_PRODUCT_SURFACE_REQUIREMENTS.md)
- [Shell Engine 本轮需求与阶段边界](docs/requirements/SHELL_ENGINE_REQUIREMENTS.md)
- [UI／响应式模块架构](docs/UI_REACTIVE_ARCHITECTURE.md)
- [WP8 UI 强制范式](docs/WP8_UI_PATTERN.md)
- [UI／功能隔离与双语需求](docs/requirements/UI_FUNCTION_I18N_REQUIREMENTS.md)
- [海图来源、单 key 与导入需求](docs/requirements/CHART_SOURCE_IMPORT_REQUIREMENTS.md)
- [TDD 开发规范](docs/TDD_PLAYBOOK.md)
- [TDD 执行日志](docs/TDD_LOG.md)
- [GitHub 交付与发布](docs/GITHUB_DELIVERY.md)
- [本地密钥保险库](docs/SECRETS_MANAGEMENT.md)

## English translation

Yokuli OS is building a truthful, extensible marine chart product with the Windows Phone 8 Shell interaction language. Phase 0A installs only Chart and Settings in production. The four-column spatial Start document places a standard `WIDE_4X2` Chart at `(0,0)` and a `SMALL_1X1` Settings tile at `(0,2)`, preserving intentional whitespace.

Chart exposes Browse only. With a configured Android Maps key it renders the real Google base map; without one it permanently labels a non-navigational fallback `DEMO MAP` and never invents vessel position, route, speed, or safety state. Settings exposes only implemented Appearance, Start Screen, Map, Language, and truthful About/Diagnostics facts. Future marine domains remain outside the production contribution graph until complete vertical slices pass the installation gate. Thirty stress entries live exclusively in the debug-only Shell Lab.

`core:shell-engine` now defines pixel-snapped Start geometry, an explicit spatial desktop document, validation/repair, layout transactions, interaction-state types, and reactive store ports. The fully interactive pager, drag auto-scroll, collision previews, undo, and durable storage are later S3–S8 work and are not claimed complete here. The commands and linked Chinese-first documents above are the normative development entry points.
