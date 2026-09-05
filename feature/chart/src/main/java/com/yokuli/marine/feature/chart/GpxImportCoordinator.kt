package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.GpxDuplicateDecision
import com.yokuli.marine.map.domain.GpxImportBatch
import com.yokuli.marine.map.domain.GpxImportPlanner
import com.yokuli.marine.map.domain.GpxImportPreview
import com.yokuli.marine.map.domain.GpxImportSelection
import com.yokuli.marine.map.domain.GpxReader
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapClock
import com.yokuli.marine.map.domain.MapDispatchResult
import com.yokuli.marine.map.domain.MapIdGenerator
import com.yokuli.marine.map.domain.MapSaveState
import com.yokuli.marine.map.domain.MapStore
import com.yokuli.marine.map.domain.RandomMapIdGenerator
import com.yokuli.marine.map.domain.SystemMapClock
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun interface GpxDocumentSource {
    fun open(sourceUri: String): InputStream
}

enum class GpxImportFailure { INVALID_DOCUMENT, EMPTY_SELECTION, DISPATCH_REJECTED, WRITE_FAILED }

sealed interface GpxImportUiState {
    data object Idle : GpxImportUiState
    data class Inspecting(val operationId: String, val generation: Long) : GpxImportUiState
    data class Preview(
        val operationId: String,
        val generation: Long,
        val preview: GpxImportPreview,
        val selection: GpxImportSelection = GpxImportSelection.all(preview),
    ) : GpxImportUiState {
        val canImport: Boolean
            get() = selection.waypointIndices.isNotEmpty() ||
                selection.routeIndices.any { preview.routes[it].points.size >= 2 } ||
                selection.trackIndices.isNotEmpty()
    }
    data class Writing(val operationId: String, val generation: Long, val targetRevision: Long) : GpxImportUiState
    data class Succeeded(
        val placeCount: Int,
        val routeCount: Int,
        val trackCount: Int,
        val durableRevision: Long,
    ) : GpxImportUiState
    data class Cancelled(val operationId: String?, val generation: Long) : GpxImportUiState
    data class Failed(val reason: GpxImportFailure, val generation: Long) : GpxImportUiState
}

sealed interface GpxImportUiAction {
    data object ChooseDocument : GpxImportUiAction
    data class ToggleWaypoint(val index: Int) : GpxImportUiAction
    data class ToggleRoute(val index: Int) : GpxImportUiAction
    data class ToggleTrack(val index: Int) : GpxImportUiAction
    data object ConfirmImport : GpxImportUiAction
    data object ImportAsCopy : GpxImportUiAction
    data object Cancel : GpxImportUiAction
    data object DismissResult : GpxImportUiAction
}

/**
 * Preview is read-only. Exactly one confirmed batch enters MapStore's serialized queue and success
 * is published only after Room acknowledges the target library revision.
 */
class GpxImportCoordinator(
    private val documentSource: GpxDocumentSource,
    private val mapStore: MapStore,
    private val scope: CoroutineScope,
    private val reader: GpxReader = GpxReader(),
    private val idGenerator: MapIdGenerator = RandomMapIdGenerator,
    private val clock: MapClock = SystemMapClock,
    private val incidentLogger: (Throwable) -> Unit = {},
) {
    private val mutableState = MutableStateFlow<GpxImportUiState>(GpxImportUiState.Idle)
    val state: StateFlow<GpxImportUiState> = mutableState.asStateFlow()
    private var operationGeneration = 0L
    private var activeJob: Job? = null

    fun inspectDocument(sourceUri: String) {
        activeJob?.cancel()
        val generation = ++operationGeneration
        val operationId = "gpx-$generation"
        mutableState.value = GpxImportUiState.Inspecting(operationId, generation)
        activeJob = scope.launch {
            try {
                val digests = mapStore.state.value.gpxImportRecords.mapTo(linkedSetOf()) { it.sha256 }
                val preview = withContext(Dispatchers.IO) {
                    documentSource.open(sourceUri).use { input -> reader.inspect(input, digests) }
                }
                if (generation == operationGeneration) {
                    mutableState.value = GpxImportUiState.Preview(operationId, generation, preview)
                }
            } catch (_: CancellationException) {
                // A superseding document or explicit cancel is an expected transition.
            } catch (error: Throwable) {
                failIfCurrent(generation, GpxImportFailure.INVALID_DOCUMENT, error)
            }
        }
    }

    fun dispatch(action: GpxImportUiAction) {
        when (action) {
            GpxImportUiAction.ChooseDocument -> Unit
            is GpxImportUiAction.ToggleWaypoint -> toggle(action.index, ItemKind.WAYPOINT)
            is GpxImportUiAction.ToggleRoute -> toggle(action.index, ItemKind.ROUTE)
            is GpxImportUiAction.ToggleTrack -> toggle(action.index, ItemKind.TRACK)
            GpxImportUiAction.ConfirmImport -> confirm(GpxDuplicateDecision.NEW_IMPORT)
            GpxImportUiAction.ImportAsCopy -> confirm(GpxDuplicateDecision.IMPORT_AS_COPY)
            GpxImportUiAction.Cancel -> cancel()
            GpxImportUiAction.DismissResult -> {
                activeJob?.cancel()
                operationGeneration += 1
                mutableState.value = GpxImportUiState.Idle
            }
        }
    }

    private fun toggle(index: Int, kind: ItemKind) {
        val current = mutableState.value as? GpxImportUiState.Preview ?: return
        val selection = when (kind) {
            ItemKind.WAYPOINT -> current.selection.copy(
                waypointIndices = current.selection.waypointIndices.toggled(index, current.preview.waypoints.indices),
            )
            ItemKind.ROUTE -> current.selection.copy(
                routeIndices = current.selection.routeIndices.toggled(index, current.preview.routes.indices),
            )
            ItemKind.TRACK -> current.selection.copy(
                trackIndices = current.selection.trackIndices.toggled(index, current.preview.tracks.indices),
            )
        }
        mutableState.value = current.copy(selection = selection)
    }

    private fun confirm(decision: GpxDuplicateDecision) {
        val current = mutableState.value as? GpxImportUiState.Preview ?: return
        if (!current.canImport) {
            mutableState.value = GpxImportUiState.Failed(GpxImportFailure.EMPTY_SELECTION, current.generation)
            return
        }
        if (current.preview.duplicate && decision != GpxDuplicateDecision.IMPORT_AS_COPY) return
        activeJob?.cancel()
        val batch = try {
            GpxImportPlanner.materialize(current.preview, decision, idGenerator, clock.nowMillis(), current.selection)
        } catch (error: Throwable) {
            failIfCurrent(current.generation, GpxImportFailure.EMPTY_SELECTION, error)
            return
        }
        val targetRevision = mapStore.state.value.libraryRevision + 1L
        val dispatchResult = mapStore.dispatch(MapAction.ImportGpxBatch(batch))
        if (dispatchResult !in setOf(MapDispatchResult.ACCEPTED, MapDispatchResult.COALESCED)) {
            mutableState.value = GpxImportUiState.Failed(GpxImportFailure.DISPATCH_REJECTED, current.generation)
            return
        }
        mutableState.value = GpxImportUiState.Writing(current.operationId, current.generation, targetRevision)
        activeJob = scope.launch { awaitDurability(current.generation, targetRevision, batch) }
    }

    private suspend fun awaitDurability(generation: Long, targetRevision: Long, batch: GpxImportBatch) {
        try {
            val settled = mapStore.state.first { state ->
                state.durableLibraryRevision >= targetRevision ||
                    (state.libraryRevision >= targetRevision && state.saveState == MapSaveState.FAILED)
            }
            if (generation != operationGeneration) return
            mutableState.value = if (settled.durableLibraryRevision >= targetRevision) {
                GpxImportUiState.Succeeded(
                    batch.places.size,
                    batch.routes.size,
                    batch.tracks.size,
                    settled.durableLibraryRevision,
                )
            } else {
                GpxImportUiState.Failed(GpxImportFailure.WRITE_FAILED, generation)
            }
        } catch (_: CancellationException) {
            // Superseded work must not overwrite a newer UI state.
        } catch (error: Throwable) {
            failIfCurrent(generation, GpxImportFailure.WRITE_FAILED, error)
        }
    }

    private fun cancel() {
        val operationId = when (val current = mutableState.value) {
            is GpxImportUiState.Inspecting -> current.operationId
            is GpxImportUiState.Preview -> current.operationId
            is GpxImportUiState.Writing -> current.operationId
            else -> null
        }
        activeJob?.cancel()
        mutableState.value = GpxImportUiState.Cancelled(operationId, ++operationGeneration)
    }

    private fun failIfCurrent(generation: Long, failure: GpxImportFailure, error: Throwable) {
        incidentLogger(error)
        if (generation == operationGeneration) mutableState.value = GpxImportUiState.Failed(failure, generation)
    }

    private fun Set<Int>.toggled(index: Int, valid: IntRange): Set<Int> {
        if (index !in valid) return this
        return if (index in this) this - index else this + index
    }

    private enum class ItemKind { WAYPOINT, ROUTE, TRACK }
}
