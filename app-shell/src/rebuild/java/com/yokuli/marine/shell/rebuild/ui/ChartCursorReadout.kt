package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.chart.*
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 海图与守锚共用的指点读数；只属于当前访问，关闭准星即取消查询并释放快照。 */
@Composable internal fun ChartCursorReadout(os:OsStore,view:MapViewState,point:GeoPoint) {
    val state by os.maps.charts.state.collectAsState()
    val selected=os.maps.selectedDatasetIds
    val dataset=state.datasets.firstOrNull {it.id==selected.firstOrNull()}
    val enabled=os.maps.portrayalPreferences.showCursorInformation&&view.interactive&&selected.isNotEmpty()
    var probe by remember(point,selected,state.revision) {mutableStateOf<ChartCursorProbe?>(null)}
    var loading by remember(point,selected,state.revision) {mutableStateOf(true)}
    var failed by remember(point,selected,state.revision) {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    LaunchedEffect(enabled,point,view.zoom,selected,state.revision,dataset?.offlineReadable,retry) {
        probe=null;failed=false;loading=enabled
        if(!enabled||dataset?.offlineReadable!=true){loading=false;return@LaunchedEffect}
        // 相机手势期间不打 IPC；旧位置的深度立即移除，不能挂在新的准星坐标下面。
        delay(300)
        try {probe=probeChartCursor(os.maps.charts,selected,point,view.zoom)}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){failed=true}
        finally {loading=false}
    }
    if(!enabled)return
    val c=LocalMetro.current
    val insets=LocalShellHorizontalInsets.current
    val preparing=state.loading||dataset?.preparing==true||state.activeJob?.let {job->
        job.phase in setOf(ChartImportPhase.COPYING,ChartImportPhase.PARSING,ChartImportPhase.INDEXING,ChartImportPhase.COMMITTING)&&
            (job.datasetId==dataset?.id||job.name==dataset?.name)
    }==true
    val reading=probe
    val raster=reading?.raster
    val uncertain=reading?.features?.firstOrNull {it.attributes["GPKG_GEOMETRY_STATUS"]=="DATELINE_TOPOLOGY_UNCERTAIN"}
    val area=reading?.features?.filter {it.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)&&it.depth?.kind==DepthEvidenceKind.INTERVAL}
        ?.minByOrNull {it.depth?.lowerMeters ?: Double.POSITIVE_INFINITY}
    val sounding=reading?.features?.filter {it.kind==NauticalFeatureKind.SOUNDING&&it.depth?.pointMeters?.isFinite()==true}
        ?.minByOrNull {chartFeatureDistance(it,point)}
    val contour=reading?.features?.filter {it.kind==NauticalFeatureKind.DEPTH_CONTOUR&&it.depth!=null}
        ?.minByOrNull {chartFeatureDistance(it,point)}
    val feature=uncertain ?: area ?: sounding ?: contour
    val facilities=reading?.features.orEmpty().filter {it.kind !in setOf(NauticalFeatureKind.COVERAGE,NauticalFeatureKind.QUALITY,NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,NauticalFeatureKind.DEPTH_CONTOUR,NauticalFeatureKind.SOUNDING)}
        .sortedWith(compareBy<NauticalFeature> {it.kind==NauticalFeatureKind.LAND}.thenBy {chartFeatureDistance(it,point)})
    val partial=dataset?.preparing==true||dataset?.preparationIssue!=null
    val awaitingHere=reading!=null&&raster==null&&feature==null&&facilities.isEmpty()&&partial
    val detail=when {
        preparing&&dataset?.offlineReadable!=true->os.t("正在准备查询数据…","Preparing chart data…")
        dataset==null->os.t("所选数据文件夹不可用","Selected data folder unavailable")
        !dataset.offlineReadable->os.t("资料尚未就绪 · 在图册查看","Data not ready · Open Atlas")
        failed->os.t("暂时读不到资料 · 点按重试","Could not read data · Tap to retry")
        loading->os.t("读取此处资料…","Reading this position…")
        awaitingHere&&preparing->os.t("资料仍在准备 · 暂无此处读数","Data is still preparing · No reading here yet")
        awaitingHere->os.t("部分资料未完成 · 在图册继续准备","Some files are not ready · Continue in Atlas")
        uncertain!=null->os.t("此处资料几何不确定 · 查看来源","Chart geometry is uncertain here · View source")
        raster!=null->when {
            raster.elevationMeters==null->os.t("此处网格无数据","No grid data here")
            raster.elevationMeters<0->os.t("估算水深 ","Estimated depth ")+os.formatDepth(-raster.elevationMeters.toDouble())
            else->os.t("地表高程 ","Surface elevation ")+os.formatDepth(raster.elevationMeters.toDouble())
        }
        feature==area&&area!=null->depthEvidenceText(os,area.depth)
        feature==sounding&&sounding!=null->os.t("附近测深 ","Nearby sounding ")+os.formatDepth(sounding.depth?.pointMeters)+" · "+os.formatDistance(chartFeatureDistance(sounding,point))
        contour!=null->depthEvidenceText(os,contour.depth)+" · "+os.t("附近","nearby")
        else->os.t("此处没有水深资料","No depth data here")
    }
    val names=facilities.map {f->featureTitle(os,f)}.distinct().take(2)
    val source=when {
        uncertain!=null->dataset?.cells?.firstOrNull {it.cellId==uncertain.cellId}?.sourceName ?: uncertain.cellId
        raster!=null->raster.grid.sourceName
        feature!=null->dataset?.cells?.firstOrNull {it.cellId==feature.cellId}?.sourceName ?: feature.cellId
        else->reading?.datasetName ?: dataset?.name
    }
    val clickable=failed||dataset?.offlineReadable!=true||reading!=null
    Row(Modifier.fillMaxWidth().clickable(enabled=clickable,role=Role.Button) {
        when {
            dataset?.offlineReadable!=true||awaitingHere->os.openLinked(dataset?.id?.let {"chartdataset:$it"} ?: "library:data")
            failed->retry++
            reading!=null->{
                view.selectedChartObjects=(listOfNotNull(feature)+facilities+reading.features).distinctBy {it.id}.take(30)
                view.selectedChartCoordinate=point
            }
        }
    }.padding(start=insets.pageStart,end=insets.pageEnd,top=2.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).heightIn(min=50.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Label(detail,14,if(failed)c.muted else c.fg,maxLines=1)
            if(names.isNotEmpty())Label(names.joinToString(" · "),13,c.muted,maxLines=1)
            if(!loading&&source!=null)Label(source+when {
                partial&&reading!=null&&!awaitingHere->os.t(" · 已就绪部分"," · Ready portion")
                reading?.incomplete==true->os.t(" · 部分内容"," · Partial details")
                else->""
            },11,c.muted,maxLines=1)
        }
        if(clickable){Spacer(Modifier.width(10.dp));Glyph("chevron_right",Modifier.size(16.dp))}
    }
}
