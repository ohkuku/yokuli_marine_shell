package com.yokuli.marine.feature.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.distinctUntilChanged

enum class LauncherPagerPage(val index: Int) {
    START(0),
    ALL_APPS(1),
    ;

    companion object {
        fun from(index: Int): LauncherPagerPage = entries.first { it.index == index }
    }
}

/**
 * 中文：Foundation pager 负责逐帧 1:1 位移、轴锁、速度 settle、边界和动画中接管。
 * 本层只把已 settle 的页面与 Engine surface 同步，不猜测未在参考视频中观察到的阈值。
 *
 * English: Foundation owns per-frame 1:1 motion, axis locking, velocity settling,
 * bounds, and interruption. This layer only synchronizes settled pages with the
 * Engine and does not invent thresholds absent from the reviewed recording.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InteractiveLauncherPager(
    requestedPage: LauncherPagerPage,
    userScrollEnabled: Boolean,
    programmaticSettleMillis: Int,
    reducedMotion: Boolean,
    onPageSettled: (LauncherPagerPage) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (LauncherPagerPage) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = requestedPage.index) { LauncherPagerPage.entries.size }
    val latestRequested by rememberUpdatedState(requestedPage)
    val latestOnPageSettled by rememberUpdatedState(onPageSettled)

    LaunchedEffect(requestedPage, programmaticSettleMillis, reducedMotion) {
        if (pagerState.settledPage != requestedPage.index) {
            pagerState.animateScrollToPage(
                page = requestedPage.index,
                animationSpec = tween(if (reducedMotion) 120 else programmaticSettleMillis),
            )
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                val page = LauncherPagerPage.from(settledPage)
                if (page != latestRequested) latestOnPageSettled(page)
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize().testTag("interactive-launcher-pager"),
        userScrollEnabled = userScrollEnabled,
        beyondViewportPageCount = 1,
        overscrollEffect = null,
        key = { page -> LauncherPagerPage.from(page).name },
    ) { page ->
        content(LauncherPagerPage.from(page))
    }
}
