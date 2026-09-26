package com.yokuli.marine.shell.rebuild.chart

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import androidx.compose.runtime.*
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.rebuild.GeoPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

sealed interface MapSource {
    /** APK 内置全球底图，不依赖网络或 Google Play services。 */
    data object Offline : MapSource
    data object Satellite : MapSource
    /** 空 ID 明确表示已选择自定义，但尚未选择文件夹；不能回退或自动选第一项。 */
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
enum class MapPointStyle { PIN, SOUNDING }
data class MapPoint(val id: String, val point: GeoPoint, val label: String = "", val color: Long = 0xFF007F9B, val radiusDp: Float = 10f, val draggable: Boolean = false,val style:MapPointStyle=MapPointStyle.PIN)
data class MapLine(val id: String, val points: List<GeoPoint>, val color: Long = 0xFF007F9B, val widthDp: Float = 3f, val dashed: Boolean = false)
data class MapCircle(val id: String, val center: GeoPoint, val radiusMeters: Double, val color: Long = 0xFF007F9B, val dashed: Boolean = false)
/** 实际观测区域，不表示水深或可安全航行范围。颜色的 alpha 表示观测密度或时间。 */
data class MapArea(val id: String, val boundary: List<GeoPoint>, val color: Long, val holes:List<List<GeoPoint>> = emptyList())
/** AIS 是独立交通实体；报告点保持真实位置，Heading 缺失时不能用 COG 冒充船艏。 */
data class MapAisTarget(
    val mmsi:String, val point:GeoPoint, val name:String, val kind:String,
    val heading:Double?=null, val course:Double?=null, val speedMetersPerSecond:Double?=null,
    val stale:Boolean=false, val lost:Boolean=false, val risk:Boolean=false, val selected:Boolean=false,
    val distress:String="NONE",
    val tracks:List<List<GeoPoint>> = emptyList(),
    /** 关注是显示优先级，不替代风险计算；视口裁剪时与选中/风险目标一起保留。 */
    val watched:Boolean = false,
)
data class MapScene(
    val vessel: MapVessel? = null,
    val points: List<MapPoint> = emptyList(),
    val lines: List<MapLine> = emptyList(),
    val circles: List<MapCircle> = emptyList(),
    val demo: Boolean = false,
    val areas: List<MapArea> = emptyList(),
    val aisTargets:List<MapAisTarget> = emptyList(),
    /** 工具模式保留显示但不接管航点、测距或放锚的交互。 */
    val aisInteractive:Boolean=true,
)

/** 两个地图引擎消费同一份 AIS 轨迹和一分钟航迹向量；历史/预测用不同线型。 */
internal fun MapScene.trafficGeometry():MapScene = if(aisTargets.isEmpty()) this else copy(lines=lines+buildList {
    aisTargets.forEach { target ->
        val color=when {target.risk||target.distress=="ACTIVE"->0xFFD74A29;target.selected->0xFFAC4DDD;target.stale->0xFF84898C;else->0xFF168B76}
        target.tracks.forEachIndexed {index,segment->if(segment.size>=2&&segment.all {it.valid()})add(MapLine("ais:track:${target.mmsi}:$index",segment,color,1.5f,false))}
        if(!target.stale&&!target.lost&&target.point.valid()) {
            val speed=target.speedMetersPerSecond
            val course=target.course
            if(speed!=null&&speed.isFinite()&&speed>0.25&&speed<1000&&course!=null&&course.isFinite()&&course in 0.0..360.0)
                add(MapLine("ais:vector:${target.mmsi}",listOf(target.point,destination(target.point,speed*60.0,course)),color,1.7f,true))
        }
    }
})

sealed interface MapEvent {
    data class CameraChanged(val center: GeoPoint, val zoom: Double) : MapEvent
    /** hitPoint是本次手指点按的地理坐标；不可用时不能用对象中心冒充。 */
    data class ItemSelected(val id: String, val hitPoint: GeoPoint? = null) : MapEvent
    data class PointMoved(val id: String, val point: GeoPoint) : MapEvent
    data class CoordinateSelected(val point: GeoPoint) : MapEvent
    data object GestureStarted : MapEvent
}

data class MapCameraRequest(val id: Long, val point: GeoPoint? = null, val zoom: Double = 13.0, val points: List<GeoPoint> = emptyList())

enum class MapOrientationMode { NORTH_UP, HEADING_UP, COURSE_UP }

/** 图册发起的只读对象预览；版本变化后失效，不能混入规划所选数据。 */
data class LibraryObjectPreview(val feature:com.yokuli.runtime.contract.chart.NauticalFeature,val datasetRevision:Long,val requestId:String=java.util.UUID.randomUUID().toString())

/** View state is local to the task. A replay cannot move the chart's camera. */
class MapViewState(center: GeoPoint, zoom: Double = 13.0) {
    /** 用户选项与本次实际可用模式分开；拖图不改选项，数据恢复后自然恢复朝向。 */
    var orientationMode by mutableStateOf(MapOrientationMode.NORTH_UP)
    var effectiveOrientationMode by mutableStateOf(MapOrientationMode.NORTH_UP)
    var orientationIssue by mutableStateOf<String?>(null)
    var center by mutableStateOf(center)
    var zoom by mutableDoubleStateOf(zoom)
    var follow by mutableStateOf(false)
    var showCrosshair by mutableStateOf(false)
    var ruler by mutableStateOf<List<GeoPoint>>(emptyList())
    var interactive by mutableStateOf(true)
    /** 放锚、选点和编辑航线等工具可关闭对象查询，保持地图手势本身可用。 */
    var objectPickingEnabled by mutableStateOf(true)
    /** 点击收藏只在海图预览；明确点按详情才打开拥有该记录的应用。 */
    /** 对象查询的实际点按位置；不移动准星，也不冒充船位。 */
    var selectedChartCoordinate by mutableStateOf<GeoPoint?>(null)
    var selectedChartObjects by mutableStateOf<List<com.yokuli.runtime.contract.chart.NauticalFeature>>(emptyList())
    var libraryPreview by mutableStateOf<LibraryObjectPreview?>(null)
    /** 只消费用户这一次“在海图查看”的定位请求；返回本次访问不能重新抢走镜头。 */
    var libraryPreviewCameraRequestId by mutableStateOf<String?>(null)
    var libraryPreviewNote by mutableStateOf<String?>(null)
    var planningLines by mutableStateOf<List<MapLine>>(emptyList())
    var planningPoints by mutableStateOf<List<MapPoint>>(emptyList())
    var planningAreas by mutableStateOf<List<MapArea>>(emptyList())
    var selectedPlaceId by mutableStateOf<String?>(null)
    var selectedAisMmsi by mutableStateOf<String?>(null)
    /** 当前宿主的投影计数，仅用于说明视口裁剪，不改变运行时目标。 */
    var aisVisibleCount by mutableIntStateOf(0)
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
    var unitPreferences by mutableStateOf(com.yokuli.shell.contract.MarineUnitPreferences())
    var chinese by mutableStateOf(legacy.optString("language", java.util.Locale.getDefault().language) == "zh")
    var snapshot by mutableStateOf<Bitmap?>(null)
    var snapshotCapturedAt by mutableLongStateOf(0L)
    var snapshotSource by mutableStateOf<MapSource?>(null)
    var snapshotDemo by mutableStateOf(false)
    private var saveGeneration = 0L
    private val file = AtomicFile(File(context.filesDir, "map-source-v1.json"))
    private val mutex = Mutex()
    private val saved = runCatching { JSONObject(file.openRead().bufferedReader().use { it.readText() }) }.getOrNull()
    private val savedType = saved?.optString("type") ?: legacy.optString("mapMode", "standard")
    private fun restored(): MapSource = when (savedType) {
        // 从同包名应用版升级时，纯 AOSP 不能恢复到依赖 Google Play services 的卫星图。
        "satellite" -> if (BuildConfig.ROM_HOME) MapSource.Offline else MapSource.Satellite
        "custom" -> MapSource.CustomLayer(saved?.optString("id").orEmpty())
        // 旧 marine 只有模式，没有文件夹身份；不能猜测用户选了列表第一项。
        "marine" -> MapSource.CustomLayer("")
        // online / standard 是升级前的普通地图选择，统一迁移到离线底图。
        "offline", "online", "standard" -> MapSource.Offline
        else -> MapSource.Offline
    }
    var source by mutableStateOf(restored())
        private set
    /** 与当前模式一起原子保存；切到底图或卫星不会忘记自定义文件夹。 */
    var customLayerId by mutableStateOf(
        (source as? MapSource.CustomLayer)?.layerId?.takeIf { it.isNotBlank() }
            ?: saved?.optString("customId")?.takeIf { it.isNotBlank() }
    )
        private set
    /** 背景与数据集分别选择；选择卫星或隐藏图形不改变分析数据。 */
    var selectedDatasetIds by mutableStateOf(saved?.optJSONArray("datasetIds")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.distinct() }.orEmpty())
        private set
    val charts get() = (context.applicationContext as com.yokuli.marine.shell.rebuild.YokuliApplication).marineSystem.charts
    fun selectDataset(id:String?) { selectedDatasetIds = id?.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty(); select(source) }
    /** 多份相邻资料可共同选用；显式取消仍保留其他选择，不把浏览当成选用。 */
    fun includeDataset(id:String,included:Boolean) {
        if(id.isBlank())return
        selectedDatasetIds=if(included)(selectedDatasetIds+id).distinct()else selectedDatasetIds.filterNot {it==id}
        select(source)
    }
    /** 仅调整已选集合顺序；越界、重复或夹带其他数据集的请求不改变当前选择。 */
    fun reorderDatasets(ids:List<String>) {
        if(ids.size!=selectedDatasetIds.size||ids.toSet()!=selectedDatasetIds.toSet()||ids.distinct().size!=ids.size)return
        if(ids==selectedDatasetIds)return
        selectedDatasetIds=ids.toList()
        select(source)
    }
    var saveFailed by mutableStateOf(false)
        private set
    private val views = mutableMapOf<String, MapViewState>()
    init {
        if (saved == null || savedType !in setOf("offline", "satellite", "custom") ||
            source == MapSource.Offline && savedType != "offline") select(source)
    }
    fun view(key: String, center: GeoPoint = GeoPoint(-36.84, 174.77), zoom: Double = 13.0) = views.getOrPut(key) { MapViewState(center, zoom) }
    /** 随 Shell 访问实例退出释放 AIS 相机草稿，返回栈仍保留的实例不受影响。 */
    fun retainAisViews(uiStateKeys:Set<String>) { views.keys.removeAll {it.startsWith("ais:")&&it.removePrefix("ais:") !in uiStateKeys} }
    fun selectedLayer(): ChartLayer? = (source as? MapSource.CustomLayer)?.let { selected -> library.layers.firstOrNull { it.id == selected.layerId } }
    fun customFolderName(zh: Boolean): String = customLayerId?.let { id ->
        library.folders.firstOrNull { it.id == id }?.let { it.layerName ?: it.name }
            ?: if (zh) "文件夹不可用" else "Folder unavailable"
    } ?: if (zh) "未选择文件夹" else "No folder selected"
    fun sourceName(zh: Boolean): String = when (source) {
        MapSource.Offline -> if (zh) "底图" else "Basemap"
        MapSource.Satellite -> if (zh) "卫星" else "Satellite"
        is MapSource.CustomLayer -> (if (zh) "自定义 · " else "Custom · ") + customFolderName(zh)
    }
    fun selectCustom() = select(MapSource.CustomLayer(customLayerId.orEmpty()))
    fun select(value: MapSource) {
        source = value
        if (value is MapSource.CustomLayer) customLayerId = value.layerId.takeIf { it.isNotBlank() }
        val generation = ++saveGeneration
        val snapshot = JSONObject().put("type", when (value) { MapSource.Offline -> "offline"; MapSource.Satellite -> "satellite"; is MapSource.CustomLayer -> "custom" })
        if (value is MapSource.CustomLayer) snapshot.put("id", value.layerId)
        snapshot.put("customId", customLayerId.orEmpty())
        snapshot.put("datasetIds", org.json.JSONArray(selectedDatasetIds))
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
    /** 移除已选文件夹后保留自定义空状态；不偷偷改用底图或别的文件夹。 */
    fun removingLayer(id: String) {
        if (id.isBlank()) return
        if (source == MapSource.CustomLayer(id)) select(MapSource.CustomLayer(""))
        else if (customLayerId == id) { customLayerId = null; select(source) }
    }
}
