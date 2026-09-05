package com.yokuli.marine.map.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class MapStoreTest {
    @Test
    fun `dispatch serializes rapid route edits`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val persisted = CompletableDeferred<MapPersistedState>()
        val store = DefaultMapStore(
            initialState = MapState(),
            scope = scope,
            effectHandler = { effect ->
                if (effect is MapEffect.Persist && effect.snapshot.routeDraft?.waypoints?.size == 3) {
                    persisted.complete(effect.snapshot)
                }
            },
        )

        store.dispatch(MapAction.SelectTool(MapTool.MANUAL_ROUTE))
        store.dispatch(MapAction.AddPoint(GeoPoint(-36.8, 174.7)))
        store.dispatch(MapAction.AddPoint(GeoPoint(-36.7, 174.8)))
        store.dispatch(MapAction.AddPoint(GeoPoint(-36.6, 174.9)))

        withTimeout(2_000L) { persisted.await() }
        val state = withTimeout(2_000L) { store.state.first { it.routeDraft?.waypoints?.size == 3 } }
        assertEquals(listOf(-36.8, -36.7, -36.6), state.routeDraft?.waypoints?.map { it.latitude })
        store.close()
        scope.cancel()
    }
}
