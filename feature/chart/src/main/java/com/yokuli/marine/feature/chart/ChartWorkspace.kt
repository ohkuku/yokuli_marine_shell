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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import com.yokuli.marine.map.domain.CoordinateCodec
import com.yokuli.marine.map.domain.CoordinateError
import com.yokuli.marine.map.domain.CoordinateField
import com.yokuli.marine.map.domain.CoordinateFormat
import com.yokuli.marine.map.domain.CoordinateParseResult
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapCameraIntent
import com.yokuli.marine.map.domain.MapCameraTarget
import com.yokuli.marine.map.domain.MapFeatureBackPolicy
import com.yokuli.marine.map.domain.MapEditTarget
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
import com.yokuli.marine.map.domain.MapPrecisePointEdit
import com.yokuli.marine.map.domain.MapViewport
import com.yokuli.marine.map.domain.MapViewportInsets
import com.yokuli.marine.map.domain.MeasurementMath
import com.yokuli.marine.map.domain.MeasurementPrompt
import com.yokuli.marine.map.domain.minimalBounds
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
    shellSafeInsets: MapViewportInsets = MapViewportInsets(),
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
        leftPx = shellSafeInsets.leftPx,
        topPx = maxOf(shellSafeInsets.topPx, with(density) { 42.dp.roundToPx() }),
        rightPx = shellSafeInsets.rightPx,
        bottomPx = maxOf(shellSafeInsets.bottomPx, with(density) { 60.dp.roundToPx() }),
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
    LaunchedEffect(configuration.orientation, imeVisible) {
        state.editGesture?.let { gesture -> onAction(MapAction.CancelPointDrag(gesture.id)) }
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
            if (state.crosshairEnabled) CrosshairAction(state, queryPort, viewportSize, viewportInsets, onAction)
            MapRootCommandBar(state, viewportInsets, onAction)
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
    val clipboard = LocalClipboardManager.current
    val defaultPlaceName = stringResource(R.string.map_default_place_name, state.places.size + 1)
    state.precisePointEdit?.let {
        Row(
            Modifier.fillMaxWidth().background(colors.background.copy(alpha = .96f)).padding(horizontal = 12.dp)
                .testTag("map-precise-point-edit"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WpText(stringResource(R.string.map_precise_edit_hint), 11, modifier = Modifier.weight(1f))
            MapActionText(R.string.map_coordinate_input, "map-precise-coordinate-input") {
                onAction(MapAction.OpenSurface(MapSurface.CoordinateInput))
            }
            MapActionText(R.string.map_cancel, "map-precise-cancel") { onAction(MapAction.CancelPrecisePointEdit) }
        }
        return
    }
    when (val transient = state.transient) {
        is MapTransient.PointCandidate -> {
            Column(
                Modifier.fillMaxWidth().background(colors.background.copy(alpha = .95f)).padding(horizontal = 12.dp)
                    .testTag("map-point-candidate"),
            ) {
                WpText(transient.point.coordinateText(), 11)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MapActionText(R.string.map_save_place, "map-candidate-save") {
                        onAction(MapAction.SavePointCandidateAsPlace(defaultPlaceName))
                    }
                    MapActionText(R.string.map_measure_from_here, "map-candidate-measure") {
                        onAction(MapAction.SelectTool(MapTool.MEASURE))
                        onAction(MapAction.AddPoint(transient.point))
                    }
                    MapActionText(R.string.map_route_from_here, "map-candidate-route") {
                        onAction(MapAction.SelectTool(MapTool.MANUAL_ROUTE))
                        onAction(MapAction.AddPoint(transient.point))
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MapActionText(R.string.map_copy_coordinate, "map-candidate-copy") {
                        clipboard.setText(AnnotatedString(transient.point.coordinateText()))
                    }
                    MapActionText(R.string.map_cancel, "map-candidate-cancel") { onAction(MapAction.DismissTransient) }
                }
            }
        }
        is MapTransient.SelectedObject -> {
            SelectedObjectSummary(state, transient.hit, onAction)
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
    if (state.transient == null && state.selection == null) {
        when (state.tool) {
            MapTool.MEASURE -> MeasurementRootSummary(state, onAction)
            MapTool.MANUAL_ROUTE -> Row(
                Modifier.fillMaxWidth().background(colors.background.copy(alpha = .90f))
                    .padding(horizontal = 12.dp, vertical = 5.dp).testTag("map-active-tool-summary"),
            ) {
                WpText(stringResource(R.string.map_route_short, state.routeDraft?.waypoints?.size ?: 0), 11)
            }
            MapTool.BROWSE -> Unit
        }
    }
}

@Composable
private fun SelectedObjectSummary(state: MapState, hit: MapHitResult, onAction: (MapAction) -> Unit) {
    val colors = LocalWpTheme.current
    val measurementIndex = hit.measurementPointIndexOrNull()
    val routeTarget = hit.routePointTargetOrNull()
    val point = measurementIndex?.let { state.measurementDraft?.points?.getOrNull(it) }
        ?: routeTarget?.let { target -> state.routeDrafts.firstOrNull { it.id == target.draftId }?.waypoints?.getOrNull(target.index) }
    Column(
        Modifier.fillMaxWidth().background(colors.background.copy(alpha = .95f)).padding(horizontal = 12.dp)
            .testTag("map-object-summary"),
    ) {
        WpText(point?.coordinateText() ?: hit.objectId, 11, maxLines = 1)
        Row(Modifier.fillMaxWidth()) {
            val target = measurementIndex?.let(MapEditTarget::MeasurementPoint) ?: routeTarget
            if (target != null) {
                MapActionText(R.string.map_move_point, "map-object-move") {
                    onAction(MapAction.BeginPrecisePointEdit(MapPrecisePointEdit.Move(target)))
                }
            }
            if (measurementIndex != null) {
                MapActionText(R.string.map_insert_after, "map-object-insert") {
                    onAction(MapAction.BeginPrecisePointEdit(MapPrecisePointEdit.InsertMeasurement(measurementIndex + 1)))
                }
                MapActionText(R.string.map_delete_point, "map-object-delete") {
                    onAction(MapAction.DeleteMeasurementPoint(measurementIndex))
                    onAction(MapAction.DismissTransient)
                }
            }
            MapActionText(R.string.map_close, "map-object-close") { onAction(MapAction.DismissTransient) }
        }
    }
}

@Composable
private fun MeasurementRootSummary(state: MapState, onAction: (MapAction) -> Unit) {
    val colors = LocalWpTheme.current
    val draft = state.measurementDraft ?: return
    val summary = MeasurementMath.summarize(draft)
    val defaultRouteName = stringResource(R.string.map_default_route_name, state.routeDrafts.size + 1)
    Column(
        Modifier.fillMaxWidth().background(colors.background.copy(alpha = .92f))
            .padding(horizontal = 12.dp, vertical = 4.dp).testTag("map-measurement-summary"),
    ) {
        val message = when (summary.prompt) {
            MeasurementPrompt.PLACE_START -> stringResource(R.string.map_measure_place_start)
            MeasurementPrompt.PLACE_END -> stringResource(R.string.map_measure_place_end)
            MeasurementPrompt.RESULTS -> {
                val last = requireNotNull(summary.segments.lastOrNull())
                stringResource(
                    R.string.map_measure_result,
                    last.distanceMeters.distanceText(),
                    last.bearingText(),
                    summary.totalDistanceMeters.distanceText(),
                )
            }
        }
        WpText(message, 11)
        Row(Modifier.fillMaxWidth()) {
            MapTextButton(stringResource(R.string.map_undo), "map-measure-undo", draft.undo.isNotEmpty()) {
                onAction(MapAction.UndoMeasurementEdit)
            }
            MapTextButton(stringResource(R.string.map_redo), "map-measure-redo", draft.redo.isNotEmpty()) {
                onAction(MapAction.RedoMeasurementEdit)
            }
            MapTextButton(stringResource(R.string.map_clear), "map-measure-clear", draft.points.isNotEmpty()) {
                onAction(MapAction.ClearMeasurement)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            MapTextButton(stringResource(R.string.map_measure_all_segments), "map-measure-details", draft.points.size >= 2) {
                onAction(MapAction.OpenSurface(MapSurface.Measurement))
            }
            MapTextButton(stringResource(R.string.map_fit_all), "map-measure-fit", draft.points.size >= 2) {
                onAction(MapAction.RequestCamera(MapCameraTarget.Bounds(minimalBounds(draft.points)), MapCameraIntent.VIEW_ROUTE, state.viewportInsets()))
            }
            MapTextButton(stringResource(R.string.map_convert_to_route), "map-measure-convert", draft.points.size >= 2) {
                onAction(MapAction.ConvertMeasurementToManualRoute(defaultRouteName))
            }
        }
    }
}

@Composable
private fun CrosshairAction(
    state: MapState,
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
            if (state.precisePointEdit == null) {
                onAction(MapAction.CrosshairConfirmed(coordinate, port.query(target, INTERACTIVE_OVERLAYS)))
            } else {
                onAction(MapAction.ConfirmPrecisePoint(coordinate))
            }
        }
    }
}

@Composable
private fun MapRootCommandBar(
    state: MapState,
    viewportInsets: MapViewportInsets,
    onAction: (MapAction) -> Unit,
) {
    val density = LocalDensity.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(LocalWpTheme.current.background.copy(alpha = .98f))
            .padding(
                start = with(density) { viewportInsets.leftPx.toDp() } + 4.dp,
                end = with(density) { viewportInsets.rightPx.toDp() } + 4.dp,
                top = 4.dp,
                bottom = 4.dp,
            ).testTag("map-root-command-bar"),
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
        MapCommandButton(R.string.map_coordinate_input_short, "map-coordinate-input", false, Modifier.weight(1f)) {
            onAction(MapAction.OpenSurface(MapSurface.CoordinateInput))
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
            MapSurface.Measurement -> R.string.map_measure_title to R.string.map_measure_context
            MapSurface.CoordinateInput -> R.string.map_coordinate_input to R.string.map_coordinate_input_context
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
                MapSurface.Measurement -> MeasurementPage(state, onAction)
                MapSurface.CoordinateInput -> CoordinateInputPage(state, onAction)
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
private fun MeasurementPage(state: MapState, onAction: (MapAction) -> Unit) {
    val draft = state.measurementDraft ?: return
    val summary = MeasurementMath.summarize(draft)
    val colors = LocalWpTheme.current
    WpText(stringResource(R.string.map_measure_total, summary.totalDistanceMeters.distanceText()), 18, weight = FontWeight.Light)
    if (summary.segments.isEmpty()) {
        WpText(
            stringResource(
                if (summary.prompt == MeasurementPrompt.PLACE_START) {
                    R.string.map_measure_place_start
                } else {
                    R.string.map_measure_place_end
                },
            ),
            12,
            color = colors.muted,
        )
    }
    summary.segments.forEach { segment ->
        Column(
            Modifier.fillMaxWidth().border(1.dp, colors.muted.copy(alpha = .45f)).padding(8.dp)
                .testTag("map-measure-segment-${segment.fromIndex}"),
        ) {
            WpText(
                stringResource(
                    R.string.map_measure_segment,
                    segment.fromIndex + 1,
                    segment.toIndex + 1,
                    segment.distanceMeters.distanceText(),
                    segment.bearingText(),
                ),
                12,
            )
        }
    }
    draft.points.forEachIndexed { index, point ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WpText(stringResource(R.string.map_measure_point, index + 1, point.coordinateText()), 11, modifier = Modifier.weight(1f))
            MapActionText(R.string.map_move_point, "map-measure-point-move-$index") {
                onAction(MapAction.CloseSurface)
                onAction(MapAction.BeginPrecisePointEdit(MapPrecisePointEdit.Move(MapEditTarget.MeasurementPoint(index))))
            }
            MapActionText(R.string.map_delete_point, "map-measure-point-delete-$index") {
                onAction(MapAction.DeleteMeasurementPoint(index))
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        MapTextButton(stringResource(R.string.map_undo), "map-measure-page-undo", draft.undo.isNotEmpty()) {
            onAction(MapAction.UndoMeasurementEdit)
        }
        MapTextButton(stringResource(R.string.map_redo), "map-measure-page-redo", draft.redo.isNotEmpty()) {
            onAction(MapAction.RedoMeasurementEdit)
        }
        MapTextButton(stringResource(R.string.map_clear), "map-measure-page-clear", draft.points.isNotEmpty()) {
            onAction(MapAction.ClearMeasurement)
        }
        MapActionText(R.string.map_close, "map-measure-page-close") { onAction(MapAction.CloseSurface) }
    }
}

@Composable
private fun CoordinateInputPage(state: MapState, onAction: (MapAction) -> Unit) {
    val colors = LocalWpTheme.current
    val initial = state.coordinateInputSeed()
    var format by remember(state.surface, state.precisePointEdit) { mutableStateOf(CoordinateFormat.DECIMAL_DEGREES) }
    val initialText = remember(initial) { CoordinateCodec.format(initial, CoordinateFormat.DECIMAL_DEGREES) }
    var latitude by remember(state.surface, state.precisePointEdit) { mutableStateOf(initialText.latitude) }
    var longitude by remember(state.surface, state.precisePointEdit) { mutableStateOf(initialText.longitude) }
    var failure by remember(state.surface, state.precisePointEdit) { mutableStateOf<CoordinateParseResult.Failure?>(null) }

    WpText(
        stringResource(
            if (format == CoordinateFormat.DECIMAL_DEGREES) R.string.map_coordinate_format_dd
            else R.string.map_coordinate_format_dmm,
        ),
        12,
        color = colors.muted,
    )
    Row(Modifier.fillMaxWidth()) {
        MapCommandButton(
            R.string.map_coordinate_format_dd_short,
            "map-coordinate-format-dd",
            format == CoordinateFormat.DECIMAL_DEGREES,
            Modifier.weight(1f),
        ) {
            val point = (CoordinateCodec.parse(latitude, longitude, format) as? CoordinateParseResult.Success)?.point ?: initial
            format = CoordinateFormat.DECIMAL_DEGREES
            CoordinateCodec.format(point, format).also {
                latitude = it.latitude
                longitude = it.longitude
            }
            failure = null
        }
        MapCommandButton(
            R.string.map_coordinate_format_dmm_short,
            "map-coordinate-format-dmm",
            format == CoordinateFormat.DEGREES_DECIMAL_MINUTES,
            Modifier.weight(1f),
        ) {
            val point = (CoordinateCodec.parse(latitude, longitude, format) as? CoordinateParseResult.Success)?.point ?: initial
            format = CoordinateFormat.DEGREES_DECIMAL_MINUTES
            CoordinateCodec.format(point, format).also {
                latitude = it.latitude
                longitude = it.longitude
            }
            failure = null
        }
    }
    CoordinateTextField(
        R.string.map_coordinate_latitude,
        latitude,
        failure?.takeIf { it.field == CoordinateField.LATITUDE },
        "map-coordinate-latitude",
    ) {
        latitude = it
        failure = null
    }
    CoordinateTextField(
        R.string.map_coordinate_longitude,
        longitude,
        failure?.takeIf { it.field == CoordinateField.LONGITUDE },
        "map-coordinate-longitude",
    ) {
        longitude = it
        failure = null
    }
    Row(Modifier.fillMaxWidth()) {
        MapActionText(R.string.map_coordinate_confirm, "map-coordinate-confirm") {
            when (val result = CoordinateCodec.parse(latitude, longitude, format)) {
                is CoordinateParseResult.Success -> onAction(MapAction.CoordinateEntered(result.point))
                is CoordinateParseResult.Failure -> failure = result
            }
        }
        MapActionText(R.string.map_cancel, "map-coordinate-cancel") { onAction(MapAction.CloseSurface) }
    }
}

@Composable
private fun CoordinateTextField(
    label: Int,
    value: String,
    failure: CoordinateParseResult.Failure?,
    tag: String,
    onValueChange: (String) -> Unit,
) {
    val colors = LocalWpTheme.current
    Column {
        WpText(stringResource(label), 10, color = colors.muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.foreground, fontSize = 16.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, colors.muted).padding(8.dp).testTag(tag),
        )
        failure?.let {
            WpText(coordinateErrorText(it.error), 10, color = colors.accent, modifier = Modifier.testTag("$tag-error"))
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
    listOf(size.width, size.height, insets.leftPx, insets.topPx, insets.rightPx, insets.bottomPx)
        .fold(17L) { hash, value -> hash * 31L + value }
        .and(Long.MAX_VALUE)
        .coerceAtLeast(1L)

private fun MapState.viewportInsets(): MapViewportInsets = viewport?.obscuredInsets ?: MapViewportInsets()

private fun GeoPoint.coordinateText(): String = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

private fun List<GeoPoint>.toBounds(): GeoBounds = minimalBounds(this)

private fun Double.distanceText(): String = if (this < 1_000.0) {
    String.format(Locale.getDefault(), "%.0f m", this)
} else {
    String.format(Locale.getDefault(), "%.2f NM", this / 1_852.0)
}

private fun com.yokuli.marine.map.domain.MeasurementSegment.bearingText(): String =
    if (initialBearingTrueDegrees == null || azimuthAmbiguous) {
        "—"
    } else {
        String.format(Locale.getDefault(), "%.1f°T", initialBearingTrueDegrees)
    }

private fun MapHitResult.measurementPointIndexOrNull(): Int? =
    takeIf { overlayId == MapOverlayId.MEASUREMENT_POINTS }
        ?.objectId
        ?.removePrefix("measurement-point:")
        ?.toIntOrNull()

private fun MapHitResult.routePointTargetOrNull(): MapEditTarget.RoutePoint? {
    if (overlayId != MapOverlayId.MANUAL_ROUTE_POINTS) return null
    val body = objectId.removePrefix("route-point:")
    val separator = body.lastIndexOf(':')
    if (separator <= 0 || separator == body.lastIndex) return null
    val index = body.substring(separator + 1).toIntOrNull() ?: return null
    return MapEditTarget.RoutePoint(body.substring(0, separator), index)
}

private fun MapState.coordinateInputSeed(): GeoPoint = when (val edit = precisePointEdit) {
    is MapPrecisePointEdit.Move -> when (val target = edit.target) {
        is MapEditTarget.MeasurementPoint -> measurementDraft?.points?.getOrNull(target.index)
        is MapEditTarget.RoutePoint -> routeDrafts.firstOrNull { it.id == target.draftId }?.waypoints?.getOrNull(target.index)
    }
    is MapPrecisePointEdit.InsertMeasurement -> {
        val points = measurementDraft?.points.orEmpty()
        points.getOrNull(edit.index - 1) ?: points.getOrNull(edit.index)
    }
    null -> selection?.point
} ?: camera.center

@Composable
private fun coordinateErrorText(error: CoordinateError): String = stringResource(
    when (error) {
        CoordinateError.EMPTY -> R.string.map_coordinate_error_empty
        CoordinateError.INVALID_FORMAT -> R.string.map_coordinate_error_format
        CoordinateError.NON_FINITE -> R.string.map_coordinate_error_nonfinite
        CoordinateError.OUT_OF_RANGE -> R.string.map_coordinate_error_range
        CoordinateError.MINUTES_OUT_OF_RANGE -> R.string.map_coordinate_error_minutes
        CoordinateError.SIGN_HEMISPHERE_CONFLICT -> R.string.map_coordinate_error_conflict
    },
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
    MapOverlayId.MEASUREMENT_POINTS,
    MapOverlayId.MANUAL_ROUTE,
    MapOverlayId.MANUAL_ROUTE_POINTS,
)
