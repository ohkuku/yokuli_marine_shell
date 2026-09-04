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
import com.yokuli.shell.engine.layout.LocalTileCollisionSolver
import com.yokuli.shell.engine.layout.SpatialLayoutProposal
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.StartDocumentValidator
import com.yokuli.shell.engine.layout.StartOccupancyIndex
import com.yokuli.shell.engine.layout.TilePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialGridTest {
    private val profile = WpReferenceProfiles.PHONE_PORTRAIT_4COL
    private val solver = LocalTileCollisionSolver(profile.columnCount)

    @Test
    fun occupancyIndexAnswersExplicitCells() {
        val document = documentOf(
            placement("a", GridCell(0, 0), MarineTileSize.STANDARD_2X2),
            placement("b", GridCell(3, 3), MarineTileSize.ICON_1X1),
        )

        val index = StartOccupancyIndex(document)

        assertEquals(TileInstanceId("tile-a"), index.tileAt(GridCell(1, 1)))
        assertTrue(index.isFree(GridCell(2, 1)))
        assertEquals(setOf(GridCell(3, 3)), index.occupiedBy(TileInstanceId("tile-b")))
        assertNull(StartOccupancyIndex(document, TileInstanceId("tile-a")).tileAt(GridCell(0, 0)))
    }

    @Test
    fun movingOneTilePreservesEveryUnaffectedCoordinate() {
        val original = documentOf(
            placement("moving", GridCell(0, 0), MarineTileSize.ICON_1X1),
            placement("collision", GridCell(1, 0), MarineTileSize.ICON_1X1),
            placement("unrelated", GridCell(3, 4), MarineTileSize.ICON_1X1),
        )

        val accepted = solver.propose(
            original,
            TileInstanceId("tile-moving"),
            GridCell(1, 0),
            MarineTileSize.ICON_1X1,
            profile.layoutPolicy,
        ) as SpatialLayoutProposal.Accepted

        assertEquals(GridCell(3, 4), accepted.proposal.after.placement("unrelated").cell)
        assertEquals(setOf(TileInstanceId("tile-moving"), TileInstanceId("tile-collision")), accepted.affectedTiles)
        assertEquals(original.placements.map { it.tileId }, accepted.proposal.after.placements.map { it.tileId })
    }

    @Test
    fun proposalIsDeterministicAndPreservesWhitespace() {
        val original = documentOf(
            placement("moving", GridCell(0, 0), MarineTileSize.ICON_1X1),
            placement("collision", GridCell(2, 0), MarineTileSize.ICON_1X1),
            placement("after-gap", GridCell(0, 5), MarineTileSize.ICON_1X1),
        )

        val first = solver.propose(
            original,
            TileInstanceId("tile-moving"),
            GridCell(2, 0),
            MarineTileSize.ICON_1X1,
            profile.layoutPolicy,
        )
        val second = solver.propose(
            original,
            TileInstanceId("tile-moving"),
            GridCell(2, 0),
            MarineTileSize.ICON_1X1,
            profile.layoutPolicy,
        )

        assertEquals(first, second)
        val after = (first as SpatialLayoutProposal.Accepted).proposal.after
        assertEquals(GridCell(0, 5), after.placement("after-gap").cell)
        assertTrue(StartOccupancyIndex(after).isFree(GridCell(0, 2)))
    }

    @Test
    fun sixtySyntheticTilesRemainValidAndStable() {
        val placements = (0 until 60).map { index ->
            placement("entry-$index", GridCell(index % 4, index / 4), MarineTileSize.ICON_1X1)
        }
        val entries = placements.map { descriptor(it.entryId.value) }
        val original = documentOf(*placements.toTypedArray())

        val accepted = solver.propose(
            original,
            placements.first().tileId,
            GridCell(1, 0),
            MarineTileSize.ICON_1X1,
            profile.layoutPolicy,
        ) as SpatialLayoutProposal.Accepted

        assertTrue(StartDocumentValidator.isValid(accepted.proposal.after, entries, profile))
        assertEquals(60, accepted.proposal.after.placements.size)
        assertEquals(GridCell(2, 0), accepted.proposal.after.placement("entry-2").cell)
        assertEquals(GridCell(0, 0), accepted.proposal.after.placement("entry-1").cell)
        assertEquals(
            accepted,
            solver.propose(
                original,
                placements.first().tileId,
                GridCell(1, 0),
                MarineTileSize.ICON_1X1,
                profile.layoutPolicy,
            ),
        )
    }

    private fun documentOf(vararg placements: TilePlacement) = StartDocument(
        schemaVersion = 1,
        profileId = profile.id,
        defaultLayoutVersion = 1,
        placements = placements.toList(),
    )

    private fun placement(id: String, cell: GridCell, size: MarineTileSize) = TilePlacement(
        tileId = TileInstanceId("tile-$id"),
        entryId = LauncherEntryId(id),
        size = size,
        cell = cell,
    )

    private fun descriptor(id: String): LauncherEntryDescriptor {
        val appId = LauncherAppId(id)
        return LauncherEntryDescriptor(
            entryId = LauncherEntryId(id),
            appId = appId,
            launchToken = LaunchToken("$id.root"),
            defaultSize = MarineTileSize.ICON_1X1,
            supportedSizes = listOf(MarineTileSize.ICON_1X1),
            pinPolicy = PinPolicy.PINNABLE,
        )
    }

    private fun StartDocument.placement(entryId: String): TilePlacement =
        placements.single { it.entryId == LauncherEntryId(entryId) }
}
