package com.yokuli.marine.data.source

import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.time.MonotonicClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SourceSelectionCommand {
    data class Select(val key: DataKey, val source: SourceIdentity) : SourceSelectionCommand
    data class Disable(val key: DataKey) : SourceSelectionCommand
}

sealed interface SourceSelectionCommandResult {
    data class Success(val selectionRevision: Long) : SourceSelectionCommandResult
    data class Rejected(val failure: SourceSelectionFailure) : SourceSelectionCommandResult
}

interface MarineSourceRuntimePort {
    val state: StateFlow<MarineSourceSnapshot>
    suspend fun execute(command: SourceSelectionCommand): SourceSelectionCommandResult
}

/**
 * Serialized source-policy owner. Persistence succeeds before a selection and its resolved value
 * are published, so consumers cannot observe a source label from one revision and a value from
 * another.
 */
class DefaultMarineSourceRuntime(
    private val repository: SourceSelectionRepository,
    private val clock: MonotonicClock,
    private val reducer: SourceSelectionReducer = SourceSelectionReducer(),
) : MarineSourceRuntimePort {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(MarineSourceSnapshot.EMPTY)
    override val state: StateFlow<MarineSourceSnapshot> = mutableState.asStateFlow()
    private var selectionState = SourceSelectionState.empty()
    private var storeRevision = 0L
    private var initialized = false

    suspend fun initialize(): SourceSelectionLoadResult = mutex.withLock {
        if (initialized) return@withLock SourceSelectionLoadResult.Loaded(
            PersistedSourceSelections(
                revision = storeRevision,
                preferences = selectionState.preferences,
            ),
        )
        when (val loaded = repository.load()) {
            is SourceSelectionLoadResult.Loaded -> {
                storeRevision = loaded.state.revision
                selectionState = SourceSelectionState.empty(
                    preferences = loaded.state.preferences,
                    selectionRevision = loaded.state.revision,
                )
                val transition = reducer.reduce(
                    selectionState,
                    SourceSelectionAction.CatalogChanged(
                        catalog = SourceCatalogSnapshot.EMPTY.copy(evaluatedAtMillis = clock.nowMillis()),
                        evaluatedAtMillis = clock.nowMillis(),
                        allowAutomaticSelection = false,
                    ),
                )
                selectionState = transition.state
                initialized = true
                publish()
                loaded
            }
            is SourceSelectionLoadResult.Failed -> {
                selectionState = selectionState.withFailure(SourceSelectionFailure.PERSISTENCE_FAILED)
                publish()
                loaded
            }
        }
    }

    suspend fun updateCatalog(catalog: SourceCatalogSnapshot) = mutex.withLock {
        check(initialized) { "Source runtime must be initialized before catalog updates" }
        val action = SourceSelectionAction.CatalogChanged(catalog, clock.nowMillis())
        val transition = reducer.reduce(selectionState, action)
        if (!transition.persistenceRequired) {
            selectionState = transition.state
            publish()
            return@withLock
        }
        when (val saved = repository.save(storeRevision, transition.state.preferences)) {
            is SourceSelectionSaveResult.Saved -> acceptPersisted(transition.state, saved.state)
            is SourceSelectionSaveResult.Conflict -> {
                selectionState = reducer.reduce(
                    selectionState,
                    action.copy(allowAutomaticSelection = false),
                ).state.withFailure(SourceSelectionFailure.PERSISTENCE_CONFLICT)
                publish()
            }
            is SourceSelectionSaveResult.Failed -> {
                selectionState = reducer.reduce(
                    selectionState,
                    action.copy(allowAutomaticSelection = false),
                ).state.withFailure(SourceSelectionFailure.PERSISTENCE_FAILED)
                publish()
            }
        }
    }

    suspend fun tick() = updateCatalog(
        state.value.sourceCatalog.copy(evaluatedAtMillis = clock.nowMillis()),
    )

    override suspend fun execute(command: SourceSelectionCommand): SourceSelectionCommandResult = mutex.withLock {
        if (!initialized) return@withLock SourceSelectionCommandResult.Rejected(SourceSelectionFailure.NOT_INITIALIZED)
        val action = when (command) {
            is SourceSelectionCommand.Select -> SourceSelectionAction.Select(
                command.key,
                command.source,
                clock.nowMillis(),
            )
            is SourceSelectionCommand.Disable -> SourceSelectionAction.Disable(command.key, clock.nowMillis())
        }
        val transition = reducer.reduce(selectionState, action)
        if (transition.rejection != null) {
            return@withLock SourceSelectionCommandResult.Rejected(SourceSelectionFailure.CANDIDATE_UNAVAILABLE)
        }
        if (!transition.persistenceRequired) {
            return@withLock SourceSelectionCommandResult.Success(selectionState.selectionRevision)
        }
        when (val saved = repository.save(storeRevision, transition.state.preferences)) {
            is SourceSelectionSaveResult.Saved -> {
                acceptPersisted(transition.state, saved.state)
                SourceSelectionCommandResult.Success(selectionState.selectionRevision)
            }
            is SourceSelectionSaveResult.Conflict -> {
                selectionState = selectionState.withFailure(SourceSelectionFailure.PERSISTENCE_CONFLICT)
                publish()
                SourceSelectionCommandResult.Rejected(SourceSelectionFailure.PERSISTENCE_CONFLICT)
            }
            is SourceSelectionSaveResult.Failed -> {
                selectionState = selectionState.withFailure(SourceSelectionFailure.PERSISTENCE_FAILED)
                publish()
                SourceSelectionCommandResult.Rejected(SourceSelectionFailure.PERSISTENCE_FAILED)
            }
        }
    }

    private fun acceptPersisted(
        proposed: SourceSelectionState,
        persisted: PersistedSourceSelections,
    ) {
        storeRevision = persisted.revision
        selectionState = proposed.withPersistedRevision(persisted.revision)
        publish()
    }

    private fun publish() {
        mutableState.value = selectionState.snapshot
    }
}
