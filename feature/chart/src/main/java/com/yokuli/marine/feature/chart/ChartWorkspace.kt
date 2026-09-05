package com.yokuli.marine.feature.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliColors
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapTool
import com.yokuli.marine.map.domain.PositionAvailability
import java.util.Locale

typealias MarineChartSurface = @Composable (
    state: MapState,
    onCameraChanged: (MapCamera) -> Unit,
    onLongPress: (GeoPoint) -> Unit,
    modifier: Modifier,
) -> Unit

/**
 * One installed Map app. Browse, places, measurement, manual routes, and chart packages are
 * internal app tools, never separate Shell entries or implied navigation capabilities.
 */
@Composable
fun ChartWorkspace(
    state: MapState,
    mapConfigured: Boolean,
    onAction: (MapAction) -> Unit,
    onOpenMapSettings: () -> Unit,
    onImportChart: () -> Unit,
    chartSurface: MarineChartSurface,
) {
    val colors = LocalWpTheme.current
    Box(
        Modifier.fillMaxSize().background(YokuliColors.ChartWater).testTag("chart-workspace-browse"),
    ) {
        chartSurface(
            state,
            { onAction(MapAction.CameraChanged(it)) },
            { point ->
                onAction(
                    if (state.tool == MapTool.MEASURE || state.tool == MapTool.MANUAL_ROUTE) {
                        MapAction.AddPoint(point)
                    } else {
                        MapAction.LongPressMap(point)
                    },
                )
            },
            Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize()) {
            WpPageHeader(
                appKey = "map",
                appName = stringResource(R.string.app_chart),
                contextLine = stringResource(R.string.map_context_offline_first),
                modifier = Modifier.background(colors.background.copy(alpha = .90f)),
            )
            PositionTruthBadge(state.position.availability)
            Spacer(Modifier.weight(1f))
            MapToolPanel(
                state = state,
                mapConfigured = mapConfigured,
                onAction = onAction,
                onOpenMapSettings = onOpenMapSettings,
                onImportChart = onImportChart,
            )
            MapToolBar(state.tool, onAction)
        }
    }
}

@Composable
private fun PositionTruthBadge(availability: PositionAvailability) {
    val colors = LocalWpTheme.current
    val label = when (availability) {
        PositionAvailability.UNAVAILABLE -> stringResource(R.string.map_position_unavailable)
        PositionAvailability.STALE -> stringResource(R.string.map_position_stale)
        PositionAvailability.FRESH -> stringResource(R.string.map_position_fresh)
    }
    Box(
        Modifier.padding(start = 18.dp, top = 8.dp).background(colors.background.copy(alpha = .86f))
            .padding(horizontal = 9.dp, vertical = 5.dp).testTag("map-position-truth"),
    ) {
        WpText(label, 11, color = colors.foreground)
    }
}

@Composable
private fun MapToolBar(selected: MapTool, onAction: (MapAction) -> Unit) {
    val colors = LocalWpTheme.current
    Row(
        Modifier.fillMaxWidth().background(colors.background.copy(alpha = .96f))
            .horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("map-tool-bar"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MapTool.entries.forEach { tool ->
            val label = stringResource(
                when (tool) {
                    MapTool.BROWSE -> R.string.map_tool_browse
                    MapTool.PLACES -> R.string.map_tool_places
                    MapTool.MEASURE -> R.string.map_tool_measure
                    MapTool.MANUAL_ROUTE -> R.string.map_tool_routes
                    MapTool.CHARTS -> R.string.map_tool_charts
                },
            )
            Box(
                Modifier.clickNoRipple { onAction(MapAction.SelectTool(tool)) }
                    .background(if (tool == selected) colors.accent else colors.background)
                    .padding(horizontal = 13.dp, vertical = 11.dp).testTag("map-tool-${tool.name.lowercase()}"),
            ) {
                WpText(label, 12, color = if (tool == selected) colors.onAccent else colors.foreground)
            }
        }
    }
}

@Composable
private fun MapToolPanel(
    state: MapState,
    mapConfigured: Boolean,
    onAction: (MapAction) -> Unit,
    onOpenMapSettings: () -> Unit,
    onImportChart: () -> Unit,
) {
    val colors = LocalWpTheme.current
    Column(
        Modifier.fillMaxWidth().heightIn(max = 230.dp).background(colors.background.copy(alpha = .94f))
            .padding(horizontal = 18.dp, vertical = 11.dp).testTag("map-tool-panel"),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when (state.tool) {
            MapTool.BROWSE -> BrowsePanel(state, onAction)
            MapTool.PLACES -> {
                WpText(stringResource(R.string.map_places_title), 22, weight = FontWeight.Light)
                WpText(
                    if (state.places.isEmpty()) stringResource(R.string.map_places_empty)
                    else stringResource(R.string.map_places_count, state.places.size),
                    12,
                    color = colors.muted,
                )
            }
            MapTool.MEASURE -> {
                val count = state.measurementDraft?.points?.size ?: 0
                WpText(stringResource(R.string.map_measure_title), 22, weight = FontWeight.Light)
                WpText(stringResource(R.string.map_points_count, count), 12, color = colors.muted)
                if (count >= 2) {
                    MapActionText(R.string.map_convert_to_route, "map-measure-convert") {
                        onAction(MapAction.ConvertMeasurementToManualRoute("Manual route"))
                    }
                }
            }
            MapTool.MANUAL_ROUTE -> RoutePanel(state, onAction)
            MapTool.CHARTS -> ChartPackagesPanel(state, mapConfigured, onOpenMapSettings, onImportChart)
        }
    }
}

@Composable
private fun BrowsePanel(state: MapState, onAction: (MapAction) -> Unit) {
    val colors = LocalWpTheme.current
    val selection = state.selection
    if (selection == null) {
        WpText(stringResource(R.string.map_browse_title), 22, weight = FontWeight.Light)
        WpText(stringResource(R.string.map_browse_hint), 12, color = colors.muted)
    } else {
        WpText(stringResource(R.string.map_selected_point), 22, weight = FontWeight.Light)
        WpText(
            String.format(Locale.US, "%.5f, %.5f", selection.point.latitude, selection.point.longitude),
            12,
            color = colors.muted,
        )
        MapActionText(R.string.map_save_place, "map-save-place") {
            onAction(MapAction.SaveSelectionAsPlace("Saved place"))
        }
    }
}

@Composable
private fun RoutePanel(state: MapState, onAction: (MapAction) -> Unit) {
    val colors = LocalWpTheme.current
    val draft = state.routeDraft
    val count = draft?.waypoints?.size ?: 0
    WpText(stringResource(R.string.map_manual_route_title), 22, weight = FontWeight.Light)
    WpText(stringResource(R.string.map_manual_route_truth), 11, color = colors.accent)
    val summary = state.routeSummary
    WpText(
        if (summary == null) stringResource(R.string.map_points_count, count)
        else stringResource(
            R.string.map_route_summary,
            summary.distanceNauticalMiles,
            draft?.plannedSpeedKnots ?: 0.0,
            summary.estimatedDurationMillis / 60_000L,
        ),
        12,
        color = colors.muted,
    )
    if (count > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MapActionText(R.string.map_undo, "map-route-undo") { onAction(MapAction.UndoRouteEdit) }
            MapActionText(R.string.map_redo, "map-route-redo") { onAction(MapAction.RedoRouteEdit) }
            if (count >= 2) {
                MapActionText(R.string.map_reverse, "map-route-reverse") { onAction(MapAction.ReverseRoute) }
                MapActionText(R.string.map_save_copy, "map-route-save") {
                    onAction(MapAction.SaveRouteCopy("Manual route ${state.savedRoutes.size + 1}"))
                }
            }
        }
    }
}

@Composable
private fun ChartPackagesPanel(
    state: MapState,
    mapConfigured: Boolean,
    onOpenMapSettings: () -> Unit,
    onImportChart: () -> Unit,
) {
    val colors = LocalWpTheme.current
    WpText(stringResource(R.string.map_charts_title), 22, weight = FontWeight.Light)
    WpText(
        if (state.chartPackages.isEmpty()) stringResource(R.string.map_charts_empty)
        else stringResource(R.string.map_charts_count, state.chartPackages.size),
        12,
        color = colors.muted,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        MapActionText(R.string.map_import_mbtiles, "map-import-chart", onImportChart)
        if (!mapConfigured) {
            MapActionText(R.string.chart_open_settings, "chart-open-map-settings", onOpenMapSettings)
        }
    }
}

@Composable
private fun MapActionText(label: Int, tag: String, action: () -> Unit) {
    val colors = LocalWpTheme.current
    WpText(
        stringResource(label),
        13,
        color = colors.accent,
        modifier = Modifier.clickNoRipple(action).padding(vertical = 4.dp).testTag(tag),
    )
}

@Composable
private fun Modifier.clickNoRipple(action: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = action,
)

/** Offline empty canvas: truthful workbench chrome, not a chart or vessel-position simulation. */
@Composable
fun MarineChartDemoSurface(modifier: Modifier = Modifier) {
    Canvas(modifier.testTag("chart-demo-canvas")) {
        drawRect(YokuliColors.ChartWater)
        val abstractLand = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width * .72f, 0f)
            cubicTo(size.width * .56f, size.height * .10f, size.width * .45f, size.height * .18f, 0f, size.height * .28f)
            close()
        }
        drawPath(abstractLand, YokuliColors.ChartLand)
    }
}

@Composable
fun MarineChartTransitionSurface(modifier: Modifier = Modifier) {
    Box(modifier.background(YokuliColors.ChartWater).testTag("chart-map-transition-plane"))
}
