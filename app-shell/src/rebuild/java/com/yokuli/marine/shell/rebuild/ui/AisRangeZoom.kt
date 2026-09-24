package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import kotlin.math.*

private const val MinimumAisRange = 100.0
private const val MaximumAisRange = 59_264.0
private const val OneTimesAisRange = 1_852.0
private val AisZoomStops = listOf(.03125, .0625, .125, .25, .5, 1.0, 2.0, 4.0, 8.0, 16.0)

/**
 * 相机式观察范围尺。倍率只缩放视图：1× 为一海里半径，不改变目标或风险计算。
 * 拖动只写访问中的临时范围；手指抬起才提交一次。页面离开或手势取消时恢复拖动前范围。
 */
@Composable
internal fun AisRangeZoom(
    os: OsStore,
    rangeMeters: Double,
    onRangeChange: (Double) -> Unit,
    onRangeCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalMetro.current
    val safe = LocalShellHorizontalInsets.current
    val active = enabled && LocalInternalAppInputEnabled.current
    val range = rangeMeters.takeIf(Double::isFinite)?.coerceIn(MinimumAisRange, MaximumAisRange) ?: OneTimesAisRange
    val liveRange = rememberUpdatedState(range)
    val liveChange = rememberUpdatedState(onRangeChange)
    val liveCommit = rememberUpdatedState(onRangeCommit)
    val liveActive = rememberUpdatedState(active)
    val density = LocalDensity.current
    val octaveWidth = with(density) { 72.dp.toPx() }
    val labelWidth = with(density) { 48.dp.toPx() }
    val domain = ln(MaximumAisRange / MinimumAisRange)
    val progress = (ln(range / MinimumAisRange) / domain).toFloat().coerceIn(0f, 1f)
    var dragging by remember { mutableStateOf(false) }

    Column(modifier.background(colors.bg).padding(start = safe.pageStart, end = safe.pageEnd), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(os.formatDistance(range), 16, if (active) colors.fg else colors.disabled,
            Modifier.padding(top = 2.dp, bottom = 1.dp), weight = FontWeight.SemiBold)
        BoxWithConstraints(Modifier.fillMaxWidth().height(52.dp).clipToBounds()
            .semantics(mergeDescendants = true) {
                contentDescription = os.t("AIS 显示范围", "AIS viewing range")
                stateDescription = os.formatDistance(range)
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                if (!active) disabled()
                else setProgress { value ->
                    val meters = (MinimumAisRange * exp(value.coerceIn(0f, 1f) * domain)).coerceIn(MinimumAisRange, MaximumAisRange)
                    liveChange.value(meters)
                    liveCommit.value(meters)
                    true
                }
            }
            .pointerInput(active, octaveWidth) {
                if (!active) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startRange = liveRange.value
                    var lastRange = startRange
                    var ownsDrag = false
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!liveActive.value || event.changes.count { it.pressed } > 1 || change.isConsumed && !ownsDrag) break
                            val travel = change.position - down.position
                            if (!ownsDrag && abs(travel.y) > viewConfiguration.touchSlop && abs(travel.y) > abs(travel.x)) break
                            if (!ownsDrag && abs(travel.x) > viewConfiguration.touchSlop) {
                                ownsDrag = true
                                dragging = true
                            }
                            if (ownsDrag) {
                                // 刻度跟着手指平移，倍率为连续对数；不依赖外部重组赶上本次触摸。
                                val dragDistance = travel.x - sign(travel.x) * viewConfiguration.touchSlop
                                lastRange = (startRange * 2.0.pow((dragDistance / octaveWidth).toDouble())).coerceIn(MinimumAisRange, MaximumAisRange)
                                change.consume()
                                liveChange.value(lastRange)
                            }
                            if (!change.pressed) {
                                released = true
                                if (ownsDrag) liveCommit.value(lastRange)
                                else if (travel.getDistance() <= viewConfiguration.touchSlop) {
                                    val zoom = OneTimesAisRange / startRange
                                    val tappedZoom = zoom * 2.0.pow(((change.position.x - size.width / 2f) / octaveWidth).toDouble())
                                    val nearest = AisZoomStops.minByOrNull { abs(log2(it / tappedZoom)) }!!
                                    val nearestX = size.width / 2f + (log2(nearest / zoom) * octaveWidth).toFloat()
                                    val meters = (OneTimesAisRange / if (abs(nearestX - change.position.x) <= labelWidth / 2f) nearest else tappedZoom)
                                        .coerceIn(MinimumAisRange, MaximumAisRange)
                                    change.consume()
                                    liveChange.value(meters)
                                    liveCommit.value(meters)
                                }
                                break
                            }
                        }
                    } finally {
                        dragging = false
                        if (ownsDrag && !released) liveChange.value(startRange)
                    }
                }
            }) {
            val width = with(density) { maxWidth.toPx() }
            val zoom = OneTimesAisRange / range
            val currentOctave = log2(zoom)
            Canvas(Modifier.fillMaxSize()) {
                val center = size.width / 2f
                val tickColor = if (active) colors.muted else colors.disabled
                for (quarter in -20..17) {
                    val octave = quarter / 4.0
                    val tickRange = OneTimesAisRange / 2.0.pow(octave)
                    if (tickRange !in MinimumAisRange..MaximumAisRange) continue
                    val x = center + ((octave - currentOctave) * octaveWidth).toFloat()
                    if (x !in 0f..size.width) continue
                    val major = quarter % 4 == 0
                    drawLine(tickColor.copy(alpha = if (major) .9f else .45f), Offset(x, 7.dp.toPx()),
                        Offset(x, if (major) 16.dp.toPx() else 12.dp.toPx()), 1.dp.toPx())
                }
                AisZoomStops.forEach { stop ->
                    if (abs(log2(stop / zoom)) < .06) {
                        val x = center + (log2(stop / zoom) * octaveWidth).toFloat()
                        drawCircle(if (active) colors.controlFill else colors.subtle, 17.dp.toPx(), Offset(x, 34.dp.toPx()))
                    }
                }
                val marker = Path().apply {
                    moveTo(center - 3.dp.toPx(), 0f)
                    lineTo(center + 3.dp.toPx(), 0f)
                    lineTo(center, 5.dp.toPx())
                    close()
                }
                drawPath(marker, if (active) colors.accent else colors.disabled)
                if (dragging) drawLine(colors.accent, Offset(center, 6.dp.toPx()), Offset(center, 16.dp.toPx()), 2.dp.toPx())
            }
            AisZoomStops.forEach { stop ->
                val x = width / 2f + (log2(stop / zoom) * octaveWidth).toFloat()
                if (x >= -labelWidth / 2f && x <= width + labelWidth / 2f) {
                    val isSelected = abs(log2(stop / zoom)) < .06
                    val text = when (stop) {
                        .03125 -> "0.03×"; .0625 -> "0.06×"; .125 -> "0.12×"; .25 -> "0.25×"; .5 -> "0.5×"
                        else -> "${stop.toInt()}×"
                    }
                    Box(Modifier.offset { IntOffset((x - labelWidth / 2f).roundToInt(), 0) }.width(48.dp).fillMaxHeight()
                        .clearAndSetSemantics {}, contentAlignment = Alignment.BottomCenter) {
                        Label(text, 12, if (!active) colors.disabled else if (isSelected) colors.accentText else colors.muted,
                            Modifier.padding(bottom = 7.dp), weight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
