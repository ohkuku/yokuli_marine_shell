# Yokuli OS WP8 UI System — Requirement Contract

Status: `ACTIVE`
Scope: Shell navigation, every core application page, motion, touch feedback, accessibility, and delivery evidence.

## Product intent

Yokuli OS adopts the Windows Phone 8 design language as a system, not as decoration. The app remains a marine chart product: WP8 defines hierarchy, typography, navigation, motion, and interaction feedback; Chart, Anchor, Trip, NMEA, Sonar, Anchorages, and Navigation retain their own marine safety semantics.

The canonical implementation pattern is documented in [`docs/WP8_UI_PATTERN.md`](../WP8_UI_PATTERN.md). New feature work must start from this contract and that pattern.

## Source basis

- Microsoft describes the phone design as clean, light, open, fast, typography-led, and “alive in motion”: [Design Your Windows Phone Apps to Sell](https://learn.microsoft.com/en-us/archive/msdn-magazine/2012/january/windows-phone-design-your-windows-phone-apps-to-sell).
- Microsoft’s phone guidance identifies Pivot, Panorama, the one-handed vertical page, Back navigation, and the compact Application Bar as core phone idioms: [Building Apps for Windows 8 and Windows Phone 8](https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/july/windows-8-building-apps-for-windows-8-and-windows-phone-8) and [Common Design Principles](https://learn.microsoft.com/en-us/archive/msdn-magazine/2011/december/windows-phone-how-to-translate-common-design-principles-to-the-windows-phone).
- Microsoft’s navigation guidance names Turnstile for movement between spaces, Continuum for carrying context, and Swivel for transient surfaces: [Windows Phone Navigation, Part 2](https://learn.microsoft.com/en-us/archive/msdn-magazine/2011/april/msdn-magazine-mobile-matters-windows-phone-navigation-part-2-advanced-recipes).
- Current Microsoft motion guidance requires transitions to communicate hierarchy and remain fast and informative: [Page transitions](https://learn.microsoft.com/en-us/windows/apps/design/motion/page-transitions) and [XAML animation](https://learn.microsoft.com/en-us/windows/apps/develop/motion/xaml-animation).
- Android recommends `AnimatedContent` for swapped content, `Transition` for coordinated values, `Animatable` for sequenced motion, and draw-layer `graphicsLayer` transforms for efficient animation: [Choose an animation API](https://developer.android.com/develop/ui/compose/animation/choose-api) and [Animation quick guide](https://developer.android.com/develop/ui/compose/animation/quick-guide).

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
