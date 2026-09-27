package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.engine.LauncherAction
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.StartColumnLayout

/** 列数是原 StartDocument 的一部分；预览使用同一排布器，保存失败不会伪装已选中。 */
@Composable internal fun StartColumnsSettings(os:OsStore) {
    val state by os.shell.engine.state.collectAsState()
    val loaded by os.shell.persistence.loaded.collectAsState()
    val document=state.start.document
    val columns=WpReferenceProfiles.require(document.profileId).columnCount
    val c=LocalMetro.current
    AppSection(os.t("桌面密度","Start layout"))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        listOf(4,6).forEach {count->
            val packed=remember(document,count) {
                val proposal=StartColumnLayout.change(document,WpReferenceProfiles.withColumns(document.profileId,count))
                AdaptiveTilePacker.pack(proposal,count)
            }
            val selected=columns==count
            Column(Modifier.weight(1f).selectable(selected,enabled=loaded,role=Role.RadioButton,
                onClick={os.shell.dispatch(LauncherAction.SetStartColumns(count))}).padding(vertical=8.dp),
                verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Canvas(Modifier.fillMaxWidth().height(144.dp)) {
                    val gap=3.dp.toPx();val border=6.dp.toPx()
                    drawRect(if(selected)c.fg else c.controlStroke,style=Stroke(if(selected)2.dp.toPx()else 1.dp.toPx()))
                    val cell=(size.width-2*border-(count-1)*gap)/count
                    packed.tiles.forEach {tile->
                        val x=border+tile.cell.column*(cell+gap);val y=border+tile.cell.row*(cell+gap)
                        val height=tile.entry.size.rows*cell+(tile.entry.size.rows-1)*gap
                        if(y<size.height-border)drawRect(c.fg.copy(alpha=if(selected).8f else .35f),Offset(x,y),
                            Size(tile.entry.size.columns*cell+(tile.entry.size.columns-1)*gap,minOf(height,size.height-border-y)))
                    }
                }
                Label(os.t("$count 列","$count columns"),16,if(selected)c.fg else c.muted)
                Label(if(count==4)os.t("看得更大","Larger tiles")else os.t("一眼看更多","More at a glance"),12,c.muted)
            }
        }
    }
    Label(os.t("按当前顺序重新排列，保留所有磁贴与内容。4×4 在两种布局中均可用。",
        "Reflows your current order, keeping every tile and its content. Large 4×4 tiles work in both layouts."),13,c.muted)
}
