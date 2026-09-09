package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import kotlinx.coroutines.*

fun PlaceKind.label(os:OsStore)=when(this) {
    PlaceKind.MARK->os.t("标记","mark");PlaceKind.ANCHORAGE->os.t("锚地","anchorage")
    PlaceKind.MARINA->os.t("码头","marina");PlaceKind.HAZARD->os.t("注意点","caution")
}

@Composable fun PlacesScreen(os:OsStore,anchoragesOnly:Boolean=false) {
    val repo=os.sailing;val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable(anchoragesOnly) { mutableStateOf<PlaceKind?>(if(anchoragesOnly)PlaceKind.ANCHORAGE else null) }
    var create by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Gpx.Contents?>(null) }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) scope.launch {
        busy=true
        try { pending=withContext(Dispatchers.IO) { os.context.contentResolver.openInputStream(uri)?.use(Gpx::read) ?: error("unreadable") } }
        catch(cancelled:CancellationException) {throw cancelled}
        catch(_:Exception) {os.notify("无法导入，请选择有效 GPX（最大 8 MB）","Choose a valid GPX file up to 8 MB.")}
        finally {busy=false}
    } }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri -> if(uri!=null) scope.launch {
        val places=os.allPlaces.toList();val routes=os.routes.toList();busy=true
        try { withContext(Dispatchers.IO) { os.context.contentResolver.openOutputStream(uri,"wt")?.use { Gpx.write(it,places,routes) } ?: error("unwritable") };os.notify("GPX 已导出","GPX exported") }
        catch(cancelled:CancellationException) {throw cancelled}
        catch(_:Exception) {os.notify("导出失败，资料未更改","Export failed. Your data is unchanged.")}
        finally {busy=false}
    } }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("我的航行","my sailing"))
        Pivot(listOf(os.t("坐标","places"),os.t("航线","routes"),os.t("整理","organize"))) { page -> PageBody {
            when(page) {
                0 -> {
                    Field(os.t("查找名称或笔记","find a name or note"),query,{query=it})
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        MetroButton(os.t("全部","all"),{kind=null},primary=kind==null)
                        PlaceKind.entries.forEach { choice -> MetroButton(choice.label(os),{kind=choice},primary=kind==choice) }
                    }
                    val local=os.places.filter { (kind==null || kind==it.kind) && (it.name.contains(query,true)||it.note.contains(query,true)) }
                    val saved=repo.locations.filter { (kind==null || kind==PlaceKind.ANCHORAGE) && (it.displayName.contains(query,true)||it.personalNotes.contains(query,true)||repo.spots.any { s -> s.placeId==it.id&&s.name.contains(query,true) }) }
                    if(local.isEmpty()&&saved.isEmpty()) Label(if(query.isBlank()) os.t("把值得记住的地方\n留在这里。","keep the places\nworth remembering.") else os.t("没有匹配的地点","no matching places"),32)
                    local.forEach { place -> MenuRow(place.name,"${place.kind.label(os)} · ${os.formatCoordinates(place.point)}","pin") {os.open("place:${place.id}")} }
                    saved.forEach { place -> MenuRow(place.displayName,os.t("锚地 · ${repo.spots.count { it.placeId==place.id }} 个具体位置 · ${place.visitCountCached+place.legacyVisitCount} 次到访","anchorage · ${repo.spots.count { it.placeId==place.id }} spots · ${place.visitCountCached+place.legacyVisitCount} visits"),"anchor") {os.open("anchorage:${place.id}")} }
                    if(repo.error) Label(os.t("部分已存地点暂时无法读取","Some saved places could not be loaded"),16,LocalMetro.current.muted)
                    MetroButton(os.t("输入坐标","enter coordinates"),{create=true},primary=true)
                    MetroButton(os.t("在海图上选点","choose on chart"),{os.showCrosshair=true;os.open("chart")})
                }
                1 -> {
                    os.activeRoute?.let { route -> MenuRow(os.t("正在导航 · ${route.name}","navigating · ${route.name}"),os.t("目标 ${os.routeLeg+1}/${route.points.size}","target ${os.routeLeg+1}/${route.points.size}"),"locate") {os.open("chart")} }
                    if(os.routes.isEmpty()) {Label(os.t("下一站，去哪里？","where to next?"),36);Label(os.t("在海图上规划，保存在这里。选择一条路线后才在地图上预览。","Plan on chart and keep it here. Select a route to preview it on the map."),18,LocalMetro.current.muted)}
                    os.routes.forEach { route -> MenuRow(route.name,"${os.formatDistance(route.length)} · ${route.points.size} "+os.t("个航点","points"),"route") {os.open("route:${route.id}")} }
                    MetroButton(if(os.draftRoute.isEmpty()) os.t("规划航线","plan a route") else os.t("继续草稿","continue draft"),{os.editingRoute=true;os.showCrosshair=true;os.ruler=emptyList();os.open("chart")},primary=true)
                }
                else -> {
                    Label(os.t("自己的收藏","your collections"),31)
                    val localCollections=os.places.map { it.collection }.filter(String::isNotBlank).distinct()
                    if(localCollections.isEmpty() && repo.collections.isEmpty()) Label(os.t("在地点详情中填写集合名称，把泊位、补给点或计划归到一起。","Give a place a collection name to group berths, supplies or plans."),18,LocalMetro.current.muted)
                    localCollections.forEach { group ->
                        Label(group,25,LocalMetro.current.accent)
                        os.places.filter {it.collection==group}.forEach { place -> MenuRow(place.name,os.formatCoordinates(place.point),"pin") {os.open("place:${place.id}")} }
                    }
                    repo.collections.forEach { group -> MenuRow(group.name,group.description,"pin") {os.open("collection:${group.id}")} }
                    if(repo.archivedLocations.isNotEmpty()) {
                        Label(os.t("已归档地点","archived places"),31)
                        repo.archivedLocations.forEach {place ->
                            MenuRow(place.displayName,os.t("点按恢复，保留原来的位置、照片和到访","Tap to restore its spots, photos and visits"),"pin") {
                                scope.launch {try {repo.restorePlace(place.id)} catch(cancelled:CancellationException) {throw cancelled} catch(_:Exception) {os.notify("恢复失败，请重试","Could not restore. Please retry.")}}
                            }
                        }
                    }
                    Label(os.t("导入与导出","import & export"),31)
                    Label(os.t("GPX 交换坐标和路线。照片、到访与锚地资料使用设置中的航行与船舶数据备份。","GPX exchanges coordinates and routes. Back up photos, visits and anchorage records in Settings → sailing and vessel data."),17,LocalMetro.current.muted)
                    MetroButton(if(busy) os.t("正在处理…","working…") else os.t("导入 GPX","import GPX"),{importer.launch(arrayOf("*/*"))},primary=true,enabled=!busy)
                    MetroButton(os.t("导出坐标与航线","export places & routes"),{exporter.launch("Yokuli-sailing.gpx")},enabled=!busy&&(os.allPlaces.isNotEmpty()||os.routes.isNotEmpty()))
                }
            }
        } }
    }
    if(create) CoordinateEditor(os,null,{create=false}) { place -> repo.put(place);create=false;os.open("place:${place.id}") }
    pending?.let { content -> Dialog(onDismissRequest={pending=null}) {
        Column(Modifier.fillMaxWidth().background(LocalMetro.current.bg).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Label(os.t("导入这份 GPX","import this GPX"),31)
            Label(os.t("${content.places.size} 个坐标，${content.routes.size} 条路线","${content.places.size} places, ${content.routes.size} routes"),21)
            Label(os.t("同名且位置相同的坐标、相同点序列的航线可以跳过。","Skip places with the same name and position, and routes with the same name and points."),17,LocalMetro.current.muted)
            fun apply(skip:Boolean) {val (places,routes)=repo.import(content,skip);pending=null;os.notify("已导入 $places 个坐标、$routes 条路线","Imported $places places and $routes routes")}
            MetroButton(os.t("跳过重复并导入","import, skip duplicates"),{apply(true)},primary=true)
            MetroButton(os.t("全部作为新资料导入","import all as new"),{apply(false)})
            MetroButton(os.t("取消","cancel"),{pending=null})
        }
    } }
}

@Composable fun PlaceScreen(os:OsStore,id:String) {
    if(id.startsWith("spot:")) {
        val spot=os.sailing.spots.firstOrNull { it.id==id.substringAfter(':').toLongOrNull() }
        if(spot!=null) SavedLocationScreen(os,spot.placeId,spot.id)
        else Column { PageHeader(os,os.t("坐标","place"));Label(os.t("正在读取地点，或该坐标已被移除","Loading place, or this coordinate has been removed"),20,modifier=Modifier.padding(22.dp)) }
        return
    }
    val place=os.places.firstOrNull { it.id==id }
    if(place==null) {Column {PageHeader(os,os.t("地点已移除","place removed"))};return}
    var edit by remember(id) {mutableStateOf(false)};var remove by remember(id) {mutableStateOf(false)};var start by remember(id) {mutableStateOf(false)}
    val (fix,now)=liveNavigationFix(os)
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,place.name,os.t("我的航行","MY SAILING"))
        PageBody {
            CoordinateMapPreview(os,listOf(MapPoint(place.id,place.point,place.name,os.accent)),place.point)
            Label(place.kind.label(os),17,LocalMetro.current.accent)
            Label(os.formatCoordinates(place.point),23)
            fix?.takeIf {it.fresh(now)}?.let { Label("${os.formatDistance(distance(it.point,place.point))} · ${decimal(bearing(it.point,place.point),0)}°T",31) }
            if(place.collection.isNotBlank()) Label(place.collection,18,LocalMetro.current.muted)
            if(place.note.isNotBlank()) Label(place.note,21)
            MetroButton(os.t("在海图上查看","show on chart"),{os.fly(place.point);os.showCrosshair=true;os.open("chart")},primary=true)
            MetroButton(os.t("前往这里","go here"),{start=true})
            MetroButton(os.t("在此设置锚警","prepare anchor watch here"),{os.anchorDraft=AnchorDraft(place.point,place.name);os.open("anchor")})
            MetroButton(os.t("编辑资料","edit place"),{edit=true})
            MetroButton(os.t("删除收藏","delete saved place"),{remove=true})
        }
    }
    if(edit) CoordinateEditor(os,place,{edit=false}) {os.sailing.put(it);edit=false}
    if(start) StartNavigationDialog(os,Route("goto:${place.id}",place.name,listOf(place.point))) {start=false}
    if(remove) ConfirmDialog(os,os.t("删除 ${place.name}？","Delete ${place.name}?"),{remove=false}) {os.sailing.remove(id);remove=false;os.back()}
}

@Composable internal fun CoordinateMapPreview(os:OsStore,points:List<MapPoint>,center:GeoPoint) {
    val view=remember(center) { MapViewState(center,14.0).apply { interactive=false } }
    Box(Modifier.fillMaxWidth().height(230.dp)) {MarineMap(os.maps,MapScene(points=points),view,Modifier.fillMaxSize())}
}

@Composable internal fun CoordinateEditor(os:OsStore,initial:Place?,onDismiss:()->Unit,onSave:(Place)->Unit) {
    var name by remember {mutableStateOf(initial?.name.orEmpty())}
    var latitude by remember {mutableStateOf(initial?.point?.lat?.toString().orEmpty())}
    var longitude by remember {mutableStateOf(initial?.point?.lon?.toString().orEmpty())}
    var note by remember {mutableStateOf(initial?.note.orEmpty())}
    var group by remember {mutableStateOf(initial?.collection.orEmpty())}
    var kind by remember {mutableStateOf(initial?.kind?:PlaceKind.MARK)}
    val point=latitude.toDoubleOrNull()?.let {lat->longitude.toDoubleOrNull()?.let {lon->GeoPoint(lat,lon).takeIf(GeoPoint::valid)}}
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max=650.dp).background(LocalMetro.current.bg).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Label(os.t("地点资料","place details"),31)
            Field(os.t("名称","name"),name,{name=it.take(100)})
            Field(os.t("纬度 · 十进制度","latitude · decimal degrees"),latitude,{latitude=it.take(20)})
            Field(os.t("经度 · 十进制度","longitude · decimal degrees"),longitude,{longitude=it.take(20)})
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {PlaceKind.entries.forEach {choice->MetroButton(choice.label(os),{kind=choice},primary=kind==choice)} }
            Field(os.t("集合","collection"),group,{group=it.take(80)})
            Field(os.t("笔记","notes"),note,{note=it.take(20000)},multiline=true)
            if(point==null&&(latitude.isNotBlank()||longitude.isNotBlank())) Label(os.t("纬度须在 -90 到 90，经度须在 -180 到 180","Latitude must be -90…90, longitude -180…180"),15,LocalMetro.current.muted)
            MetroButton(os.t("保存","save"),{point?.let {onSave(Place(initial?.id?:uid(),name.trim(),it,note,kind,group.trim()))}},primary=true,enabled=name.isNotBlank()&&point!=null)
            MetroButton(os.t("取消","cancel"),onDismiss)
        }
    }
}
