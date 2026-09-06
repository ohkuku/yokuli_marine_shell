package com.yokuli.marine.feature.datasources

import com.yokuli.marine.data.catalog.SentenceParseStatus
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.phone.PhoneLocationSnapshot
import com.yokuli.marine.data.phone.PhoneLocationState
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.SourceDecision
import com.yokuli.marine.data.source.SourceDecisionStatus
import com.yokuli.marine.data.source.SourceKind

object DataSourcesProjector {
    fun project(
        sources: MarineSourceSnapshot,
        nmea: NmeaRuntimeSnapshot,
        phone: PhoneLocationSnapshot,
        local: DataSourcesLocalState,
        nowMillis: Long,
        notice: DataSourcesNotice? = null,
    ): DataSourcesUiState {
        require(nowMillis >= 0L)
        val allDataRows = sources.sourceCatalog.candidates
            .groupBy { it.id.key }
            .map { (key, candidates) ->
                val decision = sources.decisions.singleOrNull { it.key == key }
                    ?: SourceDecision(
                        key = key,
                        status = SourceDecisionStatus.NO_CANDIDATE,
                        selectedSource = null,
                        reason = null,
                        selectableCandidateCount = 0,
                        needsReview = false,
                    )
                val resolved = sources.resolvedData.items[key]
                DataSourceRowUi(
                    key = key,
                    decision = decision,
                    candidates = candidates.sortedBy { it.descriptor.displayName.lowercase() }.map { candidate ->
                        DataSourceCandidateUi(
                            source = candidate.id.source,
                            sourceName = candidate.descriptor.displayName,
                            kind = candidate.descriptor.kind,
                            detail = candidate.descriptor.detail,
                            value = candidate.value ?: candidate.lastValidValue,
                            availability = candidate.availability,
                            ageMillis = candidate.ageMillis,
                            evidence = candidate.evidence,
                            selected = decision.selectedSource == candidate.id.source,
                        )
                    },
                    resolvedValue = resolved?.value,
                    needsAttention = decision.status in setOf(
                        SourceDecisionStatus.NEEDS_SELECTION,
                        SourceDecisionStatus.SELECTED_UNAVAILABLE,
                        SourceDecisionStatus.SELECTED_MISSING,
                    ) || decision.needsReview,
                )
            }
            .sortedBy { it.key.stableLabel() }

        val connectionNames = nmea.connections.associate {
            it.stored.config.id to it.stored.config.displayName
        }
        val allSentenceRows = nmea.sentenceCatalog.entries.map { entry ->
            val raw = nmea.rawPreview.entries.asSequence()
                .filter { it.source == entry.key.source && it.sentenceId == entry.lastSentenceId }
                .sortedByDescending { it.receivedAtMillis }
                .take(MAX_RAW_EVIDENCE_PER_SENTENCE)
                .map { RawEvidenceUi(it.raw, it.receivedAtMillis, it.isCurrentSession) }
                .toList()
            SentenceRowUi(
                key = entry.key,
                sentenceId = entry.lastSentenceId,
                sourceName = connectionNames[entry.key.source.connectionId]
                    ?: entry.key.source.connectionId.value,
                meaning = when (entry.lastParseStatus) {
                    SentenceParseStatus.PARSED -> SentenceMeaningUi.PARSED
                    SentenceParseStatus.EXPLICIT_INVALID -> SentenceMeaningUi.EXPLICIT_INVALID
                    SentenceParseStatus.UNSUPPORTED -> SentenceMeaningUi.UNSUPPORTED
                },
                current = entry.isCurrentSession,
                ageMillis = (nowMillis - entry.lastSeenMillis).coerceAtLeast(0L),
                receivedCount = entry.receivedCount,
                rawEvidence = raw,
            )
        }.sortedWith(compareBy<SentenceRowUi> { it.sentenceId }.thenBy { it.sourceName.lowercase() })

        val visibleData = allDataRows.mapNotNull { row ->
            val candidates = row.candidates.filter { candidate -> candidate.matches(local.filter) }
            val filtered = row.copy(candidates = candidates)
            filtered.takeIf {
                candidates.isNotEmpty() &&
                    filtered.matches(local.filter) &&
                    filtered.matches(local.query)
            }
        }
        val visibleSentences = allSentenceRows.filter { row ->
            row.matches(local.filter) && row.matches(local.query)
        }
        val page = when (val requested = local.page) {
            DataSourcesLocalPage.Overview -> DataSourcesPageUi.Overview(visibleData, visibleSentences)
            is DataSourcesLocalPage.DataDetail -> allDataRows.singleOrNull { it.key == requested.key }
                ?.let(DataSourcesPageUi::DataDetail)
                ?: DataSourcesPageUi.Overview(visibleData, visibleSentences)
            is DataSourcesLocalPage.SentenceDetail -> allSentenceRows.singleOrNull { it.key == requested.key }
                ?.let(DataSourcesPageUi::SentenceDetail)
                ?: DataSourcesPageUi.Overview(visibleData, visibleSentences)
        }
        return DataSourcesUiState(
            page = page,
            viewMode = local.viewMode,
            filter = local.filter,
            query = local.query,
            allDataRows = allDataRows,
            allSentenceRows = allSentenceRows,
            phone = PhoneSourceSummaryUi(
                state = phone.state.toUi(),
                permission = phone.permission,
                enabledByUser = phone.enabledByUser,
            ),
            attentionCount = allDataRows.count { it.needsAttention } +
                allSentenceRows.count { !it.current || it.meaning == SentenceMeaningUi.EXPLICIT_INVALID },
            notice = notice,
        )
    }

    private const val MAX_RAW_EVIDENCE_PER_SENTENCE = 20
}

private fun DataSourceCandidateUi.matches(filter: DataSourcesFilter): Boolean = when (filter) {
    DataSourcesFilter.All,
    DataSourcesFilter.NeedsAttention,
    DataSourcesFilter.MultipleSources,
    -> true
    DataSourcesFilter.Phone -> kind == SourceKind.PHONE_SYSTEM_LOCATION
    is DataSourcesFilter.Connection -> source.connectionId == filter.id
}

private fun DataSourceRowUi.matches(filter: DataSourcesFilter): Boolean = when (filter) {
    DataSourcesFilter.All -> true
    DataSourcesFilter.NeedsAttention -> needsAttention
    DataSourcesFilter.MultipleSources -> candidates.size > 1
    DataSourcesFilter.Phone -> candidates.any { it.kind == SourceKind.PHONE_SYSTEM_LOCATION }
    is DataSourcesFilter.Connection -> candidates.any { it.source.connectionId == filter.id }
}

private fun DataSourceRowUi.matches(query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return true
    return key.stableLabel().contains(normalized) || candidates.any { candidate ->
        candidate.sourceName.lowercase().contains(normalized) ||
            candidate.detail.lowercase().contains(normalized) ||
            candidate.evidence.toString().lowercase().contains(normalized)
    }
}

private fun SentenceRowUi.matches(filter: DataSourcesFilter): Boolean = when (filter) {
    DataSourcesFilter.All -> true
    DataSourcesFilter.NeedsAttention -> !current || meaning == SentenceMeaningUi.EXPLICIT_INVALID
    DataSourcesFilter.MultipleSources -> false
    DataSourcesFilter.Phone -> false
    is DataSourcesFilter.Connection -> key.source.connectionId == filter.id
}

private fun SentenceRowUi.matches(query: String): Boolean {
    val normalized = query.trim().lowercase()
    return normalized.isEmpty() ||
        sentenceId.lowercase().contains(normalized) ||
        key.formatter.lowercase().contains(normalized) ||
        key.talker.lowercase().contains(normalized) ||
        sourceName.lowercase().contains(normalized)
}

private fun PhoneLocationState.toUi(): PhoneSourceUi = when (this) {
    PhoneLocationState.DISABLED_BY_USER -> PhoneSourceUi.DISABLED
    PhoneLocationState.PERMISSION_REQUIRED -> PhoneSourceUi.PERMISSION_REQUIRED
    PhoneLocationState.SYSTEM_LOCATION_DISABLED -> PhoneSourceUi.SYSTEM_LOCATION_DISABLED
    PhoneLocationState.STARTING -> PhoneSourceUi.STARTING
    PhoneLocationState.RECEIVING -> PhoneSourceUi.RECEIVING
    PhoneLocationState.INTERRUPTED -> PhoneSourceUi.INTERRUPTED
    PhoneLocationState.PLATFORM_RESTRICTED -> PhoneSourceUi.PLATFORM_RESTRICTED
}

internal fun DataKey.stableLabel(): String = when (this) {
    DataKey.Position -> "position"
    DataKey.SpeedOverGround -> "speed over ground sog"
    DataKey.CourseOverGround -> "course over ground cog"
    is DataKey.Heading -> "heading ${reference.name.lowercase()}"
    is DataKey.Depth -> "depth ${reference.name.lowercase()}"
    is DataKey.WindAngle -> "wind angle ${reference.name.lowercase()}"
    is DataKey.WindSpeed -> "wind speed ${reference.name.lowercase()}"
    DataKey.MagneticVariation -> "magnetic variation"
    DataKey.SourceTime -> "source time"
    DataKey.FixQuality -> "fix quality"
    DataKey.Satellites -> "satellites"
    DataKey.HorizontalDilution -> "horizontal dilution"
    DataKey.Altitude -> "altitude"
    DataKey.PositionAccuracy -> "position accuracy"
}
