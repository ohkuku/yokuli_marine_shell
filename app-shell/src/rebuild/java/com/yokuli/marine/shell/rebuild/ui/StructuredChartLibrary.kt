package com.yokuli.marine.shell.rebuild.ui

import android.net.Uri
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
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** 图册的数据页管理离线资料，不承担路线生成，也不会因浏览资料而改变选用内容。 */
@Composable internal fun StructuredChartLibraryPane(os:OsStore) {
    val service=os.marine?.system?.charts
    val data=service?.state?.collectAsState()?.value ?: ChartDataState()
    val scope=rememberCoroutineScope()
    var importUri by rememberSaveable {mutableStateOf<String?>(null)}
    var formatDetails by rememberSaveable {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    val file=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->importUri=uri?.toString()}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->importUri=uri?.toString()}
    val insets=LocalShellHorizontalInsets.current
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(start=insets.pageStart,end=insets.pageEnd,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {ChartDataProgress(os,service,data)}
            message?.let {item {Label(it,14,LocalMetro.current.accentText)}}
            if(data.datasets.isEmpty()&&!data.loading)item {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.padding(vertical=24.dp)) {
                    Glyph("layers",Modifier.size(38.dp),LocalMetro.current.accent)
                    Label(os.t("水深、航标、障碍，都在这里","Depths, marks and hazards"),24)
                    Label(os.t("导入 S-57 海图或 GeoPackage 数据包，查看具体对象，并与 MBTiles 底图搭配使用。有足够的有效资料，才能自动规划航线。","Import S-57 charts or GeoPackage data, explore their objects and pair them with MBTiles maps. Automatic routing needs sufficient usable data."),15,LocalMetro.current.muted)
                }
            }
            items(data.datasets,key={it.id}) {dataset->
                val selected=dataset.id in os.maps.selectedDatasetIds
                Row(Modifier.fillMaxWidth().clickable {os.open("chartdataset:${dataset.id}")}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Glyph("layers",Modifier.size(28.dp),if(selected)LocalMetro.current.accent else LocalMetro.current.fg)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Label(dataset.name,19)
                        Label(os.t("${dataset.cells.sumOf {it.featureCount}} 个对象 · ${dataset.format}","${dataset.cells.sumOf {it.featureCount}} objects · ${dataset.format}")+" · "+chartUseLabel(os,dataset.eligibility),13,LocalMetro.current.muted)
                        if(selected)Label(os.t("海图与规划当前选用","Selected for Chart and planning"),13,LocalMetro.current.accentText)
                        if(!dataset.offlineReadable)Label(os.t("离线索引缺失 · 重新导入恢复","Offline index missing · Import again to restore"),13,LocalMetro.current.accentText)
                    }
                    Glyph("chevron_right",Modifier.size(18.dp),LocalMetro.current.muted)
                }
            }
            item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                MenuRow(os.t("支持哪些海图？","Supported charts"),"S-57 · GeoPackage",if(formatDetails)"minus"else"plus") {formatDetails=!formatDetails}
                if(formatDetails) {
                Label(os.t("GeoPackage（.gpkg）是可用 QGIS 等工具维护的开放地理数据包。支持 WGS84 / Web Mercator 矢量对象；水深、覆盖等字段需要明确声明，不会从地图颜色或三维高度推算。", "GeoPackage (.gpkg) is an open geodata package editable with tools such as QGIS. WGS84 / Web Mercator vector features are supported. Depth and coverage need explicit fields; neither map colours nor 3D elevation imply depth."),13,LocalMetro.current.muted)
                Label(os.t("支持未加密 S-57 .000、连续更新、ZIP 交换集或文件夹。原文件不修改，索引安装失败保留上一完整版本。","Supports unencrypted S-57 base cells, sequential updates, ZIP exchange sets and folders. Source files stay untouched; a failed installation preserves the previous complete version."),13,LocalMetro.current.muted)
                Label(os.t("新西兰 NZ ENC 使用 S-63：目前缺少正式 OEM/设备 User Permit 与获许可客户端接入，不能读取其加密包。LINZ 普通水文下载仅作参考，不能替代 ENC。","NZ ENC uses S-63. A licensed client integration and production OEM/device User Permit are not present, so encrypted packages cannot be read. General LINZ hydrographic downloads are reference data and do not replace ENCs."),13,LocalMetro.current.muted)
                Label(os.t("地图显示可查询的海图对象与基本色彩，尚未包含完整的标准电子海图符号。","The map shows queryable chart objects and basic colours. The complete standard electronic-chart symbol set is not included."),13,LocalMetro.current.muted)
                }
            }}
        }
        AppCommandBar(os,listOf(
            AppCommand("import-data","plus",os.t("导入数据海图","Import data chart"),{file.launch(arrayOf("*/*"))},enabled=service!=null&&!data.loading&&!data.jobRunning),
            AppCommand("import-exchange-folder","folder",os.t("选择交换集文件夹","Choose exchange folder"),{folder.launch(null)},enabled=service!=null&&!data.loading&&!data.jobRunning),
        ))
    }
    importUri?.let {uri->ChartImportDialog(os,uri,null,onDismiss={importUri=null}) {request->service?.importPackage(request)}}
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
    fun feedback(result:ChartCommandResult?) {message=when(result) {is ChartCommandResult.Failed->chartDataError(os,result.reason);ChartCommandResult.Busy->os.t("请等当前导入完成，或先取消","Wait for the import or cancel it first");else->null}}
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,dataset?.name ?: os.t("数据海图","Data chart"),os.title(AppId.LIBRARY))
        if(dataset==null)PageBody {
            ChartDataProgress(os,service,data)
            if(!data.loading) {Label(os.t("这份资料已不在图册中","This dataset is no longer in your library"),20);Label(os.t("地图保留原来的选用记录，补回资料或重新选择后恢复。","Chart keeps its selection. Restore this dataset or choose another."),14,LocalMetro.current.muted)}
        }else LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(start=insets.pageStart,end=insets.pageEnd,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                ChartDataProgress(os,service,data)
                message?.let {Label(it,14,LocalMetro.current.accentText)}
                val selected=dataset.id in os.maps.selectedDatasetIds
                MenuRow(os.t("浏览资料内容","Explore contents"),os.t("${dataset.cells.sumOf {it.featureCount}} 个对象 · 水深、航标、障碍…","${dataset.cells.sumOf {it.featureCount}} objects · depths, marks, hazards…"),"layers") {os.open("chartobjects:${dataset.id}")}
                Label((if(dataset.format=="GPKG")"GeoPackage"else dataset.format)+" · "+chartUseLabel(os,dataset.eligibility),14,LocalMetro.current.muted)
                if(dataset.eligibility.provider.isNotBlank())Label(dataset.eligibility.provider,14,LocalMetro.current.muted)
                MetroButton(if(selected)os.t("已选用 · 在海图查看","Selected · View in Chart")else os.t("在海图与规划中选用","Use in Chart and planning"),{
                    os.maps.includeDataset(dataset.id,true)
                    os.fitRequest=dataset.cells.filterNot {it.cancelled}.flatMap {it.bounds}.flatMap {it.chartCorners()}
                    os.openLinked("chart")
                },primary=true,enabled=dataset.offlineReadable)
                Label(os.t("加入当前地图的资料组合。参考资料可查看，但不能证明航线可通行。","Adds to the data selected for this map. Reference data is viewable but cannot prove a route passable."),13,LocalMetro.current.muted)
                MenuRow(os.t("来源与资料用途","Source and permitted use"),os.t("提供方、许可依据与有效期","Provider, permission and validity"),"settings") {message=null;permission=true}
                MenuRow(os.t("重命名","Rename"),icon="edit") {editedName=dataset.name;message=null;rename=true}
                MenuRow(os.t("导入新版或连续更新","Import a new edition or updates"),os.t("S-57 连续更新，或替换 GeoPackage 完整版本","S-57 updates or a complete GeoPackage replacement"),"plus") {update.launch(arrayOf("*/*"))}
                MenuRow(os.t("从文件夹更新","Update from a folder"),icon="folder") {folder.launch(null)}
                AppSection(if(dataset.format=="GPKG")os.t("资料范围","Dataset extent")else os.t("图幅与覆盖","Cells and coverage"))
            }}
            items(dataset.cells,key={it.cellId}) {cell->Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable {expanded=if(expanded==cell.cellId)null else cell.cellId}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Label(if(dataset.format=="GPKG")dataset.name else cell.cellId,17)
                        Label(if(cell.cancelled)os.t("提供方已取消此图幅","Cell cancelled by provider")else if(dataset.format=="GPKG")os.t("${cell.featureCount} 个对象 · 离线副本","${cell.featureCount} objects · offline copy")else os.t("第 ${cell.edition} 版 · 更新 ${cell.update}","Edition ${cell.edition} · Update ${cell.update}"),13,LocalMetro.current.muted)
                    }
                    Glyph(if(expanded==cell.cellId)"minus"else"plus",Modifier.size(18.dp),LocalMetro.current.muted)
                }
                if(expanded==cell.cellId) {
                    Label(os.t("编图比例尺 ","Compilation scale ")+(cell.compilationScale?.let {"1:$it"} ?: os.t("未提供","unspecified"))+" · "+os.t("${cell.featureCount} 个对象","${cell.featureCount} objects"),14)
                    Label(os.t("发布日期 ","Issue date ")+(cell.issueDate ?: "—"),13,LocalMetro.current.muted)
                    Label(os.t("${cell.coverage.count {it.covered}} 个有效覆盖面；${cell.coverage.count {!it.covered}} 个无覆盖面","${cell.coverage.count {it.covered}} coverage areas; ${cell.coverage.count {!it.covered}} exclusion areas"),13,LocalMetro.current.muted)
                    if(cell.quality.isNotEmpty())Label(os.t("测量质量 · ","Survey quality · ")+cell.quality.joinToString(" · "),13,LocalMetro.current.muted)
                    cell.issues.forEach {Label(chartDataError(os,it),13,LocalMetro.current.accentText)}
                    MetroButton(os.t("查看对象","Explore objects"),{os.open("chartobjects:${dataset.id}:${Uri.encode(cell.cellId)}")},enabled=!cell.cancelled&&cell.featureCount>0)
                    if(cell.bounds.isNotEmpty())MetroButton(os.t("在海图查看范围","View extent on chart"),{
                        os.fitRequest=cell.bounds.flatMap {it.chartCorners()};os.openLinked("chart")
                    })
                }
            }}
            item {Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.height(14.dp))
                MetroButton(os.t("从图册移除","Remove from library"),{removing=true},enabled=!data.jobRunning)
                Label(os.t("删除本应用的索引与记录副本，原海图文件保留。正在运行的分析保留自己的版本直到释放。","Removes the app's index and record copy. Original chart files remain. Running analyses keep their version until released."),13,LocalMetro.current.muted)
            }}
        }
    }
    if(rename&&dataset!=null)AppDialog(onDismissRequest={rename=false}) {AppDialogSurface {
        AppDialogTitle(os.t("图集名称","Dataset name"))
        Field(os.t("名称","Name"),editedName,{editedName=it.take(120)})
        message?.let {Label(it,14,LocalMetro.current.accentText)}
        MetroButton(os.t("保存","Save"),{renaming=true;scope.launch {try {val result=service?.rename(dataset.id,editedName);feedback(result);if(result is ChartCommandResult.Saved)rename=false}finally {renaming=false}}},primary=true,enabled=editedName.isNotBlank()&&!renaming)
        MetroButton(os.t("取消","Cancel"),{rename=false})
    }}
    if(permission&&dataset!=null)ChartEligibilityDialog(os,dataset.eligibility,{permission=false},error=message) {eligibility->scope.launch {val result=service?.updateEligibility(dataset.id,eligibility);feedback(result);if(result is ChartCommandResult.Saved)permission=false}}
    if(removing&&dataset!=null)ConfirmDialog(os,os.t("移除 ${dataset.name}？原文件保留。","Remove ${dataset.name}? Original files remain."),{removing=false}) {scope.launch {val result=service?.remove(dataset.id);feedback(result);removing=false;if(result is ChartCommandResult.Saved)os.back()}}
    if(importUri!=null&&dataset!=null)ChartImportDialog(os,requireNotNull(importUri),dataset,{importUri=null}) {request->service?.importPackage(request)}
}

@Composable private fun ChartDataProgress(os:OsStore,service:ChartDataService?,state:ChartDataState) {
    val scope=rememberCoroutineScope()
    var retryError by remember {mutableStateOf<String?>(null)}
    retryError?.let {Label(it,14,LocalMetro.current.accentText)}
    if(state.loading)MetroProgress(os.t("正在读取图册","Reading chart library"))
    state.error?.let {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Label(chartDataError(os,it),14,LocalMetro.current.accentText);MetroButton(os.t("重新读取","Read again"),{scope.launch {service?.retryRestore()}})}}
    state.activeJob?.let {job->Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val title=when(job.phase) {
            ChartImportPhase.COPYING->os.t("正在复制资料","Copying data")
            ChartImportPhase.PARSING->os.t("正在读取海图","Reading charts")
            ChartImportPhase.INDEXING->os.t("正在建立离线索引","Building offline index")
            ChartImportPhase.COMMITTING->os.t("正在安装完整版本","Installing complete version")
            ChartImportPhase.COMPLETE->os.t("已安装 · ","Installed · ")+job.name
            ChartImportPhase.CANCELLED->os.t("导入已取消","Import cancelled")
            ChartImportPhase.INTERRUPTED->os.t("上次导入中断，原图集保留","Import interrupted; previous charts preserved")
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
    var name by rememberSaveable(uri) {mutableStateOf(existing?.name ?: os.t("我的数据海图","My data chart"))}
    var eligibility by remember(uri) {mutableStateOf(existing?.eligibility ?: DataEligibility(ChartUse.REFERENCE_ONLY))}
    var editingEligibility by remember {mutableStateOf(false)}
    val requestId=rememberSaveable(uri) {UUID.randomUUID().toString()}
    val scope=rememberCoroutineScope()
    var submitting by remember {mutableStateOf(false)}
    var error by remember(uri) {mutableStateOf<String?>(null)}
    AppDialog(onDismissRequest=onDismiss) {AppDialogSurface {
        AppDialogTitle(if(existing==null)os.t("导入数据海图","Import data chart")else os.t("更新图集","Update dataset"))
        Field(os.t("图集名称","Dataset name"),name,{name=it.take(120)})
        MenuRow(os.t("资料用途","Permitted use"),chartUseLabel(os,eligibility),"settings") {editingEligibility=true}
        Label(os.t("确认后在后台复制、解析并建立索引。只有完整成功才替换，导入后再选择要在海图中使用的资料。","The app copies, reads and indexes the data in the background. A complete version is installed atomically; select it in Chart when ready."),14,LocalMetro.current.muted)
        error?.let {Label(it,14,LocalMetro.current.accentText)}
        MetroButton(if(submitting)os.t("正在提交","Submitting")else os.t("开始导入","Import"),{
            submitting=true;error=null
            scope.launch {
                try {when(val result=onImport(ChartImportRequest(requestId,uri,name.trim(),eligibility,existing?.id))) {
                    is ChartCommandResult.Accepted,is ChartCommandResult.Saved->onDismiss()
                    is ChartCommandResult.Failed->error=chartDataError(os,result.reason)
                    ChartCommandResult.Busy->error=os.t("另一个图集正在导入，请稍后再试","Another dataset is importing. Try again when it finishes.")
                    null->error=os.t("图集服务仍在启动","Chart service is starting")
                }}catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
                catch(failure:Exception) {error=chartDataError(os,failure.message ?: os.t("导入未能提交","Could not submit import"))}
                finally {submitting=false}
            }
        },primary=true,enabled=name.isNotBlank()&&!submitting)
        MetroButton(os.t("取消","Cancel"),onDismiss)
    }}
    if(editingEligibility)ChartEligibilityDialog(os,eligibility,{editingEligibility=false}) {eligibility=it;editingEligibility=false}
}

@Composable private fun ChartEligibilityDialog(os:OsStore,initial:DataEligibility,onDismiss:()->Unit,error:String?=null,onSave:(DataEligibility)->Unit) {
    var use by remember(initial) {mutableStateOf(initial.use)}
    var provider by remember(initial) {mutableStateOf(initial.provider)}
    var evidence by remember(initial) {mutableStateOf(initial.licenceEvidence)}
    var expiry by remember(initial) {mutableStateOf(initial.validUntilUtc?.let {java.time.Instant.ofEpochMilli(it-1L).atZone(ZoneOffset.UTC).toLocalDate().toString()}.orEmpty())}
    val expiryValue=if(expiry.isBlank())null else runCatching {LocalDate.parse(expiry).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()}.getOrNull()
    val valid=expiry.isBlank()||expiryValue!=null
    AppDialog(onDismissRequest=onDismiss) {AppDialogSurface {
        AppDialogTitle(os.t("资料用途","Permitted use"))
        error?.let {Label(it,14,LocalMetro.current.accentText)}
        ChoiceRow(os.t("仅作参考","Reference only"),use!=ChartUse.ANALYSIS_ALLOWED,os.t("可查看，不作为自动规划的通行依据","Viewable; not evidence for automatic routing")) {use=ChartUse.REFERENCE_ONLY}
        ChoiceRow(os.t("已确认可用于本地分析","Permission for local analysis confirmed"),use==ChartUse.ANALYSIS_ALLOWED,os.t("需要提供方与明确许可依据","Provider and explicit permission evidence required")) {use=ChartUse.ANALYSIS_ALLOWED}
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
    value.use==ChartUse.REFERENCE_ONLY->os.t("仅供参考","Reference only")
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
    code=="CHART_NO_SUPPORTED_DATA"->os.t("没有找到 S-57 .000 / 更新文件或 GeoPackage .gpkg。MBTiles 请在底图页导入。","No S-57 base / update or GeoPackage .gpkg found. Import MBTiles in Maps.")
    code=="CHART_MIXED_PACKAGE"->os.t("请分别导入 S-57 海图包与 GeoPackage，每个数据包独立维护。","Import S-57 and GeoPackage separately so each dataset can be maintained independently.")
    code=="CHART_FORMAT_CHANGED"->os.t("更新必须使用相同格式；不同格式请作为新数据包导入。","Update with the same format. Import another format as a new dataset.")
    code.startsWith("GPKG_SRS_UNSUPPORTED")->os.t("这个坐标系暂不支持。请将矢量图层导出为 WGS84（EPSG:4326）或 Web Mercator（EPSG:3857）。","This coordinate system is unsupported. Export vector layers as WGS84 (EPSG:4326) or Web Mercator (EPSG:3857).")
    code=="GPKG_NO_FEATURE_TABLES"||code=="GPKG_NO_FEATURES"->os.t("这个包没有矢量对象。仅有地图图片不能提供水深与覆盖资料。","This package has no vector features. Map images alone do not provide depth and coverage data.")
    code=="GPKG_DEPTH_MINIMUM_MISSING"->os.t("该深度区域没有最浅值，不能证明整片区域可通行。","This depth area has no minimum depth and cannot establish passage clearance.")
    code=="GPKG_SOUNDING_DEPTH_MISSING"->os.t("该测深点没有明确的水深值；几何高度不会自动当作水深。","This sounding has no explicit depth. Geometry elevation is not treated as water depth.")
    code=="GPKG_VERTICAL_DATUM_MISSING"->os.t("该水深资料没有深度基准，暂不能用于自动规划。","This depth has no vertical datum and cannot be used for automatic planning.")
    code=="GPKG_MORE_OBJECT_ISSUES"->os.t("更多对象存在资料问题，请查看具体对象。","More objects have data issues. Open their details to review.")
    code=="GEOMETRY_MISSING"->os.t("对象缺少可定位的几何资料。","This object has no usable location geometry.")
    code.startsWith("GPKG_")->os.t("数据包未能完整读取，原版本保留。请检查坐标系、几何与资料字段。详细原因：","The dataset could not be fully read. The previous version is preserved. Check its CRS, geometry and data fields. Detail: ")+code
    code.startsWith("CHART_QUERY")||code=="CHART_READ_FAILED"->os.t("资料读取未完成，请重试。","Data could not be read. Try again.")
    code.startsWith("S57_")->os.t("图包未能完整解析，原版本保留。详细原因：","The package could not be fully read. Previous charts are preserved. Detail: ")+code
    else->code
}

/** 保留日期变更线两侧边界，由原生地图按整组坐标取景。 */
private fun ChartBounds.chartCorners():List<GeoPoint> = listOf(GeoPoint(south,west),GeoPoint(north,west),GeoPoint(north,east),GeoPoint(south,east))
