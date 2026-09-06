package com.yokuli.marine.data.source

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SourceIdentity

const val SOURCE_DISCOVERY_WINDOW_MILLIS: Long = 3_000L
const val SOURCE_SELECTION_POLICY_VERSION: Int = 1
const val DEFAULT_SELECTION_AUDIT_LIMIT: Int = 64

enum class SourceKind {
    NMEA,
    PHONE_SYSTEM_LOCATION,
}

data class SourceDescriptor(
    val identity: SourceIdentity,
    val kind: SourceKind,
    val displayName: String,
    val detail: String,
    val present: Boolean = true,
) {
    init {
        require(displayName.isNotBlank())
    }
}

enum class SourceCandidateAvailability {
    LIVE,
    HELD,
    STALE,
    INVALID,
    UNAVAILABLE,
    /** A persisted source identity whose live/historical candidate is no longer in this process. */
    MISSING,
}

sealed interface SourceEvidence {
    data class Nmea(
        val sentenceIds: Set<String>,
        val formatters: Set<String>,
    ) : SourceEvidence

    data class PhoneSystemLocation(
        val provider: String,
        val permissionIsApproximate: Boolean,
    ) : SourceEvidence {
        init {
            require(provider.isNotBlank())
        }
    }
}

data class SourceCandidate(
    val id: CandidateId,
    val descriptor: SourceDescriptor,
    val value: MarineValue?,
    val lastValidValue: MarineValue?,
    val availability: SourceCandidateAvailability,
    val ageMillis: Long?,
    val receivedAtMillis: Long?,
    val groupId: ObservationGroupId?,
    val evidence: SourceEvidence,
) {
    init {
        require(id.source == descriptor.identity)
        require(ageMillis == null || ageMillis >= 0L)
        require(receivedAtMillis == null || receivedAtMillis >= 0L)
        require(availability != SourceCandidateAvailability.MISSING) {
            "Missing selections are represented by ResolvedDatum, not catalog candidates"
        }
        require(availability != SourceCandidateAvailability.LIVE || value != null)
    }
}

data class SourceCatalogSnapshot(
    val candidates: List<SourceCandidate>,
    val evaluatedAtMillis: Long,
    val revision: Long,
) {
    init {
        require(evaluatedAtMillis >= 0L)
        require(revision >= 0L)
        require(candidates.map { it.id }.distinct().size == candidates.size)
    }

    companion object {
        val EMPTY = SourceCatalogSnapshot(emptyList(), 0L, 0L)
    }
}

enum class SelectionReason {
    AUTOMATIC_UNIQUE,
    USER,
}

sealed interface SourcePreference {
    data class Selected(
        val source: SourceIdentity,
        val reason: SelectionReason,
    ) : SourcePreference

    data object Disabled : SourcePreference
}

enum class SourceDecisionStatus {
    NO_CANDIDATE,
    DISCOVERING,
    NEEDS_SELECTION,
    USING,
    SELECTED_UNAVAILABLE,
    SELECTED_MISSING,
    DISABLED,
}

data class SourceDecision(
    val key: DataKey,
    val status: SourceDecisionStatus,
    val selectedSource: SourceIdentity?,
    val reason: SelectionReason?,
    val selectableCandidateCount: Int,
    val needsReview: Boolean,
) {
    init {
        require(selectableCandidateCount >= 0)
    }
}

data class ResolvedDatum(
    val key: DataKey,
    val source: SourceIdentity,
    val candidate: SourceCandidate?,
    val value: MarineValue?,
    val availability: SourceCandidateAvailability,
    val selectionReason: SelectionReason,
    val selectionRevision: Long,
) {
    init {
        require(candidate == null || candidate.id == CandidateId(key, source))
        require(candidate != null || availability == SourceCandidateAvailability.MISSING)
        require(selectionRevision >= 0L)
    }
}

data class ResolvedDataSnapshot(
    val items: Map<DataKey, ResolvedDatum>,
    val selectionRevision: Long,
    val evaluatedAtMillis: Long,
) {
    init {
        require(selectionRevision >= 0L)
        require(evaluatedAtMillis >= 0L)
        require(items.all { (key, datum) -> key == datum.key && datum.selectionRevision == selectionRevision })
    }
}

enum class SourceSelectionFailure {
    NOT_INITIALIZED,
    CANDIDATE_UNAVAILABLE,
    PERSISTENCE_FAILED,
    PERSISTENCE_CONFLICT,
}

data class MarineSourceSnapshot(
    val sourceCatalog: SourceCatalogSnapshot,
    val decisions: List<SourceDecision>,
    val resolvedData: ResolvedDataSnapshot,
    val selectionRevision: Long,
    val revision: Long,
    val lastFailure: SourceSelectionFailure?,
) {
    init {
        require(selectionRevision >= 0L && revision >= 0L)
        require(resolvedData.selectionRevision == selectionRevision)
        require(decisions.map { it.key }.distinct().size == decisions.size)
    }

    companion object {
        val EMPTY = MarineSourceSnapshot(
            sourceCatalog = SourceCatalogSnapshot.EMPTY,
            decisions = emptyList(),
            resolvedData = ResolvedDataSnapshot(emptyMap(), 0L, 0L),
            selectionRevision = 0L,
            revision = 0L,
            lastFailure = null,
        )
    }
}

data class SourceSelectionAuditEntry(
    val occurredAtMillis: Long,
    val key: DataKey,
    val oldSource: SourceIdentity?,
    val newSource: SourceIdentity?,
    val reason: SelectionReason?,
    val policyVersion: Int = SOURCE_SELECTION_POLICY_VERSION,
) {
    init {
        require(occurredAtMillis >= 0L)
        require(policyVersion > 0)
    }
}
