package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import com.yokuli.shell.contract.TileBinding
import com.yokuli.shell.contract.TileBindingKind

/** 应用只提供眼前内容的稳定身份；编辑、去重、保存与返回都由 Shell 共用会话承担。 */
@Composable internal fun PinTileAction(os: OsStore, binding: TileBinding, beforeOpen: () -> Unit = {}) {
    val launcher by os.shell.engine.state.collectAsState()
    val contentState by os.persistenceState.collectAsState()
    val alreadyPinned = launcher.start.document.placements.any { tileBinding(it).contentKey == binding.contentKey }
    // 观察真正的保存回执，不能因乐观内存里已出现对象就放行固定。
    val issue = contentState.let { if (alreadyPinned) null else os.shell.tileWorkshop.contentIssue(binding) }
    val choice = tileContentDescriptor(os, binding)
    val enabled = LocalInternalAppInputEnabled.current
    val label = if (alreadyPinned) os.t("已在开始屏幕 · 编辑或查看", "On Start · Edit or view")
        else os.t("固定到开始屏幕", "Pin to Start")
    val open = { beforeOpen(); os.shell.tileWorkshop.beginAdd(binding) }
    if (issue != null || !enabled) Column {
        MetroButton(label, open, enabled = false)
        if (issue != null) Label(issue, 13, LocalMetro.current.muted, Modifier.padding(top = 6.dp))
    } else MenuRow(label, tileText(os, choice.title), "start", open)
}

internal fun currentTaskTileBinding(role: String) = TileBinding("yokuli", TileBindingKind.CURRENT_TASK, role)
internal fun savedPlaceTileBinding(id: String) = TileBinding("yokuli", TileBindingKind.SAVED_PLACE, id)
internal fun savedRouteTileBinding(id: String) = TileBinding("yokuli", TileBindingKind.SAVED_ROUTE, id)
internal fun aisOverviewTileBinding() = TileBinding("yokuli", TileBindingKind.OVERVIEW, "aisTraffic")
