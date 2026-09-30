package com.yokuli.marine.shell.rebuild.chart

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.marine.chart.ChartDrawingClipper
import kotlinx.coroutines.*
import kotlin.math.*

/** 短暂的准星读投影：一次快照内读取矢量与原始网格，绝不作为船舶传感器水深。 */
internal data class ChartCursorProbe(
    val point:GeoPoint,
    val datasetName:String,
    val features:List<NauticalFeature>,
    val raster:ChartRasterProbe?,
    val incomplete:Boolean,
)

internal suspend fun probeChartCursor(service:ChartDataService,selectedIds:List<String>,point:GeoPoint,zoom:Double):ChartCursorProbe = withContext(Dispatchers.Default) {
    require(selectedIds.size==1&&point.valid()) {"CHART_SELECTED_DATA_MISSING"}
    val snapshot=service.acquireSnapshot(selectedIds)
    try {
        require(snapshot.missingDatasetIds.isEmpty()&&snapshot.datasets.size==1&&snapshot.datasets.all {it.offlineReadable}) {"CHART_SELECTED_DATA_MISSING"}
        // 邻近设施最多搜索 150 米，缩到全球视图时也不能把数十公里外的测深说成“此处水深”。
        val radius=(24*156543.03392*cos(Math.toRadians(point.lat.coerceIn(-85.0,85.0)))/2.0.pow(zoom)).coerceIn(2.0,150.0)
        val dy=radius/111_320.0
        val dx=dy/cos(Math.toRadians(point.lat)).coerceAtLeast(.001)
        fun longitude(v:Double)=((v+180.0)%360.0+360.0)%360.0-180.0
        val bounds=ChartBounds(longitude(point.lon-dx),(point.lat-dy).coerceAtLeast(-90.0),longitude(point.lon+dx),(point.lat+dy).coerceAtMost(90.0))
        val features=ArrayList<NauticalFeature>()
        var cursor:String?=null
        var incomplete=false
        if(snapshot.cells.any {it.featureCount>0})do {
            ensureActive()
            val page=service.query(snapshot.id,bounds,128,cursor)
            features+=page.features
            incomplete=page.hasMore||page.truncated
            cursor=page.nextAfterId
        }while(incomplete&&cursor!=null&&features.size<512)
        val drawing=ChartDrawingClipper.compose(snapshot,features,bounds)
        val hits=chartObjectsAt(drawing.features,point,zoom,radius,limit=512)
        val raster=probeChartRaster(service,snapshot,selectedIds,point)
        ChartCursorProbe(point,snapshot.datasets.single().name,hits,raster,incomplete||drawing.incompleteGeometry)
    }finally {
        withContext(NonCancellable) {runCatching {withTimeout(1500) {service.releaseSnapshot(snapshot.id)}}}
    }
}

/** 只用于“附近”说明；面内位置为零。球面短距离经度解缠支持日期变更线。 */
internal fun chartFeatureDistance(feature:NauticalFeature,point:GeoPoint):Double {
    val p=ChartPoint(point.lat,point.lon)
    if(feature.geometry.kind==ChartGeometryKind.POLYGON&&feature.geometry.parts.any {!it.hole&&containsRing(it.points,p)}&&feature.geometry.parts.none {it.hole&&containsRing(it.points,p)})return 0.0
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
