package com.yokuli.marine.feature.data

import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
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

class DataCoordinator(
    private val sourcePort: MarineSourceRuntimePort,
    private val nmeaPort: NmeaInputRuntimePort,
    private val phoneDemandPort: DataPhoneDemandPort,
    scope: CoroutineScope,
) {
    private sealed interface Request {
        data class Select(val command: SourceSelectionCommand) : Request
        data class Phone(val group: SourceGroup) : Request
        data class Permission(val permanentlyDenied: Boolean) : Request
        data object ResolvePhone : Request
    }

    private val lock = Any()
    private val requests = Channel<Request>(MAX_PENDING_ACTIONS)
    private val effectChannel = Channel<DataEffect>(MAX_PENDING_EFFECTS)
    val effects: Flow<DataEffect> = effectChannel.receiveAsFlow()
    private var sources: MarineSourceSnapshot = sourcePort.state.value
    private var nmea: NmeaRuntimeSnapshot = nmeaPort.state.value
    private var phoneDemand: DataPhoneDemandState = phoneDemandPort.state.value
    private var section = DataSection.OVERVIEW
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
            for (request in requests) execute(request)
        }
    }

    fun dispatch(action: DataUiAction) {
        val request = synchronized(lock) {
            val next = when (action) {
                is DataUiAction.Navigate -> {
                    section = action.section
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
        publishLocked()
        destination
    }

    /** Root Back is deliberately left to the in-app Shell, which returns to Start. */
    fun handleBack(): Boolean = synchronized(lock) {
        val action = DataBackPolicy.actionFor(section) ?: return false
        section = (action as DataUiAction.Navigate).section
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
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            synchronized(lock) { notice = DataNotice.PERSISTENCE_FAILED; publishLocked() }
        }
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

    private fun projectLocked(): DataUiState = DataDomainProjector.project(sources, nmea, section).copy(
        phoneDemand = phoneDemand.demand,
        phone = phoneDemand.phone,
        focusedSourceConnectionId = sourceFocus,
        notice = notice,
    )

    private fun publishLocked() {
        mutableState.value = projectLocked()
    }

    private companion object {
        const val MAX_PENDING_ACTIONS = 32
        const val MAX_PENDING_EFFECTS = 8
    }
}
