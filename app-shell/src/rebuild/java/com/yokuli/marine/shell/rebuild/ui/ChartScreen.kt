package com.yokuli.marine.shell.rebuild.ui

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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

@Composable fun ChartScreen(os:OsStore,recording:Boolean=false,recordingPaused:Boolean=false,onRecording:()->Unit={os.open("trip")},initialAisMmsi:Int?=null) {
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
    var naming by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val c=LocalMetro.current
    val density=LocalDensity.current
    val chartView=os.maps.view("chart",os.center,os.zoom)
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
            // 晚到的位置可以完成这次明确的查看请求；用户已拖动、取消或改选后，绝不抢镜头。
            if(chartView.selectedAisMmsi!=initialAisMmsi.toString())aisFocusApplied=true
            else if(!os.editingRoute) requestedTarget?.position?.let {
                aisFocusApplied=true
                os.fly(it.geo(),os.zoom.coerceAtLeast(12.0))
            }
        }
    }
    ReportVisibleAppRoute(os,chartView.selectedAisMmsi?.let {"chart:ais:$it"} ?: "chart")
    AisRetainSelection(os,chartView.selectedAisMmsi?.toIntOrNull())
    val previewPlace=os.allPlaces.firstOrNull {it.id==chartView.selectedPlaceId}
    val selected=os.maps.selectedLayer()?.files.orEmpty()
    fun closeTool():Boolean = when {
        layers->{layers=false;true}
        tools->{tools=false;true}
        naming->{naming=false;true}
        discard->{discard=false;true}
        manageNavigation->{manageNavigation=false;true}
        chartView.selectedAisMmsi!=null->{if(initialAisMmsi?.toString()==chartView.selectedAisMmsi)os.shell.popRoute()else chartView.selectedAisMmsi=null;true}
        chartView.selectedPlaceId!=null->{chartView.selectedPlaceId=null;true}
        os.ruler.isNotEmpty()->{os.ruler=emptyList();true}
        os.editingRoute->{if(os.draftRoute.isEmpty())cancelRouteDraft(os)else discard=true;true}
        os.showCrosshair->{os.showCrosshair=false;true}
        else->false
    }
    val toolOpen=layers||tools||naming||discard||manageNavigation||chartView.selectedPlaceId!=null||chartView.selectedAisMmsi!=null||os.ruler.isNotEmpty()||os.editingRoute||os.showCrosshair
    AppBackHandler(toolOpen) {closeTool()}
    BindInternalAppInputHandler {input->input==ShellInput.BACK && closeTool()}
    Column(Modifier.fillMaxSize()) {
        MapPageHeader(os,os.title(AppId.CHART),{layers=true},hasLocalBack=toolOpen)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NativeChart(os,fix,Modifier.fillMaxSize()) { host=it }
            MapPositionReadout(os,fix,tick,Modifier.align(Alignment.TopStart).padding(10.dp))
            if(chartView.previewTrack.isEmpty())AisMapStatus(os,traffic,!traffic.preferences.chartLayer,Modifier.align(Alignment.TopStart).padding(start=10.dp,top=62.dp))
            MapZoomControls(os,chartView,Modifier.align(Alignment.TopEnd).padding(top=66.dp,end=10.dp)) {zoom->os.fly(os.center,zoom)}
            if(os.maps.source is MapSource.CustomLayer && selected.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(30.dp).widthIn(max=350.dp).background(c.bg).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Glyph("chart",Modifier.size(44.dp),c.accent)
                    AppSection(os.t("图层暂不可用","Layer unavailable"))
                    Label(os.t("这个图层当前没有可读取的海图。请检查文件夹授权、文件状态和参与的海图。","This layer has no readable charts. Check folder access, file status and included charts."),15,c.muted)
                    MetroButton(os.t("打开图册","open chart library"),{os.openLinked((os.maps.source as? MapSource.CustomLayer)?.layerId?.takeIf { id -> os.library.folders.any {it.id==id} }?.let { "library:$it" } ?: "library")},primary=true)
                    MetroButton(os.t("浏览内置地图","browse built-in map"),{os.maps.select(MapSource.Offline)})
                }
            }
            // Context controls float over a stable native viewport. Showing the crosshair must
            // never resize the map or change its camera/texture resolution during a drag.
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().onSizeChanged {chartView.bottomOverlayDp=with(density){it.height.toDp().value}}) {
        if(chartView.selectedAisMmsi!=null && !os.editingRoute && os.ruler.isEmpty()) {
            AisCompactDetail(os,traffic,chartView.selectedAisMmsi!!){chartView.selectedAisMmsi=null}
        } else if(previewPlace!=null && !os.editingRoute && os.ruler.isEmpty()) {
            Column(Modifier.fillMaxWidth().background(c.panel).padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Label(previewPlace.name,24,modifier=Modifier.weight(1f))
                    IconAction("close",os.t("关闭预览","close preview"),{chartView.selectedPlaceId=null})
                }
                Label(os.formatCoordinates(previewPlace.point),12,c.muted)
                MenuRow(os.t("地点详情","Place details")) {os.open("place:${previewPlace.id}")}
            }
        } else if(os.ruler.size==2) {
            Row(Modifier.fillMaxWidth().background(c.panel).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("${os.formatDistance(distance(os.ruler[0],os.ruler[1]))}   ${os.formatBearing(bearing(os.ruler[0],os.ruler[1]))}T",25)
                    Label(os.t("拖动 A / B 图钉测距","drag pins A / B to measure"),12,c.muted)
                }
                IconAction("close",os.t("结束测距","End measurement"),{os.ruler=emptyList()})
            }
        } else if(os.editingRoute) {
            Label(os.t("${os.draftRoute.size} 个航点 · 准星选点，拖动圆点调整","${os.draftRoute.size} points · aim, add, drag to adjust"),13,c.muted,Modifier.fillMaxWidth().background(c.panel).padding(10.dp))
        } else if(os.showCrosshair) {
            MapCrosshairReadout(os,os.center) {os.showCrosshair=false}
        }
        if(!os.editingRoute) ChartNavigationCard(os,fix,tick)
            }
        }
        if(os.editingRoute) AppCommandBar(os, listOf(
            AppCommand("undo", "undo", os.t("撤销航点", "Undo waypoint"), {os.draftRoute=os.draftRoute.dropLast(1)}, enabled=os.draftRoute.isNotEmpty()),
            AppCommand("add", "plus", os.t("添加航点", "Add waypoint"), {
                if(os.draftRoute.lastOrNull()?.let { distance(it,os.center)<1 }!=true) os.draftRoute=os.draftRoute+os.center
            }),
            AppCommand("save", "check", os.t("保存航线", "Save route"), {naming=true}, enabled=os.draftRoute.size>=2),
            AppCommand("cancel", "close", os.t("取消编辑", "Cancel editing"), {if(os.draftRoute.isEmpty()) cancelRouteDraft(os) else discard=true}),
        )) else AppCommandBar(os, listOf(
            AppCommand("position", "locate", os.t("回到船位", "Go to boat"), {
                if(fix!=null) { os.follow=fresh; os.showCrosshair=false; host?.camera?.move(fix.point,os.zoom) }
                else os.notify("暂无船位，请在数据中心查看来源", "No position yet. Check the source in Data Center.")
            }, active=os.follow),
            AppCommand("mark", "pin", os.t("标记位置", "Mark position"), {os.mark();chartView.selectedPlaceId=os.places.lastOrNull()?.id;os.showCrosshair=false}),
            AppCommand("record", if(recording && !recordingPaused) "record" else "play", when {
                recording && recordingPaused -> os.t("管理暂停的记录", "Manage paused recording")
                recording -> os.t("管理当前记录", "Manage recording")
                else -> os.t("开始记录", "Start recording")
            }, onRecording, active=recording),
            AppCommand("measure", "ruler", os.t("测量距离", "Measure distance"), {
                if(os.ruler.isNotEmpty()) os.ruler=emptyList() else host?.let { h -> h.camera?.let { camera ->
                    os.ruler=listOf(camera.unproject(h.width*.3f,h.height*.5f),camera.unproject(h.width*.7f,h.height*.5f)); os.showCrosshair=false
                } }
            }, enabled=host?.camera!=null, active=os.ruler.isNotEmpty()),
        ), secondaryActions=listOf(
            AppCommand("route", "route", os.t("规划航线", "Plan a route"), {resumeOrCreateRouteDraft(os)}),
            AppCommand("source", "layers", os.t("地图来源", "Map source"), {layers=true}),
            AppCommand("tools", "settings", os.t("海图工具", "Chart tools"), {tools=true}),
        ))
    }
    if(naming) TextDialog(os,os.t("保存航线","save route"),os.routes.firstOrNull {it.id==os.editingRouteId}?.name ?: os.t("航线 ${os.routes.size+1}","route ${os.routes.size+1}"),{naming=false}) { name ->
        val route=Route(id=os.editingRouteId ?: uid(),name=name,points=os.draftRoute.toList()); os.displayedRouteId=null
        // Saving edits never changes the frozen route of an active navigation session.
        os.editingRoute=false;os.editingRouteId=null;os.draftRoute=emptyList();os.showCrosshair=false;os.sailing.putRoute(route)
    }
    if(discard) ConfirmDialog(os,os.t("放弃这条未保存的航线？","Discard this unsaved route?"),{discard=false}) {cancelRouteDraft(os);discard=false}
    if(layers) MapSourcePicker(os,aisLayer=false) {layers=false}
    if(manageNavigation) os.activeRoute?.let { NavigationActionsDialog(os,it) {manageNavigation=false} }
    if(tools) Dialog(onDismissRequest={tools=false}) {
        AppDialogSurface {
            AppDialogTitle(os.t("海图工具","Chart tools"))
            AisLayerChoice(os,anchor=false)
            AisMonitoringSummary(os,traffic,!traffic.preferences.chartLayer)
            MenuRow(os.t("周围船舶","surrounding traffic"),aisInputSummary(os,traffic)){tools=false;os.openLinked("ais")}
            if(chartView.previewTrack.isNotEmpty()) MenuRow(os.t("结束日志轨迹预览","close logbook track preview"),chartView.previewTitle) {chartView.previewTrack=emptyList();chartView.previewTitle=null;tools=false}
            if(os.activeRoute!=null) MenuRow(os.t("当前导航","current navigation"),os.activeRoute?.name) {tools=false;manageNavigation=true}
            if(chartView.previewRoute!=null || os.displayedRouteId!=null && os.displayedRouteId!=os.activeRouteId) MenuRow(os.t("结束路线预览","close route preview")) {chartView.previewRoute=null;os.displayedRouteId=null;os.save();tools=false}
            MetroButton(os.t("关闭","close"),{tools=false})
        }
    }

}

@Composable fun ConfirmDialog(os:OsStore,title:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    Dialog(onDismissRequest=onDismiss) {
        AppDialogSurface {
            AppDialogTitle(title)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MetroButton(os.t("确认","Confirm"),onConfirm,Modifier.weight(1f),primary=true)
                MetroButton(os.t("取消","Cancel"),onDismiss,Modifier.weight(1f))
            }
        }
    }
}
