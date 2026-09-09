package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.data.Fix
import kotlinx.coroutines.delay
import kotlin.math.*

private const val EARTH_METERS = 6_371_008.8
private const val ARRIVAL_NEAR_METERS = 50.0

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
    os.routes=if(os.routes.any {it.id==route.id}) os.routes.map {if(it.id==route.id) route else it} else os.routes+route
    os.activeRouteId=route.id;os.displayedRouteId=route.id;os.routeLeg=index.coerceIn(route.points.indices)
    os.editingRoute=false;os.ruler=emptyList();os.showCrosshair=false
    val point=fix?.takeIf {it.fresh(now)}?.point ?: route.points[os.routeLeg]
    os.fly(point,os.zoom.coerceAtLeast(12.0));os.follow=fix?.fresh(now)==true
    os.save();os.open("chart")
}

fun endNavigation(os:OsStore,arrived:Boolean=false) {
    os.activeRouteId=null;os.displayedRouteId=null;os.routeLeg=0;os.follow=false;os.save()
    if(arrived) os.notify("已确认到达。航线保留在我的航行中。","Arrival confirmed. Your route remains in My Sailing.")
    else os.notify("导航已结束。航线保留在我的航行中。","Navigation ended. Your route remains in My Sailing.")
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
    g.outsideSegment -> os.t("距规划航段 ${nm(g.offsetMeters)}","${nm(g.offsetMeters)} from the planned segment")
    g.offsetSide>0 -> os.t("规划线右侧 ${nm(g.offsetMeters)}","${nm(g.offsetMeters)} right of the planned line")
    g.offsetSide<0 -> os.t("规划线左侧 ${nm(g.offsetMeters)}","${nm(g.offsetMeters)} left of the planned line")
    else -> os.t("距规划线 ${nm(g.offsetMeters)}","${nm(g.offsetMeters)} from the planned line")
}

@Composable fun RouteSketch(os:OsStore,route:Route,selected:Int?=null) {
    val c=LocalMetro.current
    Column(Modifier.fillMaxWidth().background(c.panel).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Label(os.t("航线草图 · 北向上","route sketch · north up"),12,c.muted)
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            if(route.points.isEmpty()) return@Canvas
            // Unwrap longitude so a route across ±180° does not span the entire sketch.
            var previous=route.points.first().lon
            val coords=route.points.map {p ->
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
            val path=Path();coords.indices.forEach {i -> val p=at(i);if(i==0) path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)}
            drawPath(path,c.accent,style=Stroke(3.dp.toPx()))
            val visible=if(coords.size<=50) coords.indices.toList() else listOf(0,coords.lastIndex)+listOfNotNull(selected?.takeIf {it in coords.indices})
            visible.distinct().forEach {i ->
                val p=at(i);drawCircle(c.bg,7.dp.toPx(),p);drawCircle(if(i==selected) c.accent else c.fg,4.dp.toPx(),p)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Label(os.t("出发 1","start 1"),12,c.muted)
            Label(os.t("终点 ${route.points.size}","finish ${route.points.size}"),12,c.muted)
        }
    }
}

@Composable fun StartNavigationDialog(os:OsStore,route:Route,initialTarget:Int=0,onDismiss:()->Unit) {
    val (fix,now)=liveNavigationFix(os)
    var target by remember(route.id,initialTarget) {mutableIntStateOf(initialTarget.coerceIn(0,(route.points.size-1).coerceAtLeast(0)))}
    val live=fix?.takeIf {it.fresh(now)}
    val c=LocalMetro.current
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max=660.dp).background(c.bg).border(1.dp,c.muted)
            .verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Label(os.t("开始沿线导航","start route navigation"),31)
            Label(route.name,21,c.accent)
            os.activeRoute?.takeIf {it.id!=route.id}?.let {
                Label(os.t("将替换当前导航：${it.name}","This replaces current navigation: ${it.name}"),16,c.muted)
            }
            Label(os.t("先去哪个航点？","which waypoint first?"),19)
            if(live!=null && route.points.size>1) MetroButton(os.t("选择最近的航点","choose nearest waypoint"),{
                target=route.points.indices.minByOrNull {distance(live.point,route.points[it])} ?: 0
            })
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=190.dp)) {
                itemsIndexed(route.points) {i,p ->
                    Row(Modifier.fillMaxWidth().background(if(i==target) c.panel else Color.Transparent).clickable {target=i}.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Label(if(i==target) "●" else "○",22,if(i==target) c.accent else c.muted)
                        Column(Modifier.padding(start=12.dp)) {
                            Label(os.t("航点 ${i+1}","waypoint ${i+1}"),20)
                            Label(live?.let {nm(distance(it.point,p))} ?: coordinates(p),12,c.muted)
                        }
                    }
                }
            }
            Label(os.t("按保存的航点依次引导。图上的连接线不判断水深、障碍或通航条件。","Guidance follows your saved waypoints. Connecting lines do not assess depth, obstacles or navigability."),15,c.muted)
            if(live==null) MenuRow(os.t("尚无可用船位","position unavailable"),os.t("可以先准备导航，距离和方位会在船位恢复后显示。","Prepare navigation now; distances and bearings appear when position becomes available.")) {onDismiss();os.open("data")}
            MetroButton(if(live!=null) os.t("开始导航","start navigation") else os.t("开始并等待船位","start and wait for position"),{
                beginNavigation(os,route,target,fix,now);onDismiss()
            },primary=true,enabled=route.points.isNotEmpty())
            MetroButton(os.t("取消","cancel"),onDismiss)
        }
    }
}

@Composable fun ChartNavigationCard(os:OsStore,fix:Fix?,now:Long) {
    val c=LocalMetro.current
    val active=os.activeRoute
    val preview=os.routes.firstOrNull {it.id==os.displayedRouteId}
    val route=preview ?: active ?: return
    var start by remember(route.id) {mutableStateOf(false)}
    var manage by remember(route.id) {mutableStateOf(false)}
    val navigating=active?.id==route.id
    val guidance=if(navigating) routeGuidance(route,os.routeLeg,fix,now) else null
    Column(Modifier.fillMaxWidth().background(c.bg.copy(alpha=.96f)).padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable {os.open("route:${route.id}")}) {
                Label(if(navigating) os.t("${route.name} · 目标 ${guidance?.index?.plus(1) ?: 1}/${route.points.size}","${route.name} · target ${guidance?.index?.plus(1) ?: 1}/${route.points.size}")
                    else os.t("已选航线 · ${route.name}","selected route · ${route.name}"),15,c.accent,maxLines=1)
                if(guidance!=null) {
                    Label(guidance.distanceMeters?.let {"${nm(it)}   ${decimal(guidance.bearingTrue,0)}°T"} ?: os.t("等待船位","waiting for position"),27)
                } else Label("${nm(route.length)} · ${route.points.size} "+os.t("个航点","waypoints"),21)
            }
            IconAction(if(navigating) "more" else "play",if(navigating) os.t("导航","navigate") else os.t("开始","start"),{if(navigating) manage=true else start=true})
        }
        if(guidance!=null) {
            Label(guidance.remainingMeters?.let {os.t("剩余 ${nm(it)} · ${offsetLabel(os,guidance)}","${nm(it)} remaining · ${offsetLabel(os,guidance)}")}
                ?: os.t("距离与偏离暂停更新 · 点此检查来源","distance and offset paused · check source"),12,c.muted,
                Modifier.clickable {if(guidance.distanceMeters==null) os.open("data") else manage=true})
            if(guidance.nearTarget) Label(os.t("目标附近（50 m 内）· 点此确认到达","near target (within 50 m) · confirm arrival"),15,c.accent,Modifier.clickable {manage=true})
        } else if(active!=null) Label(os.t("返回当前导航：${active.name}","return to navigation: ${active.name}"),13,c.muted,Modifier.clickable {
            os.displayedRouteId=active.id;os.showCrosshair=false;os.save()
        })
    }
    if(start) StartNavigationDialog(os,route) {start=false}
    if(manage) NavigationActionsDialog(os,route) {manage=false}
}

@Composable fun NavigationActionsDialog(os:OsStore,route:Route,onDismiss:()->Unit) {
    val (fix,now)=liveNavigationFix(os)
    val guidance=routeGuidance(route,os.routeLeg,fix,now) ?: return
    val c=LocalMetro.current
    var choose by remember {mutableStateOf(false)}
    var stopping by remember {mutableStateOf(false)}
    var arrival by remember {mutableStateOf(false)}
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max=660.dp).background(c.bg).border(1.dp,c.muted)
            .verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
            Label(if(stopping) os.t("结束导航？","end navigation?") else if(choose) os.t("切换目标","change target") else os.t("正在导航","navigating"),31)
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
                        if(i==guidance.index) os.t("当前目标","current target") else fix?.takeIf {it.fresh(now)}?.let {nm(distance(it.point,p))}) {
                        os.routeLeg=i;os.displayedRouteId=route.id;os.showCrosshair=false;os.save();os.open("chart");onDismiss()
                    } }
                }
                MetroButton(os.t("返回","back"),{choose=false})
            } else {
                Label(os.t("当前目标 ${guidance.index+1} / ${route.points.size}","current target ${guidance.index+1} / ${route.points.size}"),18)
                Label(guidance.distanceMeters?.let {nm(it)} ?: "—",44,c.accent)
                Label(guidance.bearingTrue?.let {os.t("直线方位 ${decimal(it,0)}°T","direct bearing ${decimal(it,0)}°T")} ?: os.t("等待新鲜船位","waiting for a fresh position"),20)
                Label(guidance.remainingMeters?.let {os.t("沿后续航点剩余 ${nm(it)}","${nm(it)} remaining via subsequent waypoints")} ?: os.t("剩余距离不可用","remaining distance unavailable"),16,c.muted)
                Label(offsetLabel(os,guidance),17,c.muted)
                guidance.accuracy?.let {Label(os.t("船位精度约 ±${it.roundToInt()} m","position accuracy approximately ±${it.roundToInt()} m"),13,c.muted)}
                if(guidance.distanceMeters==null) MetroButton(os.t("检查船位来源","check position source"),{onDismiss();os.open("data")})
                if(guidance.nearTarget) {
                    Label(os.t("目标附近（50 m 内）。到达由你确认。","Within 50 m of the target. You confirm arrival."),16,c.accent)
                    MetroButton(if(guidance.index==route.points.lastIndex) os.t("确认到达终点","confirm final arrival") else os.t("已到达，前往下一点","arrived, go to next"),{
                        if(guidance.index==route.points.lastIndex) {arrival=true;stopping=true}
                        else {os.routeLeg=guidance.index+1;os.save();os.open("chart");onDismiss()}
                    },primary=true)
                }
                MetroButton(os.t("切换目标航点","change target waypoint"),{choose=true})
                MetroButton(os.t("查看整条航线","show entire route"),{os.displayedRouteId=route.id;os.fitRequest=route.points;os.showCrosshair=false;os.open("chart");onDismiss()})
                MetroButton(os.t("结束导航","end navigation"),{stopping=true})
                MetroButton(os.t("关闭","close"),onDismiss)
            }
        }
    }
}
