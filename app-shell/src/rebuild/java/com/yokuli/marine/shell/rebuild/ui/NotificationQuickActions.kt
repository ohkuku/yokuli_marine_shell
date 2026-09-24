package com.yokuli.marine.shell.rebuild.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.data.nmea.NmeaConnectionSnapshot
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.location.PhoneLocationPhase
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.core.design.W10ToggleIndicator
import com.yokuli.marine.core.design.W10ProgressRing
import androidx.compose.ui.text.font.FontWeight
import com.yokuli.marine.core.design.LocalWpTextScale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 有限网格交给中心的唯一纵向列表滚动；偏好结果由 Shell 保存，开关没有局部真值。 */
@Composable internal fun NotificationQuickActions(
    os: OsStore,
    onOpenDetail: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenDestination: (String) -> Unit,
) {
    val c = LocalMetro.current
    val writes by os.shell.preferenceCommands.collectAsState()
    val night = writes.lastOrNull { it.key == "notification.night" }
    val awake = writes.lastOrNull { it.key == "notification.awake" }
    val position = rememberNotificationPosition(os)
    val connections = rememberNotificationConnections(os)
    fun preferenceText(value: Boolean, result: SystemPreferenceCommand?): String = when(result?.status) {
        SystemPreferenceStatus.PENDING -> os.t("正在保存", "saving")
        SystemPreferenceStatus.FAILED -> os.t("未保存 · 点击重试", "not saved · retry")
        else -> if(value) os.t("开启", "on") else os.t("关闭", "off")
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scale = LocalDensity.current.fontScale * LocalWpTextScale.current
            val columns = when {
                maxWidth < 260.dp || scale >= 1.75f && maxWidth < 600.dp -> 1
                maxWidth >= 600.dp && scale < 1.5f -> 4
                else -> 2
            }
            // 高度和文字槽位属于整组网格；大字体不会只把某一格撑高。
            val titleHeight = (34f * scale).dp
            val statusHeight = (30f * scale).dp
            val height = 48.dp + titleHeight + statusHeight
            val entries = listOf(
                QuickItem(if(os.light) "sun" else "moon", os.t("夜间显示", "night display"), preferenceText(!os.light, night), !os.light,
                    pending = night?.status == SystemPreferenceStatus.PENDING) {
                    os.shell.requestSystemPreferences("notification.night") {
                        it.copy(themeModeName = if(it.themeModeName == "LIGHT") "DARK" else "LIGHT")
                    }
                },
                QuickItem("awake", os.t("屏幕常亮", "keep screen awake"), preferenceText(os.keepAwake, awake), os.keepAwake,
                    pending = awake?.status == SystemPreferenceStatus.PENDING) {
                    os.shell.requestSystemPreferences("notification.awake") { preferences ->
                        val wasOn = preferences.appPreferenceValues["preferences.display.keep_awake"] != "b:0"
                        preferences.copy(appPreferenceValues = preferences.appPreferenceValues +
                            ("preferences.display.keep_awake" to if(wasOn) "b:0" else "b:1"))
                    }
                },
                QuickItem("locate", os.t("船位来源", "position source"), positionStatus(os, position)) { onOpenDetail("position") },
                QuickItem("connect", os.t("船舶连接", "boat connections"), connectionSummary(os, connections)) { onOpenDetail("connections") },
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item -> QuickCard(item, Modifier.weight(1f).height(height), titleHeight, statusHeight) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Label(if(expanded) os.t("收起快捷项", "fewer shortcuts") else os.t("更多快捷项", "more shortcuts"),
            16, c.accentText, Modifier.fillMaxWidth().clickable(role = Role.Button) { onExpandedChange(!expanded) }
                .heightIn(min = 48.dp).padding(vertical = 14.dp))
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                MenuRow(os.t("手机安装与船艏", "phone mounting & bow"), os.t("固定方向、校准与微调", "mounting, calibration and fine adjustment"), "next") { onOpenDestination("data_center:mount") }
                MenuRow(os.t("数据共享", "data sharing"), os.t("选择这台手机对外提供的数据", "choose the data this phone publishes"), "next") { onOpenDestination("local_nmea") }
                MenuRow(os.t("声音与警报", "sounds & alarms"), os.t("现有警报声音及提醒设置", "alarm sound and reminder settings"), "next") { onOpenDestination("settings:sound") }
            }
        }
        if(night?.status == SystemPreferenceStatus.FAILED || awake?.status == SystemPreferenceStatus.FAILED)
            Label(os.t("显示偏好未保存，当前开关仍显示已保存的状态。点对应开关可重试。", "Display preference was not saved. The switches still show the saved state. Tap the affected switch to retry."), 14, c.muted)
    }
}

private data class QuickItem(val icon: String, val title: String, val detail: String,
    val checked: Boolean? = null, val pending: Boolean = false, val action: () -> Unit)

@Composable private fun QuickCard(item: QuickItem, modifier: Modifier, titleHeight: androidx.compose.ui.unit.Dp, statusHeight: androidx.compose.ui.unit.Dp) {
    val c = LocalMetro.current
    val control = if(item.checked != null) Modifier.toggleable(item.checked, enabled = !item.pending, role = Role.Switch) { item.action() }
        else Modifier.clickable(role = Role.Button, onClick = item.action)
    Column(modifier.background(c.controlFill).border(1.dp, if(item.checked == true) c.accent else c.controlStroke)
        .semantics { stateDescription = item.detail }.then(control).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            QuickGlyph(item.icon, if(item.checked == true) c.accentText else c.fg)
            Spacer(Modifier.weight(1f))
            if(item.pending) W10ProgressRing(true, Modifier.size(20.dp))
            else if(item.checked == null) Glyph("chevron_right", Modifier.size(16.dp), c.muted)
            else W10ToggleIndicator(item.checked, modifier=Modifier.size(32.dp,16.dp))
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(titleHeight), contentAlignment = Alignment.CenterStart) { Label(item.title,15,maxLines=2,weight=FontWeight.SemiBold) }
        Box(Modifier.fillMaxWidth().height(statusHeight), contentAlignment = Alignment.TopStart) { Label(item.detail,12,c.muted,maxLines=2) }
    }
}

private data class NotificationPosition(
    val selected: GpsDataSource,
    val observation: VesselObservation<VesselPosition>,
    val locked: Boolean,
)

@Composable private fun rememberNotificationPosition(os: OsStore): NotificationPosition? {
    val services = os.marine?.services ?: return null
    return remember(services) { services.state.map { NotificationPosition(it.settings.gpsDataSource, it.vesselData.position,
        it.acceptedPosition.lockedSessionId != null) }.distinctUntilChanged() }.collectAsState(
            NotificationPosition(services.state.value.settings.gpsDataSource, services.state.value.vesselData.position,
                services.state.value.acceptedPosition.lockedSessionId != null)).value
}

private fun positionSourceName(os: OsStore, selected: GpsDataSource?) = when(selected) {
    GpsDataSource.SYSTEM -> os.t("手机", "phone")
    GpsDataSource.NMEA -> os.t("船载", "aboard")
    GpsDataSource.DEMO -> os.t("演示", "demo")
    else -> os.t("未选用", "not selected")
}
private fun positionStatus(os: OsStore, value: NotificationPosition?): String {
    if(value == null) return os.t("正在连接运行时", "connecting to runtime")
    val source = positionSourceName(os, value.selected)
    if(value.selected !in setOf(GpsDataSource.SYSTEM, GpsDataSource.NMEA, GpsDataSource.DEMO)) return source
    val observation = value.observation
    val condition = when {
        observation.value == null -> os.t("等待读数", "awaiting reading")
        observation.quality == VesselDataQuality.UNKNOWN -> os.t("质量未确认", "quality unconfirmed")
        observation.quality == VesselDataQuality.DEGRADED -> os.t("质量受限", "limited quality")
        observation.freshness == VesselDataFreshness.FRESH -> os.t("实时", "current")
        observation.freshness == VesselDataFreshness.HELD -> os.t("保留读数", "last reading")
        else -> os.t("数据过期", "data aged")
    }
    return "$source · $condition"
}

@Composable private fun rememberNotificationConnections(os: OsStore): List<NmeaConnectionSnapshot> {
    val services = os.marine?.services ?: return emptyList()
    return remember(services) { services.network.connections.distinctUntilChanged { previous, next ->
        previous.size == next.size && previous.zip(next).all { (a, b) ->
            a.spec == b.spec && a.requested == b.requested && a.state == b.state && a.error == b.error &&
                a.diagnostics.lastPacketElapsed?.div(1_000) == b.diagnostics.lastPacketElapsed?.div(1_000)
        }
    } }.collectAsState(services.network.connections.value).value
}

private fun connectionSummary(os: OsStore, connections: List<NmeaConnectionSnapshot>): String {
    if(connections.isEmpty()) return os.t("未配置", "not configured")
    val problems = connections.count { it.requested && (it.state in setOf(NmeaConnectionState.ERROR, NmeaConnectionState.DISCONNECTED, NmeaConnectionState.RECONNECTING) || it.error != null) }
    return os.t("${connections.size} 条连接", "${connections.size} connections") +
        if(problems > 0) os.t(" · $problems 条异常", " · $problems need attention") else ""
}
private fun connectionState(os: OsStore, value: NmeaConnectionSnapshot): String = when {
    !value.requested -> os.t("已停止", "stopped")
    value.error != null || value.state == NmeaConnectionState.ERROR -> os.t("连接异常", "connection error")
    value.state in setOf(NmeaConnectionState.CONNECTING, NmeaConnectionState.RECONNECTING, NmeaConnectionState.DISCONNECTED) -> os.t("等待连接", "awaiting connection")
    !value.spec.receive -> os.t("输出就绪", "output ready")
    value.state == NmeaConnectionState.CONNECTED_NO_DATA -> os.t("等待数据", "awaiting data")
    value.state == NmeaConnectionState.CONNECTED_NO_FIX -> os.t("已连接 · 船位未确认", "connected · position unconfirmed")
    value.state == NmeaConnectionState.STALE -> os.t("已连接 · 等待更新", "connected · awaiting update")
    else -> os.t("已连接", "connected")
}

/** 子详情不建立第二份配置；所有外部入口交给 Shell 保留通知中心的来路和滚动位置。 */
@Composable internal fun NotificationQuickDetail(os: OsStore, detail: String, onOpenDestination: (String) -> Unit, onClose: () -> Unit) {
    val c = LocalMetro.current
    val services = os.marine?.services
    val position = rememberNotificationPosition(os)
    val connections = rememberNotificationConnections(os)
    val now = rememberMarineClock()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Label(os.t("返回通知中心", "back to notifications"), 16, c.accentText,
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClose).heightIn(min = 48.dp).padding(vertical = 14.dp))
        if(detail == "position") {
            AppSection(os.t("船位来源", "Position source"))
            Label(positionStatus(os, position), 19)
            val observation = position?.observation
            observation?.value?.let { Label(os.formatCoordinates(GeoPoint(it.latitude, it.longitude)), 17) }
            Label(os.t("最近读数 · ", "last reading · ") + notificationReadingAge(os, observation?.receivedElapsedRealtime, now), 15, c.muted)
            if(position?.locked == true) Label(os.t("守锚正在使用此来源。查看不会暂停保护；如需换源，先在守锚中明确暂停监控。", "Anchor watch is using this source. Viewing it does not pause protection. To change source, explicitly pause monitoring in Anchor Watch first."), 15, c.muted)
            val phoneStatus = services?.sources?.phoneLocationStatus?.collectAsState()?.value
            if(position?.selected == GpsDataSource.SYSTEM) when(phoneStatus?.phase) {
                PhoneLocationPhase.PERMISSION_REQUIRED -> Label(os.t("缺少定位权限；请进入来源设置恢复权限。", "Location permission is missing; restore it in source settings."), 15, c.accent)
                PhoneLocationPhase.PROVIDER_DISABLED -> Label(os.t("Android 定位已关闭；请进入来源设置处理。", "Android location is off; open source settings to restore it."), 15, c.accent)
                PhoneLocationPhase.ERROR -> Label(os.t("手机采集遇到问题：", "Phone acquisition issue: ") + phoneStatus.error.orEmpty(), 15, c.accent)
                else -> Unit
            }
            if(observation?.conflict != null) Label(os.t("当前候选船位存在冲突，不能将旧读数视为实时船位。", "Position candidates conflict. The last reading is not a current position."), 15, c.accent)
            MenuRow(os.t("管理船位来源", "manage position source"), os.t("数据中心保留全船唯一的来源选择", "Data Center owns the source used across the vessel"), "next") { onOpenDestination("data_center:source/POSITION") }
        } else {
            AppSection(os.t("船舶连接", "Boat connections"))
            Label(connectionSummary(os, connections), 18)
            Label(os.t("传输连接与读数有效性分别判断。查看摘要不会启动任何连接。", "Transport status and reading validity are separate. Viewing this summary starts no connection."), 15, c.muted)
            connections.forEach { connection ->
                MenuRow(connection.spec.name, connectionState(os, connection) + " · " + os.t("最近接收 ", "last received ") +
                    notificationReadingAge(os, connection.diagnostics.lastPacketElapsed, now), "next") {
                    onOpenDestination("nmea:connection:${Uri.encode(connection.spec.id)}")
                }
                connection.error?.let { Label(it, 14, c.muted) }
            }
            MenuRow(os.t("管理船舶连接", "manage boat connections"), os.t("查看、添加或修改收发连接", "view, add or edit receiving and sending connections"), "next") { onOpenDestination("nmea") }
        }
    }
}

internal fun notificationReadingAge(os: OsStore, elapsed: Long?, now: Long): String {
    if(elapsed == null || elapsed <= 0 || now < elapsed) return os.t("尚无", "none yet")
    val seconds = (now - elapsed) / 1_000
    return when {
        seconds < 2 -> os.t("刚刚", "just now")
        seconds < 60 -> os.t("${seconds} 秒前", "$seconds s ago")
        seconds < 3600 -> os.t("${seconds / 60} 分钟前", "${seconds / 60} min ago")
        else -> os.t("${seconds / 3600} 小时前", "${seconds / 3600} h ago")
    }
}

@Composable private fun QuickGlyph(icon: String, color: Color) {
    if (icon !in setOf("sun", "moon", "awake", "mount", "collapse")) { Glyph(icon, Modifier.size(23.dp), color); return }
    Canvas(Modifier.size(24.dp)) {
        val scale = size.width / 24f
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        fun line(x: Float, y: Float, a: Float, b: Float) = drawLine(color, point(x, y), point(a, b), 1.6f * scale)
        when (icon) {
            "sun" -> {
                drawCircle(color, 4f * scale, point(12f, 12f), style = Stroke(1.6f * scale))
                for (angle in 0..7) { val a = angle * Math.PI / 4; line(12f + 7f * kotlin.math.cos(a).toFloat(), 12f + 7f * kotlin.math.sin(a).toFloat(), 12f + 10f * kotlin.math.cos(a).toFloat(), 12f + 10f * kotlin.math.sin(a).toFloat()) }
            }
            "moon" -> {
                val path = Path().apply { moveTo(16f * scale, 2f * scale); cubicTo(5f * scale, 0f, 0f, 15f * scale, 10f * scale, 21f * scale); cubicTo(16f * scale, 24f * scale, 23f * scale, 19f * scale, 23f * scale, 14f * scale); cubicTo(13f * scale, 20f * scale, 7f * scale, 9f * scale, 16f * scale, 2f * scale); close() }
                drawPath(path, color, style = Stroke(1.6f * scale))
            }
            "awake", "mount" -> {
                drawRect(color, point(6f, 2f), Size(12f * scale, 20f * scale), style = Stroke(1.6f * scale))
                if (icon == "awake") { line(9f, 12f, 15f, 12f); line(12f, 9f, 12f, 15f) }
                else { line(2f, 13f, 22f, 10f); line(19f, 7f, 22f, 10f); line(19f, 14f, 22f, 10f) }
            }
            "collapse" -> { line(5f, 15f, 12f, 8f); line(12f, 8f, 19f, 15f) }
        }
    }
}
