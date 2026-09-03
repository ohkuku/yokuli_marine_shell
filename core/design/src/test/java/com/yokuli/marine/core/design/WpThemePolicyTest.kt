package com.yokuli.marine.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    fun lightAndDarkModesInvertCanvasButKeepTheSelectedAccent() {
        val dark = WpThemePolicy.resolve(WpThemeSpec(WpThemeMode.DARK, WpAccent.MAGENTA))
        val light = WpThemePolicy.resolve(WpThemeSpec(WpThemeMode.LIGHT, WpAccent.MAGENTA))

        assertNotEquals(dark.background, light.background)
        assertNotEquals(dark.foreground, light.foreground)
        assertEquals(dark.accent, light.accent)
    }

    @Test
    fun everyAccentChoosesReadableTileForeground() {
        WpAccent.entries.forEach { accent ->
            val colors = WpThemePolicy.resolve(WpThemeSpec(accent = accent))
            assertTrue("insufficient ${accent.displayName} contrast", contrast(colors.accent, colors.onAccent) >= 4.5f)
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

    private fun contrast(first: Color, second: Color): Float {
        val light = maxOf(first.luminance(), second.luminance())
        val dark = minOf(first.luminance(), second.luminance())
        return (light + .05f) / (dark + .05f)
    }
}
