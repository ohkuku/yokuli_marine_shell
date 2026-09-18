package com.yokuli.marine.shell.rebuild.chart

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import androidx.compose.runtime.*
import com.yokuli.marine.shell.BuildConfig
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

/** 船位和地理图形使用 WGS84；由原生地图在同一渲染帧内投影，禁止屏幕像素位置持久化。 */
data class MapVessel(
    val point: GeoPoint,
    /** 对地航迹向，只用于航迹向量，不代替船首向。 */
    val courseDegrees: Double? = null,
    val fresh: Boolean = true,
    /** 真实船首向；缺少新鲜船首向时，用无方向的船位标记。 */
    val headingDegrees: Double? = null,
    /** 航迹向量按一分钟航程绘制；低于 0.5 kn 时不绘制。 */
    val speedKnots: Double? = null,
)
data class MapPoint(val id: String, val point: GeoPoint, val label: String = "", val color: Long = 0xFF007F9B, val radiusDp: Float = 10f, val draggable: Boolean = false)
data class MapLine(val id: String, val points: List<GeoPoint>, val color: Long = 0xFF007F9B, val widthDp: Float = 3f, val dashed: Boolean = false)
data class MapCircle(val id: String, val center: GeoPoint, val radiusMeters: Double, val color: Long = 0xFF007F9B, val dashed: Boolean = false)
/** 实际观测区域，不表示水深或可安全航行范围。颜色的 alpha 表示观测密度或时间。 */
data class MapArea(val id: String, val boundary: List<GeoPoint>, val color: Long)
data class MapScene(
    val vessel: MapVessel? = null,
    val points: List<MapPoint> = emptyList(),
    val lines: List<MapLine> = emptyList(),
    val circles: List<MapCircle> = emptyList(),
    val demo: Boolean = false,
    val areas: List<MapArea> = emptyList(),
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
    /** 点击收藏只在海图预览；明确点按详情才打开拥有该记录的应用。 */
    var selectedPlaceId by mutableStateOf<String?>(null)
    /** 航行日志请求的只读轨迹预览，与实时航行记录各自保留。 */
    var previewTrack by mutableStateOf<List<List<GeoPoint>>>(emptyList())
    var previewTitle by mutableStateOf<String?>(null)
    /** 收藏路线的只读版本快照；与正在导航的冻结版本拥有不同的显示模式。 */
    var previewRoute by mutableStateOf<com.yokuli.marine.shell.rebuild.Route?>(null)
    var scaleTopDp by mutableFloatStateOf(118f)
    /** 仅移动版权文字，保持原生地图视口与镜头尺寸不变。 */
    var bottomOverlayDp by mutableFloatStateOf(0f)
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
    var nauticalScale by mutableStateOf(true)
    var chinese by mutableStateOf(legacy.optString("language", java.util.Locale.getDefault().language) == "zh")
    var snapshot by mutableStateOf<Bitmap?>(null)
    var snapshotCapturedAt by mutableLongStateOf(0L)
    var snapshotSource by mutableStateOf<MapSource?>(null)
    var snapshotDemo by mutableStateOf(false)
    private var saveGeneration = 0L
    private val file = AtomicFile(File(context.filesDir, "map-source-v1.json"))
    private val mutex = Mutex()
    private val saved = runCatching { JSONObject(file.openRead().bufferedReader().use { it.readText() }) }.getOrNull()
    private fun restored(): MapSource = when (saved?.optString("type") ?: legacy.optString("mapMode", "standard")) {
        // 从同包名应用版升级时，纯 AOSP 不能恢复到依赖 Google Play services 的卫星图。
        "satellite" -> if (BuildConfig.ROM_HOME) MapSource.Online else MapSource.Satellite
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
