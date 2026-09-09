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
                    if(os.positionSource=="none") MetroButton(os.t("开启手机定位","enable phone GPS"),{service("gpsOn",null)},primary=true)
                    data.message?.let { Label(message(os,it),16,c.muted) }
                } else {
                    Label(os.t("谁提供船位","where position comes from"),29)
                    val nmeaConnected=data.connection in listOf("waiting","live")
                    val sourceLocked=os.marine?.vm?.ui?.collectAsState()?.value?.active?.paused==false
                    Toggle(os.t("手机 GPS","phone GPS"),os.positionSource=="phone",
                        if(os.positionSource=="nmea") os.t("先关闭 NMEA 船位","turn NMEA position off first") else os.t("开启即启动定位，关闭即停止","starts and stops phone location"),
                        enabled=!sourceLocked && os.positionSource in listOf("none","phone")) {
                        service(if(it) "gpsOn" else "gpsOff",null)
                    }
                    Toggle(os.t("NMEA 船位","NMEA position"),os.positionSource=="nmea",
                        when {os.positionSource=="phone"->os.t("先关闭手机 GPS","turn phone GPS off first")
                            !nmeaConnected->os.t("请先连接 NMEA","connect NMEA first")
                            data.nmea==null->os.t("已连接，等待有效船位","connected, awaiting a valid position")
                            else->age(os,data.nmea!!.elapsed,now)},
                        enabled=!sourceLocked && (os.positionSource=="nmea" || (os.positionSource=="none" && nmeaConnected))) {
                        service(if(it) "sourceNmea" else "sourceOff",null)
                    }
                    Label(if(sourceLocked) os.t("锚警报正在值守；暂停后可以更改船位来源。","Pause the active anchor watch before changing its position source.") else
                        os.t("两项都可以关闭。NMEA 连接可继续提供水深、风与仪表数据；船位不会自动切换来源。","Both can be off. NMEA may still provide depth, wind and instruments. Position never changes source automatically."),17,c.muted)
                    MenuRow(os.t("更多数据能力","more data capabilities"),os.t("自定义字段、手机传感器、全局 GPS 代理与演示","custom fields, phone sensors, global GPS proxy & demo"),"data") {os.open("sources")}
                    MenuRow("NMEA",connection(os,data,now),"connect") {os.open("nmea")}
                    Label(os.t("海图、航线和共享服务订阅同一份数据，每个数值保留来源与接收时间。","Chart, routes and sharing subscribe to the same data. Each reading retains its source and receive time."),16,c.muted)
                }
            }
        }
    }
}

@Composable fun NmeaScreen(os:OsStore,service:(String,String?)->Unit) {
    MarineAppScreen(os,"nmea")
}
