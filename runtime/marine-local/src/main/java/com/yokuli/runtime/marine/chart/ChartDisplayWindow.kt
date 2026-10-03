package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust

/**
 * 显示窗口只裁真实几何，保留孔洞、深度、属性与原对象 ID。它不参与分析，不将外接矩形
 * 当作覆盖，不简化海岸，也不填补裁剪失败的资料；失败由调用方呈现为可重试的缺失。
 */
internal class ChartDisplayWindow(val bounds:ChartBounds,private val geometryIndex:ChartGeometryQueryIndex,private val check:()->Unit) {
    private val longitude=if(bounds.west<=bounds.east)(bounds.west+bounds.east)/2
        else ((bounds.west+bounds.east+360.0)/2+540.0)%360.0-180.0
    private val projection=DrawingProjection(longitude,check)
    private val viewport=projection.viewport(bounds)
    private val envelope=viewport.envelopeInternal

    fun clip(geometry:ChartGeometry):ChartGeometry {
        check()
        geometryIndex.uniformWindow(geometry,bounds,check)?.let {inside->
            return if(!inside)ChartGeometry(geometry.kind,emptyList()) else
                ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(listOf(
                    ChartPoint(bounds.south,bounds.west),ChartPoint(bounds.south,bounds.east),
                    ChartPoint(bounds.north,bounds.east),ChartPoint(bounds.north,bounds.west),
                    ChartPoint(bounds.south,bounds.west),
                ))))
        }
        val source=projection.geometry(geometryIndex.window(geometry,bounds,check),envelope)
        if(source.isEmpty)return ChartGeometry(geometry.kind,emptyList())
        val clipped=if(envelope.covers(source.envelopeInternal))source
            else OverlayNGRobust.overlay(source,viewport,OverlayNG.INTERSECTION)
        check()
        require(clipped.isValid){"CHART_DISPLAY_GEOMETRY_INVALID"}
        return if(clipped.isEmpty||clipped.dimension<source.dimension)ChartGeometry(geometry.kind,emptyList())
            else projection.contract(clipped,geometry)
    }

    fun snapshot(snapshot:ChartDataSnapshot):ChartDataSnapshot {
        var vertices=0
        val datasets=snapshot.datasets.map {dataset->
            dataset.copy(cells=dataset.cells.filter {cell->
                cell.bounds.isEmpty()||"GPKG_DATELINE_TOPOLOGY_UNCERTAIN" in cell.issues||cell.bounds.any {a->
                    a.split().any {x->bounds.split().any {y->x.east>=y.west&&x.west<=y.east&&x.north>=y.south&&x.south<=y.north}}
                }
            }.map {cell->
                check()
                cell.copy(coverage=cell.coverage.map {coverage->
                    val geometry=clip(coverage.geometry)
                    vertices+=geometry.parts.sumOf {it.points.size}
                    require(vertices<=250_000){"CHART_DISPLAY_COVERAGE_LIMIT"}
                    // 空的正覆盖仍保留记录，不能误变成“该来源没有 coverage，可画参考对象”。
                    coverage.copy(geometry=geometry)
                })
            })
        }
        return snapshot.copy(datasets=datasets)
    }

    companion object {
        fun validate(bounds:ChartBounds) {
            require(bounds.valid){"CHART_DISPLAY_WINDOW_INVALID"}
            val width=if(bounds.west<=bounds.east)bounds.east-bounds.west else bounds.east+360.0-bounds.west
            // 只开放局部观察窗口；高纬经向跨度可大，但纬向与角域都保持有界。
            require(width in 0.0..60.0&&bounds.north-bounds.south<=2.0){"CHART_DISPLAY_WINDOW_LIMIT"}
        }

        fun contains(outer:ChartBounds,inner:ChartBounds):Boolean = inner.split().all {part->
            outer.split().any {box->part.west>=box.west&&part.east<=box.east&&part.south>=box.south&&part.north<=box.north}
        }
    }
}
