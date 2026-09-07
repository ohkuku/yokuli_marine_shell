package com.yokuli.marine.feature.data

import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.catalog.SentenceParseStatus
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.DepthReference
import com.yokuli.marine.data.model.HeadingReference
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.model.WindReference
import com.yokuli.marine.data.model.WindSpeedReference
import com.yokuli.marine.data.phone.PhoneLocationDemand
import com.yokuli.marine.data.phone.PhoneLocationDemandPolicy
import com.yokuli.marine.data.phone.PhoneLocationSnapshot
import com.yokuli.marine.data.runtime.ConnectionInputState
import com.yokuli.marine.data.runtime.ConnectionTransportState
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.ResolvedDatum
import com.yokuli.marine.data.source.SelectionReason
import com.yokuli.marine.data.source.SourceCandidate
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.data.source.SourceDecisionStatus
import com.yokuli.marine.data.source.SourceEvidence
import com.yokuli.marine.data.source.SourcePreference
import com.yokuli.marine.data.source.SourceSelectionCommand

enum class DataSection { OVERVIEW, INPUTS, SOURCES, FLOW, DIAGNOSTICS }

/** Product-level groups; wire sentence formatters and transport endpoints are evidence, not groups. */
enum class SourceGroup(val keys: Set<DataKey>) {
    POSITION_AND_MOTION(
        setOf(
            DataKey.Position,
            DataKey.SpeedOverGround,
            DataKey.CourseOverGround,
            DataKey.PositionAccuracy,
            DataKey.SourceTime,
            DataKey.FixQuality,
            DataKey.Satellites,
            DataKey.HorizontalDilution,
            DataKey.Altitude,
        ),
    ),
    HEADING(
        setOf(
            DataKey.Heading(HeadingReference.TRUE),
            DataKey.Heading(HeadingReference.MAGNETIC),
            DataKey.MagneticVariation,
        ),
    ),
    DEPTH(
        setOf(
            DataKey.Depth(DepthReference.BELOW_TRANSDUCER),
            DataKey.Depth(DepthReference.BELOW_SURFACE),
            DataKey.Depth(DepthReference.BELOW_KEEL),
        ),
    ),
    WIND(
        setOf(
            DataKey.WindAngle(WindReference.APPARENT),
            DataKey.WindAngle(WindReference.TRUE_RELATIVE),
            DataKey.WindAngle(WindReference.TRUE_NORTH),
            DataKey.WindAngle(WindReference.MAGNETIC_NORTH),
            DataKey.WindSpeed(WindSpeedReference.APPARENT),
            DataKey.WindSpeed(WindSpeedReference.TRUE),
        ),
    ),
}

enum class SourceGroupStatus {
    NO_DATA,
    DISCOVERING,
    NEEDS_SELECTION,
    USING,
    SELECTED_UNAVAILABLE,
    MIXED_LEGACY_SELECTION,
    DISABLED,
}

data class SourceGroupEvidence(
    val dataKeys: Set<DataKey>,
    val sentenceIds: Set<String>,
    val formatters: Set<String>,
    val phoneProviders: Set<String>,
    val availabilityByKey: Map<DataKey, SourceCandidateAvailability>,
)

data class SourceGroupCandidate(
    val source: SourceIdentity,
    val displayName: String,
    val selectableKeys: Set<DataKey>,
    val evidence: SourceGroupEvidence,
)

data class SourceGroupState(
    val group: SourceGroup,
    val status: SourceGroupStatus,
    val selectedSource: SourceIdentity?,
    val candidates: List<SourceGroupCandidate>,
)

data class DataInputState(
    val id: ConnectionId,
    val displayName: String,
    val runIntent: ConnectionRunIntent,
    val transport: ConnectionTransportState,
    val input: ConnectionInputState,
)

data class DataFlowLink(
    val source: SourceIdentity,
    val sourceDisplayName: String,
    val sentenceFamilies: Set<String>,
    val group: SourceGroup,
    val selectedForOutput: Boolean,
)

data class DataDiagnosticsState(
    val sentenceTypeCount: Int,
    val rawPreviewCount: Int,
    val checksumFailureCount: Long,
    val ingressDropCount: Long,
)

data class DataSentenceState(
    val sentenceId: String,
    val sourceName: String,
    val formatter: String,
    val semanticInstance: String,
    val status: SentenceParseStatus,
    val receivedCount: Long,
    val current: Boolean,
)

data class DataRawLineState(
    val receivedAtMillis: Long,
    val sourceName: String,
    val sender: String?,
    val raw: String,
    val current: Boolean,
)

enum class DataNotice {
    SAVED,
    DISABLED,
    CANDIDATE_UNAVAILABLE,
    PERSISTENCE_FAILED,
    PHONE_PERMISSION_REQUIRED,
    PHONE_LOCATION_DISABLED,
    PHONE_PLATFORM_RESTRICTED,
    ACTION_QUEUE_FULL,
}

data class DataUiState(
    val section: DataSection = DataSection.OVERVIEW,
    val resolvedValues: Map<DataKey, ResolvedDatum> = emptyMap(),
    val groups: List<SourceGroupState> = emptyList(),
    val inputs: List<DataInputState> = emptyList(),
    val flow: List<DataFlowLink> = emptyList(),
    val diagnostics: DataDiagnosticsState = DataDiagnosticsState(0, 0, 0L, 0L),
    val sentences: List<DataSentenceState> = emptyList(),
    val rawLines: List<DataRawLineState> = emptyList(),
    val phoneDemand: PhoneLocationDemand = PhoneLocationDemand.NONE,
    val phone: PhoneLocationSnapshot = PhoneLocationSnapshot.EMPTY,
    val focusedSourceConnectionId: ConnectionId? = null,
    val notice: DataNotice? = null,
)

object DataDomainProjector {
    fun project(
        sources: MarineSourceSnapshot,
        nmea: NmeaRuntimeSnapshot,
        section: DataSection = DataSection.OVERVIEW,
    ): DataUiState {
        val groups = SourceGroup.entries.map { group -> projectGroup(group, sources) }
        val connectionNames = nmea.connections.associate {
            it.stored.config.id to it.stored.config.displayName
        }
        return DataUiState(
            section = section,
            resolvedValues = sources.resolvedData.items,
            groups = groups,
            inputs = nmea.connections.map { connection ->
                DataInputState(
                    id = connection.stored.config.id,
                    displayName = connection.stored.config.displayName,
                    runIntent = connection.stored.runIntent,
                    transport = connection.transport,
                    input = connection.input,
                )
            },
            flow = groups.flatMap { group ->
                group.candidates.map { candidate ->
                    DataFlowLink(
                        source = candidate.source,
                        sourceDisplayName = candidate.displayName,
                        sentenceFamilies = candidate.evidence.formatters,
                        group = group.group,
                        selectedForOutput = group.selectedSource == candidate.source &&
                            group.status == SourceGroupStatus.USING,
                    )
                }
            },
            diagnostics = DataDiagnosticsState(
                sentenceTypeCount = nmea.sentenceCatalog.entries.size,
                rawPreviewCount = nmea.rawPreview.entries.size,
                checksumFailureCount = nmea.connections.sumOf { it.metrics.checksumFailureCount },
                ingressDropCount = nmea.connections.sumOf { it.metrics.ingressDropCount },
            ),
            sentences = nmea.sentenceCatalog.entries.map { entry ->
                DataSentenceState(
                    sentenceId = entry.lastSentenceId,
                    sourceName = connectionNames[entry.key.source.connectionId]
                        ?: entry.key.source.connectionId.value,
                    formatter = entry.key.formatter,
                    semanticInstance = entry.key.semanticInstance,
                    status = entry.lastParseStatus,
                    receivedCount = entry.receivedCount,
                    current = entry.isCurrentSession,
                )
            },
            rawLines = nmea.rawPreview.entries.map { entry ->
                DataRawLineState(
                    receivedAtMillis = entry.receivedAtMillis,
                    sourceName = connectionNames[entry.source.connectionId]
                        ?: entry.source.connectionId.value,
                    sender = entry.sender?.let { "${it.hostAddress}:${it.port}" },
                    raw = entry.raw,
                    current = entry.isCurrentSession,
                )
            },
            phoneDemand = PhoneLocationDemandPolicy.resolve(sources),
        )
    }

    private fun projectGroup(group: SourceGroup, sources: MarineSourceSnapshot): SourceGroupState {
        val candidates = sources.sourceCatalog.candidates.filter { it.id.key in group.keys }
            .groupBy { it.id.source }
            .map { (source, values) -> values.toGroupCandidate(source) }
            .sortedBy { it.displayName.lowercase() }
        val decisions = sources.decisions.filter { it.key in group.keys }
        val selectedSources = decisions.mapNotNull { it.selectedSource }.toSet()
        val selected = selectedSources.singleOrNull()
        val status = when {
            selectedSources.size > 1 -> SourceGroupStatus.MIXED_LEGACY_SELECTION
            selected != null && decisions.any {
                it.selectedSource == selected && it.status in setOf(
                    SourceDecisionStatus.SELECTED_UNAVAILABLE,
                    SourceDecisionStatus.SELECTED_MISSING,
                )
            } -> SourceGroupStatus.SELECTED_UNAVAILABLE
            selected != null -> SourceGroupStatus.USING
            decisions.isNotEmpty() && decisions.all { it.status == SourceDecisionStatus.DISABLED } ->
                SourceGroupStatus.DISABLED
            candidates.isEmpty() -> SourceGroupStatus.NO_DATA
            decisions.any { it.status == SourceDecisionStatus.DISCOVERING } -> SourceGroupStatus.DISCOVERING
            else -> SourceGroupStatus.NEEDS_SELECTION
        }
        return SourceGroupState(group, status, selected, candidates)
    }

    private fun List<SourceCandidate>.toGroupCandidate(source: SourceIdentity): SourceGroupCandidate {
        val nmea = mapNotNull { it.evidence as? SourceEvidence.Nmea }
        val phone = mapNotNull { it.evidence as? SourceEvidence.PhoneSystemLocation }
        return SourceGroupCandidate(
            source = source,
            displayName = first().descriptor.displayName,
            selectableKeys = filter { it.availability.isSelectable }.mapTo(linkedSetOf()) { it.id.key },
            evidence = SourceGroupEvidence(
                dataKeys = mapTo(linkedSetOf()) { it.id.key },
                sentenceIds = nmea.flatMapTo(linkedSetOf()) { it.sentenceIds },
                formatters = nmea.flatMapTo(linkedSetOf()) { it.formatters },
                phoneProviders = phone.mapTo(linkedSetOf()) { it.provider },
                availabilityByKey = associate { it.id.key to it.availability },
            ),
        )
    }

    private val SourceCandidateAvailability.isSelectable: Boolean
        get() = this == SourceCandidateAvailability.LIVE || this == SourceCandidateAvailability.HELD
}

sealed interface SourceGroupSelectionPlan {
    data class Ready(val command: SourceSelectionCommand.ApplyAtomically) : SourceGroupSelectionPlan
    data object CandidateUnavailable : SourceGroupSelectionPlan
}

/** Compatibility boundary from group UX to the durable per-DataKey selection store. */
object SourceGroupSelectionAdapter {
    fun select(
        group: SourceGroup,
        source: SourceIdentity,
        snapshot: MarineSourceSnapshot,
    ): SourceGroupSelectionPlan {
        val selectableKeys = snapshot.sourceCatalog.candidates.asSequence()
            .filter { candidate ->
                candidate.id.source == source &&
                    candidate.id.key in group.keys &&
                    candidate.availability.isSelectable
            }
            .map { it.id.key }
            .toSet()
        if (selectableKeys.isEmpty()) return SourceGroupSelectionPlan.CandidateUnavailable
        val preferences = group.keys.associateWith { key ->
            if (key in selectableKeys) SourcePreference.Selected(source, SelectionReason.USER)
            else SourcePreference.Disabled
        }
        return SourceGroupSelectionPlan.Ready(SourceSelectionCommand.ApplyAtomically(preferences))
    }

    fun disable(group: SourceGroup): SourceSelectionCommand.ApplyAtomically =
        SourceSelectionCommand.ApplyAtomically(group.keys.associateWith { SourcePreference.Disabled })

    private val SourceCandidateAvailability.isSelectable: Boolean
        get() = this == SourceCandidateAvailability.LIVE || this == SourceCandidateAvailability.HELD
}
