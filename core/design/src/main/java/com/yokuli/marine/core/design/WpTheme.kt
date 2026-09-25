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
    COBALT("cobalt", 0xFF0050EFL), CYAN("cyan", 0xFF007F9BL), EMERALD("emerald", 0xFF60A917L),
    MAGENTA("magenta", 0xFFD80073L), VIOLET("violet", 0xFF6A00FFL), CRIMSON("crimson", 0xFFA20025L), AMBER("amber", 0xFFF0A30AL),
}
data class WpThemeSpec(val mode: WpThemeMode = WpThemeMode.DARK, val accent: WpAccent = WpAccent.CYAN)

/** MDL2 的面板、文字、控件状态共享调色板；海图/领域业务颜色不从这里改写。 */
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
    /** 自定义强调色只在显示层派生可读前景，绝不改写用户保存的配色。 */
    fun resolve(spec: WpThemeSpec, accentOverride: Color? = null): WpColorScheme {
        val dark = spec.mode == WpThemeMode.DARK
        val background = if (dark) Color.Black else Color.White
        val foreground = if (dark) Color.White else Color.Black
        val accent = (accentOverride ?: Color(spec.accent.argb)).copy(alpha = 1f)
        fun foregroundFraction(alpha: Float) = foreground.copy(alpha = alpha).compositeOver(background)
        val muted = foregroundFraction(.6f)
        return WpColorScheme(
            spec = spec, background = background, foreground = foreground, muted = muted,
            chrome = if (dark) Color(0xFF1F1F1F) else Color(0xFFF2F2F2),
            accent = accent, onAccent = if (contrast(Color.White, accent) >= contrast(Color.Black, accent)) Color.White else Color.Black,
            safe = if (dark) Color(0xFF6CCB5F) else Color(0xFF107C10),
            warning = if (dark) Color(0xFFFCE100) else Color(0xFF8A5700),
            alarm = if (dark) Color(0xFFFF7B7B) else Color(0xFFC42B1C), stale = muted,
            controlFill = foregroundFraction(.2f), controlStroke = foregroundFraction(.6f),
            pressed = foregroundFraction(.4f), subtle = foregroundFraction(.08f), disabled = foregroundFraction(.4f),
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
