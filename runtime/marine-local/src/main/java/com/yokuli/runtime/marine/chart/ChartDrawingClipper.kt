package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.locationtech.jts.geom.*
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.geom.util.AffineTransformation
import kotlin.math.round

/** 只做绘制组合，不判断分析用途；参考数据与已登记分析用途的数据使用相同显示规则。 */
data class ChartDrawingResult(val features:List<NauticalFeature>,val incompleteGeometry:Boolean,val boundaries:Map<String,ChartGeometry> = emptyMap(),val rasterMasks:Map<String,ChartGeometry> = emptyMap())

/** 导入器记录的局部未知范围；它能遮住低优先资料，不能提供水深或可信覆盖。 */
internal fun NauticalFeature.hasUncertainChartGeometry() =
    attributes["GPKG_GEOMETRY_STATUS"]=="DATELINE_TOPOLOGY_UNCERTAIN" ||
        "GPKG_DATELINE_TOPOLOGY_UNCERTAIN" in issues

object ChartDrawingClipper {
    /** 当前文件夹内部的文件次序、图幅比例尺与规划一致。栅格空值仍占据来源，不泄漏低层深区。 */
    suspend fun compose(snapshot:ChartDataSnapshot,features:List<NauticalFeature>,bounds:ChartBounds):ChartDrawingResult {
        val center=if(bounds.west<=bounds.east)(bounds.west+bounds.east)/2 else ((bounds.west+bounds.east+360)/2+540)%360-180
        val work=currentCoroutineContext()
        val projection=DrawingProjection(center){work.ensureActive()}
        val factory=projection.factory
        val viewport=projection.viewport(bounds)
        var occupiedManual:Geometry=factory.createPolygon()
        var incomplete=false
        val masks=mutableMapOf<String,Geometry>()
        val rawMasks=mutableMapOf<String,Geometry>()
        val uncertainMasks=mutableMapOf<String,Geometry>()
        val rasterMasks=mutableMapOf<String,ChartGeometry>()
        data class RasterSource(val key:String,val cell:ChartCellRevision,val geometry:Geometry,val resolution:Double)
        val rasterSources=mutableListOf<RasterSource>()
        val featuresByCell=features.groupBy{"${it.datasetId}/${it.cellId}"}
        val uncertainByCell=featuresByCell.mapValues{(_,items)->items.filter{it.hasUncertainChartGeometry()}}

        val unsorted=snapshot.datasets.flatMapIndexed {index,dataset->
            dataset.cells.filterNot {it.cancelled}.map {Triple(index,dataset,it)}
        }
        val manualOrder=unsorted.any{it.third.priorityExplicit}
        fun sourceClass(entry:Triple<Int,ChartDataset,ChartCellRevision>)=when {
            entry.third.detailTier()!=null->0
            entry.third.featureCount>0->1
            entry.second.rasters.orEmpty().any{it.cellId==entry.third.cellId}->2
            else->3
        }
        fun rasterResolution(entry:Triple<Int,ChartDataset,ChartCellRevision>)=
            entry.second.rasters.orEmpty().filter{it.cellId==entry.third.cellId}
                .minOfOrNull{maxOf(it.pixelWidthDegrees,it.pixelHeightDegrees)}?:Double.POSITIVE_INFINITY
        val cells=unsorted.sortedWith(
            compareBy<Triple<Int,ChartDataset,ChartCellRevision>>{it.first}.then(
                if(manualOrder)
                    compareBy<Triple<Int,ChartDataset,ChartCellRevision>>{it.third.priority}
                        .thenBy{sourceClass(it)}
                        .thenBy{it.third.detailTier()?:Int.MAX_VALUE}
                        .thenBy{it.third.detailScaleDenominator()?:Int.MAX_VALUE}
                else
                    compareBy<Triple<Int,ChartDataset,ChartCellRevision>>{sourceClass(it)}
                        .thenBy{it.third.detailTier()?:Int.MAX_VALUE}
                        .thenBy{it.third.detailScaleDenominator()?:Int.MAX_VALUE}
                        .thenBy{rasterResolution(it)}
                        .thenBy{it.third.priority}
            ).thenByDescending{it.third.edition}.thenByDescending{it.third.update}.thenBy{it.third.cellId}
        )

        for((_,dataset,cell) in cells) {
            currentCoroutineContext().ensureActive()
            val key="${dataset.id}/${cell.cellId}"
            try {
                val uncertainFeatures=uncertainByCell[key].orEmpty()
                if(uncertainFeatures.isEmpty()&&cell.bounds.isNotEmpty()&&!projection.boundsGeometry(cell.bounds).intersects(viewport))continue

                val available=if(manualOrder)viewport.difference(occupiedManual) else viewport
                val uncertainty=projection.union(uncertainFeatures.map{projection.geometry(it.geometry).intersection(available)})
                if(!uncertainty.isEmpty){uncertainMasks[key]=available;incomplete=true}

                val positive=cell.coverage.filter {it.covered}.map {projection.geometry(it.geometry).intersection(viewport)}
                val negative=cell.coverage.filterNot {it.covered}.map {projection.geometry(it.geometry).intersection(viewport)}
                val raw=if(positive.isEmpty()) {
                    // Missing structured coverage never claims an entire viewport. It only provides
                    // a clip envelope for actual objects; feature-level ownership below decides what
                    // can hide a lower source.
                    incomplete=true
                    viewport.difference(uncertainty)
                } else {
                    projection.union(positive).difference(projection.union(negative)).intersection(viewport).difference(uncertainty)
                }
                rawMasks[key]=raw
                val effective=if(manualOrder)raw.difference(occupiedManual) else raw
                masks[key]=effective
                if(manualOrder&&positive.isNotEmpty())occupiedManual=occupiedManual.union(raw)
                if(manualOrder&&!uncertainty.isEmpty)occupiedManual=occupiedManual.union(uncertainty)

                val grids=dataset.rasters.orEmpty().filter{it.cellId==cell.cellId}
                if(grids.isNotEmpty()) {
                    val footprint=projection.boundsGeometry(grids.flatMap{it.bounds}).intersection(viewport)
                    rasterSources+=RasterSource(
                        key,cell,footprint,
                        grids.minOf{maxOf(it.pixelWidthDegrees,it.pixelHeightDegrees)}
                    )
                    if(manualOrder) {
                        val rasterEffective=footprint.difference(occupiedManual)
                        if(!rasterEffective.isEmpty)rasterMasks[key]=projection.contract(
                            rasterEffective,ChartGeometry(ChartGeometryKind.POLYGON,emptyList())
                        )
                        occupiedManual=occupiedManual.union(footprint)
                    }
                }
            }catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_:Exception) {
                // A bad source must not blank every lower source in the viewport.
                incomplete=true;masks[key]=factory.createPolygon();rawMasks[key]=factory.createPolygon()
                uncertainMasks[key]=factory.createPolygon()
            }
        }

        // Automatic precedence is feature-level: only competing area semantics own space.
        // Independent hazards/facilities are clipped to their real source coverage but never hidden
        // merely because a finer DEPARE exists.
        val ownershipKinds=setOf(
            NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,
            NauticalFeatureKind.LAND,NauticalFeatureKind.DRYING_AREA
        )
        val sourceBoundKinds=ownershipKinds+setOf(
            NauticalFeatureKind.SOUNDING,NauticalFeatureKind.DEPTH_CONTOUR,NauticalFeatureKind.QUALITY
        )
        data class OwnershipSource(
            val key:String,val cell:ChartCellRevision,val scale:Int?,
            val items:List<NauticalFeature>,val base:Geometry
        )
        val cellByKey=cells.associate{(_,dataset,cell)->"${dataset.id}/${cell.cellId}" to cell}
        val ownershipSources=features.filter{it.kind in ownershipKinds&&it.geometry.kind==ChartGeometryKind.POLYGON}
            .groupBy{"${it.datasetId}/${it.cellId}" to it.detailScaleDenominator()}
            .mapNotNull{(source,items)->
                val key=source.first;val cell=cellByKey[key]?:return@mapNotNull null
                val base=(if(manualOrder)masks[key] else rawMasks[key])?:return@mapNotNull null
                OwnershipSource(key,cell,source.second,items,base)
            }.sortedWith(
                if(manualOrder)
                    compareBy<OwnershipSource>{it.cell.priority}
                        .thenBy{detailTierForScale(it.scale)?:Int.MAX_VALUE}
                        .thenBy{it.scale?:Int.MAX_VALUE}.thenBy{it.key}
                else
                    compareBy<OwnershipSource>{detailTierForScale(it.scale)?:Int.MAX_VALUE}
                        .thenBy{it.scale?:Int.MAX_VALUE}
                        .thenBy{it.cell.priority}.thenBy{it.key}
            )
        val ownershipMasks=mutableMapOf<Pair<String,Int?>,Geometry>()
        var occupiedOwnership:Geometry=factory.createPolygon()
        for(source in ownershipSources) {
            currentCoroutineContext().ensureActive()
            try {
                val claim=projection.union(source.items.map{projection.geometry(it.geometry).intersection(source.base)})
                // Explicit ordering chooses the file first, but mixed-scale tiers inside that file
                // still obey fine-over-coarse ownership just like cursor and planning.
                val effective=claim.difference(occupiedOwnership)
                ownershipMasks[source.key to source.scale]=effective
                occupiedOwnership=occupiedOwnership.union(claim)
            }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(_:Exception){incomplete=true}
        }

        // Rasters are automatic fallback behind vector area ownership. Among rasters, finer native
        // resolution wins unless the user explicitly supplied an ordering.
        if(!manualOrder&&rasterSources.isNotEmpty()) {
            var occupiedRaster=occupiedOwnership
            for(source in rasterSources.sortedWith(
                compareBy<RasterSource>{it.resolution}.thenBy{it.cell.priority}.thenBy{it.key}
            )) {
                val effective=runCatching{source.geometry.difference(occupiedRaster)}
                    .onFailure{incomplete=true}.getOrNull()?:continue
                if(!effective.isEmpty) {
                    val previous=rasterMasks[source.key]?.let(projection::geometry)
                    val combined=if(previous==null)effective else previous.union(effective)
                    rasterMasks[source.key]=projection.contract(combined,ChartGeometry(ChartGeometryKind.POLYGON,emptyList()))
                    occupiedRaster=occupiedRaster.union(source.geometry)
                }
            }
        }
        val rank=cells.mapIndexed {index,(_,dataset,cell)->"${dataset.id}/${cell.cellId}" to index}.toMap()
        val output=ArrayList<NauticalFeature>()
        val boundaries=mutableMapOf<String,ChartGeometry>()
        for(feature in features.sortedWith(compareByDescending<NauticalFeature> {rank["${it.datasetId}/${it.cellId}"] ?: Int.MAX_VALUE}.thenBy {it.kind!=NauticalFeatureKind.DEPTH_AREA})) {
            currentCoroutineContext().ensureActive()
            val key="${feature.datasetId}/${feature.cellId}"
            val normalMask=if(feature.kind in sourceBoundKinds)
                ownershipMasks[key to feature.detailScaleDenominator()] ?: (if(manualOrder)masks[key] else rawMasks[key])
            else if(manualOrder)masks[key] else rawMasks[key]
            val mask=(if(feature.hasUncertainChartGeometry())uncertainMasks[key] else normalMask) ?: continue
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
        return ChartDrawingResult(output,incomplete,boundaries,rasterMasks)
    }
}

/** 经度围绕当前视口解缠，JTS只承担拓扑裁剪，不用于距离/安全余量。 */
internal class DrawingProjection(private val longitude:Double,private val check:()->Unit) {
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
    fun geometry(value:ChartGeometry,window:Envelope?=null):Geometry {
        fun continuous(points:List<ChartPoint>):Array<Coordinate> {
            if(points.isEmpty())return emptyArray()
            var previous=x(points.first().longitude)
            val result=points.mapIndexed {index,p->
                if(index%256==0)check()
                if(index>0)previous+=((p.longitude-points[index-1].longitude+540.0)%360.0)-180.0
                Coordinate(previous,p.latitude)
            }.toTypedArray()
            val shift=round((result.minOf {it.x}+result.maxOf {it.x})/720.0)*360.0
            result.forEach {it.x-=shift}
            if(points.size>1&&points.first().latitude==points.last().latitude&&points.first().longitude==points.last().longitude&&
                kotlin.math.abs(result.last().x-result.first().x)<1e-7)result[result.lastIndex]=Coordinate(result.first())
            return result
        }
        fun ring(part:ChartGeometryPart):LinearRing {
            val points=continuous(part.points)
            require(points.size>=4&&points.first().equals2D(points.last())) {"CHART_DRAWING_RING_OPEN"}
            return factory.createLinearRing(points)
        }
        fun nearWindow(points:Array<Coordinate>):Boolean {
            if(window==null)return true
            val envelope=Envelope();points.forEach {envelope.expandToInclude(it)}
            return listOf(-360.0,0.0,360.0).any {shift->
                window.intersects(Envelope(envelope.minX+shift,envelope.maxX+shift,envelope.minY,envelope.maxY))
            }
        }
        fun localRing(part:ChartGeometryPart):LinearRing? {
            // 先线性检查独立环的窗口关系，远方岛屿不进入 JTS 拓扑检查/孔洞配对。
            val points=continuous(part.points)
            if(points.isEmpty()||!nearWindow(points))return null
            require(points.size>=4&&points.first().equals2D(points.last())) {"CHART_DRAWING_RING_OPEN"}
            return factory.createLinearRing(points)
        }
        val primary=when(value.kind) {
            ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT->factory.createMultiPointFromCoords(value.parts.flatMap {it.points}.map(::coordinate).filter {window==null||window.contains(it)}.toTypedArray())
            ChartGeometryKind.LINE->factory.createMultiLineString(value.parts.filter {it.points.size>=2}.mapNotNull {
                val points=continuous(it.points);if(nearWindow(points))factory.createLineString(points)else null
            }.toTypedArray())
            ChartGeometryKind.POLYGON->{
                val shells=value.parts.filterNot {it.hole}.mapNotNull {part->(if(window==null)ring(part)else localRing(part))?.let(factory::createPolygon)}
                if(shells.isEmpty())return factory.createPolygon()
                val holes=mutableMapOf<Polygon,MutableList<LinearRing>>()
                for(hole in value.parts.filter {it.hole}.mapNotNull {if(window==null)ring(it)else localRing(it)}) {
                    check()
                    val owner=shells.mapNotNull {shell->
                        // 孔洞和外环各自解缠后可能分处 ±180 分支，先对齐再判所属，不能丢岛。
                        val shift=round((shell.envelopeInternal.centre().x-hole.envelopeInternal.centre().x)/360.0)*360.0
                        val aligned=if(shift==0.0)hole else factory.createLinearRing(hole.coordinates.map {Coordinate(it.x+shift,it.y)}.toTypedArray())
                        if(shell.covers(factory.createPolygon(aligned)))shell to aligned else null
                    }.minByOrNull {it.first.area} ?: error("CHART_DRAWING_HOLE_UNATTACHED")
                    holes.getOrPut(owner.first){ArrayList()}.add(owner.second)
                }
                factory.createMultiPolygon(shells.map {shell->factory.createPolygon(shell.exteriorRing as LinearRing,holes[shell].orEmpty().toTypedArray())}.toTypedArray())
            }
            ChartGeometryKind.NONE->factory.createGeometryCollection()
        }
        check()
        require(primary.isValid) {"CHART_DRAWING_INVALID_GEOMETRY"}
        // 全球预览/最低级瓦片需要接缝另一边的真实副本；短局部视口最终仍由 viewport 裁掉。
        val copies=mutableListOf<Geometry>(primary)
        if(!primary.isEmpty&&primary.envelopeInternal.minX < -180.0)copies+=AffineTransformation.translationInstance(360.0,0.0).transform(primary)
        if(!primary.isEmpty&&primary.envelopeInternal.maxX > 180.0)copies+=AffineTransformation.translationInstance(-360.0,0.0).transform(primary)
        val result=if(copies.size==1)primary else UnaryUnionOp.union(copies)
        require(result.isValid) {"CHART_DRAWING_INVALID_GEOMETRY"}
        return result
    }
    fun contract(geometry:Geometry,original:ChartGeometry):ChartGeometry {
        val parts=mutableListOf<ChartGeometryPart>()
        val depths=if(original.kind in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT))
            original.parts.flatMap {it.points}.associate {p->coordinate(p).let {it.x to it.y} to p.depthMeters}
        else emptyMap()
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
