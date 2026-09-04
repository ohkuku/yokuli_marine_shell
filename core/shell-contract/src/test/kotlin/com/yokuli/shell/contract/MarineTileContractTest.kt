package com.yokuli.shell.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineTileContractTest {
    @Test
    fun tileSupportsExtendedMarineSizes() {
        assertEquals(
            listOf(1 to 1, 2 to 1, 2 to 2, 4 to 2, 2 to 4, 4 to 4),
            MarineTileSize.entries.map { it.columns to it.rows },
        )
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
