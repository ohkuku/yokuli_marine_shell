package com.yokuli.runtime.marine

import com.yokuli.anchorwatch.MainUiState
import com.yokuli.anchorwatch.api.MarineServices
import com.yokuli.runtime.contract.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 窄航行客户端：只投递到唯一运行时，并投影实际执行账本；页面与通知不负责确认业务成败。 */
class VoyageSessionCoordinator internal constructor(private val services: MarineServices, scope: CoroutineScope) : VoyageSessionService {
    private val stateValue = MutableStateFlow(VoyageSessionState())
    override val state = stateValue.asStateFlow()
    override val commands = services.voyages.commandResults
    private val eventQueue = Channel<VoyageCommandEvent>(64, BufferOverflow.DROP_OLDEST)
    override val events = eventQueue.receiveAsFlow()
    private val presented = mutableMapOf<String, VoyageRequestStatus>()
    init {
        scope.launch {
            combine(services.state, commands) { state, receipts -> state to receipts }.collect { (value, receipts) ->
                publish(value, receipts)
                receipts.forEach { receipt ->
                    val request=receipt.request
                    if(presented[request.requestId] != receipt.status) {
                        val status=when(receipt.status) {
                            VoyageRequestStatus.CONFIRMED -> VoyageCommandStatus.CONFIRMED
                            VoyageRequestStatus.UNKNOWN -> VoyageCommandStatus.NOT_CONFIRMED
                            VoyageRequestStatus.REJECTED -> if(receipt.reason=="POSITION_REQUIRED") VoyageCommandStatus.POSITION_REQUIRED else VoyageCommandStatus.FAILED
                            VoyageRequestStatus.FAILED -> VoyageCommandStatus.FAILED
                            else -> null
                        }
                        presented[request.requestId]=receipt.status
                        if(status!=null)eventQueue.trySend(VoyageCommandEvent(request.action,status,request.requestId))
                    }
                }
                presented.keys.retainAll(receipts.map { it.request.requestId }.toSet())
            }
        }
    }

    private fun publish(value: MainUiState, receipts: List<VoyageCommandReceipt>) {
        val pending=receipts.lastOrNull { !it.terminal }
        val executing=pending?.takeUnless { it.status==VoyageRequestStatus.UNKNOWN }?.request
        val trip=value.activeTrip
        stateValue.value=VoyageSessionState(trip?.id,when {
            executing?.action==VoyageAction.START -> VoyagePhase.STARTING
            executing?.action==VoyageAction.FINISH -> VoyagePhase.SAVING
            trip==null -> VoyagePhase.IDLE
            trip.paused -> VoyagePhase.PAUSED
            else -> VoyagePhase.RECORDING
        },trip?.name.orEmpty(),trip?.distanceMeters ?: 0.0,trip?.startedAt,trip?.pausedAt,
            trip?.accumulatedPausedMillis ?: 0L,trip?.waypointCount ?: 0,pending!=null)
    }
    override fun request(command: VoyageRequest): String = services.voyages.requestCommand(command)
    override fun recheck(requestId: String) = services.voyages.recheckCommand(requestId)
    override fun start(name: String, motion: Boolean) { request(VoyageRequest(VoyageAction.START,name=name,motion=motion)) }
    override fun pause() { request(VoyageRequest(VoyageAction.PAUSE,services.state.value.activeTrip?.id)) }
    override fun resume() { request(VoyageRequest(VoyageAction.RESUME,services.state.value.activeTrip?.id)) }
    override fun finish() { request(VoyageRequest(VoyageAction.FINISH,services.state.value.activeTrip?.id)) }
}
