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
    val cells:List<ChartCellRevision>,
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
        val chartPoint=ChartPoint(point.lat,point.lon)

        // Cursor semantics are deliberately independent from visual zoom. Resolve overlapping
        // chart cells using source priority/coverage first, then hit-test the already-resident
        // full-detail objects. Zoom may change the nearby radius, but it must never swap the
        // authoritative depth area merely because the map was zoomed out.
        val rankedCells=cells.sortedWith(
            compareBy<ChartCellRevision>{it.priority}
                .thenBy{cursorScaleDenominator(it)?:Int.MAX_VALUE}
                .thenByDescending{it.edition}
                .thenByDescending{it.update}
                .thenBy{it.cellId}
        )
        val activeCells=linkedSetOf<String>()
        var occupied=false
        for(cell in rankedCells) {
            if(!occupied&&cursorCellWithinCoverage(cell,chartPoint))activeCells+=cell.cellId
            if(cursorCellCovers(cell,chartPoint)||rasters.any {it.grid.cellId==cell.cellId&&it.grid.pixelAt(chartPoint)!=null})
                occupied=true
        }

        val raw=chartObjectsAt(features,point,zoom,radiusMeters=radius,limit=512)
        val hits=raw.filter {it.cellId in activeCells}.let {accepted->
            val ordered=accepted.distinctBy{it.id}.sortedWith(
                compareBy<NauticalFeature>{cursorFeaturePriority(it)}
                    .thenBy{chartFeatureDistance(it,point)}
            )
            (ordered.take(32)+ordered.filter{cursorFeaturePriority(it)>=4}.take(16))
                .distinctBy{it.id}.take(48)
        }
        val distances=hits.associate {feature->feature.id to chartFeatureDistance(feature,point)}

        // Raster depth follows the same cell precedence. A lower-priority grid must not replace
        // a higher-priority vector depth area simply because both happen to cover the coordinate.
        val raster=rankedCells.asSequence().filter {it.cellId in activeCells}.mapNotNull {cell->
            rasters.firstNotNullOfOrNull {item->
                if(item.grid.cellId!=cell.cellId)return@firstNotNullOfOrNull null
                val pixel=item.grid.pixelAt(chartPoint)?:return@firstNotNullOfOrNull null
                val x=pixel.first-item.window.column
                val y=pixel.second-item.window.row
                if(x !in 0 until item.window.width||y !in 0 until item.window.height)null
                else ChartRasterProbe(item.grid,datasetName,item.window.elevationAt(x,y))
            }
        }.firstOrNull()
        return ChartCursorProbe(point,datasetName,hits,raster,incomplete,distances)
    }
}


private data class ChartCursorLayerKey(val datasetId:String,val revision:Long,val latitudeBucket:Int,val longitudeBucket:Int)

private const val CURSOR_LAYER_HALF_METERS=1_600.0
private const val CURSOR_LAYER_BUCKET_METERS=400.0
private const val CURSOR_LAYER_MAX_FEATURES=8_000

private val CURSOR_LAYER_KINDS:Set<NauticalFeatureKind> =
    NauticalFeatureKind.entries.filterNot{it==NauticalFeatureKind.COVERAGE}.toSet()

private fun cursorScaleDenominator(cell:ChartCellRevision):Int? =
    cell.compilationScale ?: when(cell.linzScaleBand) {
        "1:4k - 1:22k"->4_000
        "1:22k - 1:90k"->22_000
        "1:90k - 1:350k"->90_000
        "1:350k - 1:1,500k"->350_000
        "1:1.5mil and smaller"->1_500_000
        else->null
    }

private fun cursorGeometryContains(geometry:ChartGeometry,point:ChartPoint):Boolean {
    if(geometry.kind!=ChartGeometryKind.POLYGON)return false
    var coverage=0
    for(part in geometry.parts)if(containsRing(part.points,point))coverage+=if(part.hole)-1 else 1
    return coverage>0
}

private fun cursorCellCovers(cell:ChartCellRevision,point:ChartPoint):Boolean =
    cell.coverage.any{it.covered&&cursorGeometryContains(it.geometry,point)}&&
        !cell.coverage.any{!it.covered&&cursorGeometryContains(it.geometry,point)}

private fun cursorCellWithinCoverage(cell:ChartCellRevision,point:ChartPoint):Boolean =
    cell.coverage.none{it.covered}||cursorCellCovers(cell,point)

private fun cursorFeaturePriority(feature:NauticalFeature)=when {
    feature.attributes["GPKG_GEOMETRY_STATUS"]=="DATELINE_TOPOLOGY_UNCERTAIN"->0
    feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)->1
    feature.kind==NauticalFeatureKind.SOUNDING->2
    feature.kind==NauticalFeatureKind.DEPTH_CONTOUR->3
    feature.kind==NauticalFeatureKind.LAND->5
    else->4
}

private fun cursorLayerBounds(center:GeoPoint,halfMeters:Double=CURSOR_LAYER_HALF_METERS):ChartBounds {
    val dy=halfMeters/111_320.0
    val dx=halfMeters/(111_320.0*cos(Math.toRadians(center.lat)).coerceAtLeast(.05))
    fun norm(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
    return ChartBounds(norm(center.lon-dx),(center.lat-dy).coerceAtLeast(-89.999),norm(center.lon+dx),(center.lat+dy).coerceAtMost(89.999))
}

private fun cursorBoundsIntersect(a:ChartBounds,b:ChartBounds):Boolean =
    a.split().any{x->b.split().any{y->x.east>=y.west&&x.west<=y.east&&x.north>=y.south&&x.south<=y.north}}

private fun cursorLayerKey(dataset:ChartDataset,center:GeoPoint):ChartCursorLayerKey {
    val latStep=CURSOR_LAYER_BUCKET_METERS/111_320.0
    val lonStep=CURSOR_LAYER_BUCKET_METERS/(111_320.0*cos(Math.toRadians(center.lat)).coerceAtLeast(.05))
    val lat=floor((center.lat+90.0)/latStep).toInt()
    val lon=floor(((((center.lon+180.0)%360.0)+360.0)%360.0)/lonStep).toInt()
    return ChartCursorLayerKey(dataset.id,dataset.revision,lat,lon)
}

private fun cursorCells(dataset:ChartDataset,bounds:ChartBounds):List<ChartCellRevision> {
    val nearby=dataset.cells.filterNot{it.cancelled}.filter{cell->
        cell.bounds.isEmpty()||cell.bounds.any{cursorBoundsIntersect(it,bounds)}
    }
    return nearby.sortedWith(
        compareBy<ChartCellRevision>{it.priority}
            .thenBy{cursorScaleDenominator(it)?:Int.MAX_VALUE}
            .thenByDescending{it.edition}
            .thenByDescending{it.update}
            .thenBy{it.cellId}
    ).take(256)
}

/**
 * 地图移动时按 zoom 选择匹配比例尺的数据层，而不是所有尺度一起装进内存：
 * 缩远只载粗岸线/深度区/主风险；放近再加入等深线、测深点和细对象。
 */
@Composable
internal fun rememberChartCursorLayer(maps:MapSessionStore,view:MapViewState):ChartCursorLayer? {
    val state by maps.charts.state.collectAsState()
    val ids=maps.selectedDatasetIds
    val dataset=ids.singleOrNull()?.let{id->state.datasets.firstOrNull{it.id==id}}
    val enabled=maps.portrayalPreferences.showCursorInformation&&view.interactive&&
        !state.loading&&state.error==null&&dataset?.offlineReadable==true&&view.center.valid()
    val key=dataset?.let{cursorLayerKey(it,view.center)}
    val cache=remember(maps.charts){LinkedHashMap<ChartCursorLayerKey,ChartCursorLayer>(6,.75f,true)}
    var current by remember(maps.charts){mutableStateOf<ChartCursorLayer?>(null)}

    LaunchedEffect(enabled,key) {
        if(!enabled||dataset==null||key==null){current=null;return@LaunchedEffect}
        cache[key]?.let{current=it;return@LaunchedEffect}

        val center=view.center
        val bounds=cursorLayerBounds(center)
        val cells=cursorCells(dataset,bounds)
        var lease:ChartDataSnapshot?=null
        try {
            lease=maps.charts.acquireDisplaySnapshot(listOf(dataset.id),bounds)
            val features=ArrayList<NauticalFeature>()
            var after:String?=null
            var incomplete=false
            do {
                currentCoroutineContext().ensureActive()
                val room=(CURSOR_LAYER_MAX_FEATURES-features.size).coerceAtLeast(1)
                val page=maps.charts.querySpatial(
                    lease.id,bounds,ChartSpatialFilter(cells.map{it.cellId}.toSet(),CURSOR_LAYER_KINDS),
                    limit=min(1_200,room),afterId=after
                )
                features+=page.features
                incomplete=incomplete||page.truncated
                after=page.nextAfterId
                if(features.size>=CURSOR_LAYER_MAX_FEATURES&&page.hasMore){incomplete=true;break}
                if(!page.hasMore)break
            }while(after!=null)
            val rasters=runCatching {maps.charts.rasterWindows(lease.id,bounds,maxCells=65_536)}
                .getOrElse {emptyList()}
            val layer=ChartCursorLayer(
                key="${dataset.id}:${dataset.revision}:${key.latitudeBucket}:${key.longitudeBucket}",
                datasetId=dataset.id,datasetRevision=dataset.revision,datasetName=dataset.name,
                bounds=bounds,cells=cells,features=features,rasters=rasters,incomplete=incomplete,
            )
            cache[key]=layer
            while(cache.size>6)cache.remove(cache.keys.first())
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
