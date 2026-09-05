package com.yokuli.marine.core.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
 * 中文：Shell 级 WP 动效宿主；AnimatedContent 保留离场内容，绘制层补充透视进场。
 * English: Shell-level WP motion host with retained exit content and perspective entrance.
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
        WpPerspectiveEntrance(
            motionKey = surface as Any,
            plan = plan,
            animate = hasRenderedInitialSurface,
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
                animationSpec = tween(plan.durationMillis, easing = FastOutSlowInEasing),
            )
        }
    }
    val perspectiveEnabled = plan.family == WpMotionFamily.TURNSTILE || plan.family == WpMotionFamily.SWIVEL
    Box(
        Modifier
            .onSizeChanged { measuredSize = it }
            .graphicsLayer {
                if (perspectiveEnabled) {
                    val remaining = 1f - progress.value
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

/** 中文：可错峰的 WP 短进场动效。 English: Short, staggerable WP content entrance. */
@Composable
fun Modifier.wpEntrance(motionKey: Any, order: Int = 0): Modifier {
    val progress = remember(motionKey, order) { Animatable(0f) }
    val density = LocalDensity.current.density
    LaunchedEffect(motionKey, order) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 210,
                delayMillis = order.coerceIn(0, 6) * 34,
                easing = LinearOutSlowInEasing,
            ),
        )
    }
    return graphicsLayer {
        alpha = progress.value
        translationX = (1f - progress.value) * 34f * density
        rotationY = (1f - progress.value) * -4f
        transformOrigin = TransformOrigin(0f, .5f)
        cameraDistance = 12f * density
    }
}

/**
 * 中文：按触点位置倾斜；必须与点击动作共用 interactionSource，以保留无障碍语义。
 * English: Position-aware tilt sharing the clickable interaction source for accessibility.
 */
@Composable
fun Modifier.wpTilt(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    maximumDegrees: Float = 5f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (enabled && pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 70 else 115, easing = FastOutSlowInEasing),
        label = "wp-pointer-tilt",
    )
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    var pointerPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density

    return this
        .onSizeChanged {
            measuredSize = it
            if (pointerPosition == Offset.Zero) {
                pointerPosition = Offset(it.width / 2f, it.height / 2f)
            }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull { it.pressed }?.let { pointerPosition = it.position }
                }
            }
        }
        .graphicsLayer {
            if (measuredSize.width > 0 && measuredSize.height > 0) {
                val plan = WpPressPolicy.resolve(
                    normalizedX = pointerPosition.x / measuredSize.width,
                    normalizedY = pointerPosition.y / measuredSize.height,
                    pressProgress = pressProgress,
                    maximumDegrees = maximumDegrees,
                )
                rotationX = plan.rotationXDegrees
                rotationY = plan.rotationYDegrees
                scaleX = plan.scale
                scaleY = plan.scale
                cameraDistance = 12f * density
            }
        }
}
