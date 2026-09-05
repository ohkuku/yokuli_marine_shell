package com.yokuli.marine.map.offline

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
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
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapCameraCommand
import com.yokuli.marine.map.domain.MapCameraCommandId
import com.yokuli.marine.map.domain.MapCameraTarget
import com.yokuli.marine.map.domain.MapHitResult
import com.yokuli.marine.map.domain.MapOverlayId
import com.yokuli.marine.map.domain.MapRendererFailure
import com.yokuli.marine.map.domain.MapRendererGeneration
import com.yokuli.marine.map.domain.MapRendererQueryPort
import com.yokuli.marine.map.domain.MapRendererReadiness
import com.yokuli.marine.map.domain.MapScreenPoint
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapTileCoverageStatus
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.RasterLayer
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
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentAction by rememberUpdatedState(onAction)
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
    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    var activeStyle by remember(mapView) { mutableStateOf<Style?>(null) }

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
            val queryPort = MapLibreRendererQueryPort(readyMap) { !disposed.get() }
            currentQueryPortChanged(queryPort)
            cameraListener = MapLibreMap.OnCameraIdleListener {
                if (activeCameraCommand.get() == null) {
                    readyMap.cameraPosition.toDomainCameraOrNull()?.let { camera ->
                        currentAction(MapAction.RendererCameraIdle(generation, camera))
                    }
                }
            }.also(readyMap::addOnCameraIdleListener)
            clickListener = MapLibreMap.OnMapClickListener { point ->
                val screenPoint = readyMap.projection.toScreenLocation(point).toDomainScreenPoint()
                currentAction(MapAction.MapTapped(point.toDomainPoint(), queryPort.query(screenPoint, INTERACTIVE_OVERLAYS)))
                true
            }.also(readyMap::addOnMapClickListener)
            longPressListener = MapLibreMap.OnMapLongClickListener { point ->
                val screenPoint = readyMap.projection.toScreenLocation(point).toDomainScreenPoint()
                currentAction(
                    MapAction.MapLongPressed(
                        point.toDomainPoint(),
                        queryPort.query(screenPoint, INTERACTIVE_OVERLAYS),
                    ),
                )
                true
            }.also(readyMap::addOnMapLongClickListener)
        }
        onDispose {
            cameraListener?.let { listener -> map?.removeOnCameraIdleListener(listener) }
            clickListener?.let { listener -> map?.removeOnMapClickListener(listener) }
            longPressListener?.let { listener -> map?.removeOnMapLongClickListener(listener) }
            mapView.removeOnDidFailLoadingMapListener(loadFailureListener)
            mapView.removeOnRenderErrorListener(renderErrorListener)
        }
    }

    val activePackage = state.chartPackages.firstOrNull { it.id == state.activeChartPackageId }
    LaunchedEffect(map, activePackage?.id, activePackage?.localUri, activePackage?.tileSize, generation) {
        val readyMap = map ?: return@LaunchedEffect
        val requestGeneration = styleGeneration.incrementAndGet()
        activeStyle = null
        submittedCameraCommand.set(null)
        val packageExists = activePackage?.hasReadableMbTiles() == true
        currentAction(
            MapAction.RendererCoverageChanged(
                generation,
                when {
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
                style.addPointOverlay(MapOverlayId.SAVED_PLACES, 0xfff7b500.toInt(), 5f)
                style.addPointOverlay(MapOverlayId.SELECTION, 0xffffffff.toInt(), 7f)
                style.addLineOverlay(MapOverlayId.MEASUREMENT, 0xfff7b500.toInt(), 3f)
                style.addLineOverlay(MapOverlayId.MANUAL_ROUTE, 0xff00a4ef.toInt(), 5f)
                style.addPointOverlay(MapOverlayId.MANUAL_ROUTE_POINTS, 0xff00a4ef.toInt(), 5f)
                style.addPointOverlay(MapOverlayId.POSITION_OBSERVATION, 0xff00d084.toInt(), 7f)
                activeStyle = style
                currentAction(MapAction.RendererHostReady(generation))
                currentAction(MapAction.RendererReady(generation))
                currentAction(
                    MapAction.RendererCoverageChanged(
                        generation,
                        when {
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
        state.position.observation,
    ) {
        val style = activeStyle ?: return@LaunchedEffect
        style.source(MapOverlayId.SAVED_PLACES)?.setGeoJson(
            FeatureCollection.fromFeatures(state.places.map { place -> place.point.toFeature("place:${place.id}") }),
        )
        style.source(MapOverlayId.SELECTION)?.setGeoJson(
            state.selection?.point.toFeatureCollection("selection"),
        )
        style.source(MapOverlayId.MEASUREMENT)?.setGeoJson(
            state.measurementDraft?.points.toLineFeatureCollection("measurement"),
        )
        style.source(MapOverlayId.MANUAL_ROUTE)?.setGeoJson(
            state.routeDraft?.waypoints.toLineFeatureCollection("route:${state.routeDraft?.id.orEmpty()}"),
        )
        style.source(MapOverlayId.MANUAL_ROUTE_POINTS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                state.routeDraft?.waypoints.orEmpty().mapIndexed { index, point ->
                    point.toFeature("route-point:${state.routeDraft?.id.orEmpty()}:$index")
                },
            ),
        )
        style.source(MapOverlayId.POSITION_OBSERVATION)?.setGeoJson(
            state.position.observation?.point.toFeatureCollection(
                "position:${state.position.observation?.observationId.orEmpty()}",
            ),
        )
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

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
private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
private fun PointF.toDomainScreenPoint() = MapScreenPoint(x.toDouble(), y.toDouble())
private fun GeoBounds.toLatLngBounds() = LatLngBounds.from(north, east, south, west)
private fun GeoPoint.toGeoJsonPoint() = Point.fromLngLat(longitude, latitude)
private fun GeoPoint.toFeature(id: String) = Feature.fromGeometry(toGeoJsonPoint(), null, id)
private fun GeoPoint?.toFeatureCollection(id: String) = FeatureCollection.fromFeatures(
    if (this == null) emptyList() else listOf(toFeature(id)),
)
private fun List<GeoPoint>?.toLineFeatureCollection(id: String) = FeatureCollection.fromFeatures(
    this?.takeIf { it.size >= 2 }?.let { points ->
        listOf(Feature.fromGeometry(LineString.fromLngLats(points.map { it.toGeoJsonPoint() }), null, id))
    }.orEmpty(),
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

private fun Style.addLineOverlay(id: MapOverlayId, color: Int, width: Float) {
    addSource(GeoJsonSource(id.wireValue, FeatureCollection.fromFeatures(emptyList<Feature>())))
    addLayer(LineLayer(id.wireValue, id.wireValue).withProperties(lineColor(color), lineWidth(width)))
}

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
    MapOverlayId.MEASUREMENT -> "measurement"
    MapOverlayId.MANUAL_ROUTE -> "route:"
    MapOverlayId.MANUAL_ROUTE_POINTS -> "route-point:"
    MapOverlayId.POSITION_OBSERVATION -> "position:"
}

private val INTERACTIVE_OVERLAYS = setOf(
    MapOverlayId.SAVED_PLACES,
    MapOverlayId.SELECTION,
    MapOverlayId.MEASUREMENT,
    MapOverlayId.MANUAL_ROUTE,
    MapOverlayId.MANUAL_ROUTE_POINTS,
)

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

private const val CHART_SOURCE = "installed-raster-chart"
private const val CHART_LAYER = "installed-raster-chart-layer"
private val nextRendererGeneration = AtomicLong(0L)

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
