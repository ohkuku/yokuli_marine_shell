package com.yokuli.marine.feature.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.LocalMeasurementUnitSystem
import com.yokuli.marine.core.design.MarineDisplayUnits
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapCameraIntent
import com.yokuli.marine.map.domain.MapCameraTarget
import com.yokuli.marine.map.domain.MapHitResult
import com.yokuli.marine.map.domain.MapOverlayId
import com.yokuli.marine.map.domain.MapOrientationMode
import com.yokuli.marine.map.domain.MapReducer
import com.yokuli.marine.map.domain.MapRendererQueryPort
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapTool
import com.yokuli.marine.map.domain.MapTransient
import com.yokuli.marine.map.domain.NavigationCameraMode
import com.yokuli.marine.map.domain.minimalBounds
import com.yokuli.marine.navigation.domain.NavigationRouteMath
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint

typealias NavigationMapSurface = @Composable (
    state: MapState,
    onAction: (MapAction) -> Unit,
    onQueryPortChanged: (MapRendererQueryPort?) -> Unit,
    modifier: Modifier,
) -> Unit

@Composable
internal fun NavigationRouteMapEditor(
    draft: RouteDraftUi,
    baseState: MapState,
    chartSurface: NavigationMapSurface,
    onAction: (NavigationUiAction) -> Unit,
) {
    val currentAction by rememberUpdatedState(onAction)
    var mapState by remember(draft.id) { mutableStateOf(editorMapState(baseState, draft)) }
    val sourceSignature = draft.points.joinToString("|") { "${it.id}:${it.position.latitude}:${it.position.longitude}" }

    LaunchedEffect(
        baseState.chartDisplayPlan.fingerprint,
        baseState.mapViewMode,
        baseState.position,
    ) {
        mapState = mapState.copy(
            chartDisplayPlan = baseState.chartDisplayPlan,
            mapViewMode = baseState.mapViewMode,
            position = baseState.position,
        )
    }
    LaunchedEffect(sourceSignature) {
        if (mapState.editGesture == null) {
            mapState = mapState.withRouteDraft(draft)
        }
    }
    LaunchedEffect(draft.id) {
        draft.points.map { it.position.toGeoPoint() }.takeIf { it.isNotEmpty() }?.let(::minimalBounds)?.let { bounds ->
            mapState = MapReducer.reduce(
                mapState,
                MapAction.RequestCamera(MapCameraTarget.Bounds(bounds), MapCameraIntent.VIEW_ROUTE),
            ).state
        }
    }

    fun dispatchMap(action: MapAction) {
        val next = MapReducer.reduce(mapState, action).state
        mapState = next
        val geometry = next.routeDraft?.toNavigationPoints().orEmpty()
        if (geometry != draft.points) currentAction(NavigationUiAction.ReplaceRouteGeometry(geometry))
    }

    val spatialDraft = mapState.routeDraft
    val summary = spatialDraft?.takeIf { it.waypoints.size >= 2 }?.let { route ->
        NavigationRouteMath.summarize(
            RoutePlan("preview", 1L, draft.name, route.toNavigationPoints()),
        )
    }
    val selectedIndex = (mapState.transient as? MapTransient.SelectedObject)?.hit.routePointIndexOrNull(draft.id)
    val colors = LocalWpTheme.current
    val units = LocalMeasurementUnitSystem.current
    val displayedDistance = MarineDisplayUnits.distanceFromNauticalMiles(summary?.distanceNauticalMiles ?: 0.0, units)
    Box(Modifier.fillMaxSize().background(colors.background).testTag("navigation-route-map-editor")) {
        chartSurface(mapState, ::dispatchMap, {}, Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth().background(colors.background.copy(alpha = .92f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            WpText(draft.name, 24, weight = FontWeight.Light, maxLines = 1)
            WpText(
                stringResource(
                    if (units == com.yokuli.shell.contract.MeasurementUnitSystem.NAUTICAL) {
                        R.string.navigation_spatial_route_summary
                    } else {
                        R.string.navigation_spatial_route_summary_metric
                    },
                    draft.points.size,
                    displayedDistance,
                ),
                11,
                color = colors.muted,
            )
            WpText(stringResource(R.string.navigation_spatial_route_hint), 10, color = colors.muted)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(colors.background.copy(alpha = .95f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (selectedIndex != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    WpText(stringResource(R.string.navigation_selected_point, selectedIndex + 1), 11)
                    SpatialCommand(stringResource(R.string.navigation_delete_point), "navigation-map-delete-point") {
                        spatialDraft?.waypointIds?.getOrNull(selectedIndex)?.let { id ->
                            dispatchMap(MapAction.DeleteRouteWaypoint(id))
                            dispatchMap(MapAction.DismissTransient)
                        }
                    }
                    SpatialCommand(stringResource(R.string.navigation_close), "navigation-map-close-selection") {
                        dispatchMap(MapAction.DismissTransient)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SpatialCommand(stringResource(R.string.navigation_back), "navigation-route-map-back") {
                    currentAction(NavigationUiAction.RequestCloseRouteDraft)
                }
                SpatialCommand(
                    stringResource(R.string.navigation_save),
                    "navigation-route-map-save",
                    enabled = draft.points.size >= 2,
                ) { currentAction(NavigationUiAction.SaveRoute) }
                SpatialCommand(
                    stringResource(R.string.navigation_save_start),
                    "navigation-route-map-start",
                    enabled = draft.points.size >= 2,
                ) { currentAction(NavigationUiAction.SaveAndStartRoute) }
                SpatialCommand(stringResource(R.string.navigation_discard), "navigation-route-map-discard") {
                    currentAction(NavigationUiAction.DiscardRouteDraft)
                }
            }
        }
    }
}

@Composable
internal fun NavigationRouteMapDetail(
    route: RoutePlan,
    baseState: MapState,
    chartSurface: NavigationMapSurface,
    onAction: (NavigationUiAction) -> Unit,
) {
    NavigationRouteMapFrame(route, baseState, chartSurface, "navigation-route-map-detail") { mapModifier, _, _ ->
        val colors = LocalWpTheme.current
        Column(
            mapModifier.fillMaxWidth().background(colors.background.copy(alpha = .94f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            WpText(route.name, 24, weight = FontWeight.Light)
            val summary = NavigationRouteMath.summarize(route)
            val units = LocalMeasurementUnitSystem.current
            WpText(
                stringResource(
                    if (units == com.yokuli.shell.contract.MeasurementUnitSystem.NAUTICAL) {
                        R.string.navigation_spatial_route_summary
                    } else {
                        R.string.navigation_spatial_route_summary_metric
                    },
                    route.points.size,
                    MarineDisplayUnits.distanceFromNauticalMiles(summary.distanceNauticalMiles, units),
                ),
                11,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SpatialCommand(stringResource(R.string.navigation_back), "navigation-route-map-detail-back") {
                    onAction(NavigationUiAction.NavigateUp)
                }
                SpatialCommand(stringResource(R.string.navigation_edit), "navigation-route-map-edit") {
                    onAction(NavigationUiAction.EditRoute(route.id))
                }
                SpatialCommand(stringResource(R.string.navigation_start), "navigation-route-map-detail-start") {
                    onAction(NavigationUiAction.StartRoute(route.id, route.revision))
                }
            }
        }
    }
}

@Composable
internal fun NavigationActiveMap(
    state: NavigationUiState,
    baseState: MapState,
    chartSurface: NavigationMapSurface,
    onAction: (NavigationUiAction) -> Unit,
) {
    val route = state.active.route ?: return
    NavigationRouteMapFrame(route, baseState, chartSurface, "navigation-active-map") { mapModifier, localMap, onMapAction ->
        Column(mapModifier.fillMaxWidth()) {
            ActiveNavigationStrip(state.active, { onAction(NavigationUiAction.ActiveCommand(it)) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SpatialCommand(
                    stringResource(localMap.navigationCamera.mode.cameraLabel()),
                    "navigation-camera-mode",
                ) {
                    onMapAction(MapAction.SetNavigationCameraMode(localMap.navigationCamera.nextTrackingMode()))
                }
                SpatialCommand(
                    stringResource(localMap.navigationCamera.orientation.orientationLabel()),
                    "navigation-camera-orientation",
                ) {
                    onMapAction(MapAction.SetMapOrientationMode(localMap.navigationCamera.orientation.next()))
                }
                SpatialCommand(stringResource(R.string.navigation_recenter), "navigation-camera-recenter") {
                    onMapAction(MapAction.RecenterNavigationCamera)
                }
                SpatialCommand(stringResource(R.string.navigation_back), "navigation-active-map-back") {
                    onAction(NavigationUiAction.Navigate(NavigationSection.OVERVIEW))
                }
            }
        }
    }
}

@Composable
private fun NavigationRouteMapFrame(
    route: RoutePlan,
    baseState: MapState,
    chartSurface: NavigationMapSurface,
    tag: String,
    chrome: @Composable (Modifier, MapState, (MapAction) -> Unit) -> Unit,
) {
    var mapState by remember(route.id, route.revision) { mutableStateOf(previewMapState(baseState, route)) }
    LaunchedEffect(
        baseState.chartDisplayPlan.fingerprint,
        baseState.mapViewMode,
        baseState.position,
        baseState.activeNavigationRoute,
        baseState.activeNavigationLeg,
    ) {
        val geometry = route.points.map { it.position.toGeoPoint() }
        mapState = mapState.copy(
            chartDisplayPlan = baseState.chartDisplayPlan,
            mapViewMode = baseState.mapViewMode,
            position = baseState.position,
            activeNavigationRoute = geometry,
            activeNavigationLeg = baseState.activeNavigationLeg
                .takeIf { baseState.activeNavigationRoute == geometry }
                .orEmpty(),
        )
    }
    LaunchedEffect(route.id, route.revision) {
        route.points.map { it.position.toGeoPoint() }.takeIf { it.isNotEmpty() }?.let(::minimalBounds)?.let { bounds ->
            mapState = MapReducer.reduce(
                mapState,
                MapAction.RequestCamera(MapCameraTarget.Bounds(bounds), MapCameraIntent.VIEW_ROUTE),
            ).state
        }
    }
    Box(Modifier.fillMaxSize().testTag(tag)) {
        val dispatchMap: (MapAction) -> Unit = { action ->
            mapState = MapReducer.reduce(mapState, action).state
        }
        chartSurface(
            mapState,
            dispatchMap,
            {},
            Modifier.fillMaxSize(),
        )
        chrome(Modifier.align(Alignment.BottomCenter), mapState, dispatchMap)
    }
}

@Composable
internal fun NavigationRouteCloseConfirmation(draft: RouteDraftUi, onAction: (NavigationUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    Column(
        Modifier.fillMaxSize().background(colors.background).padding(20.dp)
            .testTag("navigation-route-close-confirmation"),
        verticalArrangement = Arrangement.Center,
    ) {
        WpText(stringResource(R.string.navigation_unsaved_route), 30, weight = FontWeight.Light)
        WpText(draft.name, 18)
        WpText(stringResource(R.string.navigation_unsaved_route_detail), 12, color = colors.muted)
        SpatialCommand(
            stringResource(R.string.navigation_save),
            "navigation-route-close-save",
            enabled = draft.points.size >= 2,
        ) { onAction(NavigationUiAction.SaveRoute) }
        SpatialCommand(stringResource(R.string.navigation_discard), "navigation-route-close-discard") {
            onAction(NavigationUiAction.DiscardRouteDraft)
        }
        SpatialCommand(stringResource(R.string.navigation_cancel), "navigation-route-close-cancel") {
            onAction(NavigationUiAction.CancelRouteClose)
        }
    }
}

@Composable
internal fun NavigationRouteMapUnavailable(onAction: (NavigationUiAction) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        WpText(stringResource(R.string.navigation_map_unavailable), 24, weight = FontWeight.Light)
        SpatialCommand(stringResource(R.string.navigation_back), "navigation-map-unavailable-back") {
            onAction(NavigationUiAction.NavigateUp)
        }
    }
}

@Composable
internal fun NavigationRouteThumbnail(route: RoutePlan, modifier: Modifier = Modifier) {
    val colors = LocalWpTheme.current
    Canvas(modifier.background(colors.chrome).testTag("navigation-route-thumbnail-${route.id}")) {
        val points = route.points.map(RoutePoint::position)
        if (points.isEmpty()) return@Canvas
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latitudeSpan = maxLat - minLat
        val longitudeSpan = maxLon - minLon
        fun offset(index: Int): Offset {
            val point = points[index]
            val x = if (longitudeSpan > 0.0) ((point.longitude - minLon) / longitudeSpan).toFloat() else .5f
            val y = if (latitudeSpan > 0.0) (1.0 - (point.latitude - minLat) / latitudeSpan).toFloat() else .5f
            return Offset(12.dp.toPx() + x * (size.width - 24.dp.toPx()), 12.dp.toPx() + y * (size.height - 24.dp.toPx()))
        }
        val offsets = points.indices.map(::offset)
        offsets.zipWithNext().forEach { (from, to) ->
            drawLine(colors.accent, from, to, 3.dp.toPx(), cap = StrokeCap.Round)
        }
        offsets.forEach { point -> drawCircle(colors.foreground, 4.dp.toPx(), point) }
    }
}

@Composable
private fun SpatialCommand(label: String, tag: String, enabled: Boolean = true, action: () -> Unit) {
    val colors = LocalWpTheme.current
    Box(
        Modifier.heightIn(min = 48.dp).clickable(enabled = enabled, onClick = action)
            .padding(horizontal = 8.dp).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        WpText(label, 11, color = if (enabled) colors.accent else colors.muted, maxLines = 1)
    }
}

private fun editorMapState(base: MapState, draft: RouteDraftUi): MapState = base.copy(
    tool = MapTool.MANUAL_ROUTE,
    transient = null,
    selection = null,
    routeDrafts = listOf(draft.toManualRouteDraft()),
    activeRouteDraftId = draft.id,
    activeRoutePlanId = null,
    activeNavigationRoute = emptyList(),
    activeNavigationLeg = emptyList(),
    activeNavigationRemainingRoute = emptyList(),
    navigationActive = false,
)

private fun previewMapState(base: MapState, route: RoutePlan): MapState {
    val geometry = route.points.map { it.position.toGeoPoint() }
    return base.copy(
        tool = MapTool.BROWSE,
        transient = null,
        selection = null,
        routeDrafts = emptyList(),
        activeRouteDraftId = null,
        activeRoutePlanId = null,
        activeNavigationRoute = geometry,
        activeNavigationLeg = base.activeNavigationLeg.takeIf { base.activeNavigationRoute == geometry }.orEmpty(),
        activeNavigationRemainingRoute = base.activeNavigationRemainingRoute
            .takeIf { base.activeNavigationRoute == geometry }
            .orEmpty()
            .ifEmpty { geometry },
        navigationActive = true,
    )
}

private fun com.yokuli.marine.map.domain.NavigationCameraState.nextTrackingMode(): NavigationCameraMode {
    val current = mode.takeUnless { it == NavigationCameraMode.FREE_BROWSE } ?: resumeMode
    return when (current) {
        NavigationCameraMode.VESSEL_FOLLOW -> NavigationCameraMode.LOOK_AHEAD
        NavigationCameraMode.LOOK_AHEAD -> NavigationCameraMode.NEXT_WAYPOINT
        NavigationCameraMode.NEXT_WAYPOINT -> NavigationCameraMode.ROUTE_OVERVIEW
        NavigationCameraMode.ROUTE_OVERVIEW -> NavigationCameraMode.VESSEL_FOLLOW
        NavigationCameraMode.FREE_BROWSE -> error("resolved above")
    }
}

private fun MapOrientationMode.next(): MapOrientationMode = when (this) {
    MapOrientationMode.NORTH_UP -> MapOrientationMode.COURSE_UP
    MapOrientationMode.COURSE_UP -> MapOrientationMode.HEADING_UP
    MapOrientationMode.HEADING_UP -> MapOrientationMode.NORTH_UP
}

private fun NavigationCameraMode.cameraLabel() = when (this) {
    NavigationCameraMode.VESSEL_FOLLOW -> R.string.navigation_camera_follow
    NavigationCameraMode.LOOK_AHEAD -> R.string.navigation_camera_look_ahead
    NavigationCameraMode.NEXT_WAYPOINT -> R.string.navigation_camera_next
    NavigationCameraMode.ROUTE_OVERVIEW -> R.string.navigation_camera_route
    NavigationCameraMode.FREE_BROWSE -> R.string.navigation_camera_browse
}

private fun MapOrientationMode.orientationLabel() = when (this) {
    MapOrientationMode.NORTH_UP -> R.string.navigation_orientation_north
    MapOrientationMode.COURSE_UP -> R.string.navigation_orientation_course
    MapOrientationMode.HEADING_UP -> R.string.navigation_orientation_heading
}

private fun MapState.withRouteDraft(draft: RouteDraftUi): MapState = copy(
    routeDrafts = listOf(draft.toManualRouteDraft()),
    activeRouteDraftId = draft.id,
)

private fun RouteDraftUi.toManualRouteDraft() = ManualRouteDraft(
    id = id,
    revision = (editingRevision ?: 0L) + 1L,
    name = name,
    waypoints = points.map { it.position.toGeoPoint() },
    plannedSpeedKnots = plannedSpeedKnots.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 },
    notes = notes,
    waypointIds = points.map(RoutePoint::id),
    nextWaypointOrdinal = points.size + 1,
)

private fun ManualRouteDraft.toNavigationPoints(): List<RoutePoint> = waypoints.mapIndexed { index, point ->
    RoutePoint(waypointIds[index], point.toNavigationPosition())
}

private fun com.yokuli.marine.navigation.domain.NavigationPosition.toGeoPoint() = GeoPoint(latitude, longitude)
private fun GeoPoint.toNavigationPosition() = com.yokuli.marine.navigation.domain.NavigationPosition(latitude, longitude)

private fun MapHitResult?.routePointIndexOrNull(draftId: String): Int? {
    if (this?.overlayId != MapOverlayId.MANUAL_ROUTE_POINTS) return null
    val prefix = "route-point:$draftId:"
    return objectId.removePrefix(prefix).takeIf { objectId.startsWith(prefix) }?.toIntOrNull()
}
