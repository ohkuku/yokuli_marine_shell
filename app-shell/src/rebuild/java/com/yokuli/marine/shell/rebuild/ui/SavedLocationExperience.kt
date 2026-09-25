package com.yokuli.marine.shell.rebuild.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.util.Locale

@Composable fun SavedLocationScreen(os:OsStore,id:Long?,initialSpot:Long?=null) {
    val repo=os.sailing;val scope=rememberCoroutineScope()
    var bundle by remember(id) {mutableStateOf<AnchoragePlaceBundle?>(null)}
    var revision by remember(id) {mutableIntStateOf(0)}
    var loading by remember(id) {mutableStateOf(true)}
    var selectedId by rememberSaveable(id,initialSpot) {mutableStateOf(initialSpot)}
    var edit by rememberSaveable(id) {mutableStateOf(false)}
    var editSpotId by rememberSaveable(id) {mutableStateOf<Long?>(null)}
    var archive by rememberSaveable(id) {mutableStateOf(false)}
    var startSpotId by rememberSaveable(id) {mutableStateOf<Long?>(null)}
    var collectionName by rememberSaveable(id) {mutableStateOf("")}
    var addSpot by rememberSaveable(id) {mutableStateOf(false)}
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
        Column {PageHeader(os,os.t("地点","place"))
            if(loading)MetroProgress(os.t("正在读取…","loading…"),Modifier.padding(22.dp))
            else if(readError) {
                Label(os.t("暂时无法读取，请重试","could not load, please retry"),15,modifier=Modifier.padding(22.dp))
                MetroButton(os.t("重试","retry"),{revision++})
            } else PageBody {MissingSailingObject(os)}
        }
        return
    }
    val selected=if(selectedId==null)data.spots.firstOrNull() else data.spots.firstOrNull {it.id==selectedId}
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,data.place.displayName,os.t("我的航行","MY SAILING"))
        Pivot(listOf(os.t("地点","place"),os.t("资料","details"),os.t("到访","visits"),os.t("照片","photos"))) { page -> PageBody {
            if(readError) {
                Label(os.t("更新失败，以下是此前读取的资料。","Update failed. Previously loaded information is shown below."),15,LocalMetro.current.muted)
                MetroButton(os.t("重新读取","reload"),{revision++})
            }
            if(selectedId!=null && selected==null)Label(os.t("选择的坐标已不存在，请选择本地点的另一个坐标。","The selected coordinate no longer exists. Select another spot at this place."),15,LocalMetro.current.muted)
            when(page) {
                0->{
                    CoordinateMapPreview(os,data.spots.map {MapPoint("spot:${it.id}",GeoPoint(it.latitude,it.longitude),it.name,if(it.id==selected?.id)os.accent else 0xFF888888)},
                        selected?.let {GeoPoint(it.latitude,it.longitude)} ?: GeoPoint(data.place.centerLatitude,data.place.centerLongitude))
                    if(data.spots.isEmpty()) Label(os.t("这个地点还没有具体坐标，地点中心不代表实际锚位。","This place has no specific spot. Its center is not an actual anchor position."),15,LocalMetro.current.muted)
                    data.spots.forEach {spot->ChoiceRow(spot.name,spot.id==selected?.id,os.formatCoordinates(GeoPoint(spot.latitude,spot.longitude))) {selectedId=spot.id}}
                    MetroButton(os.t("添加另一个具体位置","add another spot"),{addSpot=true})
                    selected?.let {spot->
                        val point=GeoPoint(spot.latitude,spot.longitude)
                        Label(spotSource(os,spot.coordinateSource)+spot.coordinateUncertaintyMeters?.let {" · ±${os.formatLength(it)}"}.orEmpty(),16,LocalMetro.current.muted)
                        MetroButton(os.t("在海图上查看","show on chart"),{os.fly(point);os.showCrosshair=true;os.openLinked("chart")},primary=true)
                        MetroButton(os.t("前往这个坐标","go to this spot"),{startSpotId=spot.id})
                        MetroButton(os.t("在此设置锚警","prepare anchor watch here"),{os.anchorDraft=AnchorDraft(point,spot.name,data.place.id,spot.id,spot.preferredAlarmRadiusMeters);os.openLinked("anchor:setup")})
                    }
                    if(data.place.description.isNotBlank()) Label(data.place.description,15)
                }
                1->{
                    if(data.place.personalNotes.isNotBlank()) Label(data.place.personalNotes,15)
                    selected?.let {spot->
                        Label(spot.name,20,LocalMetro.current.accentText)
                        spot.typicalWaterDepthMeters?.let { Label(os.t("水深","depth")+"  "+os.formatDepth(it),20) }
                        spot.typicalRodeLengthMeters?.let { Label(os.t("锚链长度","rode")+"  "+os.formatLength(it),20) }
                        spot.preferredAlarmRadiusMeters?.let { Label(os.t("守望半径","watch radius")+"  "+os.formatLength(it),20) }
                        if(spot.approachNotes.isNotBlank()) Label(spot.approachNotes,15)
                        if(spot.personalNotes.isNotBlank()) Label(spot.personalNotes,15)
                        MetroButton(os.t("编辑这个坐标与参数","edit this spot"),{editSpotId=spot.id})
                    }
                    MetroButton(os.t("编辑地点名称与笔记","edit place name & notes"),{edit=true},primary=true)
                    AppSection(os.t("集合","collections"))
                    repo.collections.forEach {group->MenuRow((if(data.collections.any{it.id==group.id})"✓ " else "")+group.name,null,"pin"){mutate{repo.toggleCollection(group.id,data.place.id)}}}
                    Field(os.t("新集合名称","new collection name"),collectionName,{collectionName=it.take(80)})
                    MetroButton(os.t("创建集合","create collection"),{mutate{repo.createCollection(collectionName);collectionName=""}},enabled=collectionName.isNotBlank())
                    if(data.protection.isNotEmpty()) {
                        AppSection(os.t("避风与遮蔽记录","shelter notes"))
                        data.protection.forEach {Label("${it.sector} · ${it.medium} · ${it.rating}${if(it.notes.isNotBlank()) " · ${it.notes}" else ""}",18)}
                    }
                    MetroButton(os.t("归档这个地点","archive this place"),{archive=true})
                }
                2->{
                    AppSection(os.t("在这里发生过的事","time spent here"))
                    if(data.visits.isEmpty()) Label(os.t("还没有逐次到访记录。","No individual visits recorded yet."),19,LocalMetro.current.muted)
                    if(data.place.legacyVisitCount>0) Label(os.t("另有 ${data.place.legacyVisitCount} 次旧版汇总到访","${data.place.legacyVisitCount} additional visits in the older summary"),15,LocalMetro.current.muted)
                    data.visits.forEach {visit->
                        Label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,if(os.chinese)Locale.SIMPLIFIED_CHINESE else Locale.US).format(Date(visit.startedAt)),20,LocalMetro.current.accentText)
                        Label(if(visit.endedAt!=null)durationLabel(visit.endedAt!!-visit.startedAt)else os.t("未记录结束时间","end time not recorded"),15)
                        Label(os.t("${visit.alarmCount} 次警报","${visit.alarmCount} alarms"),15)
                        if(visit.userNotes.isNotBlank())Label(visit.userNotes,15)
                        visit.maxExcursionMeters?.let {Label(os.t("最大偏移 ${os.formatLength(it)}","maximum excursion ${os.formatLength(it)}"),15)}
                    }
                }
                else->{
                    AppSection(os.t("记住这个地方","remember this place"))
                    data.photos.forEach {photo->
                        val path=repo.photos.file(photo,true).absolutePath
                        val bitmap by produceState<ImageBitmap?>(null,path) {value=withContext(Dispatchers.IO){BitmapFactory.decodeFile(path)?.asImageBitmap()}}
                        bitmap?.let {Image(it,photo.caption.ifBlank{data.place.displayName},Modifier.fillMaxWidth().height(240.dp),contentScale=ContentScale.Crop)}
                        if(photo.caption.isNotBlank())Label(photo.caption,19)
                    }
                    if(data.photos.isEmpty())Label(os.t("照片会跟随地点保存，不是另一份收藏。","Photos stay with this place."),15,LocalMetro.current.muted)
                    MetroButton(os.t("添加照片","add photo"),{photoPicker.launch(arrayOf("image/jpeg","image/png","image/webp"))},primary=true)
                }
            }
        } }
    }
    if(edit) PlaceNotesDialog(os,data.place.displayName,data.place.personalNotes,{edit=false}) {name,notes->mutate{repo.updatePlace(data.place.copy(displayName=name,personalNotes=notes));edit=false}}
    if(addSpot)MapPicker(os,selected?.let{GeoPoint(it.latitude,it.longitude)}?:GeoPoint(data.place.centerLatitude,data.place.centerLongitude),onConfirm={point->
        mutate{selectedId=repo.createSpot(data.place.id,os.t("位置 ${data.spots.size+1}","spot ${data.spots.size+1}"),point);addSpot=false}
    },onCancel={addSpot=false})
    data.spots.firstOrNull {it.id==editSpotId}?.let {spot->SpotEditor(os,spot,{editSpotId=null}) {updated->mutate{repo.updateSpot(updated);editSpotId=null}}}
    data.spots.firstOrNull {it.id==startSpotId}?.let {spot->
        StartNavigationDialog(os,Route("goto:spot:${spot.id}","${data.place.displayName} · ${spot.name}",listOf(GeoPoint(spot.latitude,spot.longitude)))){startSpotId=null}
    }
    if(archive)ConfirmDialog(os,os.t("归档 ${data.place.displayName}？历史和照片仍保留。","Archive ${data.place.displayName}? History and photos are retained."),{archive=false}){mutate{repo.archivePlace(data.place.id);archive=false;os.back()}}
}

@Composable private fun PlaceNotesDialog(os:OsStore,initialName:String,initialNotes:String,onDismiss:()->Unit,onSave:(String,String)->Unit) {
    var name by rememberSaveable(initialName) {mutableStateOf(initialName)};var notes by rememberSaveable(initialName) {mutableStateOf(initialNotes)}
    AppDialog(onDismissRequest=onDismiss){AppDialogSurface() {
        AppDialogTitle(os.t("地点资料","place details"))
        Field(os.t("名称","name"),name,{name=it.take(200)})
        Field(os.t("笔记","notes"),notes,{notes=it.take(20000)},multiline=true)
        MetroButton(os.t("保存","save"),{onSave(name.trim(),notes)},primary=true,enabled=name.isNotBlank())
        MetroButton(os.t("取消","cancel"),onDismiss)
    }}
}

@Composable private fun SpotEditor(os:OsStore,spot:AnchorageSpotEntity,onDismiss:()->Unit,onSave:(AnchorageSpotEntity)->Unit) {
    var name by rememberSaveable(spot.id) {mutableStateOf(spot.name)};var note by rememberSaveable(spot.id) {mutableStateOf(spot.personalNotes)}
    val initialLat=rememberSaveable(spot.id){os.formatLatitude(spot.latitude)}
    val initialLon=rememberSaveable(spot.id){os.formatLongitude(spot.longitude)}
    var lat by rememberSaveable(spot.id) {mutableStateOf(initialLat)};var lon by rememberSaveable(spot.id) {mutableStateOf(initialLon)}
    // 显示偏好只转换编辑文字，收藏仍精确保留米值；切换单位不清空未保存的草稿。
    val depth=rememberUnitNumberDraft(spot.typicalWaterDepthMeters,os.depthUnitLabel,os::depthValue,os::depthMeters,spot.id)
    val rode=rememberUnitNumberDraft(spot.typicalRodeLengthMeters,os.lengthUnitLabel,os::lengthValue,os::lengthMeters,spot.id)
    val radius=rememberUnitNumberDraft(spot.preferredAlarmRadiusMeters,os.lengthUnitLabel,os::lengthValue,os::lengthMeters,spot.id)
    val point=preservedCoordinate(GeoPoint(spot.latitude,spot.longitude),initialLat,initialLon,lat,lon)
    fun valid(value:UnitNumberDraft)=value.text.isBlank()||value.value?.let {it>=0}==true
    AppDialog(onDismissRequest=onDismiss){AppDialogSurface() {
        AppDialogTitle(os.t("具体坐标","specific spot"))
        Field(os.t("名称","name"),name,{name=it.take(200)})
        Field(os.t("纬度","latitude")+" · ${os.coordinateFormat}",lat,{lat=it})
        Field(os.t("经度","longitude")+" · ${os.coordinateFormat}",lon,{lon=it})
        Field(os.t("水深","depth")+" · "+os.depthUnitLabel,depth.text,depth::edit,number=true)
        Field(os.t("锚链","rode")+" · "+os.lengthUnitLabel,rode.text,rode::edit,number=true)
        Field(os.t("守望半径","watch radius")+" · "+os.lengthUnitLabel,radius.text,radius::edit,number=true)
        Field(os.t("笔记","notes"),note,{note=it.take(20000)},multiline=true)
        MetroButton(os.t("保存收藏资料","save place details"),{point?.let {onSave(spot.copy(name=name.trim(),latitude=it.lat,longitude=it.lon,personalNotes=note,
            coordinateSource=if(it==GeoPoint(spot.latitude,spot.longitude))spot.coordinateSource else "USER_EDITED",
            coordinateUncertaintyMeters=if(it==GeoPoint(spot.latitude,spot.longitude))spot.coordinateUncertaintyMeters else null,
            typicalWaterDepthMeters=depth.value,typicalRodeLengthMeters=rode.value,preferredAlarmRadiusMeters=radius.value))}},
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
    var revision by remember(id) {mutableIntStateOf(0)}
    var members by remember(id) {mutableStateOf<List<Long>?>(null)}
    var readError by remember(id) {mutableStateOf(false)}
    LaunchedEffect(repo,id,revision) {
        readError=false
        if(id==null) {members=emptyList();return@LaunchedEffect}
        try {repo.observeCollectionMembers(id).collect {members=it;readError=false}}
        catch(cancelled:CancellationException) {throw cancelled}
        catch(_:Exception) {readError=true}
    }
    val collection=repo.collections.firstOrNull {it.id==id}
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,collection?.name ?: os.t("集合","collection"))
        PageBody {
            when {
                readError || repo.error -> {
                    Label(os.t("暂时无法更新集合，已读资料仍保留。","Collection updates are unavailable; previously loaded places are retained."),15)
                    MetroButton(os.t("重新读取","reload"),{repo.retryLoading();revision++})
                }
                !repo.loaded || members==null -> MetroProgress(os.t("正在读取集合…","loading collection…"))
                collection==null -> MissingSailingObject(os)
                members.orEmpty().isEmpty() -> Label(os.t("在地点资料中把地点加入这个集合。","Add places to this collection from place details."),15)
            }
            if(collection!=null)repo.locations.filter {it.id in members.orEmpty()}.forEach {place ->
                MenuRow(place.displayName,place.personalNotes.takeIf(String::isNotBlank),"pin") {os.open("anchorage:${place.id}")}
            }
        }
    }
}
