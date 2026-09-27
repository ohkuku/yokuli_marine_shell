package com.yokuli.marine.shell.rebuild

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.YokuliBrandArrival
import com.yokuli.marine.core.design.YokuliBrandColors
import com.yokuli.shell.engine.ShellVisualSurface
import java.util.concurrent.atomic.AtomicBoolean

/** 只为本次 Shell 进程的显式冷启动领一次动效；通知、配置恢复和热返回不重新领用。 */
internal object BrandArrivalSession {
    private val claimed = AtomicBoolean(false)
    fun claim(intent: Intent?, restoring: Boolean): Boolean {
        if (restoring || intent?.action != Intent.ACTION_MAIN) return false
        if (!intent.hasCategory(Intent.CATEGORY_LAUNCHER) && !intent.hasCategory(Intent.CATEGORY_HOME)) return false
        if (intent.hasExtra("yokuli.notice.id") || intent.hasExtra("yokuli.ais.target") || intent.hasExtra("yokuli.ais.route")) return false
        return claimed.compareAndSet(false, true)
    }
}

/** 桌面从首帧就已工作；动效不等待 Core、不控制导航、不延长系统 Splash。 */
@Composable
internal fun BrandArrivalHost(os: OsStore, finish: () -> Unit) {
    val done by rememberUpdatedState(finish)
    val progress = remember { Animatable(0f) }
    val shell by os.shell.engine.state.collectAsState()
    val obscured = os.notificationShade.blocksInput || shell.surface != ShellVisualSurface.Desktop
    LaunchedEffect(obscured) { if (obscured) done() }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(760, easing = LinearEasing))
        done()
    }
    if (obscured) return
    Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = 1f - ((progress.value - .73f) / .27f).coerceIn(0f,1f)
    }.background(YokuliBrandColors.Ink)
        .semantics { onClick(os.t("进入 Yokuli","Enter Yokuli")) { done(); true } }
        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); done() } }, contentAlignment = Alignment.Center) {
        YokuliBrandArrival({ progress.value }, Modifier.fillMaxWidth().padding(horizontal=28.dp).height(90.dp), Color.White)
    }
}
