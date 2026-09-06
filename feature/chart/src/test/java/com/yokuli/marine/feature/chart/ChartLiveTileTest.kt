package com.yokuli.marine.feature.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartLiveTileTest {
    @Test fun `auto rotates navigation and real map while deterministic fallbacks stay truthful`() {
        assertEquals(
            listOf(ChartTileFrameKind.NAVIGATION, ChartTileFrameKind.MAP),
            ChartTileFramePolicy.frames(ChartTileMode.AUTO, true, true, true),
        )
        assertEquals(
            listOf(ChartTileFrameKind.MAP),
            ChartTileFramePolicy.frames(ChartTileMode.AUTO, false, true, true),
        )
        assertEquals(
            listOf(ChartTileFrameKind.POSITION),
            ChartTileFramePolicy.frames(ChartTileMode.AUTO, false, false, true),
        )
        assertEquals(
            listOf(ChartTileFrameKind.STATIC),
            ChartTileFramePolicy.frames(ChartTileMode.NAVIGATION, false, true, true),
        )
    }

    @Test fun `chart owns a bounded typed preference for every supported tile mode`() {
        val definition = ChartShellContribution.appPreferences!!.definitions.single()
            as com.yokuli.shell.contract.AppPreferenceDefinition.Choice

        assertEquals(ChartTileModePreferenceKey, definition.key)
        assertEquals(ChartTileMode.entries.map { it.name }, definition.options)
        assertTrue(definition.options.all(definition.optionLabels::containsKey))
        assertEquals(
            com.yokuli.shell.contract.AppPreferenceValue.Choice(ChartTileMode.AUTO.name),
            definition.defaultValue,
        )
    }
}
