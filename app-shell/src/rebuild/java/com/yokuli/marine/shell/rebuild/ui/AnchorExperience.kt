package com.yokuli.marine.shell.rebuild.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.yokuli.anchorwatch.AnchorWatchInput
import com.yokuli.anchorwatch.AnchorSetupDraft
import com.yokuli.anchorwatch.MainUiState
import com.yokuli.anchorwatch.MainViewModel
import com.yokuli.anchorwatch.data.database.AnchorSessionEntity
import com.yokuli.anchorwatch.domain.anchor.AnchorCentreApplyPolicy
import com.yokuli.anchorwatch.domain.anchor.AnchorCentreRecalculationStatus
import com.yokuli.anchorwatch.domain.anchor.AnchorDepthSource
import com.yokuli.anchorwatch.domain.anchor.CoordinateParser
import com.yokuli.anchorwatch.domain.condition.ConditionGuardConfig
import com.yokuli.anchorwatch.domain.model.*
import com.yokuli.anchorwatch.domain.safety.AnchorSetupReadinessEvaluator
import com.yokuli.anchorwatch.domain.safety.AnchorSetupReadinessInput
import com.yokuli.anchorwatch.location.AcceptedAnchorPositionPolicy
import com.yokuli.anchorwatch.location.GpsSourceSafety
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.yokuli.marine.core.design.MarineDisplayUnits
import com.yokuli.shell.contract.MeasurementUnitSystem

/** Anchor owns the watch; the map receives a scene and the engine receives explicit commands. */
@Composable
fun AnchorExperience(os: OsStore) {
    val marine=os.marine
    if(marine==null) {Column {PageHeader(os,os.t("锚警","anchor watch"));Label(os.t("正在恢复锚警…","restoring anchor watch…"),24)};return}
    val vm=marine.vm
    val state by vm.ui.collectAsState()
    val data by os.hub.state.collectAsState()
    val tick=rememberMarineClock()
    val fix=data.fix(os.positionSource)
    val active=state.active
    val view=os.maps.view("anchor",fix?.point ?: os.center,16.0)
    view.scaleTopDp=90f
    val referenceKey=os.anchorDraft?.let {"${it.placeId}:${it.spotId}:${it.point.lat}:${it.point.lon}"} ?: "os-watch"
    val restored=state.anchorSetupDraft?.takeIf {it.referenceKey==referenceKey}
    var page by rememberSaveable {mutableStateOf(if(restored!=null && active==null)"setup" else "watch")}
    var picked by remember {mutableStateOf(restored?.mapLatitude?.let {lat->restored.mapLongitude?.let {lon->GeoPoint(lat,lon)}})}
    var origin by rememberSaveable {mutableStateOf(restored?.knownMethod?.let {runCatching {AnchorCenterSource.valueOf(it)}.getOrNull()} ?: AnchorCenterSource.MAP_PICK)}
    var picking by remember {mutableStateOf(false)}
    var estimate by rememberSaveable {mutableStateOf(restored?.estimate ?: false)}
    var radius by rememberSaveable {mutableStateOf(restored?.alarmRadius?.takeIf {it.isNotBlank()} ?: state.settings.preferredAlarmRadiusMeters.toString())}
    var rode by rememberSaveable {mutableStateOf(restored?.rode ?: "40")}
    var depth by rememberSaveable {mutableStateOf(restored?.depth.orEmpty())}
    var manual by rememberSaveable {mutableStateOf(restored?.manualCoordinate.orEmpty())}
    var editingRadius by remember {mutableStateOf(false)}
    var confirmEnd by remember {mutableStateOf(false)}
    var estimateChoice by remember {mutableStateOf<AnchorEstimateChoice?>(null)}
    var pending by remember {mutableStateOf<String?>(null)}
    var feedbackBaseline by remember {mutableLongStateOf(0L)}
    var feedback by remember {mutableStateOf<String?>(null)}
    var endingId by remember {mutableStateOf<Long?>(null)}
    var saveSession by remember {mutableStateOf<AnchorSessionEntity?>(null)}
    var saving by remember {mutableStateOf(false)}
    var savedAnchorageId by rememberSaveable {mutableStateOf<Long?>(null)}
    var savedWatchId by rememberSaveable {mutableStateOf<Long?>(null)}
    val scope=rememberCoroutineScope()
    val c=LocalMetro.current
    val density=LocalDensity.current
    val source=state.settings.gpsDataSource
    val readiness=AcceptedAnchorPositionPolicy.evaluate(state.acceptedPosition,source,tick,state.settings.gpsLossSeconds*1000L,state.connection,state.nmeaConnectionStartedElapsed,state.nmeaTransportDiagnostics.connectionGeneration,
        GpsSourceSafety.blocksSystemGps(state.settings.mockEnabled,state.mockGps.state))
    val sourcePoint=readiness.fix?.let {GeoPoint(it.latitude,it.longitude)}
    val notificationGranted=Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(os.context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    var notifications by remember {mutableStateOf(notificationGranted)}
    val notificationRequest=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {notifications=it}
    LaunchedEffect(tick) {notifications=Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(os.context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED}
    AppBackHandler(enabled=page!="watch") {page=if(page=="advanced")"setup"else "watch"}
    BindInternalAppInputHandler {input->if(input==ShellInput.BACK && page!="watch"){page=if(page=="advanced")"setup"else "watch";true}else false}
    LaunchedEffect(os.anchorDraft,active?.id) {
        if(active==null && restored==null) os.anchorDraft?.let {draft ->picked=draft.point;origin=AnchorCenterSource.MAP_PICK;estimate=false;radius=(draft.radiusMeters ?: state.settings.preferredAlarmRadiusMeters).toString();view.fly(draft.point,16.0);page="setup"}
    }
    LaunchedEffect(referenceKey,picked,origin,estimate,radius,rode,depth,manual,page) {
        if(active==null && (page=="setup" || page=="advanced")) vm.saveAnchorSetupDraft(AnchorSetupDraft(referenceKey=referenceKey,estimate=estimate,knownMethod=origin.name,manualCoordinate=manual,mapLatitude=picked?.lat,mapLongitude=picked?.lon,alarmRadius=radius,rode=rode,depth=depth))
    }
    LaunchedEffect(active?.id) {
        active?.let {session ->view.fit(listOf(destination(watchCenter(session),session.alarmRadiusMeters*1.7,0.0),destination(watchCenter(session),session.alarmRadiusMeters*1.7,180.0)))}
    }
    fun command(kind:String,action:()->Unit) {
        feedback=null;feedbackBaseline=state.runtimeDiagnostics.lastUserFeedback?.id ?: 0L
        runCatching {action()}.onSuccess {pending=kind}.onFailure {feedback=it.message ?: os.t("操作未完成","operation failed")}
    }
    LaunchedEffect(pending,active?.id,active?.paused,active?.monitoringPhase,state.runtimeDiagnostics.lastUserFeedback?.id) {
        val operation=pending ?: return@LaunchedEffect
        val complete=when(operation) {"start"->active!=null;"pause"->active?.paused==true;"resume"->active!=null&&!active.paused;"end"->active==null;else->false}
        if(complete) {
            pending=null
            if(operation=="start") {page="watch";os.anchorDraft=null;vm.clearAnchorSetupDraft()}
            if(operation=="end") {page="review:${endingId}";endingId=null}
            return@LaunchedEffect
        }
        val latest=state.runtimeDiagnostics.lastUserFeedback
        if(latest!=null && latest.id>feedbackBaseline) {feedback=latest.message;pending=null;return@LaunchedEffect}
        delay(9000)
        if(pending==operation) {pending=null;feedback=os.t("尚未收到状态确认。当前状态如下，可检查后重试。","No confirmation received. Check the current state before retrying.")}
    }
    val coverage=remember(active?.id) {active?.let {AnchorSwingCoverage(watchCenter(it),it.alarmRadiusMeters)}}
    var swingAreas by remember(active?.id) {mutableStateOf<List<MapArea>>(emptyList())}
    LaunchedEffect(active?.id,state.points.lastOrNull()?.id) {
        val session=active ?: return@LaunchedEffect
        val aggregate=coverage ?: return@LaunchedEffect
        swingAreas=withContext(Dispatchers.IO) {
            while(true) {
                val batch=os.sailing.database.anchorDao().pointsPage(session.id,aggregate.lastTimestamp,aggregate.lastId,1000)
                aggregate.add(batch)
                if(batch.size<1000)break
            }
            aggregate.areas()
        }
    }
    val scene=anchorScene(os,state,fix?.let {MapVessel(it.point,it.freshCourse(tick),it.fresh(tick)&&readiness.ready)},if(active==null)picked else null,radius.toDoubleOrNull(),System.currentTimeMillis(),swingAreas)
    val back={page=if(page=="advanced")"setup" else "watch"}
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,when {page=="advanced"->os.t("确认锚点","confirm anchor");page=="history"->os.t("锚泊历史","anchor history");page=="estimate"->os.t("锚点估计","anchor estimate");page.startsWith("review:")->os.t("锚泊回顾","watch review");else->os.t("锚警","anchor watch")},onBack=if(page=="watch")null else back)
        if(page=="watch" || page=="setup") {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MarineMap(os.maps,scene,view,Modifier.fillMaxSize())
                MapSourceButton(os,Modifier.align(Alignment.TopEnd).background(c.bg))
                Column(Modifier.align(Alignment.TopEnd).padding(top=48.dp,end=8.dp).background(c.bg)) {
                    IconAction("plus",os.t("放大","zoom in"),{view.fly(view.center,view.zoom+1)})
                    IconAction("minus",os.t("缩小","zoom out"),{view.fly(view.center,view.zoom-1)})
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged {view.bottomOverlayDp=with(density){it.height.toDp().value}}.background(c.bg).padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(page=="setup" && active==null) {
                        Label(os.t("确认下锚位置","confirm anchor position"),25)
                        Label(picked?.let(os::formatCoordinates) ?: os.t("等待船位，或拖动地图选择锚点","wait for a position, or select the anchor on the map"),14,c.muted)
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            IconAction("minus",os.t("缩小范围","smaller boundary"),{radius=((radius.toDoubleOrNull() ?: 30.0)-5).coerceAtLeast(5.0).toString()})
                            Label(os.formatDistance(radius.toDoubleOrNull()),30,c.accent,Modifier.weight(1f))
                            IconAction("plus",os.t("扩大范围","larger boundary"),{radius=((radius.toDoubleOrNull() ?: 30.0)+5).toString()})
                            Label(os.t("选项 ›","options ›"),16,c.accent,Modifier.clickable {page="advanced"}.padding(8.dp))
                        }
                        if(!notifications)MetroButton(os.t("允许锚警通知","allow watch notifications"),{if(Build.VERSION.SDK_INT>=33)notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)})
                        if(!readiness.ready)Label(positionReason(os,readiness.reason),13,c.muted)
                    } else if(active!=null) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Label(watchTitle(os,state),21,if(state.alarmSnapshot.type!=null)c.accent else c.fg,Modifier.weight(1f))
                            val distanceNow=if(readiness.ready)sourcePoint?.let {distance(it,watchCenter(active))} else null
                            Label("${os.formatDistance(distanceNow)} / ${os.formatDistance(active.alarmRadiusMeters)}",17,c.accent)
                        }
                        if(state.alarmSnapshot.type!=null) {
                            Label(alarmReason(os,state.alarmSnapshot.type),16)
                            MetroButton(if(state.alarmSnapshot.acknowledged)os.t("告警已确认","alarm acknowledged")else os.t("确认告警","acknowledge alarm"),vm::acknowledge,enabled=!state.alarmSnapshot.acknowledged)
                        } else if(!readiness.ready)Label(positionReason(os,readiness.reason),13,c.muted)
                        else Label(os.t("轨迹渐隐 · 色斑保留本次到过的范围","fading recent trail · colour keeps this watch's observed area"),12,c.muted)
                    } else Label(os.t("下锚后，轨迹与摆动范围会留在这张海图上。","Drop anchor to keep your trail and swing area on this chart."),15,c.muted)
                    if(pending!=null)Label(os.t("正在更新值守…","updating watch…"),14,c.accent)
                    feedback?.let {Label(it,14)}
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min=69.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                IconAction("locate",os.t("船位","boat"),{
                    if(fix?.fresh(tick)==true){view.fly(fix.point);view.follow=true;view.showCrosshair=false}
                    else os.notify("暂时没有可用船位，等待定位更新","No position available yet; waiting for a new fix")
                })
                if(active!=null) {
                    IconAction("ruler",os.t("范围","boundary"),{editingRadius=true})
                    IconAction("stop",os.t("起锚","lift anchor"),{confirmEnd=true})
                    IconAction("more",os.t("详情","details"),{page="estimate"})
                } else if(page=="setup") {
                    IconAction("pin",os.t("准星锚点","anchor here"),{picked=view.center;origin=AnchorCenterSource.MAP_PICK})
                    IconAction("check",os.t("开始值守","start watch"),{
                        val point=picked
                        val meters=radius.toDoubleOrNull()?.takeIf {it.isFinite()&&it>0}
                        val sourceAllowed=source in listOf(GpsDataSource.SYSTEM,GpsDataSource.NMEA) && !(source==GpsDataSource.SYSTEM && GpsSourceSafety.blocksSystemGps(state.settings.mockEnabled,state.mockGps.state))
                        if(point==null || meters==null)os.notify("先选定锚点与警戒范围","Choose an anchor position and boundary first")
                        else if(!notifications)os.notify("请先允许锚警通知","Allow anchor watch notifications first")
                        else if(!sourceAllowed)os.notify("请在数据来源应用开启真实定位","Enable a real position source in data sources")
                        else if(origin==AnchorCenterSource.CURRENT_POSITION && !readiness.ready)os.notify("等待可信船位更新，或用准星确认实际锚点","Wait for a trusted fix, or confirm the actual anchor using the crosshair")
                        else if(pending==null)command("start") {
                            vm.arm(point.lat,point.lon,AnchorWatchInput(AnchorPlacementMode.CENTER_DROP,AnchorRangeMode.BASIC,AnchorSafetyPreset.BALANCED,
                                null,0.0,state.settings.bowRollerHeightMeters,state.settings.boatLengthMeters,meters,source,origin,true,AnchorDepthSource.MANUAL,
                                originMode=if(origin==AnchorCenterSource.CURRENT_POSITION)AnchorOriginMode.CURRENT_ACCEPTED_POSITION else AnchorOriginMode.MAP_PICK,
                                anchoragePlaceId=os.anchorDraft?.placeId,anchorageSpotId=os.anchorDraft?.spotId))
                        }
                    })
                    IconAction("close",os.t("取消","cancel"),{page="watch";picked=null;vm.clearAnchorSetupDraft()})
                } else {
                    IconAction("anchor",os.t("下锚","drop anchor"),{
                        picked=sourcePoint ?: view.center;origin=if(sourcePoint!=null)AnchorCenterSource.CURRENT_POSITION else AnchorCenterSource.MAP_PICK
                        estimate=false;view.fly(requireNotNull(picked),17.0);view.showCrosshair=false;page="setup"
                    })
                    IconAction("more",os.t("历史","history"),{page="history"})
                }
            }
        } else if(page=="advanced") {
            PageBody {
                if(active!=null) {
                    Label(os.t("已有一次锚泊正在值守","a watch is already active"),27)
                    MetroButton(os.t("回到当前值守","return to watch"),{page="watch"},primary=true)
                } else {
                    Toggle(os.t("锚点待确认","anchor position unknown"),estimate,os.t("使用临时范围值守，并从真实摆动中估计。","watch a temporary boundary while learning from actual swing.")) {estimate=it;if(it){picked=sourcePoint;origin=AnchorCenterSource.CURRENT_POSITION}}
                    if(!estimate) {
                        MetroButton(os.t("使用当前可信船位","use accepted boat position"),{picked=sourcePoint;origin=AnchorCenterSource.CURRENT_POSITION;sourcePoint?.let {view.fly(it)}},enabled=readiness.ready)
                        MetroButton(os.t("在地图上选点","choose on map"),{picking=true})
                        val latitudeText=manual.substringBefore(',')
                        val longitudeText=manual.substringAfter(',',"")
                        Field(os.t("纬度","latitude")+" · ${os.coordinateFormat}",latitudeText,{manual="$it,$longitudeText"})
                        Field(os.t("经度","longitude")+" · ${os.coordinateFormat}",longitudeText,{manual="$latitudeText,$it"})
                        if(manual.isNotBlank()) {
                            val point=parseCoordinate(latitudeText,true)?.let {lat->parseCoordinate(longitudeText,false)?.let {lon->GeoPoint(lat,lon)}}
                            if(point==null)Label(os.t("请输入有效的纬度、经度","enter valid latitude and longitude"),15)
                            else MetroButton(os.t("使用输入的坐标","use entered coordinate"),{picked=point;origin=AnchorCenterSource.MANUAL_COORDINATES})
                        }
                    }
                    val point=if(estimate || origin==AnchorCenterSource.CURRENT_POSITION)sourcePoint else picked
                    point?.let {
                        Label(os.formatCoordinates(it),22,c.accent)
                        val preview=remember {MapViewState(it,16.0).apply {interactive=false}}
                        LaunchedEffect(it) {preview.fly(it)}
                        Box(Modifier.fillMaxWidth().height(180.dp)) {MarineMap(os.maps,MapScene(points=listOf(MapPoint("draft",it,"A",os.accent)),circles=radius.toDoubleOrNull()?.takeIf {r->r>0}?.let {r->listOf(MapCircle("draft",it,r,os.accent))}.orEmpty()),preview,Modifier.fillMaxSize());MapSourceButton(os,Modifier.align(Alignment.TopEnd).background(c.bg))}
                    }
                    if(os.anchorDraft!=null)Label(os.t("来自收藏：${os.anchorDraft?.name}。请确认这是本次实际锚点。","from saved place: ${os.anchorDraft?.name}. Confirm this watch's actual anchor."),16,c.muted)
                    Field(os.t(if(estimate)"临时警戒半径 · m" else "警戒半径 · m",if(estimate)"temporary boundary radius · m" else "alarm radius · m"),radius,{radius=it},number=true)
                    if(estimate) {
                        Label(os.t("中心仍是参考位置，不是已确认锚点。候选需要你确认才采用。","the centre is a reference, not a confirmed anchor. Adoption requires your confirmation."),16)
                        Field(os.t("实际放出的锚链／缆绳 · m","deployed rode length · m"),rode,{rode=it},number=true)
                        Field(os.t("当前水深 · m","current water depth · m"),depth,{depth=it},number=true)
                        Label(os.t("船艏高度 ${os.formatDepth(state.settings.bowRollerHeightMeters)} · 可在设置中修改","bow height ${os.formatDepth(state.settings.bowRollerHeightMeters)} · edit in settings"),14,c.muted)
                    }
                    Label(os.t("船位来源：","position source: ")+sourceLabel(os,source),18)
                    if(!readiness.ready)Label(positionReason(os,readiness.reason),16)
                    if(!notifications) {
                        Label(os.t("锚警需要通知权限来呈现后台告警。","notification permission is required for background alerts."),16)
                        MetroButton(os.t("允许通知","allow notifications"),{if(Build.VERSION.SDK_INT>=33)notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)})
                        MenuRow(os.t("系统通知设置","system notification settings")) {os.context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,os.context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                    }
                    val radiusValue=radius.toDoubleOrNull()?.takeIf {it.isFinite()&&it>0}
                    val rodeValue=rode.toDoubleOrNull();val depthValue=depth.toDoubleOrNull()
                    val geometry=!estimate || (rodeValue!=null&&rodeValue.isFinite()&&rodeValue>0&&depthValue!=null&&depthValue.isFinite()&&depthValue>=0&&state.settings.bowRollerHeightMeters>0&&rodeValue>depthValue+state.settings.bowRollerHeightMeters)
                    if(!geometry)Label(os.t("估计需要有效水深和锚链长度；锚链必须长于水深加船艏高度。","estimation needs valid depth and rode; rode must exceed depth plus bow height."),15)
                    val originMode=when {estimate->AnchorOriginMode.BACKDOWN_FROM_ACCEPTED_POSITION;origin==AnchorCenterSource.CURRENT_POSITION->AnchorOriginMode.CURRENT_ACCEPTED_POSITION;origin==AnchorCenterSource.MANUAL_COORDINATES->AnchorOriginMode.MANUAL_COORDINATE;else->AnchorOriginMode.MAP_PICK}
                    val ready=AnchorSetupReadinessEvaluator.evaluate(AnchorSetupReadinessInput(originMode,readiness.ready,point?.valid()==true,notifications,source in listOf(GpsDataSource.SYSTEM,GpsDataSource.NMEA)&&!(source==GpsDataSource.SYSTEM&&GpsSourceSafety.blocksSystemGps(state.settings.mockEnabled,state.mockGps.state)),geometry,radiusValue!=null))
                    if(ready.willWaitForGps)Label(os.t("确认后将等待可信定位，尚不会执行位置监控。","the session will wait for accepted position; movement monitoring is not active yet."),16)
                    feedback?.let {Label(it,16)}
                    MetroButton(if(pending!=null)os.t("正在建立值守…","starting watch…")else if(ready.willWaitForGps)os.t("确认锚点并等待定位","confirm and wait for position")else os.t("确认并开始值守","confirm and start watch"),{
                        if(point!=null&&radiusValue!=null)command("start") {
                            vm.arm(point.lat,point.lon,AnchorWatchInput(if(estimate)AnchorPlacementMode.BACKDOWN else AnchorPlacementMode.CENTER_DROP,AnchorRangeMode.BASIC,AnchorSafetyPreset.BALANCED,
                                if(estimate)depthValue else null,if(estimate)rodeValue!! else 0.0,state.settings.bowRollerHeightMeters,state.settings.boatLengthMeters,radiusValue,source,
                                if(estimate)AnchorCenterSource.UNKNOWN else origin,true,AnchorDepthSource.MANUAL,originMode=originMode,anchoragePlaceId=os.anchorDraft?.placeId,anchorageSpotId=os.anchorDraft?.spotId))
                        }
                    },primary=true,enabled=ready.canStart&&pending==null)
                    Label(os.t("离开页面不会暂停值守。暂停和起锚是明确的独立操作。","leaving this page does not pause the watch. Pause and lift anchor are explicit actions."),15,c.muted)
                }
            }
        } else if(page=="estimate" && active!=null) {
            PageBody {
                Label(if(active.centerStatus==AnchorCenterStatus.RESOLVED.name)os.t("当前锚点已确认","current anchor adopted")else os.t("临时边界正在值守","watching a temporary boundary"),26)
                Label(os.formatCoordinates(watchCenter(active)),18,c.accent)
                val preview=remember(active.id) {MapViewState(watchCenter(active),16.0)}
                Box(Modifier.fillMaxWidth().height(250.dp)) {MarineMap(os.maps,scene,preview,Modifier.fillMaxSize());MapSourceButton(os,Modifier.align(Alignment.TopEnd).background(c.bg))}
                if(active.centerStatus!=AnchorCenterStatus.RESOLVED.name) {
                    Label(os.t("橙色是正在使用的临时范围；蓝色是可能的锚位。候选不会自行改变警戒中心。","orange is the active temporary boundary; blue is the possible anchor region. Candidates never move the adopted centre automatically."),17)
                    Label(os.t("已接受 ${state.activeLearningPointCount} 个位置样本","${state.activeLearningPointCount} accepted position samples"),18)
                    Label(if(active.candidateRadialObservable)os.t("已观察到可用于估计的摆动范围","swing extent is observable")else os.t("仍需更多真实摆动；不会用低速漂移猜测锚点。","more actual swing is needed; slow drift alone does not locate the anchor."),16,c.muted)
                    if(active.candidateDecision==CandidateDecision.AVAILABLE.name) {
                        Label(os.t("候选待确认","candidate ready for review"),26,c.accent)
                        candidatePoint(active)?.let {Label(os.formatCoordinates(it),18)}
                        Label(os.t("不确定范围：${os.formatDistance(active.provisionalRadiusMeters)}","uncertainty: ${os.formatDistance(active.provisionalRadiusMeters)}"),17)
                        MetroButton(os.t("比较并采用候选","review and adopt candidate"),{candidatePoint(active)?.let {estimateChoice=AnchorEstimateChoice(active.id,active.candidateId,it,false)}},primary=true,enabled=active.candidateRadialObservable&&AnchorCentreApplyPolicy.mayApply(state.alarmSnapshot.state))
                        MetroButton(os.t("继续观察","continue observing"),{vm.continueEstimatingCenter(active)})
                        MetroButton(os.t("保留当前参考","keep current reference"),{vm.keepCurrentCenter(active)})
                    }
                }
                MetroButton(os.t("用已记录轨迹重新估计","estimate from recorded track"),{vm.recalculateCentreFromTrack(active)},enabled=!state.centreRecalculation.loading)
                val result=state.centreRecalculation.takeIf {it.sessionId==active.id}
                if(result?.loading==true)Label(os.t("正在分析真实轨迹…","analysing recorded track…"),20)
                result?.result?.let {analysis ->
                    Label(when(analysis.status) {AnchorCentreRecalculationStatus.READY->os.t("可比较的候选已准备好","candidate ready to compare");AnchorCentreRecalculationStatus.DATA_QUALITY_INSUFFICIENT->os.t("定位质量不足，不能采用估计","position quality insufficient for adoption");AnchorCentreRecalculationStatus.RADIAL_NOT_OBSERVABLE->os.t("尚不能从摆动确定锚点距离","swing does not yet establish anchor distance");else->os.t("轨迹时长或摆动证据不足","insufficient time or swing evidence")},22)
                    analysis.candidate?.let {candidate ->
                        Label(os.formatCoordinates(GeoPoint(candidate.latitude,candidate.longitude)),18)
                        Label(os.t("相对当前中心移动 ${os.formatDistance(analysis.shiftMeters)}","shift from current centre ${os.formatDistance(analysis.shiftMeters)}"),17)
                    }
                    if(analysis.status==AnchorCentreRecalculationStatus.READY)MetroButton(os.t("比较并采用这次估计","review and adopt this estimate"),{analysis.candidate?.let {estimateChoice=AnchorEstimateChoice(active.id,null,GeoPoint(it.latitude,it.longitude),true)}},enabled=AnchorCentreApplyPolicy.mayApply(state.alarmSnapshot.state),primary=true)
                    MetroButton(os.t("保留当前锚点","keep current anchor"),vm::keepCurrentRecalculatedCentre)
                }
                feedback?.let {Label(it,16)}
                MenuRow(os.t("风与水深警戒","wind and depth guards"),os.t("查看并调整本次值守的条件","review conditions for this watch")) {page="conditions"}
                Label(positionReason(os,readiness.reason),15,c.muted)
                MetroButton(if(active.paused)os.t("恢复值守","resume watch")else os.t("暂停值守","pause watch"),{if(pending==null)if(active.paused)command("resume",vm::resumeWatch)else command("pause",vm::pauseWatch)})
                MenuRow(os.t("锚泊历史","anchor history")) {page="history"}
            }
        } else if(page=="conditions" && active!=null) {
            AnchorConditions(os,active,vm) {page="estimate"}
        } else if(page=="history") {
            PageBody {
                val history=state.sessions.filter {!it.active}
                if(history.isEmpty())Label(os.t("结束一次值守后，在这里回顾。","completed watches will appear here."),27,c.muted)
                history.forEach {session ->MenuRow(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,if(os.chinese)Locale.SIMPLIFIED_CHINESE else Locale.US).format(Date(session.startedAt)),os.t("${durationLabel((session.endedAt ?: session.startedAt)-session.startedAt)} · ${session.alarmCount} 次告警","${durationLabel((session.endedAt ?: session.startedAt)-session.startedAt)} · ${session.alarmCount} alarms")) {vm.loadHistoryEvents(session.id);page="review:${session.id}"}}
            }
        } else if(page.startsWith("review:")) {
            val session=state.sessions.firstOrNull {it.id==page.substringAfter(':').toLongOrNull()}
            PageBody {
                if(session==null)Label(os.t("正在读取锚泊记录…","loading watch record…"),24)
                else {
                    LaunchedEffect(session.id) {vm.loadHistoryEvents(session.id)}
                    Label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,if(os.chinese)Locale.SIMPLIFIED_CHINESE else Locale.US).format(Date(session.startedAt)),22)
                    Label(os.t("值守 ${durationLabel((session.endedAt ?: System.currentTimeMillis())-session.startedAt)}","watched for ${durationLabel((session.endedAt ?: System.currentTimeMillis())-session.startedAt)}"),30,c.accent)
                    val preview=remember(session.id) {MapViewState(watchCenter(session),16.0).apply {scaleTopDp=80f}}
                    val reviewScene by produceState(MapScene(points=listOf(MapPoint("anchor",watchCenter(session),"A",os.accent)),circles=listOf(MapCircle("boundary",watchCenter(session),session.alarmRadiusMeters,os.accent))),session.id) {
                        value=withContext(Dispatchers.IO) {loadAnchorReview(os,session)}
                    }
                    LaunchedEffect(session.id,reviewScene.areas.size) {
                        preview.fit(reviewScene.areas.flatMap {it.boundary}.filterIndexed {index,_->index%4==0}+
                            listOf(destination(watchCenter(session),session.alarmRadiusMeters*1.5,0.0),destination(watchCenter(session),session.alarmRadiusMeters*1.5,180.0)))
                    }
                    Box(Modifier.fillMaxWidth().height(320.dp)) {MarineMap(os.maps,reviewScene,preview,Modifier.fillMaxSize());MapSourceButton(os,Modifier.align(Alignment.TopEnd).background(c.bg))}
                    Label(os.t("本次到过的范围 · 颜色越浓，停留越久","observed area · darker colour means more time spent"),14,c.muted)
                    Label(os.formatCoordinates(watchCenter(session)),18)
                    Label(os.t("最大偏移 ${os.formatDistance(session.maxDistanceMeters)} · ${session.alarmCount} 次位置告警","maximum excursion ${os.formatDistance(session.maxDistanceMeters)} · ${session.alarmCount} position alarms"),18)
                    MetroButton(if(saving)os.t("正在保存…","saving…")else os.t("保存为我的锚地","save to my places"),{saveSession=session},primary=true,enabled=!saving)
                    if(savedWatchId==session.id && savedAnchorageId!=null) {
                        Label(os.t("已保存到我的航行","saved to my sailing"),17,c.accent)
                        MenuRow(os.t("查看收藏","view saved place")) {os.open("anchorage:$savedAnchorageId")}
                    }
                    feedback?.let {Label(it,16)}
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {MetroButton("GPX",{vm.exportGpx(session)},Modifier.weight(1f));MetroButton("CSV",{vm.exportCsv(session)},Modifier.weight(1f))}
                    val events=state.eventsBySession[session.id].orEmpty()
                    if(events.isNotEmpty())Label(os.t("值守事件","watch events"),25)
                    events.forEach {event ->Label(DateFormat.getTimeInstance(DateFormat.SHORT,if(os.chinese)Locale.SIMPLIFIED_CHINESE else Locale.US).format(Date(event.timestamp))+" · "+anchorEventLabel(os,event.type),17)}
                }
            }
        } else {LaunchedEffect(page,active?.id) {page="watch"}}
    }
    if(picking)MapPicker(os,picked,MapScene(vessel=scene.vessel),{point ->picked=point;origin=AnchorCenterSource.MAP_PICK;picking=false},{picking=false})
    if(confirmEnd && active!=null)ConfirmDialog(os,os.t("起锚并结束这次值守？","Lift anchor and end this watch?")+if(state.activeTrip!=null)os.t("航行记录会继续。","Voyage recording continues.")else "",{confirmEnd=false}) {endingId=active.id;confirmEnd=false;command("end",vm::liftAnchor)}
    if(editingRadius && active!=null)AnchorRadiusDialog(os,active.alarmRadiusMeters,{editingRadius=false}) {meters ->vm.updateAnchorSettings(watchInput(active,meters));editingRadius=false}
    estimateChoice?.let {choice ->ConfirmDialog(os,os.t("采用 ${os.formatCoordinates(choice.point)}？这会改变警戒中心。","Adopt ${os.formatCoordinates(choice.point)}? This changes the alarm centre."),{estimateChoice=null}) {
        val result=state.centreRecalculation.result
        val unchanged=active!=null && active.id==choice.sessionId && if(choice.recalculated) state.centreRecalculation.sessionId==choice.sessionId && result?.status==AnchorCentreRecalculationStatus.READY && result.candidate?.let {GeoPoint(it.latitude,it.longitude)==choice.point}==true else active.candidateId==choice.candidateId && candidatePoint(active)==choice.point && active.candidateDecision==CandidateDecision.AVAILABLE.name
        if(unchanged && AnchorCentreApplyPolicy.mayApply(state.alarmSnapshot.state)) {
            if(choice.recalculated)vm.applyRecalculatedCentre() else active?.let(vm::acceptEstimatedCenter)
        } else feedback=os.t("候选或值守状态已经改变，请重新查看。","candidate or watch state changed; review it again.")
        estimateChoice=null
    }}
    saveSession?.let {session ->TextDialog(os,os.t("锚地名称","anchorage name"),os.t("我的锚地","my anchorage"),{saveSession=null}) {name ->
        saving=true;saveSession=null
        scope.launch {runCatching {os.sailing.saveAnchorage(session,name)}.onSuccess {id->savedAnchorageId=id;savedWatchId=session.id;feedback=null}.onFailure {feedback=it.message ?: os.t("保存失败，原记录仍保留","save failed; watch record is retained")};saving=false}
    }}
}

private data class AnchorEstimateChoice(val sessionId:Long,val candidateId:Long?,val point:GeoPoint,val recalculated:Boolean)
private fun watchCenter(session:AnchorSessionEntity)=if(session.centerStatus==AnchorCenterStatus.RESOLVED.name)GeoPoint(session.anchorLatitude,session.anchorLongitude)else GeoPoint(session.learningReferenceLatitude ?: session.anchorLatitude,session.learningReferenceLongitude ?: session.anchorLongitude)
private fun candidatePoint(session:AnchorSessionEntity)=session.takeIf {it.centerSampleCount>0}?.provisionalAnchorLatitude?.let {lat->session.provisionalAnchorLongitude?.let {lon->GeoPoint(lat,lon)}}?.takeIf {it.valid()}
private fun anchorScene(os:OsStore,state:MainUiState,vessel:MapVessel?,draft:GeoPoint?,radius:Double?,now:Long,areas:List<MapArea>):MapScene {
    val points=mutableListOf<MapPoint>();val circles=mutableListOf<MapCircle>();val lines=mutableListOf<MapLine>()
    state.active?.let {session ->
        val adopted=session.centerStatus==AnchorCenterStatus.RESOLVED.name;val color=if(adopted)os.accent else 0xFFE78527
        points+=MapPoint("anchor",watchCenter(session),if(adopted)"A" else "R",color,13f)
        circles+=MapCircle("watch",watchCenter(session),session.alarmRadiusMeters,color)
        candidatePoint(session)?.let {candidate ->if(!adopted || session.estimationEpoch>0) {points+=MapPoint("candidate",candidate,"?",0xFF007ADC,11f);circles+=MapCircle("candidate",candidate,session.provisionalRadiusMeters ?: session.expectedSwingRadiusMeters,0xFF007ADC,true)}}
        // 最近 30 分钟按时间分桶淡出；无效定位与断流永远断开，不伪造穿越轨迹。
        val recent=state.points.filter {it.timestamp>=now-30*60_000}
        var previous:com.yokuli.anchorwatch.data.database.TrackPointEntity?=null
        val buckets=mutableMapOf<Int,MutableList<MutableList<GeoPoint>>>()
        recent.forEach {sample ->
            val p=GeoPoint(sample.latitude,sample.longitude)
            if(sample.wasQuarantined||sample.fixTrust in listOf("REJECTED","QUARANTINED")||!p.valid())previous=null
            else {
                val prior=previous
                if(prior!=null && sample.positionSource==prior.positionSource && sample.timestamp-prior.timestamp<=state.settings.gpsLossSeconds*1000L) {
                    val bucket=((now-sample.timestamp)/180_000).toInt().coerceIn(0,9)
                    val parts=buckets.getOrPut(bucket){mutableListOf()}
                    val start=GeoPoint(prior.latitude,prior.longitude)
                    if(parts.lastOrNull()?.lastOrNull()!=start)parts.add(mutableListOf(start))
                    parts.last().add(p)
                }
                previous=sample
            }
        }
        buckets.forEach {(age,segments) ->segments.forEachIndexed {index,segment ->
            val alpha=(245-age*21).toLong()
            lines+=MapLine("swing:$age:$index",segment,(alpha shl 24) or 0x00007789,2.4f)
        }}
    }
    if(state.active==null && draft!=null) {points+=MapPoint("draft",draft,"A",os.accent,13f);radius?.takeIf {it.isFinite()&&it>0}?.let {circles+=MapCircle("draft",draft,it,os.accent)}}
    state.centreRecalculation.result?.candidate?.let {candidate ->if(state.centreRecalculation.sessionId==state.active?.id)points+=MapPoint("recalculated",GeoPoint(candidate.latitude,candidate.longitude),"?",0xFF007ADC,12f)}
    return MapScene(vessel,points,lines,circles,areas=areas)
}
private fun sourceLabel(os:OsStore,source:GpsDataSource)=when(source) {GpsDataSource.SYSTEM->os.t("手机 GPS","phone GPS");GpsDataSource.NMEA->"NMEA";GpsDataSource.DEMO->os.t("演示","demo");else->os.t("关闭","off")}
private fun positionReason(os:OsStore,reason:String)=when {
    reason=="READY"->os.t("可信船位可用","accepted position available")
    reason.contains("DISABLED")->os.t("船位来源已关闭","position source is off")
    reason.contains("STALE")->os.t("船位已过期，等待新位置","position stale; waiting for a new fix")
    reason.contains("ACCURACY")->os.t("定位精度不足，等待更可靠的位置","position accuracy insufficient")
    reason.contains("MOCK")||reason.contains("PROXY")->os.t("当前手机定位不能用于锚警","current phone position cannot be used for watch")
    reason.contains("QUARANTIN")||reason.contains("REJECT")->os.t("位置异常，正在重新确认可信船位","position rejected; rebuilding trusted evidence")
    else->os.t("等待所选来源的可信船位","waiting for accepted position from the selected source")
}
private fun watchTitle(os:OsStore,state:MainUiState):String {
    val active=state.active ?: return os.t("尚未开始","not started")
    return when {active.paused->os.t("值守已暂停","watch paused");active.monitoringPhase=="WAITING_FOR_GPS"->os.t("等待可信定位","waiting for accepted position");state.alarmSnapshot.type!=null->os.t("需要处理告警","alarm needs attention");active.centerStatus!="RESOLVED"->os.t("临时范围值守 · 学习中","temporary watch · learning");else->os.t("正在值守","watch active")}
}
private fun alarmReason(os:OsStore,type:AlarmType?)=when(type) {AlarmType.ANCHOR_RADIUS_EXCEEDED->os.t("船位超出警戒范围","vessel outside alarm boundary");AlarmType.GPS_DATA_LOST->os.t("定位数据中断","position data lost");AlarmType.NMEA_CONNECTION_LOST->os.t("NMEA 连接中断","NMEA connection lost");AlarmType.GPS_QUALITY_BAD->os.t("定位质量不足","position quality insufficient");AlarmType.ALARM_TEST->os.t("告警测试","alarm test");else->os.t("请检查当前值守状态","check watch state")}
private fun anchorEventLabel(os:OsStore,type:String)=when {
    type=="SESSION_STARTED"->os.t("开始值守","watch started")
    type=="SESSION_CREATED_WAITING_FOR_GPS"->os.t("锚点已确认，等待可信定位","anchor confirmed; waiting for position")
    type=="SESSION_STARTED_CENTER_LEARNING"->os.t("开始临时范围值守和锚点学习","temporary watch and anchor learning started")
    type=="SESSION_PAUSED"->os.t("暂停值守","watch paused")
    type=="SESSION_RESUMED"->os.t("恢复值守","watch resumed")
    type=="ANCHOR_LIFTED"->os.t("起锚结束","watch ended")
    type.contains("SOURCE_CHANGED")->os.t("观测来源变更","observation source changed")
    type=="ANCHOR_RADIUS_EXCEEDED"->os.t("超出警戒范围","outside alarm boundary")
    type=="ALARM_RANGE_CHANGED"->os.t("警戒范围已更改","alarm boundary changed")
    type=="ALARM_SNOOZED"->os.t("已确认告警，暂时静音","alarm acknowledged and silenced")
    type=="ANCHOR_CENTER_ACCEPTED_BY_USER"||type=="ANCHOR_CENTRE_RECALCULATED_APPLIED"->os.t("用户采用了估计锚点","estimated anchor adopted by user")
    type.contains("CURRENT_KEPT")->os.t("保留当前锚点参考","current anchor reference retained")
    type.contains("ESTIMATION_CONTINUED")->os.t("继续收集摆动证据","collecting further swing evidence")
    type.contains("ANALYSIS_RESET")->os.t("重新开始锚点分析","anchor analysis restarted")
    type=="GPS_MONITORING_ACTIVATED"->os.t("可信定位恢复，监控已开始","accepted position restored; monitoring active")
    type.contains("GPS")||type.contains("POSITION")->os.t("定位质量事件","position quality event")
    type.contains("WIND")->os.t("风况警戒事件","wind guard event")
    type.contains("DEPTH")->os.t("水深警戒事件","depth guard event")
    type.contains("ALARM")->os.t("告警状态变化","alarm state changed")
    type.contains("CENTER")||type.contains("CENTRE")->os.t("锚点估计已更新","anchor estimate updated")
    else->os.t("值守状态记录","watch state event")
}
private fun watchInput(session:AnchorSessionEntity,radius:Double)=AnchorWatchInput(AnchorPlacementMode.valueOf(session.placementMode),AnchorRangeMode.BASIC,AnchorSafetyPreset.BALANCED,session.waterDepthMeters,session.rodeLengthMeters,session.bowRollerHeightMeters,session.boatLengthMeters,radius,runCatching {GpsDataSource.valueOf(session.positionSource)}.getOrDefault(GpsDataSource.NONE),runCatching {AnchorCenterSource.valueOf(session.centerSource)}.getOrDefault(AnchorCenterSource.UNKNOWN))

@Composable
private fun AnchorConditions(os:OsStore,session:AnchorSessionEntity,vm:MainViewModel,onSaved:()->Unit) {
    var depthOn by remember(session.id){mutableStateOf(session.depthGuardEnabled)}
    var shallow by remember(session.id){mutableStateOf(session.shallowDepthAlarmMeters?.toString().orEmpty())}
    var deep by remember(session.id){mutableStateOf(session.deepDepthAlarmMeters?.toString().orEmpty())}
    var windOn by remember(session.id){mutableStateOf(session.windGuardEnabled)}
    val units=os.measurementUnits
    val speedUnit=if(units==MeasurementUnitSystem.NAUTICAL)"kn" else "km/h"
    val speedFactor=if(units==MeasurementUnitSystem.NAUTICAL)1.0 else MarineDisplayUnits.NAUTICAL_MILES_TO_KILOMETRES
    fun displayedWind(knots:Double?)=knots?.let {String.format(Locale.US,"%.2f",MarineDisplayUnits.speedFromKnots(it,units)).trimEnd('0').trimEnd('.')}.orEmpty()
    val initialWarning=displayedWind(session.windWarningKnots)
    val initialAlarm=displayedWind(session.windAlarmKnots)
    var warning by remember(session.id,units){mutableStateOf(initialWarning)}
    var alarm by remember(session.id,units){mutableStateOf(initialAlarm)}
    // 用户编辑使用全局单位，领域层继续保存 kn；未编辑的原值不因显示舍入而改变。
    fun storedWind(text:String,initial:String,original:Double?):Double? = if(text==initial)original else text.toDoubleOrNull()?.div(speedFactor)
    var shiftOn by remember(session.id){mutableStateOf(session.windShiftEnabled)}
    var shift by remember(session.id){mutableStateOf(session.windShiftThresholdDegrees?.toString().orEmpty())}
    var invalid by remember {mutableStateOf(false)}
    var applying by remember {mutableStateOf<ConditionGuardConfig?>(null)}
    var timedOut by remember {mutableStateOf(false)}
    val stored=ConditionGuardConfig(session.depthGuardEnabled,session.shallowDepthAlarmMeters,session.deepDepthAlarmMeters,session.windGuardEnabled,session.windWarningKnots,session.windAlarmKnots,session.windShiftEnabled,session.windShiftThresholdDegrees,session.windAllowApparentFallback)
    LaunchedEffect(applying,stored) {
        val expected=applying ?: return@LaunchedEffect
        if(stored==expected) {applying=null;onSaved()} else {delay(9000);applying=null;timedOut=true}
    }
    PageBody {
        Label(os.t("本次值守的条件","conditions for this watch"),28)
        Label(os.t("缺少真实风或水深数据时会显示等待，不会把零当作测量。","missing wind or depth remains unavailable, never a zero measurement."),16,LocalMetro.current.muted)
        Toggle(os.t("水深警戒","depth guard"),depthOn) {depthOn=it}
        if(depthOn) {Field(os.t("过浅阈值 · m","shallow limit · m"),shallow,{shallow=it},true);Field(os.t("过深阈值 · m（可留空）","deep limit · m (optional)"),deep,{deep=it},true)}
        Toggle(os.t("风速警戒","wind speed guard"),windOn) {windOn=it}
        if(windOn) {Field(os.t("风速预警","wind warning")+" · $speedUnit",warning,{warning=it},true);Field(os.t("风速告警","wind alarm")+" · $speedUnit",alarm,{alarm=it},true)}
        Toggle(os.t("风向变化警戒","wind shift guard"),shiftOn) {shiftOn=it}
        if(shiftOn)Field(os.t("变化角度 · °","shift threshold · °"),shift,{shift=it},true)
        if(invalid)Label(os.t("请填写有效阈值：深水至少比浅水多 1 m；风速告警至少比预警多 ${os.formatSpeed(3.0)}，不超过 ${os.formatSpeed(200.0)}；风向变化 15–180°。","enter valid limits: deep ≥ shallow + 1 m; wind alarm ≥ warning + ${os.formatSpeed(3.0)}, up to ${os.formatSpeed(200.0)}; shift 15–180°."),16)
        if(timedOut)Label(os.t("尚未收到应用确认，请查看当前值守状态。","application not confirmed; check the current watch state."),16)
        MetroButton(if(applying!=null)os.t("正在应用…","applying…")else os.t("保存本次警戒条件","save watch conditions"),{
            val config=ConditionGuardConfig(depthOn,shallow.toDoubleOrNull(),deep.toDoubleOrNull(),windOn,storedWind(warning,initialWarning,session.windWarningKnots),storedWind(alarm,initialAlarm,session.windAlarmKnots),shiftOn,shift.toDoubleOrNull(),session.windAllowApparentFallback)
            val checked=config.validated()
            if(checked.depthGuardEnabled!=depthOn||checked.windGuardEnabled!=windOn||checked.windShiftEnabled!=shiftOn || (depthOn&&deep.isNotBlank()&&checked.deepDepthAlarmMeters==null))invalid=true
            else {timedOut=false;applying=checked;vm.updateConditionGuards(checked)}
        },primary=true,enabled=applying==null)
    }
}

@Composable
private fun AnchorRadiusDialog(os:OsStore,initial:Double,onDismiss:()->Unit,onApply:(Double)->Unit) {
    var value by remember {mutableStateOf(initial.toString())}
    val meters=value.toDoubleOrNull()?.takeIf {it.isFinite()&&it>0}
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().background(LocalMetro.current.bg).border(1.dp,LocalMetro.current.muted).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Label(os.t("警戒范围","watch boundary"),32)
            Field(os.t("距锚点的半径 · m","radius from anchor · m"),value,{value=it},number=true)
            if(meters==null)Label(os.t("请输入大于零的米数","enter a positive distance in metres"),16)
            MetroButton(os.t("应用到当前值守","apply to current watch"),{meters?.let(onApply)},primary=true,enabled=meters!=null)
            MetroButton(os.t("取消","cancel"),onDismiss)
        }
    }
}


/** 结束后也从同一份真实锚泊记录还原区域；不把最后一段轨迹冒充整个值守历史。 */
private suspend fun loadAnchorReview(os:OsStore,session:AnchorSessionEntity):MapScene {
    val aggregate=AnchorSwingCoverage(watchCenter(session),session.alarmRadiusMeters)
    var recent=emptyList<com.yokuli.anchorwatch.data.database.TrackPointEntity>()
    while(true) {
        val batch=os.sailing.database.anchorDao().pointsPage(session.id,aggregate.lastTimestamp,aggregate.lastId,1000)
        aggregate.add(batch);recent=(recent+batch).takeLast(1200)
        if(batch.size<1000)break
    }
    val lines=mutableListOf<MapLine>();var segment=mutableListOf<GeoPoint>()
    var previous:com.yokuli.anchorwatch.data.database.TrackPointEntity?=null
    fun flush(){if(segment.size>1)lines+=MapLine("history:${lines.size}",segment.toList(),0xCC007789,2f);segment=mutableListOf()}
    recent.forEach {sample->
        val point=GeoPoint(sample.latitude,sample.longitude)
        if(!point.valid()||sample.wasQuarantined||sample.fixTrust in listOf("REJECTED","QUARANTINED")){flush();previous=null}
        else {
            if(previous?.let {sample.timestamp-it.timestamp>60_000 || sample.positionSource!=it.positionSource}==true)flush()
            segment+=point;previous=sample
        }
    };flush()
    return MapScene(points=listOf(MapPoint("anchor",watchCenter(session),"A",os.accent)),lines=lines,
        circles=listOf(MapCircle("boundary",watchCenter(session),session.alarmRadiusMeters,os.accent)),areas=aggregate.areas())
}
