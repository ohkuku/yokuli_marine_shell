package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.runtime.contract.navigation.NavigationSource
import org.json.JSONObject

/** 加点、自动建议、保存与出发属于同一编辑器。 */
@Composable internal fun RouteDraftControls(os:OsStore,onInspect:()->Unit) {
    var saveJson by rememberSaveable {mutableStateOf<String?>(null)}
    var navigationJson by rememberSaveable {mutableStateOf<String?>(null)}
    var pointsOpen by rememberSaveable {mutableStateOf(false)}
    var routesOpen by rememberSaveable {mutableStateOf(false)}
    var replaceJson by rememberSaveable {mutableStateOf<String?>(null)}
    var discard by rememberSaveable {mutableStateOf(false)}
    val persistence by os.persistenceState.collectAsState()
    val writable=!persistence.saving&&persistence.readFailure==null
    val points=os.draftRoute
    fun snapshot():Route=Route(os.editingRouteId?:uid(),os.routes.firstOrNull {it.id==os.editingRouteId}?.name?:os.t("航线 ${os.routes.size+1}","Route ${os.routes.size+1}"),os.draftRoute.toList(),os.draftNavigationTargetIndices?.toList())
    fun requestLoad(route:Route) {
        if(os.draftRoute.isNotEmpty()&&(os.draftRoute!=route.points||os.editingRouteId!=route.id||os.draftNavigationTargetIndices!=route.navigationTargetIndices))replaceJson=route.json().toString()
        else loadRouteDraft(os,route)
    }
    AppCommandBar(os,listOf(
        AppCommand("add","plus",when(points.size){0->os.t("设为起点","Set start");1->os.t("设为终点","Set destination");else->os.t("添加航点","Add waypoint")},{
            if(os.center.valid()&&os.draftRoute.lastOrNull()?.let {distance(it,os.center)<1}!=true)os.draftRoute=os.draftRoute+os.center
        },enabled=writable),
        AppCommand("suggest","route",os.t("自动生成","Suggest route"),{
            requestDraftCalculation(os,true)?.let {os.notify(it,it)};onInspect()
        },enabled=points.size>=2&&!draftCalculationBusy(os)),
        AppCommand("save","check",os.t("保存航线","Save route"),{saveJson=snapshot().json().toString()},enabled=points.size>=2&&writable),
        AppCommand("finish","close",os.t("保留并退出","Keep and close"),{leaveRouteDraft(os)},enabled=!persistence.saving),
    ),secondaryActions=buildList {
        add(AppCommand("waypoints","pin",os.t("调整航点","Edit waypoints"),{pointsOpen=true},enabled=points.isNotEmpty()&&writable))
        add(AppCommand("undo","undo",os.t("撤销末尾航点","Remove last waypoint"),{os.draftRoute=os.draftRoute.dropLast(1)},enabled=points.isNotEmpty()&&writable))
        add(AppCommand("navigate","play",os.t("开始导航…","Start navigation…"),{navigationJson=snapshot().json().toString()},enabled=points.size>=2&&writable))
        add(AppCommand("load-route","folder",os.t("编辑已有航线","Edit a saved route"),{routesOpen=true},enabled=writable))
        if(os.activeRoute!=null&&os.navigationState.session?.source==NavigationSource.LOCAL)add(AppCommand("remaining","route",os.t("从船位重新规划余程","Replan remaining passage"),{
            val fix=os.hub.state.value.fix(os.positionSource)?.takeIf {it.fresh(SystemClock.elapsedRealtime())&&it.point.valid()&&os.positionSource!="demo"}
            val route=os.activeRoute
            if(fix==null||route==null||route.points.isEmpty())os.notify("收到当前船位后才能重新规划余程", "A current boat position is required to replan the remainder")
            else {
                val first=os.routeLeg.coerceIn(route.points.indices)
                requestLoad(route.copy(points=listOf(fix.point)+route.points.drop(first),navigationTargetIndices=route.targetIndices.filter {it>=first}.map {it-first+1}))
            }
        },enabled=writable))
        add(AppCommand("discard","delete",os.t("丢弃草稿…","Discard draft…"),{discard=true},enabled=writable))
    })
    if(discard)ConfirmDialog(os,os.t("丢弃这条未保存的草稿？已保存航线与当前导航不会改变。","Discard this unsaved draft? Saved routes and active navigation stay unchanged."),{discard=false}){discard=false;discardRouteDraft(os)}
    saveJson?.let {json->val route=remember(json){Route.from(JSONObject(json))};RouteDraftSaveDialog(os,route){saveJson=null}}
    navigationJson?.let {json->val route=remember(json){Route.from(JSONObject(json))};StartNavigationDialog(os,route){navigationJson=null}}
    if(pointsOpen)RouteDraftWaypoints(os){pointsOpen=false}
    if(routesOpen)AppDialog(onDismissRequest={routesOpen=false}) {AppDialogSurface {
        AppDialogTitle(os.t("编辑已有航线","Edit a saved route"))
        if(os.routes.isEmpty())Label(os.t("还没有保存的航线","No saved routes yet"),15)
        os.routes.forEach {route->MenuRow(route.name,os.formatDistance(route.length)){routesOpen=false;requestLoad(route)}}
        MetroButton(os.t("返回","Back"),{routesOpen=false})
    }}
    replaceJson?.let {json->ConfirmDialog(os,os.t("用所选航线替换当前草稿？当前导航与收藏保持不变。","Replace this draft with the selected route? Active navigation and saved routes stay unchanged."),{replaceJson=null}) {
        loadRouteDraft(os,Route.from(JSONObject(json)));replaceJson=null
    }}
}
