package com.yokuli.runtime.marine.planning

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.operation.union.UnionStrategy
import java.util.IdentityHashMap

/**
 * 一次局部规划构建内的只读几何运算。缓存验证结果与边索引，结束后整体释放。
 * 所有快捷分支均证明与精确叠加相同；接触海岸、孔洞或未知边界仍进行完整拓扑运算。
 */
internal class PassageGeometryOperations(private val check: () -> Unit) {
    private val repaired = IdentityHashMap<Geometry, Geometry>()
    private val prepared = IdentityHashMap<Geometry, PreparedGeometry>()
    private var repairedBytes = 0L
    private var preparedBytes = 0L

    fun repair(value: Geometry): Geometry {
        check()
        return repaired[value] ?: (if (value.isEmpty || value.isValid) value else GeometryFixer.fix(value))
            .also { result ->
                val bytes = (value.numPoints.toLong() + if (result === value) 0 else result.numPoints) * 48 + 128
                if (bytes <= MAX_BYTES) {
                    if (repairedBytes + bytes > MAX_BYTES || repaired.size >= 512) { repaired.clear(); repairedBytes = 0 }
                    repaired[value] = result; repaired[result] = result; repairedBytes += bytes
                }
                check()
            }
    }

    private fun indexed(value: Geometry): PreparedGeometry {
        prepared[value]?.let { return it }
        val result = PreparedGeometryFactory.prepare(value)
        val bytes = value.numPoints.toLong() * 64 + 128
        if (bytes <= MAX_BYTES) {
            if (preparedBytes + bytes > MAX_BYTES || prepared.size >= 128) { prepared.clear(); preparedBytes = 0 }
            prepared[value] = result; preparedBytes += bytes
        }
        return result
    }

    fun intersection(first: Geometry, second: Geometry): Geometry {
        val a = repair(first); val b = repair(second)
        if (a.isEmpty || b.isEmpty || !a.envelopeInternal.intersects(b.envelopeInternal)) return a.factory.createPolygon()
        if (mixed(a) || mixed(b)) return overlay(a, b, OverlayNG.INTERSECTION)
        if (a === b || indexed(b).covers(a)) return a
        if (indexed(a).covers(b)) return b
        return overlay(a, b, OverlayNG.INTERSECTION)
    }

    fun difference(first: Geometry, second: Geometry): Geometry {
        val a = repair(first); val b = repair(second)
        if (a.isEmpty || b.isEmpty || !a.envelopeInternal.intersects(b.envelopeInternal)) return a
        if (mixed(a) || mixed(b)) return overlay(a, b, OverlayNG.DIFFERENCE)
        if (a === b || indexed(b).covers(a)) return a.factory.createPolygon()
        if (indexed(b).disjoint(a)) return a
        return overlay(a, b, OverlayNG.DIFFERENCE)
    }

    fun union(first: Geometry, second: Geometry): Geometry {
        val a = repair(first); val b = repair(second)
        if (a.isEmpty) return b
        if (b.isEmpty || a === b) return a
        return overlay(a, b, OverlayNG.UNION)
    }

    /**
     * 批量并集也必须先按维度分组。UnaryUnionOp 的 UnionStrategy 只控制同维合并；其最终
     * 面/线合并仍调用旧 Geometry.union，不能把混维列表直接交给它。
     */
    fun union(values:List<Geometry>,factory:GeometryFactory):Geometry {
        check()
        if(values.isEmpty())return factory.createPolygon()
        return combine(values.map(::repair),factory).also{check()}
    }

    /** 仅用于覆盖/来源归属掩膜；这些域表达面积，零面积接触碎片不能充当覆盖。 */
    fun area(value:Geometry):Geometry {
        val source=repair(value)
        return combine(groups(source).filter{it.dimension==2},source.factory)
    }

    fun buffer(value: Geometry, distance: Double): Geometry {
        val source = repair(value)
        return repair(source.buffer(distance)).also { check() }
    }

    private companion object { const val MAX_BYTES = 8L * 1024 * 1024 }

    /**
     * 面相交可合法地产生「面 + 边界线/点」。这些接触碎片不是坏几何，OverlayNG 却不能把
     * 混合维度集合再当单一输入。按维度执行真实集合运算，保留线/点障碍；不能 buffer(0)
     * 或只取 Polygon，否则会悄悄消除桥线、礁石、未知区域边缘等通行约束。
     */
    private fun mixed(value:Geometry)=value is GeometryCollection&&value !is Polygonal&&value !is Lineal&&value !is Puntal

    private fun groups(value:Geometry):List<Geometry> {
        if(value.isEmpty)return emptyList()
        if(!mixed(value))return listOf(value)
        val polygons=ArrayList<Polygon>();val lines=ArrayList<LineString>();val points=ArrayList<Point>()
        fun collect(shape:Geometry) {
            check();if(shape.isEmpty)return
            when(shape) {
                is Polygon->polygons+=shape
                is LineString->lines+=shape
                is Point->points+=shape
                is GeometryCollection->for(i in 0 until shape.numGeometries)collect(shape.getGeometryN(i))
                else->error("NAVIGATION_GEOMETRY_TYPE")
            }
        }
        collect(value)
        return buildList {
            if(polygons.isNotEmpty())add(value.factory.createMultiPolygon(polygons.toTypedArray()))
            if(lines.isNotEmpty())add(value.factory.createMultiLineString(lines.toTypedArray()))
            if(points.isNotEmpty())add(value.factory.createMultiPoint(points.toTypedArray()))
        }
    }

    /** 合并同维度后按面积、线、点逐层去重，结果允许真实的混合维度集合。 */
    private fun combine(values:List<Geometry>,factory:GeometryFactory):Geometry {
        val groups=values.flatMap(::groups).groupBy{it.dimension}
        fun merged(dimension:Int):Geometry {
            val shapes=groups[dimension].orEmpty()
            if(shapes.isEmpty())return factory.createGeometryCollection()
            if(shapes.size==1)return shapes.first()
            val union=UnaryUnionOp(shapes,factory)
            union.setUnionFunction(object:UnionStrategy {
                override fun isFloatingPrecision()=true
                override fun union(first:Geometry,second:Geometry):Geometry {check();return repair(OverlayNGRobust.overlay(first,second,OverlayNG.UNION))}
            })
            return repair(union.union()).also{check()}
        }
        val area=merged(2)
        var line=merged(1)
        if(!line.isEmpty&&!area.isEmpty)line=OverlayNGRobust.overlay(line,area,OverlayNG.DIFFERENCE).also{check()}
        var point=merged(0)
        if(!point.isEmpty&&!area.isEmpty)point=OverlayNGRobust.overlay(point,area,OverlayNG.DIFFERENCE).also{check()}
        if(!point.isEmpty&&!line.isEmpty)point=OverlayNGRobust.overlay(point,line,OverlayNG.DIFFERENCE).also{check()}
        return repair(factory.buildGeometry(listOf(area,line,point).filterNot{it.isEmpty}))
    }

    private fun overlay(a: Geometry, b: Geometry, operation: Int): Geometry {
        check()
        if(!mixed(a)&&!mixed(b))return repair(OverlayNGRobust.overlay(a,b,operation)).also{check()}
        val first=groups(a);val second=groups(b)
        return when(operation) {
            OverlayNG.INTERSECTION->combine(first.flatMap{x->second.map{y->
                check();OverlayNGRobust.overlay(x,y,OverlayNG.INTERSECTION)
            }},a.factory)
            OverlayNG.DIFFERENCE->{
                var remaining=first
                for(mask in second) {
                    check()
                    remaining=remaining.flatMap{shape->
                        if(!shape.envelopeInternal.intersects(mask.envelopeInternal))listOf(shape)
                        else groups(OverlayNGRobust.overlay(shape,mask,OverlayNG.DIFFERENCE))
                    }
                }
                combine(remaining,a.factory)
            }
            OverlayNG.UNION->combine(first+second,a.factory)
            else->error("NAVIGATION_OVERLAY_OPERATION")
        }.also{check()}
    }
}
