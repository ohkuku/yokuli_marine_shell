package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.LibraryObjectPreview
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.*

private enum class LibraryObjectCategory(val kinds:Set<NauticalFeatureKind>) {
    ALL(emptySet()), DEPTH(setOf(NauticalFeatureKind.SOUNDING,NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DEPTH_CONTOUR,NauticalFeatureKind.DREDGED_AREA)),
    HAZARDS(setOf(NauticalFeatureKind.OBSTRUCTION,NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK,NauticalFeatureKind.DRYING_AREA)),
    MARKS(setOf(NauticalFeatureKind.BEACON,NauticalFeatureKind.LIGHT)), LAND(setOf(NauticalFeatureKind.LAND)),
    RULES(setOf(NauticalFeatureKind.TRAFFIC,NauticalFeatureKind.RESTRICTED)), CLEARANCE(setOf(NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD)),
    COVERAGE(setOf(NauticalFeatureKind.COVERAGE,NauticalFeatureKind.QUALITY)), OTHER(setOf(NauticalFeatureKind.OTHER));
    fun title(os:OsStore)=when(this) {
        ALL->os.t("全部内容","All objects");DEPTH->os.t("水深","Depths");HAZARDS->os.t("障碍与浅滩","Hazards and shallows")
        MARKS->os.t("航标与灯光","Marks and lights");LAND->os.t("陆地","Land");RULES->os.t("航道与限制","Traffic and restrictions")
        CLEARANCE->os.t("桥梁与净空","Bridges and clearance");COVERAGE->os.t("覆盖与质量","Coverage and quality");OTHER->os.t("其他资料","Other data")
    }
}

/** 列表只保留读数与名称；多页浏览不将每个对象的完整几何持续留在主界面。 */
private data class LibraryObjectRow(val id:String,val title:String,val acronym:String,val cellId:String,val depth:DepthEvidence?)
private data class LibraryObjectPage(val features:List<LibraryObjectRow>,val nextAfterId:String?,val hasMore:Boolean)
private fun ChartFeaturePage.forList(os:OsStore)=LibraryObjectPage(features.map {
    LibraryObjectRow(it.id,featureTitle(os,it),it.acronym,it.cellId,it.depth)
},nextAfterId,hasMore)

/** 每次浏览持有数据集版本；筛选与分页经领域端口，更新后释放旧租约并重新读取。 */
@Composable fun LibraryObjectsScreen(os:OsStore,datasetId:String,cellId:String?=null) {
    val service=os.marine?.system?.charts
    val catalogue=service?.state?.collectAsState()?.value ?: ChartDataState()
    val dataset=catalogue.datasets.firstOrNull {it.id==datasetId}
    val scope=rememberCoroutineScope()
    val insets=LocalShellHorizontalInsets.current
    var query by rememberSaveable(datasetId,cellId) {mutableStateOf("")}
    var categoryName by rememberSaveable(datasetId,cellId) {mutableStateOf(LibraryObjectCategory.ALL.name)}
    val category=LibraryObjectCategory.entries.firstOrNull {it.name==categoryName} ?: LibraryObjectCategory.ALL
    var categories by remember {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    var snapshot by remember {mutableStateOf<ChartDataSnapshot?>(null)}
    var opening by remember {mutableStateOf(true)}
    var openingError by remember {mutableStateOf<String?>(null)}
    var openingGeneration by remember {mutableLongStateOf(0L)}
    var error by remember {mutableStateOf<String?>(null)}
    var page by remember {mutableStateOf<LibraryObjectPage?>(null)}
    var loading by remember {mutableStateOf(false)}
    var pageGeneration by remember {mutableLongStateOf(0L)}
    var nextPageJob by remember {mutableStateOf<Job?>(null)}
    var selectedId by rememberSaveable(datasetId,cellId) {mutableStateOf<String?>(null)}
    var detail by remember {mutableStateOf<NauticalFeature?>(null)}
    var detailSnapshotId by remember {mutableStateOf<String?>(null)}
    var detailError by remember {mutableStateOf<String?>(null)}
    var detailRetry by remember {mutableIntStateOf(0)}
    val filter=ChartFeatureFilter(cellId,category.kinds,query.trim())
    val currentFilter by rememberUpdatedState(filter)
    LaunchedEffect(service,datasetId,dataset?.revision,dataset?.offlineReadable,retry) {
        val generation=++openingGeneration
        snapshot=null;page=null;openingError=null;opening=true
        if(service==null||dataset==null||!dataset.offlineReadable) {opening=false;return@LaunchedEffect}
        var lease:ChartDataSnapshot?=null
        try {
            lease=service.acquireSnapshot(listOf(datasetId));snapshot=lease;opening=false
            awaitCancellation()
        }catch(cancel:CancellationException) {throw cancel}
        catch(failure:Exception) {if(openingGeneration==generation)openingError=chartDataError(os,failure.message ?: "CHART_READ_FAILED")}
        finally {if(openingGeneration==generation)opening=false;lease?.let {withContext(NonCancellable) {service.releaseSnapshot(it.id)}}}
    }
    LaunchedEffect(snapshot?.id,filter,os.chinese) {
        val generation=++pageGeneration
        nextPageJob?.cancel();nextPageJob=null
        page=null;error=null;loading=false
        val lease=snapshot ?: return@LaunchedEffect
        loading=true
        try {delay(220);val result=service!!.browse(lease.id,filter,100);if(pageGeneration==generation)page=result.forList(os)}
        catch(cancel:CancellationException) {throw cancel}
        catch(failure:Exception) {if(pageGeneration==generation)error=chartDataError(os,failure.message ?: "CHART_READ_FAILED")}
        finally {if(pageGeneration==generation)loading=false}
    }
    LaunchedEffect(snapshot?.id,selectedId,detailRetry) {
        detail=null;detailSnapshotId=null;detailError=null
        val lease=snapshot ?: return@LaunchedEffect
        val id=selectedId ?: return@LaunchedEffect
        try {detail=service!!.readFeature(lease.id,id);detailSnapshotId=lease.id;if(detail==null)detailError=os.t("此对象已在新版资料中移除","This object was removed in the new dataset")}
        catch(cancel:CancellationException) {throw cancel}
        catch(failure:Exception) {detailError=chartDataError(os,failure.message ?: "CHART_READ_FAILED")}
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,cellId ?: os.t("资料内容","Contents"),dataset?.name ?: os.title(AppId.LIBRARY))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(start=insets.pageStart,end=insets.pageEnd,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(dataset==null&&!catalogue.loading&&catalogue.error==null)item {Label(os.t("这份资料已被移除，请返回图册选择另一份。","This dataset was removed. Return to Library to choose another."),15)}
            else if(dataset?.offlineReadable==false)item {Label(os.t("离线索引缺失，请重新导入这份资料。","The offline index is missing. Import this dataset again."),15)}
            else {
                item {Field(os.t("搜索名称或对象类别","Search name or object class"),query,{query=it.take(120)})}
                item {MenuRow(category.title(os),icon="layers") {categories=true}}
                if(opening||catalogue.loading||loading&&page==null)item {MetroProgress(os.t("正在读取内容","Reading contents"))}
                (error ?: openingError ?: catalogue.error?.let {chartDataError(os,it)})?.let {item {Label(it,14,LocalMetro.current.accentText);MetroButton(os.t("重试","Retry"),{scope.launch {if(catalogue.error!=null)service?.retryRestore();retry++}})}}
                if(page?.features?.isEmpty()==true&&!loading&&error==null)item {Label(os.t("没有符合条件的对象","No matching objects"),15,LocalMetro.current.muted)}
                items(page?.features.orEmpty(),key={it.id}) {feature->
                    MenuRow(feature.title,listOfNotNull(feature.depth?.let {depthEvidenceText(os,it)},feature.acronym,if(cellId==null)feature.cellId else null).joinToString(" · ")) {selectedId=feature.id}
                }
                if(page?.hasMore==true)item {MetroButton(if(loading)os.t("正在读取","Loading")else os.t("更多内容","More objects"),{
                    if(loading)return@MetroButton
                    val lease=snapshot ?: return@MetroButton
                    val current=page ?: return@MetroButton
                    val cursor=current.nextAfterId ?: return@MetroButton
                    val requestedFilter=filter
                    val generation=pageGeneration
                    loading=true;error=null
                    nextPageJob=scope.launch {
                        try {
                            val next=service!!.browse(lease.id,requestedFilter,100,cursor)
                            if(pageGeneration==generation&&snapshot?.id==lease.id&&currentFilter==requestedFilter) {
                                require(!next.hasMore||next.nextAfterId!=cursor) {"CHART_QUERY_CURSOR_STALLED"}
                                page=next.forList(os).let {it.copy(features=current.features+it.features)}
                            }
                        }catch(cancel:CancellationException) {throw cancel}
                        catch(failure:Exception) {if(pageGeneration==generation&&snapshot?.id==lease.id&&currentFilter==requestedFilter)error=chartDataError(os,failure.message ?: "CHART_READ_FAILED")}
                        finally {if(pageGeneration==generation&&snapshot?.id==lease.id&&currentFilter==requestedFilter)loading=false}
                    }
                },enabled=!loading)}
            }
        }
    }
    if(categories)AppDialog(onDismissRequest={categories=false}) {AppDialogSurface {
        AppDialogTitle(os.t("查看什么","Show"))
        LibraryObjectCategory.entries.forEach {option->ChoiceRow(option.title(os),option==category) {categoryName=option.name;categories=false}}
    }}
    if(selectedId!=null)AppDialog(onDismissRequest={selectedId=null}) {AppDialogSurface {
        AppDialogTitle(detail?.let {featureTitle(os,it)} ?: os.t("对象资料","Object details"))
        if(detail==null&&detailError==null) {
            val unavailable=openingError ?: catalogue.error?.let {chartDataError(os,it)} ?: when {
                !catalogue.loading&&dataset==null->os.t("这份资料已被移除","This dataset was removed")
                dataset?.offlineReadable==false->os.t("离线索引缺失，请重新导入","The offline index is missing. Import this dataset again")
                else->null
            }
            if(unavailable!=null)Label(unavailable,14,LocalMetro.current.accentText)else MetroProgress(os.t("正在读取","Reading"))
        }
        detailError?.let {Label(it,14,LocalMetro.current.accentText);MetroButton(os.t("重新读取","Read again"),{detailRetry++})}
        detail?.let {feature->
            feature.depth?.let {Label(depthEvidenceText(os,it),20,LocalMetro.current.accentText)}
            Label(dataset?.name.orEmpty()+" · "+feature.acronym,13,LocalMetro.current.muted)
            Label(feature.cellId,13,LocalMetro.current.muted)
            feature.depth?.datum?.let {Label(os.t("深度基准 · ","Depth datum · ")+it,13,LocalMetro.current.muted)}
            feature.source.sourceIndication?.let {Label(it,13,LocalMetro.current.muted)}
            feature.issues.forEach {Label(chartDataError(os,it),13,LocalMetro.current.accentText)}
            val hasGeometry=feature.geometry.parts.any {it.points.isNotEmpty()}
            MetroButton(os.t("在海图查看","View on chart"),{
                val lease=snapshot ?: return@MetroButton
                if(detailSnapshotId!=lease.id||selectedId!=feature.id)return@MetroButton
                val revision=lease.datasets.firstOrNull {it.id==datasetId}?.revision ?: return@MetroButton
                // 原海图离开时已经保存访问快照；新预览只清工具显示，不清草稿、导航或分析资料。
                os.editingRoute=false;os.ruler=emptyList();os.showCrosshair=false;os.follow=false
                os.cameraRequest=null;os.fitRequest=null
                os.maps.view("chart",os.center,os.zoom).apply {
                    libraryPreview=LibraryObjectPreview(feature,revision);libraryPreviewNote=null
                    selectedPlaceId=null;selectedAisMmsi=null;selectedChartCoordinate=null;selectedChartObjects=emptyList()
                    previewTrack=emptyList();previewTitle=null;previewRoute=null
                }
                // 这是只读对象预览；不改变地图的数据选择，也不移动到面积中心假装那里是一个测深点。
                os.openLinked("chart")
            },primary=true,enabled=hasGeometry&&detailSnapshotId==snapshot?.id)
            var attributes by remember(selectedId) {mutableStateOf(false)}
            MenuRow(os.t("全部属性","All attributes"),icon=if(attributes)"minus"else"plus") {attributes=!attributes}
            if(attributes)feature.attributes.forEach {(key,value)->Label("$key · $value",13,LocalMetro.current.muted)}
        }
        MetroButton(os.t("关闭","Close"),{selectedId=null})
    }}
}
