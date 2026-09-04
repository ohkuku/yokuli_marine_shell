package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TileInstanceId

data class PackedTilePlacement(
    val entry: TileDocumentEntry,
    val cell: GridCell,
)

data class PackedSpacerPlacement(
    val spacer: Spacer,
    val cell: GridCell,
)

data class AdaptivePackedLayout(
    val columns: Int,
    val tiles: List<PackedTilePlacement>,
    val spacers: List<PackedSpacerPlacement>,
) {
    val occupiedCellsByItem: Map<TileInstanceId, Set<GridCell>> = buildMap {
        tiles.forEach { put(it.entry.tileId, occupiedCells(it.cell, it.entry.size)) }
        spacers.forEach { put(it.spacer.spacerId, occupiedCells(it.cell, it.spacer.size)) }
    }

    val documentHeightRows: Int = buildList {
        tiles.forEach { add(it.cell.row + it.entry.size.rows) }
        spacers.forEach { add(it.cell.row + it.spacer.size.rows) }
    }.maxOrNull() ?: 0

    fun tile(tileId: TileInstanceId): PackedTilePlacement? = tiles.firstOrNull { it.entry.tileId == tileId }
}

/**
 * Deterministic rank-first packing for the durable, coordinate-free tile document.
 * Normal entries always use the earliest available cell. Only [Spacer] can reserve a hole.
 */
object AdaptiveTilePacker {
    private const val RANK_STEP = 1024L

    fun pack(document: StartDocument, columns: Int): AdaptivePackedLayout {
        require(columns >= MarineTileSize.entries.maxOf { it.columns })
        val occupied = mutableSetOf<GridCell>()
        val tiles = mutableListOf<PackedTilePlacement>()
        val spacers = mutableListOf<PackedSpacerPlacement>()
        orderedItems(document).forEach { item ->
            val cell = firstAvailableCell(item.size, columns, occupied)
            occupied += occupiedCells(cell, item.size)
            when (item) {
                is RankedItem.Tile -> tiles += PackedTilePlacement(item.entry, cell)
                is RankedItem.Gap -> spacers += PackedSpacerPlacement(item.spacer, cell)
            }
        }
        return AdaptivePackedLayout(columns, tiles, spacers)
    }

    fun insert(
        document: StartDocument,
        tileId: TileInstanceId,
        insertionIndex: Int,
    ): StartDocument {
        val moving = document.placements.firstOrNull { it.tileId == tileId } ?: return document
        val withoutMoving = orderedItems(document).filterNot { it is RankedItem.Tile && it.entry.tileId == tileId }
        val target = insertionIndex.coerceIn(0, withoutMoving.size)
        val reordered = withoutMoving.toMutableList().apply { add(target, RankedItem.Tile(moving)) }
        val tileRanks = mutableMapOf<TileInstanceId, Long>()
        val spacerRanks = mutableMapOf<TileInstanceId, Long>()
        reordered.forEachIndexed { index, item ->
            val rank = index * RANK_STEP
            when (item) {
                is RankedItem.Tile -> tileRanks[item.entry.tileId] = rank
                is RankedItem.Gap -> spacerRanks[item.spacer.spacerId] = rank
            }
        }
        return document.copy(
            placements = reordered.filterIsInstance<RankedItem.Tile>().map { item ->
                item.entry.copy(rank = tileRanks.getValue(item.entry.tileId))
            },
            spacers = reordered.filterIsInstance<RankedItem.Gap>().map { item ->
                item.spacer.copy(rank = spacerRanks.getValue(item.spacer.spacerId))
            },
        )
    }

    fun insertionIndexForCell(
        document: StartDocument,
        columns: Int,
        target: GridCell,
        movingTileId: TileInstanceId? = null,
    ): Int {
        val packed = pack(document, columns)
        val ordered = buildList {
            packed.tiles.filterNot { it.entry.tileId == movingTileId }
                .forEach { add(PositionedItem(it.entry.rank, it.entry.tileId.value, it.cell)) }
            packed.spacers.forEach { add(PositionedItem(it.spacer.rank, it.spacer.spacerId.value, it.cell)) }
        }.sortedWith(compareBy({ it.cell.row }, { it.cell.column }, { it.rank }, { it.id }))
        val before = ordered.indexOfFirst { candidate ->
            target.row < candidate.cell.row ||
                (target.row == candidate.cell.row && target.column <= candidate.cell.column)
        }
        return if (before < 0) ordered.size else before
    }

    fun insertionIndexOf(document: StartDocument, tileId: TileInstanceId): Int =
        orderedItems(document).indexOfFirst { item ->
            item is RankedItem.Tile && item.entry.tileId == tileId
        }

    private data class PositionedItem(val rank: Long, val id: String, val cell: GridCell)

    private fun orderedItems(document: StartDocument): List<RankedItem> =
        (document.placements.map(RankedItem::Tile) + document.spacers.map(RankedItem::Gap))
            .sortedWith(compareBy<RankedItem>({ it.rank }, { it.stableId }))

    private fun firstAvailableCell(
        size: MarineTileSize,
        columns: Int,
        occupied: Set<GridCell>,
    ): GridCell {
        var row = 0
        while (true) {
            for (column in 0..columns - size.columns) {
                val candidate = GridCell(column, row)
                if (occupiedCells(candidate, size).none(occupied::contains)) return candidate
            }
            row++
        }
    }

    private sealed interface RankedItem {
        val rank: Long
        val stableId: String
        val size: MarineTileSize

        data class Tile(val entry: TileDocumentEntry) : RankedItem {
            override val rank = entry.rank
            override val stableId = entry.tileId.value
            override val size = entry.size
        }

        data class Gap(val spacer: Spacer) : RankedItem {
            override val rank = spacer.rank
            override val stableId = spacer.spacerId.value
            override val size = spacer.size
        }
    }
}

internal fun occupiedCells(cell: GridCell, size: MarineTileSize): Set<GridCell> = buildSet {
    repeat(size.rows) { y ->
        repeat(size.columns) { x -> add(GridCell(cell.column + x, cell.row + y)) }
    }
}
