package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.DefaultMapReducer
import com.yokuli.marine.map.domain.GpxImportRecord
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapDispatchResult
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapSaveState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapStore
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxImportCoordinatorTest {
    @Test
    fun `inspection is non mutating and confirm publishes success only after durable ack`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = RecordingMapStore(autoAck = false)
        val coordinator = coordinator(store, scope)

        coordinator.inspectDocument("content://fixture")
        val preview = withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Preview } }
        assertTrue(preview is GpxImportUiState.Preview)
        assertTrue(store.state.value.places.isEmpty())

        coordinator.dispatch(GpxImportUiAction.ConfirmImport)
        withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Writing } }
        assertEquals(MapSaveState.PENDING, store.state.value.saveState)
        store.ack()
        val completed = withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Succeeded } }
            as GpxImportUiState.Succeeded
        assertEquals(1, completed.placeCount)
        assertEquals(1, completed.routeCount)
        assertEquals(1, completed.trackCount)
        scope.cancel()
    }

    @Test
    fun `known digest exposes duplicate and refuses ordinary confirm until import as copy`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bytes = GPX.toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val store = RecordingMapStore(
            initial = MapState(
                libraryLoadState = MapLibraryLoadState.READY,
                gpxImportRecords = listOf(GpxImportRecord("existing", digest, 1L)),
            ),
        )
        val coordinator = coordinator(store, scope)

        coordinator.inspectDocument("content://fixture")
        val preview = withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Preview } }
            as GpxImportUiState.Preview
        assertTrue(preview.preview.duplicate)
        coordinator.dispatch(GpxImportUiAction.ConfirmImport)
        assertTrue(coordinator.state.value is GpxImportUiState.Preview)
        assertEquals(0, store.importDispatches)

        coordinator.dispatch(GpxImportUiAction.ImportAsCopy)
        withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Succeeded } }
        assertEquals(1, store.importDispatches)
        scope.cancel()
    }

    @Test
    fun `empty selection cannot create an empty import transaction`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = RecordingMapStore()
        val coordinator = coordinator(store, scope)
        coordinator.inspectDocument("content://fixture")
        withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Preview } }
        coordinator.dispatch(GpxImportUiAction.ToggleWaypoint(0))
        coordinator.dispatch(GpxImportUiAction.ToggleRoute(0))
        coordinator.dispatch(GpxImportUiAction.ToggleTrack(0))
        coordinator.dispatch(GpxImportUiAction.ConfirmImport)

        assertEquals(GpxImportFailure.EMPTY_SELECTION, (coordinator.state.value as GpxImportUiState.Failed).reason)
        assertEquals(0, store.importDispatches)
        scope.cancel()
    }

    private fun coordinator(store: MapStore, scope: CoroutineScope) = GpxImportCoordinator(
        documentSource = GpxDocumentSource { ByteArrayInputStream(GPX.toByteArray()) },
        mapStore = store,
        scope = scope,
        idGenerator = com.yokuli.marine.map.domain.MapIdGenerator { namespace -> "$namespace-id" },
        clock = com.yokuli.marine.map.domain.MapClock { 10L },
    )

    private class RecordingMapStore(
        initial: MapState = MapState(libraryLoadState = MapLibraryLoadState.READY_EMPTY),
        private val autoAck: Boolean = true,
    ) : MapStore {
        private val reducer = DefaultMapReducer()
        private val mutable = MutableStateFlow(initial)
        override val state: StateFlow<MapState> = mutable
        var importDispatches = 0

        override fun dispatch(action: MapAction): MapDispatchResult {
            if (action is MapAction.ImportGpxBatch) importDispatches += 1
            val reduction = reducer.reduce(mutable.value, action)
            mutable.value = reduction.state
            if (action is MapAction.ImportGpxBatch && autoAck) ack()
            return MapDispatchResult.ACCEPTED
        }

        fun ack() {
            val revision = mutable.value.libraryRevision
            mutable.value = reducer.reduce(mutable.value, MapAction.PersistenceAck(revision)).state
        }

        override fun close() = Unit
    }

    private companion object {
        const val GPX = """<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
            <wpt lat="-36.8" lon="174.7"><name>Place</name></wpt>
            <rte><name>Route</name><rtept lat="-36.8" lon="174.7"/><rtept lat="-36.9" lon="174.8"/></rte>
            <trk><name>Track</name><trkseg><trkpt lat="-36.8" lon="174.7"/></trkseg></trk>
        </gpx>"""
    }
}
