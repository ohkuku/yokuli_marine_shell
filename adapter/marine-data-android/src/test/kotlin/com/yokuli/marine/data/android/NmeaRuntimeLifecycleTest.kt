package com.yokuli.marine.data.android

import com.yokuli.marine.data.android.runtime.ReconnectDelayPort
import com.yokuli.marine.data.android.transport.NetworkTransportEvent
import com.yokuli.marine.data.android.transport.NmeaTransportFactory
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.runtime.SessionToken
import com.yokuli.marine.data.runtime.ConnectionTransportState
import com.yokuli.marine.data.runtime.TransportFailureKind
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NmeaRuntimeLifecycleTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun repeatedStartIsIdempotent() = runBlocking {
        val transports = ControllableTransportFactory()
        RuntimeFixture(storage(), transportFactory = transports).use { fixture ->
            val config = tcpConfig(id = "idempotent", port = 10_111)
            fixture.start(config)
            transports.awaitOpenCount(1)

            fixture.runtime.execute(NmeaRuntimeCommand.Start(config.id))
            fixture.runtime.execute(NmeaRuntimeCommand.Start(config.id))
            repeat(20) { yield() }

            assertEquals(1, transports.openCount.get())
            assertEquals(1, transports.maxConcurrentSessions.get())
        }
    }

    @Test
    fun stopCancelsPendingReconnect() = runBlocking {
        val transports = ControllableTransportFactory()
        val retry = GateReconnectDelay()
        RuntimeFixture(storage(), transportFactory = transports, reconnectDelay = retry).use { fixture ->
            val config = tcpConfig(id = "cancel-retry", port = 10_111)
            fixture.start(config)
            val session = transports.awaitSession(0)
            session.emit(
                NetworkTransportEvent.Failed(
                    NmeaRuntimeFailure.TransportFailed(TransportFailureKind.IO),
                    recoverable = true,
                ),
            )
            session.finish()
            retry.requested.await()

            fixture.stop(config.id)
            retry.release.complete(Unit)
            repeat(20) { yield() }

            assertEquals(1, transports.openCount.get())
            val stopped = fixture.runtime.snapshots.value.connections.getValue(config.id)
            assertTrue(stopped.transport is ConnectionTransportState.Stopped)
        }
    }

    @Test
    fun staleSessionCallbackCannotMutateReplacement() = runBlocking {
        val transports = ControllableTransportFactory()
        RuntimeFixture(storage(), transportFactory = transports).use { fixture ->
            val original = tcpConfig(id = "replace", port = 10_111)
            fixture.start(original)
            val oldSession = transports.awaitSession(0)
            oldSession.emit(NetworkTransportEvent.TcpConnected)
            val originalRevision = fixture.runtime.snapshots.value.connections
                .getValue(original.id)
                .stored
                .revision

            val replacement = original.copy(
                endpoint = com.yokuli.marine.data.connection.NmeaEndpoint.TcpClient(
                    host = "127.0.0.1",
                    port = 10_112,
                ),
            )
            fixture.runtime.execute(
                NmeaRuntimeCommand.SaveAndStart(
                    replacement,
                    expectedRevision = originalRevision,
                ),
            )
            val newSession = transports.awaitSession(1)
            newSession.emit(NetworkTransportEvent.TcpConnected)
            val before = fixture.runtime.snapshots.value.connections.getValue(original.id)
            assertNotEquals(oldSession.token, newSession.token)

            oldSession.emit(
                NetworkTransportEvent.TcpBytes(
                    nmea("WIMWV,045.0,R,10.5,N,A").toByteArray(Charsets.US_ASCII),
                ),
            )
            repeat(20) { yield() }

            val rejected = fixture.runtime.snapshots.value.connections.getValue(original.id)
            assertEquals(before.metrics.legalFrameCount, rejected.metrics.legalFrameCount)
            assertEquals(before.metrics.byteCount, rejected.metrics.byteCount)
            assertEquals(before.metrics.staleSessionDropCount + 1L, rejected.metrics.staleSessionDropCount)

            newSession.emit(
                NetworkTransportEvent.TcpBytes(
                    nmea("WIMWV,045.0,R,10.5,N,A").toByteArray(Charsets.US_ASCII),
                ),
            )
            fixture.await {
                it.connections[original.id]?.metrics?.legalFrameCount ==
                    before.metrics.legalFrameCount + 1L
            }
        }
    }

    @Test
    fun networkRestoreDoesNotDuplicateSocket() = runBlocking {
        val network = MutableNetworkAvailability(false)
        val transports = ControllableTransportFactory()
        RuntimeFixture(storage(), network, transports).use { fixture ->
            val config = tcpConfig(id = "network-restore", port = 10_111)
            fixture.start(config)

            val waiting = fixture.await {
                it.connections[config.id]?.transport is ConnectionTransportState.WaitingForNetwork
            }.connections.getValue(config.id)
            assertTrue(waiting.transport is ConnectionTransportState.WaitingForNetwork)
            assertEquals(0, transports.openCount.get())

            network.setAvailable(true)
            transports.awaitOpenCount(1)
            network.setAvailable(true)
            repeat(20) { yield() }

            assertEquals(1, transports.openCount.get())
            assertEquals(1, transports.maxConcurrentSessions.get())
        }
    }

    private fun storage(): File = File(temporaryFolder.newFolder(), "connections.pb")
}

private class ControllableTransportFactory : NmeaTransportFactory {
    val openCount = AtomicInteger(0)
    val maxConcurrentSessions = AtomicInteger(0)
    private val activeSessions = AtomicInteger(0)
    private val sessions = CopyOnWriteArrayList<ControlledSession>()

    override suspend fun run(
        config: NmeaConnectionConfig,
        sessionToken: SessionToken,
        emit: suspend (NetworkTransportEvent) -> Unit,
    ) {
        openCount.incrementAndGet()
        val active = activeSessions.incrementAndGet()
        maxConcurrentSessions.getAndUpdate { current -> maxOf(current, active) }
        val session = ControlledSession(sessionToken, emit)
        sessions += session
        try {
            session.finished.await()
        } finally {
            activeSessions.decrementAndGet()
        }
    }

    suspend fun awaitOpenCount(expected: Int) = awaitCondition { openCount.get() == expected }

    suspend fun awaitSession(index: Int): ControlledSession {
        awaitCondition { sessions.size > index }
        return sessions[index]
    }
}

private class ControlledSession(
    val token: SessionToken,
    private val emitter: suspend (NetworkTransportEvent) -> Unit,
) {
    val finished = CompletableDeferred<Unit>()

    suspend fun emit(event: NetworkTransportEvent) {
        emitter(event)
    }

    fun finish() {
        finished.complete(Unit)
    }
}

private class GateReconnectDelay : ReconnectDelayPort {
    val requested = CompletableDeferred<Long>()
    val release = CompletableDeferred<Unit>()

    override suspend fun await(delayMillis: Long) {
        requested.complete(delayMillis)
        try {
            release.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }
}
