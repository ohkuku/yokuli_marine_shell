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
        if (document.spacers.map { it.spacerId }.distinct().size != document.spacers.size) return false
        if (document.spacers.any { spacer -> document.placements.any { it.tileId == spacer.spacerId } }) return false
        val byId = entries.associateBy { it.entryId }
        if (document.placements.any { it.rank < 0 } || document.spacers.any { it.rank < 0 }) return false
        val ranks = document.placements.map { it.rank } + document.spacers.map { it.rank }
        if (ranks.distinct().size != ranks.size) return false
        return document.placements.all { placement ->
            val entry = byId[placement.entryId] ?: return@all false
            if (placement.size !in entry.supportedSizes) return@all false
            placement.size.columns <= profile.columnCount
        }
    }
}

enum class StartRepairIncident {
    INVALID_DOCUMENT,
    PROFILE_MISMATCH,
    UNKNOWN_ENTRY_REMOVED,
    DUPLICATE_ENTRY_REMOVED,
    UNSUPPORTED_SIZE_REPLACED,
    INVALID_RANK_NORMALIZED,
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
        val seenEntries = mutableSetOf<LauncherEntryId>()
        val seenTiles = mutableSetOf<TileInstanceId>()
        val repairedUnranked = buildList {
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
                add(sized)
            }
        }
        val seenSpacerIds = mutableSetOf<TileInstanceId>()
        val repairedSpacers = source.spacers.filter { spacer ->
            seenSpacerIds.add(spacer.spacerId) && spacer.spacerId !in seenTiles && spacer.size.columns <= profile.columnCount
        }
        val sourceRanks = repairedUnranked.map { it.rank } + repairedSpacers.map { it.rank }
        val normalizeRanks = sourceRanks.any { it < 0 } || sourceRanks.distinct().size != sourceRanks.size
        if (normalizeRanks) incidents += StartRepairIncident.INVALID_RANK_NORMALIZED
        val ordered = (repairedUnranked.map { it.rank to it.tileId.value } +
            repairedSpacers.map { it.rank to it.spacerId.value }).sortedWith(compareBy({ it.first }, { it.second }))
        val normalizedRanks = ordered.mapIndexed { index, (_, id) -> id to index * 1024L }.toMap()
        val document = source.copy(
            placements = repairedUnranked
                .sortedWith(compareBy({ it.rank }, { it.tileId.value }))
                .map { entry -> entry.copy(rank = if (normalizeRanks) normalizedRanks.getValue(entry.tileId.value) else entry.rank) },
            spacers = repairedSpacers
                .sortedWith(compareBy({ it.rank }, { it.spacerId.value }))
                .map { spacer -> spacer.copy(rank = if (normalizeRanks) normalizedRanks.getValue(spacer.spacerId.value) else spacer.rank) },
        )
        return if (StartDocumentValidator.isValid(document, entries, profile)) {
            StartRepairResult(document, incidents, usedFallback = false)
        } else {
            fallback(defaultDocument, existing = incidents)
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
