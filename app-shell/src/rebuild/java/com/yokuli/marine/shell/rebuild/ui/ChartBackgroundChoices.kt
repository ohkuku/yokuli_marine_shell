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

/** 显示模式不会修改当前资料包，查询和规划始终沿用包内数据。 */
@Composable internal fun ChartBackgroundChoices(os:OsStore,onSelected:()->Unit={}) {
    val selected=os.maps.source
    ChoiceRow(os.t("底图","Basemap"),selected==MapSource.Offline,
        os.t("内置离线地图","Built-in offline map")) {os.maps.select(MapSource.Offline);onSelected()}
    ChoiceRow(os.t("卫星","Satellite"),selected==MapSource.Satellite,
        if(BuildConfig.GOOGLE_MAPS_CONFIGURED)os.t("需要网络","Requires a connection")else os.t("此版本未配置卫星服务","Satellite service is not configured in this build"),
        enabled=BuildConfig.GOOGLE_MAPS_CONFIGURED) {os.maps.select(MapSource.Satellite);onSelected()}
    ChoiceRow(os.t("包内海图","Collection charts"),selected is MapSource.CustomLayer,
        os.maps.activeBundle?.name?:os.t("先选择资料包","Choose a collection first"),enabled=os.maps.activeBundle?.chartFolderId!=null) {os.maps.selectCustom();onSelected()}
}

internal fun selectChartFolder(os:OsStore,id:String?):Boolean {
    if(id==null) {os.maps.selectBundle(null);return true}
    val owner=os.maps.bundles.bundles.firstOrNull {it.chartFolderId==id}?:return false
    os.maps.selectBundle(owner.id)
    return true
}

/** 从地图切换整套资料，而不是另建一个与规划来源脱节的文件夹选择。 */
@Composable internal fun CustomChartFolderSetting(os:OsStore,onSelected:()->Unit={}) {
    var choosing by rememberSaveable {mutableStateOf(false)}
    MenuRow(os.t("资料包","Collection"),os.maps.activeBundle?.name?:os.t("未选择","None"),"folder") {choosing=true}
    if(choosing)AppDialog(onDismissRequest={choosing=false}) {AppDialogSurface {
        AppDialogTitle(os.t("选择资料包","Choose a collection"))
        ChoiceRow(os.t("不使用资料包","No collection"),os.maps.activeBundleId==null,
            os.t("内置底图 · 手动规划","Built-in basemap · manual routing")) {os.maps.selectBundle(null);choosing=false;onSelected()}
        val bundles=os.maps.bundles.bundles
        if(bundles.isEmpty())Label(os.t("先到图册导入或创建资料包。","Import or create a collection in Library."),14,LocalMetro.current.muted)
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max=320.dp)) {
            items(bundles,key={it.id}) {bundle->
                ChoiceRow(bundle.name,os.maps.activeBundleId==bundle.id,
                    when {bundle.chartFolderId!=null&&bundle.datasetId!=null->os.t("海图 + 数据","Charts + data");bundle.chartFolderId!=null->os.t("仅海图","Charts only");bundle.datasetId!=null->os.t("数据 · 内置底图","Data · built-in basemap");else->os.t("尚无内容","No content yet")},
                    enabled=!os.maps.bundles.busy) {os.maps.selectBundle(bundle.id);choosing=false;onSelected()}
            }
        }
        if(os.shell.appForPage(os.page)?.app!=AppId.LIBRARY)
            MenuRow(os.t("在图册管理","Manage in Library"),icon="settings") {choosing=false;os.openLinked(os.maps.activeBundleId?.let {"library:bundle/$it"}?:"library")}
        MetroButton(os.t("返回","Back"),{choosing=false})
    }}
}

@Composable internal fun ChartSourceSaveStatus(os:OsStore) {
    if(os.maps.saveFailed) {
        Label(os.t("海图设置尚未保存","Chart settings are not saved"),13,LocalMetro.current.accentText)
        MetroButton(os.t("重试保存","Retry saving"),{os.maps.retrySaveSelection()})
    }
}
