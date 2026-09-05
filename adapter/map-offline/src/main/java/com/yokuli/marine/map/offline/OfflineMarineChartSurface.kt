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
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource

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
        }
    }

    LaunchedEffect(
        map,
        state.selection,
        state.places,
        state.measurementDraft,
        state.routeDraft,
        state.position.observation,
    ) {
        map?.apply {
            clear()
            state.places.forEach { place -> addMarker(MarkerOptions().position(place.point.toLatLng()).title(place.name)) }
            state.selection?.let { addMarker(MarkerOptions().position(it.point.toLatLng())) }
            state.measurementDraft?.points?.takeIf { it.isNotEmpty() }?.let { points ->
                addPolyline(PolylineOptions().addAll(points.map { it.toLatLng() }).color(0xfff7b500.toInt()).width(5f))
            }
            state.routeDraft?.waypoints?.takeIf { it.isNotEmpty() }?.let { points ->
                addPolyline(PolylineOptions().addAll(points.map { it.toLatLng() }).color(0xff00a4ef.toInt()).width(7f))
            }
            state.position.observation?.let { observation ->
                addMarker(MarkerOptions().position(observation.point.toLatLng()).title(observation.source))
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun MapCamera.toCameraPosition() = CameraPosition.Builder()
    .target(center.toLatLng()).zoom(zoom).bearing(bearing).tilt(0.0).build()
private fun CameraPosition.toDomainCameraOrNull() = target?.let { MapCamera(it.toDomainPoint(), zoom, bearing) }
private fun LatLng.toDomainPoint() = GeoPoint(latitude, longitude)
private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

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
private const val EMPTY_STYLE = """{
  "version": 8,
  "name": "Yokuli offline chart",
  "sources": {},
  "layers": [{"id":"background","type":"background","paint":{"background-color":"#082331"}}]
}"""
