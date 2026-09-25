package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.marine.core.design.LocalReducedMotion
import com.yokuli.shell.compose.LocalInternalAppInputEnabled

private data class InstrumentMotionSource(
    val identity: VesselSourceIdentity?,
    val source: VesselDataSource,
    val sourceClass: VesselSourceClass,
    val reference: VesselReference?,
    val provenance: VesselProvenance?,
    val scale: Any?,
)

/**
 * 中文：实时图形专用显示值。调用方在 Canvas/graphicsLayer 中读取 State.value，
 * 不让每个动画帧重组仪表文字和整页。数字、历史、警报继续使用原观测。
 * 换来源/基准/单位、失效和恢复时直接落到真实值；角度跨北走最短弧。
 */
@Composable internal fun rememberInstrumentMotion(
    observation: VesselObservation<*>,
    value: Double?,
    circular: Boolean = false,
    scaleKey: Any? = null,
): State<Float> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var foregroundEpoch by remember(lifecycle) { mutableIntStateOf(0) }
    val source = InstrumentMotionSource(observation.sourceIdentity, observation.source, observation.sourceClass,
        observation.reference, observation.provenanceDetail, scaleKey)
    val target = value?.takeIf { it.isFinite() }?.toFloat()?.takeIf { it.isFinite() }
    val motion = remember(source, circular) { Animatable(target ?: 0f) }
    val wasLive = remember(source, circular) { booleanArrayOf(false) }
    DisposableEffect(lifecycle, motion) {
        // 不用随 lifecycle 自己停收的 Flow 来判断后台，否则快速 PAUSE/STOP 可留下 RESUMED 缓存。
        val observer = LifecycleEventObserver { _, _ ->
            val next = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!next) {
                wasLive[0] = false
                // 后台未重组时 false/true 可能合流；代次保证恢复后不续播旧的半段弹簧。
                if (resumed) foregroundEpoch++
            }
            resumed = next
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val active = LocalInternalAppInputEnabled.current && resumed && !LocalReducedMotion.current
    val live = active && observation.displayIsLive() && target != null
    LaunchedEffect(motion, target, live, foregroundEpoch) {
        val continueMotion = live && wasLive[0]
        wasLive[0] = live
        if (!continueMotion) motion.snapTo(target ?: 0f)
        else {
            val destination = if (circular) {
                val delta = ((target!! - motion.value) % 360f + 540f) % 360f - 180f
                motion.value + delta
            } else target!!
            motion.animateTo(destination, spring(dampingRatio = 1f, stiffness = 450f,
                visibilityThreshold = if (circular) .02f else .0005f))
        }
    }
    return motion.asState()
}
