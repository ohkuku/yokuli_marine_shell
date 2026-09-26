package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import kotlinx.coroutines.*

@Composable fun RouteScreen(os:OsStore,id:String) {
    val savedRoute=os.routes.firstOrNull {it.id==id}
    val route=savedRoute ?: os.activeRoute?.takeIf {it.id==id}
    if(route==null) {
        Column(Modifier.fillMaxSize()) {PageHeader(os,os.t("航线","route"));PageBody {MissingSailingObject(os)}}
        return
    }
    val (fix,now)=liveNavigationFix(os)
    val c=LocalMetro.current;val context=LocalContext.current;val scope=rememberCoroutineScope()
    var rename by rememberSaveable(id) {mutableStateOf(false)};var remove by rememberSaveable(id) {mutableStateOf(false)}
    var startAt by rememberSaveable(id) {mutableStateOf<Int?>(null)};var actions by rememberSaveable(id) {mutableStateOf(false)}
    var selectedPoint by rememberSaveable(id) {mutableStateOf<Int?>(null)}
    var editConfirm by rememberSaveable(id) {mutableStateOf(false)};var exporting by remember {mutableStateOf(false)}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) {uri ->if(uri!=null) scope.launch {
        exporting=true
        try {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")?.use {Gpx.write(it,emptyList(),listOf(route))} ?: error("unwritable")};os.notify("航线已导出","Route exported")}
        catch(cancelled:CancellationException) {throw cancelled}
        catch(_:Exception) {os.notify("导出失败，请检查所选位置","Export failed. Check the selected location.")} finally {exporting=false}
    } }
    fun preview(focus:GeoPoint?=null) {
        // 整条路线和单个航点共用同一只读入口。先结束当前及返回快照中的编辑展示，
        // 保留未保存的草稿，再发布新的预览；看航点不进入准星选点或编辑模式。
        os.shell.showChartRoutePreview(route)
        os.fitRequest=if(focus==null)route.points else null
        if(focus!=null)os.fly(focus)
        os.openLinked("chart")
    }
    fun edit() {
        // 同一条航线已有未保存编辑时继续它，不用收藏中的旧版本覆盖草稿。
        if(os.editingRouteId==id&&os.draftRoute.isNotEmpty()) {
            resumeOrCreateRouteDraft(os);os.follow=false;os.fitRequest=os.draftRoute
        } else loadRouteDraft(os,route)
        os.openLinked("chart")
    }
    val navigating=os.activeRouteId==id
    val guidance=if(navigating)currentRouteGuidance(os) else null
    val activeOrdinal=os.activeRoute?.targetIndices?.indexOf(os.navigationState.session?.targetIndex)?.plus(1)?:1
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,route.name,os.t("我的航行","MY SAILING"))
        Pivot(listOf(os.t("概览","overview"),os.t("航点","waypoints"),os.t("整理","organize"))) {page ->
            PageBody {
                when(page) {
                    0 -> {
                        if(navigating) {
                            Label(os.t("正在导航 · 目标 ${activeOrdinal}","navigating · target ${activeOrdinal}"),15,c.accentText)
                            Label(guidance?.distanceMeters?.let(os::formatDistance) ?: os.t("等待船位","waiting for position"),40)
                            Label(guidance?.let {offsetLabel(os,guidance)} ?: "",16,c.muted)
                        } else {
                            Label(if(route.points.size==1) os.t("单点前往","one destination") else os.formatDistance(route.length),44,c.accent)
                            Label(os.t("${route.targetIndices.size} 个航点 · 保存的规划路线","${route.targetIndices.size} waypoints · your saved plan"),15,c.muted)
                        }
                        RouteSketch(os,if(navigating)os.activeRoute ?: route else route,if(navigating) os.navigationState.session?.targetIndex else null)
                        if(navigating && os.activeRoute?.points!=route.points)Label(os.t("收藏已编辑。本次导航继续使用出发时的路线。","Saved plan edited. This navigation keeps the route used at departure."),15,c.muted)
                        if(route.points.isEmpty()) Label(os.t("这条航线还没有航点。编辑后再开始。","This route has no waypoints. Add some before starting."),15)
                        MetroButton(if(navigating) os.t("回到海图继续导航","continue navigation on chart") else os.t("在海图上预览","preview on chart"),{
                            val live=fix?.takeIf {it.fresh(now)}
                            if(navigating) {os.shell.hideChartRoutePreview();os.displayedRouteId=id;if(live!=null){os.fitRequest=null;os.fly(live.point);os.follow=true}else os.fitRequest=os.activeRoute?.points;os.openLinked("chart")} else preview()
                        },primary=true,enabled=route.points.isNotEmpty())
                        MetroButton(if(navigating) os.t("当前导航与目标","current guidance & target") else os.t("开始沿线导航","start route navigation"),{if(navigating) actions=true else startAt=0},enabled=route.points.isNotEmpty())
                        Label(os.t("预览只显示路线。开始导航后，才会根据船位引导你逐点前往。","Preview displays the route. Start navigation to follow its waypoints from your position."),15,c.muted)
                        route.targetIndices.firstOrNull()?.let {first ->MenuRow(os.t("第一个目标","first destination"),os.formatCoordinates(route.points[first])) {selectedPoint=first}}
                        if(route.targetIndices.size>1) route.points.lastOrNull()?.let {p ->MenuRow(os.t("终点","destination"),os.formatCoordinates(p)) {selectedPoint=route.points.lastIndex}}
                    }
                    1 -> {
                        AppSection(os.t("按顺序认识这条航线","get to know each leg"))
                        Label(os.t("点一个航点，可以查看位置，或将它选为第一个目标。","Tap a waypoint to inspect its position or make it your first target."),15,c.muted)
                        val cumulative=remember(route.points){buildList {add(0.0);route.points.zipWithNext().forEach{(a,b)->add(last()+distance(a,b))}}}
                        route.targetIndices.forEachIndexed {ordinal,i ->
                            val p=route.points[i];val previous=route.targetIndices.getOrNull(ordinal-1)?:0
                            val leg=cumulative[i]-cumulative[previous]
                            MenuRow(os.t("${ordinal+1}  "+if(i==route.points.lastIndex) "终点" else "航点","${ordinal+1}  "+if(i==route.points.lastIndex) "destination" else "waypoint"),
                                if(i==0) os.formatCoordinates(p) else os.t("沿线 ${os.formatDistance(leg)} · 累计 ${os.formatDistance(cumulative[i])}","${os.formatDistance(leg)} along route · ${os.formatDistance(cumulative[i])} total")) {selectedPoint=i}
                        }
                    }
                    else -> {
                        if(savedRoute==null) {
                            Label(os.t("这是当前导航保留的路线，收藏不存在或尚未保存。导航会继续使用出发时的航点。","This is the route retained by active navigation. It was not saved, or its saved copy was removed. Guidance keeps its departure waypoints."),15,c.muted)
                            MetroButton(os.t("保存为新航线","save as a new route"),{
                                val copy=route.copy(id=uid(),points=route.points.toList())
                                os.sailing.putRoute(copy);os.open("route:${copy.id}")
                            },primary=true)
                            MetroButton(os.t("管理或结束导航","manage or end navigation"),{actions=true})
                        } else {
                        MetroButton(if(os.editingRouteId==id&&os.draftRoute.isNotEmpty())os.t("继续编辑草稿","Continue editing draft")else os.t("编辑航线","Edit route"),{
                            if(os.draftRoute.isNotEmpty() && os.editingRouteId!=id) editConfirm=true else edit()
                        },primary=true)
                        MetroButton(os.t("重命名","rename"),{rename=true})
                        MetroButton(os.t("创建反向返航路线","make a reversed return route"),{
                            val reversed=Route(name=route.name+os.t(" · 返航"," · return"),points=route.points.reversed(),navigationTargetIndices=route.navigationTargetIndices?.let{indices->(indices.map{route.points.lastIndex-it}.filter{it>0}+route.points.lastIndex).distinct().sorted()});os.sailing.putRoute(reversed);os.open("route:${reversed.id}")
                        },enabled=route.points.size>1)
                        MetroButton(if(exporting) os.t("正在导出…","exporting…") else os.t("导出这条航线 GPX","export this route as GPX"),{exporter.launch("Yokuli-route.gpx")},enabled=!exporting && route.points.isNotEmpty())
                        if(os.displayedRouteId==id && !navigating) MetroButton(os.t("从海图隐藏预览","hide chart preview"),{os.shell.hideChartRoutePreview(id);os.save()})
                        if(navigating) MetroButton(os.t("管理或结束导航","manage or end navigation"),{actions=true})
                        MetroButton(os.t("删除航线","delete route"),{remove=true})
                        }
                    }
                }
            }
        }
    }
    startAt?.let {index ->StartNavigationDialog(os,route,index) {startAt=null}}
    if(actions) NavigationActionsDialog(os,os.activeRoute?:route) {actions=false}
    selectedPoint?.let {index ->route.points.getOrNull(index)?.let {point ->
        AppDialog(onDismissRequest={selectedPoint=null}) {
            AppDialogSurface() {
                AppDialogTitle(os.t("航点 ${route.targetIndices.indexOf(index)+1}","waypoint ${route.targetIndices.indexOf(index)+1}"))
                Label(os.formatCoordinates(point),15,c.accentText)
                fix?.takeIf {it.fresh(now)}?.let {Label(os.t("距船位 ${os.formatDistance(distance(it.point,point))}","${os.formatDistance(distance(it.point,point))} from your position"),15,c.muted)}
                MetroButton(os.t("在海图上查看","show on chart"),{selectedPoint=null;preview(point)},primary=true)
                MetroButton(os.t("从这个目标开始导航","start with this target"),{selectedPoint=null;startAt=index})
                MetroButton(os.t("关闭","close"),{selectedPoint=null})
            }
        }
    }}
    if(editConfirm) ConfirmDialog(os,os.t("替换当前未保存的航线草稿？","Replace the current unsaved route draft?"),{editConfirm=false}) {editConfirm=false;edit()}
    if(rename) TextDialog(os,os.t("重命名","rename"),route.name,{rename=false}) {name ->os.sailing.putRoute(route.copy(name=name))}
    if(remove) ConfirmDialog(os,os.t("删除 ${route.name}？"+if(navigating) "当前导航继续使用启动时的路线。" else "","Delete ${route.name}?"+if(navigating) " Active guidance keeps its original route snapshot." else ""),{remove=false}) {
        os.shell.hideChartRoutePreview(id)
        os.sailing.removeRoute(id);remove=false;os.back()
    }
}
