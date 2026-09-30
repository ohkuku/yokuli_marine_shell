package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

/** 指点查询只做真实位置命中；不为一个点构造、求交全国海岸与 coverage 的 JTS 多边形。 */
internal class ChartPositionQuery(private val dataset:ChartDataset,private val point:ChartPoint,private val radiusMeters:Double) {
    private val longitudeScale=111_320.0*cos(Math.toRadians(point.latitude)).coerceAtLeast(.001)
    private var checks=0
    private var check:()->Unit={}
    private val masks=ArrayList<Mask>()
    private data class PositionKey(val cell:String,val latitude:Double,val longitude:Double)
    private val coveredPositions=HashMap<PositionKey,Boolean>()
    private val occupiedPositions=HashMap<PositionKey,Boolean>()
    private data class Mask(val cell:ChartCellRevision,val grids:List<RasterBathymetryGrid>,val unknown:List<ChartGeometry>)

    suspend fun read(
        readCell:suspend (ChartCellRevision,ChartBounds,suspend (NauticalFeature)->Unit)->Boolean,
        readRaster:suspend (RasterBathymetryGrid,Pair<Int,Int>)->Float?,
    ):ChartPositionInfo {
        val work=currentCoroutineContext();check={work.ensureActive()}
        val dy=radiusMeters/111_320.0;val dx=radiusMeters/longitudeScale
        val bounds=ChartBounds(normalize(point.longitude-dx),(point.latitude-dy).coerceAtLeast(-90.0),normalize(point.longitude+dx),(point.latitude+dy).coerceAtMost(90.0))
        val hits=ArrayList<ChartPositionHit>()
        var raster:ChartPositionRaster?=null
        var incomplete=false
        fun cellScale(cell:ChartCellRevision)=cell.compilationScale ?: LinzLdsAdapter.scaleBandSortDenominator(cell.linzScaleBand)
        val cells=dataset.cells.filterNot {it.cancelled}.sortedWith(compareBy<ChartCellRevision>{it.priority}
            .thenBy{cellScale(it)?:Int.MAX_VALUE}.thenByDescending{it.edition}.thenByDescending{it.update}.thenBy{it.cellId})
        for(cell in cells) {
            check()
            val grids=dataset.rasters.orEmpty().filter {it.cellId==cell.cellId}
            val unknown=ArrayList<ChartGeometry>()
            val cellHits=ArrayList<ChartPositionHit>()
            // 元数据只排除肯定不相交的文件；不确定几何可能在旧目录 coverage bounds 之外。
            val canSkip=cell.bounds.isNotEmpty()&&cell.bounds.none {intersects(it,bounds)}&&"GPKG_DATELINE_TOPOLOGY_UNCERTAIN" !in cell.issues
            var truncated=false
            if(cell.featureCount>0&&!canSkip) {
                truncated=readCell(cell,bounds) {feature->
                    check()
                    if(feature.hasUncertainChartGeometry())unknown+=feature.geometry
                    val hit=hit(feature)
                    if(hit!=null&&!masks.any {covers(it,hit.nearestPoint)})cellHits+=hit
                }
            }
            val mask=Mask(cell,grids,unknown)
            if(truncated) {
                // 不完整的本文件也不能发布确定深度；保留之前完整查过的高优先级文件。
                hits+=cellHits.filter {it.feature.hasUncertainChartGeometry()}
                incomplete=true
                break
            }
            // A single legacy GeoPackage cell may contain several LINZ scale bands. If a finer
            // polygon owns this exact position, coarser features from the same file must not compete
            // with it. Point/line objects do not claim area ownership by themselves.
            val ownershipKinds=setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,
                NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.LAND)
            val winningDetail=cellHits.asSequence()
                .filter{it.distanceMeters<=.001&&it.feature.kind in ownershipKinds}
                .mapNotNull{it.feature.detailScaleDenominator()}.minOrNull()
            for(hit in cellHits) {
                check()
                val scale=hit.feature.detailScaleDenominator()
                if(winningDetail!=null&&scale!=null&&scale!=winningDetail)continue
                if(hit.feature.hasUncertainChartGeometry())hits+=hit
                else if(unknown.none {contains(it,hit.nearestPoint)}&&withinCoverage(cell,hit.nearestPoint))hits+=hit
            }
            val grid=grids.sortedBy {it.id}.firstOrNull {it.pixelAt(point)!=null}
            if(raster==null&&grid!=null&&!masks.any {covers(it,point)}&&unknown.none {contains(it,point)}) {
                raster=ChartPositionRaster(grid,readRaster(grid,requireNotNull(grid.pixelAt(point))))
            }
            masks+=mask
        }
        val cellPriority=dataset.cells.associate{it.cellId to it.priority}
        val ordered=hits.distinctBy {it.feature.id}.sortedWith(compareBy<ChartPositionHit> {priority(it.feature)}
            .thenBy {cellPriority[it.feature.cellId]?:Int.MAX_VALUE}
            .thenBy {it.feature.detailScaleDenominator()?:Int.MAX_VALUE}
            .thenBy {it.distanceMeters})
        // 深度点密集时仍给实际设施留出位置，避免几百个测深点挤掉一个航标。
        val visible=(ordered.take(32)+ordered.filter {priority(it.feature)>=4}.take(16)).distinctBy {it.feature.id}.take(48)
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

    private fun contains(geometry:ChartGeometry,p:ChartPoint):Boolean {
        if(geometry.kind!=ChartGeometryKind.POLYGON)return false
        // 有效 MultiPolygon 允许另一个外环落在某外环的孔洞内；不能用 any(hole) 删掉该岛。
        var coverage=0
        for(part in geometry.parts)if(inside(part.points,p))coverage+=if(part.hole)-1 else 1
        return coverage>0
    }

    private fun inside(ring:List<ChartPoint>,p:ChartPoint):Boolean {
        if(ring.size<3)return false
        var minimumLatitude=Double.POSITIVE_INFINITY;var maximumLatitude=Double.NEGATIVE_INFINITY
        val xs=DoubleArray(ring.size)
        var west=Double.POSITIVE_INFINITY;var east=Double.NEGATIVE_INFINITY
        for(i in ring.indices) {
            tick();val vertex=ring[i]
            minimumLatitude=min(minimumLatitude,vertex.latitude);maximumLatitude=max(maximumLatitude,vertex.latitude)
            xs[i]=if(i==0)normalize(vertex.longitude-p.longitude)else xs[i-1]+normalize(vertex.longitude-ring[i-1].longitude)
            west=min(west,xs[i]);east=max(east,xs[i])
        }
        if(p.latitude<minimumLatitude||p.latitude>maximumLatitude)return false
        val queryX=round((west+east)/720.0)*360.0
        if(queryX<west||queryX>east)return false
        var inside=false;var previous=ring.lastIndex
        for(i in ring.indices) {
            tick();val a=ring[previous];val b=ring[i]
            val vx=xs[i]-xs[previous];val vy=b.latitude-a.latitude
            val cross=(queryX-xs[previous])*vy-(p.latitude-a.latitude)*vx
            if(kotlin.math.abs(cross)<=1e-10*(kotlin.math.abs(vx)+kotlin.math.abs(vy)).coerceAtLeast(1e-10)&&
                queryX>=min(xs[i],xs[previous])-1e-10&&queryX<=max(xs[i],xs[previous])+1e-10&&
                p.latitude>=min(a.latitude,b.latitude)-1e-10&&p.latitude<=max(a.latitude,b.latitude)+1e-10)return true
            if((a.latitude>p.latitude)!=(b.latitude>p.latitude)) {
                val crossing=(xs[i]-xs[previous])*(p.latitude-a.latitude)/(b.latitude-a.latitude)+xs[previous]
                if(queryX<crossing)inside=!inside
            }
            previous=i
        }
        return inside
    }
    private fun tick(){if(++checks%256==0)check()}
    private fun dx(longitude:Double)=normalize(longitude-point.longitude)*longitudeScale
    private fun normalize(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
    private fun intersects(a:ChartBounds,b:ChartBounds)=a.split().any {x->b.split().any {y->x.east>=y.west&&x.west<=y.east&&x.north>=y.south&&x.south<=y.north}}
}
