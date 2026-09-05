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

class DefaultMapStore(
    initialState: MapState,
    scope: CoroutineScope,
    private val effectHandler: suspend (MapEffect) -> Unit = {},
) : MapStore {
    private val actions = Channel<MapAction>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(initialState)
    private val processor: Job = scope.launch {
        for (action in actions) {
            val reduction = MapReducer.reduce(mutableState.value, action)
            mutableState.value = reduction.state
            reduction.effects.forEach { effectHandler(it) }
        }
    }

    override val state: StateFlow<MapState> = mutableState.asStateFlow()

    override fun dispatch(action: MapAction) {
        check(actions.trySend(action).isSuccess) { "MapStore is closed" }
    }

    override fun close() {
        actions.close()
        processor.cancel()
    }
}
