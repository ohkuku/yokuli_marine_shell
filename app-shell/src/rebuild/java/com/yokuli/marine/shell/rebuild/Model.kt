package com.yokuli.marine.shell.rebuild

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.chart.ChartLibrary
import com.yokuli.marine.shell.rebuild.chart.FolderTileProvider
import com.yokuli.anchorwatch.map.SharedChartLayers
import com.yokuli.marine.shell.rebuild.data.DataHub
import com.yokuli.marine.shell.rebuild.data.MarineRuntime
import com.yokuli.anchorwatch.MainViewModel
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.*

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

data class Place(val id: String = uid(), val name: String, val point: GeoPoint, val note: String = "") {
    fun json() = JSONObject().put("id",id).put("name",name).put("point",point.json()).put("note",note)
    companion object { fun from(j:JSONObject) = Place(j.getString("id"),j.getString("name"),GeoPoint.from(j.getJSONObject("point")),j.optString("note")) }
}
data class Route(val id: String = uid(), val name: String, val points: List<GeoPoint>) {
    val length get() = points.zipWithNext().sumOf { distance(it.first,it.second) }
    fun json() = JSONObject().put("id",id).put("name",name).put("points",JSONArray(points.map { it.json() }))
    companion object { fun from(j:JSONObject) = Route(j.getString("id"),j.getString("name"),j.getJSONArray("points").objects().map(GeoPoint::from)) }
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
data class TileSpec(val app: String, val size: Int = 2) // 1 small, 2 medium, 4 wide
enum class AppId(val zh: String, val en: String, val icon: String) {
    CHART("海图","chart","chart"), LIBRARY("海图库","chart library","layers"),
    PLACES("我的航行","my sailing","route"), DATA("船舶数据","boat data","data"),
    NMEA("NMEA","NMEA","connect"), SETTINGS("设置","settings","settings"),
    ANCHOR("锚警报","anchor watch","anchor"), TRIP("航行记录","trip recorder","record"),
    ANCHORAGES("锚地","anchorages","pin"), SONAR("个人水深图","personal sonar","sonar"),
    INSTRUMENTS("仪表","instruments","data"), VOYAGES("航行日志","logbook","logbook")
}

@HiltAndroidApp
class YokuliApplication : Application() {
    lateinit var os: OsStore
    override fun onCreate() {
        super.onCreate()
        com.yokuli.anchorwatch.LegacyMarineRuntime.initialize(this)
        os = OsStore(this)
    }
}

/** One process owns sessions; screens subscribe and never open their own connections. */
class OsStore(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val file = AtomicFile(File(context.filesDir, "experience-v1.json"))
    private val writes = Channel<String>(Channel.CONFLATED)
    private val initial = runCatching { JSONObject(file.openRead().bufferedReader().use { it.readText() }) }.getOrDefault(JSONObject())
    var chinese by mutableStateOf(initial.optString("language", Locale.getDefault().language) == "zh")
    var accent by mutableLongStateOf(initial.optLong("accent", 0xFF007F9B))
    var light by mutableStateOf(initial.optBoolean("light", false))
    var keepAwake by mutableStateOf(initial.optBoolean("keepAwake", true))
    var reduceMotion by mutableStateOf(initial.optBoolean("reduceMotion", false))
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
    var toast by mutableStateOf<String?>(null)
    private var toastJob: Job? = null
    var storageError by mutableStateOf(false)
    var center by mutableStateOf(runCatching { GeoPoint.from(initial.getJSONObject("camera")) }.getOrDefault(GeoPoint(-36.84,174.77)))
    var zoom by mutableDoubleStateOf(initial.optDouble("zoom",10.0).coerceIn(1.0,22.0))
    var mapMode by mutableStateOf(initial.optString("mapMode","marine"))
    var follow by mutableStateOf(false)
    var showCrosshair by mutableStateOf(false)
    var ruler by mutableStateOf<List<GeoPoint>>(emptyList())
    var draftRoute by mutableStateOf(initial.optJSONArray("draft")?.objects()?.mapNotNull {runCatching {GeoPoint.from(it)}.getOrNull()} ?: emptyList())
    var editingRoute by mutableStateOf(draftRoute.isNotEmpty() && initial.optString("activeRoute").isBlank())
    var editingRouteId by mutableStateOf<String?>(initial.optString("editingRoute").takeIf {it.isNotBlank()})
    var displayedRouteId by mutableStateOf<String?>(null)
    var activeRouteId by mutableStateOf<String?>(initial.optString("activeRoute").takeIf { it.isNotBlank() })
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
    var marine by mutableStateOf<MarineRuntime?>(null)
        private set
    fun attachMarine(viewModel: MainViewModel) {
        if (marine?.vm === viewModel) return
        marine?.close()
        marine = MarineRuntime(this, viewModel)
    }
    val hub = DataHub()
    val library = ChartLibrary(context, scope)
    private var sharedChartProvider: FolderTileProvider? = null
    val activeRoute get() = routes.firstOrNull { it.id == activeRouteId }
    val nextPoint get() = activeRoute?.points?.getOrNull(routeLeg)
    init {
        scope.launch {
            snapshotFlow { library.revision to library.selectedLayers }.collect { (revision, layers) ->
                val provider=layers.takeIf { it.isNotEmpty() }?.let { FolderTileProvider(context,it) }
                val previous=sharedChartProvider
                sharedChartProvider=provider
                SharedChartLayers.update(provider,revision.toLong()) { open("library") }
                if(previous!=null) scope.launch(Dispatchers.IO) { previous.close() }
            }
        }
        scope.launch(Dispatchers.IO) {
            for (snapshot in writes) {
                val ok = runCatching {
                    val stream = file.startWrite()
                    try { stream.write(snapshot.toByteArray()); file.finishWrite(stream) }
                    catch (e: Exception) { file.failWrite(stream); throw e }
                }.isSuccess
                withContext(Dispatchers.Main) { storageError = !ok }
            }
        }
    }
    fun t(zh: String, en: String) = if (chinese) zh else en
    fun title(app: AppId) = t(app.zh,app.en)
    fun notify(zh: String, en: String) {
        toast = t(zh,en); toastJob?.cancel()
        toastJob = scope.launch { delay(3500); toast = null }
    }
    fun open(destination: String) = shell.open(destination)
    fun home() = shell.home()
    fun back() = shell.back()
    fun fly(point: GeoPoint, atZoom: Double = zoom) { follow = false; cameraRequest = point to atZoom; center = point; zoom = atZoom }
    fun mark() {
        places = places + Place(name=t("标记 ${places.size+1}","mark ${places.size+1}"),point=center)
        save(); notify("已保存标记","Mark saved")
    }
    fun startRoute(route: Route) { activeRouteId = route.id; displayedRouteId = route.id; routeLeg = 0; save(); open("chart"); fly(route.points.first()) }
    fun advanceRoute() {
        val route = activeRoute ?: return
        if (routeLeg < route.points.lastIndex) routeLeg++ else { activeRouteId = null; notify("航线已结束","Route ended") }
        save()
    }
    fun save() {
        val json = JSONObject().put("language",if(chinese) "zh" else "en").put("accent",accent).put("light",light)
            .put("keepAwake",keepAwake).put("reduceMotion",reduceMotion)
            .put("places",JSONArray(places.map { it.json() })).put("routes",JSONArray(routes.map { it.json() }))
            .put("tiles",JSONArray(tiles.map { JSONObject().put("app",it.app).put("size",it.size) }))
            .put("camera",center.json()).put("zoom",zoom).put("mapMode",mapMode)
            .put("activeRoute",activeRouteId ?: "").put("routeLeg",routeLeg)
            .put("draft",JSONArray(draftRoute.map {it.json()})).put("editingRoute",editingRouteId ?: "")
            .put("host",nmeaHost).put("port",nmeaPort).put("protocol",nmeaProtocol).put("serverPort",serverPort).put("positionSource",positionSource)
        writes.trySend(json.toString())
    }
}
