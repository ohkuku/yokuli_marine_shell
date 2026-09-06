package com.yokuli.marine.feature.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.LocalMeasurementUnitSystem
import com.yokuli.marine.core.design.MarineDisplayUnits
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.navigation.domain.NavigationRouteMath
import com.yokuli.marine.navigation.domain.NavigationSessionState
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import kotlin.math.max

@Composable
fun NavigationWorkspace(
    state: NavigationUiState,
    onAction: (NavigationUiAction) -> Unit,
    gpxState: NavigationGpxUiState = NavigationGpxUiState.Idle,
    onGpxAction: (NavigationGpxUiAction) -> Unit = {},
) {
    val currentState by rememberUpdatedState(state)
    val currentAction by rememberUpdatedState(onAction)
    BindInternalAppInputHandler { input ->
        if (input != ShellInput.BACK) false else NavigationBackPolicy.canHandle(currentState).also {
            if (it) currentAction(NavigationUiAction.NavigateUp)
        }
    }
    val colors = LocalWpTheme.current
    Column(Modifier.fillMaxSize().background(colors.background).testTag(NavigationTestTags.ROOT)) {
        WpPageHeader("navigation", stringResource(R.string.navigation_title), stringResource(sectionContext(state.section)))
        SectionStrip(state.section, onAction)
        state.notice?.let {
            WpText(
                stringResource(it.label()), 11, color = colors.accent,
                modifier = Modifier.fillMaxWidth().clickable { onAction(NavigationUiAction.DismissNotice) }
                    .padding(horizontal = YokuliMetrics.PageMargin, vertical = 6.dp).testTag("navigation-notice"),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val page = state.page) {
                NavigationPage.Root -> when (state.section) {
                    NavigationSection.OVERVIEW -> OverviewPage(state, onAction)
                    NavigationSection.WAYPOINTS -> WaypointsPage(state, onAction)
                    NavigationSection.ROUTES -> RoutesPage(state, onAction)
                    NavigationSection.GPX -> NavigationGpxWorkspace(gpxState, onGpxAction)
                    NavigationSection.ACTIVE -> ActivePage(state, onAction)
                }
                is NavigationPage.WaypointEditor -> WaypointEditor(page.draft, onAction)
                is NavigationPage.RouteDetail -> state.selectedRoute?.let { RouteDetail(it, state, onAction) }
                    ?: MissingRoute(onAction)
                is NavigationPage.RouteEditor -> RouteEditor(page.draft, state, onAction)
            }
        }
    }
}

object NavigationBackPolicy {
    fun canHandle(state: NavigationUiState): Boolean =
        state.page != NavigationPage.Root || state.section != NavigationSection.OVERVIEW
}

object NavigationTestTags {
    const val ROOT = "navigation-workspace"
    fun section(value: NavigationSection) = "navigation-section-${value.name.lowercase()}"
}

@Composable
private fun SectionStrip(selectedSection: NavigationSection, onAction: (NavigationUiAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = YokuliMetrics.PageMargin),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        NavigationSection.entries.forEach { section ->
            val interactions = remember { MutableInteractionSource() }
            WpText(
                stringResource(section.label()), 14,
                color = if (section == selectedSection) LocalWpTheme.current.accent else LocalWpTheme.current.muted,
                weight = if (section == selectedSection) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.heightIn(min = YokuliMetrics.MinTouch)
                    .clickable(interactions, null, role = Role.Tab) { onAction(NavigationUiAction.Navigate(section)) }
                    .semantics { selected = section == selectedSection }
                    .padding(vertical = 12.dp).testTag(NavigationTestTags.section(section)),
            )
        }
    }
}

@Composable
private fun OverviewPage(state: NavigationUiState, onAction: (NavigationUiAction) -> Unit) = ScrollBody("navigation-overview") {
    WpText(stringResource(R.string.navigation_overview_truth), 13, color = LocalWpTheme.current.muted)
    Spacer(Modifier.height(12.dp))
    WpText(stringResource(R.string.navigation_library_summary, state.library.waypoints.size, state.library.routePlans.size), 28, weight = FontWeight.Light)
    val session = state.active.session
    if (session != null) {
        Command(stringResource(R.string.navigation_open_active), "navigation-open-active") {
            onAction(NavigationUiAction.Navigate(NavigationSection.ACTIVE))
        }
    }
    state.library.routePlans.takeLast(5).reversed().forEach { route -> RouteRow(route, onAction) }
}

@Composable
private fun WaypointsPage(state: NavigationUiState, onAction: (NavigationUiAction) -> Unit) = ScrollBody("navigation-waypoints") {
    Command(stringResource(R.string.navigation_waypoint_new), "navigation-waypoint-new") { onAction(NavigationUiAction.CreateWaypoint) }
    if (state.library.waypoints.isEmpty()) WpText(stringResource(R.string.navigation_waypoints_empty), 23, weight = FontWeight.Light)
    state.library.waypoints.forEach { waypoint ->
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("navigation-waypoint-${waypoint.id}")) {
            WpText(waypoint.name, 20, weight = FontWeight.Light)
            WpText("%.5f, %.5f".format(waypoint.position.latitude, waypoint.position.longitude), 11, color = LocalWpTheme.current.muted)
            Row {
                Command(stringResource(R.string.navigation_edit), "navigation-waypoint-edit-${waypoint.id}", Modifier.weight(1f)) {
                    onAction(NavigationUiAction.EditWaypoint(waypoint.id))
                }
                Command(stringResource(R.string.navigation_delete), "navigation-waypoint-delete-${waypoint.id}", Modifier.weight(1f)) {
                    onAction(NavigationUiAction.DeleteWaypoint(waypoint.id, waypoint.revision))
                }
            }
        }
    }
}

@Composable
private fun RoutesPage(state: NavigationUiState, onAction: (NavigationUiAction) -> Unit) = ScrollBody("navigation-routes") {
    Command(stringResource(R.string.navigation_route_new), "navigation-route-new") { onAction(NavigationUiAction.CreateRoute) }
    if (state.library.routePlans.isEmpty()) WpText(stringResource(R.string.navigation_routes_empty), 23, weight = FontWeight.Light)
    state.library.routePlans.forEach { RouteRow(it, onAction) }
}

@Composable
private fun RouteRow(route: RoutePlan, onAction: (NavigationUiAction) -> Unit) {
    val summary = NavigationRouteMath.summarize(route)
    Column(
        Modifier.fillMaxWidth().clickable { onAction(NavigationUiAction.OpenRoute(route.id)) }
            .padding(vertical = 10.dp).testTag("navigation-route-${route.id}"),
    ) {
        WpText(route.name, 20, weight = FontWeight.Light)
        WpText(routeSummary(route.points.size, summary.distanceNauticalMiles), 11, color = LocalWpTheme.current.muted)
    }
}

@Composable
private fun WaypointEditor(draft: WaypointDraftUi, onAction: (NavigationUiAction) -> Unit) = ScrollBody("navigation-waypoint-editor") {
    Field(stringResource(R.string.navigation_name), draft.name, "navigation-waypoint-name") {
        onAction(NavigationUiAction.UpdateWaypointDraft(name = it))
    }
    Field(stringResource(R.string.navigation_latitude), draft.latitude, "navigation-waypoint-latitude") {
        onAction(NavigationUiAction.UpdateWaypointDraft(latitude = it))
    }
    Field(stringResource(R.string.navigation_longitude), draft.longitude, "navigation-waypoint-longitude") {
        onAction(NavigationUiAction.UpdateWaypointDraft(longitude = it))
    }
    Field(stringResource(R.string.navigation_notes), draft.notes, "navigation-waypoint-notes") {
        onAction(NavigationUiAction.UpdateWaypointDraft(notes = it))
    }
    Command(stringResource(R.string.navigation_save), "navigation-waypoint-save") { onAction(NavigationUiAction.SaveWaypoint) }
}

@Composable
private fun RouteDetail(route: RoutePlan, state: NavigationUiState, onAction: (NavigationUiAction) -> Unit) = ScrollBody("navigation-route-detail") {
    WpText(route.name, 28, weight = FontWeight.Light)
    RouteSketch(route)
    val summary = NavigationRouteMath.summarize(route)
    WpText(routeSummary(route.points.size, summary.distanceNauticalMiles), 13)
    summary.legs.forEach { leg ->
        val units = LocalMeasurementUnitSystem.current
        val distance = MarineDisplayUnits.distanceFromNauticalMiles(leg.distanceMeters / 1852.0, units)
        WpText(
            stringResource(
                if (units == com.yokuli.shell.contract.MeasurementUnitSystem.NAUTICAL) R.string.navigation_leg else R.string.navigation_leg_metric,
                leg.index + 1, leg.index + 2, distance, leg.initialBearingTrueDegrees ?: 0.0,
            ),
            11, color = LocalWpTheme.current.muted,
        )
    }
    Command(stringResource(R.string.navigation_start), "navigation-route-start-${route.id}") {
        onAction(NavigationUiAction.StartRoute(route.id, route.revision))
    }
    Command(stringResource(R.string.navigation_show_chart), "navigation-route-chart-${route.id}") {
        onAction(NavigationUiAction.ShowRouteInChart(route.id))
    }
    Command(stringResource(R.string.navigation_edit), "navigation-route-edit-${route.id}") {
        onAction(NavigationUiAction.EditRoute(route.id))
    }
    Command(
        stringResource(R.string.navigation_delete), "navigation-route-delete-${route.id}",
        enabled = state.active.session?.routeId != route.id,
    ) { onAction(NavigationUiAction.DeleteRoute(route.id, route.revision)) }
}

@Composable
private fun routeSummary(pointCount: Int, nauticalMiles: Double): String {
    val units = LocalMeasurementUnitSystem.current
    return stringResource(
        if (units == com.yokuli.shell.contract.MeasurementUnitSystem.NAUTICAL) {
            R.string.navigation_route_summary
        } else {
            R.string.navigation_route_summary_metric
        },
        pointCount,
        MarineDisplayUnits.distanceFromNauticalMiles(nauticalMiles, units),
    )
}

@Composable
private fun MissingRoute(onAction: (NavigationUiAction) -> Unit) = ScrollBody("navigation-route-missing") {
    WpText(stringResource(R.string.navigation_route_missing), 24, weight = FontWeight.Light)
    WpText(stringResource(R.string.navigation_route_missing_detail), 12, color = LocalWpTheme.current.muted)
    Command(stringResource(R.string.navigation_back_routes), "navigation-route-missing-back") {
        onAction(NavigationUiAction.Navigate(NavigationSection.ROUTES))
    }
}

@Composable
private fun RouteEditor(draft: RouteDraftUi, state: NavigationUiState, onAction: (NavigationUiAction) -> Unit) = ScrollBody("navigation-route-editor") {
    Field(stringResource(R.string.navigation_name), draft.name, "navigation-route-name") {
        onAction(NavigationUiAction.UpdateRouteDraft(name = it))
    }
    Field(stringResource(R.string.navigation_speed), draft.plannedSpeedKnots, "navigation-route-speed") {
        onAction(NavigationUiAction.UpdateRouteDraft(plannedSpeedKnots = it))
    }
    Field(stringResource(R.string.navigation_notes), draft.notes, "navigation-route-notes") {
        onAction(NavigationUiAction.UpdateRouteDraft(notes = it))
    }
    WpText(stringResource(R.string.navigation_route_points, draft.points.size), 13)
    draft.points.forEachIndexed { index, point ->
        val waypointName = point.sourceWaypoint?.waypointId?.let { id -> state.library.waypoints.firstOrNull { it.id == id }?.name }
        Column(Modifier.fillMaxWidth().border(1.dp, LocalWpTheme.current.muted).padding(6.dp)) {
            WpText(waypointName ?: stringResource(R.string.navigation_route_point, index + 1), 14)
            Row {
                Command("↑", "navigation-point-up-${point.id}", Modifier.weight(1f), index > 0) {
                    onAction(NavigationUiAction.MoveRoutePoint(point.id, -1))
                }
                Command("↓", "navigation-point-down-${point.id}", Modifier.weight(1f), index < draft.points.lastIndex) {
                    onAction(NavigationUiAction.MoveRoutePoint(point.id, 1))
                }
                Command("×", "navigation-point-remove-${point.id}", Modifier.weight(1f)) {
                    onAction(NavigationUiAction.RemoveRoutePoint(point.id))
                }
            }
        }
    }
    WpText(stringResource(R.string.navigation_add_waypoint), 13, color = LocalWpTheme.current.muted)
    state.library.waypoints.forEach { waypoint ->
        Command(waypoint.name, "navigation-add-waypoint-${waypoint.id}") { onAction(NavigationUiAction.AddWaypointToRoute(waypoint.id)) }
    }
    Command(stringResource(R.string.navigation_save), "navigation-route-save", enabled = draft.points.size >= 2) {
        onAction(NavigationUiAction.SaveRoute)
    }
}

@Composable
private fun ActivePage(state: NavigationUiState, onAction: (NavigationUiAction) -> Unit) = ScrollBody("navigation-active") {
    if (state.active.session == null) {
        WpText(stringResource(R.string.navigation_active_empty), 24, weight = FontWeight.Light)
        WpText(stringResource(R.string.navigation_active_empty_detail), 12, color = LocalWpTheme.current.muted)
        return@ScrollBody
    }
    ActiveNavigationStrip(state.active, { onAction(NavigationUiAction.ActiveCommand(it)) })
    state.active.route?.let { route ->
        RouteSketch(route)
        Command(stringResource(R.string.navigation_show_chart), "navigation-active-chart") {
            onAction(NavigationUiAction.ShowRouteInChart(route.id))
        }
    }
    if (state.active.sessionState == NavigationSessionState.PAUSED) {
        WpText(stringResource(R.string.navigation_restore_truth), 11, color = LocalWpTheme.current.muted)
    }
}

@Composable
private fun RouteSketch(route: RoutePlan) {
    val colors = LocalWpTheme.current
    Canvas(Modifier.fillMaxWidth().height(170.dp).background(colors.chrome).testTag("navigation-route-sketch")) {
        if (route.points.isEmpty()) return@Canvas
        val minLat = route.points.minOf { it.position.latitude }
        val maxLat = route.points.maxOf { it.position.latitude }
        val minLon = route.points.minOf { it.position.longitude }
        val maxLon = route.points.maxOf { it.position.longitude }
        val latSpan = max(maxLat - minLat, 1e-9)
        val lonSpan = max(maxLon - minLon, 1e-9)
        val points = route.points.map {
            Offset(
                x = (size.width * .08f + size.width * .84f * ((it.position.longitude - minLon) / lonSpan)).toFloat(),
                y = (size.height * .92f - size.height * .84f * ((it.position.latitude - minLat) / latSpan)).toFloat(),
            )
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, colors.accent, style = Stroke(width = 3.dp.toPx()))
        points.forEachIndexed { index, point -> drawCircle(if (index == points.lastIndex) colors.accent else colors.foreground, 5.dp.toPx(), point) }
    }
}

@Composable
private fun ScrollBody(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = YokuliMetrics.PageMargin, vertical = 12.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

@Composable
private fun Field(label: String, value: String, tag: String, onChange: (String) -> Unit) {
    Column {
        WpText(label, 10, color = LocalWpTheme.current.muted)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(LocalWpTheme.current.foreground, fontSize = 16.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch).border(1.dp, LocalWpTheme.current.muted).padding(8.dp).testTag(tag),
        )
    }
}

@Composable
private fun Command(label: String, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true, action: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch)
            .clickable(interactions, indication = null, enabled = enabled, role = Role.Button, onClick = action)
            .padding(horizontal = 4.dp).testTag(tag),
    ) {
        WpText(label, 13, color = if (enabled) LocalWpTheme.current.accent else LocalWpTheme.current.muted)
    }
}

private fun NavigationSection.label() = when (this) {
    NavigationSection.OVERVIEW -> R.string.navigation_section_overview
    NavigationSection.WAYPOINTS -> R.string.navigation_section_waypoints
    NavigationSection.ROUTES -> R.string.navigation_section_routes
    NavigationSection.GPX -> R.string.navigation_section_gpx
    NavigationSection.ACTIVE -> R.string.navigation_section_active
}

private fun sectionContext(section: NavigationSection) = when (section) {
    NavigationSection.OVERVIEW -> R.string.navigation_context_overview
    NavigationSection.WAYPOINTS -> R.string.navigation_context_waypoints
    NavigationSection.ROUTES -> R.string.navigation_context_routes
    NavigationSection.GPX -> R.string.navigation_context_gpx
    NavigationSection.ACTIVE -> R.string.navigation_context_active
}

private fun NavigationNotice.label() = when (this) {
    NavigationNotice.SAVED -> R.string.navigation_saved
    NavigationNotice.DELETED -> R.string.navigation_deleted
    NavigationNotice.REVISION_CONFLICT -> R.string.navigation_conflict
    NavigationNotice.INVALID_INPUT -> R.string.navigation_invalid
    NavigationNotice.STORAGE_FAILED -> R.string.navigation_storage_failed
    NavigationNotice.ACTIVE_ROUTE_LOCKED -> R.string.navigation_active_locked
    NavigationNotice.ACTION_QUEUE_FULL -> R.string.navigation_queue_full
    NavigationNotice.NAVIGATION_REJECTED -> R.string.navigation_start_rejected
}
