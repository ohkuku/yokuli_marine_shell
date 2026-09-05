package com.yokuli.marine.feature.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliColors
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageImportFailure
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapCameraIntent
import com.yokuli.marine.map.domain.MapCameraTarget
import com.yokuli.marine.map.domain.MapFeatureBackPolicy
import com.yokuli.marine.map.domain.MapHitResult
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapOverlayId
import com.yokuli.marine.map.domain.MapRendererQueryPort
import com.yokuli.marine.map.domain.MapRendererReadiness
import com.yokuli.marine.map.domain.MapSaveState
import com.yokuli.marine.map.domain.MapScreenPoint
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapSurface
import com.yokuli.marine.map.domain.MapTileCoverageStatus
import com.yokuli.marine.map.domain.MapTool
import com.yokuli.marine.map.domain.MapTransient
import com.yokuli.marine.map.domain.MapViewport
import com.yokuli.marine.map.domain.MapViewportInsets
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

typealias MarineChartSurface = @Composable (
    state: MapState,
    onAction: (MapAction) -> Unit,
    onQueryPortChanged: (MapRendererQueryPort?) -> Unit,
    modifier: Modifier,
) -> Unit

enum class MapRecoveryExportUiState { IDLE, WRITING, SUCCEEDED, FAILED }

/**
 * Map is one installed app. Its map surface, active editing tool and transient interaction plane
 * are independent; Shell navigation never needs to understand places, routes, packages or gestures.
 */
@Composable
fun ChartWorkspace(
    state: MapState,
    onAction: (MapAction) -> Unit,
    currentState: () -> MapState = { state },
    importState: ChartImportUiState,
    onImportAction: (ChartImportUiAction) -> Unit,
    recoveryExportState: MapRecoveryExportUiState,
    onExportRecovery: () -> Unit,
    chartSurface: MarineChartSurface,
) {
    val colors = LocalWpTheme.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val isSquare = abs(configuration.screenWidthDp - configuration.screenHeightDp) <= 80
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var queryPort by remember { mutableStateOf<MapRendererQueryPort?>(null) }
    val rootInsets = MapViewportInsets(
        topPx = with(density) { 42.dp.roundToPx() },
        bottomPx = with(density) { 60.dp.roundToPx() },
    )

    BindInternalAppInputHandler { input ->
        if (input != ShellInput.BACK) {
            false
        } else if (imeVisible) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            true
        } else {
            MapFeatureBackPolicy.actionFor(currentState())?.let { action ->
                onAction(action)
                true
            } ?: false
        }
    }

    LaunchedEffect(isSquare) {
        if (isSquare && !state.crosshairEnabled) onAction(MapAction.SetCrosshairEnabled(true))
    }
    LaunchedEffect(viewportSize, rootInsets) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            val viewport = MapViewport(
                viewportSize.width,
                viewportSize.height,
                rootInsets,
                viewportRevision(viewportSize, rootInsets),
            )
            if (state.viewport != viewport) onAction(MapAction.ViewportChanged(viewport))
        }
    }

    Box(
        Modifier.fillMaxSize().background(YokuliColors.ChartWater)
            .onSizeChanged { viewportSize = it }
            .testTag("chart-workspace-browse"),
    ) {
        chartSurface(state, onAction, { queryPort = it }, Modifier.fillMaxSize())
        if (state.surface == MapSurface.Root) {
            MapRootChrome(
                state,
                queryPort,
                viewportSize,
                rootInsets,
                recoveryExportState,
                onAction,
                onExportRecovery,
            )
        } else {
            MapPageSurface(state, importState, onImportAction, onAction)
        }
    }
}

@Composable
private fun MapRootChrome(
    state: MapState,
    queryPort: MapRendererQueryPort?,
    viewportSize: IntSize,
    viewportInsets: MapViewportInsets,
    recoveryExportState: MapRecoveryExportUiState,
    onAction: (MapAction) -> Unit,
    onExportRecovery: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        MapTruthStrip(state, Modifier.align(Alignment.TopStart))
        MapPersistenceTruth(
            state,
            recoveryExportState,
            onAction,
            onExportRecovery,
            Modifier.align(Alignment.TopEnd).padding(top = 42.dp),
        )
        if (state.crosshairEnabled) {
            MapCrosshairResolver.screenPoint(viewportSize.width, viewportSize.height, viewportInsets)?.let { point ->
                MapCrosshair(
                    Modifier.offset {
                        IntOffset(
                            (point.xPx - 15.dp.toPx()).roundToInt(),
                            (point.yPx - 15.dp.toPx()).roundToInt(),
                        )
                    },
                )
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            state.chartPackages.firstOrNull { it.id == state.activeChartPackageId }?.let { chartPackage ->
                MapAttribution(chartPackage.attribution, chartPackage.license)
            }
            MapRootSummary(state, onAction)
            if (state.crosshairEnabled) CrosshairAction(queryPort, viewportSize, viewportInsets, onAction)
            MapRootCommandBar(state, onAction)
        }
    }
}

@Composable
private fun MapTruthStrip(state: MapState, modifier: Modifier = Modifier) {
    val colors = LocalWpTheme.current
    val status = when {
        state.renderer.readiness == MapRendererReadiness.ERROR -> R.string.map_renderer_error
        state.renderer.tileCoverage == MapTileCoverageStatus.PACKAGE_MISSING -> R.string.map_package_missing
        state.renderer.tileCoverage == MapTileCoverageStatus.DEGRADED -> R.string.map_package_degraded
        state.renderer.tileCoverage == MapTileCoverageStatus.CHECKING -> R.string.map_package_checking
        state.renderer.tileCoverage == MapTileCoverageStatus.PACKAGE_ATTACHED -> R.string.map_package_attached
        else -> R.string.map_no_package
    }
    Row(
        modifier.background(colors.background.copy(alpha = .88f)).padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("map-truth-strip"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WpText(stringResource(status), 10, color = colors.foreground, maxLines = 1)
        WpText(
            stringResource(R.string.map_scale_100px, state.camera.scaleNauticalMilesForPixels(100.0)),
            10,
            color = colors.muted,
            maxLines = 1,
        )
    }
}

@Composable
private fun MapPersistenceTruth(
    state: MapState,
    recoveryExportState: MapRecoveryExportUiState,
    onAction: (MapAction) -> Unit,
    onExportRecovery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWpTheme.current
    val message = when {
        state.libraryLoadState == MapLibraryLoadState.LOADING -> R.string.map_library_loading
        state.libraryLoadState == MapLibraryLoadState.READ_FAILED -> R.string.map_library_read_failed
        state.libraryLoadState == MapLibraryLoadState.CORRUPT -> R.string.map_library_corrupt
        state.saveState == MapSaveState.PENDING -> R.string.map_library_saving
        state.saveState == MapSaveState.FAILED -> R.string.map_library_save_failed
        else -> null
    }
    if (message != null) {
        Column(
            modifier.widthIn(max = 270.dp).background(colors.background.copy(alpha = .94f))
                .padding(horizontal = 9.dp, vertical = 7.dp).testTag("map-persistence-truth"),
        ) {
            WpText(stringResource(message), 10, color = colors.foreground)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.libraryLoadState == MapLibraryLoadState.READ_FAILED ||
                    state.libraryLoadState == MapLibraryLoadState.CORRUPT
                ) {
                    MapActionText(R.string.map_library_retry_load, "map-library-retry-load") {
                        onAction(MapAction.RetryLoad)
                    }
                } else if (state.saveState == MapSaveState.FAILED) {
                    MapActionText(R.string.map_library_retry_save, "map-library-retry-save") {
                        onAction(MapAction.RetryPersistence)
                    }
                    MapActionText(R.string.map_library_export_recovery, "map-library-export-recovery", onExportRecovery)
                }
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
            10,
            color = colors.foreground,
            modifier = modifier.background(colors.background.copy(alpha = .94f)).padding(7.dp)
                .testTag("map-recovery-export-state"),
        )
    }
}

@Composable
private fun MapRootSummary(state: MapState, onAction: (MapAction) -> Unit) {
    val colors = LocalWpTheme.current
    when (val transient = state.transient) {
        is MapTransient.PointCandidate -> {
            Row(
                Modifier.fillMaxWidth().background(colors.background.copy(alpha = .95f)).padding(horizontal = 12.dp)
                    .testTag("map-point-candidate"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WpText(transient.point.coordinateText(), 11, modifier = Modifier.weight(1f))
                MapActionText(R.string.map_add_point, "map-add-point") { onAction(MapAction.ConfirmPointCandidate) }
                MapActionText(R.string.map_cancel, "map-candidate-cancel") { onAction(MapAction.DismissTransient) }
            }
        }
        is MapTransient.SelectedObject -> {
            Row(
                Modifier.fillMaxWidth().background(colors.background.copy(alpha = .95f)).padding(horizontal = 12.dp)
                    .testTag("map-object-summary"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WpText(transient.hit.objectId, 11, modifier = Modifier.weight(1f), maxLines = 1)
                MapActionText(R.string.map_close, "map-object-close") { onAction(MapAction.DismissTransient) }
            }
        }
        is MapTransient.ObjectCandidates -> {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 180.dp).background(colors.background.copy(alpha = .97f))
                    .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)
                    .testTag("map-object-candidates"),
            ) {
                WpText(stringResource(R.string.map_choose_object), 12, weight = FontWeight.SemiBold)
                transient.hits.forEach { hit ->
                    MapTextButton(hit.objectId, "map-object-candidate-${hit.objectId.hashCode()}") {
                        onAction(MapAction.ChooseObjectCandidate(hit))
                    }
                }
            }
        }
        is MapTransient.UnavailableObject -> {
            Row(
                Modifier.fillMaxWidth().background(colors.background.copy(alpha = .96f)).padding(horizontal = 12.dp)
                    .testTag("map-object-unavailable"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WpText(stringResource(R.string.map_object_unavailable), 11, modifier = Modifier.weight(1f))
                MapActionText(R.string.map_close, "map-unavailable-close") { onAction(MapAction.DismissTransient) }
            }
        }
        null -> state.selection?.let { selection ->
            val defaultName = stringResource(R.string.map_default_place_name, state.places.size + 1)
            Row(
                Modifier.fillMaxWidth().background(colors.background.copy(alpha = .95f)).padding(horizontal = 12.dp)
                    .testTag("map-selection-summary"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WpText(selection.point.coordinateText(), 11, modifier = Modifier.weight(1f))
                MapActionText(R.string.map_save_place, "map-save-place") {
                    onAction(MapAction.SaveSelectionAsPlace(defaultName))
                }
                MapActionText(R.string.map_close, "map-selection-close") { onAction(MapAction.ClearSelection) }
            }
        }
    }
    if (state.transient == null && state.selection == null && state.tool != MapTool.BROWSE) {
        val count = when (state.tool) {
            MapTool.MEASURE -> state.measurementDraft?.points?.size ?: 0
            MapTool.MANUAL_ROUTE -> state.routeDraft?.waypoints?.size ?: 0
            MapTool.BROWSE -> 0
        }
        Row(
            Modifier.fillMaxWidth().background(colors.background.copy(alpha = .90f))
                .padding(horizontal = 12.dp, vertical = 5.dp).testTag("map-active-tool-summary"),
        ) {
            WpText(
                stringResource(
                    if (state.tool == MapTool.MEASURE) R.string.map_measure_short else R.string.map_route_short,
                    count,
                ),
                11,
            )
        }
    }
}

@Composable
private fun CrosshairAction(
    queryPort: MapRendererQueryPort?,
    viewportSize: IntSize,
    viewportInsets: MapViewportInsets,
    onAction: (MapAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    val screenPoint = MapCrosshairResolver.screenPoint(viewportSize.width, viewportSize.height, viewportInsets)
    val enabled = queryPort != null && screenPoint != null
    Box(
        Modifier.fillMaxWidth().background(colors.background.copy(alpha = .88f)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        MapTextButton(
            stringResource(if (enabled) R.string.map_crosshair_use else R.string.map_crosshair_wait),
            "map-crosshair-use",
            enabled,
        ) {
            val port = queryPort ?: return@MapTextButton
            val target = requireNotNull(screenPoint)
            val coordinate = port.unproject(target) ?: return@MapTextButton
            onAction(MapAction.CrosshairConfirmed(coordinate, port.query(target, INTERACTIVE_OVERLAYS)))
        }
    }
}

@Composable
private fun MapRootCommandBar(state: MapState, onAction: (MapAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(LocalWpTheme.current.background.copy(alpha = .98f))
            .padding(horizontal = 4.dp, vertical = 4.dp).testTag("map-root-command-bar"),
    ) {
        MapCommandButton(R.string.map_tool_measure, "map-tool-measure", state.tool == MapTool.MEASURE, Modifier.weight(1f)) {
            onAction(MapAction.SelectTool(if (state.tool == MapTool.MEASURE) MapTool.BROWSE else MapTool.MEASURE))
        }
        MapCommandButton(R.string.map_tool_routes, "map-tool-manual_route", state.tool == MapTool.MANUAL_ROUTE, Modifier.weight(1f)) {
            onAction(MapAction.SelectTool(if (state.tool == MapTool.MANUAL_ROUTE) MapTool.BROWSE else MapTool.MANUAL_ROUTE))
        }
        MapCommandButton(R.string.map_tool_places, "map-open-places", false, Modifier.weight(1f)) {
            onAction(MapAction.OpenSurface(MapSurface.Places))
        }
        MapCommandButton(R.string.map_routes_title, "map-open-routes", false, Modifier.weight(1f)) {
            onAction(MapAction.OpenSurface(MapSurface.Routes))
        }
        MapCommandButton(R.string.map_tool_charts, "map-open-charts", false, Modifier.weight(1f)) {
            onAction(MapAction.OpenSurface(MapSurface.ChartPackages))
        }
        MapCommandButton(R.string.map_crosshair, "map-crosshair-toggle", state.crosshairEnabled, Modifier.weight(1f)) {
            onAction(MapAction.SetCrosshairEnabled(!state.crosshairEnabled))
        }
    }
}

@Composable
private fun MapCommandButton(label: Int, tag: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    Box(
        modifier.heightIn(min = 48.dp).clickNoRipple(action = onClick)
            .background(if (selected) colors.accent else colors.background).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        WpText(stringResource(label), 9, color = if (selected) colors.onAccent else colors.foreground, maxLines = 1)
    }
}

@Composable
private fun MapCrosshair(modifier: Modifier = Modifier) {
    val color = LocalWpTheme.current.foreground.copy(alpha = .92f)
    Canvas(modifier.size(30.dp).testTag("map-crosshair")) {
        val stroke = 1.5.dp.toPx()
        drawLine(color, center.copy(x = 0f), center.copy(x = size.width), stroke)
        drawLine(color, center.copy(y = 0f), center.copy(y = size.height), stroke)
        drawCircle(color, radius = 3.dp.toPx(), style = Stroke(stroke))
    }
}

@Composable
private fun MapPageSurface(
    state: MapState,
    importState: ChartImportUiState,
    onImportAction: (ChartImportUiAction) -> Unit,
    onAction: (MapAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    Column(
        Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).imePadding()
            .padding(bottom = 16.dp).testTag("map-page-surface"),
    ) {
        val (title, context) = when (state.surface) {
            MapSurface.Places, is MapSurface.PlaceDetail -> R.string.map_places_title to R.string.map_places_context
            MapSurface.Routes, is MapSurface.RouteDetail -> R.string.map_routes_title to R.string.map_routes_context
            MapSurface.ChartPackages, is MapSurface.ChartPackageDetail -> R.string.map_charts_title to R.string.map_charts_context
            MapSurface.Root -> R.string.app_chart to R.string.map_context_offline_first
        }
        WpPageHeader("map-section", stringResource(title), stringResource(context))
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val surface = state.surface) {
                MapSurface.Places -> PlacesPage(state, onAction)
                MapSurface.Routes -> RoutesPage(state, onAction)
                MapSurface.ChartPackages -> ChartPackagesPage(state, importState, onImportAction, onAction)
                is MapSurface.PlaceDetail -> PlaceDetailPage(state, surface.placeId, onAction)
                is MapSurface.RouteDetail -> RouteDetailPage(state, surface.routeId, onAction)
                is MapSurface.ChartPackageDetail -> ChartPackageDetailPage(state, surface.packageId, onAction)
                MapSurface.Root -> Unit
            }
        }
    }
}

@Composable
private fun PlacesPage(state: MapState, onAction: (MapAction) -> Unit) {
    val colors = LocalWpTheme.current
    WpText(
        if (state.places.isEmpty()) stringResource(R.string.map_places_empty)
        else stringResource(R.string.map_places_count, state.places.size),
        12,
        color = colors.muted,
    )
    state.places.forEach { place ->
        MapTextButton(place.name, "map-place-${place.id.take(8)}") {
            onAction(MapAction.OpenSurface(MapSurface.PlaceDetail(place.id)))
        }
    }
}

@Composable
private fun RoutesPage(state: MapState, onAction: (MapAction) -> Unit) {
    val colors = LocalWpTheme.current
    WpText(stringResource(R.string.map_routes_count, state.routeDrafts.size, state.savedRoutes.size), 12, color = colors.muted)
    state.routeDrafts.forEach { route ->
        MapTextButton(route.name.ifBlank { stringResource(R.string.map_route_draft_unnamed) }, "map-route-${route.id}") {
            onAction(MapAction.OpenSurface(MapSurface.RouteDetail(route.id)))
        }
    }
    state.savedRoutes.forEach { route ->
        MapTextButton(route.name, "map-route-${route.id}") {
            onAction(MapAction.OpenSurface(MapSurface.RouteDetail(route.id)))
        }
    }
}

@Composable
private fun PlaceDetailPage(state: MapState, id: String, onAction: (MapAction) -> Unit) {
    val place = state.places.firstOrNull { it.id == id } ?: return
    WpText(place.name, 22, weight = FontWeight.Light)
    WpText(place.point.coordinateText(), 12, color = LocalWpTheme.current.muted)
    MapActionText(R.string.map_view, "map-view-place-${id.take(8)}") {
        onAction(MapAction.RequestCamera(MapCameraTarget.Exact(state.camera.copy(center = place.point)), MapCameraIntent.VIEW_PLACE, state.viewportInsets()))
        onAction(MapAction.CloseSurface)
    }
}

@Composable
private fun RouteDetailPage(state: MapState, id: String, onAction: (MapAction) -> Unit) {
    val points = state.routeDrafts.firstOrNull { it.id == id }?.waypoints
        ?: state.savedRoutes.firstOrNull { it.id == id }?.waypoints
        ?: return
    WpText(stringResource(R.string.map_points_count, points.size), 12)
    if (points.size >= 2) {
        MapActionText(R.string.map_view_route, "map-route-view") {
            onAction(MapAction.RequestCamera(MapCameraTarget.Bounds(points.toBounds()), MapCameraIntent.VIEW_ROUTE, state.viewportInsets()))
            onAction(MapAction.CloseSurface)
        }
    }
}

@Composable
private fun ChartPackagesPage(
    state: MapState,
    importState: ChartImportUiState,
    onImportAction: (ChartImportUiAction) -> Unit,
    onMapAction: (MapAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    WpText(
        if (state.chartPackages.isEmpty()) stringResource(R.string.map_charts_empty)
        else stringResource(R.string.map_charts_count, state.chartPackages.size),
        12,
        color = colors.muted,
    )
    state.chartPackages.forEach { chartPackage ->
        Column(Modifier.fillMaxWidth().border(1.dp, colors.muted.copy(alpha = .45f)).padding(8.dp)) {
            WpText(chartPackage.displayName, 14, weight = FontWeight.SemiBold)
            WpText(stringResource(R.string.map_chart_package_detail, chartPackage.source, chartPackage.version), 10, color = colors.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (chartPackage.id == state.activeChartPackageId) {
                    WpText(stringResource(R.string.map_chart_active), 11, color = colors.accent)
                } else {
                    MapActionText(R.string.map_chart_use, "map-use-${chartPackage.id.value.take(8)}") {
                        onImportAction(ChartImportUiAction.Activate(chartPackage.id))
                    }
                }
                MapActionText(R.string.map_details, "map-package-${chartPackage.id.value.take(8)}") {
                    onMapAction(MapAction.OpenSurface(MapSurface.ChartPackageDetail(chartPackage.id)))
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
}

@Composable
private fun ChartPackageDetailPage(state: MapState, id: ChartPackageId, onAction: (MapAction) -> Unit) {
    val chartPackage = state.chartPackages.firstOrNull { it.id == id } ?: return
    WpText(chartPackage.displayName, 22, weight = FontWeight.Light)
    WpText(stringResource(R.string.map_chart_attribution, chartPackage.attribution, chartPackage.license), 11)
    WpText(stringResource(R.string.map_chart_package_detail, chartPackage.source, chartPackage.version), 11)
    MapActionText(R.string.map_view_package, "map-view-package-${id.value.take(8)}") {
        onAction(MapAction.RequestCamera(MapCameraTarget.Bounds(chartPackage.coverage), MapCameraIntent.VIEW_PACKAGE, state.viewportInsets()))
        onAction(MapAction.CloseSurface)
    }
}

@Composable
private fun MapAttribution(attribution: String, license: String) {
    val colors = LocalWpTheme.current
    Box(
        Modifier.fillMaxWidth().background(colors.background.copy(alpha = .88f)).padding(horizontal = 10.dp, vertical = 3.dp)
            .testTag("map-chart-attribution"),
    ) {
        WpText(stringResource(R.string.map_chart_attribution, attribution, license), 9, color = colors.muted, maxLines = 1)
    }
}

internal object MapCrosshairResolver {
    fun screenPoint(widthPx: Int, heightPx: Int, insets: MapViewportInsets = MapViewportInsets()): MapScreenPoint? {
        if (widthPx <= 0 || heightPx <= 0) return null
        if (insets.leftPx + insets.rightPx >= widthPx || insets.topPx + insets.bottomPx >= heightPx) return null
        return MapScreenPoint(
            insets.leftPx + (widthPx - insets.leftPx - insets.rightPx) / 2.0,
            insets.topPx + (heightPx - insets.topPx - insets.bottomPx) / 2.0,
        )
    }

    fun resolve(port: MapRendererQueryPort?, widthPx: Int, heightPx: Int): GeoPoint? =
        port?.let { renderer -> screenPoint(widthPx, heightPx)?.let(renderer::unproject) }
}

private fun viewportRevision(size: IntSize, insets: MapViewportInsets): Long =
    ((size.width.toLong() shl 32) xor size.height.toLong() xor
        (insets.topPx.toLong() shl 16) xor insets.bottomPx.toLong()).and(Long.MAX_VALUE).coerceAtLeast(1L)

private fun MapState.viewportInsets(): MapViewportInsets = viewport?.obscuredInsets ?: MapViewportInsets()

private fun GeoPoint.coordinateText(): String = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

private fun List<GeoPoint>.toBounds(): GeoBounds = GeoBounds(
    south = minOf { it.latitude },
    west = minOf { it.longitude },
    north = maxOf { it.latitude },
    east = maxOf { it.longitude },
)

internal fun MapCamera.scaleNauticalMilesForPixels(pixelCount: Double): Double {
    require(pixelCount.isFinite() && pixelCount >= 0.0)
    val metresPerPixel = 156_543.03392 * kotlin.math.cos(Math.toRadians(center.latitude)) / 2.0.pow(zoom)
    return metresPerPixel * pixelCount / 1_852.0
}

@Composable
private fun ChartImportEditor(state: ChartImportUiState.Editing, onAction: (ChartImportUiAction) -> Unit) {
    WpText(
        stringResource(R.string.map_import_candidate, state.candidate.rasterFormat.uppercase(Locale.US), state.candidate.minZoom, state.candidate.maxZoom),
        11,
    )
    state.validationFailure?.let { WpText(importFailureLabel(it), 11, color = LocalWpTheme.current.accent) }
    ImportTextField(R.string.map_import_name, state.displayName, ChartImportField.DISPLAY_NAME, onAction)
    ImportTextField(R.string.map_import_source, state.source, ChartImportField.SOURCE, onAction)
    ImportTextField(R.string.map_import_license, state.license, ChartImportField.LICENSE, onAction)
    ImportTextField(R.string.map_import_attribution, state.attribution, ChartImportField.ATTRIBUTION, onAction)
    ImportTextField(R.string.map_import_version, state.version, ChartImportField.VERSION, onAction)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MapActionText(R.string.map_import_install, "map-import-install") { onAction(ChartImportUiAction.Install) }
        MapActionText(R.string.map_import_cancel, "map-import-cancel") { onAction(ChartImportUiAction.Cancel) }
    }
}

@Composable
private fun ImportTextField(label: Int, value: String, field: ChartImportField, onAction: (ChartImportUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    Column {
        WpText(stringResource(label), 10, color = colors.muted)
        BasicTextField(
            value = value,
            onValueChange = { onAction(ChartImportUiAction.UpdateField(field, it)) },
            singleLine = true,
            textStyle = TextStyle(color = colors.foreground, fontSize = 16.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, colors.muted).padding(8.dp),
        )
    }
}

@Composable
private fun importFailureLabel(reason: ChartPackageImportFailure): String = stringResource(
    when (reason) {
        ChartPackageImportFailure.CANNOT_OPEN -> R.string.map_import_error_open
        ChartPackageImportFailure.INVALID_DATABASE -> R.string.map_import_error_database
        ChartPackageImportFailure.CORRUPT_TILE -> R.string.map_import_error_format
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
    MapTextButton(stringResource(label), tag, true, action)
}

@Composable
private fun MapTextButton(label: String, tag: String, enabled: Boolean = true, action: () -> Unit) {
    val colors = LocalWpTheme.current
    Box(
        Modifier.heightIn(min = 48.dp).clickNoRipple(enabled, action).padding(horizontal = 4.dp).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        WpText(label, 12, color = if (enabled) colors.accent else colors.muted, maxLines = 1)
    }
}

@Composable
private fun Modifier.clickNoRipple(enabled: Boolean = true, action: () -> Unit): Modifier = clickable(
    enabled = enabled,
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = action,
)

@Composable
fun MarineChartTransitionSurface(modifier: Modifier = Modifier) {
    Box(modifier.background(YokuliColors.ChartWater).testTag("chart-map-transition-plane"))
}

private val INTERACTIVE_OVERLAYS = setOf(
    MapOverlayId.SAVED_PLACES,
    MapOverlayId.SELECTION,
    MapOverlayId.MEASUREMENT,
    MapOverlayId.MANUAL_ROUTE,
    MapOverlayId.MANUAL_ROUTE_POINTS,
)
