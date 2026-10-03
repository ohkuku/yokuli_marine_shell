package com.yokuli.marine.shell.rebuild.chart

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.*
import kotlin.math.*

/** 短暂的准星读投影：一次快照内读取矢量与原始网格，绝不作为船舶传感器水深。 */
internal data class ChartCursorProbe(
    val point:GeoPoint,
    val datasetName:String,
    val features:List<NauticalFeature>,
    val raster:ChartRasterProbe?,
    val incomplete:Boolean,
    val distances:Map<String,Double> = emptyMap(),
    /** 面归属已完整确认时，可先展示它，不必等待附近测深点或设施细节。 */
    val areaResolved:Boolean = !incomplete,
)


internal suspend fun probeChartCursor(service:ChartDataService,selectedIds:List<String>,point:GeoPoint,zoom:Double,expectedRevision:Long?=null):ChartCursorProbe = withContext(Dispatchers.Default) {
    require(selectedIds.size==1&&point.valid()) {"CHART_SELECTED_DATA_MISSING"}
    // 租约、完整几何和文件优先级全留在 Core；准星不再下载整幅海岸并重复做面裁剪。
    val info=service.inspectPosition(selectedIds,ChartPoint(point.lat,point.lon),chartCursorRadius(point,zoom))
    require(info.datasetId==selectedIds.single()&&(expectedRevision==null||info.datasetRevision==expectedRevision)) {
        "CHART_CURSOR_SOURCE_CHANGED"
    }
    ChartCursorProbe(point,info.datasetName,info.hits.map {it.feature},
        info.raster?.let {ChartRasterProbe(it.grid,info.datasetName,it.elevationMeters)},info.incomplete,
        info.hits.associate {it.feature.id to it.distanceMeters})
}

/** 半径按整米稳定；镜头浮点误差不使一次已经完成的查询重新进入加载状态。 */
internal fun chartCursorRadius(point:GeoPoint,zoom:Double):Double =
    ceil(24*156543.03392*cos(Math.toRadians(point.lat.coerceIn(-85.0,85.0)))/2.0.pow(zoom)).coerceIn(2.0,150.0)

internal fun ChartCursorProbe.distance(feature:NauticalFeature):Double=distances[feature.id] ?: chartFeatureDistance(feature,point)

/** 只用于“附近”说明；面内位置为零。球面短距离经度解缠支持日期变更线。 */
internal fun chartFeatureDistance(feature:NauticalFeature,point:GeoPoint):Double {
    val p=ChartPoint(point.lat,point.lon)
    if(feature.geometry.kind==ChartGeometryKind.POLYGON) {
        var coverage=0
        for(part in feature.geometry.parts)if(containsRing(part.points,p))coverage+=if(part.hole)-1 else 1
        if(coverage>0)return 0.0
    }
    fun xy(p:ChartPoint)=Pair(((p.longitude-point.lon+540)%360-180)*111_320*cos(Math.toRadians(point.lat)),(p.latitude-point.lat)*111_320)
    return feature.geometry.parts.minOfOrNull {part->
        if(feature.geometry.kind in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT))part.points.minOfOrNull {val(x,y)=xy(it);hypot(x,y)} ?: Double.POSITIVE_INFINITY
        else part.points.zipWithNext().minOfOrNull {(a,b)->
            val(x,y)=xy(a);val(u,v)=xy(b);val dx=u-x;val dy=v-y
            val t=(-(x*dx+y*dy)/(dx*dx+dy*dy).coerceAtLeast(.000001)).coerceIn(0.0,1.0)
            hypot(x+t*dx,y+t*dy)
        } ?: Double.POSITIVE_INFINITY
    } ?: Double.POSITIVE_INFINITY
}
