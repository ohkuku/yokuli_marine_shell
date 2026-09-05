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
import org.junit.Assert.assertTrue
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

    @Test
    fun `restore is an ordering barrier before queued user actions`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val saved = MapState(camera = MapCamera(GeoPoint(1.0, 2.0), 5.0)).persisted()
        val persistence = object : MapPersistencePort {
            override suspend fun load(): MapPersistedState = saved
            override suspend fun save(snapshot: MapPersistedState) = Unit
        }
        val store = DefaultMapStore(MapState(), scope, persistence)

        store.dispatch(MapAction.CameraChanged(MapCamera(GeoPoint(3.0, 4.0), 6.0)))
        val state = withTimeout(2_000L) { store.state.first { it.camera.center == GeoPoint(3.0, 4.0) } }

        assertEquals(GeoPoint(3.0, 4.0), state.camera.center)
        store.close()
        scope.cancel()
    }

    @Test
    fun `persistence failure is an effect and never crashes action processing`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val failure = CompletableDeferred<MapEffect.LogIncident>()
        val persistence = object : MapPersistencePort {
            override suspend fun load(): MapPersistedState? = null
            override suspend fun save(snapshot: MapPersistedState): Unit = error("disk unavailable")
        }
        val store = DefaultMapStore(MapState(), scope, persistence) { effect ->
            if (effect is MapEffect.LogIncident) failure.complete(effect)
        }

        store.dispatch(MapAction.CameraChanged(MapCamera(GeoPoint(3.0, 4.0), 6.0)))
        val incident = withTimeout(2_000L) { failure.await() }

        assertEquals(GeoPoint(3.0, 4.0), store.state.value.camera.center)
        assertTrue(incident.incident is MapIncident.PersistenceFailure)
        store.close()
        scope.cancel()
    }
}
