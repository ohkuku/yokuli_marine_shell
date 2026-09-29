package com.yokuli.runtime.marine.planning

import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.*
import net.sf.geographiclib.Geodesic
import org.locationtech.jts.geom.*
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import java.util.PriorityQueue
import kotlin.math.*

/** 每个局部检查块用 WGS84 反解建立米制方位等距坐标；长航段分块，日期线不变成跨全球直线。 */
internal class PassageProjection(val origin:ChartPoint) {
    val factory=GeometryFactory()
    fun xy(p:ChartPoint):Coordinate { val d=Geodesic.WGS84.Inverse(origin.latitude,origin.longitude,p.latitude,p.longitude);val a=Math.toRadians(d.azi1);return Coordinate(d.s12*sin(a),d.s12*cos(a)) }
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
internal fun union(values:List<Geometry>,factory:GeometryFactory):Geometry=if(values.isEmpty())factory.createPolygon() else UnaryUnionOp.union(values)
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
internal data class PassageWorld(
    val projection:PassageProjection,val features:List<FeatureGeometry>,val coverage:Geometry,val navigable:Geometry,
    val malformed:List<String>,val margin:Double,
    /** 实际有数值的参考水深，与 ENC datum 证据保持区分。 */
    val rasterKnownDepth:Geometry,val rasterAreas:List<RasterPassageArea>,val referenceAreas:List<PassageReferenceArea>,
    /** 外部未知/浅区边界的像元余量，分析和搜索使用同一集合；文件之间的接缝不在其中。 */
    val rasterBoundaryUncertainty:Geometry,
)

internal class PassageGeometry(private val charts:ChartDataService) {
    suspend fun world(snapshot:ChartDataSnapshot,request:PassageRequest,points:List<ChartPoint>,padding:Double):PassageWorld {
        val projection=PassageProjection(points.first());val factory=projection.factory
        val vessel=request.vessel
        val configuredMargin=max(vessel.corridorHalfWidthMeters?:0.0,(vessel.beamMeters?:0.0)/2+(vessel.clearanceMarginMeters?:0.0))
        // 粗略参考规划允许尚未填写横向走廊参数；至少保留 25 m 几何余量。
        // GEBCO 另有半像元边界余量（15″ 在 NZ 约数百米），不会因为这个默认值贴着岸线走。
        val margin=max(25.0,configuredMargin)
        // 吃水本身足够参与粗略自动规划；额外 UKC 未设置时按 0 处理并在结果里提示核对。
        val required=vessel.draftMeters?.takeIf{it.isFinite()&&it>0}?.let{draft->draft+(vessel.minimumUnderKeelMeters?:0.0)}
        val rasterAllowance=snapshot.datasets.flatMap{it.rasters.orEmpty()}.maxOfOrNull { hypot(it.pixelWidthDegrees,it.pixelHeightDegrees)*111_320.0*.5 }?:0.0
        val localPadding=max(padding,rasterAllowance+margin+100.0)
        val bounds=around(points,localPadding)
        val rasterWindows=charts.rasterWindows(snapshot.id,bounds,maxCells=262_144).groupBy{"${it.grid.datasetId}/${it.grid.cellId}"}
        val rasterAreas=ArrayList<RasterPassageArea>();val referenceAreas=ArrayList<PassageReferenceArea>()
        val rasterCoverage=ArrayList<Geometry>()
        val features=ArrayList<NauticalFeature>();var cursor:String?=null
        do {currentCoroutineContext().ensureActive();val page=charts.query(snapshot.id,bounds,2000,cursor);require(!page.truncated){"Chart query is incomplete"};features.addAll(page.features);require(features.size<=120_000){"Area contains too many chart objects; use a shorter passage"};require(!page.hasMore||page.nextAfterId!=null&&page.nextAfterId!=cursor){"Chart query cursor did not advance"};cursor=if(page.hasMore)page.nextAfterId else null}while(cursor!=null)
        val malformed=mutableListOf<String>()
        // 和搜索网格相同的局部矩形；远方图幅及已被上层覆盖的单元不能污染本区域质量状态。
        val region=projection.line(points).envelope.buffer(max(1.0,localPadding)).envelope
        var occupied:Geometry=factory.createPolygon()
        val masks=mutableMapOf<String,Geometry>()
        fun touchesQuery(box:ChartBounds)=box.split().any{part->bounds.split().any{query->part.west<=query.east&&part.east>=query.west&&part.south<=query.north&&part.north>=query.south}}
        fun hintArea(cell:ChartCellRevision):Geometry {
            // 连可定位边界都缺失时无法证明问题在远方，保守保留本区未知状态。
            if(cell.bounds.none {it.valid})return region
            val shapes=cell.bounds.filter{it.valid&&touchesQuery(it)}.flatMap{it.split()}.mapNotNull { box ->
                if(box.east-box.west>=180.0)return@mapNotNull region
                runCatching { projection.line(listOf(ChartPoint(box.south,box.west),ChartPoint(box.south,box.east),
                    ChartPoint(box.north,box.east),ChartPoint(box.north,box.west),ChartPoint(box.south,box.west))).envelope.intersection(region) }.getOrNull()
            }
            return union(shapes,factory)
        }
        val datasetOrder=request.datasetIds.withIndex().associate{it.value to it.index}
        val cells=snapshot.datasets.flatMap{dataset->dataset.cells.groupBy{it.cellId}.values.map{versions->versions.maxWith(compareBy<ChartCellRevision>{it.edition}.thenBy{it.update})}.filterNot{it.cancelled}.map{Triple(datasetOrder[dataset.id]?:Int.MAX_VALUE,dataset,it)}}.sortedWith(compareBy<Triple<Int,ChartDataset,ChartCellRevision>>{it.first}.thenBy{it.third.priority}.thenBy{it.third.compilationScale?:Int.MAX_VALUE}.thenByDescending{it.third.edition}.thenByDescending{it.third.update}.thenBy{it.third.cellId})
        for((_,dataset,cell) in cells){
            currentCoroutineContext().ensureActive()
            if(!dataset.allowsPassageDrafting(System.currentTimeMillis())||!dataset.offlineReadable||dataset.issue!=null)continue
            val declaredBounds=cell.bounds.filter{it.valid}
            if(declaredBounds.isNotEmpty()&&declaredBounds.none(::touchesQuery))continue
            val cellKey="${dataset.id}/${cell.cellId}"
            val rasterMetadata=dataset.rasters.orEmpty().filter{it.cellId==cell.cellId}
            if(rasterMetadata.isNotEmpty()){
                val windows=rasterWindows[cellKey].orEmpty()
                if(windows.isEmpty()){
                    // 已声明落在本区却读不到窗口，不能把更低优先数据补上当作同一来源。
                    val unknown=hintArea(cell).difference(occupied)
                    if(!unknown.isEmpty)malformed+=cell.cellId
                    occupied=occupied.union(hintArea(cell));continue
                }
                val raster=windows.map{rasterPassageGeometry(it,projection,required)}
                val footprint=union(raster.map{it.footprint},factory).intersection(region)
                val effective=footprint.difference(occupied)
                if(!effective.isEmpty){
                    if(cell.hasUnsupportedSemantic||cell.issues.any(::isBlockingChartIssue))malformed+=cell.cellId
                    for(area in raster.flatMap{it.areas}){
                        currentCoroutineContext().ensureActive()
                        val clipped=area.geometry.intersection(effective)
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
                occupied=occupied.union(footprint)
                continue
            }
            var brokenCoverage=false
            fun coverageGeometry(evidence:CoverageEvidence):Geometry? = runCatching {
                projection.geometry(evidence.geometry).intersection(region)
            }.onFailure{brokenCoverage=true}.getOrNull()
            val valid=cell.coverage.filter{it.covered}.mapNotNull(::coverageGeometry)
            val gaps=cell.coverage.filterNot{it.covered}.mapNotNull(::coverageGeometry)
            val coverage=union(valid,factory).difference(union(gaps,factory))
            val effective=coverage.difference(occupied)
            masks[cellKey]=effective
            if(!effective.isEmpty&&cell.referenceOnly)referenceAreas+=PassageReferenceArea(effective,cell.cellId,
                "LINZ LDS 参考资料：核对正式海图、来源日期与限制 / LINZ LDS reference data; review official charts, dates and limitations")
            if(!effective.isEmpty&&cell.issues.contains("SURVEY_QUALITY_UNSPECIFIED"))referenceAreas+=PassageReferenceArea(effective,cell.cellId,
                "测量质量未明确，请复核来源 / Survey quality is unspecified; review the source")
            // 缺少/损坏覆盖时只用边界判断“可能影响本区”，绝不把边界当作已知覆盖。
            val uncertain=if(brokenCoverage||cell.coverage.none{it.covered})hintArea(cell).difference(occupied)else factory.createPolygon()
            if((!effective.isEmpty||!uncertain.isEmpty)&&(brokenCoverage||cell.hasUnsupportedSemantic||cell.issues.any(::isBlockingChartIssue)||cell.coverage.none{it.covered}))malformed.add(cell.cellId)
            occupied=occupied.union(coverage)
        }
        val projected=features.mapNotNull{feature->
            val mask=masks["${feature.datasetId}/${feature.cellId}"]?:return@mapNotNull null
            if(mask.isEmpty)return@mapNotNull null
            // 查询包围框内的对象也可能落在已被遮盖的部分。仅相关对象解析失败影响本区。
            val envelope=Envelope()
            feature.geometry.parts.forEach{part->part.points.forEach{point->if(point.latitude.isFinite()&&point.longitude.isFinite())envelope.expandToInclude(projection.xy(point))}}
            if(!envelope.isNull&&!factory.toGeometry(envelope).intersects(mask))return@mapNotNull null
            runCatching{FeatureGeometry(feature,projection.geometry(feature.geometry).intersection(mask))}.onFailure{malformed.add(feature.id)}.getOrNull()?.takeUnless{it.geometry.isEmpty}
        }
        val depthAreas=projected.filter {fg->
            val feature=fg.feature;val d=feature.depth;val low=d?.lowerMeters
            feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)&&
                d?.kind==DepthEvidenceKind.INTERVAL&&!d.datum.isNullOrBlank()&&low!=null&&low.isFinite()&&low>=0&&(required==null||low>=required)&&!feature.issues.any(::isBlockingChartIssue)
        }.map{it.geometry}
        // 自动出线只把“物理上不可通过/无法解释”的对象当硬障碍。
        // 限制区和交通规则属于 REVIEW，不应把粗略航线生成本身卡死。
        val blocked=projected.filter{it.feature.kind in setOf(
            NauticalFeatureKind.LAND,NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.OBSTRUCTION,
            NauticalFeatureKind.WRECK,NauticalFeatureKind.ROCK
        )||it.feature.issues.any(::isBlockingChartIssue)}.map{it.geometry.buffer(max(1.0,margin))}.toMutableList()
        projected.filter{it.feature.kind in setOf(NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD)}.forEach {fg->
            val clear=(fg.feature.attributes["VERCLR"]?.toDoubleOrNull()?:fg.feature.attributes["VERCCL"]?.toDoubleOrNull())
                ?.takeIf{it.isFinite()&&it>=0}
            val need=vessel.airDraftMeters?.takeIf{it.isFinite()&&it>0}?.let{it+(vessel.clearanceMarginMeters?:0.0)}
            // 有明确船高、净空和垂直基准且足够时允许粗略通过；其余情况继续保守避开。
            if(clear==null||need==null||fg.feature.source.verticalDatum==null||clear<need)
                blocked.add(fg.geometry.buffer(max(1.0,margin)))
        }
        // 重叠深度证据取保守交集：浅区/未知区不能被旁边的深区union盖掉。
        projected.filter{it.feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)}.forEach {fg->
            val d=fg.feature.depth
            if(d?.kind!=DepthEvidenceKind.INTERVAL||d.datum.isNullOrBlank()||d.lowerMeters?.let{it.isFinite()&&it>=0&&(required==null||it>=required)}!=true)
                blocked.add(fg.geometry.buffer(max(1.0,margin)))
        }
        projected.filter{it.feature.kind==NauticalFeatureKind.SOUNDING}.forEach{fg->fg.feature.geometry.parts.flatMap{it.points}.forEach{p->
            val at=factory.createPoint(projection.xy(p))
            if(fg.geometry.covers(at)&&p.depthMeters?.let{required!=null&&it<required}==true)blocked.add(at.buffer(max(1.0,margin)))
        }}
        request.avoidances.forEach{a->runCatching{projection.geometry(ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(a.boundary))))}.onSuccess{blocked.add(it.buffer(margin))}.onFailure{malformed.add(a.id)}}
        currentCoroutineContext().ensureActive()
        val resolutionAllowances=rasterAreas.groupBy{it.grid.pixelWidthDegrees to it.grid.pixelHeightDegrees}
            .mapValues{(_,areas)->areas.maxOf{it.edgeAllowanceMeters}}
        rasterAreas.indices.forEach { index ->
            val area=rasterAreas[index]
            rasterAreas[index]=area.copy(edgeAllowanceMeters=resolutionAllowances.getValue(area.grid.pixelWidthDegrees to area.grid.pixelHeightDegrees))
        }
        val rawRasterDeep=rasterAreas.filter{it.kind==RasterPassageKind.DEEP}
        // 先合并全部可用深水。文件边界不是岸线；高优先 ENC 的真实深水面也可接续参考栅格。
        val combinedDeep=union(depthAreas+rawRasterDeep.map{it.geometry},factory)
        val rasterDeep=rawRasterDeep.groupBy{it.grid.pixelWidthDegrees to it.grid.pixelHeightDegrees}.values.map { areas ->
            currentCoroutineContext().ensureActive()
            combinedDeep.buffer(-areas.maxOf{it.edgeAllowanceMeters}).intersection(union(areas.map{it.geometry},factory))
        }
        val rasterBoundaryUncertainty=union(rawRasterDeep.map{it.geometry},factory).difference(union(rasterDeep,factory))
        // 独立扣除真实非深水风险；相邻文件或矢量深水不能把浅区/陆地/NoData 的保守余量盖掉。
        rasterAreas.filter{it.kind!=RasterPassageKind.DEEP}
            .groupBy{Triple(it.kind,it.grid.pixelWidthDegrees,it.grid.pixelHeightDegrees)}.values.forEach { areas ->
                currentCoroutineContext().ensureActive()
                blocked+=union(areas.map{it.geometry},factory).buffer(areas.maxOf{it.edgeAllowanceMeters}+max(1.0,margin))
            }
        val knownRaster=union(rasterAreas.filter{it.kind in setOf(RasterPassageKind.SHALLOW,RasterPassageKind.DEEP)}.map{it.geometry},factory)
        val actualCoverage=union(masks.values.toList()+rasterCoverage,factory)
        val navigable=union(depthAreas+rasterDeep,factory).intersection(actualCoverage).buffer(-max(1.0,margin)).difference(union(blocked,factory))
        currentCoroutineContext().ensureActive()
        return PassageWorld(projection,projected,actualCoverage,navigable,malformed.distinct(),margin,knownRaster,rasterAreas,referenceAreas,rasterBoundaryUncertainty)
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
            issue(PassageIssueKind.VESSEL,PassageSeverity.REVIEW,"未设置船宽；检查使用内置 25 m 基础横向余量 / Beam is unset; checks use the built-in 25 m lateral margin",leg=0,p=request.route.points.firstOrNull())
        val required=v.draftMeters?.takeIf{it.isFinite()&&it>0}?.let{d->d+(v.minimumUnderKeelMeters?:0.0)}
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
                val uncovered=line.difference(world.coverage)
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
                    val risk=if(area.kind==RasterPassageKind.DEEP)area.geometry else area.geometry.buffer(area.edgeAllowanceMeters)
                    if(!risk.intersects(corridor))return@forEach
                    val hit=risk.intersection(corridor);if(hit.isEmpty)return@forEach
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
                    val cut=area.geometry.intersection(line)
                    if(!cut.isEmpty)for(part in 0 until cut.numGeometries){
                        val segment=cut.getGeometryN(part);if(segment.isEmpty)continue
                        val range=segment.coordinates.map(::along)
                        require(strips.size<100_000){"深度证据过多，请分段检查 / Too much depth evidence; inspect shorter sections"}
                        strips+=PassageStripSpan(range.min(),range.max(),leg,area.evidence,area.kind!=RasterPassageKind.UNKNOWN)
                    }
                }
                world.features.forEach{(f,g)->
                    if(!g.intersects(corridor))return@forEach
                    val hit=g.intersection(corridor);val p=world.projection.point(hit.coordinate);val at=along(hit.coordinate)
                    when(f.kind){
                        NauticalFeatureKind.LAND,NauticalFeatureKind.DRYING_AREA->issue(PassageIssueKind.LAND,PassageSeverity.CONFLICT,"航段经过陆地或干出区域 / Land or drying area",leg,p,at,f)
                        NauticalFeatureKind.OBSTRUCTION,NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK->issue(PassageIssueKind.OBSTACLE,PassageSeverity.CONFLICT,"航行走廊内有障碍物 / Obstruction in corridor",leg,p,at,f)
                        NauticalFeatureKind.RESTRICTED->issue(PassageIssueKind.RESTRICTION,PassageSeverity.REVIEW,"核对限制区规定 / Review area restrictions",leg,p,at,f)
                        NauticalFeatureKind.TRAFFIC->issue(PassageIssueKind.TRAFFIC,PassageSeverity.REVIEW,"核对通航方向和交通规则 / Review traffic direction",leg,p,at,f)
                        NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD->{val clear=(f.attributes["VERCLR"]?.toDoubleOrNull()?:f.attributes["VERCCL"]?.toDoubleOrNull())?.takeIf{it.isFinite()&&it>=0};val need=v.airDraftMeters?.takeIf{it.isFinite()&&it>0}?.let{d->d+(v.clearanceMarginMeters?:0.0)};issue(PassageIssueKind.CLEARANCE,if(clear!=null&&need!=null&&clear<need)PassageSeverity.CONFLICT else if(clear==null||need==null||f.source.verticalDatum==null)PassageSeverity.INSUFFICIENT else PassageSeverity.REVIEW,"核对桥梁净空、水位基准和开桥条件 / Check overhead clearance and datum",leg,p,at,f)}
                        NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA->{
                            val d=f.depth;val cut=g.intersection(line)
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

    /** A* 的每条边和简化后的每条线都检查完整线段；未知深度不是可通行节点。 */
    suspend fun search(world:PassageWorld,start:ChartPoint,end:ChartPoint,turnRadius:Double?,smoothTurns:Boolean=true,onProgress:(Float)->Unit):List<ChartPoint>? {
        if(world.malformed.isNotEmpty())return null
        val p=world.projection;val a=p.xy(start);val b=p.xy(end)
        val prepared=PreparedGeometryFactory.prepare(world.navigable)
        fun clear(x:Coordinate,y:Coordinate)=prepared.covers(p.factory.createLineString(arrayOf(x,y)))
        if(!world.navigable.covers(p.factory.createPoint(a))||!world.navigable.covers(p.factory.createPoint(b)))return null
        if(clear(a,b))return listOf(start,end)
        // 粗略参考规划不要求转弯半径：A* 仍可先给出避开已知陆地/浅区的折线，
        // 有转弯半径时再进行相切圆弧校验。最终结果始终需要人工核对。
        val usableTurnRadius=turnRadius?.takeIf {it.isFinite()&&it>0}
        val extent=max(2000.0,a.distance(b)*0.75).coerceAtMost(60_000.0)
        val minX=min(a.x,b.x)-extent;val maxX=max(a.x,b.x)+extent;val minY=min(a.y,b.y)-extent;val maxY=max(a.y,b.y)+extent
        val span=max(maxX-minX,maxY-minY)
        val rasterStep=world.rasterAreas.minOfOrNull { area ->
            val latitude=(start.latitude+end.latitude)/2.0
            val eastWest=area.grid.pixelWidthDegrees*111_320.0*cos(Math.toRadians(latitude)).coerceAtLeast(.15)
            val northSouth=area.grid.pixelHeightDegrees*110_540.0
            max(25.0,min(eastWest,northSouth))
        }
        // 搜索分辨率不应比 15″ GEBCO 本身粗很多，否则小岛/海峡会被跳过；同时保持有界节点数。
        val step=max(25.0,min(span/180.0,(rasterStep?:span/160.0).coerceAtMost(750.0)))
        val cols=ceil((maxX-minX)/step).toInt()+1;val rows=ceil((maxY-minY)/step).toInt()+1
        fun coord(id:Int)=Coordinate(minX+(id%cols)*step,minY+(id/cols)*step)
        fun id(c:Coordinate)=(((c.y-minY)/step).roundToInt().coerceIn(0,rows-1))*cols+((c.x-minX)/step).roundToInt().coerceIn(0,cols-1)
        data class Node(val id:Int,val cost:Double,val score:Double)
        val queue=PriorityQueue<Node>(compareBy{it.score});val scores=DoubleArray(cols*rows){Double.POSITIVE_INFINITY};val parents=IntArray(cols*rows){-1}
        val first=id(a)
        if(!clear(a,coord(first)))return null
        scores[first]=a.distance(coord(first));queue.add(Node(first,scores[first],a.distance(b)))
        var found=-1;var visited=0
        // 自动规划必须有界响应；复杂区域宁可返回“未找到”并让用户加一个途经点，也不无限转圈。
        val visitBudget=min(60_000,max(18_000,cols*rows))
        while(queue.isNotEmpty()&&visited<visitBudget){
            currentCoroutineContext().ensureActive();val node=queue.remove();if(node.cost>scores[node.id])continue
            visited++;if(visited%100==0)onProgress((visited/visitBudget.toFloat()).coerceAtMost(.99f))
            val c=coord(node.id)
            if(c.distance(b)<step*2&&clear(c,b)){found=node.id;break}
            val x=node.id%cols;val y=node.id/cols
            for(dy in -1..1)for(dx in -1..1){if(dx==0&&dy==0)continue;val nx=x+dx;val ny=y+dy;if(nx !in 0 until cols||ny !in 0 until rows)continue;val next=ny*cols+nx;val d=coord(next);val cost=node.cost+c.distance(d);if(cost>=scores[next]||!clear(c,d))continue;scores[next]=cost;parents[next]=node.id;queue.add(Node(next,cost,cost+d.distance(b)))}
        }
        if(found<0)return null
        val reverse=mutableListOf<Coordinate>(b);var current=found
        while(current>=0){reverse.add(coord(current));current=parents[current]};reverse.add(a);reverse.reverse()
        val reduced=mutableListOf(reverse.first());var i=0
        while(i<reverse.lastIndex){var next=reverse.lastIndex;while(next>i+1&&!clear(reverse[i],reverse[next]))next--;reduced.add(reverse[next]);i=next}
        if(!smoothTurns||usableTurnRadius==null)return reduced.map(p::point)
        return smooth(world,reduced.map(p::point),usableTurnRadius)
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
        if(!world.navigable.covers(p.factory.createLineString(smooth.toTypedArray()).buffer(tolerance)))return null
        return smooth.map(p::point)
    }
}
