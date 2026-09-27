package com.yokuli.runtime.contract.chart

import kotlin.math.floor

/** 数值高程栅格的空间说明。正值为陆地高程、负值为海底高程；不等同 ENC 海图基准水深。 */
data class RasterBathymetryGrid(
    val id:String,
    val datasetId:String,
    val cellId:String,
    val width:Int,
    val height:Int,
    /** 左上外边界；查询以像元中心定位，内部经度允许 180～360 以保留跨日期线的连续索引。 */
    val westEdge:Double,
    val northEdge:Double,
    val pixelWidthDegrees:Double,
    val pixelHeightDegrees:Double,
    val noData:Double?=null,
    val product:String,
    val registration:String,
    val verticalReference:String="GEBCO elevation metres; not chart datum",
    val sourceName:String,
    val bounds:List<ChartBounds>,
) {
    /** 最近像元的原始值所在格，不把低分辨率像元插值成更精细的测深证据。 */
    fun pixelAt(point:ChartPoint):Pair<Int,Int>? {
        if(!point.latitude.isFinite()||!point.longitude.isFinite()||point.latitude !in -90.0..90.0)return null
        val x=westEdge+((point.longitude-westEdge)%360.0+360.0)%360.0
        val column=floor((x-westEdge)/pixelWidthDegrees).toInt()
        val row=floor((northEdge-point.latitude)/pixelHeightDegrees).toInt()
        return if(column in 0 until width&&row in 0 until height)column to row else null
    }
    fun centre(column:Int,row:Int)=ChartPoint(northEdge-(row+.5)*pixelHeightDegrees,normalize(westEdge+(column+.5)*pixelWidthDegrees))
    private fun normalize(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
}

/** 有界窗口：按从北向南、从西向东的行序排列；NaN 明确表示无数据，不能解释为海平面。 */
data class RasterBathymetryWindow(
    val gridId:String,val column:Int,val row:Int,val width:Int,val height:Int,val elevationMeters:FloatArray,
) {
    init { require(width>0&&height>0&&elevationMeters.size.toLong()==width.toLong()*height) }
    fun elevationAt(x:Int,y:Int):Float? {
        require(x in 0 until width&&y in 0 until height)
        return elevationMeters[y*width+x].takeIf(Float::isFinite)
    }
}
