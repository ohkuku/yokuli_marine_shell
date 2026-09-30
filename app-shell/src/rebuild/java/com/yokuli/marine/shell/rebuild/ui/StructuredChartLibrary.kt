package com.yokuli.marine.shell.rebuild.ui

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yokuli.chartpackage.ChartPackageManifest
import com.yokuli.chartpackage.YokuliChartPackage
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.chartDisplayText
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

/** 一个文件夹是一类完整资料，只选其中一个；覆盖优先级仅作用于文件夹内部。 */
@Composable internal fun StructuredChartLibraryPane(os:OsStore) {
    val service=os.marine?.system?.charts
    val data=service?.state?.collectAsState()?.value ?: ChartDataState()
    var importUri by rememberSaveable {mutableStateOf<String?>(null)}
    var formatDetails by rememberSaveable {mutableStateOf(false)}
    val file=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->importUri=uri?.toString()}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->importUri=uri?.toString()}
    val insets=LocalShellHorizontalInsets.current
    val selected=os.maps.selectedDatasetIds.firstOrNull()
    val ordered=data.datasets.filterNot {it.id==LINZ_ONLINE_DATASET_ID}.sortedBy {it.name.lowercase(java.util.Locale.ROOT)}
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(start=insets.pageStart,end=insets.pageEnd,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {ChartDataProgress(os,service,data)}
            item {ChoiceRow(os.t("不使用数据","No data folder"),selected==null,os.t("保留海图显示，手动规划航线","Keep the chart background and plan routes manually")) {os.maps.selectDataset(null)}}
            item {LinzLibrarySource(os)}
            item {AppSection(os.t("自定义 · 离线文件夹","Custom · Offline folders"))}
            if(ordered.isEmpty()&&!data.loading)item {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(vertical=16.dp)) {
                    Label(os.t("添加离线资料","Add offline data"),20)
                    Label(os.t("连接一个数据文件夹，统一管理里面的水深、岸线、航标与障碍。安装完整副本后，无需联网即可查询和规划。","Connect a data folder containing depths, coastlines, marks and hazards. Once a complete copy is installed, queries and planning work offline."),14,LocalMetro.current.muted)
                }
            }
            items(ordered,key={it.id}) {dataset->
                val used=selected==dataset.id
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            ChoiceRow(dataset.name,used,datasetSummary(os,dataset),enabled=used||dataset.offlineReadable) {
                                os.maps.selectDataset(dataset.id)
                            }
                        }
                        IconAction("settings",os.t("浏览与管理 ","Browse and manage ")+dataset.name,{os.open("chartdataset:${dataset.id}")})
                    }
                    if(!dataset.offlineReadable)Label(os.t("离线副本缺失 · 打开管理重新扫描","Offline copy missing · Rescan from Manage"),13,LocalMetro.current.accentText)
                }
            }
            item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                MenuRow(os.t("支持哪些资料？","Supported data"),".yklchart · S-57 · GeoPackage · GEBCO",if(formatDetails)"minus"else"plus") {formatDetails=!formatDetails}
                if(formatDetails) {
                    Label(os.t(".yklchart：整个文件夹的离线图包。导入时校验文件，自动保留每份文件的 metadata；也可将图册里的文件夹再次导出。",".yklchart: a portable offline folder. Import verifies the files and retains their individual metadata. Folders in your library can be exported again."),13,LocalMetro.current.muted)
                    Label(os.t("S-57：未加密的 .000、连续更新及 ZIP 交换集。GeoPackage：开放矢量资料，支持 LINZ 水文图层与 Yokuli 资料字段。","S-57: unencrypted .000 cells, sequential updates and ZIP exchange sets. GeoPackage: open vector data, including LINZ hydrographic layers and Yokuli fields."),13,LocalMetro.current.muted)
                    Label(os.t("GEBCO：数值 GeoTIFF 或 ESRI ASCII 网格（.asc），可离线查询和参考规划。它不是 ENC，不能证明近岸水深与障碍安全；NetCDF 文件需要先转换为上述格式。","GEBCO: numeric GeoTIFF or ESRI ASCII grids (.asc), for offline queries and reference planning. It is not an ENC and cannot establish safe inshore depth or clearance. Convert NetCDF files to one of these formats first."),13,LocalMetro.current.muted)
                    Label(os.t("每个文件夹代表一类资料，一次选择一个。文件夹内可包含多份文件，并调整其覆盖优先级。MBTiles 图片海图在“海图”页管理。","Each folder is one data collection; choose one at a time. Set overlap priority between files inside it. Manage MBTiles chart images in Charts."),13,LocalMetro.current.muted)
                    Label(os.t("S-63 加密图包需要获许可客户端与设备 User Permit，当前不能解密。","Encrypted S-63 packages need a licensed client and device User Permit and cannot currently be decrypted."),13,LocalMetro.current.muted)
                }
            }}
        }
        AppCommandBar(os,listOf(
            AppCommand("import-data-folder","folder",os.t("连接数据文件夹","Connect data folder"),{folder.launch(null)},enabled=service!=null&&!data.loading&&!data.jobRunning),
            AppCommand("import-data","plus",os.t("导入文件","Import file"),{file.launch(arrayOf("*/*"))},enabled=service!=null&&!data.loading&&!data.jobRunning),
        ))
    }
    importUri?.let {uri->ChartImportDialog(os,uri,null,onDismiss={importUri=null}) {request->service?.importPackage(request)}}
}

private fun datasetSummary(os:OsStore,dataset:ChartDataset):String {
    val rasterCount=dataset.rasters.orEmpty().size
    val objects=dataset.cells.sumOf {it.featureCount}
    return listOfNotNull(
        os.t("${dataset.cells.size} 份资料","${dataset.cells.size} sources"),
        if(rasterCount>0)os.t("$rasterCount 个高程网格","$rasterCount elevation grids")else if(objects>0)os.t("$objects 个对象","$objects objects")else null,
    ).joinToString(" · ")
}

@Composable fun LibraryDatasetScreen(os:OsStore,datasetId:String) {
    val service=os.marine?.system?.charts
    val data=service?.state?.collectAsState()?.value ?: ChartDataState()
    val dataset=data.datasets.firstOrNull {it.id==datasetId}
    val scope=rememberCoroutineScope()
    var rename by remember {mutableStateOf(false)}
    var editedName by remember(datasetId) {mutableStateOf("")}
    var renaming by remember {mutableStateOf(false)}
    var editingMetadata by remember {mutableStateOf(false)}
    var savingMetadata by remember {mutableStateOf(false)}
    var technicalDetails by rememberSaveable(datasetId) {mutableStateOf<String?>(null)}
    var fileMetadata by rememberSaveable(datasetId) {mutableStateOf<String?>(null)}
    var removing by remember {mutableStateOf(false)}
    var expanded by rememberSaveable(datasetId) {mutableStateOf<String?>(null)}
    var importUri by rememberSaveable(datasetId) {mutableStateOf<String?>(null)}
    var message by remember {mutableStateOf<String?>(null)}
    var folderDetails by remember(datasetId) {mutableStateOf<Map<String,String>?>(null)}
    var folderDetailsError by remember(datasetId) {mutableStateOf<String?>(null)}
    var folderDetailsRetry by remember(datasetId) {mutableIntStateOf(0)}
    var fileDetails by remember(datasetId) {mutableStateOf<Map<String,String>?>(null)}
    var fileDetailsError by remember(datasetId) {mutableStateOf<String?>(null)}
    var fileDetailsRetry by remember(datasetId) {mutableIntStateOf(0)}
    LaunchedEffect(service,dataset?.revision,folderDetailsRetry) {
        folderDetails=null;folderDetailsError=null
        if(dataset!=null&&service!=null)try {folderDetails=service.readMetadata(dataset.id,revision=dataset.revision)}
        catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(error:Exception){folderDetailsError=chartDataError(os,error.message ?: "CHART_READ_FAILED")}
    }
    LaunchedEffect(service,dataset?.revision,fileMetadata,fileDetailsRetry) {
        fileDetails=null;fileDetailsError=null
        if(dataset!=null&&service!=null&&fileMetadata!=null)try {fileDetails=service.readMetadata(dataset.id,fileMetadata,dataset.revision)}
        catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(error:Exception){fileDetailsError=chartDataError(os,error.message ?: "CHART_READ_FAILED")}
    }
    val update=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->importUri=uri?.toString()}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->importUri=uri?.toString()}
    val insets=LocalShellHorizontalInsets.current
    fun feedback(result:ChartCommandResult?) {message=when(result) {
        is ChartCommandResult.Failed->chartDataError(os,result.reason)
        ChartCommandResult.Busy->os.t("另一个任务仍在进行，请稍后重试","Another task is running. Try again shortly.")
        null->os.t("资料服务仍在启动","Data service is starting")
        else->null
    }}
    fun perform(action:suspend ()->ChartCommandResult?,onSaved:()->Unit={}) {
        scope.launch {
            try {val result=action();feedback(result);if(result is ChartCommandResult.Saved)onSaved()}
            catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
            catch(error:Exception) {message=chartDataError(os,error.message ?: "CHART_READ_FAILED")}
        }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->
        if(uri!=null&&dataset!=null)perform({service?.exportPackage(ChartExportRequest(UUID.randomUUID().toString(),dataset.id,uri.toString()))})
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,dataset?.name ?: os.t("数据文件夹","Data folder"),os.title(AppId.LIBRARY)+" · "+os.t("数据","Data"))
        if(dataset==null)PageBody {
            ChartDataProgress(os,service,data)
            if(!data.loading) {Label(os.t("这份资料已不在图册中","This data is no longer in your library"),20);Label(os.t("返回图册重新连接文件夹即可恢复。","Return to Library to reconnect its folder."),14,LocalMetro.current.muted)}
        }else Pivot(listOf(os.t("内容","Contents"),os.t("管理","Manage"))) {page->
        if(page==0)LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=insets.pageStart,end=insets.pageEnd,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                ChartDataProgress(os,service,data)
                message?.let {Label(it,14,LocalMetro.current.accentText)}
                val selected=dataset.id in os.maps.selectedDatasetIds
                val hasVector=dataset.cells.any {it.featureCount>0}
                ChoiceRow(os.t("使用此数据文件夹","Use this data folder"),selected,datasetSummary(os,dataset),enabled=selected||dataset.offlineReadable) {os.maps.selectDataset(dataset.id)}
                if(hasVector)MenuRow(os.t("浏览资料内容","Explore contents"),os.t("水深、岸线、航标与障碍","Depths, coastlines, marks and hazards"),"layers") {os.open("chartobjects:${dataset.id}")}
                MetroButton(os.t("预览数据与覆盖","Preview data & coverage"),{openDatasetOnChart(os,dataset)},enabled=dataset.offlineReadable)
                AppSection(os.t("文件与覆盖","Files and coverage"))
                if(dataset.cells.size>1)Label(os.t("上方优先。调整顺序会同时更新离线查询与规划。","Earlier files take priority. This order applies to offline queries and planning."),13,LocalMetro.current.muted)
            }}
            val orderedCells=dataset.cells.sortedBy {it.priority}
            items(orderedCells,key={it.cellId}) {cell->Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                val index=orderedCells.indexOfFirst {it.cellId==cell.cellId}
                val grid=dataset.rasters.orEmpty().firstOrNull {it.cellId==cell.cellId}
                Row(Modifier.fillMaxWidth().clickable {expanded=if(expanded==cell.cellId)null else cell.cellId}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(orderedCells.size>1)Label("${index+1}",14,LocalMetro.current.muted,Modifier.width(22.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Label(cell.sourceName ?: grid?.sourceName ?: cell.cellId,17)
                        Label(if(grid!=null)"${grid.width} × ${grid.height} · "+os.t("高程网格","Elevation grid")else if(cell.cancelled)os.t("提供方已取消","Cancelled by provider")else os.t("${cell.featureCount} 个对象 · 第 ${cell.edition} 版","${cell.featureCount} objects · Edition ${cell.edition}"),13,LocalMetro.current.muted)
                    }
                    Glyph(if(expanded==cell.cellId)"minus"else"plus",Modifier.size(18.dp),LocalMetro.current.muted)
                }
                if(expanded==cell.cellId) {
                    if(orderedCells.size>1) {
                        fun moveCell(delta:Int) {
                            val next=orderedCells.map {it.cellId}.toMutableList();val to=index+delta
                            if(to !in next.indices)return
                            next.add(to,next.removeAt(index))
                            perform({service?.reorderCells(dataset.id,next)})
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            MetroButton(os.t("上移","Move up"),{moveCell(-1)},Modifier.weight(1f),enabled=index>0&&!data.jobRunning)
                            MetroButton(os.t("下移","Move down"),{moveCell(1)},Modifier.weight(1f),enabled=index<orderedCells.lastIndex&&!data.jobRunning)
                        }
                    }
                    MenuRow(os.t("文件资料","File metadata"),icon=if(fileMetadata==cell.cellId)"minus"else"plus") {fileMetadata=if(fileMetadata==cell.cellId)null else cell.cellId}
                    if(fileMetadata==cell.cellId) {
                        val details=fileDetails
                        when {
                            fileDetailsError!=null->{Label(requireNotNull(fileDetailsError),13,LocalMetro.current.accentText);MetroButton(os.t("重新读取","Read again"),{fileDetailsRetry++})}
                            details==null->MetroProgress(os.t("正在读取文件资料","Reading file metadata"))
                            details.isEmpty()->Label(os.t("文件未附带说明","No metadata supplied with this file"),13,LocalMetro.current.muted)
                            else->MetadataRows(os,details)
                        }
                    }
                    cell.compilationScale?.let {Label(os.t("编图比例尺 ","Compilation scale ")+"1:$it",13,LocalMetro.current.muted)}
                    cell.issueDate?.let {Label(os.t("发布日期 ","Issue date ")+it,13,LocalMetro.current.muted)}
                    val issues=cell.issues.filterNot {it.startsWith("REFERENCE_ONLY_")}
                    if(grid!=null||issues.isNotEmpty()||cell.quality.isNotEmpty()) {
                        MenuRow(os.t("数据详情","Data details"),icon=if(technicalDetails==cell.cellId)"minus"else"plus") {technicalDetails=if(technicalDetails==cell.cellId)null else cell.cellId}
                        if(technicalDetails==cell.cellId) {
                            grid?.let {raster->
                                Label(raster.product,14)
                                Label(os.t("像元间距 ","Cell spacing ")+"${decimal(raster.pixelWidthDegrees*3600,1)}″ × ${decimal(raster.pixelHeightDegrees*3600,1)}″",13,LocalMetro.current.muted)
                                Label(os.t("海平面高程，负值为海底；不是海图基准水深。缺测像元保持未知。","Sea-level elevation; negative values describe the seabed, not chart-datum depth. Missing cells remain unknown."),13,LocalMetro.current.muted)
                                raster.bounds.forEach {bounds->Label(os.formatCoordinates(GeoPoint(bounds.south,bounds.west))+" → "+os.formatCoordinates(GeoPoint(bounds.north,bounds.east)),12,LocalMetro.current.muted)}
                            }
                            if(cell.quality.isNotEmpty())Label(cell.quality.joinToString(" · "),13,LocalMetro.current.muted)
                            issues.map {chartDataError(os,it)}.distinct().forEach {Label(it,13,LocalMetro.current.muted)}
                        }
                    }
                    if(cell.featureCount>0)MetroButton(os.t("查看对象","Explore objects"),{os.open("chartobjects:${dataset.id}:${Uri.encode(cell.cellId)}")},enabled=!cell.cancelled)
                    if(cell.bounds.isNotEmpty()||grid?.bounds.orEmpty().isNotEmpty())MetroButton(os.t("预览此文件与覆盖","Preview this file & coverage"),{
                        openDatasetOnChart(os,dataset,cell.cellId)
                    })
                }
            }}
        }else PageBody {
            ChartDataProgress(os,service,data)
            message?.let {Label(it,14,LocalMetro.current.accentText)}
            Label(dataset.name,18)
            Label(datasetSummary(os,dataset),13,LocalMetro.current.muted)
            MenuRow(os.t("重命名","Rename"),icon="edit") {editedName=dataset.name;message=null;rename=true}
            AppSection(os.t("文件夹资料","Folder metadata"))
            when {
                folderDetailsError!=null->{Label(requireNotNull(folderDetailsError),13,LocalMetro.current.accentText);MetroButton(os.t("重新读取","Read again"),{folderDetailsRetry++})}
                folderDetails==null->MetroProgress(os.t("正在读取文件夹资料","Reading folder metadata"))
                else->{
                    MenuRow(os.t("编辑文件夹资料","Edit folder metadata"),os.t("说明、来源及自定义字段","Description, source and custom fields"),"edit") {message=null;editingMetadata=true}
                    MetadataRows(os,folderDetails.orEmpty())
                }
            }
            MetroButton(os.t("导出整个文件夹","Export folder"),{export.launch(dataset.name.map {if(it in "\\/:*?\"<>|"||it.isISOControl()) '_' else it}.joinToString("").take(100)+".yklchart")},enabled=dataset.offlineReadable&&!data.exportRunning)
            Label(os.t("打包原始文件、各自的 metadata 和文件优先顺序，可离线导入其他设备。","Packages the original files, their metadata and priority order for offline import on another device."),13,LocalMetro.current.muted)
            AppSection(os.t("更新内容","Update contents"))
            if(dataset.sourceUri!=null)MetroButton(if(dataset.sourceIsFolder)os.t("重新扫描原文件夹","Rescan original folder")else os.t("重新读取原文件","Read original file again"),{
                perform({service?.importPackage(ChartImportRequest(UUID.randomUUID().toString(),requireNotNull(dataset.sourceUri),dataset.name,dataset.eligibility.copy(automatic=true),dataset.id,
                    rasterProduct=dataset.rasters.orEmpty().map {it.product}.distinct().singleOrNull()))})
            },primary=true,enabled=!data.jobRunning)
            MetroButton(os.t("重新连接文件夹","Reconnect folder"),{folder.launch(null)},enabled=!data.jobRunning)
            MetroButton(os.t("从文件更新","Update from file"),{update.launch(arrayOf("*/*"))},enabled=!data.jobRunning)
            Label(os.t("保留名称、选用状态与覆盖顺序；新版完整安装后才替换离线副本。原文件不会被修改。","Keeps the name, selection and overlap order. A complete new version replaces the offline copy; original files stay untouched."),13,LocalMetro.current.muted)
            AppSection(os.t("移除","Remove"))
            MetroButton(os.t("移除本机资料","Remove local data"),{removing=true},enabled=!data.jobRunning)
            Label(os.t("只移除图册中的离线副本，原文件夹保留。","Removes the library's offline copy and keeps the original folder."),13,LocalMetro.current.muted)
        }
        }
    }
    if(rename&&dataset!=null)AppDialog(onDismissRequest={if(!renaming)rename=false}) {AppDialogSurface {
        AppDialogTitle(os.t("资料名称","Data name"))
        Field(os.t("名称","Name"),editedName,{editedName=it.take(120)})
        message?.let {Label(it,14,LocalMetro.current.accentText)}
        MetroButton(os.t("保存","Save"),{renaming=true;scope.launch {try {val result=service?.rename(dataset.id,editedName);feedback(result);if(result is ChartCommandResult.Saved)rename=false}catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}catch(error:Exception){message=chartDataError(os,error.message ?: "CHART_READ_FAILED")}finally {renaming=false}}},primary=true,enabled=editedName.isNotBlank()&&!renaming)
        MetroButton(os.t("取消","Cancel"),{rename=false},enabled=!renaming)
    }}
    if(editingMetadata&&dataset!=null&&folderDetails!=null)MetadataEditorDialog(os,os.t("文件夹资料","Folder metadata"),folderDetails.orEmpty(),
        onDismiss={editingMetadata=false},busy=savingMetadata,failure=message,onSave={metadata->
            savingMetadata=true;message=null
            scope.launch {
                try {val result=service?.updateMetadata(dataset.id,metadata);feedback(result);if(result is ChartCommandResult.Saved)editingMetadata=false}
                catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                catch(error:Exception){message=chartDataError(os,error.message ?: "CHART_READ_FAILED")}
                finally {savingMetadata=false}
            }
        })
    if(removing&&dataset!=null)ConfirmDialog(os,os.t("移除 ${dataset.name} 的本机副本？原文件夹与文件保留。","Remove the local copy of ${dataset.name}? Keep the original folder and files."),{removing=false}) {
        removing=false
        perform({service?.remove(dataset.id)}) {if(dataset.id in os.maps.selectedDatasetIds)os.maps.selectDataset(null);os.back()}
    }
    if(importUri!=null&&dataset!=null)ChartImportDialog(os,requireNotNull(importUri),dataset,{importUri=null}) {request->service?.importPackage(request)}
}

@Composable private fun ChartDataProgress(os:OsStore,service:ChartDataService?,state:ChartDataState) {
    val scope=rememberCoroutineScope()
    var retryError by remember {mutableStateOf<String?>(null)}
    retryError?.let {Label(it,14,LocalMetro.current.accentText)}
    if(state.loading||service==null)MetroProgress(os.t("正在读取数据目录","Reading data catalogue"))
    state.error?.let {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Label(chartDataError(os,it),14,LocalMetro.current.accentText);MetroButton(os.t("重新读取","Read again"),{scope.launch {try {service?.retryRestore();retryError=null}catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}catch(error:Exception){retryError=chartDataError(os,error.message ?: "CHART_READ_FAILED")}}})}}
    state.activeJob?.let {job->Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val title=when(job.phase) {
            ChartImportPhase.COPYING->os.t("正在复制资料","Copying data")
            ChartImportPhase.PARSING->os.t("正在读取数据对象","Reading data objects")
            ChartImportPhase.INDEXING->os.t("正在建立离线索引","Building offline index")
            ChartImportPhase.COMMITTING->os.t("正在安装完整版本","Installing complete version")
            ChartImportPhase.COMPLETE->os.t("已安装 · ","Installed · ")+chartDisplayText(job.name,120)
            ChartImportPhase.CANCELLED->os.t("导入已取消","Import cancelled")
            ChartImportPhase.INTERRUPTED->os.t("上次导入中断，原数据包保留","Import interrupted; previous dataset preserved")
            ChartImportPhase.FAILED->os.t("导入未完成","Import did not finish")
        }
        if(state.jobRunning)MetroProgress(title)else Label(title,15,LocalMetro.current.accentText)
        if(job.total>0&&state.jobRunning)Label("${job.completed} / ${job.total} · ${chartDisplayText(job.detail,200)}",13,LocalMetro.current.muted)
        else if(job.detail.isNotBlank())Label(chartDataError(os,job.detail),13,LocalMetro.current.muted)
        if(state.jobRunning&&job.phase!=ChartImportPhase.COMMITTING)MetroButton(os.t("取消导入","Cancel import"),{service?.cancelImport(job.requestId)})
        if(job.phase in setOf(ChartImportPhase.FAILED,ChartImportPhase.CANCELLED,ChartImportPhase.INTERRUPTED))MetroButton(os.t("重试原导入","Retry import"),{scope.launch {try {retryError=when(val result=service?.retryImport(job.requestId)) {
            is ChartCommandResult.Failed->chartDataError(os,result.reason)
            ChartCommandResult.Busy->os.t("另一项导入仍在进行","Another import is still running")
            null->os.t("图集服务仍在启动","Chart service is starting")
            else->null
        }}catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}catch(error:Exception){retryError=chartDataError(os,error.message ?: "CHART_READ_FAILED")}}})
    }}
    state.exportJob?.let {job->Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val title=when(job.phase) {
            ChartExportPhase.PREPARING->os.t("正在准备导出","Preparing export")
            ChartExportPhase.PACKAGING->os.t("正在打包文件夹","Packaging folder")
            ChartExportPhase.COPYING->os.t("正在保存图包","Saving package")
            ChartExportPhase.COMPLETE->os.t("已导出 · ","Exported · ")+chartDisplayText(job.name,120)
            ChartExportPhase.CANCELLED->os.t("导出已取消","Export cancelled")
            ChartExportPhase.INTERRUPTED->os.t("上次导出中断，请重新选择保存位置","Export interrupted. Choose a destination again.")
            ChartExportPhase.FAILED->os.t("导出未完成","Export did not finish")
        }
        if(state.exportRunning)MetroProgress(title)else Label(title,15,LocalMetro.current.accentText)
        if(job.total>0&&state.exportRunning)Label("${(job.completed.toDouble()/job.total*100).toInt().coerceIn(0,100)}%",13,LocalMetro.current.muted)
        if(job.phase==ChartExportPhase.FAILED&&job.detail.isNotBlank())Label(chartDataError(os,job.detail.substringBefore("; the destination")),13,LocalMetro.current.muted)
        if(job.detail.contains("incomplete file"))Label(os.t("保存位置可能有未完成的文件，请删除后重新导出。","The destination may contain an incomplete file. Delete it before exporting again."),13,LocalMetro.current.accentText)
        if(state.exportRunning)MetroButton(os.t("取消导出","Cancel export"),{service?.cancelExport(job.requestId)})
    }}
}
private val ChartDataState.exportRunning get()=exportJob?.phase in setOf(ChartExportPhase.PREPARING,ChartExportPhase.PACKAGING,ChartExportPhase.COPYING)
private val ChartDataState.jobRunning get()=activeJob?.phase in setOf(ChartImportPhase.COPYING,ChartImportPhase.PARSING,ChartImportPhase.INDEXING,ChartImportPhase.COMMITTING)

@Composable private fun ChartImportDialog(os:OsStore,uri:String,existing:ChartDataset?,onDismiss:()->Unit,onImport:suspend (ChartImportRequest)->ChartCommandResult?) {
    val context=LocalContext.current
    val source=remember(uri) {Uri.parse(uri)}
    val isFolder=remember(uri) {DocumentsContract.isTreeUri(source)}
    var name by rememberSaveable(uri) {mutableStateOf(existing?.name.orEmpty())}
    val eligibility=remember(uri) {(existing?.eligibility ?: DataEligibility()).copy(automatic=true)}
    var formatExpanded by rememberSaveable(uri) {mutableStateOf(false)}
    var rasterProduct by rememberSaveable(uri) {mutableStateOf(existing?.rasters.orEmpty().map {it.product}.distinct().singleOrNull())}
    val requestId=rememberSaveable(uri) {UUID.randomUUID().toString()}
    val scope=rememberCoroutineScope()
    var submitting by remember {mutableStateOf(false)}
    var error by remember(uri) {mutableStateOf<String?>(null)}
    var packageMetadata by remember(uri) {mutableStateOf<ChartPackageManifest?>(null)}
    var readingMetadata by remember(uri) {mutableStateOf(true)}
    var metadataInvalid by remember(uri) {mutableStateOf(false)}
    LaunchedEffect(uri) {
        try {
            val discovered=withContext(Dispatchers.IO) {
                val document=if(isFolder)DocumentsContract.buildDocumentUriUsingTree(source,DocumentsContract.getTreeDocumentId(source))else source
                val sourceName=context.contentResolver.query(document,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0)else null}
                val active=currentCoroutineContext()
                val manifest=if(!isFolder&&sourceName?.let {it.endsWith(".yklchart",true)||it.endsWith(".yklcharts",true)}==true) {
                    context.contentResolver.openInputStream(source)?.use {YokuliChartPackage.readManifest(it,"data") {active.ensureActive()}} ?: error("CHART_READ_FAILED")
                }else null
                sourceName to manifest
            }
            packageMetadata=discovered.second
            if(name.isBlank())name=chartDisplayText(discovered.second?.name ?: discovered.first.orEmpty(),120).ifBlank {os.t("我的航行数据","My navigation data")}
        }catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
        catch(failure:Exception) {metadataInvalid=true;error=chartDataError(os,failure.message ?: "CHART_READ_FAILED")}
        finally {readingMetadata=false}
    }
    AppDialog(onDismissRequest={if(!submitting)onDismiss()}) {AppDialogSurface {
        AppDialogTitle(if(existing==null)if(isFolder)os.t("连接数据文件夹","Connect data folder")else os.t("导入资料","Import data")else os.t("更新离线副本","Update offline copy"))
        Field(os.t("名称","Name"),name,{name=it.take(120)})
        if(readingMetadata)MetroProgress(os.t("读取资料说明","Reading source information"))
        packageMetadata?.let {manifest ->
            Label(os.t("${manifest.files.size} 份文件 · 自动保留文件资料","${manifest.files.size} files · Metadata retained automatically"),13,LocalMetro.current.muted)
        }
        MenuRow(os.t("数值网格来源","Numeric grid source"),rasterProduct ?: os.t("自动识别","Detect automatically"),if(formatExpanded)"minus"else"plus") {formatExpanded=!formatExpanded}
        if(formatExpanded) {
            ChoiceRow(os.t("自动识别","Detect automatically"),rasterProduct==null,os.t("读取文件名与元数据中的产品信息","Read product information from file names and metadata")) {rasterProduct=null}
            if(rasterProduct!=null&&rasterProduct!="GEBCO_2026_Grid")ChoiceRow(requireNotNull(rasterProduct),true) {}
            ChoiceRow(os.t("GEBCO 2026 高程","GEBCO 2026 elevation"),rasterProduct=="GEBCO_2026_Grid",os.t("仅为官方 GEBCO 2026 数值下载指定；不把影像或 TID 当水深","Use only for official GEBCO 2026 elevation downloads, not imagery or TID grids")) {rasterProduct="GEBCO_2026_Grid"}
        }
        Label(if(isFolder)os.t("读取文件夹及其子目录，安装完整离线副本。完成后在列表选用即可使用；更新保留原有选择。","Read the folder and its subfolders into a complete offline copy. Choose it in the list to use it; updates keep the existing selection.")
            else os.t("完整安装后才替换旧副本；不会修改原文件。","A complete installation replaces the previous copy. Original files stay untouched."),13,LocalMetro.current.muted)
        error?.let {Label(it,14,LocalMetro.current.accentText)}
        MetroButton(if(submitting)os.t("正在提交","Submitting")else os.t("开始导入","Import"),{
            submitting=true;error=null
            scope.launch {
                try {when(val result=onImport(ChartImportRequest(requestId,uri,chartDisplayText(name,120),eligibility,existing?.id,rasterProduct=rasterProduct))) {
                    is ChartCommandResult.Accepted,is ChartCommandResult.Saved->onDismiss()
                    is ChartCommandResult.Failed->error=chartDataError(os,result.reason)
                    ChartCommandResult.Busy->error=os.t("另一份资料正在导入，请稍后再试","Another import is running. Try again when it finishes.")
                    null->error=os.t("资料服务仍在启动","Data service is starting")
                }}catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
                catch(failure:Exception) {error=chartDataError(os,failure.message ?: "CHART_READ_FAILED")}
                finally {submitting=false}
            }
        },primary=true,enabled=name.isNotBlank()&&!submitting&&!readingMetadata&&!metadataInvalid)
        MetroButton(os.t("取消","Cancel"),onDismiss,enabled=!submitting)
    }}
}

/** 只展示真实源文件资料；文件夹字段不会覆写这里的来源、日期或许可。 */
@Composable private fun MetadataRows(os:OsStore,metadata:Map<String,String>) {
    metadata.forEach { (key,value)->
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Label(metadataFieldLabel(os,key),12,LocalMetro.current.muted)
            Label(if(key=="metadata.notice")os.t("部分资料过长，完整内容保留在原始文件中。","Some metadata exceeds display limits. The original file retains the complete content.")else chartDisplayText(value,8192),14)
        }
    }
}
private fun metadataFieldLabel(os:OsStore,key:String):String=when(key) {
    "description"->os.t("说明","Description")
    "provider"->os.t("来源","Source")
    "license"->os.t("许可","License")
    "attribution"->os.t("署名","Attribution")
    "createdAt"->os.t("打包时间","Packaged at")
    else->chartDisplayText(key,80)
}

internal fun chartDataError(os:OsStore,code:String):String=chartDataErrorText(os,chartDisplayText(code.substringAfter("Exception:").trim(),400))
private fun chartDataErrorText(os:OsStore,code:String):String=when {
    code.contains("MARINE_CLIENT_NOT_ATTACHED")||code.contains("STALE_MARINE_CLIENT_SESSION")||code.contains("MARINE_CORE_UNAVAILABLE")->os.t("系统服务正在重新连接，请稍后重试。原资料保留。","Reconnecting to the system service. Try again shortly; existing data is preserved.")
    code=="MARINE_COMMAND_OUTCOME_UNKNOWN"->os.t("系统连接中断，操作结果待确认。请先查看任务进度，勿重复提交。","The connection was interrupted and the result is uncertain. Check task progress before submitting again.")
    code=="CHART_DATASET_MISSING"||code=="CHART_CELL_MISSING"->os.t("此文件夹或文件已更新，请返回图册重新打开。","This folder or file has changed. Reopen it from the library.")
    code.contains("CHART_METADATA_REVISION_CHANGED")->os.t("文件夹内容已更新，请重新读取资料。","The folder has changed. Read its metadata again.")
    code.startsWith("CHART_EXPORT_SOURCE_MISSING")->os.t("这个旧副本未保留原始文件。请重新扫描或导入原文件夹后再导出。","This older copy has no retained source files. Rescan or reimport the original folder before exporting.")
    code.startsWith("CHART_EXPORT_")->os.t("未能保存完整图包。请检查目标文件夹的权限和可用空间后重试。","The complete package could not be saved. Check destination access and available space, then retry.")
    code.startsWith("CHART_METADATA_")||code.startsWith("YKLCHART_METADATA_")->os.t("文件夹资料未保存，请检查字段名和内容长度后重试。","Folder metadata was not saved. Check field names and text lengths, then retry.")
    code=="YKLCHART_KIND_MISMATCH"->os.t("这是显示海图包，请在图册的“海图”页导入。","This package contains display charts. Import it in the library's Charts tab.")
    code=="YKLCHART_STORAGE_FAILED"->os.t("图包未能完整写入，请检查可用存储空间；原离线集合保留。","The package could not be written completely. Check available storage; the previous offline collection is preserved.")
    code=="YKLCHART_VERSION_UNSUPPORTED"->os.t("此图包版本暂不支持，请更新应用或获取兼容的 .yklchart 图包。","This package version is unsupported. Update the app or obtain a compatible .yklchart package.")
    code.contains("YKLCHART")&&code.contains("SIZE_LIMIT")->os.t("图包超出可处理范围，原离线集合保留。","The package exceeds the supported size. The previous offline collection is preserved.")
    code.startsWith("YKLCHART_")->os.t("图包不完整、格式无效或校验失败，未安装。请重新获取完整的 .yklchart 文件；原离线集合保留。","The package is incomplete, invalid or failed verification and was not installed. Obtain a complete .yklchart file and retry. The previous offline collection is preserved.")
    code.startsWith("LINZ_KEY_REQUIRED")->os.t("请先在设置填写 LINZ API 密钥；已有区域仍可离线使用。","Add a LINZ API key in Settings; saved areas remain available offline.")
    code.startsWith("LINZ_KEY_REJECTED")->os.t("LINZ 未接受此密钥，请在设置更换。","LINZ rejected the API key. Replace it in Settings.")
    code.startsWith("LINZ_KEY_")->os.t("密钥未能保存，请检查输入后重试。","The key could not be saved. Check it and retry.")
    code.startsWith("LINZ_AREA_")->os.t("区域过大或资料过密，请增加途经点、缩小范围，或导入离线文件夹。","Area too large or complex. Add a waypoint, reduce the area, or import an offline folder.")
    code.startsWith("LINZ_NO_HYDRO_COVERAGE")->os.t("LINZ 在这个区域没有水深覆盖，请换用自定义资料。","LINZ has no depth coverage here. Choose custom data.")
    code.startsWith("LINZ_IMPORT_BUSY")->os.t("另一份资料正在导入，请稍后再规划。","Another import is running. Plan again once it finishes.")
    code.startsWith("LINZ_")->os.t("LINZ 区域资料未完整下载；原离线副本保留，请联网后重试。","The LINZ region could not be downloaded completely. The previous offline copy is preserved; reconnect and retry.")
    code.startsWith("S63_")||code.startsWith("S57_ENCRYPTED")->os.t("这可能是加密的 S-63 图包。当前缺少获许可客户端与该设备的 User Permit，不能解密导入。","This may be an encrypted S-63 package. A licensed client and this device's User Permit are required; encrypted data cannot be imported.")
    code.startsWith("S57_MISSING_UPDATE")->os.t("增量更新缺序列，需要更新 ","A sequential update is missing: ")+code.substringAfter(':')
    code.startsWith("S57_BASE_MISSING")->os.t("缺少对应的 .000 基图：","The .000 base cell is missing: ")+code.substringAfter(':')
    code.contains("PERMISSION")->os.t("原文件访问授权失效，请重新选择文件或文件夹。","Source permission was lost. Select the file or folder again.")
    code.contains("STORAGE")||code.contains("SIZE_LIMIT")->os.t("存储空间不足或图包超出可处理范围；原版本保留。","Storage is low or this package exceeds the supported size. The previous version is preserved.")
    code.startsWith("S57_HORIZONTAL_DATUM")->os.t("此图使用的水平基准尚不支持，不会直接当作 WGS84。","This horizontal datum is unsupported and is not treated as WGS84.")
    code=="NO_EXPLICIT_ENC_COVERAGE"->os.t("没有明确的 ENC 覆盖面，规划需要补足资料。","No explicit ENC coverage; planning needs more data.")
    code=="SURVEY_QUALITY_UNSPECIFIED"->os.t("测量质量未提供","Survey quality is unspecified")
    code.startsWith("UNINTERPRETED")||code.startsWith("UNSUPPORTED")->os.t("存在尚未解释的对象或语义，不能当作无障碍：","Uninterpreted objects or semantics cannot be treated as clear water: ")+code.substringAfter(':',code)
    code=="CHART_CHANGED_DURING_IMPORT"->os.t("导入期间图集被修改，已保留原图集。检查后重试。","The dataset changed during import. The original is preserved; review and retry.")
    code=="CHART_NO_SUPPORTED_DATA"->os.t("没有找到 S-57、GeoPackage、GEBCO 数值 GeoTIFF 或 .asc 网格。MBTiles 请在“海图”页导入。","No S-57, GeoPackage, GEBCO numeric GeoTIFF or .asc grid found. Import MBTiles in Charts.")
    code=="CHART_CELL_ORDER_INVALID"->os.t("资料内容已更新，请返回文件夹后重新排序。","The folder contents changed. Reopen it before reordering.")
    code.startsWith("GPKG_SRS_UNSUPPORTED")->os.t("这个坐标系暂不支持。请将矢量图层导出为 WGS84（EPSG:4326）或 Web Mercator（EPSG:3857）。","This coordinate system is unsupported. Export vector layers as WGS84 (EPSG:4326) or Web Mercator (EPSG:3857).")
    code=="GPKG_NO_FEATURE_TABLES"||code=="GPKG_NO_FEATURES"->os.t("这个包没有矢量对象。仅有地图图片不能提供水深与覆盖资料。","This package has no vector features. Map images alone do not provide depth and coverage data.")
    code=="GPKG_DEPTH_MINIMUM_MISSING"->os.t("该深度区域没有最浅值，不能证明整片区域可通行。","This depth area has no minimum depth and cannot establish passage clearance.")
    code=="GPKG_SOUNDING_DEPTH_MISSING"->os.t("该测深点没有明确的水深值；几何高度不会自动当作水深。","This sounding has no explicit depth. Geometry elevation is not treated as water depth.")
    code=="GPKG_VERTICAL_DATUM_MISSING"->os.t("该水深资料没有深度基准，暂不能用于自动规划。","This depth has no vertical datum and cannot be used for automatic planning.")
    code=="GPKG_MORE_OBJECT_ISSUES"->os.t("更多对象存在资料问题，请查看具体对象。","More objects have data issues. Open their details to review.")
    code=="GEOMETRY_MISSING"->os.t("对象缺少可定位的几何资料。","This object has no usable location geometry.")
    code=="REFERENCE_ONLY_LINZ_LDS"->os.t("LINZ 开放水文参考资料，不替代官方航海图。","LINZ open hydrographic reference data does not replace official nautical charts.")
    code=="REFERENCE_COVERAGE_FROM_LINZ_DEPTH_AREAS"->os.t("参考覆盖来自实际水深面，不代表完整 ENC 覆盖。","Reference coverage comes from depth-area geometry; it is not complete ENC coverage.")
    code.startsWith("LINZ_LDS_")->os.t("LINZ 图层字段与官方结构不符，未完整安装。详细原因：","The LINZ layer does not match its official schema and was not fully installed. Detail: ")+code
    code=="GEBCO_PRODUCT_DECLARATION_REQUIRED"->os.t("未能识别 GEBCO 年份。若来自 GEBCO 2026 官方下载，在导入时将数值网格来源设为 GEBCO 2026。","The GEBCO release was not identified. For an official GEBCO 2026 download, select GEBCO 2026 as the numeric grid source when importing.")
    code=="GEBCO_PRODUCT_DECLARATION_CONFLICT"->os.t("文件中的 GEBCO 版本与所选来源不一致，请改用自动识别。","The GEBCO version in the file does not match the selected source. Use automatic detection.")
    code=="GEBCO_NOT_FOR_NAVIGATION"||code=="REFERENCE_BATHYMETRY_NOT_ENC"||code=="REFERENCE_ONLY_GEBCO_NOT_FOR_NAVIGATION"||code=="REFERENCE_ONLY_GEBCO"->os.t("全球高程参考资料，不是官方电子海图；规划结果需要核对。","Global elevation reference data, not an official ENC. Planning results need review.")
    code=="GEBCO_REFERENCE_GRID"->os.t("GEBCO 参考高程网格","GEBCO reference elevation grid")
    code=="NO_OBSTRUCTION_OR_LEGAL_COVERAGE"||code=="REFERENCE_ONLY_NO_OBSTRUCTION_OR_LEGAL_COVERAGE"->os.t("网格不包含障碍、航标与通航限制。","The grid does not include hazards, navigation marks or passage restrictions.")
    code=="GEBCO_TID_OR_IMAGE_IS_NOT_ELEVATION"->os.t("这不是高程数据。请选择 GEBCO 的数值高程文件，不是图片、阴影图或 TID 分类网格。","This is not elevation data. Choose a GEBCO numeric elevation file, not an image, shaded relief or TID classification grid.")
    code=="GEBCO_EXPECTED_15_ARCSECOND_GRID"->os.t("需要 GEBCO 原生 15 角秒网格；请勿用重采样图片替代数值资料。","A native 15-arc-second GEBCO grid is required. Resampled images are not numeric source data.")
    code.contains("NETCDF")->os.t("此版本不解码 NetCDF。请从 GEBCO 下载数值 GeoTIFF 或 ESRI ASCII (.asc)，或先转换原数据。","NetCDF is not decoded in this version. Download numeric GeoTIFF or ESRI ASCII (.asc) from GEBCO, or convert the original data first.")
    code.startsWith("GEBCO_")->os.t("高程网格未能完整读取，原副本保留。原因：","The elevation grid could not be read completely. The previous copy is preserved. Reason: ")+code
    code.startsWith("GPKG_")->os.t("数据包未能完整读取，原版本保留。请检查坐标系、几何与资料字段。详细原因：","The dataset could not be fully read. The previous version is preserved. Check its CRS, geometry and data fields. Detail: ")+code
    code.startsWith("CHART_QUERY")||code=="CHART_READ_FAILED"->os.t("资料读取未完成，请重试。","Data could not be read. Try again.")
    code.startsWith("S57_")->os.t("图包未能完整解析，原版本保留。详细原因：","The package could not be fully read. Previous charts are preserved. Detail: ")+code
    else->code
}

/** 保留日期变更线两侧边界，由原生地图按整组坐标取景。 */
private fun ChartBounds.chartCorners():List<GeoPoint> = listOf(GeoPoint(south,west),GeoPoint(north,west),GeoPoint(north,east),GeoPoint(south,east))
