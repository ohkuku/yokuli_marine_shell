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
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max=650.dp).background(c.bg).border(1.dp,c.muted).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Label(os.t("地图来源","map source"),40)
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
            Label(os.t("全球地图已内置，自定义海图下方也保留这张离线底图。Natural Earth 提供概略陆地与海岸，不含水深或航行障碍物。","The world map is built in and remains beneath custom charts. Natural Earth provides general land and coastlines, without depths or navigation hazards."),14,c.muted)
            if(os.maps.saveFailed)Label(os.t("选择尚未保存到设备","selection could not be saved"),16)
            aisLayer?.let {AisLayerChoice(os,it)}
            MetroButton(os.t("关闭","close"),onDismiss)
        }
    }
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
    val view=remember {MapViewState(initialPoint ?: referenceScene.vessel?.point ?: os.center,16.0).apply {showCrosshair=true}}
    Dialog(onDismissRequest=onCancel,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
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
