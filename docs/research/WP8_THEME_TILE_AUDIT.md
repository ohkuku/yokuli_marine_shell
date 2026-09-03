# WP8 Theme and Tile Detail Audit

状态：`IMPLEMENTATION INPUT`
日期：2026-09-04
范围：主题所有权、Start 磁贴几何/内容、触控反馈与优化前差距。

## 中文（主文）

这是研究记录，不是用户指令。用户需求和 Yokuli 的生效规格决定范围；Microsoft 材料只提供历史设计证据。

Microsoft 的 WP 主题资源证明颜色属于系统级偏好；深色是纯黑底/纯白前景，浅色精确反转。secondary tile 使用透明背景继承 accent，且 Phone 8.1 的磁贴前景固定为 light，因此 Yokuli 应由 Shell 统一拥有 `WpThemeSpec`，磁贴始终使用纯白前景，而不是每个入口保存颜色或按亮度自动切成黑字。WP8 的 159/336/691 px 磁贴尺寸体现一个重复 seam；新网格用同一 6dp token 表示外 gutter、横纵间隙和复合尺寸。

磁贴是入口，不是微型 dashboard：左上 glyph、中部一个当前事实与从属详情、左下稳定标题。安全/freshness 用文字与小标记，不能替换全磁贴 accent。触控时背景和内容作为整块平面朝按点倾斜并迅速回位，不用桌面 hover、Material ripple 或 elevation。主要动作继续放在底部 Application Bar。

优化前差距包括：无 Shell 主题、磁贴各自改色、SAFE 整块绿色、无浅色模式、tilt 未包住整平面、外 gutter 与 seam 不同、标题层级冲突、触控面小于 48dp、无 System → Display 主题路径和缺少真实 Activity 传播测试。下方英文为完整对照和来源。

## English translation

This is a research record, not an instruction source. The user request and the active Yokuli product specifications define scope; the linked Microsoft material supplies historical design evidence.

## Evidence extracted from Microsoft material

### Theme is a resource system

Windows Phone design guidance tells applications to use theme resources for brushes, colors, and fonts so UI follows user preferences. The platform exposed resources such as `PhoneAccentBrush`, `PhoneBackgroundBrush`, `PhoneForegroundBrush`, `PhoneChromeBrush`, high/mid/low text brushes, and pressed-state brushes.

For Windows Phone 8.x secondary tiles, Microsoft explicitly documents `BackgroundColor = transparent` as the way to inherit the accent selected by the user in Settings. If a secondary tile does not set a background color, it inherits the parent app tile color. Yokuli therefore needs a Shell-owned theme resource, not a color field owned by each launcher entry.

Microsoft's archived phone material describes the default pair as white text on black, reversed to black on white when the user selects the light theme. The Phone 8.1 secondary-tile API further states that tile foreground text on the phone is always light. These are separate rules: page foreground follows the black/white theme pair, while accent-tile foreground remains pure white. A generic luminance/contrast chooser is not the WP8 behavior.

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
| Theme pair | near-white/near-black approximations were allowed | Dark is exact black/white; Light is exact white/black |
| Host safe area | Android display cutout kept a fixed black strip in Light | system bars and short-edge cutout coverage follow the Shell mode |
| Tile foreground | generic contrast algorithm selected black on cyan | Phone tile foreground remains exact white across themes and accents |
| Tile layer | background modifier preceded the perspective modifier | tilt wraps background and content as one plane |
| Grid rhythm | 8 dp outer gutter, 6 dp internal seam | one 6 dp seam/gutter token |
| Tile identity | forced uppercase and competed with live values | stable lower-left title in natural casing |
| Touch targets | 30/42/46 dp interactive controls existed | 48 dp semantic target with smaller inner glyph if needed |
| Settings path | no functional theme UI | System → Display controls background and accent |
| Verification | motion end-state only | pure theme tests plus real-Activity theme propagation story |

## Primary sources

- [Microsoft: WP8.x secondary tiles inherit the system accent](https://learn.microsoft.com/en-us/uwp/api/windows.ui.startscreen.secondarytilevisualelements.backgroundcolor)
- [Microsoft: Phone 8.1 secondary-tile foreground is always light](https://learn.microsoft.com/en-us/uwp/api/windows.ui.startscreen.secondarytilevisualelements.foregroundtext)
- [Microsoft archive: Windows Phone uses white-on-black and reverses to black-on-white](https://learn.microsoft.com/en-us/archive/msdn-magazine/2010/december/msdn-magazine-ui-frontiers-silverlight-windows-phone-7-and-the-multi-touch-thumb)
- [Microsoft: WP8 tile types and 159/336/691 source dimensions](https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/september/windows-phone-upgrading-windows-phone-7-1-apps-to-windows-phone-8)
- [Microsoft: theme resources, visual/control/branding review](https://learn.microsoft.com/en-us/archive/msdn-magazine/2012/january/windows-phone-design-your-windows-phone-apps-to-sell)
- [Microsoft: clean composition, negative space, bottom Application Bar](https://learn.microsoft.com/en-us/archive/msdn-magazine/2011/december/windows-phone-how-to-translate-common-design-principles-to-the-windows-phone)
- [Microsoft: classic WP page header and 12 px content margin example](https://learn.microsoft.com/fr-fr/sharepoint/dev/general-development/how-to-configure-and-use-push-notifications-in-sharepoint-apps-for-windows)
- [Microsoft: deep-link role of secondary tiles](https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/july/windows-8-building-apps-for-windows-8-and-windows-phone-8)
