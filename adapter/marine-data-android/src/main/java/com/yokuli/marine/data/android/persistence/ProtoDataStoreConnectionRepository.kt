package com.yokuli.marine.data.android.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.yokuli.marine.data.android.proto.PersistedNmeaConnectionStore
import com.yokuli.marine.data.connection.MAX_NMEA_CONNECTIONS
import com.yokuli.marine.data.connection.StoredNmeaConnection
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class ConnectionPersistenceSnapshot(
    val connections: List<StoredNmeaConnection> = emptyList(),
    val quarantinedRecordCount: Int = 0,
    val capacityRejectionCount: Long = 0L,
    val loaded: Boolean = false,
    val loadFailure: Throwable? = null,
)

sealed interface ConnectionPersistenceResult {
    data class Saved(val connection: StoredNmeaConnection) : ConnectionPersistenceResult
    data class CapacityExceeded(val maximum: Int) : ConnectionPersistenceResult
    data class Failed(val cause: Throwable) : ConnectionPersistenceResult
}

/** Durable configuration and run intent only. Live transport/input facts are never serialized. */
class ProtoDataStoreConnectionRepository private constructor(
    private val dataStore: DataStore<PersistedNmeaConnectionStore>,
    scope: CoroutineScope,
    private val maxConnections: Int,
) {
    private val mutableSnapshot = MutableStateFlow(ConnectionPersistenceSnapshot())
    val snapshot: StateFlow<ConnectionPersistenceSnapshot> = mutableSnapshot.asStateFlow()
    private val initialLoad = CompletableDeferred<Unit>()
    private val capacityRejections = AtomicLong(0L)

    init {
        require(maxConnections > 0)
        scope.launch {
            try {
                dataStore.data.collect { persisted ->
                    publish(persisted)
                    initialLoad.complete(Unit)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableSnapshot.value = mutableSnapshot.value.copy(
                    loaded = false,
                    loadFailure = error,
                )
                initialLoad.completeExceptionally(error)
            }
        }
    }

    suspend fun awaitLoaded() {
        initialLoad.await()
    }

    suspend fun upsert(connection: StoredNmeaConnection): ConnectionPersistenceResult {
        awaitLoaded()
        var outcome: ConnectionPersistenceResult? = null
        return try {
            val updated = dataStore.updateData { current ->
                val existingIndex = current.connectionsList.indexOfFirst {
                    it.connectionId == connection.config.id.value
                }
                if (existingIndex < 0 && current.connectionsCount >= maxConnections) {
                    capacityRejections.incrementAndGet()
                    outcome = ConnectionPersistenceResult.CapacityExceeded(maxConnections)
                    current
                } else {
                    val encoded = NmeaConnectionProtoMapper.encode(connection)
                    current.toBuilder()
                        .setSchemaVersion(NmeaConnectionStateSerializer.CURRENT_SCHEMA_VERSION)
                        .apply {
                            if (existingIndex >= 0) setConnections(existingIndex, encoded)
                            else addConnections(encoded)
                        }
                        .build()
                        .also { outcome = ConnectionPersistenceResult.Saved(connection) }
                }
            }
            publish(updated)
            checkNotNull(outcome)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            ConnectionPersistenceResult.Failed(error)
        }
    }

    suspend fun delete(connectionId: com.yokuli.marine.data.model.ConnectionId): ConnectionPersistenceResult {
        awaitLoaded()
        return try {
            var removed: StoredNmeaConnection? = null
            val updated = dataStore.updateData { current ->
                val decoded = NmeaConnectionProtoMapper.decode(current).connections
                removed = decoded.singleOrNull { it.config.id == connectionId }
                current.toBuilder()
                    .clearConnections()
                    .addAllConnections(
                        current.connectionsList.filterNot { it.connectionId == connectionId.value },
                    )
                    .setSchemaVersion(NmeaConnectionStateSerializer.CURRENT_SCHEMA_VERSION)
                    .build()
            }
            publish(updated)
            removed?.let(ConnectionPersistenceResult::Saved)
                ?: ConnectionPersistenceResult.Failed(NoSuchElementException(connectionId.value))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            ConnectionPersistenceResult.Failed(error)
        }
    }

    private fun publish(persisted: PersistedNmeaConnectionStore) {
        val decoded = NmeaConnectionProtoMapper.decode(persisted)
        mutableSnapshot.value = ConnectionPersistenceSnapshot(
            connections = decoded.connections,
            quarantinedRecordCount = decoded.quarantinedRecordCount,
            capacityRejectionCount = capacityRejections.get(),
            loaded = true,
            loadFailure = null,
        )
    }

    companion object {
        fun create(
            storageFile: File,
            scope: CoroutineScope,
            maxConnections: Int = MAX_NMEA_CONNECTIONS,
        ): ProtoDataStoreConnectionRepository = ProtoDataStoreConnectionRepository(
            dataStore = DataStoreFactory.create(
                serializer = NmeaConnectionStateSerializer,
                scope = scope,
                produceFile = { storageFile },
            ),
            scope = scope,
            maxConnections = maxConnections,
        )
    }
}
