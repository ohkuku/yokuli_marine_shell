package com.yokuli.marine.shell.rebuild.chart

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.nm
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

sealed interface MapSource {
    data object Online : MapSource
    data object Satellite : MapSource
    data class CustomLayer(val layerId: String) : MapSource
}

data class MapVessel(val point: GeoPoint, val courseDegrees: Double? = null, val fresh: Boolean = true)
data class MapPoint(val id: String, val point: GeoPoint, val label: String = "", val color: Long = 0xFF007F9B, val radiusDp: Float = 10f, val draggable: Boolean = false)
data class MapLine(val id: String, val points: List<GeoPoint>, val color: Long = 0xFF007F9B, val widthDp: Float = 3f, val dashed: Boolean = false)
data class MapCircle(val id: String, val center: GeoPoint, val radiusMeters: Double, val color: Long = 0xFF007F9B, val dashed: Boolean = false)
data class MapScene(
    val vessel: MapVessel? = null,
    val points: List<MapPoint> = emptyList(),
    val lines: List<MapLine> = emptyList(),
    val circles: List<MapCircle> = emptyList(),
)

sealed interface MapEvent {
    data class CameraChanged(val center: GeoPoint, val zoom: Double) : MapEvent
    data class ItemSelected(val id: String) : MapEvent
    data class PointMoved(val id: String, val point: GeoPoint) : MapEvent
    data class CoordinateSelected(val point: GeoPoint) : MapEvent
    data object GestureStarted : MapEvent
}

data class MapCameraRequest(val id: Long, val point: GeoPoint? = null, val zoom: Double = 13.0, val points: List<GeoPoint> = emptyList())

/** View state is local to the task. A replay cannot move the chart's camera. */
class MapViewState(center: GeoPoint, zoom: Double = 13.0) {
    var center by mutableStateOf(center)
    var zoom by mutableDoubleStateOf(zoom)
    var follow by mutableStateOf(false)
    var showCrosshair by mutableStateOf(false)
    var ruler by mutableStateOf<List<GeoPoint>>(emptyList())
    var interactive by mutableStateOf(true)
    internal var request by mutableStateOf<MapCameraRequest?>(null)
    private var requestId = 0L
    fun fly(point: GeoPoint, zoom: Double = this.zoom) {
        if (!point.valid()) return
        follow = false
        this.center = point; this.zoom = zoom.coerceIn(1.0, 22.0)
        request = MapCameraRequest(++requestId, point, this.zoom)
    }
    fun fit(points: List<GeoPoint>) {
        val valid = points.filter { it.valid() }
        if (valid.isEmpty()) return
        follow = false
        request = MapCameraRequest(++requestId, points = valid)
    }
}

/** Owns map sources and resources only; never owns navigation, watch or recording. */
class MapSessionStore(val context: Context, val scope: CoroutineScope, val library: ChartLibrary, private val legacy: JSONObject) {
    var distanceLabel: (Double) -> String = ::nm
    var chinese by mutableStateOf(legacy.optString("language", java.util.Locale.getDefault().language) == "zh")
    var snapshot by mutableStateOf<Bitmap?>(null)
    private var saveGeneration = 0L
    private val file = AtomicFile(File(context.filesDir, "map-source-v1.json"))
    private val mutex = Mutex()
    private val saved = runCatching { JSONObject(file.openRead().bufferedReader().use { it.readText() }) }.getOrNull()
    private fun restored(): MapSource = when (saved?.optString("type") ?: legacy.optString("mapMode", "standard")) {
        "satellite" -> MapSource.Satellite
        "custom" -> MapSource.CustomLayer(saved!!.optString("id"))
        "marine" -> library.folders.firstOrNull { it.layerName != null && it.enabled }?.let { MapSource.CustomLayer(it.id) } ?: MapSource.Online
        else -> MapSource.Online
    }
    var source by mutableStateOf(restored())
        private set
    var saveFailed by mutableStateOf(false)
        private set
    private val views = mutableMapOf<String, MapViewState>()
    init { if (saved == null) select(source) }
    fun view(key: String, center: GeoPoint = GeoPoint(-36.84, 174.77), zoom: Double = 13.0) = views.getOrPut(key) { MapViewState(center, zoom) }
    fun selectedLayer(): ChartLayer? = (source as? MapSource.CustomLayer)?.let { selected -> library.layers.firstOrNull { it.id == selected.layerId } }
    fun sourceName(zh: Boolean): String = when (val selected = source) {
        MapSource.Online -> if (zh) "在线" else "online"
        MapSource.Satellite -> if (zh) "卫星" else "satellite"
        is MapSource.CustomLayer -> library.folders.firstOrNull { it.id == selected.layerId }?.layerName ?: if (zh) "图层不可用" else "layer unavailable"
    }
    fun select(value: MapSource) {
        source = value
        val generation = ++saveGeneration
        val snapshot = JSONObject().put("type", when (value) { MapSource.Online -> "online"; MapSource.Satellite -> "satellite"; is MapSource.CustomLayer -> "custom" })
        if (value is MapSource.CustomLayer) snapshot.put("id", value.layerId)
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                if (generation != saveGeneration) return@withLock
                val result = runCatching {
                    val stream = file.startWrite()
                    try { stream.write(snapshot.toString().toByteArray()); file.finishWrite(stream) }
                    catch (error: Exception) { file.failWrite(stream); throw error }
                }
                withContext(Dispatchers.Main) { if (generation == saveGeneration) saveFailed = result.isFailure }
            }
        }
    }
    fun removingLayer(id: String) { if (source == MapSource.CustomLayer(id)) select(MapSource.Online) }
}
