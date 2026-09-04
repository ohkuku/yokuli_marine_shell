package com.yokuli.shell.engine

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.WpTileSize
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.LayoutChangeReason
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.StartDocumentRepair
import com.yokuli.shell.engine.layout.StartDocumentValidator
import com.yokuli.shell.engine.layout.StartLayoutEditor
import com.yokuli.shell.engine.layout.StartRepairIncident
import com.yokuli.shell.engine.layout.TilePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartDocumentTest {
    private val profile = WpReferenceProfiles.PHONE_PORTRAIT_4COL
    private val chart = descriptor("chart", WpTileSize.WIDE_4X2, WpTileSize.entries)
    private val settings = descriptor(
        "settings",
        WpTileSize.SMALL_1X1,
        listOf(WpTileSize.SMALL_1X1, WpTileSize.MEDIUM_2X2),
    )
    private val entries = listOf(chart, settings)
    private val default = StartDocument(
        schemaVersion = 1,
        profileId = profile.id,
        defaultLayoutVersion = 1,
        placements = listOf(
            TilePlacement(TileInstanceId("tile-chart"), chart.entryId, WpTileSize.WIDE_4X2, GridCell(0, 0)),
            TilePlacement(TileInstanceId("tile-settings"), settings.entryId, WpTileSize.SMALL_1X1, GridCell(0, 2)),
        ),
    )

    @Test
    fun intentionalWhitespaceIsAValidPartOfTheDocument() {
        assertTrue(StartDocumentValidator.isValid(default, entries, profile))
        assertEquals(GridCell(0, 2), default.placements.last().cell)
        assertFalse(default.placements.any { it.cell == GridCell(1, 2) })
    }

    @Test
    fun placementOrderDoesNotDefinePosition() {
        val reversed = default.copy(placements = default.placements.reversed())

        assertTrue(StartDocumentValidator.isValid(reversed, entries, profile))
        assertEquals(
            default.placements.associate { it.tileId to it.cell },
            reversed.placements.associate { it.tileId to it.cell },
        )
    }

    @Test
    fun resizeReturnsAnAuditableTransactionWithoutPackingOtherTiles() {
        val transaction = StartLayoutEditor.resize(default, TileInstanceId("tile-settings"), entries)!!
        assertEquals(LayoutChangeReason.RESIZE, transaction.reason)
        assertEquals(default, transaction.before)
        assertEquals(GridCell(0, 0), transaction.after.placements.first().cell)
        assertEquals(WpTileSize.MEDIUM_2X2, transaction.after.placements.last().size)
        assertTrue(StartDocumentValidator.isValid(transaction.after, entries, profile))
    }

    @Test
    fun unpinAndPinChangeOnlyTheRequestedEntry() {
        val withoutSettings = StartLayoutEditor.unpin(default, TileInstanceId("tile-settings"))!!.after
        assertEquals(listOf(chart.entryId), withoutSettings.placements.map { it.entryId })
        val restored = StartLayoutEditor.pin(withoutSettings, settings.entryId, entries)!!.after
        assertEquals(setOf(chart.entryId, settings.entryId), restored.placements.map { it.entryId }.toSet())
        assertTrue(StartDocumentValidator.isValid(restored, entries, profile))
    }

    @Test
    fun invalidMoveIsRejectedInsteadOfClampedOrGloballyReflowed() {
        assertNull(StartLayoutEditor.move(default, TileInstanceId("tile-chart"), GridCell(1, 0), entries))
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
        val first = StartDocumentRepair.repair(broken, entries, default, profile)
        val second = StartDocumentRepair.repair(broken, entries, default, profile)

        assertEquals(first, second)
        assertTrue(StartRepairIncident.UNKNOWN_ENTRY_REMOVED in first.incidents)
        assertTrue(StartRepairIncident.DUPLICATE_ENTRY_REMOVED in first.incidents)
        assertTrue(StartDocumentValidator.isValid(first.document, entries, profile))
    }

    @Test
    fun profileMismatchFallsBackDeterministically() {
        val squareSource = default.copy(profileId = WpReferenceProfiles.SQUARE_4COL.id)
        val result = StartDocumentRepair.repair(squareSource, entries, default, profile)

        assertTrue(result.usedFallback)
        assertEquals(default, result.document)
        assertEquals(
            listOf(StartRepairIncident.PROFILE_MISMATCH, StartRepairIncident.FALLBACK_TO_DEFAULT),
            result.incidents,
        )
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
