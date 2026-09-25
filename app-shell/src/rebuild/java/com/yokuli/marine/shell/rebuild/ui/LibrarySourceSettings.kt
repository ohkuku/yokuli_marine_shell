package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.chart.MapSource

/** 沿用既有持久化来源配置；图册是唯一选择入口，不为每条航线复制设置。 */
@Composable internal fun LibraryBackgroundSettings(os:OsStore) {
    val c=LocalMetro.current
    AppSection(os.t("当前海图背景","Chart background"))
    Label(os.maps.sourceName(os.chinese),17,c.accentText)
    ChoiceRow(os.t("内置地图","Built-in map"),os.maps.source==MapSource.Offline,
        os.t("无须海图文件夹，可离线查看","No chart folder needed; works offline")) {os.maps.select(MapSource.Offline)}
    ChoiceRow(os.t("卫星影像","Satellite imagery"),os.maps.source==MapSource.Satellite,
        os.t("仅作为背景，不参与水深或航线计算","Background only; not depth or routing data"),enabled=BuildConfig.GOOGLE_MAPS_CONFIGURED) {os.maps.select(MapSource.Satellite)}
    Label(os.t("使用自己的海图：打开下方海图文件夹，设为显示背景。航行数据在另一个页签配置，不受背景切换影响。","For your own charts, open a chart folder below and use it as the background. Navigation data is configured in the other tab and is independent of this choice."),13,c.muted)
    if(os.maps.saveFailed) {
        Label(os.t("背景设置尚未保存","Background settings are not saved"),13,c.accentText)
        MetroButton(os.t("重试保存来源","Retry saving sources"),{os.maps.select(os.maps.source)})
    }
    AppSection(os.t("海图文件夹","Chart folders"))
}

/** 配置放在数据页；普通规划只读这些选择，既不弹选择器也不改用途许可。 */
@Composable internal fun LibraryDataSettings(os:OsStore) {
    var open by rememberSaveable {mutableStateOf(false)}
    val c=LocalMetro.current
    val data by os.maps.charts.state.collectAsState()
    val selected=os.maps.selectedDatasetIds
    val insets=LocalShellHorizontalInsets.current
    Column(Modifier.fillMaxWidth().padding(start=insets.pageStart,end=insets.pageEnd,top=8.dp,bottom=8.dp)) {
        MenuRow(os.t("已启用的航行数据","Enabled navigation data"),
            os.t("${selected.size} 份 · 在这里统一配置","${selected.size} datasets · Configure here"),"settings") {open=true}
        Label(os.t("下方指定数据文件夹或导入数据包；启用后供所有航线使用，与海图文件夹分开。","Choose data folders or import packages below. Enabled data is shared by all routes and is separate from chart folders."),13,c.muted)
    }
    if(open)AppDialog(onDismissRequest={open=false}) {AppDialogSurface {
        AppDialogTitle(os.t("启用航行数据","Enable navigation data"))
        Label(os.t("只在图册设置一次。参考资料仍可查看；分析始终检查许可、版本、覆盖和水深，不会因启用就视为安全。","Set this once in Library. Reference data remains viewable; analysis still checks permission, versions, coverage and depths."),14,c.muted)
        val byId=data.datasets.associateBy {it.id}
        val ordered=selected.mapNotNull(byId::get)+data.datasets.filterNot {it.id in selected}
        if(data.loading)MetroProgress(os.t("正在读取数据目录","Reading data library"))
        if(!data.loading&&ordered.isEmpty())Label(os.t("先在数据页指定文件夹或导入数据包。","Choose a folder or import a package in the Data tab first."),15,c.muted)
        data.error?.let {Label(chartDataError(os,it),13,c.accentText)}
        ordered.forEach {dataset->
            val enabled=dataset.id in selected
            AppCheckRow(dataset.name,enabled,if(dataset.offlineReadable)chartUseLabel(os,dataset.eligibility)else os.t("离线索引缺失，需要恢复","Offline index missing; restore this dataset"),
                enabled=enabled||dataset.offlineReadable) {os.maps.includeDataset(dataset.id,!enabled)}
        }
        if(!data.loading&&data.error==null)selected.filterNot(byId::containsKey).forEach {id->
            AppCheckRow(os.t("缺失的已启用资料","Missing enabled data")+" · "+id.take(8),true,
                os.t("取消启用或重新导入，不会偷偷换来源","Disable or restore; no silent source replacement")) {os.maps.includeDataset(id,false)}
        }
        var advanced by rememberSaveable {mutableStateOf(false)}
        if(selected.size>1)MenuRow(os.t("重叠资料优先级","Overlapping data priority"),if(advanced)os.t("收起","Hide")else os.t("高级","Advanced")) {advanced=!advanced}
        if(advanced) {
            Label(os.t("上方优先，未覆盖处使用后续资料。不会更改任何资料的许可。","Earlier data takes priority; later data fills uncovered areas. Permissions are unchanged."),13,c.muted)
            selected.forEachIndexed {index,id->
                Label("${index+1}. "+(byId[id]?.name ?: os.t("暂不可用","Unavailable")),15)
                fun move(offset:Int) {
                    val current=os.maps.selectedDatasetIds
                    val from=current.indexOf(id);val to=from+offset
                    if(from<0||to !in current.indices)return
                    val next=current.toMutableList();next[from]=current[to];next[to]=id
                    os.maps.reorderDatasets(next)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MetroButton(os.t("上移","Move up"),{move(-1)},Modifier.weight(1f),enabled=index>0)
                    MetroButton(os.t("下移","Move down"),{move(1)},Modifier.weight(1f),enabled=index<selected.lastIndex)
                }
            }
        }
        if(os.maps.saveFailed) {
            Label(os.t("来源设置尚未保存","Source settings are not saved"),14,c.accentText)
            MetroButton(os.t("重试保存来源","Retry saving sources"),{os.maps.select(os.maps.source)})
        }
        MetroButton(os.t("完成","Done"),{open=false})
    }}
}
