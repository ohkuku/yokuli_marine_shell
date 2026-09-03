# ADR 0001 — Native Compose engine for WP8 motion

Status: Accepted
Date: 2026-09-04

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
