package com.yokuli.runtime.marine.planning

import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.PassageRequest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.locationtech.jts.geom.Geometry
import kotlin.math.max

/** 已完成来源归属、裁剪及投影的通行条件。显示网格不参与此模型。 */
internal enum class PassageConstraintKind { DEPTH, OVERHEAD }
internal data class PassageConstraint(val kind:PassageConstraintKind,val featureId:String,val cellId:String,
    val minimumMeters:Double?,val datumKnown:Boolean,val geometry:Geometry)
/** 保留原像元，不能把“连续水面内最低值”当作整个面相同的深度。mask 为已解析的来源归属。 */
internal data class PassageSemanticRaster(val source:ChartRasterWindow,val mask:Geometry)
internal data class PassageSemanticRegion(val constraints:List<PassageConstraint>,val rasters:List<PassageSemanticRaster>) {
    private val constraintIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        org.locationtech.jts.index.strtree.STRtree().apply{constraints.forEach{insert(it.geometry.envelopeInternal,it)};build()}
    }
    companion object {
        fun from(world:PassageWorld,check:()->Unit):PassageSemanticRegion {
            val conditions=ArrayList<PassageConstraint>()
            for(item in world.features) {
                check();val feature=item.feature
                when(feature.kind) {
                    NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA ->
                        feature.depth?.lowerMeters?.takeIf{it.isFinite()&&it>=0}?.let {depth->
                            conditions+=PassageConstraint(PassageConstraintKind.DEPTH,feature.id,feature.cellId,depth,
                                !feature.depth?.datum.isNullOrBlank(),item.geometry)
                        }
                    NauticalFeatureKind.SOUNDING -> for(part in feature.geometry.parts)for(point in part.points) {
                        check();val depth=point.depthMeters?:feature.depth?.pointMeters?.takeIf{feature.geometry.kind==ChartGeometryKind.POINT}?:continue
                        if(!depth.isFinite()||depth<0)continue
                        val shape=world.projection.factory.createPoint(world.projection.xy(point))
                        if(item.geometry.covers(shape))conditions+=PassageConstraint(PassageConstraintKind.DEPTH,feature.id,feature.cellId,
                            depth,!feature.depth?.datum.isNullOrBlank(),shape)
                    }
                    NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD -> conditions+=PassageConstraint(
                        PassageConstraintKind.OVERHEAD,feature.id,feature.cellId,
                        (feature.attributes["VERCLR"]?:feature.attributes["VERCCL"])?.toDoubleOrNull()?.takeIf{it.isFinite()&&it>=0},
                        feature.source.verticalDatum!=null,item.geometry)
                    else->Unit
                }
            }
            return PassageSemanticRegion(conditions,world.semanticRasters)
        }
    }

    /** 快速直航：已编译海陆面 + 线段附近的通行条件，不为一条直线先剖分新船型网格。 */
    fun direct(base:PassageRegionProduct,request:PassageRequest,line:Geometry,check:()->Unit):Boolean? {
        check();val vessel=request.vessel
        val margin=max(1.0,max(vessel.corridorHalfWidthMeters?:0.0,(vessel.beamMeters?:0.0)/2+(vessel.clearanceMarginMeters?:0.0)))
        val required=vessel.draftMeters?.takeIf{it.isFinite()&&it>0}?.plus(vessel.minimumUnderKeelMeters?:0.0)
        val air=vessel.airDraftMeters?.takeIf{it.isFinite()&&it>0}?.plus(vessel.clearanceMarginMeters?:0.0)
        val trace=if(margin>base.header.margin)line.buffer(margin-base.header.margin)else line
        if(!base.preparedWater.covers(trace))return false
        val envelope=org.locationtech.jts.geom.Envelope(line.envelopeInternal).apply{expandBy(margin)}
        for(item in constraintIndex.query(envelope)) {
            check();val condition=item as PassageConstraint
            val blocked=when(condition.kind) {
                PassageConstraintKind.DEPTH->required!=null&&condition.minimumMeters?.let{it<required}==true
                PassageConstraintKind.OVERHEAD->air==null||condition.minimumMeters==null||!condition.datumKnown||condition.minimumMeters<air
            }
            if(blocked&&condition.geometry.isWithinDistance(line,margin))return false
        }
        // 含原像元的混合资料须走精确船型派生；不以稀疏采样略过沿线浅区。
        if(required!=null&&rasters.any{it.mask.envelopeInternal.intersects(envelope)&&it.mask.intersects(trace)})return null
        val projection=PassageProjection(base.header.region.center,check)
        for(avoid in request.avoidances) {
            check();val shape=projection.geometry(ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(avoid.boundary))))
            if(shape.isWithinDistance(line,margin))return false
        }
        return true
    }

    /** 只读取编译产物；修改船型不再触发全国对象读取、来源优先级解析或海岸投影。 */
    suspend fun derive(base:PassageRegionProduct,request:PassageRequest):PassageWorld {
        val work=currentCoroutineContext();val projection=PassageProjection(base.header.region.center){work.ensureActive()}
        val operations=PassageGeometryOperations{work.ensureActive()}
        val vessel=request.vessel
        val margin=max(1.0,max(vessel.corridorHalfWidthMeters?:0.0,(vessel.beamMeters?:0.0)/2+(vessel.clearanceMarginMeters?:0.0)))
        val required=vessel.draftMeters?.takeIf{it.isFinite()&&it>0}?.plus(vessel.minimumUnderKeelMeters?:0.0)
        val air=vessel.airDraftMeters?.takeIf{it.isFinite()&&it>0}?.plus(vessel.clearanceMarginMeters?:0.0)
        // 基础面已保留一米几何容差；参数只能收缩，不能扩大资料证明的水域。
        var water=if(margin>base.header.margin)operations.buffer(base.waterWithHalo,-(margin-base.header.margin)) else base.waterWithHalo
        val exclusions=ArrayList<Geometry>()
        for(condition in constraints) {
            work.ensureActive()
            val blocked=when(condition.kind) {
                PassageConstraintKind.DEPTH->required!=null&&condition.minimumMeters?.let{it<required}==true
                PassageConstraintKind.OVERHEAD->air==null||condition.minimumMeters==null||!condition.datumKnown||condition.minimumMeters<air
            }
            if(blocked)exclusions+=operations.buffer(condition.geometry,margin)
        }
        if(required!=null)for(raster in rasters) {
            work.ensureActive()
            // 基础面已去除陆地/NoData；这里只重新分类确实不够深的原像元，不读取外部文件。
            val shallow=rasterPassageGeometry(raster.source,projection,required).areas.filter{it.kind==RasterPassageKind.SHALLOW}
            for(area in shallow) {
                val local=operations.intersection(area.geometry,raster.mask)
                if(!local.isEmpty)exclusions+=operations.buffer(local,area.edgeAllowanceMeters+margin)
            }
        }
        for(avoid in request.avoidances) {
            work.ensureActive()
            val shape=projection.geometry(ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(avoid.boundary))))
            if(shape.envelopeInternal.intersects(water.envelopeInternal))exclusions+=operations.buffer(shape,margin)
        }
        if(exclusions.isNotEmpty())water=operations.difference(water,union(exclusions,projection.factory))
        val empty=projection.factory.createPolygon()
        return PassageWorld(projection,emptyList(),base.waterWithHalo,water,base.header.malformed,margin,
            empty,emptyList(),emptyList(),empty,searchBounds=water.envelopeInternal)
    }
}
