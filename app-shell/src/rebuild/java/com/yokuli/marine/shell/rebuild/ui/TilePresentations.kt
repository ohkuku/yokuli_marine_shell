package com.yokuli.marine.shell.rebuild.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.model.AlarmState
import com.yokuli.anchorwatch.domain.model.AlarmType
import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.anchorwatch.domain.vessel.VesselObservation
import com.yokuli.anchorwatch.data.sharing.SharingServerState
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.LocalReducedMotion
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.shell.rebuild.chart.MapSource
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.compose.*
import com.yokuli.shell.contract.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Extra tiles have their own stable identity but retain their app's existing launch contract. */
data class TilePreset(
    val key: String,
    val app: AppId,
    val title: AppPreferenceLabel,
    val description: AppPreferenceLabel,
    val mode: String,
    val defaultSize: MarineTileSize,
    val sizes: List<MarineTileSize>,
) {
    val entryId get() = LauncherEntryId("tile.$key")
    val launchToken get() = ShellApp(app).rootToken
}
fun tilePresets(): List<TilePreset> {
    val sizes = listOf(MarineTileSize.STANDARD_2X2, MarineTileSize.WIDE_4X2)
    fun preset(key: String, app: AppId, zh: String, en: String, detailZh: String, detailEn: String, mode: String, wide: Boolean = false) = TilePreset(key, app, AppPreferenceLabel(zh, en), AppPreferenceLabel(detailZh, detailEn), mode, if (wide) MarineTileSize.WIDE_4X2 else MarineTileSize.STANDARD_2X2, sizes)
    return listOf(
        preset("chart.cover", AppId.CHART, "海图封面", "chart cover", "整幅最近海图快照；点按回到海图", "Your latest chart snapshot, edge to edge. Tap for Chart.", "MAP", true),
        preset("chart.navigation", AppId.CHART, "当前导航", "active navigation", "当前航线、目标与距离；未导航时明确显示", "Active route, target and distance; shows when navigation is off.", "NAVIGATION", true),
        preset("instruments.speed", AppId.INSTRUMENTS, "航速", "speed", "真实对地航速；数据过期后显示缺测", "Actual speed over ground; stale readings become unavailable.", "SPEED"),
        preset("instruments.heading", AppId.INSTRUMENTS, "船首向", "heading", "真实船首向并注明真北参考", "Actual vessel heading with its true-north reference.", "HEADING"),
        preset("instruments.depth", AppId.INSTRUMENTS, "水深", "depth", "水深与已配置吃水下的龙骨余量", "Depth and under-keel clearance when draft is configured.", "DEPTH"),
        preset("instruments.wind", AppId.INSTRUMENTS, "风", "wind", "实际收到的真风与视风读数", "The true and apparent wind readings actually available.", "WIND"),
        preset("anchor.distance", AppId.ANCHOR, "锚位距离", "anchor distance", "当前船位到锚位的距离与警戒半径", "Distance from the current position to your anchor and its limit.", "DISTANCE"),
        preset("voyages.recording", AppId.VOYAGES, "当前记录", "current recording", "本次记录的状态、航程和沿途时刻", "Recording status, distance and moments from this voyage.", "RECORDING", true),
        preset("nmea.traffic", AppId.NMEA, "NMEA 收发", "NMEA traffic", "实际接收与写出计数，连接独立运行", "Actual received and written counts from independent connections.", "TRAFFIC", true),
    )
}

data class TileMode(val key: String, val title: AppPreferenceLabel)
fun tileModes(app: ShellApp): List<TileMode> {
    fun mode(key: String, zh: String, en: String) = TileMode(key, AppPreferenceLabel(zh, en))
    val specifics = when (app.app.name) {
        "CHART" -> listOf(mode("MAP", "海图封面", "chart cover"), mode("NAVIGATION", "当前导航", "active navigation"), mode("POSITION", "船位", "position"))
        "LIBRARY" -> listOf(mode("LAYERS", "海图图层", "chart layers"), mode("FOLDERS", "已授权文件夹", "authorised folders"))
        "PLACES" -> listOf(mode("PLACES", "收藏坐标", "saved coordinates"), mode("ROUTES", "航线", "routes"))
        "VOYAGES" -> listOf(mode("RECORDING", "当前记录", "current recording"), mode("LAST", "最近航行", "last voyage"))
        "ANCHOR" -> listOf(mode("WATCH", "值守状态", "watch state"), mode("DISTANCE", "锚位距离", "anchor distance"), mode("LIMIT", "警戒范围", "watch limit"))
        "INSTRUMENTS" -> listOf(mode("SPEED", "航速", "speed"), mode("HEADING", "船首向", "heading"), mode("DEPTH", "水深", "depth"), mode("WIND", "风", "wind"))
        "NMEA" -> listOf(mode("CONNECTIONS", "连接状态", "connections"), mode("TRAFFIC", "实际收发", "actual traffic"))
        "LOCAL_NMEA" -> listOf(mode("SERVICE", "服务状态", "service state"), mode("CLIENTS", "已连接客户端", "connected clients"), mode("TRAFFIC", "实际写出", "actually written"))
        "SETTINGS" -> listOf(mode("VESSEL", "我的船", "my boat"), mode("UNITS", "显示偏好", "display preferences"))
        else -> listOf(mode("COLLECTION", "已固定磁贴", "pinned tiles"))
    }
    return listOf(mode("AUTO", "轮换信息", "rotating information")) + specifics + mode("STATIC", "静态图标", "static icon")
}
fun tilePreferenceContributions(apps: List<ShellApp>): List<AppPreferenceContribution> = apps.map { app ->
    val modes = tileModes(app)
    AppPreferenceContribution(app.id, listOf(
        AppPreferenceDefinition.Choice(AppPreferenceKey("${app.id.value}.tile.mode"), modes.map { it.key }, AppPreferenceValue.Choice("AUTO"), AppPreferenceLabel("磁贴内容", "Tile content"), modes.associate { it.key to it.title }),
        AppPreferenceDefinition.Toggle(AppPreferenceKey("${app.id.value}.tile.animate"), AppPreferenceValue.Toggle(true), AppPreferenceLabel("允许信息轮换", "Allow information rotation")),
        AppPreferenceDefinition.Choice(AppPreferenceKey("${app.id.value}.tile.interval"), listOf("6", "10", "15"), AppPreferenceValue.Choice("6"), AppPreferenceLabel("轮换间隔", "Rotation interval"), listOf("6", "10", "15").associateWith { AppPreferenceLabel("$it 秒", "$it seconds") }),
    ))
}

@Composable fun tilePresentation(os: OsStore, app: ShellApp, animate: Boolean): LauncherEntryVisualContribution = buildTilePresentation(os, app, animate, null)
@Composable fun presetTilePresentation(os: OsStore, preset: TilePreset, animate: Boolean): LauncherEntryVisualContribution = buildTilePresentation(os, ShellApp(preset.app), animate, preset)

private data class TileFrame(val key: String, val headline: String, val detail: String, val eyebrow: String = "", val image: Bitmap? = null, val demo: Boolean = false)
@Composable private fun buildTilePresentation(os: OsStore, app: ShellApp, animate: Boolean, preset: TilePreset?): LauncherEntryVisualContribution {
    val preferences by os.shell.persistence.state.collectAsState()
    val data by os.hub.state.collectAsState()
    val state = os.marine?.vm?.ui?.collectAsState()?.value
    val connections = os.marine?.vm?.nmeaConnections?.collectAsState()?.value.orEmpty()
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val visible = animate && resumed
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(visible) { if (visible) while (true) { now = SystemClock.elapsedRealtime(); delay(1000) } }
    val readingNow = if (visible) now else SystemClock.elapsedRealtime()
    val values = preferences?.appPreferenceValues.orEmpty()
    val savedMode = values["${app.id.value}.tile.mode"]?.removePrefix("c:")
    val mode = preset?.mode ?: savedMode?.takeIf { value -> tileModes(app).any { it.key == value } } ?: "AUTO"
    val rotate = visible && !os.reduceMotion && !LocalReducedMotion.current && values["${app.id.value}.tile.animate"] != "b:0"
    val interval = values["${app.id.value}.tile.interval"]?.removePrefix("c:")?.toLongOrNull()?.coerceIn(6, 15) ?: 6L
    val title = preset?.title?.let { if (os.chinese) it.chinese else it.english } ?: os.title(app.app)
    val fix = data.fix(os.positionSource)?.takeIf { it.fresh(readingNow) }
    val unavailable = os.t("暂无有效数据", "no valid reading")
    fun reading(observation: VesselObservation<Double>?, format: (Double?) -> String): String = format(observation?.value?.takeIf { observation.freshness == VesselDataFreshness.FRESH && observation.receivedElapsedRealtime?.let { t -> readingNow - t in 0..10000 } == true })
    fun angle(observation: VesselObservation<Double>?): String = reading(observation) { it?.let { "${decimal(it)}°" } ?: "—" }
    val snapshotLabel = if (os.maps.snapshotCapturedAt > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(os.maps.snapshotCapturedAt)) else ""
    val snapshotCredit = when (os.maps.snapshotSource) {
        MapSource.Satellite -> "© Google"
        MapSource.Online -> if (com.yokuli.marine.shell.BuildConfig.GOOGLE_MAPS_CONFIGURED) "© Google" else "© OpenStreetMap contributors · Natural Earth"
        is MapSource.CustomLayer -> os.t("用户海图", "user chart")
        null -> ""
    }
    val frames = when (app.app.name) {
        "CHART" -> listOfNotNull(
            TileFrame("MAP", os.t("最近查看的海图", "last viewed chart"), listOf(snapshotLabel, snapshotCredit).filter { it.isNotBlank() }.joinToString(" · "), image = os.maps.snapshot?.takeUnless { it.isRecycled }, demo = os.maps.snapshotDemo),
            TileFrame("NAVIGATION", os.activeRoute?.name ?: os.t("未开始导航", "navigation is off"), os.nextPoint?.let { target -> fix?.let { os.formatDistance(distance(it.point, target)) } } ?: os.t("选择航线后明确开始", "select a route, then start"), os.t("当前导航", "active navigation")),
            TileFrame("POSITION", fix?.let { os.formatSpeed(it.freshSpeed(readingNow)) } ?: "—", fix?.let { os.formatCoordinates(it.point) } ?: os.t("船位不可用", "position unavailable"), os.t("船位 · 对地航速", "position · speed over ground")),
        )
        "LIBRARY" -> listOf(TileFrame("LAYERS", os.t("${os.library.layers.size} 个图层", "${os.library.layers.size} layers"), os.maps.sourceName(os.chinese), os.t("当前地图来源", "current map source")), TileFrame("FOLDERS", os.t("${os.library.folders.size} 个文件夹", "${os.library.folders.size} folders"), os.t("${os.library.files.size} 张已登记海图", "${os.library.files.size} indexed charts")))
        "PLACES" -> listOf(TileFrame("PLACES", os.t("${os.allPlaces.size} 个坐标", "${os.allPlaces.size} coordinates"), os.allPlaces.lastOrNull()?.name ?: os.t("标记值得再去的地方", "save somewhere worth returning to")), TileFrame("ROUTES", os.t("${os.routes.size} 条航线", "${os.routes.size} routes"), os.activeRoute?.name ?: os.t("选择后才开始导航", "navigation starts when you choose")))
        "VOYAGES" -> {
            val trip = state?.activeTrip
            val previous = state?.tripSessions?.firstOrNull { !it.active }
            listOf(TileFrame("RECORDING", trip?.let { os.formatDistance(it.distanceMeters) } ?: os.t("未在记录", "recording is off"), trip?.let { it.name + " · " + os.t("${it.waypointCount} 个时刻", "${it.waypointCount} moments") } ?: os.t("打开日志，记录下一次出发", "open Logbook for your next departure"), if (trip == null) os.t("当前记录", "current recording") else if (trip.paused) os.t("记录已暂停", "recording paused") else os.t("正在记录", "recording")), TileFrame("LAST", previous?.name ?: os.t("还没有完成的航行", "no completed voyages"), previous?.let { os.formatDistance(it.distanceMeters) + " · " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it.startedAt)) } ?: os.t("航迹与时刻会留在这里", "tracks and moments will be kept here"), os.t("最近航行", "last voyage")))
        }
        "ANCHOR" -> {
            val watch = state?.active
            val delta = watch?.let { anchor -> fix?.let { distance(it.point, GeoPoint(anchor.anchorLatitude, anchor.anchorLongitude)) } }
            listOf(TileFrame("WATCH", if (watch == null) os.t("未在值守", "watch is off") else if (watch.paused) os.t("值守已暂停", "watch paused") else if (state?.alarmSnapshot?.state == AlarmState.ALARM && state.alarmSnapshot.type != AlarmType.ALARM_TEST) os.t("警报触发", "alarm active") else if (state?.alarmSnapshot?.state == AlarmState.WARNING) os.t("锚警预警", "anchor warning") else os.t("锚警值守中", "anchor watch active"), if (watch == null) os.t("为下一次锚泊做好准备", "prepare your next anchorage") else os.t("点按查看完整值守状态", "tap for the complete watch status")), TileFrame("DISTANCE", os.formatDistance(delta), if (watch == null) os.t("未开始锚警", "no anchor watch") else os.t("警戒半径 ", "alarm radius ") + os.formatDistance(watch.alarmRadiusMeters), os.t("距离锚位", "distance to anchor")), TileFrame("LIMIT", os.formatDistance(watch?.alarmRadiusMeters), watch?.let { if (it.paused) os.t("已暂停值守", "watch paused") else os.t("当前值守范围", "current watch limit") } ?: os.t("未开始锚警", "no anchor watch"), os.t("警戒半径", "alarm radius")))
        }
        "INSTRUMENTS" -> {
            val v = state?.vesselData
            listOf(TileFrame("SPEED", reading(v?.sogKnots, os::formatSpeed), os.t("对地航速", "speed over ground")), TileFrame("HEADING", angle(v?.headingTrueDegrees), os.t("真船首向", "true heading")), TileFrame("DEPTH", reading(v?.depthMeters, os::formatDepth), os.t("龙骨下余量 ", "under-keel ") + reading(v?.derived?.underKeelClearanceMeters, os::formatDepth), os.t("水深", "depth")), TileFrame("WIND", reading(v?.trueWind?.speedKnots, os::formatSpeed), os.t("视风 ", "apparent ") + reading(v?.apparentWind?.speedKnots, os::formatSpeed), os.t("真风", "true wind")))
        }
        "NMEA" -> {
            val online = connections.count { it.state in setOf(NmeaConnectionState.CONNECTED, NmeaConnectionState.CONNECTED_NO_DATA, NmeaConnectionState.CONNECTED_NO_FIX, NmeaConnectionState.STALE) }
            listOf(TileFrame("CONNECTIONS", os.t("$online 条已连接", "$online connected"), os.t("${connections.size} 条已保存连接", "${connections.size} saved connections")), TileFrame("TRAFFIC", "↓ ${connections.sumOf { it.diagnostics.validSentences }}", "↑ ${connections.sumOf { it.writtenSentences }} · " + os.t("实际写出", "actually written"), os.t("有效接收", "valid received")))
        }
        "LOCAL_NMEA" -> {
            val server = state?.nmeaSharing
            listOf(TileFrame("SERVICE", when (server?.state) { SharingServerState.RUNNING -> os.t("正在监听", "listening"); SharingServerState.STARTING -> os.t("正在启动", "starting"); SharingServerState.ERROR -> os.t("服务受阻", "service unavailable"); else -> os.t("已停止", "stopped") }, os.t("本机数据服务", "local data service")), TileFrame("CLIENTS", os.t("${server?.clientCount ?: 0} 个客户端", "${server?.clientCount ?: 0} clients"), os.t("当前实际连接", "currently connected")), TileFrame("TRAFFIC", (server?.sentSentences ?: 0).toString(), os.t("实际写出的 NMEA 语句", "NMEA sentences actually written")))
        }
        "SETTINGS" -> listOf(TileFrame("VESSEL", state?.vesselSettings?.vesselName?.ifBlank { null } ?: os.t("我的船", "my boat"), os.t("船舶资料与系统偏好", "boat details & system preferences")), TileFrame("UNITS", if (os.measurementUnits == MeasurementUnitSystem.NAUTICAL) os.t("海里 · 节", "nm · kn") else os.t("公里 · 公里/小时", "km · km/h"), os.coordinateFormat + " · " + if (os.chinese) "简体中文" else "English"))
        else -> {
            val shellState by os.shell.engine.state.collectAsState()
            listOf(TileFrame("COLLECTION", os.t("${shellState.start.document.placements.size} 块磁贴", "${shellState.start.document.placements.size} tiles"), os.t("找到适合你的开始屏幕", "make Start your own")))
        }
    }
    val demo = (state?.settings?.demoMode == true || os.positionSource == "demo") && app.app.name in setOf("CHART", "INSTRUMENTS", "ANCHOR")
    val selectedFrames = if (mode == "STATIC") listOf(TileFrame("STATIC", title, "")) else if (mode == "AUTO") frames else frames.filter { it.key == mode }.ifEmpty { listOf(TileFrame(mode, unavailable, "")) }
    val selected = selectedFrames.map {
        val frameIsDemo=if(it.key=="MAP" && it.image!=null)it.demo else demo
        if(frameIsDemo)it.copy(eyebrow=os.t("演示 · ", "DEMO · ")+it.eyebrow,demo=true)else it
    }
    val cover = app.app == AppId.CHART && mode in setOf("AUTO", "MAP")
    val description = selected.first().let { it.eyebrow.ifBlank { it.headline } + " · " + it.detail }
    return LauncherEntryVisualContribution(preset?.entryId ?: app.entry, title,
        when (app.app.name) { "CHART", "LIBRARY", "VOYAGES" -> 'H'; "PLACES" -> 'W'; "SETTINGS" -> 'S'; "ANCHOR" -> 'M'; "INSTRUMENTS" -> 'Y'; "TILES" -> 'C'; else -> app.app.en.first().uppercaseChar() },
        title, description, LauncherIconRenderer { tint, modifier -> ShellAppIcon(app, tint, modifier) },
        (preset?.sizes ?: app.sizes).associateWith { size -> LauncherTileRenderer { context -> TileFace(os, app, title, selected, size, context, cover, rotate, interval) } }, fullBleed = cover)
}

@Composable private fun TileFace(os: OsStore, app: ShellApp, title: String, frames: List<TileFrame>, size: MarineTileSize, context: LauncherTileRenderContext, cover: Boolean, rotate: Boolean, interval: Long) {
    var page by remember(app.id, title) { mutableIntStateOf(0) }
    val keys = frames.map { it.key }
    val active = rotate && context.liveContentEnabled && size != MarineTileSize.ICON_1X1 && frames.size > 1
    LaunchedEffect(active, interval, keys) {
        if (active) {
            delay(350L * (app.id.value.hashCode().and(7)))
            while (true) { delay(interval * 1000); page = (page + 1) % frames.size }
        }
    }
    val currentFrame = frames[page.mod(frames.size)]
    var heldFrame by remember(app.id, title, keys) { mutableStateOf(currentFrame) }
    SideEffect { if (context.liveContentEnabled) heldFrame = currentFrame }
    val frame = if (context.liveContentEnabled) currentFrame else heldFrame
    Box(context.modifier.fillMaxSize().clipToBounds()) {
        if (size == MarineTileSize.ICON_1X1) ShellAppIcon(app, context.contentColor, Modifier.size(44.dp).align(Alignment.Center))
        else if (!active) TileFrameContent(os, app, title, frame, size, context.contentColor, cover)
        else AnimatedContent(frame.key, modifier = Modifier.fillMaxSize(), transitionSpec = {
            (slideInVertically(tween(420)) { it } + fadeIn(tween(240))) togetherWith (slideOutVertically(tween(420)) { -it } + fadeOut(tween(200)))
        }, label = "live-tile") { key -> TileFrameContent(os, app, title, frames.firstOrNull { it.key == key } ?: frame, size, context.contentColor, cover) }
    }
}

@Composable private fun TileFrameContent(os: OsStore, app: ShellApp, title: String, frame: TileFrame, size: MarineTileSize, color: Color, cover: Boolean) {
    if (frame.key == "MAP" && frame.image != null) {
        Box(Modifier.fillMaxSize()) {
            Image(frame.image.asImageBitmap(), os.t("最近海图快照", "last chart snapshot"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                WpText(title, 15, color = Color.White, maxLines = 1)
                WpText((if (frame.demo) os.t("演示 · ", "DEMO · ") else "") + os.t("海图快照", "chart snapshot") + " · " + frame.detail, 9, color = Color.White.copy(alpha = .9f), maxLines = 2)
            }
        }
    } else Column(Modifier.fillMaxSize().then(if (cover) Modifier.padding(YokuliMetrics.TileContentInset) else Modifier), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ShellAppIcon(app, color, Modifier.size(23.dp)); WpText(title, 12, color = color, maxLines = 1)
        }
        if (frame.key == "STATIC") Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { ShellAppIcon(app, color, Modifier.size(48.dp)) }
        else Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (frame.eyebrow.isNotBlank()) WpText(frame.eyebrow, 11, color = color.copy(alpha = .82f), maxLines = 1)
            WpText(if (frame.key == "MAP") os.t("打开海图更新封面", "open Chart for a cover") else frame.headline, if (size == MarineTileSize.WIDE_4X2) 31 else 26, color = color, weight = FontWeight.Light, maxLines = 2)
        }
        if (frame.detail.isNotBlank()) WpText(frame.detail, 11, color = color.copy(alpha = .86f), maxLines = if (size == MarineTileSize.WIDE_4X2) 2 else 1)
    }
}
