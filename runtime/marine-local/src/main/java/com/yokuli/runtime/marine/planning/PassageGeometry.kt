package com.yokuli.runtime.marine.planning

import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.*
import com.yokuli.runtime.marine.chart.LinzLdsAdapter
import com.yokuli.runtime.marine.chart.hasUncertainChartGeometry
import net.sf.geographiclib.Geodesic
import org.locationtech.jts.geom.*
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.operation.union.UnionStrategy
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.geom.util.GeometryFixer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import java.util.PriorityQueue
import kotlin.math.*

/** 每个局部检查块用 WGS84 反解建立米制方位等距坐标；长航段分块，日期线不变成跨全球直线。 */
internal class PassageProjection(val origin:ChartPoint,private val check:()->Unit={}) {
    val factory=GeometryFactory()
    // coverage、邻接水深面和陆地边界经常引用同一坐标；每个 world 只做一次昂贵的椭球反解。
    // 缓存只属于本次局部投影，不跨资料版本；有界，且不改变原始点或几何精度。
    private val positions=object:LinkedHashMap<ChartPoint,Coordinate>(4096,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<ChartPoint,Coordinate>?)=size>65_536
    }
    private var projectedPoints=0
    fun xy(p:ChartPoint):Coordinate {
        if(++projectedPoints%128==0)check()
        val key=if(p.depthMeters==null)p else p.copy(depthMeters=null)
        return positions[key]?.let(::Coordinate) ?: run {
            val d=Geodesic.WGS84.Inverse(origin.latitude,origin.longitude,p.latitude,p.longitude)
            val a=Math.toRadians(d.azi1)
            Coordinate(d.s12*sin(a),d.s12*cos(a)).also{positions[key]=Coordinate(it)}
        }
    }
    fun point(c:Coordinate):ChartPoint {val d=Geodesic.WGS84.Direct(origin.latitude,origin.longitude,Math.toDegrees(atan2(c.x,c.y)),hypot(c.x,c.y));return ChartPoint(d.lat2,d.lon2)}
    fun line(points:List<ChartPoint>):LineString=factory.createLineString(points.map(::xy).toTypedArray())
    fun geometry(value:ChartGeometry):Geometry {
        fun ring(part:ChartGeometryPart):LinearRing {val p=part.points.map(::xy).toMutableList();if(p.isNotEmpty()&&!p.first().equals2D(p.last()))p.add(p.first());require(p.size>=4){"Incomplete polygon"};return factory.createLinearRing(p.toTypedArray())}
        return when(value.kind){
            ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT->factory.createMultiPointFromCoords(value.parts.flatMap{it.points}.map(::xy).toTypedArray())
            ChartGeometryKind.LINE->factory.createMultiLineString(value.parts.filter{it.points.size>=2}.map{line(it.points)}.toTypedArray())
            ChartGeometryKind.POLYGON->{
                val shells=value.parts.filterNot{it.hole}.map{factory.createPolygon(ring(it))}
                val holes=value.parts.filter{it.hole}.map(::ring)
                require(shells.isNotEmpty()){"Missing outer boundary"}
                // 一个孔只属于完整包含它的最小外环；首点落入不能证明整个孔的归属。
                val assigned=holes.groupBy {hole->
                    val area=factory.createPolygon(hole)
                    shells.filter{it.covers(area)}.minByOrNull{it.area}?:error("Unattached polygon hole")
                }
                val polygons=shells.map{shell->factory.createPolygon(shell.exteriorRing as LinearRing,assigned[shell].orEmpty().toTypedArray())}
                factory.createMultiPolygon(polygons.toTypedArray()).also{require(it.isValid){"Invalid chart polygon"}}
            }
            ChartGeometryKind.NONE->factory.createGeometryCollection()
        }
    }
}
internal fun distance(a:ChartPoint,b:ChartPoint)=Geodesic.WGS84.Inverse(a.latitude,a.longitude,b.latitude,b.longitude).s12
internal fun atDistance(a:ChartPoint,b:ChartPoint,meters:Double):ChartPoint {val inv=Geodesic.WGS84.Inverse(a.latitude,a.longitude,b.latitude,b.longitude);val p=Geodesic.WGS84.Direct(a.latitude,a.longitude,inv.azi1,meters);return ChartPoint(p.lat2,p.lon2)}
internal fun passageHash(value:Any):String=MessageDigest.getInstance("SHA-256").digest(value.toString().toByteArray()).take(16).joinToString(""){"%02x".format(it)}
private fun repairedGeometry(value:Geometry):Geometry =
    if(value.isEmpty||value.isValid)value else GeometryFixer.fix(value)

private fun robustOverlay(a:Geometry,b:Geometry,operation:Int):Geometry =
    repairedGeometry(OverlayNGRobust.overlay(repairedGeometry(a),repairedGeometry(b),operation))

private fun robustIntersection(a:Geometry,b:Geometry):Geometry=robustOverlay(a,b,OverlayNG.INTERSECTION)
private fun robustDifference(a:Geometry,b:Geometry):Geometry=robustOverlay(a,b,OverlayNG.DIFFERENCE)
private fun robustUnionPair(a:Geometry,b:Geometry):Geometry=robustOverlay(a,b,OverlayNG.UNION)
private fun robustBuffer(value:Geometry,distance:Double):Geometry=repairedGeometry(repairedGeometry(value).buffer(distance))

internal fun union(values:List<Geometry>,factory:GeometryFactory):Geometry {
    if(values.isEmpty())return factory.createPolygon()
    val operation=UnaryUnionOp(values.map(::repairedGeometry),factory)
    operation.setUnionFunction(object:UnionStrategy {
        override fun union(a:Geometry,b:Geometry)=robustUnionPair(a,b)
        override fun isFloatingPrecision()=true
    })
    return repairedGeometry(operation.union())
}
internal fun around(points:List<ChartPoint>,paddingMeters:Double):ChartBounds {
    val first=points.first().longitude
    val longs=points.map{first+((it.longitude-first+540)%360-180)}
    val lat=points.map{it.latitude};val padLat=paddingMeters/110_000.0
    val padLon=padLat/cos(Math.toRadians(lat.maxOf{abs(it)})).coerceAtLeast(0.01)
    val lo=longs.min()-padLon;val hi=longs.max()+padLon
    fun norm(v:Double)=((v+540)%360)-180
    return ChartBounds(if(hi-lo>=360)-180.0 else norm(lo),(lat.min()-padLat).coerceAtLeast(-89.99),if(hi-lo>=360)180.0 else norm(hi),(lat.max()+padLat).coerceAtMost(89.99))
}
internal data class FeatureGeometry(val feature:NauticalFeature,val geometry:Geometry)
internal enum class PassageWorldPurpose { FULL_ANALYSIS, REFERENCE_DRAFT }
internal data class PassageWorld(
    val projection:PassageProjection,val features:List<FeatureGeometry>,val coverage:Geometry,val navigable:Geometry,
    val malformed:List<String>,val margin:Double,
    /** 实际有数值的参考水深，与 ENC datum 证据保持区分。 */
    val rasterKnownDepth:Geometry,val rasterAreas:List<RasterPassageArea>,val referenceAreas:List<PassageReferenceArea>,
    /** 外部未知/浅区边界的像元余量，分析和搜索使用同一集合；文件之间的接缝不在其中。 */
    val rasterBoundaryUncertainty:Geometry,
    /** 仅参考草稿模式保留；实际成功路径与这些对象相交时必须报告基准未知。 */
    val referenceDatumFeatures:List<FeatureGeometry> = emptyList(),
    /** 本次实际加载/裁剪的搜索范围；扩展 world 后搜索必须使用这个范围。 */
    val searchBounds:Envelope? = null,
)

internal class PassageGeometry(private val charts:ChartDataService) {
    suspend fun world(snapshot:ChartDataSnapshot,request:PassageRequest,points:List<ChartPoint>,padding:Double,
        purpose:PassageWorldPurpose=PassageWorldPurpose.FULL_ANALYSIS,preferredScaleDenominator:Int?=null,
        onProgress:(Float,String)->Unit={_,_->}):PassageWorld {
        val job=currentCoroutineContext()
        val projection=PassageProjection(points.first()){job.ensureActive()};val factory=projection.factory
        // 仍使用 JTS 空间分组并集；在每次内部归并之间允许取消，不等整个海岸集合完成。
        fun union(values:List<Geometry>,factory:GeometryFactory):Geometry {
            job.ensureActive()
            if(values.isEmpty())return factory.createPolygon()
            val operation=UnaryUnionOp(values.map(::repairedGeometry),factory)
            operation.setUnionFunction(object:UnionStrategy {
                override fun union(a:Geometry,b:Geometry):Geometry {
                    job.ensureActive()
                    return robustUnionPair(a,b).also{job.ensureActive()}
                }
                override fun isFloatingPrecision()=true
            })
            return repairedGeometry(operation.union()).also{job.ensureActive()}
        }
        val vessel=request.vessel
        val configuredMargin=max(vessel.corridorHalfWidthMeters?:0.0,(vessel.beamMeters?:0.0)/2+(vessel.clearanceMarginMeters?:0.0))
        // Do not invent a 25 m half-corridor when vessel width is unknown: that can erase a real
        // narrow channel before search even begins. Source uncertainty (e.g. GEBCO half-cell) is
        // accounted for separately; 1 m here is only a geometric tolerance.
        val margin=max(1.0,configuredMargin)
        // 吃水本身足够参与粗略自动规划；额外 UKC 未设置时按 0 处理并在结果里提示核对。
        val required=vessel.draftMeters?.takeIf{it.isFinite()&&it>0}?.let{draft->draft+(vessel.minimumUnderKeelMeters?:0.0)}
        val rasterAllowance=snapshot.datasets.flatMap{it.rasters.orEmpty()}.maxOfOrNull { hypot(it.pixelWidthDegrees,it.pixelHeightDegrees)*111_320.0*.5 }?:0.0
        val localPadding=max(padding,rasterAllowance+margin+100.0)
        val bounds=around(points,localPadding)
        val window=PassageGeometryWindow(bounds,projection){job.ensureActive()}
        onProgress(.02f,"读取选定区域资料 / Reading selected area")

        val datasetOrder=request.datasetIds.withIndex().associate{it.value to it.index}
        val unsortedCells=snapshot.datasets.flatMap{dataset->
            dataset.cells.groupBy{it.cellId}.values.map{versions->
                versions.maxWith(compareBy<ChartCellRevision>{it.edition}.thenBy{it.update})
            }.filterNot{it.cancelled}.map{Triple(datasetOrder[dataset.id]?:Int.MAX_VALUE,dataset,it)}
        }
        val manualOrder=unsortedCells.any{it.third.priorityExplicit}
        fun sourceClass(entry:Triple<Int,ChartDataset,ChartCellRevision>)=when {
            entry.third.detailTier()!=null->0
            entry.third.featureCount>0->1
            entry.second.rasters.orEmpty().any{it.cellId==entry.third.cellId}->2
            else->3
        }
        fun rasterResolution(entry:Triple<Int,ChartDataset,ChartCellRevision>)=
            entry.second.rasters.orEmpty().filter{it.cellId==entry.third.cellId}
                .minOfOrNull{max(it.pixelWidthDegrees,it.pixelHeightDegrees)}?:Double.POSITIVE_INFINITY
        val allCells=unsortedCells.sortedWith(
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

        fun touchesBounds(cell:ChartCellRevision):Boolean =
            cell.bounds.isEmpty()||cell.bounds.any{box->box.split().any{x->bounds.split().any{y->
                x.east>=y.west&&x.west<=y.east&&x.north>=y.south&&x.south<=y.north
            }}}
        fun preferredFeatureTiers(target:Int):Set<Int> {
            val primary=detailTierForScale(target)?:0
            return listOf(primary,(primary+1).takeIf{it<=4}).filterNotNull().toSet()
        }
        val detailTiers=preferredScaleDenominator?.let(::preferredFeatureTiers).orEmpty()

        val lodCells=preferredScaleDenominator?.let{target->
            val nearby=allCells.map{it.third}.filter(::touchesBounds)
            val tiers=preferredFeatureTiers(target)
            nearby.filter{cell->cell.detailTier()?.let{it in tiers}!=false}.map{it.cellId}.toSet()
        }?.takeIf{it.isNotEmpty()}

        val cells=if(lodCells==null)allCells else allCells.filter{it.third.cellId in lodCells}
        val draftKinds=setOf(
            NauticalFeatureKind.LAND,NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,
            NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.OBSTRUCTION,NauticalFeatureKind.WRECK,
            NauticalFeatureKind.ROCK,NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD,
            NauticalFeatureKind.RESTRICTED,NauticalFeatureKind.TRAFFIC,NauticalFeatureKind.OTHER,
            NauticalFeatureKind.COVERAGE
        )
        val validationKinds=setOf(
            NauticalFeatureKind.LAND,NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,
            NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.OBSTRUCTION,NauticalFeatureKind.WRECK,
            NauticalFeatureKind.ROCK,NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD,
            NauticalFeatureKind.SOUNDING,NauticalFeatureKind.OTHER,NauticalFeatureKind.COVERAGE
        )

        val rasterWindows=charts.rasterWindows(snapshot.id,bounds,maxCells=262_144).groupBy{"${it.grid.datasetId}/${it.grid.cellId}"}
        val rasterAreas=ArrayList<RasterPassageArea>();val referenceAreas=ArrayList<PassageReferenceArea>()
        val rasterCoverage=ArrayList<Geometry>()
        val features=ArrayList<NauticalFeature>();var cursor:String?=null
        do {
            job.ensureActive()
            val page=when {
                preferredScaleDenominator!=null->
                    charts.querySpatial(snapshot.id,bounds,ChartSpatialFilter(cellIds=lodCells.orEmpty(),kinds=draftKinds,detailTiers=detailTiers),2000,cursor)
                purpose==PassageWorldPurpose.REFERENCE_DRAFT->
                    charts.querySpatial(snapshot.id,bounds,ChartSpatialFilter(kinds=validationKinds),2000,cursor)
                else->charts.query(snapshot.id,bounds,2000,cursor)
            }
            require(!page.truncated){"Chart query is incomplete"}
            features.addAll(page.features)
            require(features.size<=120_000){"Area contains too many chart objects; use a shorter passage"}
            require(!page.hasMore||page.nextAfterId!=null&&page.nextAfterId!=cursor){"Chart query cursor did not advance"}
            cursor=if(page.hasMore)page.nextAfterId else null
            onProgress(.12f,"已读取 ${features.size} 个区域对象 / ${features.size} area objects loaded")
        }while(cursor!=null)
        val malformed=mutableListOf<String>()
        // 和搜索网格相同的局部矩形；远方图幅及已被上层覆盖的单元不能污染本区域质量状态。
        val region=projection.line(points).envelope.buffer(max(1.0,localPadding)).envelope
        val preparedRegion=PreparedGeometryFactory.prepare(region)
        // coverage 与对象索引可能引用同一个面；当前 world 内投影一次即可。
        val coverageShapes=mutableMapOf<String,Geometry>()
        fun regionShape(id:String,value:ChartGeometry):Geometry=coverageShapes.getOrPut(id){
            val local=window.geometry(value)
            if(local.isEmpty||preparedRegion.covers(local))local else robustIntersection(local,region)
        }
        var occupied:Geometry=factory.createPolygon()
        val masks=mutableMapOf<String,Geometry>()
        val rawMasks=mutableMapOf<String,Geometry>()
        val uncertainMasks=mutableMapOf<String,Geometry>()
        val featuresByCell=features.groupBy{"${it.datasetId}/${it.cellId}"}
        val uncertainByCell=featuresByCell.mapValues{(_,values)->values.filter{it.hasUncertainChartGeometry()}}
        fun touchesQuery(box:ChartBounds)=box.split().any{part->bounds.split().any{query->part.west<=query.east&&part.east>=query.west&&part.south<=query.north&&part.north>=query.south}}
        fun hintArea(cell:ChartCellRevision):Geometry {
            // 连可定位边界都缺失时无法证明问题在远方，保守保留本区未知状态。
            if(cell.bounds.none {it.valid})return region
            val shapes=cell.bounds.filter{it.valid&&touchesQuery(it)}.flatMap{it.split()}.mapNotNull { box ->
                if(box.east-box.west>=180.0)return@mapNotNull region
                runCatching { window.geometry(ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(listOf(
                    ChartPoint(box.south,box.west),ChartPoint(box.south,box.east),ChartPoint(box.north,box.east),
                    ChartPoint(box.north,box.west),ChartPoint(box.south,box.west)))))).let{robustIntersection(it,region)} }
                    .onFailure{if(it is kotlinx.coroutines.CancellationException)throw it}.getOrNull()
            }
            return union(shapes,factory)
        }
        for((cellIndex,entry) in cells.withIndex()){
            val (_,dataset,cell)=entry
            currentCoroutineContext().ensureActive()
            onProgress(.15f+.25f*cellIndex/cells.size.coerceAtLeast(1),"整理覆盖与来源优先级 / Resolving coverage and source priority")
            if(!dataset.allowsPassageDrafting(System.currentTimeMillis())||!dataset.offlineReadable||dataset.issue!=null)continue
            val cellKey="${dataset.id}/${cell.cellId}"
            val uncertainFeatures=uncertainByCell[cellKey].orEmpty()
            val declaredBounds=cell.bounds.filter{it.valid}
            if(uncertainFeatures.isEmpty()&&declaredBounds.isNotEmpty()&&declaredBounds.none(::touchesQuery))continue
            // 新 GPKG 区分不可定位/覆盖结构问题和逐对象问题；旧版本与 S-57 保留原有保守门槛。
            val hasWholeCellIssue=cell.wholeCellIssues?.any(::isBlockingChartIssue)
                ?: (cell.hasUnsupportedSemantic||cell.issues.any(::isBlockingChartIssue))
            val rasterMetadata=dataset.rasters.orEmpty().filter{it.cellId==cell.cellId}
            if(rasterMetadata.isNotEmpty()){
                val windows=rasterWindows[cellKey].orEmpty()
                if(windows.isEmpty()){
                    // 已声明落在本区却读不到窗口，不能把更低优先数据补上当作同一来源。
                    val unknown=robustDifference(hintArea(cell),occupied)
                    if(!unknown.isEmpty)malformed+=cell.cellId
                    occupied=robustUnionPair(occupied,hintArea(cell));continue
                }
                val raster=windows.map{rasterPassageGeometry(it,projection,required)}
                val footprint=robustIntersection(union(raster.map{it.footprint},factory),region)
                val effective=robustDifference(footprint,occupied)
                if(!effective.isEmpty){
                    if(hasWholeCellIssue)malformed+=cell.cellId
                    for(area in raster.flatMap{it.areas}){
                        currentCoroutineContext().ensureActive()
                        val clipped=robustIntersection(area.geometry,effective)
                        if(!clipped.isEmpty){
                            require(rasterAreas.size<32_768){"叠加栅格边界超出局部预算，请缩短航段 / Combined raster boundaries exceed the local budget; shorten the leg"}
                            rasterAreas+=area.copy(geometry=clipped)
                            if(area.kind!=RasterPassageKind.UNKNOWN)rasterCoverage+=clipped
                        }
                    }
                    val resolution=rasterMetadata.maxOf{max(it.pixelWidthDegrees,it.pixelHeightDegrees)*3600.0}
                    referenceAreas+=PassageReferenceArea(effective,cell.cellId,
                        "GEBCO 参考地形（${"%.1f".format(java.util.Locale.ROOT,resolution)}″）：未插值，无碍航物及海图基准保证 / GEBCO reference grid; native cells, no obstacle or chart-datum assurance")
                }
                // footprint 包含 NoData。优先来源中的未知/浅区不能从下级来源借深度填补。
                occupied=robustUnionPair(occupied,footprint)
                continue
            }
            // 局部未知对象可能完全位于该 cell 的可靠覆盖之外。以处理该来源前的
            // unoccupied region 为掩膜保留它，不能裁没后再从低优先级资料借深度。
            val available=robustDifference(region,occupied)
            val uncertainAreas=uncertainFeatures.map{feature->
                try {robustIntersection(regionShape(feature.id,feature.geometry),available)}
                catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                catch(_:Exception){malformed.add(feature.id);available}
            }
            val uncertainty=union(uncertainAreas,factory)
            if(!uncertainty.isEmpty)uncertainMasks[cellKey]=available
            var brokenCoverage=false
            fun coverageGeometry(evidence:CoverageEvidence):Geometry? = runCatching {
                // 先按地理窗口裁真实边界，再投影局部结果；不再投影整条全国海岸后裁掉绝大部分。
                regionShape(evidence.featureId,evidence.geometry)
            }.onFailure{if(it is kotlinx.coroutines.CancellationException)throw it;brokenCoverage=true}.getOrNull()
            // LINZ reference catalogues from older app versions may contain a persisted
            // tier computed with obsolete band boundaries. For reference-only LINZ coarse planning,
            // derive coverage from the selected DEPARE/DRGARE tier every time instead of trusting
            // catalogue tier metadata. Formal ENC coverage can use its exact compilationScale.
            val deriveTierCoverage=detailTiers.isNotEmpty()&&cell.referenceOnly
            val coverageEvidence=if(detailTiers.isEmpty()||deriveTierCoverage)cell.coverage else cell.coverage.filter {evidence->
                evidence.resolvedDetailTier()?.let{it in detailTiers}!=false
            }
            val selectedDepthCoverage=if(deriveTierCoverage) {
                featuresByCell[cellKey].orEmpty().filter {feature->
                    feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)&&
                        feature.geometry.kind==ChartGeometryKind.POLYGON&&feature.detailTier()?.let{it in detailTiers}!=false
                }.mapNotNull {feature->
                    runCatching{regionShape(feature.id,feature.geometry)}
                        .onFailure{if(it is kotlinx.coroutines.CancellationException)throw it;brokenCoverage=true}
                        .getOrNull()
                }
            } else emptyList()
            val valid=if(deriveTierCoverage)selectedDepthCoverage else coverageEvidence.filter{it.covered}.mapNotNull(::coverageGeometry)
            val gaps=if(deriveTierCoverage)emptyList() else coverageEvidence.filterNot{it.covered}.mapNotNull(::coverageGeometry)
            val coverage=robustDifference(union(valid,factory),union(gaps,factory))
            rawMasks[cellKey]=robustDifference(robustIntersection(coverage,region),uncertainty)
            val effective=robustDifference(robustIntersection(coverage,available),uncertainty)
            masks[cellKey]=effective
            if(!effective.isEmpty&&cell.referenceOnly)referenceAreas+=PassageReferenceArea(effective,cell.cellId,
                "LINZ LDS 参考资料：核对正式海图、来源日期与限制 / LINZ LDS reference data; review official charts, dates and limitations")
            if(!effective.isEmpty&&cell.issues.contains("SURVEY_QUALITY_UNSPECIFIED"))referenceAreas+=PassageReferenceArea(effective,cell.cellId,
                "测量质量未明确，请复核来源 / Survey quality is unspecified; review the source")
            // 缺少/损坏覆盖时只用边界判断“可能影响本区”，绝不把边界当作已知覆盖。
            val selectedCoverageMissing=if(deriveTierCoverage)selectedDepthCoverage.isEmpty() else coverageEvidence.none{it.covered}
            val uncertain=if(brokenCoverage||selectedCoverageMissing)robustDifference(hintArea(cell),occupied)else factory.createPolygon()
            if((!effective.isEmpty||!uncertain.isEmpty)&&(brokenCoverage||hasWholeCellIssue||selectedCoverageMissing))malformed.add(cell.cellId)
            occupied=robustUnionPair(robustUnionPair(occupied,coverage),uncertainty)
        }

        // Resolve feature-level source ownership globally, not merely inside one file. Mixed
        // GeoPackages can contain a tiny harbour-scale layer plus broad coarse layers, so file order
        // must not let the broad layer hide a finer overlapping source from another file.
        data class TierSource(
            val cellKey:String,val cell:ChartCellRevision,val scale:Int,
            val features:List<NauticalFeature>,val base:Geometry
        )
        val cellByKey=cells.associate {(_,dataset,cell)->"${dataset.id}/${cell.cellId}" to cell}
        val sources=features.groupBy{"${it.datasetId}/${it.cellId}"}.flatMap {(cellKey,objects)->
            val cell=cellByKey[cellKey]?:return@flatMap emptyList()
            val base=(if(manualOrder)masks[cellKey] else rawMasks[cellKey]?:masks[cellKey])?:return@flatMap emptyList()
            objects.mapNotNull{it.detailScaleDenominator()}.distinct().map {scale->
                TierSource(cellKey,cell,scale,objects.filter{it.detailScaleDenominator()==scale},base)
            }
        }.sortedWith(
            if(manualOrder)
                compareBy<TierSource>{it.cell.priority}
                    .thenBy{detailTierForScale(it.scale)?:Int.MAX_VALUE}
                    .thenBy{it.scale}
                    .thenBy{it.cellKey}
            else
                compareBy<TierSource>{detailTierForScale(it.scale)?:Int.MAX_VALUE}
                    .thenBy{it.scale}
                    .thenBy{it.cell.priority}
                    .thenBy{it.cellKey}
        )
        val tierMasks=mutableMapOf<Pair<String,Int>,Geometry>()
        var occupiedTier:Geometry=factory.createPolygon()
        for(source in sources) {
            job.ensureActive()
            val explicit=source.features.filter{it.kind==NauticalFeatureKind.COVERAGE&&it.geometry.kind==ChartGeometryKind.POLYGON}
            val coveredFeatures=explicit.filter{it.attributes["CATCOV"]=="1"}.ifEmpty {
                source.features.filter{it.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)&&it.geometry.kind==ChartGeometryKind.POLYGON}
            }
            if(coveredFeatures.isEmpty())continue
            val coveredShapes=coveredFeatures.mapNotNull {feature->
                runCatching{regionShape(feature.id,feature.geometry)}
                    .onFailure{if(it is kotlinx.coroutines.CancellationException)throw it;malformed.add(feature.id)}
                    .getOrNull()
            }
            val gapShapes=explicit.filter{it.attributes["CATCOV"]=="2"}.mapNotNull {feature->
                runCatching{regionShape(feature.id,feature.geometry)}
                    .onFailure{if(it is kotlinx.coroutines.CancellationException)throw it;malformed.add(feature.id)}
                    .getOrNull()
            }
            if(coveredShapes.isEmpty())continue
            val sourceCoverage=robustIntersection(
                robustDifference(union(coveredShapes,factory),union(gapShapes,factory)),source.base
            )
            val effectiveTier=robustDifference(sourceCoverage,occupiedTier)
            tierMasks[source.cellKey to source.scale]=effectiveTier
            occupiedTier=robustUnionPair(occupiedTier,sourceCoverage)
        }

        val sourceOwnershipKinds=setOf(
            NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,
            NauticalFeatureKind.LAND,NauticalFeatureKind.DRYING_AREA
        )
        val sourceBoundKinds=sourceOwnershipKinds+setOf(
            NauticalFeatureKind.SOUNDING,NauticalFeatureKind.DEPTH_CONTOUR,NauticalFeatureKind.QUALITY
        )
        val preparedMasks=java.util.IdentityHashMap<Geometry,org.locationtech.jts.geom.prep.PreparedGeometry>()
        val projected=features.mapIndexedNotNull{index,feature->
            if(index%128==0){job.ensureActive();onProgress(.4f+.3f*index/features.size.coerceAtLeast(1),"处理水域与障碍 ${index}/${features.size} / Processing water and obstacles")}
            val key="${feature.datasetId}/${feature.cellId}"
            val normalMask=when {
                feature.kind in sourceBoundKinds->
                    feature.detailScaleDenominator()?.let{tierMasks[key to it]} ?: masks[key]
                manualOrder->masks[key]
                else->rawMasks[key]?:masks[key]
            }
            val mask=(if(feature.hasUncertainChartGeometry())uncertainMasks[key] else normalMask)?:return@mapIndexedNotNull null
            if(mask.isEmpty)return@mapIndexedNotNull null
            // 局部几何的绝大多数点/小面完全位于来源掩膜内；无需为每个对象重新执行 overlay。
            runCatching{
                val local=coverageShapes[feature.id]?:window.geometry(feature.geometry)
                val prepared=preparedMasks.getOrPut(mask){PreparedGeometryFactory.prepare(mask)}
                val clipped=when {
                    local.isEmpty||!local.envelopeInternal.intersects(mask.envelopeInternal)->factory.createGeometryCollection()
                    prepared.covers(local)->local
                    else->robustIntersection(local,mask)
                }
                FeatureGeometry(feature,clipped)
            }.onFailure{if(it is kotlinx.coroutines.CancellationException)throw it;malformed.add(feature.id)}.getOrNull()?.takeUnless{it.geometry.isEmpty}
        }
        // 不同港区的垂直基准不会封锁全国；当前查询窗口内仍不能混用同一资料单元的深度基准。
        projected.groupBy{Triple(it.feature.datasetId,it.feature.cellId,it.feature.detailScaleDenominator())}.forEach{(source,objects)->
            if(objects.mapNotNull{it.feature.depth?.datum?.trim()?.uppercase(java.util.Locale.ROOT)?.takeIf(String::isNotEmpty)}.distinct().size>1)
                malformed.add("${source.first}/${source.second}@${source.third ?: "unscaled"}")
        }
        val referenceCells=if(purpose==PassageWorldPurpose.REFERENCE_DRAFT)cells.filter{(_,_,cell)->
            cell.referenceOnly&&LinzLdsAdapter.REFERENCE_ISSUE in cell.issues
        }.map{(_,dataset,cell)->"${dataset.id}/${cell.cellId}"}.toSet()else emptySet()
        val referenceDatumFeatures=projected.filter{item->
            val feature=item.feature;val depth=feature.depth
            "${feature.datasetId}/${feature.cellId}" in referenceCells&&
                LinzLdsAdapter.REFERENCE_ISSUE in feature.issues&&"GPKG_VERTICAL_DATUM_MISSING" in feature.issues&&
                feature.issues.none{isBlockingChartIssue(it)&&it!="GPKG_VERTICAL_DATUM_MISSING"}&&
                depth!=null&&depth.datum.isNullOrBlank()&&when(feature.kind) {
                    NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA->
                        feature.geometry.kind==ChartGeometryKind.POLYGON&&depth.kind==DepthEvidenceKind.INTERVAL&&
                            depth.lowerMeters?.let{it.isFinite()&&it>=0}==true&&
                            (depth.upperMeters?.let{it.isFinite()&&it>=requireNotNull(depth.lowerMeters)}!=false)
                    NauticalFeatureKind.SOUNDING->feature.geometry.kind in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT)&&
                        depth.kind==DepthEvidenceKind.POINT&&depth.pointMeters?.let{it.isFinite()&&it>=0}==true
                    NauticalFeatureKind.DEPTH_CONTOUR->feature.geometry.kind==ChartGeometryKind.LINE&&
                        depth.kind==DepthEvidenceKind.CONTOUR&&depth.pointMeters?.let{it.isFinite()&&it>=0}==true
                    else->false
                }
        }
        val referenceDatumIds=referenceDatumFeatures.map{it.feature.id}.toSet()
        fun blocksSearch(feature:NauticalFeature)=feature.issues.any{issue->
            isBlockingChartIssue(issue)&&!(feature.id in referenceDatumIds&&issue=="GPKG_VERTICAL_DATUM_MISSING")
        }
        onProgress(.72f,"建立可通过水域 / Building searchable water")
        val depthAreas=projected.filter {fg->
            val feature=fg.feature;val d=feature.depth;val low=d?.lowerMeters
            feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)&&
                d?.kind==DepthEvidenceKind.INTERVAL&&(!d.datum.isNullOrBlank()||feature.id in referenceDatumIds)&&
                low!=null&&low.isFinite()&&low>=0&&(required==null||low>=required)&&!blocksSearch(feature)
        }.map{it.geometry}
        // 自动出线只把“物理上不可通过/无法解释”的对象当硬障碍。
        // 限制区和交通规则属于 REVIEW，不应把粗略航线生成本身卡死。
        val vectorBlocked=projected.filter{it.feature.kind in setOf(
            NauticalFeatureKind.LAND,NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.OBSTRUCTION,
            NauticalFeatureKind.WRECK,NauticalFeatureKind.ROCK
        )||blocksSearch(it.feature)}.map{it.geometry}.toMutableList()
        val blocked=mutableListOf<Geometry>()
        projected.filter{it.feature.kind in setOf(NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD)}.forEach {fg->
            val clear=(fg.feature.attributes["VERCLR"]?.toDoubleOrNull()?:fg.feature.attributes["VERCCL"]?.toDoubleOrNull())
                ?.takeIf{it.isFinite()&&it>=0}
            val need=vessel.airDraftMeters?.takeIf{it.isFinite()&&it>0}?.let{it+(vessel.clearanceMarginMeters?:0.0)}
            // 有明确船高、净空和垂直基准且足够时允许粗略通过；其余情况继续保守避开。
            if(clear==null||need==null||fg.feature.source.verticalDatum==null||clear<need)
                vectorBlocked.add(fg.geometry)
        }
        // 重叠深度证据取保守交集：浅区/未知区不能被旁边的深区union盖掉。
        projected.filter{it.feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)}.forEach {fg->
            val d=fg.feature.depth
            if(d?.kind!=DepthEvidenceKind.INTERVAL||(d.datum.isNullOrBlank()&&fg.feature.id !in referenceDatumIds)||d.lowerMeters?.let{it.isFinite()&&it>=0&&(required==null||it>=required)}!=true)
                vectorBlocked.add(fg.geometry)
        }
        projected.filter{it.feature.kind==NauticalFeatureKind.SOUNDING}.forEach{fg->fg.feature.geometry.parts.flatMap{it.points}.forEach{p->
            val at=factory.createPoint(projection.xy(p))
            if(fg.geometry.covers(at)&&p.depthMeters?.let{required!=null&&it<required}==true)vectorBlocked.add(at)
        }}
        request.avoidances.forEach{a->runCatching{window.geometry(ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(a.boundary))))}.onSuccess{if(!it.isEmpty)vectorBlocked.add(it)}.onFailure{if(it is kotlinx.coroutines.CancellationException)throw it;malformed.add(a.id)}}
        // LAND/面、礁石/测深点、桥线等可能混合 0/1/2 维。OverlayNG 不接受 mixed-dimension
        // GeometryCollection 直接做 UNION；先分别扩成同一二维禁入面再合并，几何意义等价于
        // 对这些对象的并集取同一安全余量，同时避免把 JTS 的维度错误冒充成“陆地/无路”。
        if(vectorBlocked.isNotEmpty())blocked+=union(
            vectorBlocked.map{robustBuffer(it,max(1.0,margin))},factory
        )
        currentCoroutineContext().ensureActive()
        val resolutionAllowances=rasterAreas.groupBy{it.grid.pixelWidthDegrees to it.grid.pixelHeightDegrees}
            .mapValues{(_,areas)->areas.maxOf{it.edgeAllowanceMeters}}
        rasterAreas.indices.forEach { index ->
            val area=rasterAreas[index]
            rasterAreas[index]=area.copy(edgeAllowanceMeters=resolutionAllowances.getValue(area.grid.pixelWidthDegrees to area.grid.pixelHeightDegrees))
        }
        val rawRasterDeep=rasterAreas.filter{it.kind==RasterPassageKind.DEEP}
        // 先合并全部可用深水。文件边界不是岸线；高优先 ENC 的真实深水面也可接续参考栅格。
        val combinedDeep=if(rawRasterDeep.isEmpty())factory.createPolygon()else union(depthAreas+rawRasterDeep.map{it.geometry},factory)
        val rasterDeep=rawRasterDeep.groupBy{it.grid.pixelWidthDegrees to it.grid.pixelHeightDegrees}.values.map { areas ->
            currentCoroutineContext().ensureActive()
            robustIntersection(robustBuffer(combinedDeep,-areas.maxOf{it.edgeAllowanceMeters}),union(areas.map{it.geometry},factory))
        }
        val rasterBoundaryUncertainty=robustDifference(union(rawRasterDeep.map{it.geometry},factory),union(rasterDeep,factory))
        // 独立扣除真实非深水风险；相邻文件或矢量深水不能把浅区/陆地/NoData 的保守余量盖掉。
        rasterAreas.filter{it.kind!=RasterPassageKind.DEEP}
            .groupBy{Triple(it.kind,it.grid.pixelWidthDegrees,it.grid.pixelHeightDegrees)}.values.forEach { areas ->
                currentCoroutineContext().ensureActive()
                blocked+=robustBuffer(union(areas.map{it.geometry},factory),areas.maxOf{it.edgeAllowanceMeters}+max(1.0,margin))
            }
        val knownRaster=union(rasterAreas.filter{it.kind in setOf(RasterPassageKind.SHALLOW,RasterPassageKind.DEEP)}.map{it.geometry},factory)
        val actualCoverage=union(masks.values.toList()+rasterCoverage,factory)
        onProgress(.88f,"合并边界与安全余量 / Combining boundaries and clearance")
        val navigable=robustDifference(robustBuffer(robustIntersection(union(depthAreas+rasterDeep,factory),actualCoverage),-max(1.0,margin)),union(blocked,factory))
        currentCoroutineContext().ensureActive()
        onProgress(1f,"区域资料已就绪 / Area ready")
        return PassageWorld(projection,projected,actualCoverage,navigable,malformed.distinct(),margin,knownRaster,rasterAreas,referenceAreas,rasterBoundaryUncertainty,referenceDatumFeatures,Envelope(region.envelopeInternal))
    }


    private data class FineConflict(val segmentIndex:Int,val fromMeters:Double,val toMeters:Double)

    /** Full-detail validation returns the exact local span that invalidates a coarse proposal. */
    private suspend fun firstFineConflict(snapshot:ChartDataSnapshot,request:PassageRequest,points:List<ChartPoint>):FineConflict? {
        if(points.size<2)return FineConflict(0,0.0,0.0)
        val vessel=request.vessel
        val configured=max(vessel.corridorHalfWidthMeters?:0.0,
            (vessel.beamMeters?:0.0)/2+(vessel.clearanceMarginMeters?:0.0))
        val margin=max(1.0,configured)
        for((segmentIndex,pair) in points.zipWithNext().withIndex()) {
            val start=pair.first;val end=pair.second
            val length=distance(start,end)
            val chunks=max(1,ceil(length/2_500.0).toInt())
            for(index in 0 until chunks) {
                currentCoroutineContext().ensureActive()
                val from=length*index/chunks
                val to=length*(index+1)/chunks
                val a=atDistance(start,end,from)
                val b=atDistance(start,end,to)
                val detailed=try {
                    world(snapshot,request,listOf(a,b),max(250.0,margin+175.0),
                        PassageWorldPurpose.REFERENCE_DRAFT,preferredScaleDenominator=null)
                }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                catch(_:Exception){return FineConflict(segmentIndex,from,to)}
                // Malformed objects elsewhere in this small window are surfaced by the full route
                // check; they must not make a valid centerline look physically blocked here.
                val line=detailed.projection.line(listOf(a,b))
                if(!detailed.navigable.covers(line))return FineConflict(segmentIndex,from,to)
            }
        }
        return null
    }

    /**
     * Hierarchical repair: keep the accepted coarse corridor everywhere it is valid and reroute
     * only the short full-detail span that conflicts. This avoids throwing away a 30 km proposal
     * because of one harbour entrance or shallow patch.
     */
    suspend fun refineCoarseRoute(snapshot:ChartDataSnapshot,request:PassageRequest,points:List<ChartPoint>):List<ChartPoint>? {
        if(points.size<2)return null
        var current=points
        repeat(12) {
            currentCoroutineContext().ensureActive()
            val conflict=firstFineConflict(snapshot,request,current)?:return current
            if(conflict.segmentIndex !in 0 until current.lastIndex)return null
            val start=current[conflict.segmentIndex]
            val end=current[conflict.segmentIndex+1]
            val length=distance(start,end)
            if(length<1.0)return null
            val vessel=request.vessel
            val configured=max(vessel.corridorHalfWidthMeters?:0.0,
                (vessel.beamMeters?:0.0)/2+(vessel.clearanceMarginMeters?:0.0))
            val context=max(750.0,max(1.0,configured)*6.0)
            val from=(conflict.fromMeters-context).coerceAtLeast(0.0)
            val to=(conflict.toMeters+context).coerceAtMost(length)
            val a=atDistance(start,end,from)
            val b=atDistance(start,end,to)
            val localLength=distance(a,b)
            val paddings=listOf(
                max(1_500.0,localLength*.75).coerceAtMost(6_000.0),
                max(3_000.0,localLength*1.5).coerceAtMost(10_000.0)
            ).distinct()
            var repair:List<ChartPoint>?=null
            for(padding in paddings) {
                currentCoroutineContext().ensureActive()
                val detailed=try {
                    world(snapshot,request,listOf(a,b),padding,PassageWorldPurpose.REFERENCE_DRAFT,
                        preferredScaleDenominator=null)
                }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                catch(_:Exception){continue}
                repair=search(detailed,a,b,request.vessel.turnRadiusMeters,smoothTurns=true){ }
                if(repair!=null)break
            }
            val replacement=repair?:return null
            val next=ArrayList<ChartPoint>(current.size+replacement.size+2)
            next+=current.take(conflict.segmentIndex+1)
            if(distance(next.last(),a)>.5)next+=a
            replacement.drop(1).forEach {p->if(distance(next.last(),p)>.5)next+=p}
            if(distance(next.last(),end)>.5)next+=end
            current.drop(conflict.segmentIndex+2).forEach {p->if(distance(next.last(),p)>.5)next+=p}
            if(next.size>2_000)return null
            if(next.size==current.size&&next.indices.all{i->distance(next[i],current[i])<.5})return null
            current=next
        }
        return if(firstFineConflict(snapshot,request,current)==null)current else null
    }

    fun validateRequest(request:PassageRequest) {
        require(request.datasetIds.distinct().size<=1){"请选择一个数据文件夹 / Choose one data folder"}
        require(request.route.points.size in 2..2000){"Choose at least two points"}
        request.route.navigationTargetIndices?.let { targets ->
            require(targets.isNotEmpty() && targets==targets.distinct().sorted() && targets.all{it in request.route.points.indices} && targets.last()==request.route.points.lastIndex){"Invalid destination mapping"}
        }
        require(request.route.points.all{it.latitude.isFinite()&&it.longitude.isFinite()&&it.latitude in -89.9..89.9&&it.longitude in -180.0..180.0}){"Invalid route coordinates"}
        val v=request.vessel
        require(listOf(v.draftMeters,v.beamMeters,v.airDraftMeters,v.minimumUnderKeelMeters,v.clearanceMarginMeters,v.corridorHalfWidthMeters,v.turnRadiusMeters,v.plannedSpeedMetersPerSecond).all{it==null||it.isFinite()&&it>=0}){"Invalid vessel dimensions"}
    }

    suspend fun analyze(snapshot:ChartDataSnapshot,request:PassageRequest,onProgress:(Float)->Unit={}):PassageAnalysis {
        validateRequest(request)
        val issues=mutableListOf<PassageIssue>();val strips=mutableListOf<PassageStripSpan>();var total=0.0
        fun issue(kind:PassageIssueKind,severity:PassageSeverity,message:String,leg:Int=0,p:ChartPoint?=null,along:Double=0.0,f:NauticalFeature?=null,cellId:String?=null,evidence:DepthEvidence?=null){
            require(issues.size<50_000){"航线证据过多，请分段检查 / Too much route evidence; inspect shorter sections"}
            issues.add(PassageIssue(passageHash("$kind/$leg/${f?.id}/${cellId}/$along/$message"),severity,kind,leg,p,along,message,f?.id,f?.cellId?:cellId,f?.depth?:evidence))
        }
        if(snapshot.missingDatasetIds.isNotEmpty()||snapshot.datasets.isEmpty())issue(PassageIssueKind.DATA,PassageSeverity.INSUFFICIENT,"所选数据集尚未安装或已移除 / Selected data is missing")
        snapshot.datasets.filterNot{it.allowsPassageDrafting(System.currentTimeMillis())&&it.offlineReadable&&it.issue==null}.forEach{issue(PassageIssueKind.DATA,PassageSeverity.INSUFFICIENT,"${it.name}：资料用途不允许粗略建议 / Data use does not allow a coarse suggestion")}
        val v=request.vessel
        if(v.draftMeters?.let{it.isFinite()&&it>0}!=true)
            issue(PassageIssueKind.VESSEL,PassageSeverity.INSUFFICIENT,"设置吃水后可检查实际水深 / Set draft to check actual depth")
        if(v.beamMeters?.let{it.isFinite()&&it>0}!=true)
            issue(PassageIssueKind.VESSEL,PassageSeverity.REVIEW,"未设置船宽；只使用 1 m 几何容差，不把未知船宽伪装成 25 m 禁航带 / Beam is unset; only a 1 m geometry tolerance is used instead of inventing a 25 m exclusion corridor",leg=0,p=request.route.points.firstOrNull())
        val required=v.draftMeters?.takeIf{it.isFinite()&&it>0}?.let{d->d+(v.minimumUnderKeelMeters?:0.0)}

        // A configured turn radius also applies to a manually edited route. Hard waypoints remain
        // exact navigation targets, so a non-trivial corner is at least REVIEW; if the tangent
        // distance cannot fit in the adjacent legs it is a geometric CONFLICT.
        v.turnRadiusMeters?.takeIf{it.isFinite()&&it>0}?.let {radius->
            var alongToJoint=0.0
            for(index in 1 until request.route.points.lastIndex) {
                val previous=request.route.points[index-1]
                val joint=request.route.points[index]
                val next=request.route.points[index+1]
                val inLen=distance(previous,joint)
                val outLen=distance(joint,next)
                alongToJoint+=inLen
                if(inLen<1.0||outLen<1.0)continue
                val projection=PassageProjection(joint)
                val before=projection.xy(previous);val after=projection.xy(next)
                val ux=-before.x/inLen;val uy=-before.y/inLen
                val vx=after.x/outLen;val vy=after.y/outLen
                val turn=abs(atan2(ux*vy-uy*vx,ux*vx+uy*vy))
                if(turn<=Math.toRadians(5.0))continue
                val tangent=radius*tan(turn/2.0)
                issue(
                    PassageIssueKind.GEOMETRY,
                    if(!tangent.isFinite()||tangent>min(inLen,outLen)*.45)PassageSeverity.CONFLICT else PassageSeverity.REVIEW,
                    if(!tangent.isFinite()||tangent>min(inLen,outLen)*.45)
                        "该用户航点无法在相邻航段长度内满足已设置的转弯半径 / This hard waypoint cannot satisfy the configured turn radius within the adjacent leg lengths"
                    else
                        "用户航点为精确目标；此转角需要按已设置的转弯半径操船确认 / This hard waypoint is an exact target; review the turn against the configured turn radius",
                    leg=index-1,p=joint,along=alongToJoint
                )
            }
        }

        request.route.points.zipWithNext().forEachIndexed{leg,(start,end)->
            val length=distance(start,end);val chunks=max(1,ceil(length/20_000).toInt())
            if(length<0.1)issue(PassageIssueKind.GEOMETRY,PassageSeverity.REVIEW,"相邻航点重合 / Coincident waypoints",leg,start,total)
            repeat(chunks){chunk->
                currentCoroutineContext().ensureActive()
                val from=length*chunk/chunks;val to=length*(chunk+1)/chunks
                val a=atDistance(start,end,from);val b=atDistance(start,end,to)
                val world=world(snapshot,request,listOf(a,b),max(250.0,v.corridorHalfWidthMeters?:0.0)+100)
                val line=world.projection.line(listOf(a,b));val corridor=line.buffer(max(1.0,world.margin))
                val offset=total+from
                fun along(p:Coordinate)=offset+((p.x*line.endPoint.x+p.y*line.endPoint.y)/(line.length*line.length).coerceAtLeast(1.0)).coerceIn(0.0,1.0)*line.length
                val uncovered=robustDifference(line,world.coverage)
                if(!world.coverage.covers(corridor)){issue(PassageIssueKind.COVERAGE,PassageSeverity.INSUFFICIENT,"航行走廊缺少完整海图覆盖 / Incomplete corridor coverage",leg,world.projection.point(if(uncovered.isEmpty)line.coordinate else uncovered.coordinate),offset);if(!uncovered.isEmpty)strips.add(PassageStripSpan(offset,offset+line.length,leg,null,false))}
                if(world.malformed.isNotEmpty())issue(PassageIssueKind.QUALITY,PassageSeverity.INSUFFICIENT,"本区域有未支持或不完整的海图对象 / Incomplete chart semantics",leg,a,offset)
                if(world.rasterBoundaryUncertainty.intersects(corridor))issue(PassageIssueKind.DEPTH,PassageSeverity.INSUFFICIENT,
                    "栅格边界余量不足以支持这段走廊 / This corridor is too close to an uncertain grid boundary",leg,a,offset)
                val knownDepth=mutableListOf<Geometry>(world.rasterKnownDepth)
                world.referenceAreas.forEach { reference ->
                    if(reference.geometry.intersects(corridor))issue(PassageIssueKind.QUALITY,PassageSeverity.REVIEW,reference.message,leg,a,offset,cellId=reference.cellId)
                }
                world.rasterAreas.forEach { area ->
                    currentCoroutineContext().ensureActive()
                    val risk=if(area.kind==RasterPassageKind.DEEP)area.geometry else robustBuffer(area.geometry,area.edgeAllowanceMeters)
                    if(!risk.intersects(corridor))return@forEach
                    val hit=robustIntersection(risk,corridor);if(hit.isEmpty)return@forEach
                    val point=world.projection.point(hit.coordinate);val location=along(hit.coordinate)
                    when(area.kind){
                        RasterPassageKind.UNKNOWN->issue(PassageIssueKind.DEPTH,PassageSeverity.INSUFFICIENT,"航段靠近栅格无数据区域 / Route approaches missing grid data",leg,point,location,cellId=area.grid.cellId)
                        RasterPassageKind.LAND->if(area.geometry.intersects(line)) {
                            issue(PassageIssueKind.LAND,PassageSeverity.CONFLICT,"航线进入所选网格的陆地像元；粗网格可能无法描述近岸水道 / Route crosses a land cell in the selected grid; coarse cells may not resolve an inshore waterway",leg,point,location,cellId=area.grid.cellId)
                        } else {
                            issue(PassageIssueKind.QUALITY,PassageSeverity.INSUFFICIENT,"航线靠近粗网格岸线，不能由像元余量判断为陆路，请用更精细海图核对 / Route is near a coarse coastline; cell uncertainty is not proof of land. Review with finer charts",leg,point,location,cellId=area.grid.cellId)
                        }
                        RasterPassageKind.SHALLOW->if(required!=null)issue(PassageIssueKind.DEPTH,PassageSeverity.CONFLICT,"参考地形浅于所需深度（含像元边界余量） / Reference seabed is too shallow, including cell-edge allowance",leg,point,location,cellId=area.grid.cellId,evidence=area.evidence)
                        RasterPassageKind.DEEP->Unit
                    }
                    val cut=robustIntersection(area.geometry,line)
                    if(!cut.isEmpty)for(part in 0 until cut.numGeometries){
                        val segment=cut.getGeometryN(part);if(segment.isEmpty)continue
                        val range=segment.coordinates.map(::along)
                        require(strips.size<100_000){"深度证据过多，请分段检查 / Too much depth evidence; inspect shorter sections"}
                        strips+=PassageStripSpan(range.min(),range.max(),leg,area.evidence,area.kind!=RasterPassageKind.UNKNOWN)
                    }
                }
                world.features.forEach{(f,g)->
                    if(!g.intersects(corridor))return@forEach
                    val hit=robustIntersection(g,corridor);val p=world.projection.point(hit.coordinate);val at=along(hit.coordinate)
                    when(f.kind){
                        NauticalFeatureKind.LAND,NauticalFeatureKind.DRYING_AREA->issue(PassageIssueKind.LAND,PassageSeverity.CONFLICT,"航段经过陆地或干出区域 / Land or drying area",leg,p,at,f)
                        NauticalFeatureKind.OBSTRUCTION,NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK->issue(PassageIssueKind.OBSTACLE,PassageSeverity.CONFLICT,"航行走廊内有障碍物 / Obstruction in corridor",leg,p,at,f)
                        NauticalFeatureKind.RESTRICTED->issue(PassageIssueKind.RESTRICTION,PassageSeverity.REVIEW,"核对限制区规定 / Review area restrictions",leg,p,at,f)
                        NauticalFeatureKind.TRAFFIC->issue(PassageIssueKind.TRAFFIC,PassageSeverity.REVIEW,"核对通航方向和交通规则 / Review traffic direction",leg,p,at,f)
                        NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD->{val clear=(f.attributes["VERCLR"]?.toDoubleOrNull()?:f.attributes["VERCCL"]?.toDoubleOrNull())?.takeIf{it.isFinite()&&it>=0};val need=v.airDraftMeters?.takeIf{it.isFinite()&&it>0}?.let{d->d+(v.clearanceMarginMeters?:0.0)};issue(PassageIssueKind.CLEARANCE,if(clear!=null&&need!=null&&clear<need)PassageSeverity.CONFLICT else if(clear==null||need==null||f.source.verticalDatum==null)PassageSeverity.INSUFFICIENT else PassageSeverity.REVIEW,"核对桥梁净空、水位基准和开桥条件 / Check overhead clearance and datum",leg,p,at,f)}
                        NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA->{
                            val d=f.depth;val cut=robustIntersection(g,line)
                            if(!cut.isEmpty){
                                for(part in 0 until cut.numGeometries){val segment=cut.getGeometryN(part);if(segment.isEmpty)continue
                                    val bounds=segment.coordinates.map(::along);strips.add(PassageStripSpan(bounds.min(),bounds.max(),leg,d,true,f.id))}
                            }
                            val low=d?.lowerMeters;val high=d?.upperMeters
                            if(d?.kind==DepthEvidenceKind.INTERVAL&&!d.datum.isNullOrBlank()&&low!=null&&low.isFinite()&&!f.issues.any(::isBlockingChartIssue)){
                                knownDepth.add(g)
                                if(required!=null&&low<required)issue(PassageIssueKind.DEPTH,if(high!=null&&high<required)PassageSeverity.CONFLICT else PassageSeverity.REVIEW,"海图深度不足或区间跨过所需深度 / Depth below or spans requirement",leg,p,at,f)
                            }
                        }
                        NauticalFeatureKind.SOUNDING->f.geometry.parts.flatMap{it.points}.forEach{sp->val gp=world.projection.factory.createPoint(world.projection.xy(sp));if(g.covers(gp)&&gp.intersects(corridor)){val atPoint=along(gp.coordinate);strips.add(PassageStripSpan(atPoint,atPoint,leg,DepthEvidence(DepthEvidenceKind.POINT,pointMeters=sp.depthMeters,datum=f.depth?.datum),true,f.id));if(sp.depthMeters?.let{required!=null&&it<required}==true)issue(PassageIssueKind.DEPTH,PassageSeverity.CONFLICT,"独立测深点浅于所需深度 / Shallow sounding",leg,sp,atPoint,f)}}
                        NauticalFeatureKind.QUALITY->issue(PassageIssueKind.QUALITY,PassageSeverity.REVIEW,"查看测量质量和资料日期 / Review survey quality",leg,p,at,f)
                        else->Unit
                    }
                    if(f.attributes["RESTRN"]?.isNotBlank()==true&&f.kind!=NauticalFeatureKind.RESTRICTED)issue(PassageIssueKind.RESTRICTION,PassageSeverity.REVIEW,"该对象包含限制条件，请查阅详情 / Object has restrictions; review its terms",leg,p,at,f)
                    if(f.issues.any(::isBlockingChartIssue))issue(PassageIssueKind.DATA,PassageSeverity.INSUFFICIENT,"对象包含未支持语义 / Unsupported object semantics",leg,p,at,f)
                }
                if(!union(knownDepth,world.projection.factory).covers(corridor))issue(PassageIssueKind.DEPTH,PassageSeverity.INSUFFICIENT,"部分走廊没有可信深度区间 / Depth evidence is incomplete",leg,a,offset)
                request.avoidances.forEach{avoid->val shape=runCatching{world.projection.geometry(ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(avoid.boundary))))}.getOrNull();if(shape?.intersects(corridor)==true)issue(PassageIssueKind.AVOIDANCE,PassageSeverity.CONFLICT,"${avoid.name} / Avoidance area",leg,a,offset)}
                onProgress((leg+(chunk+1f)/chunks)/(request.route.points.size-1))
            }
            total+=length
        }
        val unique=issues.distinctBy{listOf(it.legIndex,it.kind,it.featureId,it.cellId,it.message)}.sortedBy{it.alongMeters}
        val severity=listOf(PassageSeverity.CONFLICT,PassageSeverity.INSUFFICIENT,PassageSeverity.REVIEW).firstOrNull{level->unique.any{it.severity==level}}?:PassageSeverity.NO_CONFLICT_FOUND
        val speed=v.plannedSpeedMetersPerSecond?.takeIf{it.isFinite()&&it>0.1}
        return PassageAnalysis(request.requestId,passageHash(listOf(request.route,request.vessel,request.datasetIds,snapshot.datasets.map{it.id to it.revision},request.avoidances,request.departureUtc,PASSAGE_RULES_VERSION)),request,snapshot.revision,snapshot.datasets.associate{it.id to it.revision},System.currentTimeMillis(),total,request.departureUtc?.let{depart->speed?.let{depart+(total/it*1000).toLong()}},severity,unique,strips)
    }

    /** A* 每条边和简化线都受当前 world 用途的完整水域约束；缺少真实水域或深度数值不能成为节点。 */
    suspend fun search(world:PassageWorld,start:ChartPoint,end:ChartPoint,turnRadius:Double?,smoothTurns:Boolean=true,onProgress:(Float)->Unit):List<ChartPoint>? {
        val job=currentCoroutineContext()
        val p=world.projection;val a=p.xy(start);val b=p.xy(end)
        val prepared=PreparedGeometryFactory.prepare(world.navigable)
        fun clear(x:Coordinate,y:Coordinate):Boolean {job.ensureActive();return prepared.covers(p.factory.createLineString(arrayOf(x,y)))}
        if(!prepared.covers(p.factory.createPoint(a))||!prepared.covers(p.factory.createPoint(b)))return null
        if(clear(a,b))return listOf(start,end)
        // 粗略参考规划不要求转弯半径：A* 仍可先给出避开已知陆地/浅区的折线，
        // 有转弯半径时再进行相切圆弧校验。最终结果始终需要人工核对。
        val usableTurnRadius=turnRadius?.takeIf {it.isFinite()&&it>0}
        val extent=max(2000.0,a.distance(b)*0.75).coerceAtMost(60_000.0)
        val area=world.searchBounds?:Envelope(min(a.x,b.x)-extent,max(a.x,b.x)+extent,min(a.y,b.y)-extent,max(a.y,b.y)+extent)
        val minX=area.minX;val maxX=area.maxX;val minY=area.minY;val maxY=area.maxY
        val width=maxX-minX;val height=maxY-minY
        val rasterStep=world.rasterAreas.minOfOrNull { area ->
            val latitude=(start.latitude+end.latitude)/2.0
            val eastWest=area.grid.pixelWidthDegrees*111_320.0*cos(Math.toRadians(latitude)).coerceAtLeast(.15)
            val northSouth=area.grid.pixelHeightDegrees*110_540.0
            max(10.0,min(eastWest,northSouth))
        }
        // Open water may stay coarse, but local/full-detail windows can now resolve channels down
        // to about 10–20 m when the node budget permits. Large boxes still coarsen automatically.
        val budgetStep=sqrt((width*height/250_000.0).coerceAtLeast(0.0)).coerceAtLeast(10.0)
        val sourceStep=(rasterStep?.coerceAtMost(250.0)?:10.0).coerceAtLeast(10.0)
        val step=max(budgetStep,sourceStep)
        val cols=ceil(width/step).toInt()+1;val rows=ceil(height/step).toInt()+1
        require(cols>1&&rows>1&&cols.toLong()*rows<=300_000){"搜索区域超出局部预算，请增加途经点 / Search area exceeds the local budget; add a waypoint"}
        fun coord(id:Int)=Coordinate(minX+(id%cols)*step,minY+(id/cols)*step)
        fun id(c:Coordinate)=(((c.y-minY)/step).roundToInt().coerceIn(0,rows-1))*cols+((c.x-minX)/step).roundToInt().coerceIn(0,cols-1)
        val water=ByteArray(cols*rows)
        fun nodeWater(node:Int):Boolean=when(water[node].toInt()) {
            1->false;2->true
            else->prepared.covers(p.factory.createPoint(coord(node))).also{water[node]=if(it)2 else 1}
        }
        val edges=object:LinkedHashMap<Long,Boolean>(4096,.75f,true){
            override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Long,Boolean>?)=size>65_536
        }
        fun edgeClear(from:Int,to:Int):Boolean {
            if(!nodeWater(from)||!nodeWater(to))return false
            val key=(min(from,to).toLong() shl 32) or (max(from,to).toLong() and 0xffffffffL)
            return edges[key]?:clear(coord(from),coord(to)).also{edges[key]=it}
        }
        data class Node(val id:Int,val cost:Double,val score:Double)
        val queue=PriorityQueue<Node>(compareBy<Node>{it.score}.thenByDescending{it.cost})
        val scores=DoubleArray(cols*rows){Double.POSITIVE_INFINITY}
        val parents=IntArray(cols*rows){-1}
        val closed=BooleanArray(cols*rows)
        val nearest=id(a);val firstX=nearest%cols;val firstY=nearest/cols
        // 网格中心恰在岸上不代表真实端点没路；只寻找经完整水域查线可接入的邻近节点。
        val first=(firstY-3..firstY+3).flatMap{y->(firstX-3..firstX+3).mapNotNull{x->
            if(x in 0 until cols&&y in 0 until rows)y*cols+x else null
        }}.sortedBy{coord(it).distance(a)}.firstOrNull{nodeWater(it)&&clear(a,coord(it))}?:return null
        scores[first]=a.distance(coord(first));parents[first]=first
        queue.add(Node(first,scores[first],scores[first]+coord(first).distance(b)))
        var found=-1;var visited=0
        // Basic Theta*: 仍在有界网格上扩展，但父节点若对下一节点有完整水域视线就直接跨格，
        // 因此直航道天然形成长直航段，不依赖事后 RDP；陆地由 prepared navigable 的 LOS 硬阻断。
        val visitBudget=cols*rows
        while(queue.isNotEmpty()&&visited<visitBudget){
            job.ensureActive();val node=queue.remove();if(closed[node.id]||node.cost>scores[node.id])continue
            closed[node.id]=true
            visited++;if(visited%100==0)onProgress((visited/visitBudget.toFloat()).coerceAtMost(.99f))
            val c=coord(node.id)
            if(c.distance(b)<step*2&&clear(c,b)){found=node.id;break}
            val x=node.id%cols;val y=node.id/cols
            for(dy in -1..1)for(dx in -1..1){
                if(dx==0&&dy==0)continue
                val nx=x+dx;val ny=y+dy
                if(nx !in 0 until cols||ny !in 0 until rows)continue
                val next=ny*cols+nx
                if(closed[next]||!nodeWater(next))continue
                val nextCoord=coord(next)
                if(!edgeClear(node.id,next))continue
                var bestParent=node.id
                var bestCost=node.cost+c.distance(nextCoord)
                val parent=parents[node.id]
                if(parent>=0&&parent!=node.id&&edgeClear(parent,next)){
                    val candidate=scores[parent]+coord(parent).distance(nextCoord)
                    if(candidate<bestCost){bestCost=candidate;bestParent=parent}
                }
                if(bestCost>=scores[next])continue
                scores[next]=bestCost;parents[next]=bestParent
                queue.add(Node(next,bestCost,bestCost+nextCoord.distance(b)))
            }
        }
        if(found<0)return null
        val reverse=mutableListOf<Coordinate>(b);var current=found
        while(true){
            job.ensureActive();reverse.add(coord(current))
            if(current==first)break
            current=parents[current]
            if(current<0)return null
        }
        reverse.add(a);reverse.reverse()
        if(!smoothTurns||usableTurnRadius==null)return reverse.map(p::point)
        return smooth(world,reverse.map(p::point),usableTurnRadius)
    }

    /** 整条候选一起平滑，原航段之间的接头也必须满足相同转弯约束。 */
    fun smooth(world:PassageWorld,points:List<ChartPoint>,turnRadius:Double?):List<ChartPoint>? {
        if(points.size<2)return null
        val p=world.projection
        val reduced=points.map(p::xy).fold(mutableListOf<Coordinate>()){list,c->if(list.lastOrNull()?.distance(c)?.let{it>=.01}!=false)list.add(c);list}
        if(reduced.size<2)return null
        if(reduced.size==2)return points
        if(turnRadius==null||!turnRadius.isFinite()||turnRadius<=0)return null
        // 将角点变为相切圆弧；每条弦加弓高缓冲检查，不以简化折线擦过障碍。
        val smooth=mutableListOf(reduced.first())
        for(j in 1 until reduced.lastIndex){
            val before=reduced[j-1];val corner=reduced[j];val after=reduced[j+1]
            val inLen=before.distance(corner);val outLen=corner.distance(after)
            val ux=(corner.x-before.x)/inLen;val uy=(corner.y-before.y)/inLen;val vx=(after.x-corner.x)/outLen;val vy=(after.y-corner.y)/outLen
            val angle=acos((ux*vx+uy*vy).coerceIn(-1.0,1.0));if(angle<.01){smooth.add(corner);continue}
            val tangent=turnRadius*tan(angle/2);if(!tangent.isFinite()||tangent>min(inLen,outLen)*.45)return null
            val entry=Coordinate(corner.x-ux*tangent,corner.y-uy*tangent);val exit=Coordinate(corner.x+vx*tangent,corner.y+vy*tangent)
            val side=if(ux*vy-uy*vx>0)1 else -1;val center=Coordinate(entry.x-uy*turnRadius*side,entry.y+ux*turnRadius*side)
            val base=atan2(entry.y-center.y,entry.x-center.x);val count=max(4,ceil(angle/Math.toRadians(5.0)).toInt());smooth.add(entry)
            for(k in 1..count){val theta=base+side*angle*k/count;smooth.add(Coordinate(center.x+turnRadius*cos(theta),center.y+turnRadius*sin(theta)))}
            smooth[smooth.lastIndex]=exit
        }
        smooth.add(reduced.last())
        if(smooth.size>2000)return null
        val tolerance=turnRadius*(1-cos(Math.toRadians(2.5)))+.25
        if(!world.navigable.covers(robustBuffer(p.factory.createLineString(smooth.toTypedArray()),tolerance)))return null
        return smooth.map(p::point)
    }
}
