package com.yokuli.marine.core.shell.engine.layout

import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.model.TileId
import com.yokuli.marine.core.model.TileSize

data class GridCell(val column: Int, val row: Int)

data class TilePlacement(
    val tileId: TileId,
    val entryId: LauncherEntryId,
    val size: TileSize,
    val cell: GridCell,
)

data class DesktopDocument(
    val version: Int,
    val columns: Int,
    val placements: List<TilePlacement>,
)

enum class LayoutChangeReason { MOVE, RESIZE, PIN, UNPIN, RESET, REPAIR }

data class LayoutTransaction(
    val id: String,
    val before: DesktopDocument,
    val after: DesktopDocument,
    val reason: LayoutChangeReason,
)

internal fun TilePlacement.occupiedCells(): Set<GridCell> = buildSet {
    repeat(this@occupiedCells.size.rows) { y ->
        repeat(this@occupiedCells.size.columns) { x ->
            add(GridCell(this@occupiedCells.cell.column + x, this@occupiedCells.cell.row + y))
        }
    }
}
