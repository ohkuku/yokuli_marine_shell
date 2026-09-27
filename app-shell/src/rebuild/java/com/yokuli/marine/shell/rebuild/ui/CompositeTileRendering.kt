package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.data.database.AnchorSessionEntity
import com.yokuli.anchorwatch.data.database.TrackPointEntity
import com.yokuli.anchorwatch.data.database.TripSessionEntity
import com.yokuli.anchorwatch.data.trip.TripTrackSnapshot
import com.yokuli.anchorwatch.domain.model.AlarmSnapshot
import com.yokuli.anchorwatch.domain.model.AlarmState
import com.yokuli.anchorwatch.domain.model.AlarmType
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.runtime.contract.ais.*
import com.yokuli.runtime.contract.navigation.*
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TilePresentation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.*

/** 复合磁贴是多个已有场景的只读投影；它不新建数据源、会话或业务快捷动作。 */
@Composable internal fun CompositeTileFace(
    os:OsStore,title:String,configuration:TilePresentation,size:MarineTileSize,
    context:LauncherTileRenderContext,active:Boolean,
) {
    val panels=tileCompositePanelIds(configuration)
    val color=context.contentColor
    val large=size==MarineTileSize.LARGE_4X4
    CompositionLocalProvider(LocalInternalAppInputEnabled provides (active&&LocalInternalAppInputEnabled.current)) {
        Column(context.modifier.fillMaxSize().clipToBounds(),verticalArrangement=Arrangement.spacedBy(if(large)8.dp else 4.dp)) {
            WpText(title,12,color=color,weight=FontWeight.SemiBold,maxLines=1)
            val rows=panels.chunked(2)
            rows.forEachIndexed {rowIndex,row->
                Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(if(large)10.dp else 6.dp)) {
                    row.forEachIndexed {column,id->
                        key(id) {
                            val modifier=Modifier.weight(1f).fillMaxHeight()
                            if(id in setOf("navigation","anchorWatch","recording","aisTraffic"))
                                TaskScenePanel(os,id,color,active,modifier,compact=true)
                            else CompositeOverviewPanel(os,id,configuration,color,active,modifier)
                        }
                        if(column==0&&row.size==2)Box(Modifier.fillMaxHeight().width(1.dp).background(color.copy(alpha=.14f)))
                    }
                }
                if(rowIndex<rows.lastIndex)Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha=.14f)))
            }
        }
    }
}

@Composable internal fun TaskSceneTileFace(
    os:OsStore,title:String,id:String,size:MarineTileSize,context:LauncherTileRenderContext,active:Boolean,
) {
    CompositionLocalProvider(LocalInternalAppInputEnabled provides (active&&LocalInternalAppInputEnabled.current)) {
        TaskScenePanel(os,id,context.contentColor,active,context.modifier.fillMaxSize(),
            compact=size!=MarineTileSize.LARGE_4X4,titleOverride=title)
    }
}

/** 所有图均来自已持久/已接收的真实资料，空会话不画一条演示路径。 */
@Composable private fun TaskScenePanel(
    os:OsStore,id:String,color:Color,active:Boolean,modifier:Modifier,compact:Boolean,titleOverride:String?=null,
) {
    when(id) {
        "navigation"->NavigationScenePanel(os,color,active,modifier,compact,titleOverride)
        "anchorWatch"->AnchorScenePanel(os,color,active,modifier,compact,titleOverride)
        "recording"->RecordingScenePanel(os,color,active,modifier,compact,titleOverride)
        "aisTraffic"->TrafficScenePanel(os,color,active,modifier,compact,titleOverride)
    }
}

@Composable private fun SceneContent(
    title:String,headline:String,detail:String,status:String,color:Color,modifier:Modifier,compact:Boolean,
    critical:Boolean=false,graphic:@Composable BoxScope.()->Unit,
) {
    val statusColor=if(critical)Color(0xFFFFB46B)else color.copy(alpha=.7f)
    BoxWithConstraints(modifier.clipToBounds().semantics(mergeDescendants=true) {
        contentDescription=listOf(title,headline,detail,status).filter(String::isNotBlank).joinToString(" · ")
    }) {
        val small=maxHeight<116.dp
        val dense=compact||maxHeight<230.dp
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(if(small)2.dp else 5.dp)) {
            WpText(title,if(dense)11 else 13,color=color.copy(alpha=.85f),weight=FontWeight.SemiBold,maxLines=1)
            if(small) {
                Row(Modifier.weight(1f).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.weight(.9f).fillMaxHeight(),content=graphic)
                    WpText(headline,13,color=color,maxLines=2,modifier=Modifier.weight(1.1f))
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(),content=graphic)
                WpText(headline,if(dense)15 else 25,color=color,weight=FontWeight.Light,maxLines=1)
            }
            if(!dense&&detail.isNotBlank())WpText(detail,12,color=color.copy(alpha=.75f),maxLines=2)
            if(status.isNotBlank())WpText(status,if(dense)9 else 11,color=statusColor,maxLines=if(dense)1 else 2)
        }
    }
}

private data class TilePosition(val position:VesselObservation<VesselPosition>,val heading:VesselObservation<Double>)
@Composable private fun tilePosition(os:OsStore,active:Boolean):TilePosition {
    val source=os.marine?.services?.state
    fun project(data:VesselDataSnapshot)=TilePosition(data.position,data.headingTrueDegrees)
    val flow=remember(source) {source?.map {project(it.vesselData)}?.distinctUntilChanged()}
    return activeTileValue(flow,project(source?.value?.vesselData ?: VesselDataSnapshot()),active)
}

@Composable private fun NavigationScenePanel(os:OsStore,color:Color,active:Boolean,modifier:Modifier,compact:Boolean,title:String?) {
    tileInstrumentDisplayDemand(os,active,heading=true,sensors=false)
    val source=os.marine?.system?.navigation?.state
    val nav=activeTileValue(source,source?.value ?: NavigationState(),active)
    val own=tilePosition(os,active)
    val now=tileElapsed(active)
    val session=nav.session?.takeIf {it.ongoing}
    val route=session?.route
    val guidance=nav.guidance
    val position=own.position.value?.takeIf {own.position.displayIsLive()}?.let {GeoPoint(it.latitude,it.longitude)}
    val points=remember(route) {route?.geometry.orEmpty().filter {it.valid}.map {GeoPoint(it.lat,it.lon)}}
    val target=guidance?.targetPosition?.takeIf {it.valid}?.let {GeoPoint(it.lat,it.lon)}
    val status=when {
        nav.storageIssue!=null->os.t("导航恢复受阻","Navigation recovery blocked")
        session?.phase==NavigationPhase.RECOVERY_REQUIRED->os.t("待确认恢复导航","Confirm to resume navigation")
        session?.phase==NavigationPhase.PAUSED->os.t("引导已暂停","Guidance paused")
        session==null->os.t("选择航线后开始","Choose a route to begin")
        guidance?.live!=true->os.t("等待实时船位","Waiting for live position")
        else->os.t("船位 · ","Position · ")+observationStatus(os,own.position,now)
    }
    val headline=if(session==null)os.t("尚未导航","No active navigation")else guidance?.distanceMeters?.let(os::formatDistance)
        ?: route?.name ?: os.t("外部导航","External navigation")
    val detail=if(session==null)""else listOfNotNull(guidance?.targetName ?: route?.name,
        guidance?.crossTrackMeters?.let {os.t("横偏 ","Off track ")+os.formatLength(abs(it))},
        guidance?.remainingMeters?.let {os.t("全程剩余 ","Remaining ")+os.formatDistance(it)}).joinToString(" · ")
    val headingTitle=(if(own.position.source==VesselDataSource.DEMO)os.t("演示 · ","DEMO · ")else "")+(title ?: os.t("当前导航","Navigation"))
    SceneContent(headingTitle,headline,detail,status,color,modifier,compact) {
        if(session!=null&&(points.isNotEmpty()||target!=null))TileRouteGraphic(points.takeIf {it.isNotEmpty()} ?: listOfNotNull(target),
            position.takeIf {guidance?.live==true},own.heading,target,color,Modifier.fillMaxSize())
        else EmptySceneLabel(os.t("航线与下一目标","Route and next waypoint"),color)
    }
}

private data class AnchorSceneProjection(
    val session:AnchorSessionEntity?=null,val alarm:AlarmSnapshot=AlarmSnapshot(),
    val points:List<TrackPointEntity> = emptyList(),val position:VesselObservation<VesselPosition> = VesselObservation(),
    val heading:VesselObservation<Double> = VesselObservation(),
)
@Composable private fun AnchorScenePanel(os:OsStore,color:Color,active:Boolean,modifier:Modifier,compact:Boolean,title:String?) {
    tileInstrumentDisplayDemand(os,active,heading=true,sensors=false)
    val source=os.marine?.services?.state
    fun project(state:com.yokuli.anchorwatch.MainUiState)=AnchorSceneProjection(state.active,state.alarmSnapshot,state.points,state.vesselData.position,state.vesselData.headingTrueDegrees)
    val flow=remember(source) {source?.map(::project)?.distinctUntilChanged()}
    val view=activeTileValue(flow,source?.value?.let(::project) ?: AnchorSceneProjection(),active)
    val now=tileElapsed(active)
    val session=view.session
    val position=view.position.value?.takeIf {view.position.displayIsLive()}?.let {GeoPoint(it.latitude,it.longitude)}
    val anchor=session?.takeIf {it.centerStatus=="RESOLVED"}?.let {GeoPoint(it.anchorLatitude,it.anchorLongitude)}
    val delta=if(position!=null&&anchor!=null)distance(anchor,position)else null
    val alarm=view.alarm.type!=AlarmType.ALARM_TEST&&view.alarm.state in setOf(AlarmState.ALARM,AlarmState.WARNING)
    val headline=when {
        session==null->os.t("尚未下锚","Not anchored")
        session.paused->os.t("值守已暂停","Watch paused")
        session.monitoringPhase=="WAITING_FOR_GPS"->os.t("等待可信船位","Waiting for accepted position")
        alarm->os.t("需要关注","Needs attention")
        anchor==null->os.t("锚位确认中","Resolving anchor")
        else->delta?.let {os.t("距锚 ","From anchor ")+os.formatLength(it)} ?: os.t("正在守锚","Anchor watch")
    }
    val status=if(session==null)os.t("点按准备下锚","Tap to prepare")else os.t("船位 · ","Position · ")+observationStatus(os,view.position,now)
    val detail=session?.let {os.t("警戒半径 ","Watch radius ")+os.formatLength(it.alarmRadiusMeters)}.orEmpty()
    val trail=remember(view.points,session?.id) {view.points.filter {it.sessionId==session?.id&&!it.wasQuarantined&&it.fixTrust=="TRUSTED"}.takeLast(192)}
    val headingTitle=(if(view.position.source==VesselDataSource.DEMO)os.t("演示 · ","DEMO · ")else "")+(title ?: os.t("守锚","Anchor watch"))
    SceneContent(headingTitle,headline,detail,status,color,modifier,compact,alarm) {
        if(session!=null&&anchor!=null&&session.alarmRadiusMeters>0)AnchorSceneGraphic(anchor,position,view.position,view.heading,session.alarmRadiusMeters,
            trail,color,alarm,Modifier.fillMaxSize())
        else EmptySceneLabel(if(session!=null)os.t("尚无可信锚点","No confirmed anchor")else os.t("锚位与回旋范围","Anchor and swing area"),color)
    }
}

private data class RecordingSceneProjection(val session:TripSessionEntity?=null,val track:TripTrackSnapshot=TripTrackSnapshot())
@Composable private fun RecordingScenePanel(os:OsStore,color:Color,active:Boolean,modifier:Modifier,compact:Boolean,title:String?) {
    val source=os.marine?.services?.state
    fun project(state:com.yokuli.anchorwatch.MainUiState)=RecordingSceneProjection(state.activeTrip,state.tripTrack)
    val flow=remember(source) {source?.map(::project)?.distinctUntilChanged()}
    val view=activeTileValue(flow,source?.value?.let(::project) ?: RecordingSceneProjection(),active)
    val session=view.session
    val segments=remember(view.track,session?.id) {if(view.track.tripId!=session?.id)emptyList()else view.track.rendered(192).map {segment->
        segment.points.filter {it.hasPosition}.map {GeoPoint(it.latitude!!,it.longitude!!)}
    }}
    val headline=session?.let {os.formatDistance(it.distanceMeters)} ?: os.t("尚未记录","Not recording")
    val status=when {session==null->os.t("开始下一段航程","Start your next voyage");session.paused->os.t("记录已暂停","Recording paused");else->os.t("记录中","Recording")}
    val detail=session?.let {it.name+" · "+os.t("${it.waypointCount} 个时刻","${it.waypointCount} moments")}.orEmpty()
    val headingTitle=(if(session?.positionPreference=="DEMO")os.t("演示 · ","DEMO · ")else "")+(title ?: os.t("航行记录","Voyage log"))
    SceneContent(headingTitle,headline,detail,status,color,modifier,compact) {
        if(segments.any {it.isNotEmpty()})TileRouteGraphic(segments.flatten(),null,VesselObservation(),null,color,Modifier.fillMaxSize(),segments)
        else EmptySceneLabel(os.t("已记录的航迹","Recorded trail"),color)
    }
}

private data class TrafficTilePoint(val distance:Double,val bearing:Double,val heading:Double?,val risk:AisRiskLevel,val kind:AisEntityKind)
private data class TrafficSceneProjection(
    val points:List<TrafficTilePoint> = emptyList(),val current:Int=0,val concern:Int=0,val latest:Long?=null,
    val ownValid:Boolean=false,val range:Double=3704.0,val watching:Boolean=false,val limited:Boolean=false,
)
@Composable private fun TrafficScenePanel(os:OsStore,color:Color,active:Boolean,modifier:Modifier,compact:Boolean,title:String?) {
    val source=os.marine?.system?.ais?.snapshot
    fun project(value:TrafficSnapshot):TrafficSceneProjection {
        val current=value.targets.filter {it.state==AisTargetState.CURRENT&&!it.cached&&!it.positionInvalidated}
        return TrafficSceneProjection(current.mapNotNull {target->
            val distance=target.relative.distanceMeters?.takeIf {it.isFinite()&&it>=0} ?: return@mapNotNull null
            val bearing=target.relative.bearingDegrees?.takeIf(Double::isFinite) ?: return@mapNotNull null
            TrafficTilePoint(distance,bearing,target.dynamic?.headingDegrees,target.riskLevel,target.kind)
        }.sortedBy {it.distance}.take(96),current.size,current.count {it.riskLevel!=AisRiskLevel.NONE},
            value.inputs.mapNotNull {it.lastAisElapsed}.maxOrNull(),value.ownship?.positionValid==true,
            value.preferences.rangeNauticalMiles*1852.0,value.preferences.monitoringEnabled,
            value.backgroundLimitations.isNotEmpty()||value.runtime.foregroundError!=null)
    }
    val flow=remember(source) {source?.map(::project)?.distinctUntilChanged()}
    val view=activeTileValue(flow,project(source?.value ?: TrafficSnapshot()),active)
    val now=tileElapsed(active)
    val headline=if(view.latest==null)os.t("等待 AIS","Waiting for AIS")else os.t("${view.current} 个目标","${view.current} targets")
    val status=when {
        view.concern>0->os.t("${view.concern} 个需关注","${view.concern} need attention")
        !view.ownValid->os.t("缺少本船位置","Own position needed")
        view.limited->os.t("交通警戒受限","Traffic watch limited")
        else->view.latest?.let {os.t("报告 · ","Report · ")+readingAge(os,it,now)} ?: os.t("未收到船舶报告","No vessel reports")
    }
    val detail=os.t("真北向上 · 范围 ","North up · Range ")+os.formatDistance(view.range)+
        if(view.watching)os.t(" · 警戒开启"," · Watch on")else os.t(" · 警戒关闭"," · Watch off")
    SceneContent(title ?: os.t("周围交通","Nearby traffic"),headline,detail,status,color,modifier,compact,view.concern>0) {
        if(view.ownValid)TrafficSceneGraphic(view.points,view.range,color,Modifier.fillMaxSize())
        else EmptySceneLabel(os.t("收到船位后显示方位","Positions reveal traffic bearings"),color)
    }
}

@Composable private fun BoxScope.EmptySceneLabel(text:String,color:Color) {
    WpText(text,11,color=color.copy(alpha=.5f),maxLines=2,modifier=Modifier.align(Alignment.Center))
}

/** 航线/已录航迹的局部示意；保持经度跨日界线连续，不伪造地图底图。 */
@Composable private fun TileRouteGraphic(
    route:List<GeoPoint>,own:GeoPoint?,heading:VesselObservation<Double>,target:GeoPoint?,color:Color,modifier:Modifier,
    segments:List<List<GeoPoint>> = listOf(route),
) {
    val bearing=rememberInstrumentMotion(heading,heading.liveNumber(),circular=true)
    val geometry=remember(route,own,target,segments) {
        val reference=route.firstOrNull() ?: target ?: own
        if(reference==null)null else {
            fun local(p:GeoPoint):Offset {
                val lon=((p.lon-reference.lon+540.0)%360.0)-180.0
                return Offset((lon*cos(Math.toRadians(reference.lat))).toFloat(),(reference.lat-p.lat).toFloat())
            }
            val path=segments.map {line->line.map(::local)}
            val boat=own?.let(::local);val destination=target?.let(::local)
            val all=path.flatten()+listOfNotNull(boat,destination)
            if(all.isEmpty())null else TileRouteGeometry(path,boat,destination,
                all.minOf {it.x},all.maxOf {it.x},all.minOf {it.y},all.maxOf {it.y})
        }
    }
    Canvas(modifier.clipToBounds()) {
        val data=geometry ?: return@Canvas
        val width=(data.maxX-data.minX).coerceAtLeast(.00001f)
        val height=(data.maxY-data.minY).coerceAtLeast(.00001f)
        val ratio=min(size.width*.8f/width,size.height*.76f/height)
        fun point(p:Offset)=Offset(size.width*.5f+(p.x-(data.minX+data.maxX)/2)*ratio,
            size.height*.5f+(p.y-(data.minY+data.maxY)/2)*ratio)
        for(line in data.segments) {
            if(line.isEmpty())continue
            val path=Path().apply {line.forEachIndexed {index,p->val at=point(p);if(index==0)moveTo(at.x,at.y)else lineTo(at.x,at.y)}}
            drawPath(path,color.copy(alpha=.65f),style=Stroke(1.8.dp.toPx(),cap=StrokeCap.Round))
            drawCircle(color.copy(alpha=.65f),2.dp.toPx(),point(line.first()))
        }
        data.target?.let {at->drawCircle(color,5.dp.toPx(),point(at),style=Stroke(1.4.dp.toPx()))}
        data.own?.let {at->drawTileBoat(point(at),heading.liveNumber()?.let {bearing.value},color,5.dp.toPx())}
    }
}
private data class TileRouteGeometry(val segments:List<List<Offset>>,val own:Offset?,val target:Offset?,val minX:Float,val maxX:Float,val minY:Float,val maxY:Float)

@Composable private fun AnchorSceneGraphic(
    anchor:GeoPoint,position:GeoPoint?,observation:VesselObservation<VesselPosition>,heading:VesselObservation<Double>,
    radius:Double,trail:List<TrackPointEntity>,color:Color,alarm:Boolean,modifier:Modifier,
) {
    fun vector(point:GeoPoint):Offset {
        val meters=distance(anchor,point)
        val radians=Math.toRadians(bearing(anchor,point))
        return Offset((sin(radians)*meters).toFloat(),(-cos(radians)*meters).toFloat())
    }
    val boat=position?.let(::vector)
    val x=rememberInstrumentMotion(observation,boat?.x?.toDouble(),scaleKey=anchor)
    val y=rememberInstrumentMotion(observation,boat?.y?.toDouble(),scaleKey=anchor)
    val bow=rememberInstrumentMotion(heading,heading.liveNumber(),circular=true)
    val trailPoints=remember(trail,anchor) {trail.map {it to vector(GeoPoint(it.latitude,it.longitude))}}
    val extent=max(radius,boat?.getDistance()?.toDouble() ?: 0.0).coerceAtLeast(1.0)*1.12
    Canvas(modifier.clipToBounds()) {
        val center=Offset(size.width/2,size.height/2)
        val scale=(size.minDimension*.43f/extent).toFloat()
        val ink=if(alarm)Color(0xFFFFB46B)else color
        drawCircle(ink.copy(alpha=.3f),(radius*scale).toFloat(),center,style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx()))))
        var previous:Pair<TrackPointEntity,Offset>?=null
        trailPoints.forEachIndexed {index,item->
            val (sample,v)=item
            val at=center+v*scale
            val old=previous
            if(old!=null&&sample.timestamp-old.first.timestamp in 1L..15_000L&&sample.positionSource==old.first.positionSource)
                drawLine(color.copy(alpha=.12f+.5f*index/trailPoints.size.coerceAtLeast(1)),center+old.second*scale,at,1.4.dp.toPx(),StrokeCap.Round)
            else drawCircle(color.copy(alpha=.2f),1.2.dp.toPx(),at)
            previous=item
        }
        drawCircle(color,3.dp.toPx(),center,style=Stroke(1.5.dp.toPx()))
        drawLine(color,center-Offset(0f,7.dp.toPx()),center+Offset(0f,4.dp.toPx()),1.dp.toPx())
        if(boat!=null) {
            val at=center+Offset(x.value,y.value)*scale
            drawLine(color.copy(alpha=.2f),center,at,1.dp.toPx())
            drawTileBoat(at,heading.liveNumber()?.let {bow.value},ink,5.dp.toPx())
        }
    }
}

/** 北向方位散布，没有未说明的装饰扇区；范围沿用 AIS 中用户选定值。 */
@Composable private fun TrafficSceneGraphic(points:List<TrafficTilePoint>,range:Double,color:Color,modifier:Modifier) {
    Canvas(modifier.clipToBounds()) {
        val center=Offset(size.width/2,size.height/2)
        val radius=size.minDimension*.41f
        val extent=range.takeIf {it.isFinite()&&it>0} ?: return@Canvas
        drawCircle(color.copy(alpha=.22f),radius,center,style=Stroke(1.dp.toPx()))
        drawLine(color.copy(alpha=.22f),center-Offset(radius,0f),center+Offset(radius,0f),1.dp.toPx())
        drawLine(color.copy(alpha=.22f),center-Offset(0f,radius),center+Offset(0f,radius),1.dp.toPx())
        drawCircle(color,2.dp.toPx(),center)
        points.filter {it.distance<=extent}.forEach {point->
            val a=Math.toRadians(point.bearing)
            val r=(point.distance/extent*radius).toFloat()
            val at=center+Offset(sin(a).toFloat()*r,-cos(a).toFloat()*r)
            val ink=if(point.risk in setOf(AisRiskLevel.WARNING,AisRiskLevel.URGENT))Color(0xFFFFB46B)else color
            if(point.kind in setOf(AisEntityKind.CLASS_A,AisEntityKind.CLASS_B,AisEntityKind.LONG_RANGE))
                drawTileBoat(at,point.heading?.takeIf(Double::isFinite)?.toFloat(),ink,3.5.dp.toPx())
            else {
                val r=3.dp.toPx()
                val symbol=Path().apply {moveTo(at.x,at.y-r);lineTo(at.x+r,at.y);lineTo(at.x,at.y+r);lineTo(at.x-r,at.y);close()}
                drawPath(symbol,ink,style=Stroke(1.dp.toPx()))
            }
        }
    }
}
private fun DrawScope.drawTileBoat(center:Offset,heading:Float?,color:Color,radius:Float) {
    if(heading==null) {drawCircle(color,radius*.65f,center,style=Stroke(1.2.dp.toPx()));return}
    rotate(heading,center) {
        val path=Path().apply {
            moveTo(center.x,center.y-radius*1.4f);lineTo(center.x+radius*.65f,center.y+radius)
            lineTo(center.x-radius*.65f,center.y+radius);close()
        }
        drawPath(path,color)
    }
}
