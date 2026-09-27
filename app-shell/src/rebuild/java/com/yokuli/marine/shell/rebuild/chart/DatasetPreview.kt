package com.yokuli.marine.shell.rebuild.chart

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.marine.chart.ChartDrawingClipper
import com.yokuli.shell.contract.MarineUnitPreferences
import kotlinx.coroutines.*
import kotlin.math.*

internal data class DatasetPreviewDrawing(
    val scene: MapScene = MapScene(),
    val note: String? = null,
)

/**
 * 图册的数据预览只读取同一不可变快照：矢量对象按现有呈现规则画，数值栅格按当前视口有界抽样。
 * 抽样仅帮助用户理解“有什么数据、覆盖到哪”，规划仍读取原像元和完整对象，绝不读取预览色块。
 */
internal suspend fun datasetPreviewDrawing(
    service: ChartDataService,
    preview: LibraryDatasetPreview,
    dataset: ChartDataset,
    center: GeoPoint,
    zoom: Double,
    units: MarineUnitPreferences,
    portrayal: ChartPortrayalPreferences,
    chinese: Boolean,
): DatasetPreviewDrawing {
    val snapshot=service.acquireSnapshot(listOf(dataset.id))
    try {
        require(snapshot.missingDatasetIds.isEmpty()) {"CHART_SELECTED_DATA_MISSING"}
        val bounds=previewBounds(center,zoom)
        val targetCells=dataset.cells.filterNot {it.cancelled}.filter {preview.cellId==null||it.cellId==preview.cellId}
        val cellIds=targetCells.mapTo(linkedSetOf()){it.cellId}
        val hasVectors=targetCells.any{it.featureCount>0}
        val features=ArrayList<NauticalFeature>()
        var clipped=false
        if(hasVectors) {
            var after:String?=null
            do {
                currentCoroutineContext().ensureActive()
                val page=service.query(snapshot.id,bounds,2_000,after)
                features+=page.features.filter {it.cellId in cellIds}
                clipped=clipped||page.truncated
                if(features.size>=8_000){clipped=true;break}
                after=page.nextAfterId.takeIf{page.hasMore}
            }while(after!=null)
        }
        val vectorScene=if(features.isEmpty())MapScene()else withContext(Dispatchers.Default) {
            val visible=if(preview.cellId==null)ChartDrawingClipper.compose(snapshot,features,bounds).features else features
            structuredScene(visible,center,zoom,units,portrayal).scene
        }

        val palette=ChartPalette.of(portrayal.colorMode)
        val outlines=ArrayList<MapLine>()
        val outlineBounds=(targetCells.flatMap{it.bounds}+dataset.rasters.orEmpty().filter{it.cellId in cellIds}.flatMap{it.bounds}).distinct()
        outlineBounds.forEachIndexed {index,box->
            box.split().forEachIndexed {partIndex,part->
                outlines+=MapLine(
                    "dataset-preview:coverage:$index:$partIndex",
                    listOf(
                        GeoPoint(part.south,part.west),GeoPoint(part.north,part.west),
                        GeoPoint(part.north,part.east),GeoPoint(part.south,part.east),
                        GeoPoint(part.south,part.west),
                    ),tint(palette.contour,220),2f,true,casing=false
                )
            }
        }

        val grids=dataset.rasters.orEmpty().filter{it.cellId in cellIds}
        val estimate=grids.sumOf{estimatedCells(it,bounds).coerceAtMost(65_537)}
        var rasterAreas=emptyList<MapArea>()
        var detailed=false
        if(grids.isNotEmpty()&&estimate in 1..65_536) {
            val windows=service.rasterWindows(snapshot.id,bounds,65_536).filter{it.grid.datasetId==dataset.id&&it.grid.cellId in cellIds}
            rasterAreas=previewRasterAreas(windows,portrayal,palette)
            detailed=windows.isNotEmpty()
        }
        val note=buildList {
            if(grids.isNotEmpty())add(when {
                detailed->if(chinese)"数值高程按当前视口抽样显示：陆地、浅水、较深水与未知区。色块只用于预览，规划仍读取原始像元。"
                    else "Numeric elevation is sampled for this viewport: land, shallow, deeper water and unknown cells. Colours are preview-only; planning still reads original cells."
                else->if(chinese)"已显示数值网格覆盖边界。当前范围像元过多；放大地图可查看高程分级。"
                    else "Numeric-grid coverage is shown. This view contains too many cells; zoom in to see elevation classes."
            })
            if(hasVectors)add(if(chinese)"矢量对象按“数据→矢量数据显示”的当前规则预览。" else "Vector objects use the current Data → Vector display settings.")
            if(clipped)add(if(chinese)"当前范围对象过多，预览已截断；放大查看完整细节。" else "Too many objects in this view; preview is clipped. Zoom in for full detail.")
            if(preview.cellId!=null)add(if(chinese)"仅预览所选文件/单元。" else "Previewing only the selected file/cell.")
        }.joinToString(" ")
        return DatasetPreviewDrawing(
            MapScene(
                points=vectorScene.points,
                lines=outlines+vectorScene.lines,
                areas=rasterAreas+vectorScene.areas,
            ),
            note.takeIf{it.isNotBlank()},
        )
    } finally {
        withContext(NonCancellable){runCatching{withTimeout(1500){service.releaseSnapshot(snapshot.id)}}}
    }
}

private fun previewBounds(center:GeoPoint,zoom:Double):ChartBounds {
    val span=(720.0/2.0.pow(zoom)).coerceIn(.005,180.0)
    val latSpan=span*cos(Math.toRadians(center.lat)).coerceAtLeast(.05)
    fun norm(v:Double)=((v+540)%360)-180
    return ChartBounds(
        if(span>=180)-180.0 else norm(center.lon-span),
        (center.lat-latSpan).coerceAtLeast(-90.0),
        if(span>=180)180.0 else norm(center.lon+span),
        (center.lat+latSpan).coerceAtMost(90.0),
    )
}

private fun estimatedCells(grid:RasterBathymetryGrid,query:ChartBounds):Long {
    var total=0L
    for(a in grid.bounds.flatMap{it.split()})for(b in query.split()) {
        val west=max(a.west,b.west);val east=min(a.east,b.east)
        val south=max(a.south,b.south);val north=min(a.north,b.north)
        if(east<=west||north<=south)continue
        val cols=ceil((east-west)/grid.pixelWidthDegrees).toLong().coerceAtLeast(1)
        val rows=ceil((north-south)/grid.pixelHeightDegrees).toLong().coerceAtLeast(1)
        total=(total+cols*rows).coerceAtMost(65_537)
    }
    return total
}

private suspend fun previewRasterAreas(
    windows:List<ChartRasterWindow>,
    portrayal:ChartPortrayalPreferences,
    palette:ChartPalette,
):List<MapArea> {
    val count=windows.sumOf{it.window.width.toLong()*it.window.height}.coerceAtLeast(1)
    val stride=ceil(sqrt(count/1400.0)).toInt().coerceAtLeast(1)
    val areas=ArrayList<MapArea>()
    fun norm(v:Double)=((v+180.0)%360.0+360.0)%360.0-180.0
    windows.forEach {entry->
        val grid=entry.grid;val window=entry.window
        var y=0
        while(y<window.height) {
            currentCoroutineContext().ensureActive()
            var x=0
            while(x<window.width) {
                val blockW=min(stride,window.width-x);val blockH=min(stride,window.height-y)
                var finite=0;var missing=0;var land=false;var shallowest=Double.POSITIVE_INFINITY
                for(dy in 0 until blockH)for(dx in 0 until blockW) {
                    val value=window.elevationAt(x+dx,y+dy)
                    if(value==null){missing++;continue}
                    finite++
                    if(value>=0f)land=true else shallowest=min(shallowest,-value.toDouble())
                }
                val base=when {
                    finite==0||missing>0->palette.unknown
                    land->palette.land
                    shallowest<portrayal.shallowDepthMeters->palette.shallow
                    shallowest<portrayal.safetyDepthMeters->palette.unsafe
                    shallowest<portrayal.deepDepthMeters->palette.medium
                    else->palette.deep
                }
                val globalColumn=window.column+x;val globalRow=window.row+y
                val west=norm(grid.westEdge+globalColumn*grid.pixelWidthDegrees)
                val east=norm(grid.westEdge+(globalColumn+blockW)*grid.pixelWidthDegrees)
                val north=grid.northEdge-globalRow*grid.pixelHeightDegrees
                val south=grid.northEdge-(globalRow+blockH)*grid.pixelHeightDegrees
                areas+=MapArea(
                    "dataset-preview:raster:${grid.id}:$globalColumn:$globalRow",
                    listOf(GeoPoint(south,west),GeoPoint(north,west),GeoPoint(north,east),GeoPoint(south,east)),
                    tint(base,112),
                )
                x+=blockW
            }
            y+=min(stride,window.height-y)
        }
    }
    return areas
}

private fun tint(color:Long,alpha:Int)=(color and 0x00FFFFFFL) or (alpha.coerceIn(0,255).toLong() shl 24)
