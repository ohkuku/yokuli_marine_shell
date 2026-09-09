package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.data.VesselData
import kotlinx.coroutines.delay

@Composable private fun rememberClock():Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while(true) { delay(1000);now=SystemClock.elapsedRealtime() } }
    return now
}
private fun age(os:OsStore,time:Long,now:Long) = if(now-time<2000) os.t("刚刚","now") else os.t("${((now-time)/1000).coerceAtLeast(0)} 秒前","${((now-time)/1000).coerceAtLeast(0)} s ago")
private fun connection(os:OsStore,data:VesselData,now:Long):String = when(data.connection) {
    "off"->os.t("未连接","disconnected")
    "connecting"->os.t("正在连接","connecting")
    "waiting"->os.t("已连接，等待数据","connected, waiting for data")
    "live"->if(now-data.lastRx<=10000) os.t("正在接收","receiving") else os.t("连接仍在，数据已过期","connected, data stale")
    "reconnecting"->os.t("等待重连","reconnecting")
    else->os.t("连接失败","connection failed")
}
private fun message(os:OsStore,key:String):String=when(key) {
    "gps"->os.t("手机定位不可用，请检查权限及系统定位开关。","Phone location unavailable. Check permission and system location.")
    "mock"->os.t("检测到模拟定位，未作为真实船位使用。","Mock location detected; it is not used as a real boat position.")
    "endpoint"->os.t("请输入服务器地址和 1–65535 范围内的端口。","Enter a server address and a port between 1 and 65535.")
    "network"->os.t("无法读取服务器，3 秒后自动重连。","Connection lost. Retrying every 3 seconds.")
    "loop"->os.t("输入指向了自己的共享服务，请更换地址或端口。","Input points to this device's sharing service. Change the address or port.")
    "port"->os.t("共享端口应在 1024–65535 范围内。","Sharing port must be between 1024 and 65535.")
    "server"->os.t("无法启动共享服务，端口可能被占用。","Could not start sharing. The port may be in use.")
    "sentence"->os.t("只可发送一行有效 NMEA 语句，请检查校验和。","Send one valid NMEA sentence. Check the checksum.")
    "send"->os.t("发送失败，请先建立 TCP 输入连接。","Send failed. Connect a TCP input first.")
    "sent"->os.t("已写入 TCP 连接","Written to the TCP connection")
    else->os.t("服务暂时不可用","Service unavailable")
}

@Composable fun DataScreen(os:OsStore,service:(String,String?)->Unit) {
    val data by os.hub.state.collectAsState(); val now=rememberClock(); val c=LocalMetro.current
    val fix=data.fix(os.positionSource)
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("船舶数据","boat data"))
        Pivot(listOf(os.t("此刻","now"),os.t("来源","sources"))) { page ->
            PageBody {
                if(page==0) {
                    Label(if(fix?.fresh(now)==true) os.t("船位正在更新","position is live") else os.t("等待可信船位","waiting for a fresh position"),18,c.accent)
                    Label("${decimal(fix?.takeIf { it.fresh(now) }?.freshSpeed(now))} kn",54)
                    Label(os.t("对地航速","speed over ground"),15,c.muted)
                    fix?.let {
                        Label(coordinates(it.point),20)
                        Label("${if(it.source=="phone") os.t("手机 GPS","phone GPS") else it.source} · ${age(os,it.elapsed,now)}",14,c.muted)
                        if(it.accuracy!=null) Label(os.t("定位精度 ±${decimal(it.accuracy,0)} m","accuracy ±${decimal(it.accuracy,0)} m"),14,c.muted)
                    }
                    val names=listOf("heading" to os.t("真航向","true heading"),"depth" to os.t("探头下水深","depth below transducer"),"aws" to os.t("视风速","apparent wind"),"awa" to os.t("视风角","apparent wind angle"),"tws" to os.t("真风速","true wind"),"bsp" to os.t("对水航速","speed through water"),"water" to os.t("水温","water temperature"))
                    for((key,name) in names) data.readings[key]?.let { reading ->
                        Column(Modifier.fillMaxWidth().padding(vertical=5.dp)) {
                            Row(verticalAlignment=Alignment.Bottom) { Label(name,20,modifier=Modifier.weight(1f)); Label("${decimal(reading.value)} ${reading.unit}",29,if(reading.fresh(now)) c.fg else c.muted) }
                            Label("${reading.source} · ${age(os,reading.elapsed,now)}${if(!reading.fresh(now)) os.t(" · 已过期"," · stale") else ""}",12,c.muted,Modifier.padding(top=5.dp))
                        }
                    }
                    if(data.readings.isEmpty()) Label(os.t("连接 NMEA 后，这里会显示实际收到的风、水深和航向。","Connect NMEA to see the wind, depth and heading actually received."),17,c.muted)
                    MenuRow(os.t("NMEA 连接","NMEA connection"),connection(os,data,now),"connect") {os.open("nmea")}
                    if(!data.gpsOn && os.positionSource=="phone") MetroButton(os.t("开启手机定位","enable phone GPS"),{service("gpsOn",null)},primary=true)
                    data.message?.let { Label(message(os,it),16,c.muted) }
                } else {
                    Label(os.t("谁提供船位","where position comes from"),29)
                    for((id,title) in listOf("phone" to os.t("手机 GPS","phone GPS"),"nmea" to os.t("NMEA 服务器","NMEA server"))) {
                        val candidate=data.fix(id)
                        Toggle(title,os.positionSource==id,
                            if(candidate!=null) age(os,candidate.elapsed,now) else os.t("尚无数据","no data yet")) {os.positionSource=id;os.save()}
                    }
                    Label(os.t("来源由你选择。连接丢失时显示过期状态，不会暗中换用另一来源。","You choose the source. If it disappears, the position becomes stale until that source returns."),17,c.muted)
                    Toggle(os.t("手机定位服务","phone location service"),data.gpsOn) {service(if(it) "gpsOn" else "gpsOff",null)}
                    MenuRow("NMEA",connection(os,data,now),"connect") {os.open("nmea")}
                    Label(os.t("海图、航线和共享服务订阅同一份数据，每个数值保留来源与接收时间。","Chart, routes and sharing subscribe to the same data. Each reading retains its source and receive time."),16,c.muted)
                }
            }
        }
    }
}

@Composable fun NmeaScreen(os:OsStore,service:(String,String?)->Unit) {
    val data by os.hub.state.collectAsState(); val now=rememberClock(); val c=LocalMetro.current
    var outgoing by remember { mutableStateOf("") }
    var sendConfirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,"NMEA")
        Pivot(listOf(os.t("连接","connect"),os.t("共享","share"),os.t("原始数据","raw"))) { page ->
            PageBody {
                when(page) {
                    0 -> {
                        Label(connection(os,data,now),29,if(data.connection=="live" && now-data.lastRx<=10000) c.accent else c.fg)
                        if(data.endpoint.isNotEmpty()) Label(data.endpoint,14,c.muted)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            for(protocol in listOf("TCP","UDP")) MetroButton(protocol,{os.nmeaProtocol=protocol;os.save()},Modifier.weight(1f),primary=os.nmeaProtocol==protocol)
                        }
                        if(os.nmeaProtocol=="TCP") Field(os.t("服务器地址","server address"),os.nmeaHost,{os.nmeaHost=it})
                        Field(if(os.nmeaProtocol=="TCP") os.t("服务器端口","server port") else os.t("本机监听端口","listen on port"),os.nmeaPort,{os.nmeaPort=it.take(5)},number=true)
                        MetroButton(os.t("保存并连接","save & connect"),{os.save();service("connect",null)},primary=true)
                        if(data.connection!="off") MetroButton(os.t("断开连接","disconnect"),{service("disconnect",null)})
                        if(data.endpoint.startsWith("TCP")) Toggle(os.t("向服务器输出手机船位","send phone position to server"),data.upstreamPublishing,
                            os.t("通过当前 TCP 连接每秒发送新鲜手机 GPS；先在船舶数据中开启定位。","Send fresh phone GPS once a second over this TCP connection. Enable phone location in boat data first.")) {service(if(it) "upstreamOn" else "upstreamOff",null)}
                        Label(os.t("${data.received} 条校验通过 · ${data.rejected} 条拒绝","${data.received} checksum accepted · ${data.rejected} rejected"),14,c.muted)
                        Label(os.t("TCP 可双向通信；UDP 在此端口接收。只有收到有效语句才显示正在接收。","TCP supports bidirectional communication. UDP listens on this device. Receiving means valid sentences have arrived."),16,c.muted)
                    }
                    1 -> {
                        Label(os.t("让船上的设备\n共享同一份数据","one source,\nshared on board"),33)
                        Label(os.t("开启本地 TCP 服务，将所选船位及新鲜的仪表数据生成 NMEA。仅在可信的船内网络开启。","Start a local TCP server to publish the selected position and fresh instrument readings as NMEA. Use a trusted onboard network."),17,c.muted)
                        Field(os.t("共享端口","sharing port"),os.serverPort,{os.serverPort=it.take(5)},number=true)
                        Toggle(os.t("NMEA 共享服务","NMEA sharing service"),data.server in listOf("running","starting")) {os.save();service(if(it) "shareOn" else "shareOff",null)}
                        Label(when(data.server) {"running"->os.t("正在监听 · ${data.clients} 台设备","listening · ${data.clients} clients");"starting"->os.t("正在启动","starting");"error"->os.t("启动失败","could not start");else->os.t("已关闭","off")},23,c.accent)
                        if(data.server=="running") {
                            SelectionContainer { Column { data.addresses.forEach { Label("$it:${data.serverPort}",25) } } }
                            if(data.addresses.isEmpty()) Label(os.t("尚无局域网地址，请连接船上 Wi-Fi。","No LAN address. Connect to the boat's Wi-Fi."),16,c.muted)
                            Label(os.t("已发送 ${data.transmitted} 条语句","${data.transmitted} sentences sent"),14,c.muted)
                        }
                        MenuRow(os.t("船位来源","position source"),if(os.positionSource=="phone") "GPS" else "NMEA") {os.open("data")}
                        Label(os.t("过期数据停止发送。没有 NMEA 输入时，手机 GPS 也可独立提供船位；不会生成缺失的水深或风。","Stale data stops transmitting. Phone GPS can provide position without an NMEA input. Missing depth and wind stay missing."),16,c.muted)
                    }
                    else -> {
                        Label(os.t("最近收到","recently received"),27)
                        SelectionContainer {
                            Column(Modifier.fillMaxWidth().heightIn(max=280.dp).background(c.panel).verticalScroll(rememberScrollState()).padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                if(data.raw.isEmpty()) Label(os.t("尚未收到语句","no sentences received"),16,c.muted)
                                data.raw.takeLast(40).reversed().forEach { Label(it,12) }
                            }
                        }
                        Label(os.t("发送到输入服务器","send to input server"),25)
                        Field(os.t("单条 NMEA 语句","one NMEA sentence"),outgoing,{outgoing=it.take(1024)},multiline=true)
                        Label(os.t("通过当前 TCP 连接发送。可省略 $ 和校验和。发送前会再次显示原文。","Uses the current TCP connection. You can omit $ and the checksum. Review the text before sending."),15,c.muted)
                        MetroButton(os.t("查看并发送","review & send"),{sendConfirm=true},enabled=outgoing.isNotBlank() && data.endpoint.startsWith("TCP") && data.connection in listOf("waiting","live"))
                        if(data.sentToInput>0) Label(os.t("已发送 ${data.sentToInput} 条","${data.sentToInput} sent"),14,c.muted)
                    }
                }
                data.message?.let { Label(message(os,it),16,c.muted) }
            }
        }
    }
    if(sendConfirm) ConfirmDialog(os,os.t("发送到 ${data.endpoint}？\n\n$outgoing","Send to ${data.endpoint}?\n\n$outgoing"),{sendConfirm=false}) {service("send",outgoing);sendConfirm=false}
}
