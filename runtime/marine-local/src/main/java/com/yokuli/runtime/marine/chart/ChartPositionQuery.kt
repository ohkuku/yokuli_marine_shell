package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

internal data class ChartPositionCellRead(val baseComplete:Boolean,val detailComplete:Boolean)

/** 指点查询只做真实位置命中；不为一个点构造、求交全国海岸与 coverage 的 JTS 多边形。 */
internal class ChartPositionQuery(private val dataset:ChartDataset,private val point:ChartPoint,private val radiusMeters:Double,private val geometryIndex:ChartGeometryQueryIndex) {
    private val longitudeScale=111_320.0*cos(Math.toRadians(point.latitude)).coerceAtLeast(.001)
    private var checks=0
    private var check:()->Unit={}
    private val masks=ArrayList<Mask>()
    private data class PositionKey(val cell:String,val latitude:Double,val longitude:Double)
    private val coveredPositions=HashMap<PositionKey,Boolean>()
    private val occupiedPositions=HashMap<PositionKey,Boolean>()
    private data class Mask(val cell:ChartCellRevision,val grids:List<RasterBathymetryGrid>,val unknown:List<ChartGeometry>)

    suspend fun read(
        readCell:suspend (ChartCellRevision,ChartBounds,suspend (NauticalFeature)->Unit)->ChartPositionCellRead,
        readRaster:suspend (RasterBathymetryGrid,Pair<Int,Int>)->Float?,
    ):ChartPositionInfo {
        val work=currentCoroutineContext();check={work.ensureActive()}
        val dy=radiusMeters/111_320.0;val dx=radiusMeters/longitudeScale
        val bounds=ChartBounds(normalize(point.longitude-dx),(point.latitude-dy).coerceAtLeast(-90.0),normalize(point.longitude+dx),(point.latitude+dy).coerceAtMost(90.0))
        val hits=ArrayList<ChartPositionHit>()
        var rasterChoice:Triple<ChartCellRevision,RasterBathymetryGrid,Pair<Int,Int>>?=null
        var incomplete=false
        val activeCells=dataset.cells.filterNot {it.cancelled}
        val manualOrder=activeCells.any{it.priorityExplicit}
        fun rasterResolution(cell:ChartCellRevision)=dataset.rasters.orEmpty().filter{it.cellId==cell.cellId}
            .minOfOrNull{max(it.pixelWidthDegrees,it.pixelHeightDegrees)}?:Double.POSITIVE_INFINITY
        fun sourceClass(cell:ChartCellRevision)=when {
            cell.detailTier()!=null->0
            cell.featureCount>0->1
            dataset.rasters.orEmpty().any{it.cellId==cell.cellId}->2
            else->3
        }
        val cellComparator=if(manualOrder)
            compareBy<ChartCellRevision>{it.priority}.thenBy{sourceClass(it)}.thenBy{it.detailTier()?:Int.MAX_VALUE}
        else compareBy<ChartCellRevision>{sourceClass(it)}.thenBy{it.detailTier()?:Int.MAX_VALUE}
            .thenBy{it.detailScaleDenominator()?:Int.MAX_VALUE}.thenBy{rasterResolution(it)}.thenBy{it.priority}
        val cells=activeCells.sortedWith(
            cellComparator.thenByDescending{it.edition}.thenByDescending{it.update}.thenBy{it.cellId}
        )
        for(cell in cells) {
            check()
            val grids=dataset.rasters.orEmpty().filter {it.cellId==cell.cellId}
            val unknown=ArrayList<ChartGeometry>()
            val cellHits=ArrayList<ChartPositionHit>()
            // 元数据只排除肯定不相交的文件；不确定几何可能在旧目录 coverage bounds 之外。
            val canSkip=cell.bounds.isNotEmpty()&&cell.bounds.none {intersects(it,bounds)}&&"GPKG_DATELINE_TOPOLOGY_UNCERTAIN" !in cell.issues
            var readStatus=ChartPositionCellRead(baseComplete=true,detailComplete=true)
            if(cell.featureCount>0&&!canSkip) {
                readStatus=readCell(cell,bounds) {feature->
                    check()
                    if(feature.hasUncertainChartGeometry())unknown+=feature.geometry
                    val hit=hit(feature)
                    if(hit!=null&&(!manualOrder||!masks.any {covers(it,hit.nearestPoint)}))cellHits+=hit
                }
            }
            val mask=Mask(cell,grids,unknown)
            if(!readStatus.baseComplete) {
                // Base semantics (ownership + hazards) are incomplete. Never fall through to a
                // lower-precedence source and publish a definitive depth if this source can own the
                // cursor position; that would turn truncation into a plausible but wrong answer.
                val independent=setOf(
                    NauticalFeatureKind.LAND,NauticalFeatureKind.DRYING_AREA,
                    NauticalFeatureKind.OBSTRUCTION,NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK,
                    NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD,NauticalFeatureKind.RESTRICTED,
                    NauticalFeatureKind.TRAFFIC,NauticalFeatureKind.BEACON,NauticalFeatureKind.LIGHT,
                    NauticalFeatureKind.OTHER
                )
                hits+=cellHits.filter {it.feature.hasUncertainChartGeometry()||it.feature.kind in independent}
                incomplete=true
                val ownershipKinds=setOf(
                    NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,
                    NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.LAND
                )
                val pointInDeclaredCoverage=cell.coverage.any{it.covered&&contains(it.geometry,point)}&&
                    !cell.coverage.any{!it.covered&&contains(it.geometry,point)}
                val pointInCellBounds=cell.bounds.isEmpty()||cell.bounds.any {box->
                    point.latitude in box.south..box.north&&box.split().any{part->
                        point.longitude>=part.west&&point.longitude<=part.east
                    }
                }
                val mayOwnPoint=grids.any{it.pixelAt(point)!=null}||pointInDeclaredCoverage||
                    cellHits.any{it.feature.kind in ownershipKinds&&it.distanceMeters<=.001}||
                    (cell.coverage.none{it.covered}&&pointInCellBounds)
                if(mayOwnPoint)break else continue
            }
            if(!readStatus.detailComplete)incomplete=true
            // A single legacy GeoPackage cell may contain several LINZ scale bands. If a finer
            // polygon owns this exact position, coarser features from the same file must not compete
            // with it. Point/line objects do not claim area ownership by themselves.
            val ownershipKinds=setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,
                NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.LAND)
            val winningTier=cellHits.asSequence()
                .filter{it.distanceMeters<=.001&&it.feature.kind in ownershipKinds}
                .mapNotNull{it.feature.detailTier()}.minOrNull()
            val winningScale=cellHits.asSequence()
                .filter{it.distanceMeters<=.001&&it.feature.kind in ownershipKinds&&
                    (winningTier==null||it.feature.detailTier()==null||it.feature.detailTier()==winningTier)}
                .mapNotNull{it.feature.detailScaleDenominator()}.minOrNull()
            for(hit in cellHits) {
                check()
                val tier=hit.feature.detailTier()
                val scale=hit.feature.detailScaleDenominator()
                // Only competing ownership polygons are masked by the winning source tier.
                // Independent rocks, wrecks, lights and other facilities must remain queryable even
                // if their layer is coarser than the local DEPARE/LNDARE owner.
                if(hit.feature.kind in ownershipKinds) {
                    if(winningTier!=null&&tier!=null&&tier!=winningTier)continue
                    if(winningScale!=null&&scale!=null&&scale!=winningScale)continue
                }
                if(hit.feature.hasUncertainChartGeometry())hits+=hit
                else if(unknown.none {contains(it,hit.nearestPoint)}&&withinCoverage(cell,hit.nearestPoint))hits+=hit
            }
            val grid=grids.filter {it.pixelAt(point)!=null}.minWithOrNull(
                compareBy<RasterBathymetryGrid>{max(it.pixelWidthDegrees,it.pixelHeightDegrees)}.thenBy{it.id}
            )
            if(grid!=null&&unknown.none {contains(it,point)}&&(!manualOrder||!masks.any {covers(it,point)})) {
                val pixel=requireNotNull(grid.pixelAt(point))
                val current=rasterChoice
                val better=when {
                    current==null->true
                    manualOrder->cell.priority<current.first.priority ||
                        cell.priority==current.first.priority&&max(grid.pixelWidthDegrees,grid.pixelHeightDegrees)<
                            max(current.second.pixelWidthDegrees,current.second.pixelHeightDegrees)
                    else->max(grid.pixelWidthDegrees,grid.pixelHeightDegrees)<
                        max(current.second.pixelWidthDegrees,current.second.pixelHeightDegrees) ||
                        max(grid.pixelWidthDegrees,grid.pixelHeightDegrees)==max(current.second.pixelWidthDegrees,current.second.pixelHeightDegrees)&&cell.priority<current.first.priority
                }
                if(better)rasterChoice=Triple(cell,grid,pixel)
            }
            masks+=mask
        }
        val raster=rasterChoice?.let {(_,grid,pixel)->ChartPositionRaster(grid,readRaster(grid,pixel))}
        val cellById=dataset.cells.associateBy{it.cellId}
        val ownershipKinds=setOf(
            NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,
            NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.LAND
        )
        val ownershipComparator=if(manualOrder)
            compareBy<ChartPositionHit>{cellById[it.feature.cellId]?.priority?:Int.MAX_VALUE}
                .thenBy{it.feature.detailTier()?:cellById[it.feature.cellId]?.detailTier()?:Int.MAX_VALUE}
                .thenBy{it.feature.detailScaleDenominator()?:cellById[it.feature.cellId]?.detailScaleDenominator()?:Int.MAX_VALUE}
                .thenBy{it.feature.cellId}
        else
            compareBy<ChartPositionHit>{it.feature.detailTier()?:cellById[it.feature.cellId]?.detailTier()?:Int.MAX_VALUE}
                .thenBy{it.feature.detailScaleDenominator()?:cellById[it.feature.cellId]?.detailScaleDenominator()?:Int.MAX_VALUE}
                .thenBy{cellById[it.feature.cellId]?.priority?:Int.MAX_VALUE}
                .thenBy{it.feature.cellId}
        val winningOwner=hits.asSequence()
            .filter{it.feature.kind in ownershipKinds&&it.distanceMeters<=.001}
            .minWithOrNull(ownershipComparator)
        val sourceBoundKinds=ownershipKinds+setOf(
            NauticalFeatureKind.SOUNDING,NauticalFeatureKind.DEPTH_CONTOUR,NauticalFeatureKind.QUALITY
        )
        val resolvedHits=if(winningOwner==null)hits else hits.filter {hit->
            hit.feature.kind !in sourceBoundKinds||ownershipComparator.compare(hit,winningOwner)==0
        }
        val ordered=resolvedHits.distinctBy {it.feature.id}.sortedWith(compareBy<ChartPositionHit> {priority(it.feature)}
            .thenBy {
                val cell=cellById[it.feature.cellId]
                if(manualOrder)cell?.priority?:Int.MAX_VALUE else it.feature.detailTier()?:cell?.detailTier()?:Int.MAX_VALUE
            }
            .thenBy {
                val cell=cellById[it.feature.cellId]
                if(manualOrder)it.feature.detailTier()?:cell?.detailTier()?:Int.MAX_VALUE else it.feature.detailScaleDenominator()?:cell?.detailScaleDenominator()?:Int.MAX_VALUE
            }
            .thenBy {
                val cell=cellById[it.feature.cellId]
                if(manualOrder)it.feature.detailScaleDenominator()?:cell?.detailScaleDenominator()?:Int.MAX_VALUE else cell?.priority?:Int.MAX_VALUE
            }
            .thenBy {it.distanceMeters})
        // 深度点密集时仍给实际设施留出位置，避免几百个测深点挤掉一个航标。
        val visible=(ordered.filter{it.feature.kind in ownershipKinds}.take(8)+
            ordered.take(32)+ordered.filter {priority(it.feature)>=4}.take(16))
            .distinctBy {it.feature.id}.take(48)
        return ChartPositionInfo(dataset.id,dataset.revision,dataset.name,visible,raster,incomplete||ordered.size>visible.size)
    }

    private fun priority(feature:NauticalFeature)=when {
        feature.hasUncertainChartGeometry()->0
        feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)->1
        feature.kind==NauticalFeatureKind.SOUNDING->2
        feature.kind==NauticalFeatureKind.DEPTH_CONTOUR->3
        feature.kind==NauticalFeatureKind.LAND->5
        else->4
    }

    private fun covers(mask:Mask,p:ChartPoint):Boolean = occupiedPositions.getOrPut(PositionKey(mask.cell.cellId,p.latitude,p.longitude)) {
        mask.grids.any {it.pixelAt(p)!=null}||mask.unknown.any {contains(it,p)}||covered(mask.cell,p)
    }

    private fun covered(cell:ChartCellRevision,p:ChartPoint):Boolean = coveredPositions.getOrPut(PositionKey(cell.cellId,p.latitude,p.longitude)) {
        cell.coverage.any {it.covered&&contains(it.geometry,p)}&&!cell.coverage.any {!it.covered&&contains(it.geometry,p)}
    }

    private fun withinCoverage(cell:ChartCellRevision,p:ChartPoint):Boolean =
        cell.coverage.none {it.covered} || covered(cell,p)

    private fun hit(feature:NauticalFeature):ChartPositionHit? {
        if(feature.kind==NauticalFeatureKind.COVERAGE)return null
        val geometry=feature.geometry
        if(geometry.kind==ChartGeometryKind.POLYGON) {
            if(!contains(geometry,point))return null
            // 保留对象身份与原始属性。空几何明确表示摘要，不能伪装为准星处测深点。
            return ChartPositionHit(feature.copy(geometry=ChartGeometry(ChartGeometryKind.NONE,emptyList())),0.0,point)
        }
        var nearest:ChartPoint?=null;var distance=Double.POSITIVE_INFINITY
        if(geometry.kind in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT)) {
            for(part in geometry.parts)for(p in part.points) {
                tick();val d=hypot(dx(p.longitude), (p.latitude-point.latitude)*111_320.0)
                if(d<distance){distance=d;nearest=p}
            }
        }else if(geometry.kind==ChartGeometryKind.LINE) {
            for(part in geometry.parts)for(i in 1 until part.points.size) {
                tick();val a=part.points[i-1];val b=part.points[i]
                // 同一短边连续解缠，不能把对跖线附近的远处边连接成横穿准星的全球长边。
                val start=dx(a.longitude);val end=start+normalize(b.longitude-a.longitude)*longitudeScale
                val shift=round((start+end)/(720.0*longitudeScale))*360.0*longitudeScale
                val x=start-shift;val y=(a.latitude-point.latitude)*111_320.0
                val u=end-shift;val v=(b.latitude-point.latitude)*111_320.0
                val vx=u-x;val vy=v-y
                val t=(-(x*vx+y*vy)/(vx*vx+vy*vy).coerceAtLeast(.000001)).coerceIn(0.0,1.0)
                val px=x+t*vx;val py=y+t*vy;val d=hypot(px,py)
                if(d<distance){distance=d;nearest=ChartPoint(point.latitude+py/111_320.0,normalize(point.longitude+px/longitudeScale))}
            }
        }
        val p=nearest ?: return null
        if(distance>radiusMeters)return null
        val compact=if(geometry.kind in setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT))
            feature.copy(geometry=ChartGeometry(ChartGeometryKind.POINT,listOf(ChartGeometryPart(listOf(p)))),
                depth=if(feature.kind==NauticalFeatureKind.SOUNDING)feature.depth?.copy(pointMeters=p.depthMeters ?: feature.depth?.pointMeters?.takeIf {geometry.kind==ChartGeometryKind.POINT})else feature.depth)
        else feature.copy(geometry=ChartGeometry(ChartGeometryKind.NONE,emptyList()))
        return ChartPositionHit(compact,distance,p)
    }

    private fun contains(geometry:ChartGeometry,p:ChartPoint):Boolean = geometryIndex.contains(geometry,p,check)
    private fun tick(){if(++checks%256==0)check()}
    private fun dx(longitude:Double)=normalize(longitude-point.longitude)*longitudeScale
    private fun normalize(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
    private fun intersects(a:ChartBounds,b:ChartBounds)=a.split().any {x->b.split().any {y->x.east>=y.west&&x.west<=y.east&&x.north>=y.south&&x.south<=y.north}}
}
