package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.data.Fix
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.yokuli.runtime.contract.navigation.*
import com.yokuli.runtime.contract.planning.*
import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.anchorwatch.domain.vessel.VesselDataQuality
import com.yokuli.anchorwatch.domain.vessel.VesselDataSource
import com.yokuli.runtime.marine.navigation.NavigationGeometry
import kotlin.math.*

private const val EARTH_METERS = 6_371_008.8
private const val ARRIVAL_NEAR_METERS = 50.0

/** 同一航线 ID 的收藏可继续编辑；导航和目标索引始终使用开始时冻结的版本。 */
internal fun chartRoute(os:OsStore):Route? = os.maps.view("chart",os.center,os.zoom).previewRoute?.takeIf {it.id==os.displayedRouteId}
    ?: resolveChartRoute(os.displayedRouteId,os.activeRoute,os.routes)
internal fun chartIsNavigating(os:OsStore):Boolean = os.maps.view("chart",os.center,os.zoom).previewRoute?.takeIf {it.id==os.displayedRouteId}==null &&
    os.activeRoute!=null && (os.displayedRouteId==null || os.displayedRouteId==os.activeRouteId)
internal fun resolveChartRoute(displayedId:String?,active:Route?,saved:List<Route>):Route? =
    if(displayedId==null || displayedId==active?.id) active else saved.firstOrNull {it.id==displayedId}

internal fun cancelRouteDraft(os:OsStore) {
    os.draftRoute=emptyList();os.editingRouteId=null;os.editingRoute=false;os.showCrosshair=false
}

internal fun resumeOrCreateRouteDraft(os:OsStore) {
    if(os.draftRoute.isEmpty())os.editingRouteId=null
    os.editingRoute=true;os.showCrosshair=true;os.ruler=emptyList()
}

data class RouteGuidance(
    val index:Int, val target:GeoPoint, val distanceMeters:Double?, val remainingMeters:Double?,
    val bearingTrue:Double?, val offsetMeters:Double?, val offsetSide:Int,
    val outsideSegment:Boolean, val nearTarget:Boolean, val source:String?, val accuracy:Double?
)

/** 仅供未执行路线预览；执行页面统一读取 currentRouteGuidance。 */
fun routeGuidance(route:Route,index:Int,fix:Fix?,now:Long):RouteGuidance? {
    if(route.points.isEmpty()) return null
    val at=index.coerceIn(route.points.indices)
    val session=NavigationSession("preview",1,NavigationSource.LOCAL,route.navigationSnapshot(),at,NavigationPhase.ACTIVE,
        approachOrigin=fix?.point?.let {NavigationPoint(it.lat,it.lon)},approachTargetIndex=0,startedAtUtcMillis=0,updatedAtUtcMillis=0)
    val observation=fix?.let {NavigationFixSnapshot(NavigationPoint(it.point.lat,it.point.lon),it.elapsed,it.utc,it.source,it.source,it.fresh(now),it.accuracy,
        it.freshSpeed(now)?.times(.5144444444),it.speedElapsed,it.freshCourse(now),it.courseElapsed)}
    return NavigationGeometry.guidance(session,observation,now,System.currentTimeMillis()).toRouteGuidance(at)
}
private fun NavigationGuidance.toRouteGuidance(index:Int):RouteGuidance? {
    val point=targetPosition?:return null
    return RouteGuidance(index,GeoPoint(point.lat,point.lon),distanceAlongRouteToTargetMeters?:distanceMeters,remainingMeters,steeringBearingTrueDegrees?:bearingTrueDegrees,
        crossTrackMeters?.let(::abs),when { (crossTrackMeters?:0.0)>1->1;(crossTrackMeters?:0.0)< -1-> -1;else->0 },
        segmentRelation in setOf(NavigationSegmentRelation.BEFORE,NavigationSegmentRelation.AFTER),nearTarget,positionSource,positionAccuracyMeters)
}
fun currentRouteGuidance(os:OsStore):RouteGuidance? = os.navigationState.guidance?.toRouteGuidance(os.navigationState.session?.targetIndex?:0)

internal fun navigationFailure(os:OsStore,receipt:NavigationReceipt):String = when(receipt.reason) {
    "NAVIGATION_CHANGED"->os.t("导航已在另一处改变，请重新打开操作。","Navigation changed elsewhere. Reopen these controls.")
    "REPLAN_START_MOVED"->os.t("船位已移动，请从当前船位重新规划。","Your position moved. Replan from the current position.")
    "EXTERNAL_DEVICE_OWNS_ROUTE"->os.t("先结束外部设备导航，再启用本地航线。","End device navigation before starting a local route.")
    "POSITION_REQUIRED"->os.t("收到更新船位后才能继续导航。","Resume when a current position arrives.")
    "NAVIGATION_READ_BLOCKED"->os.t("原导航记录读取失败，写入已暂停。","The navigation record could not be read; writes are paused.")
    "EXTERNAL_SOURCE_NOT_FOUND"->os.t("设备目标已不在来源列表，请重新选择。","The external target source is no longer listed. Select it again.")
    else->os.t("导航没有更改，请重试。","Navigation was not changed. Try again.")
}
private class NavigationOperation(private val os:OsStore) {
    var pending by mutableStateOf(false); private set
    var failure by mutableStateOf<NavigationReceipt?>(null); private set
    private var retry:Pair<NavigationCommand,()->Unit>?=null
    fun submit(command:NavigationCommand,onSaved:()->Unit={}) {
        if(pending)return
        pending=true;failure=null;retry=command to onSaved
        os.scope.launch {
            try { val result=os.commitNavigation(command)
                if(result.result==NavigationResult.SAVED){retry=null;onSaved()}else failure=result
            } finally {pending=false}
        }
    }
    fun retry(){retry?.let {submit(it.first,it.second)}}
}
@Composable private fun NavigationFailure(os:OsStore,operation:NavigationOperation) {
    operation.failure?.let {receipt->
        Label(navigationFailure(os,receipt),13,LocalMetro.current.accentText)
        if(receipt.result==NavigationResult.FAILED)MetroButton(os.t("重试保存","Retry save"),{operation.retry()},enabled=!operation.pending)
    }
}
private fun presentNavigation(os:OsStore,route:Route,index:Int,fix:Fix?,now:Long,origin:String) {
    if(os.page!=origin)return
    os.maps.view("chart",os.center,os.zoom).apply {previewRoute=null;selectedPlaceId=null;selectedAisMmsi=null}
    os.displayedRouteId=route.id;os.editingRoute=false;os.ruler=emptyList();os.showCrosshair=false
    val point=fix?.takeIf {it.fresh(now)}?.point ?: route.points[index]
    os.fly(point,os.zoom.coerceAtLeast(12.0));os.follow=fix?.fresh(now)==true
    if(os.shell.appForPage(os.page)?.app!=AppId.CHART)os.openLinked("chart")
}
fun beginNavigation(os:OsStore,route:Route,index:Int,fix:Fix?,now:Long) {
    if(route.points.isEmpty())return
    val origin=os.page;val target=index.takeIf{it in route.targetIndices}?:route.targetIndices.first()
    val command=os.navigationCommand(NavigationAction.START,targetIndex=target,route=route.navigationSnapshot())
    os.scope.launch {val receipt=os.commitNavigation(command)
        if(receipt.result==NavigationResult.SAVED)presentNavigation(os,route,target,fix,now,origin)
        else os.notify(navigationFailure(os,receipt),navigationFailure(os,receipt),app=AppId.CHART,severity=NoticeSeverity.WARNING)}
}
fun endNavigation(os:OsStore,arrived:Boolean=false) {
    val command=os.navigationCommand(if(arrived)NavigationAction.ARRIVE else NavigationAction.END)
    os.scope.launch {val receipt=os.commitNavigation(command)
        if(receipt.result==NavigationResult.SAVED){os.maps.view("chart",os.center,os.zoom).previewRoute=null;os.displayedRouteId=null;os.follow=false}
        else os.notify(navigationFailure(os,receipt),navigationFailure(os,receipt),app=AppId.CHART,severity=NoticeSeverity.WARNING)}
}
private fun advanceNavigationTarget(os:OsStore,route:Route,expectedIndex:Int,expectedSession:NavigationSession?):Boolean {
    if(os.activeRouteId!=route.id||os.navigationState.session?.targetIndex!=expectedIndex||expectedIndex>=route.targetIndices.last())return false
    val command=os.navigationCommand(NavigationAction.ADVANCE).copy(expectedSessionId=expectedSession?.id,expectedRevision=expectedSession?.revision)
    os.scope.launch {val receipt=os.commitNavigation(command)
        if(receipt.result==NavigationResult.SAVED){os.displayedRouteId=route.id;os.maps.view("chart",os.center,os.zoom).previewRoute=null}
        else os.notify(navigationFailure(os,receipt),navigationFailure(os,receipt),app=AppId.CHART,severity=NoticeSeverity.WARNING)}
    return true
}

@Composable fun liveNavigationFix(os:OsStore):Pair<Fix?,Long> {
    val data by os.hub.state.collectAsState()
    val now=rememberMarineClock()
    return data.fix(os.positionSource) to now
}

fun offsetLabel(os:OsStore,g:RouteGuidance):String = when {
    g.distanceMeters==null -> os.t("等待新鲜船位","waiting for a fresh position")
    g.outsideSegment -> os.t("船位在航段延长线上，暂不显示横偏","Outside the active leg; cross-track offset is not shown")
    g.offsetMeters==null -> os.t("此航段无法计算横向偏离","cross-track offset unavailable for this leg")
    g.offsetSide>0 -> os.t("规划线右侧 ${os.formatDistance(g.offsetMeters)}","${os.formatDistance(g.offsetMeters)} right of the planned line")
    g.offsetSide<0 -> os.t("规划线左侧 ${os.formatDistance(g.offsetMeters)}","${os.formatDistance(g.offsetMeters)} left of the planned line")
    else -> os.t("距规划线 ${os.formatDistance(g.offsetMeters)}","${os.formatDistance(g.offsetMeters)} from the planned line")
}

@Composable fun RouteSketch(os:OsStore,route:Route,selected:Int?=null,ownPosition:GeoPoint?=null) {
    val c=LocalMetro.current
    Column(Modifier.fillMaxWidth().background(c.panel).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Label(if(route.points.size==1)os.t("直线引导预览 · 北向上","Direct guidance · north up")else os.t("航线预览 · 北向上","Route preview · north up"),12,c.muted)
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            if(route.points.isEmpty()) return@Canvas
            // Unwrap longitude so a route across ±180° does not span the entire sketch.
            var previous=route.points.first().lon
            val coords=(route.points+listOfNotNull(ownPosition)).map {p ->
                var lon=p.lon
                while(lon-previous>180) lon-=360
                while(lon-previous < -180) lon+=360
                previous=lon
                lon to -Math.toDegrees(asinh(tan(Math.toRadians(p.lat.coerceIn(-85.0,85.0)))))
            }
            val minX=coords.minOf {it.first};val maxX=coords.maxOf {it.first}
            val minY=coords.minOf {it.second};val maxY=coords.maxOf {it.second}
            val pad=12.dp.toPx();val width=(maxX-minX).coerceAtLeast(.000001);val height=(maxY-minY).coerceAtLeast(.000001)
            val scale=min((size.width-2*pad)/width,(size.height-2*pad)/height)
            fun at(index:Int):Offset {
                val p=coords[index]
                return Offset((size.width/2+(p.first-(minX+maxX)/2)*scale).toFloat(),(size.height/2+(p.second-(minY+maxY)/2)*scale).toFloat())
            }
            val path=Path();route.points.indices.forEach {i -> val p=at(i);if(i==0) path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)}
            drawPath(path,c.accent,style=Stroke(3.dp.toPx()))
            if(ownPosition!=null) {
                val own=at(coords.lastIndex);val target=at((selected?:0).coerceIn(route.points.indices))
                drawLine(c.fg.copy(alpha=.8f),own,target,1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),4.dp.toPx())))
                drawCircle(c.bg,7.dp.toPx(),own);drawCircle(c.fg,5.dp.toPx(),own,style=Stroke(2.dp.toPx()))
            }
            val visible=if(route.targetIndices.size<=50) route.targetIndices else listOf(route.targetIndices.first(),route.targetIndices.last())+listOfNotNull(selected?.takeIf {it in route.targetIndices})
            visible.distinct().forEach {i ->
                val p=at(i);drawCircle(c.bg,7.dp.toPx(),p);drawCircle(if(i==selected) c.accent else c.fg,4.dp.toPx(),p)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Label(if(ownPosition!=null)os.t("空心圆：本船","Open circle: your boat")else os.t("保存的航点","Saved waypoints"),12,c.muted)
            Label(if(route.points.size==1)os.t("实心点：目的地","Dot: destination")else os.t("目标 ${route.targetIndices.indexOf(selected?:route.targetIndices.firstOrNull()).plus(1).coerceAtLeast(1)} / ${route.targetIndices.size}","Target ${route.targetIndices.indexOf(selected?:route.targetIndices.firstOrNull()).plus(1).coerceAtLeast(1)} / ${route.targetIndices.size}"),12,c.muted)
        }
    }
}

/** 出发检查包含本船真实接入段；复用系统分析所有者，收藏航线的旧结果不能代替本次检查。 */
@Composable private fun NavigationApproachCheck(os:OsStore,route:Route,target:Int,now:Long,enabled:Boolean):PassageAnalysis? {
    val system=os.marine?.system ?: return null
    val marine by system.services.state.collectAsState()
    val workspace by system.analysis.state.collectAsState()
    val chartData by system.charts.state.collectAsState()
    val observation=marine.vesselData.position
    val point=observation.value?.let{GeoPoint(it.latitude,it.longitude)}?.takeIf{it.valid()}
    val trustworthy=point!=null&&observation.freshness==VesselDataFreshness.FRESH&&observation.quality!=VesselDataQuality.UNKNOWN&&
        observation.source!=VesselDataSource.DEMO&&observation.receivedElapsedRealtime?.let{now-it in 0..10_000}==true
    val prefix="approach:${route.id}:"
    val source=observation.sourceIdentity?.id?:observation.source.name
    // 和执行时 initialGeometry 一致：保留选中业务目标前尚需经过的形状点，不把圆弧变成目的地。
    val firstGeometry=if(route.navigationTargetIndices==null)target else (route.targetIndices.lastOrNull{it<target}?.plus(1)?:0).coerceAtMost(target)
    val request=remember(route,target,point,source,os.maps.selectedDatasetIds,marine.vesselSettings,os.maps.source,os.library.revision,workspace.avoidances) {
        point?.let{origin->
            val points=listOf(origin)+route.points.drop(firstGeometry)
            val targets=route.targetIndices.filter{it>=target}.map{it-firstGeometry+1}
            if(points.size<2||targets.isEmpty())null else os.planningRequest(points,"$prefix$target:$source",route.name,targets)
        }
    }
    val previous=workspace.analysis?.takeIf{it.request.route.id.startsWith(prefix)}
    val sameInput=previous!=null&&request!=null&&previous.request.route==request.route&&
        previous.request.datasetIds==request.datasetIds&&previous.request.vessel==request.vessel&&
        previous.request.backgroundKey==request.backgroundKey&&previous.request.avoidances==request.avoidances
    val sameData=previous?.let{analysis->analysis.dataRevision==chartData.revision&&
        analysis.datasetRevisions.all{(id,revision)->chartData.datasets.firstOrNull{it.id==id}?.let{
            it.revision==revision&&(analysis.severity==PassageSeverity.INSUFFICIENT||it.offlineReadable&&it.issue==null&&it.eligibility.allowsAnalysis(System.currentTimeMillis()))}==true}}==true
    val applicable=previous?.takeIf{trustworthy&&sameInput&&sameData&&it.complete}
    val busy=workspace.job?.phase in setOf(PassageJobPhase.LOADING,PassageJobPhase.ANALYZING,PassageJobPhase.SEARCHING)
    val ownJob=workspace.pendingRequest?.route?.id?.startsWith(prefix)==true
    var issueLimit by rememberSaveable(route.id,target){mutableIntStateOf(2)}
    val manageable=request?.route?.points?.size?.let{it in 2..2000}==true
    val c=LocalMetro.current
    val title=when {
        busy&&ownJob->os.t("正在检查船位接入与余下航线…","Checking your approach and remaining route…")
        applicable?.severity==PassageSeverity.NO_CONFLICT_FOUND->os.t("接入已检查 · 所选资料中未发现冲突","Approach checked · no conflict found in selected data")
        applicable?.severity==PassageSeverity.CONFLICT->os.t("接入检查发现冲突","Approach check found conflicts")
        applicable?.severity==PassageSeverity.REVIEW->os.t("接入检查有待核对项","Approach check needs review")
        applicable?.severity==PassageSeverity.INSUFFICIENT->os.t("接入检查资料不足","Insufficient evidence for the approach")
        previous!=null->os.t("接入结果已过期","Approach result is out of date")
        else->os.t("从船位接入尚未检查","Approach from your position has not been checked")
    }
    Label(title,14,if(applicable?.severity==PassageSeverity.NO_CONFLICT_FOUND)c.fg else c.accentText)
    when {
        !trustworthy->Label(os.t("收到已接受的新鲜船位后可检查；仍可手动开始并等待船位。","A current accepted position is needed to check; you can still start manually and wait for a fix."),12,c.muted)
        os.maps.selectedDatasetIds.isEmpty()->Label(os.t("地图尚未选择可分析的数据集，当前底图不能代替航行资料。仍可手动导航。","No analysis dataset is selected on Chart. A basemap is not navigational evidence. Manual guidance remains available."),12,c.muted)
        previous!=null&&applicable==null->Label(os.t("船位、起始目标、船型或地图资料已变化，重新检查后才能引用结果。","Your position, start target, vessel settings or chart data changed. Recheck before using this result."),12,c.muted)
        applicable!=null-> {
            Label(os.t("范围：当前船位接入所选目标，再沿保存的路径至终点。不会自动绕行。","Covers the approach from your position and the saved path to the destination. It does not create a detour."),12,c.muted)
            applicable.issues.take(issueLimit).forEach{issue->Label(issue.message.split(" / ").let{if(os.chinese)it.first()else it.last()},12,c.muted)}
            if(applicable.issues.size>issueLimit)MetroButton(os.t("展开其余 ${applicable.issues.size-issueLimit} 项","Show ${applicable.issues.size-issueLimit} more items"),{issueLimit+=10})
        }
    }
    if(request!=null&&!manageable)Label(os.t("这条完整接入航线过长，需要分段检查；手动导航不受影响。","This approach route is too detailed for one check. Check it in sections; manual guidance remains available."),12,c.muted)
    if(workspace.storageIssue!=null)Label(os.t("分析记录尚未保存：","Analysis record is not saved: ")+workspace.storageIssue,12,c.accentText)
    if(ownJob&&!busy&&workspace.job?.phase in setOf(PassageJobPhase.FAILED,PassageJobPhase.INTERRUPTED,PassageJobPhase.CANCELLED))
        Label(os.t("上次检查未完成，可重新检查。","The previous check did not finish. Check again."),12,c.muted)
    if(busy&&!ownJob)Label(os.t("另一项航线计算正在进行，完成后可检查接入。","Another passage calculation is running. Check your approach when it finishes."),12,c.muted)
    MetroButton(if(previous!=null)os.t("重新检查接入","Recheck approach")else os.t("检查船位接入","Check approach"),{
        if(request!=null)system.analysis.analyze(request.copy(requestId=uid(),departureUtc=System.currentTimeMillis()))
    },enabled=enabled&&!busy&&trustworthy&&manageable&&workspace.ready&&os.maps.selectedDatasetIds.isNotEmpty())
    return applicable
}

@Composable fun StartNavigationDialog(os:OsStore,route:Route,initialTarget:Int=0,onDismiss:()->Unit) {
    val (fix,now)=liveNavigationFix(os)
    val operation=remember(os,route.id){NavigationOperation(os)}
    val expectedSession=os.navigationState.session
    var target by rememberSaveable(route.id,initialTarget) {mutableIntStateOf(initialTarget.takeIf{it in route.targetIndices}?:route.targetIndices.firstOrNull()?:0)}
    var choosing by rememberSaveable(route.id){mutableStateOf(false)}
    val live=fix?.takeIf {it.fresh(now)&&it.point.valid()}
    val c=LocalMetro.current
    val voyage=os.marine?.voyage?.collectAsState()?.value
    val single=route.targetIndices.size==1
    val targetPoint=route.points.getOrNull(target)
    val nearest=live?.let {position->route.targetIndices.minByOrNull {distance(position.point,route.points[it])}}
    val canStartRecording=voyage!=null&&!voyage.active&&!voyage.commandPending&&live!=null&&os.positionSource in listOf("phone","nmea")
    if(com.yokuli.shell.compose.LocalInternalAppInputEnabled.current)AppDialog(onDismissRequest={if(!operation.pending)onDismiss()}) {
        AppDialogSurface {
            AppDialogTitle(if(route.points.size==1)os.t("前往这里","Go here")else os.t("开始沿线导航","Start route navigation"))
            Label(route.name,20,c.accentText)
            os.activeRoute?.takeIf {it.id!=route.id}?.let {
                Label(os.t("将替换当前导航：${it.name}","This replaces current navigation: ${it.name}"),15,c.muted)
            }
            RouteSketch(os,route,target,live?.point)
            if(targetPoint!=null)Label(live?.let {os.t("距本船 ","From your boat ")+os.formatDistance(distance(it.point,targetPoint))} ?: os.formatCoordinates(targetPoint),20)
            if(!single) {
                MenuRow(os.t("先到航点 ${route.targetIndices.indexOf(target)+1}","Start with waypoint ${route.targetIndices.indexOf(target)+1}"),os.t("按保存顺序到终点 · 更改起点","Then follow the saved order · change start")){choosing=!choosing}
                if(route.targetIndices.indexOf(target)>0)Label(os.t("本次会跳过前 ${route.targetIndices.indexOf(target)} 个航点。","The first ${route.targetIndices.indexOf(target)} waypoints will be skipped."),12,c.accentText)
                if(choosing) {
                    if(nearest!=null)Label(os.t("最近的是航点 ${route.targetIndices.indexOf(nearest)+1}；是否跳过前面的航段由你决定。","Waypoint ${route.targetIndices.indexOf(nearest)+1} is nearest; you decide whether to skip earlier legs."),12,c.muted)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max=190.dp)) {
                        itemsIndexed(route.targetIndices) {ordinal,i ->val p=route.points[i];ChoiceRow(os.t("航点 ${ordinal+1}","Waypoint ${ordinal+1}"),i==target,
                            live?.let {os.formatDistance(distance(it.point,p))} ?: os.formatCoordinates(p)){target=i;choosing=false}}
                    }
                }
            }
            val approachAnalysis=NavigationApproachCheck(os,route,target,now,!operation.pending)
            Label(os.t("这里只是几何引导，不会自动避开浅水或障碍。","This is geometric guidance; it does not route around shallow water or obstacles."),12,c.muted)
            if(live==null)MenuRow(os.t("等待更新船位","Waiting for a position update"),os.t("检查船位来源","Check position source")){os.openLinked("data_center:source/POSITION")}
            if(voyage?.active==true)Label(if(voyage.phase==com.yokuli.runtime.contract.VoyagePhase.PAUSED)os.t("现有记录保持暂停；导航不会改变它。","Existing recording stays paused; navigation does not change it.")else os.t("航行记录正在进行，将继续使用本次记录。","Your current voyage recording continues."),12,c.muted)
            else AppCheckRow(os.t("同时开始航行记录","Also start voyage recording"),os.recordWhenNavigating&&canStartRecording,
                if(canStartRecording)os.t("记住这次选择；导航与记录可分别结束。","Remember this choice; guidance and recording end separately.")else os.t("收到船位后，可从海图单独开始记录。","Start recording separately from Chart once position is available."),enabled=canStartRecording&&!operation.pending){os.recordWhenNavigating=!os.recordWhenNavigating;os.save()}
            NavigationFailure(os,operation)
            if(os.navigationState.storageIssue!=null) {
                Label(os.t("导航存储需要恢复后才能提交。","Restore navigation storage before submitting."),13,c.accentText)
                MetroButton(os.t("重新读取","Reload record"),{os.marine?.system?.navigation?.retryRead()})
            }
            MetroButton(if(operation.pending)os.t("正在保存…","Saving…")else if(live==null)os.t("手动开始并等待船位","Start manually and wait for position")else if(approachAnalysis?.severity==PassageSeverity.NO_CONFLICT_FOUND)os.t("开始导航","Start navigation")else os.t("手动开始导航","Start manual guidance"),{
                val origin=os.page
                val selected=target
                val planSpeed=os.marine?.services?.state?.value?.vesselSettings?.plannedSpeedMetersPerSecond
                val command=os.navigationCommand(NavigationAction.START,targetIndex=selected,route=route.navigationSnapshot(),settings=NavigationSettings(plannedSpeedMetersPerSecond=planSpeed),analysisReference=approachAnalysis?.key).copy(expectedSessionId=expectedSession?.id,expectedRevision=expectedSession?.revision)
                operation.submit(command){
                    if(os.recordWhenNavigating&&canStartRecording)os.marine?.startRecording(route.name)
                    presentNavigation(os,route,selected,fix,now,origin);onDismiss()
                }
            },primary=true,enabled=!operation.pending&&os.navigationState.ready&&route.points.isNotEmpty()&&route.points.all(GeoPoint::valid))
            MetroButton(os.t("取消","Cancel"),onDismiss,enabled=!operation.pending)
        }
    }
}

@Composable fun ChartNavigationCard(os:OsStore,fix:Fix?,now:Long) {
    val c=LocalMetro.current
    val inputEnabled=com.yokuli.shell.compose.LocalInternalAppInputEnabled.current
    val active=os.activeRoute
    val route=chartRoute(os) ?: return
    var start by rememberSaveable(route.id) {mutableStateOf(false)}
    var manage by rememberSaveable(route.id) {mutableStateOf(false)}
    var confirmation by remember(route.id){mutableStateOf<Pair<Int,NavigationCommand>?>(null)}
    val expectedSession=os.navigationState.session
    val operation=remember(os,route.id){NavigationOperation(os)}
    val navigating=chartIsNavigating(os)
    val guidance=if(navigating) currentRouteGuidance(os) else null
    Column(Modifier.fillMaxWidth().background(c.bg.copy(alpha=.96f)).padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(enabled=inputEnabled) {os.open("route:${route.id}")}) {
                Label(if(navigating) os.t("${route.name} · 目标 ${route.targetIndices.indexOf(guidance?.index).plus(1).coerceAtLeast(1)}/${route.targetIndices.size}","${route.name} · target ${route.targetIndices.indexOf(guidance?.index).plus(1).coerceAtLeast(1)}/${route.targetIndices.size}")
                    else os.t("已选航线 · ${route.name}","selected route · ${route.name}"),15,c.accent,maxLines=1)
                if(guidance!=null) {
                    Label(guidance.distanceMeters?.let {"${os.formatDistance(it)}   ${os.formatBearing(guidance.bearingTrue)}T"} ?: os.t("等待船位","waiting for position"),27)
                } else Label("${os.formatDistance(route.length)} · ${route.targetIndices.size} "+os.t("个航点","waypoints"),21)
            }
            IconAction(if(navigating) "more" else "play",if(navigating) os.t("导航","navigate") else os.t("开始","start"),{if(navigating) manage=true else start=true})
        }
        if(guidance!=null) {
            Label(guidance.remainingMeters?.let {os.t("剩余 ${os.formatDistance(it)} · ${offsetLabel(os,guidance)}","${os.formatDistance(it)} remaining · ${offsetLabel(os,guidance)}")}
                ?: os.t("距离与偏离暂停更新 · 点此检查来源","Distance and offset paused · check source"),12,c.muted,
                if(guidance.distanceMeters==null)Modifier.clickable(enabled=inputEnabled) {os.openLinked("data_center:source/POSITION")}else Modifier)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                if(guidance.nearTarget)Label(os.t("已到目标附近","Near the target"),12,c.muted,Modifier.weight(1f))else Spacer(Modifier.weight(1f))
                MetroButton(when {guidance.index==route.targetIndices.last()->os.t("确认到达","Confirm arrival");guidance.nearTarget->os.t("已到达，下一点","Arrived · next point");else->os.t("下一航点","Next waypoint")},{
                    if(guidance.nearTarget&&guidance.index<route.targetIndices.last())advanceNavigationTarget(os,route,guidance.index,expectedSession)
                    else confirmation=guidance.index to os.navigationCommand(if(guidance.index==route.targetIndices.last())NavigationAction.ARRIVE else NavigationAction.ADVANCE).copy(expectedSessionId=expectedSession?.id,expectedRevision=expectedSession?.revision)
                })
            }
        } else if(active!=null) Label(os.t("返回当前导航：${active.name}","return to navigation: ${active.name}"),13,c.muted,Modifier.clickable(enabled=inputEnabled) {
            os.maps.view("chart",os.center,os.zoom).previewRoute=null;os.displayedRouteId=active.id;os.showCrosshair=false;os.save()
        })
    }
    if(start) StartNavigationDialog(os,route) {start=false}
    if(manage) NavigationActionsDialog(os,route) {manage=false}
    confirmation?.takeIf {inputEnabled}?.let {(expected,command)->AppDialog(onDismissRequest={if(!operation.pending)confirmation=null}){AppDialogSurface {
        val final=expected==route.targetIndices.last()
        AppDialogTitle(if(final)os.t("到达并结束导航？","Arrive and end navigation?")else os.t("跳过当前航点？","Skip the current waypoint?"))
        Label(if(final)os.t("由你确认已到达目的地。航行记录会继续独立运行。","Confirm you have arrived. Voyage recording continues independently.")else os.t("尚未确认到达航点 ${route.targetIndices.indexOf(expected)+1}。继续将直接引导至航点 ${route.targetIndices.indexOf(expected)+2}。","Arrival at waypoint ${route.targetIndices.indexOf(expected)+1} is not confirmed. Continue directly to waypoint ${route.targetIndices.indexOf(expected)+2}."),15)
        MetroButton(if(final)os.t("确认到达并结束","Confirm arrival and finish")else os.t("前往下一航点","Go to next waypoint"),{
            operation.submit(command){confirmation=null}
        },primary=true)
        NavigationFailure(os,operation)
        MetroButton(os.t("继续当前导航","Keep current guidance"),{confirmation=null},enabled=!operation.pending)
    }}}
}

@Composable fun NavigationActionsDialog(os:OsStore,route:Route,onDismiss:()->Unit) {
    val route=os.activeRoute?.takeIf {it.id==route.id} ?: return
    val operation=remember(os,route.id){NavigationOperation(os)}
    val expectedSession=os.navigationState.session
    fun command(action:NavigationAction,targetIndex:Int?=null,settings:NavigationSettings?=null)=
        os.navigationCommand(action,targetIndex=targetIndex,settings=settings).copy(expectedSessionId=expectedSession?.id,expectedRevision=expectedSession?.revision)
    val inputEnabled=com.yokuli.shell.compose.LocalInternalAppInputEnabled.current
    LaunchedEffect(route.id,inputEnabled) {if(inputEnabled){os.maps.view("chart",os.center,os.zoom).previewRoute=null;os.displayedRouteId=route.id}}
    val (fix,now)=liveNavigationFix(os)
    val guidance=currentRouteGuidance(os) ?: return
    val c=LocalMetro.current
    var choose by rememberSaveable(route.id) {mutableStateOf(false)}
    var stopping by rememberSaveable(route.id) {mutableStateOf(false)}
    var arrival by rememberSaveable(route.id) {mutableStateOf(false)}
    if(inputEnabled)AppDialog(onDismissRequest={if(!operation.pending)onDismiss()}) {
        AppDialogSurface {
            AppDialogTitle(if(stopping) os.t("结束导航？","End navigation?") else if(choose) os.t("切换目标","Change target") else os.t("正在导航","Navigating"))
            Label(route.name,20,c.accent)
            NavigationFailure(os,operation)
            if(stopping) {
                Label(if(arrival) os.t("确认已到达终点后结束导航。","Confirm arrival at the final waypoint and end navigation.")
                    else os.t("结束当前引导，保留已保存的航线。航行记录由记录按钮单独控制。","End current guidance and keep the saved route. Track recording is controlled separately."),19)
                MetroButton(if(arrival) os.t("确认到达并结束","confirm arrival and finish") else os.t("结束导航","end navigation"),{operation.submit(command(if(arrival)NavigationAction.ARRIVE else NavigationAction.END)){os.displayedRouteId=null;onDismiss()}},primary=true,enabled=!operation.pending)
                MetroButton(os.t("继续导航","keep navigating"),{stopping=false;arrival=false})
            } else if(choose) {
                Label(os.t("选择后直接前往该点，再按顺序继续。","Head to the selected waypoint, then continue in order."),16,c.muted)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=300.dp)) {
                    itemsIndexed(route.targetIndices) {ordinal,i ->val p=route.points[i];MenuRow(os.t("航点 ${ordinal+1}","waypoint ${ordinal+1}"),
                        if(i==guidance.index) os.t("当前目标","current target") else fix?.takeIf {it.fresh(now)}?.let {os.formatDistance(distance(it.point,p))}) {
                        operation.submit(command(NavigationAction.SELECT_TARGET,targetIndex=i)){os.displayedRouteId=route.id;os.showCrosshair=false;os.maps.view("chart",os.center,os.zoom).previewRoute=null;onDismiss()}
                    } }
                }
                MetroButton(os.t("返回","back"),{choose=false})
            } else {
                Label(os.t("当前目标 ${route.targetIndices.indexOf(guidance.index)+1} / ${route.targetIndices.size}","current target ${route.targetIndices.indexOf(guidance.index)+1} / ${route.targetIndices.size}"),18)
                Label(guidance.distanceMeters?.let {os.formatDistance(it)} ?: "—",44,c.accent)
                Label(guidance.bearingTrue?.let {os.t("沿线引导 ${os.formatBearing(it)}T","route guidance ${os.formatBearing(it)}T")} ?: os.t("等待新鲜船位","waiting for a fresh position"),20)
                Label(guidance.remainingMeters?.let {os.t("沿后续航点剩余 ${os.formatDistance(it)}","${os.formatDistance(it)} remaining via subsequent waypoints")} ?: os.t("剩余距离不可用","remaining distance unavailable"),16,c.muted)
                Label(offsetLabel(os,guidance),17,c.muted)
                guidance.accuracy?.let {Label(os.t("船位精度约 ±${os.formatLength(it)}","position accuracy approximately ±${os.formatLength(it)}"),13,c.muted)}
                if(guidance.distanceMeters==null) MetroButton(os.t("检查船位来源","Check position source"),{os.openLinked("data_center:source/POSITION")})
                if(guidance.nearTarget) {
                    Label(os.t("目标附近（${os.formatLength(ARRIVAL_NEAR_METERS)} 内）。到达由你确认。","Within ${os.formatLength(ARRIVAL_NEAR_METERS)} of the target. You confirm arrival."),16,c.accent)
                    MetroButton(if(guidance.index==route.targetIndices.last()) os.t("确认到达终点","confirm final arrival") else os.t("已到达，前往下一点","arrived, go to next"),{
                        if(guidance.index==route.targetIndices.last()) {arrival=true;stopping=true}
                        else operation.submit(command(NavigationAction.ADVANCE)){onDismiss()}
                    },primary=true)
                }
                val session=os.navigationState.session
                if(session!=null) {
                    if(session.phase!=NavigationPhase.ACTIVE)MetroButton(os.t("继续这次导航","Resume navigation"),{operation.submit(command(NavigationAction.RESUME))},primary=true,enabled=!operation.pending)
                    else MetroButton(os.t("暂停引导","Pause guidance"),{operation.submit(command(NavigationAction.PAUSE))},enabled=!operation.pending)
                    AppCheckRow(os.t("到达后自动前往下一点","Advance on arrival"),session.settings.advanceMode==WaypointAdvanceMode.AUTOMATIC,
                        os.t("仅在船位准确、连续进入到达范围后推进一次。","Advances once after accurate consecutive positions confirm arrival."),enabled=!operation.pending){
                        operation.submit(command(NavigationAction.SETTINGS,settings=session.settings.copy(advanceMode=if(session.settings.advanceMode==WaypointAdvanceMode.MANUAL)WaypointAdvanceMode.AUTOMATIC else WaypointAdvanceMode.MANUAL)))
                    }
                    Label(os.t("预计到达依据","Estimate using"),13,c.muted)
                    ChoiceRow(os.t("当前朝目标的有效航速","Current progress toward target"),session.settings.etaBasis==NavigationEtaBasis.CURRENT_PROGRESS){
                        operation.submit(command(NavigationAction.SETTINGS,settings=session.settings.copy(etaBasis=NavigationEtaBasis.CURRENT_PROGRESS)))
                    }
                    session.settings.plannedSpeedMetersPerSecond?.let {speed->
                        ChoiceRow(os.t("计划航速 · ","Planned speed · ")+os.formatSpeed(speed/.5144444444),session.settings.etaBasis==NavigationEtaBasis.PLAN_SPEED){
                            operation.submit(command(NavigationAction.SETTINGS,settings=session.settings.copy(etaBasis=NavigationEtaBasis.PLAN_SPEED)))
                        }
                    }
                    val estimate=os.navigationState.guidance
                    estimate?.etaRouteUtcMillis?.let {Label(os.t("预计到达 ","Estimated arrival ")+java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it)),16,c.muted)}
                    os.navigationState.backgroundIssue?.let {Label(os.t("后台导航受系统限制，请保持应用打开。","Android restricted background navigation. Keep the app open."),13,c.accentText)}
                }
                MetroButton(os.t("切换目标航点","change target waypoint"),{choose=true})
                MetroButton(os.t("查看整条航线","show entire route"),{os.maps.view("chart",os.center,os.zoom).previewRoute=null;os.displayedRouteId=route.id;os.fitRequest=route.points;os.showCrosshair=false;if(os.shell.appForPage(os.page)?.app!=AppId.CHART)os.openLinked("chart");onDismiss()})
                MetroButton(os.t("结束导航","end navigation"),{stopping=true})
                MetroButton(os.t("关闭","close"),onDismiss)
            }
        }
    }
}

/** 外部导航是明确选择的一份目标来源；不会收到一条NMEA句子就覆盖本地航线。 */
@Composable fun ExternalNavigationDialog(os:OsStore,onDismiss:()->Unit) {
    val nav=os.navigationState
    val operation=remember(os){NavigationOperation(os)}
    val session=nav.session?.takeIf {it.ongoing}
    fun command(action:NavigationAction,externalSourceId:String?=null)=os.navigationCommand(action,externalSourceId=externalSourceId)
        .copy(expectedSessionId=nav.session?.id,expectedRevision=nav.session?.revision)
    val c=LocalMetro.current
    AppDialog(onDismissRequest={if(!operation.pending)onDismiss()}) { AppDialogSurface {
        AppDialogTitle(os.t("设备上的导航","Navigation from a device"))
        Label(os.t("跟随你选定设备的目标。切换会替换当前引导；航点由该设备推进。","Follow the selected device's destination. This replaces current guidance; the device advances its waypoints."),15,c.muted)
        if(nav.externalSources.isEmpty())Label(os.t("还没有收到设备的目标信息。请先在船联网连接导航仪。","No device destination has arrived. Connect a navigator in Boat Network first."),16)
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max=260.dp)) {
            itemsIndexed(nav.externalSources){_,source->
                ChoiceRow(source.name,session?.source==NavigationSource.EXTERNAL_NMEA&&session.externalSourceId==source.id,
                    if(source.current)os.t("正在接收目标","Receiving destination")else os.t("等待目标更新","Waiting for destination update")) {
                    operation.submit(command(NavigationAction.SELECT_EXTERNAL,externalSourceId=source.id)){onDismiss()}
                }
            }
        }
        if(session?.source==NavigationSource.EXTERNAL_NMEA) {
            nav.guidance?.let {guide->
                Label(guide.targetName ?: os.t("当前目标","Current destination"),20,c.accentText)
                Label(listOfNotNull(guide.distanceMeters?.let(os::formatDistance),guide.bearingTrueDegrees?.let {os.formatBearing(it)+"T"}).joinToString(" · ").ifBlank {os.t("等待目标更新","Waiting for destination update")},20)
                if(guide.targetPosition==null)Label(os.t("设备未提供目标坐标，地图不会推测一个位置。","The device did not provide destination coordinates; no position is inferred on the map."),13,c.muted)
            }
            MetroButton(if(session.phase==NavigationPhase.ACTIVE)os.t("暂停引导","Pause guidance")else os.t("继续导航","Resume navigation"),{
                operation.submit(command(if(session.phase==NavigationPhase.ACTIVE)NavigationAction.PAUSE else NavigationAction.RESUME))
            },enabled=!operation.pending)
            MetroButton(os.t("结束导航","End navigation"),{operation.submit(command(NavigationAction.END)){onDismiss()}},enabled=!operation.pending)
        }
        NavigationFailure(os,operation)
        MetroButton(os.t("关闭","Close"),onDismiss,enabled=!operation.pending)
    }}
}
