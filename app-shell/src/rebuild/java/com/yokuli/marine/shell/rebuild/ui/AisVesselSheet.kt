package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTextScale
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.runtime.contract.ais.*
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 覆盖在观察场景上方；拖动只改变面板偏移，不改三维 Surface 的尺寸或身份。 */
@Composable internal fun AisVesselSheet(
    os: OsStore,
    s: TrafficSnapshot,
    mmsi: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    view: (AisView, Int) -> Unit,
    viewTrack: (Int) -> Unit,
    openInputs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalMetro.current
    val target = s.target(mmsi)
    val now = rememberMarineClock()
    val density = LocalDensity.current
    val textScale = LocalWpTextScale.current
    val enabled = LocalInternalAppInputEnabled.current
    val scope = rememberCoroutineScope()
    val fraction = remember(mmsi) { Animatable(if (expanded) 0f else 1f) }
    var dragging by remember(mmsi) { mutableStateOf(false) }
    var releaseVelocity by remember(mmsi) { mutableFloatStateOf(0f) }

    AppBackHandler(enabled) {
        if (expanded) onExpandedChange(false) else onClose()
    }
    LaunchedEffect(expanded, dragging, enabled) {
        if (!enabled) {
            dragging = false
            fraction.snapTo(if (expanded) 0f else 1f)
        } else if (!dragging) {
            fraction.animateTo(if (expanded) 0f else 1f,
                animationSpec = spring(dampingRatio = .9f, stiffness = 560f), initialVelocity = releaseVelocity)
            releaseVelocity = 0f
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        // 横屏同样保留可用的滚动区域；字号增大只扩摘要，不扩大整个场景的布局。
        val summaryHeight = (108.dp * textScale * density.fontScale.coerceAtLeast(1f)).coerceAtMost(maxHeight * .48f)
        val expandedHeight = (maxHeight * .90f).coerceAtLeast(summaryHeight)
        val travel = with(density) { (expandedHeight - summaryHeight).toPx() }.coerceAtLeast(1f)
        val dragState = rememberDraggableState { delta ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                fraction.snapTo((fraction.value + delta / travel).coerceIn(0f, 1f))
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(expandedHeight)
            .offset { IntOffset(0, (fraction.value * travel).roundToInt()) }
            .background(c.panel)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.controlStroke))
            Column(Modifier.fillMaxWidth().height(summaryHeight - 1.dp)
                .semantics { stateDescription = if (expanded) os.t("详情已展开", "Details expanded") else os.t("目标摘要", "Target summary") }
                .draggable(dragState, Orientation.Vertical, enabled = enabled,
                    onDragStarted = { dragging = true; fraction.stop() },
                    onDragStopped = { velocity ->
                        if (enabled) {
                            releaseVelocity = velocity / travel
                            // 松手后的去向综合位移与速度；短拖不会自动关闭目标或改变选中船只。
                            val projected = fraction.value + releaseVelocity * .16f
                            onExpandedChange(projected < .5f)
                        }
                        dragging = false
                    })) {
                Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(32.dp).height(2.dp).background(c.muted))
                }
                Row(Modifier.fillMaxWidth().weight(1f).padding(start = LocalShellHorizontalInsets.current.pageStart,
                    end = LocalShellHorizontalInsets.current.pageEnd), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled, role = Role.Button) { onExpandedChange(!expanded) },
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                        Label(target?.displayName ?: aisNumber(mmsi), 15, modifier = Modifier.fillMaxWidth(), maxLines = 1, weight = FontWeight.SemiBold)
                        if (target != null) {
                            val location = listOfNotNull(target.relative.distanceMeters?.let(os::formatDistance), target.relative.bearingDegrees?.let { os.formatBearing(it) + " T" }).joinToString(" · ")
                            val trend = when (target.relative.approachTrend) {
                                AisApproachTrend.APPROACHING -> os.t("正在接近", "Approaching")
                                AisApproachTrend.RECEDING -> os.t("正在远离", "Moving away")
                                else -> null
                            }
                            Label(listOfNotNull(location.takeIf(String::isNotBlank), trend).joinToString(" · ").ifBlank { aisCpaReason(os, target.relative.state) }, 12, c.fg, maxLines = 1)
                            val activeRisk = s.events.firstOrNull { it.mmsi == mmsi && it.active }
                            val risk = when {
                                target.distress == AisDistressState.ACTIVE -> os.t("遇险设备已激活", "Active distress device")
                                activeRisk != null -> aisRiskName(os, activeRisk.kind)
                                target.riskLevel != AisRiskLevel.NONE -> os.t("需要关注", "Needs attention")
                                target.state != AisTargetState.CURRENT -> aisState(os, target)
                                else -> null
                            }
                            val age = if (target.cached) os.t("历史记录", "Historical record") else target.dynamic?.let { readingAge(os, it.receivedElapsed, now) }
                                ?: readingAge(os, target.lastMessageElapsed, now)
                            Label(listOfNotNull(risk, age).joinToString(" · "), 12,
                                if (activeRisk != null || target.distress == AisDistressState.ACTIVE || target.riskLevel != AisRiskLevel.NONE) { if (os.light) Color(0xFFAD2525) else Color(0xFFFF8C86) } else c.muted,
                                maxLines = 2)
                        } else Label(os.t("等待接收恢复，原有选择保留", "Waiting for reception; selection retained"), 12, c.muted, maxLines = 2)
                    }
                    Box(Modifier.size(48.dp).semantics { contentDescription = os.t("取消选中", "Clear selection") }
                        .clickable(enabled = enabled, role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                        Glyph("close", Modifier.size(20.dp), c.muted)
                    }
                }
            }
            // 滚动与展开状态留在本面板；没有嵌套一个新的应用访问，也不重建下面的观察场景。
            // 收起的详情留在原位置以保留滚动，但不能接收触摸或被辅助功能选中。
            val detailEnabled = enabled && expanded && !dragging
            Box(Modifier.weight(1f).fillMaxWidth().then(if (detailEnabled) Modifier else Modifier
                .clearAndSetSemantics {}
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } })) {
                CompositionLocalProvider(LocalInternalAppInputEnabled provides detailEnabled) {
                    key(mmsi) { AisTargetDetail(os, s, mmsi, view, viewTrack, openInputs) }
                }
            }
        }
    }
}
