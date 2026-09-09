package com.yokuli.marine.shell.rebuild.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.anchorwatch.data.anchorage.AnchoragePlaceBundle
import com.yokuli.anchorwatch.data.database.entity.AnchorageSpotEntity
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.MapPoint
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable fun SavedLocationScreen(os:OsStore,id:Long?,initialSpot:Long?=null) {
    val repo=os.sailing;val scope=rememberCoroutineScope()
    var bundle by remember(id) {mutableStateOf<AnchoragePlaceBundle?>(null)}
    var revision by remember(id) {mutableIntStateOf(0)}
    var loading by remember(id) {mutableStateOf(true)}
    var selectedId by remember(id,initialSpot) {mutableStateOf(initialSpot)}
    var edit by remember(id) {mutableStateOf(false)}
    var editSpot by remember(id) {mutableStateOf<AnchorageSpotEntity?>(null)}
    var archive by remember(id) {mutableStateOf(false)}
    var start by remember(id) {mutableStateOf<Place?>(null)}
    var collectionName by remember(id) {mutableStateOf("")}
    var addSpot by remember(id) {mutableStateOf(false)}
    var readError by remember(id) {mutableStateOf(false)}
    LaunchedEffect(id,revision,repo.locations,repo.spots) {
        loading=true
        try {bundle=id?.let {repo.bundle(it)};readError=false}
        catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){readError=true}
        finally {loading=false}
    }
    fun mutate(block:suspend ()->Unit) {scope.launch {
        try {block();revision++}
        catch(cancelled:CancellationException) {throw cancelled}
        catch(_:Exception) {os.notify("未能保存，请重试；当前值守使用的资料不能移除","Could not save. Retry; a place used by an active watch cannot be removed.")}
    } }
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->if(uri!=null&&id!=null)mutate{repo.photos.import(id,uri)} }
    val data=bundle
    if(data==null) {
        Column {PageHeader(os,os.t("地点","place"));Label(if(loading)os.t("正在读取…","loading…")else if(readError)os.t("暂时无法读取，请重试","could not load, please retry")else os.t("这个地点已不可用","this place is unavailable"),22,modifier=Modifier.padding(22.dp))
            if(readError)MetroButton(os.t("重试","retry"),{revision++})}
        return
    }
    val selected=data.spots.firstOrNull {it.id==selectedId}?:data.spots.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,data.place.displayName,os.t("我的航行","MY SAILING"))
        Pivot(listOf(os.t("地点","place"),os.t("资料","details"),os.t("到访","visits"),os.t("照片","photos"))) { page -> PageBody {
            when(page) {
                0->{
                    CoordinateMapPreview(os,data.spots.map {MapPoint("spot:${it.id}",GeoPoint(it.latitude,it.longitude),it.name,if(it.id==selected?.id)os.accent else 0xFF888888)},
                        selected?.let {GeoPoint(it.latitude,it.longitude)} ?: GeoPoint(data.place.centerLatitude,data.place.centerLongitude))
                    if(data.spots.isEmpty()) Label(os.t("这个地点还没有具体坐标，地点中心不代表实际锚位。","This place has no specific spot. Its center is not an actual anchor position."),19,LocalMetro.current.muted)
                    data.spots.forEach {spot->MenuRow((if(spot.id==selected?.id)"✓ " else "")+spot.name,os.formatCoordinates(GeoPoint(spot.latitude,spot.longitude)),"pin"){selectedId=spot.id}}
                    MetroButton(os.t("添加另一个具体位置","add another spot"),{addSpot=true})
                    selected?.let {spot->
                        val point=GeoPoint(spot.latitude,spot.longitude)
                        Label(spotSource(os,spot.coordinateSource)+spot.coordinateUncertaintyMeters?.let {" · ±${os.formatDistance(it)}"}.orEmpty(),16,LocalMetro.current.muted)
                        MetroButton(os.t("在海图上查看","show on chart"),{os.fly(point);os.showCrosshair=true;os.open("chart")},primary=true)
                        MetroButton(os.t("前往这个坐标","go to this spot"),{start=Place("spot:${spot.id}","${data.place.displayName} · ${spot.name}",point,kind=PlaceKind.ANCHORAGE)})
                        MetroButton(os.t("在此设置锚警","prepare anchor watch here"),{os.anchorDraft=AnchorDraft(point,spot.name,data.place.id,spot.id,spot.preferredAlarmRadiusMeters);os.open("anchor")})
                    }
                    if(data.place.description.isNotBlank()) Label(data.place.description,20)
                }
                1->{
                    if(data.place.personalNotes.isNotBlank()) Label(data.place.personalNotes,22)
                    selected?.let {spot->
                        Label(spot.name,30,LocalMetro.current.accent)
                        listOf(os.t("水深","depth") to spot.typicalWaterDepthMeters,os.t("锚链长度","rode") to spot.typicalRodeLengthMeters,os.t("守望半径","watch radius") to spot.preferredAlarmRadiusMeters)
                            .filter {it.second!=null}.forEach {(label,value)->Label("$label  ${os.formatDepth(value)}",23)}
                        if(spot.approachNotes.isNotBlank()) Label(spot.approachNotes,20)
                        if(spot.personalNotes.isNotBlank()) Label(spot.personalNotes,20)
                        MetroButton(os.t("编辑这个坐标与参数","edit this spot"),{editSpot=spot})
                    }
                    MetroButton(os.t("编辑地点名称与笔记","edit place name & notes"),{edit=true},primary=true)
                    Label(os.t("集合","collections"),30)
                    repo.collections.forEach {group->MenuRow((if(data.collections.any{it.id==group.id})"✓ " else "")+group.name,null,"pin"){mutate{repo.toggleCollection(group.id,data.place.id)}}}
                    Field(os.t("新集合名称","new collection name"),collectionName,{collectionName=it.take(80)})
                    MetroButton(os.t("创建集合","create collection"),{mutate{repo.createCollection(collectionName);collectionName=""}},enabled=collectionName.isNotBlank())
                    if(data.protection.isNotEmpty()) {
                        Label(os.t("避风与遮蔽记录","shelter notes"),29)
                        data.protection.forEach {Label("${it.sector} · ${it.medium} · ${it.rating}${if(it.notes.isNotBlank()) " · ${it.notes}" else ""}",18)}
                    }
                    MetroButton(os.t("归档这个地点","archive this place"),{archive=true})
                }
                2->{
                    Label(os.t("在这里发生过的事","time spent here"),31)
                    if(data.visits.isEmpty()) Label(os.t("还没有逐次到访记录。","No individual visits recorded yet."),19,LocalMetro.current.muted)
                    if(data.place.legacyVisitCount>0) Label(os.t("另有 ${data.place.legacyVisitCount} 次旧版汇总到访","${data.place.legacyVisitCount} additional visits in the older summary"),16,LocalMetro.current.muted)
                    data.visits.forEach {visit->
                        Label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(visit.startedAt)),26,LocalMetro.current.accent)
                        Label(if(visit.endedAt!=null)durationLabel(visit.endedAt!!-visit.startedAt)else os.t("未记录结束时间","end time not recorded"),20)
                        Label(os.t("${visit.alarmCount} 次警报","${visit.alarmCount} alarms"),18)
                        if(visit.userNotes.isNotBlank())Label(visit.userNotes,20)
                        visit.maxExcursionMeters?.let {Label(os.t("最大偏移 ${os.formatDistance(it)}","maximum excursion ${os.formatDistance(it)}"),18)}
                    }
                }
                else->{
                    Label(os.t("记住这个地方","remember this place"),31)
                    data.photos.forEach {photo->
                        val path=repo.photos.file(photo,true).absolutePath
                        val bitmap by produceState<ImageBitmap?>(null,path) {value=withContext(Dispatchers.IO){BitmapFactory.decodeFile(path)?.asImageBitmap()}}
                        bitmap?.let {Image(it,photo.caption.ifBlank{data.place.displayName},Modifier.fillMaxWidth().height(240.dp),contentScale=ContentScale.Crop)}
                        if(photo.caption.isNotBlank())Label(photo.caption,19)
                    }
                    if(data.photos.isEmpty())Label(os.t("照片会跟随地点保存，不是另一份收藏。","Photos stay with this place."),19,LocalMetro.current.muted)
                    MetroButton(os.t("添加照片","add photo"),{photoPicker.launch(arrayOf("image/jpeg","image/png","image/webp"))},primary=true)
                }
            }
        } }
    }
    if(edit) PlaceNotesDialog(os,data.place.displayName,data.place.personalNotes,{edit=false}) {name,notes->mutate{repo.updatePlace(data.place.copy(displayName=name,personalNotes=notes));edit=false}}
    if(addSpot)MapPicker(os,selected?.let{GeoPoint(it.latitude,it.longitude)}?:GeoPoint(data.place.centerLatitude,data.place.centerLongitude),onConfirm={point->
        mutate{selectedId=repo.createSpot(data.place.id,os.t("位置 ${data.spots.size+1}","spot ${data.spots.size+1}"),point);addSpot=false}
    },onCancel={addSpot=false})
    editSpot?.let {spot->SpotEditor(os,spot,{editSpot=null}) {updated->mutate{repo.updateSpot(updated);editSpot=null}}}
    start?.let {place->StartNavigationDialog(os,Route("goto:${place.id}",place.name,listOf(place.point))){start=null}}
    if(archive)ConfirmDialog(os,os.t("归档 ${data.place.displayName}？历史和照片仍保留。","Archive ${data.place.displayName}? History and photos are retained."),{archive=false}){mutate{repo.archivePlace(data.place.id);archive=false;os.back()}}
}

@Composable private fun PlaceNotesDialog(os:OsStore,initialName:String,initialNotes:String,onDismiss:()->Unit,onSave:(String,String)->Unit) {
    var name by remember {mutableStateOf(initialName)};var notes by remember {mutableStateOf(initialNotes)}
    Dialog(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().background(LocalMetro.current.bg).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        Label(os.t("地点资料","place details"),31)
        Field(os.t("名称","name"),name,{name=it.take(200)})
        Field(os.t("笔记","notes"),notes,{notes=it.take(20000)},multiline=true)
        MetroButton(os.t("保存","save"),{onSave(name.trim(),notes)},primary=true,enabled=name.isNotBlank())
        MetroButton(os.t("取消","cancel"),onDismiss)
    }}
}

@Composable private fun SpotEditor(os:OsStore,spot:AnchorageSpotEntity,onDismiss:()->Unit,onSave:(AnchorageSpotEntity)->Unit) {
    var name by remember {mutableStateOf(spot.name)};var note by remember {mutableStateOf(spot.personalNotes)}
    var lat by remember {mutableStateOf(spot.latitude.toString())};var lon by remember {mutableStateOf(spot.longitude.toString())}
    var depth by remember {mutableStateOf(spot.typicalWaterDepthMeters?.toString().orEmpty())}
    var rode by remember {mutableStateOf(spot.typicalRodeLengthMeters?.toString().orEmpty())}
    var radius by remember {mutableStateOf(spot.preferredAlarmRadiusMeters?.toString().orEmpty())}
    val point=lat.toDoubleOrNull()?.let {a->lon.toDoubleOrNull()?.let{b->GeoPoint(a,b).takeIf(GeoPoint::valid)}}
    fun valid(value:String)=value.isBlank()||value.toDoubleOrNull()?.let {it.isFinite()&&it>=0}==true
    Dialog(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().heightIn(max=650.dp).background(LocalMetro.current.bg).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Label(os.t("具体坐标","specific spot"),31)
        Field(os.t("名称","name"),name,{name=it.take(200)})
        Field(os.t("纬度 · 十进制度","latitude · decimal degrees"),lat,{lat=it})
        Field(os.t("经度 · 十进制度","longitude · decimal degrees"),lon,{lon=it})
        Field(os.t("水深 · m","depth · m"),depth,{depth=it});Field(os.t("锚链 · m","rode · m"),rode,{rode=it})
        Field(os.t("守望半径 · m","watch radius · m"),radius,{radius=it})
        Field(os.t("笔记","notes"),note,{note=it.take(20000)},multiline=true)
        MetroButton(os.t("保存收藏资料","save place details"),{point?.let {onSave(spot.copy(name=name.trim(),latitude=it.lat,longitude=it.lon,personalNotes=note,
            coordinateSource=if(it==GeoPoint(spot.latitude,spot.longitude))spot.coordinateSource else "USER_EDITED",
            coordinateUncertaintyMeters=if(it==GeoPoint(spot.latitude,spot.longitude))spot.coordinateUncertaintyMeters else null,
            typicalWaterDepthMeters=depth.toDoubleOrNull(),typicalRodeLengthMeters=rode.toDoubleOrNull(),preferredAlarmRadiusMeters=radius.toDoubleOrNull()))}},
            primary=true,enabled=name.isNotBlank()&&point!=null&&valid(depth)&&valid(rode)&&valid(radius))
        Label(os.t("编辑收藏不改变当前锚警的中心和范围。","Editing this saved spot leaves the active watch unchanged."),15,LocalMetro.current.muted)
        MetroButton(os.t("取消","cancel"),onDismiss)
    }}
}

private fun spotSource(os:OsStore,value:String)=when(value){
    "CONFIRMED_ANCHOR"->os.t("确认的锚位","confirmed anchor position")
    "ESTIMATED_REGION_CENTRE"->os.t("估计坐标","estimated position")
    "TEMPORARY_WATCH_REFERENCE"->os.t("临时守望参考","temporary watch reference")
    "MAP_SELECTED"->os.t("地图选点","selected on chart")
    "USER_EDITED"->os.t("手动编辑的坐标","manually edited position")
    else->os.t("保存的坐标","saved position")
}

@Composable fun CollectionScreen(os:OsStore,id:Long?) {
    val repo=os.sailing
    val members by produceState<List<Long>>(emptyList(),id,repo.locations) {value=withContext(Dispatchers.IO){repo.database.anchorageCollectionDao().membershipsNow().filter{it.collectionId==id}.map{it.placeId}}}
    Column(Modifier.fillMaxSize()){PageHeader(os,repo.collections.firstOrNull{it.id==id}?.name?:os.t("集合","collection"));PageBody{
        repo.locations.filter{it.id in members}.forEach{place->MenuRow(place.displayName,place.personalNotes.takeIf(String::isNotBlank),"pin"){os.open("anchorage:${place.id}")}}
        if(members.isEmpty())Label(os.t("在地点资料中把地点加入这个集合。","Add places to this collection from place details."),23)
    }}
}
