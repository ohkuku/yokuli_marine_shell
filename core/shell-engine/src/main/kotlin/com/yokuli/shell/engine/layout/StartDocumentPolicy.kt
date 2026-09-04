package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.WpReferenceProfile

object StartDocumentValidator {
    fun isValid(
        document: StartDocument,
        entries: Collection<LauncherEntryDescriptor>,
        profile: WpReferenceProfile,
    ): Boolean {
        if (document.schemaVersion <= 0 || document.defaultLayoutVersion <= 0) return false
        if (document.profileId != profile.id) return false
        if (document.placements.map { it.tileId }.distinct().size != document.placements.size) return false
        if (document.placements.map { it.entryId }.distinct().size != document.placements.size) return false
        val byId = entries.associateBy { it.entryId }
        val occupied = mutableSetOf<GridCell>()
        return document.placements.all { placement ->
            val entry = byId[placement.entryId] ?: return@all false
            if (placement.size !in entry.supportedSizes) return@all false
            placement.cell.column >= 0 && placement.cell.row >= 0 &&
                placement.cell.column + placement.size.columns <= profile.columnCount &&
                placement.occupiedCells().all(occupied::add)
        }
    }
}

enum class StartRepairIncident {
    INVALID_DOCUMENT,
    PROFILE_MISMATCH,
    UNKNOWN_ENTRY_REMOVED,
    DUPLICATE_ENTRY_REMOVED,
    UNSUPPORTED_SIZE_REPLACED,
    OUT_OF_BOUNDS_RELOCATED,
    OVERLAP_RELOCATED,
    FALLBACK_TO_DEFAULT,
}

data class StartRepairResult(
    val document: StartDocument,
    val incidents: List<StartRepairIncident>,
    val usedFallback: Boolean,
)

object StartDocumentRepair {
    fun repair(
        source: StartDocument,
        entries: Collection<LauncherEntryDescriptor>,
        defaultDocument: StartDocument,
        profile: WpReferenceProfile,
    ): StartRepairResult {
        if (source.schemaVersion <= 0 || source.defaultLayoutVersion <= 0) {
            return fallback(defaultDocument, StartRepairIncident.INVALID_DOCUMENT)
        }
        if (source.profileId != profile.id || defaultDocument.profileId != profile.id) {
            return fallback(defaultDocument, StartRepairIncident.PROFILE_MISMATCH)
        }
        val byId = entries.associateBy { it.entryId }
        val incidents = mutableListOf<StartRepairIncident>()
        val occupied = mutableSetOf<GridCell>()
        val seenEntries = mutableSetOf<LauncherEntryId>()
        val seenTiles = mutableSetOf<TileInstanceId>()
        val repaired = buildList {
            source.placements.forEach { original ->
                val descriptor = byId[original.entryId]
                if (descriptor == null) {
                    incidents += StartRepairIncident.UNKNOWN_ENTRY_REMOVED
                    return@forEach
                }
                if (!seenEntries.add(original.entryId) || !seenTiles.add(original.tileId)) {
                    incidents += StartRepairIncident.DUPLICATE_ENTRY_REMOVED
                    return@forEach
                }
                val sized = if (original.size in descriptor.supportedSizes) {
                    original
                } else {
                    incidents += StartRepairIncident.UNSUPPORTED_SIZE_REPLACED
                    original.copy(size = descriptor.defaultSize)
                }
                val inBounds = sized.cell.column >= 0 && sized.cell.row >= 0 &&
                    sized.cell.column + sized.size.columns <= profile.columnCount
                val overlaps = sized.occupiedCells().any { it in occupied }
                val resolved = if (inBounds && !overlaps) {
                    sized
                } else {
                    incidents += if (!inBounds) {
                        StartRepairIncident.OUT_OF_BOUNDS_RELOCATED
                    } else {
                        StartRepairIncident.OVERLAP_RELOCATED
                    }
                    sized.copy(cell = nearestFreeCell(sized, profile.columnCount, occupied))
                }
                occupied += resolved.occupiedCells()
                add(resolved)
            }
        }
        val document = source.copy(placements = repaired)
        return if (StartDocumentValidator.isValid(document, entries, profile)) {
            StartRepairResult(document, incidents, usedFallback = false)
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
        defaultDocument: StartDocument,
        reason: StartRepairIncident? = null,
        existing: List<StartRepairIncident> = emptyList(),
    ) = StartRepairResult(
        document = defaultDocument,
        incidents = existing + listOfNotNull(reason) + StartRepairIncident.FALLBACK_TO_DEFAULT,
        usedFallback = true,
    )
}
