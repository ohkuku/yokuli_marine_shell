package com.yokuli.shell.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineTileContractTest {
    @Test
    fun tileSupportsOnlyClassicSmallMediumAndWideSizes() {
        assertEquals(
            listOf(1 to 1, 2 to 2, 4 to 2),
            MarineTileSize.entries.map { it.columns to it.rows },
        )
    }

    @Test
    fun retiredPersistedShapesMapToTheNearestClassicShape() {
        assertEquals(MarineTileSize.ICON_1X1, MarineTileSize.fromPersistedName("ICON_1X1"))
        assertEquals(MarineTileSize.STANDARD_2X2, MarineTileSize.fromPersistedName("COMPACT_2X1"))
        assertEquals(MarineTileSize.STANDARD_2X2, MarineTileSize.fromPersistedName("STANDARD_2X2"))
        assertEquals(MarineTileSize.WIDE_4X2, MarineTileSize.fromPersistedName("TALL_2X4"))
        assertEquals(MarineTileSize.WIDE_4X2, MarineTileSize.fromPersistedName("LARGE_4X4"))
        assertEquals(MarineTileSize.WIDE_4X2, MarineTileSize.fromPersistedName("WIDE_4X2"))
        assertEquals(null, MarineTileSize.fromPersistedName("FUTURE_9X9"))
    }

    @Test
    fun everySizeSelectsAnIndependentContentLayout() {
        assertEquals(
            MarineTileSize.entries.size,
            MarineTileSize.entries.map { it.contentLayout }.distinct().size,
        )
    }

    @Test
    fun safetyPresentationCannotAutomaticallyCycleAwayFromAnAlarm() {
        assertFalse(TilePresentationKind.SAFETY.allowsAutomaticCycling)
        assertTrue(TilePresentationKind.CYCLE.allowsAutomaticCycling)
    }
}
