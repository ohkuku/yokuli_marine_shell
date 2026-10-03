package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.distance
import com.yokuli.marine.shell.rebuild.scene.navigation.*
import com.yokuli.runtime.contract.chart.ChartDataService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 展示宿主共享的只读场景状态，不持有定位、AIS 或导航业务状态。 */
internal data class ChartTerrainLoadState(
    val scene:NavigationChartScene?,
    val loading:Boolean,
    val error:String?,
    val selected:Boolean,
    val datasetName:String?,
    val radiusMeters:Double,
    val retry:()->Unit,
)

/** 可见生命周期拥有加载和预取；同源移窗保留上一份完整场景，换源立即清除。 */
@Composable
internal fun rememberChartTerrain(
    charts:ChartDataService,
    selectedDatasetIds:List<String>,
    center:GeoPoint?,
    requestedRadiusMeters:Double,
    courseTrueDegrees:Double?,
    speedMetersPerSecond:Double?,
    active:Boolean,
):ChartTerrainLoadState {
    val state by charts.state.collectAsState()
    val ids=selectedDatasetIds.toList()
    val dataset=state.datasets.firstOrNull {it.id==ids.singleOrNull()}
    val sourceKey="$ids:${dataset?.let(::terrainSourceKey)}:${dataset?.offlineReadable}:${dataset?.issue}:${state.preparedFactsRevision}"
    val point=center?.takeIf(GeoPoint::valid)
    // 捏合逐帧只改变相机；跨实际范围档位才重建地形。
    val desired=listOf(1_000.0,2_000.0,4_000.0,8_000.0,16_000.0,32_000.0)
        .firstOrNull {it>=requestedRadiusMeters} ?: 32_000.0
    val radius=remember(dataset?.id,dataset?.revision,dataset?.rasters,point,desired) {
        if(point==null)desired else navigationTerrainRadius(dataset,point,desired)
    }
    var origin by remember(charts,radius){mutableStateOf(point?.let {navigationTerrainOrigin(it,radius)})}
    LaunchedEffect(point,radius) {
        val current=origin
        if(point==null)origin=null
        else if(current==null||distance(current,point)>radius/3.0)origin=navigationTerrainOrigin(point,radius)
    }
    val context=LocalContext.current.applicationContext
    val loader=remember(charts,context){NavigationTerrainRuntime.shared(context,charts)}
    var scene by remember(loader){mutableStateOf(origin?.let{loader.peek(ids,it,radius)})}
    var loading by remember(loader){mutableStateOf(false)}
    var error by remember(loader){mutableStateOf<String?>(null)}
    var retry by remember(loader){mutableIntStateOf(0)}
    var loadedSource by remember(loader){mutableStateOf<String?>(sourceKey)}
    var loadedKey by remember(loader){mutableStateOf<String?>(null)}
    var loadedCompleted by remember(loader){mutableIntStateOf(-1)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle){mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer)}
    }
    val source=sourceKey
    val progress=state.terrainPreparation.firstOrNull{it.datasetId==dataset?.id}
    val completed=progress?.ready?:0
    val key="$source:$origin:$radius:$retry"
    // 页面等待有界，后台作业继续；后续完成事件续读现成块，不要求用户退出再进入。
    LaunchedEffect(completed,progress?.failed,progress?.queued,progress?.preparing,loading,active,resumed) {
        if(!loading&&error=="CHART_TERRAIN_PREPARING"&&progress!=null&&progress.queued+progress.preparing==0&&progress.failed>0)
            error="CHART_TERRAIN_PREPARATION_FAILED"
        if(active&&resumed&&!loading&&loadedKey!=null&&
            (error=="CHART_TERRAIN_PREPARING"||scene?.warnings?.contains(NavigationChartWarning.PARTIAL_CONTENT)==true)&&
            completed>0&&completed!=loadedCompleted){retry++}
    }
    LaunchedEffect(loader,key,active,resumed) {
        if(loadedSource!=source){scene=null;error=null;loadedKey=null;loadedSource=source}
        if(!active||!resumed){loadedKey=null;return@LaunchedEffect}
        val where=origin
        if(where==null||ids.isEmpty()){scene=null;loading=false;error=null;return@LaunchedEffect}
        if(loadedKey==key)return@LaunchedEffect
        loader.peek(ids,where,radius)?.let{scene=it}
        loading=true;error=null
        try {
            val result=loader.loadRegion(ids,where,radius,retryFailed=retry>0){scene=it}
            scene=result
            loadedKey=key
            loadedCompleted=completed
        }
        catch(cancel:CancellationException){throw cancel}
        catch(failure:Exception){
            error=failure.message ?: "CHART_SOURCE_UNAVAILABLE"
            loadedKey=key;loadedCompleted=completed
            if(error=="CHART_SELECTED_DATA_MISSING"||error?.startsWith("CHART_SOURCE_")==true)scene=null
        }
        finally {loading=false}
    }
    val ahead=point?.let {navigationTerrainPrefetchOrigin(it,courseTrueDegrees,speedMetersPerSecond,radius)}
    LaunchedEffect(loader,key,loadedKey,ahead,active,resumed) {
        if(active&&resumed&&loadedKey==key&&ahead!=null&&ahead!=origin&&ids.isNotEmpty())loader.prefetch(ids,ahead,radius)
    }
    // Shell 的 35 秒等待结束不代表 Core 的 120 秒准备失败。PREPARING 继续显示加载态，
    // 由 terrainPreparation 完成事件自动续读；只有真实 FAILED/读取错误才进入错误 UI。
    val preparing=error=="CHART_TERRAIN_PREPARING"
    return ChartTerrainLoadState(scene.takeIf{loadedSource==source},(loading||preparing)&&scene?.hasGeometry!=true,
        error?.takeUnless{it=="CHART_TERRAIN_PREPARING"},ids.isNotEmpty(),dataset?.name,radius){retry++}
}

/** 可见二维导航提前提交附近基础层；离页只停止等待，已接受工作归 Core 生命周期。 */
@Composable
internal fun PrewarmChartTerrain(charts:ChartDataService,selectedIds:List<String>,center:GeoPoint,revision:Long,enabled:Boolean) {
    val context=LocalContext.current.applicationContext
    val chartState by charts.state.collectAsState()
    val loader=remember(charts,context){NavigationTerrainRuntime.shared(context,charts)}
    val origin=navigationTerrainOrigin(center,4_000.0)
    val resumed=rememberNavigationResumed()
    LaunchedEffect(loader,selectedIds,origin,revision,chartState.preparedFactsRevision,enabled,resumed) {
        if(enabled&&resumed&&selectedIds.size==1&&origin.valid())loader.prewarm(selectedIds,origin)
    }
}
