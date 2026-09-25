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

/** Measurements are relative to the user's own planned segments, never a navigability verdict. */
fun routeGuidance(route:Route,index:Int,fix:Fix?,now:Long):RouteGuidance? {
    if(route.points.isEmpty()) return null
    val current=index.coerceIn(route.points.indices)
    val target=route.points[current]
    val live=fix?.takeIf {it.fresh(now) && it.point.valid()}
    val toTarget=live?.let {distance(it.point,target)}
    val accuracy=live?.accuracy?.takeIf {it.isFinite() && it>=0}
    var offset:Double?=null;var side=0;var outside=false
    if(live!=null && current>0) {
        val previous=route.points[current-1]
        val length=distance(previous,target)
        if(length>=1.0 && length<PI*EARTH_METERS*.99) {
            val angular=distance(previous,live.point)/EARTH_METERS
            val angle=Math.toRadians(bearing(previous,live.point)-bearing(previous,target))
            val cross=asin((sin(angular)*sin(angle)).coerceIn(-1.0,1.0))*EARTH_METERS
            val along=atan2(sin(angular)*cos(angle),cos(angular))*EARTH_METERS
            outside=along<0 || along>length
            offset=when {along<0 -> distance(previous,live.point);along>length -> toTarget;else -> abs(cross)}
            side=when {cross>1 -> 1;cross < -1 -> -1;else -> 0}
        }
    }
    val rest=route.points.drop(current).zipWithNext().sumOf {distance(it.first,it.second)}
    return RouteGuidance(current,target,toTarget,toTarget?.plus(rest),
        live?.takeIf {toTarget!=null && toTarget>=.5}?.let {bearing(it.point,target)},offset,side,outside,
        toTarget!=null && toTarget<=ARRIVAL_NEAR_METERS && (accuracy==null || accuracy<=ARRIVAL_NEAR_METERS),
        live?.source,accuracy)
}

fun beginNavigation(os:OsStore,route:Route,index:Int,fix:Fix?,now:Long) {
    if(route.points.isEmpty()) return
    os.navigationRoute=route.copy(points=route.points.toList())
    os.maps.view("chart",os.center,os.zoom).apply {previewRoute=null;selectedPlaceId=null;selectedAisMmsi=null}
    os.activeRouteId=route.id;os.displayedRouteId=route.id;os.routeLeg=index.coerceIn(route.points.indices)
    os.editingRoute=false;os.ruler=emptyList();os.showCrosshair=false
    val point=fix?.takeIf {it.fresh(now)}?.point ?: route.points[os.routeLeg]
    os.fly(point,os.zoom.coerceAtLeast(12.0));os.follow=fix?.fresh(now)==true
    os.save();if(os.shell.appForPage(os.page)?.app!=AppId.CHART)os.openLinked("chart")
}

fun endNavigation(os:OsStore,arrived:Boolean=false) {
    os.maps.view("chart",os.center,os.zoom).previewRoute=null
    os.activeRouteId=null;os.navigationRoute=null;os.displayedRouteId=null;os.routeLeg=0;os.follow=false;os.save()
    if(arrived) os.notify("已确认到达，导航结束。","Arrival confirmed. Navigation ended.")
    else os.notify("导航已结束。","Navigation ended.")
}

/** 只推进用户刚才看到的那个目标；另一页面已改目标时，旧面板不能多跳一段。 */
private fun advanceNavigationTarget(os:OsStore,route:Route,expectedIndex:Int):Boolean {
    if(os.activeRouteId!=route.id||os.routeLeg!=expectedIndex||expectedIndex>=route.points.lastIndex)return false
    os.routeLeg=expectedIndex+1;os.displayedRouteId=route.id;os.maps.view("chart",os.center,os.zoom).previewRoute=null;os.save()
    return true
}

@Composable fun liveNavigationFix(os:OsStore):Pair<Fix?,Long> {
    val data by os.hub.state.collectAsState()
    val now=rememberMarineClock()
    return data.fix(os.positionSource) to now
}

fun offsetLabel(os:OsStore,g:RouteGuidance):String = when {
    g.distanceMeters==null -> os.t("等待新鲜船位","waiting for a fresh position")
    g.index==0 -> os.t("先直线前往第一个目标","direct approach to the first target")
    g.offsetMeters==null -> os.t("此航段无法计算横向偏离","cross-track offset unavailable for this leg")
    g.outsideSegment -> os.t("距规划航段 ${os.formatDistance(g.offsetMeters)}","${os.formatDistance(g.offsetMeters)} from the planned segment")
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
            val visible=if(route.points.size<=50) route.points.indices.toList() else listOf(0,route.points.lastIndex)+listOfNotNull(selected?.takeIf {it in route.points.indices})
            visible.distinct().forEach {i ->
                val p=at(i);drawCircle(c.bg,7.dp.toPx(),p);drawCircle(if(i==selected) c.accent else c.fg,4.dp.toPx(),p)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Label(if(ownPosition!=null)os.t("空心圆：本船","Open circle: your boat")else os.t("保存的航点","Saved waypoints"),12,c.muted)
            Label(if(route.points.size==1)os.t("实心点：目的地","Dot: destination")else os.t("目标 ${(selected?:0)+1} / ${route.points.size}","Target ${(selected?:0)+1} / ${route.points.size}"),12,c.muted)
        }
    }
}

@Composable fun StartNavigationDialog(os:OsStore,route:Route,initialTarget:Int=0,onDismiss:()->Unit) {
    val (fix,now)=liveNavigationFix(os)
    var target by rememberSaveable(route.id,initialTarget) {mutableIntStateOf(initialTarget.coerceIn(0,(route.points.size-1).coerceAtLeast(0)))}
    var choosing by rememberSaveable(route.id){mutableStateOf(false)}
    val live=fix?.takeIf {it.fresh(now)&&it.point.valid()}
    val c=LocalMetro.current
    val voyage=os.marine?.voyage?.collectAsState()?.value
    val single=route.points.size==1
    val targetPoint=route.points.getOrNull(target)
    val nearest=live?.let {position->route.points.indices.minByOrNull {distance(position.point,route.points[it])}}
    val canStartRecording=voyage!=null&&!voyage.active&&!voyage.commandPending&&live!=null&&os.positionSource in listOf("phone","nmea")
    if(com.yokuli.shell.compose.LocalInternalAppInputEnabled.current)AppDialog(onDismissRequest=onDismiss) {
        AppDialogSurface {
            AppDialogTitle(if(single)os.t("前往这里","Go here")else os.t("开始沿线导航","Start route navigation"))
            Label(route.name,20,c.accentText)
            os.activeRoute?.takeIf {it.id!=route.id}?.let {
                Label(os.t("将替换当前导航：${it.name}","This replaces current navigation: ${it.name}"),15,c.muted)
            }
            RouteSketch(os,route,target,live?.point)
            if(targetPoint!=null)Label(live?.let {os.t("距本船 ","From your boat ")+os.formatDistance(distance(it.point,targetPoint))} ?: os.formatCoordinates(targetPoint),20)
            if(!single) {
                MenuRow(os.t("先到航点 ${target+1}","Start with waypoint ${target+1}"),os.t("按保存顺序到终点 · 更改起点","Then follow the saved order · change start")){choosing=!choosing}
                if(target>0)Label(os.t("本次会跳过前 $target 个航点。","The first $target waypoints will be skipped."),12,c.accentText)
                if(choosing) {
                    if(nearest!=null)Label(os.t("最近的是航点 ${nearest+1}；是否跳过前面的航段由你决定。","Waypoint ${nearest+1} is nearest; you decide whether to skip earlier legs."),12,c.muted)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max=190.dp)) {
                        itemsIndexed(route.points) {i,p ->ChoiceRow(os.t("航点 ${i+1}","Waypoint ${i+1}"),i==target,
                            live?.let {os.formatDistance(distance(it.point,p))} ?: os.formatCoordinates(p)){target=i;choosing=false}}
                    }
                }
            }
            Label(os.t("这里只是几何引导，不会自动避开浅水或障碍。","This is geometric guidance; it does not route around shallow water or obstacles."),12,c.muted)
            if(live==null)MenuRow(os.t("等待更新船位","Waiting for a position update"),os.t("检查船位来源","Check position source")){os.openLinked("data_center:source/POSITION")}
            if(voyage?.active==true)Label(if(voyage.phase==com.yokuli.runtime.contract.VoyagePhase.PAUSED)os.t("现有记录保持暂停；导航不会改变它。","Existing recording stays paused; navigation does not change it.")else os.t("航行记录正在进行，将继续使用本次记录。","Your current voyage recording continues."),12,c.muted)
            else AppCheckRow(os.t("同时开始航行记录","Also start voyage recording"),os.recordWhenNavigating&&canStartRecording,
                if(canStartRecording)os.t("记住这次选择；导航与记录可分别结束。","Remember this choice; guidance and recording end separately.")else os.t("收到船位后，可从海图单独开始记录。","Start recording separately from Chart once position is available."),enabled=canStartRecording){os.recordWhenNavigating=!os.recordWhenNavigating;os.save()}
            MetroButton(if(live!=null) os.t("开始导航","Start navigation") else os.t("开始并等待船位","Start and wait for position"),{
                if(os.recordWhenNavigating&&canStartRecording)os.marine?.startRecording(route.name)
                beginNavigation(os,route,target,fix,now);onDismiss()
            },primary=true,enabled=route.points.isNotEmpty()&&route.points.all(GeoPoint::valid))
            MetroButton(os.t("取消","Cancel"),onDismiss)
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
    var confirmTarget by rememberSaveable(route.id){mutableStateOf<Int?>(null)}
    val navigating=chartIsNavigating(os)
    val guidance=if(navigating) routeGuidance(route,os.routeLeg,fix,now) else null
    Column(Modifier.fillMaxWidth().background(c.bg.copy(alpha=.96f)).padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(enabled=inputEnabled) {os.open("route:${route.id}")}) {
                Label(if(navigating) os.t("${route.name} · 目标 ${guidance?.index?.plus(1) ?: 1}/${route.points.size}","${route.name} · target ${guidance?.index?.plus(1) ?: 1}/${route.points.size}")
                    else os.t("已选航线 · ${route.name}","selected route · ${route.name}"),15,c.accent,maxLines=1)
                if(guidance!=null) {
                    Label(guidance.distanceMeters?.let {"${os.formatDistance(it)}   ${os.formatBearing(guidance.bearingTrue)}T"} ?: os.t("等待船位","waiting for position"),27)
                } else Label("${os.formatDistance(route.length)} · ${route.points.size} "+os.t("个航点","waypoints"),21)
            }
            IconAction(if(navigating) "more" else "play",if(navigating) os.t("导航","navigate") else os.t("开始","start"),{if(navigating) manage=true else start=true})
        }
        if(guidance!=null) {
            Label(guidance.remainingMeters?.let {os.t("剩余 ${os.formatDistance(it)} · ${offsetLabel(os,guidance)}","${os.formatDistance(it)} remaining · ${offsetLabel(os,guidance)}")}
                ?: os.t("距离与偏离暂停更新 · 点此检查来源","Distance and offset paused · check source"),12,c.muted,
                if(guidance.distanceMeters==null)Modifier.clickable(enabled=inputEnabled) {os.openLinked("data_center:source/POSITION")}else Modifier)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                if(guidance.nearTarget)Label(os.t("已到目标附近","Near the target"),12,c.muted,Modifier.weight(1f))else Spacer(Modifier.weight(1f))
                MetroButton(when {guidance.index==route.points.lastIndex->os.t("确认到达","Confirm arrival");guidance.nearTarget->os.t("已到达，下一点","Arrived · next point");else->os.t("下一航点","Next waypoint")},{
                    if(guidance.nearTarget&&guidance.index<route.points.lastIndex)advanceNavigationTarget(os,route,guidance.index)
                    else confirmTarget=guidance.index
                })
            }
        } else if(active!=null) Label(os.t("返回当前导航：${active.name}","return to navigation: ${active.name}"),13,c.muted,Modifier.clickable(enabled=inputEnabled) {
            os.maps.view("chart",os.center,os.zoom).previewRoute=null;os.displayedRouteId=active.id;os.showCrosshair=false;os.save()
        })
    }
    if(start) StartNavigationDialog(os,route) {start=false}
    if(manage) NavigationActionsDialog(os,route) {manage=false}
    confirmTarget?.takeIf {inputEnabled}?.let {expected->AppDialog(onDismissRequest={confirmTarget=null}){AppDialogSurface {
        val final=expected==route.points.lastIndex
        AppDialogTitle(if(final)os.t("到达并结束导航？","Arrive and end navigation?")else os.t("跳过当前航点？","Skip the current waypoint?"))
        Label(if(final)os.t("由你确认已到达目的地。航行记录会继续独立运行。","Confirm you have arrived. Voyage recording continues independently.")else os.t("尚未确认到达航点 ${expected+1}。继续将直接引导至航点 ${expected+2}。","Arrival at waypoint ${expected+1} is not confirmed. Continue directly to waypoint ${expected+2}."),15)
        MetroButton(if(final)os.t("确认到达并结束","Confirm arrival and finish")else os.t("前往下一航点","Go to next waypoint"),{
            if(os.activeRouteId==route.id&&os.routeLeg==expected){if(final)endNavigation(os,true)else advanceNavigationTarget(os,route,expected)}
            confirmTarget=null
        },primary=true)
        MetroButton(os.t("继续当前导航","Keep current guidance"),{confirmTarget=null})
    }}}
}

@Composable fun NavigationActionsDialog(os:OsStore,route:Route,onDismiss:()->Unit) {
    val route=os.activeRoute?.takeIf {it.id==route.id} ?: return
    val inputEnabled=com.yokuli.shell.compose.LocalInternalAppInputEnabled.current
    LaunchedEffect(route.id,inputEnabled) {if(inputEnabled){os.maps.view("chart",os.center,os.zoom).previewRoute=null;os.displayedRouteId=route.id}}
    val (fix,now)=liveNavigationFix(os)
    val guidance=routeGuidance(route,os.routeLeg,fix,now) ?: return
    val c=LocalMetro.current
    var choose by rememberSaveable(route.id) {mutableStateOf(false)}
    var stopping by rememberSaveable(route.id) {mutableStateOf(false)}
    var arrival by rememberSaveable(route.id) {mutableStateOf(false)}
    if(inputEnabled)AppDialog(onDismissRequest=onDismiss) {
        AppDialogSurface {
            AppDialogTitle(if(stopping) os.t("结束导航？","End navigation?") else if(choose) os.t("切换目标","Change target") else os.t("正在导航","Navigating"))
            Label(route.name,20,c.accent)
            if(stopping) {
                Label(if(arrival) os.t("确认已到达终点后结束导航。","Confirm arrival at the final waypoint and end navigation.")
                    else os.t("结束当前引导，保留已保存的航线。航行记录由记录按钮单独控制。","End current guidance and keep the saved route. Track recording is controlled separately."),19)
                MetroButton(if(arrival) os.t("确认到达并结束","confirm arrival and finish") else os.t("结束导航","end navigation"),{endNavigation(os,arrival);onDismiss()},primary=true)
                MetroButton(os.t("继续导航","keep navigating"),{stopping=false;arrival=false})
            } else if(choose) {
                Label(os.t("选择后直接前往该点，再按顺序继续。","Head to the selected waypoint, then continue in order."),16,c.muted)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=300.dp)) {
                    itemsIndexed(route.points) {i,p -> MenuRow(os.t("航点 ${i+1}","waypoint ${i+1}"),
                        if(i==guidance.index) os.t("当前目标","current target") else fix?.takeIf {it.fresh(now)}?.let {os.formatDistance(distance(it.point,p))}) {
                        os.routeLeg=i;os.displayedRouteId=route.id;os.showCrosshair=false;os.save();if(os.shell.appForPage(os.page)?.app!=AppId.CHART)os.openLinked("chart");onDismiss()
                        os.maps.view("chart",os.center,os.zoom).previewRoute=null
                    } }
                }
                MetroButton(os.t("返回","back"),{choose=false})
            } else {
                Label(os.t("当前目标 ${guidance.index+1} / ${route.points.size}","current target ${guidance.index+1} / ${route.points.size}"),18)
                Label(guidance.distanceMeters?.let {os.formatDistance(it)} ?: "—",44,c.accent)
                Label(guidance.bearingTrue?.let {os.t("直线方位 ${os.formatBearing(it)}T","direct bearing ${os.formatBearing(it)}T")} ?: os.t("等待新鲜船位","waiting for a fresh position"),20)
                Label(guidance.remainingMeters?.let {os.t("沿后续航点剩余 ${os.formatDistance(it)}","${os.formatDistance(it)} remaining via subsequent waypoints")} ?: os.t("剩余距离不可用","remaining distance unavailable"),16,c.muted)
                Label(offsetLabel(os,guidance),17,c.muted)
                guidance.accuracy?.let {Label(os.t("船位精度约 ±${os.formatLength(it)}","position accuracy approximately ±${os.formatLength(it)}"),13,c.muted)}
                if(guidance.distanceMeters==null) MetroButton(os.t("检查船位来源","Check position source"),{os.openLinked("data_center:source/POSITION")})
                if(guidance.nearTarget) {
                    Label(os.t("目标附近（${os.formatLength(ARRIVAL_NEAR_METERS)} 内）。到达由你确认。","Within ${os.formatLength(ARRIVAL_NEAR_METERS)} of the target. You confirm arrival."),16,c.accent)
                    MetroButton(if(guidance.index==route.points.lastIndex) os.t("确认到达终点","confirm final arrival") else os.t("已到达，前往下一点","arrived, go to next"),{
                        if(guidance.index==route.points.lastIndex) {arrival=true;stopping=true}
                        else {advanceNavigationTarget(os,route,guidance.index);onDismiss()}
                    },primary=true)
                }
                MetroButton(os.t("切换目标航点","change target waypoint"),{choose=true})
                MetroButton(os.t("查看整条航线","show entire route"),{os.maps.view("chart",os.center,os.zoom).previewRoute=null;os.displayedRouteId=route.id;os.fitRequest=route.points;os.showCrosshair=false;if(os.shell.appForPage(os.page)?.app!=AppId.CHART)os.openLinked("chart");onDismiss()})
                MetroButton(os.t("结束导航","end navigation"),{stopping=true})
                MetroButton(os.t("关闭","close"),onDismiss)
            }
        }
    }
}
