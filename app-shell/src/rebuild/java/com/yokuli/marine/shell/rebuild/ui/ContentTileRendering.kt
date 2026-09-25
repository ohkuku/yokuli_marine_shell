package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.runtime.*
import com.yokuli.anchorwatch.domain.model.AlarmState
import com.yokuli.anchorwatch.domain.model.AlarmType
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.data.Reading
import com.yokuli.marine.shell.rebuild.data.withNavigation
import com.yokuli.runtime.contract.ais.*
import com.yokuli.shell.compose.*
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.layout.TileDocumentEntry
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** 桌面与编辑器共用实际渲染；表现变更不再被旧帧或迁移模式遮住。 */
@Composable fun instanceTilePresentation(os:OsStore,placement:TileDocumentEntry,active:Boolean):LauncherEntryVisualContribution {
    val binding=tileBinding(placement)
    val choice=tileContentDescriptor(os,binding)
    val app=ShellApp(choice.owner)
    val config=placement.presentation
    if(binding.kind==TileBindingKind.APP&&choice.supported) {
        val preset=tilePresets().firstOrNull {it.entryId.value==binding.contentId||it.entryId==placement.entryId}
        val legacyMode=config.legacyMode
        val mode=when {
            config.style=="static"->"STATIC"
            legacyMode!=null->legacyMode
            config.style=="summary"->"AUTO"
            else->preset?.mode ?: "STATIC"
        }
        return LauncherEntryVisualContribution(placement.entryId,choice.title.text(os),choice.owner.chineseIndex,
            choice.title.text(os),choice.subtitle.text(os),LauncherIconRenderer {color,modifier->ShellAppIcon(app,color,modifier)},
            (choice.sizes+placement.size).distinct().associateWith {size->LauncherTileRenderer {context->
                key(binding.contentKey,config,size) {
                    LegacyInstanceTile(os,app,active,mode,config.rotate ?: (mode=="AUTO"),config.intervalSeconds?.toLong() ?: 6L,size,context,preset)
                }
            }},fullBleed=choice.owner==AppId.CHART&&mode in setOf("AUTO","MAP"))
    }
    return LauncherEntryVisualContribution(placement.entryId,choice.title.text(os),choice.owner.chineseIndex,
        choice.title.text(os),choice.subtitle.text(os),LauncherIconRenderer {color,modifier->ShellAppIcon(app,color,modifier)},
        (choice.sizes+placement.size).distinct().associateWith {size->LauncherTileRenderer {context->
            key(binding.contentKey,config,size) {
                val live=tilePresentationActive(active&&context.liveContentEnabled)
                if(binding.providerId=="yokuli"&&binding.kind==TileBindingKind.READING) {
                    val content=readingTileFrame(os,binding.contentId,config,live)
                    ReadingTileFace(os,app,choice.title.text(os),content,size,context,live)
                } else {
                    val frame=contentTileFrame(os,binding,config,live)
                    TileFace(os,app,choice.title.text(os),listOf(frame),size,context,false,false,6)
                }
            }
        }})
}

private fun AppPreferenceLabel.text(os:OsStore)=if(os.chinese)chinese else english
private enum class TileContentAvailability { LOADING, AVAILABLE, MISSING, FAILED, UNSUPPORTED }

@Composable private fun contentTileFrame(os:OsStore,binding:TileBinding,presentation:TilePresentation,active:Boolean):TileFrame {
    if(binding.providerId!="yokuli")return unavailableTileFrame(os,TileContentAvailability.UNSUPPORTED)
    return when(binding.kind) {
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
        return TileFrame("route",route.name,os.formatDistance(route.length)+" · "+os.t("${route.navigationTargetIndices?.size ?: route.points.size} 个航点","${route.navigationTargetIndices?.size ?: route.points.size} waypoints"),os.t("收藏航线","Saved route"))
    }
    val place=os.allPlaces.firstOrNull {it.id==binding.contentId} ?: return unavailableTileFrame(os,TileContentAvailability.MISSING)
    return TileFrame("place",place.name,os.formatCoordinates(place.point),os.t("收藏地点","Saved place"))
}

private data class ReadingTileProjection(val observation:VesselObservation<*> = VesselObservation<Double>(),val number:Double?=null,val auxiliary:VesselObservation<Double>?=null)
private fun readingTileProjection(state:VesselDataSnapshot,tile:InstrumentTileId,auxiliary:Boolean):ReadingTileProjection {
    val observation=instrumentObservation(state,tile)
    val number=when(tile) {
        InstrumentTileId.ROLL_PERIOD->(observation.value as? VesselMotion)?.dominantRollPeriodSeconds
        InstrumentTileId.MOTION_SCORE->(observation.value as? VesselMotion)?.score
        InstrumentTileId.IMPACT_COUNT->(observation.value as? VesselMotion)?.impactCandidateCount?.toDouble()
        else->(observation.value as? Number)?.toDouble()
    }?.takeIf(Double::isFinite)
    return ReadingTileProjection(observation,number,if(!auxiliary)null else when(tile) {
        InstrumentTileId.TRUE_WIND_SPEED->state.trueWind.directionDegrees
        InstrumentTileId.APPARENT_WIND_SPEED->state.apparentWind.angleDegrees
        else->null
    })
}

@Composable private fun readingTileFrame(os:OsStore,id:String,config:TilePresentation,active:Boolean):ReadingTileContent {
    val tile=tileInstrumentId(id) ?: return ReadingTileContent(unavailableTileFrame(os,TileContentAvailability.UNSUPPORTED))
    val metric=instrumentTrendKey(tile).orEmpty()
    val relative=id in TileReadingPresentationPolicy.relativeDirectionIds||id=="APPARENT_WIND_SPEED"
    val directionStyle=config.style=="compass"||config.style=="detail"&&
        (id in TileReadingPresentationPolicy.windIds||id in TileReadingPresentationPolicy.directionIds||id in TileReadingPresentationPolicy.relativeDirectionIds)
    val state=os.marine?.services?.state
    val navigation=os.marine?.system?.navigation?.state
    val navigationMetric=id in setOf("WAYPOINT_BEARING","WAYPOINT_DISTANCE","CROSS_TRACK_ERROR","VMC")
    val projectionFlow=remember(state,navigation,tile,directionStyle) {
        if(state==null)null else if(navigationMetric&&navigation!=null)combine(state,navigation) {values,nav->readingTileProjection(values.vesselData.withNavigation(nav),tile,directionStyle)}.distinctUntilChanged()
        else state.map {readingTileProjection(it.vesselData,tile,directionStyle)}.distinctUntilChanged()
    }
    val initial=state?.value?.vesselData?.let {if(navigationMetric&&navigation!=null)it.withNavigation(navigation.value)else it}
    val data=activeTileValue(projectionFlow,initial?.let {readingTileProjection(it,tile,directionStyle)} ?: ReadingTileProjection(),active)
    tileReadingDisplayDemand(os,id,active)
    val now=tileElapsed(active)
    val observation=data.observation
    val number=data.number?.takeIf {!InstrumentReadingPolicy.requiresFresh(tile)||observation.displayIsLive()}
        ?.let {if(id in TileReadingPresentationPolicy.relativeDirectionIds)signedHistoryAngle(it)else it}
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
        else->when {
            id=="DEPTH"->os.t("测深参考未提供","Depth reference unspecified")
            id=="UKC"->os.t("龙骨以下","Below keel")
            relative->os.t("船艏 0° · 左负右正","Bow 0° · port − / starboard +")
            id in TileReadingPresentationPolicy.directionIds->os.t("真北","True north")
            else->""
        }
    }
    val source=observation.sourceIdentity?.displayName ?: observation.provenance.orEmpty()
    val status=observationStatus(os,observation,now)
    val aux=data.auxiliary
    val extra=if(directionStyle&&aux!=null) {
        val angle=aux.liveNumber()
        val angleText=if(id=="APPARENT_WIND_SPEED")angle?.let {os.formatMetric("awa",signedHistoryAngle(it))} ?: "—"else os.formatBearing(angle)
        os.t(if(id=="APPARENT_WIND_SPEED")"船体风角 "else"风向 ",if(id=="APPARENT_WIND_SPEED")"Relative angle "else"Direction ")+angleText+" · "+observationStatus(os,aux,now)
    }else ""
    val history=if(tileHasHistory(id,config.style))rememberTileHistory(os,metric,active,now,tileHistoryMinutes(id,config),config.rangeMinimum,config.rangeMaximum)else null
    val historyCaption=remember(history,os.chinese) {history?.let {
        val end=it.points.lastOrNull()?.reading?.observedUtcMillis ?: (System.currentTimeMillis()-(SystemClock.elapsedRealtime()-it.end))
        os.t("近 ${tileHistoryMinutes(id,config)} 分钟 · 截至 ","${tileHistoryMinutes(id,config)} min · through ")+DateFormat.getTimeInstance(DateFormat.SHORT,if(os.chinese)Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH).format(Date(end))
    }.orEmpty()}
    val fixedMinimum=config.rangeMinimum;val fixedMaximum=config.rangeMaximum
    val range=if(fixedMinimum!=null&&fixedMaximum!=null&&fixedMinimum<fixedMaximum)fixedMinimum to fixedMaximum else tileDefaultRange(id,number)
    val rangeOut=number!=null&&fixedMinimum!=null&&fixedMaximum!=null&&(number<fixedMinimum||number>fixedMaximum)
    val graphicObservation=if(directionStyle&&id in TileReadingPresentationPolicy.windIds)aux ?: VesselObservation<Double>()else observation
    val graphic=when {
        directionStyle->TileMetricGraphic(TileMetricGraphicKind.COMPASS,if(id in TileReadingPresentationPolicy.windIds)aux?.liveNumber()else number?.takeIf {observation.displayIsLive()},relative=relative)
        config.style=="attitude"->TileMetricGraphic(if(id=="PITCH")TileMetricGraphicKind.PITCH else TileMetricGraphicKind.HEEL,number,range.first,range.second)
        config.style=="gauge"->TileMetricGraphic(TileMetricGraphicKind.GAUGE,number?.let {os.displayMetricValue(metric,it)},
            os.displayMetricValue(metric,range.first),os.displayMetricValue(metric,range.second),os.formatMetric(metric,range.first),os.formatMetric(metric,range.second))
        else->null
    }?.copy(observation=graphicObservation)
    val position=observation.value as? VesselPosition
    val positionDetail=if(id=="POSITION"&&config.style=="detail")listOfNotNull(position?.horizontalAccuracyMeters?.let {os.t("精度 ±","Accuracy ±")+os.formatLength(it)},position?.satellites?.let {os.t("$it 颗卫星","$it satellites")}).joinToString(" · ")else ""
    val headline=if(id=="POSITION")position?.let {os.formatCoordinates(GeoPoint(it.latitude,it.longitude))} ?: "—"else os.formatMetric(metric,number)
    val details=listOf(reference.takeIf {config.showReference||id in TileReadingPresentationPolicy.requiredReferenceIds}.orEmpty(),
        source.takeIf {config.showSource}.orEmpty(),positionDetail).filter {it.isNotBlank()}.joinToString(" · ")
    return ReadingTileContent(TileFrame(id,headline,details,
        if(observation.source==VesselDataSource.DEMO)os.t("演示读数","Demo reading")else extra.takeIf {config.style=="detail"}.orEmpty(),
        live=observation.displayIsLive(),priorityLine=status+if(rangeOut)os.t(" · 超出显示量程"," · Outside display range")else "",
        history=history,historyCaption=historyCaption),graphic,config.style)
}

/** 历史只展示既有观测；同分钟内首次到达的样本也立即进入空图，不补造点。 */
@Composable internal fun rememberTileHistory(os:OsStore,metric:String,active:Boolean,now:Long,minutes:Int=5,minimum:Double?=null,maximum:Double?=null):InstrumentHistoryFrame {
    data class Capture(val end:Long,val samples:List<Reading>)
    var capture by remember(metric) {mutableStateOf(Capture(now,os.hub.history.value[metric].orEmpty().toList()))}
    val empty=capture.samples.isEmpty()
    LaunchedEffect(active,if(empty)now/1_000L else now/60_000L,metric) {
        if(active) {
            val values=os.hub.history.value[metric].orEmpty()
            val newest=values.lastOrNull()
            if(newest!=null&&(capture.samples.isEmpty() || (now/60_000L!=capture.end/60_000L&&
                (newest.elapsed>capture.end||newest.continuityKey!=capture.samples.lastOrNull()?.continuityKey))))
                capture=Capture(now,values.toList())
        }
    }
    return remember(metric,capture,os.unitPreferences,minutes,minimum,maximum) {
        val history=buildInstrumentHistory(os,metric,capture.samples,capture.end,minutes)
        if(minimum!=null&&maximum!=null&&minimum<maximum&&history.kind !in setOf(InstrumentHistoryKind.DIRECTION,InstrumentHistoryKind.BEARING,InstrumentHistoryKind.COUNTER,InstrumentHistoryKind.COUNT))
            history.copy(lower=os.displayMetricValue(metric,minimum),upper=os.displayMetricValue(metric,maximum))else history
    }
}

@Composable private fun taskTileFrame(os:OsStore,id:String,detail:Boolean,active:Boolean):TileFrame {
    val now=tileElapsed(active)
    val state=os.marine?.services?.state
    return when(id) {
        "navigation"->{
            val flow=os.marine?.system?.navigation?.state
            val nav=activeTileValue(flow,flow?.value?:os.navigationState,active)
            val session=nav.session?.takeIf {it.ongoing}
            val guide=nav.guidance
            val route=session?.route
            TileFrame("navigation",route?.name ?: if(session!=null)os.t("外部设备导航","External navigation")else os.t("未在导航","Navigation is off"),
                if(session==null)os.t("打开海图选择航线","Open Chart to choose a route")else
                    (guide?.distanceMeters?.let {os.formatDistance(it)+" · "} ?: "")+(route?.let {os.t("第 ${it.targetIndices.indexOf(session.targetIndex)+1}/${it.targetIndices.size} 个航点","Waypoint ${it.targetIndices.indexOf(session.targetIndex)+1}/${it.targetIndices.size}")}?:guide?.targetName.orEmpty()),
                if(detail)os.t("当前导航","Current navigation")else "",
                priorityLine=when(session?.phase){
                    com.yokuli.runtime.contract.navigation.NavigationPhase.PAUSED->os.t("引导已暂停","Guidance paused")
                    com.yokuli.runtime.contract.navigation.NavigationPhase.RECOVERY_REQUIRED->os.t("点按继续上次导航","Resume previous navigation")
                    null->""
                    else->guide?.positionElapsedMillis?.let {os.t("船位 · ","Position · ")+readingAge(os,it,now)}?:os.t("等待船位","Waiting for position")
                })
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
