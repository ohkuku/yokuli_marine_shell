package com.yokuli.marine.map.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface MapStore : AutoCloseable {
    val state: StateFlow<MapState>
    fun dispatch(action: MapAction)
}

interface MapPersistencePort {
    suspend fun load(): MapPersistedState?
    suspend fun save(snapshot: MapPersistedState)
}

class DefaultMapStore(
    initialState: MapState,
    scope: CoroutineScope,
    private val persistence: MapPersistencePort? = null,
    private val effectHandler: suspend (MapEffect) -> Unit = {},
) : MapStore {
    private val actionSignal = Channel<Unit>(Channel.CONFLATED)
    private val actionQueue = ArrayDeque<MapAction>()
    private val actionQueueLock = Any()
    private val mutableState = MutableStateFlow(initialState)
    private val processor: Job = scope.launch {
        restoreBeforeUserActions()
        for (ignored in actionSignal) {
            while (true) {
                val action = synchronized(actionQueueLock) { actionQueue.removeFirstOrNull() } ?: break
                process(action)
            }
        }
    }

    override val state: StateFlow<MapState> = mutableState.asStateFlow()

    override fun dispatch(action: MapAction) {
        synchronized(actionQueueLock) {
            check(actionQueue.size < MAX_PENDING_ACTIONS) { "MapStore action queue capacity exceeded" }
            actionQueue.addLast(action)
        }
        check(actionSignal.trySend(Unit).isSuccess) { "MapStore action queue is closed" }
    }

    override fun close() {
        actionSignal.close()
        processor.cancel()
    }

    private suspend fun restoreBeforeUserActions() {
        val snapshot = try {
            persistence?.load()
        } catch (error: Throwable) {
            effectHandler(
                MapEffect.LogIncident(
                    MapIncident.PersistenceFailure("load", error.message ?: error.javaClass.simpleName),
                ),
            )
            null
        }
        if (snapshot != null) {
            mutableState.value = MapReducer.reduce(mutableState.value, MapAction.Restore(snapshot)).state
        }
    }

    private suspend fun process(action: MapAction) {
        val reduction = MapReducer.reduce(mutableState.value, action)
        mutableState.value = reduction.state
        reduction.effects.forEach { effect ->
            if (effect is MapEffect.Persist) {
                try {
                    persistence?.save(effect.snapshot)
                } catch (error: Throwable) {
                    effectHandler(
                        MapEffect.LogIncident(
                            MapIncident.PersistenceFailure("save", error.message ?: error.javaClass.simpleName),
                        ),
                    )
                }
            }
            effectHandler(effect)
        }
    }

    private companion object {
        const val MAX_PENDING_ACTIONS = 256
    }
}
