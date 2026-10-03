package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*

/** 编译器只读取冻结事实；手机 Core 和桌面制包共用，不能用此端口修改来源或任务。 */
internal interface ChartFactReader {
    suspend fun query(snapshotId:String,bounds:ChartBounds,limit:Int=2000,afterId:String?=null):ChartFeaturePage
    suspend fun querySpatial(snapshotId:String,bounds:ChartBounds,filter:ChartSpatialFilter,limit:Int=2000,afterId:String?=null):ChartFeaturePage
    suspend fun rasterWindows(snapshotId:String,bounds:ChartBounds,maxCells:Int=262_144):List<ChartRasterWindow>
}
internal class ServiceChartFactReader(private val service:ChartDataService):ChartFactReader {
    override suspend fun query(snapshotId:String,bounds:ChartBounds,limit:Int,afterId:String?)=service.query(snapshotId,bounds,limit,afterId)
    override suspend fun querySpatial(snapshotId:String,bounds:ChartBounds,filter:ChartSpatialFilter,limit:Int,afterId:String?)=service.querySpatial(snapshotId,bounds,filter,limit,afterId)
    override suspend fun rasterWindows(snapshotId:String,bounds:ChartBounds,maxCells:Int)=service.rasterWindows(snapshotId,bounds,maxCells)
}
