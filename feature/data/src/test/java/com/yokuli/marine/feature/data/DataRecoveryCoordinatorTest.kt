package com.yokuli.marine.feature.data

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeCommandResult
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineSourceRuntimePort
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.ResolvedDataSnapshot
import com.yokuli.marine.data.source.SourceCandidate
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.data.source.SourceCatalogSnapshot
import com.yokuli.marine.data.source.SourceDescriptor
import com.yokuli.marine.data.source.SourceEvidence
import com.yokuli.marine.data.source.SourceKind
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
    fun gatewayWizardAutoNamesAndTestsThroughTheProcessRuntimePort() = runTest(UnconfinedTestDispatcher()) {
        val runtime = RecordingNmeaPort()
        val coordinator = DataCoordinator(
            sourcePort = RecoverySourcePort(),
            nmeaPort = runtime,
            phoneDemandPort = RecoveryPhoneDemandPort(),
            scope = backgroundScope,
            newConnectionId = { ConnectionId("wizard-gateway") },
        )

        coordinator.dispatch(DataUiAction.OpenAddSource)
        coordinator.dispatch(DataUiAction.ChooseConnectionType(DataConnectionType.BOAT_GATEWAY))
        coordinator.dispatch(DataUiAction.ChangeConnectionHost("192.168.4.1"))
        coordinator.dispatch(DataUiAction.ChangeConnectionPort("10110"))
        coordinator.dispatch(DataUiAction.TestAndSaveConnection)
        advanceUntilIdle()

        val command = runtime.commands.single() as NmeaRuntimeCommand.SaveAndStart
        assertEquals("Boat Gateway", command.config.displayName)
        assertEquals(ConnectionId("wizard-gateway"), command.config.id)
        assertEquals(ConnectionTestPhase.WAITING_FOR_MARINE_DATA, coordinator.state.value.connectionTest.phase)
        assertTrue(coordinator.state.value.surface is DataSurface.ConnectionWizard)
    }

    @Test
    fun detectedSemanticEvidenceCompletesTheTestWithoutCallingSocketConnectedASuccess() = runTest(UnconfinedTestDispatcher()) {
        val source = RecoverySourcePort()
        val coordinator = DataCoordinator(
            sourcePort = source,
            nmeaPort = RecordingNmeaPort(),
            phoneDemandPort = RecoveryPhoneDemandPort(),
            scope = backgroundScope,
            newConnectionId = { ConnectionId("detected") },
        )
        coordinator.dispatch(DataUiAction.OpenAddSource)
        coordinator.dispatch(DataUiAction.ChooseConnectionType(DataConnectionType.BOAT_GATEWAY))
        coordinator.dispatch(DataUiAction.ChangeConnectionHost("boat.local"))
        coordinator.dispatch(DataUiAction.TestAndSaveConnection)
        advanceUntilIdle()
        assertEquals(ConnectionTestPhase.WAITING_FOR_MARINE_DATA, coordinator.state.value.connectionTest.phase)

        source.mutable.value = sourceWithPosition(ConnectionId("detected"))
        advanceUntilIdle()

        assertEquals(ConnectionTestPhase.DETECTED, coordinator.state.value.connectionTest.phase)
        assertEquals(setOf(BoatSensor.POSITION), coordinator.state.value.connectionTest.detectedSensors)
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

    private fun sourceWithPosition(id: ConnectionId): MarineSourceSnapshot {
        val source = SourceIdentity(id)
        val value = MarineValue.Position(-36.84, 174.76)
        val candidate = SourceCandidate(
            id = CandidateId(DataKey.Position, source),
            descriptor = SourceDescriptor(source, SourceKind.NMEA, "Boat Gateway", "RMC"),
            value = value,
            lastValidValue = value,
            availability = SourceCandidateAvailability.LIVE,
            ageMillis = 0L,
            receivedAtMillis = 20L,
            groupId = ObservationGroupId(2L),
            evidence = SourceEvidence.Nmea(setOf("GPRMC"), setOf("RMC")),
        )
        return MarineSourceSnapshot(
            sourceCatalog = SourceCatalogSnapshot(listOf(candidate), 20L, 2L),
            decisions = emptyList(),
            resolvedData = ResolvedDataSnapshot(emptyMap(), 0L, 20L),
            selectionRevision = 0L,
            revision = 2L,
            lastFailure = null,
        )
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
