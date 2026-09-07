package com.yokuli.marine.feature.data

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.phone.PHONE_SYSTEM_LOCATION_SOURCE
import com.yokuli.marine.data.phone.PhoneLocationCommand
import com.yokuli.marine.data.phone.PhoneLocationCommandResult
import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.phone.PhoneLocationRuntimePort
import com.yokuli.marine.data.phone.PhoneLocationSnapshot
import com.yokuli.marine.data.phone.PhoneLocationState
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataAppContractTest {
    @Test
    fun currentAndLegacyTokensResolveIntoOneDataApp() {
        assertEquals(DataSection.OVERVIEW, DataDestinations.parse(DataDestinations.Overview)?.section)
        assertEquals(DataSection.INPUTS, DataDestinations.parse(com.yokuli.shell.contract.LaunchToken("nmea.root"))?.section)
        assertEquals(
            ConnectionId("gateway"),
            (DataDestinations.parse(com.yokuli.shell.contract.LaunchToken("nmea.connection.gateway")) as DataDestination.Input)
                .connectionId,
        )
        assertEquals(DataSection.DIAGNOSTICS, DataDestinations.parse(com.yokuli.shell.contract.LaunchToken("sources.attention"))?.section)
        assertNull(DataDestinations.parse(com.yokuli.shell.contract.LaunchToken("chart.browse")))
    }

    @Test
    fun rootBackReturnsToShellWhileEveryOtherSectionReturnsToOverview() = runTest {
        val source = FakeSourcePort()
        val phone = FakePhonePort()
        val demand = FakeDemandPort(phone.state.value)
        val coordinator = DataCoordinator(source, FakeNmeaPort(), demand, backgroundScope)

        coordinator.open(DataDestinations.Flow)
        assertTrue(coordinator.handleBack())
        assertEquals(DataSection.OVERVIEW, coordinator.state.value.section)
        assertFalse(coordinator.handleBack())
    }

    @Test
    fun phoneRequestIsSelectionDemandNotAnIndependentEnableToggle() = runTest {
        val source = FakeSourcePort(phoneCandidateSnapshot())
        val phone = FakePhonePort()
        val runtime = DataPhoneDemandRuntime(source, phone, backgroundScope)

        assertEquals(DataPhoneRequestResult.Accepted, runtime.request(SourceGroup.POSITION_AND_MOTION))
        val command = source.commands.single() as SourceSelectionCommand.ApplyAtomically
        assertEquals(PHONE_SYSTEM_LOCATION_SOURCE, (command.preferences.getValue(DataKey.Position) as com.yokuli.marine.data.source.SourcePreference.Selected).source)
        assertTrue(command.preferences.values.any { it is com.yokuli.marine.data.source.SourcePreference.Disabled })
        assertEquals(emptyList<PhoneLocationCommand>(), phone.commands)
    }

    @Test
    fun selectedPhoneDemandSurvivesUiAndNoDemandStopsOnlyAfterSourceInitialization() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeSourcePort(MarineSourceSnapshot.EMPTY)
        val enabled = PhoneLocationSnapshot.EMPTY.copy(
            enabledByUser = true,
            permission = PhoneLocationPermission.PRECISE,
            systemLocationEnabled = true,
            state = PhoneLocationState.STARTING,
            revision = 1L,
        )
        val phone = FakePhonePort(enabled)
        DataPhoneDemandRuntime(source, phone, backgroundScope)
        advanceUntilIdle()
        assertTrue(phone.commands.isEmpty())

        source.mutable.value = emptyInitializedSnapshot()
        advanceUntilIdle()
        assertEquals(listOf(PhoneLocationCommand.Disable), phone.commands)
    }

    @Test
    fun missingPermissionIsReturnedAsTypedRecoveryInsteadOfPretendingPhoneIsLive() = runTest {
        val source = FakeSourcePort(emptyInitializedSnapshot())
        val phone = FakePhonePort(
            PhoneLocationSnapshot.EMPTY.copy(
                permission = PhoneLocationPermission.NOT_DETERMINED,
                systemLocationEnabled = true,
                state = PhoneLocationState.DISABLED_BY_USER,
            ),
            enableResult = PhoneLocationCommandResult.PermissionRequired(PhoneLocationPermission.NOT_DETERMINED),
        )
        val runtime = DataPhoneDemandRuntime(source, phone, backgroundScope)

        assertEquals(DataPhoneRequestResult.PermissionRequired, runtime.request(SourceGroup.POSITION_AND_MOTION))
        assertEquals(SourceGroup.POSITION_AND_MOTION, runtime.state.value.pendingGroup)
        assertFalse(runtime.state.value.demand.required)
    }

    private fun phoneCandidateSnapshot(): MarineSourceSnapshot {
        val candidate = SourceCandidate(
            id = CandidateId(DataKey.Position, PHONE_SYSTEM_LOCATION_SOURCE),
            descriptor = SourceDescriptor(
                PHONE_SYSTEM_LOCATION_SOURCE,
                SourceKind.PHONE_SYSTEM_LOCATION,
                "Phone",
                "platform location",
            ),
            value = MarineValue.Position(-36.8, 174.7),
            lastValidValue = MarineValue.Position(-36.8, 174.7),
            availability = SourceCandidateAvailability.LIVE,
            ageMillis = 0L,
            receivedAtMillis = 1L,
            groupId = ObservationGroupId(1L),
            evidence = SourceEvidence.PhoneSystemLocation("gps", false),
        )
        return emptyInitializedSnapshot().copy(
            sourceCatalog = SourceCatalogSnapshot(listOf(candidate), 1L, 1L),
        )
    }

    private fun emptyInitializedSnapshot() = MarineSourceSnapshot.EMPTY.copy(
        sourceCatalog = SourceCatalogSnapshot.EMPTY.copy(revision = 1L),
        resolvedData = ResolvedDataSnapshot(emptyMap(), 0L, 1L),
        revision = 1L,
    )
}

private class FakeSourcePort(initial: MarineSourceSnapshot = MarineSourceSnapshot.EMPTY) : MarineSourceRuntimePort {
    val mutable = MutableStateFlow(initial)
    override val state: StateFlow<MarineSourceSnapshot> = mutable
    val commands = mutableListOf<SourceSelectionCommand>()
    override suspend fun execute(command: SourceSelectionCommand): SourceSelectionCommandResult {
        commands += command
        return SourceSelectionCommandResult.Success(mutable.value.selectionRevision + 1L)
    }
}

private class FakePhonePort(
    initial: PhoneLocationSnapshot = PhoneLocationSnapshot.EMPTY,
    private val enableResult: PhoneLocationCommandResult = PhoneLocationCommandResult.Success,
) : PhoneLocationRuntimePort {
    private val mutable = MutableStateFlow(initial)
    override val state: StateFlow<PhoneLocationSnapshot> = mutable
    val commands = mutableListOf<PhoneLocationCommand>()
    override suspend fun execute(command: PhoneLocationCommand): PhoneLocationCommandResult {
        commands += command
        if (command == PhoneLocationCommand.Disable) {
            mutable.value = mutable.value.copy(enabledByUser = false, state = PhoneLocationState.DISABLED_BY_USER)
        }
        return if (command == PhoneLocationCommand.Enable) enableResult else PhoneLocationCommandResult.Success
    }
}

private class FakeDemandPort(phone: PhoneLocationSnapshot) : DataPhoneDemandPort {
    private val mutable = MutableStateFlow(DataPhoneDemandState(phone = phone))
    override val state: StateFlow<DataPhoneDemandState> = mutable
    override suspend fun request(group: SourceGroup) = DataPhoneRequestResult.Accepted
    override suspend fun permissionResult(permanentlyDenied: Boolean) = DataPhoneRequestResult.Accepted
    override suspend fun refreshPlatformState() = DataPhoneRequestResult.Accepted
    override suspend fun cancelPending() = Unit
}

private class FakeNmeaPort : NmeaInputRuntimePort {
    override val state: StateFlow<NmeaRuntimeSnapshot> = MutableStateFlow(NmeaRuntimeSnapshot.EMPTY)
    override suspend fun execute(command: NmeaRuntimeCommand): NmeaRuntimeCommandResult =
        NmeaRuntimeCommandResult.Success(null, 0L)
}
