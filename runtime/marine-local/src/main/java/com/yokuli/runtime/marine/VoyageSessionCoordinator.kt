package com.yokuli.runtime.marine

import com.yokuli.anchorwatch.MainUiState
import com.yokuli.anchorwatch.api.MarineServices
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.vessel.VesselSourcePreference
import com.yokuli.runtime.contract.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 唯一航行命令协调器。关闭页面/通知/地图不会取消命令或创建第二个活动航行。 */
class VoyageSessionCoordinator internal constructor(private val services: MarineServices, private val scope: CoroutineScope) : VoyageSessionService {
    private class PendingCommand(val action: VoyageAction)
    private val guard = Any()
    private var pending: PendingCommand? = null
    private val stateValue = MutableStateFlow(VoyageSessionState())
    override val state = stateValue.asStateFlow()
    private val eventQueue = Channel<VoyageCommandEvent>(Channel.UNLIMITED)
    override val events = eventQueue.receiveAsFlow()
    init { scope.launch { services.state.collect(::publish) } }

    private fun publish(value: MainUiState) = synchronized(guard) {
        val trip = value.activeTrip
        stateValue.value = VoyageSessionState(trip?.id, when {
            pending?.action == VoyageAction.START -> VoyagePhase.STARTING
            pending?.action == VoyageAction.FINISH -> VoyagePhase.SAVING
            trip == null -> VoyagePhase.IDLE
            trip.paused -> VoyagePhase.PAUSED
            else -> VoyagePhase.RECORDING
        }, trip?.name.orEmpty(), trip?.distanceMeters ?: 0.0, trip?.startedAt, trip?.pausedAt,
            trip?.accumulatedPausedMillis ?: 0L, trip?.waypointCount ?: 0, pending != null)
    }
    override fun start(name: String, motion: Boolean) = synchronized(guard) {
        if (services.state.value.activeTrip != null || pending != null) return@synchronized
        val source = services.state.value.settings.gpsDataSource
        if (source !in listOf(GpsDataSource.SYSTEM, GpsDataSource.NMEA)) {
            eventQueue.trySend(VoyageCommandEvent(VoyageAction.START, VoyageCommandStatus.POSITION_REQUIRED)); return@synchronized
        }
        command(VoyageAction.START, { services.voyages.startTrip(name, motion, if(source == GpsDataSource.NMEA) VesselSourcePreference.BOAT else VesselSourcePreference.PHONE) }, { it.activeTrip != null })
    }
    override fun pause() = synchronized(guard) {
        val id = services.state.value.activeTrip?.takeIf { !it.paused }?.id ?: return@synchronized
        command(VoyageAction.PAUSE, { services.voyages.pauseTrip() }, { it.activeTrip?.let { trip -> trip.id == id && trip.paused } == true })
    }
    override fun resume() = synchronized(guard) {
        val id = services.state.value.activeTrip?.takeIf { it.paused }?.id ?: return@synchronized
        command(VoyageAction.RESUME, { services.voyages.resumeTrip() }, { it.activeTrip?.let { trip -> trip.id == id && !trip.paused } == true })
    }
    override fun finish() = synchronized(guard) {
        val id = services.state.value.activeTrip?.id ?: return@synchronized
        command(VoyageAction.FINISH, { services.voyages.endTrip() }, { it.tripSessions.any { trip -> trip.id == id && !trip.active && trip.endedAt != null } })
    }
    private fun command(action: VoyageAction, send: () -> Unit, acknowledged: (MainUiState) -> Boolean) {
        if (pending != null) return
        if (!scope.isActive) {
            eventQueue.trySend(VoyageCommandEvent(action, VoyageCommandStatus.FAILED))
            return
        }
        val request = PendingCommand(action)
        pending = request; publish(services.state.value)
        val execution = scope.launch {
            try {
                send()
                val confirmed = withTimeoutOrNull(18_000) { services.state.first(acknowledged) } != null
                eventQueue.send(VoyageCommandEvent(action, if(confirmed) VoyageCommandStatus.CONFIRMED else VoyageCommandStatus.NOT_CONFIRMED))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { eventQueue.send(VoyageCommandEvent(action, VoyageCommandStatus.FAILED)) }
            finally { complete(request) }
        }
        // 若系统 scope 在 launch 分派前关闭，协程体/finally 不会执行。仍须撤销本次 pending；
        // 用请求身份判断，旧协程的完成回调不能清除随后另一次命令。
        execution.invokeOnCompletion { complete(request) }
    }

    private fun complete(request: PendingCommand) = synchronized(guard) {
        if (pending !== request) return@synchronized
        pending = null
        publish(services.state.value)
    }
}
