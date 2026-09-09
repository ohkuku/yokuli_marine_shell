package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import kotlinx.coroutines.*

@Composable fun PlacesScreen(os:OsStore) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();var busy by remember {mutableStateOf(false)}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> if(uri!=null) scope.launch {
        busy=true
        try {
            val content=withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)?.use {Gpx.read(it)} ?: error("unreadable")}
            os.places=os.places+content.places;os.routes=os.routes+content.routes;os.save()
            os.notify("已导入 ${content.places.size} 个标记、${content.routes.size} 条航线","Imported ${content.places.size} marks and ${content.routes.size} routes")
        } catch(_:Exception) {os.notify("无法导入，请选择有效 GPX（最大 8 MB）","Could not import. Choose a valid GPX file up to 8 MB.")} finally {busy=false}
    } }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) {uri ->if(uri!=null) scope.launch {
        val places=os.places.toList();val routes=os.routes.toList();busy=true
        try {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")?.use {Gpx.write(it,places,routes)} ?: error("unwritable")};os.notify("GPX 已导出","GPX exported")}
        catch(_:Exception) {os.notify("导出失败，请检查所选位置","Export failed. Check the selected location.")} finally {busy=false}
    } }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("我的航行","my sailing"))
        Pivot(listOf(os.t("标记","marks"),os.t("航线","routes"),os.t("导入导出","files"))) {page ->
            PageBody {
                when(page) {
                    0->{
                        if(os.places.isEmpty()) {Label(os.t("把值得记住的地方\n留在海图上。","keep the places\nworth remembering."),34);Label(os.t("在海图上移动准星，点一次标记即可保存。","Move the crosshair on chart, then tap mark to save."),18,LocalMetro.current.muted)}
                        os.places.forEach {place ->MenuRow(place.name,coordinates(place.point),"pin") {os.open("place:${place.id}")}}
                        MetroButton(os.t("去海图标记","mark on chart"),{os.open("chart");os.showCrosshair=true},primary=true)
                    }
                    1->{
                        os.activeRoute?.let {route ->Label(os.t("正在使用：${route.name}","active: ${route.name}"),22,LocalMetro.current.accent);MetroButton(os.t("结束航线","end route"),{os.activeRouteId=null;os.save()})}
                        if(os.routes.isEmpty()) {Label(os.t("下一站，去哪里？","where to next?"),35);Label(os.t("直接在海图上添加航点，拖动调整，再保存你的航线。","Add points directly on chart, drag to adjust, then save your route."),18,LocalMetro.current.muted)}
                        os.routes.forEach {route ->MenuRow(route.name,"${nm(route.length)} · ${route.points.size} "+os.t("个航点","points"),"route") {os.open("route:${route.id}")}}
                        MetroButton(if(os.editingRoute) os.t("继续编辑航线","continue editing") else os.t("新建航线","new route"),{
                            os.editingRoute=true;os.showCrosshair=true;os.ruler=emptyList();os.open("chart")
                        },primary=true)
                    }
                    else->{
                        Label("GPX",40)
                        Label(os.t("把已有的标记和航线带进来，也可以导出一份自己保管。","Bring your existing marks and routes, or export a copy to keep."),18,LocalMetro.current.muted)
                        MetroButton(if(busy) os.t("正在处理…","working…") else os.t("导入 GPX","import GPX"),{importer.launch(arrayOf("*/*"))},primary=true,enabled=!busy)
                        MetroButton(os.t("导出所有标记和航线","export all marks & routes"),{exporter.launch("Yokuli-sailing.gpx")},enabled=!busy && (os.places.isNotEmpty() || os.routes.isNotEmpty()))
                    }
                }
            }
        }
    }
}
@Composable fun PlaceScreen(os:OsStore,id:String) {
    val place=os.places.firstOrNull {it.id==id}
    if(place==null) {LaunchedEffect(id) {os.back()};return}
    var name by remember(id) {mutableStateOf(place.name)};var note by remember(id) {mutableStateOf(place.note)};var remove by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,place.name,os.t("我的航行","MY SAILING"))
        PageBody {
            Label(coordinates(place.point),20,LocalMetro.current.accent)
            MetroButton(os.t("在海图上查看","show on chart"),{os.fly(place.point);os.showCrosshair=true;os.open("chart")},primary=true)
            MetroButton(os.t("前往这里","go here"),{
                val route=Route(name=place.name,points=listOf(place.point));os.routes=os.routes+route;os.startRoute(route)
            })
            Field(os.t("名称","name"),name,{name=it.take(100)})
            Field(os.t("备注","notes"),note,{note=it.take(2000)},multiline=true)
            MetroButton(os.t("保存修改","save changes"),{os.places=os.places.map {if(it.id==id) it.copy(name=name.trim(),note=note) else it};os.save();os.notify("标记已保存","Mark saved")},enabled=name.isNotBlank())
            MetroButton(os.t("删除标记","delete mark"),{remove=true})
        }
    }
    if(remove) ConfirmDialog(os,os.t("删除 ${place.name}？","Delete ${place.name}?"),{remove=false}) {os.places=os.places.filter {it.id!=id};os.save();remove=false;os.back()}
}
@Composable fun RouteScreen(os:OsStore,id:String) {
    val route=os.routes.firstOrNull {it.id==id}
    if(route==null) {LaunchedEffect(id) {os.back()};return}
    var rename by remember {mutableStateOf(false)};var remove by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,route.name,os.t("我的航行","MY SAILING"))
        PageBody {
            Label(nm(route.length),46,LocalMetro.current.accent)
            Label(os.t("${route.points.size} 个航点 · 距离与方位为直线值","${route.points.size} points · straight-line distances and bearings"),16,LocalMetro.current.muted)
            MetroButton(if(os.activeRouteId==id) os.t("继续航行","continue") else os.t("使用这条航线","use this route"),{
                if(os.activeRouteId==id) os.open("chart") else os.startRoute(route)
            },primary=true)
            MetroButton(os.t("查看整条航线","view route"),{
                os.displayedRouteId=id;os.fitRequest=route.points;os.open("chart")
            })
            MetroButton(os.t("编辑航点","edit points"),{
                os.editingRouteId=id;os.draftRoute=route.points.toList();os.editingRoute=true;os.showCrosshair=true;os.ruler=emptyList();os.fly(route.points.first());os.open("chart")
            })
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MetroButton(os.t("重命名","rename"),{rename=true},Modifier.weight(1f))
                MetroButton(os.t("反向副本","reverse copy"),{
                    val reversed=Route(name=route.name+os.t(" · 返航"," · return"),points=route.points.reversed());os.routes=os.routes+reversed;os.save();os.open("route:${reversed.id}")
                },Modifier.weight(1f))
            }
            route.points.forEachIndexed {i,p ->MenuRow("${i+1}   ${coordinates(p)}",if(i>0) "${nm(distance(route.points[i-1],p))} · ${decimal(bearing(route.points[i-1],p),0)}°T" else null) {os.fly(p);os.showCrosshair=true;os.open("chart")}}
            if(os.activeRouteId==id) MetroButton(os.t("结束航线","end route"),{os.activeRouteId=null;os.save()})
            MetroButton(os.t("删除航线","delete route"),{remove=true})
        }
    }
    if(rename) TextDialog(os,os.t("重命名","rename"),route.name,{rename=false}) {name ->os.routes=os.routes.map {if(it.id==id) it.copy(name=name) else it};os.save()}
    if(remove) ConfirmDialog(os,os.t("删除 ${route.name}？","Delete ${route.name}?"),{remove=false}) {os.routes=os.routes.filter {it.id!=id};if(os.activeRouteId==id) os.activeRouteId=null;os.save();remove=false;os.back()}
}
