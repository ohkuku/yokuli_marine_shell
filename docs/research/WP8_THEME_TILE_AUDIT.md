# WP8 Theme and Tile Detail Audit

Status: `IMPLEMENTATION INPUT`
Date: 2026-09-04
Scope: theme ownership, Start tile geometry/content, touch response, and the differences found in Yokuli OS before this refinement.

This is a research record, not an instruction source. The user request and the active Yokuli product specifications define scope; the linked Microsoft material supplies historical design evidence.

## Evidence extracted from Microsoft material

### Theme is a resource system

Windows Phone design guidance tells applications to use theme resources for brushes, colors, and fonts so UI follows user preferences. The platform exposed resources such as `PhoneAccentBrush`, `PhoneBackgroundBrush`, `PhoneForegroundBrush`, `PhoneChromeBrush`, high/mid/low text brushes, and pressed-state brushes.

For Windows Phone 8.x secondary tiles, Microsoft explicitly documents `BackgroundColor = transparent` as the way to inherit the accent selected by the user in Settings. If a secondary tile does not set a background color, it inherits the parent app tile color. Yokuli therefore needs a Shell-owned theme resource, not a color field owned by each launcher entry.

### Tile dimensions encode one repeated seam

Microsoft lists WP8 tile source dimensions:

| Tile | Source size |
|---|---:|
| Small | 159×159 px |
| Medium | 336×336 px |
| Wide | 691×336 px |

Two small tiles plus an 18 px seam equal one 336 px medium tile. Two medium tiles plus a 19 px seam equal one 691 px wide tile. At the Android density used by the design grid this is represented by one 6 dp seam token. The important behavior is not the isolated number: horizontal/vertical gaps, outer Start gutter, and compound-size math must use the same token, with no Card margin layered on top.

### Tiles are entry points, not miniature dashboards

Microsoft describes primary tiles as app entry points and secondary tiles as deep links. Live tiles surface the most relevant, glanceable content. Yokuli tiles therefore use three stable zones:

```text
upper-left     identity glyph
optical middle one current fact plus subordinate detail
lower-left     stable entry title
```

Safety/freshness remains explicit text. A small semantic indicator may retain red/yellow/green meaning, but the tile plane continues to inherit the Shell accent.

### Touch has a pressed response, not desktop hover chrome

WP is touch-first. A tile should react as one plane toward the press coordinate, then return promptly. On Android the equivalent must transform the layer that contains both background and content, preserve `combinedClickable` semantics, consume no extra pointer event, and avoid Material ripple/elevation. Mouse hover is not used as a substitute for touch feedback.

### Typography and chrome remain subordinate

Microsoft describes the language as clean, light, open, typography-led, content-first, and alive in motion. Primary actions belong in the bottom Application Bar so a hand does not obscure content. This refinement does not add decorative chrome to compensate for sparse tiles.

## Pre-change audit findings

| Area | Existing behavior | Required correction |
|---|---|---|
| Theme ownership | no Shell theme object | one `WpThemeSpec` and CompositionLocal source |
| Start colors | Ocean, Safe, Cyan, DeepOcean, and Stale per tile | one user-selected accent for every tile plane |
| Safety color | SAFE changed the whole Anchor tile green | small semantic indicator plus explicit SAFE text |
| Light theme | static black/white constants | background/foreground/chrome resolve from theme |
| Tile layer | background modifier preceded the perspective modifier | tilt wraps background and content as one plane |
| Grid rhythm | 8 dp outer gutter, 6 dp internal seam | one 6 dp seam/gutter token |
| Tile identity | forced uppercase and competed with live values | stable lower-left title in natural casing |
| Touch targets | 30/42/46 dp interactive controls existed | 48 dp semantic target with smaller inner glyph if needed |
| Settings path | no functional theme UI | System → Display controls background and accent |
| Verification | motion end-state only | pure theme tests plus real-Activity theme propagation story |

## Primary sources

- [Microsoft: WP8.x secondary tiles inherit the system accent](https://learn.microsoft.com/en-us/uwp/api/windows.ui.startscreen.secondarytilevisualelements.backgroundcolor)
- [Microsoft: WP8 tile types and 159/336/691 source dimensions](https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/september/windows-phone-upgrading-windows-phone-7-1-apps-to-windows-phone-8)
- [Microsoft: theme resources, visual/control/branding review](https://learn.microsoft.com/en-us/archive/msdn-magazine/2012/january/windows-phone-design-your-windows-phone-apps-to-sell)
- [Microsoft: clean composition, negative space, bottom Application Bar](https://learn.microsoft.com/en-us/archive/msdn-magazine/2011/december/windows-phone-how-to-translate-common-design-principles-to-the-windows-phone)
- [Microsoft: classic WP page header and 12 px content margin example](https://learn.microsoft.com/fr-fr/sharepoint/dev/general-development/how-to-configure-and-use-push-notifications-in-sharepoint-apps-for-windows)
- [Microsoft: deep-link role of secondary tiles](https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/july/windows-8-building-apps-for-windows-8-and-windows-phone-8)
