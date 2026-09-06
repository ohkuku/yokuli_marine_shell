package com.yokuli.marine.feature.nmeainput

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeCommandResult
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineFeatureLinks
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Serializes Feature commands before they cross the process-owned runtime port.
 *
 * Form/navigation actions reduce synchronously so Back and validation are deterministic. Socket,
 * persistence and receiving truth are never owned or predicted here; they only arrive through
 * [NmeaInputRuntimePort.state].
 */
class NmeaInputCoordinator(
    private val runtimePort: NmeaInputRuntimePort,
    scope: CoroutineScope,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val newConnectionId: () -> ConnectionId = {
        ConnectionId(UUID.randomUUID().toString())
    },
) {
    private data class RuntimeRequest(
        val command: NmeaRuntimeCommand,
        val successNotice: NmeaInputNoticeUi,
    )

    private val lock = Any()
    private val requests = Channel<RuntimeRequest>(capacity = MAX_PENDING_ACTIONS)
    private val effectChannel = Channel<NmeaInputEffect>(capacity = MAX_PENDING_EFFECTS)
    val effects: Flow<NmeaInputEffect> = effectChannel.receiveAsFlow()
    private var runtime = runtimePort.state.value
    private var local: NmeaInputLocalState = NmeaInputLocalState.Overview
    private var notice: NmeaInputNoticeUi? = null
    private val mutableState = MutableStateFlow(projectLocked())
    val state: StateFlow<NmeaInputUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            runtimePort.state.collect { snapshot ->
                synchronized(lock) {
                    runtime = snapshot
                    publishLocked()
                }
            }
        }
        scope.launch {
            for (request in requests) execute(request)
        }
    }

    fun dispatch(action: NmeaInputUiAction) {
        if (action is NmeaInputUiAction.ViewReceivedData) {
            synchronized(lock) {
                if (effectChannel.trySend(
                        NmeaInputEffect.OpenDataSources(
                            MarineFeatureLinks.dataSourcesForConnection(action.id),
                        ),
                    ).isFailure
                ) {
                    notice = NmeaInputNoticeUi.ActionQueueFull
                    publishLocked()
                }
            }
            return
        }
        val request = synchronized(lock) {
            if (action == NmeaInputUiAction.DismissNotice) notice = null
            val reduction = NmeaInputLocalReducer.reduce(local, action, runtime, newConnectionId)
            local = reduction.localState
            publishLocked()
            reduction.command?.let { command ->
                RuntimeRequest(command, requireNotNull(reduction.successNotice))
            }
        }
        if (request != null && requests.trySend(request).isFailure) {
            synchronized(lock) {
                local = local.withSubmitting(false)
                notice = NmeaInputNoticeUi.ActionQueueFull
                publishLocked()
            }
        }
    }

    /** Returns false only on the overview so the in-app Shell can consume Back. */
    fun handleBack(): Boolean = synchronized(lock) {
        val action = NmeaInputBackPolicy.actionFor(local) ?: return false
        local = NmeaInputLocalReducer.reduce(local, action, runtime, newConnectionId).localState
        publishLocked()
        true
    }

    private suspend fun execute(request: RuntimeRequest) {
        val result = try {
            runtimePort.execute(request.command)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            synchronized(lock) {
                local = local.withSubmitting(false)
                notice = NmeaInputNoticeUi.OperationFailed(NmeaInputFailureUi.InternalError)
                publishLocked()
            }
            return
        }
        synchronized(lock) {
            local = local.withSubmitting(false)
            val failure = (result as? NmeaRuntimeCommandResult.Rejected)?.failure
            val duplicate = failure as? NmeaRuntimeFailure.DuplicateTcpEndpoint
            val editor = local as? NmeaInputLocalState.Editor
            if (duplicate != null && editor != null) {
                local = NmeaInputLocalState.DuplicateTcpConfirmation(
                    draft = editor.draft,
                    startAfterSave = request.command is NmeaRuntimeCommand.SaveAndStart,
                    existingConnectionId = duplicate.existingId,
                )
                notice = null
                publishLocked()
                return
            }
            notice = when (result) {
                is NmeaRuntimeCommandResult.Success -> {
                    result.connectionId?.let { connectionId ->
                        if (request.command is NmeaRuntimeCommand.Save ||
                            request.command is NmeaRuntimeCommand.SaveAndStart
                        ) {
                            local = NmeaInputLocalState.Detail(connectionId)
                        }
                    }
                    request.successNotice
                }
                is NmeaRuntimeCommandResult.Rejected -> {
                    NmeaInputNoticeUi.OperationFailed(result.failure.toUiFailure())
                }
            }
            publishLocked()
        }
    }

    private fun publishLocked() {
        mutableState.value = projectLocked()
    }

    private fun projectLocked(): NmeaInputUiState = NmeaInputProjector.project(
        snapshot = runtime,
        localState = local,
        nowMillis = nowMillis(),
    ).copy(notice = notice)

    private companion object {
        const val MAX_PENDING_ACTIONS = 32
        const val MAX_PENDING_EFFECTS = 16
    }
}

private fun NmeaInputLocalState.withSubmitting(submitting: Boolean): NmeaInputLocalState = when (this) {
    is NmeaInputLocalState.Editor -> copy(draft = draft.copy(submitting = submitting))
    is NmeaInputLocalState.ReplacementConfirmation -> copy(
        draft = draft.copy(submitting = submitting),
    )
    is NmeaInputLocalState.DuplicateTcpConfirmation -> copy(
        draft = draft.copy(submitting = submitting),
    )
    else -> this
}
