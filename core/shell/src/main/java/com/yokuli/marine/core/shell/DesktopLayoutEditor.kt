package com.yokuli.marine.core.shell

import com.yokuli.marine.core.model.DesktopLayout
import com.yokuli.marine.core.model.DesktopPlacement
import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.model.TileId
import com.yokuli.marine.core.model.TileSize

object DesktopLayoutEditor {
    private val sizeCycle = listOf(
        TileSize.SMALL_1X1,
        TileSize.WIDE_2X1,
        TileSize.MEDIUM_2X2,
        TileSize.HERO_4X2,
    )

    fun resize(layout: DesktopLayout, tileId: TileId): DesktopLayout {
        val current = layout.placements.firstOrNull { it.tileId == tileId } ?: return layout
        val entry = LauncherRegistry.entry(current.entryId) ?: return layout
        val start = sizeCycle.indexOf(current.size)
        val next = (1..sizeCycle.size)
            .map { sizeCycle[(start + it) % sizeCycle.size] }
            .first { it in entry.supportedSizes }
        return reflow(layout.columns, layout.placements.map { if (it.tileId == tileId) it.copy(size = next) else it })
    }

    fun unpin(layout: DesktopLayout, tileId: TileId): DesktopLayout =
        reflow(layout.columns, layout.placements.filterNot { it.tileId == tileId })

    fun pin(layout: DesktopLayout, entryId: LauncherEntryId): DesktopLayout {
        if (layout.placements.any { it.entryId == entryId }) return layout
        val entry = LauncherRegistry.entry(entryId) ?: return layout
        val tile = DesktopPlacement(
            tileId = TileId("tile-${entryId.value}"),
            entryId = entryId,
            size = entry.supportedSizes.first(),
            column = 0,
            row = 0,
        )
        return reflow(layout.columns, layout.placements + tile)
    }

    fun moveBefore(layout: DesktopLayout, tileId: TileId, beforeTileId: TileId): DesktopLayout {
        if (tileId == beforeTileId) return layout
        val moving = layout.placements.firstOrNull { it.tileId == tileId } ?: return layout
        val remaining = layout.placements.filterNot { it.tileId == tileId }.toMutableList()
        val target = remaining.indexOfFirst { it.tileId == beforeTileId }
        if (target < 0) return layout
        remaining.add(target, moving)
        return reflow(layout.columns, remaining)
    }

    fun moveToEnd(layout: DesktopLayout, tileId: TileId): DesktopLayout {
        val moving = layout.placements.firstOrNull { it.tileId == tileId } ?: return layout
        return reflow(layout.columns, layout.placements.filterNot { it.tileId == tileId } + moving)
    }

    private fun reflow(columns: Int, placements: List<DesktopPlacement>): DesktopLayout {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        val packed = placements.map { placement ->
            var row = 0
            var found: Pair<Int, Int>? = null
            while (found == null) {
                for (column in 0..(columns - placement.size.columns)) {
                    val cells = cells(column, row, placement.size)
                    if (cells.none { it in occupied }) {
                        found = column to row
                        occupied += cells
                        break
                    }
                }
                if (found == null) row++
            }
            placement.copy(column = found.first, row = found.second)
        }
        return DesktopLayout(columns, packed)
    }

    private fun cells(column: Int, row: Int, size: TileSize): Set<Pair<Int, Int>> = buildSet {
        repeat(size.rows) { y ->
            repeat(size.columns) { x -> add(column + x to row + y) }
        }
    }
}
