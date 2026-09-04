# ADR 0001 — Native Compose engine for WP8 motion

状态：`SUPERSEDED_BY_MASTER`。本 ADR 仅保留早期 Compose 动效选择的历史理由；当前 Renderer 可替换性、Motion Runtime 与依赖边界以 [`LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md`](../requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md) 为准。
日期：2026-09-04

## 中文（主文）

### 背景

Yokuli OS 需要 WP8 的深度转场、错峰排版进场和按触点倾斜；普通 crossfade 不足。同时方案必须支持无障碍、确定性测试、现有 Compose UI 和长期维护。

### 决策

直接使用 Jetpack Compose 动画原语，并由 `core:design` 独占 WP8 语义。纯 `WpMotionPolicy` 把导航意图映射为稳定几何；`WpSurfaceTransitionHost` 组合 `AnimatedContent` 生命周期和 `Animatable` 透视层；`WpPageHeader`、`WpApplicationBar`、`wpEntrance`、`wpTilt` 是当前共享构件。feature 选择意图和内容，不定义任意动效常量；安全关键展示解析为零时长。

不引入第三方引擎，因为这些是 UI 状态转场，不是预制矢量场景。Compose 已具备生命周期、协程序列、系统动画倍率、测试时钟和绘制层 3D 变换。Lottie/Rive 以后只可用于独立品牌插画，不能承载导航、实时船舶状态或安全反馈。

结果是动效意图可做 JVM 测试，真实帧仍需 instrumented/视觉检查，所有 feature 共用一个时长与导航语义来源；不宣称与已退役 WP compositor 像素级相同。

## English translation

Status: `SUPERSEDED_BY_MASTER`. This ADR preserves the earlier Compose rationale only; the Master Spec now governs renderer replaceability, Motion Runtime, and dependency boundaries.

## Context

Yokuli OS needs WP8-style depth transitions, staggered typography, and press tilt. A plain crossfade is insufficient. The solution also has to remain accessible, deterministic under tests, compatible with the existing Compose UI, and maintainable by feature teams.

## Decision

Use Jetpack Compose animation primitives directly and keep the WP8 semantics in `core:design`.

- A pure `WpMotionPolicy` converts semantic navigation intent into stable animation geometry.
- `WpSurfaceTransitionHost` combines `AnimatedContent` lifecycle with an `Animatable`-driven perspective layer.
- `WpPageHeader`, `WpApplicationBar`, `wpEntrance`, and `wpTilt` are the only shared public page/motion building blocks in this slice.
- Features select intent and content; they do not define arbitrary motion constants.
- Safety-critical presentation resolves to zero-duration motion.

## Why not a third-party engine

The required effects are UI-state transitions rather than authored vector scenes. Compose already provides transition lifecycle, coroutine sequencing, system duration-scale integration, test-clock support, and draw-layer 3D transforms. A second runtime would add version, lifecycle, semantics, and performance boundaries without supplying a missing capability.

Lottie/Rive may be reconsidered later for isolated branded illustrations, never for navigation, live marine state, or safety feedback.

## Consequences

- Motion intent can be unit tested without screenshots.
- Real frame behavior still requires instrumented and visual checks.
- Feature reviews have one source for timings and navigation meaning.
- Android animator scale controls normal Compose animation; immediate safety presentation is explicit in policy.
- Pixel-perfect parity with the retired WP compositor is not claimed.

## References

- [Android animation API decision guide](https://developer.android.com/develop/ui/compose/animation/choose-api)
- [Android animation quick guide](https://developer.android.com/develop/ui/compose/animation/quick-guide)
- [Android graphics layers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)
- [Microsoft Windows Phone transition recipes](https://learn.microsoft.com/en-us/archive/msdn-magazine/2011/april/msdn-magazine-mobile-matters-windows-phone-navigation-part-2-advanced-recipes)
