package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.runtime.contract.PositionSourceRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.anchorwatch.location.vessel.DeviceBowAxis
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.runtime.contract.VoyagePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 快捷入口只发送既有业务命令。状态来自系统偏好和运行中的会话，不另存开关。 */
@Composable internal fun NotificationQuickActions(os: OsStore) {
    val c = LocalMetro.current
    val scope = rememberCoroutineScope()
    val insets = LocalShellHorizontalInsets.current
    val marine = os.marine
    val state = marine?.services?.state?.collectAsState()?.value
    val voyage = marine?.voyage?.collectAsState()?.value
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmMount by rememberSaveable { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var pendingAnchor by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val active = state?.active
    val gpsLocked = active?.paused == false
    LaunchedEffect(pendingAnchor, active?.id, active?.paused) {
        val request = pendingAnchor ?: return@LaunchedEffect
        if (active == null || active.id != request.first || active.paused == request.second) { pendingAnchor = null; return@LaunchedEffect }
        delay(8000)
        pendingAnchor = null
        feedback = os.t("守锚状态尚未确认，请检查当前值守。", "Anchor state is not confirmed; check the current watch.")
    }
    Column(Modifier.fillMaxWidth().padding(start = insets.topStart, end = insets.topEnd), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickAction(os, if (os.light) "sun" else "moon", os.t("日 / 夜", "day / night"), if (os.light) os.t("日间", "day") else os.t("夜间", "night"), modifier = Modifier.weight(1f)) {
                os.shell.updateSystemPreferences { it.copy(themeModeName = if (it.themeModeName == "LIGHT") "DARK" else "LIGHT") }
            }
            QuickAction(os, "locate", "GPS", when {
                gpsLocked -> os.t("守锚使用中", "watch in use")
                os.positionSource == "phone" -> os.t("已开启", "on")
                os.positionSource == "nmea" -> os.t("船载船位", "boat position")
                else -> os.t("已关闭", "off")
            }, active = os.positionSource == "phone", enabled = marine != null && !gpsLocked && os.positionSource in listOf("none", "phone"), modifier = Modifier.weight(1f)) {
                os.requestPosition(if (os.positionSource == "phone") PositionSourceRequest.DISABLE_POSITION else PositionSourceRequest.ENABLE_PHONE)
            }
            QuickAction(os, "awake", os.t("常亮", "awake"), if (os.keepAwake) os.t("已开启", "on") else os.t("已关闭", "off"), active = os.keepAwake, modifier = Modifier.weight(1f)) {
                os.shell.updateSystemPreferences { preferences ->
                    val wasOn = preferences.appPreferenceValues["preferences.display.keep_awake"] != "b:0"
                    preferences.copy(appPreferenceValues = preferences.appPreferenceValues + ("preferences.display.keep_awake" to if (wasOn) "b:0" else "b:1"))
                }
            }
            QuickAction(os, if (expanded) "collapse" else "more", os.t("更多", "more"), if (expanded) os.t("收起", "collapse") else os.t("展开", "expand"), modifier = Modifier.weight(1f)) { expanded = !expanded; if (!expanded) confirmMount = false }
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(180)), exit = shrinkVertically(tween(160))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickAction(os, "anchor", os.t("守锚", "anchor"), when {
                    pendingAnchor != null -> os.t("处理中", "pending")
                    active == null -> os.t("未下锚", "inactive")
                    active.paused -> os.t("继续", "resume")
                    else -> os.t("暂停", "pause")
                }, active = active?.paused == false, enabled = active != null && pendingAnchor == null, modifier = Modifier.weight(1f)) {
                    val services = marine?.services
                    val current = services?.state?.value?.active
                    if (current != null && services != null) {
                        feedback = null
                        pendingAnchor = current.id to !current.paused
                        runCatching { if (current.paused) services.anchor.resumeWatch() else services.anchor.pauseWatch() }.onFailure {
                            pendingAnchor = null; feedback = os.t("守锚操作未完成，请重试。", "Anchor action did not complete; please retry.")
                        }
                    }
                }
                QuickAction(os, "record", os.t("航行", "voyage"), when {
                    voyage?.commandPending == true -> os.t("处理中", "pending")
                    voyage?.phase == VoyagePhase.PAUSED -> os.t("继续", "resume")
                    voyage?.phase == VoyagePhase.RECORDING -> os.t("暂停", "pause")
                    else -> os.t("未记录", "inactive")
                }, active = voyage?.phase == VoyagePhase.RECORDING, enabled = voyage?.phase in listOf(VoyagePhase.PAUSED, VoyagePhase.RECORDING) && voyage?.commandPending == false, modifier = Modifier.weight(1f)) {
                    when (marine?.voyage?.value?.phase) { VoyagePhase.PAUSED -> marine?.resumeRecording(); VoyagePhase.RECORDING -> marine?.pauseRecording(); else -> Unit }
                }
                QuickAction(os, "mount", os.t("姿态", "attitude"), if (confirming) os.t("读取中", "reading") else os.t("重新确认", "reconfirm"),
                    enabled = state?.phoneSensorCapabilities?.attitudeAvailable == true && state.activeTrip?.paused != true && !confirming, modifier = Modifier.weight(1f)) { feedback = null; confirmMount = !confirmMount }
                QuickAction(os, "connect", os.t("来源", "sources"), os.t("数据中心", "data center"), modifier = Modifier.weight(1f)) { os.openSystemDestination("data_center") }
            }
        }
        if (confirmMount && state != null) Column(Modifier.fillMaxWidth().background(c.panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val axis = when (state.vesselMountCalibration.bowAxis) { DeviceBowAxis.TOP -> os.t("顶部", "top"); DeviceBowAxis.BOTTOM -> os.t("底部", "bottom"); DeviceBowAxis.LEFT -> os.t("左侧", "left"); DeviceBowAxis.RIGHT -> os.t("右侧", "right") }
            Label(os.t("固定手机 · $axis 朝向船艏", "mounted phone · $axis edge toward bow"), 18)
            Label(os.t("保持手机与船体固定，重新确认当前安装方向。实际横倾会保留。", "Keep the phone fixed to the boat and reconfirm its mounting direction. The boat's actual heel is preserved."), 14, c.muted)
            if (confirming) MetroProgress(os.t("正在读取传感器", "reading the sensor"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Label(os.t("重新确认", "reconfirm"), 17, if (confirming) c.muted else c.accent, Modifier.clickable(enabled = !confirming) {
                    marine?.services?.let { services ->
                        confirming = true; feedback = null; services.sources.clearVesselCalibrationFeedback()
                        val command = services.sources.confirmTripAttitudeFrame(services.state.value.vesselMountCalibration.bowAxis)
                        scope.launch {
                            val completed = withTimeoutOrNull(6000) { command.join(); true } == true
                            val result = if (completed) services.state.value.vesselCalibrationFeedback else null
                            confirming = false
                            feedback = when (result) {
                                "Trip attitude frame confirmed." -> { confirmMount = false; os.t("姿态安装已重新确认", "attitude mounting reconfirmed") }
                                "No rotation-vector sample is available on this phone." -> os.t("没有收到新的姿态样本，未修改安装方向。", "No fresh attitude sample received; mounting was not changed.")
                                "Resume the trip before confirming a new attitude segment." -> os.t("先继续航行，再确认姿态安装。", "Resume the voyage before confirming attitude mounting.")
                                else -> os.t("尚未确认安装，请检查后重试。", "Mounting is not confirmed; check and retry.")
                            }
                        }
                    }
                }.padding(vertical = 7.dp))
                Label(os.t("更改方向", "change direction"), 17, c.accent, Modifier.clickable(enabled = !confirming) { os.openSystemDestination("data_center:phone") }.padding(vertical = 7.dp))
                Label(os.t("取消", "cancel"), 17, c.muted, Modifier.clickable(enabled = !confirming) { confirmMount = false }.padding(vertical = 7.dp))
            }
        }
        feedback?.let { text -> Label(text, 14, c.muted, Modifier.padding(vertical = 4.dp)) }
    }
}

/** active 只表达 GPS、常亮、运行会话等持续状态；切换模式、姿态确认和展开仅是动作。 */
@Composable private fun QuickAction(os: OsStore, icon: String, title: String, detail: String, active: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, action: () -> Unit) {
    val c = LocalMetro.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, tween(100), label = "quick-action")
    Column(modifier.heightIn(min = 68.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else .42f }
        .background(if (active) c.accent else c.panel)
        .semantics { stateDescription = detail }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = title, onClick = action)
        .padding(horizontal = 3.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val ink = if (active) Color.White else c.fg
        QuickGlyph(icon, ink)
        Label(title, 12, ink, maxLines = 1)
        Label(detail, 10, if (active) Color.White.copy(alpha = .8f) else c.muted, maxLines = 1)
    }
}

@Composable private fun QuickGlyph(icon: String, color: Color) {
    if (icon !in setOf("sun", "moon", "awake", "mount", "collapse")) { Glyph(icon, Modifier.size(23.dp), color); return }
    Canvas(Modifier.size(23.dp)) {
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
