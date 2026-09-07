package com.yokuli.marine.map.offline

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageLease
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapCameraCommand
import com.yokuli.marine.map.domain.MapCameraCommandId
import com.yokuli.marine.map.domain.MapCameraTarget
import com.yokuli.marine.map.domain.MapEditGesture
import com.yokuli.marine.map.domain.MapEditTarget
import com.yokuli.marine.map.domain.MapGestureId
import com.yokuli.marine.map.domain.MapHitResult
import com.yokuli.marine.map.domain.MapOverlayId
import com.yokuli.marine.map.domain.MapRendererFailure
import com.yokuli.marine.map.domain.MapRendererGeneration
import com.yokuli.marine.map.domain.MapRendererQueryPort
import com.yokuli.marine.map.domain.MapRendererReadiness
import com.yokuli.marine.map.domain.MapScreenPoint
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapTileCoverageStatus
import com.yokuli.marine.map.domain.MapTileSnapshotFormat
import com.yokuli.marine.map.domain.MapTileSnapshotSink
import com.yokuli.marine.map.domain.PositionRenderPolicy
import com.yokuli.marine.map.domain.VesselMarkerStyle
import com.yokuli.marine.map.domain.Wgs84Geodesic
import com.yokuli.marine.map.domain.ImportedTrackDisplayLod
import com.yokuli.marine.map.domain.ImportedTrack
import com.yokuli.marine.map.domain.Wgs84Polyline
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplaySelection
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayViewport
import com.yokuli.marine.map.domain.chartlibrary.ChartResourceAccessPort
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** The single production renderer. It never requires a provider key or an online style. */
@Composable
fun OfflineMarineChartSurface(
    state: MapState,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
    onQueryPortChanged: (MapRendererQueryPort?) -> Unit = {},
    acquirePackageLease: (ChartPackageId) -> ChartPackageLease = { ChartPackageLease {} },
    chartLibraryAccess: ChartResourceAccessPort? = null,
    chartTileGateway: ChartLoopbackTileGateway? = null,
    tileSnapshotSink: MapTileSnapshotSink? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentAction by rememberUpdatedState(onAction)
    val currentState by rememberUpdatedState(state)
    val currentQueryPortChanged by rememberUpdatedState(onQueryPortChanged)
    remember(context.applicationContext) { MapLibre.getInstance(context.applicationContext) }

    val generation = remember { MapRendererGeneration(nextRendererGeneration.incrementAndGet()) }
    // The remember calculation may be evaluated by an abandoned composition. Only count a
    // renderer after its lifecycle effect commits and MapView.onCreate has actually run.
    val mapView = remember(context, generation) { MapView(context) }
    val lifecycleDriver = remember(mapView) { OfflineMapLifecycleDriver(mapView) }
    val disposed = remember(mapView) { AtomicBoolean(false) }
    val styleGeneration = remember(mapView) { AtomicLong(0L) }
    val activeCameraCommand = remember(mapView) { AtomicReference<MapCameraCommandId?>(null) }
    val submittedCameraCommand = remember(mapView) { AtomicReference<MapCameraCommandId?>(null) }
    val activePointDrag = remember(mapView) { AtomicReference<ActivePointDrag?>(null) }
    val touchSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop.toDouble() }
    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    var activeStyle by remember(mapView) { mutableStateOf<Style?>(null) }
    val displayPlan = state.chartDisplayPlan
    val librarySelectionActive = displayPlan.selection !is ChartDisplaySelection.None
    val activePackage = if (librarySelectionActive) null else {
        state.chartPackages.firstOrNull { it.id == state.activeChartPackageId }
    }
    var preparedDisplay by remember(mapView) { mutableStateOf<PreparedChartDisplay?>(null) }
    val snapshotCadence = remember(mapView) { RendererSnapshotCadence() }

    LaunchedEffect(
        map,
        state.camera,
        state.chartDisplayPlan.fingerprint,
        state.renderer.readiness,
        state.renderer.tileCoverage,
        state.renderer.overlayStatus,
        tileSnapshotSink,
    ) {
        val readyMap = map ?: return@LaunchedEffect
        val sink = tileSnapshotSink ?: return@LaunchedEffect
        if (state.renderer.readiness != MapRendererReadiness.RENDERER_READY) return@LaunchedEffect
        delay(snapshotCadence.delayUntilNext(SystemClock.elapsedRealtime()))
        snapshotCadence.markRequested(SystemClock.elapsedRealtime())
        val request = sink.begin(state.camera, "maplibre:${state.chartDisplayPlan.fingerprint}")
        readyMap.snapshot { bitmap -> bitmap?.publishTileSnapshot(sink, request) }
    }

    LaunchedEffect(displayPlan.fingerprint, chartLibraryAccess, chartTileGateway) {
        preparedDisplay?.close()
        preparedDisplay = null
        if (displayPlan.layers.isEmpty() || chartLibraryAccess == null || chartTileGateway == null) return@LaunchedEffect
        preparedDisplay = try {
            withContext(Dispatchers.IO) {
                ChartDisplayPreparer(chartLibraryAccess, chartTileGateway).prepare(displayPlan)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            currentAction(MapAction.RendererFailed(generation, MapRendererFailure.PACKAGE_MISSING))
            null
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            preparedDisplay?.close()
            preparedDisplay = null
        }
    }

    DisposableEffect(activePackage?.id, acquirePackageLease) {
        val lease = activePackage?.id?.let(acquirePackageLease)
        onDispose { lease?.close() }
    }

    LaunchedEffect(generation) {
        currentAction(MapAction.RendererHostReady(generation))
    }

    DisposableEffect(mapView, lifecycle, context.applicationContext) {
        lifecycleDriver.create()
        OfflineMapInstanceMetrics.onCreated()
        val observer = LifecycleEventObserver { _, event -> lifecycleDriver.onEvent(event) }
        val memoryCallbacks = OfflineMapMemoryCallbacks(mapView)
        lifecycle.addObserver(observer)
        lifecycleDriver.syncTo(lifecycle.currentState)
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)
        onDispose {
            disposed.set(true)
            styleGeneration.incrementAndGet()
            currentQueryPortChanged(null)
            currentAction(MapAction.RendererDetached(generation))
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
            lifecycleDriver.destroy()
            OfflineMapInstanceMetrics.onDestroyed()
        }
    }

    DisposableEffect(mapView, generation) {
        var cameraListener: MapLibreMap.OnCameraIdleListener? = null
        var clickListener: MapLibreMap.OnMapClickListener? = null
        var longPressListener: MapLibreMap.OnMapLongClickListener? = null
        var queryPort: MapRendererQueryPort? = null
        val loadFailureListener = MapView.OnDidFailLoadingMapListener {
            if (!disposed.get()) currentAction(MapAction.RendererFailed(generation, MapRendererFailure.STYLE))
        }
        val renderErrorListener = MapView.OnRenderErrorListener {
            if (!disposed.get()) {
                currentAction(MapAction.RendererCoverageChanged(generation, MapTileCoverageStatus.DEGRADED))
            }
        }
        mapView.addOnDidFailLoadingMapListener(loadFailureListener)
        mapView.addOnRenderErrorListener(renderErrorListener)
        mapView.setOnTouchListener { _, event ->
            val screenPoint = MapScreenPoint(event.x.toDouble(), event.y.toDouble())
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val target = queryPort?.query(screenPoint, HANDLE_OVERLAYS)
                        ?.asSequence()
                        ?.mapNotNull(MapHitResult::toEditTargetOrNull)
                        ?.firstOrNull()
                    if (target == null) {
                        false
                    } else {
                        val gestureId = MapGestureId(
                            "renderer-${generation.value}-${nextPointGesture.incrementAndGet()}",
                        )
                        activePointDrag.set(ActivePointDrag(gestureId, target, screenPoint))
                        currentAction(MapAction.BeginPointDrag(gestureId, target))
                        true
                    }
                }
                MotionEvent.ACTION_MOVE -> activePointDrag.get()?.let { drag ->
                    val moved = drag.moved || PointDragMotion.hasMoved(drag.downPoint, screenPoint, touchSlop)
                    if (moved) {
                        activePointDrag.set(drag.copy(moved = true))
                        queryPort?.unproject(screenPoint)?.let { point ->
                            currentAction(MapAction.PreviewPointDrag(drag.id, point))
                        }
                    }
                    true
                } ?: false
                MotionEvent.ACTION_UP -> activePointDrag.getAndSet(null)?.let { drag ->
                    val finalPoint = queryPort?.unproject(screenPoint)
                    val moved = drag.moved || PointDragMotion.hasMoved(drag.downPoint, screenPoint, touchSlop)
                    if (finalPoint == null) {
                        currentAction(MapAction.CancelPointDrag(drag.id))
                    } else if (!moved) {
                        currentAction(MapAction.CancelPointDrag(drag.id))
                        currentAction(MapAction.MapTapped(finalPoint, listOf(drag.target.toHitResult())))
                    } else {
                        currentAction(MapAction.CommitPointDrag(drag.id, finalPoint))
                    }
                    true
                } ?: false
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> activePointDrag.getAndSet(null)?.let { drag ->
                    currentAction(MapAction.CancelPointDrag(drag.id))
                    true
                } ?: false
                else -> activePointDrag.get() != null
            }
        }
        mapView.getMapAsync { readyMap ->
            if (disposed.get()) return@getMapAsync
            map = readyMap
            currentAction(MapAction.RendererHostReady(generation))
            readyMap.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true
                isRotateGesturesEnabled = true
                isScrollGesturesEnabled = true
                isZoomGesturesEnabled = true
                isTiltGesturesEnabled = false
            }
            queryPort = MapLibreRendererQueryPort(readyMap) { !disposed.get() }
            currentQueryPortChanged(queryPort)
            cameraListener = MapLibreMap.OnCameraIdleListener {
                readyMap.currentChartViewport()?.let { viewport ->
                    currentAction(MapAction.ChartDisplayViewportChanged(generation, viewport))
                }
                if (activeCameraCommand.get() == null) {
                    readyMap.cameraPosition.toDomainCameraOrNull()?.let { camera ->
                        currentAction(MapAction.RendererCameraIdle(generation, camera))
                    }
                }
            }.also(readyMap::addOnCameraIdleListener)
            clickListener = MapLibreMap.OnMapClickListener { point ->
                val screenPoint = readyMap.projection.toScreenLocation(point).toDomainScreenPoint()
                currentAction(
                    MapAction.MapTapped(
                        point.toDomainPoint(),
                        requireNotNull(queryPort).query(screenPoint, INTERACTIVE_OVERLAYS),
                    ),
                )
                true
            }.also(readyMap::addOnMapClickListener)
            longPressListener = MapLibreMap.OnMapLongClickListener { point ->
                val screenPoint = readyMap.projection.toScreenLocation(point).toDomainScreenPoint()
                currentAction(
                    MapAction.MapLongPressed(
                        point.toDomainPoint(),
                        requireNotNull(queryPort).query(screenPoint, INTERACTIVE_OVERLAYS),
                    ),
                )
                true
            }.also(readyMap::addOnMapLongClickListener)
        }
        onDispose {
            activePointDrag.getAndSet(null)?.let { drag -> currentAction(MapAction.CancelPointDrag(drag.id)) }
            mapView.setOnTouchListener(null)
            cameraListener?.let { listener -> map?.removeOnCameraIdleListener(listener) }
            clickListener?.let { listener -> map?.removeOnMapClickListener(listener) }
            longPressListener?.let { listener -> map?.removeOnMapLongClickListener(listener) }
            mapView.removeOnDidFailLoadingMapListener(loadFailureListener)
            mapView.removeOnRenderErrorListener(renderErrorListener)
        }
    }

    LaunchedEffect(
        map,
        activePackage?.id,
        activePackage?.localUri,
        activePackage?.tileSize,
        displayPlan.fingerprint,
        preparedDisplay?.planFingerprint,
        generation,
    ) {
        val readyMap = map ?: return@LaunchedEffect
        if (
            librarySelectionActive && displayPlan.layers.isNotEmpty() &&
            chartLibraryAccess != null && chartTileGateway != null &&
            preparedDisplay?.planFingerprint != displayPlan.fingerprint
        ) {
            styleGeneration.incrementAndGet()
            activeStyle = null
            readyMap.setStyle(Style.Builder().fromJson(EMPTY_STYLE))
            currentAction(MapAction.RendererCoverageChanged(generation, MapTileCoverageStatus.CHECKING))
            return@LaunchedEffect
        }
        val requestGeneration = styleGeneration.incrementAndGet()
        activeStyle = null
        submittedCameraCommand.set(null)
        val packageExists = activePackage?.hasReadableMbTiles() == true
        currentAction(
            MapAction.RendererCoverageChanged(
                generation,
                when {
                    librarySelectionActive && displayPlan.layers.isNotEmpty() -> MapTileCoverageStatus.CHECKING
                    librarySelectionActive -> MapTileCoverageStatus.PACKAGE_MISSING
                    activePackage == null -> MapTileCoverageStatus.NO_PACKAGE
                    packageExists -> MapTileCoverageStatus.CHECKING
                    else -> MapTileCoverageStatus.PACKAGE_MISSING
                },
            ),
        )
        readyMap.setStyle(Style.Builder().fromJson(EMPTY_STYLE)) { style ->
            if (disposed.get() || styleGeneration.get() != requestGeneration) return@setStyle
            try {
                if (activePackage != null && packageExists) {
                    style.addSource(RasterSource(CHART_SOURCE, activePackage.localUri, activePackage.tileSize))
                    style.addLayer(RasterLayer(CHART_LAYER, CHART_SOURCE))
                }
                preparedDisplay?.layers?.forEachIndexed { index, prepared ->
                    val sourceId = "library-raster-source-$index"
                    val layerId = "library-raster-layer-$index"
                    style.addSource(prepared.registration.toRasterSource(sourceId))
                    style.addLayer(
                        RasterLayer(layerId, sourceId).withProperties(rasterOpacity(prepared.planLayer.opacity)),
                    )
                }
                style.addPointOverlay(MapOverlayId.SAVED_PLACES, 0xfff7b500.toInt(), 5f)
                style.addPointOverlay(MapOverlayId.SELECTION, 0xffffffff.toInt(), 7f)
                style.addLineOverlay(MapOverlayId.MEASUREMENT, 0xfff7b500.toInt(), 3f)
                style.addPointOverlay(MapOverlayId.MEASUREMENT_POINTS, 0xfff7b500.toInt(), 6f)
                style.addMeasurementLabels()
                style.addLineOverlay(MapOverlayId.MANUAL_ROUTE, 0xff00a4ef.toInt(), 5f)
                style.addLineOverlay(MapOverlayId.ACTIVE_NAVIGATION_LEG, 0xfff7b500.toInt(), 7f)
                style.addLineOverlay(MapOverlayId.ACTIVE_TRACK, 0xff00d084.toInt(), 4f)
                style.addPointOverlay(MapOverlayId.MANUAL_ROUTE_POINTS, 0xff00a4ef.toInt(), 5f)
                style.addPointLabels(MapOverlayId.MANUAL_ROUTE_POINTS)
                style.addLineOverlay(MapOverlayId.IMPORTED_TRACKS, 0xff9b59b6.toInt(), 3f)
                style.addPointOverlay(MapOverlayId.POSITION_OBSERVATION, 0xff00d084.toInt(), 7f)
                style.addPointOverlay(MapOverlayId.POSITION_HISTORY, 0xff7f8c8d.toInt(), 6f)
                style.addLineOverlay(MapOverlayId.TRUE_HEADING, 0xff00d084.toInt(), 4f)
                style.addLineOverlay(MapOverlayId.COURSE_OVER_GROUND, 0xff00a4ef.toInt(), 2f, dashed = true)
                style.addLineOverlay(MapOverlayId.POSITION_ACCURACY, 0xffb7c4c8.toInt(), 1.5f, dashed = true)
                activeStyle = style
                currentAction(MapAction.RendererHostReady(generation))
                currentAction(MapAction.RendererReady(generation))
                readyMap.currentChartViewport()?.let { viewport ->
                    currentAction(MapAction.ChartDisplayViewportChanged(generation, viewport))
                }
                currentAction(
                    MapAction.RendererCoverageChanged(
                        generation,
                        when {
                            librarySelectionActive && preparedDisplay?.layers?.isNotEmpty() == true &&
                                preparedDisplay?.rejectedLayerCount == 0 -> MapTileCoverageStatus.PACKAGE_ATTACHED
                            librarySelectionActive && preparedDisplay?.layers?.isNotEmpty() == true -> MapTileCoverageStatus.DEGRADED
                            librarySelectionActive -> MapTileCoverageStatus.PACKAGE_MISSING
                            activePackage == null -> MapTileCoverageStatus.NO_PACKAGE
                            packageExists -> MapTileCoverageStatus.PACKAGE_ATTACHED
                            else -> MapTileCoverageStatus.PACKAGE_MISSING
                        },
                    ),
                )
            } catch (_: Throwable) {
                currentAction(MapAction.RendererFailed(generation, MapRendererFailure.STYLE))
            }
        }
    }

    LaunchedEffect(
        map,
        generation,
        state.renderer.generation,
        state.renderer.readiness,
        state.renderer.pendingCameraCommand,
    ) {
        val readyMap = map ?: return@LaunchedEffect
        val command = state.renderer.pendingCameraCommand ?: return@LaunchedEffect
        if (
            state.renderer.generation != generation ||
            state.renderer.readiness != MapRendererReadiness.RENDERER_READY ||
            submittedCameraCommand.get() == command.id
        ) {
            return@LaunchedEffect
        }
        submittedCameraCommand.set(command.id)
        activeCameraCommand.set(command.id)
        if (command.target is MapCameraTarget.Exact) {
            readyMap.setPadding(
                command.viewportInsets.leftPx,
                command.viewportInsets.topPx,
                command.viewportInsets.rightPx,
                command.viewportInsets.bottomPx,
            )
        } else {
            readyMap.setPadding(0, 0, 0, 0)
        }
        readyMap.moveCamera(command.toCameraUpdate(), object : MapLibreMap.CancelableCallback {
            override fun onFinish() {
                if (disposed.get() || activeCameraCommand.getAndSet(null) != command.id) return
                readyMap.cameraPosition.toDomainCameraOrNull()?.let { camera ->
                    currentAction(MapAction.RendererCameraIdle(generation, camera, command.id))
                }
            }

            override fun onCancel() {
                activeCameraCommand.compareAndSet(command.id, null)
                submittedCameraCommand.compareAndSet(command.id, null)
            }
        })
    }

    LaunchedEffect(
        activeStyle,
        state.selection,
        state.places,
        state.measurementDraft,
        state.routeDraft,
        state.activeRoutePlanId,
        state.activeNavigationRoute,
        state.savedRoutes,
        state.importedTracks,
        state.editGesture,
        state.activeNavigationLeg,
        state.activeTrackSegments,
        state.camera.zoom,
        state.position,
    ) {
        val style = activeStyle ?: return@LaunchedEffect
        val measurementPoints = state.visibleMeasurementPoints
        val routePoints = state.routePointsWithPreview()
        val trackFeatures = withContext(Dispatchers.Default) {
            state.importedTracks.toDisplayFeatureCollection(state.camera.zoom)
        }
        val positionFrame = withContext(Dispatchers.Default) { state.position.toOverlayFrame() }
        withContext(Dispatchers.Main.immediate) {
            if (disposed.get() || activeStyle !== style) return@withContext
            style.source(MapOverlayId.SAVED_PLACES)?.setGeoJson(
                FeatureCollection.fromFeatures(state.places.map { place -> place.point.toFeature("place:${place.id}") }),
            )
            style.source(MapOverlayId.SELECTION)?.setGeoJson(
                state.selection?.point.toFeatureCollection("selection"),
            )
            style.source(MapOverlayId.MEASUREMENT)?.setGeoJson(
                measurementPoints.toGeodesicFeatureCollection(
                    "measurement",
                    state.geodesicMaxSegmentMeters(measurementPoints),
                ),
            )
            style.source(MapOverlayId.MEASUREMENT_POINTS)?.setGeoJson(
                FeatureCollection.fromFeatures(
                    measurementPoints.mapIndexed { index, point ->
                        point.toLabeledFeature("measurement-point:$index", if (index == 0) "A" else "B")
                    },
                ),
            )
            style.source(MapOverlayId.MANUAL_ROUTE)?.setGeoJson(
                routePoints.toGeodesicFeatureCollection(
                    "route:${state.routeOverlayObjectId()}",
                    state.geodesicMaxSegmentMeters(routePoints),
                ),
            )
            style.source(MapOverlayId.MANUAL_ROUTE_POINTS)?.setGeoJson(
                FeatureCollection.fromFeatures(
                    routePoints.mapIndexed { index, point ->
                        point.toLabeledFeature(state.routePointObjectId(index), (index + 1).toString())
                    },
                ),
            )
            style.source(MapOverlayId.ACTIVE_NAVIGATION_LEG)?.setGeoJson(
                state.activeNavigationLeg.toGeodesicFeatureCollection(
                    "active-navigation-leg",
                    state.geodesicMaxSegmentMeters(state.activeNavigationLeg),
                ),
            )
            style.source(MapOverlayId.ACTIVE_TRACK)?.setGeoJson(
                FeatureCollection.fromFeatures(
                    state.activeTrackSegments.mapIndexedNotNull { index, segment ->
                        segment.takeIf { it.size >= 2 }?.let { points ->
                            Feature.fromGeometry(
                                LineString.fromLngLats(points.map(GeoPoint::toGeoJsonPoint)),
                                null,
                                "active-track:$index",
                            )
                        }
                    },
                ),
            )
            style.source(MapOverlayId.IMPORTED_TRACKS)?.setGeoJson(trackFeatures)
            style.source(MapOverlayId.POSITION_OBSERVATION)?.setGeoJson(positionFrame.livePoint)
            style.source(MapOverlayId.POSITION_HISTORY)?.setGeoJson(positionFrame.historicalPoint)
            style.source(MapOverlayId.TRUE_HEADING)?.setGeoJson(positionFrame.trueHeading)
            style.source(MapOverlayId.COURSE_OVER_GROUND)?.setGeoJson(positionFrame.courseOverGround)
            style.source(MapOverlayId.POSITION_ACCURACY)?.setGeoJson(positionFrame.accuracy)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private class RendererSnapshotCadence {
    private var lastRequestAtMillis = Long.MIN_VALUE
    fun delayUntilNext(nowMillis: Long): Long = if (lastRequestAtMillis == Long.MIN_VALUE) 0L else {
        (lastRequestAtMillis + TILE_SNAPSHOT_CAPTURE_INTERVAL_MILLIS - nowMillis).coerceAtLeast(0L)
    }
    fun markRequested(nowMillis: Long) { lastRequestAtMillis = nowMillis }
}

private fun Bitmap.publishTileSnapshot(
    sink: MapTileSnapshotSink,
    request: com.yokuli.marine.map.domain.MapTileSnapshotRequest,
) {
    if (width <= 0 || height <= 0) return
    val scale = kotlin.math.min(
        1.0,
        kotlin.math.min(TILE_SNAPSHOT_WIDTH.toDouble() / width, TILE_SNAPSHOT_HEIGHT.toDouble() / height),
    )
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    val scaled = if (targetWidth == width && targetHeight == height) this
    else Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    val output = ByteArrayOutputStream()
    try {
        if (scaled.compress(Bitmap.CompressFormat.JPEG, 72, output)) {
            sink.complete(request, scaled.width, scaled.height, MapTileSnapshotFormat.JPEG, output.toByteArray())
        }
    } finally {
        if (scaled !== this) scaled.recycle()
    }
}

private const val TILE_SNAPSHOT_CAPTURE_INTERVAL_MILLIS = 5_000L
private const val TILE_SNAPSHOT_WIDTH = 320
private const val TILE_SNAPSHOT_HEIGHT = 180

private fun MapCameraCommand.toCameraUpdate(): CameraUpdate = when (val value = target) {
    is MapCameraTarget.Exact -> CameraUpdateFactory.newCameraPosition(value.camera.toCameraPosition())
    is MapCameraTarget.Bounds -> CameraUpdateFactory.newLatLngBounds(
        value.bounds.toLatLngBounds(),
        viewportInsets.leftPx,
        viewportInsets.topPx,
        viewportInsets.rightPx,
        viewportInsets.bottomPx,
    )
}

private fun MapCamera.toCameraPosition() = CameraPosition.Builder()
    .target(center.toLatLng()).zoom(zoom).bearing(bearing).tilt(0.0).build()
private fun CameraPosition.toDomainCameraOrNull() = target?.let { MapCamera(it.toDomainPoint(), zoom, bearing) }
private fun LatLng.toDomainPoint() = GeoPoint(latitude.coerceIn(-90.0, 90.0), wrapLongitude(longitude))
private fun GeoPoint.toLatLng() = LatLng(latitude.coerceIn(-WEB_MERCATOR_MAX_LATITUDE, WEB_MERCATOR_MAX_LATITUDE), longitude)
private fun PointF.toDomainScreenPoint() = MapScreenPoint(x.toDouble(), y.toDouble())
private fun GeoBounds.toLatLngBounds() = LatLngBounds.from(
    north.coerceAtMost(WEB_MERCATOR_MAX_LATITUDE),
    if (crossesAntimeridian) east + 360.0 else east,
    south.coerceAtLeast(-WEB_MERCATOR_MAX_LATITUDE),
    west,
)
internal fun GeoPoint.toGeoJsonPoint() = Point.fromLngLat(longitude, latitude)
private fun GeoPoint.toFeature(id: String) = Feature.fromGeometry(toGeoJsonPoint(), null, id)
private fun GeoPoint.toLabeledFeature(id: String, label: String) = toFeature(id).apply {
    addStringProperty("label", label)
}
private fun GeoPoint?.toFeatureCollection(id: String) = FeatureCollection.fromFeatures(
    if (this == null) emptyList() else listOf(toFeature(id)),
)
private fun List<GeoPoint>.toGeodesicFeatureCollection(
    id: String,
    maxSegmentMeters: Double,
) = FeatureCollection.fromFeatures(
    takeIf { it.size >= 2 }?.let { Wgs84Polyline.build(it, maxSegmentMeters) }?.parts?.mapIndexed { index, part ->
        Feature.fromGeometry(LineString.fromLngLats(part.map(GeoPoint::toGeoJsonPoint)), null, "$id:$index")
    }.orEmpty(),
)

internal fun List<ImportedTrack>.toDisplayFeatureCollection(zoom: Double): FeatureCollection =
    FeatureCollection.fromFeatures(
        flatMap { track ->
            ImportedTrackDisplayLod.sample(track, zoom).mapIndexedNotNull { segmentIndex, segment ->
                segment.points.takeIf { it.size >= 2 }?.let { points ->
                    Feature.fromGeometry(
                        LineString.fromLngLats(points.map { it.point.toGeoJsonPoint() }),
                        null,
                        "track:${track.id}:segment:$segmentIndex",
                    )
                }
            }
        },
    )

private fun MapState.routePointsWithPreview(): List<GeoPoint> {
    val points = visibleRoutePoints
    val draft = routeDraft?.takeIf { tool == com.yokuli.marine.map.domain.MapTool.MANUAL_ROUTE } ?: return points
    val gesture = editGesture ?: return draft.waypoints
    val target = gesture.target as? MapEditTarget.RoutePoint ?: return draft.waypoints
    if (target.draftId != draft.id) return draft.waypoints
    return draft.waypoints.replaceAt(target.index, gesture.previewPoint)
}

private fun MapState.routeOverlayObjectId(): String = routeDraft
    ?.takeIf { tool == com.yokuli.marine.map.domain.MapTool.MANUAL_ROUTE }
    ?.id
    ?: activeRoutePlanId
    ?: "active-navigation"

private fun MapState.routePointObjectId(index: Int): String = routeDraft
    ?.takeIf { tool == com.yokuli.marine.map.domain.MapTool.MANUAL_ROUTE }
    ?.let { "route-point:${it.id}:$index" }
    ?: "route-preview-point:${activeRoutePlanId ?: "active-navigation"}:$index"

private fun List<GeoPoint>.replaceAt(index: Int, point: GeoPoint): List<GeoPoint> =
    if (index !in indices) this else mapIndexed { itemIndex, item -> if (itemIndex == index) point else item }

private fun MapState.geodesicMaxSegmentMeters(points: List<GeoPoint>): Double =
    GeodesicRenderBudget.maxSegmentMeters(
        zoom = camera.zoom,
        maxAbsoluteLatitude = points.maxOfOrNull { kotlin.math.abs(it.latitude) }
            ?: kotlin.math.abs(camera.center.latitude),
    )

private fun Style.source(id: MapOverlayId) = getSourceAs<GeoJsonSource>(id.wireValue)

private fun Style.addPointOverlay(id: MapOverlayId, color: Int, radius: Float) {
    addSource(GeoJsonSource(id.wireValue, FeatureCollection.fromFeatures(emptyList<Feature>())))
    addLayer(
        CircleLayer(id.wireValue, id.wireValue).withProperties(
            circleColor(color),
            circleRadius(radius),
            circleStrokeColor(0xffffffff.toInt()),
            circleStrokeWidth(1.5f),
        ),
    )
}

private fun Style.addMeasurementLabels() = addPointLabels(MapOverlayId.MEASUREMENT_POINTS)

private fun Style.addPointLabels(id: MapOverlayId) {
    addLayer(
        SymbolLayer("${id.wireValue}-labels", id.wireValue)
            .withProperties(
                textField(get("label")),
                textSize(13f),
                textColor(0xff000000.toInt()),
                textAllowOverlap(true),
                textIgnorePlacement(true),
            ),
    )
}

private fun Style.addLineOverlay(id: MapOverlayId, color: Int, width: Float, dashed: Boolean = false) {
    addSource(GeoJsonSource(id.wireValue, FeatureCollection.fromFeatures(emptyList<Feature>())))
    val layer = LineLayer(id.wireValue, id.wireValue).withProperties(lineColor(color), lineWidth(width))
    if (dashed) layer.setProperties(lineDasharray(arrayOf(2f, 2f)))
    addLayer(layer)
}

internal data class PositionOverlayFrame(
    val livePoint: FeatureCollection,
    val historicalPoint: FeatureCollection,
    val trueHeading: FeatureCollection,
    val courseOverGround: FeatureCollection,
    val accuracy: FeatureCollection,
)

internal fun com.yokuli.marine.map.domain.PositionState.toOverlayFrame(): PositionOverlayFrame {
    val render = PositionRenderPolicy.resolve(this)
    val id = observation?.identity?.observationId.orEmpty()
    val live = render.point.takeIf {
        render.markerStyle == VesselMarkerStyle.LIVE_NEUTRAL || render.markerStyle == VesselMarkerStyle.LIVE_TRUE_HEADING
    }
    val historical = render.point.takeIf { render.markerStyle == VesselMarkerStyle.HISTORICAL }
    return PositionOverlayFrame(
        livePoint = live.toFeatureCollection("position:$id"),
        historicalPoint = historical.toFeatureCollection("position:$id"),
        trueHeading = render.point.directionFeatureCollection(
            render.trueHeadingDegrees,
            HEADING_VECTOR_METERS,
            "position-heading:$id",
        ),
        courseOverGround = render.point.directionFeatureCollection(
            render.courseVector?.trueDegrees,
            render.courseVector?.speedKnots?.times(COURSE_VECTOR_METERS_PER_KNOT)?.coerceIn(
                MIN_COURSE_VECTOR_METERS,
                MAX_COURSE_VECTOR_METERS,
            ),
            "position-course:$id",
        ),
        accuracy = render.point.accuracyFeatureCollection(render.accuracyMeters, "position-accuracy:$id"),
    )
}

private fun GeoPoint?.directionFeatureCollection(
    trueDegrees: Double?,
    distanceMeters: Double?,
    id: String,
): FeatureCollection {
    if (this == null || trueDegrees == null || distanceMeters == null) return emptyFeatureCollection()
    val end = Wgs84Geodesic.destination(this, trueDegrees, distanceMeters)
    return FeatureCollection.fromFeatures(
        listOf(Feature.fromGeometry(LineString.fromLngLats(listOf(toGeoJsonPoint(), end.toGeoJsonPoint())), null, id)),
    )
}

private fun GeoPoint?.accuracyFeatureCollection(radiusMeters: Double?, id: String): FeatureCollection {
    if (this == null || radiusMeters == null || radiusMeters <= 0.0) return emptyFeatureCollection()
    val ring = (0..ACCURACY_RING_SEGMENTS).map { index ->
        Wgs84Geodesic.destination(this, index * 360.0 / ACCURACY_RING_SEGMENTS, radiusMeters).toGeoJsonPoint()
    }
    return FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(LineString.fromLngLats(ring), null, id)))
}

private fun emptyFeatureCollection() = FeatureCollection.fromFeatures(emptyList<Feature>())

private fun ChartPackage.hasReadableMbTiles(): Boolean {
    val uri = Uri.parse(localUri)
    return uri.scheme == "mbtiles" && uri.path?.let { File(it).isFile && File(it).canRead() } == true
}

internal class MapLibreRendererQueryPort(
    private val map: MapLibreMap,
    private val isCurrent: () -> Boolean,
) : MapRendererQueryPort {
    override fun project(point: GeoPoint): MapScreenPoint? = ifCurrent {
        map.projection.toScreenLocation(point.toLatLng()).let { MapScreenPoint(it.x.toDouble(), it.y.toDouble()) }
    }

    override fun unproject(point: MapScreenPoint): GeoPoint? = ifCurrent {
        map.projection.fromScreenLocation(PointF(point.xPx.toFloat(), point.yPx.toFloat())).toDomainPoint()
    }

    override fun query(point: MapScreenPoint, overlayIds: Set<MapOverlayId>): List<MapHitResult> = ifCurrent {
        val layers = overlayIds.map { it.wireValue }.toTypedArray()
        map.queryRenderedFeatures(PointF(point.xPx.toFloat(), point.yPx.toFloat()), *layers).mapNotNull { feature ->
            val layer = overlayIds.firstOrNull { candidate ->
                feature.id()?.startsWith(candidate.objectIdPrefix()) == true
            } ?: return@mapNotNull null
            feature.id()?.let { MapHitResult(layer, it) }
        }
    }.orEmpty()

    private inline fun <T> ifCurrent(block: () -> T): T? = if (isCurrent()) runCatching(block).getOrNull() else null
}

private fun MapOverlayId.objectIdPrefix(): String = when (this) {
    MapOverlayId.SAVED_PLACES -> "place:"
    MapOverlayId.SELECTION -> "selection"
    MapOverlayId.MEASUREMENT -> "measurement:"
    MapOverlayId.MEASUREMENT_POINTS -> "measurement-point:"
    MapOverlayId.MANUAL_ROUTE -> "route:"
    MapOverlayId.MANUAL_ROUTE_POINTS -> "route-point:"
    MapOverlayId.ACTIVE_NAVIGATION_LEG -> "active-navigation-leg:"
    MapOverlayId.ACTIVE_TRACK -> "active-track:"
    MapOverlayId.IMPORTED_TRACKS -> "track:"
    MapOverlayId.POSITION_OBSERVATION -> "position:"
    MapOverlayId.POSITION_HISTORY -> "position:"
    MapOverlayId.TRUE_HEADING -> "position-heading:"
    MapOverlayId.COURSE_OVER_GROUND -> "position-course:"
    MapOverlayId.POSITION_ACCURACY -> "position-accuracy:"
}

private val INTERACTIVE_OVERLAYS = setOf(
    MapOverlayId.SAVED_PLACES,
    MapOverlayId.SELECTION,
    MapOverlayId.MEASUREMENT,
    MapOverlayId.MEASUREMENT_POINTS,
    MapOverlayId.MANUAL_ROUTE,
    MapOverlayId.MANUAL_ROUTE_POINTS,
    MapOverlayId.IMPORTED_TRACKS,
)

private val HANDLE_OVERLAYS = setOf(
    MapOverlayId.MEASUREMENT_POINTS,
    MapOverlayId.MANUAL_ROUTE_POINTS,
)

internal fun MapHitResult.toEditTargetOrNull(): MapEditTarget? = when (overlayId) {
    MapOverlayId.MEASUREMENT_POINTS -> objectId.removePrefix("measurement-point:")
        .toIntOrNull()
        ?.let(MapEditTarget::MeasurementPoint)
    MapOverlayId.MANUAL_ROUTE_POINTS -> objectId.removePrefix("route-point:").let { body ->
        val separator = body.lastIndexOf(':')
        if (separator <= 0 || separator == body.lastIndex) {
            null
        } else {
            body.substring(separator + 1).toIntOrNull()?.let { MapEditTarget.RoutePoint(body.substring(0, separator), it) }
        }
    }
    else -> null
}

private fun MapEditTarget.toHitResult(): MapHitResult = when (this) {
    is MapEditTarget.MeasurementPoint -> MapHitResult(MapOverlayId.MEASUREMENT_POINTS, "measurement-point:$index")
    is MapEditTarget.RoutePoint -> MapHitResult(MapOverlayId.MANUAL_ROUTE_POINTS, "route-point:$draftId:$index")
}

private data class ActivePointDrag(
    val id: MapGestureId,
    val target: MapEditTarget,
    val downPoint: MapScreenPoint,
    val moved: Boolean = false,
)

internal object PointDragMotion {
    fun hasMoved(down: MapScreenPoint, current: MapScreenPoint, touchSlop: Double): Boolean {
        require(touchSlop >= 0.0)
        return hypot(current.xPx - down.xPx, current.yPx - down.yPx) >= touchSlop
    }
}

/**
 * Bounds projected geodesic chord error to roughly three quarters of a pixel at the current
 * zoom. The floor prevents pathological world-scale lines from allocating unbounded vertices;
 * source coordinates and measurement math remain full WGS84 precision.
 */
internal object GeodesicRenderBudget {
    const val MIN_SEGMENT_METERS = 500.0
    const val MAX_SEGMENT_METERS = 25_000.0
    private const val TARGET_PIXEL_ERROR = 0.75
    private const val WGS84_MEAN_RADIUS_METERS = 6_371_008.8
    private const val EQUATOR_METERS_PER_PIXEL_ZOOM_0 = 156_543.03392

    fun maxSegmentMeters(zoom: Double, maxAbsoluteLatitude: Double): Double {
        require(zoom.isFinite() && zoom in 0.0..24.0)
        require(maxAbsoluteLatitude.isFinite() && maxAbsoluteLatitude in 0.0..90.0)
        val displayLatitude = maxAbsoluteLatitude.coerceAtMost(WEB_MERCATOR_MAX_LATITUDE)
        val metersPerPixel = EQUATOR_METERS_PER_PIXEL_ZOOM_0 *
            cos(Math.toRadians(displayLatitude)) / 2.0.pow(zoom)
        val segmentForSagitta = sqrt(8.0 * WGS84_MEAN_RADIUS_METERS * metersPerPixel * TARGET_PIXEL_ERROR)
        return segmentForSagitta.coerceIn(MIN_SEGMENT_METERS, MAX_SEGMENT_METERS)
    }
}

private class OfflineMapLifecycleDriver(private val mapView: MapView) {
    private var created = false
    private var started = false
    private var resumed = false
    private var destroyed = false
    fun create() { if (!created) { mapView.onCreate(Bundle()); created = true } }
    fun onEvent(event: Lifecycle.Event) = when (event) {
        Lifecycle.Event.ON_START -> start()
        Lifecycle.Event.ON_RESUME -> resume()
        Lifecycle.Event.ON_PAUSE -> pause()
        Lifecycle.Event.ON_STOP -> stop()
        Lifecycle.Event.ON_DESTROY -> destroy()
        else -> Unit
    }
    fun syncTo(state: Lifecycle.State) {
        when {
            state == Lifecycle.State.DESTROYED -> destroy()
            state.isAtLeast(Lifecycle.State.RESUMED) -> resume()
            state.isAtLeast(Lifecycle.State.STARTED) -> start()
        }
    }
    private fun start() { if (!started && !destroyed) { mapView.onStart(); started = true } }
    private fun resume() { start(); if (!resumed && !destroyed) { mapView.onResume(); resumed = true } }
    private fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
    private fun stop() { pause(); if (started) { mapView.onStop(); started = false } }
    fun destroy() { if (!destroyed) { stop(); mapView.onDestroy(); destroyed = true } }
}

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private class OfflineMapMemoryCallbacks(private val mapView: MapView) : ComponentCallbacks2 {
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    override fun onLowMemory() = mapView.onLowMemory()
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) mapView.onLowMemory()
    }
}

private fun wrapLongitude(value: Double): Double = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

private fun MapLibreMap.currentChartViewport(): ChartDisplayViewport? = runCatching {
    val bounds = projection.visibleRegion.latLngBounds
    ChartDisplayViewport(
        bounds = GeoBounds(
            south = bounds.latitudeSouth.coerceIn(-90.0, 90.0),
            west = bounds.longitudeWest.coerceIn(-180.0, 180.0),
            north = bounds.latitudeNorth.coerceIn(-90.0, 90.0),
            east = bounds.longitudeEast.coerceIn(-180.0, 180.0),
        ),
        zoom = cameraPosition.zoom.toInt().coerceIn(0, 24),
    )
}.getOrNull()

private const val CHART_SOURCE = "installed-raster-chart"
private const val CHART_LAYER = "installed-raster-chart-layer"
private const val WEB_MERCATOR_MAX_LATITUDE = 85.05112878
private const val HEADING_VECTOR_METERS = 120.0
private const val COURSE_VECTOR_METERS_PER_KNOT = 25.0
private const val MIN_COURSE_VECTOR_METERS = 80.0
private const val MAX_COURSE_VECTOR_METERS = 500.0
private const val ACCURACY_RING_SEGMENTS = 36
private val nextRendererGeneration = AtomicLong(0L)
private val nextPointGesture = AtomicLong(0L)

/** Bounded process-local counters used by lifecycle gates and later diagnostics. */
object OfflineMapInstanceMetrics {
    private val live = AtomicInteger(0)
    private val created = AtomicInteger(0)
    private val peak = AtomicInteger(0)

    val liveCount: Int get() = live.get()
    val createdCount: Int get() = created.get()
    val peakLiveCount: Int get() = peak.get()

    internal fun onCreated() {
        created.incrementAndGet()
        val now = live.incrementAndGet()
        peak.updateAndGet { previous -> maxOf(previous, now) }
    }

    internal fun onDestroyed() {
        check(live.decrementAndGet() >= 0) { "MapView lifecycle counter became negative" }
    }

    fun resetForTest() {
        check(live.get() == 0) { "Cannot reset while a MapView is live" }
        created.set(0)
        peak.set(0)
    }
}

private const val EMPTY_STYLE = """{
  "version": 8,
  "name": "Yokuli offline chart",
  "sources": {},
  "layers": [{"id":"background","type":"background","paint":{"background-color":"#082331"}}]
}"""
