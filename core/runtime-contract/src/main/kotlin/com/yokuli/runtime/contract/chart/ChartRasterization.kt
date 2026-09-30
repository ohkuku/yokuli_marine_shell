package com.yokuli.runtime.contract.chart

import kotlin.math.*

/** 一次性离线海图生成参数；输出只用于显示，不能替代所选数据的规划/指点查询。 */
data class ChartRasterization(
    val bounds:ChartBounds,
    val minZoom:Int=8,
    val maxZoom:Int=14,
    val shallowDepthMeters:Double=2.0,
    val safetyDepthMeters:Double=5.0,
    val deepDepthMeters:Double=30.0,
    val night:Boolean=false,
) {
    fun tileCount():Long {
        if(!bounds.valid||minZoom !in 1..18||maxZoom !in minZoom..18||bounds.south< -85.05112878||bounds.north>85.05112878)return Long.MAX_VALUE
        return (minZoom..maxZoom).sumOf {z->
            val n=1 shl z
            fun x(lon:Double)=floor((lon+180.0)/360*n).toInt().coerceIn(0,n-1)
            fun y(lat:Double)=floor((1.0-asinh(tan(Math.toRadians(lat)))/Math.PI)/2*n).toInt().coerceIn(0,n-1)
            val spans=bounds.split().map {b->x(b.west)..x(b.east)}
            val overlap=if(spans.size==2)(minOf(spans[0].last,spans[1].last)-maxOf(spans[0].first,spans[1].first)+1).coerceAtLeast(0)else 0
            val columns=spans.sumOf {it.last.toLong()-it.first+1}-overlap
            columns*(y(bounds.south)-y(bounds.north)+1)
        }
    }
    companion object {const val MAX_TILES=4096L}
}
