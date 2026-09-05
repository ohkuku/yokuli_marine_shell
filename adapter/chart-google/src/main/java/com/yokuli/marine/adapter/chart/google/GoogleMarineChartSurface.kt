package com.yokuli.marine.adapter.chart.google

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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapState

private data class CameraSnapshot(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float,
    val bearing: Float,
    val tilt: Float,
)

private val CameraSnapshotSaver = listSaver<CameraSnapshot, Double>(
    save = { value ->
        listOf(
            value.latitude,
            value.longitude,
            value.zoom.toDouble(),
            value.bearing.toDouble(),
            value.tilt.toDouble(),
        )
    },
    restore = { values ->
        CameraSnapshot(
            latitude = values[0],
            longitude = values[1],
            zoom = values[2].toFloat(),
            bearing = values[3].toFloat(),
            tilt = values[4].toFloat(),
        )
    },
)

private val AucklandHarbour = CameraSnapshot(
    latitude = -36.8485,
    longitude = 174.7633,
    zoom = 11f,
    bearing = 0f,
    tilt = 0f,
)

/**
 * Google Maps adapter for the shared chart surface.
 *
 * 中文：本模块只负责 SDK 生命周期、camera 与手势，不拥有 Anchor/NMEA/Navigation 任务。
 * English: This adapter owns SDK lifecycle, camera, and gestures, never marine runtimes.
 */
@Composable
fun GoogleMarineChartSurface(
    state: MapState,
    onCameraChanged: (MapCamera) -> Unit,
    onLongPress: (GeoPoint) -> Unit,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val topInsetPx = with(density) { 96.dp.roundToPx() }
    val bottomInsetPx = with(density) { 150.dp.roundToPx() }
    var camera by rememberSaveable(stateSaver = CameraSnapshotSaver) {
        mutableStateOf(state.camera.toSnapshot())
    }
    val currentOnCameraChanged by rememberUpdatedState(onCameraChanged)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val mapView = remember(context) {
        MapView(
            context,
            GoogleMapOptions()
                .mapColorScheme(if (darkMode) MapColorScheme.DARK else MapColorScheme.LIGHT)
                .mapType(GoogleMap.MAP_TYPE_NORMAL)
                .compassEnabled(false)
                .mapToolbarEnabled(false)
                .rotateGesturesEnabled(true)
                .scrollGesturesEnabled(true)
                .tiltGesturesEnabled(false)
                .zoomControlsEnabled(false)
                .zoomGesturesEnabled(true),
        )
    }
    val lifecycleDriver = remember(mapView) { MapViewLifecycleDriver(mapView) }
    var googleMap by remember(mapView) { mutableStateOf<GoogleMap?>(null) }

    DisposableEffect(mapView, lifecycle, context.applicationContext) {
        lifecycleDriver.create()
        val observer = LifecycleEventObserver { _, event -> lifecycleDriver.onEvent(event) }
        val memoryCallbacks = MapMemoryCallbacks(mapView)
        lifecycle.addObserver(observer)
        lifecycleDriver.syncTo(lifecycle.currentState)
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)
        onDispose {
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
            googleMap?.setOnCameraIdleListener(null)
            lifecycleDriver.destroy()
        }
    }

    DisposableEffect(mapView) {
        var disposed = false
        mapView.getMapAsync { readyMap ->
            if (disposed) return@getMapAsync
            googleMap = readyMap.apply {
                mapType = GoogleMap.MAP_TYPE_NORMAL
                isBuildingsEnabled = false
                isIndoorEnabled = false
                isTrafficEnabled = false
                uiSettings.apply {
                    isCompassEnabled = false
                    isIndoorLevelPickerEnabled = false
                    isMapToolbarEnabled = false
                    isMyLocationButtonEnabled = false
                    isRotateGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    isTiltGesturesEnabled = false
                    isZoomControlsEnabled = false
                    isZoomGesturesEnabled = true
                }
                moveCamera(CameraUpdateFactory.newCameraPosition(camera.toCameraPosition()))
                setOnCameraIdleListener {
                    camera = cameraPosition.toSnapshot()
                    currentOnCameraChanged(cameraPosition.toDomainCamera())
                }
                setOnMapLongClickListener { point -> currentOnLongPress(point.toDomainPoint()) }
            }
        }
        onDispose {
            disposed = true
            googleMap?.setOnMapLongClickListener(null)
        }
    }

    LaunchedEffect(googleMap, darkMode, topInsetPx, bottomInsetPx) {
        googleMap?.apply {
            setMapColorScheme(if (darkMode) MapColorScheme.DARK else MapColorScheme.LIGHT)
            setPadding(0, topInsetPx, 0, bottomInsetPx)
        }
    }

    LaunchedEffect(
        googleMap,
        state.selection,
        state.places,
        state.measurementDraft,
        state.routeDraft,
        state.position.observation,
    ) {
        googleMap?.apply {
            clear()
            state.places.forEach { place ->
                addMarker(MarkerOptions().position(place.point.toLatLng()).title(place.name))
            }
            state.selection?.let { selection ->
                addMarker(MarkerOptions().position(selection.point.toLatLng()))
            }
            state.measurementDraft?.points?.takeIf { it.isNotEmpty() }?.let { points ->
                addPolyline(PolylineOptions().addAll(points.map(GeoPoint::toLatLng)).color(0xfff7b500.toInt()).width(5f))
            }
            state.routeDraft?.waypoints?.takeIf { it.isNotEmpty() }?.let { points ->
                addPolyline(PolylineOptions().addAll(points.map(GeoPoint::toLatLng)).color(0xff00a4ef.toInt()).width(7f))
            }
            state.position.observation?.let { observation ->
                addMarker(MarkerOptions().position(observation.point.toLatLng()).title(observation.source))
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}

private fun CameraSnapshot.toCameraPosition(): CameraPosition = CameraPosition.Builder()
    .target(LatLng(latitude, longitude))
    .zoom(zoom)
    .bearing(bearing)
    .tilt(tilt)
    .build()

private fun MapCamera.toSnapshot(): CameraSnapshot = CameraSnapshot(
    latitude = center.latitude,
    longitude = center.longitude,
    zoom = zoom.toFloat(),
    bearing = bearing.toFloat(),
    tilt = 0f,
)

private fun CameraPosition.toDomainCamera(): MapCamera = MapCamera(
    center = target.toDomainPoint(),
    zoom = zoom.toDouble(),
    bearing = bearing.toDouble(),
)

private fun LatLng.toDomainPoint(): GeoPoint = GeoPoint(latitude, longitude)
private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun CameraPosition.toSnapshot(): CameraSnapshot = CameraSnapshot(
    latitude = target.latitude,
    longitude = target.longitude,
    zoom = zoom,
    bearing = bearing,
    tilt = tilt,
)

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
