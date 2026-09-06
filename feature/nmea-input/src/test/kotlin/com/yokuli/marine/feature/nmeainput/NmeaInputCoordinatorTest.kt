package com.yokuli.marine.feature.nmeainput

import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.connection.StoredNmeaConnection
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.marine.data.runtime.ConnectionInputState
import com.yokuli.marine.data.runtime.ConnectionRuntimeSnapshot
import com.yokuli.marine.data.runtime.ConnectionTransportState
import com.yokuli.marine.data.runtime.NmeaConnectionDiagnostics
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeCommandResult
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.runtime.SessionToken
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Red contract for the serialized Feature coordinator; the process runtime remains core-owned. */
class NmeaInputCoordinatorTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun closeScope() = scope.cancel()

    @Test
    fun saveAndSaveEnabledAreSeparateUserIntents() = runBlocking {
        val saveOnlyPort = FakeRuntimePort()
        val saveOnly = NmeaInputCoordinator(saveOnlyPort, scope)
        enterValidTcpDraft(saveOnly, "saved-only")

        saveOnly.dispatch(NmeaInputUiAction.Save)
        awaitCommands(saveOnlyPort, 1)

        val saved = saveOnlyPort.commands.single() as NmeaRuntimeCommand.Save
        assertEquals("saved-only", saved.config.displayName)

        val enabledPort = FakeRuntimePort()
        val enabled = NmeaInputCoordinator(enabledPort, scope)
        enterValidTcpDraft(enabled, "saved-and-started")

        enabled.dispatch(NmeaInputUiAction.SaveAndEnable)
        awaitCommands(enabledPort, 1)

        val started = enabledPort.commands.single() as NmeaRuntimeCommand.SaveAndStart
        assertEquals("saved-and-started", started.config.displayName)
    }

    @Test
    fun runningEndpointReplacementRequiresConfirmationAndExpectedRevision() = runBlocking {
        val id = ConnectionId("running")
        val token = SessionToken(
            connectionId = id,
            configRevision = 14L,
            generation = SessionGeneration(9),
        )
        val existing = NmeaConnectionConfig(
            id = id,
            displayName = "Boat gateway",
            endpoint = NmeaEndpoint.TcpClient("old.local", 10_111),
            checksumPolicy = ChecksumPolicy.STRICT,
        )
        val port = FakeRuntimePort(snapshotOf(existing, token, revision = 14L))
        val coordinator = NmeaInputCoordinator(port, scope)

        coordinator.dispatch(NmeaInputUiAction.OpenConnection(id))
        coordinator.dispatch(NmeaInputUiAction.EditConnection(id))
        coordinator.dispatch(NmeaInputUiAction.ChangeTcpHost("new.local"))
        coordinator.dispatch(NmeaInputUiAction.SaveAndEnable)

        assertTrue(port.commands.isEmpty())
        assertTrue(coordinator.state.value.page is NmeaInputPageUi.ReplacementConfirmation)

        coordinator.dispatch(NmeaInputUiAction.ConfirmRunningReplacement)
        awaitCommands(port, 1)

        val replace = port.commands.single() as NmeaRuntimeCommand.SaveAndStart
        assertEquals(id, replace.config.id)
        assertEquals(NmeaEndpoint.TcpClient("new.local", 10_111), replace.config.endpoint)
        assertEquals(14L, replace.expectedRevision)
    }

    @Test
    fun typedPersistenceFailureDoesNotBecomeSuccessCopy() = runBlocking {
        val port = FakeRuntimePort(
            result = NmeaRuntimeCommandResult.Rejected(NmeaRuntimeFailure.PersistenceFailed),
        )
        val coordinator = NmeaInputCoordinator(port, scope)
        enterValidTcpDraft(coordinator, "failure")

        coordinator.dispatch(NmeaInputUiAction.SaveAndEnable)
        awaitCommands(port, 1)
        withTimeout(1_000L) {
            while (coordinator.state.value.notice == null) yield()
        }

        assertEquals(
            NmeaInputNoticeUi.OperationFailed(NmeaInputFailureUi.PersistenceFailed),
            coordinator.state.value.notice,
        )
        assertTrue(coordinator.state.value.page is NmeaInputPageUi.Editor)
    }

    @Test
    fun invalidTcpAndUdpDraftsNeverReachRuntime() = runBlocking {
        val port = FakeRuntimePort()
        val coordinator = NmeaInputCoordinator(port, scope)

        coordinator.dispatch(NmeaInputUiAction.AddConnection)
        coordinator.dispatch(NmeaInputUiAction.ChangeName(" "))
        coordinator.dispatch(NmeaInputUiAction.ChangeTcpHost(""))
        coordinator.dispatch(NmeaInputUiAction.ChangePort("+10111"))
        coordinator.dispatch(NmeaInputUiAction.Save)

        assertTrue(port.commands.isEmpty())
        val tcpDraft = (coordinator.state.value.page as NmeaInputPageUi.Editor).draft
        assertEquals(
            setOf(NmeaInputField.NAME, NmeaInputField.TCP_HOST, NmeaInputField.PORT),
            tcpDraft.errors.keys,
        )

        coordinator.dispatch(NmeaInputUiAction.ChangeTransport(NmeaInputTransportKind.UDP_LISTENER))
        coordinator.dispatch(NmeaInputUiAction.ChangeName("UDP"))
        coordinator.dispatch(NmeaInputUiAction.ChangePort("65536"))
        coordinator.dispatch(NmeaInputUiAction.SaveAndEnable)

        assertTrue(port.commands.isEmpty())
        val udpDraft = (coordinator.state.value.page as NmeaInputPageUi.Editor).draft
        assertEquals(setOf(NmeaInputField.PORT), udpDraft.errors.keys)
    }

    @Test
    fun backDetailAndEditorReturnOverviewWhileOverviewIsNotConsumed() {
        val id = ConnectionId("back")
        val port = FakeRuntimePort(snapshotOf(config(id), null))
        val coordinator = NmeaInputCoordinator(port, scope)

        coordinator.dispatch(NmeaInputUiAction.OpenConnection(id))
        assertTrue(coordinator.state.value.page is NmeaInputPageUi.Detail)
        assertTrue(coordinator.handleBack())
        assertTrue(coordinator.state.value.page is NmeaInputPageUi.Overview)
        assertFalse(coordinator.handleBack())

        coordinator.dispatch(NmeaInputUiAction.AddConnection)
        assertTrue(coordinator.state.value.page is NmeaInputPageUi.Editor)
        assertTrue(coordinator.handleBack())
        assertTrue(coordinator.state.value.page is NmeaInputPageUi.Overview)
        assertFalse(coordinator.handleBack())
    }

    @Test
    fun runtimeSnapshotIsTheOnlyReceivingTruth() {
        val port = FakeRuntimePort()
        val coordinator = NmeaInputCoordinator(port, scope)
        enterValidTcpDraft(coordinator, "not-runtime-yet")

        coordinator.dispatch(NmeaInputUiAction.SaveAndEnable)

        assertEquals(0, coordinator.state.value.summary.receivingCount)
        assertTrue(coordinator.state.value.page is NmeaInputPageUi.Editor)
    }

    private fun enterValidTcpDraft(coordinator: NmeaInputCoordinator, name: String) {
        coordinator.dispatch(NmeaInputUiAction.AddConnection)
        coordinator.dispatch(NmeaInputUiAction.ChangeName(name))
        coordinator.dispatch(NmeaInputUiAction.ChangeTcpHost("127.0.0.1"))
        coordinator.dispatch(NmeaInputUiAction.ChangePort("10111"))
    }

    private suspend fun awaitCommands(port: FakeRuntimePort, count: Int) {
        withTimeout(1_000L) {
            while (port.commands.size < count) yield()
        }
    }

    private fun config(id: ConnectionId) = NmeaConnectionConfig(
        id,
        "Gateway",
        NmeaEndpoint.TcpClient("127.0.0.1", 10_111),
        ChecksumPolicy.STRICT,
    )

    private fun snapshotOf(
        config: NmeaConnectionConfig,
        token: SessionToken?,
        revision: Long = 1L,
    ): NmeaRuntimeSnapshot {
        val transport = if (token != null) ConnectionTransportState.TcpConnected else ConnectionTransportState.Stopped
        return NmeaRuntimeSnapshot.EMPTY.copy(
            revision = 1L,
            connections = listOf(
                ConnectionRuntimeSnapshot(
                    stored = StoredNmeaConnection(config, ConnectionRunIntent.ENABLED, revision),
                    token = token,
                    transport = transport,
                    input = ConnectionInputState.NO_BYTES,
                    metrics = NmeaConnectionDiagnostics.EMPTY,
                ),
            ),
        )
    }
}

private class FakeRuntimePort(
    initial: NmeaRuntimeSnapshot = NmeaRuntimeSnapshot.EMPTY,
    private val result: NmeaRuntimeCommandResult = NmeaRuntimeCommandResult.Success(
        connectionId = null,
        revision = 0L,
    ),
) : NmeaInputRuntimePort {
    private val mutableSnapshots = MutableStateFlow(initial)
    override val state: StateFlow<NmeaRuntimeSnapshot> = mutableSnapshots
    val commands = CopyOnWriteArrayList<NmeaRuntimeCommand>()

    override suspend fun execute(command: NmeaRuntimeCommand): NmeaRuntimeCommandResult {
        commands += command
        return result
    }
}
