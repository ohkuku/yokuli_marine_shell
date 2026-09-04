package com.yokuli.shell.engine

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.WpTileSize
import com.yokuli.shell.engine.layout.DesktopDocument
import com.yokuli.shell.engine.layout.DesktopDocumentRepair
import com.yokuli.shell.engine.layout.DesktopDocumentValidator
import com.yokuli.shell.engine.layout.DesktopLayoutEditor
import com.yokuli.shell.engine.layout.DesktopRepairIncident
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.LayoutChangeReason
import com.yokuli.shell.engine.layout.TilePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopDocumentTest {
    private val chart = descriptor("chart", WpTileSize.WIDE_4X2, WpTileSize.entries)
    private val settings = descriptor(
        "settings",
        WpTileSize.SMALL_1X1,
        listOf(WpTileSize.SMALL_1X1, WpTileSize.MEDIUM_2X2),
    )
    private val entries = listOf(chart, settings)
    private val default = DesktopDocument(
        version = 1,
        columns = 4,
        placements = listOf(
            TilePlacement(TileInstanceId("tile-chart"), chart.entryId, WpTileSize.WIDE_4X2, GridCell(0, 0)),
            TilePlacement(TileInstanceId("tile-settings"), settings.entryId, WpTileSize.SMALL_1X1, GridCell(0, 2)),
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
        val transaction = DesktopLayoutEditor.resize(default, TileInstanceId("tile-settings"), entries)!!
        assertEquals(LayoutChangeReason.RESIZE, transaction.reason)
        assertEquals(default, transaction.before)
        assertEquals(GridCell(0, 0), transaction.after.placements.first().cell)
        assertEquals(WpTileSize.MEDIUM_2X2, transaction.after.placements.last().size)
        assertTrue(DesktopDocumentValidator.isValid(transaction.after, entries))
    }

    @Test
    fun unpinAndPinChangeOnlyTheRequestedEntry() {
        val withoutSettings = DesktopLayoutEditor.unpin(default, TileInstanceId("tile-settings"))!!.after
        assertEquals(listOf(chart.entryId), withoutSettings.placements.map { it.entryId })
        val restored = DesktopLayoutEditor.pin(withoutSettings, settings.entryId, entries)!!.after
        assertEquals(setOf(chart.entryId, settings.entryId), restored.placements.map { it.entryId }.toSet())
        assertTrue(DesktopDocumentValidator.isValid(restored, entries))
    }

    @Test
    fun invalidMoveIsRejectedInsteadOfClampedOrGloballyReflowed() {
        assertNull(DesktopLayoutEditor.move(default, TileInstanceId("tile-chart"), GridCell(1, 0), entries))
        assertEquals(default, default.copy())
    }

    @Test
    fun repairDropsUnknownAndDuplicateEntriesDeterministically() {
        val broken = default.copy(
            placements = default.placements + listOf(
                TilePlacement(
                    TileInstanceId("unknown"),
                    LauncherEntryId("unknown"),
                    WpTileSize.SMALL_1X1,
                    GridCell(2, 2),
                ),
                TilePlacement(
                    TileInstanceId("duplicate-settings"),
                    settings.entryId,
                    WpTileSize.SMALL_1X1,
                    GridCell(0, 2),
                ),
            ),
        )
        val result = DesktopDocumentRepair.repair(broken, entries, default)
        assertTrue(DesktopRepairIncident.UNKNOWN_ENTRY_REMOVED in result.incidents)
        assertTrue(DesktopRepairIncident.DUPLICATE_ENTRY_REMOVED in result.incidents)
        assertTrue(DesktopDocumentValidator.isValid(result.document, entries))
    }

    private fun descriptor(
        id: String,
        defaultSize: WpTileSize,
        sizes: List<WpTileSize>,
    ): LauncherEntryDescriptor {
        val app = LauncherAppId(id)
        return LauncherEntryDescriptor(
            entryId = LauncherEntryId(id),
            appId = app,
            launchToken = LaunchToken("$id.root"),
            defaultSize = defaultSize,
            supportedSizes = sizes,
            pinPolicy = PinPolicy.PINNABLE,
        )
    }
}
