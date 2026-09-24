package com.yokuli.marine.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** 纯视觉指示器；整行的 toggleable/可用性/失败结果属于调用方，不能在这里保有第二份开关状态。 */
@Composable
fun W10ToggleIndicator(checked: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val colors = LocalWpTheme.current
    val progress by animateFloatAsState(if (checked) 1f else 0f,
        tween(if (LocalReducedMotion.current) 0 else W10MobileMotion.ControlMillis, easing = W10MobileMotion.EntranceEasing), label = "w10-toggle")
    Canvas(modifier.size(W10MobileMetrics.ToggleWidth, W10MobileMetrics.ToggleHeight).clearAndSetSemantics {}) {
        val outline = if (!enabled) colors.disabled else if (checked) colors.accentText else colors.controlStroke
        val fill = if (checked) { if (enabled) colors.accent else colors.controlFill } else Color.Transparent
        // 允许快捷菜单使用等比例的小指示器；点击目标仍由整行提供，不能放大 thumb 挤满轨道。
        val stroke = size.height * .1f
        val inset = stroke / 2f
        drawRoundRect(fill, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), CornerRadius(size.height / 2f))
        drawRoundRect(outline, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), CornerRadius(size.height / 2f), style = Stroke(stroke))
        val center = Offset(size.height / 2f + (size.width - size.height) * progress, size.height / 2f)
        drawCircle(if (!enabled) colors.disabled else if (checked) colors.onAccent else colors.foreground,
            radius = size.height * .3f, center = center)
    }
}

@Composable
fun W10RadioIndicator(selected: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val colors = LocalWpTheme.current
    val dot by animateFloatAsState(if (selected) 1f else 0f,
        tween(if (LocalReducedMotion.current) 0 else W10MobileMotion.ControlMillis), label = "w10-radio")
    Canvas(modifier.size(W10MobileMetrics.Selection).clearAndSetSemantics {}) {
        val color = if (!enabled) colors.disabled else if (selected) colors.accentText else colors.controlStroke
        drawCircle(color, radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        if (dot > 0f) drawCircle(color, radius = W10MobileMetrics.RadioDot.toPx() / 2f * dot)
    }
}

@Composable
fun W10CheckIndicator(selected: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val colors = LocalWpTheme.current
    val progress by animateFloatAsState(if (selected) 1f else 0f,
        tween(if (LocalReducedMotion.current) 0 else W10MobileMotion.ControlMillis), label = "w10-checkbox")
    Canvas(modifier.size(W10MobileMetrics.Selection).clearAndSetSemantics {}) {
        val color = if (!enabled) colors.disabled else if (selected) colors.accentText else colors.controlStroke
        val stroke = 2.dp.toPx()
        val bounds = Size(size.width - stroke, size.height - stroke)
        if (progress > 0f) drawRect((if (enabled) colors.accent else colors.controlFill).copy(alpha = progress), Offset(stroke / 2, stroke / 2), bounds)
        drawRect(color, Offset(stroke / 2, stroke / 2), bounds, style = Stroke(stroke))
        if (progress > 0f) {
            val mark = Path().apply {
                moveTo(size.width * .24f, size.height * .52f)
                lineTo(size.width * .43f, size.height * .7f)
                lineTo(size.width * .79f, size.height * .3f)
            }
            drawPath(mark, (if (enabled) colors.onAccent else colors.disabled).copy(alpha = progress), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Square))
        }
    }
}

/** UWP 时代的点状环形进度；不可见时调用方必须传 active=false，以释放无限动画。 */
@Composable
fun W10ProgressRing(active: Boolean, modifier: Modifier = Modifier, color: Color? = null) {
    if (!active) { Box(modifier.size(24.dp)); return }
    val tint = color ?: LocalWpTheme.current.accentText
    val reduced = LocalReducedMotion.current
    val cycle = if (reduced) null else rememberInfiniteTransition(label = "w10-progress")
    val phase = cycle?.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "orbit")
    Canvas(modifier.size(24.dp).clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val radius = size.minDimension * .38f
        repeat(5) { index ->
            val position = (((phase?.value ?: .55f) - index * .055f + 1f) % 1f)
            val eased = if (reduced) position else W10MobileMotion.EntranceEasing.transform(position)
            val angle = Math.toRadians((eased * 360f - 90f).toDouble())
            drawCircle(tint.copy(alpha = .55f + .45f * (1f - index / 5f)), radius = size.minDimension * .059f,
                center = Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius))
        }
    }
}
