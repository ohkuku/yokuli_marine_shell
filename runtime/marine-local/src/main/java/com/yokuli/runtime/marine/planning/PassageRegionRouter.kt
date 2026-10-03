package com.yokuli.runtime.marine.planning

import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.*
import com.yokuli.runtime.marine.chart.LocalChartDataService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import java.util.PriorityQueue
import kotlin.math.*

/** 持久区域拓扑的真实生产读方。基础海陆图不绑定船型；船型派生图与基础图均可跨请求复用。 */
internal class PassageRegionRouter(private val charts:LocalChartDataService,private val geometry:PassageGeometry) {
    private val compiler=Mutex()
    private val neutral=PassageVessel(null,null,null,null,null,null,null,null)
    private val basePolicy="base-water-topology-v1"
    internal data class Context(val source:String,val products:PassageRegionProducts)
    data class Result(val points:List<ChartPoint>,val issues:List<PassageIssue>)

    suspend fun context(snapshot:ChartDataSnapshot):Context=Context(charts.preparedSourceIdentity(snapshot.id),
        PassageRegionProducts(charts.preparedNavigationDirectory(snapshot.id)))

    suspend fun prepare(snapshot:ChartDataSnapshot,context:Context,id:PassageRegionId) {
        val request=PassageRequest("prepare",PassageRoute("prepare","prepare","",listOf(id.center,id.center)),
            snapshot.datasets.map{it.id},neutral)
        product(snapshot,request,context,id,basePolicy,true)
    }

    private suspend fun product(snapshot:ChartDataSnapshot,request:PassageRequest,context:Context,id:PassageRegionId,
        policy:String,basic:Boolean):PassageRegionProduct {
        val key=context.products.key(context.source,policy,id)
        context.products.read(key,context.source,policy,id)?.let{return it}
        return compiler.withLock {
            context.products.read(key,context.source,policy,id)?.let{return@withLock it}
            val work=currentCoroutineContext()
            // 区域边界外保留 halo，船宽/危险缓冲不会把人工区域接缝误当岸线。
            val center=id.center
            val corners=listOf(ChartPoint(id.south,id.west),ChartPoint(id.south+PassageRegionId.STEP,id.west+PassageRegionId.STEP))
            val padding=corners.maxOf{distance(center,it)}+max(500.0,request.vessel.corridorHalfWidthMeters?:0.0)
            val buildRequest=if(basic)request.copy(vessel=neutral,avoidances=emptyList()) else request
            val world=geometry.world(snapshot,buildRequest,listOf(center,center),padding,
                if(basic)PassageWorldPurpose.NAVIGATION_TOPOLOGY else PassageWorldPurpose.REFERENCE_DRAFT)
            val p=world.projection;val operations=PassageGeometryOperations{work.ensureActive()}
            // 各边在相同地理位置细分；相邻区使用同一参数，不借投影直弦跨过弯曲边界。
            fun edge(edge:Int):LineString {
                val lower=if(edge<2)id.south else id.west
                return p.line((0..16).map{id.point(edge,lower+PassageRegionId.STEP*it/16)})
            }
            val ring=buildList {
                addAll(edge(2).coordinates.toList())
                addAll(edge(1).coordinates.drop(1))
                addAll(edge(3).coordinates.reversed().drop(1))
                addAll(edge(0).coordinates.reversed().drop(1))
            }.toMutableList().also{if(!it.first().equals2D(it.last()))it+=Coordinate(it.first())}
            val rectangle=p.factory.createPolygon(ring.toTypedArray())
            val clipped=operations.intersection(world.navigable,rectangle)
            val components=ArrayList<Geometry>()
            fun polygons(shape:Geometry) {
                work.ensureActive()
                if(shape is Polygon){if(!shape.isEmpty&&shape.area>.01)components+=shape;return}
                if(shape is GeometryCollection)for(i in 0 until shape.numGeometries)polygons(shape.getGeometryN(i))
            }
            polygons(clipped)
            components.sortWith(compareBy<Geometry>{it.envelopeInternal.minX}.thenBy{it.envelopeInternal.minY}.thenBy{it.area})
            require(components.size<=4096){"NAVIGATION_COMPONENT_LIMIT"}
            val portals=ArrayList<PassagePortal>()
            for((index,component) in components.withIndex())for(side in 0..3) {
                work.ensureActive()
                val hit=operations.intersection(component,edge(side))
                fun addLines(shape:Geometry) {
                    if(shape is LineString) {
                        if(shape.length<.2)return
                        val values=shape.coordinates.map {c->p.point(c).let{point->
                            if(side<2)point.latitude else id.west+((point.longitude-id.west+540)%360)-180
                        }}
                        portals+=PassagePortal(index,side,values.min(),values.max())
                    }else if(shape is GeometryCollection)for(j in 0 until shape.numGeometries)addLines(shape.getGeometryN(j))
                }
                addLines(hit)
            }
            require(portals.size<=16384){"NAVIGATION_PORTAL_LIMIT"}
            val uncertain=(world.referenceDatumFeatures+world.unknownDepthFeatures).distinctBy{it.feature.id}
            require(uncertain.size<=8192){"NAVIGATION_EVIDENCE_LIMIT"}
            val header=PassageRegionHeader(source=context.source,policy=policy,region=id,portals=portals,
                componentCount=components.size,evidence=uncertain.map{item->
                    PassageRegionEvidence(item.feature.id,item.feature.cellId,item.feature.depth)
                },malformed=world.malformed,margin=world.margin)
            val result=PassageRegionProduct(header,components,uncertain.map{it.geometry},world.navigable)
            context.products.write(key,result)
            result
        }
    }

    /** 只在真实连通分量及相邻边界区间之间走边，海岸出现在中间时继续搜索相邻区域。 */
    suspend fun route(snapshot:ChartDataSnapshot,request:PassageRequest,start:ChartPoint,end:ChartPoint,
        onProgress:(String)->Unit):Result? {
        val work=currentCoroutineContext();val context=context(snapshot)
        val policy=passageHash(listOf("vessel-region-v1",request.vessel,request.avoidances,PASSAGE_RULES_VERSION))
        val products=LinkedHashMap<PassageRegionId,PassageRegionProduct>(8,.75f,true)
        fun bytes(value:PassageRegionProduct)=
            (value.waterWithHalo.numPoints.toLong()+value.components.sumOf{it.numPoints.toLong()}+
                value.unknownDepth.sumOf{it.numPoints.toLong()})*64L+value.header.evidence.size*1024L+4096
        fun retain(id:PassageRegionId,value:PassageRegionProduct) {
            val size=bytes(value);if(size>48L*1024*1024)return
            products.remove(id)
            while(products.isNotEmpty()&&products.values.sumOf(::bytes)+size>48L*1024*1024) {
                val iterator=products.entries.iterator();iterator.next();iterator.remove()
            }
            products[id]=value
        }
        val headers=LinkedHashMap<PassageRegionId,PassageRegionHeader>(32,.75f,true)
        fun headerBytes(value:PassageRegionHeader)=value.portals.size*64L+value.evidence.size*512L+value.malformed.sumOf{it.length*2L}+1024L
        fun retainHeader(id:PassageRegionId,value:PassageRegionHeader) {
            headers.remove(id);val bytes=headerBytes(value)
            while(headers.isNotEmpty()&&headers.values.sumOf(::headerBytes)+bytes>24L*1024*1024) {
                val iterator=headers.entries.iterator();iterator.next();iterator.remove()
            }
            if(bytes<=24L*1024*1024)headers[id]=value
        }
        val prepared=HashSet<PassageRegionId>()
        fun claim(id:PassageRegionId) {
            prepared+=id
            require(prepared.size<=192){"导航区域搜索达到预算，已准备的区域已保留 / Navigation region search reached its budget; prepared regions are retained"}
        }
        val baseHeaders=object:LinkedHashMap<PassageRegionId,PassageRegionHeader>(16,.75f,true) {
            override fun removeEldestEntry(eldest:MutableMap.MutableEntry<PassageRegionId,PassageRegionHeader>?)=size>32
        }
        suspend fun baseHeader(id:PassageRegionId):PassageRegionHeader {
            baseHeaders[id]?.let{return it}
            val header=context.products.readHeader(context.products.key(context.source,basePolicy,id),context.source,basePolicy,id)
                ?:run{claim(id);product(snapshot,request,context,id,basePolicy,true).header}
            if(headerBytes(header)<=256*1024)baseHeaders[id]=header
            return header
        }
        suspend fun load(id:PassageRegionId):PassageRegionProduct {
            products[id]?.let{return it}
            claim(id)
            // 先复用/生成不依赖船型的基础拓扑，参数化结果不覆盖基础版本。
            product(snapshot,request,context,id,basePolicy,true)
            val value=product(snapshot,request,context,id,policy,false)
            retain(id,value);retainHeader(id,value.header)
            return value
        }
        suspend fun header(id:PassageRegionId):PassageRegionHeader {
            headers[id]?.let{return it}
            val chosen=policy
            context.products.readHeader(context.products.key(context.source,chosen,id),context.source,chosen,id)?.let{
                retainHeader(id,it);return it
            }
            onProgress("准备水域 ${prepared.size+1} / Preparing water region ${prepared.size+1}")
            return load(id).header
        }
        // 沿真实 WGS84 航段求每个固定地理网格边界的交点；仅访问被航段触及的区域。
        // 保留 halo 的精确几何查整条分段，不以端点都在水上代替查线。
        suspend fun clearAcross(a:ChartPoint,b:ChartPoint,extra:Double=0.0,
            observe:((PassageRegionProduct,PassageProjection,Geometry,Double)->Unit)?=null):Boolean {
            val length=distance(a,b)
            if(length<.01)return true
            val cuts=java.util.TreeSet<Double>().apply{add(0.0);add(length)}
            val chunks=max(1,ceil(length/5000.0).toInt())
            fun lon(p:ChartPoint)=a.longitude+((p.longitude-a.longitude+540)%360)-180
            for(chunk in 0 until chunks) {
                work.ensureActive()
                val from=length*chunk/chunks;val to=length*(chunk+1)/chunks
                val first=atDistance(a,b,from);val last=atDistance(a,b,to)
                cuts+=from;cuts+=to
                for(axis in 0..1) {
                    val v0=if(axis==0)first.latitude else lon(first)
                    val v1=if(axis==0)last.latitude else lon(last)
                    val lower=ceil(min(v0,v1)/PassageRegionId.STEP).toInt()
                    val upper=floor(max(v0,v1)/PassageRegionId.STEP).toInt()
                    for(boundary in lower..upper) {
                        val target=boundary*PassageRegionId.STEP
                        var lo=from;var hi=to
                        repeat(32) {
                            val mid=(lo+hi)/2;val point=atDistance(a,b,mid)
                            val value=if(axis==0)point.latitude else lon(point)
                            if((value<target)==(v0<v1))lo=mid else hi=mid
                        }
                        val cut=(lo+hi)/2;if(cut>from+1e-5&&cut<to-1e-5)cuts+=cut
                    }
                }
            }
            val intervals=cuts.toList()
            for(index in 0 until intervals.lastIndex) {
                work.ensureActive()
                val from=intervals[index];val to=intervals[index+1]
                if(to-from<1e-5)continue
                val id=PassageRegionId.at(atDistance(a,b,(from+to)/2))
                val tile=load(id);val projection=PassageProjection(id.center){work.ensureActive()}
                val line=projection.line(listOf(atDistance(a,b,from),atDistance(a,b,to)))
                val shape=if(extra>0)line.buffer(extra)else line
                if(!PreparedGeometryFactory.prepare(tile.waterWithHalo).covers(shape))return false
                observe?.invoke(tile,projection,line,from)
            }
            return true
        }
        suspend fun finishPath(path:List<ChartPoint>):Result? {
            val issues=ArrayList<PassageIssue>();val noted=HashSet<String>();var along=0.0
            for((a,b) in path.zipWithNext()) {
                if(!clearAcross(a,b) {tile,projection,line,offset->
                    val corridor=if(tile.header.margin>0)line.buffer(tile.header.margin)else line
                    for((index,evidence) in tile.header.evidence.withIndex()) {
                        work.ensureActive()
                        if(evidence.featureId in noted||!tile.unknownDepth[index].intersects(corridor))continue
                        noted+=evidence.featureId
                        val nearby=org.locationtech.jts.operation.distance.DistanceOp.nearestPoints(line,tile.unknownDepth[index])[0]
                        val onLine=org.locationtech.jts.linearref.LengthIndexedLine(line).project(nearby)
                        issues+=PassageIssue("region:depth:${issues.size}",PassageSeverity.INSUFFICIENT,PassageIssueKind.DEPTH,0,
                            projection.point(nearby),along+offset+onLine,
                            "此段缺少完整深度或垂直基准，仅作参考草稿 / This segment lacks complete depth or vertical-datum evidence; reference draft only",
                            evidence.featureId,evidence.cellId,evidence.depth)
                    }
                    if(tile.header.malformed.isNotEmpty()&&noted.add("unknown:${tile.header.region}"))issues+=PassageIssue(
                        "region:unknown:${issues.size}",PassageSeverity.INSUFFICIENT,PassageIssueKind.DATA,0,
                        projection.point(line.coordinate),along+offset,
                        "本区部分资料无法完整解释，请核对原海图 / Some data in this region could not be interpreted completely; review the source chart")
                })return null
                along+=distance(a,b)
            }
            return Result(path,issues)
        }
        if(clearAcross(start,end))return finishPath(listOf(start,end))
        val firstId=PassageRegionId.at(start);val lastId=PassageRegionId.at(end)
        val firstProduct=load(firstId);val lastProduct=if(lastId==firstId)firstProduct else load(lastId)
        fun component(product:PassageRegionProduct,point:ChartPoint):Int? {
            val p=PassageProjection(product.header.region.center){work.ensureActive()}
            val probe=p.factory.createPoint(p.xy(point))
            return product.components.indices.firstOrNull{product.components[it].covers(probe)}
        }
        val firstComponent=component(firstProduct,start)?:return null
        val lastComponent=component(lastProduct,end)?:return null
        data class State(val region:PassageRegionId,val component:Int,val entry:String)
        data class Node(val state:State,val point:ChartPoint,val cost:Double,val score:Double)
        data class Link(val previous:State,val point:ChartPoint)
        val first=State(firstId,firstComponent,"start")
        val queue=PriorityQueue<Node>(compareBy<Node>{it.score}.thenBy{it.cost})
        val costs=hashMapOf(first to 0.0);val positions=hashMapOf(first to start);val links=HashMap<State,Link>()
        queue+=Node(first,start,0.0,distance(start,end))
        var finish:State?=null;var expanded=0
        while(queue.isNotEmpty()) {
            work.ensureActive()
            require(++expanded<=24_000){"NAVIGATION_GRAPH_BUDGET"}
            val node=queue.remove();if(node.cost>costs.getValue(node.state))continue
            if(node.state.region==lastId&&node.state.component==lastComponent){finish=node.state;break}
            val current=header(node.state.region)
            for(portal in current.portals.filter{it.component==node.state.component}) {
                val nextId=node.state.region.neighbor(portal.edge)?:continue
                val opposite=portal.edge xor 1
                // 已准备的公共海陆图先排除没有真实连接的相邻区，再加载船型精确派生。
                // 吃水等参数只能缩小该基础水域，不得用参数化缺测把陆地补成可通行。
                val basic=baseHeader(nextId)
                if(basic.portals.none{it.edge==opposite&&min(it.upper,portal.upper)-max(it.lower,portal.lower)>1e-8})continue
                val other=header(nextId)
                for(peer in other.portals.filter{it.edge==opposite}) {
                    val lo=max(portal.lower,peer.lower);val hi=min(portal.upper,peer.upper)
                    if(hi-lo<1e-8)continue
                    val at=node.state.region.point(portal.edge,(lo+hi)/2)
                    val next=State(nextId,peer.component,"${opposite}:${lo}:${hi}")
                    val cost=node.cost+distance(node.point,at)
                    if(cost>=costs.getOrDefault(next,Double.POSITIVE_INFINITY))continue
                    costs[next]=cost;positions[next]=at;links[next]=Link(node.state,at)
                    queue+=Node(next,at,cost,cost+distance(at,end))
                }
            }
        }
        val found=finish?:return null
        val chain=ArrayList<State>();var cursor=found
        while(true){chain+=cursor;if(cursor==first)break;cursor=links.getValue(cursor).previous}
        chain.reverse()
        val result=mutableListOf(start)
        for((index,state) in chain.withIndex()) {
            work.ensureActive();onProgress("连接航段 ${index+1}/${chain.size} / Connecting passage ${index+1}/${chain.size}")
            val tile=load(state.region);val base=tile.world{work.ensureActive()}
            val from=positions.getValue(state);val to=if(index==chain.lastIndex)end else positions.getValue(chain[index+1])
            // 门户位于人为分区边缘；反投影再投影的舍入不应把它当成陆地。
            // 局部路径在原始 halo 水域内求解，搜索范围仍由所选分量约束；不扩张真实水域。
            val envelope=org.locationtech.jts.geom.Envelope(tile.components[state.component].envelopeInternal).apply{expandBy(1.0)}
            val world=base.copy(searchBounds=envelope)
            val path=geometry.search(world,from,to,null,smoothTurns=false){ }?:return null
            val line=world.projection.line(path)
            // 必须完整覆盖最终折线，不能只用连通分量/边界相交来宣布路径成立。
            if(!PreparedGeometryFactory.prepare(world.navigable).covers(line))return null
            result+=path.drop(1)
            require(result.size<=2000){"NAVIGATION_ROUTE_POINT_BUDGET"}
        }
        // 原始用户端点保持原值；跨区接缝由同一个地理位置连接，不吸附到格子中心。
        result[0]=start;result[result.lastIndex]=end
        // 跨区域门户只负责连通，不能把边界中点留成一串无意义折线；整线查水后拉直。
        val simplified=mutableListOf(start);var anchor=0
        while(anchor<result.lastIndex) {
            work.ensureActive();var next=min(result.lastIndex,anchor+32)
            while(next>anchor+1&&!clearAcross(result[anchor],result[next]))next--
            simplified+=result[next];anchor=next
        }
        val radius=request.vessel.turnRadiusMeters?.takeIf{it.isFinite()&&it>0}
        if(radius!=null&&simplified.size>2) {
            val curved=mutableListOf(simplified.first())
            for(index in 1 until simplified.lastIndex) {
                work.ensureActive()
                val p=PassageProjection(simplified[index]);val corner=Coordinate(0.0,0.0)
                val before=p.xy(simplified[index-1]);val after=p.xy(simplified[index+1])
                val inLength=before.distance(corner);val outLength=after.distance(corner)
                if(inLength<.01||outLength<.01)return null
                val ux=-before.x/inLength;val uy=-before.y/inLength
                val vx=after.x/outLength;val vy=after.y/outLength
                val angle=acos((ux*vx+uy*vy).coerceIn(-1.0,1.0))
                if(angle<.01){curved+=simplified[index];continue}
                val tangent=radius*tan(angle/2)
                if(!tangent.isFinite()||tangent>min(inLength,outLength)*.45)return null
                val entry=Coordinate(-ux*tangent,-uy*tangent);val exit=Coordinate(vx*tangent,vy*tangent)
                val side=if(ux*vy-uy*vx>0)1 else -1
                val center=Coordinate(entry.x-uy*radius*side,entry.y+ux*radius*side)
                val initial=atan2(entry.y-center.y,entry.x-center.x)
                val count=max(4,ceil(angle/Math.toRadians(5.0)).toInt())
                curved+=p.point(entry)
                for(step in 1..count) {
                    val theta=initial+side*angle*step/count
                    curved+=p.point(Coordinate(center.x+radius*cos(theta),center.y+radius*sin(theta)))
                }
                curved[curved.lastIndex]=p.point(exit)
            }
            curved+=simplified.last()
            val tolerance=radius*(1-cos(Math.toRadians(2.5)))+.25
            for((a,b) in curved.zipWithNext())if(!clearAcross(a,b,tolerance))return null
            return finishPath(curved)
        }
        return finishPath(simplified)
    }
}
