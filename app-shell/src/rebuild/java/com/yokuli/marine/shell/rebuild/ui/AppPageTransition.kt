package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.W10MobileMotion
import com.yokuli.shell.compose.LocalInternalAppInputEnabled

/**
 * 应用内层级转场只消费现有页面路径，不另建导航栈或业务状态。
 * 相同页面键复用组合；退出页仅保留画面，不能接受返回、触摸或无障碍操作。
 * 内容始终占用同一尺寸；此容器不用于复制持有原生地图的页面。
 */
@Composable
internal fun <T> AppPageTransition(
    targetState: T,
    pageKey: (T) -> Any,
    pageDepth: (T) -> Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val distance = with(LocalDensity.current) { 32.dp.roundToPx() }
    val activePageKey = pageKey(targetState)
    val parentInputEnabled = LocalInternalAppInputEnabled.current
    Box(modifier.fillMaxSize().clipToBounds()) {
        AnimatedContent(
            targetState = targetState,
            modifier = Modifier.fillMaxSize(),
            contentKey = pageKey,
            transitionSpec = {
                val direction = if (pageDepth(this.targetState) < pageDepth(this.initialState)) -1 else 1
                ((slideInHorizontally(tween(W10MobileMotion.PageMillis, easing = W10MobileMotion.EntranceEasing)) {
                    direction * distance
                } + fadeIn(tween(W10MobileMotion.ContentMillis))) togetherWith
                    (slideOutHorizontally(tween(W10MobileMotion.ContentMillis, easing = W10MobileMotion.ExitEasing)) {
                        -direction * distance / 2
                    } + fadeOut(tween(W10MobileMotion.ContentMillis)))).using(null)
            },
            label = "app-page",
        ) { page ->
            val inputEnabled = parentInputEnabled && pageKey(page) == activePageKey
            val inputBarrier = if (inputEnabled) Modifier else Modifier
                .clearAndSetSemantics { }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            CompositionLocalProvider(LocalInternalAppInputEnabled provides inputEnabled) {
                Box(Modifier.fillMaxSize().then(inputBarrier)) { content(page) }
            }
        }
    }
}
