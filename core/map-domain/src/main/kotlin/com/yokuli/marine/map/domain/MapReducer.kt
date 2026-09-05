package com.yokuli.marine.map.domain

sealed interface MapAction {
    data class Restore(val snapshot: MapPersistedState) : MapAction
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
}

sealed interface MapIncident {
    data class InvalidPlannedSpeed(val knots: Double) : MapIncident
    data object MissingSelection : MapIncident
    data object InsufficientMeasurement : MapIncident
    data object InsufficientRoute : MapIncident
    data class UnknownChartPackage(val packageId: ChartPackageId) : MapIncident
    data class PersistenceFailure(val operation: String, val detail: String) : MapIncident
}

sealed interface MapEffect {
    data class Persist(val snapshot: MapPersistedState) : MapEffect
    data class LogIncident(val incident: MapIncident) : MapEffect
}

data class MapReduction(val state: MapState, val effects: List<MapEffect> = emptyList())

object MapReducer {
    private const val FRESH_POSITION_WINDOW_MILLIS = 30_000L

    fun reduce(state: MapState, action: MapAction): MapReduction = when (action) {
        is MapAction.Restore -> MapReduction(action.snapshot.toRuntimeState())
        is MapAction.CameraChanged -> persist(state.copy(camera = action.camera))
        is MapAction.SelectTool -> MapReduction(selectTool(state, action.tool))
        is MapAction.LongPressMap -> MapReduction(state.copy(selection = MapSelection(action.point)))
        is MapAction.AddPoint -> addPoint(state, action.point)
        is MapAction.SaveSelectionAsPlace -> savePlace(state, action.name)
        is MapAction.ConvertMeasurementToManualRoute -> convertMeasurement(state, action.name)
        MapAction.UndoRouteEdit -> editRoute(state) { draft ->
            val previous = draft.undo.lastOrNull() ?: return@editRoute null
            draft.copy(
                waypoints = previous,
                undo = draft.undo.dropLast(1),
                redo = draft.redo + listOf(draft.waypoints),
            )
        }
        MapAction.RedoRouteEdit -> editRoute(state) { draft ->
            val next = draft.redo.lastOrNull() ?: return@editRoute null
            draft.copy(
                waypoints = next,
                undo = draft.undo + listOf(draft.waypoints),
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
        is MapAction.ChartPackagesChanged -> {
            val packages = action.packages.distinctBy { it.id }
            persist(
                state.copy(
                    chartPackages = packages,
                    activeChartPackageId = state.activeChartPackageId?.takeIf { active ->
                        packages.any { it.id == active }
                    } ?: packages.lastOrNull()?.id,
                ),
            )
        }
        is MapAction.SelectChartPackage -> if (state.chartPackages.none { it.id == action.packageId }) {
            incident(state, MapIncident.UnknownChartPackage(action.packageId))
        } else {
            persist(state.copy(activeChartPackageId = action.packageId))
        }
    }

    private fun selectTool(state: MapState, tool: MapTool): MapState = when (tool) {
        MapTool.MEASURE -> state.copy(tool = tool, measurementDraft = state.measurementDraft ?: MeasurementDraft())
        MapTool.MANUAL_ROUTE -> state.copy(tool = tool, routeDraft = state.routeDraft ?: ManualRouteDraft())
        else -> state.copy(tool = tool)
    }

    private fun addPoint(state: MapState, point: GeoPoint): MapReduction = when (state.tool) {
        MapTool.MEASURE -> persist(
            state.copy(measurementDraft = (state.measurementDraft ?: MeasurementDraft()).let { draft ->
                draft.copy(points = draft.points + point)
            }),
        )
        MapTool.MANUAL_ROUTE -> persist(
            state.copy(routeDraft = (state.routeDraft ?: ManualRouteDraft()).let { draft ->
                draft.record(draft.waypoints + point)
            }),
        )
        else -> MapReduction(state.copy(selection = MapSelection(point)))
    }

    private fun savePlace(state: MapState, name: String): MapReduction {
        val selection = state.selection ?: return incident(state, MapIncident.MissingSelection)
        val displayName = name.trim().ifEmpty { "Place ${state.places.size + 1}" }
        return persist(
            state.copy(
                places = state.places + SavedPlace("place-${state.places.size + 1}", displayName, selection.point),
                selection = null,
            ),
        )
    }

    private fun convertMeasurement(state: MapState, name: String): MapReduction {
        val points = state.measurementDraft?.points.orEmpty()
        if (points.size < 2) return incident(state, MapIncident.InsufficientMeasurement)
        return persist(
            state.copy(
                tool = MapTool.MANUAL_ROUTE,
                routeDraft = ManualRouteDraft(name = name.trim(), waypoints = points),
            ),
        )
    }

    private fun setPlannedSpeed(state: MapState, knots: Double): MapReduction {
        if (!knots.isFinite() || knots <= 0.0) return incident(state, MapIncident.InvalidPlannedSpeed(knots))
        val draft = state.routeDraft ?: return incident(state, MapIncident.InsufficientRoute)
        return persist(state.copy(routeDraft = draft.copy(plannedSpeedKnots = knots)))
    }

    private fun saveRouteCopy(state: MapState, name: String): MapReduction {
        val draft = state.routeDraft?.takeIf { it.waypoints.size >= 2 }
            ?: return incident(state, MapIncident.InsufficientRoute)
        val displayName = name.trim().ifEmpty { "Manual route ${state.savedRoutes.size + 1}" }
        return persist(
            state.copy(
                savedRoutes = state.savedRoutes + SavedRoute(
                    id = "route-${state.savedRoutes.size + 1}",
                    name = displayName,
                    waypoints = draft.waypoints,
                    plannedSpeedKnots = draft.plannedSpeedKnots,
                ),
            ),
        )
    }

    private fun editRoute(
        state: MapState,
        transform: (ManualRouteDraft) -> ManualRouteDraft?,
    ): MapReduction {
        val draft = state.routeDraft ?: return incident(state, MapIncident.InsufficientRoute)
        val updated = transform(draft) ?: return MapReduction(state)
        return persist(state.copy(routeDraft = updated))
    }

    private fun observePosition(state: MapState, action: MapAction.ObservePosition): MapReduction {
        if (state.position.observation?.observationId == action.observation.observationId) {
            return MapReduction(state)
        }
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
        waypoints = points,
        undo = undo + listOf(waypoints),
        redo = emptyList(),
    )

    private fun persist(state: MapState): MapReduction = MapReduction(
        state = state,
        effects = listOf(MapEffect.Persist(state.persisted())),
    )

    private fun incident(state: MapState, value: MapIncident): MapReduction =
        MapReduction(state, listOf(MapEffect.LogIncident(value)))
}

private fun MapPersistedState.toRuntimeState(): MapState = MapState(
    camera = camera,
    places = places,
    measurementDraft = measurementDraft,
    routeDraft = routeDraft,
    savedRoutes = savedRoutes,
    chartPackages = chartPackages,
    activeChartPackageId = activeChartPackageId,
    position = PositionState(),
    navigationActive = false,
)
