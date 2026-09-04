package com.yokuli.marine.core.shell.engine

import com.yokuli.marine.core.model.DestinationId
import com.yokuli.marine.core.model.LaunchTarget
import com.yokuli.marine.core.model.LauncherEntryDescriptor
import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.model.MarineAppId
import com.yokuli.marine.core.model.TileId
import com.yokuli.marine.core.model.TileSize
import com.yokuli.marine.core.shell.engine.layout.DesktopDocument
import com.yokuli.marine.core.shell.engine.layout.DesktopDocumentRepair
import com.yokuli.marine.core.shell.engine.layout.DesktopDocumentValidator
import com.yokuli.marine.core.shell.engine.layout.DesktopLayoutEditor
import com.yokuli.marine.core.shell.engine.layout.DesktopRepairIncident
import com.yokuli.marine.core.shell.engine.layout.GridCell
import com.yokuli.marine.core.shell.engine.layout.LayoutChangeReason
import com.yokuli.marine.core.shell.engine.layout.TilePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopDocumentTest {
    private val chart = descriptor("chart", TileSize.WIDE_4X2, TileSize.entries)
    private val settings = descriptor("settings", TileSize.SMALL_1X1, listOf(TileSize.SMALL_1X1, TileSize.MEDIUM_2X2))
    private val entries = listOf(chart, settings)
    private val default = DesktopDocument(
        version = 1,
        columns = 4,
        placements = listOf(
            TilePlacement(TileId("tile-chart"), chart.id, TileSize.WIDE_4X2, GridCell(0, 0)),
            TilePlacement(TileId("tile-settings"), settings.id, TileSize.SMALL_1X1, GridCell(0, 2)),
        ),
    )

    @Test
    fun intentionalWhitespaceIsAValidPartOfTheDocument() {
        assertTrue(DesktopDocumentValidator.isValid(default, entries))
        assertEquals(GridCell(0, 2), default.placements.last().cell)
        assertFalse(default.placements.any { it.cell == GridCell(1, 2) })
    }

    @Test
    fun resizeReturnsAnAuditableTransactionWithoutPackingOtherTiles() {
        val transaction = DesktopLayoutEditor.resize(default, TileId("tile-settings"), entries)!!
        assertEquals(LayoutChangeReason.RESIZE, transaction.reason)
        assertEquals(default, transaction.before)
        assertEquals(GridCell(0, 0), transaction.after.placements.first().cell)
        assertEquals(TileSize.MEDIUM_2X2, transaction.after.placements.last().size)
        assertTrue(DesktopDocumentValidator.isValid(transaction.after, entries))
    }

    @Test
    fun unpinAndPinChangeOnlyTheRequestedEntry() {
        val withoutSettings = DesktopLayoutEditor.unpin(default, TileId("tile-settings"))!!.after
        assertEquals(listOf(chart.id), withoutSettings.placements.map { it.entryId })
        val restored = DesktopLayoutEditor.pin(withoutSettings, settings.id, entries)!!.after
        assertEquals(setOf(chart.id, settings.id), restored.placements.map { it.entryId }.toSet())
        assertTrue(DesktopDocumentValidator.isValid(restored, entries))
    }

    @Test
    fun invalidMoveIsRejectedInsteadOfClampedOrGloballyReflowed() {
        assertNull(DesktopLayoutEditor.move(default, TileId("tile-chart"), GridCell(1, 0), entries))
        assertEquals(default, default.copy())
    }

    @Test
    fun repairDropsUnknownAndDuplicateEntriesDeterministically() {
        val broken = default.copy(
            placements = default.placements + listOf(
                TilePlacement(TileId("unknown"), LauncherEntryId("unknown"), TileSize.SMALL_1X1, GridCell(2, 2)),
                TilePlacement(TileId("duplicate-settings"), settings.id, TileSize.SMALL_1X1, GridCell(0, 2)),
            ),
        )
        val result = DesktopDocumentRepair.repair(broken, entries, default)
        assertTrue(DesktopRepairIncident.UNKNOWN_ENTRY_REMOVED in result.incidents)
        assertTrue(DesktopRepairIncident.DUPLICATE_ENTRY_REMOVED in result.incidents)
        assertTrue(DesktopDocumentValidator.isValid(result.document, entries))
    }

    private fun descriptor(id: String, defaultSize: TileSize, sizes: List<TileSize>): LauncherEntryDescriptor {
        val app = MarineAppId(id)
        return LauncherEntryDescriptor(
            id = LauncherEntryId(id),
            appId = app,
            launchTarget = LaunchTarget(app, DestinationId("$id.root")),
            defaultSize = defaultSize,
            supportedSizesInCycleOrder = sizes,
        )
    }
}
