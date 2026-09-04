package com.yokuli.shell.engine

import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
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
    private val chart = descriptor("chart", MarineTileSize.WIDE_4X2, MarineTileSize.entries)
    private val settings = descriptor(
        "settings",
        MarineTileSize.ICON_1X1,
        listOf(MarineTileSize.ICON_1X1, MarineTileSize.STANDARD_2X2),
    )
    private val entries = listOf(chart, settings)
    private val default = StartDocument(
        schemaVersion = 1,
        profileId = profile.id,
        defaultLayoutVersion = 1,
        placements = listOf(
            TilePlacement(TileInstanceId("tile-chart"), chart.entryId, MarineTileSize.WIDE_4X2, 0L),
            TilePlacement(TileInstanceId("tile-settings"), settings.entryId, MarineTileSize.ICON_1X1, 1024L),
        ),
    )

    @Test
    fun coordinateFreeRankDocumentIsValid() {
        assertTrue(StartDocumentValidator.isValid(default, entries, profile))
        assertEquals(listOf(0L, 1024L), default.placements.map { it.rank })
        assertEquals(GridCell(0, 2), AdaptiveTilePacker.pack(default, 4).tiles.last().cell)
    }

    @Test
    fun listOrderDoesNotOverrideRank() {
        val reversed = default.copy(placements = default.placements.reversed())

        assertTrue(StartDocumentValidator.isValid(reversed, entries, profile))
        assertEquals(
            AdaptiveTilePacker.pack(default, 4),
            AdaptiveTilePacker.pack(reversed, 4),
        )
    }

    @Test
    fun resizeReturnsAnAuditableTransactionWithoutPackingOtherTiles() {
        val transaction = StartLayoutEditor.resize(default, TileInstanceId("tile-settings"), entries)!!
        assertEquals(LayoutChangeReason.RESIZE, transaction.reason)
        assertEquals(default, transaction.before)
        assertEquals(0L, transaction.after.placements.first().rank)
        assertEquals(MarineTileSize.STANDARD_2X2, transaction.after.placements.last().size)
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
    fun moveSelectsInsertionOrderRatherThanPersistingCell() {
        val moved = StartLayoutEditor.move(default, TileInstanceId("tile-settings"), GridCell(0, 0), entries)!!.after
        assertEquals(listOf("tile-settings", "tile-chart"), moved.placements.map { it.tileId.value })
        assertEquals(listOf(0L, 1024L), moved.placements.map { it.rank })
    }

    @Test
    fun repairDropsUnknownAndDuplicateEntriesDeterministically() {
        val broken = default.copy(
            placements = default.placements + listOf(
                TilePlacement(
                    TileInstanceId("unknown"),
                    LauncherEntryId("unknown"),
                    MarineTileSize.ICON_1X1,
                    2048L,
                ),
                TilePlacement(
                    TileInstanceId("duplicate-settings"),
                    settings.entryId,
                    MarineTileSize.ICON_1X1,
                    3072L,
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
        defaultSize: MarineTileSize,
        sizes: List<MarineTileSize>,
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
