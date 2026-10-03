package com.yokuli.runtime.marine.chart.terrain

import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.marine.chart.ChartDrawingClipper
import kotlinx.coroutines.*
import kotlin.math.*

/** Core 的静态地形准备 pass；只能由持久作业调用，页面读取绝不进入此编译路径。 */
internal class ChartTerrainCompiler(private val charts:com.yokuli.runtime.marine.chart.ChartFactReader) {
    constructor(charts:ChartDataService):this(com.yokuli.runtime.marine.chart.ServiceChartFactReader(charts))
    suspend fun compile(snapshot:ChartDataSnapshot,key:String,sourceKey:String,bounds:ChartBounds,lod:Int):ChartTerrainTile {
        val work=currentCoroutineContext()
        val dataset=snapshot.datasets.single()
        val origin=ChartPoint((bounds.south+bounds.north)/2,(bounds.west+bounds.east)/2)
        val radius=hypot((bounds.east-bounds.west)*111_320*cos(Math.toRadians(origin.latitude))/2,
            (bounds.north-bounds.south)*111_320/2)
        val warnings=linkedSetOf<ChartTerrainWarning>()
        if(dataset.preparing||dataset.preparationIssue!=null)warnings+=ChartTerrainWarning.PARTIAL_CONTENT
        val features=ArrayList<NauticalFeature>();var after:String?=null;var vertices=0L
        val baseKinds=setOf(NauticalFeatureKind.LAND,NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,
            NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK,NauticalFeatureKind.OBSTRUCTION,
            NauticalFeatureKind.BEACON,NauticalFeatureKind.LIGHT,NauticalFeatureKind.OTHER)
        if(snapshot.cells.any{it.featureCount>0})do {
            work.ensureActive()
            val page=if(lod==0)charts.querySpatial(snapshot.id,bounds,ChartSpatialFilter(kinds=baseKinds),256,after)
                else charts.query(snapshot.id,bounds,256,after)
            vertices+=page.features.sumOf{feature->feature.geometry.parts.sumOf{it.points.size.toLong()}}
            require(features.size+page.features.size<=8192&&vertices<=800_000&&!page.truncated){"CHART_TERRAIN_SUBDIVIDE_REQUIRED"}
            features+=page.features
            if(!page.hasMore)break
            require(page.nextAfterId!=null&&page.nextAfterId!=after){"CHART_QUERY_CURSOR_STALLED"}
            after=page.nextAfterId
        }while(true)
        work.ensureActive()
        val drawing=ChartDrawingClipper.compose(snapshot,features,bounds)
        if(drawing.incompleteGeometry)warnings+=ChartTerrainWarning.GEOMETRY_UNCERTAIN
        val estimated=dataset.rasters.orEmpty().sumOf{estimateTerrainSamples(it,bounds)}
        val windows=if(estimated<=262_144) {
            try{charts.rasterWindows(snapshot.id,bounds,262_144)}
            catch(cancel:CancellationException){throw cancel}
            catch(failure:Exception){
                if(failure.message?.contains("GEBCO_WINDOW_LIMIT")!=true)throw failure
                error("CHART_TERRAIN_SUBDIVIDE_REQUIRED")
            }
        }else error("CHART_TERRAIN_SUBDIVIDE_REQUIRED")
        val geometry=ChartTerrainGeometry(origin,radius,dataset,warnings,bounds,lod)
        val tile=geometry.build(key,drawing,windows)
        // 粗层可先提供明确受限的概貌；详细层预算不足必须细分，不能把缺掉的半片区域当作完成。
        require(lod==0||!geometry.needsSubdivision||minOf(bounds.north-bounds.south,bounds.east-bounds.west)*111_320<=400){"CHART_TERRAIN_SUBDIVIDE_REQUIRED"}
        return tile.copy(sourceKey=sourceKey,bounds=bounds,lod=lod)
    }
}

/** 与原始数值窗口一致；缺测不转换成海平面。 */
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
    return rectangles.sumOf{it[2].toLong()*it[3]}.coerceAtMost(262_145)
}
