package com.yokuli.marine.shell.rebuild.chart

import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.*
import kotlin.math.*

/** 短暂的准星读投影：一次快照内读取矢量与原始网格，绝不作为船舶传感器水深。 */
internal data class ChartCursorProbe(
    val point:GeoPoint,
    val datasetName:String,
    val features:List<NauticalFeature>,
    val raster:ChartRasterProbe?,
    val incomplete:Boolean,
    val distances:Map<String,Double> = emptyMap(),
)


/**
 * 与地图一起预取、但不实际绘制的语义数据层。
 * Garmin 类体验的关键不是“点一下再查一次数据库”，而是当前位置附近的对象已经驻留内存；
 * 准星只在这份不可见数据层上做同步 hit-test。显示仍由海图背景负责，语义层不送 GPU。
 */
internal data class ChartCursorLayer(
    val key:String,
    val datasetId:String,
    val datasetRevision:Long,
    val datasetName:String,
    val bounds:ChartBounds,
    val features:List<NauticalFeature>,
    val rasters:List<ChartRasterWindow>,
    val incomplete:Boolean,
) {
    private fun normalize(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
    private fun contains(latitude:Double,longitude:Double):Boolean =
        latitude in bounds.south..bounds.north&&bounds.split().any {part->
            val lon=normalize(longitude)
            lon>=part.west-1e-9&&lon<=part.east+1e-9
        }

    fun covers(point:GeoPoint,radiusMeters:Double):Boolean {
        if(datasetId.isBlank()||!point.valid())return false
        val dy=radiusMeters/111_320.0
        val dx=radiusMeters/(111_320.0*cos(Math.toRadians(point.lat)).coerceAtLeast(.05))
        return contains(point.lat,point.lon)&&contains(point.lat-dy,point.lon-dx)&&contains(point.lat+dy,point.lon+dx)
    }

    fun probe(point:GeoPoint,zoom:Double):ChartCursorProbe {
        val radius=chartCursorRadius(point,zoom)
        val hits=chartObjectsAt(features,point,zoom,radiusMeters=radius,limit=48)
        val distances=hits.associate {feature->feature.id to chartFeatureDistance(feature,point)}
        val chartPoint=ChartPoint(point.lat,point.lon)
        val raster=rasters.firstNotNullOfOrNull {item->
            val pixel=item.grid.pixelAt(chartPoint)?:return@firstNotNullOfOrNull null
            val x=pixel.first-item.window.column
            val y=pixel.second-item.window.row
            if(x !in 0 until item.window.width||y !in 0 until item.window.height)null
            else ChartRasterProbe(item.grid,datasetName,item.window.elevationAt(x,y))
        }
        return ChartCursorProbe(point,datasetName,hits,raster,incomplete,distances)
    }
}

private data class ChartCursorLayerKey(val datasetId:String,val revision:Long,val latitudeBucket:Int,val longitudeBucket:Int)

private fun cursorLayerBounds(center:GeoPoint,halfMeters:Double=1_250.0):ChartBounds {
    val dy=halfMeters/111_320.0
    val dx=halfMeters/(111_320.0*cos(Math.toRadians(center.lat)).coerceAtLeast(.05))
    fun norm(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
    return ChartBounds(norm(center.lon-dx),(center.lat-dy).coerceAtLeast(-89.999),norm(center.lon+dx),(center.lat+dy).coerceAtMost(89.999))
}

private fun cursorLayerKey(dataset:ChartDataset,center:GeoPoint):ChartCursorLayerKey {
    // 约 350 m 一格；预取窗口半径 1.25 km，因此在同一格拖动不会频繁重载。
    val bucketMeters=350.0
    val latStep=bucketMeters/111_320.0
    val lonStep=bucketMeters/(111_320.0*cos(Math.toRadians(center.lat)).coerceAtLeast(.05))
    val lat=floor((center.lat+90.0)/latStep).toInt()
    val lon=floor(((((center.lon+180.0)%360.0)+360.0)%360.0)/lonStep).toInt()
    return ChartCursorLayerKey(dataset.id,dataset.revision,lat,lon)
}

/**
 * 地图打开时就预取准星中心附近约 2.5 km 的矢量/栅格语义数据。
 * 相机只在跨过约 350 m bucket 时触发下一块读取；最近 8 块保留在内存，回拖时立即复用。
 */
@Composable
internal fun rememberChartCursorLayer(maps:MapSessionStore,view:MapViewState):ChartCursorLayer? {
    val state by maps.charts.state.collectAsState()
    val ids=maps.selectedDatasetIds
    val dataset=ids.singleOrNull()?.let{id->state.datasets.firstOrNull{it.id==id}}
    val enabled=maps.portrayalPreferences.showCursorInformation&&view.interactive&&
        !state.loading&&state.error==null&&dataset?.offlineReadable==true&&view.center.valid()
    val key=dataset?.let{cursorLayerKey(it,view.center)}
    val cache=remember(maps.charts){LinkedHashMap<ChartCursorLayerKey,ChartCursorLayer>(8,.75f,true)}
    var current by remember(maps.charts){mutableStateOf<ChartCursorLayer?>(null)}

    LaunchedEffect(enabled,key) {
        if(!enabled||dataset==null||key==null){current=null;return@LaunchedEffect}
        cache[key]?.let{current=it;return@LaunchedEffect}

        val center=view.center
        val bounds=cursorLayerBounds(center)
        var lease:ChartDataSnapshot?=null
        try {
            lease=maps.charts.acquireDisplaySnapshot(listOf(dataset.id),bounds)
            val features=ArrayList<NauticalFeature>()
            var after:String?=null
            var incomplete=false
            do {
                currentCoroutineContext().ensureActive()
                val page=maps.charts.query(lease.id,bounds,1_200,after)
                features+=page.features
                incomplete=incomplete||page.truncated
                after=page.nextAfterId
                if(features.size>=6_000&&page.hasMore){incomplete=true;break}
                if(!page.hasMore)break
            }while(after!=null)
            val rasters=runCatching {maps.charts.rasterWindows(lease.id,bounds,maxCells=65_536)}
                .getOrElse {emptyList()}
            val layer=ChartCursorLayer(
                key="${dataset.id}:${dataset.revision}:${key.latitudeBucket}:${key.longitudeBucket}",
                datasetId=dataset.id,datasetRevision=dataset.revision,datasetName=dataset.name,
                bounds=bounds,features=features,rasters=rasters,incomplete=incomplete,
            )
            cache[key]=layer
            while(cache.size>8)cache.remove(cache.keys.first())
            current=layer
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){
            // 预取失败不伪装成“此处无数据”；保留旧层，准星会回退到 Core 精确查询。
        }finally{
            lease?.let{snapshot->withContext(NonCancellable){runCatching{maps.charts.releaseSnapshot(snapshot.id)}}}
        }
    }
    return current
}

internal suspend fun probeChartCursor(service:ChartDataService,selectedIds:List<String>,point:GeoPoint,zoom:Double):ChartCursorProbe = withContext(Dispatchers.Default) {
    require(selectedIds.size==1&&point.valid()) {"CHART_SELECTED_DATA_MISSING"}
    // 租约、完整几何和文件优先级全留在 Core；准星不再下载整幅海岸并重复做面裁剪。
    val info=service.inspectPosition(selectedIds,ChartPoint(point.lat,point.lon),chartCursorRadius(point,zoom))
    ChartCursorProbe(point,info.datasetName,info.hits.map {it.feature},
        info.raster?.let {ChartRasterProbe(it.grid,info.datasetName,it.elevationMeters)},info.incomplete,
        info.hits.associate {it.feature.id to it.distanceMeters})
}

/** 半径按整米稳定；镜头浮点误差不使一次已经完成的查询重新进入加载状态。 */
internal fun chartCursorRadius(point:GeoPoint,zoom:Double):Double =
    ceil(24*156543.03392*cos(Math.toRadians(point.lat.coerceIn(-85.0,85.0)))/2.0.pow(zoom)).coerceIn(2.0,150.0)

internal fun ChartCursorProbe.distance(feature:NauticalFeature):Double=distances[feature.id] ?: chartFeatureDistance(feature,point)

/** 只用于“附近”说明；面内位置为零。球面短距离经度解缠支持日期变更线。 */
internal fun chartFeatureDistance(feature:NauticalFeature,point:GeoPoint):Double {
    val p=ChartPoint(point.lat,point.lon)
    if(feature.geometry.kind==ChartGeometryKind.POLYGON&&feature.geometry.parts.any {!it.hole&&containsRing(it.points,p)}&&feature.geometry.parts.none {it.hole&&containsRing(it.points,p)})return 0.0
    fun xy(p:ChartPoint)=Pair(((p.longitude-point.lon+540)%360-180)*111_320*cos(Math.toRadians(point.lat)),(p.latitude-point.lat)*111_320)
    return feature.geometry.parts.minOfOrNull {part->
        if(feature.geometry.kind in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT))part.points.minOfOrNull {val(x,y)=xy(it);hypot(x,y)} ?: Double.POSITIVE_INFINITY
        else part.points.zipWithNext().minOfOrNull {(a,b)->
            val(x,y)=xy(a);val(u,v)=xy(b);val dx=u-x;val dy=v-y
            val t=(-(x*dx+y*dy)/(dx*dx+dy*dy).coerceAtLeast(.000001)).coerceIn(0.0,1.0)
            hypot(x+t*dx,y+t*dy)
        } ?: Double.POSITIVE_INFINITY
    } ?: Double.POSITIVE_INFINITY
}
