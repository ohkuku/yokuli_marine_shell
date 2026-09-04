# Changelog

## [Unreleased]

### Changed

- Android 与 GitHub Actions 统一从 `GOOGLE_MAPS_ANDROID_API_KEY` 环境变量向 manifest 注入地图 key；普通 PR／本地无 key 构建使用明确 fixture fallback，release preflight 要求地图 key 与四项签名 secret 同时存在。
- Phase 1 海图来源收敛为 Google Maps Android SDK、OpenSeaMap 默认航标叠加和用户本地海图导入；明确移除 LINZ provider、key 与 URL override。
- 基础地图不按 dev/prod 拆 key：两个 Android applicationId 及其 debug/release 证书共享一个仅允许 Maps SDK for Android 的受限 key；OpenSeaMap 和本地导入不新增 key。
- OpenCPN-like 导入先限定为 raster MBTiles MVP；BSB/KAP、S-57、S-63 和专有格式分别记录为后续或不支持，避免宣称全格式兼容。
- 为 WP Phone 全屏 cutout 布局增加 Android 9／API 28 版本保护，保留 minSdk 26 兼容性并恢复 lint gate。
- UI 与功能改为明确的 `UiState`／`UiAction` 边界；后续 runtime 采用 Kotlin Flow 响应式单向数据流，不使用全局 EventBus。
- 应用以简体中文为默认资源，提供完整英文翻译；System → Display 可以切换并持久化应用级语言。
- 修正 WP Phone 主题真值：深色严格黑底白字、浅色严格白底黑字，宿主系统栏与异形屏区域同步主题，accent 磁贴固定纯白前景，不再按亮度自动改成黑字。
- Launcher 标题、glyph 和拼音分组从领域 descriptor 移入 `feature:desktop` 视觉目录。
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

- 新增隔离的 `adapter:chart-google`：用 Google Maps Android SDK 20.0.0 承载共享 Chart surface，管理 `MapView` 生命周期、camera 保存恢复、缩放/平移手势、Auckland Harbour 初始视区及深浅主题同步；`feature:chart` 不依赖供应商 SDK。
- 新增 Google Maps adapter TDD 合同，覆盖模块边界、环境变量到 manifest 的安全注入、无 key fallback、Chart 插槽以及 Actions secret 传递。
- 新增海图来源／导入双语需求合同和自动化 gate，固定单一运行期地图凭据、provider 退化语义、MBTiles 输入验证与安全 runtime 隔离。
- 新增基于 `age` 的本地加密密钥保险库：随机 identity 由个人强口令保护，API key 作为整体加密 JSON 提交；提供 `doctor/init/set/remove/list/get/copy/run/rotate` Bash 命令、双语安全手册和独立 CI 合同测试。
- 新增常见明文凭据、签名文件和 vault 临时文件的 Git 忽略规则；CI 只使用假加密器验证流程，不接触个人主口令或真实 secret。
- 新增 UI／功能隔离、多语言和响应式模块需求合同，以及自动检查资源 key、模型纯净度和 feature UI 契约的 TDD gate。
- 新增真实 `ShellActivity` 中英切换故事；导航故事改用语言无关语义 tag。
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
- Encrypted secrets Bash contract: PASS, including malformed artifact, tracked plaintext, unsafe environment-name, metacharacter, clipboard, rotation, and child-process cases.
- Local age 1.3.2 and jq 1.7.1 vault structural doctor: PASS; encrypted personal vault is initialized without decrypting or printing its contents.
- Google Maps adapter contract: PASS (5/5); all Python contracts: PASS (19/19).
- Configured-path compile with a non-secret test value: PASS; merged manifest and BuildConfig fallback were then verified without that value.
- Full `test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug`: PASS (714 actionable tasks).
- Compose integration stories: 8/8 PASS on API 34 through the configured Google surface path using an explicit non-secret test value.
- Release signing plumbing: PASS locally for both APKs and both AABs with a disposable test key; GitHub secrets not exercised.
- GitHub-hosted API 34/API 36 workflows: PASS in Actions run `33764978254`; final verified artifact `9897363683` published.
- Device: VERIFIED_DEVICE_EMULATOR; Samsung square hardware remains UNVERIFIED_HARDWARE.
- Vessel: UNVERIFIED_VESSEL.

### Known boundaries

- 个人加密 vault 已由仓库所有者初始化并通过结构检查；agent 没有解密、列出或打印真实值。真实 Google key 的地图加载仍需所有者在交互终端解锁后做设备验收。
- GitHub 中提交的 vault 密文不会自动成为 Actions Secret；要让 GitHub 测试 APK 使用真实地图，仓库所有者仍需另行设置 `GOOGLE_MAPS_ANDROID_API_KEY`。无 secret 的 PR 使用 fixture fallback，release 会在 preflight 失败。
- Desktop edits currently live for the process lifetime; Proto DataStore persistence and Reset/Lock/Safe Mode come in the next Shell Runtime slice.
- Theme selection currently survives Activity saved-state restoration but is not yet persisted as durable Shell storage across an explicit data clear/reinstall; global Safety Overlay, Recents UI and runtime task ownership are not implemented yet.
- GPS, NMEA, Anchor Watch, Trip, Survey, OpenSeaMap, local chart import, and foreground services remain disconnected. The Google base-map adapter is connected, but real-key device behavior is not agent-verified.

## English translation — current UI/i18n slice

- Established immutable `UiState`/sealed `UiAction` boundaries for every current feature; future runtime integration uses Kotlin Flow UDF without a global event bus.
- Made Simplified Chinese the unqualified default resource and added key-complete English resources plus an AndroidX-backed per-app language selector in System → Display.
- Corrected the WP Phone color contract to exact black/white theme pairs, theme-synchronized host/cutout chrome, and a fixed pure-white foreground on accent tiles instead of luminance-selected black text.
- Guarded the API 28 display-cutout call so the minSdk 26 build remains lint-clean.
- Moved launcher title/glyph/index metadata out of the domain descriptor and into the desktop UI catalog.
- Added architecture contracts, automated bilingual-resource and UI-boundary checks, and a real-Activity Chinese/English switching story.
- GPS, NMEA, Anchor, Trip, Survey, OpenSeaMap, local-chart import, and foreground runtimes remain deliberately unimplemented in this UI slice. The isolated Google Maps base adapter is now wired, but real-key device acceptance is still pending.
- Added an age-based local encrypted vault, bilingual runbook, plaintext-ignore policy, and fake-crypto CI contract. The owner initialized the encrypted vault; the agent validated only its structure and never decrypted or printed a real credential.
- Reduced Phase 1 chart sources to Google Maps, keyless OpenSeaMap seamarks, and local chart import. One Android-restricted Google key covers both app variants without a dev/prod split; LINZ is excluded and raster MBTiles is the only import MVP.
- Added environment-to-manifest Maps key injection, explicit fixture fallback, lifecycle/theme/camera-safe Google Maps adapter behavior, and GitHub Actions secret propagation. Encrypted vault files remain independent from GitHub-managed Actions secrets.
