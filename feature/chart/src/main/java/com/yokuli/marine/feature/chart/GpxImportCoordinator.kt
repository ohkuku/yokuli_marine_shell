package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.GpxDuplicateDecision
import com.yokuli.marine.map.domain.GpxImportBatch
import com.yokuli.marine.map.domain.GpxImportPlanner
import com.yokuli.marine.map.domain.GpxImportPreview
import com.yokuli.marine.map.domain.GpxImportSelection
import com.yokuli.marine.map.domain.GpxReader
import com.yokuli.marine.map.domain.MapClock
import com.yokuli.marine.map.domain.MapIdGenerator
import com.yokuli.marine.map.domain.RandomMapIdGenerator
import com.yokuli.marine.map.domain.SystemMapClock
import com.yokuli.marine.navigation.domain.GpxImportMode
import com.yokuli.marine.navigation.domain.GpxImportReceipt
import com.yokuli.marine.navigation.domain.NavigationLibraryChange
import com.yokuli.marine.navigation.domain.NavigationLibraryCommitResult
import com.yokuli.marine.navigation.domain.NavigationLibraryLoadResult
import com.yokuli.marine.navigation.domain.NavigationLibraryPort
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.NavigationTrack
import com.yokuli.marine.navigation.domain.NavigationTrackOrigin
import com.yokuli.marine.navigation.domain.NavigationTrackPoint
import com.yokuli.marine.navigation.domain.NavigationTrackSegment
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint
import com.yokuli.marine.navigation.domain.Waypoint
import com.yokuli.marine.navigation.domain.WaypointCategory
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Preview is read-only. Exactly one confirmed batch enters NavigationLibraryPort; Chart never
 * creates a parallel GPX/waypoint/route truth in MapStore.
 */
class GpxImportCoordinator(
    private val documentSource: GpxDocumentSource,
    private val navigationLibrary: NavigationLibraryPort,
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
    private var observedLibraryRevision = 0L

    fun inspectDocument(sourceUri: String) {
        activeJob?.cancel()
        val generation = ++operationGeneration
        val operationId = "gpx-$generation"
        mutableState.value = GpxImportUiState.Inspecting(operationId, generation)
        activeJob = scope.launch {
            try {
                val loaded = navigationLibrary.loadNavigationLibrary()
                if (loaded !is NavigationLibraryLoadResult.Ready) {
                    failIfCurrent(generation, GpxImportFailure.WRITE_FAILED, IllegalStateException("Navigation library unavailable"))
                    return@launch
                }
                observedLibraryRevision = loaded.library.revision
                val digests = loaded.library.gpxImports.mapTo(linkedSetOf()) { it.sha256 }
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
        val targetRevision = observedLibraryRevision + 1L
        mutableState.value = GpxImportUiState.Writing(current.operationId, current.generation, targetRevision)
        activeJob = scope.launch { commit(current.generation, decision, batch) }
    }

    private suspend fun commit(generation: Long, decision: GpxDuplicateDecision, batch: GpxImportBatch) {
        try {
            val result = navigationLibrary.commitNavigationChange(
                expectedLibraryRevision = observedLibraryRevision,
                change = batch.toNavigationChange(decision),
            )
            if (generation != operationGeneration) return
            mutableState.value = if (result is NavigationLibraryCommitResult.Committed) {
                observedLibraryRevision = result.revision
                GpxImportUiState.Succeeded(
                    batch.places.size,
                    batch.routes.size,
                    batch.tracks.size,
                    result.revision,
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
        if (mutableState.value is GpxImportUiState.Writing) return
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

private fun GpxImportBatch.toNavigationChange(decision: GpxDuplicateDecision) = NavigationLibraryChange.ImportGpx(
    waypoints = places.map { place ->
        Waypoint(
            id = place.id,
            revision = place.revision,
            name = place.name,
            position = NavigationPosition(place.point.latitude, place.point.longitude),
            notes = place.notes,
            category = WaypointCategory.valueOf(place.category.name),
            tags = place.tags,
            createdAtMillis = place.createdAtMillis,
            updatedAtMillis = place.updatedAtMillis,
        )
    },
    routePlans = routes.map { route ->
        RoutePlan(
            id = route.id,
            revision = route.revision,
            name = route.name,
            points = route.waypoints.mapIndexed { index, point ->
                RoutePoint(route.waypointIds[index], NavigationPosition(point.latitude, point.longitude))
            },
            plannedSpeedKnots = route.plannedSpeedKnots,
            notes = route.notes,
            sourceDraftId = route.sourceDraftId,
            sourceDraftRevision = route.sourceDraftRevision,
        )
    },
    tracks = tracks.map { track ->
        NavigationTrack(
            id = track.id,
            revision = track.revision,
            name = track.name,
            description = track.description,
            segments = track.segments.map { segment ->
                NavigationTrackSegment(segment.points.map { point ->
                    NavigationTrackPoint(
                        position = NavigationPosition(point.point.latitude, point.point.longitude),
                        elevationMeters = point.elevationMeters,
                        time = point.time,
                        recordedAtEpochMillis = point.recordedAtEpochMillis,
                        sourceId = point.sourceId,
                        speedOverGroundKnots = point.speedOverGroundKnots,
                        courseOverGroundTrueDegrees = point.courseOverGroundTrueDegrees,
                    )
                })
            },
            sourceDigest = track.sourceDigest,
            importedAtMillis = track.importedAtMillis,
            origin = NavigationTrackOrigin.valueOf(track.origin.name),
            startedAtEpochMillis = track.startedAtEpochMillis,
            endedAtEpochMillis = track.endedAtEpochMillis,
            durationMillis = track.durationMillis,
            distanceNauticalMiles = track.distanceNauticalMiles,
            navigationSessionId = track.navigationSessionId,
            routeId = track.routeId,
            routeRevision = track.routeRevision,
        )
    },
    receipt = GpxImportReceipt(importRecord.id, importRecord.sha256, importRecord.importedAtMillis),
    mode = if (decision == GpxDuplicateDecision.IMPORT_AS_COPY) GpxImportMode.IMPORT_AS_COPY else GpxImportMode.NEW_IMPORT,
)
