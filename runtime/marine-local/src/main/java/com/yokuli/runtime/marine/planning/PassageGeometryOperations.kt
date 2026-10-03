package com.yokuli.runtime.marine.planning

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
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
        if (a === b || indexed(b).covers(a)) return a
        if (indexed(a).covers(b)) return b
        return overlay(a, b, OverlayNG.INTERSECTION)
    }

    fun difference(first: Geometry, second: Geometry): Geometry {
        val a = repair(first); val b = repair(second)
        if (a.isEmpty || b.isEmpty || !a.envelopeInternal.intersects(b.envelopeInternal)) return a
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

    fun buffer(value: Geometry, distance: Double): Geometry {
        val source = repair(value)
        return repair(source.buffer(distance)).also { check() }
    }

    private companion object { const val MAX_BYTES = 8L * 1024 * 1024 }

    private fun overlay(a: Geometry, b: Geometry, operation: Int): Geometry {
        check()
        return repair(OverlayNGRobust.overlay(a, b, operation)).also { check() }
    }
}
