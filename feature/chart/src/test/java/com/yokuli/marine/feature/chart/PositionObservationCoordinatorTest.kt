package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapDispatchResult
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapStore
import com.yokuli.marine.map.domain.MonotonicTime
import com.yokuli.marine.map.domain.NoSourcePositionPort
import com.yokuli.marine.map.domain.ObservationMonotonicClock
import com.yokuli.marine.map.domain.ObservationSource
import com.yokuli.marine.map.domain.PositionPortEvent
import com.yokuli.marine.map.domain.ReadOnlyPositionPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionObservationCoordinatorTest {
    @Test
    fun `production NoSource starts no collector and no freshness timer`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = RecordingMapStore()
        val coordinator = PositionObservationCoordinator(
            NoSourcePositionPort,
            store,
            scope,
            ObservationMonotonicClock { MonotonicTime("boot", 1L) },
        )

        assertTrue(coordinator.isIdleNoSource)
        assertTrue(store.actions.isEmpty())
        coordinator.close()
        scope.cancel()
    }

    @Test
    fun `read only port events enter the one serialized MapStore action boundary`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val events = MutableSharedFlow<PositionPortEvent>(extraBufferCapacity = 1)
        val port = object : ReadOnlyPositionPort { override val events: Flow<PositionPortEvent> = events }
        val store = RecordingMapStore()
        val source = ObservationSource("future-provider", "epoch")
        val coordinator = PositionObservationCoordinator(
            port,
            store,
            scope,
            ObservationMonotonicClock { MonotonicTime("boot", 2L) },
            freshnessTickMillis = Long.MAX_VALUE,
        )

        assertTrue(events.tryEmit(PositionPortEvent.SourceConnected(source)))
        assertEquals(listOf(MapAction.PositionSourceConnected(source)), store.actions)
        coordinator.close()
        scope.cancel()
    }

    private class RecordingMapStore : MapStore {
        override val state: StateFlow<MapState> = MutableStateFlow(MapState())
        val actions = mutableListOf<MapAction>()
        override fun dispatch(action: MapAction): MapDispatchResult {
            actions += action
            return MapDispatchResult.ACCEPTED
        }
        override fun close() = Unit
    }
}
