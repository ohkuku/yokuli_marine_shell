# Changelog

## Stage 11 — Performance & Fidelity Gate

- 新增独立 Macrobenchmark 与 Baseline Profile 模块，版本化保存只包含 Yokuli 产品 descriptor 的 Baseline/Startup Profile；CI 归档 AndroidX 原始 metrics 与 trace，并明确模拟器只用于趋势。
- Shell Lab 压测目录增至 60 Tile，新增 320×320、360×360 与 All Apps 三键常驻回归；Release 仍不包含 Shell Lab。
- 新增 Master 八场景加 360×360 方屏共九张 API 34 renderer 候选、content-addressed manifest、Reference comparison 和精确场景语义 validator；Golden、三星方屏与 60/90/120 Hz 真机保持人工／硬件待验证。
- 将字母跳转提升为 Engine transient；虚拟／Android Back 先关闭该层再离开 All Apps。为虚拟 Back/Start/Search 增加独立合成层，避免动态 overlay 使真实设备截图丢失 glyph。
- 虚拟 Back/Start/Search 与可投递平台键继续进入同一个串行 Engine；Android 系统保留的物理 HOME 仍通过 Home flavor intent 接入，不伪造硬件权限、灯效、触感或 latency。
- Profile harness 使用 3 轮上限／2 轮稳定收敛，并与生产 crash-loop 记账隔离；最终规则在双 Release APK 中编译打包。封口自审修正了 Alphabet 测试 matcher，API 34 Activity stories 达到 26/26。
- Hosted 自审修正 Activity reset 前置状态竞态，Macrobenchmark 使用显式 Activity intent 和更宽的 emulator tag 等待；performance XML 与 benchmark/profile 诊断现可直接形成 Actions annotation/artifact。
- Hosted Compose 故事门不再把串行 Engine 的异步 action 当作同步 Compose 点击：跨 Surface 的断言会等待真实目标可见，消除快慢 runner 对转场测试的偶发性。
- GitHub emulator 从已弃用的 `swiftshader_indirect` 切换为官方推荐的自动图形后端选择，保留启用动画的 Activity story 和真实 FrameTimingMetric trace。

## Stage 10 — Durable Storage & Recovery

- 使用单一 Proto DataStore 原子保存 Start document、主题、主题色、语言、布局锁、上次页面/前台 token 和启动健康状态；支持 schema migration、损坏数据回退与进程重建。
- 启动恢复期间禁止用户修改但继续串行 Catalog 变化；布局修复、迁移和存储失败进入 Engine Incident 记录，未提交的 pointer/drag/transition 状态不落盘。
- 连续三次短窗口启动失败进入非破坏性 Safe Mode，保留 Chart、Settings、重置和 Android 默认桌面设置出口；默认布局只临时使用，不静默覆盖用户布局。
- HOME flavor 继续依靠 `singleTask/onNewIntent` 接收系统 HOME intent；Android 保留的实体 HOME 不被虚假描述为普通 Activity 可截获。

## Stage 9 — Navigation / Motion / Immersive / Virtual Keys

- 壳内 Back/Start/Search、Android Back 与可投递硬件键统一为串行 Engine 输入；HOME flavor 通过 singleTask HOME intent 回 Start。
- 新增真实 Catalog 搜索与内部任务 Recents；Settings 子页改用 opaque token 返回栈，Home 保留任务。
- 默认沉浸式全屏，使用批准的可见动效区间、Reduced Motion 和 MapView 延迟挂载；未观察的虚拟键尺寸/触感明确为派生适配。

## Stage 8 — Pin / Unpin / Context Menu

- All Apps context menus, Pin, Unpin, reveal and Undo are now serialized Engine actions and state.
- Pin returns to Start and reveals the committed tile; Unpin never removes the installed app.
- Catalog additions do not auto-pin, removals preserve unrelated coordinates, and failures are visible in Chinese and English.

## [Unreleased]

### 中文（主文）

#### Stage 7 — Complete Edit / Drag / Resize

- 编辑、拖动、边缘自动滚动、Drop/Cancel、Resize 与无障碍方向移动统一进入串行 Launcher Engine。
- 拖动保留真实 pointer/grab offset，原位 placeholder 与邻居 proposed layout 在提交前可见；Back、pause、pointer/viewport/catalog 取消不污染文档。
- Resize 以 provisional transaction 渲染后提交，Chart 严格循环 Small/Medium/Wide；新增中英文无障碍移动标签。
- Stage 2.5 未观察的滞回、自动滚动速度和触感均明确为派生运行策略，不冒充 WP8 测量值。

#### Stage 6 — Custom Spatial Grid

- 新增显式 cell 占用索引和局部确定性碰撞求解；移动/缩放只影响直接碰撞项，不压缩空白、不改无关坐标。
- Start 改用像素对齐的自定义 Compose `Layout`，提交前以独立 `proposedDocument` 预览，邻居只做视觉层平移。
- 新增占用、局部性、空白保持和 60 个合成磁贴稳定性测试；越界提案明确拒绝，不隐式 clamp。

#### Stage 5 — Interactive Start / All Apps Pager

- 删除固定距离、松手后才换页的 `SwipeSurface`，改为 Foundation `HorizontalPager` 支撑的 `InteractiveLauncherPager`。
- Start 与 All Apps 逐帧随手指移动，使用基础库的轴锁、位置/速度 settle、边界和可中断动画；settled page 与串行 Engine 双向同步。
- Edit mode 禁用横向分页但保留 Start 纵向滚动；不新增参考视频未观察到的自定义阈值或灯效。
- 新增 6 项 Stage 5 合同与慢短拖、长拖、纵向意图、编辑禁用和 Back 的真实 Activity stories。

#### Stage 4 — Engine State, Effects & Persistence Ports

- 新增纯 `LauncherReducer`、`LauncherEngineState/Action/Effect` 与唯一串行 action queue；异步 token 解析不再允许旧结果越过后续 Back/Home。
- 失效或已卸载 token 保持当前界面并产生结构化 incident，不再抛异常；HostPort catalog flow 成为 Engine 与 Renderer 的唯一 runtime catalog 来源。
- 布局变更使用确定性 transaction id，支持 commit/cancel/undo 与 persistence effect；损坏内存文档确定性恢复默认布局。
- `ShellViewModel` 在 Activity 配置重建期间保留 Engine；`ShellActivity` 不再用 Compose `remember` 持有导航或 Start document 真值。
- 新增 7 项 Stage 4 Red/Green 合同、6 项 reducer/controller JVM 场景与 Activity recreation story；DataStore 进程恢复、pager、拖拽和系统键仍属于后续 Stage。

#### Stage 3 — WP Geometry & Start Document

- 用 Stage 2.5 人工批准的 revision 1/hash 建立 `WpReferenceProfile`；480×800 Phone profile 固定测得的 24px 外边距、12px seam、99px Small、210px Medium、432px Wide、32px 状态条和 57px 首磁贴顶边。
- 新增 `StartViewport`、整数像素 `ResolvedStartGeometry` 与 Small/Medium/Wide 公式；320×320、360×360 方屏可完整落界，但 `SQUARE_4COL` 明确保持 `DERIVED_UNVERIFIED_HARDWARE`。
- 将候选 `DesktopDocument` 正式替换为带 schema/profile/default-layout 版本的二维 `StartDocument`，位置由 `GridCell` 显式定义；validator/repair 保留空白、拒绝重叠、移除未知/重复条目并确定性恢复。
- 默认 Start 仍严格为 Chart Wide + Settings Small；Renderer 不再使用未批准的 0.05/0.023 猜测比例。未在参考视频出现的长按、按压缩放和快速 fling 参数保持未知。
- 新增 6 项 Stage 3 Red/Green 合同、12 项几何/文档 JVM 场景和命名 CI Gate；Stage 4 reducer/effects/queue 未在本提交启动。

#### Batch A foundation — Installed app composition binding

- 新增单点 `InstalledAppBinding`，Chart 与 Settings 的目录贡献、LaunchToken 注册、视觉贡献和内部 Compose 宿主均从同一组 binding 派生；新增第三个应用时不再维护四张分散表。
- Desktop 视觉状态不再按 `"chart"`／`"settings"` 字符串分支；`ShellActivity` 不再现场创建内部宿主表。本提交只收口 composition root，不改变 Stage 0–2.5 产品与交互合同。
- 先加入四项 Red 合同，再更新旧 Stage 1/2 回归断言以验证相同语义的新派生结构；完整前置 Gate 结果记录于 TDD 日志。

#### Stage 2.5 — WP8 Reference Acquisition & Human Approval

- 从 Stage 2 批准 tag `launcher-engine-stage2-approved-v1`／`5386da0…` 重建 Stage 2.5；拒绝旧候选的手持／倾斜手机图，唯一视觉来源改为仓库所有者提供的 `kuku.mp4` WP8.1 模拟器录屏。
- 保存原始 MP4 与 16 张完整无变换时间戳 PNG；source/capture 均记录 SHA-256、字节数、尺寸、格式、来源引用和权利边界。
- 建立 480×800 逻辑 viewport 的几何测量：左右 inset 24、seam 12、Small 99×99、Medium 210×210、Wide 432×210 logical px；物理 DPI 未知，保持 `null`。
- 建立 Start→All Apps、app open、app→Start 与 Live Tile 可见运动时间线；没有 pointer/key overlay 的输入时序及 edit/drag/resize/pin/unpin/fast-fling/press feedback 均不伪造。
- 新增固定取帧依赖与工具、source manifest、测量方法、第三方 notices、语义 validator、Stage 2.5 TDD contract 和命名 CI Gate；Gate 校验文件 signature/hash/bytes/dimensions/reference/timeline/geometry/coverage 与人工 review hash。
- 记录 Yokuli OS 后续默认沉浸式全屏和壳内虚拟 Back/Start/Search 决定；全部系统、虚拟和未来物理输入必须汇入同一串行 Engine action 路径。本 Stage 不改生产 UI/runtime，Stage 3 未开始。
- 仓库所有者 kuku 已批准 profile revision 1 与 canonical hash `af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5`；measurement 为 `HUMAN_REVIEWED`，Stage 3 尚未开始。

#### Stage 2 — Engine Contract Extraction

- 从 Stage 1 批准 tag `launcher-engine-stage1-approved-v1`／`df371fb…` 开始，把 Launcher 合同与 Engine 抽成 Kotlin/JVM 模块；Engine 不再具有 Android manifest 或依赖 Compose、Feature、Google Maps、`core:model` 和 Marine 类型。
- 用 opaque `LauncherAppId`、`LauncherEntryId`、`LaunchToken`、`TileInstanceId` 替换 `MarineAppId`、`DestinationId`、`LaunchTarget` 与 `TileId`；固定 Registry 改为 Feature contribution + validated catalog composition。
- 新增 `LauncherHostPort`、Compose `InternalAppHostResolver`、Android 静态 host adapter，并把 Chart/Settings token 与 UI host 的组合限制在 `app-shell`；`ShellActivity` 不再按 app id 的 `when` 引用 Feature。
- 清除未接入调用方、也没有持久化实现的临时 `ShellStore/DesktopLayoutStore/ShellPreferencesStore` 空接口；正式响应式 Store、reducer 与恢复仍留在后续 Stage。
- 新增 Stage 2 Red/Green 架构合同、JVM catalog/navigation/adapter 测试和独立 GitHub Actions Gate；Stage 0/1 contracts 与双 Release 产品面审计继续作为回归门禁。
- 保留现有 UI、手势、动画、地图适配器、Chart + Settings 产品面和 debug-only Shell Lab；不启动 Stage 2.5，也不把冻结候选 geometry/layout/interaction 认定为后续 Stage 完成。

#### Stage 1 correction — Release truthfulness

- 把 `GOOGLE_MAPS_CONFIGURED` 的用户文案从“地图已就绪”收紧为“地图已配置”，明确它只表示提供了非占位密钥，不证明授权、网络、账单、应用限制或图块加载。
- Release UI 统一使用“仅浏览”，移除船位状态、离线导入路线图、未实现诊断清单，并把“关于与诊断”改为“关于”。
- Stage 1 contract 只扫描 production `strings.xml` 的用户可见值，拒绝 readiness、position 与 future-feature 文案。
- CI 同时构建、检查 standalone/HOME 两个 Release APK；二者包含 Chart + Settings 并排除 Shell Lab 与旧 feature，HOME 只额外要求 HOME/DEFAULT 启动类别。

#### Stage 1 — Product Surface Reduction

- 从 annotated approval tag `launcher-engine-stage0-approved-v1.1`／`16b0e5c…` 开始，重新审计 `ca84ef9…` 已存在的候选结果，不继承其完成结论。
- 新增 Stage 1 静态合同，精确锁定 production catalog、Start placements 与 All Apps 为 Chart + Settings；Chart 只有 Browse destination，旧 cockpit/library/system 没有 active source 或依赖。
- API 34 story 增加 All Apps 条目数恰为 2 的断言；已知 Coming Soon 与假 SAFE/SOG/COG/Trip/NMEA 数值进入拒绝清单。
- CI 增加独立 Stage 1 Gate，额外构建 standalone release audit APK，并用 `apkanalyzer` 确认 Chart/Settings classes 存在、`ShellLabActivity` 不在 release manifest 或 Dex。
- 候选生产 UI 已满足 Stage 1 Gate，因此不为制造变更重写行为；Shell Engine contract extraction 与 Stage 2 未开始。

#### Stage 0 correction — Reference & Baseline Contracts

- 新增机器可读 `BASELINE_LOCK.json` 与逐文件 `BASELINE_RECONCILIATION.md`，锁定 reviewed/actual/ending SHA、Master 新旧哈希、覆盖理由和 `PENDING_HUMAN_REVIEW`；明确 Android Library engine、Marine 耦合、猜测比例、随机 UUID 与临时 stores 不通过后续 Gate。
- Master 升级为 v1.1，在 Stage 2 和 Stage 3 之间加入强制 `Stage 2.5 — WP8 Reference Acquisition & Human Approval`；测量状态未达到 `HUMAN_REVIEWED` 时禁止开始 Geometry。
- WP8 schema 改为状态相关的 `captures[]`、场景化 `measurementSets[]` 和 hash-bound human `review`；`NOT_YET_MEASURED` 不再需要伪造 provenance，direct manipulation 同时记录 input timeline 与 visual samples。
- 新增三份有效、四份无效 fixture；Android CI 安装固定版本 `jsonschema` 并用真正的 Draft 2020-12 validator 执行，而非只用 `json.loads` 检查字段名。
- 新增完整 Stage 0 report，记录 hosted run `33850770612` 通过 Build、API 34 与 API 36；Reference 仍是 `NOT_YET_MEASURED`，Samsung 方屏仍是 `UNVERIFIED_HARDWARE`。
- 本纠偏不修改生产 UI、Registry、Google Maps Adapter、Feature、Shell Engine 生产代码或海事功能，也不进入 Stage 1。

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

Stage 2.5 is rebuilt from the approved Stage 2 tag using only the repository-owner-supplied WP8.1 emulator recording as visual evidence. The source MP4 and sixteen uncropped exact frames are content-addressed. Five scenario measurement sets capture normalized 480×800 Start geometry and visible page, app-open, Back-return, and Live Tile motion without inventing unseen input timing or edit/direct-manipulation behavior. A deterministic extractor, source and rights manifests, measurement method, semantic validator, TDD contract, and named CI gate enforce signatures, hashes, dimensions, references, timelines, coverage, and hash-bound human review. The future immersive-fullscreen and shell-owned virtual Back/Start/Search requirement is recorded without changing production runtime. Repository owner kuku approved profile revision 1 and the canonical hash; the package is `HUMAN_REVIEWED`, and Stage 3 has not started.

Stage 2 starts from the immutable Stage 1 approval tag and extracts pure Kotlin launcher contract/engine modules, opaque IDs and launch tokens, validated feature contributions, a Compose internal-host boundary, and an Android host adapter. Only `app-shell` wires Chart and Settings. The engine has no Android, Compose, Feature, Google Maps, core-model, or marine-domain dependencies. Existing UI and interaction behavior is preserved, and Stage 2.5 is not started.


The Stage 1 correction replaces map-readiness claims with configuration-only wording, removes vessel-position state and future-feature/diagnostics copy, and renames the section to About. The resource contract scans only production user-visible strings. CI now assembles and inspects both standalone and HOME release APKs, with HOME adding only its expected HOME/DEFAULT launch categories. No engine, gesture, persistence, reducer, marine capability, or Stage 2 contract changes are included.

Stage 1 starts from the immutable Stage 0 approval tag and revalidates the earlier candidate rather than inheriting its status. New contracts lock production catalog, Start, and All Apps to exactly Chart and Settings, keep Chart Browse-only, reject known placeholder marine claims, and confirm removed modules have no active source or dependency. API 34 checks exactly two All Apps entries. CI assembles a standalone release audit APK and uses `apkanalyzer` to prove that Chart and Settings are present while `ShellLabActivity` is absent. Production behavior is unchanged because the candidate already passes the product-surface gate; Stage 2 is not started.

Stage 0 correction locks the reviewed, selected, and ending baselines; reconciles every pre-existing shell-engine artifact without pre-approving later work; versions Master v1.1 with mandatory Stage 2.5 reference approval; and replaces the flattened measurement contract with state-aware, content-addressed captures, scenario measurement sets, direct-manipulation timelines, and hash-bound human review. CI runs three valid and four invalid fixtures through pinned real Draft 2020-12 validation. Hosted run `33850770612` passed build, API 34, and API 36, while reference measurements and Samsung square hardware remain unmeasured or unverified. No production behavior or Stage 1 work is included.

Stage 0 starts `codex/launcher-engine` from owner-selected latest commit `ca84ef9`, imports the Master Spec verbatim with a SHA-256 contract, establishes the unmeasured WP8 Reference/schema/screenshots/golden/artifact boundaries, archives superseded requirements and Slice 1–14 evidence, and adds a separately reported CI gate. It changes no UI, Registry, Google Maps, feature, or Android runtime behavior. Existing Phase 0A/S0–S2 code is a frozen baseline, not automatic proof that any later Master stage passed.

Phase 0A reduces production to Chart and Settings. The default spatial document preserves whitespace around a standard wide Chart and small Settings tile. Chart is Browse-only and the keyless fallback is permanently labeled `DEMO MAP`, with no fictional marine state. Settings exposes only implemented configuration and build facts. Cockpit, Library, the old System module, fake shortcuts, fake marine values, and non-standard tile sizes were removed.

The new contribution registry and `core:shell-engine` provide extensible identifiers, exact tile sizes, pixel-snapped geometry, an explicit spatial document, validation/repair, transactions, interaction-state types, and reactive storage ports. A 30-entry Shell Lab is debug-only. The earlier 29 Python contracts, Kotlin unit tests, lint, both debug variants, standalone release packaging, and eight API 34 real-Activity stories describe frozen baseline `ca84ef9`. That evidence does not pre-approve any later Master stage; physical square-device and vessel verification remain unverified.
