# Yokuli OS — Canonical WP8 UI Pattern

状态：`MANDATORY FOR NEW UI`
所有者：`core:design`
需求合同：[`requirements/WP8_UI_SYSTEM_REQUIREMENTS.md`](requirements/WP8_UI_SYSTEM_REQUIREMENTS.md)
响应式边界：[`UI_REACTIVE_ARCHITECTURE.md`](UI_REACTIVE_ARCHITECTURE.md)

## 中文（主文）

这是每个 Yokuli OS 新功能必须复用的设计与评审基线。WP8 负责层级、排版、导航、动效与触控反馈；海图产品语义仍由 Chart、Anchor、Trip、NMEA、Sonar、Anchorages 和 Navigation 的领域合同定义。它既不是普通磁贴 Demo，也不复制第三方海图产品的外观。

### 0. 主题与 Start 不变量

主题是 Shell 状态。唯一输入 `WpThemeSpec` 解析背景、前景、弱化文字、chrome、accent、accent 对比色和安全语义色。功能页面不得私自定义 Shell 黑/白/青色。所有 Start 磁贴使用同一 accent 平面；SAFE、WARNING、ALARM、STALE、OFF 只能用文字和小型状态标记，不能给整块磁贴重新染色。海水、陆地、航线、声呐和照片是领域内容，可以保留独立调色板。

Start 使用四等分网格和唯一的 `6dp` seam；左右 gutter、横纵间隙和复合尺寸计算都复用它。磁贴无圆角、描边、阴影和 elevation。内容固定为左上 glyph、中部一个主要事实和次要详情、左下稳定入口名。small 收起实时详情但不缩小触控面。快捷磁贴是核心 App 的 deep link，不是假 App。

### 1. 页面骨架与信息层级

每个核心 App 依次为：Shell 状态条、左上 44sp 轻字重应用名、accent 模式/section 行、一个主要任务、稀疏的支持信息、固定底部 Application Bar。应用名跨页面稳定；模式放第二行，例如“海图／锚泊”。海事值必须同时呈现 freshness/source/conflict；无数据用 `—`、陈旧、保持或关闭，动效不能暗示数据仍然实时。

### 2. 排版与构图

使用平台 sans-serif，不分发授权不明的 Segoe 字体。应用标题 44sp、单行；英文资源可以使用 WP 式小写，中文保持自然字形。主要数值 48–64sp，单位相邻但弱化；列表 20–24sp；metadata 9–13sp。默认页面边距 18dp。留白承担分组，禁止用 Material card、分割线墙或装饰容器填满界面。

### 3. 导航与手势

Start 左滑进入 All Apps；All Apps 右滑/Back 返回 Start。点击磁贴进入目标；长按只进入编辑，不得同时打开。拖动吸附网格，resize 只遍历入口支持的尺寸，unpin 不卸载 App。Home 返回 Start 但不能停止仍由 runtime owner 持有的 Anchor/Trip/Survey。所有导航必须使用 typed `LaunchTarget`／`ShellCommand`，不能使用字符串 route。

### 4. 动效与触控

同级使用 Slide；深入/返回使用方向相反的 Turnstile；临时覆盖层使用 Swivel；后台刷新使用克制 Fade；安全告警零延迟出现。Shell 转场只由 `WpMotionPolicy` 和 `WpSurfaceTransitionHost` 控制，页面内容用 `wpEntrance` 错峰。动效时长通常 120–300ms，不能阻挡输入、改变语义顺序或隐藏安全状态。

可点击的整块平面与内容一起按触点位置倾斜，中心近似下压、角落朝触点倾斜、释放精确归零；禁止 ripple、hover、高亮阴影、弹跳和只缩放内部图标。最小触控面 48dp。

### 5. Application Bar

全局主要动作放底部，使用圆形线框 glyph 和短标签；最多四个常驻动作，其余进入 overflow。选中状态可使用 accent 填充，但不能改变按钮位置。必须提供本地化 content description 和稳定测试 tag。

### 6. 所有权

`core:design` 独占视觉 token、动效和共用控件；`feature:*` 只拥有内容构图、`UiState`、`UiAction` 和 UI projector；`core:model`/domain/runtime 不得包含资源 ID、展示文字、图标、颜色或 Compose 类型。Composable 只接收 state 并发 action，功能接入不能修改统一页面骨架。

### 7. 新功能流程与检查

先写中英双语需求和 `UiState`／`UiAction`，再用 fixture 完成布局，按 Red→Green 实现真实 Activity 故事，最后才能接入 Flow publisher。评审逐项检查：大标题/section 层级、唯一主任务、freshness/source、固定 App Bar、导航深度动效、整平面 tilt、48dp、主题传播、中英文资源 key 对齐、稳定 tag、无 feature 私有设计系统、无真实功能伪装。

## English translation

This is the reusable design and review baseline for every new Yokuli OS feature. It uses the Windows Phone 8 language to shape a marine product; it does not turn marine features into a generic tile demo and it does not copy third-party chart products.

## 0. Theme and Start invariants

Theme is Shell state, not a collection of feature colors. `WpThemeSpec` is the single input and `LocalWpTheme` resolves background, foreground, muted text, chrome, accent, contrast-on-accent, and the semantic safety palette. A feature must not hard-code black, white, or cyan for Shell chrome.

- Background is `dark` or `light`; accent is one of the Shell palette choices.
- Every default and user-pinned Start tile uses exactly the selected accent plane and its resolved contrast foreground.
- SAFE, WARNING, ALARM, STALE, and OFF never recolor the whole Start tile. Use explicit text and, when useful, one small semantic indicator.
- Chart water, land, routes, sonar imagery, photographs, and other domain visualizations keep feature-owned palettes; these are content, not Shell chrome.
- Theme changes recompose Start, All Apps, page headers, controls, and Application Bar immediately. Durable cold-start persistence belongs to the Shell-state storage slice and must be added behind the same `WpThemeSpec` boundary.

### Start grid construction

The grid has four equal units and one repeated `6dp` seam. The same token is the left/right Start gutter, the horizontal and vertical tile gap, and the join inside compound tile math:

```text
small  = one grid unit
medium = small × 2 + seam, in both axes
wide   = medium × 2 + seam, horizontally
hero   = wide × medium
```

This mirrors the proportions encoded by Microsoft’s WP8 source sizes: 159, 336, and 691 pixels (`159 + 18 + 159 = 336`; `336 + 19 + 336 = 691`). Tiles are square-cornered planes with no border, elevation, shadow, or extra card margin.

### Tile content zones

```text
┌──────────────────────────────┐
│ glyph                 state ▪│  upper identity / subordinate state
│                              │
│ one primary fact             │  optical middle
│ subordinate detail           │
│                              │
│ stable entry title           │  lower-left identity
└──────────────────────────────┘
```

Small tiles keep the glyph and title, then collapse live detail. Do not shrink the touch plane to preserve text. A secondary tile is a deep link into its owning core app, not a pretend independent application.

## 1. Page anatomy

Every core application destination follows this vertical grammar:

```text
┌──────────────────────────────────────┐
│ system status strip                  │  Shell-owned, never app-owned
├──────────────────────────────────────┤
│ app name                             │  44sp, light, lowercase, upper-left
│ CURRENT MODE / SECTION        context│  11sp, accent/muted, optional
│                                      │
│ primary content                      │  One dominant job, flush left
│ secondary facts / lists              │  Sparse grouping, no card wallpaper
│                                      │
├──────────────────────────────────────┤
│  ◯       ◯       ◯       ◯       …  │  Fixed Application Bar
│ label   label   label   label         │
└──────────────────────────────────────┘
```

Use `WpPageHeader` for application identity and `WpApplicationBar` for page actions. The application name is stable across the app; its mode belongs on the second line. For example, Anchor is `chart / ANCHOR`, not a visually unrelated “Anchor app.”

### Required hierarchy

1. App name: identity, not an instruction.
2. Mode/section: current context.
3. Primary state: what is happening now.
4. Primary action: what the user can safely do next.
5. Supporting details: units, freshness, source, history.

For marine data, a value is incomplete without state. Show `—`, `STALE`, `HELD`, `OFF`, source, or conflict explicitly; never let animation imply that unavailable data is live.

## 2. Typography and composition

- Use one platform sans-serif family and express hierarchy with size, weight, case, and space. Do not ship Segoe assets without a verified redistribution right.
- App title: 44sp light and one line; English resources may use WP-style lowercase while Chinese keeps natural casing. Semantic tag: `wp-page-title-<app>`.
- Primary numeric value: 48–64sp light; its unit is visually secondary but remains adjacent.
- Section/list label: 20–24sp light.
- Metadata/status: 9–13sp regular; accent for active context, muted for supporting context.
- Default horizontal page margin: 18dp. Start uses the repeated 6dp seam/gutter token.
- Prefer edge alignment and negative space to nested surfaces. A colored block must convey state, grouping, or a tappable target—not decoration.

## 3. Navigation and gestures

The Shell owns global navigation history. An app owns local mode/section state.

| User intent | Structure | Motion family | Example |
|---|---|---|---|
| Move to peer surface | sibling | Slide | Start ↔ All Apps, Pivot page |
| Open a workspace | deeper | Turnstile forward | Start → Chart |
| Return/Home | shallower | Turnstile backward | Chart → Start |
| Preserve an object across drill-in | contextual deeper | Continuum | Route row → route detail (future) |
| Show short-lived UI | transient | Swivel | overflow/menu/dialog |
| Raise safety-critical state | interrupt | None | anchor alarm/global alarm |

- Android system Back follows the Shell journal. Back and Home must not silently stop long-running Anchor, Trip, Navigation, NMEA, or Sonar runtime work.
- Start ↔ All Apps supports the visible arrow plus horizontal swipe.
- Long press is allowed for Pin/Edit, but all safety and primary tasks need a visible path.
- Swipe or drag may enhance a task; it must not be the only path to destructive or safety-relevant actions.

## 4. Motion grammar

Motion communicates origin, destination, and hierarchy. It is not a reward animation.

### Engine

Yokuli uses Jetpack Compose’s native animation stack:

- `AnimatedContent`: keeps outgoing/incoming destinations alive through a transition.
- `Animatable`: deterministic entrance progress and stagger sequencing.
- `graphicsLayer`: draw-phase translation, scale, alpha, perspective rotation, transform origin, and camera distance.
- `MutableInteractionSource`: preserves high-level click/long-click semantics while driving press state.

The engine entry points are:

- `WpMotionPolicy`: pure, JVM-tested intent → plan mapping.
- `WpSurfaceTransitionHost`: sibling slide plus 3D Turnstile/Swivel host.
- `Modifier.wpEntrance`: page-title/content stagger.
- `Modifier.wpTilt`: pointer-position press tilt.

No feature module invents durations or easing curves. If a new motion family is required, add its behavior contract and failing policy test first.

### Timing tokens

| Motion | Duration | Geometry |
|---|---:|---|
| sibling slide | 245ms | 100% horizontal travel |
| deeper forward Turnstile | 260ms | −22° Y, +12% X, left origin |
| shallower Turnstile | 235ms | inverse signs, right origin |
| transient Swivel | 220ms | +14° X, center origin |
| content entrance | 210ms | +34dp X, −4° Y; 34ms stagger |
| reduced-motion fallback | 120ms | fade only |
| safety-critical | 0ms | immediate |

Compose animation timing follows Android’s system animator-duration scale. Do not implement infinite decorative motion. Map/video/sonar updates may be continuous because they visualize live state, but they must have bounded per-frame work.

### Press response

WP phone has a touch press response, not desktop hover decoration. The pointer coordinate selects the direction; a center press depresses without rotation. The layer containing the background, content, and clipping plane tilts no more than 5° toward the finger and scales to 97.5%. It enters in 70ms, restores in 115ms, consumes no pointer event, and shares the action’s `MutableInteractionSource`. Do not add ripple, raised hover state, shadow, overshoot, or bounce.

`WpPressPolicy` is the pure geometry seam. Its center, corner, clamping, and release-to-rest cases must pass JVM tests before modifier changes are accepted.

## 5. Application Bar

- Fixed to the bottom of the current app page; uses the current theme chrome brush.
- Up to five primary actions on phone/square layouts.
- 48dp minimum target containing a 36dp, 2dp white circular outline.
- A concise lowercase label appears below the icon.
- Selected mode uses accent fill; disabled state must change semantics as well as color.
- Ellipsis represents actual secondary actions. Never render a decorative ellipsis with no menu in production.
- Home is a Shell shortcut, not a replacement for Android Back.

## 6. Component ownership

```text
app-shell
  owns Shell history, status strip, global transition intent
    ↓
core:design
  owns WP tokens, page header, app bar, motion policy/engine, tilt
    ↓
feature:*
  owns marine content, local modes, labels and actions
    ↓
future runtime/domain modules
  own Anchor/Trip/NMEA/Navigation/Sonar state and safety invariants
```

Feature UI may request a motion intent; it may not bypass safety state, forge data freshness, or own a long-running runtime’s lifecycle.

## 7. New-feature workflow

Every feature issue and pull request answers these in order:

1. What is the marine user outcome?
2. Which existing core app owns it?
3. Is it a sibling, deeper, contextual, transient, or interrupting surface?
4. What is the large app title and secondary mode label?
5. What is the one primary action?
6. What stale/off/conflict/alarm states must remain explicit?
7. Which JVM contract is written first?
8. Which real-Activity story proves the user path?
9. Which device/vessel claims remain unverified?

The required sequence is `Requirement → Red → Green → Refactor → Record`; see [`TDD_PLAYBOOK.md`](TDD_PLAYBOOK.md) and [`TDD_LOG.md`](TDD_LOG.md).

## 8. Review checklist

- [ ] Large upper-left app identity comes from `WpPageHeader`.
- [ ] Shell chrome resolves from `LocalWpTheme`; domain colors remain feature-owned.
- [ ] Every Start tile plane exposes the same selected accent; semantic state is subordinate and textual.
- [ ] Start gutter, gaps, and compound-size math use the single 6dp seam token.
- [ ] Tile content follows glyph / one live fact / lower-left identity zones.
- [ ] One dominant task is obvious without reading documentation.
- [ ] Page uses WP type/space hierarchy rather than card accumulation.
- [ ] Navigation depth selects a tested `WpNavigationIntent`.
- [ ] Content entrance is grouped and short; no gratuitous looping motion.
- [ ] Press feedback transforms the whole plane, stays within 5°/97.5%, and adds no ripple, hover elevation, or bounce.
- [ ] Touch target is at least 48dp and exposes click semantics/content description.
- [ ] App Bar labels are short and visible.
- [ ] Back/Home do not stop domain tasks.
- [ ] Stale/off/conflict and safety states are explicit.
- [ ] 320×320 and phone portrait stories pass at supported font scale.
- [ ] Test, build, and hardware/vessel evidence is recorded honestly.

## 9. Primary references

- [Microsoft: common phone design principles and Application Bar](https://learn.microsoft.com/en-us/archive/msdn-magazine/2011/december/windows-phone-how-to-translate-common-design-principles-to-the-windows-phone)
- [Microsoft: Windows Phone navigation and Turnstile/Continuum/Swivel](https://learn.microsoft.com/en-us/archive/msdn-magazine/2011/april/msdn-magazine-mobile-matters-windows-phone-navigation-part-2-advanced-recipes)
- [Microsoft: phone controls and one-handed vertical layout](https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/july/windows-8-building-apps-for-windows-8-and-windows-phone-8)
- [Microsoft: WP8.x transparent tiles inherit the selected system accent](https://learn.microsoft.com/en-us/uwp/api/windows.ui.startscreen.secondarytilevisualelements.backgroundcolor)
- [Microsoft: WP8 tile types and 159/336/691 source sizes](https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/september/windows-phone-upgrading-windows-phone-7-1-apps-to-windows-phone-8)
- [Microsoft: Windows Phone theme resources](https://learn.microsoft.com/en-us/previous-versions/windows/apps/ms653055%28v%3Dvs.105%29)
- [Microsoft: page transition hierarchy](https://learn.microsoft.com/en-us/windows/apps/design/motion/page-transitions)
- [Microsoft: typography hierarchy](https://learn.microsoft.com/en-us/windows/apps/design/signature-experiences/typography)
- [Android: choose a Compose animation API](https://developer.android.com/develop/ui/compose/animation/choose-api)
- [Android: animation performance and `graphicsLayer`](https://developer.android.com/develop/ui/compose/animation/quick-guide)
- [Android: gesture abstraction and semantics](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures)
