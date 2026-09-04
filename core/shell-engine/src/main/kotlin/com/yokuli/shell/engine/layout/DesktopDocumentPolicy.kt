package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.TileInstanceId

object DesktopDocumentValidator {
    fun isValid(document: DesktopDocument, entries: Collection<LauncherEntryDescriptor>): Boolean {
        if (document.version <= 0 || document.columns != 4) return false
        if (document.placements.map { it.tileId }.distinct().size != document.placements.size) return false
        if (document.placements.map { it.entryId }.distinct().size != document.placements.size) return false
        val byId = entries.associateBy { it.entryId }
        val occupied = mutableSetOf<GridCell>()
        return document.placements.all { placement ->
            val entry = byId[placement.entryId] ?: return@all false
            if (placement.size !in entry.supportedSizes) return@all false
            placement.cell.column >= 0 && placement.cell.row >= 0 &&
                placement.cell.column + placement.size.columns <= document.columns &&
                placement.occupiedCells().all(occupied::add)
        }
    }
}

enum class DesktopRepairIncident {
    INVALID_DOCUMENT,
    UNKNOWN_ENTRY_REMOVED,
    DUPLICATE_ENTRY_REMOVED,
    UNSUPPORTED_SIZE_REPLACED,
    OUT_OF_BOUNDS_RELOCATED,
    OVERLAP_RELOCATED,
    FALLBACK_TO_DEFAULT,
}

data class DesktopRepairResult(
    val document: DesktopDocument,
    val incidents: List<DesktopRepairIncident>,
    val usedFallback: Boolean,
)

object DesktopDocumentRepair {
    fun repair(
        source: DesktopDocument,
        entries: Collection<LauncherEntryDescriptor>,
        defaultDocument: DesktopDocument,
    ): DesktopRepairResult {
        if (source.version <= 0 || source.columns != 4) {
            return fallback(defaultDocument, DesktopRepairIncident.INVALID_DOCUMENT)
        }
        val byId = entries.associateBy { it.entryId }
        val incidents = mutableListOf<DesktopRepairIncident>()
        val occupied = mutableSetOf<GridCell>()
        val seenEntries = mutableSetOf<LauncherEntryId>()
        val seenTiles = mutableSetOf<TileInstanceId>()
        val repaired = buildList {
            source.placements.forEach { original ->
                val descriptor = byId[original.entryId]
                if (descriptor == null) {
                    incidents += DesktopRepairIncident.UNKNOWN_ENTRY_REMOVED
                    return@forEach
                }
                if (!seenEntries.add(original.entryId) || !seenTiles.add(original.tileId)) {
                    incidents += DesktopRepairIncident.DUPLICATE_ENTRY_REMOVED
                    return@forEach
                }
                val sized = if (original.size in descriptor.supportedSizes) {
                    original
                } else {
                    incidents += DesktopRepairIncident.UNSUPPORTED_SIZE_REPLACED
                    original.copy(size = descriptor.defaultSize)
                }
                val inBounds = sized.cell.column >= 0 && sized.cell.row >= 0 &&
                    sized.cell.column + sized.size.columns <= source.columns
                val overlaps = sized.occupiedCells().any { it in occupied }
                val resolved = if (inBounds && !overlaps) {
                    sized
                } else {
                    incidents += if (!inBounds) {
                        DesktopRepairIncident.OUT_OF_BOUNDS_RELOCATED
                    } else {
                        DesktopRepairIncident.OVERLAP_RELOCATED
                    }
                    sized.copy(cell = nearestFreeCell(sized, source.columns, occupied))
                }
                occupied += resolved.occupiedCells()
                add(resolved)
            }
        }
        val document = source.copy(placements = repaired)
        return if (DesktopDocumentValidator.isValid(document, entries)) {
            DesktopRepairResult(document, incidents, usedFallback = false)
        } else {
            fallback(defaultDocument, existing = incidents)
        }
    }

    private fun nearestFreeCell(
        placement: TilePlacement,
        columns: Int,
        occupied: Set<GridCell>,
    ): GridCell {
        val maxColumn = columns - placement.size.columns
        var row = placement.cell.row.coerceAtLeast(0)
        while (true) {
            val candidates = (0..maxColumn).sortedWith(
                compareBy<Int> { kotlin.math.abs(it - placement.cell.column) }.thenBy { it },
            )
            candidates.firstOrNull { column ->
                placement.copy(cell = GridCell(column, row)).occupiedCells().none { it in occupied }
            }?.let { return GridCell(it, row) }
            row++
        }
    }

    private fun fallback(
        defaultDocument: DesktopDocument,
        reason: DesktopRepairIncident? = null,
        existing: List<DesktopRepairIncident> = emptyList(),
    ) = DesktopRepairResult(
        document = defaultDocument,
        incidents = existing + listOfNotNull(reason) + DesktopRepairIncident.FALLBACK_TO_DEFAULT,
        usedFallback = true,
    )
}
