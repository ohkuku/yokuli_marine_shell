package com.yokuli.marine.data.source

import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.SourceIdentity

@ConsistentCopyVisibility
data class SourceSelectionState private constructor(
    val catalog: SourceCatalogSnapshot,
    val preferences: Map<DataKey, SourcePreference>,
    val discoveryStartedAtMillis: Map<DataKey, Long>,
    val selectionRevision: Long,
    val revision: Long,
    val audit: List<SourceSelectionAuditEntry>,
    val maxAuditEntries: Int,
    val lastFailure: SourceSelectionFailure?,
) {
    val snapshot: MarineSourceSnapshot
        get() = projectSnapshot()

    companion object {
        fun empty(
            preferences: Map<DataKey, SourcePreference> = emptyMap(),
            selectionRevision: Long = 0L,
            maxAuditEntries: Int = DEFAULT_SELECTION_AUDIT_LIMIT,
        ): SourceSelectionState {
            require(selectionRevision >= 0L)
            require(maxAuditEntries > 0)
            return SourceSelectionState(
                catalog = SourceCatalogSnapshot.EMPTY,
                preferences = preferences,
                discoveryStartedAtMillis = emptyMap(),
                selectionRevision = selectionRevision,
                revision = 0L,
                audit = emptyList(),
                maxAuditEntries = maxAuditEntries,
                lastFailure = null,
            )
        }
    }

    internal fun withFailure(failure: SourceSelectionFailure?): SourceSelectionState = copy(
        revision = increment(revision),
        lastFailure = failure,
    )

    internal fun withPersistedRevision(persistedRevision: Long): SourceSelectionState {
        require(persistedRevision >= 0L)
        return copy(selectionRevision = persistedRevision, lastFailure = null)
    }
}

sealed interface SourceSelectionAction {
    data class CatalogChanged(
        val catalog: SourceCatalogSnapshot,
        val evaluatedAtMillis: Long,
        val allowAutomaticSelection: Boolean = true,
    ) : SourceSelectionAction {
        init {
            require(evaluatedAtMillis >= 0L)
        }
    }

    data class Select(
        val key: DataKey,
        val source: SourceIdentity,
        val selectedAtMillis: Long,
    ) : SourceSelectionAction {
        init {
            require(selectedAtMillis >= 0L)
        }
    }

    data class Disable(
        val key: DataKey,
        val disabledAtMillis: Long,
    ) : SourceSelectionAction {
        init {
            require(disabledAtMillis >= 0L)
        }
    }
}

enum class SourceSelectionRejection {
    CANDIDATE_UNAVAILABLE,
}

data class SourceSelectionTransition(
    val state: SourceSelectionState,
    val persistenceRequired: Boolean = false,
    val rejection: SourceSelectionRejection? = null,
)

class SourceSelectionReducer {
    fun reduce(
        state: SourceSelectionState,
        action: SourceSelectionAction,
    ): SourceSelectionTransition = when (action) {
        is SourceSelectionAction.CatalogChanged -> catalogChanged(state, action)
        is SourceSelectionAction.Select -> select(state, action)
        is SourceSelectionAction.Disable -> disable(state, action)
    }

    private fun catalogChanged(
        state: SourceSelectionState,
        action: SourceSelectionAction.CatalogChanged,
    ): SourceSelectionTransition {
        val candidateKeys = action.catalog.candidates.mapTo(linkedSetOf()) { it.id.key }
        val allKeys = linkedSetOf<DataKey>().apply {
            addAll(candidateKeys)
            addAll(state.preferences.keys)
            addAll(state.discoveryStartedAtMillis.keys)
        }
        val discovery = state.discoveryStartedAtMillis.toMutableMap()
        allKeys.forEach { key ->
            if (key !in state.preferences && action.catalog.liveCandidates(key).isNotEmpty()) {
                discovery.putIfAbsent(key, action.evaluatedAtMillis)
            }
        }

        var preferences = state.preferences
        var audit = state.audit
        var changedPreference = false
        if (action.allowAutomaticSelection) {
            allKeys.sortedBy(DataKey::stableKey).forEach { key ->
                if (key !in preferences) {
                    val started = discovery[key]
                    val live = action.catalog.liveCandidates(key)
                    if (
                        started != null &&
                        action.evaluatedAtMillis - started >= SOURCE_DISCOVERY_WINDOW_MILLIS &&
                        live.size == 1
                    ) {
                        val source = live.single().id.source
                        preferences = preferences + (key to SourcePreference.Selected(source, SelectionReason.AUTOMATIC_UNIQUE))
                        audit = audit.appendBounded(
                            SourceSelectionAuditEntry(
                                occurredAtMillis = action.evaluatedAtMillis,
                                key = key,
                                oldSource = null,
                                newSource = source,
                                reason = SelectionReason.AUTOMATIC_UNIQUE,
                            ),
                            state.maxAuditEntries,
                        )
                        changedPreference = true
                    }
                }
            }
        }
        val next = state.copy(
            catalog = action.catalog,
            preferences = preferences,
            discoveryStartedAtMillis = discovery,
            selectionRevision = if (changedPreference) increment(state.selectionRevision) else state.selectionRevision,
            revision = increment(state.revision),
            audit = audit,
            lastFailure = null,
        )
        return SourceSelectionTransition(next, persistenceRequired = changedPreference)
    }

    private fun select(
        state: SourceSelectionState,
        action: SourceSelectionAction.Select,
    ): SourceSelectionTransition {
        val candidate = state.catalog.candidates.singleOrNull {
            it.id.key == action.key && it.id.source == action.source
        }
        if (candidate == null || !candidate.availability.isSelectable) {
            return SourceSelectionTransition(
                state = state,
                rejection = SourceSelectionRejection.CANDIDATE_UNAVAILABLE,
            )
        }
        val old = (state.preferences[action.key] as? SourcePreference.Selected)?.source
        val preference = SourcePreference.Selected(action.source, SelectionReason.USER)
        if (state.preferences[action.key] == preference) return SourceSelectionTransition(state)
        return SourceSelectionTransition(
            state = state.copy(
                preferences = state.preferences + (action.key to preference),
                selectionRevision = increment(state.selectionRevision),
                revision = increment(state.revision),
                audit = state.audit.appendBounded(
                    SourceSelectionAuditEntry(
                        occurredAtMillis = action.selectedAtMillis,
                        key = action.key,
                        oldSource = old,
                        newSource = action.source,
                        reason = SelectionReason.USER,
                    ),
                    state.maxAuditEntries,
                ),
                lastFailure = null,
            ),
            persistenceRequired = true,
        )
    }

    private fun disable(
        state: SourceSelectionState,
        action: SourceSelectionAction.Disable,
    ): SourceSelectionTransition {
        if (state.preferences[action.key] == SourcePreference.Disabled) return SourceSelectionTransition(state)
        val old = (state.preferences[action.key] as? SourcePreference.Selected)?.source
        return SourceSelectionTransition(
            state = state.copy(
                preferences = state.preferences + (action.key to SourcePreference.Disabled),
                selectionRevision = increment(state.selectionRevision),
                revision = increment(state.revision),
                audit = state.audit.appendBounded(
                    SourceSelectionAuditEntry(
                        occurredAtMillis = action.disabledAtMillis,
                        key = action.key,
                        oldSource = old,
                        newSource = null,
                        reason = null,
                    ),
                    state.maxAuditEntries,
                ),
                lastFailure = null,
            ),
            persistenceRequired = true,
        )
    }
}

private fun SourceSelectionState.projectSnapshot(): MarineSourceSnapshot {
    val keys = linkedSetOf<DataKey>().apply {
        addAll(catalog.candidates.map { it.id.key })
        addAll(preferences.keys)
        addAll(discoveryStartedAtMillis.keys)
    }
    val decisions = mutableListOf<SourceDecision>()
    val resolved = linkedMapOf<DataKey, ResolvedDatum>()
    keys.sortedBy(DataKey::stableKey).forEach { key ->
        val candidates = catalog.candidates.filter { it.id.key == key }
        val selectable = candidates.filter { it.availability.isSelectable }
        when (val preference = preferences[key]) {
            SourcePreference.Disabled -> decisions += SourceDecision(
                key = key,
                status = SourceDecisionStatus.DISABLED,
                selectedSource = null,
                reason = null,
                selectableCandidateCount = selectable.size,
                needsReview = false,
            )
            is SourcePreference.Selected -> {
                val selected = candidates.singleOrNull { it.id.source == preference.source }
                val status = when {
                    selected == null || !selected.descriptor.present -> SourceDecisionStatus.SELECTED_MISSING
                    selected.availability.isSelectable -> SourceDecisionStatus.USING
                    else -> SourceDecisionStatus.SELECTED_UNAVAILABLE
                }
                decisions += SourceDecision(
                    key = key,
                    status = status,
                    selectedSource = preference.source,
                    reason = preference.reason,
                    selectableCandidateCount = selectable.size,
                    needsReview = preference.reason == SelectionReason.AUTOMATIC_UNIQUE &&
                        selectable.any { it.id.source != preference.source },
                )
                resolved[key] = ResolvedDatum(
                    key = key,
                    source = preference.source,
                    candidate = selected,
                    value = selected?.let {
                        if (it.availability.isSelectable) it.value else it.lastValidValue
                    },
                    availability = selected?.takeIf { it.descriptor.present }?.availability
                        ?: SourceCandidateAvailability.MISSING,
                    selectionReason = preference.reason,
                    selectionRevision = selectionRevision,
                )
            }
            null -> {
                val started = discoveryStartedAtMillis[key]
                val status = when {
                    selectable.isEmpty() -> SourceDecisionStatus.NO_CANDIDATE
                    started == null || catalog.evaluatedAtMillis - started < SOURCE_DISCOVERY_WINDOW_MILLIS ->
                        SourceDecisionStatus.DISCOVERING
                    else -> SourceDecisionStatus.NEEDS_SELECTION
                }
                decisions += SourceDecision(
                    key = key,
                    status = status,
                    selectedSource = null,
                    reason = null,
                    selectableCandidateCount = selectable.size,
                    needsReview = status == SourceDecisionStatus.NEEDS_SELECTION,
                )
            }
        }
    }
    return MarineSourceSnapshot(
        sourceCatalog = catalog,
        decisions = decisions,
        resolvedData = ResolvedDataSnapshot(
            items = resolved,
            selectionRevision = selectionRevision,
            evaluatedAtMillis = catalog.evaluatedAtMillis,
        ),
        selectionRevision = selectionRevision,
        revision = revision,
        lastFailure = lastFailure,
    )
}

private val SourceCandidateAvailability.isSelectable: Boolean
    get() = this == SourceCandidateAvailability.LIVE || this == SourceCandidateAvailability.HELD

private fun SourceCatalogSnapshot.liveCandidates(key: DataKey): List<SourceCandidate> =
    candidates.filter { it.id.key == key && it.availability == SourceCandidateAvailability.LIVE }

private fun List<SourceSelectionAuditEntry>.appendBounded(
    entry: SourceSelectionAuditEntry,
    maximum: Int,
): List<SourceSelectionAuditEntry> = (this + entry).takeLast(maximum)

private fun DataKey.stableKey(): String = toString()

private fun increment(value: Long): Long = if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L
