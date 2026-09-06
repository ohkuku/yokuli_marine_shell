package com.yokuli.marine.data.source

import com.yokuli.marine.data.model.DataKey

const val SOURCE_SELECTION_SCHEMA_VERSION: Int = 1

data class PersistedSourceSelections(
    val schemaVersion: Int = SOURCE_SELECTION_SCHEMA_VERSION,
    val revision: Long = 0L,
    val preferences: Map<DataKey, SourcePreference> = emptyMap(),
) {
    init {
        require(schemaVersion > 0)
        require(revision >= 0L)
    }
}

sealed interface SourceSelectionLoadResult {
    data class Loaded(
        val state: PersistedSourceSelections,
        val quarantinedRecordCount: Int = 0,
    ) : SourceSelectionLoadResult {
        init {
            require(quarantinedRecordCount >= 0)
        }
    }

    data class Failed(val cause: Throwable) : SourceSelectionLoadResult
}

sealed interface SourceSelectionSaveResult {
    data class Saved(val state: PersistedSourceSelections) : SourceSelectionSaveResult
    data class Conflict(val current: PersistedSourceSelections) : SourceSelectionSaveResult
    data class Failed(val cause: Throwable) : SourceSelectionSaveResult
}

interface SourceSelectionRepository {
    suspend fun load(): SourceSelectionLoadResult

    suspend fun save(
        expectedRevision: Long,
        preferences: Map<DataKey, SourcePreference>,
    ): SourceSelectionSaveResult
}
