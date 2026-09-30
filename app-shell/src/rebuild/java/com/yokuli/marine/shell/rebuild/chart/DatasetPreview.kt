package com.yokuli.marine.shell.rebuild.chart

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.ChartDataset

internal data class DatasetPreviewDrawing(val scene:MapScene=MapScene(),val note:String?=null)

/** 只展示目录声明的覆盖框；不查对象、不采样网格，更不会把数据实时绘成海图。 */
internal fun datasetCoverageDrawing(preview:LibraryDatasetPreview,dataset:ChartDataset,chinese:Boolean):DatasetPreviewDrawing {
    val cells=dataset.cells.filterNot {it.cancelled}.filter {preview.cellId==null||it.cellId==preview.cellId}
    val bounds=(cells.flatMap {it.bounds}+dataset.rasters.orEmpty().filter {preview.cellId==null||it.cellId==preview.cellId}.flatMap {it.bounds}).distinct()
    val outlines=bounds.flatMapIndexed {index,box->box.split().mapIndexed {partIndex,part->
        MapLine("dataset-coverage:$index:$partIndex",listOf(
            GeoPoint(part.south,part.west),GeoPoint(part.north,part.west),
            GeoPoint(part.north,part.east),GeoPoint(part.south,part.east),GeoPoint(part.south,part.west)
        ),0xFF637D86,2f,true,casing=false)
    }}
    return DatasetPreviewDrawing(MapScene(lines=outlines),
        if(chinese)"资料覆盖范围；范围内仍可能有缺测。选用后在准星下查看深度与设施。"
        else "Data extent; gaps may exist inside it. Use the collection to inspect depths and facilities at the cursor.")
}
