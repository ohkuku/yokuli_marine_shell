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
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while(true) { delay(1000); tick=SystemClock.elapsedRealtime() } }
    val fix=data.fix(os.positionSource); val fresh=fix?.fresh(tick)==true
    var host by remember { mutableStateOf<ChartHost?>(null) }
    var layers by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var coverage by remember { mutableStateOf<Boolean?>(null) }
    val context=LocalContext.current
    val c=LocalMetro.current
    val selected=os.library.selected
    LaunchedEffect(os.center,os.zoom,os.mapMode,os.library.revision) {
        coverage=null
        if(os.mapMode=="marine" && selected.isNotEmpty()) {
            delay(550)
            val center=os.center; val zoom=os.zoom
            coverage=withContext(Dispatchers.IO) {
                selected.any { chart ->
                    ensureActive()
                    runCatching {
                    val z=floor(zoom+1).toInt().coerceAtMost(chart.maxZoom)
                    if(z<chart.minZoom) false else {
                        val n=2.0.pow(z); val x=floor((center.lon+180)/360*n).toInt().coerceIn(0,(1 shl z)-1)
                        val lat=Math.toRadians(center.lat.coerceIn(-85.0511,85.0511)); val y=floor((1-asinh(tan(lat))/PI)/2*n).toInt()
                        ChartReader(context,Uri.parse(chart.uri)).use { it.coverageZoom(x,y,z,chart)!=null }
                    }
                }.getOrDefault(false) }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(55.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { os.back() },contentAlignment=Alignment.CenterStart) { Glyph("back",Modifier.size(23.dp)) }
            Label(os.t("海图","chart"),31,modifier=Modifier.weight(1f))
            Label(when(os.mapMode) {"standard"->os.t("地图","map");"satellite"->os.t("卫星","satellite");else->os.t("海图","marine")},13,c.muted,Modifier.clickable { layers=true }.padding(8.dp))
            Glyph("layers",Modifier.size(25.dp).clickable {layers=true})
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NativeChart(os,fix,Modifier.fillMaxSize()) { host=it }
            // Reference these observable values to redraw the native overlay while dragging.
            SideEffect { os.ruler; os.draftRoute; os.showCrosshair; os.places; host?.overlay?.invalidate() }
            Row(Modifier.align(Alignment.TopStart).padding(10.dp).background(c.bg.copy(alpha=.94f)).clickable { os.open("data") }.padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(if(fresh) c.accent else c.muted)); Spacer(Modifier.width(8.dp))
                Label(if(fresh) "${decimal(fix?.freshSpeed(tick))} kn   ${decimal(fix?.freshCourse(tick),0)}°" else if(fix!=null) os.t("船位已过期","position stale") else os.t("等待船位","waiting for position"),15)
                Spacer(Modifier.width(8.dp)); Label(when(os.positionSource) {"nmea"->"NMEA";"phone"->"GPS";else->os.t("未选择来源","no source")},11,c.muted)
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end=10.dp).background(c.bg.copy(alpha=.94f))) {
                Box(Modifier.size(46.dp).clickable { os.fly(os.center,(os.zoom+1).coerceAtMost(22.0)) },contentAlignment=Alignment.Center) { Glyph("plus",Modifier.size(22.dp)) }
                Box(Modifier.size(46.dp).clickable { os.fly(os.center,(os.zoom-1).coerceAtLeast(1.0)) },contentAlignment=Alignment.Center) { Glyph("minus",Modifier.size(22.dp)) }
            }
            if(os.mapMode=="marine" && selected.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(30.dp).widthIn(max=350.dp).background(c.bg).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Glyph("chart",Modifier.size(44.dp),c.accent)
                    Label(os.t("带上你的海图","bring your charts"),32)
                    Label(os.t("连接存放海图的文件夹，就能在这里直接浏览和操作。","Connect your chart folder to browse and work directly on the chart."),17,c.muted)
                    MetroButton(os.t("打开海图库","open chart library"),{os.open("library")},primary=true)
                    MetroButton(os.t("先浏览在线地图","browse online map"),{os.mapMode="standard";os.save()})
                }
            }
            if(coverage==false && selected.isNotEmpty() && os.mapMode=="marine") {
                Label(os.t("准星处无本地图块 · 点击查看海图","no local tile here · view a chart"),13,c.fg,
                    Modifier.align(Alignment.TopCenter).padding(top=58.dp).background(c.bg.copy(alpha=.93f)).clickable {layers=true}.padding(10.dp))
            }
            if(host?.error!=null) Label(os.t("海图读取失败 · 打开海图库检查","chart read failed · check library"),13,Color.White,
                Modifier.align(Alignment.TopCenter).padding(top=94.dp).background(Color(0xFFB94F2B)).clickable {os.open("library")}.padding(10.dp))
            Column(Modifier.align(Alignment.BottomEnd).padding(end=4.dp,bottom=2.dp).widthIn(max=230.dp)) {
                if(os.mapMode=="standard" && !BuildConfig.GOOGLE_MAPS_CONFIGURED) Label("© OpenStreetMap contributors",10,Color(0xFF263B43),Modifier.background(Color.White.copy(alpha=.9f)).clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.openstreetmap.org/copyright")))
                }.padding(4.dp))
                if(os.mapMode=="marine") selected.map { android.text.Html.fromHtml(it.attribution,0).toString() }.filter { it.isNotBlank() }.distinct().take(2).forEach { attribution ->
                    Label(attribution,10,Color(0xFF263B43),Modifier.background(Color.White.copy(alpha=.9f)).padding(3.dp),maxLines=3)
                }
            }
            // Context controls float over a stable native viewport. Showing the crosshair must
            // never resize the map or change its camera/texture resolution during a drag.
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom=48.dp)) {
        if(os.ruler.size==2) {
            Row(Modifier.fillMaxWidth().background(c.panel).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("${nm(distance(os.ruler[0],os.ruler[1]))}   ${decimal(bearing(os.ruler[0],os.ruler[1]),0)}°T",25)
                    Label(os.t("拖动 A / B 图钉测距","drag pins A / B to measure"),12,c.muted)
                }
                Glyph("close",Modifier.size(34.dp).clickable {os.ruler=emptyList()})
            }
        } else if(os.editingRoute) {
            Label(os.t("${os.draftRoute.size} 个航点 · 准星选点，拖动圆点调整","${os.draftRoute.size} points · aim, add, drag to adjust"),13,c.muted,Modifier.fillMaxWidth().background(c.panel).padding(10.dp))
        } else if(os.showCrosshair) {
            Row(Modifier.fillMaxWidth().background(c.panel).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(coordinates(os.center),15)
                    if(fresh && fix!=null) Label("${nm(distance(fix.point,os.center))}   ${decimal(bearing(fix.point,os.center),0)}°T",13,c.muted)
                }
                Glyph("close",Modifier.size(28.dp).clickable {os.showCrosshair=false})
            }
        } else if(os.activeRoute!=null) {
            Row(Modifier.fillMaxWidth().background(c.panel).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("${os.activeRoute!!.name}  ·  ${os.routeLeg+1}/${os.activeRoute!!.points.size}",17,maxLines=1)
                    Label(if(fresh && fix!=null && os.nextPoint!=null) "${nm(distance(fix.point,os.nextPoint!!))}   ${decimal(bearing(fix.point,os.nextPoint!!),0)}°T" else os.t("等待新鲜船位","waiting for fresh position"),13,c.muted)
                }
                IconAction("next",os.t("下一点","next"),{os.advanceRoute()})
            }
        }
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
                    else { os.open("data"); os.notify("先开启手机定位或连接 NMEA","Enable phone GPS or connect NMEA first") }
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
                IconAction("more",os.t("收藏","saved"),{os.open("places")})
            }
        }
    }
    if(naming) TextDialog(os,os.t("保存航线","save route"),os.routes.firstOrNull {it.id==os.editingRouteId}?.name ?: os.t("航线 ${os.routes.size+1}","route ${os.routes.size+1}"),{naming=false}) { name ->
        val route=Route(id=os.editingRouteId ?: uid(),name=name,points=os.draftRoute.toList()); os.routes=os.routes.filter {it.id!=route.id}+route; os.displayedRouteId=null
        if(os.activeRouteId==route.id) {os.activeRouteId=null;os.routeLeg=0}
        os.editingRoute=false;os.editingRouteId=null;os.draftRoute=emptyList();os.showCrosshair=false;os.save();os.notify("航线已保存","Route saved")
    }
    if(discard) ConfirmDialog(os,os.t("放弃这条未保存的航线？","Discard this unsaved route?"),{discard=false}) {os.draftRoute=emptyList();os.editingRoute=false;os.editingRouteId=null;discard=false}
    if(layers) Dialog(onDismissRequest={layers=false}) {
        Column(Modifier.fillMaxWidth().heightIn(max=650.dp).background(c.bg).border(1.dp,c.muted).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Label(os.t("地图","maps"),40)
            for((mode,title) in listOf("marine" to os.t("我的海图","my charts"),"standard" to os.t("在线地图","online map"),"satellite" to os.t("卫星影像","satellite"))) {
                MenuRow(title,if(mode=="satellite" && !BuildConfig.GOOGLE_MAPS_CONFIGURED) os.t("此 APK 未配置 Google Maps Key","Google Maps key not configured in this APK") else null) {
                    if(mode=="satellite" && !BuildConfig.GOOGLE_MAPS_CONFIGURED) return@MenuRow
                    os.mapMode=mode;os.save();layers=false
                }
            }
            if(os.library.layers.isNotEmpty()) {
                Label(os.t("我的图层 · 上方优先","my layers · top first"),14,c.accent)
                os.library.folders.filter {it.layerName!=null}.forEach {folder ->
                    val count=os.library.folderFiles(folder).count {it.enabled && it.error==null}
                    Toggle(folder.layerName!!,folder.enabled,os.t("$count 张海图","$count charts")) {os.library.toggleLayer(folder)}
                }
            }
            os.displayedRouteId?.let {id ->
                os.routes.firstOrNull {it.id==id}?.let {route ->
                    MenuRow(os.t("隐藏航线：${route.name}","hide route: ${route.name}")) {
                        os.displayedRouteId=null
                        if(os.activeRouteId==id) {os.activeRouteId=null;os.routeLeg=0}
                        os.save();layers=false
                    }
                }
            }
            MetroButton(os.t("管理海图库","manage chart library"),{layers=false;os.open("library")})
            MetroButton(os.t("关闭","close"),{layers=false})
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
