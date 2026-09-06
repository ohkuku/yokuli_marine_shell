package com.yokuli.marine.data.android

import com.yokuli.marine.data.android.persistence.ConnectionPersistenceResult
import com.yokuli.marine.data.android.persistence.NmeaConnectionProtoMapper
import com.yokuli.marine.data.android.persistence.ProtoDataStoreConnectionRepository
import com.yokuli.marine.data.android.proto.PersistedNmeaConnection
import com.yokuli.marine.data.android.proto.PersistedNmeaConnectionStore
import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.connection.StoredNmeaConnection
import com.yokuli.marine.data.model.ConnectionId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConnectionPersistenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun configPersistsStoppedIntentWithoutRevivingRuntimeTruth() = runBlocking {
        val file = File(temporaryFolder.newFolder("restart"), "nmea-connections.pb")
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + Dispatchers.IO)
        val first = ProtoDataStoreConnectionRepository.create(file, firstScope)
        first.awaitLoaded()
        val config = tcpConfig(id = "saved-stopped", port = 10_111)

        assertTrue(
            first.upsert(
                StoredNmeaConnection(
                    config = config,
                    runIntent = ConnectionRunIntent.STOPPED_BY_USER,
                    revision = 0L,
                ),
            ) is ConnectionPersistenceResult.Saved,
        )
        firstJob.cancelAndJoin()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val restored = ProtoDataStoreConnectionRepository.create(file, secondScope)
            restored.awaitLoaded()
            val record = restored.snapshot.value.connections.single {
                it.config.id == ConnectionId("saved-stopped")
            }

            assertEquals(config, record.config)
            assertTrue(record.runIntent.isStopped())
            assertEquals(0L, record.revision)
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun repositoryBoundRejectsNewConnectionWithoutDroppingExistingRecords() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = ProtoDataStoreConnectionRepository.create(
                storageFile = File(temporaryFolder.newFolder("bounded"), "nmea-connections.pb"),
                scope = scope,
                maxConnections = 2,
            )
            repository.awaitLoaded()

            assertTrue(
                repository.upsert(
                    StoredNmeaConnection(
                        tcpConfig("one", port = 10_111),
                        ConnectionRunIntent.STOPPED_BY_USER,
                        0L,
                    ),
                ) is
                    ConnectionPersistenceResult.Saved,
            )
            assertTrue(
                repository.upsert(
                    StoredNmeaConnection(
                        tcpConfig("two", port = 10_112),
                        ConnectionRunIntent.STOPPED_BY_USER,
                        0L,
                    ),
                ) is
                    ConnectionPersistenceResult.Saved,
            )
            val rejected = repository.upsert(
                StoredNmeaConnection(
                    tcpConfig("three", port = 10_113),
                    ConnectionRunIntent.STOPPED_BY_USER,
                    0L,
                ),
            )

            assertTrue(rejected is ConnectionPersistenceResult.CapacityExceeded)
            assertEquals(
                setOf(ConnectionId("one"), ConnectionId("two")),
                repository.snapshot.value.connections.map { it.config.id }.toSet(),
            )
            assertEquals(1L, repository.snapshot.value.capacityRejectionCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun semanticCorruptionIsQuarantinedWithoutDroppingValidConnections() = runBlocking {
        val file = File(temporaryFolder.newFolder("semantic-corruption"), "nmea-connections.pb")
        val valid = StoredNmeaConnection(
            tcpConfig("valid", port = 10_111),
            ConnectionRunIntent.STOPPED_BY_USER,
            0L,
        )
        PersistedNmeaConnectionStore.newBuilder()
            .setSchemaVersion(1)
            .addConnections(NmeaConnectionProtoMapper.encode(valid))
            .addConnections(
                PersistedNmeaConnection.newBuilder()
                    .setConnectionId("invalid-without-endpoint")
                    .setDisplayName("Invalid")
                    .build(),
            )
            .build()
            .writeTo(file.outputStream())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = ProtoDataStoreConnectionRepository.create(file, scope)
            repository.awaitLoaded()

            assertEquals(listOf(valid), repository.snapshot.value.connections)
            assertEquals(1, repository.snapshot.value.quarantinedRecordCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun wireCorruptionIsReportedWithoutReplacingTheOriginalFile() = runBlocking {
        val file = File(temporaryFolder.newFolder("wire-corruption"), "nmea-connections.pb")
        val corruptBytes = byteArrayOf(0x0A, 0x7F, 0x01, 0x02, 0x03)
        file.writeBytes(corruptBytes)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = ProtoDataStoreConnectionRepository.create(file, scope)
            runCatching { repository.awaitLoaded() }

            assertNotNull(repository.snapshot.value.loadFailure)
            assertArrayEquals(corruptBytes, file.readBytes())
        } finally {
            scope.cancel()
        }
    }
}
