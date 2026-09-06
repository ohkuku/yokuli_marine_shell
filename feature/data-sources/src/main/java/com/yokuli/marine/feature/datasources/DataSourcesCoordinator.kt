package com.yokuli.marine.feature.datasources

import com.yokuli.marine.data.phone.PhoneLocationCommand
import com.yokuli.marine.data.phone.PhoneLocationCommandResult
import com.yokuli.marine.data.phone.PhoneLocationRuntimePort
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineFeatureDestination
import com.yokuli.marine.data.source.MarineFeatureLinkToken
import com.yokuli.marine.data.source.MarineFeatureLinks
import com.yokuli.marine.data.source.MarineSourceRuntimePort
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.SourceSelectionCommand
import com.yokuli.marine.data.source.SourceSelectionCommandResult
import com.yokuli.marine.data.source.SourceSelectionFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DataSourcesCoordinator(
    private val sourcePort: MarineSourceRuntimePort,
    private val nmeaPort: NmeaInputRuntimePort,
    private val phonePort: PhoneLocationRuntimePort,
    scope: CoroutineScope,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private sealed interface Request {
        data class Selection(val command: SourceSelectionCommand, val success: DataSourcesNotice) : Request
        data class Phone(val command: PhoneLocationCommand, val success: DataSourcesNotice) : Request
    }

    private val lock = Any()
    private val requests = Channel<Request>(MAX_PENDING_ACTIONS)
    private val effectChannel = Channel<DataSourcesEffect>(MAX_PENDING_EFFECTS)
    val effects: Flow<DataSourcesEffect> = effectChannel.receiveAsFlow()
    private var sources = sourcePort.state.value
    private var nmea = nmeaPort.state.value
    private var phone = phonePort.state.value
    private var local = DataSourcesLocalState()
    private var notice: DataSourcesNotice? = null
    private val mutableState = MutableStateFlow(projectLocked())
    val state: StateFlow<DataSourcesUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            sourcePort.state.collect { snapshot ->
                synchronized(lock) {
                    sources = snapshot
                    publishLocked()
                }
            }
        }
        scope.launch {
            nmeaPort.state.collect { snapshot ->
                synchronized(lock) {
                    nmea = snapshot
                    publishLocked()
                }
            }
        }
        scope.launch {
            phonePort.state.collect { snapshot ->
                synchronized(lock) {
                    phone = snapshot
                    publishLocked()
                }
            }
        }
        scope.launch {
            for (request in requests) execute(request)
        }
    }

    fun dispatch(action: DataSourcesUiAction) {
        val request = synchronized(lock) {
            when (action) {
                is DataSourcesUiAction.ChangeView -> local = local.copy(viewMode = action.mode)
                is DataSourcesUiAction.ChangeFilter -> local = local.copy(filter = action.filter)
                is DataSourcesUiAction.ChangeQuery -> local = local.copy(query = action.query)
                is DataSourcesUiAction.OpenData -> local = local.copy(page = DataSourcesLocalPage.DataDetail(action.key))
                is DataSourcesUiAction.OpenSentence -> local = local.copy(
                    page = DataSourcesLocalPage.SentenceDetail(action.key),
                )
                DataSourcesUiAction.BackToOverview -> local = local.copy(page = DataSourcesLocalPage.Overview)
                DataSourcesUiAction.DismissNotice -> notice = null
                is DataSourcesUiAction.OpenNmeaInput -> {
                    val token = action.connectionId?.let(MarineFeatureLinks::nmeaInputForConnection)
                        ?: MarineFeatureLinks.nmeaInputRoot
                    if (effectChannel.trySend(DataSourcesEffect.OpenNmeaInput(token)).isFailure) {
                        notice = DataSourcesNotice.ACTION_QUEUE_FULL
                    }
                }
                else -> Unit
            }
            publishLocked()
            when (action) {
                is DataSourcesUiAction.UseSource -> Request.Selection(
                    SourceSelectionCommand.Select(action.key, action.source),
                    DataSourcesNotice.SELECTION_SAVED,
                )
                is DataSourcesUiAction.DisableData -> Request.Selection(
                    SourceSelectionCommand.Disable(action.key),
                    DataSourcesNotice.SELECTION_DISABLED,
                )
                DataSourcesUiAction.EnablePhoneLocation -> Request.Phone(
                    PhoneLocationCommand.Enable,
                    DataSourcesNotice.PHONE_LOCATION_ENABLED,
                )
                DataSourcesUiAction.DisablePhoneLocation -> Request.Phone(
                    PhoneLocationCommand.Disable,
                    DataSourcesNotice.PHONE_LOCATION_DISABLED,
                )
                is DataSourcesUiAction.PhonePermissionResult -> Request.Phone(
                    PhoneLocationCommand.PermissionResult(action.permanentlyDenied),
                    DataSourcesNotice.PHONE_LOCATION_ENABLED,
                )
                else -> null
            }
        }
        if (request != null && requests.trySend(request).isFailure) {
            synchronized(lock) {
                notice = DataSourcesNotice.ACTION_QUEUE_FULL
                publishLocked()
            }
        }
    }

    fun open(token: MarineFeatureLinkToken): Boolean = synchronized(lock) {
        val destination = MarineFeatureLinks.parse(token) as? MarineFeatureDestination.DataSources
            ?: return false
        local = local.copy(
            filter = destination.connectionId?.let(DataSourcesFilter::Connection) ?: DataSourcesFilter.All,
            page = DataSourcesLocalPage.Overview,
        )
        publishLocked()
        true
    }

    fun handleBack(): Boolean = synchronized(lock) {
        if (local.page == DataSourcesLocalPage.Overview) return false
        local = local.copy(page = DataSourcesLocalPage.Overview)
        publishLocked()
        true
    }

    private suspend fun execute(request: Request) {
        try {
            when (request) {
                is Request.Selection -> handleSelection(request)
                is Request.Phone -> handlePhone(request)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            synchronized(lock) {
                notice = DataSourcesNotice.SELECTION_SAVE_FAILED
                publishLocked()
            }
        }
    }

    private suspend fun handleSelection(request: Request.Selection) {
        val result = sourcePort.execute(request.command)
        synchronized(lock) {
            notice = when (result) {
                is SourceSelectionCommandResult.Success -> request.success
                is SourceSelectionCommandResult.Rejected -> when (result.failure) {
                    SourceSelectionFailure.CANDIDATE_UNAVAILABLE -> DataSourcesNotice.CANDIDATE_UNAVAILABLE
                    else -> DataSourcesNotice.SELECTION_SAVE_FAILED
                }
            }
            publishLocked()
        }
    }

    private suspend fun handlePhone(request: Request.Phone) {
        val result = phonePort.execute(request.command)
        synchronized(lock) {
            notice = when (result) {
                PhoneLocationCommandResult.Success -> request.success
                is PhoneLocationCommandResult.PermissionRequired -> {
                    if (effectChannel.trySend(DataSourcesEffect.RequestPhoneLocationPermission).isFailure) {
                        DataSourcesNotice.ACTION_QUEUE_FULL
                    } else {
                        DataSourcesNotice.PHONE_LOCATION_UNAVAILABLE
                    }
                }
                PhoneLocationCommandResult.SystemLocationDisabled -> DataSourcesNotice.PHONE_LOCATION_UNAVAILABLE
                PhoneLocationCommandResult.PlatformRestricted -> DataSourcesNotice.PHONE_LOCATION_UNAVAILABLE
            }
            publishLocked()
        }
    }

    private fun publishLocked() {
        mutableState.value = projectLocked()
    }

    private fun projectLocked() = DataSourcesProjector.project(
        sources = sources,
        nmea = nmea,
        phone = phone,
        local = local,
        nowMillis = nowMillis(),
        notice = notice,
    )

    private companion object {
        const val MAX_PENDING_ACTIONS = 32
        const val MAX_PENDING_EFFECTS = 16
    }
}
