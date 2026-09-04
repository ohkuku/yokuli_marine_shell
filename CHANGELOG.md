# Changelog

## [Unreleased]

### 中文（主文）

#### Stage 0 — Freeze & Reference Contract

- 以仓库所有者指定的最新提交 `ca84ef9` 冻结 `codex/launcher-engine` 起点；Master 附件中的旧 reviewed SHA 只作为文档历史，不再作为分支基线。
- 逐字纳入 Launcher Shell Engine Master Spec，并用 SHA-256 静态合同保证来源可追踪。
- 建立 WP8 Reference Lab、measurement JSON schema，以及 screenshots、Golden、比较 artifact 的独立目录合同；当前全部为 `NOT_YET_MEASURED`。
- 把旧 Phase 0A/S0–S2、Chart-source、WP8/UI requirement 和 Slice 1–14 TDD 日志移入历史归档；当前 requirements 只保留 Master 与独立的 secrets supporting contract。
- GitHub Actions 新增命名的 `launcher_stage0_contract` Gate，并把结果纳入候选 APK、失败诊断、Job Summary 和最终质量判定。
- Stage 0 没有修改 UI、Registry、Google Map、Feature 或 Android 运行行为。

#### Added

- 新增 `core:shell-engine`，包含按屏宽比例计算并像素对齐的 WP8 Start 几何、显式 `DesktopDocument/GridCell/TilePlacement`、布局校验与确定性修复、`LayoutTransaction`、Start 交互状态类型，以及 `ShellStore/DesktopLayoutStore/ShellPreferencesStore` 响应式端口。
- 新增由 `ShellFeatureContribution` 组成的生产注册表；`MarineAppId` 与 `DestinationId` 改为稳定值类型，未来模块无需修改中心 enum。
- 新增真实 `feature:settings`，只提供外观、开始屏幕、地图、语言和关于/诊断事实。
- 新增 debug-only `feature:shell-lab`，用 30 个永久标记 `DEMO` 的条目验证密集布局、长标题与边界；production release 不含其依赖或 Activity。
- 新增 Phase 0A 与 Shell Engine 双语需求、10 项 Red/Green 静态合同、空间布局/几何 JVM 测试和 8 条真实 Activity 故事。
- 新增 Canvas/Vector 风格 MarineIcon；All Apps 长按使用可见的 Pin/Unpin 与 App Info 菜单。

#### Changed

- 生产产品面从五磁贴假数据原型收敛为 Chart + Settings。默认四列空间文档固定 `Chart WIDE_4X2 @ (0,0)` 与 `Settings SMALL_1X1 @ (0,2)`，保留空白且不全局回流打包。
- Chart 收敛为 Browse-only。缺少地图 key 时永久显示 `DEMO MAP / 地图未配置`；演示背景不含船位、路线或航海事实。
- Launcher Composable 必须显式接收 `LauncherUiState`，生产 main 不再包含 `LauncherUiFixtures` 或其他海事 fixture 对象。
- All Apps 只列出生产注册表已安装应用，不再显示“核心应用／快捷方式／长按固定”等内部元数据。
- 状态栏改为系统时间与真实电池状态，不再显示模拟 GPS/NMEA 或固定时间。
- Shell 主题继续遵守深色黑底白字、浅色白底黑字和 accent 磁贴固定白色前景；所有可见文案保持中文默认＋英文翻译。

#### Removed

- 从生产模块图、路由 host、Registry、Start 与 All Apps 删除 `feature:cockpit`、`feature:library` 和旧 `feature:system`。
- 删除 Anchor、Navigation、Survey、Trips、Anchorages、NMEA、Data Sources 与独立 Diagnostics 的假入口和假数字；未来真实垂直切片仍可通过贡献门禁重新安装。
- 删除 `HERO_4X2/WIDE_2X1` 等非本轮标准尺寸，只保留 `SMALL_1X1`、`MEDIUM_2X2`、`WIDE_4X2`。
- 删除功能页面中的 Home action；系统 Back 负责返回 Start。

#### Verification

- Phase 0 静态 TDD 合同：PASS（10/10；初始 Red 为 6 failures + 4 errors）。
- 全部 Python helper/architecture contracts：PASS（29/29）。
- Kotlin unit tests：PASS，包括主题、动效、贡献注册表、导航、几何与空间布局。
- `lintStandaloneDebug`：PASS。
- `assembleStandaloneDebug`、`assembleHomeDebug`、`assembleStandaloneRelease`：PASS。
- API 34 真实 `ShellActivity` Compose stories：PASS（8/8）。
- 截图人工检查：Start、未配置 Chart 和 Settings→Map 已检查；黑底白字、统一 cyan 磁贴、永久 DEMO 标签和有意留白符合本轮合同。
- 设备状态：`VERIFIED_DEVICE_EMULATOR`；方屏 Samsung 硬件与实船仍为 `UNVERIFIED_HARDWARE`、`UNVERIFIED_VESSEL`。

#### Frozen implementation boundaries at `ca84ef9`

- `ca84ef9` 的 Phase 0A 与旧 S0/S1/S2 结果只作为冻结实现基线；是否满足新 Master 的 Stage 1–11 必须逐阶段重新验证，Stage 0 不继承“后续阶段已完成”结论。
- 主题与桌面修改当前只在进程/Activity 状态中存在；store 是端口合同，不是持久化实现。
- Google Maps 真实 key 的设备加载仍由所有者通过加密 vault 交互解锁验收；agent 未解密、列出或打印任何真实 secret。
- GPS、NMEA、Anchor、Navigation、Trip、Survey、Sonar、OpenSeaMap 和本地海图导入仍未接入生产 runtime。

### English translation

Stage 0 starts `codex/launcher-engine` from owner-selected latest commit `ca84ef9`, imports the Master Spec verbatim with a SHA-256 contract, establishes the unmeasured WP8 Reference/schema/screenshots/golden/artifact boundaries, archives superseded requirements and Slice 1–14 evidence, and adds a separately reported CI gate. It changes no UI, Registry, Google Maps, feature, or Android runtime behavior. Existing Phase 0A/S0–S2 code is a frozen baseline, not automatic proof that any later Master stage passed.

Phase 0A reduces production to Chart and Settings. The default spatial document preserves whitespace around a standard wide Chart and small Settings tile. Chart is Browse-only and the keyless fallback is permanently labeled `DEMO MAP`, with no fictional marine state. Settings exposes only implemented configuration and build facts. Cockpit, Library, the old System module, fake shortcuts, fake marine values, and non-standard tile sizes were removed.

The new contribution registry and `core:shell-engine` provide extensible identifiers, exact tile sizes, pixel-snapped geometry, an explicit spatial document, validation/repair, transactions, interaction-state types, and reactive storage ports. A 30-entry Shell Lab is debug-only. The earlier 29 Python contracts, Kotlin unit tests, lint, both debug variants, standalone release packaging, and eight API 34 real-Activity stories describe frozen baseline `ca84ef9`. That evidence does not pre-approve any later Master stage; physical square-device and vessel verification remain unverified.
