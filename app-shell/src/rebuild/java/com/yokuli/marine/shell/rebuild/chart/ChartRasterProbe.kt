package com.yokuli.marine.shell.rebuild.chart

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 点选详情的临时读投影。资料、优先级与像元值均来自同一不可变快照，不另建海深状态。 */
internal data class ChartRasterProbe(val grid:RasterBathymetryGrid,val datasetName:String,val elevationMeters:Float?)

internal fun hasRasterAt(datasets:List<ChartDataset>,selectedIds:List<String>,point:GeoPoint):Boolean =
    datasets.any {it.id in selectedIds&&it.rasters.orEmpty().any {grid->grid.pixelAt(ChartPoint(point.lat,point.lon))!=null}}

/**
 * 当前单选数据文件夹 → 文件优先级 → 原图幅尺度。选定高层像元为 no-data 也占据该位置，不能偷用低层补值。
 * 高层 ENC 的真实覆盖已承担此位置时，保持现有 ENC 对象详情，不把底层粗网格写成另一个水深。
 */
internal suspend fun probeChartRaster(service:ChartDataService,selectedIds:List<String>,point:GeoPoint):ChartRasterProbe? = withContext(Dispatchers.Default) {
    if(selectedIds.isEmpty()||!point.valid())return@withContext null
    val snapshot=service.acquireSnapshot(selectedIds)
    try {
        require(snapshot.missingDatasetIds.isEmpty()) {"CHART_SELECTED_DATA_MISSING"}
        val p=ChartPoint(point.lat,point.lon)
        data class Candidate(val folder:Int,val dataset:ChartDataset,val cell:ChartCellRevision)
        val candidates=snapshot.datasets.flatMap {dataset->dataset.cells.filterNot {it.cancelled}.map {Candidate(selectedIds.indexOf(dataset.id),dataset,it)}}
            .sortedWith(compareBy<Candidate>{it.folder}.thenBy{it.cell.priority}.thenBy{it.cell.compilationScale?:Int.MAX_VALUE}.thenByDescending{it.cell.edition}.thenByDescending{it.cell.update}.thenBy{it.cell.cellId})
        var selected:Pair<ChartDataset,RasterBathymetryGrid>?=null
        for(candidate in candidates) {
            currentCoroutineContext().ensureActive()
            val grid=candidate.dataset.rasters.orEmpty().filter{it.cellId==candidate.cell.cellId}.sortedBy{it.id}.firstOrNull{it.pixelAt(p)!=null}
            if(grid!=null){selected=candidate.dataset to grid;break}
            fun includes(coverage:CoverageEvidence)=coverage.geometry.parts.any{!it.hole&&containsRing(it.points,p)}&&!coverage.geometry.parts.any{it.hole&&containsRing(it.points,p)}
            if(candidate.cell.coverage.any{it.covered&&includes(it)}&&!candidate.cell.coverage.any{!it.covered&&includes(it)})return@withContext null
        }
        val (dataset,grid)=selected?:return@withContext null
        require(dataset.offlineReadable) {"CHART_SELECTED_DATA_MISSING"}
        val pixel=grid.pixelAt(p)?:return@withContext null
        // 极小经纬框只读相邻少量像元；绝不以当前屏幕分辨率采样或读取整幅全球网格。
        fun longitude(v:Double)=((v+180.0)%360.0+360.0)%360.0-180.0
        val epsilon=1e-8
        val bounds=ChartBounds(longitude(point.lon-epsilon),(point.lat-epsilon).coerceAtLeast(-90.0),longitude(point.lon+epsilon),(point.lat+epsilon).coerceAtMost(90.0))
        val windows=service.rasterWindows(snapshot.id,bounds,maxCells=4096)
        val window=windows.firstOrNull {entry->entry.grid.id==grid.id&&pixel.first in entry.window.column until entry.window.column+entry.window.width&&pixel.second in entry.window.row until entry.window.row+entry.window.height}
            ?:error("GEBCO_POINT_WINDOW_MISSING")
        val value=window.window.elevationAt(pixel.first-window.window.column,pixel.second-window.window.row)
        return@withContext ChartRasterProbe(grid,dataset.name,value)
    }finally{withContext(NonCancellable){service.releaseSnapshot(snapshot.id)}}
}
