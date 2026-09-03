package com.yokuli.marine.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
        val black = YokuliColors.Black
        val white = Color(0xFFF7F7F7)
        val onAccent = listOf(black, white).maxBy { contrast(accent, it) }
        return WpColorScheme(
            spec = spec,
            background = if (spec.mode == WpThemeMode.DARK) YokuliColors.Black else Color(0xFFF4F4F4),
            foreground = if (spec.mode == WpThemeMode.DARK) white else Color(0xFF101010),
            muted = if (spec.mode == WpThemeMode.DARK) Color(0xFFA8ADB2) else Color(0xFF5C6064),
            chrome = if (spec.mode == WpThemeMode.DARK) YokuliColors.Black else Color(0xFFE9E9E9),
            accent = accent,
            onAccent = onAccent,
            safe = YokuliColors.Safe,
            warning = YokuliColors.Warning,
            alarm = YokuliColors.Alarm,
            stale = YokuliColors.Stale,
        )
    }

    private fun contrast(first: Color, second: Color): Float {
        val light = maxOf(first.luminance(), second.luminance())
        val dark = minOf(first.luminance(), second.luminance())
        return (light + .05f) / (dark + .05f)
    }
}

val LocalWpTheme = staticCompositionLocalOf { WpThemePolicy.resolve(WpThemeSpec()) }

@Composable
fun YokuliTheme(spec: WpThemeSpec, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWpTheme provides WpThemePolicy.resolve(spec), content = content)
}
