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
    data class ConvertMeasurementToManualRoute(val name: String) : MapAction
    data object UndoRouteEdit : MapAction
    data object RedoRouteEdit : MapAction
    data object ReverseRoute : MapAction
    data class SetPlannedSpeedKnots(val knots: Double) : MapAction
    data class SaveRouteCopy(val name: String) : MapAction
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
) {
    init {
        require(historyLimit > 0)
    }

    fun reduce(state: MapState, action: MapAction): MapReduction = when (action) {
        is MapAction.Restore -> restore(state, action.result)
        is MapAction.CameraChanged -> persistSession(state.copy(camera = action.camera))
        is MapAction.SelectTool -> MapReduction(selectTool(state, action.tool))
        is MapAction.OpenSurface -> MapReduction(openSurface(state, action.surface))
        MapAction.CloseSurface -> MapReduction(state.copy(surface = MapSurface.Root))
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
        is MapAction.ConvertMeasurementToManualRoute -> convertMeasurement(state, action.name)
        MapAction.UndoRouteEdit -> editRoute(state) { draft ->
            val previous = draft.undo.lastOrNull() ?: return@editRoute null
            draft.copy(
                revision = draft.revision + 1,
                waypoints = previous,
                undo = draft.undo.dropLast(1),
                redo = bounded(draft.redo + listOf(draft.waypoints)),
            )
        }
        MapAction.RedoRouteEdit -> editRoute(state) { draft ->
            val next = draft.redo.lastOrNull() ?: return@editRoute null
            draft.copy(
                revision = draft.revision + 1,
                waypoints = next,
                undo = bounded(draft.undo + listOf(draft.waypoints)),
                redo = draft.redo.dropLast(1),
            )
        }
        MapAction.ReverseRoute -> editRoute(state) { draft ->
            if (draft.waypoints.size < 2) null else draft.record(draft.waypoints.reversed())
        }
        is MapAction.SetPlannedSpeedKnots -> setPlannedSpeed(state, action.knots)
        is MapAction.SaveRouteCopy -> saveRouteCopy(state, action.name)
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
            MapReduction(
                state.copy(
                    camera = result.session.camera,
                    measurementDraft = result.session.measurementDraft,
                    places = library.places,
                    routeDrafts = library.routeDrafts,
                    activeRouteDraftId = activeDraftId,
                    savedRoutes = library.savedRoutes,
                    activeChartPackageId = result.session.activeChartPackageId,
                    libraryLoadState = if (library.isEmpty) MapLibraryLoadState.READY_EMPTY else MapLibraryLoadState.READY,
                    libraryRevision = library.revision,
                    durableLibraryRevision = library.revision,
                    saveState = MapSaveState.SAVED,
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
        is MapSurface.PlaceDetail -> if (state.places.any { it.id == surface.placeId }) {
            state.copy(surface = surface, transient = null)
        } else {
            state.copy(surface = MapSurface.Root, transient = MapTransient.UnavailableObject(surface.placeId))
        }
        is MapSurface.RouteDetail -> if (state.savedRoutes.any { it.id == surface.routeId } ||
            state.routeDrafts.any { it.id == surface.routeId }
        ) {
            state.copy(surface = surface, transient = null)
        } else {
            state.copy(surface = MapSurface.Root, transient = MapTransient.UnavailableObject(surface.routeId))
        }
        is MapSurface.ChartPackageDetail -> if (state.chartPackages.any { it.id == surface.packageId }) {
            state.copy(surface = surface, transient = null)
        } else {
            state.copy(surface = MapSurface.Root, transient = MapTransient.UnavailableObject(surface.packageId.value))
        }
        else -> state.copy(surface = surface, transient = null)
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
        }
    }

    private fun commitPointEdit(state: MapState, target: MapEditTarget, point: GeoPoint): MapReduction = when (target) {
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
            val updated = draft.record(
                draft.waypoints.mapIndexed { index, existing -> if (index == target.index) point else existing },
            )
            persistLibrary(
                state.copy(routeDrafts = state.routeDrafts.map { if (it.id == updated.id) updated else it }),
            )
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
        MapTool.MANUAL_ROUTE -> ifWritable(state) {
            val active = state.routeDraft
            if (active == null) {
                val draft = ManualRouteDraft(
                    id = idGenerator.nextId("draft"),
                    revision = 1L,
                    waypoints = listOf(point),
                )
                persistLibrary(state.copy(routeDrafts = state.routeDrafts + draft, activeRouteDraftId = draft.id))
            } else {
                replaceActiveDraft(state, active.record(active.waypoints + point))
            }
        }
        else -> MapReduction(state.copy(selection = MapSelection(point)))
    }

    private fun savePlace(state: MapState, name: String): MapReduction = ifWritable(state) {
        val selection = state.selection ?: return@ifWritable incident(state, MapIncident.MissingSelection)
        val displayName = name.trim().ifEmpty { "Place ${state.places.size + 1}" }
        persistLibrary(
            state.copy(
                places = state.places + SavedPlace(
                    id = idGenerator.nextId("place"),
                    name = displayName,
                    point = selection.point,
                ),
                selection = null,
            ),
        )
    }

    private fun convertMeasurement(state: MapState, name: String): MapReduction = ifWritable(state) {
        val points = state.measurementDraft?.points.orEmpty()
        if (points.size < 2) return@ifWritable incident(state, MapIncident.InsufficientMeasurement)
        val draft = ManualRouteDraft(
            id = idGenerator.nextId("draft"),
            revision = 1L,
            name = name.trim(),
            waypoints = points.toList(),
        )
        persistLibrary(
            state.copy(
                tool = MapTool.MANUAL_ROUTE,
                routeDrafts = state.routeDrafts + draft,
                activeRouteDraftId = draft.id,
            ),
        )
    }

    private fun setPlannedSpeed(state: MapState, knots: Double): MapReduction {
        if (!knots.isFinite() || knots <= 0.0) return incident(state, MapIncident.InvalidPlannedSpeed(knots))
        return editRoute(state) { draft -> draft.copy(revision = draft.revision + 1, plannedSpeedKnots = knots) }
    }

    private fun saveRouteCopy(state: MapState, name: String): MapReduction = ifWritable(state) {
        val draft = state.routeDraft?.takeIf { it.waypoints.size >= 2 }
            ?: return@ifWritable incident(state, MapIncident.InsufficientRoute)
        if (state.savedRoutes.any { it.sourceDraftId == draft.id && it.sourceDraftRevision == draft.revision }) {
            return@ifWritable MapReduction(state)
        }
        val displayName = name.trim().ifEmpty { "Manual route ${state.savedRoutes.size + 1}" }
        persistLibrary(
            state.copy(
                savedRoutes = state.savedRoutes + SavedRoute(
                    id = idGenerator.nextId("route"),
                    name = displayName,
                    waypoints = draft.waypoints,
                    plannedSpeedKnots = draft.plannedSpeedKnots,
                    sourceDraftId = draft.id,
                    sourceDraftRevision = draft.revision,
                ),
            ),
        )
    }

    private fun editRoute(
        state: MapState,
        transform: (ManualRouteDraft) -> ManualRouteDraft?,
    ): MapReduction = ifWritable(state) {
        val draft = state.routeDraft ?: return@ifWritable incident(state, MapIncident.InsufficientRoute)
        val updated = transform(draft) ?: return@ifWritable MapReduction(state)
        replaceActiveDraft(state, updated)
    }

    private fun replaceActiveDraft(state: MapState, updated: ManualRouteDraft): MapReduction = persistLibrary(
        state.copy(routeDrafts = state.routeDrafts.map { if (it.id == updated.id) updated else it }),
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
                state.copy(saveState = MapSaveState.PENDING, persistenceFailure = null),
                listOf(MapEffect.PersistLibrary(state.librarySnapshot())),
            )
        } else {
            MapReduction(state)
        }

    private fun persistenceAck(state: MapState, revision: Long): MapReduction = when {
        revision < state.libraryRevision -> MapReduction(state)
        revision != state.libraryRevision -> incident(state, MapIncident.ActionRejected)
        else -> MapReduction(
            state.copy(
                durableLibraryRevision = revision,
                saveState = MapSaveState.SAVED,
                persistenceFailure = null,
            ),
        )
    }

    private fun persistenceFailed(state: MapState, revision: Long, failure: MapReadFailure): MapReduction =
        if (revision != state.libraryRevision) {
            MapReduction(state)
        } else {
            MapReduction(
                state.copy(saveState = MapSaveState.FAILED, persistenceFailure = failure),
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

    private fun ManualRouteDraft.record(points: List<GeoPoint>): ManualRouteDraft = copy(
        revision = revision + 1,
        waypoints = points,
        undo = bounded(undo + listOf(waypoints)),
        redo = emptyList(),
    )

    private fun bounded(history: List<List<GeoPoint>>): List<List<GeoPoint>> = history.takeLast(historyLimit)

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
            libraryLoadState = if (state.places.isEmpty() && state.routeDrafts.isEmpty() && state.savedRoutes.isEmpty()) {
                MapLibraryLoadState.READY_EMPTY
            } else {
                MapLibraryLoadState.READY
            },
        )
        return MapReduction(updated, listOf(MapEffect.PersistLibrary(updated.librarySnapshot())))
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
    }
}

/** Compatibility entrypoint for pure reducer tests and small adapters. */
object MapReducer {
    private val delegate = DefaultMapReducer()
    fun reduce(state: MapState, action: MapAction): MapReduction = delegate.reduce(state, action)
}
