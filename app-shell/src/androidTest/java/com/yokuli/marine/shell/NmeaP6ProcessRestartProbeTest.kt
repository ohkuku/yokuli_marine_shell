package com.yokuli.marine.shell

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.marine.data.nmea.NmeaChecksum
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeCommandResult
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.data.source.SourceDecisionStatus
import com.yokuli.marine.data.source.SourceSelectionCommand
import com.yokuli.marine.data.source.SourceSelectionCommandResult
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Each method is launched by an external driver in a different app process. */
@RunWith(AndroidJUnit4::class)
class NmeaP6ProcessRestartProbeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Test
    fun seedNmeaStateBeforeExternalProcessRestart() = runBlocking {
        OneShotNmeaServer().use { server ->
            val application = application()
            val config = NmeaConnectionConfig(
                id = PROBE_CONNECTION,
                displayName = "P6 process probe",
                endpoint = NmeaEndpoint.TcpClient("127.0.0.1", server.port),
                checksumPolicy = ChecksumPolicy.STRICT,
            )
            val result = application.nmeaInputRuntime.execute(NmeaRuntimeCommand.SaveAndStart(config))
            assertTrue(result is NmeaRuntimeCommandResult.Success)

            server.send(validPositionFrame())
            val receiving = withTimeout(10_000L) {
                application.nmeaInputRuntime.state.first { snapshot ->
                    snapshot.connections.singleOrNull { it.stored.config.id == PROBE_CONNECTION }
                        ?.metrics?.positionFrameCount == 1L
                }
            }
            assertEquals(1L, receiving.connections.single().metrics.legalFrameCount)

            val positionSource = withTimeout(10_000L) {
                application.marineSourceRuntime.state.first { snapshot ->
                    snapshot.sourceCatalog.candidates.any { it.id.key == DataKey.Position }
                }.sourceCatalog.candidates.single { it.id.key == DataKey.Position }.id.source
            }
            val selection = application.marineSourceRuntime.execute(
                SourceSelectionCommand.Select(DataKey.Position, positionSource),
            )
            assertTrue(selection is SourceSelectionCommandResult.Success)
            val selected = withTimeout(10_000L) {
                application.marineSourceRuntime.state.first { snapshot ->
                    snapshot.resolvedData.items[DataKey.Position]?.value is MarineValue.Position
                }
            }
            assertEquals(
                SourceIdentity(PROBE_CONNECTION),
                selected.resolvedData.items.getValue(DataKey.Position).source,
            )
        }
        Unit
    }

    @Test
    fun verifyPolicySurvivesButLiveValuesDoNot() = runBlocking {
        val application = application()
        val restored = withTimeout(10_000L) {
            application.nmeaInputRuntime.state.first { snapshot ->
                snapshot.connections.any { it.stored.config.id == PROBE_CONNECTION }
            }
        }
        val connection = restored.connections.single { it.stored.config.id == PROBE_CONNECTION }
        assertEquals(ConnectionRunIntent.ENABLED, connection.stored.runIntent)
        assertEquals(0L, connection.metrics.byteCount)
        assertEquals(0L, connection.metrics.legalFrameCount)
        assertTrue(restored.observationCatalog.candidates.none { it.id.source == SourceIdentity(PROBE_CONNECTION) })

        val sourceState = withTimeout(10_000L) {
            application.marineSourceRuntime.state.first { snapshot ->
                snapshot.decisions.any {
                    it.key == DataKey.Position && it.selectedSource == SourceIdentity(PROBE_CONNECTION)
                }
            }
        }
        val decision = sourceState.decisions.single { it.key == DataKey.Position }
        assertTrue(
            decision.status in setOf(
                SourceDecisionStatus.SELECTED_MISSING,
                SourceDecisionStatus.SELECTED_UNAVAILABLE,
            ),
        )
        val position = sourceState.resolvedData.items.getValue(DataKey.Position)
        assertNull(position.value)
        assertTrue(position.availability in setOf(SourceCandidateAvailability.MISSING, SourceCandidateAvailability.UNAVAILABLE))
    }

    private fun application(): ShellApplication {
        lateinit var value: ShellApplication
        compose.activityRule.scenario.onActivity { value = it.application as ShellApplication }
        return value
    }

    private fun validPositionFrame(): String = NmeaChecksum.append(
        "GPRMC,123519,A,4807.038,N,01131.000,E,6.5,140.0,230394,,,A",
    ) + "\r\n"

    private companion object {
        val PROBE_CONNECTION = ConnectionId("p6-process-probe")
    }
}

private class OneShotNmeaServer : Closeable {
    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val accepted = CompletableDeferred<Socket>()
    private var client: Socket? = null
    val port: Int get() = server.localPort

    init {
        scope.launch {
            val socket = server.accept()
            client = socket
            accepted.complete(socket)
        }
    }

    suspend fun send(frame: String) {
        val socket = withTimeout(5_000L) { accepted.await() }
        withContext(Dispatchers.IO) {
            socket.getOutputStream().apply {
                write(frame.toByteArray(Charsets.US_ASCII))
                flush()
            }
        }
    }

    override fun close() {
        client?.close()
        server.close()
        scope.cancel()
    }
}
