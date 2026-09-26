package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.chart.ChartDataset

/**
 * 图册查看数据范围只提交相机请求，并沿 Shell 的关联访问进入海图。
 * 当前底图、数据用途和导航任务不变；是否选用数据仍由调用处的明确操作决定。
 */
internal fun openDatasetOnChart(os: OsStore, dataset: ChartDataset) {
    val corners = dataset.cells.filterNot { it.cancelled }.flatMap { cell ->
        cell.bounds.flatMap { bounds ->
            // 分别保留日期变更线两侧的边界，不自行合并成横跨全球的包络。
            listOf(
                GeoPoint(bounds.south, bounds.west),
                GeoPoint(bounds.north, bounds.west),
                GeoPoint(bounds.north, bounds.east),
                GeoPoint(bounds.south, bounds.east),
            )
        }
    }
    // 没有可用范围时仍能进入海图，不发送空取景，也不沿用上一次待处理的范围。
    os.fitRequest = corners.takeIf { it.isNotEmpty() }
    os.openLinked("chart")
}
