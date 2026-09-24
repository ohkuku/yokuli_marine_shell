package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import com.yokuli.marine.shell.rebuild.NotificationShadePhase
import com.yokuli.marine.shell.rebuild.NotificationShadeState

/** 标题/把手始终可收起；普通按钮仍经过 touch slop 后才让出点击。 */
@Composable internal fun Modifier.shadeHandle(shade: NotificationShadeState): Modifier {
    val density = LocalDensity.current.density
    return draggable(rememberDraggableState { shade.dragBy(it) }, Orientation.Vertical,
        startDragImmediately = shade.phase in setOf(NotificationShadePhase.OPENING, NotificationShadePhase.SETTLING),
        onDragStarted = { shade.beginDrag() }, onDragStopped = { shade.release(it, density) })
}

/** 只追踪整次触摸，不消费子控件；Closed 之前开始的手指必须抬起才释放底层输入。 */
internal fun Modifier.trackShadeTouches(shade: NotificationShadeState): Modifier = pointerInput(shade) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        shade.touchStarted()
        try { do { val event = awaitPointerEvent(PointerEventPass.Initial) } while (event.changes.any { it.pressed }) }
        finally { shade.touchFinished() }
    }
}

private class ShadeScrollGate {
    var eligible = false
    var owns = false
    var consumeFling = false
    var interruptedMotion = false
}

/**
 * 只有在列表末端新开始的手势才可交给面板。列表滚动/惯性撞底的剩余 delta 不关闭。
 * 横向消息由子 draggable 锁定，根面板从不再消费它的 X 位移。
 */
@Composable internal fun Modifier.shadeListScroll(shade: NotificationShadeState, list: LazyListState): Modifier {
    val density = LocalDensity.current.density
    val gate = remember(shade, list) { ShadeScrollGate() }
    val connection = remember(shade, list) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || !gate.eligible) return Offset.Zero
            if (!gate.owns && available.y < 0f) { gate.owns = true; shade.beginDrag() }
            return if (gate.owns) {
                if (!gate.interruptedMotion) shade.dragBy(available.y)
                // 归面板后整段纵向手势只归面板，碰到锚点也不把余量泄回列表。
                Offset(0f, available.y)
            } else Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity =
            if (gate.consumeFling) { gate.consumeFling = false; Velocity(0f, available.y) } else Velocity.Zero
    } }
    return nestedScroll(connection).pointerInput(shade, list, density) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            gate.interruptedMotion = shade.phase in setOf(NotificationShadePhase.OPENING, NotificationShadePhase.SETTLING)
            gate.eligible = gate.interruptedMotion || (!list.canScrollForward && !list.isScrollInProgress)
            gate.owns = gate.interruptedMotion; gate.consumeFling = false
            if (gate.interruptedMotion) { shade.beginDrag(); down.consume() }
            // 面板本身在移动：速度采样转换回宿主坐标，不能把跟手位移抵消成零速度。
            var previousHost = down.position + Offset(0f, shade.offsetPx)
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, previousHost) }
            var ended = false
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull { it.id == down.id }?.let {
                        val hostPosition = it.position + Offset(0f, shade.offsetPx)
                        tracker.addPosition(it.uptimeMillis, hostPosition)
                        if (gate.interruptedMotion) { shade.dragBy(hostPosition.y - previousHost.y); it.consume() }
                        previousHost = hostPosition
                    }
                    if (gate.interruptedMotion) event.changes.forEach { it.consume() }
                    ended = event.changes.none { it.pressed }
                } while (!ended)
                if (gate.owns) {
                    gate.consumeFling = true
                    shade.release(tracker.calculateVelocity().y, density)
                }
            } finally {
                if (!ended && gate.owns) shade.cancelDrag()
                gate.owns = false; gate.eligible = false; gate.interruptedMotion = false
            }
        }
    }
}
