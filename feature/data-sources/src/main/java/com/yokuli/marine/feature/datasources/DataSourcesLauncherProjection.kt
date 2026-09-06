package com.yokuli.marine.feature.datasources

import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.data.source.SourceDecisionStatus
import com.yokuli.marine.data.source.SourceKind

private const val MAX_TILE_RELATIONS = 3

enum class DataSourcesTilePriority { WAITING, USING, ATTENTION }

data class DataSourceTileRelation(
    val key: DataKey,
    val sourceName: String,
    val sourceKind: SourceKind,
    val availability: SourceCandidateAvailability,
)

data class DataSourcesTileState(
    val priority: DataSourcesTilePriority,
    val usingCount: Int,
    val attentionCount: Int,
    val needsSelectionCount: Int,
    val interruptedCount: Int,
    val relations: List<DataSourceTileRelation>,
    val persistenceFailed: Boolean,
) {
    init {
        require(listOf(usingCount, attentionCount, needsSelectionCount, interruptedCount).all { it >= 0 })
        require(relations.size <= MAX_TILE_RELATIONS)
    }

    val critical: Boolean get() = priority == DataSourcesTilePriority.ATTENTION
}

data class DataSourcesStatusState(
    val visible: Boolean,
    val attentionCount: Int,
    val needsSelectionCount: Int,
    val interruptedCount: Int,
    val openNeedsAttention: Boolean,
)

data class DataSourcesLauncherState(
    val tile: DataSourcesTileState,
    val status: DataSourcesStatusState,
)

object DataSourcesLauncherProjector {
    fun project(snapshot: MarineSourceSnapshot): DataSourcesLauncherState {
        val needsSelection = snapshot.decisions.count { it.status == SourceDecisionStatus.NEEDS_SELECTION }
        val interrupted = snapshot.decisions.count {
            it.status == SourceDecisionStatus.SELECTED_UNAVAILABLE ||
                it.status == SourceDecisionStatus.SELECTED_MISSING
        }
        val using = snapshot.decisions.count { it.status == SourceDecisionStatus.USING }
        val persistenceFailed = snapshot.lastFailure != null
        val attention = needsSelection + interrupted + if (persistenceFailed) 1 else 0
        val candidates = snapshot.sourceCatalog.candidates.associateBy { it.id }
        val relations = snapshot.resolvedData.items.values.asSequence()
            .sortedBy { it.key.stableLabel() }
            .take(MAX_TILE_RELATIONS)
            .map { resolved ->
                DataSourceTileRelation(
                    key = resolved.key,
                    sourceName = candidates[com.yokuli.marine.data.model.CandidateId(resolved.key, resolved.source)]
                        ?.descriptor?.displayName
                        ?: resolved.source.connectionId.value,
                    sourceKind = candidates[com.yokuli.marine.data.model.CandidateId(resolved.key, resolved.source)]
                        ?.descriptor?.kind
                        ?: SourceKind.NMEA,
                    availability = resolved.availability,
                )
            }
            .toList()
        val priority = when {
            attention > 0 -> DataSourcesTilePriority.ATTENTION
            using > 0 -> DataSourcesTilePriority.USING
            else -> DataSourcesTilePriority.WAITING
        }
        val tile = DataSourcesTileState(
            priority,
            using,
            attention,
            needsSelection,
            interrupted,
            relations,
            persistenceFailed,
        )
        return DataSourcesLauncherState(
            tile,
            DataSourcesStatusState(
                visible = snapshot.decisions.isNotEmpty() || persistenceFailed,
                attentionCount = attention,
                needsSelectionCount = needsSelection,
                interruptedCount = interrupted,
                openNeedsAttention = attention > 0,
            ),
        )
    }
}

class DataSourcesTileDisplaySlot(initial: DataSourcesTileState) {
    var shown: DataSourcesTileState = initial
        private set

    fun resolve(incoming: DataSourcesTileState, liveContentEnabled: Boolean): DataSourcesTileState {
        if (liveContentEnabled || incoming.critical || shown.critical != incoming.critical) shown = incoming
        return shown
    }
}
