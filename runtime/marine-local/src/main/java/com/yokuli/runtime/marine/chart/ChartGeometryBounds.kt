package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*

/** 包围盒用于召回，不代替精确几何分析；最小经度弧在日期变更线处分成两个索引矩形。 */
internal fun geometryBounds(geometry:ChartGeometry):List<ChartBounds> {
    val points=geometry.parts.flatMap {it.points};if(points.isEmpty())return emptyList()
    val longitudes=points.map {it.longitude}.distinct().sorted();val south=points.minOf {it.latitude};val north=points.maxOf {it.latitude}
    if(longitudes.size==1)return listOf(ChartBounds(longitudes[0],south,longitudes[0],north))
    var largest=-1.0;var gap=0
    for(i in longitudes.indices) {val next=if(i==longitudes.lastIndex)longitudes[0]+360 else longitudes[i+1];val size=next-longitudes[i];if(size>largest){largest=size;gap=i}}
    val west=longitudes[(gap+1)%longitudes.size];val east=longitudes[gap]
    return ChartBounds(west,south,east,north).split()
}
