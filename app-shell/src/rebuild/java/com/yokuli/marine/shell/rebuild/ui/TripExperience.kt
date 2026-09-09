package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.marine.shell.rebuild.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos

@Composable internal fun TripRecorderScreen(os:OsStore) {
    val marine=os.marine ?: return
    val state by marine.vm.ui.collectAsState()
    val data by os.hub.state.collectAsState()
    val history by os.hub.history.collectAsState()
    val clock=rememberMarineClock()
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    var controls by remember {mutableStateOf(false)}
    var moment by remember {mutableStateOf(false)}
    var metric by remember {mutableStateOf("sog")}
    val c=LocalMetro.current
    LaunchedEffect(Unit) {while(true) {delay(1000);now=System.currentTimeMillis()}}
    val trip=state.activeTrip
    val freshFix=data.fix(os.positionSource)?.takeIf {it.fresh(clock)}
    val elapsed=trip?.let { ((if(it.paused) it.pausedAt ?: now else now)-it.startedAt-it.accumulatedPausedMillis).coerceAtLeast(0)/1000 } ?: 0L
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("航行记录","trip recorder"))
        Pivot(listOf(os.t("航行","voyage"),os.t("船况","conditions"),os.t("回忆","memories"))) {page->
            PageBody {
                if(page==0) {
                    if(trip==null) {
                        Label(os.t("下一次出发","your next departure"),40,c.accent)
                        Label(os.t("留下航迹，也留下沿途值得记住的时刻。","Keep the track and the moments worth remembering."),22)
                        Label(when {state.active!=null->os.t("锚警报正在值守，起锚后可以开始航行。","An anchor watch is active. Lift the anchor before departing.")
                            freshFix!=null->os.t("船位已准备好，可以出发。","Your position is ready. You can depart.")
                            os.positionSource=="none"->os.t("先选择船位，航迹才知道从哪里开始。","Choose a position source to give your track a starting point.")
                            else->os.t("正在等待所选来源提供有效船位。","Waiting for a valid fix from the selected source.")},17,c.muted)
                        MetroButton(os.t("开始记录","start recording"),{controls=true},primary=true,enabled=state.active==null)
                        if(freshFix==null) MenuRow(os.t("准备船位","prepare position"),os.t("手机 GPS 或已连接的 NMEA","phone GPS or connected NMEA"),"locate") {os.open("data")}
                        os.displayedRouteId?.let {id->os.routes.firstOrNull {it.id==id}}?.let {route->
                            MenuRow(route.name,os.t("已选航线 · ${nm(route.length)}","selected route · ${nm(route.length)}"),"route") {os.open("route:${route.id}")}
                        }
                    } else {
                        Label(trip.name,28)
                        Label(nm(trip.distanceMeters),56,c.accent)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Label("%02d:%02d:%02d".format(elapsed/3600,elapsed/60%60,elapsed%60),29)
                            Label(if(trip.paused)os.t("已暂停","paused") else os.t("正在记录","recording"),18,c.muted)
                        }
                        TrackSketch(os,os.recordedSegments) {
                            os.fitRequest=os.recordedSegments.flatten().takeIf {it.isNotEmpty()};os.follow=false;os.open("chart")
                        }
                        if(freshFix==null && !trip.paused) Label(os.t("船位暂不可用。记录仍在继续，缺失的位置会保留为航迹间断。","Position is unavailable. Recording continues; missing positions remain gaps in the track."),17,c.muted)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f)) {Label("${decimal(trip.maxSogKnots)} kn",30);Label(os.t("最高航速","top speed"),14,c.muted)}
                            Column(Modifier.weight(1f)) {Label("${trip.waypointCount}",30);Label(os.t("沿途标记","moments"),14,c.muted)}
                        }
                        MetroButton(os.t("记住这一刻","mark this moment"),{moment=true},primary=true,enabled=freshFix!=null)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            MetroButton(if(trip.paused)os.t("继续","resume") else os.t("暂停","pause"),{if(trip.paused)marine.vm.resumeTrip() else marine.vm.pauseTrip()},Modifier.weight(1f))
                            MetroButton(os.t("结束航行","finish voyage"),{controls=true},Modifier.weight(1f))
                        }
                    }
                    MenuRow(os.t("海图","chart"),os.t("查看船位、选点和航线","position, points and routes"),"chart") {os.open("chart")}
                } else if(page==1) {
                    Label(os.t("这一路的变化","conditions along the way"),28)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(22.dp)) {
                        listOf("sog","depth","aws","pressure").forEach {key->Label(metricName(os,key),22,if(metric==key)c.accent else c.muted,Modifier.clickable {metric=key}.padding(vertical=8.dp))}
                    }
                    val current=data.readings[metric]
                    Label("${decimal(current?.takeIf {it.fresh(clock)}?.value)} ${current?.unit.orEmpty()}",52)
                    ReadingTrace(os,history[metric].orEmpty().filter {it.elapsed>=clock-((now-(trip?.startedAt ?: now-900_000)).coerceIn(0,900_000))},metric,clock)
                    Label(os.t("最近 15 分钟的实况；完整记录保存在航行日志。","Live conditions over the last 15 minutes. Complete recordings live in the logbook."),16,c.muted)
                    if(trip!=null) {
                        MenuRow(os.t("大字与空间仪表","large & spatial instruments"),os.t("船首向、横倾、纵倾与自定义读数","heading, heel, pitch and custom readings"),"data") {os.open("instruments")}
                        MetroButton(os.t("添加天气或换帆记录","note weather or a sail change"),{moment=true},enabled=freshFix!=null)
                    }
                    MenuRow(os.t("查看数据来源","inspect data sources"),os.t("读数、来源与更新时间","measurements, provenance and age"),"connect") {os.open("data")}
                } else {
                    val completed=state.tripSessions.filter {!it.active}.sortedByDescending {it.startedAt}
                    Label(os.t("${completed.size} 次航行","${completed.size} voyages"),42,c.accent)
                    if(completed.isEmpty()) Label(os.t("第一段航行结束后，航迹、沿途标记和船况报告会留在这里。","After your first voyage, return to its track, moments and boat-condition report here."),22)
                    completed.take(8).forEach {saved->
                        Column(Modifier.fillMaxWidth().padding(vertical=10.dp)) {
                            Label(saved.name,27)
                            Label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(saved.startedAt)),14,c.muted)
                            Label("${nm(saved.distanceMeters)} · ${os.t("${saved.waypointCount} 个标记","${saved.waypointCount} moments")}",21,modifier=Modifier.padding(vertical=10.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                MetroButton(os.t("回放航迹","replay track"),{marine.vm.openTripMap(saved.id);os.open("voyages")},Modifier.weight(1f))
                                MetroButton(os.t("导出 GPX","export GPX"),{marine.vm.exportTripGpx(saved)},Modifier.weight(1f))
                            }
                        }
                    }
                    MenuRow(os.t("完整航行日志","full logbook"),os.t("所有航程、报告、事件和导出","all voyages, reports, events and exports"),"logbook") {os.open("voyages")}
                }
            }
        }
    }
    if(controls) RecordingDialog(os) {controls=false}
    if(moment) TripMomentDialog(os) {moment=false}
}

@Composable private fun TrackSketch(os:OsStore,segments:List<List<GeoPoint>>,open:()->Unit) {
    val c=LocalMetro.current
    val all=segments.flatten()
    Column(Modifier.fillMaxWidth().clickable(onClick=open)) {
        Canvas(Modifier.fillMaxWidth().height(180.dp).background(c.panel)) {
            if(all.isNotEmpty()) {
                val origin=all.first();val latitude=all.sumOf {it.lat}/all.size
                fun xy(p:GeoPoint)=Pair((((p.lon-origin.lon+540)%360)-180)*cos(Math.toRadians(latitude)),p.lat-origin.lat)
                val coords=all.map(::xy)
                val minX=coords.minOf {it.first};val maxX=coords.maxOf {it.first}
                val minY=coords.minOf {it.second};val maxY=coords.maxOf {it.second}
                val extentX=(maxX-minX).coerceAtLeast(.0001);val extentY=(maxY-minY).coerceAtLeast(.0001)
                val scale=minOf((size.width-36.dp.toPx())/extentX,(size.height-36.dp.toPx())/extentY)
                fun project(p:GeoPoint):Offset {val(x,y)=xy(p);return Offset((size.width/2+(x-(minX+maxX)/2)*scale).toFloat(),(size.height/2-(y-(minY+maxY)/2)*scale).toFloat())}
                segments.forEach {segment->val path=Path();segment.forEachIndexed {index,p->val point=project(p);if(index==0)path.moveTo(point.x,point.y) else path.lineTo(point.x,point.y)};drawPath(path,c.accent,style=Stroke(2.dp.toPx()))}
                drawCircle(c.fg,6.dp.toPx(),project(all.first()),style=Stroke(2.dp.toPx()))
                drawCircle(c.accent,4.dp.toPx(),project(all.last()))
            }
        }
        Label(if(all.isEmpty())os.t("等待航迹 · 点击打开海图","waiting for track · tap to open chart") else os.t("航迹形状 · 北向上 · 点击查看海图","track shape · north up · tap for chart"),13,c.muted,Modifier.padding(top=7.dp))
    }
}

@Composable private fun TripMomentDialog(os:OsStore,dismiss:()->Unit) {
    var name by remember {mutableStateOf("")};var note by remember {mutableStateOf("")};var type by remember {mutableStateOf("GENERAL")}
    val c=LocalMetro.current
    Dialog(onDismissRequest=dismiss) {
        Column(Modifier.fillMaxWidth().background(c.bg).border(1.dp,c.muted).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Label(os.t("记住这一刻","keep this moment"),32)
            Field(os.t("名称","name"),name,{name=it.take(100)})
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                listOf("GENERAL" to os.t("随记","moment"),"SAIL_CHANGE" to os.t("换帆","sail change"),"WEATHER" to os.t("天气","weather"),"HAZARD" to os.t("注意点","hazard")).forEach {(value,label)->
                    Label(label,21,if(type==value)c.accent else c.muted,Modifier.clickable {type=value}.padding(vertical=8.dp))
                }
            }
            Field(os.t("想记住什么","what happened"),note,{note=it.take(2000)},multiline=true)
            Label(os.t("保存时记录当前有效船位和船况。","Saving captures the current valid position and boat conditions."),14,c.muted)
            MetroButton(os.t("保存这一刻","save moment"),{os.marine?.vm?.markTripWaypoint(name.trim(),note.trim(),type);dismiss()},primary=true,enabled=name.isNotBlank())
            MetroButton(os.t("取消","cancel"),dismiss)
        }
    }
}
