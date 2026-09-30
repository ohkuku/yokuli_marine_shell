package com.yokuli.marine.shell.rebuild.scene.navigation

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.marine.chart.ChartDrawingClipper
import kotlinx.coroutines.*
import java.security.MessageDigest
import kotlin.math.*

/** 页面 remember 一个加载器。缓存仅为最多两份派生网格，没有复制原资料或建立第二份数据源。 */
class NavigationTerrainLoader(private val service:ChartDataService) {
    private val guard=Any()
    private data class Cached(val datasetId:String,val scene:NavigationChartScene)
    private val cache=LinkedHashMap<String,Cached>(3,.75f,true)
    private var generation=0L

    /** 切换/移除资料时使已在途的旧生成失效；页面仍应通过 LaunchedEffect 取消旧请求。 */
    fun clearSource(datasetId:String?=null)=synchronized(guard) {
        generation++
        if(datasetId==null)cache.clear()else cache.entries.removeAll {(_,entry)->entry.datasetId==datasetId}
    }

    suspend fun load(selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double=2_000.0):NavigationChartScene=try {withTimeout(25_000) {withContext(Dispatchers.Default) {
        require(origin.valid()&&abs(origin.lat)<=89.8) {"CHART_TERRAIN_POSITION_INVALID"}
        require(radiusMeters.isFinite()&&radiusMeters in 250.0..8_000.0) {"CHART_TERRAIN_RADIUS_INVALID"}
        require(selectedIds.size==1) {"CHART_SELECTED_DATA_MISSING"}
        val current=service.state.value.datasets.firstOrNull {it.id==selectedIds.single()}
        require(current?.offlineReadable!=false) {"CHART_SELECTED_DATA_MISSING"}
        val before=synchronized(guard){generation}
        val expected=current?.let {key(it,origin,radiusMeters)}
        expected?.let {synchronized(guard){cache[it]?.scene}}?.let {return@withContext it}
        var lease:ChartDataSnapshot?=null
        try {
            // 接到快照立即在同一 Default 上下文保存句柄；后续 IO/取消统一走 finally 释放。
            lease=service.acquireSnapshot(selectedIds)
            val snapshot=requireNotNull(lease)
            require(snapshot.missingDatasetIds.isEmpty()&&snapshot.datasets.size==1&&snapshot.datasets.single().offlineReadable) {"CHART_SELECTED_DATA_MISSING"}
            val dataset=snapshot.datasets.single()
            val sceneKey=key(dataset,origin,radiusMeters)
            val warnings=linkedSetOf<NavigationChartWarning>()
            if(dataset.preparing||dataset.preparationIssue!=null)warnings+=NavigationChartWarning.PARTIAL_CONTENT
            val bounds=terrainBounds(origin,radiusMeters)
            val features=ArrayList<NauticalFeature>()
            var after:String?=null;var vertices=0;var complete=true
            if(snapshot.cells.any {it.featureCount>0})do {
                ensureActive()
                val page=service.query(snapshot.id,bounds,256,after)
                vertices+=page.features.sumOf {feature->feature.geometry.parts.sumOf {it.points.size}}
                if(features.size+page.features.size>4_096||vertices>350_000||page.truncated) {complete=false;break}
                features+=page.features
                if(!page.hasMore)break
                require(page.nextAfterId!=null&&page.nextAfterId!=after) {"CHART_QUERY_CURSOR_STALLED"}
                after=page.nextAfterId
            }while(true)
            // 未查齐未知范围时不能生成一个把低优先级/未知区伪装为完整海底的模型。
            val scene=if(!complete) {
                warnings+=NavigationChartWarning.MODEL_BUDGET;warnings+=NavigationChartWarning.PARTIAL_CONTENT
                NavigationChartScene(sceneKey,origin,radiusMeters,null,null,emptyList(),emptyList(),warnings,0,0.0,0.0,dataset.revision)
            }else {
                val drawing=ChartDrawingClipper.compose(snapshot,features,bounds)
                ensureActive()
                if(drawing.incompleteGeometry)warnings+=NavigationChartWarning.GEOMETRY_UNCERTAIN
                val estimated=dataset.rasters.orEmpty().sumOf {estimateTerrainSamples(it,bounds)}
                val windows=if(estimated<=262_144) {
                    try {service.rasterWindows(snapshot.id,bounds,262_144)}
                    catch(cancel:CancellationException){throw cancel}
                    catch(error:Exception) {
                        if(error.message?.contains("GEBCO_WINDOW_LIMIT")!=true)throw error
                        warnings+=NavigationChartWarning.RASTER_RESOLUTION_LIMIT;emptyList()
                    }
                }else {warnings+=NavigationChartWarning.RASTER_RESOLUTION_LIMIT;emptyList()}
                NavigationTerrainGeometry(origin,radiusMeters,dataset,warnings).build(sceneKey,drawing,windows)
            }
            ensureActive()
            synchronized(guard) {
                if(before!=generation)throw CancellationException("CHART_TERRAIN_SOURCE_CHANGED")
                // 一个派生 GLB 的输入完全由版本+窗口确定；图册版本改变不会命中旧缓存。
                cache[sceneKey]=Cached(dataset.id,scene)
                while(cache.size>2)cache.remove(cache.keys.first())
            }
            scene
        }finally {
            lease?.let {snapshot->withContext(NonCancellable){runCatching {withTimeout(1_500){service.releaseSnapshot(snapshot.id)}}}}
        }
    }}}catch(timeout:TimeoutCancellationException){throw IllegalStateException("CHART_TERRAIN_QUERY_TIMEOUT",timeout)}

    private fun key(dataset:ChartDataset,origin:GeoPoint,radius:Double):String {
        val text="terrain-1|${dataset.id}|${dataset.revision}|${origin.lat}|${origin.lon}|$radius"
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).take(16).joinToString(""){"%02x".format(it)}
    }
}

/** 无长期宿主时的一次加载入口；持续视图使用 NavigationTerrainLoader 保留有界缓存。 */
suspend fun loadNavigationTerrain(service:ChartDataService,selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double=2_000.0):NavigationChartScene =
    NavigationTerrainLoader(service).load(selectedIds,origin,radiusMeters)

internal fun terrainBounds(origin:GeoPoint,radius:Double):ChartBounds {
    val dy=radius/111_320.0;val dx=dy/cos(Math.toRadians(origin.lat)).coerceAtLeast(.003)
    fun wrap(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
    return ChartBounds(wrap(origin.lon-dx),(origin.lat-dy).coerceAtLeast(-90.0),wrap(origin.lon+dx),(origin.lat+dy).coerceAtMost(90.0))
}

/** 与 Core 窗口的外边界/像元计数一致，跨日期线不按全球包围框估算。 */
private fun estimateTerrainSamples(grid:RasterBathymetryGrid,bounds:ChartBounds):Long {
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
