package com.yokuli.marine.adapter.chart.google

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.android.gms.maps.model.TileProvider
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapBaseRenderStatus
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapCameraCommand
import com.yokuli.marine.map.domain.MapCameraCommandId
import com.yokuli.marine.map.domain.MapCameraTarget
import com.yokuli.marine.map.domain.MapEditTarget
import com.yokuli.marine.map.domain.MapGestureId
import com.yokuli.marine.map.domain.MapHitResult
import com.yokuli.marine.map.domain.MapOverlayId
import com.yokuli.marine.map.domain.MapOverlayRenderStatus
import com.yokuli.marine.map.domain.MapRendererGeneration
import com.yokuli.marine.map.domain.MapRendererGenerations
import com.yokuli.marine.map.domain.MapRendererQueryPort
import com.yokuli.marine.map.domain.MapRendererReadiness
import com.yokuli.marine.map.domain.MapScreenPoint
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapTileCoverageStatus
import com.yokuli.marine.map.domain.MapTileSnapshotFormat
import com.yokuli.marine.map.domain.MapTileSnapshotSink
import com.yokuli.marine.map.domain.MapViewMode
import com.yokuli.marine.map.domain.PositionRenderPolicy
import com.yokuli.marine.map.domain.VesselMarkerStyle
import com.yokuli.marine.map.domain.chartlibrary.ChartResourceAccessPort
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

private fun MapViewMode.googleMapType(): Int = when (this) {
    MapViewMode.STANDARD -> GoogleMap.MAP_TYPE_NORMAL
    MapViewMode.SATELLITE, MapViewMode.MARINE -> GoogleMap.MAP_TYPE_SATELLITE
}

/**
 * Google Maps adapter for the shared chart surface.
 *
 * 中文：本模块只负责 SDK 生命周期、camera 与手势，不拥有 Anchor/NMEA/Navigation 任务。
 * English: This adapter owns SDK lifecycle, camera, and gestures, never marine runtimes.
 */
@Composable
fun GoogleMarineChartSurface(
    state: MapState,
    onAction: (MapAction) -> Unit,
    onQueryPortChanged: (MapRendererQueryPort?) -> Unit = {},
    darkMode: Boolean,
    chartLibraryAccess: ChartResourceAccessPort? = null,
    tileSnapshotSink: MapTileSnapshotSink? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentAction by rememberUpdatedState(onAction)
    val currentState by rememberUpdatedState(state)
    val currentQueryPortChanged by rememberUpdatedState(onQueryPortChanged)
    val displayDensity = context.resources.displayMetrics.density
    val generation = remember { MapRendererGenerations.next() }
    val activeCameraCommand = remember { AtomicReference<MapCameraCommandId?>(null) }
    val submittedCameraCommand = remember { AtomicReference<MapCameraCommandId?>(null) }
    val activePointDrag = remember { AtomicReference<ActivePointDrag?>(null) }
    val touchSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop.toDouble() }
    val domainMarkers = remember { mutableListOf<Marker>() }
    val domainPolylines = remember { mutableListOf<Polyline>() }
    val chartTileOverlays = remember { mutableListOf<TileOverlay>() }
    val mapView = remember(context) {
        MapView(
            context,
            GoogleMapOptions()
                .mapColorScheme(if (darkMode) MapColorScheme.DARK else MapColorScheme.LIGHT)
                .mapType(state.mapViewMode.googleMapType())
                .compassEnabled(false)
                .mapToolbarEnabled(false)
                .rotateGesturesEnabled(false)
                .scrollGesturesEnabled(true)
                .tiltGesturesEnabled(false)
                .zoomControlsEnabled(false)
                .zoomGesturesEnabled(true),
        )
    }
    val markerIcons = remember(mapView) { mutableMapOf<String, BitmapDescriptor>() }
    val lifecycleDriver = remember(mapView) { MapViewLifecycleDriver(mapView) }
    var googleMap by remember(mapView) { mutableStateOf<GoogleMap?>(null) }
    var preparedDisplay by remember(mapView) { mutableStateOf<PreparedGoogleChartDisplay?>(null) }
    val snapshotCadence = remember(mapView) { RendererSnapshotCadence() }

    LaunchedEffect(googleMap, state.mapViewMode) {
        googleMap?.mapType = state.mapViewMode.googleMapType()
    }

    LaunchedEffect(
        googleMap,
        state.camera,
        state.chartDisplayPlan.fingerprint,
        state.renderer.readiness,
        state.renderer.baseStatus,
        state.renderer.overlayStatus,
        tileSnapshotSink,
        darkMode,
    ) {
        val readyMap = googleMap ?: return@LaunchedEffect
        val sink = tileSnapshotSink ?: return@LaunchedEffect
        if (state.renderer.readiness != MapRendererReadiness.RENDERER_READY) return@LaunchedEffect
        delay(snapshotCadence.delayUntilNext(SystemClock.elapsedRealtime()))
        snapshotCadence.markRequested(SystemClock.elapsedRealtime())
        val request = sink.begin(
            state.camera,
            "google:${state.chartDisplayPlan.fingerprint}:${if (darkMode) "dark" else "light"}",
        )
        readyMap.snapshot { bitmap -> bitmap?.publishTileSnapshot(sink, request) }
    }

    LaunchedEffect(generation) {
        currentAction(MapAction.RendererHostReady(generation))
        currentAction(
            MapAction.RendererContentChanged(
                generation,
                base = MapBaseRenderStatus.BASE_LOADING,
                overlay = if (state.chartDisplayPlan.layers.isEmpty()) {
                    MapOverlayRenderStatus.OVERLAY_NONE
                } else {
                    MapOverlayRenderStatus.OVERLAY_LOADING
                },
            ),
        )
    }

    LaunchedEffect(state.chartDisplayPlan.fingerprint, chartLibraryAccess) {
        preparedDisplay?.close()
        preparedDisplay = null
        val plan = state.chartDisplayPlan
        if (plan.layers.isEmpty()) {
            currentAction(
                MapAction.RendererContentChanged(generation, overlay = MapOverlayRenderStatus.OVERLAY_NONE),
            )
            return@LaunchedEffect
        }
        val access = chartLibraryAccess
        if (access == null) {
            currentAction(
                MapAction.RendererContentChanged(generation, overlay = MapOverlayRenderStatus.OVERLAY_DEGRADED),
            )
            return@LaunchedEffect
        }
        currentAction(
            MapAction.RendererContentChanged(generation, overlay = MapOverlayRenderStatus.OVERLAY_LOADING),
        )
        val next = try {
            withContext(Dispatchers.IO) { GoogleChartOverlayPreparer(access).prepare(plan) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            currentAction(
                MapAction.RendererContentChanged(generation, overlay = MapOverlayRenderStatus.OVERLAY_DEGRADED),
            )
            return@LaunchedEffect
        }
        try {
            currentCoroutineContext().ensureActive()
            preparedDisplay = next
        } catch (cancelled: CancellationException) {
            next.close()
            throw cancelled
        }
    }

    DisposableEffect(mapView, lifecycle, context.applicationContext) {
        lifecycleDriver.create()
        val observer = LifecycleEventObserver { _, event -> lifecycleDriver.onEvent(event) }
        val memoryCallbacks = MapMemoryCallbacks(mapView)
        lifecycle.addObserver(observer)
        lifecycleDriver.syncTo(lifecycle.currentState)
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)
        onDispose {
            chartTileOverlays.clearGoogleTileOverlays()
            domainMarkers.removeMarkersFromMap()
            domainPolylines.removePolylinesFromMap()
            preparedDisplay?.close()
            preparedDisplay = null
            activePointDrag.getAndSet(null)?.let { currentAction(MapAction.CancelPointDrag(it.id)) }
            currentQueryPortChanged(null)
            currentAction(MapAction.RendererDetached(generation))
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
            googleMap?.setOnCameraIdleListener(null)
            lifecycleDriver.destroy()
        }
    }

    DisposableEffect(mapView, generation) {
        var disposed = false
        var queryPort: GoogleRendererQueryPort? = null
        mapView.getMapAsync { readyMap ->
            if (disposed) return@getMapAsync
            googleMap = readyMap.apply {
                mapType = currentState.mapViewMode.googleMapType()
                isBuildingsEnabled = false
                isIndoorEnabled = false
                isTrafficEnabled = false
                uiSettings.apply {
                    isCompassEnabled = false
                    isIndoorLevelPickerEnabled = false
                    isMapToolbarEnabled = false
                    isMyLocationButtonEnabled = false
                    isRotateGesturesEnabled = false
                    isScrollGesturesEnabled = true
                    isTiltGesturesEnabled = false
                    isZoomControlsEnabled = false
                    isZoomGesturesEnabled = true
                }
                moveCamera(CameraUpdateFactory.newCameraPosition(currentState.camera.toCameraPosition()))
                queryPort = GoogleRendererQueryPort(
                    this,
                    { currentState },
                    { !disposed },
                    hitRadiusPx = 42.0 * displayDensity,
                    lineHitRadiusPx = 18.0 * displayDensity,
                )
                currentQueryPortChanged(queryPort)
                setOnCameraIdleListener {
                    if (activeCameraCommand.get() == null) {
                        currentAction(MapAction.RendererCameraIdle(generation, cameraPosition.toDomainCamera()))
                    }
                }
                setOnMapLoadedCallback {
                    currentAction(
                        MapAction.RendererContentChanged(generation, base = MapBaseRenderStatus.BASE_READY),
                    )
                }
                setOnMapClickListener { point ->
                    val screen = projection.toScreenLocation(point).toDomainScreenPoint()
                    currentAction(MapAction.MapTapped(point.toDomainPoint(), requireNotNull(queryPort).query(screen, INTERACTIVE_OVERLAYS)))
                }
                setOnMapLongClickListener { point ->
                    val screen = projection.toScreenLocation(point).toDomainScreenPoint()
                    currentAction(MapAction.MapLongPressed(point.toDomainPoint(), requireNotNull(queryPort).query(screen, INTERACTIVE_OVERLAYS)))
                }
            }
            currentAction(MapAction.RendererHostReady(generation))
            currentAction(MapAction.RendererReady(generation))
        }
        fun setMapGesturesForPointDrag(enabled: Boolean) {
            googleMap?.uiSettings?.apply {
                isScrollGesturesEnabled = enabled
                isZoomGesturesEnabled = enabled
            }
            if (enabled) mapView.parent?.requestDisallowInterceptTouchEvent(false)
        }
        mapView.setOnTouchListener { view, event ->
            val port = queryPort ?: return@setOnTouchListener false
            val screen = MapScreenPoint(event.x.toDouble(), event.y.toDouble())
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> port.query(screen, HANDLE_OVERLAYS)
                    .asSequence()
                    .mapNotNull(MapHitResult::toEditTargetOrNull)
                    .firstOrNull()
                    ?.let { target ->
                        val gestureId = MapGestureId("google-${generation.value}-${nextPointGesture.incrementAndGet()}")
                        activePointDrag.set(ActivePointDrag(gestureId, target, screen))
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        setMapGesturesForPointDrag(false)
                        currentAction(MapAction.BeginPointDrag(gestureId, target))
                        true
                    } ?: false
                MotionEvent.ACTION_MOVE -> activePointDrag.get()?.let { drag ->
                    val moved = drag.moved || drag.down.distanceTo(screen) >= touchSlop
                    if (moved) {
                        activePointDrag.set(drag.copy(moved = true))
                        port.unproject(screen)?.let { currentAction(MapAction.PreviewPointDrag(drag.id, it)) }
                    }
                    true
                } ?: false
                MotionEvent.ACTION_UP -> activePointDrag.getAndSet(null)?.let { drag ->
                    setMapGesturesForPointDrag(true)
                    val point = port.unproject(screen)
                    val moved = drag.moved || drag.down.distanceTo(screen) >= touchSlop
                    when {
                        point == null -> currentAction(MapAction.CancelPointDrag(drag.id))
                        moved -> currentAction(MapAction.CommitPointDrag(drag.id, point))
                        else -> {
                            currentAction(MapAction.CancelPointDrag(drag.id))
                            currentAction(MapAction.MapTapped(point, listOf(drag.target.toHitResult())))
                        }
                    }
                    true
                } ?: false
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> activePointDrag.getAndSet(null)?.let {
                    setMapGesturesForPointDrag(true)
                    currentAction(MapAction.CancelPointDrag(it.id))
                    true
                } ?: false
                else -> activePointDrag.get() != null
            }
        }
        onDispose {
            disposed = true
            setMapGesturesForPointDrag(true)
            mapView.setOnTouchListener(null)
            googleMap?.setOnMapClickListener(null)
            googleMap?.setOnMapLongClickListener(null)
        }
    }

    LaunchedEffect(googleMap, darkMode) {
        googleMap?.setMapColorScheme(if (darkMode) MapColorScheme.DARK else MapColorScheme.LIGHT)
    }

    LaunchedEffect(
        googleMap,
        state.chartDisplayPlan.fingerprint,
        preparedDisplay?.planFingerprint,
    ) {
        val map = googleMap ?: return@LaunchedEffect
        chartTileOverlays.clearGoogleTileOverlays()
        val plan = state.chartDisplayPlan
        val prepared = preparedDisplay
        if (plan.layers.isEmpty()) {
            currentAction(MapAction.RendererCoverageChanged(generation, MapTileCoverageStatus.NO_PACKAGE))
            currentAction(
                MapAction.RendererContentChanged(generation, overlay = MapOverlayRenderStatus.OVERLAY_NONE),
            )
            return@LaunchedEffect
        }
        if (prepared?.planFingerprint != plan.fingerprint) {
            currentAction(MapAction.RendererCoverageChanged(generation, MapTileCoverageStatus.CHECKING))
            return@LaunchedEffect
        }
        prepared.layers.forEachIndexed { index, layer ->
            val overlay = map.addTileOverlay(
                TileOverlayOptions()
                    .tileProvider(
                        TileProvider { column, row, zoom ->
                            layer.reader.read(column, row, zoom)?.let { payload ->
                                Tile(payload.widthPx, payload.heightPx, payload.bytes)
                            } ?: TileProvider.NO_TILE
                        },
                    )
                    .visible(true)
                    .transparency((1f - layer.planLayer.opacity).coerceIn(0f, 1f))
                    .zIndex(index.toFloat()),
            )
            if (overlay != null) chartTileOverlays += overlay
        }
        val degraded = prepared.rejectedLayerCount > 0 || chartTileOverlays.size != prepared.layers.size
        currentAction(
            MapAction.RendererContentChanged(
                generation,
                overlay = if (degraded) {
                    MapOverlayRenderStatus.OVERLAY_DEGRADED
                } else {
                    MapOverlayRenderStatus.OVERLAY_READY
                },
            ),
        )
        currentAction(
            MapAction.RendererCoverageChanged(
                generation,
                when {
                    chartTileOverlays.isEmpty() -> MapTileCoverageStatus.PACKAGE_MISSING
                    degraded -> MapTileCoverageStatus.DEGRADED
                    else -> MapTileCoverageStatus.PACKAGE_ATTACHED
                },
            ),
        )
    }

    LaunchedEffect(googleMap, generation, state.renderer.generation, state.renderer.readiness, state.renderer.pendingCameraCommand) {
        val map = googleMap ?: return@LaunchedEffect
        val command = state.renderer.pendingCameraCommand ?: return@LaunchedEffect
        if (state.renderer.generation != generation ||
            state.renderer.readiness != MapRendererReadiness.RENDERER_READY ||
            submittedCameraCommand.get() == command.id
        ) return@LaunchedEffect
        submittedCameraCommand.set(command.id)
        activeCameraCommand.set(command.id)
        map.setPadding(
            command.viewportInsets.leftPx,
            command.viewportInsets.topPx,
            command.viewportInsets.rightPx,
            command.viewportInsets.bottomPx,
        )
        map.animateCamera(command.toCameraUpdate(), object : GoogleMap.CancelableCallback {
            override fun onFinish() {
                if (activeCameraCommand.getAndSet(null) != command.id) return
                currentAction(MapAction.RendererCameraIdle(generation, map.cameraPosition.toDomainCamera(), command.id))
            }

            override fun onCancel() {
                activeCameraCommand.compareAndSet(command.id, null)
                submittedCameraCommand.compareAndSet(command.id, null)
            }
        })
    }

    LaunchedEffect(
        googleMap,
        state.selection,
        state.transient,
        state.places,
        state.measurementDraft,
        state.routeDraft,
        state.activeRoutePlanId,
        state.activeNavigationRoute,
        state.activeNavigationLeg,
        state.activeTrackSegments,
        state.savedRoutes,
        state.editGesture,
        state.position.observation,
    ) {
        googleMap?.apply {
            fun icon(key: String, create: () -> Bitmap): BitmapDescriptor =
                markerIcons.getOrPut(key) { BitmapDescriptorFactory.fromBitmap(create()) }
            domainMarkers.removeMarkersFromMap()
            domainPolylines.removePolylinesFromMap()
            state.places.forEach { place ->
                addMarker(
                    MarkerOptions()
                        .position(place.point.toLatLng())
                        .title(place.name)
                        .anchor(.5f, 1f)
                        .icon(icon("waypoint") { waypointBitmap(displayDensity) }),
                )?.let(domainMarkers::add)
            }
            ((state.transient as? com.yokuli.marine.map.domain.MapTransient.PointCandidate)?.point
                ?: state.selection?.point)?.let { selectedPoint ->
                addMarker(
                    MarkerOptions()
                        .position(selectedPoint.toLatLng())
                        .anchor(.5f, .5f)
                        .icon(icon("target") { targetBitmap(displayDensity) }),
                )?.let(domainMarkers::add)
            }
            state.visibleMeasurementPoints.takeIf { it.isNotEmpty() }?.let { points ->
                domainPolylines += addPolyline(
                    PolylineOptions().addAll(points.map(GeoPoint::toLatLng)).color(0xfff7b500.toInt()).width(5f),
                )
                points.take(2).forEachIndexed { index, point ->
                    addMarker(
                        MarkerOptions()
                            .position(point.toLatLng())
                            .anchor(.5f, 1f)
                            .icon(icon("measure-$index") {
                                measurementHandleBitmap(if (index == 0) "A" else "B", displayDensity)
                            }),
                    )?.let(domainMarkers::add)
                }
            }
            state.routePointsWithPreview().takeIf { it.isNotEmpty() }?.let { points ->
                domainPolylines += addPolyline(
                    PolylineOptions().addAll(points.map(GeoPoint::toLatLng)).color(0xff00a4ef.toInt()).width(7f),
                )
                points.forEachIndexed { index, point ->
                    addMarker(
                        MarkerOptions()
                            .position(point.toLatLng())
                            .anchor(.5f, 1f)
                            .icon(icon("route-${index + 1}") {
                                measurementHandleBitmap((index + 1).toString(), displayDensity)
                            }),
                    )?.let(domainMarkers::add)
                }
            }
            state.activeNavigationLeg.takeIf { it.size == 2 }?.let { leg ->
                domainPolylines += addPolyline(
                    PolylineOptions().addAll(leg.map(GeoPoint::toLatLng)).color(0xfff7b500.toInt()).width(10f),
                )
            }
            state.activeTrackSegments.forEach { segment ->
                segment.takeIf { it.size >= 2 }?.let { points ->
                    domainPolylines += addPolyline(
                        PolylineOptions().addAll(points.map(GeoPoint::toLatLng)).color(0xff00d084.toInt()).width(6f),
                    )
                }
            }
            val positionRender = PositionRenderPolicy.resolve(state.position)
            positionRender.point?.let { point ->
                addMarker(
                    MarkerOptions()
                        .position(point.toLatLng())
                        .title(state.position.observation?.identity?.source?.sourceId)
                        .anchor(.5f, .5f)
                        .flat(true)
                        .rotation(
                            (positionRender.trueHeadingDegrees
                                ?: positionRender.courseVector?.trueDegrees
                                ?: 0.0).toFloat(),
                        )
                        .icon(icon("vessel-${positionRender.markerStyle}") {
                            vesselBitmap(
                                displayDensity,
                                live = positionRender.markerStyle != VesselMarkerStyle.HISTORICAL,
                            )
                        }),
                )?.let(domainMarkers::add)
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
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
    val scale = min(1.0, min(TILE_SNAPSHOT_WIDTH.toDouble() / width, TILE_SNAPSHOT_HEIGHT.toDouble() / height))
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

private fun MutableList<TileOverlay>.clearGoogleTileOverlays() {
    forEach { overlay ->
        runCatching { overlay.clearTileCache() }
        runCatching { overlay.remove() }
    }
    clear()
}

private fun MutableList<Marker>.removeMarkersFromMap() {
    forEach { marker -> runCatching { marker.remove() } }
    clear()
}

private fun MutableList<Polyline>.removePolylinesFromMap() {
    forEach { polyline -> runCatching { polyline.remove() } }
    clear()
}

private fun MapCameraCommand.toCameraUpdate() = when (val value = target) {
    is MapCameraTarget.Exact -> CameraUpdateFactory.newCameraPosition(value.camera.toCameraPosition())
    is MapCameraTarget.Bounds -> CameraUpdateFactory.newLatLngBounds(value.bounds.toGoogleBounds(), 0)
}

private fun MapCamera.toCameraPosition(): CameraPosition = CameraPosition.Builder()
    .target(center.toLatLng())
    .zoom(zoom.coerceIn(0.0, GOOGLE_MAX_ZOOM).toFloat())
    .bearing(bearing.toFloat())
    .tilt(0f)
    .build()

private fun CameraPosition.toDomainCamera(): MapCamera = MapCamera(
    center = target.toDomainPoint(),
    zoom = zoom.toDouble(),
    bearing = bearing.toDouble(),
)

private fun LatLng.toDomainPoint(): GeoPoint = GeoPoint(latitude, longitude)

private fun measurementHandleBitmap(label: String, density: Float): Bitmap {
    val size = (56f * density).roundToInt().coerceAtLeast(56)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xfff7b500.toInt() }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        textSize = size * .36f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val centerX = size / 2f
    val centerY = size * .34f
    val radius = size * .27f
    val pin = Path().apply {
        moveTo(centerX, size * .96f)
        lineTo(centerX - radius * .7f, centerY + radius * .58f)
        arcTo(centerX - radius, centerY - radius, centerX + radius, centerY + radius, 135f, 270f, false)
        close()
    }
    canvas.drawPath(pin, fill)
    canvas.drawPath(pin, stroke)
    canvas.drawText(label, centerX, centerY - (text.descent() + text.ascent()) / 2f, text)
    return bitmap
}

private fun waypointBitmap(density: Float): Bitmap {
    val size = (42f * density).roundToInt().coerceAtLeast(42)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff00a4ef.toInt() }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    val radius = size * .28f
    val centerX = size / 2f
    val centerY = size * .35f
    val pin = Path().apply {
        moveTo(centerX, size * .96f)
        lineTo(centerX - radius * .72f, centerY + radius * .58f)
        arcTo(centerX - radius, centerY - radius, centerX + radius, centerY + radius, 135f, 270f, false)
        close()
    }
    canvas.drawPath(pin, fill)
    canvas.drawPath(pin, stroke)
    canvas.drawCircle(centerX, centerY, radius * .32f, stroke)
    return bitmap
}

private fun targetBitmap(density: Float): Bitmap {
    val size = (44f * density).roundToInt().coerceAtLeast(44)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    val center = size / 2f
    val radius = size * .25f
    canvas.drawCircle(center, center, radius, paint)
    canvas.drawLine(center, 0f, center, center - radius * .55f, paint)
    canvas.drawLine(center, center + radius * .55f, center, size.toFloat(), paint)
    canvas.drawLine(0f, center, center - radius * .55f, center, paint)
    canvas.drawLine(center + radius * .55f, center, size.toFloat(), center, paint)
    return bitmap
}

private fun vesselBitmap(density: Float, live: Boolean): Bitmap {
    val size = (48f * density).roundToInt().coerceAtLeast(48)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (live) 0xff00d084.toInt() else 0xff6f7f89.toInt()
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeJoin = Paint.Join.ROUND
    }
    val arrow = Path().apply {
        moveTo(size * .5f, size * .04f)
        lineTo(size * .82f, size * .88f)
        lineTo(size * .5f, size * .7f)
        lineTo(size * .18f, size * .88f)
        close()
    }
    canvas.drawPath(arrow, fill)
    canvas.drawPath(arrow, stroke)
    return bitmap
}

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)
private fun Point.toDomainScreenPoint() = MapScreenPoint(x.toDouble(), y.toDouble())
private fun MapScreenPoint.toAndroidPoint() = Point(xPx.toInt(), yPx.toInt())
private fun GeoBounds.toGoogleBounds() = LatLngBounds(LatLng(south, west), LatLng(north, east))

private data class ActivePointDrag(
    val id: MapGestureId,
    val target: MapEditTarget,
    val down: MapScreenPoint,
    val moved: Boolean = false,
)

private fun MapScreenPoint.distanceTo(other: MapScreenPoint) = hypot(xPx - other.xPx, yPx - other.yPx)

private fun MapScreenPoint.distanceToSegment(from: MapScreenPoint, to: MapScreenPoint): Double {
    val dx = to.xPx - from.xPx
    val dy = to.yPx - from.yPx
    if (dx == 0.0 && dy == 0.0) return distanceTo(from)
    val fraction = (((xPx - from.xPx) * dx + (yPx - from.yPx) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
    return hypot(xPx - (from.xPx + fraction * dx), yPx - (from.yPx + fraction * dy))
}

private class GoogleRendererQueryPort(
    private val map: GoogleMap,
    private val currentState: () -> MapState,
    private val isCurrent: () -> Boolean,
    private val hitRadiusPx: Double,
    private val lineHitRadiusPx: Double,
) : MapRendererQueryPort {
    override fun project(point: GeoPoint): MapScreenPoint? = ifCurrent {
        map.projection.toScreenLocation(point.toLatLng()).toDomainScreenPoint()
    }

    override fun unproject(point: MapScreenPoint): GeoPoint? = ifCurrent {
        map.projection.fromScreenLocation(point.toAndroidPoint()).toDomainPoint()
    }

    override fun query(point: MapScreenPoint, overlayIds: Set<MapOverlayId>): List<MapHitResult> = ifCurrent {
        val state = currentState()
        val pointHits = state.hitCandidates(overlayIds)
            .mapNotNull { candidate ->
                val screen = map.projection.toScreenLocation(candidate.point.toLatLng()).toDomainScreenPoint()
                candidate.hit.takeIf { screen.distanceTo(point) <= hitRadiusPx }
            }
            .distinct()
        val routeHit = if (
            MapOverlayId.MANUAL_ROUTE in overlayIds &&
            state.tool == com.yokuli.marine.map.domain.MapTool.MANUAL_ROUTE &&
            state.routePointsWithPreview().zipWithNext().any { (from, to) ->
                point.distanceToSegment(
                    map.projection.toScreenLocation(from.toLatLng()).toDomainScreenPoint(),
                    map.projection.toScreenLocation(to.toLatLng()).toDomainScreenPoint(),
                ) <= lineHitRadiusPx
            }
        ) {
            listOf(MapHitResult(MapOverlayId.MANUAL_ROUTE, "route:${state.routeDraft?.id.orEmpty()}"))
        } else {
            emptyList()
        }
        (pointHits + routeHit).distinct()
    }.orEmpty()

    private inline fun <T> ifCurrent(block: () -> T): T? = if (isCurrent()) runCatching(block).getOrNull() else null
}

private data class HitCandidate(val point: GeoPoint, val hit: MapHitResult)

private fun MapState.hitCandidates(ids: Set<MapOverlayId>): List<HitCandidate> = buildList {
    if (MapOverlayId.SAVED_PLACES in ids) places.forEach { add(HitCandidate(it.point, MapHitResult(MapOverlayId.SAVED_PLACES, "place:${it.id}"))) }
    if (MapOverlayId.SELECTION in ids) selection?.let { add(HitCandidate(it.point, MapHitResult(MapOverlayId.SELECTION, "selection"))) }
    if (MapOverlayId.MEASUREMENT_POINTS in ids) visibleMeasurementPoints.forEachIndexed { index, point ->
        add(HitCandidate(point, MapHitResult(MapOverlayId.MEASUREMENT_POINTS, "measurement-point:$index")))
    }
    if (MapOverlayId.MANUAL_ROUTE_POINTS in ids) routePointsWithPreview().forEachIndexed { index, point ->
        add(HitCandidate(point, MapHitResult(MapOverlayId.MANUAL_ROUTE_POINTS, routePointObjectId(index))))
    }
}

private fun MapState.routePointsWithPreview(): List<GeoPoint> {
    val points = visibleRoutePoints
    val target = editGesture?.target as? MapEditTarget.RoutePoint ?: return points
    return points.replaceAt(target.index, requireNotNull(editGesture).previewPoint)
}

private fun MapState.routePointObjectId(index: Int): String = routeDraft
    ?.let { "route-point:${it.id}:$index" }
    ?: "route-preview-point:${activeRoutePlanId ?: "active-navigation"}:$index"

private fun List<GeoPoint>.replaceAt(index: Int, value: GeoPoint): List<GeoPoint> =
    if (index !in indices) this else mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }

private fun MapHitResult.toEditTargetOrNull(): MapEditTarget? = when (overlayId) {
    MapOverlayId.MEASUREMENT_POINTS -> objectId.removePrefix("measurement-point:").toIntOrNull()
        ?.let(MapEditTarget::MeasurementPoint)
    MapOverlayId.MANUAL_ROUTE_POINTS -> objectId.removePrefix("route-point:").let { body ->
        val separator = body.lastIndexOf(':')
        if (separator <= 0) null else body.substring(separator + 1).toIntOrNull()
            ?.let { MapEditTarget.RoutePoint(body.substring(0, separator), it) }
    }
    else -> null
}

private fun MapEditTarget.toHitResult(): MapHitResult = when (this) {
    is MapEditTarget.MeasurementPoint -> MapHitResult(MapOverlayId.MEASUREMENT_POINTS, "measurement-point:$index")
    is MapEditTarget.RoutePoint -> MapHitResult(MapOverlayId.MANUAL_ROUTE_POINTS, "route-point:$draftId:$index")
}

private val INTERACTIVE_OVERLAYS = setOf(
    MapOverlayId.SAVED_PLACES,
    MapOverlayId.SELECTION,
    MapOverlayId.MEASUREMENT_POINTS,
    MapOverlayId.MANUAL_ROUTE_POINTS,
)
private val HANDLE_OVERLAYS = setOf(MapOverlayId.MEASUREMENT_POINTS, MapOverlayId.MANUAL_ROUTE_POINTS)
private val nextPointGesture = AtomicLong(0L)
private const val GOOGLE_MAX_ZOOM = 21.0

private class MapViewLifecycleDriver(private val mapView: MapView) {
    private var created = false
    private var started = false
    private var resumed = false
    private var destroyed = false

    fun create() {
        if (!created) {
            mapView.onCreate(Bundle())
            created = true
        }
    }

    fun onEvent(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> start()
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            Lifecycle.Event.ON_DESTROY -> destroy()
            else -> Unit
        }
    }

    fun syncTo(state: Lifecycle.State) {
        when {
            state == Lifecycle.State.DESTROYED -> destroy()
            state.isAtLeast(Lifecycle.State.RESUMED) -> resume()
            state.isAtLeast(Lifecycle.State.STARTED) -> start()
        }
    }

    private fun start() {
        if (!started && !destroyed) {
            mapView.onStart()
            started = true
        }
    }

    private fun resume() {
        start()
        if (!resumed && !destroyed) {
            mapView.onResume()
            resumed = true
        }
    }

    private fun pause() {
        if (resumed) {
            mapView.onPause()
            resumed = false
        }
    }

    private fun stop() {
        pause()
        if (started) {
            mapView.onStop()
            started = false
        }
    }

    fun destroy() {
        if (!destroyed) {
            stop()
            mapView.onDestroy()
            destroyed = true
        }
    }
}

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private class MapMemoryCallbacks(private val mapView: MapView) : ComponentCallbacks2 {
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    override fun onLowMemory() = mapView.onLowMemory()
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) mapView.onLowMemory()
    }
}
