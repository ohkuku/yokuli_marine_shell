package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*

@Composable
fun MapSourcePicker(os: OsStore, aisLayer:Boolean?=null, onDismiss: () -> Unit) {
    val c=LocalMetro.current
    val chartData by os.maps.charts.state.collectAsState()
    var ordering by rememberSaveable {mutableStateOf(false)}
    val selectedIds=os.maps.selectedDatasetIds
    val orderedDatasets=remember(chartData.datasets,selectedIds) {
        val byId=chartData.datasets.associateBy {it.id}
        selectedIds.mapNotNull(byId::get)+chartData.datasets.filterNot {it.id in selectedIds}
    }
    AppDialog(onDismissRequest=onDismiss) {
        AppDialogSurface {
            AppDialogTitle(os.t("地图来源","Map source"))
            AppSection(os.t("底图","Background"))
            fun choose(source:MapSource) {os.maps.select(source);onDismiss()}
            Column(Modifier.selectableGroup()) {
                for(source in listOf(MapSource.Offline,MapSource.Satellite)) {
                    val title=if(source==MapSource.Offline)os.t("地图","map") else os.t("卫星","satellite")
                    val available=source!=MapSource.Satellite || BuildConfig.GOOGLE_MAPS_CONFIGURED
                    val detail=when {
                        source==MapSource.Offline -> os.t("内置全球地图 · 无需联网","built-in world map · works offline")
                        !available -> os.t("此构建的卫星服务不可用","satellite service unavailable in this build")
                        else -> os.t("高清影像与地名 · 需要网络","high-resolution imagery & place names · internet required")
                    }
                    MapSourceOption(os,title,os.maps.source==source,detail,available) {choose(source)}
                }
                val folders=os.library.folders.filter {it.layerName!=null}
                folders.forEach {folder ->
                    val source=MapSource.CustomLayer(folder.id)
                    val count=os.library.folderFiles(folder).count {it.enabled && it.error==null}
                    MapSourceOption(os,folder.layerName.orEmpty(),os.maps.source==source,os.t("$count 张可用海图","$count available charts")) {choose(source)}
                }
                (os.maps.source as? MapSource.CustomLayer)?.takeIf {selected->folders.none {it.id==selected.layerId}}?.let {
                    MapSourceOption(os,os.t("图层暂不可用","layer unavailable"),true,os.t("在图册检查原文件夹","check its folder in chart library"),false) {}
                }
            }
            if(os.library.layers.isEmpty())Label(os.t("在图册连接文件夹并命名后，它会出现在这里。","Connect and name a folder in chart library to add it here."),16,c.muted)
            AppSection(os.t("海图数据","Chart data"))
            Column {
                if(chartData.datasets.isEmpty()&&!chartData.loading)Label(os.t("还没有航行数据。底图仍可查看，也可手动画线。","No navigation data yet. You can still view maps and draw routes manually."),14,c.muted)
                orderedDatasets.forEach { dataset ->
                    val priority=selectedIds.indexOf(dataset.id)
                    val selected=priority>=0
                    val detail=if(!dataset.offlineReadable)os.t("离线索引缺失 · 在图册恢复","Offline index missing · Restore in Library")else chartUseLabel(os,dataset.eligibility)
                    AppCheckRow(dataset.name,selected,
                        (if(selected)os.t("优先 ${priority+1} · ","Priority ${priority+1} · ")else "")+detail,enabled=selected||dataset.offlineReadable) {os.maps.includeDataset(dataset.id,!selected)}
                }
                if(!chartData.loading&&chartData.error==null)selectedIds.filter {id->chartData.datasets.none {it.id==id}}.forEach {id->
                    AppCheckRow(os.t("所选数据已移除","Selected dataset is missing")+" · "+id.take(8),true,
                        os.t("优先 ${selectedIds.indexOf(id)+1} · 取消选用，或在图册重新导入","Priority ${selectedIds.indexOf(id)+1} · Deselect or restore in Library")) {os.maps.includeDataset(id,false)}
                }
                if(chartData.loading)MetroProgress(os.t("正在读取数据图册","Reading chart data"))
                chartData.error?.let {Label(chartDataError(os,it),13,c.accentText)}
            }
            if(selectedIds.size>1)MenuRow(os.t("优先顺序","Priority order"),os.t("上方优先，未覆盖处使用后续资料","Earlier data takes priority; later data fills uncovered areas"),"layers") {ordering=true}
            MenuRow(os.t("管理与导入数据","Manage and import data"),icon="folder") {onDismiss();os.openLinked("library:data")}

            if(os.maps.saveFailed)Label(os.t("选择尚未保存到设备","selection could not be saved"),16)
            aisLayer?.let {AisLayerChoice(os,it)}
            MetroButton(os.t("关闭","close"),onDismiss)
        }
    }
    if(ordering)AppDialog(onDismissRequest={ordering=false}) {AppDialogSurface {
        AppDialogTitle(os.t("资料优先顺序","Data priority"))
        Label(os.t("覆盖重叠时优先使用上方资料，空白处使用后续资料。只调整已选资料，不改变它们的用途许可。","Earlier datasets take priority where coverage overlaps; later datasets fill uncovered areas. Reordering does not change permitted use."),14,c.muted)
        if(selectedIds.isEmpty())Label(os.t("当前没有选用资料","No data selected"),15,c.muted)
        selectedIds.forEachIndexed {index,id->
            val dataset=chartData.datasets.firstOrNull {it.id==id}
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Label("${index+1}. "+(dataset?.name ?: os.t("暂不可用","Unavailable")+" · "+id.take(8)),17)
                fun move(offset:Int) {
                    val current=os.maps.selectedDatasetIds
                    val from=current.indexOf(id);val to=from+offset
                    if(from<0||to !in current.indices)return
                    val next=current.toMutableList();next[from]=current[to];next[to]=id
                    os.maps.reorderDatasets(next)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MetroButton(os.t("上移","Move up"),{move(-1)},Modifier.weight(1f),enabled=index>0)
                    MetroButton(os.t("下移","Move down"),{move(1)},Modifier.weight(1f),enabled=index<selectedIds.lastIndex)
                }
            }
        }
        if(os.maps.saveFailed)Label(os.t("顺序尚未保存到设备","Order could not be saved to this device"),14,c.accentText)
        MetroButton(os.t("完成","Done"),{ordering=false})
    }}
}

/** 图源与系统偏好共用单选控件，以选中圆点表示当前值。 */
@Composable
internal fun MapSourceOption(os:OsStore,title:String,selected:Boolean,detail:String?=null,enabled:Boolean=true,onSelect:()->Unit) {
    ChoiceRow(title=title,selected=selected,subtitle=detail,enabled=enabled,onClick=onSelect)
}

@Composable
fun MapSourceButton(os:OsStore,modifier:Modifier=Modifier) {
    var choosing by rememberSaveable {mutableStateOf(false)}
    Label(os.maps.sourceName(os.chinese)+" ▾",14,LocalMetro.current.accent,modifier.clickable {choosing=true}.padding(10.dp))
    if(choosing)MapSourcePicker(os) {choosing=false}
}

/** A capability request. No business changes until the caller accepts the result. */
@Composable
fun MapPicker(os:OsStore,initialPoint:GeoPoint?,referenceScene:MapScene=MapScene(),onConfirm:(GeoPoint)->Unit,onCancel:()->Unit) {
    val density=LocalDensity.current
    val view=remember {MapViewState(initialPoint ?: referenceScene.vessel?.point ?: os.center,16.0).apply {showCrosshair=true;objectPickingEnabled=false}}
    AppDialog(onDismissRequest=onCancel,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        AppBackHandler(onBack=onCancel)
        Column(Modifier.fillMaxSize().background(LocalMetro.current.bg)) {
            PageHeader(os,os.t("选择位置","choose position"),hasLocalBack=true)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MarineMap(os.maps,referenceScene,view,Modifier.fillMaxSize())
                MapSourceButton(os,Modifier.align(Alignment.TopEnd).background(LocalMetro.current.bg))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged {view.bottomOverlayDp=with(density){it.height.toDp().value}}.background(LocalMetro.current.panel).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Label(os.formatCoordinates(view.center),20)
                    Label(os.t("拖动地图，把准星放到目标位置","move the map to place the crosshair"),14,LocalMetro.current.muted)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        MetroButton(os.t("取消","cancel"),onCancel,Modifier.weight(1f))
                        MetroButton(os.t("使用此位置","use this point"),{onConfirm(view.center)},Modifier.weight(1f),primary=true)
                    }
                }
            }
        }
    }
}
