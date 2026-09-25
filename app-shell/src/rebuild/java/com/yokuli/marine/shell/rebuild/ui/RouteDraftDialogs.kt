package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun RouteDraftSaveDialog(os:OsStore,route:Route,onDismiss:()->Unit) {
    var name by rememberSaveable(route.id){mutableStateOf(route.name)}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val persistence by os.persistenceState.collectAsState()
    AppDialog(onDismissRequest={if(!saving)onDismiss()}) {AppDialogSurface {
        AppDialogTitle(os.t("保存航线","Save route"))
        Field(os.t("名称","Name"),name,{name=it.take(120)})
        Label(os.t("保存到我的航行，不会开始或替换导航。","Save to My Sailing without starting or replacing navigation."),13,LocalMetro.current.muted)
        error?.let {Label(it,14,LocalMetro.current.accentText)}
        MetroButton(if(saving)os.t("正在保存…","Saving…")else os.t("保存","Save"),{
            if(os.draftRoute!=route.points||os.draftNavigationTargetIndices!=route.navigationTargetIndices){error=os.t("草稿已有新修改，请返回后重新保存。","The draft changed. Go back and save its current version.");return@MetroButton}
            val savedRoute=route.copy(name=name.trim())
            saving=true;error=null
            os.scope.launch {try {
                val receipt=os.sailing.putRoute(savedRoute)
                if(receipt.result.await()!=DurableCommitResult.SAVED){error=os.t("保存失败，草稿保留，可重试。","Save failed. The draft is retained; retry.");return@launch}
                // 先确认航线落盘，再清理相同草稿；后来的编辑不被覆盖。
                if(os.draftRoute==route.points&&os.draftNavigationTargetIndices==route.navigationTargetIndices&&os.editingRoute) {
                    os.editingRoute=false;os.showCrosshair=false;os.draftRoute=emptyList();os.draftNavigationTargetIndices=null;os.editingRouteId=null;os.planningDraftUndo=null
                    os.maps.view("chart",os.center,os.zoom).previewRoute=savedRoute;os.displayedRouteId=savedRoute.id
                    os.save()
                }
                onDismiss()
            }catch(cancel:CancellationException){throw cancel}catch(_:Exception){error=os.t("未能保存，草稿已保留。","Could not save. The draft is retained.")}finally{saving=false}}
        },primary=true,enabled=!saving&&!persistence.saving&&persistence.readFailure==null&&name.isNotBlank())
        MetroButton(os.t("返回编辑","Back to editing"),onDismiss,enabled=!saving)
    }}
}

@Composable internal fun RouteDraftWaypoints(os:OsStore,onDismiss:()->Unit) {
    val points=os.draftRoute
    val targets=(os.draftNavigationTargetIndices?:points.indices.toList()).filter {it in points.indices}
    val c=LocalMetro.current
    var showPlaces by rememberSaveable {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    AppDialog(onDismissRequest=onDismiss) {AppDialogSurface {
        AppDialogTitle(os.t("当前航线航点","Waypoints in this route"))
        Label(os.t("点航点回到同一张地图，拖动圆点调整；自动绕行的形状点仍保留。","Open a waypoint on the same map and drag its marker to adjust it. Automatic detour shape points are preserved."),13,c.muted)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=280.dp)) {
            itemsIndexed(targets,key={_,index->index}) {ordinal,index->
                MenuRow(os.t("航点 ${ordinal+1}","Waypoint ${ordinal+1}"),os.formatCoordinates(points[index])) {os.fly(points[index],os.zoom.coerceAtLeast(13.0));onDismiss()}
            }
        }
        if(os.draftNavigationTargetIndices==null&&points.size>=2)MetroButton(os.t("反转航点顺序","Reverse waypoint order"),{os.draftRoute=os.draftRoute.reversed()})
        MenuRow(os.t("从收藏地点添加航点","Add a saved place")){showPlaces=!showPlaces}
        if(showPlaces) {
            Field(os.t("查找地点","Find a place"),query,{query=it})
            val places=os.allPlaces.filter {query.isBlank()||it.name.contains(query,true)}
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=250.dp)) {
                itemsIndexed(places,key={_,place->place.id}) {_,place->MenuRow(place.name,os.formatCoordinates(place.point)) {
                    if(os.draftRoute.lastOrNull()?.let {distance(it,place.point)<1}!=true)os.draftRoute=os.draftRoute+place.point
                    os.fly(place.point);onDismiss()
                }}
            }
        }
        MetroButton(os.t("返回地图","Back to map"),onDismiss)
    }}
}
