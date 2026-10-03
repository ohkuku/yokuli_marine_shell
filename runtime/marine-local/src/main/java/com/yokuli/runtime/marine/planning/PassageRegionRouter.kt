package com.yokuli.runtime.marine.planning

import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.*
import com.yokuli.runtime.marine.chart.LocalChartDataService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.locationtech.jts.geom.*
import java.util.PriorityQueue
import kotlin.math.*

/** 持久区域拓扑的真实生产读方。基础海陆图不绑定船型；船型派生图与基础图均可跨请求复用。 */
internal class PassageRegionRouter(private val charts:LocalChartDataService,private val geometry:PassageGeometry) {
    private val compiler=Mutex()
    private val neutral=PassageVessel(null,null,null,null,null,null,null,null)
    private val basePolicy="base-water-semantics-v2"
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
        policy:String,basic:Boolean,onPreparing:()->Unit={}):PassageRegionProduct {
        val key=context.products.key(context.source,policy,id)
        context.products.read(key,context.source,policy,id)?.let{return it}
        // 基础语义面只准备一次。船型不再从原始海图构面。递归发生在锁外避免互锁。
        val base=if(basic)null else product(snapshot,request,context,id,basePolicy,true,onPreparing)
        return compiler.withLock {
            context.products.read(key,context.source,policy,id)?.let{return@withLock it}
            val work=currentCoroutineContext()
            onPreparing()
            // 区域边界外保留 halo，船宽/危险缓冲不会把人工区域接缝误当岸线。
            val center=id.center
            val corners=listOf(ChartPoint(id.south,id.west),ChartPoint(id.south+PassageRegionId.STEP,id.west+PassageRegionId.STEP))
            val padding=corners.maxOf{distance(center,it)}+max(500.0,request.vessel.corridorHalfWidthMeters?:0.0)
            val buildRequest=if(basic)request.copy(vessel=neutral,avoidances=emptyList()) else request
            val world=if(base==null)geometry.world(snapshot,buildRequest,listOf(center,center),padding,PassageWorldPurpose.NAVIGATION_TOPOLOGY)
                else base.semantics.derive(base,request)
            val result=compilePassageRegion(world,context.source,id,policy,base){work.ensureActive()}
            context.products.write(key,result)
            result
        }
    }

    /** 只在真实连通分量及相邻边界区间之间走边，海岸出现在中间时继续搜索相邻区域。 */
    suspend fun route(snapshot:ChartDataSnapshot,request:PassageRequest,start:ChartPoint,end:ChartPoint,
        onProgress:(String)->Unit):Result? {
        val work=currentCoroutineContext()
        // 草稿规划只回答“水域是否连通”。船型参数属于后续风险检查，不得把浅水、未知净空或窄水路从草稿图中删除。
        val vessel=request.vessel
        val routingRequest=request.copy(vessel=neutral)
        // 先消费制包/之前准备好的基础产品，不能在缓存查询之前再构建 raw world。
        // 未设置避让区时直接复用基础面和三角网，不复制、重压缩另一份相同的 .nav。
        val context=context(snapshot)
        val useBase=request.avoidances.isEmpty()
        val policy=if(useBase)basePolicy else passageHash(listOf("water-connectivity-v1",request.avoidances.map{it.boundary},PASSAGE_RULES_VERSION))
        val projections=object:LinkedHashMap<PassageRegionId,PassageProjection>(8,.75f,true) {
            override fun removeEldestEntry(eldest:MutableMap.MutableEntry<PassageRegionId,PassageProjection>?)=size>8
        }
        fun projection(id:PassageRegionId)=projections.getOrPut(id){PassageProjection(id.center){work.ensureActive()}}
        val products=LinkedHashMap<PassageRegionId,PassageRegionProduct>(8,.75f,true)
        fun bytes(value:PassageRegionProduct)=value.estimatedBytes
        fun retain(id:PassageRegionId,value:PassageRegionProduct) {
            val size=bytes(value);if(size>48L*1024*1024)return
            products.remove(id)
            while(products.isNotEmpty()&&products.values.sumOf(::bytes)+size>48L*1024*1024) {
                val iterator=products.entries.iterator();iterator.next();iterator.remove()
            }
            products[id]=value
        }
        val headers=LinkedHashMap<PassageRegionId,PassageRegionHeader>(32,.75f,true)
        fun headerBytes(value:PassageRegionHeader)=value.portals.size*64L+value.evidence.size*512L+value.constraints.size*256L+value.rasters.size*1024L+value.malformed.sumOf{it.length*2L}+1024L
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
                ?:run{
                    claim(id);onProgress("准备基础水域 ${prepared.size} / Preparing base water region ${prepared.size}")
                    product(snapshot,routingRequest,context,id,basePolicy,true) {
                        onProgress("准备缺失基础块 ${id.x}/${id.y} · 完成后复用 / Preparing missing base block ${id.x}/${id.y}; reusable when ready")
                    }.header
                }
            if(headerBytes(header)<=256*1024)baseHeaders[id]=header
            return header
        }
        suspend fun load(id:PassageRegionId):PassageRegionProduct {
            products[id]?.let{return it}
            claim(id)
            onProgress("读取导航区域 ${prepared.size} / Reading navigation region ${prepared.size}")
            // 只有明确画出的避让区才需要派生；常规规划直接读包内基础产品。
            val value=product(snapshot,routingRequest,context,id,policy,useBase) {
                onProgress("准备缺失导航块 ${id.x}/${id.y} · 完成后复用 / Preparing missing navigation block ${id.x}/${id.y}; reusable when ready")
            }
            retain(id,value);retainHeader(id,value.header)
            onProgress("在已准备水域中寻路 / Searching prepared water")
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
        suspend fun clearAcross(a:ChartPoint,b:ChartPoint,extra:Double=0.0,directOnly:Boolean=false,
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
                val tile=if(directOnly&&!useBase)product(snapshot,routingRequest,context,id,basePolicy,true) {
                    onProgress("准备缺失基础块 ${id.x}/${id.y} · 完成后复用 / Preparing missing base block ${id.x}/${id.y}; reusable when ready")
                }else load(id)
                val projection=projection(id)
                val line=projection.line(listOf(atDistance(a,b,from),atDistance(a,b,to)))
                if(directOnly&&!useBase) {
                    if(tile.semantics.direct(tile,routingRequest,line){work.ensureActive()}!=true)return false
                }else {
                    val shape=if(extra>0)line.buffer(extra)else line
                    if(!tile.preparedWater.covers(shape))return false
                }
                observe?.invoke(tile,projection,line,from)
            }
            return true
        }
        suspend fun finishPath(path:List<ChartPoint>,directOnly:Boolean=false):Result? {
            val issues=ArrayList<PassageIssue>();val noted=HashSet<String>();var along=0.0
            for((a,b) in path.zipWithNext()) {
                if(!clearAcross(a,b,directOnly=directOnly) {tile,projection,line,offset->
                    val margin=max(1.0,max(vessel.corridorHalfWidthMeters?:0.0,(vessel.beamMeters?:0.0)/2+(vessel.clearanceMarginMeters?:0.0)))
                    val corridor=line.buffer(margin)
                    for(index in tile.unknownAlong(corridor)) {
                        work.ensureActive();val evidence=tile.header.evidence[index]
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
        // 同一次查线同时收集来源证据，不对成功直线再扫一遍。
        finishPath(listOf(start,end),directOnly=true)?.let{return it}
        val firstId=PassageRegionId.at(start);val lastId=PassageRegionId.at(end)
        val firstProduct=load(firstId);val lastProduct=if(lastId==firstId)firstProduct else load(lastId)
        fun component(product:PassageRegionProduct,point:ChartPoint):Int? {
            val p=projection(product.header.region)
            val probe=p.factory.createPoint(p.xy(point))
            return product.components.indices.firstOrNull{product.components[it].covers(probe)}
        }
        val firstComponent=component(firstProduct,start)?:return null
        val lastComponent=component(lastProduct,end)?:return null
        data class State(val region:PassageRegionId,val component:Int,val entry:String)
        data class Node(val state:State,val point:ChartPoint,val cost:Double,val score:Double)
        data class Connection(val points:List<ChartPoint>,val length:Double)
        data class Link(val previous:State,val path:List<ChartPoint>)
        // 局部连线来自持久导航面，真实绕岸长度参与区域 A*；选完链后无需再次网格搜索。
        val connections=object:LinkedHashMap<List<Any>,Connection?>(128,.75f,true) {
            override fun removeEldestEntry(eldest:MutableMap.MutableEntry<List<Any>,Connection?>?)=size>256
        }
        suspend fun connect(id:PassageRegionId,a:ChartPoint,b:ChartPoint):Connection? {
            val key=listOf(id,a,b)
            if(connections.containsKey(key))return connections[key]
            val tile=load(id);val projection=projection(id)
            val coordinates=tile.mesh.route(projection.xy(a),projection.xy(b),tile.preparedWater){work.ensureActive()}
            val result=coordinates?.map(projection::point)?.toMutableList()?.let {points->
                if(points.size<2)null else {require(points.size<=2000){"NAVIGATION_ROUTE_POINT_BUDGET"};points[0]=a;points[points.lastIndex]=b
                    Connection(points,points.zipWithNext().sumOf{distance(it.first,it.second)})}
            }
            connections[key]=result;return result
        }
        // 短航线已在一个连通分量中：局部网格找到绕行后立即交付，不展开周围区域来继续求更短。
        if(firstId==lastId&&firstComponent==lastComponent) {
            connect(firstId,start,end)?.let {local->
                finishPath(local.points)?.let{return it}
                // 精确跨区复核未通过的连接不能在下面再次当作有效终点连接。
                connections[listOf(firstId,start,end)]=null
            }
        }
        val first=State(firstId,firstComponent,"start")
        val queue=PriorityQueue<Node>(compareBy<Node>{it.score}.thenBy{it.cost})
        val costs=hashMapOf(first to 0.0);val links=HashMap<State,Link>()
        queue+=Node(first,start,0.0,distance(start,end))
        var finish:State?=null;var finishConnection:Connection?=null;var expanded=0
        while(queue.isNotEmpty()) {
            work.ensureActive()
            require(++expanded<=24_000){"NAVIGATION_GRAPH_BUDGET"}
            val node=queue.remove();if(node.cost>costs.getValue(node.state))continue
            if(node.state.region==lastId&&node.state.component==lastComponent) {
                val tail=connect(lastId,node.point,end)
                // 草稿优先首条可连接目的地的路径；不把全局最短性证明当作出线门槛。
                if(tail!=null){finish=node.state;finishConnection=tail;break}
            }
            val current=header(node.state.region)
            for(portal in current.portals.filter{it.component==node.state.component}) {
                val nextId=node.state.region.neighbor(portal.edge)?:continue
                val opposite=portal.edge xor 1
                val basic=baseHeader(nextId)
                if(basic.portals.none{it.edge==opposite&&min(it.upper,portal.upper)-max(it.lower,portal.lower)>1e-8})continue
                val other=header(nextId)
                for(peer in other.portals.filter{it.edge==opposite}) {
                    val lo=max(portal.lower,peer.lower);val hi=min(portal.upper,peer.upper)
                    if(hi-lo<1e-8)continue
                    // 门户候选由固定起终点决定，不随搜索前驱改变，防止循环产生大量不同 entry 状态。
                    fun axis(point:ChartPoint)=if(portal.edge<2)point.latitude else node.state.region.west+((point.longitude-node.state.region.west+540)%360)-180
                    val inset=min((hi-lo)*.01,1e-7)
                    val entries=listOf(axis(end).coerceIn(lo+inset,hi-inset),axis(start).coerceIn(lo+inset,hi-inset),(lo+hi)/2).distinct()
                    for(value in entries) {
                        val at=node.state.region.point(portal.edge,value)
                        val next=State(nextId,peer.component,"${opposite}:${lo}:${hi}:${value}")
                        val known=costs.getOrDefault(next,Double.POSITIVE_INFINITY)
                        // 直线下界已经不可能改善的边，不能先跑一次完整的局部三角网寻路。
                        if(node.cost+distance(node.point,at)>=known)continue
                        val connection=connect(node.state.region,node.point,at)?:continue
                        val cost=node.cost+connection.length
                        if(cost>=known)continue
                        costs[next]=cost;links[next]=Link(node.state,connection.points)
                        queue+=Node(next,at,cost,cost+distance(at,end))
                    }
                }
            }
        }
        val found=finish?:return null
        val chain=ArrayList<List<ChartPoint>>();var cursor=found
        while(cursor!=first){work.ensureActive();val link=links.getValue(cursor);chain+=link.path;cursor=link.previous}
        chain.reverse()
        val result=mutableListOf(start)
        for(path in chain){result+=path.drop(1);require(result.size<=2000){"NAVIGATION_ROUTE_POINT_BUDGET"}}
        result+=requireNotNull(finishConnection).points.drop(1)
        require(result.size<=2000){"NAVIGATION_ROUTE_POINT_BUDGET"}
        // 原始用户端点保持原值；跨区接缝由同一个地理位置连接，不吸附到格子中心。
        result[0]=start;result[result.lastIndex]=end
        // 跨区域门户只负责连通，不能把边界中点留成一串无意义折线；整线查水后拉直。
        val simplified=mutableListOf(start);var anchor=0
        while(anchor<result.lastIndex) {
            work.ensureActive();var next=result.lastIndex
            if(next>anchor+32&&!clearAcross(result[anchor],result[next]))next=anchor+32
            while(next>anchor+1&&!clearAcross(result[anchor],result[next]))next--
            simplified+=result[next];anchor=next
        }
        // 转弯半径只作为候选风险提示；不能让几何平滑失败反过来把真实连续水路判成“无路”。
        return finishPath(simplified)
    }
}
