package com.yokuli.marine.feature.data

import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaConnectionProbePort
import com.yokuli.marine.data.connection.NmeaConnectionProbeResult
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeCommandResult
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineSourceRuntimePort
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.SourceSelectionCommand
import com.yokuli.marine.data.source.SourceSelectionCommandResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataRecoveryCoordinatorTest {
    @Test
    fun probeDetectsCapabilitiesWithoutPersistingUntilUserConfirms() = runTest(UnconfinedTestDispatcher()) {
        val runtime = RecordingNmeaPort()
        val probe = RecordingProbe(
            NmeaConnectionProbeResult.Detected(setOf(DataKey.Position), legalFrameCount = 1L),
        )
        val coordinator = DataCoordinator(
            sourcePort = RecoverySourcePort(),
            nmeaPort = runtime,
            phoneDemandPort = RecoveryPhoneDemandPort(),
            scope = backgroundScope,
            newConnectionId = { ConnectionId("wizard-gateway") },
            connectionProbe = probe,
        )

        coordinator.dispatch(DataUiAction.OpenAddSource)
        coordinator.dispatch(DataUiAction.ChooseConnectionType(DataConnectionType.BOAT_GATEWAY))
        coordinator.dispatch(DataUiAction.ChangeConnectionHost("192.168.4.1"))
        coordinator.dispatch(DataUiAction.ChangeConnectionPort("10110"))
        coordinator.dispatch(DataUiAction.TestConnection)
        advanceUntilIdle()

        assertTrue(runtime.commands.isEmpty())
        assertEquals("Boat Gateway", probe.configs.single().displayName)
        assertEquals(ConnectionId("wizard-gateway"), probe.configs.single().id)
        assertEquals(ConnectionTestPhase.DETECTED, coordinator.state.value.connectionTest.phase)
        assertEquals(setOf(BoatSensor.POSITION), coordinator.state.value.connectionTest.detectedSensors)
        assertTrue(coordinator.state.value.surface is DataSurface.ConnectionWizard)

        coordinator.dispatch(DataUiAction.UseTestedConnection)
        advanceUntilIdle()

        val command = runtime.commands.single() as NmeaRuntimeCommand.SaveAndStart
        assertEquals("Boat Gateway", command.config.displayName)
        assertEquals(ConnectionId("wizard-gateway"), command.config.id)
        assertEquals(DataSurface.Primary(PrimaryDataArea.CONNECTIONS), coordinator.state.value.surface)
    }

    @Test
    fun transportWithoutSemanticDataNeverPollutesDurableConnections() = runTest(UnconfinedTestDispatcher()) {
        val runtime = RecordingNmeaPort()
        val coordinator = DataCoordinator(
            sourcePort = RecoverySourcePort(),
            nmeaPort = runtime,
            phoneDemandPort = RecoveryPhoneDemandPort(),
            scope = backgroundScope,
            newConnectionId = { ConnectionId("transport-only") },
            connectionProbe = RecordingProbe(
                NmeaConnectionProbeResult.NoSemanticData(
                    transportReady = true,
                    legalFrameCount = 3L,
                ),
            ),
        )
        coordinator.dispatch(DataUiAction.OpenAddSource)
        coordinator.dispatch(DataUiAction.ChooseConnectionType(DataConnectionType.BOAT_GATEWAY))
        coordinator.dispatch(DataUiAction.ChangeConnectionHost("boat.local"))
        coordinator.dispatch(DataUiAction.TestConnection)
        advanceUntilIdle()

        assertEquals(ConnectionTestPhase.NO_SEMANTIC_DATA, coordinator.state.value.connectionTest.phase)
        assertTrue(coordinator.state.value.connectionTest.transportReady)
        assertEquals(3L, coordinator.state.value.connectionTest.legalFrameCount)
        assertTrue(runtime.commands.isEmpty())
    }

    @Test
    fun diagnosticsAndSensorTrustAreDrilldownsNotPrimaryNavigation() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = DataCoordinator(
            sourcePort = RecoverySourcePort(),
            nmeaPort = RecordingNmeaPort(),
            phoneDemandPort = RecoveryPhoneDemandPort(),
            scope = backgroundScope,
        )

        coordinator.dispatch(DataUiAction.OpenSensor(BoatSensor.POSITION))
        assertTrue(coordinator.state.value.surface is DataSurface.Sensor)
        assertTrue(coordinator.handleBack())
        assertEquals(DataSurface.Primary(PrimaryDataArea.BOAT), coordinator.state.value.surface)

        coordinator.dispatch(DataUiAction.OpenDiagnostics())
        assertTrue(coordinator.state.value.surface is DataSurface.Diagnostics)
        assertFalse(coordinator.state.value.surface is DataSurface.Primary)
    }

}

private class RecordingProbe(
    private val result: NmeaConnectionProbeResult,
) : NmeaConnectionProbePort {
    val configs = mutableListOf<NmeaConnectionConfig>()

    override suspend fun probe(
        config: NmeaConnectionConfig,
        timeoutMillis: Long,
    ): NmeaConnectionProbeResult {
        configs += config
        return result
    }
}

private class RecoverySourcePort : MarineSourceRuntimePort {
    val mutable = MutableStateFlow(MarineSourceSnapshot.EMPTY)
    override val state: StateFlow<MarineSourceSnapshot> = mutable
    override suspend fun execute(command: SourceSelectionCommand): SourceSelectionCommandResult =
        SourceSelectionCommandResult.Success(mutable.value.selectionRevision + 1L)
}

private class RecordingNmeaPort : NmeaInputRuntimePort {
    val mutable = MutableStateFlow(NmeaRuntimeSnapshot.EMPTY)
    override val state: StateFlow<NmeaRuntimeSnapshot> = mutable
    val commands = mutableListOf<NmeaRuntimeCommand>()
    override suspend fun execute(command: NmeaRuntimeCommand): NmeaRuntimeCommandResult {
        commands += command
        return NmeaRuntimeCommandResult.Success(
            connectionId = when (command) {
                is NmeaRuntimeCommand.Save -> command.config.id
                is NmeaRuntimeCommand.SaveAndStart -> command.config.id
                is NmeaRuntimeCommand.Start -> command.connectionId
                is NmeaRuntimeCommand.Retry -> command.connectionId
                is NmeaRuntimeCommand.Stop -> command.connectionId
                is NmeaRuntimeCommand.Delete -> command.connectionId
            },
            revision = 1L,
        )
    }
}

private class RecoveryPhoneDemandPort : DataPhoneDemandPort {
    private val mutable = MutableStateFlow(DataPhoneDemandState())
    override val state: StateFlow<DataPhoneDemandState> = mutable
    override suspend fun request(group: SourceGroup) = DataPhoneRequestResult.Accepted
    override suspend fun permissionResult(permanentlyDenied: Boolean) = DataPhoneRequestResult.Accepted
    override suspend fun refreshPlatformState() = DataPhoneRequestResult.Accepted
    override suspend fun cancelPending() = Unit
}
