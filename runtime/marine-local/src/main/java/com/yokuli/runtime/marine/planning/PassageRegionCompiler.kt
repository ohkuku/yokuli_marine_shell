package com.yokuli.runtime.marine.planning

import org.locationtech.jts.geom.*
import kotlin.math.*

/** Android 与桌面编译器共用：将已完成语义解析的局部事实编译成同一种持久规划产物。 */
internal fun compilePassageRegion(world:PassageWorld,source:String,id:PassageRegionId,policy:String,
    base:PassageRegionProduct?=null,check:()->Unit={}):PassageRegionProduct {
    if(base!=null&&world.navigable===base.waterWithHalo)return base.copy(
        header=base.header.copy(policy=policy,margin=world.margin,constraints=emptyList(),rasters=emptyList()),
        semantics=PassageSemanticRegion(emptyList(),emptyList()))
    val semantics=if(base==null)PassageSemanticRegion.from(world){check()}else PassageSemanticRegion(emptyList(),emptyList())
    val p=world.projection;val operations=PassageGeometryOperations{check()}
    // 各边在相同地理位置细分；相邻区使用同一参数，不借投影直弦跨过弯曲边界。
    fun edge(edge:Int):LineString {
        val lower=if(edge<2)id.south else id.west
        return p.line((0..16).map{id.point(edge,lower+PassageRegionId.STEP*it/16)})
    }
    val ring=buildList {
        addAll(edge(2).coordinates.toList())
        addAll(edge(1).coordinates.drop(1))
        addAll(edge(3).coordinates.reversed().drop(1))
        addAll(edge(0).coordinates.reversed().drop(1))
    }.toMutableList().also{if(!it.first().equals2D(it.last()))it+=Coordinate(it.first())}
    val rectangle=p.factory.createPolygon(ring.toTypedArray())
    val clipped=operations.intersection(world.navigable,rectangle)
    val components=ArrayList<Geometry>()
    fun polygons(shape:Geometry) {
        check()
        if(shape is Polygon){if(!shape.isEmpty&&shape.area>.01)components+=shape;return}
        if(shape is GeometryCollection)for(i in 0 until shape.numGeometries)polygons(shape.getGeometryN(i))
    }
    polygons(clipped)
    components.sortWith(compareBy<Geometry>{it.envelopeInternal.minX}.thenBy{it.envelopeInternal.minY}.thenBy{it.area})
    require(components.size<=4096){"NAVIGATION_COMPONENT_LIMIT"}
    val portals=ArrayList<PassagePortal>()
    for((index,component) in components.withIndex())for(side in 0..3) {
        check()
        val hit=operations.intersection(component,edge(side))
        fun addLines(shape:Geometry) {
            if(shape is LineString) {
                if(shape.length<.2)return
                val values=shape.coordinates.map {c->p.point(c).let{point->
                    if(side<2)point.latitude else id.west+((point.longitude-id.west+540)%360)-180
                }}
                portals+=PassagePortal(index,side,values.min(),values.max())
            }else if(shape is GeometryCollection)for(j in 0 until shape.numGeometries)addLines(shape.getGeometryN(j))
        }
        addLines(hit)
    }
    require(portals.size<=16384){"NAVIGATION_PORTAL_LIMIT"}
    val uncertain=(world.referenceDatumFeatures+world.unknownDepthFeatures).distinctBy{it.feature.id}
    require(uncertain.size<=8192){"NAVIGATION_EVIDENCE_LIMIT"}
    val header=PassageRegionHeader(source=source,policy=policy,region=id,portals=portals,
        componentCount=components.size,evidence=base?.header?.evidence?:uncertain.map{item->
            PassageRegionEvidence(item.feature.id,item.feature.cellId,item.feature.depth)
        },malformed=world.malformed,margin=world.margin,
        constraints=semantics.constraints.map{PassageConstraintHeader(it.kind,it.featureId,it.cellId,it.minimumMeters,it.datumKnown)},
        rasters=semantics.rasters.map{item->val w=item.source.window;PassageRasterHeader(item.source.grid,w.column,w.row,w.width,w.height)})
    val mesh=PassageNavigationMesh.compile(world.navigable){check()}
    return PassageRegionProduct(header,components,base?.unknownDepth?:uncertain.map{it.geometry},world.navigable,semantics,mesh)
}
