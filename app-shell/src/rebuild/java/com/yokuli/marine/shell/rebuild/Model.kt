package com.yokuli.marine.shell.rebuild

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.chart.ChartLibrary
import com.yokuli.marine.shell.rebuild.chart.MapSessionStore
import com.yokuli.shell.contract.MeasurementUnitSystem
import com.yokuli.marine.shell.rebuild.data.DataHub
import com.yokuli.marine.shell.rebuild.data.MarinePresentationBridge
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.*

/** WGS84 坐标，以十进制度存储；显示格式只由系统偏好决定。 */
data class GeoPoint(val lat: Double, val lon: Double) {
    fun json() = JSONObject().put("lat", lat).put("lon", lon)
    fun valid() = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
    companion object { fun from(j: JSONObject) = GeoPoint(j.getDouble("lat"), j.getDouble("lon")).also { require(it.valid()) } }
}
fun distance(a: GeoPoint, b: GeoPoint): Double {
    val p = Math.toRadians(a.lat); val q = Math.toRadians(b.lat)
    val h = sin((q-p)/2).pow(2) + cos(p)*cos(q)*sin(Math.toRadians(b.lon-a.lon)/2).pow(2)
    return 6371008.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
fun bearing(a: GeoPoint, b: GeoPoint): Double {
    val p = Math.toRadians(a.lat); val q = Math.toRadians(b.lat); val d = Math.toRadians(b.lon-a.lon)
    return (Math.toDegrees(atan2(sin(d)*cos(q), cos(p)*sin(q)-sin(p)*cos(q)*cos(d)))+360)%360
}
fun coordinates(p: GeoPoint): String {
    fun value(v: Double, positive: String, negative: String) = String.format(Locale.US, "%d° %06.3f′ %s", abs(v).toInt(), (abs(v)%1)*60, if(v>=0) positive else negative)
    return value(p.lat,"N","S")+"  "+value(p.lon,"E","W")
}
fun nm(m: Double) = if (m < 185.2) "${m.roundToInt()} m" else String.format(Locale.US, "%.2f nm", m/1852)
fun decimal(v: Double?, digits: Int = 1) = v?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "—"
fun uid() = UUID.randomUUID().toString()

enum class PlaceKind { MARK, ANCHORAGE, MARINA, HAZARD }
/** 我的航行拥有的收藏；锚地是坐标的一种用途，不另建重复位置。 */
data class Place(val id: String = uid(), val name: String, val point: GeoPoint, val note: String = "",
    val kind: PlaceKind = PlaceKind.MARK, val collection: String = "") {
    fun json() = JSONObject().put("id",id).put("name",name).put("point",point.json()).put("note",note)
        .put("kind",kind.name).put("collection",collection)
    companion object { fun from(j:JSONObject) = Place(j.getString("id"),j.getString("name"),GeoPoint.from(j.getJSONObject("point")),j.optString("note"),
        runCatching { PlaceKind.valueOf(j.optString("kind")) }.getOrDefault(PlaceKind.MARK),j.optString("collection")) }
}
/** 用户规划的有序折线；保存、预览、导航是三个独立动作。 */
data class Route(val id: String = uid(), val name: String, val points: List<GeoPoint>) {
    val length get() = points.zipWithNext().sumOf { distance(it.first,it.second) }
    fun json() = JSONObject().put("id",id).put("name",name).put("points",JSONArray(points.map { it.json() }))
    companion object { fun from(j:JSONObject) = Route(j.getString("id"),j.getString("name"),j.getJSONArray("points").objects().map(GeoPoint::from)) }
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
/** 旧版开始布局的兼容输入；1 小、2 中、4 宽，启动时迁移到 Shell 的 StartDocument。 */
data class TileSpec(val app: String, val size: Int = 2)
/** 下锚前的未提交草稿；placeId/spotId 引用收藏位置，半径使用米，草稿不等于已值守。 */
data class AnchorDraft(val point:GeoPoint,val name:String,val placeId:Long?=null,val spotId:Long?=null,val radiusMeters:Double?=null)
/** 一次海图访问的临时交互；不包含图源、导航会话、航行记录或用户已保存的数据。 */
data class ChartInteractionSnapshot(
    val center: GeoPoint, val zoom: Double, val follow: Boolean, val showCrosshair: Boolean,
    val ruler: List<GeoPoint>, val selectedPlaceId: String?, val displayedRouteId: String?,
    val previewTrack: List<List<GeoPoint>>, val previewTitle: String?, val previewRoute: Route?,
    val selectedAisMmsi:String?=null,
)
/** 系统安装的应用身份；UI 标签与入口组织不能另建不一致的应用列表。 */
enum class AppId(val zh: String, val en: String, val icon: String) {
    CHART("海图","chart","chart"), LIBRARY("图册","chart library","layers"),
    VOYAGES("航海日志","logbook","logbook"), ANCHOR("守锚","anchor watch","anchor"),
    PLACES("我的航行","my sailing","route"), INSTRUMENTS("驾驶台","helm","helm"),
    DATA_CENTER("数据中心","data center","data"), NMEA("船联网","boat network","connect"),
    AIS("AIS","AIS","ais"),
    LOCAL_NMEA("数据共享","data sharing","share"), SETTINGS("设置","settings","settings"),
    TILES("磁贴工坊","tile studio","start");
    /** 中文应用列表按当前名称的拼音首字母分组，不沿用旧品牌或英文索引。 */
    val chineseIndex:Char get()=when(this) {
        CHART,VOYAGES->'H'; LIBRARY->'T'; PLACES->'W'; INSTRUMENTS->'J'
        NMEA,TILES->'C'; DATA_CENTER,LOCAL_NMEA,SETTINGS,ANCHOR->'S';AIS->'A'
    }
}

@HiltAndroidApp
class YokuliApplication : Application() {
    @javax.inject.Inject lateinit var marineProvider: javax.inject.Provider<com.yokuli.runtime.marine.MarineSystem>
    @javax.inject.Inject lateinit var contentProvider: javax.inject.Provider<com.yokuli.anchorwatch.api.MarineContentService>
    // Lazy connection preserves boot/background behavior: observing persisted notices does not open sensors.
    val marineSystem get() = marineProvider.get()
    val marineContent get() = contentProvider.get()
    lateinit var os: OsStore
    override fun onCreate() {
        super.onCreate()
        com.yokuli.runtime.marine.MarineSystemBootstrap.initialize(this)
        os = OsStore(this)
    }
}

/** 进程级组合入口：持有系统偏好读模型及业务适配器；页面只订阅，不自行打开连接或创建会话。 */
class OsStore(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val file = AtomicFile(File(context.filesDir, "experience-v1.json"))
    private val persistence = DurableSnapshotStore(file, scope)
    val persistenceState = persistence.state
    internal val initial = runCatching { JSONObject(file.openRead().bufferedReader().use { it.readText() }) }.getOrDefault(JSONObject())
    var chinese by mutableStateOf(initial.optString("language", Locale.getDefault().language) == "zh")
    var accent by mutableLongStateOf(initial.optLong("accent", 0xFF007F9B))
    var light by mutableStateOf(initial.optBoolean("light", false))
    var keepAwake by mutableStateOf(initial.optBoolean("keepAwake", true))
    var reduceMotion by mutableStateOf(initial.optBoolean("reduceMotion", false))
    var textSize by mutableStateOf("STANDARD")
    var measurementUnits by mutableStateOf(MeasurementUnitSystem.NAUTICAL)
    var coordinateFormat by mutableStateOf("DMM")
    var places by mutableStateOf(initial.optJSONArray("places")?.objects()?.mapNotNull { runCatching { Place.from(it) }.getOrNull() } ?: emptyList())
    var routes by mutableStateOf(initial.optJSONArray("routes")?.objects()?.mapNotNull { runCatching { Route.from(it) }.getOrNull() } ?: emptyList())
    var tiles by mutableStateOf(initial.optJSONArray("tiles")?.objects()?.mapNotNull {
        it.optString("app").takeIf { name -> AppId.entries.any { a -> a.name == name } }?.let { name -> TileSpec(name,it.optInt("size",2).takeIf { s -> s in listOf(1,2,4) } ?: 2) }
    } ?: listOf(TileSpec("CHART",4),TileSpec("PLACES"),TileSpec("LIBRARY"),TileSpec("DATA"),TileSpec("NMEA"),TileSpec("SETTINGS",4)))
    val shell by lazy { WpShellRuntime(this) }
    var page by mutableStateOf("start")
    private val backStack = mutableListOf<String>()
    var recent by mutableStateOf(listOf<String>())
    var editTiles by mutableStateOf(false)
    val notifications = SystemNotificationStore(context, scope)
    var center by mutableStateOf(runCatching { GeoPoint.from(initial.getJSONObject("camera")) }.getOrDefault(GeoPoint(-36.84,174.77)))
    var zoom by mutableDoubleStateOf(initial.optDouble("zoom",10.0).coerceIn(1.0,22.0))
    var mapMode by mutableStateOf(initial.optString("mapMode","marine"))
    var follow by mutableStateOf(false)
    var showCrosshair by mutableStateOf(false)
    var anchorDraft by mutableStateOf<AnchorDraft?>(null)
    var ruler by mutableStateOf<List<GeoPoint>>(emptyList())
    var draftRoute by mutableStateOf(initial.optJSONArray("draft")?.objects()?.mapNotNull {runCatching {GeoPoint.from(it)}.getOrNull()} ?: emptyList())
    var editingRoute by mutableStateOf(draftRoute.isNotEmpty() && initial.optString("activeRoute").isBlank())
    var editingRouteId by mutableStateOf<String?>(initial.optString("editingRoute").takeIf {it.isNotBlank()})
    var displayedRouteId by mutableStateOf<String?>(null)
    var activeRouteId by mutableStateOf<String?>(initial.optString("activeRoute").takeIf { it.isNotBlank() })
    var navigationRoute by mutableStateOf<Route?>(runCatching { Route.from(initial.getJSONObject("navigationSnapshot")) }.getOrNull()
        ?: routes.firstOrNull { it.id == activeRouteId }?.copy(points=routes.first { it.id == activeRouteId }.points.toList()))
    var routeLeg by mutableIntStateOf(initial.optInt("routeLeg",0))
    var recordingActive by mutableStateOf(false)
    var recordingPaused by mutableStateOf(false)
    var recordedSegments by mutableStateOf<List<List<GeoPoint>>>(emptyList())
    var cameraRequest by mutableStateOf<Pair<GeoPoint,Double>?>(null)
    var fitRequest by mutableStateOf<List<GeoPoint>?>(null)
    var nmeaHost by mutableStateOf(initial.optString("host","192.168.4.1"))
    var nmeaPort by mutableStateOf(initial.optString("port","10110"))
    var nmeaProtocol by mutableStateOf(initial.optString("protocol","TCP"))
    var serverPort by mutableStateOf(initial.optString("serverPort","10111"))
    var positionSource by mutableStateOf("none")
    var marine by mutableStateOf<MarinePresentationBridge?>(null)
        private set
    val content get() = (context.applicationContext as YokuliApplication).marineContent
    var systemAction: ((com.yokuli.runtime.contract.PositionSourceRequest)->Unit)? = null
    fun requestPosition(action:com.yokuli.runtime.contract.PositionSourceRequest) { systemAction?.invoke(action) }
    fun connectSystem(system: com.yokuli.runtime.marine.MarineSystem) {
        if (marine?.system === system) return
        marine?.close()
        marine = MarinePresentationBridge(this, system)
        notifications.connectAis(system.ais)
    }
    val hub = DataHub()
    val library = ChartLibrary(context, scope)
    val maps = MapSessionStore(context, scope, library, initial)
    val sailing by lazy { MySailingRepository(this) }
    val allPlaces get() = places + sailing.coordinates
    val activeRoute get() = navigationRoute?.takeIf { it.id == activeRouteId }
    val nextPoint get() = activeRoute?.points?.getOrNull(routeLeg)
    init {
        observeMarineNotices()
        scope.launch {
            var reportedFailure: Long? = null
            persistenceState.collect { state ->
                if (state.failed && !state.saving && reportedFailure != state.failedRevision) {
                    reportedFailure = state.failedRevision
                    notify("保存未完成，改动暂留本次运行中。打开通知中心重试。",
                        "Save did not complete. Changes are kept in this running app. Open notifications to retry.",
                        app=AppId.PLACES, severity=NoticeSeverity.WARNING, key="sailing-storage")
                } else if (!state.saving && state.requestedRevision > 0 && state.durableRevision == state.requestedRevision) {
                    // 重试或后续完整快照已落盘，解除过时的“仍未保存”操作提示。
                    notifications.items.filter { it.key == "sailing-storage" }.forEach { notifications.remove(it.id) }
                }
            }
        }
    }
    fun t(zh: String, en: String) = if (chinese) zh else en
    fun title(app: AppId) = t(app.zh,app.en)
    fun notify(zh: String, en: String, app: AppId? = shell.appForPage(page)?.app,
               severity: NoticeSeverity = NoticeSeverity.INFO, destination: String? = null, key: String? = null) {
        notifications.post(SystemNotice(app = app, chinese = zh, english = en, severity = severity,
            destination = destination, key = key))
    }
    fun open(destination: String) = shell.open(destination)
    /** 请求另一个应用处理当前对象；完成或返回时恢复调用页，而不是启动一个无关首页。 */
    fun openLinked(destination: String) = shell.openLinked(destination)
    /** 点开即消费通知；查看消息与业务警报确认是两个独立动作。 */
    fun openNotification(id: String) {
        val notice=notifications.items.firstOrNull {it.id==id} ?: return
        notifications.remove(id)
        if (notice.key == "sailing-storage" && persistenceState.value.failed) {
            notifications.open()
            return
        }
        notice.destination?.let(::openSystemDestination) ?: notifications.close()
    }
    /** 系统面板定位已有内容，当前页面只收起面板，不重建应用。 */
    fun openSystemDestination(destination: String) {
        notifications.close()
        shell.openSystemDestination(destination)
    }
    fun home() = shell.home()
    fun back() = shell.back()
    fun fly(point: GeoPoint, atZoom: Double = zoom) { follow = false; cameraRequest = point to atZoom; center = point; zoom = atZoom }
    internal fun captureChartInteraction(): ChartInteractionSnapshot {
        val view = maps.view("chart", center, zoom)
        return ChartInteractionSnapshot(center, zoom, follow, showCrosshair, ruler.toList(),
            view.selectedPlaceId, displayedRouteId, view.previewTrack.map { it.toList() }, view.previewTitle,
            view.previewRoute?.let { it.copy(points = it.points.toList()) },view.selectedAisMmsi)
    }
    internal fun restoreChartInteraction(snapshot: ChartInteractionSnapshot) {
        center = snapshot.center; zoom = snapshot.zoom; follow = snapshot.follow
        showCrosshair = snapshot.showCrosshair; ruler = snapshot.ruler
        displayedRouteId = snapshot.displayedRouteId
        maps.view("chart", center, zoom).apply {
            center = snapshot.center; zoom = snapshot.zoom; follow = snapshot.follow
            selectedPlaceId = snapshot.selectedPlaceId
            selectedAisMmsi = snapshot.selectedAisMmsi
            previewTrack = snapshot.previewTrack; previewTitle = snapshot.previewTitle; previewRoute = snapshot.previewRoute
        }
        // 新请求触发真实原生相机复位，不能仅更新坐标文案。
        fitRequest = null; cameraRequest = snapshot.center to snapshot.zoom
    }
    fun mark() {
        sailing.put(Place(name=t("标记 ${places.size+1}","mark ${places.size+1}"),point=center))
    }
    fun startRoute(route: Route) { navigationRoute=route.copy(points=route.points.toList()); activeRouteId = route.id; displayedRouteId = route.id; routeLeg = 0; save(); openLinked("chart"); fly(route.points.first()) }
    fun advanceRoute() {
        val route = activeRoute ?: return
        if (routeLeg < route.points.lastIndex) routeLeg++ else { activeRouteId = null; notify("航线已结束","Route ended") }
        save()
    }
    /** 只排队，不代表已保存。需要用户成功反馈的操作必须等待返回的 DurableCommit。 */
    fun save(): DurableCommit {
        val json = JSONObject()
            .put("places",JSONArray(places.map { it.json() })).put("routes",JSONArray(routes.map { it.json() }))
            .put("tiles",JSONArray(tiles.map { JSONObject().put("app",it.app).put("size",it.size) }))
            .put("camera",center.json()).put("zoom",zoom).put("mapMode",mapMode)
            .put("activeRoute",activeRouteId ?: "").put("routeLeg",routeLeg)
            .put("navigationSnapshot",navigationRoute?.takeIf { activeRouteId != null }?.json())
            .put("draft",JSONArray(draftRoute.map {it.json()})).put("editingRoute",editingRouteId ?: "")
            .put("host",nmeaHost).put("port",nmeaPort).put("protocol",nmeaProtocol).put("serverPort",serverPort).put("positionSource",positionSource)
        return persistence.submit(json.toString())
    }
    /** 由进程等待回执，切换页面不会取消保存。失败保留内存中的对象，统一入口可重试。 */
    fun saveWithFeedback(zh: String, en: String, destination: String? = null): DurableCommit {
        val commit = save()
        scope.launch {
            when (commit.result.await()) {
                DurableCommitResult.SAVED -> notify(zh, en, app=AppId.PLACES, destination=destination)
                DurableCommitResult.FAILED -> Unit // 统一存储状态给出可重试提示，避免同一次失败弹两条通知。
            }
        }
        return commit
    }
}
