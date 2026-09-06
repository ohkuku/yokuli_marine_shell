package com.yokuli.marine.shell

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.data.android.persistence.ProtoDataStoreConnectionRepository
import com.yokuli.marine.data.android.runtime.AndroidElapsedRealtimeClock
import com.yokuli.marine.data.android.runtime.AndroidNmeaInputRuntime
import com.yokuli.marine.data.android.runtime.NetworkAvailabilityPort
import com.yokuli.marine.data.android.runtime.ReconnectDelayPort
import com.yokuli.marine.data.android.runtime.SocketNmeaTransportFactory
import com.yokuli.marine.data.android.service.ForegroundRuntimeController
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.marine.data.nmea.NmeaChecksum
import com.yokuli.marine.data.runtime.ConnectionInputState
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.feature.nmeainput.NmeaInputCoordinator
import com.yokuli.marine.feature.nmeainput.NmeaInputTestTags
import com.yokuli.marine.feature.nmeainput.NmeaInputWorkspace
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** P2 real-loopback stories without prematurely installing the feature in the production catalog. */
class NmeaInputWorkspaceStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Test
    fun savingAndStartingUsesRealLoopback() = runBlocking {
        LoopbackNmeaServer().use { server ->
            NmeaStoryEnvironment().use { environment ->
                showWorkspace(environment.coordinator)
                compose.onNodeWithTag(NmeaInputTestTags.ADD).performClick()
                compose.onNodeWithTag(NmeaInputTestTags.NAME).performTextInput("Bridge gateway")
                compose.onNodeWithTag(NmeaInputTestTags.TCP_HOST).performTextInput("127.0.0.1")
                compose.onNodeWithTag(NmeaInputTestTags.PORT).performTextClearance()
                compose.onNodeWithTag(NmeaInputTestTags.PORT).performTextInput(server.port.toString())
                compose.onNodeWithTag(NmeaInputTestTags.SAVE_AND_ENABLE).performClick()

                server.send(validWindFrame())
                val receiving = environment.awaitRuntime { snapshot ->
                    snapshot.connections.singleOrNull()?.input == ConnectionInputState.RECEIVING_VALID_FRAMES
                }

                assertEquals(1L, receiving.connections.single().metrics.legalFrameCount)
                assertEquals(0L, receiving.connections.single().metrics.positionFrameCount)
                compose.waitUntil(5_000L) {
                    runCatching {
                        compose.onNodeWithTag(NmeaInputTestTags.DETAIL).assertIsDisplayed()
                    }.isSuccess
                }
            }
        }
        Unit
    }

    @Test
    fun leavingWorkspaceDoesNotStopRuntime() = runBlocking {
        LoopbackNmeaServer().use { server ->
            NmeaStoryEnvironment().use { environment ->
                val config = NmeaConnectionConfig(
                    id = ConnectionId("leave-workspace"),
                    displayName = "Bridge gateway",
                    endpoint = NmeaEndpoint.TcpClient("127.0.0.1", server.port),
                    checksumPolicy = ChecksumPolicy.STRICT,
                )
                environment.runtime.execute(NmeaRuntimeCommand.SaveAndStart(config))
                server.send(validWindFrame())
                environment.awaitRuntime { it.connections.singleOrNull()?.metrics?.legalFrameCount == 1L }

                showWorkspace(environment.coordinator)
                compose.onNodeWithTag(NmeaInputTestTags.ROOT).assertIsDisplayed()
                compose.activityRule.scenario.onActivity { activity ->
                    activity.setContent { YokuliTheme(WpThemeSpec()) { Box(androidx.compose.ui.Modifier) } }
                }
                environment.closeFeature()

                server.send(validWindFrame())
                val afterLeaving = environment.awaitRuntime {
                    it.connections.singleOrNull()?.metrics?.legalFrameCount == 2L
                }
                assertEquals(1, afterLeaving.activeSocketCount)
                assertTrue(afterLeaving.connections.single().token != null)
            }
        }
        Unit
    }

    private fun showWorkspace(coordinator: NmeaInputCoordinator) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val state by coordinator.state.collectAsState()
                YokuliTheme(WpThemeSpec()) {
                    NmeaInputWorkspace(state, coordinator::dispatch)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun validWindFrame(): String =
        NmeaChecksum.append("WIMWV,045.0,R,10.5,N,A") + "\r\n"
}

private class NmeaStoryEnvironment : Closeable {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val storage = File(context.cacheDir, "nmea-story-${UUID.randomUUID()}.pb")
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val featureScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = ProtoDataStoreConnectionRepository.create(storage, runtimeScope)
    val runtime: NmeaInputRuntimePort = AndroidNmeaInputRuntime(
        repository = repository,
        transportFactory = SocketNmeaTransportFactory(),
        networkAvailability = AlwaysAvailableNetwork,
        clock = AndroidElapsedRealtimeClock,
        reconnectDelay = ReconnectDelayPort.SYSTEM,
        applicationScope = runtimeScope,
        foregroundController = ForegroundRuntimeController.NO_OP,
    )
    val coordinator = NmeaInputCoordinator(runtime, featureScope)

    suspend fun awaitRuntime(predicate: (NmeaRuntimeSnapshot) -> Boolean): NmeaRuntimeSnapshot =
        withTimeout(5_000L) { runtime.state.first(predicate) }

    fun closeFeature() {
        featureScope.cancel()
    }

    override fun close() {
        runBlocking {
            runtime.state.value.connections.forEach {
                runtime.execute(NmeaRuntimeCommand.Stop(it.stored.config.id))
            }
        }
        featureScope.cancel()
        runtimeScope.cancel()
        storage.delete()
    }
}

private object AlwaysAvailableNetwork : NetworkAvailabilityPort {
    override val available: StateFlow<Boolean> = MutableStateFlow(true)
}

private class LoopbackNmeaServer : Closeable {
    private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val accepted = CompletableDeferred<Socket>()
    @Volatile private var client: Socket? = null
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
