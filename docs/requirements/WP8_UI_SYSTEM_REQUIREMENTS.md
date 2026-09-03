# Yokuli OS WP8 UI System — Requirement Contract

状态：`ACTIVE`
范围：Shell 导航、所有核心 App 页面、动效、触控反馈、无障碍与交付证据。

## 中文（主文）

Yokuli OS 把 WP8 作为系统设计语言，而非表面装饰。它仍是海图产品：WP8 定义层级、排版、导航、动效和反馈；Chart、Anchor、Trip、NMEA、Sonar、Anchorages 与 Navigation 保留各自安全语义。

历史依据来自 Microsoft Design Language、Windows Phone 8 Start/App list/Application Bar/Turnstile/Swivel/tilt 的官方或归档材料。实现使用 Compose 重建可验证的交互语法，不分发来源不明的 Segoe 字体或受限资产。

- `UI-001`：每个核心 App 使用左上稳定大标题；mode/section 位于第二行；不得把深链伪装成独立 App。
- `UI-002`：排版和留白建立层级；禁止 Material card 墙、圆角/elevation/shadow 和无意义 chrome。
- `UI-003`：同级 Slide、深入/返回相反 Turnstile、临时界面 Swivel；Home/Back 语义与 runtime 生命周期分离。
- `UI-004`：标题和内容组采用短、错峰、左侧透视进场；安全状态不等待装饰动效。
- `UI-005`：整块触控平面按触点倾斜并精确归零；禁用 ripple、hover 和只动 glyph。
- `UI-006`：全局主要动作放固定底部 Application Bar，最多四个常驻按钮，均有本地化描述。
- `UI-007`：reduced-motion 下保留状态/导航语义并移除非必要空间动效；告警立即可见。
- `UI-008`：动效策略必须能用纯 JVM 测试方向、时长、角度、clamp 和 release。
- `UI-009`：真实 Activity 故事覆盖核心导航、320×320、主题、语言和 API 兼容；静态截图不算动效证明。
- `UI-010`：发布只能在测试、lint、设备 gate、签名和 checksum 全部通过后产生；未签名或失败制品必须明确标记。
- `UI-011`：一个 `WpThemeSpec` 驱动全 Shell，功能模块不得创建私有 Shell 主题。
- `UI-012`：Start 使用四列和一个 6dp seam；磁贴只有一个主要事实，安全色仅作从属标记。
- `UI-013`：所有磁贴、列表、跳转和设置选项至少 48dp，并共享 position-aware tilt。

本阶段不接入真实海图 SDK、NMEA/GPS、后台 runtime、固件或硬件验证；这些非目标不能被 UI fixture 冒充为已实现。

## English translation

## Product intent

Yokuli OS adopts the Windows Phone 8 design language as a system, not as decoration. The app remains a marine chart product: WP8 defines hierarchy, typography, navigation, motion, and interaction feedback; Chart, Anchor, Trip, NMEA, Sonar, Anchorages, and Navigation retain their own marine safety semantics.

The canonical implementation pattern is documented in [`docs/WP8_UI_PATTERN.md`](../WP8_UI_PATTERN.md). New feature work must start from this contract and that pattern.

## Source basis

- Microsoft describes the phone design as clean, light, open, fast, typography-led, and “alive in motion”: [Design Your Windows Phone Apps to Sell](https://learn.microsoft.com/en-us/archive/msdn-magazine/2012/january/windows-phone-design-your-windows-phone-apps-to-sell).
- Microsoft’s phone guidance identifies Pivot, Panorama, the one-handed vertical page, Back navigation, and the compact Application Bar as core phone idioms: [Building Apps for Windows 8 and Windows Phone 8](https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/july/windows-8-building-apps-for-windows-8-and-windows-phone-8) and [Common Design Principles](https://learn.microsoft.com/en-us/archive/msdn-magazine/2011/december/windows-phone-how-to-translate-common-design-principles-to-the-windows-phone).
- Microsoft’s navigation guidance names Turnstile for movement between spaces, Continuum for carrying context, and Swivel for transient surfaces: [Windows Phone Navigation, Part 2](https://learn.microsoft.com/en-us/archive/msdn-magazine/2011/april/msdn-magazine-mobile-matters-windows-phone-navigation-part-2-advanced-recipes).
- Current Microsoft motion guidance requires transitions to communicate hierarchy and remain fast and informative: [Page transitions](https://learn.microsoft.com/en-us/windows/apps/design/motion/page-transitions) and [XAML animation](https://learn.microsoft.com/en-us/windows/apps/develop/motion/xaml-animation).
- Android recommends `AnimatedContent` for swapped content, `Transition` for coordinated values, `Animatable` for sequenced motion, and draw-layer `graphicsLayer` transforms for efficient animation: [Choose an animation API](https://developer.android.com/develop/ui/compose/animation/choose-api) and [Animation quick guide](https://developer.android.com/develop/ui/compose/animation/quick-guide).
- Microsoft documents that a Windows Phone 8.x secondary tile with a transparent background inherits the system accent selected by the user, and that an unset secondary-tile color inherits its parent tile: [SecondaryTileVisualElements.BackgroundColor](https://learn.microsoft.com/en-us/uwp/api/windows.ui.startscreen.secondarytilevisualelements.backgroundcolor).
- Microsoft documents WP8 tile assets at 159×159, 336×336, and 691×336 pixels. The 18/19-pixel differences between adjacent source sizes establish a narrow, repeated seam rather than card spacing: [Upgrading Windows Phone 7.1 Apps to Windows Phone 8](https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/september/windows-phone-upgrading-windows-phone-7-1-apps-to-windows-phone-8).

## Required behavior

### UI-001 — Application identity

Given a user opens Chart, Cockpit, Library, or System
When the destination first becomes visible
Then a large, light-weight, lowercase application title is visible at the upper-left edge
And mode/section state is secondary to the application name
And the title exposes a stable `wp-page-title-<app>` semantic tag.

### UI-002 — Typography and space

Given a normal application page
When content is laid out
Then one sans-serif family, light/regular weight contrast, flush-left alignment, and deliberate negative space establish hierarchy
And chrome does not imitate card-heavy Material dashboards
And text may reflow rather than becoming horizontally scroll-bound at large font scales.

### UI-003 — Navigation-depth motion

Given a Shell surface changes
When Start and All Apps are siblings
Then a horizontal slide expresses lateral navigation.

Given a Shell surface changes
When an application is opened or closed
Then a perspective Turnstile transition expresses movement to or from a deeper space
And forward/backward transitions use opposite rotation and translation signs.

Given a transient overlay is shown
Then Swivel is the default pattern; a context-carrying drill-in may explicitly select Continuum.

### UI-004 — Page entrance choreography

Given a destination page enters
When its chrome and content are presented
Then the application title arrives first
And content groups arrive in a short stagger
And the bottom Application Bar remains spatially stable.

### UI-005 — touch feedback

Given a tile or icon action is enabled
When a pointer is held
Then the surface scales slightly and tilts toward the press position using perspective
And releasing or cancelling restores the neutral transform
And semantic click/long-click behavior remains available to accessibility services.

### UI-006 — Application Bar

Given a page exposes primary actions
When the page is visible
Then actions use a fixed bottom black bar, outlined circular icons, short labels, and a minimum 48 dp touch target
And destructive/safety actions are not hidden behind unexplained gesture-only affordances
And overflow is represented by an ellipsis when secondary actions exist.

### UI-007 — motion safety and accessibility

Given the platform animation duration scale is zero
When ordinary transitions run
Then Compose completes them without requiring an app-specific switch.

Given a safety-critical alarm or acknowledgement surface must appear
When it is raised
Then decorative movement is suppressed and the state becomes immediately legible
And motion never delays Anchor, Navigation, collision, stale-data, or emergency status.

### UI-008 — deterministic motion contract

Given a navigation intent
When the UI requests its motion plan
Then the plan is selected by a pure deterministic policy
And its family, duration, rotation, translation, and transform origin are JVM-testable without rendering frames.

### UI-009 — delivery evidence

Given a pull request or branch push
When GitHub Actions runs
Then unit tests, lint, both shell variants, API 34 UI stories, and an API 36 launch smoke form explicit gates
And a debug APK is called verified only after all required gates pass
And failures publish annotations, a Markdown job summary, test reports, and a bounded diagnostics bundle.

### UI-010 — signed releases

Given a valid semantic release tag or manual release input
When all quality, emulator, signing, and metadata checks pass
Then signed standalone and HOME APK/AAB artifacts are created, signatures and checksums are verified, and a GitHub Release is published
And no release is published from a dirty tree, a mutable existing tag, or a disallowed channel branch.

### UI-011 — shell theme resources

Given the Shell has a selected background mode and accent
When Start, All Apps, application chrome, or settings is composed
Then those surfaces resolve color through one immutable `WpThemeSpec`
And every Start tile uses the same selected accent and matching contrast foreground
And changing the setting updates the whole visible Shell without recreating feature-owned palettes.

Given a tile represents SAFE, WARNING, ALARM, STALE, or OFF
When the tile is shown on Start
Then the semantic state may use a small explicit indicator and text
But it does not replace the selected accent across the tile surface
And state remains understandable without color.

### UI-012 — tile geometry and content zones

Given a four-unit Start grid
When adjacent tiles are laid out
Then a single narrow 6 dp seam token is used horizontally, vertically, and at the Start canvas edge
And tile planes have zero elevation, zero border, and square corners
And wide/medium/hero dimensions are calculated only from the same cell and seam tokens.

Given a tile has enough room for live content
When its front is rendered
Then identity stays at the lower-left edge, its glyph stays upper-left, and one primary fact occupies the optical middle
And supporting detail is subordinate rather than competing with identity
And small tiles collapse detail before reducing touchability or legibility.

### UI-013 — authentic touch response

Given a tile receives touch input
When the pointer presses a position on its plane
Then the entire colored plane, content, and clipping layer depress together toward that position
And the response begins within 80 ms, scales no lower than 0.97, tilts no more than 5 degrees, and restores within 120 ms on release or cancellation
And no Material ripple, shadow, bounce, or desktop-hover-only affordance is introduced.

Given edit controls, alphabet jump cells, or launcher list identities are interactive
When they are rendered
Then their semantic touch target is at least 48 dp even when the visible glyph is smaller.

## Non-goals for this slice

- Pixel-perfect copying of copyrighted WP assets or fonts.
- Recreating Windows Phone OS APIs, system multitasking, or firmware behavior.
- Claiming real-device, GNSS, NMEA, Anchor Watch, sonar, or vessel verification from emulator tests.
- Porting the old runtime soak workflow before the corresponding runtime exists in this repository.

## Acceptance gate

```text
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
bash .github/scripts/test-ci-contract.sh
```

The final two hardware states remain explicit: `UNVERIFIED_HARDWARE` and `UNVERIFIED_VESSEL`.
