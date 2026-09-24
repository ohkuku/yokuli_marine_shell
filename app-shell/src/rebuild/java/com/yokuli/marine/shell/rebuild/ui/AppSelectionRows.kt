package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.W10CheckIndicator

/** 多选使用统一 Windows 10 Mobile 复选视觉；整行只有一份可聚焦、可读的选中语义。 */
@Composable internal fun AppCheckRow(
    title: String,
    selected: Boolean,
    detail: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalMetro.current
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        W10CheckIndicator(selected, enabled, Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(title, 15, if (enabled) c.fg else c.muted)
            if (!detail.isNullOrBlank()) Label(detail, 12, c.muted)
        }
    }
}
