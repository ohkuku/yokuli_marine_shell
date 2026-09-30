package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.locationtech.jts.geom.*
import org.locationtech.jts.operation.union.UnaryUnionOp

/** 只做绘制组合，不判断分析用途；参考数据与已登记分析用途的数据使用相同显示规则。 */
data class ChartDrawingResult(val features:List<NauticalFeature>,val incompleteGeometry:Boolean,val boundaries:Map<String,ChartGeometry> = emptyMap())

/** 导入器记录的局部未知范围；它能遮住低优先资料，不能提供水深或可信覆盖。 */
internal fun NauticalFeature.hasUncertainChartGeometry() =
    attributes["GPKG_GEOMETRY_STATUS"]=="DATELINE_TOPOLOGY_UNCERTAIN" ||
        "GPKG_DATELINE_TOPOLOGY_UNCERTAIN" in issues

object ChartDrawingClipper {
    /** 当前文件夹内部的文件次序、图幅比例尺与规划一致。栅格空值仍占据来源，不泄漏低层深区。 */
    suspend fun compose(snapshot:ChartDataSnapshot,features:List<NauticalFeature>,bounds:ChartBounds):ChartDrawingResult {
        val center=if(bounds.west<=bounds.east)(bounds.west+bounds.east)/2 else ((bounds.west+bounds.east+360)/2+540)%360-180
        val projection=DrawingProjection(center)
        val factory=projection.factory
        val viewport=projection.viewport(bounds)
        var occupied:Geometry=factory.createPolygon()
        var incomplete=false
        val masks=mutableMapOf<String,Geometry>()
        val uncertainMasks=mutableMapOf<String,Geometry>()
        val uncertainByCell=features.filter{it.hasUncertainChartGeometry()}.groupBy{"${it.datasetId}/${it.cellId}"}
        val cells=snapshot.datasets.flatMapIndexed {index,dataset->dataset.cells.filterNot {it.cancelled}.map {Triple(index,dataset,it)}}
            .sortedWith(compareBy<Triple<Int,ChartDataset,ChartCellRevision>> {it.first}.thenBy {it.third.priority}.thenBy {it.third.compilationScale ?: Int.MAX_VALUE}.thenByDescending {it.third.edition}.thenByDescending {it.third.update}.thenBy {it.third.cellId})
        for((_,dataset,cell) in cells) {
            currentCoroutineContext().ensureActive()
            val key="${dataset.id}/${cell.cellId}"
            try {
                val grids=dataset.rasters.orEmpty().filter{it.cellId==cell.cellId}
                if(grids.isNotEmpty()) {
                    // 数值网格不伪造矢量对象，但其选定范围必须遵守同一来源遮盖规则。
                    val footprint=projection.boundsGeometry(grids.flatMap{it.bounds}).intersection(viewport)
                    occupied=occupied.union(footprint)
                    continue
                }
                val uncertainFeatures=uncertainByCell[key].orEmpty()
                // 旧目录 bounds 可能只存可信 coverage；独立未知对象必须按它自己的位置处理。
                if(uncertainFeatures.isEmpty()&&cell.bounds.isNotEmpty()&&!projection.boundsGeometry(cell.bounds).intersects(viewport))continue
                val available=viewport.difference(occupied)
                val uncertainty=projection.union(uncertainFeatures.map{projection.geometry(it.geometry).intersection(available)})
                if(!uncertainty.isEmpty){uncertainMasks[key]=available;incomplete=true}
                val positive=cell.coverage.filter {it.covered}.map {projection.geometry(it.geometry).intersection(viewport)}
                val negative=cell.coverage.filterNot {it.covered}.map {projection.geometry(it.geometry).intersection(viewport)}
                if(positive.isEmpty()) {
                    // 无覆盖的参考对象仍可查阅，但其包围框不冒充一整块真实覆盖，也不盖住其他图幅。
                    masks[key]=available.difference(uncertainty)
                    incomplete=true
                }else {
                    val coverage=projection.union(positive).difference(projection.union(negative))
                    masks[key]=coverage.intersection(available).difference(uncertainty)
                    occupied=occupied.union(coverage)
                }
                // 未知对象不受本图幅 coverage 裁掉；只让已经在更高优先级占据的来源遮住它。
                // 加入 occupied 后，较低层也不能把这块空白重新绘成可靠深区。
                occupied=occupied.union(uncertainty)
            }catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_:Exception) {incomplete=true;masks[key]=factory.createPolygon();uncertainMasks[key]=factory.createPolygon();occupied=viewport}
        }
        val rank=cells.mapIndexed {index,(_,dataset,cell)->"${dataset.id}/${cell.cellId}" to index}.toMap()
        val output=ArrayList<NauticalFeature>()
        val boundaries=mutableMapOf<String,ChartGeometry>()
        for(feature in features.sortedWith(compareByDescending<NauticalFeature> {rank["${it.datasetId}/${it.cellId}"] ?: Int.MAX_VALUE}.thenBy {it.kind!=NauticalFeatureKind.DEPTH_AREA})) {
            currentCoroutineContext().ensureActive()
            val key="${feature.datasetId}/${feature.cellId}"
            val mask=(if(feature.hasUncertainChartGeometry())uncertainMasks[key] else masks[key]) ?: continue
            if(mask.isEmpty)continue
            try {
                val original=projection.geometry(feature.geometry)
                val geometry=original.intersection(mask)
                // 面在视口/图幅接缝裁开后，不能把人为剪切边当岸线或限制区边界绘出。
                if(original.dimension==2&&feature.kind !in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.COVERAGE)) {
                    val boundary=original.boundary.intersection(mask)
                    boundaries[feature.id]=projection.contract(boundary,feature.geometry)
                }
                if(!geometry.isEmpty)output+=feature.copy(geometry=projection.contract(geometry,feature.geometry))
            }catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_:Exception) {incomplete=true}
        }
        return ChartDrawingResult(output,incomplete,boundaries)
    }
}

/** 经度围绕当前视口解缠，JTS只承担拓扑裁剪，不用于距离/安全余量。 */
private class DrawingProjection(private val longitude:Double) {
    val factory=GeometryFactory()
    private fun x(value:Double)=((value-longitude+540)%360)-180
    private fun coordinate(p:ChartPoint)=Coordinate(x(p.longitude),p.latitude)
    private fun point(p:Coordinate,depth:Double?=null)=ChartPoint(p.y,((p.x+longitude+540)%360)-180,depth)
    fun union(values:List<Geometry>):Geometry=if(values.isEmpty())factory.createPolygon()else UnaryUnionOp.union(values)
    fun boundsGeometry(bounds:List<ChartBounds>):Geometry=union(bounds.flatMap{it.split()}.flatMap {box->
        if(box.east-box.west>=359.999999)listOf(factory.toGeometry(Envelope(-180.0,180.0,box.south,box.north)))
        else {
            val west=x(box.west);val east=west+box.east-box.west
            listOf(-360.0,0.0,360.0).map{offset->factory.toGeometry(Envelope(west+offset,east+offset,box.south,box.north))}
        }
    })
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
