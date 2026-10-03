package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.AffineTransformation
import org.locationtech.jts.operation.valid.IsValidOp
import kotlin.math.round
import java.util.IdentityHashMap

/** 只做绘制组合，不判断分析用途；参考数据与已登记分析用途的数据使用相同显示规则。 */
data class ChartDrawingResult(val features:List<NauticalFeature>,val incompleteGeometry:Boolean,val boundaries:Map<String,ChartGeometry> = emptyMap(),val rasterMasks:Map<String,ChartGeometry> = emptyMap())

/** 导入器记录的局部未知范围；它能遮住低优先资料，不能提供水深或可信覆盖。 */
internal fun NauticalFeature.hasUncertainChartGeometry() =
    attributes["GPKG_GEOMETRY_STATUS"]=="DATELINE_TOPOLOGY_UNCERTAIN" ||
        "GPKG_DATELINE_TOPOLOGY_UNCERTAIN" in issues

object ChartDrawingClipper {
    // 元数据覆盖对象是不可变的；全国轮廓的边索引由连续窗口共享，并按内存预算释放。
    private val geometryIndex=ChartGeometryQueryIndex()
    /** 当前文件夹内部的文件次序、图幅比例尺与规划一致。栅格空值仍占据来源，不泄漏低层深区。 */
    suspend fun compose(snapshot:ChartDataSnapshot,features:List<NauticalFeature>,bounds:ChartBounds):ChartDrawingResult {
        val center=if(bounds.west<=bounds.east)(bounds.west+bounds.east)/2 else ((bounds.west+bounds.east+360)/2+540)%360-180
        val work=currentCoroutineContext()
        val projection=DrawingProjection(center){work.ensureActive()}
        val factory=projection.factory
        val viewport=projection.viewport(bounds)
        val operations=projection.operations
        val envelope=viewport.envelopeInternal
        data class LocalGeometry(val shape:Geometry,val original:Geometry?)
        val localGeometries=IdentityHashMap<ChartGeometry,LocalGeometry>()
        fun local(value:ChartGeometry):LocalGeometry=localGeometries[value]?:run {
            work.ensureActive()
            // 只有真实边索引证明窗口内没有边界，才把交集写成整窗或空窗。覆盖窗的
            // 全国大面不再每块转成完整 JTS；接触海岸/孔洞仍进行精确拓扑裁剪。
            val uniform=geometryIndex.uniformWindow(value,bounds){work.ensureActive()}
            val result=when(uniform) {
                true->LocalGeometry(viewport,null)
                false->LocalGeometry(factory.createPolygon(),null)
                null->{
                    val original=projection.geometry(geometryIndex.window(value,bounds){work.ensureActive()},envelope)
                    LocalGeometry(operations.intersection(original,viewport),original)
                }
            }
            localGeometries[value]=result
            result
        }
        fun localArea(value:ChartGeometry)=operations.area(local(value).shape)
        fun overlaps(box:ChartBounds)=box.split().any {a->bounds.split().any {b->
            a.east>=b.west&&a.west<=b.east&&a.north>=b.south&&a.south<=b.north
        }}
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
                if(uncertainFeatures.isEmpty()&&cell.bounds.isNotEmpty()&&!cell.bounds.any(::overlaps))continue

                val available=if(manualOrder)operations.difference(viewport,occupiedManual) else viewport
                val uncertainty=operations.area(projection.union(uncertainFeatures.map{operations.intersection(local(it.geometry).shape,available)}))
                if(!uncertainty.isEmpty){uncertainMasks[key]=available;incomplete=true}

                val positive=cell.coverage.filter {it.covered}.map {localArea(it.geometry)}
                val negative=cell.coverage.filterNot {it.covered}.map {localArea(it.geometry)}
                val raw=if(positive.isEmpty()) {
                    // Missing structured coverage never claims an entire viewport. It only provides
                    // a clip envelope for actual objects; feature-level ownership below decides what
                    // can hide a lower source.
                    incomplete=true
                    operations.difference(viewport,uncertainty)
                } else {
                    operations.difference(operations.difference(projection.union(positive),projection.union(negative)),uncertainty)
                }
                rawMasks[key]=raw
                val effective=if(manualOrder)operations.difference(raw,occupiedManual) else raw
                masks[key]=effective
                if(manualOrder&&positive.isNotEmpty())occupiedManual=operations.union(occupiedManual,raw)
                if(manualOrder&&!uncertainty.isEmpty)occupiedManual=operations.union(occupiedManual,uncertainty)

                val grids=dataset.rasters.orEmpty().filter{it.cellId==cell.cellId}
                if(grids.isNotEmpty()) {
                    val footprint=operations.area(operations.intersection(projection.boundsGeometry(grids.flatMap{it.bounds},envelope),viewport))
                    rasterSources+=RasterSource(
                        key,cell,footprint,
                        grids.minOf{maxOf(it.pixelWidthDegrees,it.pixelHeightDegrees)}
                    )
                    if(manualOrder) {
                        val rasterEffective=operations.difference(footprint,occupiedManual)
                        if(!rasterEffective.isEmpty)rasterMasks[key]=projection.contract(
                            rasterEffective,ChartGeometry(ChartGeometryKind.POLYGON,emptyList())
                        )
                        occupiedManual=operations.union(occupiedManual,footprint)
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
                val claim=operations.area(projection.union(source.items.map{operations.intersection(local(it.geometry).shape,source.base)}))
                // Explicit ordering chooses the file first, but mixed-scale tiers inside that file
                // still obey fine-over-coarse ownership just like cursor and planning.
                val effective=operations.difference(claim,occupiedOwnership)
                ownershipMasks[source.key to source.scale]=effective
                occupiedOwnership=operations.union(occupiedOwnership,claim)
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
                val effective=try{operations.difference(source.geometry,occupiedRaster)}
                    catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                    catch(_:Exception){incomplete=true;continue}
                if(!effective.isEmpty) {
                    val previous=rasterMasks[source.key]?.let(projection::geometry)
                    val combined=if(previous==null)effective else operations.union(previous,effective)
                    rasterMasks[source.key]=projection.contract(combined,ChartGeometry(ChartGeometryKind.POLYGON,emptyList()))
                    occupiedRaster=operations.union(occupiedRaster,source.geometry)
                }
            }
        }
        val rank=cells.mapIndexed {index,(_,dataset,cell)->"${dataset.id}/${cell.cellId}" to index}.toMap()
        val output=ArrayList<NauticalFeature>()
        val boundaries=mutableMapOf<String,ChartGeometry>()
        for(feature in features.sortedWith(compareByDescending<NauticalFeature> {rank["${it.datasetId}/${it.cellId}"] ?: Int.MAX_VALUE}.thenBy {it.kind!=NauticalFeatureKind.DEPTH_AREA})) {
            currentCoroutineContext().ensureActive()
            val key="${feature.datasetId}/${feature.cellId}"
            val normalMask=when {
                feature.kind in ownershipKinds->
                    ownershipMasks[key to feature.detailScaleDenominator()] ?: (if(manualOrder)masks[key] else rawMasks[key])
                feature.kind in sourceBoundKinds->{
                    ownershipMasks[key to feature.detailScaleDenominator()] ?:
                        (if(manualOrder)masks[key] else rawMasks[key])?.let{operations.difference(it,occupiedOwnership)}
                }
                manualOrder->masks[key]
                else->rawMasks[key]
            }
            val mask=(if(feature.hasUncertainChartGeometry())uncertainMasks[key] else normalMask) ?: continue
            if(mask.isEmpty)continue
            try {
                val source=local(feature.geometry)
                val geometry=operations.intersection(source.shape,mask)
                // 岸线取原始真实环再裁剪，不能把 viewport 的人为剪切边画成岸线。
                // uniformWindow 已证明无真实边界的整窗面，其岸线明确为空。
                if(feature.geometry.kind==ChartGeometryKind.POLYGON&&feature.kind !in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.COVERAGE)) {
                    val boundary=source.original?.let{operations.intersection(it.boundary,mask)}?:factory.createLineString()
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
    val operations=ChartGeometryOperations(check)
    private fun x(value:Double)=((value-longitude+540)%360)-180
    private fun coordinate(p:ChartPoint)=Coordinate(x(p.longitude),p.latitude)
    private fun point(p:Coordinate,depth:Double?=null)=ChartPoint(p.y,((p.x+longitude+540)%360)-180,depth)
    fun union(values:List<Geometry>):Geometry=operations.union(values,factory)
    fun boundsGeometry(bounds:List<ChartBounds>,window:Envelope?=null):Geometry=union(bounds.flatMap{it.split()}.flatMap {box->
        val boxes=if(box.east-box.west>=359.999999)listOf(Envelope(-180.0,180.0,box.south,box.north))
        else {
            val west=x(box.west);val east=west+box.east-box.west
            listOf(-360.0,0.0,360.0).map{offset->Envelope(west+offset,east+offset,box.south,box.north)}
        }
        boxes.mapNotNull {area->
            check()
            val clipped=if(window==null)area else area.intersection(window)
            if(clipped.isNull)null else factory.toGeometry(clipped)
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
                // 每个点从其原始经度求同一连续分支，不能逐段累加经度差。后者会让
                // 日期线切开的共用顶点产生 1e-12° 漂移，制造面重叠/自交和未结点边。
                val exact=x(p.longitude)
                previous=exact+round((previous-exact)/360.0)*360.0
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
                val polygons=shells.map {shell->
                    check()
                    factory.createPolygon(shell.exteriorRing as LinearRing,holes[shell].orEmpty().toTypedArray()).also {polygon->
                        // 不猜测修复单个坏环；已登记的未知范围依旧由 OTHER/uncertainty 遮罩处理。
                        require(polygon.isValid){"CHART_DRAWING_INVALID_GEOMETRY:${IsValidOp(polygon).validationError}"}
                    }
                }
                val collection=factory.createMultiPolygon(polygons.toTypedArray())
                // 同一对象的日期线分片在同一经度分支上可能接边或重叠。各子面已单独
                // 证明有效，此处只求它们原本覆盖集合的精确并集，不填孔、不补未知海水。
                if(collection.isValid)collection else union(polygons)
            }
            ChartGeometryKind.NONE->factory.createGeometryCollection()
        }
        check()
        require(primary.isValid) {"CHART_DRAWING_INVALID_GEOMETRY:${IsValidOp(primary).validationError}"}
        // 全球预览/最低级瓦片需要接缝另一边的真实副本；短局部视口最终仍由 viewport 裁掉。
        val copies=mutableListOf<Geometry>(primary)
        if(!primary.isEmpty&&primary.envelopeInternal.minX < -180.0)copies+=AffineTransformation.translationInstance(360.0,0.0).transform(primary)
        if(!primary.isEmpty&&primary.envelopeInternal.maxX > 180.0)copies+=AffineTransformation.translationInstance(-360.0,0.0).transform(primary)
        val result=if(copies.size==1)primary else union(copies)
        require(result.isValid) {"CHART_DRAWING_INVALID_GEOMETRY:${IsValidOp(result).validationError}"}
        return result
    }
    fun contract(geometry:Geometry,original:ChartGeometry):ChartGeometry {
        if(geometry.isEmpty)return ChartGeometry(when(geometry.dimension) {
            2->ChartGeometryKind.POLYGON;1->ChartGeometryKind.LINE;else->original.kind
        },emptyList())
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
