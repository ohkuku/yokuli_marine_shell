package com.yokuli.runtime.marine.planning

import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.locationtech.jts.geom.Geometry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/** 仅在本次有界搜索内保存连续行段；不是永久对象索引，也不把每个像元包装成海图对象。 */
internal enum class RasterPassageKind { UNKNOWN, LAND, SHALLOW, DEEP }
internal data class RasterPassageArea(
    val geometry: Geometry,
    val grid: RasterBathymetryGrid,
    val kind: RasterPassageKind,
    val lowerDepthMeters: Double?,
    val upperDepthMeters: Double?,
    val edgeAllowanceMeters: Double,
) {
    val evidence: DepthEvidence? get() = lowerDepthMeters?.let {
        DepthEvidence(DepthEvidenceKind.INTERVAL, it, upperDepthMeters, datum=grid.verticalReference,
            quality="REFERENCE_ONLY_GEBCO; ${grid.pixelWidthDegrees * 3600} arc seconds; no interpolation")
    }
}
internal data class PassageReferenceArea(val geometry:Geometry,val cellId:String,val message:String)
internal data class RasterPassageGeometry(val footprint:Geometry,val areas:List<RasterPassageArea>)

/** 合并同一行类别连续段，并纵向延长完全同宽的段；复杂窗口明确超预算，不降低分辨率抹掉浅区。 */
internal suspend fun rasterPassageGeometry(
    item:ChartRasterWindow, projection:PassageProjection, requiredDepthMeters:Double?,
):RasterPassageGeometry {
    val job=currentCoroutineContext();val grid=item.grid;val window=item.window
    require(window.elevationMeters.size<=262_144) { "栅格区域过大，请添加中间航点 / Raster area is too large; add intermediate waypoints" }
    data class Key(val start:Int,val end:Int,val kind:RasterPassageKind)
    data class Rectangle(val key:Key,val top:Int,var bottom:Int,var minimum:Double?,var maximum:Double?)
    var active=mutableMapOf<Key,Rectangle>();val finished=ArrayList<Rectangle>()
    fun classify(value:Float):RasterPassageKind=when {
        !value.isFinite()->RasterPassageKind.UNKNOWN
        value>=0f->RasterPassageKind.LAND
        requiredDepthMeters==null||-value.toDouble()<requiredDepthMeters->RasterPassageKind.SHALLOW
        else->RasterPassageKind.DEEP
    }
    fun limit(){require(finished.size+active.size<=16_384){"栅格边界过于复杂，请缩短航段 / Raster boundaries exceed the local budget; shorten the leg"}}
    for(y in 0 until window.height){
        job.ensureActive();val next=mutableMapOf<Key,Rectangle>();var x=0
        while(x<window.width){
            if(x%128==0)job.ensureActive()
            val start=x;val kind=classify(window.elevationMeters[y*window.width+x]);var minimum:Double?=null;var maximum:Double?=null
            while(x<window.width&&classify(window.elevationMeters[y*window.width+x])==kind){
                if(x%128==0)job.ensureActive()
                if(kind==RasterPassageKind.SHALLOW||kind==RasterPassageKind.DEEP){
                    val depth=-window.elevationMeters[y*window.width+x].toDouble()
                    minimum=minimum?.coerceAtMost(depth)?:depth;maximum=maximum?.coerceAtLeast(depth)?:depth
                };x++
            }
            val key=Key(start,x,kind);val previous=active.remove(key)
            next[key]=if(previous!=null){
                previous.bottom=y+1
                minimum?.let{previous.minimum=previous.minimum?.coerceAtMost(it)?:it}
                maximum?.let{previous.maximum=previous.maximum?.coerceAtLeast(it)?:it}
                previous
            }else Rectangle(key,y,y+1,minimum,maximum)
        }
        finished+=active.values;active=next;limit()
    }
    finished+=active.values;active.clear();limit()
    // 分文件仿射原点的浮点尾差不能把同一格边界分成纳米级裂缝；约 0.01 mm 规范仅用于坐标计算。
    fun registered(value:Double)=round(value*10_000_000_000.0)/10_000_000_000.0
    fun gridPoint(column:Double,row:Double)=ChartPoint(registered(grid.northEdge-row*grid.pixelHeightDegrees),
        registered(((grid.westEdge+column*grid.pixelWidthDegrees+180.0)%360.0+360.0)%360.0-180.0))
    fun point(column:Int,row:Int)=gridPoint(column.toDouble(),row.toDouble())
    var vertices=0
    fun rectangle(left:Int,top:Int,right:Int,bottom:Int):Geometry {
        // 沿原像元的公共边界取点。邻接长短行段必须共享完全相同的折线，避免投影长弦留下假裂缝。
        val corners=listOf(left to top,right to top,right to bottom,left to bottom,left to top)
        val boundary=ArrayList<org.locationtech.jts.geom.Coordinate>()
        corners.zipWithNext().forEach { (a,b) ->
            val steps=max(abs(b.first-a.first),abs(b.second-a.second))
            require(steps in 1..100_000&&vertices+steps<=1_000_000) { "栅格几何超出局部预算 / Raster geometry exceeds the local budget" }
            val dx=(b.first-a.first).compareTo(0);val dy=(b.second-a.second).compareTo(0)
            repeat(steps){ index ->
                if(index%128==0)job.ensureActive()
                boundary+=projection.xy(point(a.first+dx*index,a.second+dy*index))
            };vertices+=steps
        }
        boundary+=boundary.first()
        return projection.factory.createPolygon(boundary.toTypedArray()).also {
            require(it.isValid) { "栅格配准边界无效 / Invalid raster registration geometry" }
        }
    }
    val footprint=rectangle(window.column,window.row,window.column+window.width,window.row+window.height)
    // 在本窗口北/中/南三处取较大像元对角，给岸线/浅区/未知边界留至少半个像元的空间余量。
    val allowance=listOf(window.row,window.row+window.height/2,(window.row+window.height-1).coerceAtLeast(window.row)).maxOf { row ->
        distance(point(window.column,row),point(window.column+1,row+1))*.5
    }
    require(allowance.isFinite()&&allowance>0) { "栅格分辨率无效 / Invalid raster resolution" }
    val areas=finished.mapIndexed { index, r ->
        if(index%32==0)job.ensureActive()
        RasterPassageArea(rectangle(window.column+r.key.start,window.row+r.top,window.column+r.key.end,window.row+r.bottom),
            grid,r.key.kind,r.minimum,r.maximum,max(1.0,allowance))
    }
    return RasterPassageGeometry(footprint,areas)
}
