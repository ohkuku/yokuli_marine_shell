package com.yokuli.marine.shell.rebuild.ui

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import kotlin.math.*

@Composable fun ChartScreen(os:OsStore,recording:Boolean=false,recordingPaused:Boolean=false,onRecording:()->Unit={os.open("trip")},initialAisMmsi:Int?=null,interactionBlocked:Boolean=false) {
    val positionSource=os.positionSource
    val currentFix by remember(os.hub,positionSource){os.hub.state.map {it.fix(positionSource)}.distinctUntilChanged()}
        .collectAsState(os.hub.state.value.fix(positionSource))
    val fix=currentFix
    val tick=rememberMarineClock()
    val traffic=rememberAisTraffic(os)
    val fresh=fix?.fresh(tick)==true
    var host by remember { mutableStateOf<ChartHost?>(null) }
    var layers by rememberSaveable { mutableStateOf(false) }
    var tools by rememberSaveable { mutableStateOf(false) }
    var manageNavigation by rememberSaveable { mutableStateOf(false) }
    var externalNavigation by rememberSaveable { mutableStateOf(false) }
    // 检查面板只显示当前草稿；不存在另一份规划输入或独立规划入口。
    var planning by rememberSaveable { mutableStateOf(false) }
    fun openPlanning(){planning=true}
    LaunchedEffect(os.editingRoute){if(!os.editingRoute)planning=false}
    var spatialVisible by rememberSaveable { mutableStateOf(false) }
    var liftOffer by rememberSaveable { mutableStateOf(false) }
    var manualMapEpoch by rememberSaveable { mutableLongStateOf(0L) }
    var spatialInteraction by remember { mutableStateOf(false) }
    var mapTouched by remember { mutableStateOf(false) }
    val savedPreferences by os.shell.persistence.state.collectAsState()
    val liftEnabled=savedPreferences?.appPreferenceValues?.get("chart.lift_to_direction")=="b:1"
    val display=os.marine?.services?.display
    val mountedFlow=remember(os.marine){os.marine?.services?.state?.map{it.vesselMountCalibration.mountConfirmed&&it.phoneVesselMountState==com.yokuli.anchorwatch.location.vessel.PhoneVesselMountState.VESSEL_MOUNTED}?.distinctUntilChanged()}
    val mounted=mountedFlow?.collectAsState(false)?.value?:false
    fun openSpatial(){spatialVisible=true;liftOffer=false}
    fun returnToMap(){spatialVisible=false;spatialInteraction=false;liftOffer=false;manualMapEpoch++}
    var startingPlaceId by rememberSaveable {mutableStateOf<String?>(null)}
    var editingPlaceId by rememberSaveable {mutableStateOf<String?>(null)}
    var morePlaceId by rememberSaveable {mutableStateOf<String?>(null)}
    val c=LocalMetro.current
    val density=LocalDensity.current
    val chartView=os.maps.view("chart",os.center,os.zoom)
    val savedOrientation=savedPreferences?.appPreferenceValues?.get("chart.orientation")?.removePrefix("c:")
    LaunchedEffect(savedOrientation){chartView.orientationMode=runCatching{MapOrientationMode.valueOf(savedOrientation.orEmpty())}.getOrDefault(MapOrientationMode.NORTH_UP)}
    var aisEntrySelected by rememberSaveable(initialAisMmsi){mutableStateOf(false)}
    var aisFocusApplied by rememberSaveable(initialAisMmsi){mutableStateOf(false)}
    val requestedTarget=initialAisMmsi?.let(traffic::target)
    val chartInputEnabled=com.yokuli.shell.compose.LocalInternalAppInputEnabled.current
    LaunchedEffect(initialAisMmsi,traffic.runtime.ready,chartInputEnabled) {
        if(initialAisMmsi!=null&&traffic.runtime.ready&&chartInputEnabled&&!aisEntrySelected) {
            aisEntrySelected=true
            chartView.selectedAisMmsi=initialAisMmsi.toString()
            chartView.selectedPlaceId=null
            chartView.previewTrack=emptyList();chartView.previewTitle=null
            os.showCrosshair=false;os.ruler=emptyList()
        }
    }
    LaunchedEffect(initialAisMmsi,aisEntrySelected,requestedTarget?.position,chartView.selectedAisMmsi,chartInputEnabled,os.editingRoute) {
        if(initialAisMmsi!=null&&aisEntrySelected&&chartInputEnabled&&!aisFocusApplied) {
            if(chartView.selectedAisMmsi!=initialAisMmsi.toString())aisFocusApplied=true
            else if(!os.editingRoute) requestedTarget?.position?.let {
                aisFocusApplied=true
                os.fly(it.geo(),os.zoom.coerceAtLeast(12.0))
            }
        }
    }
    ReportVisibleAppRoute(os,chartView.selectedAisMmsi?.let {"chart:ais:$it"} ?: "chart")
    AisRetainSelection(os,chartView.selectedAisMmsi?.toIntOrNull())
    LaunchedEffect(spatialVisible){if(spatialVisible){host=null;mapTouched=false}}
    val previewPlace=os.allPlaces.firstOrNull {it.id==chartView.selectedPlaceId}
    val selected=os.maps.selectedLayer()?.files.orEmpty()
    fun closeTool():Boolean = when {
        startingPlaceId!=null->{startingPlaceId=null;true}
        editingPlaceId!=null->{editingPlaceId=null;true}
        morePlaceId!=null->{morePlaceId=null;true}
        layers->{layers=false;true}
        tools->{tools=false;true}
        manageNavigation->{manageNavigation=false;true}
        externalNavigation->{externalNavigation=false;true}
        planning->{planning=false;true}
        spatialVisible->{returnToMap();true}
        chartView.selectedAisMmsi!=null->{if(initialAisMmsi?.toString()==chartView.selectedAisMmsi)os.shell.popRoute()else chartView.selectedAisMmsi=null;true}
        chartView.selectedPlaceId!=null->{chartView.selectedPlaceId=null;true}
        os.ruler.isNotEmpty()->{os.ruler=emptyList();true}
        os.editingRoute->{leaveRouteDraft(os);true}
        os.showCrosshair->{os.showCrosshair=false;true}
        else->false
    }
    val toolOpen=layers||tools||manageNavigation||externalNavigation||planning||startingPlaceId!=null||editingPlaceId!=null||morePlaceId!=null||chartView.selectedPlaceId!=null||chartView.selectedAisMmsi!=null||os.ruler.isNotEmpty()||os.editingRoute||os.showCrosshair
    if(display!=null)NavigationLiftObserver(display,active=chartInputEnabled&&os.navigationState.guidance!=null,
        inhibited=toolOpen||interactionBlocked||mapTouched||spatialInteraction,
        spatialVisible=spatialVisible,autoEnabled=liftEnabled,mounted=mounted,manuallyReturnedToMapEpoch=manualMapEpoch,
        onOfferSpatial={liftOffer=true},onRequestSpatial={openSpatial()},onRequestMap={spatialVisible=false;spatialInteraction=false})
    AppBackHandler(toolOpen||spatialVisible) {closeTool()}
    BindInternalAppInputHandler {input->input==ShellInput.BACK && closeTool()}
    Column(Modifier.fillMaxSize()) {
        if(spatialVisible)Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(start=LocalShellHorizontalInsets.current.pageStart,end=LocalShellHorizontalInsets.current.pageEnd),verticalAlignment=Alignment.CenterVertically){
            Label(os.t("立体方向","Direction view"),22,modifier=Modifier.weight(1f),maxLines=1)
            MetroButton(os.t("地图","Map"),{returnToMap()})
        }else MapPageHeader(os,if(os.editingRoute)os.t("规划航线","Plan a route")else os.title(AppId.CHART),{layers=true},hasLocalBack=toolOpen)
        if(spatialVisible&&display!=null) {
            ChartNavigationSpatial(os,fix,tick,chartInputEnabled&&!interactionBlocked&&!manageNavigation&&!externalNavigation,Modifier.weight(1f).fillMaxWidth(),
                onOpenMap={returnToMap()},onOpenTarget={id->
                    val point=os.navigationState.session?.route?.waypoints?.firstOrNull{it.id==id}?.point
                    if(point!=null){returnToMap();os.fly(GeoPoint(point.lat,point.lon),os.zoom.coerceAtLeast(13.0))}
                    else if(os.navigationState.session?.source==com.yokuli.runtime.contract.navigation.NavigationSource.EXTERNAL_NMEA)externalNavigation=true else manageNavigation=true
                },onAutomaticSwitchInhibited={spatialInteraction=it})
        } else {
        Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit){
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                mapTouched=true
                try { do { val event=awaitPointerEvent(PointerEventPass.Initial) } while(event.changes.any{it.pressed}) }
                finally {mapTouched=false}
            }
        }) {
            NativeChart(os,fix,Modifier.fillMaxSize()) { host=it }
            MapPositionReadout(os,fix,tick,Modifier.align(Alignment.TopStart).padding(10.dp))
            if(chartView.previewTrack.isEmpty())AisMapStatus(os,traffic,!traffic.preferences.chartLayer,Modifier.align(Alignment.TopStart).padding(start=10.dp,top=62.dp))
            if(chartView.orientationMode!=MapOrientationMode.NORTH_UP)Label(when(chartView.orientationIssue){
                "heading"->os.t("北向朝上 · 等待船首向","North up · waiting for heading")
                "course"->os.t("北向朝上 · 等待稳定航迹向","North up · waiting for a steady course")
                else->if(chartView.effectiveOrientationMode==MapOrientationMode.HEADING_UP)os.t("船艏朝上","Heading up")else os.t("航迹向朝上","Course up")
            },12,c.muted,Modifier.align(Alignment.TopEnd).padding(top=12.dp,end=10.dp).widthIn(max=150.dp).background(c.bg.copy(alpha=.92f)).padding(horizontal=8.dp,vertical=5.dp),maxLines=2)
            MapZoomControls(os,chartView,Modifier.align(Alignment.TopEnd).padding(top=66.dp,end=10.dp)) {zoom->os.fly(os.center,zoom)}
            if(os.maps.source is MapSource.CustomLayer && selected.isEmpty()) {
                val noFolder=(os.maps.source as? MapSource.CustomLayer)?.layerId.isNullOrBlank()
                Column(Modifier.align(Alignment.Center).padding(30.dp).widthIn(max=350.dp).background(c.bg).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    AppSection(if(noFolder)os.t("自定义背景为空","Custom background is empty")else os.t("自定义海图暂无内容","No custom charts available"))
                    Label(if(noFolder)os.t("尚未选择海图文件夹。选择后显示其中的海图，也可以保持为空。","No chart folder is selected. Choose one to display its charts, or leave the background empty.")else os.t("选定文件夹当前没有可读取的海图。请检查文件夹授权和参与显示的文件。","The selected folder has no readable charts. Check its access and included files."),14,c.muted)
                    CustomChartFolderSetting(os)
                    MenuRow(os.t("在图册管理文件夹","Manage folders in Library"),icon="settings") {os.openLinked((os.maps.source as? MapSource.CustomLayer)?.layerId?.takeIf { id -> os.library.folders.any {it.id==id} }?.let { "library:$it" } ?: "library")}
                }
            }
            // 控件覆盖稳定地图视口，显示编辑器不能改变原生地图尺寸。
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().onSizeChanged {chartView.bottomOverlayDp=with(density){it.height.toDp().value}}) {
                chartView.libraryPreview?.let {preview->
                    Row(Modifier.fillMaxWidth().background(c.panel).padding(start=16.dp,end=8.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label(featureTitle(os,preview.feature),16,maxLines=1)
                            Label(chartView.libraryPreviewNote ?: preview.feature.depth?.let {depthEvidenceText(os,it)} ?: os.t("图册对象预览","Library object preview"),12,c.muted,maxLines=2)
                        }
                        IconAction("close",os.t("关闭对象预览","Close object preview"),{chartView.libraryPreview=null})
                    }
                }
                if(chartView.selectedAisMmsi!=null && !os.editingRoute && os.ruler.isEmpty()) {
                    AisCompactDetail(os,traffic,chartView.selectedAisMmsi!!){chartView.selectedAisMmsi=null}
                } else if(os.ruler.size==2) {
                    Row(Modifier.fillMaxWidth().background(c.panel).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label("${os.formatDistance(distance(os.ruler[0],os.ruler[1]))}   ${os.formatBearing(bearing(os.ruler[0],os.ruler[1]))}T",25)
                            Label(os.t("拖动 A / B 图钉测距","drag pins A / B to measure"),12,c.muted)
                        }
                        IconAction("close",os.t("结束测距","End measurement"),{os.ruler=emptyList()})
                    }
                } else if(os.editingRoute) {
                    RouteDraftSummary(os){openPlanning()}
                } else if(os.showCrosshair&&previewPlace==null) {
                    MapCrosshairReadout(os,os.center) {os.showCrosshair=false}
                }
                val visiblePlace=previewPlace?.takeIf {chartView.selectedAisMmsi==null&&!os.editingRoute&&os.ruler.isEmpty()}
                AnimatedContent(visiblePlace,contentKey={it?.id ?: "no-place"},transitionSpec={
                    ((slideInVertically(tween(220)){it/4}+fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(160)){it/4}+fadeOut(tween(160)))).using(SizeTransform(clip=false))
                },label="chart-place-preview") {place->
                    if(place!=null)CompositionLocalProvider(com.yokuli.shell.compose.LocalInternalAppInputEnabled provides (chartInputEnabled&&visiblePlace?.id==place.id)) {
                        Column(Modifier.fillMaxWidth().background(c.panel).padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Label(place.name,20,modifier=Modifier.weight(1f),maxLines=1)
                                IconAction("close",os.t("关闭预览","Close preview"),{chartView.selectedPlaceId=null})
                            }
                            Label(listOfNotNull(os.formatCoordinates(place.point),fix?.takeIf {it.fresh(tick)}?.let {os.formatDistance(distance(it.point,place.point))}).joinToString(" · "),12,c.muted)
                            if(place.note.isNotBlank())Label(place.note,15,c.muted,maxLines=2)
                            PlaceSaveFeedback(os,place)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                MetroButton(os.t("前往这里","Go here"),{startingPlaceId=place.id},Modifier.weight(1f),primary=true)
                                IconAction("edit",os.t("编辑收藏","Edit saved place"),{if(place.id.startsWith("spot:"))os.openLinked("place:${place.id}")else editingPlaceId=place.id})
                                IconAction("more",os.t("地点操作","Place actions"),{morePlaceId=place.id})
                            }
                        }
                    }
                }
                if(!os.editingRoute) {
                    ChartNavigationCard(os,fix,tick)
                    if(os.navigationState.guidance!=null&&display!=null&&!toolOpen)Row(Modifier.fillMaxWidth().background(c.bg.copy(alpha=.96f)).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){
                        if(liftOffer)Label(os.t("抬起了手机，查看目标方向？","Raised your phone? See the target direction."),12,c.muted,Modifier.weight(1f))else Spacer(Modifier.weight(1f))
                        MetroButton(os.t("立体方向","3D direction"),{openSpatial()})
                        if(liftOffer)IconAction("close",os.t("暂不查看","Not now"),{liftOffer=false;manualMapEpoch++})
                    }
                }
            }
        }
        if(os.editingRoute)RouteDraftControls(os){openPlanning()}else AppCommandBar(os,listOf(
            AppCommand("position","locate",os.t("回到船位","Go to boat"),{
                if(fix!=null){os.follow=fresh;os.showCrosshair=false;host?.camera?.move(fix.point,os.zoom)}
                else os.notify("暂无船位，请在数据中心查看来源","No position yet. Check the source in Data Center.")
            },active=os.follow),
            AppCommand("mark","pin",when {os.showCrosshair->os.t("标记此处","Mark here");fresh->os.t("记录当前船位","Mark boat position");else->os.t("选择标记位置","Choose a position")},{
                val capturedAt=System.currentTimeMillis()
                val current=os.hub.state.value.fix(os.positionSource)?.takeIf {it.fresh(SystemClock.elapsedRealtime())&&it.point.valid()}
                if(os.showCrosshair){chartView.selectedPlaceId=os.mark(os.center,PlaceCapture(capturedAt)).id;os.showCrosshair=false}
                else if(fresh&&current!=null){chartView.selectedPlaceId=os.mark(current.point,PlaceCapture(capturedAt,current.utc.takeIf {it>0},current.source)).id}
                else {chartView.selectedPlaceId=null;os.showCrosshair=true;os.follow=false}
                chartView.selectedAisMmsi=null
            }),
            AppCommand("record",if(recording&&!recordingPaused)"record"else"play",when {
                recording&&recordingPaused->os.t("管理暂停的记录","Manage paused recording")
                recording->os.t("管理当前记录","Manage recording")
                else->os.t("开始记录","Start recording")
            },onRecording,active=recording),
            AppCommand("measure","ruler",os.t("测量距离","Measure distance"),{
                if(os.ruler.isNotEmpty())os.ruler=emptyList()else host?.let {h->h.camera?.let {camera->
                    os.ruler=listOf(camera.unproject(h.width*.3f,h.height*.5f),camera.unproject(h.width*.7f,h.height*.5f));os.showCrosshair=false
                }}
            },enabled=host?.camera!=null,active=os.ruler.isNotEmpty()),
        ),secondaryActions=listOf(
            AppCommand("route","route",if(os.draftRoute.isEmpty())os.t("规划航线","Plan a route")else os.t("继续航线草稿","Continue route draft"),{resumeOrCreateRouteDraft(os);os.follow=false}),
            AppCommand("tools","settings",os.t("海图工具","Chart tools"),{tools=true}),
            AppCommand("direction","compass",os.t("立体方向","3D direction"),{openSpatial()},enabled=display!=null),
        ))
        }
    }
    if(layers)MapSourcePicker(os,aisLayer=false){layers=false}
    if(planning&&os.editingRoute)PassagePlanningPanel(os){planning=false}
    if(externalNavigation)ExternalNavigationDialog(os){externalNavigation=false}
    if(manageNavigation)os.activeRoute?.let {NavigationActionsDialog(os,it){manageNavigation=false}}
    startingPlaceId?.let {id->os.allPlaces.firstOrNull {it.id==id}?.let {place->StartNavigationDialog(os,Route("goto:${place.id}",place.name,listOf(place.point))){startingPlaceId=null}}}
    editingPlaceId?.let {id->os.places.firstOrNull {it.id==id}?.let {place->CoordinateEditor(os,place,{editingPlaceId=null}){os.sailing.put(it);editingPlaceId=null}}}
    morePlaceId?.takeIf {chartInputEnabled}?.let {id->os.allPlaces.firstOrNull {it.id==id}?.let {place->AppDialog(onDismissRequest={morePlaceId=null}) {AppDialogSurface {
        AppDialogTitle(place.name)
        MenuRow(os.t("完整资料","Full details")){morePlaceId=null;os.openLinked("place:${place.id}")}
        MenuRow(os.t("在这里设置守锚","Prepare anchor watch here")){
            val spot=place.id.takeIf {it.startsWith("spot:")}?.substringAfter(':')?.toLongOrNull()?.let {id->os.sailing.spots.firstOrNull {it.id==id}}
            morePlaceId=null;os.anchorDraft=AnchorDraft(place.point,place.name,spot?.placeId,spot?.id);os.openLinked("anchor:setup")
        }
        MetroButton(os.t("完成","Done"),{morePlaceId=null})
    }}}}
    if(tools)AppDialog(onDismissRequest={tools=false}) {AppDialogSurface {
        AppDialogTitle(os.t("海图工具","Chart tools"))
        MenuRow(os.t("立体方向","3D direction")){tools=false;openSpatial()}
        NavigationLiftPreference(liftEnabled,os.chinese){value->
            os.shell.requestSystemPreferences("chart.lift_to_direction"){before->before.copy(appPreferenceValues=before.appPreferenceValues+("chart.lift_to_direction" to if(value)"b:1"else"b:0"))}
        }
        MenuRow(os.t("外部导航","External navigation")){tools=false;externalNavigation=true}
        AppSection(os.t("地图朝向","Map orientation"))
        MapOrientationMode.entries.forEach{mode->ChoiceRow(when(mode){MapOrientationMode.NORTH_UP->os.t("北向朝上","North up");MapOrientationMode.HEADING_UP->os.t("船艏朝上","Heading up");MapOrientationMode.COURSE_UP->os.t("航迹向朝上","Course up")},chartView.orientationMode==mode){
            os.shell.requestSystemPreferences("chart.orientation"){before->before.copy(appPreferenceValues=before.appPreferenceValues+("chart.orientation" to "c:${mode.name}"))}
        }}
        AisLayerChoice(os,anchor=false)
        AisMonitoringSummary(os,traffic,!traffic.preferences.chartLayer)
        MenuRow(os.t("周围船舶","surrounding traffic"),aisInputSummary(os,traffic)){tools=false;os.openLinked("ais")}
        if(chartView.previewTrack.isNotEmpty())MenuRow(os.t("结束日志轨迹预览","close logbook track preview"),chartView.previewTitle){chartView.previewTrack=emptyList();chartView.previewTitle=null;tools=false}
        if(os.activeRoute!=null)MenuRow(os.t("当前导航","current navigation"),os.activeRoute?.name){tools=false;manageNavigation=true}
        if(chartView.previewRoute!=null||os.displayedRouteId!=null&&os.displayedRouteId!=os.activeRouteId)MenuRow(os.t("结束路线预览","close route preview")){os.shell.hideChartRoutePreview();os.save();tools=false}
        MetroButton(os.t("关闭","close"),{tools=false})
    }}
}

@Composable fun ConfirmDialog(os:OsStore,title:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    AppDialog(onDismissRequest=onDismiss) {AppDialogSurface {
        AppDialogTitle(title)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            MetroButton(os.t("确认","Confirm"),onConfirm,Modifier.weight(1f),primary=true)
            MetroButton(os.t("取消","Cancel"),onDismiss,Modifier.weight(1f))
        }
    }}
}