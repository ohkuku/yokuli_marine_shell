package com.yokuli.marine.shell.rebuild.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.runtime.contract.ais.*
import kotlin.math.abs

/** 详情先回答「离我多远、正在怎样、要做什么」；报文与来源按需展开，真值仍来自交通服务。 */
@Composable internal fun AisTargetDetail(
    os: OsStore,
    s: TrafficSnapshot,
    mmsi: Int?,
    view: (AisView, Int) -> Unit,
    viewTrack: (Int) -> Unit,
    openInputs: () -> Unit,
) {
    val inputEnabled = com.yokuli.shell.compose.LocalInternalAppInputEnabled.current
    val target = mmsi?.let(s::target)
    val now = rememberMarineClock()
    var aliasEditing by rememberSaveable(mmsi) { mutableStateOf(false) }
    var motionExpanded by rememberSaveable(mmsi) { mutableStateOf(false) }
    var particularsExpanded by rememberSaveable(mmsi) { mutableStateOf(false) }
    var sourcesExpanded by rememberSaveable(mmsi) { mutableStateOf(false) }
    var reportsExpanded by rememberSaveable(mmsi) { mutableStateOf(false) }
    val c = LocalMetro.current

    PageBody {
        if (target == null) {
            AppSection(os.t("等待目标更新", "Waiting for target updates"))
            Label(os.t("暂时没有保留这个目标的报告。接收恢复后会按 MMSI 重新识别。", "Its report is no longer retained. It will be recognised by MMSI when reception returns."), 15, c.muted)
            MenuRow(os.t("查看接收状态", "Check reception")) { openInputs() }
            return@PageBody
        }

        val dynamic = target.dynamic
        val vessel = target.kind in setOf(AisEntityKind.CLASS_A, AisEntityKind.CLASS_B, AisEntityKind.LONG_RANGE)
        val alerts = s.events.filter { it.mmsi == target.mmsi && it.active }
        val attention = alerts.isNotEmpty() || target.riskLevel != AisRiskLevel.NONE || target.distress == AisDistressState.ACTIVE
        val riskColor = if (os.light) Color(0xFFAD2525) else Color(0xFFFF8C86)
        val lastReceived = if (target.cached) os.t("上次启动的记录", "Saved from an earlier session")
            else os.t("最近接收 · ", "Last received · ") + readingAge(os, target.lastMessageElapsed, now)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (attention) {
                Glyph("warning", Modifier.size(20.dp), riskColor)
                Spacer(Modifier.width(8.dp))
            }
            val summary = when {
                alerts.isNotEmpty() -> alerts.map { aisRiskName(os, it.kind) }.distinct().joinToString(" · ")
                target.distress == AisDistressState.ACTIVE -> aisState(os, target)
                target.riskLevel != AisRiskLevel.NONE -> os.t("需要关注", "Needs attention")
                else -> aisState(os, target)
            }
            Label(summary,
                15, if (attention) riskColor else c.fg, Modifier.weight(1f), weight = FontWeight.SemiBold)
        }
        // 接收时间不能替代位置时间：收到静态资料不会让旧船位看起来更新鲜。
        Label(lastReceived, 12, c.muted)
        if (attention && (target.state != AisTargetState.CURRENT || target.cached)) Label(aisState(os, target), 12, c.muted)
        if (target.distress == AisDistressState.UNKNOWN && target.kind in setOf(AisEntityKind.SART, AisEntityKind.MOB, AisEntityKind.EPIRB)) {
            Label(os.t("尚不能判断设备是否激活或正在自检，请核对报告。", "It is not yet clear whether the device is activated or self-testing. Inspect its reports."), 15, riskColor)
        }
        val distance = target.relative.distanceMeters?.let(os::formatDistance)
        val speed = dynamic?.let { report -> report.sogMetersPerSecond?.let {
            (if ("sog_lower_bound" in report.invalidFields) "≥ " else "") + os.formatSpeed(it / .514444)
        } }
        if (distance != null || speed != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (distance != null) AisHeroFact(os.t("距本船", "From your boat"), distance,
                    target.relative.bearingDegrees?.let { os.formatBearing(it) + os.t(" 真方位", " true") }, Modifier.weight(1f))
                if (speed != null) AisHeroFact(os.t("对地航速", "Speed over ground"), speed,
                    dynamic?.navigationStatus?.let { aisNavigationStatus(os, it) }, Modifier.weight(1f))
            }
        } else Label(aisCpaReason(os, target.relative.state), 15, c.muted)
        dynamic?.let {
            Label(os.t("位置报告 · ", "Position report · ") + if (target.cached) os.t("历史记录", "Historical") else readingAge(os, it.receivedElapsed, now), 12, c.muted)
        }
        MetroButton(os.t("在海图查看", "View on Chart"), { view(AisView.CHART, target.mmsi) }, Modifier.fillMaxWidth(), primary = true, enabled = target.position != null)
        Toggle(if (vessel) os.t("关注这艘船", "Follow this vessel") else os.t("关注此目标", "Follow this target"), target.watched,
            if (target.watched) os.t("在关注列表中保留，方便再次找到", "Kept in your followed list") else null) { os.aisCommand(AisCommand.Watch(target.mmsi, it)) }

        // 活动警报始终直接可见；折叠资料和确认阅读均不能代替业务确认或解除风险。
        alerts.forEach { event ->
            Column(Modifier.fillMaxWidth().background(c.subtle).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(aisRiskName(os, event.kind), 15, riskColor, weight = FontWeight.SemiBold)
                if (event.reason == "risk_cannot_be_reconfirmed") Label(os.t("新数据不足，不能确认风险已经解除。", "New data is insufficient to confirm that this risk has cleared."), 15)
                if (event.kind == AisRiskKind.ANCHOR_PROXIMITY) event.distanceMeters?.let { meters ->
                    Label((if (s.preferences.anchorUsesAnchorPoint) os.t("距锚点 · ", "From anchor · ") else os.t("距本船 · ", "From your boat · ")) + os.formatDistance(meters), 15)
                }
                if (event.acknowledged) Label(os.t("已确认，警戒条件仍存在", "Acknowledged; the condition remains"), 12, c.muted)
                else MetroButton(os.t("我已看到", "Acknowledge"), { os.aisCommand(AisCommand.Acknowledge(event.id)) }, Modifier.fillMaxWidth())
                if (event.snoozedUntilElapsed > now) Label(os.t("稍后提醒 · 还剩 ${(event.snoozedUntilElapsed - now) / 1000} 秒", "Snoozed · ${(event.snoozedUntilElapsed - now) / 1000} seconds remaining"), 12, c.muted)
                MetroButton(os.t("五分钟后再提醒", "Remind me in five minutes"), { os.aisCommand(AisCommand.Snooze(event.id)) }, Modifier.fillMaxWidth())
            }
        }

        if (target.relative.cpaMeters != null || target.relative.tcpaSeconds != null) {
            AppSection(os.t("预计会遇", "Expected encounter"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                target.relative.cpaMeters?.let { AisHeroFact(os.t("最近距离", "Closest distance"), os.formatDistance(it), modifier = Modifier.weight(1f), size = 24) }
                target.relative.tcpaSeconds?.let { seconds ->
                    val value = if (seconds < 0) os.t("${decimal(abs(seconds) / 60, 1)} 分钟前", "${decimal(abs(seconds) / 60, 1)} min ago") else os.t("${decimal(seconds / 60, 1)} 分钟后", "In ${decimal(seconds / 60, 1)} min")
                    AisHeroFact(os.t("到达最近距离", "Time to closest"), value, modifier = Modifier.weight(1f), size = 24)
                }
            }
            Label(aisCpaReason(os, target.relative.state), 12, c.muted)
            Label(os.t("按当前速度与方向估计，不保证未来距离，也不是操船指令。请保持瞭望。", "Estimated from current speed and course; not a guaranteed distance or manoeuvring instruction. Keep a lookout."), 12, c.muted)
        } else if (distance != null || speed != null) Label(os.t("会遇估计 · ", "Encounter estimate · ") + aisCpaReason(os, target.relative.state), 12, c.muted)
        if (target.relative.approachTrend != AisApproachTrend.UNKNOWN) Label(when (target.relative.approachTrend) {
            AisApproachTrend.APPROACHING -> os.t("观测距离正在缩小", "Observed distance is decreasing")
            AisApproachTrend.RECEDING -> os.t("观测距离正在增大", "Observed distance is increasing")
            else -> os.t("观测距离变化不明显", "No significant change in observed distance")
        }, 15, c.muted)
        if (target.track.isNotEmpty()) MenuRow(os.t("在海图查看航迹", "View trail on Chart"), os.t("只连接连续收到的位置", "Gaps in reception stay disconnected"), "route") { viewTrack(target.mmsi) }

        if (target.safetyMessages.isNotEmpty()) {
            AppSection(os.t("收到的安全信息", "Received safety messages"))
            target.safetyMessages.asReversed().forEach { message ->
                Label(message.text, 15)
                Label(readingAge(os, message.receivedElapsed, now), 12, c.muted)
            }
        }
        if (target.state == AisTargetState.CONFLICT) Label(os.t("不同来源报告了互相矛盾的位置。已停止会遇推算，请查看下方数据来源。", "Sources report conflicting positions. Encounter prediction has stopped; inspect Data sources below."), 15, riskColor)

        AisDetailSection(os, os.t("位置与航向", "Position and motion"), motionExpanded, { motionExpanded = !motionExpanded }) {
            AisFact(os.t("最后报告位置", "Last reported position"), target.position?.let { os.formatCoordinates(it.geo()) })
            AisFact(os.t("对地航向", "Course over ground"), dynamic?.cogDegrees?.let { os.formatBearing(it) + " T" })
            AisFact(os.t("船首向", "Heading"), dynamic?.headingDegrees?.let { os.formatBearing(it) + " T" })
            AisFact(os.t("相对船艏方位", "Bearing relative to bow"), target.relative.relativeBearingDegrees?.let(os::formatAngle))
            if (dynamic?.headingDegrees == null) Label(os.t("尚未收到船首向，不以对地航向代替。", "Heading has not been received; course over ground is not used in its place."), 12, c.muted)
            if (dynamic == null) Label(os.t("尚未收到位置或运动报告。", "No position or motion report has been received."), 15, c.muted)
            if (target.relative.extrapolationMillis > 0) Label(os.t("为比较两船位置，已对齐 ${target.relative.extrapolationMillis / 1000} 秒的观测时差。", "Positions are aligned over an observation-time difference of ${target.relative.extrapolationMillis / 1000} seconds."), 12, c.muted)
        }
        val static = target.staticData
        AisDetailSection(os, os.t("船舶资料", "Vessel information"), particularsExpanded, { particularsExpanded = !particularsExpanded }, aisKind(os, target.kind)) {
            Label(os.t("来自这艘船的 AIS 广播，未经登记核验。", "Broadcast by the vessel over AIS; not verified registry information."), 12, c.muted)
            AisFact("MMSI", aisNumber(target.mmsi))
            AisStaticFact(os, s, os.t("广播船名", "Broadcast name"), static.name, now)
            AisStaticFact(os, s, os.t("呼号", "Call sign"), static.callSign, now)
            AisStaticFact(os, s, "IMO", static.imo, now)
            AisStaticFact(os, s, os.t("船型代码", "Vessel type code"), static.shipType, now)
            AisStaticFact(os, s, os.t("目的地", "Destination"), static.destination, now)
            AisStaticFact(os, s, os.t("广播预计到港时间", "Broadcast arrival time"), static.eta, now)
            static.eta?.let { Label(os.t("这是对方填写的到港时间，与两船会遇时间不同。", "This is the vessel's declared arrival time, not the time to an encounter."), 12, c.muted) }
            static.draughtMeters?.let { AisFact(os.t("吃水", "Draught"), os.formatDepth(it.value)) }
            static.dimensions?.let { value ->
                val d = value.value
                AisFact(os.t("长度 × 宽度", "Length × beam"), os.formatLength(d.lengthMeters.toDouble()) + " × " + os.formatLength(d.beamMeters.toDouble()))
                Label(os.t("定位天线至船艏 / 艉 / 左舷 / 右舷：", "Antenna to bow / stern / port / starboard: ") + listOf(d.toBowMeters, d.toSternMeters, d.toPortMeters, d.toStarboardMeters).joinToString(" / ") { os.formatLength(it.toDouble()) }, 12, c.muted)
            }
            AisStaticFact(os, s, os.t("母船 MMSI", "Mother ship MMSI"), static.motherShipMmsi, now)
            MenuRow(os.t("备注名", "My name for this vessel"), target.alias ?: os.t("只保存在本机", "Saved on this device only")) { aliasEditing = true }
            MenuRow(os.t("复制 MMSI", "Copy MMSI"), aisNumber(target.mmsi)) {
                runCatching {
                    val clipboard = os.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: error("clipboard unavailable")
                    clipboard.setPrimaryClip(ClipData.newPlainText("MMSI", aisNumber(target.mmsi)))
                }.onFailure { os.notify("无法复制 MMSI，请重试。", "Could not copy MMSI. Please retry.", app = AppId.AIS) }
            }
        }
        val sources = (listOfNotNull(dynamic?.source, static.name?.source, static.callSign?.source, static.imo?.source,
            static.shipType?.source, static.destination?.source, static.eta?.source, static.dimensions?.source,
            static.draughtMeters?.source, static.motherShipMmsi?.source) + target.candidates.map { it.source } +
            target.recentMessages.map { it.source } + target.safetyMessages.map { it.source }).distinctBy { it.key }
        AisDetailSection(os, os.t("数据来源", "Data sources"), sourcesExpanded, { sourcesExpanded = !sourcesExpanded }, os.t("接收设备与更新时间", "Receivers and update times")) {
            if (sources.isEmpty()) Label(os.t("此缓存未保留接收来源。", "The receiver was not retained with this cached record."), 15, c.muted)
            sources.forEach { source ->
                val input = s.inputs.firstOrNull { it.connectionId == source.connectionId }
                val detail = listOfNotNull(source.peer, source.formatter, source.channel.takeIf(String::isNotBlank)).joinToString(" · ")
                if (input != null) MenuRow(input.name, detail) { os.openLinked("nmea:connection:${Uri.encode(source.connectionId)}") }
                else AisFact(os.t("已移除的接收连接", "Receiver connection removed"), detail.ifBlank { source.connectionId })
            }
            if (target.state == AisTargetState.CONFLICT) target.candidates.forEach { report ->
                AisFact(s.inputs.firstOrNull { it.connectionId == report.source.connectionId }?.name ?: os.t("历史来源", "Historical source"),
                    (report.position?.let { os.formatCoordinates(it.geo()) } ?: os.t("没有位置", "No position")) + " · " + readingAge(os, report.receivedElapsed, now))
            }
            MenuRow(os.t("查看全部 AIS 输入", "View all AIS inputs")) { openInputs() }
        }
        AisDetailSection(os, os.t("报告详情", "Report details"), reportsExpanded, { reportsExpanded = !reportsExpanded }, os.t("报文原文与设备声明", "Raw reports and device declarations")) {
            dynamic?.let {
                Label(os.t("位置精度声明 · ", "Declared position accuracy · ") + if (it.positionAccurate) os.t("高精度", "High accuracy") else os.t("低精度或未声明", "Low or not declared"), 12, c.muted)
                it.utcSecond?.let { second -> Label(os.t("AIS 秒字段 $second（不是完整 UTC 时间）", "AIS second field $second (not a complete UTC timestamp)"), 12, c.muted) }
                it.sourceTimestampText?.let { text -> Label(os.t("来源时间标签 · ", "Source time tag · ") + text, 12, c.muted) }
                it.invalidFields.forEach { field -> Label(aisFieldReason(os, field), 12, c.muted) }
            }
            if (target.recentMessages.isEmpty()) Label(os.t("未保留报文原文。", "No raw reports retained."), 15, c.muted)
            target.recentMessages.takeLast(12).asReversed().forEach { message ->
                Label(os.t("类型 ${message.type} · ", "Type ${message.type} · ") + readingAge(os, message.receivedElapsed, now), 12, c.muted)
                Label(message.payload, 13)
            }
        }
    }
    if (aliasEditing && target != null && inputEnabled) TextDialog(os, os.t("我的备注名", "My name for this vessel"), target.alias.orEmpty(), { aliasEditing = false }) { name ->
        os.aisCommand(AisCommand.Alias(target.mmsi, name.take(80).ifBlank { null })) { aliasEditing = false }
    }
}

@Composable private fun AisHeroFact(label: String, value: String, detail: String? = null, modifier: Modifier = Modifier, size: Int = 28) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Label(label, 12, LocalMetro.current.muted)
        Label(value, size)
        if (!detail.isNullOrBlank()) Label(detail, 12, LocalMetro.current.muted)
    }
}

/** 缺失资料省略，避免把接收尚未提供的字段排成「未提供」表格。 */
@Composable private fun AisFact(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Label(label, 12, LocalMetro.current.muted)
        Label(value, 15)
    }
}

@Composable private fun <T> AisStaticFact(os: OsStore, s: TrafficSnapshot, label: String, value: AisStaticValue<T>?, now: Long) {
    if (value == null) return
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AisFact(label, value.value.toString())
        val source = s.inputs.firstOrNull { it.connectionId == value.source.connectionId }?.name ?: os.t("历史来源", "Historical source")
        val age = if (value.receivedElapsed > 0) readingAge(os, value.receivedElapsed, now) else os.t("缓存资料", "Cached information")
        Label("$source · $age", 12, LocalMetro.current.muted)
    }
}

@Composable private fun AisDetailSection(os: OsStore, title: String, expanded: Boolean, toggle: () -> Unit, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LocalMetro.current.controlStroke))
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { stateDescription = if (expanded) os.t("已展开", "Expanded") else os.t("已收起", "Collapsed") }
            .clickable(role = Role.Button, onClick = toggle).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Label(title, 15, weight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank() && !expanded) Label(subtitle, 12, LocalMetro.current.muted)
            }
            Spacer(Modifier.width(12.dp))
            Glyph(if (expanded) "chevron_down" else "chevron_right", Modifier.size(16.dp), LocalMetro.current.muted)
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(180)) + fadeIn(tween(130)), exit = shrinkVertically(tween(160)) + fadeOut(tween(100))) {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

private fun aisNavigationStatus(os: OsStore, value: Int) = when (value) {
    0 -> os.t("机动航行", "Under engine")
    1 -> os.t("锚泊", "At anchor")
    2 -> os.t("失去控制", "Not under command")
    3 -> os.t("操纵受限", "Restricted manoeuvrability")
    4 -> os.t("吃水受限", "Constrained by draught")
    5 -> os.t("系泊", "Moored")
    6 -> os.t("搁浅", "Aground")
    7 -> os.t("从事捕鱼", "Fishing")
    8 -> os.t("帆航", "Under sail")
    14 -> os.t("设备报告状态 14", "Device status 14")
    else -> os.t("未声明（$value）", "Not declared ($value)")
}

private fun aisFieldReason(os: OsStore, field: String): String = when (field) {
    "position" -> os.t("此报文未提供有效位置", "This report does not provide a valid position")
    "sog" -> os.t("未提供对地航速", "Speed over ground not provided")
    "cog" -> os.t("未提供对地航向", "Course over ground not provided")
    "heading" -> os.t("未提供船首向，不以对地航向代替", "Heading not provided; course over ground is not used in its place")
    "sog_lower_bound" -> os.t("航速达到编码上限，仅知道下界，不用于预测", "Speed is at the encoding limit; a lower bound, excluded from prediction")
    "manual_position" -> os.t("设备报告手工输入的位置", "Device reports a manually entered position")
    "estimated_position" -> os.t("设备报告推算位置", "Device reports an estimated position")
    "position_system_inoperative" -> os.t("设备报告定位系统失效", "Device reports an inoperative positioning system")
    "rot_direction_only" -> os.t("仅提供转向方向，没有可用转向速率", "Turn direction only; no usable rate of turn")
    "position_latency_over_five_seconds" -> os.t("设备声明位置延迟超过五秒", "Device declares position latency over five seconds")
    else -> os.t("报文含未能采用的字段", "Report contains a field that cannot be used")
}
