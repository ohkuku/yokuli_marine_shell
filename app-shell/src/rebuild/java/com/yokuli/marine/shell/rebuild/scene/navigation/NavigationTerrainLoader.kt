package com.yokuli.marine.shell.rebuild.scene.navigation

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.marine.chart.ChartDrawingClipper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.*

/** 海图与 AIS 共同使用的有界分块编译器；缓存跨页面，来源事实始终由 Core 租约验证。 */
class NavigationTerrainLoader(private val service:ChartDataService,cacheDirectory:File?=null) {
    private val products=NavigationTerrainProducts(cacheDirectory)
    private val buildMutex=Mutex()
    fun trimMemory()=products.trim()
    /** 兼容旧调用；换源由内容键隔离，不删除其他视图仍可使用的不可变产物。 */
    fun clearSource(datasetId:String?=null){if(datasetId==null)products.trim()}

    suspend fun load(selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double=2_000.0):NavigationChartScene =
        loadTile(selectedIds,TerrainTile(terrainBounds(origin,radiusMeters),origin,radiusMeters))

    /**
     * 先发布最近块，然后增量扩展完整视野；每块独立取消、校验、持久化与重用。
     * 不按栅格密度缩小整个视野；个别超预算空块细分后重试，绝不补造海底。
     */
    suspend fun loadRegion(selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double,
        publish:suspend (NavigationChartScene)->Unit):NavigationChartScene {
        require(origin.valid()&&abs(origin.lat)<=89.8){"CHART_TERRAIN_POSITION_INVALID"}
        require(selectedIds.size==1){"CHART_SELECTED_DATA_MISSING"}
        val initial=service.state.value.datasets.firstOrNull{it.id==selectedIds.single()}
        require(initial?.offlineReadable==true){"CHART_SELECTED_DATA_MISSING"}
        val dataset=requireNotNull(initial)
        val source=terrainSourceKey(dataset)
        val verified=withTimeoutOrNull(4_000){service.validateDisplayProduct(dataset.id,dataset.revision)}
            ?:throw IllegalStateException("CHART_TERRAIN_QUERY_TIMEOUT")
        require(verified){"CHART_SELECTED_DATA_MISSING"}
        val radius=radiusMeters.coerceIn(500.0,32_000.0)
        val pending=ArrayDeque(terrainTiles(origin,radius))
        var expected=pending.size
        val completed=linkedMapOf<String,NavigationChartScene>()
        var lastError:Exception?=null
        suspend fun current():NavigationChartScene=withContext(Dispatchers.Default){
            val leaves=completed.values.toList()
            val warnings=leaves.flatMap{it.warnings}.toMutableSet()
            if(leaves.size<expected)warnings+=NavigationChartWarning.PARTIAL_CONTENT
            val markers=leaves.flatMap{it.markers}.distinctBy{it.id}.map{it.copy(
                eastMeters=wrappedLongitude(it.point.lon-origin.lon)*111_320*cos(Math.toRadians(origin.lat)),
                southMeters=-(it.point.lat-origin.lat)*111_320)}
            NavigationChartScene(terrainHash("region:$source:$origin:$radius:"+leaves.joinToString{it.sceneKey}),origin,radius,
                null,null,leaves.flatMap{it.sources}.distinctBy{it.id},markers,warnings,leaves.sumOf{it.triangleCount},
                leaves.minOfOrNull{it.minElevationMeters}?:0.0,leaves.maxOfOrNull{it.maxElevationMeters}?:0.0,dataset.revision,
                NavigationTerrainCoverage(leaves.sumOf{it.coverage.rasterSampleCount},leaves.sumOf{it.coverage.missingRasterSamples},
                    markers.count{it.kind==NavigationChartMarkerKind.SOUNDING},markers.count{it.kind!=NavigationChartMarkerKind.SOUNDING}),
                source,terrainBounds(origin,radius),leaves,expected)
        }
        val completedInTime=withTimeoutOrNull(32_000){
            while(pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val latest=service.state.value.datasets.firstOrNull{it.id==dataset.id}
                require(latest?.offlineReadable==true&&terrainSourceKey(latest)==source){"CHART_SELECTED_DATA_MISSING"}
                val tile=pending.removeFirst()
                try {
                    val scene=loadTile(selectedIds,tile,dataset)
                    require(scene.sourceKey==source){"CHART_SELECTED_DATA_MISSING"}
                    val budget=NavigationChartWarning.MODEL_BUDGET in scene.warnings||NavigationChartWarning.RASTER_RESOLUTION_LIMIT in scene.warnings
                    if(budget&&(!scene.hasGeometry||NavigationChartWarning.RASTER_RESOLUTION_LIMIT in scene.warnings)&&tile.radius>450&&expected+3<=24) {
                        val children=tile.split().sortedBy{terrainDistanceSquared(it.origin,origin)}
                        children.reversed().forEach(pending::addFirst);expected+=3
                        continue
                    }
                    completed[scene.sceneKey]=scene
                    publish(current())
                }catch(cancel:CancellationException){throw cancel}
                catch(failure:Exception){lastError=failure}
                yield()
            }
            true
        }?:false
        if(completed.isEmpty())throw lastError?:IllegalStateException("CHART_TERRAIN_QUERY_TIMEOUT")
        val result=current()
        // 可用分块继续呈现，尚未读取的部分明确标记；下一次进入可从磁盘续载。
        if(!completedInTime||lastError!=null)publish(result)
        return result
    }

    private suspend fun loadTile(selectedIds:List<String>,tile:TerrainTile,validated:ChartDataset?=null):NavigationChartScene {
        val result=withTimeoutOrNull(10_000){buildMutex.withLock{withContext(Dispatchers.Default){
            // 整个区域已由 Core 核验精确来源版本，命中网格无需再跨 IPC 裁全国 coverage。
            if(validated!=null) {
                val source=terrainSourceKey(validated)
                products.read(terrainHash("$source:${tile.bounds}"),source)?.let{return@withContext it}
            }
            var lease:ChartDataSnapshot?=null
            try {
                lease=service.acquireDisplaySnapshot(selectedIds,tile.bounds)
                val snapshot=requireNotNull(lease)
                require(snapshot.missingDatasetIds.isEmpty()&&snapshot.datasets.size==1&&snapshot.datasets.single().offlineReadable){"CHART_SELECTED_DATA_MISSING"}
                val dataset=snapshot.datasets.single()
                val source=terrainSourceKey(dataset)
                require(validated==null||terrainSourceKey(validated)==source){"CHART_SELECTED_DATA_MISSING"}
                val key=terrainHash("$source:${tile.bounds}")
                // 即便磁盘命中，也先由 Core 验证原件权限/版本；缓存从不绕过源租约。
                products.read(key,source)?.let{return@withContext it}
                val warnings=linkedSetOf<NavigationChartWarning>()
                if(dataset.preparing||dataset.preparationIssue!=null)warnings+=NavigationChartWarning.PARTIAL_CONTENT
                val features=ArrayList<NauticalFeature>()
                var after:String?=null;var vertices=0;var complete=true
                if(snapshot.cells.any{it.featureCount>0})do {
                    ensureActive()
                    val page=service.query(snapshot.id,tile.bounds,256,after)
                    vertices+=page.features.sumOf{feature->feature.geometry.parts.sumOf{it.points.size}}
                    if(features.size+page.features.size>4_096||vertices>350_000||page.truncated){complete=false;break}
                    features+=page.features
                    if(!page.hasMore)break
                    require(page.nextAfterId!=null&&page.nextAfterId!=after){"CHART_QUERY_CURSOR_STALLED"}
                    after=page.nextAfterId
                }while(true)
                val scene=if(!complete){
                    warnings+=NavigationChartWarning.MODEL_BUDGET;warnings+=NavigationChartWarning.PARTIAL_CONTENT
                    NavigationChartScene(key,tile.origin,tile.radius,null,null,emptyList(),emptyList(),warnings,0,0.0,0.0,dataset.revision)
                }else {
                    val drawing=ChartDrawingClipper.compose(snapshot,features,tile.bounds)
                    ensureActive()
                    if(drawing.incompleteGeometry)warnings+=NavigationChartWarning.GEOMETRY_UNCERTAIN
                    val estimated=dataset.rasters.orEmpty().sumOf{estimateTerrainSamples(it,tile.bounds)}
                    val windows=if(estimated<=262_144){
                        try{service.rasterWindows(snapshot.id,tile.bounds,262_144)}
                        catch(cancel:CancellationException){throw cancel}
                        catch(error:Exception){if(error.message?.contains("GEBCO_WINDOW_LIMIT")!=true)throw error
                            warnings+=NavigationChartWarning.RASTER_RESOLUTION_LIMIT;emptyList()}
                    }else{warnings+=NavigationChartWarning.RASTER_RESOLUTION_LIMIT;emptyList()}
                    NavigationTerrainGeometry(tile.origin,tile.radius,dataset,warnings,tile.bounds).build(key,drawing,windows)
                }.copy(sourceKey=source,bounds=tile.bounds)
                ensureActive()
                val latest=service.state.value.datasets.firstOrNull{it.id==dataset.id}
                require(latest?.offlineReadable==true&&terrainSourceKey(latest)==source){"CHART_SELECTED_DATA_MISSING"}
                products.write(scene)
                scene
            }finally{
                lease?.let{snapshot->withContext(NonCancellable){runCatching{withTimeout(1_500){service.releaseSnapshot(snapshot.id)}}}}
            }
        }}}
        return result?:throw IllegalStateException("CHART_TERRAIN_QUERY_TIMEOUT")
    }

    /** 二维导航/抬起提示出现时仅准备第一片；打开三维后复用，避免后台抢建整片海域。 */
    suspend fun prewarm(selectedIds:List<String>,origin:GeoPoint) {
        delay(1_000)
        if(buildMutex.isLocked)return
        try {
            val dataset=service.state.value.datasets.firstOrNull{it.id==selectedIds.singleOrNull()}?:return
            if(!dataset.offlineReadable||withTimeoutOrNull(4_000){service.validateDisplayProduct(dataset.id,dataset.revision)}!=true)return
            loadTile(selectedIds,terrainTiles(origin,4_000.0).first(),dataset)
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){}
    }

    /** 可见宿主取消即可停止预取；逐块让出锁，不启动脱离生命周期的后台建模。 */
    suspend fun prefetch(selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double=2_000.0) {
        delay(400)
        if(buildMutex.isLocked)return
        try{loadRegion(selectedIds,origin,radiusMeters){}}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){}
    }
}

internal fun terrainSourceKey(dataset:ChartDataset)=terrainHash("terrain-4:${dataset.id}:${dataset.revision}:${dataset.preparing}:${dataset.preparationIssue}")
private fun wrappedLongitude(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
private fun terrainDistanceSquared(a:GeoPoint,b:GeoPoint):Double {
    val x=wrappedLongitude(a.lon-b.lon)*cos(Math.toRadians(b.lat));val y=a.lat-b.lat
    return x*x+y*y
}
private data class TerrainTile(val bounds:ChartBounds,val origin:GeoPoint,val radius:Double) {
    fun split():List<TerrainTile> {
        val midX=(bounds.west+bounds.east)/2;val midY=(bounds.south+bounds.north)/2
        return listOf(ChartBounds(bounds.west,bounds.south,midX,midY),ChartBounds(midX,bounds.south,bounds.east,midY),
            ChartBounds(bounds.west,midY,midX,bounds.north),ChartBounds(midX,midY,bounds.east,bounds.north)).map(::terrainTile)
    }
}
private fun terrainTile(bounds:ChartBounds):TerrainTile {
    val center=GeoPoint((bounds.north+bounds.south)/2,(bounds.west+bounds.east)/2)
    val y=(bounds.north-bounds.south)*111_320/2
    val x=(bounds.east-bounds.west)*111_320*cos(Math.toRadians(center.lat))/2
    return TerrainTile(bounds,center,hypot(x,y))
}
private fun terrainTiles(center:GeoPoint,radius:Double):List<TerrainTile> {
    val bounds=terrainBounds(center,radius)
    val longitudeFactor=2.0.pow(ceil(log2(1.0/cos(Math.toRadians(center.lat)).coerceAtLeast(.003))))
    var level=18
    while(true){
        val step=360.0/(1 shl level)
        val longitudeStep=(step*longitudeFactor).coerceAtMost(360.0)
        val rows=floor((bounds.south+90)/step).toInt()..floor((bounds.north+90-1e-10)/step).toInt()
        val ranges=bounds.split().map{box->floor((box.west+180)/longitudeStep).toInt()..floor((box.east+180-1e-10)/longitudeStep).toInt()}
        val count=rows.count().toLong()*ranges.sumOf{it.count().toLong()}
        if(count in 1..9){
            return ranges.flatMap{cols->rows.flatMap{y->cols.map{x->x to y}}}.distinct().map{(x,y)->
                terrainTile(ChartBounds(-180+x*longitudeStep,(-90+y*step).coerceAtLeast(-90.0),
                    (-180+(x+1)*longitudeStep).coerceAtMost(180.0),(-90+(y+1)*step).coerceAtMost(90.0)))
            }.sortedBy{terrainDistanceSquared(it.origin,center)}
        }
        require(level>4){"CHART_TERRAIN_POSITION_INVALID"}
        level--
    }
}

suspend fun loadNavigationTerrain(service:ChartDataService,selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double=2_000.0):NavigationChartScene =
    NavigationTerrainLoader(service).loadRegion(selectedIds,origin,radiusMeters){}

/** 范围是用户观察范围。密集资料按块处理，不能悄悄缩成 250 米的小片。 */
fun navigationTerrainRadius(dataset:ChartDataset?,point:GeoPoint,requestedRadiusMeters:Double=4_000.0):Double =
    requestedRadiusMeters.takeIf(Double::isFinite)?.coerceIn(500.0,32_000.0)?:4_000.0

internal fun terrainBounds(origin:GeoPoint,radius:Double):ChartBounds {
    val dy=radius/111_320.0;val dx=dy/cos(Math.toRadians(origin.lat)).coerceAtLeast(.003)
    fun wrap(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
    return ChartBounds(wrap(origin.lon-dx),(origin.lat-dy).coerceAtLeast(-90.0),wrap(origin.lon+dx),(origin.lat+dy).coerceAtMost(90.0))
}

/** 与 Core 窗口的外边界/像元计数一致，跨日期线不按全球包围框估算。 */
internal fun estimateTerrainSamples(grid:RasterBathymetryGrid,bounds:ChartBounds):Long {
    val rectangles=hashSetOf<List<Int>>()
    for(box in bounds.split())for(shift in listOf(-360.0,0.0,360.0,720.0)) {
        val west=max(grid.westEdge,box.west+shift);val east=min(grid.westEdge+grid.width*grid.pixelWidthDegrees,box.east+shift)
        val north=min(grid.northEdge,box.north);val south=max(grid.northEdge-grid.height*grid.pixelHeightDegrees,box.south)
        if(east<=west||north<=south)continue
        val x=floor((west-grid.westEdge)/grid.pixelWidthDegrees).toInt().coerceIn(0,grid.width-1)
        val y=floor((grid.northEdge-north)/grid.pixelHeightDegrees).toInt().coerceIn(0,grid.height-1)
        val endX=(floor((east-grid.westEdge)/grid.pixelWidthDegrees).toInt()+1).coerceIn(x+1,grid.width)
        val endY=(floor((grid.northEdge-south)/grid.pixelHeightDegrees).toInt()+1).coerceIn(y+1,grid.height)
        rectangles+=listOf(x,y,endX-x,endY-y)
    }
    return rectangles.sumOf {it[2].toLong()*it[3]}.coerceAtMost(262_145)
}
