package com.yokuli.marine.feature.chart

import com.yokuli.marine.navigation.domain.GpxImportReceipt
import com.yokuli.marine.navigation.domain.NavigationChangeResult
import com.yokuli.marine.navigation.domain.NavigationLibrary
import com.yokuli.marine.navigation.domain.NavigationLibraryChange
import com.yokuli.marine.navigation.domain.NavigationLibraryCommitResult
import com.yokuli.marine.navigation.domain.NavigationLibraryEditor
import com.yokuli.marine.navigation.domain.NavigationLibraryFailure
import com.yokuli.marine.navigation.domain.NavigationLibraryLoadResult
import com.yokuli.marine.navigation.domain.NavigationLibraryPort
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.yield
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
        val store = RecordingNavigationLibrary(autoAck = false)
        val coordinator = coordinator(store, scope)

        coordinator.inspectDocument("content://fixture")
        val preview = withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Preview } }
        assertTrue(preview is GpxImportUiState.Preview)
        assertTrue(store.value.waypoints.isEmpty())

        coordinator.dispatch(GpxImportUiAction.ConfirmImport)
        withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Writing } }
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
        val store = RecordingNavigationLibrary(
            initial = NavigationLibrary(gpxImports = listOf(GpxImportReceipt("existing", digest, 1L))),
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
        val store = RecordingNavigationLibrary()
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

    @Test
    fun `cancelled preview never dispatches a library mutation`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = RecordingNavigationLibrary()
        val coordinator = coordinator(store, scope)
        coordinator.inspectDocument("content://fixture")
        withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Preview } }

        coordinator.dispatch(GpxImportUiAction.Cancel)

        assertTrue(coordinator.state.value is GpxImportUiState.Cancelled)
        assertEquals(0, store.importDispatches)
        assertTrue(store.value.waypoints.isEmpty())
        scope.cancel()
    }

    @Test
    fun `persistence failure remains visible and never becomes success`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = RecordingNavigationLibrary(autoAck = false)
        val coordinator = coordinator(store, scope)
        coordinator.inspectDocument("content://fixture")
        withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Preview } }
        coordinator.dispatch(GpxImportUiAction.ConfirmImport)
        withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Writing } }

        store.failWrite()

        val failed = withTimeout(2_000L) { coordinator.state.first { it is GpxImportUiState.Failed } }
            as GpxImportUiState.Failed
        assertEquals(GpxImportFailure.WRITE_FAILED, failed.reason)
        scope.cancel()
    }

    private fun coordinator(store: NavigationLibraryPort, scope: CoroutineScope) = GpxImportCoordinator(
        documentSource = GpxDocumentSource { ByteArrayInputStream(GPX.toByteArray()) },
        navigationLibrary = store,
        scope = scope,
        idGenerator = com.yokuli.marine.map.domain.MapIdGenerator { namespace -> "$namespace-id" },
        clock = com.yokuli.marine.map.domain.MapClock { 10L },
    )

    private class RecordingNavigationLibrary(
        initial: NavigationLibrary = NavigationLibrary(),
        private val autoAck: Boolean = true,
    ) : NavigationLibraryPort {
        var value = initial
            private set
        var importDispatches = 0
        private var pending: Pending? = null

        override suspend fun loadNavigationLibrary() = NavigationLibraryLoadResult.Ready(value)

        override suspend fun commitNavigationChange(
            expectedLibraryRevision: Long,
            change: NavigationLibraryChange,
        ): NavigationLibraryCommitResult {
            if (change is NavigationLibraryChange.ImportGpx) importDispatches += 1
            if (autoAck) return apply(expectedLibraryRevision, change)
            val wait = CompletableDeferred<NavigationLibraryCommitResult>()
            pending = Pending(expectedLibraryRevision, change, wait)
            return wait.await()
        }

        suspend fun ack() {
            while (pending == null) yield()
            val request = requireNotNull(pending).also { pending = null }
            request.reply.complete(apply(request.expectedRevision, request.change))
        }

        suspend fun failWrite() {
            while (pending == null) yield()
            val request = requireNotNull(pending).also { pending = null }
            request.reply.complete(NavigationLibraryCommitResult.Failed(NavigationLibraryFailure.IO))
        }

        private fun apply(expectedRevision: Long, change: NavigationLibraryChange): NavigationLibraryCommitResult {
            if (expectedRevision != value.revision) return NavigationLibraryCommitResult.Conflict(value.revision)
            return when (val result = NavigationLibraryEditor.apply(value, change)) {
                is NavigationChangeResult.Applied -> {
                    value = result.library
                    NavigationLibraryCommitResult.Committed(value.revision)
                }
                is NavigationChangeResult.Rejected -> NavigationLibraryCommitResult.Rejected(result.reason)
            }
        }

        private data class Pending(
            val expectedRevision: Long,
            val change: NavigationLibraryChange,
            val reply: CompletableDeferred<NavigationLibraryCommitResult>,
        )
    }

    private companion object {
        const val GPX = """<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
            <wpt lat="-36.8" lon="174.7"><name>Place</name></wpt>
            <rte><name>Route</name><rtept lat="-36.8" lon="174.7"/><rtept lat="-36.9" lon="174.8"/></rte>
            <trk><name>Track</name><trkseg><trkpt lat="-36.8" lon="174.7"/></trkseg></trk>
        </gpx>"""
    }
}
