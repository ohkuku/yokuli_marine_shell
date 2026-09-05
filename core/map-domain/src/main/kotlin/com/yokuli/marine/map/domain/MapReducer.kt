package com.yokuli.marine.map.domain

sealed interface MapAction {
    data class Restore(val result: MapLoadResult) : MapAction
    data class CameraChanged(val camera: MapCamera) : MapAction
    data class SelectTool(val tool: MapTool) : MapAction
    data class OpenSurface(val surface: MapSurface) : MapAction
    data object CloseSurface : MapAction
    data object DismissTransient : MapAction
    data object ClearSelection : MapAction
    data class SetCrosshairEnabled(val enabled: Boolean) : MapAction
    data class MapTapped(val point: GeoPoint, val hits: List<MapHitResult>) : MapAction
    data class MapLongPressed(val point: GeoPoint, val hits: List<MapHitResult>) : MapAction
    data class CrosshairConfirmed(val point: GeoPoint, val hits: List<MapHitResult>) : MapAction
    data class CoordinateEntered(val point: GeoPoint) : MapAction
    data class ChooseObjectCandidate(val hit: MapHitResult) : MapAction
    data object ConfirmPointCandidate : MapAction
    data class InsertMeasurementPoint(val index: Int, val point: GeoPoint) : MapAction
    data class DeleteMeasurementPoint(val index: Int) : MapAction
    data object UndoMeasurementEdit : MapAction
    data object RedoMeasurementEdit : MapAction
    data object ClearMeasurement : MapAction
    data class BeginPrecisePointEdit(val edit: MapPrecisePointEdit) : MapAction
    data class ConfirmPrecisePoint(val point: GeoPoint) : MapAction
    data object CancelPrecisePointEdit : MapAction
    data class BeginPointDrag(val gestureId: MapGestureId, val target: MapEditTarget) : MapAction
    data class PreviewPointDrag(val gestureId: MapGestureId, val point: GeoPoint) : MapAction
    data class CommitPointDrag(val gestureId: MapGestureId, val finalPoint: GeoPoint) : MapAction
    data class CancelPointDrag(val gestureId: MapGestureId) : MapAction
    data class ViewportChanged(val viewport: MapViewport) : MapAction
    /** Compatibility action for pre-C03 callers; production input uses MapLongPressed. */
    data class LongPressMap(val point: GeoPoint) : MapAction
    /** Compatibility action for pre-C03 tests and imports; production input confirms a candidate. */
    data class AddPoint(val point: GeoPoint) : MapAction
    data class SaveSelectionAsPlace(val name: String) : MapAction
    data class SavePointCandidateAsPlace(val name: String) : MapAction
    data class CreatePlace(
        val point: GeoPoint,
        val name: String,
        val notes: String,
        val category: PlaceCategory,
        val tags: List<String>,
    ) : MapAction
    data class UpdatePlace(
        val placeId: String,
        val expectedRevision: Long,
        val name: String,
        val notes: String,
        val category: PlaceCategory,
        val tags: List<String>,
    ) : MapAction
    data class BeginPlaceMove(val placeId: String) : MapAction
    data class PreviewPlaceMove(val point: GeoPoint) : MapAction
    data object ConfirmPlaceMove : MapAction
    data object CancelPlaceMove : MapAction
    data class RequestDeletePlace(val placeId: String) : MapAction
    data object ConfirmDeletePlace : MapAction
    data object CancelDeletePlace : MapAction
    data object UndoDeletePlace : MapAction
    data class SetPlaceQuery(val query: String) : MapAction
    data class SetPlaceSort(val sort: PlaceSort) : MapAction
    data class CreateRouteDraft(
        val name: String,
        val notes: String = "",
        val startPoint: GeoPoint? = null,
        val sourcePlaceId: String? = null,
        val sourcePlaceRevision: Long? = null,
    ) : MapAction
    data class ActivateRouteDraft(val draftId: String) : MapAction
    data class UpdateRouteDraftMetadata(val name: String, val notes: String) : MapAction
    data class AddRouteWaypoint(
        val point: GeoPoint,
        val sourcePlaceId: String? = null,
        val sourcePlaceRevision: Long? = null,
    ) : MapAction
    data class InsertRouteWaypoint(
        val beforeWaypointId: String,
        val point: GeoPoint,
        val sourcePlaceId: String? = null,
        val sourcePlaceRevision: Long? = null,
    ) : MapAction
    data class MoveRouteWaypoint(val waypointId: String, val point: GeoPoint) : MapAction
    data class DeleteRouteWaypoint(val waypointId: String) : MapAction
    data class ReorderRouteWaypoint(val waypointId: String, val toIndex: Int) : MapAction
    data class ConvertMeasurementToManualRoute(val name: String) : MapAction
    data object UndoRouteEdit : MapAction
    data object RedoRouteEdit : MapAction
    data object ReverseRoute : MapAction
    data class SetPlannedSpeedKnots(val knots: Double) : MapAction
    data object ClearPlannedSpeed : MapAction
    data class PreviewRoutePlan(val routeId: String) : MapAction
    data class BeginRoutePlanEdit(val routeId: String) : MapAction
    data object SaveRoutePlan : MapAction
    data class SaveRoutePlanAsCopy(val name: String) : MapAction
    data class DuplicateRoutePlan(
        val routeId: String,
        val reverse: Boolean = false,
        val name: String? = null,
    ) : MapAction
    data class DiscardRouteDraft(val draftId: String) : MapAction
    data class RequestDeleteRoutePlan(val routeId: String) : MapAction
    data object ConfirmDeleteRoutePlan : MapAction
    data object CancelDeleteRoutePlan : MapAction
    data object UndoDeleteRoutePlan : MapAction
    /** Compatibility action retained for pre-C06 callers; C06 product UI uses SaveRoutePlan/SaveRoutePlanAsCopy. */
    data class SaveRouteCopy(val name: String) : MapAction
    /** One confirmed GPX preview becomes one optimistic, transactional library revision. */
    data class ImportGpxBatch(val batch: GpxImportBatch) : MapAction
    data class ObservePosition(val observation: PositionObservation, val nowMillis: Long) : MapAction
    data class ClockTick(val nowMillis: Long) : MapAction
    data object PositionUnavailable : MapAction
    data class ChartPackagesChanged(val packages: List<ChartPackage>) : MapAction
    data class SelectChartPackage(val packageId: ChartPackageId) : MapAction
    data object RetryPersistence : MapAction
    data object RetryLoad : MapAction

    data class RendererHostReady(val generation: MapRendererGeneration) : MapAction
    data class RendererDetached(val generation: MapRendererGeneration) : MapAction
    data class RendererReady(val generation: MapRendererGeneration) : MapAction
    data class RendererFailed(
        val generation: MapRendererGeneration,
        val failure: MapRendererFailure,
    ) : MapAction
    data class RendererCoverageChanged(
        val generation: MapRendererGeneration,
        val coverage: MapTileCoverageStatus,
    ) : MapAction
    data class RendererCameraIdle(
        val generation: MapRendererGeneration,
        val camera: MapCamera,
        val commandId: MapCameraCommandId? = null,
    ) : MapAction
    data class RequestCamera(
        val target: MapCameraTarget,
        val intent: MapCameraIntent,
        val viewportInsets: MapViewportInsets = MapViewportInsets(),
    ) : MapAction

    /** Internal writer callbacks. Revisions make stale callbacks harmless. */
    data class PersistenceAck(val revision: Long) : MapAction
    data class PersistenceFailed(val revision: Long, val failure: MapReadFailure) : MapAction
}

sealed interface MapIncident {
    data class InvalidPlannedSpeed(val knots: Double) : MapIncident
    data object AdjacentDuplicateWaypoint : MapIncident
    data class RouteRevisionConflict(val routeId: String, val expectedRevision: Long, val actualRevision: Long?) : MapIncident
    data object MissingSelection : MapIncident
    data object InsufficientMeasurement : MapIncident
    data object InsufficientRoute : MapIncident
    data object LibraryUnavailable : MapIncident
    data object QueueBackpressure : MapIncident
    data object ActionRejected : MapIncident
    data class UnknownChartPackage(val packageId: ChartPackageId) : MapIncident
    data class PersistenceFailure(val operation: String, val failure: MapReadFailure) : MapIncident
    data class RendererFailure(val failure: MapRendererFailure) : MapIncident
}

sealed interface MapEffect {
    data class PersistSession(val snapshot: MapSessionSnapshot) : MapEffect
    data class PersistLibrary(val snapshot: MapLibrarySnapshot) : MapEffect
    data class LogIncident(val incident: MapIncident) : MapEffect
    data object Reload : MapEffect
}

data class MapReduction(val state: MapState, val effects: List<MapEffect> = emptyList())

class DefaultMapReducer(
    private val idGenerator: MapIdGenerator = RandomMapIdGenerator,
    private val historyLimit: Int = DEFAULT_HISTORY_LIMIT,
    private val clock: MapClock = SystemMapClock,
) {
    init {
        require(historyLimit > 0)
    }

    fun reduce(state: MapState, action: MapAction): MapReduction = when (action) {
        is MapAction.Restore -> restore(state, action.result)
        is MapAction.CameraChanged -> persistSession(state.copy(camera = action.camera))
        is MapAction.SelectTool -> MapReduction(selectTool(state, action.tool))
        is MapAction.OpenSurface -> MapReduction(openSurface(state, action.surface))
        MapAction.CloseSurface -> MapReduction(closeSurface(state))
        MapAction.DismissTransient -> MapReduction(state.copy(transient = null))
        MapAction.ClearSelection -> MapReduction(state.copy(selection = null))
        is MapAction.SetCrosshairEnabled -> MapReduction(state.copy(crosshairEnabled = action.enabled))
        is MapAction.MapTapped -> MapReduction(mapInteraction(state, action.point, action.hits, PointCandidateOrigin.MAP_TAP))
        is MapAction.MapLongPressed -> MapReduction(
            mapInteraction(state, action.point, action.hits, PointCandidateOrigin.MAP_LONG_PRESS),
        )
        is MapAction.CrosshairConfirmed -> MapReduction(
            mapInteraction(state, action.point, action.hits, PointCandidateOrigin.CROSSHAIR),
        )
        is MapAction.CoordinateEntered -> if (state.precisePointEdit == null) {
            MapReduction(
                mapInteraction(
                    state.copy(surface = MapSurface.Root),
                    action.point,
                    emptyList(),
                    PointCandidateOrigin.COORDINATE_INPUT,
                ),
            )
        } else {
            confirmPrecisePoint(state.copy(surface = MapSurface.Root), action.point)
        }
        is MapAction.ChooseObjectCandidate -> chooseObjectCandidate(state, action.hit)
        MapAction.ConfirmPointCandidate -> confirmPointCandidate(state)
        is MapAction.InsertMeasurementPoint -> insertMeasurementPoint(state, action.index, action.point)
        is MapAction.DeleteMeasurementPoint -> deleteMeasurementPoint(state, action.index)
        MapAction.UndoMeasurementEdit -> undoMeasurement(state)
        MapAction.RedoMeasurementEdit -> redoMeasurement(state)
        MapAction.ClearMeasurement -> editMeasurement(state) { emptyList() }
        is MapAction.BeginPrecisePointEdit -> beginPrecisePointEdit(state, action.edit)
        is MapAction.ConfirmPrecisePoint -> confirmPrecisePoint(state, action.point)
        MapAction.CancelPrecisePointEdit -> MapReduction(state.copy(precisePointEdit = null))
        is MapAction.BeginPointDrag -> beginPointDrag(state, action.gestureId, action.target)
        is MapAction.PreviewPointDrag -> previewPointDrag(state, action.gestureId, action.point)
        is MapAction.CommitPointDrag -> commitPointDrag(state, action.gestureId, action.finalPoint)
        is MapAction.CancelPointDrag -> MapReduction(
            if (state.editGesture?.id == action.gestureId) state.copy(editGesture = null) else state,
        )
        is MapAction.ViewportChanged -> MapReduction(
            if (state.viewport == action.viewport) {
                state
            } else {
                state.copy(viewport = action.viewport, editGesture = null, precisePointEdit = null)
            },
        )
        is MapAction.LongPressMap -> MapReduction(
            state.copy(
                selection = MapSelection(action.point),
            ),
        )
        is MapAction.AddPoint -> addPoint(state, action.point)
        is MapAction.SaveSelectionAsPlace -> savePlace(state, action.name)
        is MapAction.SavePointCandidateAsPlace -> savePointCandidate(state, action.name)
        is MapAction.CreatePlace -> createPlace(state, action)
        is MapAction.UpdatePlace -> updatePlace(state, action)
        is MapAction.BeginPlaceMove -> beginPlaceMove(state, action.placeId)
        is MapAction.PreviewPlaceMove -> previewPlaceMove(state, action.point)
        MapAction.ConfirmPlaceMove -> confirmPlaceMove(state)
        MapAction.CancelPlaceMove -> MapReduction(closeSurface(state.copy(placeMove = null)))
        is MapAction.RequestDeletePlace -> requestDeletePlace(state, action.placeId)
        MapAction.ConfirmDeletePlace -> confirmDeletePlace(state)
        MapAction.CancelDeletePlace -> MapReduction(closeSurface(state.copy(placeDeleteRequest = null)))
        MapAction.UndoDeletePlace -> undoDeletePlace(state)
        is MapAction.SetPlaceQuery -> MapReduction(state.copy(placeQuery = action.query))
        is MapAction.SetPlaceSort -> MapReduction(state.copy(placeSort = action.sort))
        is MapAction.CreateRouteDraft -> createRouteDraft(state, action)
        is MapAction.ActivateRouteDraft -> activateRouteDraft(state, action.draftId)
        is MapAction.UpdateRouteDraftMetadata -> updateRouteMetadata(state, action.name, action.notes)
        is MapAction.AddRouteWaypoint -> addRouteWaypoint(
            state,
            action.point,
            action.sourcePlaceId?.let { id -> action.sourcePlaceRevision?.let { revision -> PlaceRevisionReference(id, revision) } },
        )
        is MapAction.InsertRouteWaypoint -> insertRouteWaypoint(
            state,
            action.beforeWaypointId,
            action.point,
            action.sourcePlaceId?.let { id -> action.sourcePlaceRevision?.let { revision -> PlaceRevisionReference(id, revision) } },
        )
        is MapAction.MoveRouteWaypoint -> moveRouteWaypoint(state, action.waypointId, action.point)
        is MapAction.DeleteRouteWaypoint -> deleteRouteWaypoint(state, action.waypointId)
        is MapAction.ReorderRouteWaypoint -> reorderRouteWaypoint(state, action.waypointId, action.toIndex)
        is MapAction.ConvertMeasurementToManualRoute -> convertMeasurement(state, action.name)
        MapAction.UndoRouteEdit -> editRoute(state) { draft ->
            val previous = draft.undo.lastOrNull() ?: return@editRoute null
            draft.copy(
                revision = draft.revision + 1,
                waypoints = previous.waypoints,
                waypointIds = previous.waypointIds,
                waypointPlaceReferences = previous.waypointPlaceReferences,
                nextWaypointOrdinal = previous.nextWaypointOrdinal,
                undo = draft.undo.dropLast(1),
                redo = boundedRouteHistory(draft.redo + draft.geometry()),
            )
        }
        MapAction.RedoRouteEdit -> editRoute(state) { draft ->
            val next = draft.redo.lastOrNull() ?: return@editRoute null
            draft.copy(
                revision = draft.revision + 1,
                waypoints = next.waypoints,
                waypointIds = next.waypointIds,
                waypointPlaceReferences = next.waypointPlaceReferences,
                nextWaypointOrdinal = next.nextWaypointOrdinal,
                undo = boundedRouteHistory(draft.undo + draft.geometry()),
                redo = draft.redo.dropLast(1),
            )
        }
        MapAction.ReverseRoute -> editRoute(state) { draft ->
            if (draft.waypoints.size < 2) null else draft.record(draft.routeWaypoints().reversed())
        }
        is MapAction.SetPlannedSpeedKnots -> setPlannedSpeed(state, action.knots)
        MapAction.ClearPlannedSpeed -> clearPlannedSpeed(state)
        is MapAction.PreviewRoutePlan -> previewRoutePlan(state, action.routeId)
        is MapAction.BeginRoutePlanEdit -> beginRoutePlanEdit(state, action.routeId)
        MapAction.SaveRoutePlan -> saveRoutePlan(state)
        is MapAction.SaveRoutePlanAsCopy -> saveRoutePlan(state, action.name)
        is MapAction.DuplicateRoutePlan -> duplicateRoutePlan(state, action.routeId, action.reverse, action.name)
        is MapAction.DiscardRouteDraft -> discardRouteDraft(state, action.draftId)
        is MapAction.RequestDeleteRoutePlan -> requestDeleteRoutePlan(state, action.routeId)
        MapAction.ConfirmDeleteRoutePlan -> confirmDeleteRoutePlan(state)
        MapAction.CancelDeleteRoutePlan -> MapReduction(closeSurface(state.copy(routeDeleteRequest = null)))
        MapAction.UndoDeleteRoutePlan -> undoDeleteRoutePlan(state)
        is MapAction.SaveRouteCopy -> saveRouteCopy(state, action.name)
        is MapAction.ImportGpxBatch -> importGpxBatch(state, action.batch)
        is MapAction.ObservePosition -> observePosition(state, action)
        is MapAction.ClockTick -> MapReduction(state.copy(position = state.position.at(action.nowMillis)))
        MapAction.PositionUnavailable -> MapReduction(state.copy(position = PositionState()))
        is MapAction.ChartPackagesChanged -> updateChartPackages(state, action.packages)
        is MapAction.SelectChartPackage -> selectChartPackage(state, action.packageId)
        MapAction.RetryPersistence -> retryPersistence(state)
        MapAction.RetryLoad -> MapReduction(
            state.copy(libraryLoadState = MapLibraryLoadState.LOADING, persistenceFailure = null),
            listOf(MapEffect.Reload),
        )
        is MapAction.PersistenceAck -> persistenceAck(state, action.revision)
        is MapAction.PersistenceFailed -> persistenceFailed(state, action.revision, action.failure)
        is MapAction.RendererHostReady -> rendererHostReady(state, action.generation)
        is MapAction.RendererDetached -> rendererDetached(state, action.generation)
        is MapAction.RendererReady -> rendererReady(state, action.generation)
        is MapAction.RendererFailed -> rendererFailed(state, action.generation, action.failure)
        is MapAction.RendererCoverageChanged -> rendererCoverage(state, action.generation, action.coverage)
        is MapAction.RendererCameraIdle -> rendererCameraIdle(state, action)
        is MapAction.RequestCamera -> MapReduction(
            state.withCameraCommand(action.target, action.intent, action.viewportInsets),
        )
    }

    private fun restore(state: MapState, result: MapLoadResult): MapReduction = when (result) {
        is MapLoadResult.Ready -> {
            val library = result.library
            val activeDraftId = result.session.activeRouteDraftId?.takeIf { active ->
                library.routeDrafts.any { it.id == active }
            } ?: library.routeDrafts.lastOrNull()?.id
            val activePlanId = result.session.activeRoutePlanId?.takeIf { active ->
                library.savedRoutes.any { it.id == active }
            }
            MapReduction(
                state.copy(
                    camera = result.session.camera,
                    measurementDraft = result.session.measurementDraft,
                    places = library.places,
                    routeDrafts = library.routeDrafts,
                    activeRouteDraftId = activeDraftId,
                    savedRoutes = library.savedRoutes,
                    importedTracks = library.importedTracks,
                    gpxImportRecords = library.gpxImportRecords,
                    activeRoutePlanId = activePlanId,
                    activeChartPackageId = result.session.activeChartPackageId,
                    libraryLoadState = if (library.isEmpty) MapLibraryLoadState.READY_EMPTY else MapLibraryLoadState.READY,
                    libraryRevision = library.revision,
                    durableLibraryRevision = library.revision,
                    saveState = MapSaveState.SAVED,
                    placeMove = null,
                    placeDeleteRequest = null,
                    placeDeleteUndo = null,
                    placeSaveStatus = null,
                    routeSaveStatus = null,
                    routeSaveTransaction = null,
                    routeDeleteRequest = null,
                    routeDeleteUndo = null,
                    routeSpeedNotice = null,
                    routeEditNotice = null,
                    persistenceFailure = null,
                ).withCameraCommand(
                    target = MapCameraTarget.Exact(result.session.camera),
                    intent = MapCameraIntent.RESTORE,
                ),
                if (result.quarantinedRecordCount > 0) {
                    listOf(MapEffect.LogIncident(MapIncident.PersistenceFailure("load-record", MapReadFailure.CORRUPT)))
                } else {
                    emptyList()
                },
            )
        }
        is MapLoadResult.ReadFailed -> MapReduction(
            state.copy(libraryLoadState = MapLibraryLoadState.READ_FAILED, persistenceFailure = result.failure),
            listOf(MapEffect.LogIncident(MapIncident.PersistenceFailure("load", result.failure))),
        )
        is MapLoadResult.Corrupt -> MapReduction(
            state.copy(libraryLoadState = MapLibraryLoadState.CORRUPT, persistenceFailure = result.failure),
            listOf(MapEffect.LogIncident(MapIncident.PersistenceFailure("load", result.failure))),
        )
    }

    private fun rendererHostReady(state: MapState, generation: MapRendererGeneration): MapReduction {
        val current = state.renderer.generation
        if (current != null && generation.value < current.value) return MapReduction(state)
        if (current == generation && state.renderer.readiness != MapRendererReadiness.DETACHED) return MapReduction(state)
        val coverage = if (state.activeChartPackageId == null) {
            MapTileCoverageStatus.NO_PACKAGE
        } else {
            MapTileCoverageStatus.CHECKING
        }
        return MapReduction(
            state.copy(
                renderer = state.renderer.copy(
                    generation = generation,
                    readiness = MapRendererReadiness.HOST_READY,
                    tileCoverage = coverage,
                    cameraInputEnabled = false,
                    failure = null,
                ),
            ),
        )
    }

    private fun rendererDetached(state: MapState, generation: MapRendererGeneration): MapReduction =
        if (state.renderer.generation != generation) MapReduction(state) else MapReduction(
            state.copy(
                renderer = state.renderer.copy(
                    readiness = MapRendererReadiness.DETACHED,
                    cameraInputEnabled = false,
                ),
            ),
        )

    private fun rendererReady(state: MapState, generation: MapRendererGeneration): MapReduction =
        if (state.renderer.generation != generation) MapReduction(state) else MapReduction(
            state.copy(renderer = state.renderer.copy(readiness = MapRendererReadiness.RENDERER_READY, failure = null)),
        )

    private fun rendererFailed(
        state: MapState,
        generation: MapRendererGeneration,
        failure: MapRendererFailure,
    ): MapReduction = if (state.renderer.generation != generation) {
        MapReduction(state)
    } else {
        MapReduction(
            state.copy(
                renderer = state.renderer.copy(readiness = MapRendererReadiness.ERROR, failure = failure),
            ),
            listOf(MapEffect.LogIncident(MapIncident.RendererFailure(failure))),
        )
    }

    private fun rendererCoverage(
        state: MapState,
        generation: MapRendererGeneration,
        coverage: MapTileCoverageStatus,
    ): MapReduction = if (state.renderer.generation != generation) {
        MapReduction(state)
    } else {
        MapReduction(state.copy(renderer = state.renderer.copy(tileCoverage = coverage)))
    }

    private fun rendererCameraIdle(state: MapState, action: MapAction.RendererCameraIdle): MapReduction {
        if (state.renderer.generation != action.generation) return MapReduction(state)
        val pending = state.renderer.pendingCameraCommand
        if (action.commandId != null) {
            if (pending?.id != action.commandId) return MapReduction(state)
            return MapReduction(
                state.copy(
                    camera = action.camera,
                    renderer = state.renderer.copy(
                        pendingCameraCommand = null,
                        lastAcknowledgedCameraCommandId = action.commandId,
                        cameraInputEnabled = true,
                    ),
                ),
            )
        }
        if (
            state.renderer.readiness != MapRendererReadiness.RENDERER_READY ||
            pending != null ||
            !state.renderer.cameraInputEnabled
        ) {
            return MapReduction(state)
        }
        return if (state.camera == action.camera) MapReduction(state) else persistSession(state.copy(camera = action.camera))
    }

    private fun selectTool(state: MapState, tool: MapTool): MapState = when (tool) {
        MapTool.MEASURE -> state.copy(
            tool = tool,
            transient = null,
            editGesture = null,
            precisePointEdit = null,
            measurementDraft = state.measurementDraft ?: MeasurementDraft(),
        )
        else -> state.copy(tool = tool, transient = null, editGesture = null, precisePointEdit = null)
    }

    private fun openSurface(state: MapState, surface: MapSurface): MapState = when (surface) {
        MapSurface.Root -> state.copy(
            surface = MapSurface.Root,
            surfaceHistory = emptyList(),
            transient = null,
            placeMove = null,
            placeDeleteRequest = null,
        )
        is MapSurface.NewPlace -> state.pushSurface(surface)
        is MapSurface.EditPlace -> if (state.places.any { it.id == surface.placeId }) {
            state.pushSurface(surface)
        } else {
            state.copy(surface = MapSurface.Root, surfaceHistory = emptyList(), transient = MapTransient.UnavailableObject(surface.placeId))
        }
        is MapSurface.MovePlace -> if (state.places.any { it.id == surface.placeId }) {
            state.pushSurface(surface)
        } else {
            state.copy(surface = MapSurface.Root, surfaceHistory = emptyList(), transient = MapTransient.UnavailableObject(surface.placeId))
        }
        is MapSurface.DeletePlace -> if (state.places.any { it.id == surface.placeId }) {
            state.pushSurface(surface)
        } else {
            state.copy(surface = MapSurface.Root, surfaceHistory = emptyList(), transient = MapTransient.UnavailableObject(surface.placeId))
        }
        is MapSurface.PlaceDetail -> if (state.places.any { it.id == surface.placeId }) {
            state.pushSurface(surface)
        } else {
            state.copy(surface = MapSurface.Root, transient = MapTransient.UnavailableObject(surface.placeId))
        }
        is MapSurface.RouteDetail -> if (state.savedRoutes.any { it.id == surface.routeId } ||
            state.routeDrafts.any { it.id == surface.routeId }
        ) {
            state.pushSurface(surface)
        } else {
            state.copy(surface = MapSurface.Root, transient = MapTransient.UnavailableObject(surface.routeId))
        }
        is MapSurface.DeleteRoutePlan -> if (state.savedRoutes.any { it.id == surface.routeId }) {
            state.pushSurface(surface)
        } else {
            state.copy(surface = MapSurface.Root, transient = MapTransient.UnavailableObject(surface.routeId))
        }
        is MapSurface.ChartPackageDetail -> if (state.chartPackages.any { it.id == surface.packageId }) {
            state.pushSurface(surface)
        } else {
            state.copy(surface = MapSurface.Root, transient = MapTransient.UnavailableObject(surface.packageId.value))
        }
        is MapSurface.ImportedTrackDetail -> if (state.importedTracks.any { it.id == surface.trackId }) {
            state.pushSurface(surface)
        } else {
            state.copy(surface = MapSurface.Root, transient = MapTransient.UnavailableObject(surface.trackId))
        }
        else -> state.pushSurface(surface)
    }

    private fun MapState.pushSurface(next: MapSurface): MapState = if (surface == next) {
        copy(transient = null)
    } else {
        copy(surface = next, surfaceHistory = surfaceHistory + surface, transient = null)
    }

    private fun closeSurface(state: MapState): MapState {
        val previous = state.surfaceHistory.lastOrNull() ?: MapSurface.Root
        return state.copy(
            surface = previous,
            surfaceHistory = state.surfaceHistory.dropLast(1),
            precisePointEdit = if (state.surface == MapSurface.CoordinateInput) null else state.precisePointEdit,
            placeMove = if (state.surface is MapSurface.MovePlace) null else state.placeMove,
            placeDeleteRequest = if (state.surface is MapSurface.DeletePlace) null else state.placeDeleteRequest,
            routeDeleteRequest = if (state.surface is MapSurface.DeleteRoutePlan) null else state.routeDeleteRequest,
        )
    }

    private fun mapInteraction(
        state: MapState,
        point: GeoPoint,
        inputHits: List<MapHitResult>,
        origin: PointCandidateOrigin,
    ): MapState {
        val hits = inputHits.distinctBy { it.overlayId to it.objectId }
        val transient = when (hits.size) {
            0 -> if (state.tool == MapTool.BROWSE && origin == PointCandidateOrigin.MAP_TAP) {
                null
            } else {
                MapTransient.PointCandidate(point, origin)
            }
            1 -> MapTransient.SelectedObject(hits.single())
            else -> MapTransient.ObjectCandidates(hits)
        }
        return state.copy(transient = transient)
    }

    private fun chooseObjectCandidate(state: MapState, hit: MapHitResult): MapReduction {
        val candidates = (state.transient as? MapTransient.ObjectCandidates)?.hits ?: return MapReduction(state)
        return if (hit in candidates) {
            MapReduction(state.copy(transient = MapTransient.SelectedObject(hit)))
        } else {
            incident(state, MapIncident.ActionRejected)
        }
    }

    private fun confirmPointCandidate(state: MapState): MapReduction {
        val candidate = state.transient as? MapTransient.PointCandidate ?: return MapReduction(state)
        val cleared = state.copy(transient = null)
        return if (state.tool == MapTool.BROWSE) {
            MapReduction(cleared.copy(selection = MapSelection(candidate.point)))
        } else {
            addPoint(cleared, candidate.point)
        }
    }

    private fun beginPointDrag(state: MapState, id: MapGestureId, target: MapEditTarget): MapReduction {
        if (state.editGesture != null) return incident(state, MapIncident.ActionRejected)
        val original = state.pointFor(target) ?: return incident(state, MapIncident.ActionRejected)
        return MapReduction(state.copy(editGesture = MapEditGesture(id, target, original)))
    }

    private fun previewPointDrag(state: MapState, id: MapGestureId, point: GeoPoint): MapReduction {
        val gesture = state.editGesture?.takeIf { it.id == id } ?: return MapReduction(state)
        return MapReduction(state.copy(editGesture = gesture.copy(previewPoint = point)))
    }

    private fun commitPointDrag(state: MapState, id: MapGestureId, finalPoint: GeoPoint): MapReduction {
        val gesture = state.editGesture?.takeIf { it.id == id } ?: return MapReduction(state)
        val cleared = state.copy(editGesture = null)
        return commitPointEdit(cleared, gesture.target, finalPoint)
    }

    private fun MapState.pointFor(target: MapEditTarget): GeoPoint? = when (target) {
        is MapEditTarget.MeasurementPoint -> measurementDraft?.points?.getOrNull(target.index)
        is MapEditTarget.RoutePoint -> routeDrafts.firstOrNull { it.id == target.draftId }?.waypoints?.getOrNull(target.index)
    }

    private fun beginPrecisePointEdit(state: MapState, edit: MapPrecisePointEdit): MapReduction {
        val valid = when (edit) {
            is MapPrecisePointEdit.Move -> state.pointFor(edit.target) != null
            is MapPrecisePointEdit.InsertMeasurement -> {
                val size = state.measurementDraft?.points?.size ?: 0
                edit.index in 0..size
            }
            is MapPrecisePointEdit.InsertRoute -> {
                val draft = state.routeDrafts.firstOrNull { it.id == edit.draftId }
                draft != null && (edit.beforeWaypointId == null || edit.beforeWaypointId in draft.waypointIds)
            }
        }
        return if (valid) {
            MapReduction(state.copy(precisePointEdit = edit, transient = null, crosshairEnabled = true))
        } else {
            incident(state, MapIncident.ActionRejected)
        }
    }

    private fun confirmPrecisePoint(state: MapState, point: GeoPoint): MapReduction {
        val edit = state.precisePointEdit ?: return incident(state, MapIncident.ActionRejected)
        val cleared = state.copy(precisePointEdit = null)
        return when (edit) {
            is MapPrecisePointEdit.Move -> commitPointEdit(cleared, edit.target, point)
            is MapPrecisePointEdit.InsertMeasurement -> insertMeasurementPoint(cleared, edit.index, point)
            is MapPrecisePointEdit.InsertRoute -> {
                val draft = state.routeDrafts.firstOrNull { it.id == edit.draftId }
                    ?: return incident(state, MapIncident.ActionRejected)
                if (state.activeRouteDraftId != draft.id) return incident(state, MapIncident.ActionRejected)
                if (edit.beforeWaypointId == null) addRouteWaypoint(cleared, point, null)
                else insertRouteWaypoint(cleared, edit.beforeWaypointId, point, null)
            }
        }
    }

    private fun commitPointEdit(state: MapState, target: MapEditTarget, point: GeoPoint): MapReduction {
        return when (target) {
            is MapEditTarget.MeasurementPoint -> {
                val points = state.measurementDraft?.points ?: return incident(state, MapIncident.ActionRejected)
                if (target.index !in points.indices) return incident(state, MapIncident.ActionRejected)
                editMeasurement(state) { current ->
                    current.mapIndexed { index, existing -> if (index == target.index) point else existing }
                }
            }
            is MapEditTarget.RoutePoint -> {
                val draft = state.routeDrafts.firstOrNull { it.id == target.draftId }
                    ?: return incident(state, MapIncident.ActionRejected)
                if (target.index !in draft.waypoints.indices) return incident(state, MapIncident.ActionRejected)
                val updated = draft.record(draft.routeWaypoints().mapIndexed { index, existing ->
                    if (index == target.index) existing.copy(point = point, source = null) else existing
                })
                persistLibrary(
                    state.copy(routeDrafts = state.routeDrafts.map { if (it.id == updated.id) updated else it }),
                )
            }
        }
    }

    private fun insertMeasurementPoint(state: MapState, index: Int, point: GeoPoint): MapReduction {
        val points = state.measurementDraft?.points ?: emptyList()
        if (index !in 0..points.size) return incident(state, MapIncident.ActionRejected)
        return editMeasurement(state) { current -> current.toMutableList().apply { add(index, point) } }
    }

    private fun deleteMeasurementPoint(state: MapState, index: Int): MapReduction {
        val points = state.measurementDraft?.points ?: return incident(state, MapIncident.ActionRejected)
        if (index !in points.indices) return incident(state, MapIncident.ActionRejected)
        return editMeasurement(state) { current -> current.filterIndexed { candidate, _ -> candidate != index } }
    }

    private fun undoMeasurement(state: MapState): MapReduction {
        val draft = state.measurementDraft ?: return MapReduction(state)
        val previous = draft.undo.lastOrNull() ?: return MapReduction(state)
        return persistSession(
            state.copy(
                measurementDraft = draft.copy(
                    points = previous,
                    undo = draft.undo.dropLast(1),
                    redo = bounded(draft.redo + listOf(draft.points)),
                ),
            ),
        )
    }

    private fun redoMeasurement(state: MapState): MapReduction {
        val draft = state.measurementDraft ?: return MapReduction(state)
        val next = draft.redo.lastOrNull() ?: return MapReduction(state)
        return persistSession(
            state.copy(
                measurementDraft = draft.copy(
                    points = next,
                    undo = bounded(draft.undo + listOf(draft.points)),
                    redo = draft.redo.dropLast(1),
                ),
            ),
        )
    }

    private fun editMeasurement(state: MapState, transform: (List<GeoPoint>) -> List<GeoPoint>): MapReduction {
        val draft = state.measurementDraft ?: MeasurementDraft()
        val updated = transform(draft.points).toList()
        if (updated == draft.points) return MapReduction(state)
        return persistSession(
            state.copy(
                measurementDraft = draft.copy(
                    points = updated,
                    undo = bounded(draft.undo + listOf(draft.points)),
                    redo = emptyList(),
                ),
            ),
        )
    }

    private fun addPoint(state: MapState, point: GeoPoint): MapReduction = when (state.tool) {
        MapTool.MEASURE -> editMeasurement(state) { points -> points + point }
        MapTool.MANUAL_ROUTE -> addRouteWaypoint(state, point, null)
        else -> MapReduction(state.copy(selection = MapSelection(point)))
    }

    private fun savePlace(state: MapState, name: String): MapReduction = ifWritable(state) {
        val selection = state.selection ?: return@ifWritable incident(state, MapIncident.MissingSelection)
        createPlace(
            state.copy(selection = null),
            MapAction.CreatePlace(
                point = selection.point,
                name = name.trim().ifEmpty { "Place" },
                notes = "",
                category = PlaceCategory.PERSONAL_MARKER,
                tags = emptyList(),
            ),
        )
    }

    private fun savePointCandidate(state: MapState, name: String): MapReduction = ifWritable(state) {
        val candidate = state.transient as? MapTransient.PointCandidate
            ?: return@ifWritable incident(state, MapIncident.MissingSelection)
        createPlace(
            state.copy(transient = null),
            MapAction.CreatePlace(
                point = candidate.point,
                name = name.trim().ifEmpty { "Place" },
                notes = "",
                category = PlaceCategory.PERSONAL_MARKER,
                tags = emptyList(),
            ),
        )
    }

    private fun createPlace(state: MapState, action: MapAction.CreatePlace): MapReduction = ifWritable(state) {
        val now = clock.nowMillis().coerceAtLeast(0L)
        val unavailableIds = buildSet {
            addAll(state.places.map { it.id })
            state.placeDeleteUndo?.place?.id?.let(::add)
            state.savedRoutes.flatMapTo(this) { route -> route.waypointPlaceReferences.values.map { it.placeId } }
        }
        var generatedId: String? = null
        repeat(16) {
            if (generatedId == null) {
                generatedId = idGenerator.nextId("place").trim().takeIf { it.isNotEmpty() && it !in unavailableIds }
            }
        }
        val placeId = generatedId ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        val place = SavedPlace(
            id = placeId,
            name = action.name.trim().ifEmpty { "Place" },
            point = action.point,
            revision = 1L,
            notes = action.notes.trim(),
            category = action.category,
            tags = action.tags.normalizedTags(),
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        persistLibrary(
            state.copy(
                places = state.places + place,
                selection = null,
                transient = null,
                placeSaveStatus = PlaceSaveStatus(place.id, place.revision, MapSaveState.PENDING),
                placeDeleteUndo = null,
                surface = MapSurface.PlaceDetail(place.id),
                surfaceHistory = listOf(MapSurface.Root, MapSurface.Places),
            ),
        )
    }

    private fun updatePlace(state: MapState, action: MapAction.UpdatePlace): MapReduction = ifWritable(state) {
        val existing = state.places.firstOrNull { it.id == action.placeId }
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        if (existing.revision != action.expectedRevision) return@ifWritable incident(state, MapIncident.ActionRejected)
        val updated = existing.copy(
            name = action.name.trim().ifEmpty { existing.name },
            notes = action.notes.trim(),
            category = action.category,
            tags = action.tags.normalizedTags(),
            revision = existing.revision + 1L,
            updatedAtMillis = clock.nowMillis().coerceAtLeast(existing.updatedAtMillis),
        )
        persistLibrary(
            state.copy(
                places = state.places.map { if (it.id == updated.id) updated else it },
                placeSaveStatus = PlaceSaveStatus(updated.id, updated.revision, MapSaveState.PENDING),
                placeDeleteUndo = null,
                surface = MapSurface.PlaceDetail(updated.id),
                surfaceHistory = if (state.surface is MapSurface.EditPlace) {
                    state.surfaceHistory.dropLast(1)
                } else {
                    state.surfaceHistory
                },
            ),
        )
    }

    private fun beginPlaceMove(state: MapState, placeId: String): MapReduction {
        val place = state.places.firstOrNull { it.id == placeId }
            ?: return incident(state, MapIncident.ActionRejected)
        return MapReduction(
            state.copy(
                placeMove = PlaceMoveDraft(place.id, place.revision, place.point),
                placeDeleteRequest = null,
                transient = null,
            ).pushSurface(MapSurface.MovePlace(place.id)),
        )
    }

    private fun previewPlaceMove(state: MapState, point: GeoPoint): MapReduction {
        val move = state.placeMove ?: return incident(state, MapIncident.ActionRejected)
        return MapReduction(state.copy(placeMove = move.copy(candidatePoint = point)))
    }

    private fun confirmPlaceMove(state: MapState): MapReduction = ifWritable(state) {
        val move = state.placeMove ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        val place = state.places.firstOrNull { it.id == move.placeId }
            ?.takeIf { it.revision == move.expectedRevision }
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        if (move.candidatePoint == place.point) return@ifWritable MapReduction(state.copy(placeMove = null))
        val updated = place.copy(
            point = move.candidatePoint,
            revision = place.revision + 1L,
            updatedAtMillis = clock.nowMillis().coerceAtLeast(place.updatedAtMillis),
        )
        persistLibrary(
            state.copy(
                places = state.places.map { if (it.id == updated.id) updated else it },
                placeMove = null,
                placeSaveStatus = PlaceSaveStatus(updated.id, updated.revision, MapSaveState.PENDING),
                placeDeleteUndo = null,
                surface = MapSurface.PlaceDetail(updated.id),
                surfaceHistory = if (state.surface is MapSurface.MovePlace) {
                    state.surfaceHistory.dropLast(1)
                } else {
                    state.surfaceHistory
                },
            ),
        )
    }

    private fun requestDeletePlace(state: MapState, placeId: String): MapReduction {
        val place = state.places.firstOrNull { it.id == placeId }
            ?: return incident(state, MapIncident.ActionRejected)
        val routeCount = state.savedRoutes.count { route ->
            route.waypointPlaceReferences.values.any { it.placeId == place.id }
        }
        return MapReduction(
            state.copy(
                placeDeleteRequest = PlaceDeleteRequest(place.id, place.revision, place.name, routeCount),
                placeMove = null,
            ).pushSurface(MapSurface.DeletePlace(place.id)),
        )
    }

    private fun confirmDeletePlace(state: MapState): MapReduction = ifWritable(state) {
        val request = state.placeDeleteRequest ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        val place = state.places.firstOrNull { it.id == request.placeId }
            ?.takeIf { it.revision == request.expectedRevision }
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        persistLibrary(
            state.copy(
                places = state.places.filterNot { it.id == place.id },
                placeDeleteRequest = null,
                placeDeleteUndo = PlaceDeleteUndo(place, state.libraryRevision + 1L),
                placeSaveStatus = null,
                surface = MapSurface.Places,
                surfaceHistory = listOf(MapSurface.Root),
            ),
        )
    }

    private fun undoDeletePlace(state: MapState): MapReduction = ifWritable(state) {
        val undo = state.placeDeleteUndo ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        if (state.libraryRevision != undo.compatibleLibraryRevision || state.places.any { it.id == undo.place.id }) {
            return@ifWritable incident(state, MapIncident.ActionRejected)
        }
        val restored = undo.place.copy(
            revision = undo.place.revision + 1L,
            updatedAtMillis = clock.nowMillis().coerceAtLeast(undo.place.updatedAtMillis),
        )
        persistLibrary(
            state.copy(
                places = state.places + restored,
                placeDeleteUndo = null,
                placeSaveStatus = PlaceSaveStatus(restored.id, restored.revision, MapSaveState.PENDING),
            ),
        )
    }

    private fun createRouteDraft(state: MapState, action: MapAction.CreateRouteDraft): MapReduction = ifWritable(state) {
        val id = nextUniqueId("draft", state.routeDrafts.map { it.id }.toSet())
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        if ((action.sourcePlaceId == null) != (action.sourcePlaceRevision == null)) {
            return@ifWritable incident(state, MapIncident.ActionRejected)
        }
        val points = action.startPoint?.let(::listOf).orEmpty()
        val ids = if (points.isEmpty()) emptyList() else listOf(routeWaypointId(id, 1))
        val references = if (points.isEmpty() || action.sourcePlaceId == null) emptyMap() else mapOf(
            0 to PlaceRevisionReference(action.sourcePlaceId, requireNotNull(action.sourcePlaceRevision)),
        )
        val draft = ManualRouteDraft(
            id = id,
            revision = 1L,
            name = action.name.trim(),
            waypoints = points,
            notes = action.notes.trim(),
            waypointIds = ids,
            waypointPlaceReferences = references,
            nextWaypointOrdinal = points.size + 1,
        )
        persistLibrary(
            state.copy(
                tool = MapTool.MANUAL_ROUTE,
                routeDrafts = state.routeDrafts + draft,
                activeRouteDraftId = draft.id,
                activeRoutePlanId = null,
                routeSaveStatus = null,
                routeSpeedNotice = null,
                surface = MapSurface.RouteDetail(draft.id),
                surfaceHistory = listOf(MapSurface.Root, MapSurface.Routes),
            ),
        )
    }

    private fun activateRouteDraft(state: MapState, draftId: String): MapReduction {
        if (state.routeDrafts.none { it.id == draftId }) return incident(state, MapIncident.ActionRejected)
        return persistSession(
            state.copy(
                tool = MapTool.MANUAL_ROUTE,
                activeRouteDraftId = draftId,
                activeRoutePlanId = null,
                routeSpeedNotice = null,
            ),
        )
    }

    private fun updateRouteMetadata(state: MapState, name: String, notes: String): MapReduction = editRoute(state) { draft ->
        val normalizedName = name.trim()
        val normalizedNotes = notes.trim()
        if (draft.name == normalizedName && draft.notes == normalizedNotes) null
        else draft.copy(revision = draft.revision + 1L, name = normalizedName, notes = normalizedNotes)
    }

    private fun addRouteWaypoint(
        state: MapState,
        point: GeoPoint,
        source: PlaceRevisionReference?,
    ): MapReduction = ifWritable(state) {
        val draft = state.routeDraft ?: return@ifWritable createRouteDraft(
            state,
            MapAction.CreateRouteDraft("", startPoint = point, sourcePlaceId = source?.placeId, sourcePlaceRevision = source?.revision),
        )
        if (draft.waypoints.lastOrNull() == point) return@ifWritable adjacentDuplicate(state)
        val items = draft.routeWaypoints() + RouteWaypointValue(
            id = routeWaypointId(draft.id, draft.nextWaypointOrdinal),
            point = point,
            source = source,
        )
        replaceActiveDraft(state, draft.record(items, draft.nextWaypointOrdinal + 1))
    }

    private fun insertRouteWaypoint(
        state: MapState,
        beforeWaypointId: String,
        point: GeoPoint,
        source: PlaceRevisionReference?,
    ): MapReduction = ifWritable(state) {
        val draft = state.routeDraft ?: return@ifWritable incident(state, MapIncident.InsufficientRoute)
        val index = draft.waypointIds.indexOf(beforeWaypointId).takeIf { it >= 0 }
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        val items = draft.routeWaypoints().toMutableList()
        if (items.getOrNull(index - 1)?.point == point || items.getOrNull(index)?.point == point) {
            return@ifWritable adjacentDuplicate(state)
        }
        items.add(index, RouteWaypointValue(routeWaypointId(draft.id, draft.nextWaypointOrdinal), point, source))
        replaceActiveDraft(state, draft.record(items, draft.nextWaypointOrdinal + 1))
    }

    private fun moveRouteWaypoint(state: MapState, waypointId: String, point: GeoPoint): MapReduction = ifWritable(state) {
        val draft = state.routeDraft ?: return@ifWritable incident(state, MapIncident.InsufficientRoute)
        val items = draft.routeWaypoints().toMutableList()
        val index = items.indexOfFirst { it.id == waypointId }.takeIf { it >= 0 }
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        if (items.getOrNull(index - 1)?.point == point || items.getOrNull(index + 1)?.point == point) {
            return@ifWritable adjacentDuplicate(state)
        }
        items[index] = items[index].copy(point = point, source = null)
        replaceActiveDraft(state, draft.record(items))
    }

    private fun deleteRouteWaypoint(state: MapState, waypointId: String): MapReduction = ifWritable(state) {
        val draft = state.routeDraft ?: return@ifWritable incident(state, MapIncident.InsufficientRoute)
        val items = draft.routeWaypoints()
        if (items.none { it.id == waypointId }) return@ifWritable incident(state, MapIncident.ActionRejected)
        replaceActiveDraft(state, draft.record(items.filterNot { it.id == waypointId }))
    }

    private fun reorderRouteWaypoint(state: MapState, waypointId: String, toIndex: Int): MapReduction = ifWritable(state) {
        val draft = state.routeDraft ?: return@ifWritable incident(state, MapIncident.InsufficientRoute)
        if (toIndex !in draft.waypoints.indices) return@ifWritable incident(state, MapIncident.ActionRejected)
        val items = draft.routeWaypoints().toMutableList()
        val fromIndex = items.indexOfFirst { it.id == waypointId }.takeIf { it >= 0 }
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        if (fromIndex == toIndex) return@ifWritable MapReduction(state)
        val moved = items.removeAt(fromIndex)
        items.add(toIndex, moved)
        if (items.zipWithNext().any { (from, to) -> from.point == to.point }) {
            return@ifWritable adjacentDuplicate(state)
        }
        replaceActiveDraft(state, draft.record(items))
    }

    private fun convertMeasurement(state: MapState, name: String): MapReduction = ifWritable(state) {
        val points = state.measurementDraft?.points.orEmpty()
        if (points.size < 2) return@ifWritable incident(state, MapIncident.InsufficientMeasurement)
        val id = nextUniqueId("draft", state.routeDrafts.map { it.id }.toSet())
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        val draft = ManualRouteDraft(
            id = id,
            revision = 1L,
            name = name.trim(),
            waypoints = points.toList(),
            waypointIds = points.indices.map { routeWaypointId(id, it + 1) },
            nextWaypointOrdinal = points.size + 1,
        )
        persistLibrary(
            state.copy(
                tool = MapTool.MANUAL_ROUTE,
                routeDrafts = state.routeDrafts + draft,
                activeRouteDraftId = draft.id,
                activeRoutePlanId = null,
            ),
        )
    }

    private fun setPlannedSpeed(state: MapState, knots: Double): MapReduction {
        if (!knots.isFinite() || knots <= 0.0) return incident(state, MapIncident.InvalidPlannedSpeed(knots))
        val reduction = editRoute(state) { draft ->
            if (draft.plannedSpeedKnots == knots) null else draft.copy(revision = draft.revision + 1, plannedSpeedKnots = knots)
        }
        return reduction.copy(
            state = reduction.state.copy(routeSpeedNotice = if (knots > EXTREME_PLANNED_SPEED_KNOTS) RouteSpeedNotice.EXTREME else null),
        )
    }

    private fun clearPlannedSpeed(state: MapState): MapReduction {
        val reduction = editRoute(state) { draft ->
            if (draft.plannedSpeedKnots == null) null else draft.copy(revision = draft.revision + 1L, plannedSpeedKnots = null)
        }
        return reduction.copy(state = reduction.state.copy(routeSpeedNotice = null))
    }

    private fun previewRoutePlan(state: MapState, routeId: String): MapReduction {
        val plan = state.savedRoutes.firstOrNull { it.id == routeId }
            ?: return incident(state, MapIncident.ActionRejected)
        return persistSession(
            state.copy(
                tool = MapTool.BROWSE,
                activeRoutePlanId = plan.id,
                activeRouteDraftId = null,
                surface = MapSurface.RouteDetail(plan.id),
                transient = null,
                routeSpeedNotice = null,
            ),
        )
    }

    private fun beginRoutePlanEdit(state: MapState, routeId: String): MapReduction = ifWritable(state) {
        if (state.routeSaveTransaction?.savedPlanId == routeId) {
            return@ifWritable incident(state, MapIncident.ActionRejected)
        }
        val plan = state.savedRoutes.firstOrNull { it.id == routeId }
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        val id = nextUniqueId("draft", state.routeDrafts.map { it.id }.toSet())
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        val draft = ManualRouteDraft(
            id = id,
            revision = 1L,
            name = plan.name,
            waypoints = plan.waypoints.toList(),
            plannedSpeedKnots = plan.plannedSpeedKnots,
            notes = plan.notes,
            waypointIds = plan.waypointIds.toList(),
            waypointPlaceReferences = plan.waypointPlaceReferences.toMap(),
            basePlanId = plan.id,
            basePlanRevision = plan.revision,
            nextWaypointOrdinal = plan.waypoints.size + 1,
        )
        persistLibrary(
            state.copy(
                tool = MapTool.MANUAL_ROUTE,
                routeDrafts = state.routeDrafts + draft,
                activeRouteDraftId = draft.id,
                activeRoutePlanId = plan.id,
                surface = MapSurface.RouteDetail(draft.id),
                routeSaveStatus = null,
                routeSpeedNotice = null,
            ),
        )
    }

    private fun saveRoutePlan(state: MapState, copyName: String? = null): MapReduction = ifWritable(state) {
        if (state.routeSaveTransaction != null) return@ifWritable incident(state, MapIncident.ActionRejected)
        val draft = state.routeDraft?.takeIf { it.waypoints.size >= 2 }
            ?: return@ifWritable incident(state, MapIncident.InsufficientRoute)
        val asCopy = copyName != null
        val previous = if (!asCopy) draft.basePlanId?.let { id -> state.savedRoutes.firstOrNull { it.id == id } } else null
        if (!asCopy && draft.basePlanId != null && previous?.revision != draft.basePlanRevision) {
            return@ifWritable incident(
                state,
                MapIncident.RouteRevisionConflict(draft.basePlanId, requireNotNull(draft.basePlanRevision), previous?.revision),
            )
        }
        val planId = previous?.id ?: nextUniqueId("route", state.savedRoutes.map { it.id }.toSet())
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        val plan = SavedRoute(
            id = planId,
            name = copyName?.trim()?.takeIf(String::isNotEmpty)
                ?: draft.name.trim().ifEmpty { "Manual route ${state.savedRoutes.size + 1}" },
            waypoints = draft.waypoints.toList(),
            plannedSpeedKnots = draft.plannedSpeedKnots,
            revision = previous?.revision?.plus(1L) ?: 1L,
            sourceDraftId = draft.id,
            sourceDraftRevision = draft.revision,
            waypointPlaceReferences = draft.waypointPlaceReferences.toMap(),
            notes = draft.notes,
            waypointIds = draft.waypointIds.toList(),
        )
        val plans = if (previous == null) state.savedRoutes + plan else state.savedRoutes.map { if (it.id == plan.id) plan else it }
        val optimistic = state.copy(
            tool = MapTool.BROWSE,
            routeDrafts = state.routeDrafts.filterNot { it.id == draft.id },
            activeRouteDraftId = null,
            savedRoutes = plans,
            activeRoutePlanId = plan.id,
            routeSaveStatus = RouteSaveStatus(plan.id, plan.revision, MapSaveState.PENDING),
            routeDeleteUndo = null,
            surface = MapSurface.RouteDetail(plan.id),
            surfaceHistory = listOf(MapSurface.Root, MapSurface.Routes),
        )
        val persisted = persistLibrary(optimistic)
        val withTransaction = persisted.state.copy(
                routeSaveTransaction = RouteSaveTransaction(draft, previous, plan.id, persisted.state.libraryRevision),
            )
        persisted.copy(
            state = withTransaction,
            effects = persisted.effects + MapEffect.PersistSession(withTransaction.sessionSnapshot()),
        )
    }

    private fun duplicateRoutePlan(
        state: MapState,
        routeId: String,
        reverse: Boolean,
        name: String?,
    ): MapReduction = ifWritable(state) {
        if (state.routeSaveTransaction != null) return@ifWritable incident(state, MapIncident.ActionRejected)
        val source = state.savedRoutes.firstOrNull { it.id == routeId }
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        val id = nextUniqueId("route", state.savedRoutes.map { it.id }.toSet())
            ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        val points = if (reverse) source.waypoints.reversed() else source.waypoints.toList()
        val ids = if (reverse) source.waypointIds.reversed() else source.waypointIds.toList()
        val references = if (reverse) source.waypointPlaceReferences.entries.associate { (index, value) ->
            source.waypoints.lastIndex - index to value
        } else source.waypointPlaceReferences.toMap()
        val copy = source.copy(
            id = id,
            name = name?.trim()?.takeIf(String::isNotEmpty) ?: source.name,
            waypoints = points,
            waypointIds = ids,
            waypointPlaceReferences = references,
            revision = 1L,
            sourceDraftId = null,
            sourceDraftRevision = null,
        )
        persistLibrary(
            state.copy(
                savedRoutes = state.savedRoutes + copy,
                activeRoutePlanId = copy.id,
                routeSaveStatus = RouteSaveStatus(copy.id, copy.revision, MapSaveState.PENDING),
                routeDeleteUndo = null,
            ),
        )
    }

    private fun discardRouteDraft(state: MapState, draftId: String): MapReduction = ifWritable(state) {
        if (state.routeDrafts.none { it.id == draftId }) return@ifWritable incident(state, MapIncident.ActionRejected)
        persistLibrary(
            state.copy(
                routeDrafts = state.routeDrafts.filterNot { it.id == draftId },
                activeRouteDraftId = state.activeRouteDraftId.takeUnless { it == draftId },
                tool = if (state.activeRouteDraftId == draftId) MapTool.BROWSE else state.tool,
                surface = MapSurface.Routes,
                surfaceHistory = listOf(MapSurface.Root),
            ),
        )
    }

    private fun requestDeleteRoutePlan(state: MapState, routeId: String): MapReduction {
        if (state.routeSaveTransaction?.savedPlanId == routeId) {
            return incident(state, MapIncident.ActionRejected)
        }
        val route = state.savedRoutes.firstOrNull { it.id == routeId }
            ?: return incident(state, MapIncident.ActionRejected)
        return MapReduction(
            state.copy(routeDeleteRequest = RouteDeleteRequest(route.id, route.revision, route.name))
                .pushSurface(MapSurface.DeleteRoutePlan(route.id)),
        )
    }

    private fun confirmDeleteRoutePlan(state: MapState): MapReduction = ifWritable(state) {
        val request = state.routeDeleteRequest ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        val route = state.savedRoutes.firstOrNull { it.id == request.routeId }
            ?.takeIf { it.revision == request.expectedRevision }
            ?: return@ifWritable incident(state, MapIncident.RouteRevisionConflict(request.routeId, request.expectedRevision, null))
        persistLibrary(
            state.copy(
                savedRoutes = state.savedRoutes.filterNot { it.id == route.id },
                activeRoutePlanId = state.activeRoutePlanId.takeUnless { it == route.id },
                routeDeleteRequest = null,
                routeDeleteUndo = RouteDeleteUndo(route, state.libraryRevision + 1L),
                routeSaveStatus = null,
                surface = MapSurface.Routes,
                surfaceHistory = listOf(MapSurface.Root),
            ),
        )
    }

    private fun undoDeleteRoutePlan(state: MapState): MapReduction = ifWritable(state) {
        val undo = state.routeDeleteUndo ?: return@ifWritable incident(state, MapIncident.ActionRejected)
        if (state.libraryRevision != undo.compatibleLibraryRevision || state.savedRoutes.any { it.id == undo.route.id }) {
            return@ifWritable incident(state, MapIncident.ActionRejected)
        }
        val restored = undo.route.copy(revision = undo.route.revision + 1L)
        persistLibrary(
            state.copy(
                savedRoutes = state.savedRoutes + restored,
                routeDeleteUndo = null,
                routeSaveStatus = RouteSaveStatus(restored.id, restored.revision, MapSaveState.PENDING),
            ),
        )
    }

    private fun saveRouteCopy(state: MapState, name: String): MapReduction = saveRoutePlan(state, name)

    private fun editRoute(
        state: MapState,
        transform: (ManualRouteDraft) -> ManualRouteDraft?,
    ): MapReduction = ifWritable(state) {
        val draft = state.routeDraft ?: return@ifWritable incident(state, MapIncident.InsufficientRoute)
        val updated = transform(draft) ?: return@ifWritable MapReduction(state)
        replaceActiveDraft(state, updated)
    }

    private fun replaceActiveDraft(state: MapState, updated: ManualRouteDraft): MapReduction = persistLibrary(
        state.copy(
            routeDrafts = state.routeDrafts.map { if (it.id == updated.id) updated else it },
            routeEditNotice = null,
        ),
    )

    private fun adjacentDuplicate(state: MapState): MapReduction = MapReduction(
        state.copy(routeEditNotice = RouteEditNotice.ADJACENT_DUPLICATE),
        listOf(MapEffect.LogIncident(MapIncident.AdjacentDuplicateWaypoint)),
    )

    private fun updateChartPackages(state: MapState, input: List<ChartPackage>): MapReduction {
        val packages = input.distinctBy { it.id }
        return persistSession(
            state.copy(
                chartPackages = packages,
                activeChartPackageId = state.activeChartPackageId?.takeIf { active ->
                    packages.any { it.id == active }
                } ?: packages.lastOrNull()?.id,
            ),
        )
    }

    private fun selectChartPackage(state: MapState, packageId: ChartPackageId): MapReduction =
        if (state.chartPackages.none { it.id == packageId }) {
            incident(state, MapIncident.UnknownChartPackage(packageId))
        } else {
            persistSession(state.copy(activeChartPackageId = packageId))
        }

    private fun retryPersistence(state: MapState): MapReduction =
        if (state.saveState == MapSaveState.FAILED && state.isWritable) {
            MapReduction(
                state.copy(
                    saveState = MapSaveState.PENDING,
                    placeSaveStatus = state.placeSaveStatus?.copy(state = MapSaveState.PENDING),
                    routeSaveStatus = state.routeSaveStatus?.copy(state = MapSaveState.PENDING),
                    persistenceFailure = null,
                ),
                listOf(MapEffect.PersistLibrary(state.librarySnapshot())),
            )
        } else {
            MapReduction(state)
        }

    private fun persistenceAck(state: MapState, revision: Long): MapReduction = when {
        revision < state.libraryRevision -> {
            val completedRouteSave = state.routeSaveTransaction?.takeIf { it.targetLibraryRevision <= revision }
            if (completedRouteSave == null) {
                MapReduction(state)
            } else {
                MapReduction(
                    state.copy(
                        durableLibraryRevision = maxOf(state.durableLibraryRevision, revision),
                        routeSaveStatus = state.routeSaveStatus?.copy(state = MapSaveState.SAVED),
                        routeSaveTransaction = null,
                    ),
                )
            }
        }
        revision != state.libraryRevision -> incident(state, MapIncident.ActionRejected)
        else -> {
            val completedRouteSave = state.routeSaveTransaction?.takeIf { it.targetLibraryRevision <= revision }
            MapReduction(
            state.copy(
                durableLibraryRevision = revision,
                saveState = MapSaveState.SAVED,
                placeSaveStatus = state.placeSaveStatus?.copy(state = MapSaveState.SAVED),
                routeSaveStatus = state.routeSaveStatus?.copy(state = MapSaveState.SAVED),
                routeSaveTransaction = if (completedRouteSave != null) null else state.routeSaveTransaction,
                persistenceFailure = null,
            ),
            )
        }
    }

    private fun persistenceFailed(state: MapState, revision: Long, failure: MapReadFailure): MapReduction =
        if (revision != state.libraryRevision) {
            MapReduction(state)
        } else {
            val transaction = state.routeSaveTransaction?.takeIf { it.targetLibraryRevision <= revision }
            val rolledBackRoutes = if (transaction == null) {
                state.savedRoutes
            } else if (transaction.previousPlan == null) {
                state.savedRoutes.filterNot { it.id == transaction.savedPlanId }
            } else {
                state.savedRoutes.map { route ->
                    if (route.id == transaction.savedPlanId) transaction.previousPlan else route
                }
            }
            MapReduction(
                state.copy(
                    saveState = MapSaveState.FAILED,
                    placeSaveStatus = state.placeSaveStatus?.copy(state = MapSaveState.FAILED),
                    routeSaveStatus = if (transaction == null) {
                        state.routeSaveStatus
                    } else {
                        state.routeSaveStatus?.copy(state = MapSaveState.FAILED)
                    },
                    routeSaveTransaction = null,
                    savedRoutes = rolledBackRoutes,
                    routeDrafts = if (transaction == null || state.routeDrafts.any { it.id == transaction.draft.id }) {
                        state.routeDrafts
                    } else {
                        state.routeDrafts + transaction.draft
                    },
                    activeRouteDraftId = transaction?.draft?.id ?: state.activeRouteDraftId,
                    activeRoutePlanId = transaction?.previousPlan?.id,
                    tool = if (transaction != null) MapTool.MANUAL_ROUTE else state.tool,
                    surface = transaction?.let { MapSurface.RouteDetail(it.draft.id) } ?: state.surface,
                    persistenceFailure = failure,
                ),
                listOf(MapEffect.LogIncident(MapIncident.PersistenceFailure("save", failure))),
            )
        }

    private fun observePosition(state: MapState, action: MapAction.ObservePosition): MapReduction {
        if (state.position.observation?.observationId == action.observation.observationId) return MapReduction(state)
        return MapReduction(
            state.copy(
                position = PositionState(
                    availability = availability(action.observation, action.nowMillis),
                    observation = action.observation,
                ),
            ),
        )
    }

    private fun PositionState.at(nowMillis: Long): PositionState = observation?.let { observation ->
        copy(availability = availability(observation, nowMillis))
    } ?: PositionState()

    private fun availability(observation: PositionObservation, nowMillis: Long): PositionAvailability =
        if (nowMillis - observation.observedAtMillis <= FRESH_POSITION_WINDOW_MILLIS) {
            PositionAvailability.FRESH
        } else {
            PositionAvailability.STALE
        }

    private fun ManualRouteDraft.record(
        items: List<RouteWaypointValue>,
        ordinal: Int = nextWaypointOrdinal,
    ): ManualRouteDraft = copy(
        revision = revision + 1,
        waypoints = items.map { it.point },
        waypointIds = items.map { it.id },
        waypointPlaceReferences = items.mapIndexedNotNull { index, item -> item.source?.let { index to it } }.toMap(),
        nextWaypointOrdinal = ordinal,
        undo = boundedRouteHistory(undo + geometry()),
        redo = emptyList(),
    )

    private fun ManualRouteDraft.geometry() = RouteGeometrySnapshot(
        waypoints = waypoints,
        waypointIds = waypointIds,
        waypointPlaceReferences = waypointPlaceReferences,
        nextWaypointOrdinal = nextWaypointOrdinal,
    )

    private fun ManualRouteDraft.routeWaypoints(): List<RouteWaypointValue> = waypoints.mapIndexed { index, point ->
        RouteWaypointValue(waypointIds[index], point, waypointPlaceReferences[index])
    }

    private fun routeWaypointId(draftId: String, ordinal: Int): String = "$draftId-waypoint-$ordinal"

    private fun nextUniqueId(namespace: String, unavailable: Set<String>): String? {
        repeat(16) {
            val candidate = idGenerator.nextId(namespace).trim()
            if (candidate.isNotEmpty() && candidate !in unavailable) return candidate
        }
        return null
    }

    private fun boundedRouteHistory(history: List<RouteGeometrySnapshot>): List<RouteGeometrySnapshot> =
        history.takeLast(historyLimit)

    private fun bounded(history: List<List<GeoPoint>>): List<List<GeoPoint>> = history.takeLast(historyLimit)

    private fun List<String>.normalizedTags(): List<String> = map(String::trim).filter(String::isNotEmpty).distinct()

    private fun persistSession(state: MapState): MapReduction = MapReduction(
        state = state,
        effects = listOf(MapEffect.PersistSession(state.sessionSnapshot())),
    )

    private fun persistLibrary(state: MapState): MapReduction {
        val revision = state.libraryRevision + 1L
        val updated = state.copy(
            libraryRevision = revision,
            saveState = MapSaveState.PENDING,
            persistenceFailure = null,
            libraryLoadState = if (
                state.places.isEmpty() && state.routeDrafts.isEmpty() && state.savedRoutes.isEmpty() &&
                state.importedTracks.isEmpty() && state.gpxImportRecords.isEmpty()
            ) {
                MapLibraryLoadState.READY_EMPTY
            } else {
                MapLibraryLoadState.READY
            },
        )
        return MapReduction(updated, listOf(MapEffect.PersistLibrary(updated.librarySnapshot())))
    }

    private fun importGpxBatch(state: MapState, batch: GpxImportBatch): MapReduction {
        if (state.libraryLoadState !in setOf(MapLibraryLoadState.READY, MapLibraryLoadState.READY_EMPTY)) {
            return incident(state, MapIncident.LibraryUnavailable)
        }
        if (batch.places.isEmpty() && batch.routes.isEmpty() && batch.tracks.isEmpty()) {
            return incident(state, MapIncident.ActionRejected)
        }
        val collides = batch.places.any { incoming -> state.places.any { it.id == incoming.id } } ||
            batch.routes.any { incoming -> state.savedRoutes.any { it.id == incoming.id } } ||
            batch.tracks.any { incoming -> state.importedTracks.any { it.id == incoming.id } } ||
            state.gpxImportRecords.any { it.id == batch.importRecord.id }
        if (collides) return incident(state, MapIncident.ActionRejected)
        return persistLibrary(
            state.copy(
                places = state.places + batch.places,
                savedRoutes = state.savedRoutes + batch.routes,
                importedTracks = state.importedTracks + batch.tracks,
                gpxImportRecords = state.gpxImportRecords + batch.importRecord,
            ),
        )
    }

    private fun MapState.withCameraCommand(
        target: MapCameraTarget,
        intent: MapCameraIntent,
        viewportInsets: MapViewportInsets = MapViewportInsets(),
    ): MapState {
        val commandId = MapCameraCommandId(renderer.nextCameraCommandId)
        return copy(
            renderer = renderer.copy(
                pendingCameraCommand = MapCameraCommand(commandId, target, intent, viewportInsets),
                nextCameraCommandId = renderer.nextCameraCommandId + 1L,
            ),
        )
    }

    private inline fun ifWritable(state: MapState, block: () -> MapReduction): MapReduction =
        if (state.isWritable) block() else incident(state, MapIncident.LibraryUnavailable)

    private val MapState.isWritable: Boolean
        get() = libraryLoadState == MapLibraryLoadState.READY_EMPTY || libraryLoadState == MapLibraryLoadState.READY

    private fun incident(state: MapState, value: MapIncident): MapReduction =
        MapReduction(state, listOf(MapEffect.LogIncident(value)))

    private companion object {
        const val FRESH_POSITION_WINDOW_MILLIS = 30_000L
        const val DEFAULT_HISTORY_LIMIT = 50
        const val EXTREME_PLANNED_SPEED_KNOTS = 100.0
    }
}

private data class RouteWaypointValue(
    val id: String,
    val point: GeoPoint,
    val source: PlaceRevisionReference?,
)

/** Compatibility entrypoint for pure reducer tests and small adapters. */
object MapReducer {
    private val delegate = DefaultMapReducer()
    fun reduce(state: MapState, action: MapAction): MapReduction = delegate.reduce(state, action)
}
