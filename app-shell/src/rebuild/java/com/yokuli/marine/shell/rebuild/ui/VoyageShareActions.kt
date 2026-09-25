package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import com.yokuli.anchorwatch.data.database.TripSessionEntity
import com.yokuli.marine.shell.rebuild.OsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 先选择分享用途，格式保留在对应的任务里；文件仍由唯一航行导出器产生并交系统分享。 */
@Composable internal fun VoyageShareActions(os:OsStore,session:TripSessionEntity) {
    val voyages=os.marine?.services?.voyages ?: return
    var purpose by rememberSaveable(session.id) {mutableStateOf<String?>(null)}
    var preparing by remember(session.id) {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun export(action:()->Job) {
        if(preparing)return
        preparing=true
        // Job 完成只用于解除按钮忙态；“已分享”由 Android 分享操作决定，不能在这里猜测。
        val job=action()
        scope.launch {try {job.join()} finally {preparing=false}}
    }
    AppSection(os.t("分享这次航行","share this voyage"))
    Label(os.t("选择要交付什么，接着使用手机的分享菜单。","Choose what to send, then use your phone’s share menu."),15,LocalMetro.current.muted)
    if(preparing)MetroProgress(os.t("正在准备文件…","preparing the file…"))
    MetroButton(os.t("分享行程摘要","share a voyage summary"),{export {voyages.shareTripReportSnapshot(session)}},primary=true,enabled=!preparing)
    MenuRow(os.t("给其他导航软件使用","use in another navigation app"),os.t("航迹与有位置的随记","track and geolocated moments"),"map") {purpose="track"}
    MenuRow(os.t("保存完整资料 / 分析数据","keep or analyse your data"),os.t("记录、笔记、实际读数及来源","recordings, notes, readings and sources"),"logbook") {purpose="data"}
    purpose?.let {selected ->
        AppDialog(onDismissRequest={purpose=null}) {AppDialogSurface {
            AppDialogTitle(if(selected=="track")os.t("分享航迹","share your track") else os.t("保存与分析","keep and analyse"))
            if(preparing)MetroProgress(os.t("正在准备文件…","preparing the file…"))
            if(selected=="track") {
                Label(os.t("GPX 适合大多数航海软件；没有位置的随记只保存在数据资料中。","GPX works with most navigation apps. Moments without a position remain in the data exports."),15,LocalMetro.current.muted)
                MetroButton(os.t("分享航迹 · GPX","share track · GPX"),{export {voyages.exportTripGpx(session)}},primary=true,enabled=!preparing)
                AppSection(os.t("其他兼容格式","other compatible formats"))
                MetroButton("KML",{export {voyages.exportTripKml(session)}},enabled=!preparing)
                MetroButton("KMZ",{export {voyages.exportTripKmz(session)}},enabled=!preparing)
            } else {
                MetroButton(os.t("完整资料包 · ZIP","complete data archive · ZIP"),{export {voyages.exportTripAiSource(session)}},primary=true,enabled=!preparing)
                AppSection(os.t("按内容导出表格","export a data table"))
                listOf(
                    os.t("全部实际读数 · CSV","all recorded readings · CSV") to {voyages.exportTripCsv(session)},
                    os.t("沿途随记 · CSV","voyage moments · CSV") to {voyages.exportTripWaypoints(session)},
                    os.t("全部事件与来源变化 · CSV","events and source changes · CSV") to {voyages.exportTripEvents(session)},
                    os.t("自选读数 · CSV","custom readings · CSV") to {voyages.exportTripCustomMetrics(session)},
                ).forEach {(label,action)->MetroButton(label,{export(action)},enabled=!preparing)}
            }
            MetroButton(os.t("完成","done"),{purpose=null})
        }}
    }
}
