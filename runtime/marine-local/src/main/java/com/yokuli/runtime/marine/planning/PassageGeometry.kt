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
internal data class PassageWorld(val projection:PassageProjection,val features:List<FeatureGeometry>,val coverage:Geometry,val navigable:Geometry,val malformed:List<String>,val margin:Double)

internal class PassageGeometry(private val charts:ChartDataService) {
    suspend fun world(snapshot:ChartDataSnapshot,request:PassageRequest,points:List<ChartPoint>,padding:Double):PassageWorld {
        val projection=PassageProjection(points.first());val factory=projection.factory
        val bounds=around(points,padding)
        val features=ArrayList<NauticalFeature>();var cursor:String?=null
        do {currentCoroutineContext().ensureActive();val page=charts.query(snapshot.id,bounds,2000,cursor);require(!page.truncated){"Chart query is incomplete"};features.addAll(page.features);require(features.size<=120_000){"Area contains too many chart objects; use a shorter passage"};require(!page.hasMore||page.nextAfterId!=null&&page.nextAfterId!=cursor){"Chart query cursor did not advance"};cursor=if(page.hasMore)page.nextAfterId else null}while(cursor!=null)
        val malformed=mutableListOf<String>()
        var occupied:Geometry=factory.createPolygon()
        val masks=mutableMapOf<String,Geometry>()
        val cells=snapshot.datasets.flatMapIndexed{index,dataset->dataset.cells.groupBy{it.cellId}.values.map{versions->versions.maxWith(compareBy<ChartCellRevision>{it.edition}.thenBy{it.update})}.filterNot{it.cancelled}.map{Triple(index,dataset,it)}}.sortedWith(compareBy<Triple<Int,ChartDataset,ChartCellRevision>>{it.first}.thenBy{it.third.compilationScale?:Int.MAX_VALUE}.thenByDescending{it.third.edition}.thenByDescending{it.third.update})
        for((_,dataset,cell) in cells){
            currentCoroutineContext().ensureActive()
            if(!dataset.eligibility.allowsAnalysis(System.currentTimeMillis())||!dataset.offlineReadable||dataset.issue!=null)continue
            val valid=cell.coverage.filter{it.covered}.mapNotNull{runCatching{projection.geometry(it.geometry)}.onFailure{malformed.add(cell.cellId)}.getOrNull()}
            val gaps=cell.coverage.filterNot{it.covered}.mapNotNull{runCatching{projection.geometry(it.geometry)}.onFailure{malformed.add(cell.cellId)}.getOrNull()}
            val coverage=union(valid,factory).difference(union(gaps,factory))
            masks["${dataset.id}/${cell.cellId}"]=coverage.difference(occupied)
            occupied=occupied.union(coverage)
            if(cell.hasUnsupportedSemantic||cell.issues.isNotEmpty())malformed.add(cell.cellId)
        }
        val projected=features.mapNotNull{feature->
            val mask=masks["${feature.datasetId}/${feature.cellId}"]?:return@mapNotNull null
            if(mask.isEmpty)return@mapNotNull null
            runCatching{FeatureGeometry(feature,projection.geometry(feature.geometry).intersection(mask))}.onFailure{malformed.add(feature.id)}.getOrNull()?.takeUnless{it.geometry.isEmpty}
        }
        val vessel=request.vessel
        val margin=max(vessel.corridorHalfWidthMeters?:0.0,(vessel.beamMeters?:0.0)/2+(vessel.clearanceMarginMeters?:0.0))
        val required=vessel.draftMeters?.let{draft->vessel.minimumUnderKeelMeters?.let{draft+it}}
        val depthAreas=projected.filter {fg->
            val feature=fg.feature;val d=feature.depth;val low=d?.lowerMeters
            feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)&&
                d?.kind==DepthEvidenceKind.INTERVAL&&!d.datum.isNullOrBlank()&&low!=null&&low.isFinite()&&required!=null&&low>=required&&feature.issues.isEmpty()
        }.map{it.geometry}
        val blocked=projected.filter{it.feature.kind in setOf(NauticalFeatureKind.LAND,NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.OBSTRUCTION,NauticalFeatureKind.WRECK,NauticalFeatureKind.ROCK,NauticalFeatureKind.RESTRICTED,NauticalFeatureKind.TRAFFIC,NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD)||it.feature.attributes["RESTRN"]?.isNotBlank()==true||it.feature.issues.isNotEmpty()}.map{it.geometry.buffer(max(1.0,margin))}.toMutableList()
        // 重叠深度证据取保守交集：浅区/未知区不能被旁边的深区union盖掉。
        projected.filter{it.feature.kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)}.forEach {fg->
            val d=fg.feature.depth
            if(required==null||d?.kind!=DepthEvidenceKind.INTERVAL||d.datum.isNullOrBlank()||d.lowerMeters?.let{it.isFinite()&&it>=required}!=true)
                blocked.add(fg.geometry.buffer(max(1.0,margin)))
        }
        projected.filter{it.feature.kind==NauticalFeatureKind.SOUNDING}.forEach{fg->fg.feature.geometry.parts.flatMap{it.points}.forEach{p->
            val at=factory.createPoint(projection.xy(p))
            if(fg.geometry.covers(at)&&p.depthMeters?.let{required!=null&&it<required}==true)blocked.add(at.buffer(max(1.0,margin)))
        }}
        request.avoidances.forEach{a->runCatching{projection.geometry(ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(a.boundary))))}.onSuccess{blocked.add(it.buffer(margin))}.onFailure{malformed.add(a.id)}}
        val navigable=union(depthAreas,factory).intersection(occupied).buffer(-max(1.0,margin)).difference(union(blocked,factory))
        return PassageWorld(projection,projected,occupied,navigable,malformed.distinct(),margin)
    }

    suspend fun analyze(snapshot:ChartDataSnapshot,request:PassageRequest,onProgress:(Float)->Unit={}):PassageAnalysis {
        require(request.route.points.size in 2..2000){"Choose at least two points"}
        request.route.navigationTargetIndices?.let { targets ->
            require(targets.isNotEmpty() && targets==targets.distinct().sorted() && targets.all{it in request.route.points.indices} && targets.last()==request.route.points.lastIndex){"Invalid destination mapping"}
        }
        require(request.route.points.all{it.latitude.isFinite()&&it.longitude.isFinite()&&it.latitude in -89.9..89.9&&it.longitude in -180.0..180.0}){"Invalid route coordinates"}
        val issues=mutableListOf<PassageIssue>();val strips=mutableListOf<PassageStripSpan>();var total=0.0
        fun issue(kind:PassageIssueKind,severity:PassageSeverity,message:String,leg:Int=0,p:ChartPoint?=null,along:Double=0.0,f:NauticalFeature?=null){issues.add(PassageIssue(passageHash("$kind/$leg/${f?.id}/$along/$message"),severity,kind,leg,p,along,message,f?.id,f?.cellId,f?.depth))}
        if(snapshot.missingDatasetIds.isNotEmpty()||snapshot.datasets.isEmpty())issue(PassageIssueKind.DATA,PassageSeverity.INSUFFICIENT,"所选数据集尚未安装或已移除 / Selected data is missing")
        snapshot.datasets.filterNot{it.eligibility.allowsAnalysis(System.currentTimeMillis())&&it.offlineReadable&&it.issue==null}.forEach{issue(PassageIssueKind.DATA,PassageSeverity.INSUFFICIENT,"${it.name}：尚未确认分析用途 / Analysis use not confirmed")}
        val v=request.vessel
        require(listOf(v.draftMeters,v.beamMeters,v.airDraftMeters,v.minimumUnderKeelMeters,v.clearanceMarginMeters,v.corridorHalfWidthMeters,v.turnRadiusMeters,v.plannedSpeedMetersPerSecond).all{it==null||it.isFinite()&&it>=0}){"Invalid vessel dimensions"}
        if(v.draftMeters?.let{it.isFinite()&&it>0}!=true||v.minimumUnderKeelMeters?.let{it.isFinite()&&it>=0}!=true)issue(PassageIssueKind.VESSEL,PassageSeverity.INSUFFICIENT,"设置吃水和富余水深后可检查深度 / Set draft and under-keel margin")
        if(v.beamMeters?.let{it.isFinite()&&it>0}!=true||v.clearanceMarginMeters?.let{it.isFinite()&&it>=0}!=true||v.corridorHalfWidthMeters?.let{it.isFinite()&&it>0}!=true)issue(PassageIssueKind.VESSEL,PassageSeverity.INSUFFICIENT,"设置船宽和避让距离后可检查航行走廊 / Set beam and clearance")
        val required=v.draftMeters?.let{d->v.minimumUnderKeelMeters?.let{d+it}}
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
                val knownDepth=mutableListOf<Geometry>()
                world.features.forEach{(f,g)->
                    if(!g.intersects(corridor))return@forEach
                    val hit=g.intersection(corridor);val p=world.projection.point(hit.coordinate);val at=along(hit.coordinate)
                    when(f.kind){
                        NauticalFeatureKind.LAND,NauticalFeatureKind.DRYING_AREA->issue(PassageIssueKind.LAND,PassageSeverity.CONFLICT,"航段经过陆地或干出区域 / Land or drying area",leg,p,at,f)
                        NauticalFeatureKind.OBSTRUCTION,NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK->issue(PassageIssueKind.OBSTACLE,PassageSeverity.CONFLICT,"航行走廊内有障碍物 / Obstruction in corridor",leg,p,at,f)
                        NauticalFeatureKind.RESTRICTED->issue(PassageIssueKind.RESTRICTION,PassageSeverity.REVIEW,"核对限制区规定 / Review area restrictions",leg,p,at,f)
                        NauticalFeatureKind.TRAFFIC->issue(PassageIssueKind.TRAFFIC,PassageSeverity.REVIEW,"核对通航方向和交通规则 / Review traffic direction",leg,p,at,f)
                        NauticalFeatureKind.BRIDGE,NauticalFeatureKind.OVERHEAD->{val clear=(f.attributes["VERCLR"]?.toDoubleOrNull()?:f.attributes["VERCCL"]?.toDoubleOrNull())?.takeIf{it.isFinite()&&it>=0};val need=v.airDraftMeters?.let{d->v.clearanceMarginMeters?.let{d+it}};issue(PassageIssueKind.CLEARANCE,if(clear!=null&&need!=null&&clear<need)PassageSeverity.CONFLICT else if(clear==null||need==null||f.source.verticalDatum==null)PassageSeverity.INSUFFICIENT else PassageSeverity.REVIEW,"核对桥梁净空、水位基准和开桥条件 / Check overhead clearance and datum",leg,p,at,f)}
                        NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA->{
                            val d=f.depth;val cut=g.intersection(line)
                            if(!cut.isEmpty){
                                for(part in 0 until cut.numGeometries){val segment=cut.getGeometryN(part);if(segment.isEmpty)continue
                                    val bounds=segment.coordinates.map(::along);strips.add(PassageStripSpan(bounds.min(),bounds.max(),leg,d,true,f.id))}
                            }
                            val low=d?.lowerMeters;val high=d?.upperMeters
                            if(d?.kind==DepthEvidenceKind.INTERVAL&&!d.datum.isNullOrBlank()&&low!=null&&low.isFinite()&&f.issues.isEmpty()){
                                knownDepth.add(g)
                                if(required!=null&&low<required)issue(PassageIssueKind.DEPTH,if(high!=null&&high<required)PassageSeverity.CONFLICT else PassageSeverity.REVIEW,"海图深度不足或区间跨过所需深度 / Depth below or spans requirement",leg,p,at,f)
                            }
                        }
                        NauticalFeatureKind.SOUNDING->f.geometry.parts.flatMap{it.points}.forEach{sp->val gp=world.projection.factory.createPoint(world.projection.xy(sp));if(g.covers(gp)&&gp.intersects(corridor)){val atPoint=along(gp.coordinate);strips.add(PassageStripSpan(atPoint,atPoint,leg,DepthEvidence(DepthEvidenceKind.POINT,pointMeters=sp.depthMeters,datum=f.depth?.datum),true,f.id));if(sp.depthMeters?.let{required!=null&&it<required}==true)issue(PassageIssueKind.DEPTH,PassageSeverity.CONFLICT,"独立测深点浅于所需深度 / Shallow sounding",leg,sp,atPoint,f)}}
                        NauticalFeatureKind.QUALITY->issue(PassageIssueKind.QUALITY,PassageSeverity.REVIEW,"查看测量质量和资料日期 / Review survey quality",leg,p,at,f)
                        else->Unit
                    }
                    if(f.attributes["RESTRN"]?.isNotBlank()==true&&f.kind!=NauticalFeatureKind.RESTRICTED)issue(PassageIssueKind.RESTRICTION,PassageSeverity.REVIEW,"该对象包含限制条件，请查阅详情 / Object has restrictions; review its terms",leg,p,at,f)
                    if(f.issues.isNotEmpty())issue(PassageIssueKind.DATA,PassageSeverity.INSUFFICIENT,"对象包含未支持语义 / Unsupported object semantics",leg,p,at,f)
                }
                if(!union(knownDepth,world.projection.factory).covers(corridor))issue(PassageIssueKind.DEPTH,PassageSeverity.INSUFFICIENT,"部分走廊没有可信深度区间 / Depth evidence is incomplete",leg,a,offset)
                request.avoidances.forEach{avoid->val shape=runCatching{world.projection.geometry(ChartGeometry(ChartGeometryKind.POLYGON,listOf(ChartGeometryPart(avoid.boundary))))}.getOrNull();if(shape?.intersects(corridor)==true)issue(PassageIssueKind.AVOIDANCE,PassageSeverity.CONFLICT,"${avoid.name} / Avoidance area",leg,a,offset)}
                onProgress((leg+(chunk+1f)/chunks)/(request.route.points.size-1))
            }
            total+=length
        }
        val unique=issues.distinctBy{listOf(it.legIndex,it.kind,it.featureId,it.message)}.sortedBy{it.alongMeters}
        val severity=listOf(PassageSeverity.CONFLICT,PassageSeverity.INSUFFICIENT,PassageSeverity.REVIEW).firstOrNull{level->unique.any{it.severity==level}}?:PassageSeverity.NO_CONFLICT_FOUND
        val speed=v.plannedSpeedMetersPerSecond?.takeIf{it.isFinite()&&it>0.1}
        return PassageAnalysis(request.requestId,passageHash(listOf(request.route,request.vessel,request.datasetIds,snapshot.datasets.map{it.id to it.revision},request.avoidances,request.departureUtc,"geometry-1")),request,snapshot.revision,snapshot.datasets.associate{it.id to it.revision},System.currentTimeMillis(),total,request.departureUtc?.let{depart->speed?.let{depart+(total/it*1000).toLong()}},severity,unique,strips)
    }

    /** A* 的每条边和简化后的每条线都检查完整线段；未知深度不是可通行节点。 */
    suspend fun search(world:PassageWorld,start:ChartPoint,end:ChartPoint,turnRadius:Double?,smoothTurns:Boolean=true,onProgress:(Float)->Unit):List<ChartPoint>? {
        if(world.malformed.isNotEmpty())return null
        val p=world.projection;val a=p.xy(start);val b=p.xy(end)
        val prepared=PreparedGeometryFactory.prepare(world.navigable)
        fun clear(x:Coordinate,y:Coordinate)=prepared.covers(p.factory.createLineString(arrayOf(x,y)))
        if(!world.navigable.covers(p.factory.createPoint(a))||!world.navigable.covers(p.factory.createPoint(b)))return null
        if(clear(a,b))return listOf(start,end)
        if(turnRadius==null||turnRadius<=0)return null
        val extent=max(2000.0,a.distance(b)*0.6).coerceAtMost(20_000.0)
        val minX=min(a.x,b.x)-extent;val maxX=max(a.x,b.x)+extent;val minY=min(a.y,b.y)-extent;val maxY=max(a.y,b.y)+extent
        val step=max(25.0,max(maxX-minX,maxY-minY)/140)
        val cols=ceil((maxX-minX)/step).toInt()+1;val rows=ceil((maxY-minY)/step).toInt()+1
        fun coord(id:Int)=Coordinate(minX+(id%cols)*step,minY+(id/cols)*step)
        fun id(c:Coordinate)=(((c.y-minY)/step).roundToInt().coerceIn(0,rows-1))*cols+((c.x-minX)/step).roundToInt().coerceIn(0,cols-1)
        data class Node(val id:Int,val cost:Double,val score:Double)
        val queue=PriorityQueue<Node>(compareBy{it.score});val scores=DoubleArray(cols*rows){Double.POSITIVE_INFINITY};val parents=IntArray(cols*rows){-1}
        val first=id(a)
        if(!clear(a,coord(first)))return null
        scores[first]=a.distance(coord(first));queue.add(Node(first,scores[first],a.distance(b)))
        var found=-1;var visited=0
        while(queue.isNotEmpty()&&visited<24_000){
            currentCoroutineContext().ensureActive();val node=queue.remove();if(node.cost>scores[node.id])continue
            visited++;if(visited%100==0)onProgress((visited/24_000f).coerceAtMost(.99f))
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
        if(!smoothTurns)return reduced.map(p::point)
        return smooth(world,reduced.map(p::point),turnRadius)
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
