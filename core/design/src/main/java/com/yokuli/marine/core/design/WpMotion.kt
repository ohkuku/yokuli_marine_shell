package com.yokuli.marine.core.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize

/**
 * W10M Shell 过渡宿主：保留离场页面，当前默认短位移/连续缩放。
 * 旧 TURNSTILE/SWIVEL 只为已有显式调用兼容，不再作为页面默认动画。
 */
@Composable
fun <T> WpSurfaceTransitionHost(
    targetState: T,
    transitionKind: WpSurfaceTransitionKind,
    reducedMotion: Boolean,
    timings: WpMotionTimings,
    modifier: Modifier = Modifier,
    content: @Composable (surface: T, heavyContentReady: Boolean) -> Unit,
) {
    val plan = WpMotionPolicy.resolve(transitionKind, reducedMotion, timings)
    var hasRenderedInitialSurface by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasRenderedInitialSurface = true }
    val transition = updateTransition(targetState, label = "wp-surface-transition-state")

    transition.AnimatedContent(
        modifier = modifier,
        transitionSpec = { plan.contentTransform() },
    ) { surface ->
        val heavyContentReady = surface == transition.targetState && !transition.isRunning
        // 离场页保留自己的入场参数；下一次导航不能把旧页面重新从零旋转一遍。
        val entrancePlan = remember(surface) { plan }
        val animateEntrance = remember(surface) { hasRenderedInitialSurface }
        WpPerspectiveEntrance(
            motionKey = surface as Any,
            plan = entrancePlan,
            animate = animateEntrance,
        ) {
            Box(Modifier.testTag("shell-transition-plane")) {
                content(surface, heavyContentReady)
            }
        }
    }
}

private fun WpMotionPlan.contentTransform(): ContentTransform = when (family) {
    WpMotionFamily.TURNSTILE ->
        fadeIn(
            tween(
                targetEntranceMillis + settleMillis,
                delayMillis = targetEntranceDelayMillis,
                easing = LinearOutSlowInEasing,
            ),
        ) togetherWith fadeOut(tween(contentExitMillis))
    WpMotionFamily.SWIVEL ->
        fadeIn(tween(targetEntranceMillis, easing = LinearOutSlowInEasing)) togetherWith
            fadeOut(tween(contentExitMillis))
    WpMotionFamily.SLIDE, WpMotionFamily.CONTINUUM ->
        fadeIn(tween(targetEntranceMillis, easing = W10MobileMotion.EntranceEasing)) togetherWith
            fadeOut(tween(contentExitMillis, easing = W10MobileMotion.ExitEasing))
    WpMotionFamily.FADE -> fadeIn(tween(targetEntranceMillis)) togetherWith fadeOut(tween(contentExitMillis))
    WpMotionFamily.NONE -> EnterTransition.None togetherWith ExitTransition.None
}

@Composable
private fun WpPerspectiveEntrance(
    motionKey: Any,
    plan: WpMotionPlan,
    animate: Boolean,
    content: @Composable () -> Unit,
) {
    val progress = remember(motionKey) { Animatable(if (animate) 0f else 1f) }
    val density = LocalDensity.current.density
    var measuredSize by remember(motionKey) { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(motionKey, animate, plan) {
        if (!animate || plan.durationMillis == 0) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(plan.durationMillis, easing = W10MobileMotion.EntranceEasing),
            )
        }
    }
    val perspectiveEnabled = plan.family == WpMotionFamily.TURNSTILE || plan.family == WpMotionFamily.SWIVEL
    Box(
        Modifier
            .onSizeChanged { measuredSize = it }
            .graphicsLayer {
                val remaining = 1f - progress.value
                if (plan.family == WpMotionFamily.SLIDE || plan.family == WpMotionFamily.CONTINUUM) {
                    translationX = measuredSize.width * plan.initialTranslationXFraction * remaining
                    translationY = plan.initialTranslationYDp * density * remaining
                    scaleX = 1f - (1f - plan.initialScale) * remaining
                    scaleY = scaleX
                }
                if (perspectiveEnabled) {
                    alpha = .32f + .68f * progress.value
                    rotationX = plan.initialRotationXDegrees * remaining
                    rotationY = plan.initialRotationYDegrees * remaining
                    translationX = measuredSize.width * plan.initialTranslationXFraction * remaining
                    transformOrigin = TransformOrigin(plan.transformOriginX, .5f)
                    cameraDistance = 12f * density
                }
            },
    ) { content() }
}

/** MDL2 的轻量错峰内容进场；改变资料不应更换 motionKey 来反复播放。 */
@Composable
fun Modifier.wpEntrance(motionKey: Any, order: Int = 0): Modifier {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember(motionKey, order, reducedMotion) { Animatable(if (reducedMotion) 1f else 0f) }
    val density = LocalDensity.current.density
    LaunchedEffect(motionKey, order, reducedMotion) {
        if (reducedMotion) progress.snapTo(1f) else progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = W10MobileMotion.ContentMillis,
                delayMillis = order.coerceIn(0, 5) * W10MobileMotion.StaggerMillis,
                easing = W10MobileMotion.EntranceEasing,
            ),
        )
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 16f * density
    }
}

/**
 * 默认平面按压缩放；只有显式启用倾斜的磁贴才追踪触点位置，旧角度限制到轻微透视。
 * 必须与点击动作共用 interactionSource，以保留取消、焦点和无障碍语义。
 */
@Composable
fun Modifier.wpTilt(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    maximumDegrees: Float = 0f,
): Modifier {
    val reducedMotion = LocalReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (enabled && !reducedMotion && pressed) 1f else 0f,
        animationSpec = tween(if (pressed) W10MobileMotion.PressMillis else W10MobileMotion.ReleaseMillis, easing = W10MobileMotion.EntranceEasing),
        label = "wp-pointer-tilt",
    )
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    var pointerPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    val tilt = maximumDegrees.coerceIn(0f, 1.5f)

    val tracked = if (tilt == 0f) this else this
        .onSizeChanged {
            measuredSize = it
            if (pointerPosition == Offset.Zero) {
                pointerPosition = Offset(it.width / 2f, it.height / 2f)
            }
        }
        .pointerInput(enabled, reducedMotion) {
            if (!enabled || reducedMotion) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull { it.pressed }?.let { pointerPosition = it.position }
                }
            }
        }
    return tracked.graphicsLayer {
        val plan = WpPressPolicy.resolve(
            normalizedX = if (measuredSize.width > 0) pointerPosition.x / measuredSize.width else .5f,
            normalizedY = if (measuredSize.height > 0) pointerPosition.y / measuredSize.height else .5f,
            pressProgress = pressProgress,
            maximumDegrees = tilt,
        )
        rotationX = plan.rotationXDegrees
        rotationY = plan.rotationYDegrees
        scaleX = plan.scale
        scaleY = plan.scale
        if (tilt > 0f) cameraDistance = 12f * density
    }
}
