package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.chart.ChartDataset
import com.yokuli.marine.shell.rebuild.chart.LibraryDatasetPreview

/**
 * 海图册查看数据范围只提交相机请求，并沿 Shell 的关联访问进入海图。
 * 当前底图、数据用途和导航任务不变；是否选用数据仍由调用处的明确操作决定。
 */
internal fun openDatasetOnChart(os: OsStore, dataset: ChartDataset, cellId:String?=null) {
    val cells=dataset.cells.filterNot {it.cancelled}.filter {cellId==null||it.cellId==cellId}
    val bounds=(cells.flatMap {it.bounds}+dataset.rasters.orEmpty().filter {cellId==null||it.cellId==cellId}.flatMap {it.bounds}).distinct()
    val corners=bounds.flatMap { box ->
        box.split().flatMap { part ->
            // 分别保留日期变更线两侧的边界，不自行合并成横跨全球的包络。
            listOf(
                GeoPoint(part.south, part.west),
                GeoPoint(part.north, part.west),
                GeoPoint(part.north, part.east),
                GeoPoint(part.south, part.east),
            )
        }
    }
    val view=os.maps.view("chart",os.center,os.zoom)
    view.libraryPreview=null;view.libraryPreviewNote=null
    view.selectedChartObjects=emptyList();view.selectedChartCoordinate=null
    view.datasetPreview=LibraryDatasetPreview(dataset.id,dataset.revision,cellId)
    view.datasetPreviewNote=null
    // 预览不改变当前数据选择；用户可在海图预览条中明确选择“设为当前数据”。
    os.fitRequest=corners.takeIf {it.isNotEmpty()}
    os.openLinked("chart")
}
