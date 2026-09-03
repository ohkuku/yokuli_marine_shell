# Changelog

## [Unreleased]

### Changed

- 放弃把所有功能平铺为默认磁贴的 Launcher 原型，改为 WP8 Shell 与成熟海图功能架构结合。
- 应用仍然启动到 WP8 Start Screen，默认只显示 Chart、Anchor、Cockpit、Library、System 五个磁贴。
- Anchor、Navigate 和 Survey 定义为共享海图的工作模式，而不是平级应用或独立地图。
- WP8 Start Screen、Live Tile、All Apps、字母跳转、编辑模式和转场继续作为完整 Shell 交互目标。

### Added

- 新增成熟海图产品共性和 Yokuli Chart-first 信息架构文档。
- 新建 clean-slate Android 多模块工程与 `standalone` / `home` 两种可并排安装的构建变体。
- 新增四个核心 App host：Chart、Cockpit、Library、System。
- 新增 WP8 Start Screen、五个默认 Live Tile、All Apps 字母列表与字母跳转。
- 新增长按编辑、拖动吸附、受支持尺寸循环、Unpin，以及 All Apps 长按 Pin/Unpin。
- 新增共享 Chart 的 Browse、Navigate、Anchor、Survey typed modes 和 Anchor 深链。
- 新增 Shell/布局 JVM 单元测试、真实 `ShellActivity` Compose 故事测试和 320×320 dp 约束测试。
- 新增 GitHub Actions：单测、lint、双 APK 构建、API 34 模拟器集成测试和可下载 APK/报告。
- 恢复旧工作流审计、TDD 规范、TDD Red/Green 日志与截图证据。

### Removed

- 新分支不包含上一版多应用磁贴桌面实现；原型仍安全保留在 `codex/launcher-foundation` 分支和提交 `549d67b`。

### Verification

- Product research: completed against official B&G, Simrad and Garmin/Navionics sources.
- Implementation: M1/M2 first pass complete with fake tile snapshots and workspace stubs.
- Unit tests: PASS.
- Compose integration stories: PASS on API 34 emulator.
- 320×320 dp constrained layout: PASS; Library/System reachable by vertical scroll.
- Standalone APK: PASS.
- HOME APK: PASS.
- Android lint: PASS.
- Device: VERIFIED_DEVICE_EMULATOR; Samsung square hardware remains UNVERIFIED_HARDWARE.
- Vessel: UNVERIFIED_HARDWARE.

### Known boundaries

- Desktop edits currently live for the process lifetime; Proto DataStore persistence and Reset/Lock/Safe Mode come in the next Shell Runtime slice.
- DAY theme, global Safety Overlay, Recents UI and runtime task ownership are not implemented yet.
- GPS, NMEA, Anchor Watch, Trip, Survey, map SDKs and foreground services are deliberately not connected in this pass.
