package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.locationtech.jts.geom.*
import org.locationtech.jts.operation.union.UnaryUnionOp

/** 只做绘制组合，不判断分析用途；参考数据与已登记分析用途的数据使用相同显示规则。 */
data class ChartDrawingResult(val features:List<NauticalFeature>,val incompleteGeometry:Boolean)

object ChartDrawingClipper {
    /** 用户数据集顺序优先，其次图幅的编图比例尺。高优先级真实覆盖切掉低层，透明填色不泄漏旧深区。 */
    suspend fun compose(snapshot:ChartDataSnapshot,features:List<NauticalFeature>,bounds:ChartBounds):ChartDrawingResult {
        val center=if(bounds.west<=bounds.east)(bounds.west+bounds.east)/2 else ((bounds.west+bounds.east+360)/2+540)%360-180
        val projection=DrawingProjection(center)
        val factory=projection.factory
        val viewport=projection.viewport(bounds)
        var occupied:Geometry=factory.createPolygon()
        var incomplete=false
        val masks=mutableMapOf<String,Geometry>()
        val cells=snapshot.datasets.flatMapIndexed {index,dataset->dataset.cells.filterNot {it.cancelled}.map {Triple(index,dataset,it)}}
            .sortedWith(compareBy<Triple<Int,ChartDataset,ChartCellRevision>> {it.first}.thenBy {it.third.compilationScale ?: Int.MAX_VALUE}.thenByDescending {it.third.edition}.thenByDescending {it.third.update}.thenBy {it.third.cellId})
        for((_,dataset,cell) in cells) {
            currentCoroutineContext().ensureActive()
            try {
                val positive=cell.coverage.filter {it.covered}.map {projection.geometry(it.geometry).intersection(viewport)}
                val negative=cell.coverage.filterNot {it.covered}.map {projection.geometry(it.geometry).intersection(viewport)}
                if(positive.isEmpty()) {
                    // 无覆盖的参考对象仍可查阅，但其包围框不冒充一整块真实覆盖，也不盖住其他图幅。
                    masks["${dataset.id}/${cell.cellId}"]=viewport.difference(occupied)
                    incomplete=true
                }else {
                    val coverage=projection.union(positive).difference(projection.union(negative))
                    masks["${dataset.id}/${cell.cellId}"]=coverage.difference(occupied)
                    occupied=occupied.union(coverage)
                }
            }catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_:Exception) {incomplete=true;masks["${dataset.id}/${cell.cellId}"]=factory.createPolygon();occupied=viewport}
        }
        val rank=cells.mapIndexed {index,(_,dataset,cell)->"${dataset.id}/${cell.cellId}" to index}.toMap()
        val output=ArrayList<NauticalFeature>()
        for(feature in features.sortedWith(compareByDescending<NauticalFeature> {rank["${it.datasetId}/${it.cellId}"] ?: Int.MAX_VALUE}.thenBy {it.kind!=NauticalFeatureKind.DEPTH_AREA})) {
            currentCoroutineContext().ensureActive()
            val mask=masks["${feature.datasetId}/${feature.cellId}"] ?: continue
            if(mask.isEmpty)continue
            try {
                val geometry=projection.geometry(feature.geometry).intersection(mask)
                if(!geometry.isEmpty)output+=feature.copy(geometry=projection.contract(geometry,feature.geometry))
            }catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_:Exception) {incomplete=true}
        }
        return ChartDrawingResult(output,incomplete)
    }
}

/** 经度围绕当前视口解缠，JTS只承担拓扑裁剪，不用于距离/安全余量。 */
private class DrawingProjection(private val longitude:Double) {
    val factory=GeometryFactory()
    private fun x(value:Double)=((value-longitude+540)%360)-180
    private fun coordinate(p:ChartPoint)=Coordinate(x(p.longitude),p.latitude)
    private fun point(p:Coordinate,depth:Double?=null)=ChartPoint(p.y,((p.x+longitude+540)%360)-180,depth)
    fun union(values:List<Geometry>):Geometry=if(values.isEmpty())factory.createPolygon()else UnaryUnionOp.union(values)
    fun viewport(bounds:ChartBounds):Geometry {
        val west=if(bounds.west==-180.0&&bounds.east==180.0)-180.0 else x(bounds.west)
        val east=if(bounds.west==-180.0&&bounds.east==180.0)180.0 else x(bounds.east)
        require(west<=east) {"CHART_DRAWING_VIEWPORT"}
        return factory.toGeometry(Envelope(west,east,bounds.south,bounds.north))
    }
    fun geometry(value:ChartGeometry):Geometry {
        fun ring(part:ChartGeometryPart):LinearRing {
            val points=part.points.map(::coordinate)
            require(points.size>=4&&points.first().equals2D(points.last())) {"CHART_DRAWING_RING_OPEN"}
            return factory.createLinearRing(points.toTypedArray())
        }
        val result=when(value.kind) {
            ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT->factory.createMultiPointFromCoords(value.parts.flatMap {it.points}.map(::coordinate).toTypedArray())
            ChartGeometryKind.LINE->factory.createMultiLineString(value.parts.filter {it.points.size>=2}.map {factory.createLineString(it.points.map(::coordinate).toTypedArray())}.toTypedArray())
            ChartGeometryKind.POLYGON->{
                val shells=value.parts.filterNot {it.hole}.map {factory.createPolygon(ring(it))}
                val holes=value.parts.filter {it.hole}.map(::ring).groupBy {hole->shells.filter {it.covers(factory.createPolygon(hole))}.minByOrNull {it.area} ?: error("CHART_DRAWING_HOLE_UNATTACHED")}
                factory.createMultiPolygon(shells.map {shell->factory.createPolygon(shell.exteriorRing as LinearRing,holes[shell].orEmpty().toTypedArray())}.toTypedArray())
            }
            ChartGeometryKind.NONE->factory.createGeometryCollection()
        }
        require(result.isValid) {"CHART_DRAWING_INVALID_GEOMETRY"}
        return result
    }
    fun contract(geometry:Geometry,original:ChartGeometry):ChartGeometry {
        val parts=mutableListOf<ChartGeometryPart>()
        val depths=original.parts.flatMap {it.points}.associate {p->coordinate(p).let {it.x to it.y} to p.depthMeters}
        fun append(value:Geometry) {if(value.dimension!=geometry.dimension)return;when(value) {
            is Polygon->{parts+=ChartGeometryPart(value.exteriorRing.coordinates.map {point(it)});for(i in 0 until value.numInteriorRing)parts+=ChartGeometryPart(value.getInteriorRingN(i).coordinates.map {point(it)},true)}
            is LineString->parts+=ChartGeometryPart(value.coordinates.map {point(it)})
            is Point->parts+=ChartGeometryPart(listOf(point(value.coordinate,depths[value.x to value.y])))
            is GeometryCollection->for(i in 0 until value.numGeometries)append(value.getGeometryN(i))
        }}
        append(geometry)
        return ChartGeometry(when(geometry.dimension) {2->ChartGeometryKind.POLYGON;1->ChartGeometryKind.LINE;else->if(parts.sumOf {it.points.size}>1)ChartGeometryKind.MULTIPOINT else ChartGeometryKind.POINT},parts)
    }
}
