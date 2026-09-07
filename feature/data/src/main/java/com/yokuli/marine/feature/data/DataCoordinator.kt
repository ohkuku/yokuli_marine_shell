package com.yokuli.marine.feature.data

import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeCommandResult
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.connection.StoredNmeaConnection
import com.yokuli.marine.data.connection.DEFAULT_NMEA_PROBE_TIMEOUT_MILLIS
import com.yokuli.marine.data.connection.NmeaConnectionProbePort
import com.yokuli.marine.data.connection.NmeaConnectionProbeResult
import com.yokuli.marine.data.source.MarineSourceRuntimePort
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.SourceSelectionCommand
import com.yokuli.marine.data.source.SourceSelectionCommandResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

class DataCoordinator(
    private val sourcePort: MarineSourceRuntimePort,
    private val nmeaPort: NmeaInputRuntimePort,
    private val phoneDemandPort: DataPhoneDemandPort,
    scope: CoroutineScope,
    private val newConnectionId: () -> ConnectionId = { ConnectionId(UUID.randomUUID().toString()) },
    consumerActivity: StateFlow<MarineConsumerActivitySnapshot> = MutableStateFlow(MarineConsumerActivitySnapshot.EMPTY),
    private val connectionProbe: NmeaConnectionProbePort? = null,
) {
    private sealed interface Request {
        data class Select(val command: SourceSelectionCommand) : Request
        data class Phone(val group: SourceGroup) : Request
        data class Permission(val permanentlyDenied: Boolean) : Request
        data object ResolvePhone : Request
        data class Runtime(val command: NmeaRuntimeCommand, val purpose: RuntimePurpose) : Request
        data class Probe(val draft: DataConnectionDraft) : Request
    }

    private enum class RuntimePurpose { USE_PROBED, START, STOP, RETRY, DELETE }

    private val lock = Any()
    private val requests = Channel<Request>(MAX_PENDING_ACTIONS)
    private val effectChannel = Channel<DataEffect>(MAX_PENDING_EFFECTS)
    val effects: Flow<DataEffect> = effectChannel.receiveAsFlow()
    private var sources: MarineSourceSnapshot = sourcePort.state.value
    private var nmea: NmeaRuntimeSnapshot = nmeaPort.state.value
    private var phoneDemand: DataPhoneDemandState = phoneDemandPort.state.value
    private var consumers: MarineConsumerActivitySnapshot = consumerActivity.value
    private var section = DataSection.OVERVIEW
    private var surface: DataSurface = DataSurface.Primary(PrimaryDataArea.BOAT)
    private var connectionDraft: DataConnectionDraft? = null
    private var connectionTest = DataConnectionTestState.IDLE
    private var flowExpert = false
    private var sourceFocus: com.yokuli.marine.data.model.ConnectionId? = null
    private var notice: DataNotice? = null
    private val mutableState = MutableStateFlow(projectLocked())
    val state: StateFlow<DataUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            sourcePort.state.collect { synchronized(lock) { sources = it; publishLocked() } }
        }
        scope.launch {
            nmeaPort.state.collect { synchronized(lock) { nmea = it; publishLocked() } }
        }
        scope.launch {
            phoneDemandPort.state.collect { synchronized(lock) { phoneDemand = it; publishLocked() } }
        }
        scope.launch {
            consumerActivity.collect { synchronized(lock) { consumers = it; publishLocked() } }
        }
        scope.launch {
            for (request in requests) execute(request)
        }
    }

    fun dispatch(action: DataUiAction) {
        val request = synchronized(lock) {
            val next = when (action) {
                is DataUiAction.NavigatePrimary -> {
                    surface = DataSurface.Primary(action.area)
                    section = action.area.toLegacySection()
                    null
                }
                is DataUiAction.Navigate -> {
                    section = action.section
                    surface = action.section.toSurface()
                    null
                }
                is DataUiAction.OpenSensor -> {
                    surface = DataSurface.Sensor(action.sensor)
                    null
                }
                is DataUiAction.OpenTrust -> {
                    surface = DataSurface.Trust(action.group)
                    null
                }
                DataUiAction.OpenAddSource -> {
                    surface = DataSurface.AddSource
                    connectionDraft = null
                    connectionTest = DataConnectionTestState.IDLE
                    null
                }
                is DataUiAction.ChooseConnectionType -> {
                    connectionDraft = DataConnectionDraft.create(newConnectionId(), action.type)
                    connectionTest = DataConnectionTestState.IDLE
                    surface = DataSurface.ConnectionWizard(action.type)
                    null
                }
                is DataUiAction.ChangeConnectionType -> {
                    connectionDraft = connectionDraft?.copy(type = action.type)
                    connectionTest = DataConnectionTestState.IDLE
                    surface = DataSurface.ConnectionWizard(action.type)
                    null
                }
                DataUiAction.ChoosePhoneSource -> {
                    surface = DataSurface.Sensor(BoatSensor.POSITION)
                    Request.Phone(SourceGroup.POSITION_AND_MOTION)
                }
                is DataUiAction.ChangeConnectionHost -> {
                    connectionDraft = connectionDraft?.copy(host = action.value)
                    connectionTest = DataConnectionTestState.IDLE
                    null
                }
                is DataUiAction.ChangeConnectionPort -> {
                    connectionDraft = connectionDraft?.copy(portText = action.value)
                    connectionTest = DataConnectionTestState.IDLE
                    null
                }
                is DataUiAction.ChangeConnectionName -> {
                    connectionDraft = connectionDraft?.copy(customName = action.value)
                    connectionTest = DataConnectionTestState.IDLE
                    null
                }
                DataUiAction.TestConnection -> {
                    val draft = connectionDraft
                    val config = draft?.configOrNull()
                    if (draft == null || config == null) {
                        connectionTest = DataConnectionTestState(ConnectionTestPhase.INVALID_CONFIGURATION)
                        null
                    } else {
                        connectionTest = DataConnectionTestState(ConnectionTestPhase.PROBING)
                        Request.Probe(draft)
                    }
                }
                DataUiAction.UseTestedConnection -> {
                    val draft = connectionDraft
                    val config = draft?.configOrNull()
                    if (draft == null || config == null || connectionTest.phase != ConnectionTestPhase.DETECTED) {
                        null
                    } else {
                        connectionTest = connectionTest.copy(phase = ConnectionTestPhase.SAVING)
                        Request.Runtime(
                            NmeaRuntimeCommand.SaveAndStart(config, draft.expectedRevision),
                            RuntimePurpose.USE_PROBED,
                        )
                    }
                }
                DataUiAction.CancelConnectionSetup -> {
                    surface = DataSurface.Primary(PrimaryDataArea.CONNECTIONS)
                    section = DataSection.INPUTS
                    connectionDraft = null
                    connectionTest = DataConnectionTestState.IDLE
                    null
                }
                is DataUiAction.OpenConnection -> {
                    surface = DataSurface.Connection(action.id)
                    null
                }
                is DataUiAction.EditConnection -> {
                    val stored = nmea.connections.firstOrNull { it.stored.config.id == action.id }?.stored
                    if (stored != null) {
                        connectionDraft = stored.toDataDraft()
                        connectionTest = DataConnectionTestState.IDLE
                        surface = DataSurface.ConnectionWizard(connectionDraft!!.type)
                    }
                    null
                }
                is DataUiAction.StartConnection -> Request.Runtime(
                    NmeaRuntimeCommand.Start(action.id), RuntimePurpose.START,
                )
                is DataUiAction.StopConnection -> Request.Runtime(
                    NmeaRuntimeCommand.Stop(action.id), RuntimePurpose.STOP,
                )
                is DataUiAction.RetryConnection -> Request.Runtime(
                    NmeaRuntimeCommand.Retry(action.id), RuntimePurpose.RETRY,
                )
                is DataUiAction.DeleteConnection -> {
                    surface = DataSurface.Primary(PrimaryDataArea.CONNECTIONS)
                    Request.Runtime(NmeaRuntimeCommand.Delete(action.id), RuntimePurpose.DELETE)
                }
                is DataUiAction.OpenDiagnostics -> {
                    surface = DataSurface.Diagnostics(action.connectionId)
                    null
                }
                DataUiAction.ToggleFlowExpert -> {
                    flowExpert = !flowExpert
                    null
                }
                is DataUiAction.UseSource -> {
                    val plan = SourceGroupSelectionAdapter.select(action.group, action.source, sources)
                    when (plan) {
                        is SourceGroupSelectionPlan.Ready -> Request.Select(plan.command)
                        SourceGroupSelectionPlan.CandidateUnavailable -> {
                            notice = DataNotice.CANDIDATE_UNAVAILABLE
                            null
                        }
                    }
                }
                is DataUiAction.DisableGroup -> Request.Select(SourceGroupSelectionAdapter.disable(action.group))
                is DataUiAction.InspectFlowSource -> {
                    section = DataSection.SOURCES
                    surface = DataSurface.Connection(action.source.connectionId)
                    sourceFocus = action.source.connectionId
                    null
                }
                is DataUiAction.InspectFlowGroup -> {
                    section = DataSection.SOURCES
                    surface = DataSurface.Trust(action.group)
                    sourceFocus = null
                    null
                }
                is DataUiAction.UsePhone -> Request.Phone(action.group)
                is DataUiAction.PhonePermissionResult -> Request.Permission(action.permanentlyDenied)
                DataUiAction.ResolvePhoneDemand -> Request.ResolvePhone
                DataUiAction.ClearSourceFocus -> {
                    sourceFocus = null
                    null
                }
                DataUiAction.DismissNotice -> {
                    notice = null
                    null
                }
            }
            publishLocked()
            next
        }
        if (request != null && requests.trySend(request).isFailure) {
            synchronized(lock) { notice = DataNotice.ACTION_QUEUE_FULL; publishLocked() }
        }
    }

    fun open(token: com.yokuli.shell.contract.LaunchToken): DataDestination? = synchronized(lock) {
        val destination = DataDestinations.parse(token) ?: return null
        section = destination.section
        sourceFocus = (destination as? DataDestination.Source)?.connectionId
        surface = when (destination) {
            is DataDestination.Input -> destination.connectionId?.let(DataSurface::Connection)
                ?: DataSurface.Primary(PrimaryDataArea.CONNECTIONS)
            is DataDestination.Source -> DataSurface.Sensor(BoatSensor.POSITION)
            is DataDestination.Section -> destination.section.toSurface()
        }
        publishLocked()
        destination
    }

    /** Root Back is deliberately left to the in-app Shell, which returns to Start. */
    fun handleBack(): Boolean = synchronized(lock) {
        val parent = DataBackPolicy.parent(surface) ?: return false
        surface = parent
        section = (parent as? DataSurface.Primary)?.area?.toLegacySection() ?: DataSection.OVERVIEW
        sourceFocus = null
        publishLocked()
        true
    }

    private suspend fun execute(request: Request) {
        try {
            when (request) {
                is Request.Select -> {
                    phoneDemandPort.cancelPending()
                    val result = sourcePort.execute(request.command)
                    synchronized(lock) {
                        notice = if (result is SourceSelectionCommandResult.Success) {
                            if (request.command is SourceSelectionCommand.ApplyAtomically &&
                                request.command.preferences.values.all {
                                    it is com.yokuli.marine.data.source.SourcePreference.Disabled
                                }
                            ) DataNotice.DISABLED else DataNotice.SAVED
                        } else DataNotice.PERSISTENCE_FAILED
                        publishLocked()
                    }
                }
                is Request.Phone -> handlePhoneResult(phoneDemandPort.request(request.group))
                is Request.Permission -> handlePhoneResult(phoneDemandPort.permissionResult(request.permanentlyDenied))
                Request.ResolvePhone -> handlePhoneResult(phoneDemandPort.refreshPlatformState())
                is Request.Runtime -> handleRuntimeResult(request, nmeaPort.execute(request.command))
                is Request.Probe -> handleProbe(request)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            synchronized(lock) { notice = DataNotice.PERSISTENCE_FAILED; publishLocked() }
        }
    }

    private suspend fun handleProbe(request: Request.Probe) {
        val config = request.draft.configOrNull() ?: run {
            synchronized(lock) {
                connectionTest = DataConnectionTestState(ConnectionTestPhase.INVALID_CONFIGURATION)
                publishLocked()
            }
            return
        }
        when (val probe = connectionProbe?.probe(config, DEFAULT_NMEA_PROBE_TIMEOUT_MILLIS)
            ?: NmeaConnectionProbeResult.NoSemanticData(false, 0L)
        ) {
            is NmeaConnectionProbeResult.Detected -> {
                val save = nmeaPort.execute(NmeaRuntimeCommand.SaveAndStart(config, request.draft.expectedRevision))
                synchronized(lock) {
                    connectionTest = when (save) {
                        is NmeaRuntimeCommandResult.Success -> DataConnectionTestState(
                            phase = ConnectionTestPhase.DETECTED,
                            detectedSensors = probe.dataKeys.mapNotNullTo(linkedSetOf(), ::sensorFor),
                            transportReady = true,
                            legalFrameCount = probe.legalFrameCount,
                        )
                        is NmeaRuntimeCommandResult.Rejected -> DataConnectionTestState(
                            phase = ConnectionTestPhase.FAILED,
                            failure = save.failure,
                            transportReady = true,
                            legalFrameCount = probe.legalFrameCount,
                        )
                    }
                    publishLocked()
                }
            }
            is NmeaConnectionProbeResult.NoSemanticData -> synchronized(lock) {
                connectionTest = DataConnectionTestState(
                    phase = ConnectionTestPhase.NO_SEMANTIC_DATA,
                    transportReady = probe.transportReady,
                    legalFrameCount = probe.legalFrameCount,
                )
                publishLocked()
            }
            is NmeaConnectionProbeResult.Failed -> synchronized(lock) {
                connectionTest = DataConnectionTestState(ConnectionTestPhase.FAILED, failure = probe.failure)
                publishLocked()
            }
        }
    }

    private fun handleRuntimeResult(request: Request.Runtime, result: NmeaRuntimeCommandResult) = synchronized(lock) {
        val failure = (result as? NmeaRuntimeCommandResult.Rejected)?.failure
        when (request.purpose) {
            RuntimePurpose.USE_PROBED -> {
                if (failure == null) {
                    surface = DataSurface.Primary(PrimaryDataArea.CONNECTIONS)
                    section = DataSection.INPUTS
                    connectionDraft = null
                    connectionTest = DataConnectionTestState.IDLE
                    notice = DataNotice.SAVED
                } else {
                    connectionTest = DataConnectionTestState(ConnectionTestPhase.FAILED, failure = failure)
                }
            }
            RuntimePurpose.START, RuntimePurpose.STOP, RuntimePurpose.RETRY, RuntimePurpose.DELETE -> {
                notice = if (failure == null) DataNotice.SAVED else DataNotice.PERSISTENCE_FAILED
            }
        }
        publishLocked()
    }

    private fun handlePhoneResult(result: DataPhoneRequestResult) = synchronized(lock) {
        notice = when (result) {
            DataPhoneRequestResult.Accepted -> DataNotice.SAVED
            DataPhoneRequestResult.PermissionRequired -> {
                offerEffect(DataEffect.RequestPhoneLocationPermission)
                DataNotice.PHONE_PERMISSION_REQUIRED
            }
            DataPhoneRequestResult.SystemLocationDisabled -> {
                offerEffect(DataEffect.OpenSystemLocationSettings)
                DataNotice.PHONE_LOCATION_DISABLED
            }
            DataPhoneRequestResult.PlatformRestricted -> {
                if (phoneDemand.phone.permission == com.yokuli.marine.data.phone.PhoneLocationPermission.PERMANENTLY_DENIED) {
                    offerEffect(DataEffect.OpenAppPermissionSettings)
                }
                DataNotice.PHONE_PLATFORM_RESTRICTED
            }
            DataPhoneRequestResult.CandidateUnavailable -> DataNotice.CANDIDATE_UNAVAILABLE
            DataPhoneRequestResult.PersistenceFailed -> DataNotice.PERSISTENCE_FAILED
        }
        publishLocked()
    }

    private fun offerEffect(effect: DataEffect) {
        if (effectChannel.trySend(effect).isFailure) notice = DataNotice.ACTION_QUEUE_FULL
    }

    private fun projectLocked(): DataUiState {
        return DataDomainProjector.project(sources, nmea, section, consumers).copy(
        surface = surface,
        primaryArea = (surface as? DataSurface.Primary)?.area ?: section.toPrimaryArea(),
        connectionDraft = connectionDraft,
        connectionTest = connectionTest,
        flowExpert = flowExpert,
        phoneDemand = phoneDemand.demand,
        phone = phoneDemand.phone,
        focusedSourceConnectionId = sourceFocus,
        notice = notice,
    )
    }

    private fun publishLocked() {
        mutableState.value = projectLocked()
    }

    private companion object {
        const val MAX_PENDING_ACTIONS = 32
        const val MAX_PENDING_EFFECTS = 8
    }
}

private fun PrimaryDataArea.toLegacySection(): DataSection = when (this) {
    PrimaryDataArea.BOAT -> DataSection.OVERVIEW
    PrimaryDataArea.FLOW -> DataSection.FLOW
    PrimaryDataArea.CONNECTIONS -> DataSection.INPUTS
}

private fun DataSection.toPrimaryArea(): PrimaryDataArea = when (this) {
    DataSection.FLOW -> PrimaryDataArea.FLOW
    DataSection.INPUTS -> PrimaryDataArea.CONNECTIONS
    else -> PrimaryDataArea.BOAT
}

private fun DataSection.toSurface(): DataSurface = when (this) {
    DataSection.FLOW -> DataSurface.Primary(PrimaryDataArea.FLOW)
    DataSection.INPUTS -> DataSurface.Primary(PrimaryDataArea.CONNECTIONS)
    DataSection.DIAGNOSTICS -> DataSurface.Diagnostics()
    DataSection.SOURCES -> DataSurface.Sensor(BoatSensor.POSITION)
    DataSection.OVERVIEW -> DataSurface.Primary(PrimaryDataArea.BOAT)
}

private fun StoredNmeaConnection.toDataDraft(): DataConnectionDraft {
    val endpoint = config.endpoint
    return DataConnectionDraft(
        id = config.id,
        type = when (endpoint) {
            is NmeaEndpoint.TcpClient -> DataConnectionType.ADVANCED_TCP
            is NmeaEndpoint.UdpListener -> DataConnectionType.ADVANCED_UDP
        },
        host = (endpoint as? NmeaEndpoint.TcpClient)?.host.orEmpty(),
        portText = when (endpoint) {
            is NmeaEndpoint.TcpClient -> endpoint.port.toString()
            is NmeaEndpoint.UdpListener -> endpoint.localPort.toString()
        },
        customName = config.displayName,
        checksumPolicy = config.checksumPolicy,
        expectedRevision = revision,
    )
}
