package com.yokuli.marine.shell.rebuild.ui

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import kotlinx.coroutines.*
import kotlin.math.*

@Composable fun ChartScreen(os:OsStore,recording:Boolean=false,recordingPaused:Boolean=false,onRecording:()->Unit={os.open("trip")}) {
    val data by os.hub.state.collectAsState()
    val tick=rememberMarineClock()
    val fix=data.fix(os.positionSource); val fresh=fix?.fresh(tick)==true
    var host by remember { mutableStateOf<ChartHost?>(null) }
    var layers by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    var manageNavigation by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val c=LocalMetro.current
    val selected=os.maps.selectedLayer()?.files.orEmpty()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(55.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { os.back() },contentAlignment=Alignment.CenterStart) { Glyph("back",Modifier.size(23.dp)) }
            Label(os.t("海图","chart"),31,modifier=Modifier.weight(1f))
            Label(os.maps.sourceName(os.chinese),13,c.muted,Modifier.clickable { layers=true }.padding(8.dp))
            Glyph("layers",Modifier.size(25.dp).clickable {layers=true})
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NativeChart(os,fix,Modifier.fillMaxSize()) { host=it }
            Row(Modifier.align(Alignment.TopStart).padding(10.dp).background(c.bg.copy(alpha=.94f)).clickable { os.open("sources") }.padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(if(fresh) c.accent else c.muted)); Spacer(Modifier.width(8.dp))
                Label(if(fresh) "${os.formatSpeed(fix?.freshSpeed(tick))}   ${decimal(fix?.freshCourse(tick),0)}°" else if(fix!=null) os.t("船位已过期","position stale") else os.t("等待船位","waiting for position"),15)
                Spacer(Modifier.width(8.dp)); Label(when(os.positionSource) {"nmea"->"NMEA";"phone"->"GPS";"demo"->os.t("演示","DEMO");else->os.t("未选择来源","no source")},11,c.muted)
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end=10.dp).background(c.bg.copy(alpha=.94f))) {
                Box(Modifier.size(46.dp).clickable { os.fly(os.center,(os.zoom+1).coerceAtMost(22.0)) },contentAlignment=Alignment.Center) { Glyph("plus",Modifier.size(22.dp)) }
                Box(Modifier.size(46.dp).clickable { os.fly(os.center,(os.zoom-1).coerceAtLeast(1.0)) },contentAlignment=Alignment.Center) { Glyph("minus",Modifier.size(22.dp)) }
            }
            if(os.maps.source is MapSource.CustomLayer && selected.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(30.dp).widthIn(max=350.dp).background(c.bg).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Glyph("chart",Modifier.size(44.dp),c.accent)
                    Label(os.t("图层暂不可用","layer unavailable"),32)
                    Label(os.t("这个图层当前没有可读取的海图。请检查文件夹授权、文件状态和参与的海图。","This layer has no readable charts. Check folder access, file status and included charts."),17,c.muted)
                    MetroButton(os.t("打开海图库","open chart library"),{os.open("library")},primary=true)
                    MetroButton(os.t("先浏览在线地图","browse online map"),{os.maps.select(MapSource.Online)})
                }
            }
            // Context controls float over a stable native viewport. Showing the crosshair must
            // never resize the map or change its camera/texture resolution during a drag.
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom=if(BuildConfig.GOOGLE_MAPS_CONFIGURED && os.maps.source !is MapSource.CustomLayer) 80.dp else 48.dp)) {
        if(os.ruler.size==2) {
            Row(Modifier.fillMaxWidth().background(c.panel).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("${os.formatDistance(distance(os.ruler[0],os.ruler[1]))}   ${decimal(bearing(os.ruler[0],os.ruler[1]),0)}°T",25)
                    Label(os.t("拖动 A / B 图钉测距","drag pins A / B to measure"),12,c.muted)
                }
                Glyph("close",Modifier.size(34.dp).clickable {os.ruler=emptyList()})
            }
        } else if(os.editingRoute) {
            Label(os.t("${os.draftRoute.size} 个航点 · 准星选点，拖动圆点调整","${os.draftRoute.size} points · aim, add, drag to adjust"),13,c.muted,Modifier.fillMaxWidth().background(c.panel).padding(10.dp))
        } else if(os.showCrosshair) {
            Row(Modifier.fillMaxWidth().background(c.panel).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(os.formatCoordinates(os.center),15)
                    if(fresh && fix!=null) Label("${os.formatDistance(distance(fix.point,os.center))}   ${decimal(bearing(fix.point,os.center),0)}°T",13,c.muted)
                }
                Glyph("close",Modifier.size(28.dp).clickable {os.showCrosshair=false})
            }
        }
        if(!os.editingRoute) ChartNavigationCard(os,fix,tick)
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min=69.dp).background(c.bg),horizontalArrangement=Arrangement.SpaceEvenly) {
            if(os.editingRoute) {
                IconAction("undo",os.t("撤销","undo"),{os.draftRoute=os.draftRoute.dropLast(1)})
                IconAction("plus",os.t("添加航点","add point"),{
                    if(os.draftRoute.lastOrNull()?.let { distance(it,os.center)<1 }!=true) os.draftRoute=os.draftRoute+os.center
                })
                IconAction("check",os.t("保存航线","save"),{if(os.draftRoute.size>=2) naming=true else os.notify("至少添加两个航点","Add at least two points")})
                IconAction("close",os.t("取消","cancel"),{if(os.draftRoute.isEmpty()) os.editingRoute=false else discard=true})
            } else {
                IconAction("locate",os.t("船位","boat"),{
                    if(fresh && fix!=null) { os.follow=true; os.showCrosshair=false; host?.camera?.move(fix.point,os.zoom) }
                    else { os.open("sources"); os.notify("先开启手机定位或连接 NMEA","Enable phone GPS or connect NMEA first") }
                },active=os.follow)
                IconAction("pin",os.t("标记","mark"),{os.mark();os.showCrosshair=true})
                IconAction("ruler",os.t("测距","measure"),{
                    if(os.ruler.isNotEmpty()) os.ruler=emptyList() else host?.let { h -> h.camera?.let { camera ->
                        os.ruler=listOf(camera.unproject(h.width*.3f,h.height*.5f),camera.unproject(h.width*.7f,h.height*.5f)); os.showCrosshair=false
                    } }
                },active=os.ruler.isNotEmpty())
                IconAction("route",os.t("航线","route"),{os.ruler=emptyList();os.editingRoute=true;os.showCrosshair=true})
                IconAction(if(recording && !recordingPaused) "stop" else "play",when {
                    recording && recordingPaused -> os.t("已暂停","paused")
                    recording -> os.t("记录中","recording")
                    else -> os.t("开始记录","record")
                },onRecording,active=recording)
                IconAction("more",os.t("更多","more"),{tools=true})
            }
        }
    }
    if(naming) TextDialog(os,os.t("保存航线","save route"),os.routes.firstOrNull {it.id==os.editingRouteId}?.name ?: os.t("航线 ${os.routes.size+1}","route ${os.routes.size+1}"),{naming=false}) { name ->
        val route=Route(id=os.editingRouteId ?: uid(),name=name,points=os.draftRoute.toList()); os.routes=os.routes.filter {it.id!=route.id}+route; os.displayedRouteId=null
        // Saving edits never changes the frozen route of an active navigation session.
        os.editingRoute=false;os.editingRouteId=null;os.draftRoute=emptyList();os.showCrosshair=false;os.save();os.notify("航线已保存","Route saved")
    }
    if(discard) ConfirmDialog(os,os.t("放弃这条未保存的航线？","Discard this unsaved route?"),{discard=false}) {os.draftRoute=emptyList();os.editingRoute=false;os.editingRouteId=null;discard=false}
    if(layers) MapSourcePicker(os) {layers=false}
    if(manageNavigation) os.activeRoute?.let { NavigationActionsDialog(os,it) {manageNavigation=false} }
    if(tools) Dialog(onDismissRequest={tools=false}) {
        Column(Modifier.fillMaxWidth().background(c.bg).border(1.dp,c.muted).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Label(os.t("海图工具","chart tools"),34)
            MenuRow(os.t("我的航行","my sailing"),os.t("收藏地点与航线","saved places and routes")) {tools=false;os.open("places")}
            MenuRow(os.t("实测水深","measured depth"),os.t("查看自己的测量与调查","your depth measurements and surveys")) {tools=false;os.open("chart:depth")}
            if(os.activeRoute!=null) MenuRow(os.t("当前导航","current navigation"),os.activeRoute?.name) {tools=false;manageNavigation=true}
            if(os.displayedRouteId!=null && os.displayedRouteId!=os.activeRouteId) MenuRow(os.t("结束路线预览","close route preview")) {os.displayedRouteId=null;os.save();tools=false}
            MetroButton(os.t("关闭","close"),{tools=false})
        }
    }

}

@Composable fun ConfirmDialog(os:OsStore,title:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().background(LocalMetro.current.bg).border(1.dp,LocalMetro.current.muted).padding(22.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Label(title,27)
            MetroButton(os.t("确认","confirm"),onConfirm,primary=true)
            MetroButton(os.t("取消","cancel"),onDismiss)
        }
    }
}
