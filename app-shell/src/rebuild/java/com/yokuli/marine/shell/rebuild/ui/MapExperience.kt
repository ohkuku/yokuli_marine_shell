package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*

/** 这里只切换显示背景；水深、障碍等航行数据仍由图册统一配置。 */
@Composable
fun MapSourcePicker(os: OsStore, aisLayer:Boolean?=null, onDismiss: () -> Unit) {
    AppDialog(onDismissRequest=onDismiss) {
        AppDialogSurface {
            AppDialogTitle(os.t("切换海图","Chart background"))
            ChartBackgroundChoices(os,onSelected=onDismiss)
            CustomChartFolderSetting(os,onSelected=onDismiss)
            ChartSourceSaveStatus(os)
            aisLayer?.let {AisLayerChoice(os,it)}
            MetroButton(os.t("关闭","Close"),onDismiss)
        }
    }
}

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
