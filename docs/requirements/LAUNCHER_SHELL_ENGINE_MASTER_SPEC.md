# Yokuli Launcher Shell Engine — Master Construction Specification
## 在接入任何海事功能之前，先完成一个与应用解耦、可验证、可持久化、可高帧率运行的 Windows Phone 8 风格模拟 Launcher

### 文档状态

```text
NORMATIVE / 施工主文档
version: 1.1
previous master SHA-256: f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0
```

v1.1 在 Stage 0 尚未人工批准时修正基线与 Reference 合同：实际施工起点使用仓库所有者明确指定的最新实现提交，并在 Stage 2 与 Stage 3 之间加入强制 WP8 Reference Acquisition & Human Approval Gate。此前 v1.0 字节哈希保留在上方和 Stage 0 `BASELINE_LOCK.json`，不得静默丢失来源链。

本文件取代此前分散的：

```text
产品表面缩减文档
Shell Engine Phase 0 文档
WP8 Fidelity 讨论稿
```

Codex 后续必须按本文件的阶段顺序施工，不得一次跨越多个阶段，也不得在 Shell Engine 完成前接入 GPS、NMEA、锚警、Trip、Navigation、Survey 或 OpenSeaMap。

---

# 0. 当前基线

## 仓库

```text
https://github.com/ohkuku/yokuli_marine_shell
master reviewed HEAD: 943d85276e4a042092f87090aa0d23da9a7cbbc6
execution branch: codex/launcher-engine
owner-selected Stage 0 starting HEAD: ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7
initial Stage 0 ending HEAD: 98121412893d5331b22d4327463794993a4a4eff
```

`943d852…` 是原 Master 审阅快照；仓库所有者随后明确要求从当时最新提交 `ca84ef9…` 开始。该覆盖及其原因必须由 Stage 0 baseline lock 与 reconciliation 共同记录，提前存在的实现不得自动算作后续 Stage Gate 已通过。

## 当前已经存在且可以保留的基础

```text
clean-slate applicationId
standalone / home 两种构建
多模块工程骨架
WP8 风格主题与 Accent
中英文资源
Google Maps Adapter 隔离
UiState / UiAction 方向
CI、lint、API 34 / API 36 测试基础
```

## 当前必须替换或重构的部分

```text
固定四 App 的 MarineAppId enum
生产注册表中的大量空应用和假快捷方式
生产页面中的假船位、假速度、假锚警、假航程和假 NMEA 状态
SwipeSurface 的“松手后超过 90px 才切页”
DesktopLayoutEditor 的全局 top-left reflow
Pin 后静默 append
拖动中其他磁贴不让位
2×1 非经典 WP8 磁贴尺寸
Feature App Bar 中的 Home
Chart 中并列暴露未实现 Browse / Navigate / Anchor / Survey
Shell 状态全部由 Compose remember 临时持有
缺少真正的持久化、恢复、撤销和性能门禁
```

---

# 1. 目标定义

本阶段不是做一张“像 Windows Phone 的首页”。

本阶段要完成一个：

```text
Yokuli Launcher Shell
```

它在产品上模拟一个完整 Launcher，在工程上又与 Yokuli Marine 应用功能彻底解耦。

它必须具备：

```text
Start Screen
All Apps
应用注册与卸载后的布局协调
Live Tile 状态投影
Pin / Unpin
拖拽与空间重排
Resize
编辑模式
Home / Back
内部 App Task
系统状态条
布局持久化
损坏恢复
HOME flavor
高帧率直接操纵
方屏适配
可测试的动效和布局引擎
```

但它暂时只启动 Yokuli 内部应用，不启动任意第三方 Android 应用。

---

# 2. 技术决策

## 2.1 不使用 Unity

本阶段技术路线：

```text
纯 Kotlin Shell Core
+
原生 Android / Jetpack Compose Renderer
+
Android Platform Adapter
```

不使用：

```text
Unity
Flutter
全屏 OpenGL 游戏 UI
WebView Launcher
```

原因不是“Unity 做不出动画”，而是 Launcher 的核心难题属于：

```text
Android HOME 生命周期
指针与系统 Back/Home
无障碍语义
嵌套滚动
状态恢复
权限
MapView/AndroidView
前台服务
低内存回收
应用任务
```

这些需要原生平台集成。

## 2.2 Engine 与 Renderer 分离

“引擎”不是一个巨大 Composable。

必须拆成：

```text
Launcher Contract
Launcher Core Engine
Launcher Motion Runtime
Launcher Compose Renderer
Launcher Android Adapter
Launcher Test Lab
```

其中：

```text
Core Engine
```

不能依赖 Android、Compose、Google Maps、Chart、NMEA 或其他 Feature。

## 2.3 Renderer 可替换

第一实现使用 Compose。

但 Engine API 必须允许未来将 Start Screen Renderer 替换为：

```text
自定义 ViewGroup
RenderNode
低层 Canvas Renderer
```

而不修改：

```text
布局文档
碰撞算法
Pin 事务
应用目录
状态恢复
Home/Back 语义
```

只有经过 Macrobenchmark 和真机测试确认 Compose Renderer 无法达到目标时，才允许替换 Renderer；不得提前更换整个技术栈。

---

# 3. 忠实度目标：锁定一个明确的 WP Profile

不能同时混合：

```text
Windows Phone 7
Windows Phone 8
Windows Phone 8.1 三列选项
Windows 10 Mobile
Windows 8 桌面 Start
Windows 10 Start Menu
```

第一份 Profile 固定为：

```text
WP8_CLASSIC_PHONE_4COL
```

核心特征：

```text
纵向 Start Screen
Start 与 All Apps 为水平相邻平面
4 个逻辑小格宽
Small / Medium / Wide 三种经典磁贴
平面纯色
黑色背景
强调排版与留白
长按编辑
应用列表字母跳转
底部 Application Bar
页面层级动效
```

## 3.1 标准磁贴类型

```kotlin
enum class WpTileSize {
    SMALL_1X1,
    MEDIUM_2X2,
    WIDE_4X2,
}
```

不再把以下尺寸称为标准 WP8：

```text
2×1
HERO
4×4 Large
```

未来 Yokuli 若确实需要扩展尺寸，必须用独立 Profile 或显式前缀：

```text
YOKULI_HALF_2X1
YOKULI_LARGE_4X4
```

默认 WP8 Profile 中关闭。

## 3.2 几何不是固定 dp

建立：

```kotlin
data class WpReferenceProfile(
    val id: ProfileId,
    val columnCount: Int,
    val seamToSmallRatio: Float,
    val outerInsetPolicy: OuterInsetPolicy,
    val statusStrip: StatusStripProfile,
    val typography: WpTypographyProfile,
    val tileContent: TileContentProfile,
    val motion: WpMotionProfile,
    val interaction: WpInteractionProfile,
    val layoutPolicy: WpLayoutPolicy,
)
```

最终几何由：

```text
可用物理像素
系统 inset
Profile 比例
像素取整策略
```

共同计算。

## 3.3 Reference Lab

所谓“完美还原”必须有真实对照。

建立：

```text
docs/reference/wp8/
```

包含：

```text
参考设备或模拟器信息
Start 截图
All Apps 截图
编辑模式截图
慢速分页视频
快速 fling 视频
Pin 视频
Drag / Resize / Unpin 视频
App 打开与 Back 视频
测量表
```

输出：

```text
WP8_REFERENCE_MEASUREMENTS.json
```

至少记录：

```text
reference viewport
outer inset
small tile bounds
medium tile bounds
wide tile bounds
seam
状态条高度
标题 baseline
glyph optical box
长按时长
分页 settle 时长
应用打开时长
按压 scale / tilt
```

Codex 不能凭感觉自行声明 pixel-perfect。

---

# 4. 生产产品表面先缩减

Shell Engine 施工前，Release 生产注册表必须只保留：

```text
CHART
SETTINGS
```

## 4.1 默认 Start

```text
CHART       WIDE_4X2
SETTINGS    SMALL_1X1
```

允许留白。

不为了填满屏幕创建假应用。

## 4.2 All Apps

只显示：

```text
Chart / 海图
Settings / 设置
```

## 4.3 Release 中彻底移除

```text
Anchor
Cockpit
Library
Navigation
Survey
Trips
Anchorages
Data Sources
NMEA Input
Diagnostics 独立入口
```

它们不是灰色 Coming Soon，也不是 Fixture 页面。

## 4.4 生产假数据禁止

Release 不得显示：

```text
SAFE
32 / 60 m
6.2 kn
HDG 184°T
MOTUIHE
DTW 3.4 NM
27 TRIPS
12 PLACES
假 GPS / NMEA 状态
```

Chart 暂时没有真实船位时显示：

```text
地图已就绪
船位尚未接入
```

无 Google Maps 配置时显示：

```text
地图未配置
```

## 4.5 Shell Lab

为了充分测试 Launcher，引入仅 Debug / Benchmark 可见的：

```text
feature:shell-lab
```

它可生成：

```text
2 / 10 / 30 / 60 个应用
Small / Medium / Wide 混合
中文、英文、长标题
大量动态图块
稀疏与密集布局
损坏布局
不同状态和徽标
```

Shell Lab 不得进入：

```text
Release Registry
Release All Apps
Release Start
Release Manifest
```

---

# 5. 目标模块结构

建议重构为：

```text
app-shell

shell/
├── contract
├── engine
├── compose
├── android
├── benchmark
└── testkit

feature/
├── desktop
├── chart
├── settings
└── shell-lab        // debug / benchmark only

adapter/
└── chart-google
```

在当前 Gradle 命名风格下可使用：

```text
:core:shell-contract
:core:shell-engine
:ui:shell-compose
:adapter:shell-android
:benchmark:shell
:core:shell-testkit
```

## 5.1 依赖方向

```text
app-shell
    ├── feature:desktop
    ├── feature:chart
    ├── feature:settings
    ├── ui:shell-compose
    └── adapter:shell-android
            ↓
       core:shell-engine
            ↓
       core:shell-contract
```

禁止：

```text
core:shell-engine → android.*
core:shell-engine → androidx.compose.*
core:shell-engine → feature.*
core:shell-engine → Google Maps
core:shell-engine → ChartMode
core:shell-engine → NMEA / GPS
feature:chart → shell-engine internals
feature:settings → shell-engine internals
```

## 5.2 架构自动检查

CI 增加静态合同：

```text
shell-engine contains no Android imports
shell-engine contains no Compose imports
shell-engine contains no feature imports
shell-engine contains no resource IDs
shell-engine contains no marine-domain names
release does not depend on shell-lab
```

---

# 6. App 与 Engine 的解耦合同

Engine 不能知道什么是 Chart、Settings、Anchor 或 Marine。

## 6.1  opaque ID

```kotlin
@JvmInline
value class LauncherAppId(val value: String)

@JvmInline
value class LauncherEntryId(val value: String)

@JvmInline
value class LaunchToken(val value: String)

@JvmInline
value class TileInstanceId(val value: String)
```

## 6.2 应用目录

```kotlin
data class LauncherCatalogSnapshot(
    val revision: Long,
    val apps: List<LauncherAppDescriptor>,
    val entries: List<LauncherEntryDescriptor>,
)

data class LauncherAppDescriptor(
    val appId: LauncherAppId,
    val rootEntryId: LauncherEntryId,
)

data class LauncherEntryDescriptor(
    val entryId: LauncherEntryId,
    val appId: LauncherAppId,
    val launchToken: LaunchToken,
    val defaultSize: WpTileSize,
    val supportedSizes: List<WpTileSize>,
    val pinPolicy: PinPolicy,
)
```

Engine 不保存：

```text
显示语言字符串
Android resource ID
Composable
NavController
ViewModel
Context
```

## 6.3 视觉目录

由 Renderer/Feature 提供：

```kotlin
data class LauncherEntryVisual(
    val entryId: LauncherEntryId,
    val title: UiText,
    val icon: LauncherIcon,
    val sortKey: String,
    val jumpGroup: String,
)
```

## 6.4 Live Tile 数据

应用通过只读源提供：

```kotlin
data class TileContentSnapshot(
    val entryId: LauncherEntryId,
    val primary: UiText?,
    val secondary: UiText?,
    val badge: TileBadge?,
    val semanticState: TileSemanticState,
    val updatedAtElapsedMillis: Long?,
    val animationPolicy: TileAnimationPolicy,
)
```

Engine 只负责磁贴生命周期和动画政策，不解释海事含义。

## 6.5 Host Port

```kotlin
interface LauncherHostPort {
    val catalog: StateFlow<LauncherCatalogSnapshot>
    val tileContents: StateFlow<Map<LauncherEntryId, TileContentSnapshot>>
    val systemStatus: StateFlow<LauncherSystemStatus>

    suspend fun resolveLaunch(token: LaunchToken): LaunchResolution
}
```

## 6.6 App Host Resolver

```kotlin
interface InternalAppHostResolver {
    fun hostFor(appId: LauncherAppId): InternalAppHost?
}
```

由 `app-shell` 连接：

```text
chart launch token → Chart Host
settings launch token → Settings Host
```

Engine 不引用 Feature 类。

---

# 7. Engine 核心 API

## 7.1 纯 Reducer

```kotlin
interface LauncherReducer {
    fun reduce(
        state: LauncherEngineState,
        action: LauncherAction,
        context: LauncherReducerContext,
    ): LauncherReduction
}

data class LauncherReduction(
    val state: LauncherEngineState,
    val effects: List<LauncherEffect> = emptyList(),
)
```

## 7.2 Engine Controller

```kotlin
interface LauncherEngine {
    val state: StateFlow<LauncherEngineState>
    val effects: Flow<LauncherEffect>

    fun dispatch(action: LauncherAction)
}
```

Controller 负责串行化 action，但最终语义由纯 reducer 决定。

## 7.3 Effects

```kotlin
sealed interface LauncherEffect {
    data class Launch(val token: LaunchToken) : LauncherEffect
    data class PersistDocument(val document: StartDocument) : LauncherEffect
    data class Haptic(val kind: LauncherHaptic) : LauncherEffect
    data class AccessibilityAnnouncement(val text: UiText) : LauncherEffect
    data class LogIncident(val incident: LauncherIncident) : LauncherEffect
    data class ScrollStartToReveal(val tileId: TileInstanceId) : LauncherEffect
    data object OpenAndroidSettings : LauncherEffect
}
```

Android/Compose 层执行 effect，Engine 不直接调用平台。

---

# 8. Launcher 状态层级

## 8.1 顶层 Surface

```kotlin
sealed interface LauncherSurface {
    data object Start : LauncherSurface
    data object AllApps : LauncherSurface
    data class InternalApp(val taskId: InternalAppTaskId) : LauncherSurface
    data object Recents : LauncherSurface
}
```

Phase 0 可暂不显示 Recents UI，但模型预留。

## 8.2 Launcher State

```kotlin
data class LauncherEngineState(
    val surface: LauncherSurface,
    val start: StartScreenState,
    val allApps: AllAppsState,
    val tasks: InternalTaskState,
    val transient: LauncherTransient?,
    val systemOverlay: LauncherSystemOverlay?,
)
```

## 8.3 页面与 Runtime 分离

Engine 的 `InternalAppTask` 只代表 UI：

```text
Chart UI 是否存在
Settings UI 是否存在
Chart 内最后 destination
```

它不代表：

```text
锚警是否运行
NMEA 是否连接
Trip 是否记录
```

后续海事 Runtime 完全独立。

---

# 9. Start Document：桌面必须是真正二维空间

## 9.1 持久化文档

```kotlin
data class StartDocument(
    val schemaVersion: Int,
    val profileId: ProfileId,
    val defaultLayoutVersion: Int,
    val placements: List<TilePlacement>,
)

data class TilePlacement(
    val tileId: TileInstanceId,
    val entryId: LauncherEntryId,
    val size: WpTileSize,
    val cell: GridCell,
)

data class GridCell(
    val column: Int,
    val row: Int,
)
```

## 9.2 位置是事实

不得再以：

```text
List 顺序
```

作为磁贴位置的真实来源。

不得每次操作后从左上角整体 pack。

## 9.3 Catalog reconciliation

应用目录变化时：

```text
Entry 消失
→ 移除对应 Tile
→ 其余 Tile 尽量不动

新 Entry 出现
→ 只进入 All Apps
→ 不自动 Pin

未知 Tile
→ 忽略并记录 Incident
```

## 9.4 重复 Pin

Phase 0：

```text
同一 entryId 最多一个 Tile
```

未来参数化深链接需要多个实例时，再引入：

```text
TileInstanceKey
```

---

# 10. Start 几何引擎

## 10.1 输入

```kotlin
data class StartViewport(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val topInsetPx: Int,
    val bottomInsetPx: Int,
    val fontScale: Float,
)
```

## 10.2 输出

```kotlin
data class ResolvedStartGeometry(
    val columns: Int,
    val outerInsetsPx: IntInsets,
    val seamPx: Int,
    val smallCellPx: Int,
    val statusStripHeightPx: Int,
    val contentBounds: IntRect,
)
```

## 10.3 尺寸公式

```text
Small:
s × s

Medium:
(2s + g) × (2s + g)

Wide:
(4s + 3g) × (2s + g)
```

其中：

```text
s = smallCellPx
g = seamPx
```

## 10.4 Pixel snapping

所有边界必须落在整数物理像素：

```text
Tile bounds
seam
outer inset
icon box
title baseline
edit controls
```

避免半像素模糊和间距不均。

## 10.5 Profile 适配

第一期支持：

```text
PHONE_PORTRAIT_4COL
SQUARE_4COL
```

宽屏先保持同一视觉语言，可在后续增加：

```text
YOKULI_WIDE_6COL
```

不得为了平板提前破坏 WP8 Phone Profile。

---

# 11. Gesture Arena

必须建立统一：

```kotlin
interface LauncherGestureCoordinator
```

不能让多个 Modifier 各自抢 pointer。

## 11.1 优先级

```text
系统安全覆盖层
>
Transient / Context Menu
>
活动 Tile Drag
>
活动 Tile Resize
>
Start ↔ All Apps Paging
>
Start / All Apps Vertical Scroll
>
普通 Tile Tap
```

## 11.2 Pointer ownership

一个 pointer session 一旦被某个交互赢得：

```text
其他交互不得中途抢占
```

但允许：

```text
系统安全覆盖层立即中断普通交互
```

## 11.3 方向锁定

超过 touch slop 后：

```text
abs(dx) > abs(dy) × directionLockRatio
→ 横向分页

abs(dy) > abs(dx) × directionLockRatio
→ 纵向滚动
```

未锁定前不消费为最终手势。

`directionLockRatio` 来自 Profile，初始可设约 `1.15–1.25`，必须真机调校。

## 11.4 Edit Mode

Edit Mode 时：

```text
禁用 Start ↔ All Apps 分页
```

纵向滚动仍可通过未被 Tile Drag 占有的区域进行。

---

# 12. Start ↔ All Apps：直接操纵

## 12.1 禁止当前做法

删除：

```text
drag 累加
画面不动
松手判断 ±90px
再开始 AnimatedContent
```

## 12.2 实现原则

优先使用：

```text
Foundation HorizontalPager
```

作为指针、速度、nested scroll 和 settle 的基础。

在 Renderer 外围建立：

```text
InteractiveLauncherPager
```

## 12.3 行为

手指按住时：

```text
页面位移与手指位移 1:1
Start 与 All Apps 同时存在
可滑到一半再返回
动画中再次触摸可立即接管
```

松手时根据：

```text
当前位置
页面进度
速度
方向
```

决定完成或返回。

不得只用固定 px。

## 12.4 页面边界

```text
Start 左侧不能无限拉出第三页
All Apps 右侧不能无限拉出第三页
```

边缘可以有 Profile 控制的阻尼，但不能产生 Material Stretch 风格。

## 12.5 编辑模式

进入编辑时：

```text
pager userScrollEnabled = false
```

退出后恢复。

## 12.6 可中断 settle

```text
正在 settle
→ 用户重新按下
→ 取消动画
→ 从当前 offset 接管
```

不能跳回动画起点。

---

# 13. Start 纵向滚动

Start 是一条纵向连续平面。

要求：

```text
手指跟随
快速 fling
低速停止稳定
不出现 Material overscroll glow/stretch
编辑拖动时支持边缘自动滚动
恢复到上次滚动位置（仅当前任务或按产品策略）
```

滚动内容高度由最高占用 row 决定。

空白 row 是文档的一部分，不能因重新组合自动消失。

---

# 14. Tile Tap 与按压反馈

## 14.1 Tap

只有满足以下条件才触发 Launch：

```text
未进入 Edit Mode
未超过 touch slop
未被分页或纵向滚动赢得
pointer 正常抬起
entry 当前仍可用
```

## 14.2 Press feedback

要求：

```text
触摸后下一帧出现反馈
整块 Tile plane 一起 scale / tilt
内容与背景不能分离
松手、取消或滑出后恢复
```

初始 Profile：

```text
scale 最低约 0.975
tilt 上限约 4–5°
press in 约 60–80ms
release 约 90–120ms
```

最终以 Reference Lab 为准。

## 14.3 Launch transition

点击 Tile：

```text
按压恢复
→ 创建轻量 Compose transition plane
→ App 进入
→ 重型 AndroidView/MapView 延后出现
```

不得直接对 Google MapView 做 3D shared transform。

---

# 15. Edit Mode 状态机

## 15.1 状态

```kotlin
sealed interface StartEditState {
    data object Off : StartEditState

    data class Idle(
        val selectedTileId: TileInstanceId?,
    ) : StartEditState

    data class Dragging(
        val session: TileDragSession,
    ) : StartEditState

    data class Resizing(
        val session: TileResizeSession,
    ) : StartEditState

    data class Settling(
        val transactionId: LayoutTransactionId,
    ) : StartEditState
}
```

## 15.2 进入

```text
长按 Tile
达到 profile.longPressMillis
移动未超过 longPress slop
→ Haptic
→ Tile lift
→ Edit Mode
```

## 15.3 退出

以下都可退出：

```text
点击空白
按 Back
点击 Done
```

如果正在 Drag/Resize：

```text
Back 先取消当前 provisional 操作
再次 Back 才退出 Edit Mode
```

---

# 16. Tile Drag 引擎

## 16.1 Grab offset

长按后保存：

```kotlin
grabOffsetPx
```

磁贴跟随时保持用户抓住的位置，不能跳到中心。

## 16.2 原始占位

拖起后：

```text
原 cell 保留 placeholder
Tile plane 提升 zIndex
```

## 16.3 Proposed layout

每个有效目标 cell 变化时，Engine 计算：

```text
proposed document
```

Renderer 显示：

```text
目标占位
邻居预计位置
可放置 / 不可放置
```

不在每个 pointer pixel 上重算完整布局。

## 16.4 Cell hysteresis

目标 cell 变化必须有滞回：

```text
指针穿过目标 cell 中心阈值
或
占用超过一定比例
```

避免边界抖动。

## 16.5 Collision Solver

定义：

```kotlin
interface TileCollisionSolver {
    fun propose(
        document: StartDocument,
        movingTile: TileInstanceId,
        target: GridCell,
        size: WpTileSize,
        policy: WpLayoutPolicy,
    ): LayoutProposal
}
```

目标：

```text
确定性
只移动必要磁贴
未受影响磁贴保持坐标
支持 profile 指定是否允许空白
不做全局 top-left repack
```

推荐算法：

1. 放置移动 Tile 到目标 cells；
2. 找出冲突 Tile；
3. 依照原始视觉顺序搜索最近合法位置；
4. 优先同列/同局部区域向下位移；
5. 发生链式冲突时仅继续处理冲突集合；
6. 找不到合法方案时返回 `Rejected`；
7. 对相同输入永远返回相同方案。

是否允许任意空白，由 `WpLayoutPolicy` 与 Reference Lab 决定；数据模型必须能够表示空白。

## 16.6 Neighbor animation

邻居从 committed bounds 到 proposed bounds：

```text
使用 graphicsLayer translation
```

避免每个指针事件触发昂贵的全树 Measure/Layout。

## 16.7 Auto-scroll

拖到顶部/底部 activation zone：

```text
开始自动滚动
速度按接近边缘程度变化
Tile 继续保持在手指下
```

初始 activation zone 可约 `48–64dp`，最终由 Profile 调校。

## 16.8 Drop

```text
valid proposal
→ 创建 LayoutTransaction
→ settle
→ commit
→ persist
→ haptic

invalid
→ 回原位
→ document 不变
```

## 16.9 Cancel

以下情况必须恢复 committed document：

```text
pointer cancel
系统 Back
Activity pause
安全 overlay
catalog revision 改变
屏幕尺寸突变
```

---

# 17. Resize 引擎

## 17.1 尺寸循环

经典 Profile：

```text
Small → Medium → Wide → Small
```

只经过 Entry 支持的尺寸。

Entry 必须显式提供：

```text
defaultSize
supportedSizesInCycleOrder
```

禁止使用：

```kotlin
supportedSizes.first()
```

作为默认尺寸。

## 17.2 Resize 流程

```text
点击 Resize control
→ 计算 next size
→ 生成 proposed layout
→ 邻居实时让位
→ settle
→ commit
→ persist
```

无法放置时：

```text
不改变尺寸
明确反馈
```

## 17.3 Small Tile 内容

Small Tile 优先显示：

```text
图标
可选 badge
```

不强塞标题和动态数字。

Medium/Wide 才显示更多内容。

---

# 18. Pin to Start

## 18.1 All Apps 长按

不能直接 silent pin。

长按后打开 WP 风格 context menu：

```text
Pin to Start
Unpin from Start
App info
```

仅显示适用项。

## 18.2 严格 WP Profile 行为

选择 Pin：

```text
1. Engine 创建 Pin transaction
2. 使用 PinPlacementPolicy.APPEND_AFTER_LAST_OCCUPIED_ROW
3. commit 新 Tile
4. 切回 Start
5. 自动滚动到新 Tile
6. 新 Tile 短暂高亮
7. 用户随后可长按拖动/Resize
```

此行为不自动进入长期编辑模式，除非 Reference Lab 证明目标 WP 版本如此。

可选的 `YOKULI_ENHANCED` Profile 才允许：

```text
Pin 后直接进入 Edit Mode
```

但 Classic WP Profile 默认关闭。

## 18.3 Undo

Yokuli 可以增加短时 Undo，但不能改变已提交布局的可预测性。

```text
Undo Pin
→ 恢复 transaction.before
```

## 18.4 Pin 失败

```text
entry 已固定
entry 不可固定
document 无法修复
```

必须明确返回状态，不静默失败。

---

# 19. Unpin

在 Edit Mode 点击 Unpin：

```text
创建 LayoutTransaction
移除 Tile
依照 Profile 执行局部布局协调
commit
persist
提供短时 Undo
```

不得：

```text
删除整个应用
停止 Runtime
删除 Feature 数据
```

Unpin 只影响 Start。

---

# 20. All Apps

## 20.1 内容来源

只显示 Catalog 中已安装 Entry。

Release Phase 0A：

```text
Chart
Settings
```

Shell Lab 使用大量 synthetic entries。

## 20.2 排序与字母分组

视觉层负责本地化：

```text
英文：locale collator + 首字母
中文：明确拼音 index 或本地化 jump group
未知：#
```

Engine 不参与语言排序。

## 20.3 不显示开发者术语

删除：

```text
core app
shortcut
hold to pin
```

用户只需要：

```text
应用名
必要的简短状态
context menu
```

## 20.4 Alphabet Jump

```text
点击分组字母
→ 打开 Jump Overlay
→ 只激活存在内容的分组
→ 选择后滚动到目标
```

Overlay 是 transient，Back 先关闭它。

---

# 21. Shell 导航层级

## Level 0 — System Overlay

```text
安全报警
关键错误
权限
```

## Level 1 — Launcher

```text
Start
All Apps
Edit Mode
Alphabet Jump
Recents
```

## Level 2 — Internal App

```text
Chart
Settings
以后新增的应用
```

## Level 3 — Feature 内部目的地

由 Feature 自己管理。

Engine 只保存 opaque navigation snapshot 或 last launch token，不知道 Chart Mode。

## Level 4 — Transient

```text
Context Menu
Inspector
Dialog
Sheet
```

---

# 22. Home、Back 与内部 Task

## 22.1 Feature App Bar 删除 Home

`Home` 不属于 Chart 或 Settings 的 Action。

从所有 Feature App Bar 删除 Home 图标。

## 22.2 Back

```text
Transient 打开
→ 关闭 Transient

Edit 正在 Drag/Resize
→ 取消 provisional 操作

Edit Idle
→ 退出 Edit

Internal App 有内部 back
→ Feature 处理

Internal App 已到根
→ Start

All Apps
→ Start

Start
→ standalone 交给 Android
```

## 22.3 Home Intent

`home` flavor 收到 Android HOME intent：

```text
始终显示 Start
不重复创建 Activity
不清除内部 App task
不停止未来 Runtime
```

需要：

```text
launchMode / onNewIntent
确定性 reducer action
```

## 22.4 Internal App Task

```kotlin
data class InternalAppTask(
    val taskId: InternalAppTaskId,
    val appId: LauncherAppId,
    val lastLaunchToken: LaunchToken,
    val savedUiStateKey: String?,
)
```

Phase 0 每个 App 只保留一个 UI Task。

---

# 23. Motion Runtime

Core Engine 决定：

```text
语义目标
允许的 motion family
开始状态与目标状态
```

Compose Motion Runtime 决定：

```text
帧同步位移
Animatable
Pager offset
GraphicsLayer
Velocity settle
```

## 23.1 Motion 分类

```text
DIRECT:
Start ↔ All Apps
Tile Drag
Sheet Drag

SETTLE:
Page Snap
Drop
Resize
Return
Neighbor movement

NAVIGATION:
Tile → App
App → Start
Feature drill-in
Transient

SAFETY:
立即出现，无装饰延迟
```

## 23.2 中断

所有非 Safety 动效必须支持：

```text
新 pointer
Back
Home
Activity pause
Profile/viewport change
```

中断后从当前视觉状态继续，不跳回起点。

## 23.3 Reduced Motion

```text
保留直接操纵
取消大幅 3D
导航改为短 Fade/Slide
安全状态仍立即显示
```

---

# 24. Renderer 合同

## 24.1 Engine Render Model

```kotlin
data class LauncherRenderState(
    val surface: LauncherSurface,
    val geometry: ResolvedStartGeometry,
    val startTiles: List<TileRenderModel>,
    val paging: PagingRenderModel,
    val edit: EditRenderModel?,
    val transient: TransientRenderModel?,
)
```

## 24.2 Compose Renderer

负责：

```text
Custom Layout
HorizontalPager
Canvas / vector icon
graphicsLayer
文本与本地化
Compose semantics
```

不负责：

```text
决定布局事实
修改 Catalog
直接持久化
直接启动 App
理解 Chart/NMEA
```

## 24.3 Feature Host

Chart 或 Settings 被打开后，Launcher Renderer 只显示 Host。

Feature UI 不被 Start/All Apps pager 包裹。

这样未来地图手势不会与 Launcher 分页竞争。

---

# 25. 图标系统

删除生产 Unicode glyph：

```text
⌖ ⚓︎ ◒ ▤ ⚙︎ ➤ ≋
```

建立：

```text
LauncherIconId
MarineLauncherIconRenderer
```

图标使用：

```text
VectorDrawable
ImageVector
Canvas Path
```

要求：

```text
统一 viewBox
统一 optical center
统一线宽
无 emoji fallback
支持 Dark/Light/Accent
```

Engine 只保存 `LauncherIconId` 或完全不保存视觉信息。

---

# 26. Tile 内容区

每种尺寸建立明确模板。

## Small

```text
中心或左上 Icon
可选小 Badge
不显示多行文本
```

## Medium

```text
左上 Icon
中部一个主事实
下方辅助事实
左下 Title
```

## Wide

```text
左上 Icon
主事实或轻量 preview
辅助事实
左下 Title
```

安全事实不得被装饰性轮播隐藏。

Phase 0 不实现复杂 Flip/Cycle Tile；先实现：

```text
STATIC
ICONIC
LIVE_FACT
SAFETY
```

---

# 27. Status Strip

Shell 状态条属于 Launcher Android/Compose 层，不属于 Feature。

Phase 0 只显示真实平台数据：

```text
系统时间
真实电量/充电
```

未接入 GPS/NMEA 时：

```text
不显示假的 GPS / NMEA 状态
```

未来 Host 提供 `LauncherSystemStatus` 后再增加。

点击状态条打开的 System Center 留到对应阶段，但模型可预留 transient。

---

# 28. 持久化 Port

## 28.1 Contract

```kotlin
interface LauncherPersistencePort {
    suspend fun load(): LauncherPersistedState?
    suspend fun save(state: LauncherPersistedState)
    suspend fun reset()
}
```

## 28.2 持久化内容

```text
StartDocument
Theme
Accent
Language
Layout lock
上次 Launcher page
上次前台 App（按策略）
```

## 28.3 不持久化

```text
Paging progress
Drag offset
当前 pointer
正在播放的 transition
临时 Context Menu
未 commit proposal
```

## 28.4 Android 实现

使用：

```text
Proto DataStore
```

不要把布局编码成散落 Preferences key。

---

# 29. 恢复与修复

加载文档时执行：

```text
schema version 检查
Profile 检查
Entry existence
Tile instance uniqueness
Entry duplication policy
bounds
overlap
size support
```

## 29.1 修复顺序

```text
未知 Entry → 移除
不支持尺寸 → 回 Entry default size
越界 → 最近合法位置
重叠 → 确定性局部求解
无法修复 → default document
```

必须记录：

```text
LauncherIncident
```

不得使用：

```kotlin
getValue()
first { }
```

在恢复路径中直接崩溃。

---

# 30. HOME flavor Hardening

在设为默认桌面前必须完成：

```text
singleTask 或等价确定性实例策略
onNewIntent(HOME)
Home 始终回 Start
Safe Mode
Reset Start Layout
打开 Android Settings
切换默认 Home App 指引
Crash-loop counter
Recovery Surface
```

## Safe Mode

启动参数或连续崩溃触发：

```text
忽略自定义布局
禁用复杂动效
只显示 Chart / Settings / Android Settings
```

不能把用户锁死在损坏 Launcher 中。

---

# 31. Shell Lab

## 31.1 目的

Shell Lab 是 Engine 的压力场，不是用户产品。

## 31.2 场景

```text
SparseGrid
DenseGrid
MixedSizes
LongLocalizedTitles
60Tiles
AlarmStates
RapidTileUpdates
CorruptDocument
Square320
Square360
Portrait360
LandscapeTablet
FontScale150
```

## 31.3 控制

```text
重置场景
注入 catalog revision
删除 Entry
添加 Entry
模拟持久化损坏
模拟旋转
模拟进程恢复
模拟高频 Tile 更新
```

## 31.4 访问

仅：

```text
debug Settings
adb deep link
benchmark Activity
```

Release 无入口。

---

# 32. 性能工程

## 32.1 Benchmark 模块

新增：

```text
:benchmark:shell
:baselineprofile:shell
```

或等价结构。

## 32.2 关键旅程

```text
cold_start_to_start
warm_start_to_start
start_vertical_scroll_60_tiles
start_to_apps_slow_drag
start_to_apps_fast_fling
apps_to_start_cancel_drag
enter_edit_mode
drag_medium_over_30_tiles
drag_with_auto_scroll
resize_small_medium_wide
pin_from_apps
unpin_and_undo
open_chart_and_return
rotate_square_layout
restore_after_process_death
```

## 32.3 指标

```text
StartupTimingMetric
FrameTimingMetric
frameDurationCpuMs
frameOverrunMs
jank percentage
input-to-render latency（可通过 trace/自定义 marker）
```

## 32.4 目标

在指定中端真机和三星方屏上校准后设置门禁。

初始目标：

```text
指针移动 → 视觉反馈 <= 1 frame
60Hz 关键交互 jank < 1%
P95 frameOverrunMs <= 0
连续严重超时帧 = 0
```

CI 模拟器只做回归趋势，不把模拟器结果冒充真机最终结论。

## 32.5 Baseline Profile

覆盖：

```text
Start
All Apps
Edit
Pin
Chart launch
Back to Start
```

---

# 33. Compose 性能规则

## Pointer 每帧禁止

```text
DataStore 写入
JSON 编码
全 catalog 排序
全 document 重建
全屏字符串格式化
日志刷屏
数据库访问
Google Map 操作
```

## Pointer 每帧允许

```text
更新 Offset
查询预计算 occupancy
局部 collision proposal
graphicsLayer transform
```

## Tile 更新隔离

每个 Tile 应有独立 recomposition boundary。

一个 Tile 的 Live Fact 更新不能使全部 Tile 重组。

---

# 34. 无障碍与输入

必须保留：

```text
TalkBack click / long-click
48dp 最小语义触控区
键盘/D-pad 基础焦点
字体 1.0 / 1.3 / 1.5
状态不只靠颜色
Reduce Motion
```

编辑模式要有无障碍替代动作：

```text
Move up
Move down
Move left
Move right
Resize
Unpin
Done
```

不能只允许拖拽。

---

# 35. 测试体系

## 35.1 Core Engine Unit

```text
engineHasNoAndroidDependency
engineHasNoFeatureDependency
catalogAdditionDoesNotAutoPin
catalogRemovalRemovesOnlyMissingTiles
unknownEntryRepairPreservesOtherCells
duplicateEntryRejected
layoutProposalIsDeterministic
unaffectedTilesPreserveCoordinates
resizeUsesDeclaredCycle
transactionCommitPersists
transactionCancelRestoresBefore
undoRestoresBefore
homeDoesNotDeleteTasks
backPriorityIsDeterministic
```

## 35.2 Geometry

```text
wp8ProfileUsesSmallMediumWideOnly
mediumEqualsTwoSmallPlusSeam
wideEqualsFourSmallPlusThreeSeams
geometrySnapsToPhysicalPixels
square320HasValidBounds
square360HasValidBounds
fontScaleDoesNotChangeTileGeometry
```

## 35.3 Pager

```text
pageTracksFingerOneToOne
shortSlowDragCancels
longSlowDragCompletes
fastFlingCompletes
reverseDuringDragTracksImmediately
touchDuringSettleInterruptsContinuously
verticalIntentDoesNotPage
editModeDisablesPageSwipe
```

## 35.4 Drag

```text
grabOffsetIsPreserved
placeholderRemainsAtOrigin
neighborMovesBeforeDrop
cellHysteresisPreventsThrash
autoScrollKeepsTileUnderFinger
invalidDropReturnsOrigin
pointerCancelRestoresCommittedDocument
catalogChangeCancelsDragSafely
```

## 35.5 Pin / Resize / Unpin

```text
pinOpensContextMenuFirst
pinReturnsToStart
pinScrollsToNewTile
pinDoesNotDuplicateEntry
resizeShowsProposal
resizeFailureKeepsOldSize
unpinDoesNotDeleteApp
undoPinRestoresDocument
undoUnpinRestoresDocument
```

## 35.6 UI

```text
releaseShowsOnlyChartAndSettings
releaseHasNoFixtureMarineFacts
allAppsHidesInternalMetadata
featureAppBarsHaveNoHomeButton
startAndAppsAreInteractiveSiblings
editModeShowsUnpinAndResize
smallTileCollapsesText
statusStripUsesRealPlatformFactsOnly
shellLabAbsentFromRelease
```

## 35.7 Golden

```text
wp8_start_360_dark
wp8_start_360_light
wp8_start_320_square
wp8_all_apps_360
wp8_edit_medium
wp8_context_menu
wp8_alphabet_jump
wp8_tile_launch_plane
```

Golden 测试不能代替 Reference Lab 人工审核。

---

# 36. 分阶段施工计划

Codex 必须一阶段一阶段执行。

每完成一个阶段：

```text
提交代码
运行该阶段全部 Gate
输出报告
停止
等待人工审核
```

不得自动继续下一阶段。

---

## Stage 0 — Freeze & Reference Contract

### 目标

冻结当前基线，建立最终主文档和 WP Reference Lab。

### 施工

```text
创建新分支 codex/launcher-engine
记录起始 SHA
将本文件加入 docs/requirements
建立 docs/reference/wp8
建立 measurement schema
建立 golden screenshot 输出目录
建立 baseline lock 与 pre-existing implementation reconciliation
使用真实 Draft 2020-12 validator 验证正反 fixtures
输出完整 Stage 0 report
```

### 不改

```text
当前 UI 行为
Google Map
生产 Registry
```

### Gate

```text
文档可追踪
reference schema 可验证
baseline reconciliation 完整
Stage 0 report 明确停止并等待人工审核
CI 仍绿
```

### 提交

```text
docs(shell): correct stage0 reference and baseline contracts
```

---

## Stage 1 — Product Surface Reduction

### 目标

Release 只剩 Chart + Settings。

### 施工

```text
删除 feature:cockpit
删除 feature:library
feature:system → feature:settings
删除 Anchor/Navigation/Survey/Trips/NMEA 等生产入口
删除未标注 Fixture marine facts
Chart 只开放 Browse
Settings 只保留真实外观、语言、地图状态、关于
建立 debug-only shell-lab module boundary
```

### Gate

```text
Release Start 只有 Chart + Settings
Release All Apps 只有 Chart + Settings
无 Coming Soon
无假 SAFE/SOG/COG/Trip/NMEA
Shell Lab 不进入 Release
```

### 提交

```text
refactor(product): reduce release shell to chart and settings
```

---

## Stage 2 — Engine Contract Extraction

### 目标

Engine 与应用、Android、Compose 解耦。

### 施工

```text
创建 shell-contract
创建 shell-engine
创建 shell-compose
创建 shell-android
MarineAppId enum → opaque LauncherAppId
固定 Registry → contribution/catalog model
LaunchTarget → opaque LaunchToken
建立 LauncherHostPort
建立 InternalAppHostResolver
```

### Gate

```text
shell-engine 无 Android/Compose/Feature import
Release catalog 由 Chart/Settings contributions 组合
现有 Chart/Settings 能被 Shell 打开
```

### 提交

```text
refactor(shell): extract app-agnostic launcher engine contracts
```

---

## Stage 2.5 — WP8 Reference Acquisition & Human Approval

### 目标

在任何 WP8 几何、拖拽、分页或转场实现成为规范前，取得合法、可追溯且由人工批准的真实 Reference 证据。

### 施工

```text
采集合法 WP8 截图与视频
为每个 capture 记录路径、SHA-256、字节数、MIME、尺寸、来源与所有权/许可
按 scenario 建立 geometry 与 motion measurementSets
直接操控场景同时记录 input timeline 与 visual samples
计算 measurementSets canonical JSON SHA-256
完成 reviewer、review time、decision、notes 与 approved profile revision
```

### Gate

```text
WP8_REFERENCE_MEASUREMENTS.json 通过 Draft 2020-12 schema
所有 capture 内容哈希与仓库文件一致
核心 geometry 和 motion scenario 均有真实证据
review decision = APPROVED
measurement status = HUMAN_REVIEWED
reviewedMeasurementHash 与 measurementSets canonical JSON 一致
```

没有上述人工审核结果，必须停止；不得进入 Stage 3，也不得用当前渲染器、fixture、估算比例或视觉印象补位。

### 提交

```text
docs(shell): approve wp8 reference evidence for geometry
```

---

## Stage 3 — WP Geometry & Start Document

### 目标

建立标准 Small/Medium/Wide 与二维文档。

### 施工

```text
移除 2×1 和 HERO 命名
建立 WpReferenceProfile
建立 WpStartGeometry
建立 StartDocument
建立 TilePlacement explicit cells
建立 document validator / repair
默认布局 Chart Wide + Settings Small
```

### Gate

```text
WP8 reference measurement status = HUMAN_REVIEWED
review decision = APPROVED
reviewed measurement hash 已验证
几何公式 Unit 通过
320×320 / 360×360 bounds 通过
布局可表示空白
不再依赖列表顺序作为位置
```

### 提交

```text
refactor(shell): establish wp8 geometry and spatial start document
```

---

## Stage 4 — Engine State, Effects & Persistence Ports

### 目标

消除 ShellActivity 中的 remember 状态真值。

### 施工

```text
LauncherReducer
LauncherEngine controller
LauncherEngineState
LauncherAction
LauncherEffect
LayoutTransaction
Undo
LauncherPersistencePort
ShellViewModel / Controller
```

先用内存 Persistence Adapter 测试，再接 Proto DataStore。

### Gate

```text
Activity 重建后 document 恢复
transaction cancel/undo 通过
损坏 document 有确定性 fallback
```

### 提交

```text
feat(shell): add deterministic engine state and layout transactions
```

---

## Stage 5 — Interactive Start / All Apps Pager

### 目标

分页完全跟手。

### 施工

```text
删除 SwipeSurface
删除固定 90px threshold
使用 InteractiveLauncherPager
接入 HorizontalPager 或等价 direct-manipulation 基础
axis lock
velocity settle
可中断
Edit 禁用分页
```

### Gate

```text
慢拖跟手
滑一半返回
快速 fling
动画中重新按下
纵向滚动不误分页
320 方屏手感正常
```

### 提交

```text
feat(shell): implement direct-manipulation start and apps paging
```

---

## Stage 6 — Custom Spatial Grid

### 目标

Start 不再是 order + global reflow。

### 施工

```text
自定义 Start Layout
occupancy index
explicit cells
pixel snapping
局部 collision solver
proposed layout
邻居 graphicsLayer movement
空白 policy
```

### Gate

```text
拖动一个 Tile 不改变无关 Tile 坐标
proposed layout 可视
没有全局 top-left repack
60 synthetic tiles 仍稳定
```

### 提交

```text
feat(shell): add spatial wp8 start-grid engine
```

---

## Stage 7 — Complete Edit / Drag / Resize

### 目标

建立完整桌面编辑。

### 施工

```text
long-press state
haptic
grab offset
drag
placeholder
hysteresis
auto-scroll
drop/cancel
resize proposal
edit controls
无障碍移动动作
```

### Gate

```text
拖动全程跟手
邻居放下前让位
边缘自动滚动
取消无脏状态
Small/Medium/Wide resize 正确
```

### 提交

```text
feat(shell): complete tile drag resize and edit interactions
```

---

## Stage 8 — Pin / Unpin / Context Menu

### 目标

完整模拟 Launcher 安装入口逻辑。

### 施工

```text
All Apps context menu
Pin transaction
返回 Start
滚动到新 Tile
高亮
Unpin
Undo
重复 Pin 处理
catalog change reconciliation
```

### Gate

```text
Pin 不再 silent append
用户知道新 Tile 在哪里
Unpin 不影响 App
新增 Entry 不自动 Pin
移除 Entry 不重排全部桌面
```

### 提交

```text
feat(shell): implement launcher-grade pin unpin and catalog reconciliation
```

---

## Stage 9 — App Navigation & Motion

### 目标

层级和转场与 Launcher 语义一致。

### 施工

```text
Feature App Bar 删除 Home
Back 优先级
Home Intent action
InternalAppTask
Tile transition plane
Turnstile/Swivel/Slide
MapView 延迟挂载
Reduced Motion
```

### Gate

```text
Chart/Settings App Bar 无 Home
Back 根页面回 Start
Home 不销毁 task
MapView 不参与复杂 shared transform
动效可中断
```

### 提交

```text
feat(shell): unify launcher navigation and app transition semantics
```

---

## Stage 10 — Durable Storage & Recovery

### 目标

真正可作为 Launcher 长期使用。

### 施工

```text
Proto DataStore
layout/theme/accent/language 持久化
document migration strategy
repair
crash-loop recovery
Safe Mode
Reset Layout
Android Settings escape
onNewIntent HOME
```

### Gate

```text
进程死亡恢复
旋转恢复
损坏布局恢复
连续崩溃进入 Recovery
HOME 版不会锁死用户
```

### 提交

```text
feat(shell): add durable launcher restore and home recovery
```

---

## Stage 11 — Performance & Fidelity Gate

### 目标

用数据证明壳子足够好。

### 施工

```text
Macrobenchmark
Baseline Profile
Startup Profile
Jank tracking
golden screenshots
Reference Lab comparison
真机 60/90/120Hz 测试
三星方屏测试
```

### Gate

```text
核心旅程达到帧预算
无明显 input latency
Golden 人工通过
方屏真实设备通过
```

### 提交

```text
perf(shell): enforce wp8 launcher frame and fidelity gates
```

---

# 37. Shell Engine 最终 Definition of Done

只有以下全部通过，才允许开始接真实海事功能：

```text
[ ] Release 只有 Chart 与 Settings。
[ ] Engine 不依赖应用、Android、Compose 或 Marine Domain。
[ ] Renderer 可以被替换。
[ ] Start ↔ All Apps 与手指 1:1 跟随。
[ ] 分页可取消、可中断。
[ ] 纵向滚动与横向分页不打架。
[ ] Edit Mode 不会误切页。
[ ] Start 保存真实二维位置。
[ ] Tile 使用 Small / Medium / Wide。
[ ] Tile 几何通过 WP Reference。
[ ] 拖动保持 grab offset。
[ ] 邻居实时让位。
[ ] 自动滚动可用。
[ ] 不再全局 top-left reflow。
[ ] Pin 有 context menu、回 Start、定位新 Tile。
[ ] Resize 有 proposal 和 settle。
[ ] Pin / Unpin / Resize 支持 Undo。
[ ] Catalog 新增不自动 Pin。
[ ] Catalog 删除不破坏其他 Tile。
[ ] Feature App Bar 中没有 Home。
[ ] Back/Home 层级确定。
[ ] MapView 不参与重型 3D shared transform。
[ ] 布局、主题、语言可持久化。
[ ] 损坏布局可恢复。
[ ] HOME flavor 有 Safe Mode 和 Android Settings 逃生。
[ ] Shell Lab 与 Release 完全隔离。
[ ] Macrobenchmark 达标。
[ ] 320×320 和 360×360 通过。
[ ] 真实三星方屏通过。
[ ] 人工 WP8 Reference Review 通过。
```

---

# 38. 完成前禁止接入

```text
GPS
NMEA
Anchor Watch
Trip Recording
Active Navigation
Survey
OpenSeaMap
MBTiles
AIS
Weather
Tide
Foreground marine runtime
```

Google Maps Adapter 只保留现有基础，不继续扩展海事业务。

---

# 39. Shell 完成后的第一条真实功能

Shell Engine 完成并通过人工验收后，才进入：

```text
Phase 1:
Chart Browse + Phone Position
```

第一条真实数据链：

```text
Android GNSS
→ Marine Runtime Port
→ Position Observation
→ Chart
→ Chart Tile
```

不能在 Shell 尚未冻结时开始迁移旧应用流程。

---

# 40. Codex 每阶段报告格式

每个 Stage 结束必须输出：

## Baseline

```text
stage:
starting SHA:
ending SHA:
branch:
```

## Scope

```text
implemented:
explicitly not implemented:
```

## Architecture

```text
modules changed:
dependency direction:
engine forbidden imports:
```

## Interaction

```text
state transitions added:
cancel behavior:
back/home behavior:
```

## Tests

```text
unit:
compose:
golden:
benchmark:
lint:
assemble standalone:
assemble home:
```

## Hardware

```text
API 34 emulator:
API 36 emulator:
Samsung square:
refresh rate:
```

未验证必须写：

```text
UNVERIFIED_HARDWARE
NOT YET MEASURED
```

不得用：

```text
looks smooth
should work
```

代替数据。

## Stop

每份报告最后必须写：

```text
STOPPED AT STAGE GATE.
AWAITING HUMAN REVIEW BEFORE NEXT STAGE.
```

---

# 41. 给 Codex 的首次执行指令

首次只执行：

```text
Stage 0
```

不得自动执行 Stage 1。

v1.1 的首次执行指令已由仓库所有者修正；Stage 0 correction 只执行：

```text
1. 从当前 codex/launcher-engine HEAD 98121412893d5331b22d4327463794993a4a4eff 纠正 Stage 0。
2. 记录 master reviewed SHA、实际 starting SHA、initial Stage 0 ending SHA、Master 新旧哈希与覆盖理由。
3. 对账所有提前存在的 Shell Engine 候选实现，不得提前批准后续 Stage。
4. 重建状态相关、内容寻址、可人工签核的 WP8 measurement schema 与正反 fixtures。
5. 在 CI 使用真实 Draft 2020-12 validator。
6. 插入 Stage 2.5 人工 Reference Gate；只在 Stage 2 完成人工审核后执行，未批准不得进入 Stage 3。
7. 不修改当前 UI、Registry、Google Maps、Feature、Shell Engine 生产代码或海事行为。
8. 提交：docs(shell): correct stage0 reference and baseline contracts
9. 运行全部 CI。
10. 输出完整 Stage 0 报告并停止，不得执行 Stage 1。
```

---

# 42. 参考依据

本设计采用以下稳定原则：

```text
Windows Phone 8 经典 Tile 使用 Small / Medium / Wide 三类；
公开资源尺寸体现 1×1、2×2、4×2 的比例关系。

可固定入口可以代表应用本身，也可以代表应用内部的深链接体验；
是否创建固定入口应由用户决定。

Android 原生 Compose 提供自定义布局、直接拖动、分页、帧同步动画、
Macrobenchmark 和 Baseline Profile 等能力，足以建设 Shell；
业务引擎与 Renderer 分离后仍保留更换 Start Renderer 的余地。
```
