package com.yokuli.marine.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

enum class WpThemeMode { DARK, LIGHT }

enum class WpAccent(val displayName: String, val argb: Long) {
    // Keep the stored key compatible. The default marine accent follows the approved sail.
    COBALT("cobalt", 0xFF0050EFL), CYAN("sea glass", 0xFF69877FL), EMERALD("emerald", 0xFF60A917L),
    MAGENTA("magenta", 0xFFD80073L), VIOLET("violet", 0xFF6A00FFL), CRIMSON("crimson", 0xFFA20025L), AMBER("amber", 0xFFF0A30AL),
}
data class WpThemeSpec(val mode: WpThemeMode = WpThemeMode.DARK, val accent: WpAccent = WpAccent.CYAN)

/** Shared ink, paper and sail palette; chart and safety colours remain semantic. */
data class WpColorScheme(
    val spec: WpThemeSpec, val background: Color, val foreground: Color, val muted: Color, val chrome: Color,
    val accent: Color, val onAccent: Color, val safe: Color, val warning: Color, val alarm: Color, val stale: Color,
    val controlFill: Color = chrome,
    val controlStroke: Color = muted,
    val pressed: Color = chrome,
    val subtle: Color = chrome,
    val disabled: Color = muted,
    val accentText: Color = accent,
)

object WpThemePolicy {
    /** Derive legible text without rewriting the user's explicitly selected accent. */
    fun resolve(spec: WpThemeSpec, accentOverride: Color? = null): WpColorScheme {
        val dark = spec.mode == WpThemeMode.DARK
        val background = if (dark) Color(0xFF172127) else YokuliBrandColors.Paper
        val foreground = if (dark) YokuliBrandColors.Paper else YokuliBrandColors.Ink
        val accent = (accentOverride ?: Color(spec.accent.argb)).copy(alpha = 1f)
        fun foregroundFraction(alpha: Float) = foreground.copy(alpha = alpha).compositeOver(background)
        val muted = foregroundFraction(.65f)
        return WpColorScheme(
            spec = spec, background = background, foreground = foreground, muted = muted,
            chrome = if (dark) Color(0xFF263239) else Color(0xFFEFEFED),
            accent = accent, onAccent = if (contrast(Color.White, accent) >= contrast(Color.Black, accent)) Color.White else Color.Black,
            safe = if (dark) Color(0xFF6CCB5F) else Color(0xFF107C10),
            warning = if (dark) Color(0xFFFCE100) else Color(0xFF8A5700),
            alarm = if (dark) Color(0xFFFF7B7B) else Color(0xFFC42B1C), stale = muted,
            controlFill = foregroundFraction(.07f), controlStroke = foregroundFraction(.55f),
            pressed = foregroundFraction(.14f), subtle = accent.copy(alpha = .10f).compositeOver(background), disabled = foregroundFraction(.4f),
            accentText = readableAccent(accent, background, foreground),
        )
    }
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance(); val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
    private fun readableAccent(accent: Color, background: Color, foreground: Color): Color {
        if (contrast(accent, background) >= 4.5f) return accent
        for (step in 1..20) {
            val candidate = lerp(accent, foreground, step / 20f)
            if (contrast(candidate, background) >= 4.5f) return candidate
        }
        return foreground
    }
}
val LocalWpTheme = staticCompositionLocalOf { WpThemePolicy.resolve(WpThemeSpec()) }
@Composable
fun YokuliTheme(spec: WpThemeSpec, content: @Composable () -> Unit) {
    val colors = remember(spec) { WpThemePolicy.resolve(spec) }
    CompositionLocalProvider(LocalWpTheme provides colors, content = content)
}
