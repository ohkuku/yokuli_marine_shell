package com.yokuli.marine.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

enum class WpThemeMode { DARK, LIGHT }

enum class WpAccent(val displayName: String, val argb: Long) {
    COBALT("cobalt", 0xFF0050EFL),
    CYAN("cyan", 0xFF007F9BL),
    EMERALD("emerald", 0xFF60A917L),
    MAGENTA("magenta", 0xFFD80073L),
    VIOLET("violet", 0xFF6A00FFL),
    CRIMSON("crimson", 0xFFA20025L),
    AMBER("amber", 0xFFF0A30AL),
}

data class WpThemeSpec(
    val mode: WpThemeMode = WpThemeMode.DARK,
    val accent: WpAccent = WpAccent.CYAN,
)

data class WpColorScheme(
    val spec: WpThemeSpec,
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val chrome: Color,
    val accent: Color,
    val onAccent: Color,
    val safe: Color,
    val warning: Color,
    val alarm: Color,
    val stale: Color,
)

object WpThemePolicy {
    fun resolve(spec: WpThemeSpec): WpColorScheme {
        val accent = Color(spec.accent.argb)
        val background = if (spec.mode == WpThemeMode.DARK) Color.Black else Color.White
        val foreground = if (spec.mode == WpThemeMode.DARK) Color.White else Color.Black
        return WpColorScheme(
            spec = spec,
            background = background,
            foreground = foreground,
            muted = if (spec.mode == WpThemeMode.DARK) Color(0xFFA8ADB2) else Color(0xFF5C6064),
            chrome = background,
            accent = accent,
            // 中文：WP8.1 手机上的磁贴前景固定为 light，不跟随页面黑白主题反转。
            // English: WP8.1 phone tiles keep a light foreground independent of the page theme.
            onAccent = Color.White,
            safe = YokuliColors.Safe,
            warning = YokuliColors.Warning,
            alarm = YokuliColors.Alarm,
            stale = YokuliColors.Stale,
        )
    }
}

val LocalWpTheme = staticCompositionLocalOf { WpThemePolicy.resolve(WpThemeSpec()) }

@Composable
fun YokuliTheme(spec: WpThemeSpec, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWpTheme provides WpThemePolicy.resolve(spec), content = content)
}
