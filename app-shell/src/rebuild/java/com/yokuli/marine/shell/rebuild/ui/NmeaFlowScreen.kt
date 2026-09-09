package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yokuli.anchorwatch.MainUiState
import com.yokuli.anchorwatch.data.nmea.output.NmeaTxConnectionState
import com.yokuli.anchorwatch.data.nmea.output.NmeaTxStatus
import com.yokuli.anchorwatch.data.sharing.NmeaSharingStatus
import com.yokuli.anchorwatch.data.sharing.SharingServerState
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.marine.shell.rebuild.OsStore
import kotlinx.coroutines.delay

/** A view of the existing marine engine. Reading or pausing this screen never owns a transport. */
@Composable fun NmeaScreen(os: OsStore, service: (String, String?) -> Unit) {
    val marine = os.marine
    if (marine == null) {
        Column { PageHeader(os, "nmea"); Label(os.t("正在准备连接…", "Preparing connections…"), modifier = Modifier.padding(22.dp)) }
        return
    }
    val state by marine.vm.ui.collectAsState()
    var child by rememberSaveable { mutableStateOf<String?>(null) }
    var frozenTraffic by remember { mutableStateOf<TrafficSnapshot?>(null) }
    var trafficDirection by rememberSaveable { mutableIntStateOf(0) }
    var trafficFilter by rememberSaveable { mutableStateOf("") }
    if (child != null) {
        MarineAppScreen(os, child!!, onBack = { child = null })
        return
    }
    LaunchedEffect(marine.vm) {
        marine.vm.navigationRequests.collect { request ->
            when (request) {
                0 -> os.open("anchor")
                1 -> os.open("instruments")
                2 -> when (marine.vm.ui.value.dataSection) {
                    1 -> child = "nmea"
                    2 -> child = "output"
                    3 -> os.open("sonar")
                    else -> os.open("sources")
                }
                else -> os.open("marine-settings")
            }
        }
    }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = SystemClock.elapsedRealtime() } }
    Column(Modifier.fillMaxSize().testTag("nmea-flow")) {
        PageHeader(os, "nmea")
        Pivot(listOf(os.t("实况", "flow"), os.t("语句", "traffic"), os.t("客户端", "clients"))) { page ->
            when (page) {
                0 -> NmeaFlow(os, state, now, { child = it })
                1 -> NmeaTraffic(os, frozenTraffic ?: state.trafficSnapshot(), frozenTraffic != null,
                    trafficDirection, { trafficDirection = it }, trafficFilter, { trafficFilter = it },
                    { frozenTraffic = if (frozenTraffic == null) state.trafficSnapshot() else null })
                else -> NmeaClients(os, state.nmeaSharing, now, { child = "output" }, { service("shareOff", null) })
            }
        }
    }
}

@Composable private fun NmeaFlow(os: OsStore, state: MainUiState, now: Long, open: (String) -> Unit) {
    val c = LocalMetro.current
    val rx = state.diagnostics
    val tx = state.phonePositionOutputStatus
    val server = state.nmeaSharing
    val connected = state.connection in setOf(NmeaConnectionState.CONNECTED, NmeaConnectionState.CONNECTED_NO_DATA, NmeaConnectionState.CONNECTED_NO_FIX, NmeaConnectionState.STALE)
    val profile = state.settings.profile
    val freshInput = connected && rx.lastPacketElapsed?.let { now - it <= profile.noDataTimeoutSeconds * 1000L } == true
    val observations = with(state.vesselData) { listOf(position, sogKnots, cogTrueDegrees, headingTrueDegrees, depthMeters, speedThroughWaterKnots, apparentWind.speedKnots, trueWind.speedKnots, pressureHpa, attitude) }
    val freshFields = observations.count { it.value != null && it.freshness == VesselDataFreshness.FRESH }
    PageBody {
        Column(Modifier.fillMaxWidth().clickable { open("nmea") }) {
            Label(os.t("船上 → 本机", "boat → this device"), 17, c.muted)
            Label(inputStatus(os, state.connection, freshInput), 34, if (freshInput) c.accent else c.fg, Modifier.padding(top = 8.dp))
            Label(if (profile.host.isBlank()) os.t("连接你的船载网关", "connect your boat gateway") else "${profile.protocol} · ${profile.host}:${profile.port}", 16, c.muted, Modifier.padding(top = 6.dp))
            if (rx.lastPacketElapsed != null) Label(os.t("最近收到：", "last received: ") + nmeaAge(os, rx.lastPacketElapsed, now), 14, c.muted, Modifier.padding(top = 6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            NmeaCount(rx.validSentences.toString(), os.t("有效语句", "valid sentences"), Modifier.weight(1f))
            NmeaCount(rx.invalidSentences.toString(), os.t("未解析语句", "unparsed sentences"), Modifier.weight(1f))
        }
        Label("↓", 27, c.accent)
        Column {
            Label(os.t("应用共享的数据", "data for your apps"), 27)
            Label(os.t("$freshFields 项常用数据新鲜可用", "$freshFields common fields are fresh"), 18, c.accent, Modifier.padding(top = 6.dp))
            Label(os.t("海图、仪表与记录订阅同一份带来源的数据。", "Chart, instruments and recording use the same data with its source attached."), 16, c.muted, Modifier.padding(top = 6.dp))
        }
        MenuRow(os.t("查看船位与数据来源", "see position & sources"), icon = "data") { os.open("data") }
        Label(os.t("手机 / App → 其他设备", "phone / app → other devices"), 22)
        Label(os.t("分别发布已选择的手机与 App 数据。船载输入供本机应用使用。", "Each output publishes your selected phone and app data. Boat input supplies the apps on this device."), 16, c.muted)
        NmeaOutputRow(os.t("向船网发送", "send to the boat"), txStatus(os, tx, now), os.t("已写入 ${tx.writtenSentences} 句", "${tx.writtenSentences} sentences written"), nmeaAge(os, tx.lastWriteElapsed, now)) { open("output") }
        NmeaOutputRow(os.t("本机 NMEA 服务", "local NMEA service"), serverStatus(os, server, now), os.t("${server.clientCount} 个客户端 · 已写入 ${server.sentSentences} 句", "${server.clientCount} clients · ${server.sentSentences} sentences written"), nmeaAge(os, server.lastOutputElapsed, now)) { open("output") }
        Label(os.t("写入计数来自实际网络发送完成；接收设备如何使用数据，请在接收端查看。", "Write counts come from completed network writes. Check the receiving device to see how it uses the data."), 14, c.muted)
        MetroButton(os.t("连接设置", "connection settings"), { open("nmea") }, primary = !connected)
        MetroButton(os.t("输出与服务设置", "output & service settings"), { open("output") })
    }
}

@Composable private fun NmeaCount(value: String, label: String, modifier: Modifier) {
    Column(modifier) { Label(value, 33); Label(label, 14, LocalMetro.current.muted, Modifier.padding(top = 3.dp)) }
}

@Composable private fun NmeaOutputRow(title: String, status: String, count: String, time: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(title, 24, modifier = Modifier.weight(1f))
            Glyph("next", Modifier.size(18.dp), LocalMetro.current.muted)
        }
        Label(status, 20, LocalMetro.current.accent, Modifier.padding(top = 7.dp))
        Label(count, 15, LocalMetro.current.muted, Modifier.padding(top = 5.dp))
        Label(time, 13, LocalMetro.current.muted, Modifier.padding(top = 4.dp))
    }
}

private data class TrafficSnapshot(val input: List<String>, val boat: List<String>, val clients: List<String>)
private fun MainUiState.trafficSnapshot() = TrafficSnapshot(diagnostics.raw.toList(), phonePositionOutputStatus.recentTx.toList(), nmeaSharing.recentWritten.toList())

@Composable private fun NmeaTraffic(os: OsStore, current: TrafficSnapshot, paused: Boolean,
    direction: Int, onDirection: (Int) -> Unit, filter: String, onFilter: (String) -> Unit,
    togglePause: () -> Unit) {
    val source = when (direction) { 0 -> current.input; 1 -> current.boat; else -> current.clients }
    val lines = source.asReversed().filter { it.contains(filter.trim(), ignoreCase = true) }
    val c = LocalMetro.current
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(os.t("接收", "received"), os.t("写向船网", "to boat"), os.t("写向客户端", "to clients")).forEachIndexed { i, title ->
                Label(title, 20, if (i == direction) c.accent else c.muted, Modifier.clickable { onDirection(i) }.padding(vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Field(os.t("筛选语句，例如 RMC、MWV 或 talker ID", "filter, e.g. RMC, MWV or talker ID"), filter, { onFilter(it.take(80)) })
        Spacer(Modifier.height(12.dp))
        MetroButton(if (!paused) os.t("暂停查看", "pause reading") else os.t("返回实时", "return to live"), togglePause, primary = paused)
        Label(if (paused) os.t("画面已暂停，连接和记录仍在继续。", "Reading is paused. Connections and recording continue.") else os.t("最新在上 · 长按可选择和复制", "newest first · hold to select and copy"), 13, c.muted, Modifier.padding(vertical = 10.dp))
        if (lines.isEmpty()) {
            Label(when { filter.isNotBlank() -> os.t("没有匹配的语句。", "No matching sentences."); direction == 0 -> os.t("收到的原始语句会出现在这里。", "Received raw sentences appear here."); else -> os.t("完成网络写入的语句会出现在这里。", "Sentences appear here after a completed network write.") }, 21, c.muted, Modifier.padding(top = 20.dp))
        } else LazyColumn(Modifier.weight(1f).testTag("nmea-traffic-lines"), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(lines) { _, line ->
                SelectionContainer {
                    BasicText(line.trim(), Modifier.fillMaxWidth().background(c.panel).padding(12.dp), style = TextStyle(color = c.fg, fontSize = 14.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp))
                }
            }
        }
    }
}

@Composable private fun NmeaClients(os: OsStore, server: NmeaSharingStatus, now: Long, configure: () -> Unit, stop: () -> Unit) {
    PageBody {
        Label(serverStatus(os, server, now), 33, LocalMetro.current.accent)
        Label(os.t("让另一台手机、平板或仪表连接到这里。", "Let another phone, tablet or instrument connect here."), 20)
        if (server.state == SharingServerState.RUNNING) {
            Label(os.t("TCP 端口 ${server.port}", "TCP port ${server.port}"), 26)
            SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                server.addresses.forEach { Label(it, 20) }
            } }
            Label(os.t("在同一网络的接收端选择 TCP 客户端，填写上面的本机地址与端口。", "On a receiver on the same network, choose TCP client and enter this device’s address and port."), 16, LocalMetro.current.muted)
            if (server.clients.isEmpty()) Label(os.t("正在监听，等待第一个客户端。", "Listening for the first client."), 23)
            server.clients.forEach { client ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Label(client.address, 23)
                    Label(os.t("已写入 ${client.sentSentences} 句", "${client.sentSentences} sentences written"), 16, LocalMetro.current.muted, Modifier.padding(top = 6.dp))
                }
            }
            if (server.droppedSlowClients > 0) Label(os.t("${server.droppedSlowClients} 次慢速客户端连接已断开，其他连接继续运行。", "${server.droppedSlowClients} slow client connections were closed; other connections continue."), 16, LocalMetro.current.muted)
            MetroButton(os.t("停止本机服务", "stop local service"), stop)
        } else Label(os.t("先选择要分享的手机 / App 数据，再启动本机服务。", "Choose the phone and app data to share, then start the local service."), 17, LocalMetro.current.muted)
        MetroButton(os.t("管理服务与分享内容", "manage service & shared data"), configure, primary = server.state != SharingServerState.RUNNING)
    }
}

private fun inputStatus(os: OsStore, state: NmeaConnectionState, fresh: Boolean) = when (state) {
    NmeaConnectionState.DISCONNECTED -> os.t("尚未连接", "not connected")
    NmeaConnectionState.CONNECTING -> os.t("正在连接", "connecting")
    NmeaConnectionState.RECONNECTING -> os.t("正在重连", "reconnecting")
    NmeaConnectionState.ERROR -> os.t("连接需要处理", "connection needs attention")
    NmeaConnectionState.CONNECTED_NO_DATA -> os.t("已连接，等待语句", "connected, awaiting sentences")
    else -> if (fresh) os.t("正在接收", "receiving") else os.t("已连接，数据已过期", "connected, data stale")
}

private fun txStatus(os: OsStore, tx: NmeaTxStatus, now: Long) = when (tx.connectionState) {
    NmeaTxConnectionState.OFF -> os.t("已关闭", "off")
    NmeaTxConnectionState.STOPPING -> os.t("正在停止", "stopping")
    NmeaTxConnectionState.CONNECTING -> os.t("正在连接", "connecting")
    NmeaTxConnectionState.ERROR -> os.t("输出需要处理", "output needs attention")
    NmeaTxConnectionState.DISCONNECTED -> os.t("等待输出连接", "waiting for an output connection")
    NmeaTxConnectionState.CONNECTED -> if (tx.lastWriteElapsed?.let { now - it <= 5000 } == true) os.t("正在写入", "writing") else os.t("连接就绪，等待可用数据", "connected, waiting for data")
}

private fun serverStatus(os: OsStore, server: NmeaSharingStatus, now: Long) = when (server.state) {
    SharingServerState.STOPPED -> os.t("已关闭", "off")
    SharingServerState.STARTING -> os.t("正在启动", "starting")
    SharingServerState.ERROR -> os.t("服务需要处理", "service needs attention")
    SharingServerState.RUNNING -> when {
        server.clientCount == 0 -> os.t("正在监听，暂无客户端", "listening, no clients")
        server.lastOutputElapsed?.let { now - it <= 5000 } == true -> os.t("正在向客户端写入", "writing to clients")
        else -> os.t("客户端已连接，等待数据", "clients connected, waiting for data")
    }
}

private fun nmeaAge(os: OsStore, elapsed: Long?, now: Long): String {
    if (elapsed == null) return os.t("尚无记录", "no activity yet")
    val seconds = ((now - elapsed).coerceAtLeast(0) / 1000)
    return when {
        seconds < 2 -> os.t("刚刚", "just now")
        seconds < 60 -> os.t("$seconds 秒前", "$seconds s ago")
        seconds < 3600 -> os.t("${seconds / 60} 分钟前", "${seconds / 60} min ago")
        else -> os.t("${seconds / 3600} 小时前", "${seconds / 3600} h ago")
    }
}
