package com.yokuli.marine.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
            chrome = if (dark) Color(0xFF17191B) else Color(0xFFF4F4F3),
            // WP 前景跟系统明暗主题：深色白字/白图标，浅色黑字/黑图标。
            // 强调色只负责底色、边界和状态，不再按每个色块单独翻转文字。
            accent = accent, onAccent = foreground,
            safe = if (dark) Color(0xFF6CCB5F) else Color(0xFF107C10),
            warning = if (dark) Color(0xFFFCE100) else Color(0xFF8A5700),
            alarm = if (dark) Color(0xFFFF7B7B) else Color(0xFFC42B1C), stale = muted,
            controlFill = foregroundFraction(.10f), controlStroke = foregroundFraction(.48f),
            pressed = foregroundFraction(.18f), subtle = foregroundFraction(.05f), disabled = foregroundFraction(.36f),
            accentText = foreground,
        )
    }
}
val LocalWpTheme = staticCompositionLocalOf { WpThemePolicy.resolve(WpThemeSpec()) }
@Composable
fun YokuliTheme(spec: WpThemeSpec, content: @Composable () -> Unit) {
    val colors = remember(spec) { WpThemePolicy.resolve(spec) }
    CompositionLocalProvider(LocalWpTheme provides colors, content = content)
}
