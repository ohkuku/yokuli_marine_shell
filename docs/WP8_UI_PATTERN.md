# Yokuli OS — Canonical WP8 UI Pattern

Status: `MANDATORY FOR NEW UI`
Owner: `core:design`
Requirement contract: [`requirements/WP8_UI_SYSTEM_REQUIREMENTS.md`](requirements/WP8_UI_SYSTEM_REQUIREMENTS.md)

This is the reusable design and review baseline for every new Yokuli OS feature. It uses the Windows Phone 8 language to shape a marine product; it does not turn marine features into a generic tile demo and it does not copy third-party chart products.

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
- App title: 44sp light, lowercase, one line, semantic tag `wp-page-title-<app>`.
- Primary numeric value: 48–64sp light; its unit is visually secondary but remains adjacent.
- Section/list label: 20–24sp light.
- Metadata/status: 9–13sp regular; accent for active context, muted for supporting context.
- Default horizontal page margin: 18dp. Start tiles remain on the Shell’s tighter 8dp grid.
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

Tiles and circular actions tilt no more than 5° toward the pointer and scale to 97.5%. The effect starts quickly, unwinds on cancellation/release, consumes no pointer event, and shares the action’s `MutableInteractionSource`.

## 5. Application Bar

- Fixed to the bottom of the current app page; black background.
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
- [ ] One dominant task is obvious without reading documentation.
- [ ] Page uses WP type/space hierarchy rather than card accumulation.
- [ ] Navigation depth selects a tested `WpNavigationIntent`.
- [ ] Content entrance is grouped and short; no gratuitous looping motion.
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
- [Microsoft: page transition hierarchy](https://learn.microsoft.com/en-us/windows/apps/design/motion/page-transitions)
- [Microsoft: typography hierarchy](https://learn.microsoft.com/en-us/windows/apps/design/signature-experiences/typography)
- [Android: choose a Compose animation API](https://developer.android.com/develop/ui/compose/animation/choose-api)
- [Android: animation performance and `graphicsLayer`](https://developer.android.com/develop/ui/compose/animation/quick-guide)
- [Android: gesture abstraction and semantics](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures)
