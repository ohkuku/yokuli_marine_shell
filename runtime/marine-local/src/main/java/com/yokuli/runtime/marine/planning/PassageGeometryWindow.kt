package com.yokuli.runtime.marine.planning

import com.yokuli.runtime.contract.chart.ChartBounds
import com.yokuli.runtime.contract.chart.ChartGeometry
import com.yokuli.runtime.contract.chart.ChartGeometryKind
import com.yokuli.runtime.contract.chart.ChartGeometryPart
import com.yokuli.runtime.contract.chart.ChartPoint
import com.yokuli.runtime.marine.chart.ChartGeometryQueryIndex
import org.locationtech.jts.geom.*
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust

/**
 * 先在资料的连续经纬度分支裁出本次窗口，再进行昂贵的椭球米制投影。
 * 裁剪保留外环、孔洞和多部件，不简化边界、不补未知面，也不把包围框当作资料覆盖。
 */
internal class PassageGeometryWindow(
    private val bounds:ChartBounds,
    private val projection:PassageProjection,
    private val queries:ChartGeometryQueryIndex,
    private val check:()->Unit,
) {
    private val factory=GeometryFactory()
    private val longitude=projection.origin.longitude
    private fun x(value:Double)=longitude+((value-longitude+540.0)%360.0)-180.0
    private val span=if(bounds.west<=bounds.east)bounds.east-bounds.west else bounds.east+360.0-bounds.west
    private val west=if(span>=359.999999)longitude-180.0 else x(bounds.west)
    private val window=Envelope(west,west+span,bounds.south,bounds.north)
    private val rectangle=factory.toGeometry(window)

    fun geometry(value:ChartGeometry):Geometry {
        check()
        fun point(p:ChartPoint):Coordinate {
            require(p.latitude.isFinite()&&p.longitude.isFinite()&&p.latitude in -90.0..90.0&&p.longitude in -180.0..180.0){"Invalid chart coordinate"}
            return Coordinate(x(p.longitude),p.latitude)
        }
        fun envelope(part:ChartGeometryPart):Envelope {
            val result=Envelope()
            part.points.forEachIndexed{index,p->if(index%256==0)check();result.expandToInclude(point(p))}
            return result
        }
        fun ring(part:ChartGeometryPart):LinearRing {
            val coordinates=part.points.mapIndexed{index,p->if(index%256==0)check();point(p)}.toMutableList()
            if(coordinates.isNotEmpty()&&!coordinates.first().equals2D(coordinates.last()))coordinates+=Coordinate(coordinates.first())
            require(coordinates.size>=4){"Incomplete polygon"}
            return factory.createLinearRing(coordinates.toTypedArray())
        }
        // 全国水域/陆地区域经常完整包住短航段。只有边索引证明窗口内不存在真实边界，
        // 才直接使用精确的矩形交集；碰到海岸/孔洞边界仍走原来的拓扑裁剪。
        val uniform=queries.uniformWindow(value,bounds,check)
        val local=if(uniform==null)queries.window(value,bounds,check)else value
        val source=if(uniform==true)rectangle else if(uniform==false)factory.createPolygon() else when(local.kind) {
            ChartGeometryKind.NONE->return projection.factory.createGeometryCollection()
            ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT->factory.createMultiPointFromCoords(local.parts.flatMap{it.points}.mapIndexedNotNull{index,p->
                if(index%256==0)check();point(p).takeIf(window::contains)
            }.toTypedArray())
            ChartGeometryKind.LINE->factory.createMultiLineString(local.parts.filter{it.points.size>=2&&envelope(it).intersects(window)}.map{part->
                factory.createLineString(part.points.mapIndexed{index,p->if(index%256==0)check();point(p)}.toTypedArray())
            }.toTypedArray())
            ChartGeometryKind.POLYGON->{
                // 远方岛屿的独立外环无需进入 JTS 或米制投影；覆盖窗口的大面则完整保留到精确裁剪。
                val shells=local.parts.filter{!it.hole&&envelope(it).intersects(window)}.map{factory.createPolygon(ring(it))}
                if(shells.isEmpty())return projection.factory.createPolygon()
                val holes=local.parts.filter{it.hole&&envelope(it).intersects(window)}.map(::ring)
                val assigned=holes.groupBy{hole->
                    check();val area=factory.createPolygon(hole)
                    shells.filter{it.envelopeInternal.covers(area.envelopeInternal)&&it.covers(area)}.minByOrNull{it.area}
                        ?:error("Unattached polygon hole")
                }
                factory.createMultiPolygon(shells.map{shell->
                    factory.createPolygon(shell.exteriorRing as LinearRing,assigned[shell].orEmpty().toTypedArray())
                }.toTypedArray())
            }
        }
        if(source.isEmpty)return projection.factory.createGeometryCollection()
        check()
        val clipped=if(window.covers(source.envelopeInternal))source else OverlayNGRobust.overlay(source,rectangle,OverlayNG.INTERSECTION)
        check()
        if(clipped.isEmpty)return projection.factory.createGeometryCollection()
        require(clipped.isValid){"Invalid local chart geometry"}
        val result=clipped.copy()
        result.apply(object:CoordinateSequenceFilter {
            override fun filter(sequence:CoordinateSequence,index:Int) {
                if(index%128==0)check()
                val unwrapped=sequence.getX(index)
                val canonical=((unwrapped+180.0)%360.0+360.0)%360.0-180.0
                val projected=projection.xy(ChartPoint(sequence.getY(index),canonical))
                sequence.setOrdinate(index,0,projected.x);sequence.setOrdinate(index,1,projected.y)
            }
            override fun isDone()=false
            override fun isGeometryChanged()=true
        })
        check();require(result.isValid){"Invalid projected chart geometry"}
        return result
    }
}
