package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore

/** 复用海图的三选一和即时生效行为；不为每条航线复制来源设置。 */
@Composable internal fun LibraryBackgroundSettings(os:OsStore) {
    AppSection(os.t("当前海图背景","Chart background"))
    ChartBackgroundChoices(os)
    ChartSourceSaveStatus(os)
    ChartPortrayalSetting(os)
    Label(os.t("点选立即切换背景，管理按钮只打开文件夹。航行数据在“数据”页配置，不随背景切换。","Selections change the background immediately; Manage only opens the folder. Navigation data stays configured in Data."),13,LocalMetro.current.muted)
    AppSection(os.t("海图文件夹","Chart folders"))
}

/** 文件夹是单选资料类型；不在文件夹之间叠加或排序。 */
@Composable internal fun LibraryDataSettings(os:OsStore) {
    val data by os.maps.charts.state.collectAsState()
    val selected=os.maps.selectedDatasetIds.firstOrNull()
    val insets=LocalShellHorizontalInsets.current
    Column(Modifier.fillMaxWidth().padding(start=insets.pageStart,end=insets.pageEnd,top=8.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Label(os.t("数据文件夹","Data folders"),18)
        Label(os.t("一次选择一个文件夹，供海图查询与离线规划使用。","Choose one folder for chart queries and offline planning."),13,LocalMetro.current.muted)
        if(selected!=null&&!data.loading&&data.error==null&&data.datasets.none {it.id==selected}) {
            Label(os.t("已选文件夹缺失，未自动改用其他资料。","The selected folder is missing. No replacement was chosen automatically."),13,LocalMetro.current.muted)
            MetroButton(os.t("取消选择","Clear selection"),{os.maps.selectDataset(null)})
        }
        if(os.maps.saveFailed) {
            Label(os.t("数据选择尚未保存","Data selection is not saved"),13,LocalMetro.current.accentText)
            MetroButton(os.t("重试保存","Retry saving"),{os.maps.select(os.maps.source)})
        }
    }
}
