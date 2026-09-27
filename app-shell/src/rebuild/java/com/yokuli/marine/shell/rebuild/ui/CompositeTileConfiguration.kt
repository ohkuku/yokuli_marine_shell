package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.layout.TileDocumentEntry

/** 组合只拥有内容引用、顺序和去向；每个面板仍读取原领域所有者。 */
@Composable internal fun CompositeTileConfiguration(os:OsStore,config:TilePresentation,onChange:(TilePresentation)->Unit) {
    val panels=tileCompositePanelIds(config)
    val choices=tileCompositePanelChoices(os)
    var adding by rememberSaveable {mutableStateOf(false)}
    Field(os.t("磁贴名称","Tile name"),config.title.orEmpty(),{onChange(config.copy(title=it.filterNot(Char::isISOControl).take(48).takeIf(String::isNotBlank)))})
    Label(os.t("组合内容 · ${panels.size}/4","Contents · ${panels.size}/4"),15,LocalMetro.current.muted)
    Column {
        panels.forEachIndexed {index,id->
            val choice=choices.firstOrNull {it.binding.contentId==id}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Label("${index+1}",12,LocalMetro.current.muted,Modifier.width(24.dp))
                Label(choice?.let {tileText(os,it.title)} ?: id,15,modifier=Modifier.weight(1f),maxLines=2)
                TileOrderButton("↑",os.t("上移","Move up"),index>0) {
                    val next=panels.toMutableList();next[index]=next[index-1];next[index-1]=id
                    onChange(config.copy(compositePanels=next))
                }
                TileOrderButton("↓",os.t("下移","Move down"),index<panels.lastIndex) {
                    val next=panels.toMutableList();next[index]=next[index+1];next[index+1]=id
                    onChange(config.copy(compositePanels=next))
                }
                TileOrderButton("−",os.t("移除这项内容","Remove content"),panels.size>2) {
                    onChange(config.copy(compositePanels=panels.filterNot {it==id}))
                }
            }
        }
        if(panels.size<4)MenuRow(os.t("添加内容","Add content"),icon="plus") {adding=true}
        Label(os.t("保留 2–4 项。顺序与预览一致，共享一个打开目标。","Choose 2–4 items. Their order matches the preview; the tile has one destination."),12,LocalMetro.current.muted)
    }
    if(adding)AppDialog(onDismissRequest={adding=false}) {AppDialogSurface {
        AppDialogTitle(os.t("加入组合","Add to your tile"))
        choices.filter {it.binding.contentId !in panels}.forEach {choice->
            MenuRow(tileText(os,choice.title),tileText(os,choice.subtitle)) {
                if(panels.size<4)onChange(config.copy(compositePanels=panels+choice.binding.contentId))
                adding=false
            }
        }
        MetroButton(os.t("返回","Back"),{adding=false})
    }}
}

@Composable private fun TileOrderButton(text:String,description:String,enabled:Boolean,onClick:()->Unit) {
    Box(Modifier.size(44.dp).semantics {contentDescription=description;if(!enabled)disabled()}
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Label(text,20,if(enabled)LocalMetro.current.fg else LocalMetro.current.disabled)
    }
}

/** 尺寸由真实网格比例表达，避免四个文字单选挤成一行。 */
@Composable internal fun TileSizeChoices(os:OsStore,sizes:List<MarineTileSize>,selected:MarineTileSize,
    enabled:Boolean,onChange:(MarineTileSize)->Unit) {
    val c=LocalMetro.current
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        sizes.forEach {size->
            Column(Modifier.weight(1f).selectable(size==selected,enabled=enabled,role=Role.RadioButton,onClick={onChange(size)})
                .padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Canvas(Modifier.size(44.dp)) {
                    val w=this.size.width*size.columns/4f;val h=this.size.height*size.rows/4f
                    val origin=Offset((this.size.width-w)/2,(this.size.height-h)/2)
                    drawRect(if(size==selected)c.fg else c.controlStroke,origin,Size(w,h),style=Stroke(2.dp.toPx()))
                    if(size==selected)drawRect(c.fg.copy(alpha=.18f),origin,Size(w,h))
                }
                Label(tileSizeName(os,size),13,if(size==selected)c.fg else c.muted)
                Label("${size.columns} × ${size.rows}",11,c.muted)
            }
        }
    }
}

private data class TileDestinationOption(val target:String,val title:String,val group:Int)
private fun tileDestinationOptions(os:OsStore):List<TileDestinationOption> {
    fun shortcut(target:String,zh:String,en:String)=TileDestinationOption(target,os.t(zh,en),1)
    return AppId.entries.map {TileDestinationOption(ShellApp(it).page,os.title(it),0)}+listOf(
        shortcut("task:navigation","继续查看导航","Current navigation"),
        shortcut("task:anchorWatch","当前守锚","Current anchor watch"),
        shortcut("task:recording","当前航行记录","Current recording"),
        shortcut("instruments:tab:navigation","驾驶台 · 航行","Helm · Under way"),
        shortcut("instruments:tab:sailing","驾驶台 · 风况","Helm · Wind"),
        shortcut("instruments:tab:attitude","驾驶台 · 船姿","Helm · Attitude"),
        shortcut("instruments:tab:weather","驾驶台 · 气象","Helm · Weather"),
        shortcut("library:data","图册 · 航行数据","Chart Library · Navigation data"),
        shortcut("places:routes","我的航线","My routes"),
        shortcut("places:anchorages","我的锚地","My anchorages"),
        shortcut("settings:units","单位与坐标","Units and coordinates"),
        shortcut("settings:start","开始屏幕","Start settings"),
        shortcut("settings:exit","退出前确认","Confirm exit"),
    )+os.allPlaces.map {TileDestinationOption("tileplace:${it.id}",os.t("地点 · ","Place · ")+it.name,2)}+
        os.routes.map {TileDestinationOption("tileroute:${it.id}",os.t("航线 · ","Route · ")+it.name,2)}
}

/** 去向只能是已声明的内部页面；不会把任意字符串变成 Intent 或开始/停止任务。 */
internal fun tileLaunchDestination(os:OsStore,tile:TileDocumentEntry):String {
    val binding=tileBinding(tile)
    val target=tile.presentation.tapTarget?.takeIf {binding.kind==TileBindingKind.COMPOSITE && TileCompositePolicy.isAllowedTarget(it)}
        ?: return tileContentDescriptor(os,binding).launchToken
    return AppId.entries.firstOrNull {ShellApp(it).page==target}?.let {ShellApp(it).rootToken.value} ?: target
}

@Composable internal fun CompositeTileDestination(os:OsStore,config:TilePresentation,enabled:Boolean,onChange:(TilePresentation)->Unit) {
    var open by rememberSaveable {mutableStateOf(false)}
    val options=tileDestinationOptions(os)
    val target=config.tapTarget
    val current=options.firstOrNull {it.target==target}?.title ?: when {
        target==null->os.t("驾驶台首页","Helm home")
        target.startsWith("tileplace:")->os.t("原收藏地点（已移除）","Saved place (removed)")
        target.startsWith("tileroute:")->os.t("原收藏航线（已移除）","Saved route (removed)")
        else->os.t("目标暂不可用 · 打开驾驶台","Destination unavailable · Open Helm")
    }
    MenuRow(os.t("点按后打开","Tap to open"),current,"chevron_right") {if(enabled)open=true}
    if(open)AppDialog(onDismissRequest={open=false}) {AppDialogSurface {
        AppDialogTitle(os.t("这块磁贴打开哪里","Choose a destination"))
        var query by rememberSaveable {mutableStateOf("")}
        var group by rememberSaveable {mutableIntStateOf(1)}
        ChoiceRow(os.t("默认 · 驾驶台首页","Default · Helm home"),config.tapTarget==null) {
            onChange(config.copy(tapTarget=null));open=false
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf(os.t("应用","Apps"),os.t("捷径","Shortcuts"),os.t("收藏","Saved")).forEachIndexed {index,name->
                ChoiceRow(name,index==group,modifier=Modifier.weight(1f)) {group=index;query=""}
            }
        }
        Field(os.t("查找目标","Find destination"),query,{query=it.take(80)})
        val results=options.filter {it.group==group && (query.isBlank()||it.title.contains(query,ignoreCase=true))}
        results.take(40).forEach {item->
            ChoiceRow(item.title,config.tapTarget==item.target) {onChange(config.copy(tapTarget=item.target));open=false}
        }
        if(results.size>40)Label(os.t("输入名称以缩小范围","Type a name to narrow the list"),13,LocalMetro.current.muted)
        if(results.isEmpty())Label(os.t("没有匹配的目标","No matching destinations"),14,LocalMetro.current.muted)
        Label(os.t("捷径打开对应页面；开始、暂停或退出仍由你确认。","Shortcuts open their page. Starting, pausing or exiting remains your choice."),12,LocalMetro.current.muted)
        MetroButton(os.t("取消","Cancel"),{open=false})
    }}
}
