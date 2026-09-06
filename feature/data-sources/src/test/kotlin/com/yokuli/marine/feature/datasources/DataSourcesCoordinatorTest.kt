package com.yokuli.marine.feature.datasources

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.catalog.SentenceCatalogEntry
import com.yokuli.marine.data.catalog.SentenceCatalogKey
import com.yokuli.marine.data.catalog.SentenceParseStatus
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.phone.PhoneLocationCommand
import com.yokuli.marine.data.phone.PhoneLocationCommandResult
import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.phone.PhoneLocationRuntimePort
import com.yokuli.marine.data.phone.PhoneLocationSnapshot
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeCommandResult
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineFeatureDestination
import com.yokuli.marine.data.source.MarineFeatureLinks
import com.yokuli.marine.data.source.MarineSourceRuntimePort
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.ResolvedDataSnapshot
import com.yokuli.marine.data.source.SourceCandidate
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.data.source.SourceCatalogSnapshot
import com.yokuli.marine.data.source.SourceDecision
import com.yokuli.marine.data.source.SourceDecisionStatus
import com.yokuli.marine.data.source.SourceDescriptor
import com.yokuli.marine.data.source.SourceEvidence
import com.yokuli.marine.data.source.SourceKind
import com.yokuli.marine.data.source.SourceSelectionCommand
import com.yokuli.marine.data.source.SourceSelectionCommandResult
import com.yokuli.marine.data.source.SourceSelectionFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourcesCoordinatorTest {
    private val source = SourceIdentity(ConnectionId("gateway"))

    @Test
    fun selectingCandidateUsesTheSharedOsPortAndReportsActualTransactionResult() = runBlocking {
        val sourcePort = FakeSourcePort(snapshot())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = DataSourcesCoordinator(sourcePort, FakeNmeaPort(), FakePhonePort(), scope)
            coordinator.dispatch(DataSourcesUiAction.UseSource(DataKey.Position, source))

            assertEquals(SourceSelectionCommand.Select(DataKey.Position, source), sourcePort.commands.single())
            assertEquals(DataSourcesNotice.SELECTION_SAVED, coordinator.state.value.notice)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedSelectionNeverShowsSuccessOrMutatesAParallelLocalSelection() = runBlocking {
        val sourcePort = FakeSourcePort(snapshot()).apply {
            nextResult = SourceSelectionCommandResult.Rejected(SourceSelectionFailure.PERSISTENCE_FAILED)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = DataSourcesCoordinator(sourcePort, FakeNmeaPort(), FakePhonePort(), scope)
            coordinator.dispatch(DataSourcesUiAction.UseSource(DataKey.Position, source))

            assertEquals(DataSourcesNotice.SELECTION_SAVE_FAILED, coordinator.state.value.notice)
            assertEquals(SourceDecisionStatus.NEEDS_SELECTION, coordinator.state.value.dataRows.single().decision.status)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun deniedPhoneEnableProducesOnePermissionEffectAndDoesNotAffectNmea() = runBlocking {
        val phone = FakePhonePort().apply {
            nextResult = PhoneLocationCommandResult.PermissionRequired(PhoneLocationPermission.DENIED)
        }
        val nmea = FakeNmeaPort()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = DataSourcesCoordinator(FakeSourcePort(snapshot()), nmea, phone, scope)
            val effect = async { coordinator.effects.first() }
            coordinator.dispatch(DataSourcesUiAction.EnablePhoneLocation)

            assertEquals(DataSourcesEffect.RequestPhoneLocationPermission, effect.await())
            assertTrue(phone.commands.contains(PhoneLocationCommand.Enable))
            assertTrue(nmea.commands.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun controlledNmeaLinkCarriesOnlyAnOpaqueValidatedToken() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = DataSourcesCoordinator(FakeSourcePort(snapshot()), FakeNmeaPort(), FakePhonePort(), scope)
            val effect = async { coordinator.effects.first() }
            coordinator.dispatch(DataSourcesUiAction.OpenNmeaInput(ConnectionId("gateway")))
            val actual = effect.await() as DataSourcesEffect.OpenNmeaInput

            assertEquals(
                MarineFeatureDestination.NmeaInput(ConnectionId("gateway")),
                MarineFeatureLinks.parse(actual.token),
            )
            assertFalse("gateway" in actual.token.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun backClosesDetailBeforeReturningControlToTheShellDesktop() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = DataSourcesCoordinator(FakeSourcePort(snapshot()), FakeNmeaPort(), FakePhonePort(), scope)
            coordinator.dispatch(DataSourcesUiAction.OpenData(DataKey.Position))
            assertTrue(coordinator.handleBack())
            assertFalse(coordinator.handleBack())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun newSentencePublishedByRuntimeAppearsWithoutRecreatingTheCoordinator() {
        val nmea = FakeNmeaPort()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = DataSourcesCoordinator(FakeSourcePort(snapshot()), nmea, FakePhonePort(), scope)
            coordinator.dispatch(DataSourcesUiAction.ChangeView(DataSourcesViewMode.SENTENCES))
            assertTrue((coordinator.state.value.page as DataSourcesPageUi.Overview).sentenceRows.isEmpty())

            val key = SentenceCatalogKey(source, "GP", "XYZ")
            nmea.state.value = NmeaRuntimeSnapshot.EMPTY.copy(
                sentenceCatalog = NmeaRuntimeSnapshot.EMPTY.sentenceCatalog.copy(
                    entries = listOf(
                        SentenceCatalogEntry(
                            key,
                            SessionGeneration(1L),
                            1L,
                            1L,
                            1L,
                            ObservationGroupId(1L),
                            "GPXYZ",
                            SentenceParseStatus.UNSUPPORTED,
                            null,
                            true,
                        ),
                    ),
                ),
            )

            assertEquals("GPXYZ", (coordinator.state.value.page as DataSourcesPageUi.Overview).sentenceRows.single().sentenceId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun permanentlyDeniedPhoneResolutionOpensAppPermissionSettingsWithoutTouchingRuntime() = runBlocking {
        val phone = FakePhonePort(
            PhoneLocationSnapshot(
                true,
                PhoneLocationPermission.PERMANENTLY_DENIED,
                true,
                com.yokuli.marine.data.phone.PhoneLocationState.PERMISSION_REQUIRED,
                null,
                1L,
            ),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = DataSourcesCoordinator(FakeSourcePort(snapshot()), FakeNmeaPort(), phone, scope)
            val effect = async { coordinator.effects.first() }
            coordinator.dispatch(DataSourcesUiAction.ResolvePhoneLocation)

            assertEquals(DataSourcesEffect.OpenAppPermissionSettings, effect.await())
            assertTrue(phone.commands.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun disabledSystemLocationResolutionOpensLocationSettingsWithoutTouchingRuntime() = runBlocking {
        val phone = FakePhonePort(
            PhoneLocationSnapshot(
                true,
                PhoneLocationPermission.PRECISE,
                false,
                com.yokuli.marine.data.phone.PhoneLocationState.SYSTEM_LOCATION_DISABLED,
                null,
                1L,
            ),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = DataSourcesCoordinator(FakeSourcePort(snapshot()), FakeNmeaPort(), phone, scope)
            val effect = async { coordinator.effects.first() }
            coordinator.dispatch(DataSourcesUiAction.ResolvePhoneLocation)

            assertEquals(DataSourcesEffect.OpenSystemLocationSettings, effect.await())
            assertTrue(phone.commands.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    private fun snapshot(): MarineSourceSnapshot {
        val candidate = SourceCandidate(
            CandidateId(DataKey.Position, source),
            SourceDescriptor(source, SourceKind.NMEA, "Gateway", "RMC"),
            MarineValue.Position(-36.8, 174.7),
            MarineValue.Position(-36.8, 174.7),
            SourceCandidateAvailability.LIVE,
            0L,
            1L,
            ObservationGroupId(1L),
            SourceEvidence.Nmea(setOf("GPRMC"), setOf("RMC")),
        )
        return MarineSourceSnapshot(
            SourceCatalogSnapshot(listOf(candidate), 1L, 1L),
            listOf(SourceDecision(DataKey.Position, SourceDecisionStatus.NEEDS_SELECTION, null, null, 1, true)),
            ResolvedDataSnapshot(emptyMap(), 0L, 1L),
            0L,
            1L,
            null,
        )
    }
}

private class FakeSourcePort(initial: MarineSourceSnapshot) : MarineSourceRuntimePort {
    override val state = MutableStateFlow(initial)
    val commands = mutableListOf<SourceSelectionCommand>()
    var nextResult: SourceSelectionCommandResult = SourceSelectionCommandResult.Success(1L)
    override suspend fun execute(command: SourceSelectionCommand): SourceSelectionCommandResult {
        commands += command
        return nextResult
    }
}

private class FakeNmeaPort : NmeaInputRuntimePort {
    override val state = MutableStateFlow(NmeaRuntimeSnapshot.EMPTY)
    val commands = mutableListOf<NmeaRuntimeCommand>()
    override suspend fun execute(command: NmeaRuntimeCommand): NmeaRuntimeCommandResult {
        commands += command
        return NmeaRuntimeCommandResult.Success(null, state.value.revision)
    }
}

private class FakePhonePort(initial: PhoneLocationSnapshot = PhoneLocationSnapshot.EMPTY) : PhoneLocationRuntimePort {
    override val state = MutableStateFlow(initial)
    val commands = mutableListOf<PhoneLocationCommand>()
    var nextResult: PhoneLocationCommandResult = PhoneLocationCommandResult.Success
    override suspend fun execute(command: PhoneLocationCommand): PhoneLocationCommandResult {
        commands += command
        return nextResult
    }
}
