package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** MDL2 ContentDialog 内容面：方角、主题层级和固定间距，长文/键盘压缩时仍可滚动。 */
@Composable fun AppDialogSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalMetro.current
    BoxWithConstraints(modifier.widthIn(max = 480.dp).fillMaxWidth()) {
        val availableHeight = if (maxHeight.value.isFinite()) maxHeight else 720.dp
        Column(Modifier.fillMaxWidth().heightIn(max = availableHeight)
            .background(colors.panel).border(1.dp, colors.controlStroke)
            .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
