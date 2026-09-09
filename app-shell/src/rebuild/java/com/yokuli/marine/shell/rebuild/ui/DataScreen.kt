package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.data.VesselData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable internal fun rememberMarineClock():Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while(true) { delay(1000);now=SystemClock.elapsedRealtime() } }
    // A measurement can arrive between timer ticks. Read the monotonic clock again
    // on that recomposition so a just-received sample is not mistaken for future data.
    return maxOf(now,SystemClock.elapsedRealtime())
}
internal fun readingAge(os:OsStore,time:Long,now:Long) = when {
    time>now -> os.t("接收时间异常","invalid receive time")
    now-time<2000 -> os.t("刚刚","now")
    else -> os.t("${(now-time)/1000} 秒前","${(now-time)/1000} s ago")
}
internal fun connectionLabel(os:OsStore,data:VesselData,now:Long):String = when(data.connection) {
    "off"->os.t("未连接","disconnected")
    "connecting"->os.t("正在连接","connecting")
    "waiting"->os.t("已连接，等待数据","connected, waiting for data")
    "live"->if(now-data.lastRx<=10000) os.t("正在接收","receiving") else os.t("数据已过期","data stale")
    "reconnecting"->os.t("等待重连","reconnecting")
    else->os.t("连接失败","connection failed")
}
internal fun metricName(os:OsStore,key:String)=when(key) {
    "sog"->os.t("对地航速","speed over ground")
    "cog"->os.t("对地航向","course over ground")
    "heading"->os.t("真船首向","true heading")
    "depth"->os.t("水深","depth")
    "ukc"->os.t("龙骨下余量","under-keel clearance")
    "aws"->os.t("视风速","apparent wind speed")
    "awa"->os.t("视风角","apparent wind angle")
    "tws"->os.t("真风速","true wind speed")
    "bsp"->os.t("对水航速","speed through water")
    "pressure"->os.t("气压","pressure")
    "water"->os.t("水温","water temperature")
    else->key
}

@Composable fun DataScreen(os:OsStore,service:(String,String?)->Unit) {
    val data by os.hub.state.collectAsState()
    val history by os.hub.history.collectAsState()
    val now=rememberMarineClock(); val c=LocalMetro.current
    val fix=data.fix(os.positionSource)?.takeIf {it.fresh(now)}
    var selected by remember {mutableStateOf("sog")}
    val keys=listOf("sog","depth","aws","heading","cog","awa","tws","bsp","ukc","pressure","water")
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("船舶数据","boat data"))
        Pivot(listOf(os.t("此刻","now"),os.t("变化","trends"),os.t("来源","sources"))) { page ->
            val bodyScroll=rememberScrollState()
            val scope=rememberCoroutineScope()
            PageBody(bodyScroll) {
                if(page==2) {
                    PositionSources(os,data,now,service)
                } else {
                    if(page==0) {
                        Label(if(fix!=null) os.t("船位正在更新","position is live") else if(os.positionSource=="none") os.t("船位已关闭","position is off") else os.t("等待可信船位","waiting for a trusted position"),17,if(fix!=null)c.accent else c.muted)
                        if(os.positionSource=="demo") Label(os.t("演示数据","DEMO DATA"),18,c.accent)
                        fix?.let {
                            Label(coordinates(it.point),20)
                            Label("${it.source} · ${readingAge(os,it.elapsed,now)}${it.accuracy?.let { a -> " · ±${decimal(a,0)} m" }.orEmpty()}",14,c.muted)
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(22.dp)) {
                        keys.filter {it in listOf("sog","depth","aws") || data.readings.containsKey(it) || history.containsKey(it)}.forEach { key ->
                            Label(metricName(os,key),22,if(selected==key)c.accent else c.muted,Modifier.clickable {selected=key}.padding(vertical=8.dp))
                        }
                    }
                    val current=data.readings[selected]
                    Label("${decimal(current?.takeIf {it.fresh(now)}?.value)} ${current?.unit.orEmpty()}",58)
                    Label(current?.let {"${it.source} · ${readingAge(os,it.elapsed,now)}${if(!it.fresh(now))os.t(" · 已过期"," · stale") else ""}"}
                        ?: os.t("还没有收到此项数据","no measurement received yet"),15,c.muted)
                    ReadingTrace(os,history[selected].orEmpty(),selected,now)
                    if(page==1) {
                        Label(os.t("最近 15 分钟 · 本次运行期间","last 15 minutes · this app session"),15,c.muted)
                        Label(os.t("按住曲线查看过去的读数。断线和来源变化会断开曲线；完整航程请在航行记录中保存。","Touch the trace to inspect a reading. Gaps and source changes stay separate. Use trip recording to keep a complete voyage."),17,c.muted)
                        MenuRow(os.t("记录这段变化","record these conditions"),os.t("把位置与船况保存在一次航行中","save position and conditions in a voyage"),"record") {os.open("trip")}
                    } else {
                        Label(os.t("船上正在发生什么","aboard, right now"),26)
                        keys.filter {it!=selected && data.readings.containsKey(it)}.forEach { key ->
                            val value=data.readings.getValue(key)
                            Row(Modifier.fillMaxWidth().clickable {selected=key;scope.launch {bodyScroll.animateScrollTo(0)}}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {Label(metricName(os,key),21);Label(value.source,12,c.muted)}
                                Label("${decimal(value.takeIf {it.fresh(now)}?.value)} ${value.unit}",27,if(value.fresh(now))c.fg else c.muted)
                            }
                        }
                        if(data.readings.isEmpty()) Label(os.t("开启手机定位可查看航速；连接船载 NMEA 后，风、水深和船首向会出现在这里。","Phone location provides speed. Connect boat NMEA to bring in wind, depth and heading."),18,c.muted)
                        MenuRow(os.t("打开仪表","open instruments"),os.t("大字读数、船体姿态与自定义仪表","large readings, vessel attitude & your own instruments"),"data") {os.open("instruments")}
                    }
                    MenuRow("NMEA",connectionLabel(os,data,now),"connect") {os.open("nmea")}
                    if(os.positionSource=="none") MetroButton(os.t("开启手机定位","enable phone GPS"),{service("gpsOn",null)})
                }
            }
        }
    }
}

@Composable private fun PositionSources(os:OsStore,data:VesselData,now:Long,service:(String,String?)->Unit) {
    val c=LocalMetro.current
    Label(os.t("谁提供船位","where position comes from"),29)
    val nmeaConnected=data.connection in listOf("waiting","live")
    val sourceLocked=os.marine?.vm?.ui?.collectAsState()?.value?.active?.paused==false
    Toggle(os.t("手机 GPS","phone GPS"),os.positionSource=="phone",
        if(os.positionSource=="nmea") os.t("先关闭 NMEA 船位","turn NMEA position off first") else os.t("开启即启动定位，关闭即停止","starts and stops phone location"),
        enabled=!sourceLocked && os.positionSource in listOf("none","phone")) {service(if(it) "gpsOn" else "gpsOff",null)}
    Toggle(os.t("NMEA 船位","NMEA position"),os.positionSource=="nmea",
        when {os.positionSource=="phone"->os.t("先关闭手机 GPS","turn phone GPS off first")
            !nmeaConnected->os.t("请先连接 NMEA","connect NMEA first")
            data.nmea==null->os.t("已连接，等待有效船位","connected, awaiting a valid position")
            else->readingAge(os,data.nmea.elapsed,now)},
        enabled=!sourceLocked && (os.positionSource=="nmea" || (os.positionSource=="none" && nmeaConnected))) {service(if(it) "sourceNmea" else "sourceOff",null)}
    Label(if(sourceLocked) os.t("锚警报正在值守；暂停后可以更改船位来源。","Pause the active anchor watch before changing its position source.") else
        os.t("两项都可以关闭。NMEA 仍可提供水深、风与仪表数据；船位不会自动切换来源。","Both can be off. NMEA can still provide depth, wind and instruments. Position never changes source automatically."),17,c.muted)
    MenuRow(os.t("每个读数从哪里来","inspect measurement sources"),os.t("字段来源、质量、手机传感器与自定义能力","provenance, quality, phone sensors & custom capabilities"),"data") {os.open("sources")}
    MenuRow("NMEA",connectionLabel(os,data,now),"connect") {os.open("nmea")}
}
