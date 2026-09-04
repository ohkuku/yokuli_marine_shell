package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.geometry.WpLayoutPolicy
import kotlin.math.abs

/** Immutable cell lookup for a Start document. */
class StartOccupancyIndex(
    document: StartDocument,
    excludingTileId: TileInstanceId? = null,
) {
    private val cells: Map<GridCell, TileInstanceId> = buildMap {
        document.placements
            .filterNot { it.tileId == excludingTileId }
            .forEach { placement ->
                placement.occupiedCells().forEach { cell -> put(cell, placement.tileId) }
            }
    }

    fun tileAt(cell: GridCell): TileInstanceId? = cells[cell]

    fun isFree(cell: GridCell): Boolean = cell !in cells

    fun occupiedBy(tileId: TileInstanceId): Set<GridCell> =
        cells.filterValues { it == tileId }.keys
}

enum class SpatialRejectReason {
    TILE_NOT_FOUND,
    OUT_OF_BOUNDS,
    POLICY_REJECTED,
}

sealed interface SpatialLayoutProposal {
    data class Accepted(
        val proposal: LayoutProposal,
        val affectedTiles: Set<TileInstanceId>,
    ) : SpatialLayoutProposal

    data class Rejected(val reason: SpatialRejectReason) : SpatialLayoutProposal
}

interface TileCollisionSolver {
    fun propose(
        document: StartDocument,
        movingTile: TileInstanceId,
        target: GridCell,
        size: MarineTileSize,
        policy: WpLayoutPolicy,
    ): SpatialLayoutProposal
}

/**
 * Resolves only tiles directly covered by the requested placement. It never compacts the
 * document, so deliberate gaps and every unrelated coordinate remain part of user state.
 */
class LocalTileCollisionSolver(
    private val columnCount: Int = 4,
) : TileCollisionSolver {
    override fun propose(
        document: StartDocument,
        movingTile: TileInstanceId,
        target: GridCell,
        size: MarineTileSize,
        policy: WpLayoutPolicy,
    ): SpatialLayoutProposal {
        if (!policy.allowIntentionalWhitespace) {
            return SpatialLayoutProposal.Rejected(SpatialRejectReason.POLICY_REJECTED)
        }
        val moving = document.placements.firstOrNull { it.tileId == movingTile }
            ?: return SpatialLayoutProposal.Rejected(SpatialRejectReason.TILE_NOT_FOUND)
        val requested = moving.copy(cell = target, size = size)
        if (!requested.isWithin(columnCount)) {
            return SpatialLayoutProposal.Rejected(SpatialRejectReason.OUT_OF_BOUNDS)
        }

        val requestedCells = requested.occupiedCells()
        val collisions = document.placements
            .filter { it.tileId != movingTile && it.occupiedCells().any(requestedCells::contains) }
            .sortedWith(compareBy<TilePlacement>({ it.cell.row }, { it.cell.column }, { it.tileId.value }))
        val collisionIds = collisions.mapTo(mutableSetOf()) { it.tileId }
        val occupied = document.placements
            .filter { it.tileId != movingTile && it.tileId !in collisionIds }
            .flatMapTo(mutableSetOf()) { it.occupiedCells() }
        occupied += requestedCells

        val relocated = buildMap {
            collisions.forEach { placement ->
                val cell = nearestFreeCell(placement, occupied)
                val resolved = placement.copy(cell = cell)
                put(placement.tileId, resolved)
                occupied += resolved.occupiedCells()
            }
        }
        val after = document.copy(
            placements = document.placements.map { placement ->
                when (placement.tileId) {
                    movingTile -> requested
                    in relocated -> relocated.getValue(placement.tileId)
                    else -> placement
                }
            },
        )
        val reason = if (moving.size == size) LayoutChangeReason.MOVE else LayoutChangeReason.RESIZE
        return SpatialLayoutProposal.Accepted(
            proposal = LayoutProposal(document, after, reason),
            affectedTiles = setOf(movingTile) + collisionIds,
        )
    }

    private fun nearestFreeCell(placement: TilePlacement, occupied: Set<GridCell>): GridCell {
        val maxColumn = columnCount - placement.size.columns
        var row = placement.cell.row.coerceAtLeast(0)
        while (true) {
            val columns = (0..maxColumn).sortedWith(compareBy<Int> { abs(it - placement.cell.column) }.thenBy { it })
            columns.firstOrNull { column ->
                placement.copy(cell = GridCell(column, row)).occupiedCells().none(occupied::contains)
            }?.let { return GridCell(it, row) }
            row++
        }
    }

    private fun TilePlacement.isWithin(columns: Int): Boolean =
        cell.column >= 0 && cell.row >= 0 && cell.column + size.columns <= columns
}
