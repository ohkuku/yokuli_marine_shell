package com.yokuli.marine.shell.rebuild

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Shell 的覆盖层状态。偏移/返回上下文不写消息仓库，也不跨冷启动恢复。 */
enum class NotificationShadePhase { CLOSED, OPENING, OPEN, DRAGGING, SETTLING }

data class NotificationShadePresentation(
    val listIndex: Int = 0, val listOffset: Int = 0, val more: Boolean = false,
    val expandedMessages: Set<String> = emptySet(), val detail: String? = null,
)

/** 两个稳定锚点；所有关闭入口、输入阻挡、截图策略读取同一个实际动画状态。 */
class NotificationShadeState {
    private var animationScope: CoroutineScope? = null
    var phase by mutableStateOf(NotificationShadePhase.CLOSED); private set
    var offsetPx by mutableFloatStateOf(0f); private set
    var heightPx by mutableFloatStateOf(0f); private set
    private var visiblePresentation by mutableStateOf(NotificationShadePresentation())
    private var savedListIndex = 0
    private var savedListOffset = 0
    /** 中文：滚动位置属于恢复记录，不是需要让整个通知中心逐像素重组的页面状态。 */
    var presentation: NotificationShadePresentation
        get() = visiblePresentation.let { current ->
            if (current.listIndex == savedListIndex && current.listOffset == savedListOffset) current
            else current.copy(listIndex = savedListIndex, listOffset = savedListOffset)
        }
        set(value) {
            rememberListPosition(value.listIndex, value.listOffset)
            visiblePresentation = value.copy(listIndex = 0, listOffset = 0)
        }
    private var touchCount by mutableIntStateOf(0)
    private var motion: Job? = null
    private var motionVelocityPx = 0f
    private var wantsOpen = false
    private var afterClose: (() -> Unit)? = null
    val visible get() = phase != NotificationShadePhase.CLOSED
    val blocksInput get() = visible || touchCount > 0
    val settledOpen get() = phase == NotificationShadePhase.OPEN && touchCount == 0
    val progress get() = if (heightPx > 0) (1f + offsetPx / heightPx).coerceIn(0f, 1f) else 0f

    fun rememberListPosition(index: Int, offset: Int) {
        savedListIndex = index.coerceAtLeast(0)
        savedListOffset = offset.coerceAtLeast(0)
    }

    fun open(restored: NotificationShadePresentation? = null) {
        afterClose = null
        restored?.let { presentation = it }
        val wasClosed = !visible
        wantsOpen = true
        if (wasClosed) { offsetPx = -heightPx; motionVelocityPx = 0f }
        phase = NotificationShadePhase.OPENING
        if (heightPx > 0) settle(true)
    }
    fun close(onClosed: (() -> Unit)? = null) {
        afterClose = onClosed
        wantsOpen = false
        if (heightPx <= 0 || !visible) { motion?.cancel(); motionVelocityPx = 0f; phase = NotificationShadePhase.CLOSED; finishClose() }
        else settle(false)
    }
    fun toggle() { if (wantsOpen) close() else open() }
    fun back() {
        if (presentation.detail != null) presentation = presentation.copy(detail = null) else close()
    }
    fun showDetail(detail: String) { presentation = presentation.copy(detail = detail) }
    fun updateHeight(height: Float) {
        if (!height.isFinite() || height <= 0 || height == heightPx) return
        val oldProgress = progress
        val oldHeight = heightPx
        motion?.cancel()
        heightPx = height
        motionVelocityPx = if (oldHeight > 0f) motionVelocityPx * height / oldHeight else 0f
        offsetPx = when { !visible -> -height; oldHeight <= 0 -> -height; else -> (oldProgress - 1f) * height }
        // 旋转/IME 改变锚点时按当前意图收敛，不保留不可交互的半开状态。
        if (visible && phase != NotificationShadePhase.DRAGGING) settle(wantsOpen)
    }
    fun touchStarted() { touchCount++ }
    fun touchFinished() { touchCount = (touchCount - 1).coerceAtLeast(0); finishClose() }
    fun beginDrag() {
        motion?.cancel(); afterClose = null
        motionVelocityPx = 0f
        if (visible) phase = NotificationShadePhase.DRAGGING
    }
    fun dragBy(delta: Float): Float {
        if (!visible || !delta.isFinite()) return 0f
        val before = offsetPx
        offsetPx = (offsetPx + delta).coerceIn(-heightPx, 0f)
        return offsetPx - before
    }
    fun release(velocity: Float, density: Float) {
        val speed = velocity.takeIf { it.isFinite() } ?: 0f
        val reversing = speed > REVERSE_VELOCITY_DP * density
        val dismiss = !reversing && (-offsetPx >= heightPx * CLOSE_FRACTION ||
            (-offsetPx >= MIN_FLING_DP * density && speed < -FLING_VELOCITY_DP * density))
        wantsOpen = !dismiss
        settle(!dismiss, speed)
    }
    fun cancelDrag() { if (visible) { wantsOpen = true; settle(true) } }
    /** 位移动画使用当前 Compose 宿主的帧时钟，不能在 Application 的无帧 scope 中运行。 */
    fun attachHost(scope: CoroutineScope) {
        animationScope = scope
        if (visible && phase != NotificationShadePhase.DRAGGING && heightPx > 0) settle(wantsOpen)
    }
    fun hostDetached() {
        motion?.cancel(); animationScope = null; touchCount = 0
        motionVelocityPx = 0f
        if (visible) { offsetPx = if (wantsOpen) 0f else -heightPx; phase = if (wantsOpen) NotificationShadePhase.OPEN else NotificationShadePhase.CLOSED }
        finishClose()
    }
    private fun settle(open: Boolean, velocity: Float = motionVelocityPx) {
        motion?.cancel()
        wantsOpen = open
        phase = if (open && offsetPx <= -heightPx) NotificationShadePhase.OPENING else NotificationShadePhase.SETTLING
        val host = animationScope ?: return
        motion = host.launch {
            val target = if (open) 0f else -heightPx
            if (abs(offsetPx - target) > .1f) Animatable(offsetPx).animateTo(target,
                spring(dampingRatio = 1f, stiffness = 520f), initialVelocity = velocity) {
                offsetPx = value.coerceIn(-heightPx, 0f)
                motionVelocityPx = if (value in -heightPx..0f) this.velocity else 0f
            }
            offsetPx = target
            motionVelocityPx = 0f
            phase = if (open) NotificationShadePhase.OPEN else NotificationShadePhase.CLOSED
            finishClose()
        }
    }
    private fun finishClose() {
        if (!blocksInput && !wantsOpen) afterClose?.also { afterClose = null }?.invoke()
    }
    companion object {
        const val CLOSE_FRACTION = .22f
        const val MIN_FLING_DP = 32f
        const val FLING_VELOCITY_DP = 900f
        const val REVERSE_VELOCITY_DP = 180f
    }
}
