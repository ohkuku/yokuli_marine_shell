package com.yokuli.marine.map.domain

sealed interface MapAction {
    data class Restore(val result: MapLoadResult) : MapAction
    data class CameraChanged(val camera: MapCamera) : MapAction
    data class SelectTool(val tool: MapTool) : MapAction
    data class LongPressMap(val point: GeoPoint) : MapAction
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
        is MapAction.LongPressMap -> MapReduction(state.copy(selection = MapSelection(action.point)))
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

    private fun selectTool(state: MapState, tool: MapTool): MapState = when (tool) {
        MapTool.MEASURE -> state.copy(tool = tool, measurementDraft = state.measurementDraft ?: MeasurementDraft())
        else -> state.copy(tool = tool)
    }

    private fun addPoint(state: MapState, point: GeoPoint): MapReduction = when (state.tool) {
        MapTool.MEASURE -> persistSession(
            state.copy(measurementDraft = (state.measurementDraft ?: MeasurementDraft()).let { draft ->
                draft.copy(points = draft.points + point)
            }),
        )
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
            waypoints = points,
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
