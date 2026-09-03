package com.yokuli.marine.core.shell

import com.yokuli.marine.core.model.DesktopLayout

object DesktopLayoutValidator {
    fun isValid(layout: DesktopLayout): Boolean {
        if (layout.columns !in setOf(4, 6, 8)) return false
        if (layout.placements.map { it.tileId }.distinct().size != layout.placements.size) return false
        val occupied = mutableSetOf<Pair<Int, Int>>()
        return layout.placements.all { placement ->
            val entry = LauncherRegistry.entry(placement.entryId) ?: return@all false
            if (placement.size !in entry.supportedSizes) return@all false
            val cells = buildList {
                repeat(placement.size.rows) { y ->
                    repeat(placement.size.columns) { x -> add(placement.column + x to placement.row + y) }
                }
            }
            cells.all { (x, y) -> x in 0 until layout.columns && y >= 0 } && cells.all(occupied::add)
        }
    }
}
