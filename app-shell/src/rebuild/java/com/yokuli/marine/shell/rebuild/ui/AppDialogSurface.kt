package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yokuli.marine.core.design.LocalReducedMotion
import com.yokuli.marine.core.design.W10MobileMotion
import com.yokuli.shell.compose.LocalInternalAppInputEnabled

/** 对话框属于发起页面；离场帧、隐藏 Pivot 与通知后的页面不能保留一个抢输入的窗口。 */
@Composable internal fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable () -> Unit,
) {
    if (!LocalInternalAppInputEnabled.current) return
    Dialog(onDismissRequest = onDismissRequest, properties = properties, content = content)
}

/** W10M ContentDialog：方角、主题面板、短促入场；键盘压缩时滚动内容，保留动作可达。 */
@Composable fun AppDialogSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalMetro.current
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(if (reduced) 0 else W10MobileMotion.ContentMillis, easing = W10MobileMotion.EntranceEasing)) }
    BoxWithConstraints(modifier.padding(horizontal = 18.dp, vertical = 20.dp).widthIn(max = 480.dp).fillMaxWidth()) {
        val availableHeight = if (maxHeight.value.isFinite()) maxHeight else 720.dp
        Column(Modifier.fillMaxWidth().heightIn(max = availableHeight)
            .graphicsLayer { alpha = progress.value; scaleX = 1.035f - .035f * progress.value; scaleY = scaleX; translationY = (1f - progress.value) * 8.dp.toPx() }
            .background(colors.panel).border(1.dp, colors.controlStroke)
            .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
