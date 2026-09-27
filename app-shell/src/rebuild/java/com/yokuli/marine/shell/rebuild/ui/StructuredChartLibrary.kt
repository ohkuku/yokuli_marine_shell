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
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset
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
    val ordered=data.datasets.sortedBy {it.name.lowercase(java.util.Locale.ROOT)}
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(start=insets.pageStart,end=insets.pageEnd,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {ChartDataProgress(os,service,data)}
            item {ChoiceRow(os.t("不使用数据","No data folder"),selected==null,os.t("保留海图显示，手动规划航线","Keep the chart background and plan routes manually")) {os.maps.selectDataset(null)}}
            if(data.datasets.isEmpty()&&!data.loading)item {
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
                MenuRow(os.t("支持哪些资料？","Supported data"),"S-57 · LINZ · GEBCO",if(formatDetails)"minus"else"plus") {formatDetails=!formatDetails}
                if(formatDetails) {
                    Label(os.t("S-57：未加密的 .000、连续更新及 ZIP 交换集。GeoPackage：开放矢量资料，支持 LINZ 水文图层与 Yokuli 资料字段。","S-57: unencrypted .000 cells, sequential updates and ZIP exchange sets. GeoPackage: open vector data, including LINZ hydrographic layers and Yokuli fields."),13,LocalMetro.current.muted)
                    Label(os.t("GEBCO：数值 GeoTIFF 或 ESRI ASCII 网格（.asc），可离线查询和参考规划。它不是 ENC，不能证明近岸水深与障碍安全；NetCDF 文件需要先转换为上述格式。","GEBCO: numeric GeoTIFF or ESRI ASCII grids (.asc), for offline queries and reference planning. It is not an ENC and cannot establish safe inshore depth or clearance. Convert NetCDF files to one of these formats first."),13,LocalMetro.current.muted)
                    Label(os.t("每个文件夹代表一类资料，一次选择一个。文件夹内可包含多份文件，并调整其覆盖优先级。MBTiles 图片海图在“海图”页管理。","Each folder is one data collection; choose one at a time. Set overlap priority between files inside it. Manage MBTiles chart images in Charts."),13,LocalMetro.current.muted)
                    Label(os.t("资料用途需按提供方许可登记。S-63 加密图包需要获许可客户端与设备 User Permit，当前不能解密。","Record permitted use according to the provider's licence. Encrypted S-63 packages need a licensed client and device User Permit and cannot currently be decrypted."),13,LocalMetro.current.muted)
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
        if(dataset.sourceIsFolder)os.t("${dataset.cells.size} 份资料","${dataset.cells.size} sources")else os.t("文件导入","Imported file"),
        if(rasterCount>0)os.t("$rasterCount 个高程网格","$rasterCount elevation grids")else if(objects>0)os.t("$objects 个对象","$objects objects")else null,
        chartUseLabel(os,dataset.eligibility),
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
    var permission by remember {mutableStateOf(false)}
    var removing by remember {mutableStateOf(false)}
    var expanded by rememberSaveable(datasetId) {mutableStateOf<String?>(null)}
    var importUri by rememberSaveable(datasetId) {mutableStateOf<String?>(null)}
    var message by remember {mutableStateOf<String?>(null)}
    val update=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->importUri=uri?.toString()}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->importUri=uri?.toString()}
    val insets=LocalShellHorizontalInsets.current
    fun feedback(result:ChartCommandResult?) {message=when(result) {
        is ChartCommandResult.Failed->chartDataError(os,result.reason)
        ChartCommandResult.Busy->os.t("请等当前导入完成，或先取消","Wait for the import or cancel it first")
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
                val analysisAllowed=dataset.eligibility.allowsAnalysis(System.currentTimeMillis())
                val hasRaster=dataset.rasters.orEmpty().isNotEmpty()
                val hasVector=dataset.cells.any {it.featureCount>0}
                ChoiceRow(os.t("使用此数据文件夹","Use this data folder"),selected,datasetSummary(os,dataset),enabled=selected||dataset.offlineReadable) {os.maps.selectDataset(dataset.id)}
                if(hasVector)MenuRow(os.t("浏览资料内容","Explore contents"),os.t("水深、岸线、航标与障碍","Depths, coastlines, marks and hazards"),"layers") {os.open("chartobjects:${dataset.id}")}
                if(dataset.eligibility.provider.isNotBlank())Label(dataset.eligibility.provider,13,LocalMetro.current.muted)
                if(dataset.cells.any {it.referenceOnly}||hasRaster)
                    Label(os.t("参考资料 · 自动建议始终需要人工核对，不能替代正式海图、瞭望和现场操船。","Reference data · Suggested routes always require human review and do not replace official charts, watchkeeping, or vessel handling."),13,LocalMetro.current.muted)
                AppSection(os.t("粗略规划能力","Coarse planning capability"))
                if(hasRaster)Label(os.t("GEBCO/数值高程可帮助粗略绕开陆地、明显浅水和无数据区；它不包含沉船、礁石、航标或通航限制。","GEBCO/numeric elevation can help roughly avoid land, obvious shallow water and missing-data areas. It does not contain wrecks, rocks, aids or passage restrictions."),13,LocalMetro.current.muted)
                if(hasVector)Label(os.t("矢量水文资料可补充岸线、水深面、障碍、限制区等对象，实际能力取决于文件中包含的图层。","Vector hydrographic data can add coastlines, depth areas, hazards and restrictions; actual capability depends on the included layers."),13,LocalMetro.current.muted)
                if(analysisAllowed)Label(os.t("已登记本地分析用途 · 自动建议会保留来源限制与复核提示。","Local analysis use recorded · suggestions retain source limitations and review warnings."),13,LocalMetro.current.accentText)
                else MenuRow(os.t("自动建议当前未启用","Route suggestions are not enabled"),chartUseLabel(os,dataset.eligibility),"settings") {message=null;permission=true}
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
                        Label(if(grid!=null)"${grid.width} × ${grid.height} · "+os.t("高程网格","Elevation grid")else if(cell.cancelled)os.t("提供方已取消","Cancelled by provider")else if(cell.referenceOnly)os.t("参考资料 · 离线副本","Reference data · Offline copy")else os.t("${cell.featureCount} 个对象 · 第 ${cell.edition} 版","${cell.featureCount} objects · Edition ${cell.edition}"),13,LocalMetro.current.muted)
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
                    grid?.let {raster->
                        Label(raster.product,14)
                        Label(os.t("像元间距 ","Cell spacing ")+"${decimal(raster.pixelWidthDegrees*3600,1)}″ × ${decimal(raster.pixelHeightDegrees*3600,1)}″",13,LocalMetro.current.muted)
                        Label(os.t("数值为相对海平面的高程；负值表示海底，不是海图基准水深。","Values are elevations relative to sea level. Negative values describe the seabed, not chart-datum depth."),13,LocalMetro.current.muted)
                        Label(os.t("缺测像元保持未知；不会补成零水深或插值成精细测深。","Missing cells remain unknown; they are not zero-depth values or interpolated fine soundings."),13,LocalMetro.current.muted)
                        raster.bounds.forEach {bounds->Label(os.formatCoordinates(GeoPoint(bounds.south,bounds.west))+" → "+os.formatCoordinates(GeoPoint(bounds.north,bounds.east)),12,LocalMetro.current.muted)}
                    }
                    cell.compilationScale?.let {Label(os.t("编图比例尺 ","Compilation scale ")+"1:$it",13,LocalMetro.current.muted)}
                    cell.issueDate?.let {Label(os.t("发布日期 ","Issue date ")+it,13,LocalMetro.current.muted)}
                    if(grid==null&&cell.quality.isNotEmpty())Label(os.t("资料质量 · ","Data quality · ")+cell.quality.joinToString(" · "),13,LocalMetro.current.muted)
                    cell.issues.filterNot {it=="REFERENCE_ONLY_GEBCO"}.map {chartDataError(os,it)}.distinct().forEach {Label(it,13,LocalMetro.current.muted)}
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
            MenuRow(os.t("来源与用途","Source and use"),os.t("提供方、许可与有效期","Provider, permission and validity"),"settings") {message=null;permission=true}
            AppSection(os.t("更新内容","Update contents"))
            if(dataset.sourceUri!=null)MetroButton(if(dataset.sourceIsFolder)os.t("重新扫描原文件夹","Rescan original folder")else os.t("重新读取原文件","Read original file again"),{
                perform({service?.importPackage(ChartImportRequest(UUID.randomUUID().toString(),requireNotNull(dataset.sourceUri),dataset.name,dataset.eligibility,dataset.id,
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
    if(permission&&dataset!=null) {
        val gebco2026=dataset.rasters.orEmpty().isNotEmpty()&&dataset.rasters.orEmpty().all {it.product=="GEBCO_2026_Grid"}
        ChartEligibilityDialog(
            os,dataset.eligibility,{permission=false},error=message,
            suggestedProvider=if(gebco2026)"GEBCO Bathymetric Compilation Group 2026" else null,
            suggestedEvidence=if(gebco2026)"GEBCO_2026 Grid public-domain Terms of Use; reference terrain analysis only; GEBCO states it should not be used for navigation or safety at sea." else null,
            suggestedNote=if(gebco2026)os.t(
                "GEBCO 官方条款允许免费使用与改编，但明确说明不应用于导航或海上安全。Yokuli 只把它作为粗略参考地形，生成结果保持“需要核对”。",
                "GEBCO permits free use and adaptation but explicitly says the grid should not be used for navigation or safety at sea. Yokuli treats it only as coarse reference terrain and keeps suggestions in Review."
            ) else null,
        ) {eligibility->perform({service?.updateEligibility(dataset.id,eligibility)}) {permission=false}}
    }
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
    state.error?.let {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Label(chartDataError(os,it),14,LocalMetro.current.accentText);MetroButton(os.t("重新读取","Read again"),{scope.launch {service?.retryRestore()}})}}
    state.activeJob?.let {job->Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val title=when(job.phase) {
            ChartImportPhase.COPYING->os.t("正在复制资料","Copying data")
            ChartImportPhase.PARSING->os.t("正在读取数据对象","Reading data objects")
            ChartImportPhase.INDEXING->os.t("正在建立离线索引","Building offline index")
            ChartImportPhase.COMMITTING->os.t("正在安装完整版本","Installing complete version")
            ChartImportPhase.COMPLETE->os.t("已安装 · ","Installed · ")+job.name
            ChartImportPhase.CANCELLED->os.t("导入已取消","Import cancelled")
            ChartImportPhase.INTERRUPTED->os.t("上次导入中断，原数据包保留","Import interrupted; previous dataset preserved")
            ChartImportPhase.FAILED->os.t("导入未完成","Import did not finish")
        }
        if(state.jobRunning)MetroProgress(title)else Label(title,15,LocalMetro.current.accentText)
        if(job.total>0&&state.jobRunning)Label("${job.completed} / ${job.total} · ${job.detail}",13,LocalMetro.current.muted)
        else if(job.detail.isNotBlank())Label(chartDataError(os,job.detail),13,LocalMetro.current.muted)
        if(state.jobRunning&&job.phase!=ChartImportPhase.COMMITTING)MetroButton(os.t("取消导入","Cancel import"),{service?.cancelImport(job.requestId)})
        if(job.phase in setOf(ChartImportPhase.FAILED,ChartImportPhase.CANCELLED,ChartImportPhase.INTERRUPTED))MetroButton(os.t("重试原导入","Retry import"),{scope.launch {retryError=when(val result=service?.retryImport(job.requestId)) {
            is ChartCommandResult.Failed->chartDataError(os,result.reason)
            ChartCommandResult.Busy->os.t("另一项导入仍在进行","Another import is still running")
            null->os.t("图集服务仍在启动","Chart service is starting")
            else->null
        }}})
    }}
}
private val ChartDataState.jobRunning get()=activeJob?.phase in setOf(ChartImportPhase.COPYING,ChartImportPhase.PARSING,ChartImportPhase.INDEXING,ChartImportPhase.COMMITTING)

@Composable private fun ChartImportDialog(os:OsStore,uri:String,existing:ChartDataset?,onDismiss:()->Unit,onImport:suspend (ChartImportRequest)->ChartCommandResult?) {
    val context=LocalContext.current
    val source=remember(uri) {Uri.parse(uri)}
    val isFolder=remember(uri) {DocumentsContract.isTreeUri(source)}
    var name by rememberSaveable(uri) {mutableStateOf(existing?.name.orEmpty())}
    var eligibility by remember(uri) {mutableStateOf(existing?.eligibility ?: DataEligibility(ChartUse.REFERENCE_ONLY))}
    var editingEligibility by remember {mutableStateOf(false)}
    var formatExpanded by rememberSaveable(uri) {mutableStateOf(false)}
    var rasterProduct by rememberSaveable(uri) {mutableStateOf(existing?.rasters.orEmpty().map {it.product}.distinct().singleOrNull())}
    val requestId=rememberSaveable(uri) {UUID.randomUUID().toString()}
    val scope=rememberCoroutineScope()
    var submitting by remember {mutableStateOf(false)}
    var error by remember(uri) {mutableStateOf<String?>(null)}
    LaunchedEffect(uri) {
        if(name.isNotBlank())return@LaunchedEffect
        val discovered=withContext(Dispatchers.IO) {
            runCatching {
                val document=if(isFolder)DocumentsContract.buildDocumentUriUsingTree(source,DocumentsContract.getTreeDocumentId(source))else source
                context.contentResolver.query(document,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0)else null}
            }.getOrNull()
        }
        if(name.isBlank())name=discovered?.takeIf {it.isNotBlank()}?.take(120) ?: os.t("我的航行数据","My navigation data")
    }
    AppDialog(onDismissRequest={if(!submitting)onDismiss()}) {AppDialogSurface {
        AppDialogTitle(if(existing==null)if(isFolder)os.t("连接数据文件夹","Connect data folder")else os.t("导入资料","Import data")else os.t("更新离线副本","Update offline copy"))
        Field(os.t("名称","Name"),name,{name=it.take(120)})
        MenuRow(os.t("资料用途","Permitted use"),chartUseLabel(os,eligibility),"settings") {editingEligibility=true}
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
                try {when(val result=onImport(ChartImportRequest(requestId,uri,name.trim(),eligibility,existing?.id,rasterProduct=rasterProduct))) {
                    is ChartCommandResult.Accepted,is ChartCommandResult.Saved->onDismiss()
                    is ChartCommandResult.Failed->error=chartDataError(os,result.reason)
                    ChartCommandResult.Busy->error=os.t("另一份资料正在导入，请稍后再试","Another import is running. Try again when it finishes.")
                    null->error=os.t("资料服务仍在启动","Data service is starting")
                }}catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
                catch(failure:Exception) {error=chartDataError(os,failure.message ?: "CHART_READ_FAILED")}
                finally {submitting=false}
            }
        },primary=true,enabled=name.isNotBlank()&&!submitting)
        MetroButton(os.t("取消","Cancel"),onDismiss,enabled=!submitting)
    }}
    if(editingEligibility) {
        val gebco2026=rasterProduct=="GEBCO_2026_Grid"
        ChartEligibilityDialog(
            os,eligibility,{editingEligibility=false},
            suggestedProvider=if(gebco2026)"GEBCO Bathymetric Compilation Group 2026" else null,
            suggestedEvidence=if(gebco2026)"GEBCO_2026 Grid public-domain Terms of Use; reference terrain analysis only; GEBCO states it should not be used for navigation or safety at sea." else null,
            suggestedNote=if(gebco2026)os.t("GEBCO 仅作为粗略参考地形；不得把建议当作安全航线。","GEBCO is coarse reference terrain only; suggestions are not safe-route guarantees.") else null,
        ) {eligibility=it;editingEligibility=false}
    }
}

@Composable private fun ChartEligibilityDialog(
    os:OsStore,initial:DataEligibility,onDismiss:()->Unit,error:String?=null,
    suggestedProvider:String?=null,suggestedEvidence:String?=null,suggestedNote:String?=null,
    onSave:(DataEligibility)->Unit,
) {
    var use by remember(initial) {mutableStateOf(initial.use)}
    var provider by remember(initial) {mutableStateOf(initial.provider)}
    var evidence by remember(initial) {mutableStateOf(initial.licenceEvidence)}
    var expiry by remember(initial) {mutableStateOf(initial.validUntilUtc?.let {java.time.Instant.ofEpochMilli(it-1L).atZone(ZoneOffset.UTC).toLocalDate().toString()}.orEmpty())}
    val expiryValue=if(expiry.isBlank())null else runCatching {LocalDate.parse(expiry).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()}.getOrNull()
    val valid=expiry.isBlank()||expiryValue!=null
    AppDialog(onDismissRequest=onDismiss) {AppDialogSurface {
        AppDialogTitle(os.t("资料用途","Permitted use"))
        error?.let {Label(it,14,LocalMetro.current.accentText)}
        ChoiceRow(os.t("仅浏览","Viewing only"),use!=ChartUse.ANALYSIS_ALLOWED,os.t("可查询，不参与自动规划","View and query; excluded from automatic planning")) {use=ChartUse.REFERENCE_ONLY}
        ChoiceRow(os.t("已确认可用于本地分析","Permission for local analysis confirmed"),use==ChartUse.ANALYSIS_ALLOWED,os.t("按资料许可登记，不会提升测量精度或变成 ENC","Record the provider licence; this does not increase precision or turn data into an ENC")) {
            use=ChartUse.ANALYSIS_ALLOWED
            if(provider.isBlank())suggestedProvider?.let {provider=it}
            if(evidence.isBlank())suggestedEvidence?.let {evidence=it}
        }
        suggestedNote?.let {Label(it,12,LocalMetro.current.muted)}
        Field(os.t("资料提供方","Data provider"),provider,{provider=it.take(200)})
        if(use==ChartUse.ANALYSIS_ALLOWED)Field(os.t("许可依据或授权说明","Permission evidence"),evidence,{evidence=it.take(1000)},multiline=true)
        Field(os.t("有效期（可选 YYYY-MM-DD）","Valid through (optional YYYY-MM-DD)"),expiry,{expiry=it.take(10)})
        if(!valid)Label(os.t("日期格式应为 YYYY-MM-DD","Use YYYY-MM-DD"),13,LocalMetro.current.accentText)
        Label(os.t("这是资料持有人的用途记录，不是 ENC 认证。未确认的许可、过期或缺少覆盖的资料不能证明航线可通过。","This records the holder's permission evidence; it is not ENC certification. Unknown permission, expiry or missing coverage cannot prove a route passable."),13,LocalMetro.current.muted)
        MetroButton(os.t("保存用途","Save use"),{onSave(DataEligibility(use,provider.trim(),evidence.trim(),expiryValue))},primary=true,
            enabled=valid&&(use!=ChartUse.ANALYSIS_ALLOWED||provider.isNotBlank()&&evidence.isNotBlank()&&(expiryValue==null||expiryValue>System.currentTimeMillis())))
        MetroButton(os.t("取消","Cancel"),onDismiss)
    }}
}
internal fun chartUseLabel(os:OsStore,value:DataEligibility):String=when {
    value.validUntilUtc?.let {it<=System.currentTimeMillis()}==true->os.t("许可已过期","Permission expired")
    value.allowsAnalysis(System.currentTimeMillis())->os.t("已登记分析用途","Analysis permission recorded")
    value.use==ChartUse.CANCELLED->os.t("资料已取消","Cancelled data")
    value.use==ChartUse.REFERENCE_ONLY->os.t("仅浏览","Viewing only")
    else->os.t("用途未确认","Use unconfirmed")
}
internal fun chartDataError(os:OsStore,code:String):String=when {
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
