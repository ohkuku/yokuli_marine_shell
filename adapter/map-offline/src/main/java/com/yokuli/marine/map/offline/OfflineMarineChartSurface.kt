package com.yokuli.marine.map.offline

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
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
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapState
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Renders an installed raster MBTiles package without network or a provider API key. */
@Composable
fun OfflineMarineChartSurface(
    state: MapState,
    onCameraChanged: (MapCamera) -> Unit,
    onLongPress: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentCameraChanged by rememberUpdatedState(onCameraChanged)
    val currentLongPress by rememberUpdatedState(onLongPress)
    remember(context.applicationContext) { MapLibre.getInstance(context.applicationContext) }
    val mapView = remember(context) { MapView(context) }
    val lifecycleDriver = remember(mapView) { OfflineMapLifecycleDriver(mapView) }
    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    var activeStyle by remember(mapView) { mutableStateOf<Style?>(null) }

    DisposableEffect(mapView, lifecycle, context.applicationContext) {
        lifecycleDriver.create()
        val observer = LifecycleEventObserver { _, event -> lifecycleDriver.onEvent(event) }
        val memoryCallbacks = OfflineMapMemoryCallbacks(mapView)
        lifecycle.addObserver(observer)
        lifecycleDriver.syncTo(lifecycle.currentState)
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)
        onDispose {
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
            lifecycleDriver.destroy()
        }
    }

    DisposableEffect(mapView) {
        var disposed = false
        var cameraListener: MapLibreMap.OnCameraIdleListener? = null
        var longPressListener: MapLibreMap.OnMapLongClickListener? = null
        mapView.getMapAsync { readyMap ->
            if (disposed) return@getMapAsync
            map = readyMap
            readyMap.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = false
            }
            readyMap.cameraPosition = state.camera.toCameraPosition()
            cameraListener = MapLibreMap.OnCameraIdleListener {
                readyMap.cameraPosition.toDomainCameraOrNull()?.let(currentCameraChanged)
            }.also(readyMap::addOnCameraIdleListener)
            longPressListener = MapLibreMap.OnMapLongClickListener { point ->
                currentLongPress(point.toDomainPoint())
                true
            }.also(readyMap::addOnMapLongClickListener)
        }
        onDispose {
            disposed = true
            cameraListener?.let { listener -> map?.removeOnCameraIdleListener(listener) }
            longPressListener?.let { listener -> map?.removeOnMapLongClickListener(listener) }
        }
    }

    val activePackage = state.chartPackages.firstOrNull { it.id == state.activeChartPackageId }
    LaunchedEffect(map, activePackage) {
        map?.setStyle(Style.Builder().fromJson(EMPTY_STYLE)) { style ->
            activePackage?.let { chartPackage ->
                style.addSource(RasterSource(CHART_SOURCE, chartPackage.localUri, 256))
                style.addLayer(RasterLayer(CHART_LAYER, CHART_SOURCE))
            }
            style.addPointOverlay(PLACES_SOURCE, PLACES_LAYER, 0xfff7b500.toInt(), 5f)
            style.addPointOverlay(SELECTION_SOURCE, SELECTION_LAYER, 0xffffffff.toInt(), 7f)
            style.addLineOverlay(MEASUREMENT_SOURCE, MEASUREMENT_LAYER, 0xfff7b500.toInt(), 3f)
            style.addLineOverlay(ROUTE_SOURCE, ROUTE_LAYER, 0xff00a4ef.toInt(), 5f)
            style.addPointOverlay(ROUTE_POINTS_SOURCE, ROUTE_POINTS_LAYER, 0xff00a4ef.toInt(), 5f)
            style.addPointOverlay(POSITION_SOURCE, POSITION_LAYER, 0xff00d084.toInt(), 7f)
            activeStyle = style
        }
    }

    LaunchedEffect(
        activeStyle,
        state.selection,
        state.places,
        state.measurementDraft,
        state.routeDraft,
        state.position.observation,
    ) {
        activeStyle?.getSourceAs<GeoJsonSource>(PLACES_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(state.places.map { it.point.toFeature() }),
        )
        activeStyle?.getSourceAs<GeoJsonSource>(SELECTION_SOURCE)?.setGeoJson(
            state.selection?.point.toFeatureCollection(),
        )
        activeStyle?.getSourceAs<GeoJsonSource>(MEASUREMENT_SOURCE)?.setGeoJson(
            state.measurementDraft?.points.toLineFeatureCollection(),
        )
        activeStyle?.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(
            state.routeDraft?.waypoints.toLineFeatureCollection(),
        )
        activeStyle?.getSourceAs<GeoJsonSource>(ROUTE_POINTS_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(state.routeDraft?.waypoints.orEmpty().map { it.toFeature() }),
        )
        activeStyle?.getSourceAs<GeoJsonSource>(POSITION_SOURCE)?.setGeoJson(
            state.position.observation?.point.toFeatureCollection(),
        )
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun MapCamera.toCameraPosition() = CameraPosition.Builder()
    .target(center.toLatLng()).zoom(zoom).bearing(bearing).tilt(0.0).build()
private fun CameraPosition.toDomainCameraOrNull() = target?.let { MapCamera(it.toDomainPoint(), zoom, bearing) }
private fun LatLng.toDomainPoint() = GeoPoint(latitude, longitude)
private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
private fun GeoPoint.toGeoJsonPoint() = Point.fromLngLat(longitude, latitude)
private fun GeoPoint.toFeature() = Feature.fromGeometry(toGeoJsonPoint())
private fun GeoPoint?.toFeatureCollection() = FeatureCollection.fromFeatures(
    if (this == null) emptyList() else listOf(toFeature()),
)
private fun List<GeoPoint>?.toLineFeatureCollection() = FeatureCollection.fromFeatures(
    this?.takeIf { it.size >= 2 }?.let { points ->
        listOf(Feature.fromGeometry(LineString.fromLngLats(points.map { it.toGeoJsonPoint() })))
    }
        .orEmpty(),
)

private fun Style.addPointOverlay(sourceId: String, layerId: String, color: Int, radius: Float) {
    addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyList<Feature>())))
    addLayer(
        CircleLayer(layerId, sourceId).withProperties(
            circleColor(color),
            circleRadius(radius),
            circleStrokeColor(0xffffffff.toInt()),
            circleStrokeWidth(1.5f),
        ),
    )
}

private fun Style.addLineOverlay(sourceId: String, layerId: String, color: Int, width: Float) {
    addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyList<Feature>())))
    addLayer(LineLayer(layerId, sourceId).withProperties(lineColor(color), lineWidth(width)))
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

private const val CHART_SOURCE = "installed-raster-chart"
private const val CHART_LAYER = "installed-raster-chart-layer"
private const val PLACES_SOURCE = "saved-places"
private const val PLACES_LAYER = "saved-places-layer"
private const val SELECTION_SOURCE = "map-selection"
private const val SELECTION_LAYER = "map-selection-layer"
private const val MEASUREMENT_SOURCE = "measurement-draft"
private const val MEASUREMENT_LAYER = "measurement-draft-layer"
private const val ROUTE_SOURCE = "manual-route-draft"
private const val ROUTE_LAYER = "manual-route-draft-layer"
private const val ROUTE_POINTS_SOURCE = "manual-route-points"
private const val ROUTE_POINTS_LAYER = "manual-route-points-layer"
private const val POSITION_SOURCE = "position-observation"
private const val POSITION_LAYER = "position-observation-layer"
private const val EMPTY_STYLE = """{
  "version": 8,
  "name": "Yokuli offline chart",
  "sources": {},
  "layers": [{"id":"background","type":"background","paint":{"background-color":"#082331"}}]
}"""
