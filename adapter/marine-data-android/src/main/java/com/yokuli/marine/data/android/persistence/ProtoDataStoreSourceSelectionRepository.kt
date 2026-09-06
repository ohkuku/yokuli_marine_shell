package com.yokuli.marine.data.android.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.yokuli.marine.data.android.proto.PersistedSourceSelectionStore
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.source.PersistedSourceSelections
import com.yokuli.marine.data.source.SourcePreference
import com.yokuli.marine.data.source.SourceSelectionLoadResult
import com.yokuli.marine.data.source.SourceSelectionRepository
import com.yokuli.marine.data.source.SourceSelectionSaveResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

/** Persists policy only. Catalog values, age and health never cross a process boundary. */
class ProtoDataStoreSourceSelectionRepository private constructor(
    private val dataStore: DataStore<PersistedSourceSelectionStore>,
) : SourceSelectionRepository {
    override suspend fun load(): SourceSelectionLoadResult = try {
        val decoded = SourceSelectionProtoMapper.decode(dataStore.data.first())
        SourceSelectionLoadResult.Loaded(decoded.state, decoded.quarantinedRecordCount)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        SourceSelectionLoadResult.Failed(error)
    }

    override suspend fun save(
        expectedRevision: Long,
        preferences: Map<DataKey, SourcePreference>,
    ): SourceSelectionSaveResult {
        require(expectedRevision >= 0L)
        var result: SourceSelectionSaveResult? = null
        return try {
            dataStore.updateData { currentProto ->
                val current = SourceSelectionProtoMapper.decode(currentProto).state
                if (current.revision != expectedRevision) {
                    result = SourceSelectionSaveResult.Conflict(current)
                    currentProto
                } else {
                    val updated = PersistedSourceSelections(
                        revision = increment(current.revision),
                        preferences = preferences,
                    )
                    result = SourceSelectionSaveResult.Saved(updated)
                    SourceSelectionProtoMapper.encode(updated)
                }
            }
            checkNotNull(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            SourceSelectionSaveResult.Failed(error)
        }
    }

    companion object {
        fun create(
            storageFile: File,
            scope: CoroutineScope,
        ): ProtoDataStoreSourceSelectionRepository = ProtoDataStoreSourceSelectionRepository(
            DataStoreFactory.create(
                serializer = SourceSelectionStateSerializer,
                scope = scope,
                produceFile = { storageFile },
            ),
        )
    }
}

private fun increment(value: Long): Long = if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L
