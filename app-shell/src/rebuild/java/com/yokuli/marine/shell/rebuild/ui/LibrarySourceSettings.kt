package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.chart.LINZ_ONLINE_DATASET_ID

/** 复用海图的三选一和即时生效行为；不为每条航线复制来源设置。 */
@Composable internal fun LibraryBackgroundSettings(os:OsStore) {
    AppSection(os.t("当前海图背景","Chart background"))
    ChartBackgroundChoices(os)
    ChartSourceSaveStatus(os)
    Label(os.t("点选立即切换背景，管理按钮只打开文件夹。航行数据在“数据”页配置，不随背景切换。","Selections change the background immediately; Manage only opens the folder. Navigation data stays configured in Data."),13,LocalMetro.current.muted)
    AppSection(os.t("海图文件夹","Chart folders"))
}

/** 文件夹是单选资料类型；不在文件夹之间叠加或排序。 */
@Composable internal fun LibraryDataSettings(os:OsStore) {
    val data by os.maps.charts.state.collectAsState()
    val selected=os.maps.selectedDatasetIds.firstOrNull()
    val current=data.datasets.firstOrNull {it.id==selected}
    val insets=LocalShellHorizontalInsets.current
    Column(Modifier.fillMaxWidth().padding(start=insets.pageStart,end=insets.pageEnd,top=8.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Label(os.t("航行数据","Navigation data"),18)
        Label(os.t("选择 LINZ 区域资料或一个离线文件夹；海图查询与规划共用这份数据。","Choose LINZ regional data or one offline folder. Chart queries and planning share this source."),13,LocalMetro.current.muted)
        if(current?.cells?.any {it.featureCount>0}==true) {
            AppSection(os.t("矢量数据显示","Vector data display"))
            ChartPortrayalSetting(os)
            Label(os.t("这里只控制 S-57 / LINZ / GeoPackage 对象怎么画，不改变原始数据，也不改变规划条件。","This only controls how S-57 / LINZ / GeoPackage objects are drawn. It does not change source data or planning criteria."),12,LocalMetro.current.muted)
        } else if(current?.rasters.orEmpty().isNotEmpty()) {
            AppSection(os.t("数据预览","Data preview"))
            Label(os.t("当前数据是数值高程网格。进入数据文件夹后用“预览数据与覆盖”查看 GEBCO 覆盖和高程分级。","The current data is a numeric elevation grid. Open the data folder and use Preview data & coverage to inspect GEBCO coverage and elevation classes."),12,LocalMetro.current.muted)
        }
        if(selected!=null&&selected!=LINZ_ONLINE_DATASET_ID&&!data.loading&&data.error==null&&data.datasets.none {it.id==selected}) {
            Label(os.t("已选文件夹缺失，未自动改用其他资料。","The selected folder is missing. No replacement was chosen automatically."),13,LocalMetro.current.muted)
            MetroButton(os.t("取消选择","Clear selection"),{os.maps.selectDataset(null)})
        }
        if(os.maps.saveFailed) {
            Label(os.t("数据选择尚未保存","Data selection is not saved"),13,LocalMetro.current.accentText)
            MetroButton(os.t("重试保存","Retry saving"),{os.maps.select(os.maps.source)})
        }
    }
}
