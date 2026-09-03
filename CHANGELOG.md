# Changelog

## [Unreleased]

### Changed

- 放弃把所有功能平铺为默认磁贴的 Launcher 原型，改为 WP8 Shell 与成熟海图功能架构结合。
- 应用仍然启动到 WP8 Start Screen，默认只显示 Chart、Anchor、Cockpit、Library、System 五个磁贴。
- Anchor、Navigate 和 Survey 定义为共享海图的工作模式，而不是平级应用或独立地图。
- WP8 Start Screen、Live Tile、All Apps、字母跳转、编辑模式和转场继续作为完整 Shell 交互目标。
- Shell 转场升级为可复用的 WP8 动效系统：同级 Slide、深入/返回 Turnstile、临时界面 Swivel，以及安全关键状态零时长策略。
- Chart、Cockpit、Library、System 统一使用左上大字应用名、次级模式行和经典底部 Application Bar。
- 旧仓库的成熟 CI/Release 语义已按当前双变体工程重构；未照搬不存在运行时的 soak 覆盖。
- Shell 新增统一 WP8 主题资源；Start 上所有磁贴继承同一主题色，安全/陈旧状态改为文字和小型状态点，不再整块改色。
- Start 外边距与横纵间隙统一为 6dp，磁贴明确分为左上 glyph、中部单一实时事实、左下稳定入口名三个区域。
- 磁贴/列表/字母跳转/系统设置的触控反馈统一为整平面按触点倾斜；无 ripple、阴影、弹跳或桌面 hover 伪态。

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
- 新增 `WpMotionPolicy`、3D `WpSurfaceTransitionHost`、分层 `wpEntrance` 和按触点响应的 `wpTilt`。
- 新增 UI 动效策略 JVM 测试和四个核心 App 大标题真实 Activity 故事。
- 新增 API 36 reduced-motion smoke、API 34/36 夜间矩阵、候选/未验证/已验证制品分级、GitHub 摘要/注解和失败证据包。
- 新增语义版本解析、签名预检、双变体 APK/AAB 签名校验、SHA-256 与不可覆盖 GitHub Release 流水线。
- 新增 WP8 UI 需求合同、强制范式、Compose 动效 ADR、GitHub 交付手册、PR 模板和功能 Issue 表单。
- 新增 System → Display 的 dark/light 与七种 accent 选择，以及全 Shell 即时主题传播。
- 新增主题策略、按压几何、Settings 深链 JVM 测试和真实 Activity 主题传播故事。
- 新增 WP8 主题/磁贴细节研究审计，并将结论固化进强制 UI 范式。

### Removed

- 新分支不包含上一版多应用磁贴桌面实现；原型仍安全保留在 `codex/launcher-foundation` 分支和提交 `549d67b`。

### Verification

- Product research: completed against official B&G, Simrad and Garmin/Navionics sources.
- Implementation: M1/M2 first pass complete with fake tile snapshots and workspace stubs.
- Unit tests: PASS.
- Compose integration stories: 6/6 PASS on API 34 emulator, including light/magenta propagation to every default tile.
- WP8 motion and press policies: PASS; center/corner/clamp/release geometry is JVM-tested.
- 320×320 dp constrained layout: PASS; Library/System reachable by vertical scroll.
- Standalone APK: PASS.
- HOME APK: PASS.
- Android lint: PASS.
- Release signing plumbing: PASS locally for both APKs and both AABs with a disposable test key; GitHub secrets not exercised.
- GitHub-hosted API 34/API 36 workflows: PASS in Actions run `33764978254`; final verified artifact `9897363683` published.
- Device: VERIFIED_DEVICE_EMULATOR; Samsung square hardware remains UNVERIFIED_HARDWARE.
- Vessel: UNVERIFIED_VESSEL.

### Known boundaries

- Desktop edits currently live for the process lifetime; Proto DataStore persistence and Reset/Lock/Safe Mode come in the next Shell Runtime slice.
- Theme selection currently survives Activity saved-state restoration but is not yet persisted as durable Shell storage across an explicit data clear/reinstall; global Safety Overlay, Recents UI and runtime task ownership are not implemented yet.
- GPS, NMEA, Anchor Watch, Trip, Survey, map SDKs and foreground services are deliberately not connected in this pass.
