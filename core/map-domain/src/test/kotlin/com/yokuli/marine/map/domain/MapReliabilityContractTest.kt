package com.yokuli.marine.map.domain

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapReliabilityContractTest {
    private val first = GeoPoint(-36.8, 174.7)
    private val second = GeoPoint(-36.7, 174.8)

    @Test
    fun `read failure keeps the user library read only and camera writes cannot replace it`() = runBlocking {
        val persistence = RecordingPersistence(MapLoadResult.ReadFailed(MapReadFailure.IO))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = DefaultMapStore(MapState(), scope, persistence)

        withTimeout(2_000) { store.state.first { it.libraryLoadState == MapLibraryLoadState.READ_FAILED } }
        store.dispatch(MapAction.CameraChanged(MapCamera(first, 8.0)))
        store.dispatch(MapAction.SelectTool(MapTool.MANUAL_ROUTE))
        store.dispatch(MapAction.AddPoint(first))
        withTimeout(2_000) { store.state.first { it.camera.center == first } }

        assertTrue(persistence.libraryWrites.isEmpty())
        assertEquals(MapLibraryLoadState.READ_FAILED, store.state.value.libraryLoadState)
        assertTrue(store.state.value.routeDrafts.isEmpty())
        store.close()
        scope.cancel()
    }

    @Test
    fun `slow durable write does not block map interaction and an old ack cannot mark a newer edit saved`() = runBlocking {
        val firstWrite = CompletableDeferred<Unit>()
        val persistence = RecordingPersistence(MapLoadResult.Ready(MapSessionSnapshot(), MapLibrarySnapshot()))
        persistence.beforeLibraryWrite = { revision -> if (revision == 1L) firstWrite.await() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reducer = DefaultMapReducer(SequenceMapIdGenerator("draft-a", "point-a", "point-b"))
        val store = DefaultMapStore(MapState(), scope, persistence, reducer = reducer)
        withTimeout(2_000) { store.state.first { it.libraryLoadState == MapLibraryLoadState.READY_EMPTY } }

        store.dispatch(MapAction.SelectTool(MapTool.MANUAL_ROUTE))
        store.dispatch(MapAction.AddPoint(first))
        withTimeout(2_000) { store.state.first { it.libraryRevision == 1L } }
        store.dispatch(MapAction.AddPoint(second))
        store.dispatch(MapAction.SelectTool(MapTool.BROWSE))
        withTimeout(2_000) { store.state.first { it.tool == MapTool.BROWSE && it.libraryRevision == 2L } }

        assertEquals(MapSaveState.PENDING, store.state.value.saveState)
        firstWrite.complete(Unit)
        withTimeout(2_000) { store.state.first { it.durableLibraryRevision == 2L } }
        assertEquals(MapSaveState.SAVED, store.state.value.saveState)
        store.close()
        scope.cancel()
    }

    @Test
    fun `failed write preserves pending edits and retry commits the same id and revision`() = runBlocking {
        val persistence = RecordingPersistence(MapLoadResult.Ready(MapSessionSnapshot(), MapLibrarySnapshot()))
        persistence.libraryFailure = IOException("precise path and coordinates must not escape")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = DefaultMapStore(
            MapState(),
            scope,
            persistence,
            reducer = DefaultMapReducer(SequenceMapIdGenerator("draft-stable", "point-stable")),
        )
        withTimeout(2_000) { store.state.first { it.libraryLoadState == MapLibraryLoadState.READY_EMPTY } }
        store.dispatch(MapAction.SelectTool(MapTool.MANUAL_ROUTE))
        store.dispatch(MapAction.AddPoint(first))
        val failed = withTimeout(2_000) { store.state.first { it.saveState == MapSaveState.FAILED } }
        val draft = failed.routeDrafts.single()

        persistence.libraryFailure = null
        store.dispatch(MapAction.RetryPersistence)
        val saved = withTimeout(2_000) { store.state.first { it.saveState == MapSaveState.SAVED } }

        assertEquals(draft.id, saved.routeDrafts.single().id)
        assertEquals(draft.revision, saved.routeDrafts.single().revision)
        assertEquals(saved.libraryRevision, saved.durableLibraryRevision)
        store.close()
        scope.cancel()
    }

    @Test
    fun `closed and saturated stores reject without throwing and camera input is coalesced`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val persistence = RecordingPersistence(MapLoadResult.Ready(MapSessionSnapshot(), MapLibrarySnapshot()))
        persistence.beforeLoad = { gate.await() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = DefaultMapStore(MapState(), scope, persistence, maxPendingActions = 4)

        var last: MapDispatchResult = MapDispatchResult.ACCEPTED
        repeat(100) { index ->
            last = store.dispatch(MapAction.CameraChanged(MapCamera(GeoPoint(0.0, index.toDouble()), 4.0)))
        }
        assertEquals(MapDispatchResult.COALESCED, last)
        gate.complete(Unit)
        withTimeout(2_000) { store.state.first { it.camera.center.longitude == 99.0 } }
        store.close()
        assertEquals(MapDispatchResult.REJECTED_CLOSED, store.dispatch(MapAction.SelectTool(MapTool.BROWSE)))
        scope.cancel()
    }

    @Test
    fun `effect logger failure never kills the next action`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = AtomicInteger()
        val store = DefaultMapStore(
            MapState(),
            scope,
            RecordingPersistence(MapLoadResult.Ready(MapSessionSnapshot(), MapLibrarySnapshot())),
            effectHandler = {
                if (calls.incrementAndGet() == 1) error("logger failed")
            },
        )
        withTimeout(2_000) { store.state.first { it.libraryLoadState == MapLibraryLoadState.READY_EMPTY } }
        store.dispatch(MapAction.SetPlannedSpeedKnots(0.0))
        store.dispatch(MapAction.CameraChanged(MapCamera(second, 7.0)))

        withTimeout(2_000) { store.state.first { it.camera.center == second } }
        withTimeout(2_000) {
            while (calls.get() < 2) delay(10)
        }
        store.close()
        scope.cancel()
    }

    @Test
    fun `one malformed action cannot kill the serialized consumer`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = DefaultMapStore(
            MapState(),
            scope,
            RecordingPersistence(MapLoadResult.Ready(MapSessionSnapshot(), MapLibrarySnapshot())),
            reducer = DefaultMapReducer(MapIdGenerator { error("broken id provider") }),
        )
        withTimeout(2_000) { store.state.first { it.libraryLoadState == MapLibraryLoadState.READY_EMPTY } }

        store.dispatch(MapAction.SelectTool(MapTool.MANUAL_ROUTE))
        store.dispatch(MapAction.AddPoint(first))
        store.dispatch(MapAction.CameraChanged(MapCamera(second, 7.0)))

        withTimeout(2_000) { store.state.first { it.camera.center == second } }
        assertTrue(store.state.value.routeDrafts.isEmpty())
        store.close()
        scope.cancel()
    }

    @Test
    fun `reliable queue pressure is typed and bounded instead of throwing`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val persistence = RecordingPersistence(MapLoadResult.Ready(MapSessionSnapshot(), MapLibrarySnapshot()))
        persistence.beforeLoad = { gate.await() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val incidents = AtomicInteger()
        val store = DefaultMapStore(
            MapState(),
            scope,
            persistence,
            maxPendingActions = 4,
            effectHandler = { if (it == MapEffect.LogIncident(MapIncident.QueueBackpressure)) incidents.incrementAndGet() },
        )

        val accepted = MapTool.entries.map(MapAction::SelectTool) +
            MapAction.OpenSurface(MapSurface.ChartPackages)
        accepted.forEach { assertEquals(MapDispatchResult.ACCEPTED, store.dispatch(it)) }
        assertEquals(
            MapDispatchResult.REJECTED_BACKPRESSURE,
            store.dispatch(MapAction.OpenSurface(MapSurface.Places)),
        )
        withTimeout(2_000) {
            while (incidents.get() == 0) delay(10)
        }

        gate.complete(Unit)
        store.close()
        scope.cancel()
    }

    @Test
    fun `cancellation is not converted into a failed save or late success`() = runBlocking {
        val persistence = RecordingPersistence(MapLoadResult.Ready(MapSessionSnapshot(), MapLibrarySnapshot()))
        persistence.libraryFailure = CancellationException("host stopped")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = DefaultMapStore(
            MapState(),
            scope,
            persistence,
            reducer = DefaultMapReducer(SequenceMapIdGenerator("draft-cancelled")),
        )
        withTimeout(2_000) { store.state.first { it.libraryLoadState == MapLibraryLoadState.READY_EMPTY } }

        store.dispatch(MapAction.SelectTool(MapTool.MANUAL_ROUTE))
        store.dispatch(MapAction.AddPoint(first))
        withTimeout(2_000) { store.state.first { it.saveState == MapSaveState.PENDING } }
        delay(100)

        assertEquals(MapSaveState.PENDING, store.state.value.saveState)
        assertEquals(0L, store.state.value.durableLibraryRevision)
        store.close()
        scope.cancel()
    }

    private class RecordingPersistence(private val loadResult: MapLoadResult) : MapPersistencePort {
        val libraryWrites = mutableListOf<MapLibrarySnapshot>()
        var beforeLoad: suspend () -> Unit = {}
        var beforeLibraryWrite: suspend (Long) -> Unit = {}
        var libraryFailure: Throwable? = null

        override suspend fun load(): MapLoadResult {
            beforeLoad()
            return loadResult
        }

        override suspend fun saveSession(snapshot: MapSessionSnapshot) = Unit

        override suspend fun saveLibrary(snapshot: MapLibrarySnapshot): MapPersistenceAck {
            beforeLibraryWrite(snapshot.revision)
            libraryFailure?.let { throw it }
            libraryWrites += snapshot
            return MapPersistenceAck(snapshot.revision)
        }
    }

    private class SequenceMapIdGenerator(vararg ids: String) : MapIdGenerator {
        private val remaining = ArrayDeque(ids.toList())
        override fun nextId(namespace: String): String = remaining.removeFirst()
    }
}
