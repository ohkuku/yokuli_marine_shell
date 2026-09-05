package com.yokuli.shell.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassicTileSizeTest {
    @Test fun onlyClassicShapesCanBeRegisteredOrResized() {
        assertEquals(listOf(1 to 1, 2 to 2, 4 to 2), MarineTileSize.entries.map { it.columns to it.rows })
    }
    @Test fun retiredNamesDecodeToClassicShapesWithoutBecomingSelectable() {
        assertEquals(MarineTileSize.STANDARD_2X2, MarineTileSize.fromPersistedName("COMPACT_2X1"))
        assertEquals(MarineTileSize.WIDE_4X2, MarineTileSize.fromPersistedName("TALL_2X4"))
        assertEquals(MarineTileSize.WIDE_4X2, MarineTileSize.fromPersistedName("LARGE_4X4"))
        MarineTileSize.entries.forEach { assertEquals(it, MarineTileSize.fromPersistedName(it.name)) }
        assertNull(MarineTileSize.fromPersistedName("UNKNOWN_FUTURE_SIZE"))
    }
}
