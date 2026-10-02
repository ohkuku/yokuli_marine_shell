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
    val sourceKey="$ids:${dataset?.let(::terrainSourceKey)}:${dataset?.offlineReadable}:${dataset?.issue}"
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
    var scene by remember(loader){mutableStateOf<NavigationChartScene?>(null)}
    var loading by remember(loader){mutableStateOf(false)}
    var error by remember(loader){mutableStateOf<String?>(null)}
    var retry by remember(loader){mutableIntStateOf(0)}
    var loadedSource by remember(loader){mutableStateOf<String?>(null)}
    var loadedKey by remember(loader){mutableStateOf<String?>(null)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle){mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer)}
    }
    val source=sourceKey
    val key="$source:$origin:$radius:$retry"
    LaunchedEffect(loader,key,active,resumed) {
        if(loadedSource!=source){scene=null;error=null;loadedKey=null;loadedSource=source}
        if(!active||!resumed){loadedKey=null;return@LaunchedEffect}
        val where=origin
        if(where==null||ids.isEmpty()){scene=null;loading=false;error=null;return@LaunchedEffect}
        if(loadedKey==key)return@LaunchedEffect
        loading=true;error=null
        try {
            var result=loader.loadRegion(ids,where,radius){scene=it}
            scene=result
            // 当前区域先显示，缺块可续读一次；每块命中缓存，不让静止视野永远停在首批。
            // 永久预算/无覆盖不是后台无限重试的理由，用户仍可显式重试。
            if(result.patches.size<result.expectedPatches){
                delay(500)
                result=loader.loadRegion(ids,where,radius){scene=it}
                scene=result
            }
            loadedKey=key
        }
        catch(cancel:CancellationException){throw cancel}
        catch(failure:Exception){
            error=failure.message ?: "CHART_SOURCE_UNAVAILABLE"
            if(error=="CHART_SELECTED_DATA_MISSING"||error?.startsWith("CHART_SOURCE_")==true)scene=null
        }
        finally {loading=false}
    }
    val ahead=point?.let {navigationTerrainPrefetchOrigin(it,courseTrueDegrees,speedMetersPerSecond,radius)}
    LaunchedEffect(loader,key,loadedKey,ahead,active,resumed) {
        if(active&&resumed&&loadedKey==key&&ahead!=null&&ahead!=origin&&ids.isNotEmpty())loader.prefetch(ids,ahead,radius)
    }
    return ChartTerrainLoadState(scene.takeIf{loadedSource==source},loading,error,ids.isNotEmpty(),dataset?.name,radius){retry++}
}

/** 可见二维导航提前准备附近第一块；退出/切源/进入三维即取消，绝不创建后台业务会话。 */
@Composable
internal fun PrewarmChartTerrain(charts:ChartDataService,selectedIds:List<String>,center:GeoPoint,revision:Long,enabled:Boolean) {
    val context=LocalContext.current.applicationContext
    val loader=remember(charts,context){NavigationTerrainRuntime.shared(context,charts)}
    val origin=navigationTerrainOrigin(center,4_000.0)
    val resumed=rememberNavigationResumed()
    LaunchedEffect(loader,selectedIds,origin,revision,enabled,resumed) {
        if(enabled&&resumed&&selectedIds.size==1&&origin.valid())loader.prewarm(selectedIds,origin)
    }
}
