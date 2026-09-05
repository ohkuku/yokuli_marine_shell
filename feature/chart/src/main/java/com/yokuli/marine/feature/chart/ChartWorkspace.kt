package com.yokuli.marine.feature.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliColors
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapSaveState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapTool
import com.yokuli.marine.map.domain.PositionAvailability
import com.yokuli.marine.map.domain.ChartPackageImportFailure
import java.util.Locale

typealias MarineChartSurface = @Composable (
    state: MapState,
    onAction: (MapAction) -> Unit,
    modifier: Modifier,
) -> Unit

enum class MapRecoveryExportUiState { IDLE, WRITING, SUCCEEDED, FAILED }

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
    importState: ChartImportUiState,
    onImportAction: (ChartImportUiAction) -> Unit,
    recoveryExportState: MapRecoveryExportUiState,
    onExportRecovery: () -> Unit,
    chartSurface: MarineChartSurface,
) {
    val colors = LocalWpTheme.current
    val configuration = LocalConfiguration.current
    Box(
        Modifier.fillMaxSize().background(YokuliColors.ChartWater).testTag("chart-workspace-browse"),
    ) {
        chartSurface(
            state,
            onAction,
            Modifier.fillMaxSize(),
        )
        if (kotlin.math.abs(configuration.screenWidthDp - configuration.screenHeightDp) <= 80) {
            Canvas(Modifier.align(Alignment.Center).size(30.dp).testTag("map-square-crosshair")) {
                val stroke = 1.5.dp.toPx()
                drawLine(colors.foreground.copy(alpha = .9f), center.copy(x = 0f), center.copy(x = size.width), stroke)
                drawLine(colors.foreground.copy(alpha = .9f), center.copy(y = 0f), center.copy(y = size.height), stroke)
                drawCircle(colors.foreground.copy(alpha = .9f), radius = 3.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            }
        }
        Column(Modifier.fillMaxSize()) {
            WpPageHeader(
                // Stable technical key preserves the approved launch/test contract; the visible App name is Map.
                appKey = "chart",
                appName = stringResource(R.string.app_chart),
                contextLine = stringResource(R.string.map_context_offline_first),
                modifier = Modifier.background(colors.background.copy(alpha = .90f)),
            )
            PositionTruthBadge(state.position.availability)
            MapPersistenceTruth(state, recoveryExportState, onAction, onExportRecovery)
            Spacer(Modifier.weight(1f))
            state.chartPackages.firstOrNull { it.id == state.activeChartPackageId }?.let { chartPackage ->
                Box(
                    Modifier.fillMaxWidth().background(colors.background.copy(alpha = .90f))
                        .padding(horizontal = 18.dp, vertical = 5.dp).testTag("map-chart-attribution"),
                ) {
                    WpText(
                        stringResource(
                            R.string.map_chart_attribution,
                            chartPackage.attribution,
                            chartPackage.license,
                        ),
                        10,
                        color = colors.muted,
                        maxLines = 2,
                    )
                }
            }
            MapToolPanel(
                state = state,
                mapConfigured = mapConfigured,
                onAction = onAction,
                onOpenMapSettings = onOpenMapSettings,
                importState = importState,
                onImportAction = onImportAction,
            )
            MapToolBar(state.tool, onAction)
        }
    }
}

@Composable
private fun MapPersistenceTruth(
    state: MapState,
    recoveryExportState: MapRecoveryExportUiState,
    onAction: (MapAction) -> Unit,
    onExportRecovery: () -> Unit,
) {
    val colors = LocalWpTheme.current
    val message = when {
        state.libraryLoadState == MapLibraryLoadState.LOADING -> R.string.map_library_loading
        state.libraryLoadState == MapLibraryLoadState.READ_FAILED -> R.string.map_library_read_failed
        state.libraryLoadState == MapLibraryLoadState.CORRUPT -> R.string.map_library_corrupt
        state.saveState == MapSaveState.PENDING -> R.string.map_library_saving
        state.saveState == MapSaveState.FAILED -> R.string.map_library_save_failed
        else -> null
    } ?: return
    Row(
        Modifier.padding(start = 18.dp, top = 6.dp).background(colors.background.copy(alpha = .92f))
            .padding(horizontal = 9.dp, vertical = 7.dp).testTag("map-persistence-truth"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WpText(stringResource(message), 11, color = colors.foreground, modifier = Modifier.weight(1f))
        when {
            state.libraryLoadState == MapLibraryLoadState.READ_FAILED ||
                state.libraryLoadState == MapLibraryLoadState.CORRUPT -> {
                MapActionText(R.string.map_library_retry_load, "map-library-retry-load") {
                    onAction(MapAction.RetryLoad)
                }
            }
            state.saveState == MapSaveState.FAILED -> {
                MapActionText(R.string.map_library_retry_save, "map-library-retry-save") {
                    onAction(MapAction.RetryPersistence)
                }
                MapActionText(R.string.map_library_export_recovery, "map-library-export-recovery", onExportRecovery)
            }
        }
    }
    val exportMessage = when (recoveryExportState) {
        MapRecoveryExportUiState.IDLE -> null
        MapRecoveryExportUiState.WRITING -> R.string.map_library_export_writing
        MapRecoveryExportUiState.SUCCEEDED -> R.string.map_library_export_succeeded
        MapRecoveryExportUiState.FAILED -> R.string.map_library_export_failed
    }
    exportMessage?.let {
        WpText(
            stringResource(it),
            11,
            color = colors.foreground,
            modifier = Modifier.padding(start = 18.dp, top = 4.dp).background(colors.background.copy(alpha = .92f))
                .padding(horizontal = 9.dp, vertical = 5.dp).testTag("map-recovery-export-state"),
        )
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
    importState: ChartImportUiState,
    onImportAction: (ChartImportUiAction) -> Unit,
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
                val defaultRouteName = stringResource(R.string.map_default_route_name, state.savedRoutes.size + 1)
                WpText(stringResource(R.string.map_measure_title), 22, weight = FontWeight.Light)
                WpText(stringResource(R.string.map_points_count, count), 12, color = colors.muted)
                if (count >= 2) {
                    MapActionText(R.string.map_convert_to_route, "map-measure-convert") {
                        onAction(MapAction.ConvertMeasurementToManualRoute(defaultRouteName))
                    }
                }
            }
            MapTool.MANUAL_ROUTE -> RoutePanel(state, onAction)
            MapTool.CHARTS -> ChartPackagesPanel(state, mapConfigured, onOpenMapSettings, importState, onImportAction)
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
        val defaultPlaceName = stringResource(R.string.map_default_place_name, state.places.size + 1)
        WpText(stringResource(R.string.map_selected_point), 22, weight = FontWeight.Light)
        WpText(
            String.format(Locale.US, "%.5f, %.5f", selection.point.latitude, selection.point.longitude),
            12,
            color = colors.muted,
        )
        MapActionText(R.string.map_save_place, "map-save-place") {
            onAction(MapAction.SaveSelectionAsPlace(defaultPlaceName))
        }
    }
}

@Composable
private fun RoutePanel(state: MapState, onAction: (MapAction) -> Unit) {
    val colors = LocalWpTheme.current
    val draft = state.routeDraft
    val count = draft?.waypoints?.size ?: 0
    val defaultRouteName = stringResource(R.string.map_default_route_name, state.savedRoutes.size + 1)
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
                    onAction(MapAction.SaveRouteCopy(defaultRouteName))
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
    importState: ChartImportUiState,
    onImportAction: (ChartImportUiAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        WpText(stringResource(R.string.map_charts_title), 22, weight = FontWeight.Light)
        WpText(
            if (state.chartPackages.isEmpty()) stringResource(R.string.map_charts_empty)
            else stringResource(R.string.map_charts_count, state.chartPackages.size),
            12,
            color = colors.muted,
        )
        state.chartPackages.forEach { chartPackage ->
            Column(Modifier.fillMaxWidth().border(1.dp, colors.muted.copy(alpha = .45f)).padding(7.dp)) {
                WpText(chartPackage.displayName, 14, weight = FontWeight.SemiBold)
                WpText(
                    stringResource(R.string.map_chart_package_detail, chartPackage.source, chartPackage.version),
                    10,
                    color = colors.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (chartPackage.id == state.activeChartPackageId) {
                        WpText(stringResource(R.string.map_chart_active), 11, color = colors.accent)
                    } else {
                        MapActionText(R.string.map_chart_use, "map-use-${chartPackage.id.value.take(8)}") {
                            onImportAction(ChartImportUiAction.Activate(chartPackage.id))
                        }
                    }
                    MapActionText(R.string.map_chart_delete, "map-delete-${chartPackage.id.value.take(8)}") {
                        onImportAction(ChartImportUiAction.Delete(chartPackage.id))
                    }
                }
            }
        }
        when (importState) {
            ChartImportUiState.Idle -> MapActionText(R.string.map_import_mbtiles, "map-import-chart") {
                onImportAction(ChartImportUiAction.ChooseDocument)
            }
            ChartImportUiState.Inspecting -> WpText(stringResource(R.string.map_import_inspecting), 12)
            ChartImportUiState.Installing -> WpText(stringResource(R.string.map_import_installing), 12)
            is ChartImportUiState.Failed -> {
                WpText(importFailureLabel(importState.reason), 12, color = colors.accent)
                MapActionText(R.string.map_import_try_again, "map-import-retry") {
                    onImportAction(ChartImportUiAction.ChooseDocument)
                }
            }
            is ChartImportUiState.Editing -> ChartImportEditor(importState, onImportAction)
        }
        if (!mapConfigured) {
            MapActionText(R.string.chart_open_settings, "chart-open-map-settings", onOpenMapSettings)
        }
    }
}

@Composable
private fun ChartImportEditor(
    state: ChartImportUiState.Editing,
    onAction: (ChartImportUiAction) -> Unit,
) {
    WpText(
        stringResource(
            R.string.map_import_candidate,
            state.candidate.rasterFormat.uppercase(Locale.US),
            state.candidate.minZoom,
            state.candidate.maxZoom,
        ),
        11,
    )
    state.validationFailure?.let { WpText(importFailureLabel(it), 11, color = LocalWpTheme.current.accent) }
    ImportTextField(R.string.map_import_name, state.displayName, ChartImportField.DISPLAY_NAME, onAction)
    ImportTextField(R.string.map_import_source, state.source, ChartImportField.SOURCE, onAction)
    ImportTextField(R.string.map_import_license, state.license, ChartImportField.LICENSE, onAction)
    ImportTextField(R.string.map_import_attribution, state.attribution, ChartImportField.ATTRIBUTION, onAction)
    ImportTextField(R.string.map_import_version, state.version, ChartImportField.VERSION, onAction)
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        MapActionText(R.string.map_import_install, "map-import-install") { onAction(ChartImportUiAction.Install) }
        MapActionText(R.string.map_import_cancel, "map-import-cancel") { onAction(ChartImportUiAction.Cancel) }
    }
}

@Composable
private fun ImportTextField(
    label: Int,
    value: String,
    field: ChartImportField,
    onAction: (ChartImportUiAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    Column {
        WpText(stringResource(label), 10, color = colors.muted)
        BasicTextField(
            value = value,
            onValueChange = { onAction(ChartImportUiAction.UpdateField(field, it)) },
            singleLine = true,
            textStyle = TextStyle(color = colors.foreground),
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.muted).padding(7.dp),
        )
    }
}

@Composable
private fun importFailureLabel(reason: ChartPackageImportFailure): String = stringResource(
    when (reason) {
        ChartPackageImportFailure.CANNOT_OPEN -> R.string.map_import_error_open
        ChartPackageImportFailure.INVALID_DATABASE -> R.string.map_import_error_database
        ChartPackageImportFailure.EMPTY_PACKAGE -> R.string.map_import_error_empty
        ChartPackageImportFailure.UNSUPPORTED_FORMAT -> R.string.map_import_error_format
        ChartPackageImportFailure.INVALID_METADATA -> R.string.map_import_error_metadata
        ChartPackageImportFailure.REQUIRED_FIELD_MISSING -> R.string.map_import_error_required
        ChartPackageImportFailure.STAGING_EXPIRED -> R.string.map_import_error_expired
        ChartPackageImportFailure.INSTALL_FAILED -> R.string.map_import_error_install
        ChartPackageImportFailure.IO_FAILURE -> R.string.map_import_error_io
    },
)

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

@Composable
fun MarineChartTransitionSurface(modifier: Modifier = Modifier) {
    Box(modifier.background(YokuliColors.ChartWater).testTag("chart-map-transition-plane"))
}
