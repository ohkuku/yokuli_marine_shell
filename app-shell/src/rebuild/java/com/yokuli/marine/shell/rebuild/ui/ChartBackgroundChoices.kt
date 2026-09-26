package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.rebuild.AppId
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.chart.MapSource

/** 切换海图和图册共用这三项：点选立即生效，没有待确认的临时选项。 */
@Composable internal fun ChartBackgroundChoices(os:OsStore,onSelected:()->Unit={}) {
    val selected=os.maps.source
    ChoiceRow(os.t("底图","Basemap"),selected==MapSource.Offline,
        os.t("内置离线地图","Built-in offline map")) {os.maps.select(MapSource.Offline);onSelected()}
    ChoiceRow(os.t("卫星","Satellite"),selected==MapSource.Satellite,
        if(BuildConfig.GOOGLE_MAPS_CONFIGURED)os.t("需要网络","Requires a connection")else os.t("此版本未配置卫星服务","Satellite service is not configured in this build"),
        enabled=BuildConfig.GOOGLE_MAPS_CONFIGURED) {os.maps.select(MapSource.Satellite);onSelected()}
    ChoiceRow(os.t("自定义","Custom"),selected is MapSource.CustomLayer,
        os.maps.customFolderName(os.chinese)) {os.maps.selectCustom();onSelected()}
}

/** 选择文件夹与查看/管理文件夹分开；选择本身不导航、不要求再按一次使用。 */
internal fun selectChartFolder(os:OsStore,id:String?):Boolean {
    if(id==null) {os.maps.select(MapSource.CustomLayer(""));return true}
    val folder=os.library.folders.firstOrNull {it.id==id} ?: return false
    // 已连接但尚未命名的文件夹用自己的名称；无需命名弹窗才能选择它。
    if(folder.layerName==null)os.library.setLayer(folder,folder.name.ifBlank {os.t("海图文件夹","Chart folder")})
    os.maps.select(MapSource.CustomLayer(folder.id))
    return true
}

@Composable internal fun CustomChartFolderSetting(os:OsStore,onSelected:()->Unit={}) {
    var choosing by rememberSaveable {mutableStateOf(false)}
    MenuRow(os.t("自定义海图文件夹","Custom chart folder"),os.maps.customFolderName(os.chinese),"folder") {choosing=true}
    if(choosing)AppDialog(onDismissRequest={choosing=false}) {AppDialogSurface {
        AppDialogTitle(os.t("选择海图文件夹","Choose a chart folder"))
        ChoiceRow(os.t("不选择文件夹","No folder"),os.maps.customLayerId==null,
            os.t("自定义背景留空","Leave the custom background empty")) {selectChartFolder(os,null);choosing=false;onSelected()}
        val folders=os.library.folders
        if(folders.isEmpty())Label(os.t("还没有海图文件夹。可在图册中连接文件夹或导入 MBTiles。","No chart folders yet. Connect a folder or import MBTiles in Library."),14,LocalMetro.current.muted)
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max=320.dp)) {
            items(folders,key={it.id}) {folder->
                val count=os.library.folderFiles(folder).count {it.enabled&&it.error==null}
                ChoiceRow(folder.layerName ?: folder.name,os.maps.customLayerId==folder.id,
                    os.t("$count 张海图参与显示","$count charts included")) {
                    if(selectChartFolder(os,folder.id)){choosing=false;onSelected()}
                }
            }
        }
        if(os.maps.customLayerId!=null&&folders.none {it.id==os.maps.customLayerId})
            Label(os.t("之前选择的文件夹已不可用，未自动换成其他文件夹。","The selected folder is unavailable; no replacement has been selected automatically."),13,LocalMetro.current.muted)
        if(os.shell.appForPage(os.page)?.app!=AppId.LIBRARY)
            MenuRow(os.t("在图册管理文件夹","Manage folders in Library"),icon="settings") {choosing=false;os.openLinked("library")}
        MetroButton(os.t("返回","Back"),{choosing=false})
    }}
}

@Composable internal fun ChartSourceSaveStatus(os:OsStore) {
    if(os.maps.saveFailed) {
        Label(os.t("背景设置尚未保存","Background settings are not saved"),13,LocalMetro.current.accentText)
        MetroButton(os.t("重试保存","Retry saving"),{os.maps.select(os.maps.source)})
    }
}
