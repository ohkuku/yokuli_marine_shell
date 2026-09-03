package com.yokuli.marine.core.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WpThemePolicyTest {
    @Test
    fun everyAccentResolvesToTheUserSelectedShellColor() {
        WpAccent.entries.forEach { accent ->
            val colors = WpThemePolicy.resolve(WpThemeSpec(accent = accent))
            assertEquals("wrong color for ${accent.displayName}", Color(accent.argb), colors.accent)
        }
    }

    @Test
    fun darkIsPureBlackWithWhiteTextAndLightIsPureWhiteWithBlackText() {
        val dark = WpThemePolicy.resolve(WpThemeSpec(WpThemeMode.DARK, WpAccent.MAGENTA))
        val light = WpThemePolicy.resolve(WpThemeSpec(WpThemeMode.LIGHT, WpAccent.MAGENTA))

        assertEquals(Color.Black, dark.background)
        assertEquals(Color.White, dark.foreground)
        assertEquals(Color.Black, dark.chrome)
        assertEquals(Color.White, light.background)
        assertEquals(Color.Black, light.foreground)
        assertEquals(Color.White, light.chrome)
        assertEquals(dark.accent, light.accent)
    }

    @Test
    fun phoneTilesKeepPureWhiteForegroundAcrossThemesAndAccents() {
        WpThemeMode.entries.forEach { mode ->
            WpAccent.entries.forEach { accent ->
                val colors = WpThemePolicy.resolve(WpThemeSpec(mode, accent))
                assertEquals("wrong tile foreground for $mode/${accent.displayName}", Color.White, colors.onAccent)
            }
        }
    }

    @Test
    fun safetyPaletteDoesNotReplaceTheSelectedAccent() {
        val colors = WpThemePolicy.resolve(WpThemeSpec(accent = WpAccent.COBALT))
        assertNotEquals(colors.safe, colors.accent)
        assertNotEquals(colors.warning, colors.accent)
        assertNotEquals(colors.alarm, colors.accent)
        assertNotEquals(colors.stale, colors.accent)
    }

    @Test
    fun startCanvasUsesOneRepeatedSeamToken() {
        assertEquals(YokuliMetrics.TileGap, YokuliMetrics.OuterMargin)
    }
}
