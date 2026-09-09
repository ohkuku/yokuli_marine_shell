package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import kotlinx.coroutines.*

@Composable fun RouteScreen(os:OsStore,id:String) {
    val route=os.routes.firstOrNull {it.id==id}
    if(route==null) {LaunchedEffect(id) {os.back()};return}
    val (fix,now)=liveNavigationFix(os)
    val c=LocalMetro.current;val context=LocalContext.current;val scope=rememberCoroutineScope()
    var rename by remember(id) {mutableStateOf(false)};var remove by remember(id) {mutableStateOf(false)}
    var startAt by remember(id) {mutableStateOf<Int?>(null)};var actions by remember(id) {mutableStateOf(false)}
    var selectedPoint by remember(id) {mutableStateOf<Int?>(null)}
    var editConfirm by remember(id) {mutableStateOf(false)};var exporting by remember {mutableStateOf(false)}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) {uri ->if(uri!=null) scope.launch {
        exporting=true
        try {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")?.use {Gpx.write(it,emptyList(),listOf(route))} ?: error("unwritable")};os.notify("航线已导出","Route exported")}
        catch(_:Exception) {os.notify("导出失败，请检查所选位置","Export failed. Check the selected location.")} finally {exporting=false}
    } }
    fun preview() {os.displayedRouteId=id;os.fitRequest=route.points;os.showCrosshair=false;os.open("chart")}
    fun edit() {
        os.editingRouteId=id;os.draftRoute=route.points.toList();os.editingRoute=true;os.showCrosshair=true;os.ruler=emptyList()
        route.points.firstOrNull()?.let {os.fly(it)};os.open("chart")
    }
    val navigating=os.activeRouteId==id
    val guidance=os.activeRoute?.takeIf{navigating}?.let {routeGuidance(it,os.routeLeg,fix,now)}
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,route.name,os.t("我的航行","MY SAILING"))
        Pivot(listOf(os.t("概览","overview"),os.t("航点","waypoints"),os.t("整理","organize"))) {page ->
            PageBody {
                when(page) {
                    0 -> {
                        if(navigating) {
                            Label(os.t("正在导航 · 目标 ${os.routeLeg+1}","navigating · target ${os.routeLeg+1}"),16,c.accent)
                            Label(guidance?.distanceMeters?.let(os::formatDistance) ?: os.t("等待船位","waiting for position"),40)
                            Label(guidance?.let {offsetLabel(os,it)} ?: "",16,c.muted)
                        } else {
                            Label(if(route.points.size==1) os.t("单点前往","one destination") else os.formatDistance(route.length),44,c.accent)
                            Label(os.t("${route.points.size} 个航点 · 保存的规划路线","${route.points.size} waypoints · your saved plan"),17,c.muted)
                        }
                        RouteSketch(os,route,if(navigating) os.routeLeg else null)
                        if(route.points.isEmpty()) Label(os.t("这条航线还没有航点。编辑后再开始。","This route has no waypoints. Add some before starting."),18)
                        MetroButton(if(navigating) os.t("回到海图继续导航","continue navigation on chart") else os.t("在海图上预览","preview on chart"),{
                            val live=fix?.takeIf {it.fresh(now)}
                            if(navigating && live!=null) {os.displayedRouteId=id;os.showCrosshair=false;os.fly(live.point);os.follow=true;os.open("chart")} else preview()
                        },primary=true,enabled=route.points.isNotEmpty())
                        MetroButton(if(navigating) os.t("当前导航与目标","current guidance & target") else os.t("开始沿线导航","start route navigation"),{if(navigating) actions=true else startAt=0},enabled=route.points.isNotEmpty())
                        Label(os.t("预览只显示路线。开始导航后，才会根据船位引导你逐点前往。","Preview displays the route. Start navigation to follow its waypoints from your position."),16,c.muted)
                        route.points.firstOrNull()?.let {p ->MenuRow(os.t("第一个航点","first waypoint"),os.formatCoordinates(p)) {selectedPoint=0}}
                        if(route.points.size>1) route.points.lastOrNull()?.let {p ->MenuRow(os.t("终点","destination"),os.formatCoordinates(p)) {selectedPoint=route.points.lastIndex}}
                    }
                    1 -> {
                        Label(os.t("按顺序认识这条航线","get to know each leg"),29)
                        Label(os.t("点一个航点，可以查看位置，或将它选为第一个目标。","Tap a waypoint to inspect its position or make it your first target."),17,c.muted)
                        var cumulative=0.0
                        route.points.forEachIndexed {i,p ->
                            val leg=if(i>0) distance(route.points[i-1],p) else 0.0;cumulative+=leg
                            MenuRow(os.t("${i+1}  "+if(i==route.points.lastIndex) "终点" else "航点","${i+1}  "+if(i==route.points.lastIndex) "destination" else "waypoint"),
                                if(i==0) os.formatCoordinates(p) else "${os.formatDistance(leg)} · ${decimal(bearing(route.points[i-1],p),0)}°T · "+os.t("累计 ${os.formatDistance(cumulative)}","${os.formatDistance(cumulative)} total")) {selectedPoint=i}
                        }
                    }
                    else -> {
                        MetroButton(os.t("编辑航点","edit waypoints"),{
                            if(os.draftRoute.isNotEmpty() && os.editingRouteId!=id) editConfirm=true else edit()
                        },primary=true)
                        MetroButton(os.t("重命名","rename"),{rename=true})
                        MetroButton(os.t("创建反向返航路线","make a reversed return route"),{
                            val reversed=Route(name=route.name+os.t(" · 返航"," · return"),points=route.points.reversed());os.routes=os.routes+reversed;os.save();os.open("route:${reversed.id}")
                        },enabled=route.points.size>1)
                        MetroButton(if(exporting) os.t("正在导出…","exporting…") else os.t("导出这条航线 GPX","export this route as GPX"),{exporter.launch("Yokuli-route.gpx")},enabled=!exporting && route.points.isNotEmpty())
                        if(os.displayedRouteId==id && !navigating) MetroButton(os.t("从海图隐藏预览","hide chart preview"),{os.displayedRouteId=null;os.save()})
                        if(navigating) MetroButton(os.t("管理或结束导航","manage or end navigation"),{actions=true})
                        MetroButton(os.t("删除航线","delete route"),{remove=true})
                    }
                }
            }
        }
    }
    startAt?.let {index ->StartNavigationDialog(os,route,index) {startAt=null}}
    if(actions) NavigationActionsDialog(os,os.activeRoute?:route) {actions=false}
    selectedPoint?.let {index ->route.points.getOrNull(index)?.let {point ->
        Dialog(onDismissRequest={selectedPoint=null}) {
            Column(Modifier.fillMaxWidth().background(c.bg).border(1.dp,c.muted).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Label(os.t("航点 ${index+1}","waypoint ${index+1}"),32)
                Label(os.formatCoordinates(point),20,c.accent)
                fix?.takeIf {it.fresh(now)}?.let {Label(os.t("距船位 ${os.formatDistance(distance(it.point,point))}","${os.formatDistance(distance(it.point,point))} from your position"),17,c.muted)}
                MetroButton(os.t("在海图上查看","show on chart"),{os.displayedRouteId=id;os.fly(point);os.showCrosshair=true;selectedPoint=null;os.open("chart")},primary=true)
                MetroButton(os.t("从这个目标开始导航","start with this target"),{selectedPoint=null;startAt=index})
                MetroButton(os.t("关闭","close"),{selectedPoint=null})
            }
        }
    }}
    if(editConfirm) ConfirmDialog(os,os.t("替换当前未保存的航线草稿？","Replace the current unsaved route draft?"),{editConfirm=false}) {editConfirm=false;edit()}
    if(rename) TextDialog(os,os.t("重命名","rename"),route.name,{rename=false}) {name ->os.routes=os.routes.map {if(it.id==id) it.copy(name=name) else it};os.save()}
    if(remove) ConfirmDialog(os,os.t("删除 ${route.name}？"+if(navigating) "当前导航继续使用启动时的路线。" else "","Delete ${route.name}?"+if(navigating) " Active guidance keeps its original route snapshot." else ""),{remove=false}) {
        if(os.displayedRouteId==id) os.displayedRouteId=null
        os.routes=os.routes.filter {it.id!=id};os.save();remove=false;os.back()
    }
}
