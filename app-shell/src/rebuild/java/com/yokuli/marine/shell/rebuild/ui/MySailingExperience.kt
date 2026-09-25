package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
    var create by rememberSaveable { mutableStateOf(false) }
    var choosingKind by rememberSaveable {mutableStateOf(false)}
    var pending by remember { mutableStateOf<Gpx.Contents?>(null) }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) scope.launch {
        busy=true
        try { pending=withContext(Dispatchers.IO) { os.context.contentResolver.openInputStream(uri)?.use(Gpx::read) ?: error("unreadable") } }
        catch(cancelled:CancellationException) {throw cancelled}
        catch(_:Gpx.UnsupportedTracks) {os.notify("这份 GPX 包含历史轨迹。目前仅支持坐标与计划航线，整份文件未导入。","This GPX contains recorded tracks. Only places and planned routes are supported; nothing was imported.")}
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
        PageHeader(os,os.title(AppId.PLACES))
        Pivot(listOf(os.t("坐标","places"),os.t("航线","routes"),os.t("整理","organize"))) { page -> PageBody {
            when(page) {
                0 -> {
                    Field(os.t("查找名称或笔记","find a name or note"),query,{query=it})
                    Label((kind?.label(os) ?: os.t("全部坐标","all places"))+"  ▾",15,LocalMetro.current.accentText,
                        Modifier.clickable {choosingKind=true}.padding(vertical=8.dp))
                    val local=os.places.filter { (kind==null || kind==it.kind) && (it.name.contains(query,true)||it.note.contains(query,true)) }
                    val saved=repo.locations.filter { (kind==null || kind==PlaceKind.ANCHORAGE) && (it.displayName.contains(query,true)||it.personalNotes.contains(query,true)||repo.spots.any { s -> s.placeId==it.id&&s.name.contains(query,true) }) }
                    if(local.isEmpty()&&saved.isEmpty()) Label(if(query.isBlank()) os.t("把值得记住的地方\n留在这里。","keep the places\nworth remembering.") else os.t("没有匹配的地点","no matching places"),24)
                    local.forEach { place -> MenuRow(place.name,"${place.kind.label(os)} · ${os.formatCoordinates(place.point)}","pin") {os.open("place:${place.id}")} }
                    saved.forEach { place -> MenuRow(place.displayName,os.t("锚地 · ${repo.spots.count { it.placeId==place.id }} 个具体位置 · ${place.visitCountCached+place.legacyVisitCount} 次到访","anchorage · ${repo.spots.count { it.placeId==place.id }} spots · ${place.visitCountCached+place.legacyVisitCount} visits"),"anchor") {os.open("anchorage:${place.id}")} }
                    if(repo.error) {
                        Label(os.t("地点目录更新中断，已读资料仍保留。","Place updates were interrupted; previously loaded places are retained."),15,LocalMetro.current.muted)
                        MetroButton(os.t("重新读取地点","reload places"),repo::retryLoading)
                    }
                    MetroButton(os.t("输入坐标","enter coordinates"),{create=true},primary=true)
                    MetroButton(os.t("在海图上选点","choose on chart"),{os.showCrosshair=true;os.openLinked("chart")})
                }
                1 -> {
                    os.activeRoute?.let { route -> MenuRow(os.t("正在导航 · ${route.name}","navigating · ${route.name}"),os.t("目标 ${route.targetIndices.indexOf(os.navigationState.session?.targetIndex).plus(1).coerceAtLeast(1)}/${route.targetIndices.size}","target ${route.targetIndices.indexOf(os.navigationState.session?.targetIndex).plus(1).coerceAtLeast(1)}/${route.targetIndices.size}"),"locate") {os.maps.view("chart",os.center,os.zoom).previewRoute=null;os.displayedRouteId=route.id;os.openLinked("chart")} }
                    if(os.routes.isEmpty()) {Label(os.t("下一站，去哪里？","where to next?"),24);Label(os.t("在海图上规划，保存在这里。选择一条路线后才在地图上预览。","Plan on chart and keep it here. Select a route to preview it on the map."),24,LocalMetro.current.muted)}
                    os.routes.forEach { route -> MenuRow(route.name,"${os.formatDistance(route.length)} · ${route.targetIndices.size} "+os.t("个航点","points"),"route") {os.open("route:${route.id}")} }
                    MetroButton(if(os.draftRoute.isEmpty()) os.t("规划航线","plan a route") else os.t("继续草稿","continue draft"),{resumeOrCreateRouteDraft(os);os.openLinked("chart")},primary=true)
                }
                else -> {
                    AppSection(os.t("自己的收藏","your collections"))
                    val localCollections=os.places.map { it.collection }.filter(String::isNotBlank).distinct()
                    if(localCollections.isEmpty() && repo.collections.isEmpty()) Label(os.t("在地点详情中填写集合名称，把泊位、补给点或计划归到一起。","Give a place a collection name to group berths, supplies or plans."),15,LocalMetro.current.muted)
                    localCollections.forEach { group ->
                        Label(group,20,LocalMetro.current.accentText)
                        os.places.filter {it.collection==group}.forEach { place -> MenuRow(place.name,os.formatCoordinates(place.point),"pin") {os.open("place:${place.id}")} }
                    }
                    repo.collections.forEach { group -> MenuRow(group.name,group.description,"pin") {os.open("collection:${group.id}")} }
                    if(repo.archivedLocations.isNotEmpty()) {
                        AppSection(os.t("已归档地点","archived places"))
                        repo.archivedLocations.forEach {place ->
                            MenuRow(place.displayName,os.t("点按恢复，保留原来的位置、照片和到访","Tap to restore its spots, photos and visits"),"pin") {
                                scope.launch {try {repo.restorePlace(place.id)} catch(cancelled:CancellationException) {throw cancelled} catch(_:Exception) {os.notify("恢复失败，请重试","Could not restore. Please retry.")}}
                            }
                        }
                    }
                    AppSection(os.t("导入与导出","import & export"))
                    Label(os.t("GPX 交换坐标和路线。照片、到访与锚地资料使用设置中的航行与船舶数据备份。","GPX exchanges coordinates and routes. Back up photos, visits and anchorage records in Settings → sailing and vessel data."),15,LocalMetro.current.muted)
                    Label(os.t("暂不导入历史轨迹，也不会把轨迹转换成计划航线。", "Recorded tracks are not supported for import and are never converted into planned routes."),15,LocalMetro.current.muted)
                    MetroButton(if(busy) os.t("正在处理…","working…") else os.t("导入 GPX","import GPX"),{importer.launch(arrayOf("*/*"))},primary=true,enabled=!busy)
                    MetroButton(os.t("导出坐标与航线","export places & routes"),{exporter.launch("Yokuli-sailing.gpx")},enabled=!busy&&(os.allPlaces.isNotEmpty()||os.routes.isNotEmpty()))
                }
            }
        } }
    }
    if(choosingKind)AppDialog(onDismissRequest={choosingKind=false}) {
        AppDialogSurface() {
            AppDialogTitle(os.t("显示坐标","show places"))
            ChoiceRow(os.t("全部坐标","all places"),kind==null) {kind=null;choosingKind=false}
            PlaceKind.entries.forEach {choice->ChoiceRow(choice.label(os),kind==choice) {kind=choice;choosingKind=false}}
            MetroButton(os.t("关闭","close"),{choosingKind=false})
        }
    }
    if(create) CoordinateEditor(os,null,{create=false}) { place -> repo.put(place);create=false;os.open("place:${place.id}") }
    pending?.let { content -> AppDialog(onDismissRequest={pending=null}) {
        AppDialogSurface() {
            AppDialogTitle(os.t("导入这份 GPX","import this GPX"))
            Label(os.t("${content.places.size} 个坐标，${content.routes.size} 条路线","${content.places.size} places, ${content.routes.size} routes"),15)
            Label(os.t("同名且位置相同的坐标、相同点序列的航线可以跳过。","Skip places with the same name and position, and routes with the same name and points."),15,LocalMetro.current.muted)
            fun apply(skip:Boolean) {repo.import(content,skip);pending=null}
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
        else Column(Modifier.fillMaxSize()) {
            PageHeader(os,os.t("坐标","place"))
            PageBody {
                when {
                    !os.sailing.loaded -> MetroProgress(os.t("正在读取地点…","loading place…"))
                    os.sailing.error -> {
                        Label(os.t("暂时无法确认这个坐标，请重新读取。","This coordinate could not be checked. Reload the places."),15)
                        MetroButton(os.t("重新读取","reload"),os.sailing::retryLoading)
                    }
                    else -> MissingSailingObject(os)
                }
            }
        }
        return
    }
    val place=os.places.firstOrNull { it.id==id }
    if(place==null) {
        Column(Modifier.fillMaxSize()) {PageHeader(os,os.t("地点","place"));PageBody {MissingSailingObject(os)}}
        return
    }
    var edit by rememberSaveable(id) {mutableStateOf(false)};var remove by rememberSaveable(id) {mutableStateOf(false)};var start by rememberSaveable(id) {mutableStateOf(false)}
    val (fix,now)=liveNavigationFix(os)
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,place.name,os.t("我的航行","MY SAILING"))
        PageBody {
            CoordinateMapPreview(os,listOf(MapPoint(place.id,place.point,place.name,os.accent)),place.point)
            Label(place.kind.label(os),17,LocalMetro.current.accentText)
            Label(os.formatCoordinates(place.point),20)
            fix?.takeIf {it.fresh(now)}?.let { Label("${os.formatDistance(distance(it.point,place.point))} · ${os.formatBearing(bearing(it.point,place.point))}T",24) }
            PlaceSaveFeedback(os,place)
            place.capture?.let {capture->
                val formatter=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.MEDIUM,if(os.chinese)java.util.Locale.SIMPLIFIED_CHINESE else java.util.Locale.ENGLISH)
                Label(os.t("标记于 ","Marked ")+formatter.format(java.util.Date(capture.capturedAtUtc)),12,LocalMetro.current.muted)
                Label(capture.positionSource?.let {source->os.t("船位来源：","Position source: ")+source+(capture.positionObservedAtUtc?.let {" · "+formatter.format(java.util.Date(it))} ?: "")} ?: os.t("地图上选择的位置","Position chosen on the chart"),12,LocalMetro.current.muted)
            }
            if(place.collection.isNotBlank()) Label(place.collection,18,LocalMetro.current.muted)
            if(place.note.isNotBlank()) Label(place.note,15)
            MetroButton(os.t("前往这里","Go here"),{start=true},primary=true)
            MetroButton(os.t("在海图上查看","Show on chart"),{os.maps.view("chart",os.center,os.zoom).apply {selectedPlaceId=place.id;selectedAisMmsi=null};os.fly(place.point);os.showCrosshair=false;os.openLinked("chart")})
            MetroButton(os.t("在此设置锚警","prepare anchor watch here"),{os.anchorDraft=AnchorDraft(place.point,place.name);os.openLinked("anchor:setup")})
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
    val placeId=rememberSaveable(initial?.id) {initial?.id ?: uid()}
    var name by rememberSaveable(initial?.id) {mutableStateOf(initial?.name.orEmpty())}
    val initialLatitude=rememberSaveable(initial?.id){initial?.point?.lat?.let(os::formatLatitude).orEmpty()}
    val initialLongitude=rememberSaveable(initial?.id){initial?.point?.lon?.let(os::formatLongitude).orEmpty()}
    var latitude by rememberSaveable(initial?.id) {mutableStateOf(initialLatitude)}
    var longitude by rememberSaveable(initial?.id) {mutableStateOf(initialLongitude)}
    var note by rememberSaveable(initial?.id) {mutableStateOf(initial?.note.orEmpty())}
    var group by rememberSaveable(initial?.id) {mutableStateOf(initial?.collection.orEmpty())}
    var kind by rememberSaveable(initial?.id) {mutableStateOf(initial?.kind?:PlaceKind.MARK)}
    val point=preservedCoordinate(initial?.point,initialLatitude,initialLongitude,latitude,longitude)
    if(com.yokuli.shell.compose.LocalInternalAppInputEnabled.current)AppDialog(onDismissRequest=onDismiss) {
        AppDialogSurface() {
            AppDialogTitle(os.t("地点资料","place details"))
            Field(os.t("名称","name"),name,{name=it.take(100)})
            Field(os.t("纬度","latitude")+" · ${os.coordinateFormat}",latitude,{latitude=it.take(60)})
            Field(os.t("经度","longitude")+" · ${os.coordinateFormat}",longitude,{longitude=it.take(60)})
            Column {PlaceKind.entries.forEach {choice->ChoiceRow(choice.label(os),kind==choice) {kind=choice}}}
            Field(os.t("集合","collection"),group,{group=it.take(80)})
            Field(os.t("笔记","notes"),note,{note=it.take(20000)},multiline=true)
            if(point==null&&(latitude.isNotBlank()||longitude.isNotBlank())) Label(os.t("纬度须在 -90 到 90，经度须在 -180 到 180","Latitude must be -90…90, longitude -180…180"),15,LocalMetro.current.muted)
            MetroButton(os.t("保存","save"),{point?.let {newPoint->onSave(Place(placeId,name.trim(),newPoint,note,kind,group.trim(),initial?.takeIf {it.point==newPoint}?.capture))}},primary=true,enabled=name.isNotBlank()&&point!=null)
            MetroButton(os.t("取消","cancel"),onDismiss)
        }
    }
}

/** 同一地点在海图和详情使用相同落盘回执；失败原地重试不产生第二个地点。 */
@Composable internal fun PlaceSaveFeedback(os:OsStore,place:Place) {
    val commit=os.sailing.placeCommit(place.id) ?: return
    val persistence by os.persistenceState.collectAsState()
    var result by remember(commit){mutableStateOf<DurableCommitResult?>(null)}
    LaunchedEffect(commit){result=commit.result.await()}
    when {
        result==DurableCommitResult.SAVED||persistence.durableRevision>=commit.revision->Label(os.t("已保存","Saved"),12,LocalMetro.current.muted)
        result==null->MetroProgress(os.t("正在保存位置…","Saving position…"))
        else->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Label(os.t("尚未保存","Not saved yet"),12,LocalMetro.current.accentText,Modifier.weight(1f))
            MetroButton(os.t("重试保存","Retry save"),{os.sailing.put(os.places.firstOrNull {it.id==place.id} ?: place)})
        }
    }
}

/** 对象失效不是一次成功跳转；保留这次访问的返回关系，列表仅在用户明确选择时进入。 */
@Composable internal fun MissingSailingObject(os:OsStore) {
    val persistence by os.persistenceState.collectAsState()
    if (persistence.readFailure != null) {
        ContentRecoveryStatus(os, showRecoveredCopy = false)
        return
    }
    Label(os.t("对象已不存在","This item no longer exists"),20)
    Label(os.t("它可能已被删除或归档。返回可回到刚才的页面，也可以查看我的航行。","It may have been deleted or archived. Go back to your previous page, or open My Sailing."),15,LocalMetro.current.muted)
    MetroButton(os.t("查看我的航行","open My Sailing"),{os.openLinked("places")})
}
