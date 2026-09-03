# Yokuli OS — Canonical WP8 UI Pattern

Status: `MANDATORY FOR NEW UI`
Owner: `core:design`
Requirement contract: [`requirements/WP8_UI_SYSTEM_REQUIREMENTS.md`](requirements/WP8_UI_SYSTEM_REQUIREMENTS.md)

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
- App title: 44sp light, lowercase, one line, semantic tag `wp-page-title-<app>`.
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
