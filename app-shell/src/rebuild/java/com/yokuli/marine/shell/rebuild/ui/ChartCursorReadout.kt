package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private data class CursorReadKey(val datasetId:String?,val revision:Long?,val point:GeoPoint,val radiusMeters:Double,val readable:Boolean)

/** 海图与守锚共用的指点读数；只属于当前访问，关闭准星即取消查询并释放快照。 */
@Composable internal fun ChartCursorReadout(os:OsStore,view:MapViewState,point:GeoPoint) {
    val state by os.maps.charts.state.collectAsState()
    val selected=os.maps.selectedDatasetIds
    val dataset=state.datasets.firstOrNull {it.id==selected.firstOrNull()}
    val enabled=os.maps.portrayalPreferences.showCursorInformation&&view.interactive&&selected.isNotEmpty()
    val key=CursorReadKey(dataset?.id,dataset?.revision,point,chartCursorRadius(point,view.zoom),dataset?.offlineReadable==true)
    val cache=remember(os.maps.charts) {LinkedHashMap<CursorReadKey,ChartCursorProbe>(24,.75f,true)}
    var probe by remember(key) {mutableStateOf(cache[key])}
    var loading by remember(key) {mutableStateOf(cache[key]==null)}
    var failed by remember(key) {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    LaunchedEffect(enabled,key,retry) {
        failed=false
        if(!enabled||!key.readable){probe=null;loading=false;return@LaunchedEffect}
        cache[key]?.let {probe=it;loading=false;return@LaunchedEffect}
        probe=null;loading=true
        // 相机手势期间不打 IPC；旧位置的深度立即移除，不能挂在新的准星坐标下面。
        // 只去抖极短的相机余振；Core 端已有空间索引、热对象缓存和短租约，不能再人为等待 300 ms。
        delay(40)
        try {
            val reading=withTimeout(1_600) {probeChartCursor(os.maps.charts,listOf(requireNotNull(key.datasetId)),key.point,view.zoom)}
            cache[key]=reading
            while(cache.size>24)cache.remove(cache.keys.first())
            probe=reading
        }
        catch(_:TimeoutCancellationException){failed=true}
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
        ?.minByOrNull {reading?.distance(it) ?: Double.POSITIVE_INFINITY}
    val contour=reading?.features?.filter {it.kind==NauticalFeatureKind.DEPTH_CONTOUR&&it.depth!=null}
        ?.minByOrNull {reading?.distance(it) ?: Double.POSITIVE_INFINITY}
    val feature=uncertain ?: area ?: sounding ?: contour
    val facilities=reading?.features.orEmpty().filter {it.kind !in setOf(NauticalFeatureKind.COVERAGE,NauticalFeatureKind.QUALITY,NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,NauticalFeatureKind.DEPTH_CONTOUR,NauticalFeatureKind.SOUNDING)}
        .sortedWith(compareBy<NauticalFeature> {it.kind==NauticalFeatureKind.LAND}.thenBy {reading?.distance(it) ?: Double.POSITIVE_INFINITY})
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
        feature==sounding&&sounding!=null->os.t("附近测深 ","Nearby sounding ")+os.formatDepth(sounding.depth?.pointMeters)+" · "+os.formatDistance(reading?.distance(sounding))
        contour!=null->depthEvidenceText(os,contour.depth)+" · "+os.t("附近","nearby")
        reading?.incomplete==true->os.t("此处内容较多 · 放大查看","Many objects here · Zoom in")
        else->os.t("此处没有水深资料","No depth data here")
    }
    val source=when {
        uncertain!=null->dataset?.cells?.firstOrNull {it.cellId==uncertain.cellId}?.sourceName ?: uncertain.cellId
        raster!=null->raster.grid.sourceName
        feature!=null->dataset?.cells?.firstOrNull {it.cellId==feature.cellId}?.sourceName ?: feature.cellId
        else->reading?.datasetName ?: dataset?.name
    }
    val clickable=failed||dataset?.offlineReadable!=true||reading!=null
    fun showObjects(objects:List<NauticalFeature>) {
        view.selectedChartObjects=objects.distinctBy {it.id}.take(30)
        view.selectedChartCoordinate=point
    }
    val openReading:()->Unit={
        when {
            dataset?.offlineReadable!=true||awaitingHere->os.openLinked(dataset?.id?.let {"chartdataset:$it"} ?: "library:data")
            failed->{cache.remove(key);retry++}
            reading!=null->showObjects(listOfNotNull(feature)+facilities+reading.features)
        }
    }
    Column(Modifier.fillMaxWidth().padding(start=insets.pageStart,end=insets.pageEnd,top=2.dp,bottom=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=40.dp).border(1.dp,c.muted.copy(alpha=.28f))
            .clickable(enabled=clickable,role=Role.Button,onClick=openReading).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Label(detail,13,if(failed)c.muted else c.fg,Modifier.weight(1f),maxLines=1)
            if(clickable){Spacer(Modifier.width(8.dp));Glyph("chevron_right",Modifier.size(14.dp))}
        }
        val nearby=facilities.distinctBy {featureTitle(os,it)}.take(2)
        if(!loading&&nearby.isNotEmpty())Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            nearby.forEach {facility->
                Row(Modifier.weight(1f).heightIn(min=40.dp).border(1.dp,c.muted.copy(alpha=.28f))
                    .clickable(role=Role.Button){showObjects(listOf(facility))}.padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Label(featureTitle(os,facility),13,c.fg,Modifier.weight(1f),maxLines=1)
                    Spacer(Modifier.width(6.dp));Glyph("chevron_right",Modifier.size(14.dp))
                }
            }
        }
        if(!loading&&source!=null)Label(source+when {
                partial&&reading!=null&&!awaitingHere->os.t(" · 已就绪部分"," · Ready portion")
                reading?.incomplete==true->os.t(" · 部分内容"," · Partial details")
                else->""
            },11,c.muted,maxLines=1)
    }
}
