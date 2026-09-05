package com.yokuli.marine.map.domain

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface MapStore : AutoCloseable {
    val state: StateFlow<MapState>
    fun dispatch(action: MapAction): MapDispatchResult
}

enum class MapDispatchResult { ACCEPTED, COALESCED, REJECTED_BACKPRESSURE, REJECTED_CLOSED }

interface MapPersistencePort {
    suspend fun load(): MapLoadResult
    suspend fun saveSession(snapshot: MapSessionSnapshot)
    suspend fun saveLibrary(snapshot: MapLibrarySnapshot): MapPersistenceAck
}

class DefaultMapStore(
    initialState: MapState,
    private val scope: CoroutineScope,
    private val persistence: MapPersistencePort? = null,
    private val reducer: DefaultMapReducer = DefaultMapReducer(),
    maxPendingActions: Int = DEFAULT_MAX_PENDING_ACTIONS,
    private val effectHandler: suspend (MapEffect) -> Unit = {},
) : MapStore {
    private val closed = AtomicBoolean(false)
    private val actionSignal = Channel<Unit>(Channel.CONFLATED)
    private val actionMailbox = ActionMailbox(maxPendingActions)
    private val writeSignal = Channel<Unit>(Channel.CONFLATED)
    private val writeMailbox = PersistenceMailbox()
    private val mutableState = MutableStateFlow(initialState.copy(libraryLoadState = MapLibraryLoadState.LOADING))

    private val writer: Job = scope.launch {
        for (ignored in writeSignal) {
            while (true) {
                val request = writeMailbox.take() ?: break
                write(request)
            }
        }
    }

    private val processor: Job = scope.launch {
        restoreBeforeUserActions()
        for (ignored in actionSignal) {
            while (true) {
                val action = actionMailbox.take() ?: break
                process(action)
            }
        }
    }

    override val state: StateFlow<MapState> = mutableState.asStateFlow()

    override fun dispatch(action: MapAction): MapDispatchResult = enqueue(action, internal = false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        actionMailbox.close()
        writeMailbox.close()
        actionSignal.close()
        writeSignal.close()
        processor.cancel()
        writer.cancel()
    }

    private fun enqueue(action: MapAction, internal: Boolean): MapDispatchResult {
        if (closed.get()) return MapDispatchResult.REJECTED_CLOSED
        val result = actionMailbox.offer(action, internal)
        if (result == MapDispatchResult.ACCEPTED || result == MapDispatchResult.COALESCED) {
            actionSignal.trySend(Unit)
        } else if (result == MapDispatchResult.REJECTED_BACKPRESSURE) {
            deliverEffect(MapEffect.LogIncident(MapIncident.QueueBackpressure))
        }
        return result
    }

    private suspend fun restoreBeforeUserActions() {
        val result = loadResult()
        if (!closed.get()) process(MapAction.Restore(result))
    }

    private suspend fun loadResult(): MapLoadResult = try {
        persistence?.load() ?: MapLoadResult.Ready(MapSessionSnapshot(), MapLibrarySnapshot())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        when (classify(error)) {
            MapReadFailure.CORRUPT -> MapLoadResult.Corrupt()
            else -> MapLoadResult.ReadFailed(classify(error))
        }
    }

    private suspend fun process(action: MapAction) {
        val reduction = try {
            reducer.reduce(mutableState.value, action)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            deliverEffect(MapEffect.LogIncident(MapIncident.ActionRejected))
            return
        }
        mutableState.value = reduction.state
        reduction.effects.forEach { effect ->
            when (effect) {
                is MapEffect.PersistSession -> offerWrite(PersistenceRequest.Session(effect.snapshot))
                is MapEffect.PersistLibrary -> offerWrite(PersistenceRequest.Library(effect.snapshot))
                MapEffect.Reload -> scope.launch {
                    val loaded = loadResult()
                    enqueue(MapAction.Restore(loaded), internal = true)
                }
                is MapEffect.LogIncident -> Unit
            }
            deliverEffect(effect)
        }
    }

    private fun offerWrite(request: PersistenceRequest) {
        if (writeMailbox.offer(request)) writeSignal.trySend(Unit)
    }

    private suspend fun write(request: PersistenceRequest) {
        try {
            when (request) {
                is PersistenceRequest.Session -> persistence?.saveSession(request.snapshot)
                is PersistenceRequest.Library -> {
                    val ack = persistence?.saveLibrary(request.snapshot) ?: MapPersistenceAck(request.snapshot.revision)
                    enqueue(MapAction.PersistenceAck(ack.revision), internal = true)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (request is PersistenceRequest.Library) {
                enqueue(MapAction.PersistenceFailed(request.snapshot.revision, classify(error)), internal = true)
            } else {
                deliverEffect(MapEffect.LogIncident(MapIncident.PersistenceFailure("save-session", classify(error))))
            }
        }
    }

    private fun deliverEffect(effect: MapEffect) {
        scope.launch {
            try {
                effectHandler(effect)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // An optional logger/observer is outside the reducer and may never kill either actor.
            }
        }
    }

    private fun classify(error: Throwable): MapReadFailure {
        val type = error.javaClass.simpleName
        val message = error.message.orEmpty()
        return when {
            type.contains("Corrupt", ignoreCase = true) -> MapReadFailure.CORRUPT
            message.contains("schema", ignoreCase = true) && message.contains("unsupported", ignoreCase = true) ->
                MapReadFailure.FUTURE_SCHEMA
            error is java.io.IOException -> MapReadFailure.IO
            else -> MapReadFailure.UNKNOWN
        }
    }

    private sealed interface PersistenceRequest {
        data class Session(val snapshot: MapSessionSnapshot) : PersistenceRequest
        data class Library(val snapshot: MapLibrarySnapshot) : PersistenceRequest
    }

    private class PersistenceMailbox {
        private var session: PersistenceRequest.Session? = null
        private var library: PersistenceRequest.Library? = null
        private var closed = false

        @Synchronized
        fun offer(request: PersistenceRequest): Boolean {
            if (closed) return false
            when (request) {
                is PersistenceRequest.Session -> session = request
                is PersistenceRequest.Library -> library = request
            }
            return true
        }

        @Synchronized
        fun take(): PersistenceRequest? = library.also { library = null } ?: session.also { session = null }

        @Synchronized
        fun close() {
            closed = true
            session = null
            library = null
        }
    }

    private class ActionMailbox(private val capacity: Int) {
        private val actions = ArrayDeque<MapAction>()
        private var closed = false

        init {
            require(capacity > 0)
        }

        @Synchronized
        fun offer(action: MapAction, internal: Boolean): MapDispatchResult {
            if (closed) return MapDispatchResult.REJECTED_CLOSED
            if (action is MapAction.CameraChanged) {
                val removed = actions.removeAll { it is MapAction.CameraChanged }
                if (actions.size >= capacity) {
                    actions.removeFirstOrNull { it is MapAction.PositionClockTick }
                }
                if (actions.size >= capacity) return MapDispatchResult.REJECTED_BACKPRESSURE
                actions.addLast(action)
                return if (removed) MapDispatchResult.COALESCED else MapDispatchResult.ACCEPTED
            }
            if (actions.size >= capacity) {
                actions.removeFirstOrNull { it is MapAction.CameraChanged || it is MapAction.PositionClockTick }
            }
            if (actions.size >= capacity && !internal) return MapDispatchResult.REJECTED_BACKPRESSURE
            actions.addLast(action)
            return MapDispatchResult.ACCEPTED
        }

        @Synchronized
        fun take(): MapAction? = actions.removeFirstOrNull()

        @Synchronized
        fun close() {
            closed = true
            actions.clear()
        }

        private inline fun <T> ArrayDeque<T>.removeFirstOrNull(predicate: (T) -> Boolean): T? {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (predicate(item)) {
                    iterator.remove()
                    return item
                }
            }
            return null
        }
    }

    private companion object {
        const val DEFAULT_MAX_PENDING_ACTIONS = 256
    }
}
