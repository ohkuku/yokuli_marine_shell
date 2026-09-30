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


private class CursorResidentIndex(private val all:List<NauticalFeature>) {
    private companion object { const val BUCKET_DEGREES=.002 }
    private val buckets=HashMap<Long,MutableList<NauticalFeature>>()
    private val wide=ArrayList<NauticalFeature>()
    private fun key(lat:Int,lon:Int)=(lat.toLong() shl 32) xor (lon.toLong() and 0xffffffffL)
    init {
        for(feature in all) {
            val points=feature.geometry.parts.flatMap{it.points}
            if(points.isEmpty())continue
            val south=points.minOf{it.latitude};val north=points.maxOf{it.latitude}
            val west=points.minOf{it.longitude};val east=points.maxOf{it.longitude}
            // Dateline-spanning or very large objects stay in a tiny always-check list.
            if(east-west>180.0) {wide+=feature;continue}
            val minLat=floor((south+90.0)/BUCKET_DEGREES).toInt()
            val maxLat=floor((north+90.0)/BUCKET_DEGREES).toInt()
            val minLon=floor((west+180.0)/BUCKET_DEGREES).toInt()
            val maxLon=floor((east+180.0)/BUCKET_DEGREES).toInt()
            val cells=(maxLat-minLat+1L)*(maxLon-minLon+1L)
            if(cells>256L) {wide+=feature;continue}
            for(lat in minLat..maxLat)for(lon in minLon..maxLon)
                buckets.getOrPut(key(lat,lon)){ArrayList()}+=feature
        }
    }
    fun candidates(point:GeoPoint,radiusMeters:Double):List<NauticalFeature> {
        val latRadius=radiusMeters/111_320.0
        val lonRadius=radiusMeters/(111_320.0*cos(Math.toRadians(point.lat)).coerceAtLeast(.05))
        if(point.lon-lonRadius < -180.0||point.lon+lonRadius > 180.0)return all
        val minLat=floor((point.lat-latRadius+90.0)/BUCKET_DEGREES).toInt()
        val maxLat=floor((point.lat+latRadius+90.0)/BUCKET_DEGREES).toInt()
        val minLon=floor((point.lon-lonRadius+180.0)/BUCKET_DEGREES).toInt()
        val maxLon=floor((point.lon+lonRadius+180.0)/BUCKET_DEGREES).toInt()
        val result=ArrayList<NauticalFeature>(wide.size+64);result+=wide
        for(lat in minLat..maxLat)for(lon in minLon..maxLon)buckets[key(lat,lon)]?.let(result::addAll)
        return result.distinctBy{it.id}
    }
}

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
    private val residentIndex:CursorResidentIndex,
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
        val manualOrder=cells.any{it.priorityExplicit}
        val rankedCells=cells.sortedWith(
            (if(manualOrder)
                compareBy<ChartCellRevision>{it.priority}
                    .thenBy{it.detailTier()?:Int.MAX_VALUE}
                    .thenBy{it.detailScaleDenominator()?:Int.MAX_VALUE}
             else
                compareBy<ChartCellRevision>{it.detailTier()?:Int.MAX_VALUE}
                    .thenBy{it.detailScaleDenominator()?:Int.MAX_VALUE}
                    .thenBy{it.priority})
                .thenByDescending{it.edition}
                .thenByDescending{it.update}
                .thenBy{it.cellId}
        )
        val activeCells=linkedSetOf<String>()
        var occupied=false
        for(cell in rankedCells) {
            val rasterHere=rasters.any {it.grid.cellId==cell.cellId&&it.grid.pixelAt(chartPoint)!=null}
            if(manualOrder) {
                if(!occupied&&(cursorCellWithinCoverage(cell,chartPoint)||rasterHere))activeCells+=cell.cellId
                if(cursorCellCovers(cell,chartPoint)||rasterHere)occupied=true
            } else if(cursorCellWithinCoverage(cell,chartPoint)||rasterHere) {
                // Automatic precedence is resolved from the actual feature tier at this position,
                // not from file order or a mixed-cell summary.
                activeCells+=cell.cellId
            }
        }

        val ownershipKinds=setOf(
            NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,
            NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.LAND
        )
        // Area ownership is exact-position semantics and must never compete with a nearby-object
        // limit. Dense soundings may fill the 512 nearby slots, but the containing DEPARE/LNDARE
        // still has to participate in source resolution.
        val candidates=residentIndex.candidates(point,radius)
        val owners=candidates.asSequence()
            .filter{it.kind in ownershipKinds&&it.geometry.kind==ChartGeometryKind.POLYGON&&cursorGeometryContains(it.geometry,chartPoint)}
            .toList()
        val nearby=chartObjectsAt(candidates,point,zoom,radiusMeters=radius,limit=512)
        val accepted=(owners+nearby).distinctBy{it.id}.filter {it.cellId in activeCells}
        val cellOrder=rankedCells.associateBy{it.cellId}
        fun featureScale(feature:NauticalFeature):Int? =
            feature.detailScaleDenominator() ?: cellOrder[feature.cellId]?.detailScaleDenominator()
        fun featureTier(feature:NauticalFeature):Int? =
            feature.detailTier() ?: cellOrder[feature.cellId]?.detailTier()
        val sourceComparator=if(manualOrder)
            compareBy<NauticalFeature>{cellOrder[it.cellId]?.priority?:Int.MAX_VALUE}
                .thenBy{featureTier(it)?:Int.MAX_VALUE}
                .thenBy{featureScale(it)?:Int.MAX_VALUE}
                .thenBy{it.cellId}
        else
            compareBy<NauticalFeature>{featureTier(it)?:Int.MAX_VALUE}
                .thenBy{featureScale(it)?:Int.MAX_VALUE}
                .thenBy{cellOrder[it.cellId]?.priority?:Int.MAX_VALUE}
                .thenBy{it.cellId}
        val winningOwner=accepted.asSequence()
            .filter{it.kind in ownershipKinds&&chartFeatureDistance(it,point)<=.001}
            .minWithOrNull(sourceComparator)
        val resolved=if(winningOwner==null)accepted else accepted.filter {feature->
            // Ownership polygons obey the winning source. Independent hazards/facilities remain
            // visible even if they came from another layer so a fine DEPARE cannot hide a rock/light.
            feature.kind !in ownershipKinds||sourceComparator.compare(feature,winningOwner)<=0
        }
        val hits=resolved.distinctBy{it.id}.sortedWith(Comparator {a,b->
            val semantic=cursorFeaturePriority(a).compareTo(cursorFeaturePriority(b))
            if(semantic!=0)semantic else {
                val source=sourceComparator.compare(a,b)
                if(source!=0)source else chartFeatureDistance(a,point).compareTo(chartFeatureDistance(b,point))
            }
        }).let {ordered->
            (ordered.take(32)+ordered.filter{cursorFeaturePriority(it)>=4}.take(16))
                .distinctBy{it.id}.take(48)
        }
        val distances=hits.associate {feature->feature.id to chartFeatureDistance(feature,point)}

        // Raster depth follows the same cell precedence. A lower-priority grid must not replace
        // a higher-priority vector depth area simply because both happen to cover the coordinate.
        val rasterCandidates=rasters.mapNotNull {item->
            if(item.grid.cellId !in activeCells)return@mapNotNull null
            val pixel=item.grid.pixelAt(chartPoint)?:return@mapNotNull null
            val x=pixel.first-item.window.column
            val y=pixel.second-item.window.row
            if(x !in 0 until item.window.width||y !in 0 until item.window.height)return@mapNotNull null
            Triple(item,cellOrder[item.grid.cellId],ChartRasterProbe(item.grid,datasetName,item.window.elevationAt(x,y)))
        }
        val raster=rasterCandidates.minWithOrNull(
            if(manualOrder)
                compareBy<Triple<ChartRasterWindow,ChartCellRevision?,ChartRasterProbe>>{it.second?.priority?:Int.MAX_VALUE}
                    .thenBy{max(it.first.grid.pixelWidthDegrees,it.first.grid.pixelHeightDegrees)}
            else
                compareBy<Triple<ChartRasterWindow,ChartCellRevision?,ChartRasterProbe>>{max(it.first.grid.pixelWidthDegrees,it.first.grid.pixelHeightDegrees)}
                    .thenBy{it.second?.priority?:Int.MAX_VALUE}
        )?.third
        val ownsPoint=hits.any{it.kind in ownershipKinds&&chartFeatureDistance(it,point)<=.001}||raster!=null
        return ChartCursorProbe(point,datasetName,hits,raster,incomplete||!ownsPoint,distances)
    }
}


private data class ChartCursorLayerKey(val datasetId:String,val revision:Long,val latitudeBucket:Int,val longitudeBucket:Int)

private const val CURSOR_LAYER_HALF_METERS=1_600.0
private const val CURSOR_LAYER_BUCKET_METERS=400.0
private const val CURSOR_LAYER_MAX_FEATURES=3_000
private const val CURSOR_DETAIL_HALF_METERS=900.0

private val CURSOR_LAYER_BASE_KINDS:Set<NauticalFeatureKind> =
    NauticalFeatureKind.entries.filterNot{
        it in setOf(NauticalFeatureKind.COVERAGE,NauticalFeatureKind.SOUNDING,
            NauticalFeatureKind.DEPTH_CONTOUR,NauticalFeatureKind.QUALITY)
    }.toSet()
private val CURSOR_LAYER_DETAIL_KINDS:Set<NauticalFeatureKind> =
    setOf(NauticalFeatureKind.SOUNDING,NauticalFeatureKind.DEPTH_CONTOUR,NauticalFeatureKind.QUALITY)

private fun cursorScaleDenominator(cell:ChartCellRevision):Int? = cell.detailScaleDenominator()

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
    val manualOrder=nearby.any{it.priorityExplicit}
    return nearby.sortedWith(
        (if(manualOrder)
            compareBy<ChartCellRevision>{it.priority}.thenBy{it.detailTier()?:Int.MAX_VALUE}.thenBy{cursorScaleDenominator(it)?:Int.MAX_VALUE}
         else
            compareBy<ChartCellRevision>{it.detailTier()?:Int.MAX_VALUE}.thenBy{cursorScaleDenominator(it)?:Int.MAX_VALUE}.thenBy{it.priority})
            .thenByDescending{it.edition}
            .thenByDescending{it.update}
            .thenBy{it.cellId}
    ).take(256)
}

/**
 * 地图中心附近维护固定物理范围的全细节语义瓦片。视觉绘制仍可按 zoom 做 LOD，
 * 但准星、水深和对象语义绝不能因为缩放级别而切换到另一套粗资料。
 * 最近瓦片留在内存并大幅重叠，拖图时后台换块通常不会让读数重新等待。
 */
@Composable
internal fun rememberChartCursorLayer(maps:MapSessionStore,view:MapViewState):ChartCursorLayer? {
    val state by maps.charts.state.collectAsState()
    val ids=maps.selectedDatasetIds
    val dataset=ids.singleOrNull()?.let{id->state.datasets.firstOrNull{it.id==id}}
    val enabled=maps.portrayalPreferences.showCursorInformation&&view.interactive&&
        !state.loading&&state.error==null&&dataset?.offlineReadable==true&&view.center.valid()
    val key=dataset?.let{cursorLayerKey(it,view.center)}
    val cache=remember(maps.charts){LinkedHashMap<ChartCursorLayerKey,ChartCursorLayer>(4,.75f,true)}
    var current by remember(maps.charts){mutableStateOf<ChartCursorLayer?>(null)}

    LaunchedEffect(enabled,key) {
        if(!enabled||dataset==null||key==null){current=null;return@LaunchedEffect}
        val cached=cache[key]
        if(cached!=null) {
            current=cached
            if(cached.key.endsWith(":full"))return@LaunchedEffect
        }

        val center=view.center
        val bounds=cursorLayerBounds(center)
        val cells=cursorCells(dataset,bounds)
        var lease:ChartDataSnapshot?=null
        try {
            val snapshot=maps.charts.acquireDisplaySnapshot(listOf(dataset.id),bounds)
            lease=snapshot
            suspend fun load(queryBounds:ChartBounds,queryCells:List<ChartCellRevision>,kinds:Set<NauticalFeatureKind>):Pair<List<NauticalFeature>,Boolean> {
                val loaded=ArrayList<NauticalFeature>()
                var after:String?=null
                var clipped=false
                do {
                    currentCoroutineContext().ensureActive()
                    val room=(CURSOR_LAYER_MAX_FEATURES-loaded.size).coerceAtLeast(1)
                    val page=maps.charts.querySpatial(
                        snapshot.id,queryBounds,ChartSpatialFilter(queryCells.map{it.cellId}.toSet(),kinds),
                        limit=min(1_200,room),afterId=after
                    )
                    loaded+=page.features
                    clipped=clipped||page.truncated
                    after=page.nextAfterId
                    if(loaded.size>=CURSOR_LAYER_MAX_FEATURES&&page.hasMore){clipped=true;break}
                    if(!page.hasMore)break
                }while(after!=null)
                return loaded to clipped
            }

            // Phase 1: area ownership, land and hazards. Reuse a cached base tile if a previous
            // camera move cancelled only the dense-detail phase.
            val baseResult=if(cached!=null)cached.features to cached.incomplete else load(bounds,cells,CURSOR_LAYER_BASE_KINDS)
            val baseFeatures=baseResult.first
            val baseIncomplete=baseResult.second
            val rasters=if(cached!=null)cached.rasters else runCatching {
                maps.charts.rasterWindows(snapshot.id,bounds,maxCells=65_536)
            }.getOrElse {emptyList()}
            val prefix="${dataset.id}:${dataset.revision}:${key.latitudeBucket}:${key.longitudeBucket}"
            val baseLayer=cached ?: ChartCursorLayer(
                key="$prefix:base",
                datasetId=dataset.id,datasetRevision=dataset.revision,datasetName=dataset.name,
                bounds=bounds,cells=cells,features=baseFeatures,rasters=rasters,incomplete=baseIncomplete,
                residentIndex=withContext(Dispatchers.Default){CursorResidentIndex(baseFeatures)},
            )
            if(cached==null) {
                cache[key]=baseLayer
                while(cache.size>4)cache.remove(cache.keys.first())
                current=baseLayer
            }

            // Phase 2: dense soundings/contours arrive behind the already-correct area answer.
            // 900 m covers a 400 m bucket plus the maximum 150 m cursor radius with prefetch margin.
            val detailBounds=cursorLayerBounds(center,CURSOR_DETAIL_HALF_METERS)
            val detailCells=cursorCells(dataset,detailBounds)
            val (details,detailIncomplete)=load(detailBounds,detailCells,CURSOR_LAYER_DETAIL_KINDS)
            // A successful empty query is also a completed detail phase; cache that fact so
            // revisiting a quiet 400 m tile does not repeat the same SOUNDING/CONTOUR lookup.
            val fullFeatures=(baseFeatures+details).distinctBy{it.id}
            val fullLayer=baseLayer.copy(
                key="$prefix:full",
                features=fullFeatures,
                cells=(cells+detailCells).distinctBy{it.cellId},
                incomplete=baseLayer.incomplete||detailIncomplete,
                residentIndex=withContext(Dispatchers.Default){CursorResidentIndex(fullFeatures)},
            )
            cache[key]=fullLayer
            current=fullLayer
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
