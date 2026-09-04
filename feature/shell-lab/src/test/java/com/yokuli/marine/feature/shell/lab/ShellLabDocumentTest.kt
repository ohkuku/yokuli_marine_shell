package com.yokuli.marine.feature.shell.lab

import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.StartDocumentValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellLabDocumentTest {
    @Test
    fun thirtyTileDemoFixtureRemainsAvailableForTheFrozenBaseline() {
        val descriptors = demoDescriptors(30)
        assertEquals(30, descriptors.size)
        assertTrue(
            StartDocumentValidator.isValid(
                demoDocument(descriptors),
                descriptors,
                WpReferenceProfiles.PHONE_PORTRAIT_4COL,
            ),
        )
    }

    @Test
    fun sixtyTileLayoutRemainsDeterministic() {
        val descriptors = demoDescriptors(60)
        val first = demoDocument(descriptors)
        val second = demoDocument(descriptors)

        assertEquals(60, first.placements.size)
        assertEquals(first, second)
        assertEquals(60, first.placements.map { it.tileId }.toSet().size)
        assertTrue(
            StartDocumentValidator.isValid(
                first,
                descriptors,
                WpReferenceProfiles.PHONE_PORTRAIT_4COL,
            ),
        )
    }
}
