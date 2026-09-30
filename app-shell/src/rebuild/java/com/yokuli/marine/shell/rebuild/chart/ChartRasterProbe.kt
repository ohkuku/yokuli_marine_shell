package com.yokuli.marine.shell.rebuild.chart

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
    val info=withTimeout(8_000) {service.inspectPosition(selectedIds,ChartPoint(point.lat,point.lon),2.0)}
    info.raster?.let {ChartRasterProbe(it.grid,info.datasetName,it.elevationMeters)}
}
