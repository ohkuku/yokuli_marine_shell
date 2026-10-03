package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.AppId
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 图册唯一顶层是资料包；包内海图负责显示，数据负责查询、规划及显式生成。 */
@Composable internal fun ChartBundlesPane(os:OsStore) {
    val store=os.maps.bundles
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->uri?.let(store::importPackage)}
    var creating by rememberSaveable {mutableStateOf(false)}
    var name by rememberSaveable {mutableStateOf("")}
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {PageBody {
            MenuRow(os.t("获取官方资料包","Get official collections"),os.t("按海域下载，随时离线使用","Download by region for offline use"),"download") {os.openLinked("chart_store")}
            Label(os.t("资料包","Collections"),20)
            Label(os.t("把同一区域的海图和数据放在一起。","Keep an area's charts and data together."),13,LocalMetro.current.muted)
            ChoiceRow(os.t("不使用资料包","No collection"),os.maps.activeBundleId==null,
                os.t("内置底图 · 手动规划","Built-in basemap · manual routing")) {os.maps.selectBundle(null)}
            if(store.bundles.isEmpty()&&!store.busy) {
                Label(os.t("从一个资料包开始","Start a collection"),18)
                Label(os.t("导入 .yklpkg，或新建后加入海图与原始数据。","Import a .yklpkg, or create a collection and add charts and source data."),14,LocalMetro.current.muted)
            }
            store.bundles.forEach {bundle->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        ChoiceRow(bundle.name,os.maps.activeBundleId==bundle.id,bundleContents(os,bundle),enabled=!store.busy) {os.maps.selectBundle(bundle.id)}
                    }
                    IconAction("settings",os.t("管理 ","Manage ")+bundle.name,{os.open("library:bundle/${bundle.id}")})
                }
                bundle.issue?.let {Label(bundleProblem(os,it),12,LocalMetro.current.muted)}
            }
            BundleProgress(os)
            ChartSourceSaveStatus(os)
        }}
        AppCommandBar(os,listOf(
            AppCommand("create-bundle","plus",os.t("新建","New"),{name="";creating=true},enabled=!store.busy),
            AppCommand("import-bundle","folder",os.t("导入资料包","Import package"),{picker.launch(arrayOf("*/*"))},enabled=!store.busy)
        ))
    }
    if(creating)AppDialog(onDismissRequest={if(!store.busy)creating=false}) {AppDialogSurface {
        AppDialogTitle(os.t("新建资料包","New collection"))
        Field(os.t("名称","Name"),name,{name=it.take(120)})
        Label(os.t("创建后可添加海图或数据，不会自动生成地图。","Add charts or data after creating it. Maps are only generated when you request them."),13,LocalMetro.current.muted)
        MetroButton(os.t("创建","Create"),{store.create(name.trim());creating=false},primary=true,enabled=name.isNotBlank()&&!store.busy)
        MetroButton(os.t("取消","Cancel"),{creating=false},enabled=!store.busy)
    }}
}

@Composable internal fun ChartBundleDetailScreen(os:OsStore,bundleId:String) {
    val store=os.maps.bundles
    val bundle=store.bundles.firstOrNull {it.id==bundleId}
    val charts=os.maps.charts
    val state by charts.state.collectAsState()
    val scope=rememberCoroutineScope()
    var renaming by rememberSaveable(bundleId) {mutableStateOf(false)}
    var name by rememberSaveable(bundleId) {mutableStateOf("")}
    var metadata by remember {mutableStateOf(false)}
    var confirm by rememberSaveable(bundleId) {mutableStateOf<String?>(null)}
    var add by rememberSaveable(bundleId) {mutableStateOf<String?>(null)}
    var message by remember {mutableStateOf<String?>(null)}
    val chartFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let {uri->store.importCharts(bundleId,uri)}}
    val chartFolder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {it?.let {uri->store.importCharts(bundleId,uri,true)}}
    val dataFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let {uri->store.importData(bundleId,uri)}}
    val dataFolder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {it?.let {uri->store.importData(bundleId,uri,true)}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {it?.let {uri->store.exportBundle(bundleId,uri)}}
    val folder=bundle?.chartFolderId?.let {id->os.library.folders.firstOrNull {it.id==id}}
    val dataset=state.datasets.firstOrNull {it.id==bundle?.datasetId}
    val exportCharts=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->if(uri!=null&&folder!=null)os.library.exportFolder(folder,uri)}
    val exportData=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->if(uri!=null&&dataset!=null)scope.launch {
        try {when(val result=charts.exportPackage(ChartExportRequest(java.util.UUID.randomUUID().toString(),dataset.id,uri.toString()))) {
            is ChartCommandResult.Failed->message=chartDataError(os,result.reason)
            ChartCommandResult.Busy->message=os.t("另一项资料任务正在进行","Another library task is running")
            else->message=null
        }}catch(cancel:CancellationException){throw cancel}
        catch(error:Exception){message=chartDataError(os,error.message?:"CHART_EXPORT_FAILED")}
    }}
    val idle=!store.busy&&!os.library.busy&&!state.jobRunning&&!state.exportRunning
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,bundle?.name?:os.t("资料包","Collection"),os.title(AppId.LIBRARY))
        if(bundle==null)PageBody {
            if(store.busy)MetroProgress(os.t("正在读取资料包","Reading collection"))
            else {Label(os.t("这份资料包已移除","This collection was removed"),16);MetroButton(os.t("返回图册","Back to Library"),{os.back()})}
        }else Pivot(listOf(os.t("内容","Contents"),os.t("管理","Manage"))) {page->PageBody {
            BundleProgress(os)
            message?.let {Label(it,13,LocalMetro.current.accentText)}
            bundle.issue?.let {Label(bundleProblem(os,it),13,LocalMetro.current.muted)}
            if(page==0) {
                ChoiceRow(os.t("使用这份资料包","Use this collection"),os.maps.activeBundleId==bundleId,bundleContents(os,bundle),enabled=!store.busy) {os.maps.selectBundle(bundleId)}
                ChartSourceSaveStatus(os)
                AppSection(os.t("海图","Charts"))
                if(folder!=null) {
                    val count=os.library.folderFiles(folder).size
                    MenuRow(os.t("查看海图 · $count 张","View charts · $count"),os.t("文件、显示顺序与元数据","Files, display order and metadata"),"layers") {os.open("library:${folder.id}")}
                    MetroButton(os.t("在地图显示","Show on map"),{os.maps.selectBundle(bundleId);viewLayer(os,folder)},enabled=os.library.folderFiles(folder).any {it.enabled&&it.error==null})
                }else Label(os.t("还没有海图；可添加 MBTiles，或用下方数据生成。","No charts yet. Add MBTiles, or generate a chart from the data below."),13,LocalMetro.current.muted)
                MenuRow(os.t("添加海图","Add charts"),".yklcharts · MBTiles","plus") {if(idle)add="charts"}
                AppSection(os.t("数据","Data"))
                if(dataset!=null) {
                    MenuRow(os.t("查看数据 · ${dataset.cells.size} 份","View data · ${dataset.cells.size} sources"),os.t("对象、水深、文件优先级与元数据","Objects, depths, file priority and metadata"),"layers") {os.open("chartdataset:${dataset.id}")}
                    PrepareChartAreaAction(os,dataset)
                    GenerateChartAction(os,dataset,bundleId)
                }else Label(if(bundle.datasetId==LINZ_ONLINE_DATASET_ID)os.t("LINZ 区域尚未下载","LINZ area has not been downloaded")else os.t("加入数据后，准星显示参数并支持离线规划。","Add data for cursor information and offline routing."),13,LocalMetro.current.muted)
                MenuRow(if(bundle.datasetId==null)os.t("添加数据","Add data")else os.t("替换数据来源","Replace data source"),".yklgeodata · S-57 · GeoPackage · GEBCO","plus") {if(idle)add="data"}
                if(bundle.datasetId==LINZ_ONLINE_DATASET_ID)LinzBundleControls(os)
                if(dataset!=null)ChartPortrayalSetting(os)
            }else {
                MenuRow(os.t("重命名","Rename"),bundle.name,"edit") {name=bundle.name;renaming=true}
                MenuRow(os.t("资料说明","Collection metadata"),os.t("包级说明；文件各自的资料保持独立","Collection notes; each file keeps its own metadata"),"edit") {metadata=true}
                bundle.metadata.forEach {(key,value)->if(value.isNotBlank()){Label(chartDisplayText(key,80),12,LocalMetro.current.muted);Label(chartDisplayText(value,8192),14)}}
                AppSection(os.t("导出","Export"))
                val stem=bundle.name.map {if(it in "\\/:*?\"<>|"||it.isISOControl())'_' else it}.joinToString("").take(90).ifBlank {"yokuli"}
                MetroButton(os.t("整个资料包 · .yklpkg","Collection · .yklpkg"),{export.launch("$stem.yklpkg")},enabled=idle&&(folder!=null||dataset?.offlineReadable==true))
                if(folder!=null)MetroButton(os.t("仅海图 · .yklcharts","Charts · .yklcharts"),{exportCharts.launch("$stem.yklcharts")},enabled=idle)
                if(dataset!=null)MetroButton(os.t("仅数据 · .yklgeodata","Data · .yklgeodata"),{exportData.launch("$stem.yklgeodata")},enabled=idle&&dataset.offlineReadable)
                LibraryProgress(os)
                ChartDataProgress(os,charts,state)
                AppSection(os.t("移除","Remove"))
                if(bundle.chartFolderId!=null)MetroButton(os.t("移除包内海图","Remove charts"),{confirm="charts"},enabled=idle)
                if(bundle.datasetId!=null)MetroButton(os.t("移除包内数据","Remove data"),{confirm="data"},enabled=idle)
                MetroButton(os.t("移除资料包","Remove collection"),{confirm="bundle"},enabled=idle)
            }
        }}
    }
    if(renaming&&bundle!=null)AppDialog(onDismissRequest={renaming=false}) {AppDialogSurface {
        AppDialogTitle(os.t("资料包名称","Collection name"));Field(os.t("名称","Name"),name,{name=it.take(120)})
        MetroButton(os.t("保存","Save"),{store.rename(bundleId,name.trim());renaming=false},enabled=idle&&name.isNotBlank())
        MetroButton(os.t("取消","Cancel"),{renaming=false})
    }}
    if(metadata&&bundle!=null)MetadataEditorDialog(os,os.t("资料包说明","Collection metadata"),bundle.metadata,
        onDismiss={metadata=false},busy=store.busy,failure=store.issue?.let {bundleProblem(os,it)},onSave={store.updateMetadata(bundleId,it);metadata=false})
    if(add!=null&&bundle!=null)AppDialog(onDismissRequest={add=null}) {AppDialogSurface {
        val isCharts=add=="charts"
        AppDialogTitle(if(isCharts)os.t("添加海图","Add charts")else if(bundle.datasetId==null)os.t("添加数据","Add data")else os.t("替换数据来源","Replace data source"))
        if(!isCharts&&bundle.datasetId!=null)Label(os.t("新来源完整就绪后才替换，包内海图保持不变。","Replaces data only after the new source is ready. Charts are kept."),13,LocalMetro.current.muted)
        MenuRow(os.t("选择文件夹","Choose folder"),os.t("优先读取原件，保留各文件资料","Links originals and preserves file metadata"),"folder") {add=null;if(isCharts)chartFolder.launch(null)else dataFolder.launch(null)}
        MenuRow(os.t("选择文件","Choose file"),if(isCharts)".yklcharts · MBTiles"else".yklgeodata · S-57 · GeoPackage · GEBCO","plus") {add=null;if(isCharts)chartFile.launch(arrayOf("*/*"))else dataFile.launch(arrayOf("*/*"))}
        if(!isCharts)MenuRow("LINZ",os.t("下载区域资料，保存后离线使用","Download areas for offline use"),"download") {
            add=null
            if(state.linz?.configured!=true)os.openLinked("settings:linz")
            else scope.launch {try {store.attachDataSource(bundleId,LINZ_ONLINE_DATASET_ID)}catch(cancel:CancellationException){throw cancel}catch(error:Exception){message=bundleProblem(os,error.message.orEmpty())}}
        }
        MetroButton(os.t("取消","Cancel"),{add=null})
    }}
    confirm?.let {kind->ConfirmDialog(os,when(kind){"charts"->os.t("移除包内海图？原始文件保留。","Remove the charts? Original files are kept.");"data"->os.t("移除包内数据？原始文件保留。","Remove the data? Original files are kept.");else->os.t("移除资料包及其导入的本机内容？原始文件保留。","Remove the collection and its imported local contents? Original files are kept.")},{confirm=null}) {
        when(kind){"charts"->store.clearCharts(bundleId);"data"->store.clearData(bundleId);else->{if(os.maps.activeBundleId==bundleId)os.maps.selectBundle(null);store.remove(bundleId)}}
        confirm=null
    }}
}

@Composable private fun BundleProgress(os:OsStore) {
    val store=os.maps.bundles
    store.task?.let {task->
        val label=when(task.phase) {
            ChartBundlePhase.VERIFYING->os.t("正在读取资料包","Reading package")
            ChartBundlePhase.IMPORTING_DATA->os.t("正在准备数据","Preparing data")
            ChartBundlePhase.IMPORTING_CHARTS->os.t("正在登记海图","Adding charts")
            ChartBundlePhase.COMMITTING->os.t("正在保存资料","Saving collection")
            ChartBundlePhase.EXPORTING->os.t("正在导出资料包","Exporting collection")
            ChartBundlePhase.CLEANING->os.t("正在整理文件","Finishing up")
            ChartBundlePhase.COMPLETE->os.t("资料已就绪","Collection ready")
            ChartBundlePhase.CANCELLED->os.t("已取消","Cancelled")
            ChartBundlePhase.FAILED->os.t("操作未完成","Operation did not finish")
            ChartBundlePhase.INTERRUPTED->os.t("上次操作中断","Operation interrupted")
        }
        if(store.busy)MetroProgress(label)else Label(label,13,LocalMetro.current.muted)
        if(task.fileCount>0)Label(os.t("文件 ${task.fileIndex} / ${task.fileCount}","File ${task.fileIndex} / ${task.fileCount}"),12,LocalMetro.current.muted)
        if(task.total>0)Label(os.t("当前文件：${task.completed} / ${task.total}","Current file: ${task.completed} / ${task.total}"),12,LocalMetro.current.muted)
        if(store.busy&&task.cancellable)MetroButton(os.t("取消","Cancel"),store::cancelImport)
        if(task.retryable&&task.phase in setOf(ChartBundlePhase.FAILED,ChartBundlePhase.INTERRUPTED,ChartBundlePhase.CANCELLED))MetroButton(os.t("重试","Retry"),store::retryImport,enabled=!store.busy)
    }
    if(store.busy&&store.task==null)MetroProgress(os.t("正在保存资料","Saving collection"))
    store.issue?.let {Label(bundleProblem(os,it),13,LocalMetro.current.accentText)}
    os.maps.collectionIssue?.let {
        Label(os.t("旧资料尚未整理完成，原文件保留。","Previous collections could not be organized yet. Original files are kept."),13,LocalMetro.current.muted)
        MetroButton(os.t("重新整理","Retry"),os.maps::retryCollections,enabled=!store.busy)
    }
}

private fun bundleContents(os:OsStore,b:ChartBundle):String=when {
    b.chartFolderId!=null&&b.datasetId!=null->os.t("海图 + 数据","Charts + data")
    b.chartFolderId!=null->os.t("海图 · 尚无规划数据","Charts · no routing data")
    b.datasetId!=null->os.t("数据 · 使用内置底图","Data · built-in basemap")
    else->os.t("空资料包 · 添加内容","Empty collection · add content")
}

internal fun bundleProblem(os:OsStore,code:String):String=when {
    code=="ATLAS_DATA_PENDING"->os.t("尚未下载区域数据","Area data has not been downloaded")
    code.contains("PERMISSION",true)->os.t("文件授权失效，请重新选择原文件。","File access was lost. Select the original file again.")
    code.contains("EXTENSION",true)||code.contains("KIND",true)->os.t("资料格式不匹配。整体导入使用 .yklpkg；海图与数据请在包内分别添加。","File type does not match. Import .yklpkg collections here; add charts and data inside a collection.")
    code.contains("MISSING",true)->os.t("已关联的资料不可用，请重新连接。","Linked content is unavailable. Reconnect its source.")
    code.contains("BUSY",true)->os.t("另一项资料任务正在进行，请稍后重试。","Another library task is running. Try again shortly.")
    code.contains("EMPTY",true)->os.t("请先向资料包添加海图或数据。","Add charts or data to this collection first.")
    code.contains("STORAGE",true)->os.t("储存空间不足或无法写入，原有资料保留。","Storage is full or not writable. Existing content is kept.")
    else->os.t("资料未能完整准备，原有内容保留。请重试或重新选择原文件。","The operation could not finish. Existing content is kept. Retry or select the source again.")
}
