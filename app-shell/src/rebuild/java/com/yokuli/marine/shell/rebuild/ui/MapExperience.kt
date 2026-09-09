package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*

@Composable
fun MapSourcePicker(os: OsStore, onDismiss: () -> Unit) {
    val c=LocalMetro.current
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max=650.dp).background(c.bg).border(1.dp,c.muted).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Label(os.t("地图来源","map source"),36)
            fun choose(source:MapSource) {os.maps.select(source);onDismiss()}
            for(source in listOf(MapSource.Online,MapSource.Satellite)) {
                val title=if(source==MapSource.Online)os.t("在线","online") else os.t("卫星","satellite")
                val available=source!=MapSource.Satellite || BuildConfig.GOOGLE_MAPS_CONFIGURED
                MenuRow((if(os.maps.source==source)"✓  " else "")+title,if(!available)os.t("此构建的卫星服务不可用","satellite service unavailable in this build")else null) {
                    if(available)choose(source)
                }
            }
            os.library.folders.filter {it.layerName!=null}.forEach {folder ->
                val source=MapSource.CustomLayer(folder.id)
                val count=os.library.folderFiles(folder).count {it.enabled && it.error==null}
                MenuRow((if(os.maps.source==source)"✓  " else "")+folder.layerName,os.t("$count 张可用海图","$count available charts")) {choose(source)}
            }
            if(os.library.layers.isEmpty())Label(os.t("在海图库连接文件夹并命名后，它会出现在这里。","Connect and name a folder in chart library to add it here."),16,c.muted)
            if(os.maps.saveFailed)Label(os.t("选择尚未保存到设备","selection could not be saved"),16)
            MetroButton(os.t("管理海图库","manage chart library"),{onDismiss();os.open("library")})
            MetroButton(os.t("关闭","close"),onDismiss)
        }
    }
}

@Composable
fun MapSourceButton(os:OsStore,modifier:Modifier=Modifier) {
    var choosing by remember {mutableStateOf(false)}
    Label(os.maps.sourceName(os.chinese)+" ▾",14,LocalMetro.current.accent,modifier.clickable {choosing=true}.padding(10.dp))
    if(choosing)MapSourcePicker(os) {choosing=false}
}

/** A capability request. No business changes until the caller accepts the result. */
@Composable
fun MapPicker(os:OsStore,initialPoint:GeoPoint?,referenceScene:MapScene=MapScene(),onConfirm:(GeoPoint)->Unit,onCancel:()->Unit) {
    val view=remember {MapViewState(initialPoint ?: referenceScene.vessel?.point ?: os.center,16.0).apply {showCrosshair=true}}
    Dialog(onDismissRequest=onCancel,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BackHandler(onBack=onCancel)
        Column(Modifier.fillMaxSize().background(LocalMetro.current.bg).systemBarsPadding()) {
            PageHeader(os,os.t("选择位置","choose position"),onBack=onCancel)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MarineMap(os.maps,referenceScene,view,Modifier.fillMaxSize())
                MapSourceButton(os,Modifier.align(Alignment.TopEnd).background(LocalMetro.current.bg))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom=80.dp).background(LocalMetro.current.panel).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
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
