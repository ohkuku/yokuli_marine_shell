package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.runtime.*
import com.yokuli.anchorwatch.MainUiState
import com.yokuli.anchorwatch.domain.model.AlarmState
import com.yokuli.anchorwatch.domain.model.AlarmType
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.data.Reading
import com.yokuli.runtime.contract.ais.*
import com.yokuli.shell.compose.*
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.layout.TileDocumentEntry
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** 中文：桌面与唯一编辑预览使用同一入口；实例配置优先，不修改旧应用偏好。 */
@Composable fun instanceTilePresentation(os:OsStore,placement:TileDocumentEntry,active:Boolean):LauncherEntryVisualContribution {
    val binding=tileBinding(placement)
    val choice=tileContentDescriptor(os,binding)
    val app=ShellApp(choice.owner)
    val config=placement.presentation
    if(binding.kind==TileBindingKind.APP&&choice.supported) {
        val preset=tilePresets().firstOrNull {it.entryId.value==binding.contentId||it.entryId==placement.entryId}
        val mode=when(config.style) {
            "static"->"STATIC"
            else->config.legacyMode ?: preset?.mode ?: if(config.style=="summary")"AUTO"else"STATIC"
        }
        // 声明本身不执行 LiveAppTile；只在真正组成到屏幕的 renderer 内观察数据。
        return LauncherEntryVisualContribution(placement.entryId,choice.title.text(os),choice.owner.chineseIndex,
            choice.title.text(os),choice.subtitle.text(os),LauncherIconRenderer {color,modifier->ShellAppIcon(app,color,modifier)},
            (choice.sizes+placement.size).distinct().associateWith {size->LauncherTileRenderer {context->
                LegacyInstanceTile(os,app,active,mode,config.rotate ?: (mode=="AUTO"),config.intervalSeconds?.toLong() ?: 6L,size,context,preset)
            }},fullBleed=choice.owner==AppId.CHART&&mode in setOf("AUTO","MAP"))
    }
    return LauncherEntryVisualContribution(placement.entryId,choice.title.text(os),choice.owner.chineseIndex,
        choice.title.text(os),choice.subtitle.text(os),LauncherIconRenderer {color,modifier->ShellAppIcon(app,color,modifier)},
        (choice.sizes+placement.size).distinct().associateWith {size->LauncherTileRenderer {context->
            val live=tilePresentationActive(active&&context.liveContentEnabled)
            val frame=contentTileFrame(os,binding,config,live)
            TileFace(os,app,choice.title.text(os),listOf(frame),size,context,false,false,6)
        }})
}

private fun AppPreferenceLabel.text(os:OsStore)=if(os.chinese)chinese else english

/** 内容可解析性与观测时效是两个维度；缺失对象不是“读数过期”。 */
private enum class TileContentAvailability { LOADING, AVAILABLE, MISSING, FAILED, UNSUPPORTED }

@Composable private fun contentTileFrame(os:OsStore,binding:TileBinding,presentation:TilePresentation,active:Boolean):TileFrame {
    if(binding.providerId!="yokuli")return unavailableTileFrame(os,TileContentAvailability.UNSUPPORTED)
    return when(binding.kind) {
        TileBindingKind.READING->readingTileFrame(os,binding.contentId,presentation.style=="detail",active)
        TileBindingKind.CURRENT_TASK->taskTileFrame(os,binding.contentId,presentation.style=="detail",active)
        TileBindingKind.OVERVIEW->if(binding.contentId=="aisTraffic")trafficTileFrame(os,presentation.style=="detail",active)else unavailableTileFrame(os,TileContentAvailability.UNSUPPORTED)
        TileBindingKind.SAVED_PLACE,TileBindingKind.SAVED_ROUTE->savedTileFrame(os,binding)
        else->unavailableTileFrame(os,TileContentAvailability.UNSUPPORTED)
    }
}

private fun unavailableTileFrame(os:OsStore,status:TileContentAvailability)=when(status) {
    TileContentAvailability.LOADING->TileFrame("loading",os.t("正在读取","Loading"),os.t("保留原内容与位置","Content and position are preserved"))
    TileContentAvailability.FAILED->TileFrame("failed",os.t("资料暂未读出","Could not read content"),os.t("打开我的航行重试","Open My Sailing to retry"))
    TileContentAvailability.MISSING->TileFrame("missing",os.t("内容已删除","Content removed"),os.t("可在工坊更换内容或移除磁贴","Change content or remove this tile in Tile Studio"))
    TileContentAvailability.UNSUPPORTED->TileFrame("unsupported",os.t("暂不支持的内容","Unsupported content"),os.t("原配置已保留 · 在工坊编辑","Configuration preserved · Edit in Tile Studio"))
    TileContentAvailability.AVAILABLE->error("An available tile needs actual content")
}

private fun savedTileFrame(os:OsStore,binding:TileBinding):TileFrame {
    val roomObject=binding.kind==TileBindingKind.SAVED_PLACE&&binding.contentId.startsWith("spot:")
    val status=when {
        roomObject&&os.sailing.error->TileContentAvailability.FAILED
        roomObject&&!os.sailing.loaded->TileContentAvailability.LOADING
        !roomObject&&(os.contentRecoveryFailure!=null||os.persistenceState.value.readFailure!=null)->TileContentAvailability.FAILED
        !roomObject&&os.contentReadInProgress->TileContentAvailability.LOADING
        else->TileContentAvailability.AVAILABLE
    }
    if(status!=TileContentAvailability.AVAILABLE)return unavailableTileFrame(os,status)
    if(binding.kind==TileBindingKind.SAVED_ROUTE) {
        val route=os.routes.firstOrNull {it.id==binding.contentId} ?: return unavailableTileFrame(os,TileContentAvailability.MISSING)
        return TileFrame("route",route.name,os.formatDistance(route.length)+" · "+os.t("${route.points.size} 个航点","${route.points.size} waypoints"),os.t("收藏航线","Saved route"))
    }
    val place=os.allPlaces.firstOrNull {it.id==binding.contentId} ?: return unavailableTileFrame(os,TileContentAvailability.MISSING)
    return TileFrame("place",place.name,os.formatCoordinates(place.point),os.t("收藏地点","Saved place"))
}

private data class ReadingTileProjection(val value:VesselObservation<Double> = VesselObservation(),val auxiliary:VesselObservation<Double>?=null)
private fun readingTileProjection(state:MainUiState,id:String,detail:Boolean):ReadingTileProjection=with(state.vesselData) {
    when(id) {
        "SOG"->ReadingTileProjection(sogKnots)
        "HEADING_TRUE"->ReadingTileProjection(headingTrueDegrees)
        "DEPTH"->ReadingTileProjection(depthMeters)
        "TRUE_WIND_SPEED"->ReadingTileProjection(trueWind.speedKnots,trueWind.directionDegrees.takeIf {detail})
        "APPARENT_WIND_SPEED"->ReadingTileProjection(apparentWind.speedKnots,apparentWind.angleDegrees.takeIf {detail})
        "PRESSURE"->ReadingTileProjection(pressureHpa)
        else->ReadingTileProjection()
    }
}
private fun tileMetric(id:String)=when(id) {"SOG"->"sog";"HEADING_TRUE"->"heading";"DEPTH"->"depth";"TRUE_WIND_SPEED"->"tws";"APPARENT_WIND_SPEED"->"aws";"PRESSURE"->"pressure";else->null}

@Composable private fun readingTileFrame(os:OsStore,id:String,detail:Boolean,active:Boolean):TileFrame {
    val metric=tileMetric(id) ?: return unavailableTileFrame(os,TileContentAvailability.UNSUPPORTED)
    val state=os.marine?.services?.state
    val projectionFlow=remember(state,id,detail) {state?.map {readingTileProjection(it,id,detail)}?.distinctUntilChanged()}
    val data=activeTileValue(projectionFlow,state?.value?.let {readingTileProjection(it,id,detail)} ?: ReadingTileProjection(),active)
    val now=tileElapsed(active)
    val observation=data.value
    val heading=id=="HEADING_TRUE"
    val number=if(heading)observation.liveNumber()else observation.displayNumber()
    val reference=when(val ref=observation.reference) {
        VesselReference.TrueNorth->os.t("真北","True north")
        VesselReference.WaterReferenced->os.t("相对于水","Water referenced")
        VesselReference.GroundReferenced->os.t("相对于地面","Ground referenced")
        VesselReference.VesselRelative->os.t("相对于船艏","Vessel relative")
        is VesselReference.Depth->when(ref.reference.name) {
            "BELOW_SURFACE"->os.t("水面以下","Below surface")
            "BELOW_KEEL"->os.t("龙骨以下","Below keel")
            "BELOW_TRANSDUCER"->os.t("换能器以下","Below transducer")
            else->os.t("测深参考未提供","Depth reference unspecified")
        }
        else->if(id=="DEPTH")os.t("测深参考未提供","Depth reference unspecified")else ""
    }
    val source=observation.sourceIdentity?.displayName.orEmpty()
    val status=observationStatus(os,observation,now)
    val aux=data.auxiliary
    val extra=if(detail&&aux?.value!=null) {
        val angle=aux.liveNumber()
        val angleText=if(id=="APPARENT_WIND_SPEED")angle?.let {os.formatMetric("awa",signedHistoryAngle(it))} ?: "—"else os.formatBearing(angle)
        os.t(if(id=="APPARENT_WIND_SPEED")"船体风角 "else"风向 ",if(id=="APPARENT_WIND_SPEED")"Relative angle "else"Direction ")+angleText+" · "+observationStatus(os,aux,now)
    }else ""
    val showHistory=detail&&id in setOf("SOG","HEADING_TRUE","DEPTH","PRESSURE")
    val history=if(showHistory)rememberTileHistory(os,metric,active,now)else null
    val time=history?.points?.lastOrNull()?.reading?.observedUtcMillis
    val historyCaption=remember(history,os.chinese) {history?.takeIf {it.points.isNotEmpty()}?.let {
        val end=time ?: (System.currentTimeMillis()-(SystemClock.elapsedRealtime()-it.end))
        os.t("近 5 分钟 · 截至 ","5 min · through ")+DateFormat.getTimeInstance(DateFormat.SHORT,if(os.chinese)Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH).format(Date(end))
    }.orEmpty()}
    return TileFrame(id,os.formatMetric(metric,number),listOf(reference,source).filter {it.isNotBlank()}.joinToString(" · "),
        if(observation.source==VesselDataSource.DEMO)os.t("演示读数","Demo reading")else extra,
        bearing=if(heading&&history==null)number else if(detail&&id=="TRUE_WIND_SPEED")aux?.liveNumber()else null,
        live=observation.displayIsLive(),priorityLine=status+if(id=="DEPTH"&&reference.isNotBlank())" · "+reference else "",history=history,historyCaption=historyCaption)
}

/** 主读数持续更新；五分钟历史只在分钟边界且确有新观测时替换，尺寸改变不重采样。 */
@Composable internal fun rememberTileHistory(os:OsStore,metric:String,active:Boolean,now:Long):InstrumentHistoryFrame {
    data class Capture(val end:Long,val samples:List<Reading>)
    // 历史已有唯一 DataHub 所有者；这里按共用时钟读取不可变快照，不为每个新样本重组磁贴。
    var capture by remember(metric) {mutableStateOf(Capture(now,os.hub.history.value[metric].orEmpty().toList()))}
    LaunchedEffect(active,now/60_000L,metric) {
        if(active) {
            val values=os.hub.history.value[metric].orEmpty()
            val newest=values.lastOrNull()
            if(newest!=null&&(capture.samples.isEmpty() || (now/60_000L!=capture.end/60_000L&&
                (newest.elapsed>capture.end||newest.continuityKey!=capture.samples.lastOrNull()?.continuityKey))))
                capture=Capture(now,values.toList())
        }
    }
    return remember(metric,capture,os.unitPreferences) {buildInstrumentHistory(os,metric,capture.samples,capture.end,5)}
}

@Composable private fun taskTileFrame(os:OsStore,id:String,detail:Boolean,active:Boolean):TileFrame {
    val now=tileElapsed(active)
    val state=os.marine?.services?.state
    return when(id) {
        "navigation"->{
            val source=os.positionSource
            val flow=remember(os.hub,source) {os.hub.state.map {it.fix(source)}.distinctUntilChanged()}
            val fix=activeTileValue(flow,os.hub.state.value.fix(source),active)
            val route=os.activeRoute
            val target=os.nextPoint
            val remaining=target?.let {point->fix?.takeIf {it.fresh(now)}?.let {distance(it.point,point)}}
            TileFrame("navigation",route?.name ?: os.t("未在导航","Navigation is off"),
                if(route==null)os.t("打开海图选择航线","Open Chart to choose a route")else
                    (remaining?.let {os.formatDistance(it)+" · "} ?: "")+os.t("第 ${os.routeLeg+1}/${route.points.size} 个航点","Waypoint ${os.routeLeg+1}/${route.points.size}"),
                if(detail)os.t("当前导航","Current navigation")else "",
                priorityLine=if(route==null)""else fix?.let {os.t("船位 · ","Position · ")+readingAge(os,it.elapsed,now)} ?: os.t("等待船位","Waiting for position"))
        }
        "recording"->{
            val flow=remember(state) {state?.map {it.activeTrip}?.distinctUntilChanged()}
            val trip=activeTileValue(flow,state?.value?.activeTrip,active)
            TileFrame("recording",trip?.let {os.formatDistance(it.distanceMeters)} ?: os.t("未在记录","Recording is off"),
                trip?.let {it.name+if(detail)" · "+os.t("${it.waypointCount} 个时刻","${it.waypointCount} moments")else ""} ?: os.t("在日志中记录下一次出发","Record your next departure in Logbook"),
                priorityLine=trip?.let {if(it.paused)os.t("记录已暂停","Recording paused")else os.t("正在记录","Recording")}.orEmpty())
        }
        "anchorWatch"->{
            val flow=remember(state) {state?.map {Triple(it.active,it.alarmSnapshot,it.vesselData.position)}?.distinctUntilChanged()}
            val view=activeTileValue(flow,state?.value?.let {Triple(it.active,it.alarmSnapshot,it.vesselData.position)},active)
            val watch=view?.first;val alarm=view?.second;val position=view?.third
            val critical=when {
                watch==null->os.t("未在值守","Watch is off")
                alarm?.state==AlarmState.ALARM&&alarm.type!=AlarmType.ALARM_TEST->os.t("警报触发","Alarm active")
                watch.paused->os.t("值守已暂停","Watch paused")
                alarm?.state==AlarmState.WARNING->os.t("锚警预警","Anchor warning")
                else->os.t("正在守锚","Anchor watch active")
            }
            val delta=position?.value?.takeIf {position.displayIsLive()}?.let {point->watch?.let {distance(GeoPoint(point.latitude,point.longitude),GeoPoint(it.anchorLatitude,it.anchorLongitude))}}
            TileFrame("anchorWatch",critical,
                if(watch==null)os.t("打开守锚准备下锚","Open Anchor Watch to prepare")else os.t("半径 ","Radius ")+os.formatLength(watch.alarmRadiusMeters)+
                    if(detail&&delta!=null)" · "+os.t("距锚 ","From anchor ")+os.formatLength(delta)else "",
                priorityLine=if(watch==null)""else position?.let {os.t("船位 · ","Position · ")+observationStatus(os,it,now)} ?: os.t("等待船位","Waiting for position"),
                progress=if(detail&&delta!=null&&watch!=null&&watch.alarmRadiusMeters>0)(delta/watch.alarmRadiusMeters).toFloat()else null)
        }
        else->unavailableTileFrame(os,TileContentAvailability.UNSUPPORTED)
    }
}

private data class TrafficTileProjection(val current:Int,val concern:Int,val watching:Boolean,val limited:Boolean,val latest:Long?,val input:String)
@Composable private fun trafficTileFrame(os:OsStore,detail:Boolean,active:Boolean):TileFrame {
    val source=os.marine?.system?.ais?.snapshot
    val chinese=os.chinese
    fun project(snapshot:TrafficSnapshot)=TrafficTileProjection(snapshot.targets.count {it.state==AisTargetState.CURRENT&&!it.cached},
        snapshot.targets.count {it.riskLevel!=AisRiskLevel.NONE},snapshot.preferences.monitoringEnabled,
        snapshot.preferences.monitoringEnabled&&(snapshot.backgroundLimitations.isNotEmpty()||snapshot.inputs.none {it.state==AisInputState.ONLINE}),
        snapshot.inputs.mapNotNull {it.lastAisElapsed}.maxOrNull(),aisInputSummary(os,snapshot))
    val flow=remember(source,chinese) {source?.map(::project)?.distinctUntilChanged()}
    val data=activeTileValue(flow,project(source?.value ?: TrafficSnapshot()),active)
    val now=tileElapsed(active)
    val watch=if(data.concern>0)os.t("${data.concern} 个需关注","${data.concern} need attention")else if(data.watching)os.t("交通警戒已启用","Traffic watch on")else os.t("交通警戒关闭","Traffic watch off")
    return TileFrame("traffic",if(data.latest==null)os.t("等待 AIS","Waiting for AIS")else os.t("${data.current} 个当前目标","${data.current} current targets"),
        if(detail)data.input else data.latest?.let {os.t("最近报告 · ","Latest report · ")+readingAge(os,it,now)} ?: data.input,
        priorityLine=watch+if(data.limited)os.t(" · 运行受限"," · Limited")else "")
}
