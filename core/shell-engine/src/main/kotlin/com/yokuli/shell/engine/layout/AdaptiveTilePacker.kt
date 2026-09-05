package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TileInstanceId

data class PackedTilePlacement(val entry: TileDocumentEntry, val cell: GridCell)
data class PackedSpacerPlacement(val spacer: Spacer, val cell: GridCell)

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

enum class InsertionSide { BEFORE, AFTER }

/** A visual hit names an item, never an index into a differently ordered list. */
data class TileInsertionTarget(val anchorId: TileInstanceId, val side: InsertionSide)

/** Deterministic rank-first packing. Only an explicit [Spacer] reserves a hole. */
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

    fun insert(document: StartDocument, tileId: TileInstanceId, insertionIndex: Int): StartDocument {
        val items = orderedItems(document)
        val moving = items.filterIsInstance<RankedItem.Tile>().firstOrNull { it.entry.tileId == tileId }
            ?: return document
        val rest = items.filterNot { it.id == tileId }
        val reordered = rest.toMutableList().apply { add(insertionIndex.coerceIn(0, rest.size), moving) }
        // A stationary long press and an out-and-back drag must not rewrite ranks or create undo entries.
        if (items.map { it.id } == reordered.map { it.id }) return document
        return document.copy(
            placements = reordered.mapIndexedNotNull { index, item ->
                (item as? RankedItem.Tile)?.entry?.copy(rank = index * RANK_STEP)
            },
            spacers = reordered.mapIndexedNotNull { index, item ->
                (item as? RankedItem.Gap)?.spacer?.copy(rank = index * RANK_STEP)
            },
        )
    }

    fun insert(document: StartDocument, tileId: TileInstanceId, target: TileInsertionTarget): StartDocument =
        insertionIndexForTarget(document, target, tileId)?.let { insert(document, tileId, it) } ?: document

    /** Resolve BEFORE/AFTER against the durable rank order, including spacers and excluding the lifted tile. */
    fun insertionIndexForTarget(
        document: StartDocument,
        target: TileInsertionTarget,
        movingTileId: TileInstanceId? = null,
    ): Int? {
        val rankOrder = orderedItems(document).filterNot { it.id == movingTileId }
        val index = rankOrder.indexOfFirst { it.id == target.anchorId }
        if (index < 0) return null
        return index + if (target.side == InsertionSide.AFTER) 1 else 0
    }

    fun insertionTargetForCell(
        document: StartDocument,
        columns: Int,
        target: GridCell,
        movingTileId: TileInstanceId? = null,
    ): TileInsertionTarget? {
        val packed = pack(document, columns)
        val hit = GridCell(target.column.coerceIn(0, columns - 1), target.row.coerceAtLeast(0))
        val visible = buildList {
            packed.tiles.filterNot { it.entry.tileId == movingTileId }
                .forEach { add(PositionedItem(it.entry.tileId, it.cell, it.entry.size)) }
            packed.spacers.forEach { add(PositionedItem(it.spacer.spacerId, it.cell, it.spacer.size)) }
        }.sortedWith(compareBy({ it.cell.row }, { it.cell.column }, { it.id.value }))
        if (hit.row >= packed.documentHeightRows) {
            return orderedItems(document).lastOrNull { it.id != movingTileId }
                ?.let { TileInsertionTarget(it.id, InsertionSide.AFTER) }
        }
        // Hit the entire rectangle, not just its top-left cell. Wide/tall tiles are indivisible anchors.
        val containing = visible.firstOrNull {
            hit.column in it.cell.column until it.cell.column + it.size.columns &&
                hit.row in it.cell.row until it.cell.row + it.size.rows
        }
        if (containing != null) {
            val fraction = if (containing.size.columns > 1) {
                (hit.column - containing.cell.column + .5f) / containing.size.columns
            } else {
                (hit.row - containing.cell.row + .5f) / containing.size.rows
            }
            return TileInsertionTarget(containing.id, if (fraction <= .5f) InsertionSide.BEFORE else InsertionSide.AFTER)
        }
        val next = visible.firstOrNull {
            hit.row < it.cell.row || (hit.row == it.cell.row && hit.column < it.cell.column)
        }
        return next?.let { TileInsertionTarget(it.id, InsertionSide.BEFORE) }
            ?: visible.lastOrNull()?.let { TileInsertionTarget(it.id, InsertionSide.AFTER) }
    }

    /** Compatibility boundary for the Engine's serialized index action. Preview and drop use the same rank index. */
    fun insertionIndexForCell(
        document: StartDocument,
        columns: Int,
        target: GridCell,
        movingTileId: TileInstanceId? = null,
    ): Int {
        val original = movingTileId?.let { insertionIndexOf(document, it) }?.coerceAtLeast(0) ?: 0
        val source = movingTileId?.let { pack(document, columns).tile(it) }
        if (source != null && target == source.cell) return original
        val anchor = insertionTargetForCell(document, columns, target, movingTileId) ?: return original
        return insertionIndexForTarget(document, anchor, movingTileId) ?: original
    }

    fun insertionIndexOf(document: StartDocument, tileId: TileInstanceId): Int =
        orderedItems(document).indexOfFirst { it.id == tileId }

    private data class PositionedItem(val id: TileInstanceId, val cell: GridCell, val size: MarineTileSize)
    private fun orderedItems(document: StartDocument): List<RankedItem> =
        (document.placements.map(RankedItem::Tile) + document.spacers.map(RankedItem::Gap))
            .sortedWith(compareBy<RankedItem>({ it.rank }, { it.id.value }))

    private fun firstAvailableCell(size: MarineTileSize, columns: Int, occupied: Set<GridCell>): GridCell {
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
        val id: TileInstanceId
        val size: MarineTileSize
        data class Tile(val entry: TileDocumentEntry) : RankedItem {
            override val rank = entry.rank
            override val id = entry.tileId
            override val size = entry.size
        }
        data class Gap(val spacer: Spacer) : RankedItem {
            override val rank = spacer.rank
            override val id = spacer.spacerId
            override val size = spacer.size
        }
    }
}

internal fun occupiedCells(cell: GridCell, size: MarineTileSize): Set<GridCell> = buildSet {
    repeat(size.rows) { y -> repeat(size.columns) { x -> add(GridCell(cell.column + x, cell.row + y)) } }
}
