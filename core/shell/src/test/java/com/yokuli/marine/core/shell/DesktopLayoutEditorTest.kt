package com.yokuli.marine.core.shell

import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.model.TileId
import com.yokuli.marine.core.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLayoutEditorTest {
    @Test
    fun resizeCyclesOnlyThroughSizesSupportedByTheEntryAndReflows() {
        val resized = DesktopLayoutEditor.resize(LauncherRegistry.defaultLayout, TileId("tile-chart"))

        assertEquals(TileSize.WIDE_2X1, resized.placements.first { it.tileId.value == "tile-chart" }.size)
        assertTrue(DesktopLayoutValidator.isValid(resized))
    }

    @Test
    fun unpinRemovesOnlyTheSelectedTile() {
        val edited = DesktopLayoutEditor.unpin(LauncherRegistry.defaultLayout, TileId("tile-anchor"))

        assertFalse(edited.placements.any { it.entryId.value == "anchor" })
        assertEquals(4, edited.placements.size)
        assertTrue(DesktopLayoutValidator.isValid(edited))
    }

    @Test
    fun pinAddsAnAllAppsShortcutAtTheNextFreeGridPosition() {
        val edited = DesktopLayoutEditor.pin(LauncherRegistry.defaultLayout, LauncherEntryId("navigation"))

        assertEquals(TileSize.WIDE_2X1, edited.placements.first { it.entryId.value == "navigation" }.size)
        assertTrue(DesktopLayoutValidator.isValid(edited))
    }

    @Test
    fun movingATileBeforeAnotherReflowsWithoutOverlap() {
        val edited = DesktopLayoutEditor.moveBefore(
            LauncherRegistry.defaultLayout,
            tileId = TileId("tile-system"),
            beforeTileId = TileId("tile-chart"),
        )

        assertEquals("system", edited.placements.first().entryId.value)
        assertTrue(DesktopLayoutValidator.isValid(edited))
    }
}
