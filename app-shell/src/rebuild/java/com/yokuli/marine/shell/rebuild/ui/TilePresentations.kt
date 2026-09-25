package com.yokuli.marine.shell.rebuild.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.runtime.contract.ais.*
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.model.AlarmState
import com.yokuli.anchorwatch.domain.model.AlarmType
import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.anchorwatch.domain.vessel.VesselObservation
import com.yokuli.anchorwatch.data.sharing.SharingServerState
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.LocalReducedMotion
import com.yokuli.marine.core.design.LocalWpTextScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.shell.rebuild.chart.MapSource
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.data.Reading
import com.yokuli.shell.compose.*
import com.yokuli.shell.contract.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.yokuli.anchorwatch.MainUiState
import com.yokuli.marine.shell.rebuild.data.VesselData
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

/** 只处理本地定义的英文模式标签，不改用户命名的航线、船名或收藏坐标。 */
private fun tileLabelCase(text: String): String = text.split(' ').joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
}
private val TileNumericHeadline = Regex("^([−+\\-]?\\d+(?:[.,]\\d+)?)(.*)$")

/** 中文：旧版独立样式入口仅用于迁移；新磁贴使用应用的唯一入口及样式偏好。 */
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
    fun preset(key: String, app: AppId, zh: String, en: String, detailZh: String, detailEn: String, mode: String, wide: Boolean = false) = TilePreset(key, app, AppPreferenceLabel(zh, tileLabelCase(en)), AppPreferenceLabel(detailZh, detailEn), mode, if (wide) MarineTileSize.WIDE_4X2 else MarineTileSize.STANDARD_2X2, sizes)
    return listOf(
        preset("chart.cover", AppId.CHART, "海图封面", "chart cover", "整幅最近海图快照；点按回到海图", "Your latest chart snapshot, edge to edge. Tap for Chart.", "MAP", true),
        preset("chart.navigation", AppId.CHART, "当前导航", "active navigation", "当前航线、目标与距离；未导航时明确显示", "Active route, target and distance; shows when navigation is off.", "NAVIGATION", true),
        preset("instruments.speed", AppId.INSTRUMENTS, "航速", "speed", "真实对地航速与观测时间", "Actual speed over ground with the time observed.", "SPEED"),
        preset("instruments.heading", AppId.INSTRUMENTS, "船首向", "heading", "真实船首向并注明真北参考", "Actual vessel heading with its true-north reference.", "HEADING"),
        preset("instruments.depth", AppId.INSTRUMENTS, "水深", "depth", "水深与已配置吃水下的龙骨余量", "Depth and under-keel clearance when draft is configured.", "DEPTH"),
        preset("instruments.wind", AppId.INSTRUMENTS, "风", "wind", "实际收到的真风与视风读数", "The true and apparent wind readings actually available.", "WIND"),
        preset("anchor.distance", AppId.ANCHOR, "锚位距离", "anchor distance", "当前船位到锚位的距离与警戒半径", "Distance from the current position to your anchor and its limit.", "DISTANCE"),
        preset("voyages.recording", AppId.VOYAGES, "当前记录", "current recording", "本次记录的状态、航程和沿途时刻", "Recording status, distance and moments from this voyage.", "RECORDING", true),
        preset("ais.traffic", AppId.AIS, "周围交通", "nearby traffic", "接收中的真实目标、警戒状态与最近目标，沿用全局单位", "Received traffic, monitoring status and nearest target in your chosen units.", "TRAFFIC", true),
        preset("nmea.traffic", AppId.NMEA, "NMEA 收发", "NMEA traffic", "实际接收与写出计数，连接独立运行", "Actual received and written counts from independent connections.", "TRAFFIC", true),
    )
}

data class TileMode(val key: String, val title: AppPreferenceLabel)
fun tileModes(app: ShellApp): List<TileMode> {
    fun mode(key: String, zh: String, en: String) = TileMode(key, AppPreferenceLabel(zh, tileLabelCase(en)))
    val specifics = when (app.app.name) {
        "CHART" -> listOf(mode("MAP", "海图封面", "chart cover"), mode("NAVIGATION", "当前导航", "active navigation"), mode("POSITION", "船位", "position"))
        "LIBRARY" -> listOf(mode("LAYERS", "海图图层", "chart layers"), mode("FOLDERS", "已授权文件夹", "authorised folders"))
        "PLACES" -> listOf(mode("PLACES", "收藏坐标", "saved coordinates"), mode("ROUTES", "航线", "routes"))
        "VOYAGES" -> listOf(mode("RECORDING", "当前记录", "current recording"), mode("LAST", "最近航行", "last voyage"))
        "ANCHOR" -> listOf(mode("WATCH", "值守状态", "watch state"), mode("DISTANCE", "锚位距离", "anchor distance"), mode("LIMIT", "警戒范围", "watch limit"))
        "INSTRUMENTS" -> listOf(mode("SPEED", "航速", "speed"), mode("HEADING", "船首向", "heading"), mode("DEPTH", "水深", "depth"), mode("WIND", "风", "wind"))
        "DATA_CENTER" -> listOf(mode("SOURCE", "船位来源", "position source"), mode("READINGS", "正在采用的读数", "selected readings"))
        "AIS" -> listOf(mode("TRAFFIC", "周围交通", "nearby traffic"), mode("WATCH", "交通警戒", "traffic watch"), mode("NEAREST", "最近目标", "nearest target"))
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

@Composable fun tilePresentation(os: OsStore, app: ShellApp, animate: Boolean, modeOverride: String? = null, rotateOverride: Boolean? = null, intervalOverride: Long? = null): LauncherEntryVisualContribution = buildTilePresentation(os, app, animate, null, modeOverride, rotateOverride, intervalOverride)
@Composable fun presetTilePresentation(os: OsStore, preset: TilePreset, animate: Boolean): LauncherEntryVisualContribution = buildTilePresentation(os, ShellApp(preset.app), animate, preset, null, null, null)

/** 中文：磁贴帧只缓存展示形态；值、来源时效、趋势直接订阅系统的同一份观测。 */
internal data class TileFrame(val key: String, val headline: String, val detail: String, val eyebrow: String = "", val image: Bitmap? = null, val demo: Boolean = false, val bearing: Double? = null, val progress: Float? = null, val live: Boolean = true, val priorityLine:String="", val history:InstrumentHistoryFrame?=null, val historyCaption:String="")
/** 目录声明只依赖应用及用户偏好。高频船舶数据不能从返回值 Composable 扩散到整个 Shell。 */
@Composable private fun buildTilePresentation(os: OsStore, app: ShellApp, animate: Boolean, preset: TilePreset?, modeOverride: String?, rotateOverride: Boolean?, intervalOverride: Long?): LauncherEntryVisualContribution {
    val preferences=if(modeOverride==null)os.shell.persistence.state.value else null
    val savedMode=preferences?.appPreferenceValues?.get("${app.id.value}.tile.mode")?.removePrefix("c:")
    val mode=modeOverride ?: preset?.mode ?: savedMode?.takeIf {value->tileModes(app).any {it.key==value}} ?: "AUTO"
    val title=preset?.title?.let {if(os.chinese)it.chinese else it.english} ?: os.title(app.app)
    val description=preset?.description?.let {if(os.chinese)it.chinese else it.english}
        ?: if(app.app==AppId.AIS)os.t("周围船舶","surrounding vessels") else title
    val cover=app.app==AppId.CHART&&mode in setOf("AUTO","MAP")
    return LauncherEntryVisualContribution(preset?.entryId ?: app.entry,title,app.app.chineseIndex,title,description,
        LauncherIconRenderer {tint,modifier->ShellAppIcon(app,tint,modifier)},
        (preset?.sizes ?: app.sizes).associateWith {size->LauncherTileRenderer {context->
            // 只有真正进入 Compose 树的磁贴才订阅数据；应用内、目录图标和未固定样式不会创建监听。
            LiveAppTile(os,app,animate,preset,modeOverride,rotateOverride,intervalOverride,size,context)
        }},fullBleed=cover)
}

/** 中文：兼容实例仍复用同一真实磁贴，不再读取或覆写另一个实例的模式。 */
@Composable internal fun LegacyInstanceTile(os:OsStore,app:ShellApp,active:Boolean,mode:String,rotate:Boolean,interval:Long,size:MarineTileSize,context:LauncherTileRenderContext,preset:TilePreset?=null) =
    LiveAppTile(os,app,active,preset,mode,rotate,interval,size,context)

/** 旧 App 摘要只观察本 App 使用的字段；不因传感器列表、调平或无关网络包重组。 */
private fun legacyTileFields(state:MainUiState,app:AppId,mode:String):List<Any?> = when(app) {
    AppId.CHART->listOf(state.settings.demoMode)
    AppId.ANCHOR->listOf(state.active,state.alarmSnapshot,state.settings.demoMode)
    AppId.INSTRUMENTS->with(state.vesselData) {when(mode) {
        "SPEED"->listOf(sogKnots);"HEADING"->listOf(headingTrueDegrees)
        "DEPTH"->listOf(depthMeters,derived.underKeelClearanceMeters)
        "WIND"->listOf(trueWind.speedKnots,apparentWind.speedKnots)
        else->listOf(sogKnots,headingTrueDegrees,depthMeters,derived.underKeelClearanceMeters,trueWind.speedKnots,apparentWind.speedKnots)
    }+state.settings.demoMode}
    AppId.VOYAGES->listOf(state.activeTrip,state.tripSessions.firstOrNull {!it.active})
    AppId.LOCAL_NMEA->listOf(state.nmeaSharing.state,state.nmeaSharing.clientCount,state.nmeaSharing.sentSentences)
    AppId.SETTINGS->listOf(state.vesselSettings.vesselName)
    else->emptyList()
}

@Composable private fun LiveAppTile(os: OsStore, app: ShellApp, animate: Boolean, preset: TilePreset?, modeOverride: String?, rotateOverride: Boolean?, intervalOverride: Long?,size:MarineTileSize,context:LauncherTileRenderContext) {
    val visible=tilePresentationActive(animate&&context.liveContentEnabled)
    val preferenceFlow=remember(os.shell,app.id) {os.shell.persistence.state.map {state->state?.appPreferenceValues.orEmpty().filterKeys {it.startsWith("${app.id.value}.tile.")}}.distinctUntilChanged()}
    val preferenceValues=activeTileValue(preferenceFlow,os.shell.persistence.state.value?.appPreferenceValues.orEmpty(),visible&&modeOverride==null)
    val values = preferenceValues
    val savedMode = values["${app.id.value}.tile.mode"]?.removePrefix("c:")
    val mode = modeOverride ?: preset?.mode ?: savedMode?.takeIf { value -> tileModes(app).any { it.key == value } } ?: "AUTO"
    val rotate = visible && !os.reduceMotion && !LocalReducedMotion.current && (rotateOverride ?: (values["${app.id.value}.tile.animate"] != "b:0"))
    val interval = (intervalOverride ?: values["${app.id.value}.tile.interval"]?.removePrefix("c:")?.toLongOrNull() ?: 6L).coerceIn(6, 15)
    val title = preset?.title?.let { if (os.chinese) it.chinese else it.english } ?: os.title(app.app)
    if(mode=="STATIC"||size==MarineTileSize.ICON_1X1) {
        TileFace(os,app,title,listOf(TileFrame("STATIC",title,"")),size,context,false,false,interval)
        return
    }
    val dataFlow=remember(os.hub,app.app,os.positionSource) {
        if(app.app in setOf(AppId.CHART,AppId.DATA_CENTER,AppId.ANCHOR))os.hub.state.map {value->
            if(app.app==AppId.DATA_CENTER)VesselData(phone=value.phone,nmea=value.nmea,demo=value.demo,readings=value.readings)
            else VesselData(phone=value.phone,nmea=value.nmea,demo=value.demo,readings=if(app.app==AppId.CHART)value.readings.filterKeys {it=="sog"}else emptyMap())
        }.distinctUntilChanged() else null
    }
    val data=activeTileValue(dataFlow,if(dataFlow!=null)os.hub.state.value else VesselData(),visible)
    val ownerState=os.marine?.services?.state
    val stateFlow=remember(ownerState,app.app,mode) {ownerState?.takeIf {app.app in setOf(AppId.CHART,AppId.ANCHOR,AppId.INSTRUMENTS,AppId.VOYAGES,AppId.LOCAL_NMEA,AppId.SETTINGS)}
        ?.distinctUntilChanged {old,new->legacyTileFields(old,app.app,mode)==legacyTileFields(new,app.app,mode)}}
    val state=activeTileValue(stateFlow,ownerState?.value,visible)
    val connectionSource=os.marine?.services?.network?.connections
    val connectionFlow=remember(connectionSource,app.app) {connectionSource?.takeIf {app.app==AppId.NMEA}}
    val connections=activeTileValue(connectionFlow,if(app.app==AppId.NMEA)connectionSource?.value.orEmpty()else emptyList(),visible)
    val trafficSource=os.marine?.system?.ais?.snapshot
    val traffic=activeTileValue(if(app.app==AppId.AIS)trafficSource else null,if(app.app==AppId.AIS)trafficSource?.value else null,visible)
    val readingNow=tileElapsed(visible)
    val lastFix = data.fix(os.positionSource)
    val fix = lastFix?.takeIf { it.fresh(readingNow) }
    val unavailable = os.t("等待读数", "waiting for a reading")
    fun reading(observation: VesselObservation<Double>?, format: (Double?) -> String): String = format(observation?.displayNumber())
    fun stamp(observation: VesselObservation<*>?): String = observation?.let { observationStatus(os, it, readingNow) } ?: unavailable
    val miniHistories=if(app.app==AppId.INSTRUMENTS)listOf("SPEED" to "sog","HEADING" to "heading","DEPTH" to "depth","WIND" to "tws")
        .filter {mode=="AUTO"||it.first==mode}.associate {it.second to rememberTileHistory(os,it.second,visible,readingNow)} else emptyMap()
    val historyCaptions=remember(miniHistories,os.chinese) {miniHistories.mapValues {(_,frame)->
        if(frame.points.isEmpty())""else {
            val utc=frame.points.lastOrNull()?.reading?.observedUtcMillis ?: (System.currentTimeMillis()-(SystemClock.elapsedRealtime()-frame.end))
            os.t("近 5 分钟 · 截至 ","5 min · through ")+DateFormat.getTimeInstance(DateFormat.SHORT,if(os.chinese)Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH).format(Date(utc))
        }
    }}
    fun historyCaption(metric:String)=historyCaptions[metric].orEmpty()
    val displayLocale = if (os.chinese) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
    val snapshotLabel = if (os.maps.snapshotCapturedAt > 0) DateFormat.getTimeInstance(DateFormat.SHORT, displayLocale).format(Date(os.maps.snapshotCapturedAt)) else ""
    val snapshotCredit = when (os.maps.snapshotSource) {
        MapSource.Satellite -> "© Google"
        MapSource.Offline -> "Natural Earth"
        is MapSource.CustomLayer -> os.t("用户海图 · Natural Earth", "user chart · Natural Earth")
        null -> ""
    }
    val frames = when (app.app.name) {
        "CHART" -> listOfNotNull(
            TileFrame("MAP", lastFix?.let { (if (os.positionSource == "demo" || state?.settings?.demoMode == true) os.t("演示 · ", "DEMO · ") else "") + os.formatSpeed(data.readings["sog"]?.value) + " · " + os.formatCoordinates(it.point) } ?: os.t("等待船位", "waiting for position"), listOf(snapshotLabel, snapshotCredit, lastFix?.takeUnless { it.fresh(readingNow) }?.let { os.t("船位 ", "position ") + readingAge(os, it.elapsed, readingNow) }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), image = os.maps.snapshot?.takeUnless { it.isRecycled }, demo = os.maps.snapshotDemo),
            TileFrame("NAVIGATION", os.activeRoute?.name ?: os.t("未开始导航", "navigation is off"), os.nextPoint?.let { target -> fix?.let { os.formatDistance(distance(it.point, target)) } } ?: os.t("选择航线后明确开始", "select a route, then start"), os.t("当前导航", "active navigation")),
            TileFrame("POSITION", os.formatSpeed(data.readings["sog"]?.value), lastFix?.let { os.formatCoordinates(it.point) + " · " + readingAge(os, it.elapsed, readingNow) } ?: os.t("等待船位", "waiting for position"), os.t("船位 · 对地航速", "position · speed over ground"), live = data.readings["sog"]?.fresh(readingNow) == true),
        )
        "LIBRARY" -> listOf(TileFrame("LAYERS", os.t("${os.library.layers.size} 个图层", "${os.library.layers.size} layers"), os.maps.sourceName(os.chinese), os.t("当前地图来源", "current map source")), TileFrame("FOLDERS", os.t("${os.library.folders.size} 个文件夹", "${os.library.folders.size} folders"), os.t("${os.library.files.size} 张已登记海图", "${os.library.files.size} indexed charts")))
        "PLACES" -> listOf(TileFrame("PLACES", os.t("${os.allPlaces.size} 个坐标", "${os.allPlaces.size} coordinates"), os.allPlaces.lastOrNull()?.name ?: os.t("标记值得再去的地方", "save somewhere worth returning to")), TileFrame("ROUTES", os.t("${os.routes.size} 条航线", "${os.routes.size} routes"), os.activeRoute?.name ?: os.t("选择后才开始导航", "navigation starts when you choose")))
        "VOYAGES" -> {
            val trip = state?.activeTrip
            val previous = state?.tripSessions?.firstOrNull { !it.active }
            listOf(TileFrame("RECORDING", trip?.let { os.formatDistance(it.distanceMeters) } ?: os.t("未在记录", "recording is off"), trip?.let { it.name + " · " + os.t("${it.waypointCount} 个时刻", "${it.waypointCount} moments") } ?: os.t("打开日志，记录下一次出发", "open Logbook for your next departure"), if (trip == null) os.t("当前记录", "current recording") else if (trip.paused) os.t("记录已暂停", "recording paused") else os.t("正在记录", "recording")), TileFrame("LAST", previous?.name ?: os.t("还没有完成的航行", "no completed voyages"), previous?.let { os.formatDistance(it.distanceMeters) + " · " + DateFormat.getDateInstance(DateFormat.SHORT, displayLocale).format(Date(it.startedAt)) } ?: os.t("航迹与时刻会留在这里", "tracks and moments will be kept here"), os.t("最近航行", "last voyage")))
        }
        "ANCHOR" -> {
            val watch = state?.active
            val delta = watch?.let { anchor -> fix?.let { distance(it.point, GeoPoint(anchor.anchorLatitude, anchor.anchorLongitude)) } }
            listOf(TileFrame("WATCH", if (watch == null) os.t("未在值守", "watch is off") else if (watch.paused) os.t("值守已暂停", "watch paused") else if (state?.alarmSnapshot?.state == AlarmState.ALARM && state.alarmSnapshot.type != AlarmType.ALARM_TEST) os.t("警报触发", "alarm active") else if (state?.alarmSnapshot?.state == AlarmState.WARNING) os.t("锚警预警", "anchor warning") else os.t("锚警值守中", "anchor watch active"), if (watch == null) os.t("为下一次锚泊做好准备", "prepare your next anchorage") else os.t("点按查看完整值守状态", "tap for the complete watch status")), TileFrame("DISTANCE", os.formatLength(delta), if (watch == null) os.t("未开始锚警", "no anchor watch") else os.t("警戒半径 ", "alarm radius ") + os.formatLength(watch.alarmRadiusMeters), os.t("距离锚位", "distance to anchor"), progress = if (delta != null && watch != null && watch.alarmRadiusMeters > 0) (delta / watch.alarmRadiusMeters).toFloat() else null), TileFrame("LIMIT", os.formatLength(watch?.alarmRadiusMeters), watch?.let { if (it.paused) os.t("已暂停值守", "watch paused") else os.t("当前值守范围", "current watch limit") } ?: os.t("未开始锚警", "no anchor watch"), os.t("警戒半径", "alarm radius")))
        }
        "INSTRUMENTS" -> {
            val v = state?.vesselData
            listOf(
                TileFrame("SPEED", reading(v?.sogKnots, os::formatSpeed), stamp(v?.sogKnots), os.t("对地航速", "speed over ground"), history = miniHistories["sog"], historyCaption=historyCaption("sog"), live = v?.sogKnots?.displayIsLive() == true),
                TileFrame("HEADING", os.formatBearing(v?.headingTrueDegrees?.liveNumber()), stamp(v?.headingTrueDegrees), os.t("船首向 · 真北", "heading · true"), history=miniHistories["heading"],historyCaption=historyCaption("heading"),bearing=if(miniHistories["heading"]==null)v?.headingTrueDegrees?.liveNumber()else null, live = v?.headingTrueDegrees?.displayIsLive() == true),
                TileFrame("DEPTH", reading(v?.depthMeters, os::formatDepth), stamp(v?.depthMeters), os.t("水深 · 余量 ", "depth · UKC ") + reading(v?.derived?.underKeelClearanceMeters, os::formatDepth), history = miniHistories["depth"], historyCaption=historyCaption("depth"), live = v?.depthMeters?.displayIsLive() == true),
                TileFrame("WIND", reading(v?.trueWind?.speedKnots, os::formatSpeed), stamp(v?.trueWind?.speedKnots), os.t("真风 · 视风 ", "true wind · apparent ") + reading(v?.apparentWind?.speedKnots, os::formatSpeed), history = miniHistories["tws"], historyCaption=historyCaption("tws"), live = v?.trueWind?.speedKnots?.displayIsLive() == true),
            )
        }
        "DATA_CENTER" -> {
            // 只显示系统已采用的同一份快照；磁贴不创建来源配置，也不驱动传感器。
            val newestReading = data.readings.values.maxOfOrNull { it.elapsed }
            val sourceName = lastFix?.source ?: when (os.positionSource) {
                "phone" -> os.t("手机定位", "phone location")
                "nmea" -> os.t("船载定位", "boat position")
                "demo" -> os.t("演示船位", "demo position")
                else -> os.t("船位已关闭", "position is off")
            }
            listOf(
                TileFrame("SOURCE", sourceName,
                    lastFix?.let { readingAge(os, it.elapsed, maxOf(readingNow, it.elapsed)) }
                        ?: if (os.positionSource == "none") os.t("打开数据中心选择来源", "choose a source in Data Center")
                        else os.t("等待首次船位", "waiting for the first position"),
                    os.t("当前船位来自", "position comes from"), live = fix != null),
                TileFrame("READINGS", os.t("${data.readings.size} 项读数", "${data.readings.size} readings"),
                    newestReading?.let { os.t("最近更新 · ", "last updated · ") + readingAge(os, it, maxOf(readingNow, it)) }
                        ?: os.t("收到读数后显示真实来源与时间", "readings show their actual source and time"),
                    os.t("系统当前采用", "selected by the system"), live = data.readings.values.any { it.fresh(readingNow) }),
            )
        }
        "NMEA" -> {
            val online = connections.count { it.state in setOf(NmeaConnectionState.CONNECTED, NmeaConnectionState.CONNECTED_NO_DATA, NmeaConnectionState.CONNECTED_NO_FIX, NmeaConnectionState.STALE) }
            listOf(TileFrame("CONNECTIONS", os.t("$online 条已连接", "$online connected"), os.t("${connections.size} 条已保存连接", "${connections.size} saved connections")), TileFrame("TRAFFIC", "↓ ${connections.sumOf { it.diagnostics.validSentences }}", "↑ ${connections.sumOf { it.writtenSentences }} · " + os.t("实际写出", "actually written"), os.t("有效接收", "valid received")))
        }
        "AIS" -> {
            val snapshot=traffic ?: TrafficSnapshot()
            val latest=snapshot.inputs.mapNotNull {it.lastAisElapsed}.maxOrNull()
            val current=snapshot.targets.count {it.state==AisTargetState.CURRENT&&!it.cached}
            val concern=snapshot.targets.count {it.riskLevel!=AisRiskLevel.NONE}
            val nearest=snapshot.targets.filter {it.relative.distanceMeters!=null&&it.state==AisTargetState.CURRENT}.minByOrNull {it.relative.distanceMeters!!}
            val input=aisInputSummary(os,snapshot)
            listOf(
                TileFrame("TRAFFIC",if(latest==null)os.t("等待 AIS", "waiting for AIS")else os.t("$current 个当前目标", "$current current targets"),
                    input+ (latest?.let {" · "+readingAge(os,it,readingNow)} ?: ""),os.t("周围交通", "nearby traffic"),live=current>0),
                TileFrame("WATCH",if(concern>0)os.t("$concern 个需关注", "$concern need attention")else if(snapshot.preferences.monitoringEnabled)os.t("交通警戒已启用", "traffic watch enabled")else os.t("交通警戒关闭", "traffic watch off"),
                    if(snapshot.preferences.monitoringEnabled&&(snapshot.backgroundLimitations.isNotEmpty()||snapshot.inputs.none {it.state==AisInputState.ONLINE}))os.t("运行条件受限 · 点按检查", "monitoring limited · tap to inspect")else os.t("AIS 不代表完整周围交通", "AIS does not show all surrounding traffic")),
                TileFrame("NEAREST",nearest?.relative?.distanceMeters?.let {os.formatDistance(it)} ?: os.t("等待有效相对位置", "waiting for relative position"),
                    nearest?.let {it.displayName+" · "+aisState(os,it)} ?: input,os.t("最近已观测目标", "nearest observed target"),bearing=nearest?.relative?.bearingDegrees,live=nearest!=null),
            )
        }
        "LOCAL_NMEA" -> {
            val server = state?.nmeaSharing
            listOf(TileFrame("SERVICE", when (server?.state) { SharingServerState.RUNNING -> os.t("正在监听", "listening"); SharingServerState.STARTING -> os.t("正在启动", "starting"); SharingServerState.ERROR -> os.t("服务受阻", "service unavailable"); else -> os.t("已停止", "stopped") }, os.t("本机数据服务", "local data service")), TileFrame("CLIENTS", os.t("${server?.clientCount ?: 0} 个客户端", "${server?.clientCount ?: 0} clients"), os.t("当前实际连接", "currently connected")), TileFrame("TRAFFIC", (server?.sentSentences ?: 0).toString(), os.t("实际写出的 NMEA 语句", "NMEA sentences actually written")))
        }
        "SETTINGS" -> listOf(TileFrame("VESSEL", state?.vesselSettings?.vesselName?.ifBlank { null } ?: os.t("我的船", "my boat"), os.t("船舶资料与系统偏好", "boat details & system preferences")), TileFrame("UNITS", os.distanceUnitLabel + " · " + os.speedUnitLabel, os.t("长度 ", "length ") + os.lengthUnitLabel + " · " + os.t("水深 ", "depth ") + os.depthUnitLabel + " · " + os.coordinateFormat, os.t("全局显示单位", "global display units")))
        else -> {
            val countFlow=remember(os.shell) {os.shell.engine.state.map {it.start.document.placements.size}.distinctUntilChanged()}
            val count=activeTileValue(countFlow,os.shell.engine.state.value.start.document.placements.size,visible)
            listOf(TileFrame("COLLECTION", os.t("$count 块磁贴", "$count tiles"), os.t("找到适合你的开始屏幕", "make Start your own")))
        }
    }
    val demo = (state?.settings?.demoMode == true || os.positionSource == "demo") && app.app.name in setOf("CHART", "INSTRUMENTS", "ANCHOR", "DATA_CENTER")
    val selectedFrames = if (mode == "STATIC") listOf(TileFrame("STATIC", title, "")) else if (mode == "AUTO") frames else frames.filter { it.key == mode }.ifEmpty { listOf(TileFrame(mode, unavailable, "")) }
    val permanentState=when(app.app) {
        AppId.ANCHOR->frames.firstOrNull {it.key=="WATCH"}?.headline.orEmpty()+
            if(state?.active!=null)" · "+(lastFix?.let {readingAge(os,it.elapsed,readingNow)} ?: os.t("等待船位","Waiting for position"))else ""
        AppId.AIS->frames.firstOrNull {it.key=="WATCH"}?.headline.orEmpty()
        AppId.VOYAGES->frames.firstOrNull {it.key=="RECORDING"}?.eyebrow.orEmpty()
        else->""
    }
    val selected = selectedFrames.map {original->
        val it=if(mode!="STATIC"&&permanentState.isNotBlank())original.copy(priorityLine=permanentState)else original
        val frameIsDemo=if(it.key=="MAP" && it.image!=null)it.demo else demo
        if(frameIsDemo)it.copy(eyebrow=os.t("演示 · ", "DEMO · ")+it.eyebrow,demo=true)else it
    }
    val cover = app.app == AppId.CHART && mode in setOf("AUTO", "MAP")
    TileFace(os,app,title,selected,size,context,cover,rotate,interval)
}

@Composable internal fun TileFace(os: OsStore, app: ShellApp, title: String, frames: List<TileFrame>, size: MarineTileSize, context: LauncherTileRenderContext, cover: Boolean, rotate: Boolean, interval: Long) {
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
    // 冻结实时内容不冻结显示偏好；切换单位、坐标或语言时立刻刷新同一帧的文案。
    var heldFrame by remember(app.id, title, keys, os.unitPreferences, os.coordinateFormat, os.chinese) { mutableStateOf(currentFrame) }
    SideEffect { if (context.liveContentEnabled) heldFrame = currentFrame }
    val frame = if (context.liveContentEnabled) currentFrame else heldFrame
    Box(context.modifier.fillMaxSize().clipToBounds().semantics(mergeDescendants=true) {
        contentDescription=listOf(title,frame.eyebrow,frame.headline,frame.priorityLine,frame.detail,frame.historyCaption).filter {it.isNotBlank()}.joinToString(" · ")
    }) {
        if (size == MarineTileSize.ICON_1X1) ShellAppIcon(app, context.contentColor, Modifier.size(36.dp).align(Alignment.Center))
        else if (!active) TileFrameContent(os, app, title, frame, size, context.contentColor, cover)
        else AnimatedContent(frame.key, modifier = Modifier.fillMaxSize(), transitionSpec = {
            ((slideInVertically(tween(420)) { it } + fadeIn(tween(240))) togetherWith (slideOutVertically(tween(420)) { -it } + fadeOut(tween(200)))).using(null)
        }, label = "live-tile") { key -> TileFrameContent(os, app, title, frames.firstOrNull { it.key == key } ?: frame, size, context.contentColor, cover) }
    }
}

@Composable private fun TileFrameContent(os: OsStore, app: ShellApp, title: String, frame: TileFrame, size: MarineTileSize, color: Color, cover: Boolean) {
    if (frame.key == "MAP" && frame.image != null) {
        Box(Modifier.fillMaxSize()) {
            Image(frame.image.asImageBitmap(), os.t("最近海图快照", "Latest chart snapshot"), Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .6f), Color.Black.copy(alpha = .9f)))).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WpText(frame.headline, 12, color = Color.White, maxLines = if (size == MarineTileSize.WIDE_4X2) 1 else 2)
                WpText((if (frame.demo) os.t("演示 · ", "DEMO · ") else "") + os.t("海图快照", "Chart snapshot") + " · " + frame.detail, 12, color = Color.White.copy(alpha = .9f), maxLines = 2)
                WpText(title, 12, color = Color.White, maxLines = 1, weight = FontWeight.SemiBold)
            }
        }
    } else BoxWithConstraints(Modifier.fillMaxSize().then(if (cover) Modifier.padding(YokuliMetrics.TileContentInset) else Modifier)) {
        val wide = size == MarineTileSize.WIDE_4X2
        val textScale = LocalDensity.current.fontScale * LocalWpTextScale.current
        val compactContent = maxHeight < (128f * textScale).dp
        val showHistory=frame.history?.points?.isNotEmpty()==true&&maxHeight>=(112f*textScale).dp
        if (frame.bearing != null || frame.progress != null) {
            Canvas(Modifier.fillMaxSize()) {
                frame.bearing?.let { bearing ->
                    val radius = min(this.size.width, this.size.height) * .37f
                    val origin = Offset(this.size.width * .75f, this.size.height * .55f)
                    drawCircle(color.copy(alpha = .16f), radius, origin, style = Stroke(1.dp.toPx()))
                    val radians = Math.toRadians(bearing - 90)
                    val end = Offset(origin.x + cos(radians).toFloat() * radius, origin.y + sin(radians).toFloat() * radius)
                    drawLine(color.copy(alpha = .3f), origin, end, 3.dp.toPx())
                }
                frame.progress?.let { ratio ->
                    val left = Offset(0f, this.size.height * .76f)
                    drawLine(color.copy(alpha = .2f), left, left.copy(x = this.size.width), 4.dp.toPx())
                    drawLine(color.copy(alpha = .65f), left, left.copy(x = this.size.width * ratio.coerceIn(0f, 1f)), 4.dp.toPx())
                }
            }
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (frame.key == "STATIC") Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ShellAppIcon(app, color, Modifier.size(48.dp))
            } else {
                if(frame.eyebrow.isNotBlank())Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (frame.eyebrow.isNotBlank()) WpText(
                        if (os.chinese) frame.eyebrow else tileLabelCase(frame.eyebrow),
                        12, color = color, maxLines = 1, modifier = Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                    ShellAppIcon(app, color, Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f).fillMaxWidth().clipToBounds(), verticalArrangement = Arrangement.Center) {
                    val headline = if (frame.key == "MAP") os.t("打开海图更新封面", "Open Chart for a cover") else frame.headline
                    val numeric = TileNumericHeadline.matchEntire(headline)?.takeIf {
                        it.groupValues[2].trim().length <= 18 && '·' !in it.groupValues[2]
                    }
                    if (numeric != null) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WpText(numeric.groupValues[1], if (compactContent) 24 else if (wide) 46 else 34,
                            color = color,
                            weight = FontWeight.Light, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (numeric.groupValues[2].isNotBlank()) WpText(numeric.groupValues[2].trim(), 12,
                            color = color, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
                    } else WpText(headline, if (compactContent) 18 else 20, color = color, maxLines = 2)
                }
                if(frame.priorityLine.isNotBlank())WpText(frame.priorityLine,12,color=color,weight=FontWeight.SemiBold,maxLines=2)
                if(showHistory&&frame.history!=null) {
                    val palette=MetroColors(Color.Transparent,color,color.copy(alpha=.6f),Color.Transparent,color.copy(alpha=.8f))
                    Canvas(Modifier.fillMaxWidth().height(if(wide&&!compactContent)34.dp else 24.dp).clipToBounds()) {drawInstrumentHistory(frame.history,palette,null,false)}
                    if(frame.historyCaption.isNotBlank())WpText(frame.historyCaption,11,color=color,maxLines=1)
                }
                if (frame.detail.isNotBlank()&&(!showHistory||wide&&!compactContent)) WpText(frame.detail, 12, color = color,
                    maxLines = if (wide && !compactContent) 2 else 1)
            }
            WpText(title, 12, color = color, maxLines = 1, weight = FontWeight.SemiBold)
        }
    }
}
