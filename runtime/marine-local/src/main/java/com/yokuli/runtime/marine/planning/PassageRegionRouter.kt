package com.yokuli.runtime.marine.planning

import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.*
import com.yokuli.runtime.marine.chart.LocalChartDataService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.locationtech.jts.geom.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.*

/** Prepared topology -> lazy candidate corridor -> exact local refinement. No compilation in search. */
internal class PassageRegionRouter(private val charts: LocalChartDataService, private val geometry: PassageGeometry) {
    private val compiler = Mutex()
    private val neutral = PassageVessel(null, null, null, null, null, null, null, null)
    private val basePolicy = "base-water-semantics-v2"
    internal data class Context(val source: String, val products: PassageRegionProducts,
        val topology: PassageTopologyStore? = null)
    data class Result(val points: List<ChartPoint>, val issues: List<PassageIssue>)
    private data class ProductKey(val region: PassageRegionId, val filtered: Boolean)
    private class PreparationRequired(val product: ProductKey) : RuntimeException(null, null, false, false)
    private data class Vertex(val region: PassageRegionId, val component: Int, val point: ChartPoint, val goal: Boolean = false)
    private data class EdgeKey(val region: PassageRegionId, val from: ChartPoint, val to: ChartPoint)

    suspend fun context(snapshot: ChartDataSnapshot): Context {
        val source = charts.preparedSourceIdentity(snapshot.id)
        val directory = charts.preparedNavigationDirectory(snapshot.id)
        return Context(source, PassageRegionProducts(directory), PassageTopologyStore(directory))
    }

    suspend fun prepare(snapshot: ChartDataSnapshot, context: Context, id: PassageRegionId) {
        val request = PassageRequest("prepare", PassageRoute("prepare", "prepare", "", listOf(id.center, id.center)),
            snapshot.datasets.map { it.id }, neutral)
        prepareProduct(snapshot, request, context, id, basePolicy, true)
    }

    /** Producer only. Called by explicit preparation, or outside the bounded search after a cache miss. */
    private suspend fun prepareProduct(snapshot: ChartDataSnapshot, request: PassageRequest, context: Context,
        id: PassageRegionId, policy: String, basic: Boolean): PassageRegionProduct {
        val key = context.products.key(context.source, policy, id)
        context.products.read(key, context.source, policy, id)?.let { return it }
        val base = if(basic) null else prepareProduct(snapshot, request, context, id, basePolicy, true)
        return compiler.withLock {
            context.products.read(key, context.source, policy, id)?.let { return@withLock it }
            val work = currentCoroutineContext()
            val center = id.center
            val corners = listOf(ChartPoint(id.south, id.west),
                ChartPoint(id.south + PassageRegionId.STEP, id.west + PassageRegionId.STEP))
            val padding = corners.maxOf { distance(center, it) } + max(500.0, request.vessel.corridorHalfWidthMeters ?: 0.0)
            val buildRequest = if(basic) request.copy(vessel = neutral, avoidances = emptyList()) else request
            val world = if(base == null) geometry.world(snapshot, buildRequest, listOf(center, center), padding,
                PassageWorldPurpose.NAVIGATION_TOPOLOGY) else base.semantics.derive(base, request)
            compilePassageRegion(world, context.source, id, policy, base) { work.ensureActive() }.also {
                context.products.write(key, it)
            }
        }
    }

    /**
     * One bounded search budget for the whole leg, not a renewed timeout after every cache miss.
     * Preparation has a separate deadline and phase; completed nav-v3 products remain reusable.
     * The source snapshot, checked connections and decoded products survive preparation/resume.
     */
    suspend fun route(snapshot: ChartDataSnapshot, request: PassageRequest, start: ChartPoint, end: ChartPoint,
        onPreparing: ((String) -> Unit)? = null, onProgress: (String) -> Unit): Result? {
        val began = System.nanoTime()
        val context = context(snapshot)
        val vessel = request.vessel.copy(turnRadiusMeters = null, plannedSpeedMetersPerSecond = null)
        val routing = request.copy(vessel = vessel)
        val policy = passageHash(listOf("preferred-depth-lazy-v1", vessel, routing.avoidances.map { it.boundary }, PASSAGE_RULES_VERSION))
        val query = Query(context, routing, start, end, policy, onProgress)
        val prepared = HashSet<ProductKey>()
        var searchingNanos = 0L
        var preparingNanos = 0L
        try {
            while(true) {
                currentCoroutineContext().ensureActive()
                val attempt = System.nanoTime()
                var needed: PreparationRequired? = null
                try {
                    val remaining = (SEARCH_BUDGET_MILLIS - searchingNanos / 1_000_000L).coerceAtLeast(0L)
                    onProgress("搜索已准备通道 / Searching prepared corridors")
                    return withTimeout(remaining) { query.search() }
                } catch(missing: PreparationRequired) { needed = missing }
                finally { searchingNanos += System.nanoTime() - attempt }
                val required = requireNotNull(needed).product
                check(prepared.add(required) && prepared.size <= 192) {
                    "NAVIGATION_PREPARATION_LIMIT: preparation did not produce a readable region; inputs retained"
                }
                val id = required.region
                (onPreparing ?: onProgress)(if(required.filtered)
                    "准备选中通道的水深条件 ${id.x}/${id.y} · 非寻路计时 · 完成后复用 / Preparing selected corridor depth constraints; reusable when ready"
                else "准备缺失导航数据 ${id.x}/${id.y} · 非寻路计时 · 完成后复用 / Preparing missing navigation data; reusable when ready")
                val preparation = System.nanoTime()
                try {
                    withTimeout(PREPARATION_BUDGET_MILLIS) {
                        prepareProduct(snapshot, routing, context, id, if(required.filtered) policy else basePolicy, !required.filtered)
                    }
                    query.productPrepared(required)
                } finally { preparingNanos += System.nanoTime() - preparation }
            }
        } finally {
            android.util.Log.i("YokuliPassage", "lazy-route request=${request.requestId} " +
                "totalMs=${(System.nanoTime()-began)/1_000_000} searchMs=${searchingNanos/1_000_000} " +
                "prepareMs=${preparingNanos/1_000_000} prepared=${prepared.size} ${query.statistics()}")
        }
    }

    private inner class Query(private val context: Context, private val request: PassageRequest,
        private val start: ChartPoint, private val end: ChartPoint, private val policy: String,
        private val onProgress: (String) -> Unit) {
        private lateinit var work: CoroutineContext
        private fun checkWork() = work.ensureActive()
        private val products = LinkedHashMap<ProductKey, PassageRegionProduct>(8, .75f, true)
        private val absentFiltered = HashSet<PassageRegionId>()
        private val headers = LinkedHashMap<PassageRegionId, PassageRegionTopology>(32, .75f, true)
        private val projections = object : LinkedHashMap<PassageRegionId, PassageProjection>(8, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PassageRegionId, PassageProjection>?) = size > 8
        }
        private val checkedConnections = LinkedHashMap<EdgeKey, List<ChartPoint>?>(32, .75f, true)
        private val visitedRegions = HashSet<PassageRegionId>()
        private var headerReads = 0; private var fullReads = 0; private var meshSearches = 0
        private var lineChecks = 0; private var expanded = 0; private var refined = 0; private var candidates = 0
        fun statistics() = "headers=$headerReads products=$fullReads localSearches=$meshSearches " +
            "lineChecks=$lineChecks expanded=$expanded refined=$refined candidates=$candidates"
        fun productPrepared(key: ProductKey) { if(key.filtered) absentFiltered.remove(key.region) }
        private fun projection(id: PassageRegionId) = projections.getOrPut(id) {
            PassageProjection(id.center) { checkWork() }
        }
        private fun claim(id: PassageRegionId) {
            visitedRegions += id
            check(visitedRegions.size <= 192) { "NAVIGATION_REGION_BUDGET: no-route is not established" }
        }
        private suspend fun load(id: PassageRegionId, filtered: Boolean = false, required: Boolean = true): PassageRegionProduct? {
            checkWork()
            val slot = ProductKey(id, filtered)
            products[slot]?.let { return it }
            if(filtered && id in absentFiltered) {
                if(required) throw PreparationRequired(slot)
                return null
            }
            claim(id)
            val chosen = if(filtered) policy else basePolicy
            val value = context.products.read(context.products.key(context.source, chosen, id), context.source, chosen, id)
            fullReads++
            if(value == null) {
                if(filtered) absentFiltered += id
                if(required) throw PreparationRequired(slot)
                return null
            }
            val size = value.estimatedBytes
            while(products.isNotEmpty() && products.values.sumOf { it.estimatedBytes } + size > max(48L * 1024 * 1024, size)) {
                val iterator = products.entries.iterator(); iterator.next(); iterator.remove()
            }
            check(size <= 96L * 1024 * 1024) { "NAVIGATION_WORKSET_LIMIT: prepared region exceeds memory budget" }
            products[slot] = value
            return value
        }
        private suspend fun topology(id: PassageRegionId): PassageRegionTopology {
            checkWork(); claim(id)
            headers[id]?.let { return it }
            headerReads++
            val key = context.products.key(context.source, basePolicy, id)
            val value = context.topology?.read(key, context.source, basePolicy, id)
                ?: if(context.topology == null) context.products.readHeader(key, context.source, basePolicy, id)?.let {
                    PassageRegionTopology(it.schema, it.source, it.policy, it.rules, it.region, it.portals, it.componentCount)
                } else null
            if(value == null) throw PreparationRequired(ProductKey(id, false))
            while(headers.isNotEmpty() && headers.values.sumOf { it.bytes } + value.bytes > 8L * 1024 * 1024) {
                val iterator = headers.entries.iterator(); iterator.next(); iterator.remove()
            }
            headers[id] = value
            return value
        }
        private fun component(product: PassageRegionProduct, point: ChartPoint): Int? {
            val p = projection(product.header.region)
            val probe = p.factory.createPoint(p.xy(point))
            return product.components.indices.firstOrNull { checkWork(); product.components[it].covers(probe) }
        }

        /** Cheap constraints first. Exact depth/avoidance geometry is prepared only for a chosen corridor. */
        private suspend fun allowed(id: PassageRegionId, a: ChartPoint, b: ChartPoint, mayPrepare: Boolean,
            requireBase: Boolean): Boolean {
            checkWork(); lineChecks++
            val base = load(id, required = requireBase) ?: return false
            val p = projection(id); val line = p.line(listOf(a, b))
            if(!base.preparedWater.covers(line)) return false
            val filtered = load(id, filtered = true, required = false)
            if(filtered != null) return filtered.preparedWater.covers(line)
            if(base.semantics.direct(base, request, line) { checkWork() } == true) return true
            if(mayPrepare) throw PreparationRequired(ProductKey(id, true))
            return false
        }

        /** Same WGS84 partitioning for final validation and shortcut acceptance; never endpoint-only. */
        private suspend fun clearAcross(a: ChartPoint, b: ChartPoint, mayPrepare: Boolean = false,
            requireBase: Boolean = false,
            observe: ((PassageRegionProduct, PassageProjection, Geometry, Double) -> Unit)? = null): Boolean {
            checkWork()
            val length = distance(a, b)
            if(length < .01) return allowed(PassageRegionId.at(a), a, b, mayPrepare, requireBase)
            val cuts = java.util.TreeSet<Double>().apply { add(0.0); add(length) }
            val chunks = max(1, ceil(length / 2500.0).toInt())
            fun longitude(p: ChartPoint) = a.longitude + ((p.longitude - a.longitude + 540) % 360) - 180
            for(chunk in 0 until chunks) {
                checkWork()
                val from = length * chunk / chunks; val to = length * (chunk + 1) / chunks
                val first = atDistance(a, b, from); val last = atDistance(a, b, to)
                cuts += from; cuts += to
                for(axis in 0..1) {
                    val v0 = if(axis == 0) first.latitude else longitude(first)
                    val v1 = if(axis == 0) last.latitude else longitude(last)
                    if(abs(v1-v0) < 1e-12) continue
                    for(boundary in ceil(min(v0,v1)/PassageRegionId.STEP).toInt()..floor(max(v0,v1)/PassageRegionId.STEP).toInt()) {
                        val target = boundary * PassageRegionId.STEP
                        var lo = from; var hi = to
                        repeat(32) {
                            val mid = (lo+hi)/2; val point = atDistance(a,b,mid)
                            val value = if(axis == 0) point.latitude else longitude(point)
                            if((value < target) == (v0 < v1)) lo = mid else hi = mid
                        }
                        val cut = (lo+hi)/2
                        if(cut > from+1e-5 && cut < to-1e-5) cuts += cut
                    }
                }
            }
            val intervals = cuts.toList()
            for(index in 0 until intervals.lastIndex) {
                checkWork()
                val from = intervals[index]; val to = intervals[index+1]
                if(to-from < 1e-5) continue
                val id = PassageRegionId.at(atDistance(a,b,(from+to)/2))
                val first = atDistance(a,b,from); val last = atDistance(a,b,to)
                if(!allowed(id,first,last,mayPrepare,requireBase)) return false
                if(observe != null) {
                    val base = requireNotNull(load(id))
                    val p = projection(id)
                    observe(base,p,p.line(listOf(first,last)),from)
                }
            }
            return true
        }

        private suspend fun connect(key: EdgeKey): List<ChartPoint>? {
            if(checkedConnections.containsKey(key)) return checkedConnections[key]
            checkWork()
            val base = requireNotNull(load(key.region))
            var tile = load(key.region, filtered=true, required=false) ?: base
            val p = projection(key.region)
            var points: List<ChartPoint>? = null
            // Direct visibility is tested against the actual depth/avoidance policy before using the mesh.
            if(clearAcross(key.from,key.to,requireBase=true)) points = listOf(key.from,key.to)
            else {
                // A base-mesh candidate may already satisfy the policy; do not rebuild a mesh needlessly.
                meshSearches++
                val raw = tile.mesh.route(p.xy(key.from),p.xy(key.to),tile.preparedWater) { checkWork() }
                if(raw != null && raw.size >= 2) {
                    val candidate = raw.map(p::point).toMutableList().also { it[0]=key.from; it[it.lastIndex]=key.to }
                    require(candidate.size <= 2000) { "NAVIGATION_ROUTE_POINT_BUDGET" }
                    var valid = true
                    for((a,b) in candidate.zipWithNext()) if(!clearAcross(a,b,requireBase=true)) { valid=false; break }
                    if(valid) points=candidate
                }
                if(points == null && tile === base && base.semantics.needsFiltering(base,request)) {
                    // This demand leaves the search coroutine. The producer runs under its own timeout.
                    tile = requireNotNull(load(key.region,filtered=true))
                    meshSearches++
                    val refined = tile.mesh.route(p.xy(key.from),p.xy(key.to),tile.preparedWater) { checkWork() }
                    if(refined != null && refined.size >= 2) points=refined.map(p::point).toMutableList().also {
                        it[0]=key.from; it[it.lastIndex]=key.to
                    }
                }
                if(points != null) {
                    require(points.size <= 2000) { "NAVIGATION_ROUTE_POINT_BUDGET" }
                    for((a,b) in points.zipWithNext()) if(!clearAcross(a,b,mayPrepare=true,requireBase=true)) { points=null; break }
                }
            }
            while(checkedConnections.isNotEmpty() && (checkedConnections.size >= 256 ||
                    checkedConnections.values.sumOf { it?.size ?: 0 } + (points?.size ?: 0) > 32_768)) {
                val iterator=checkedConnections.entries.iterator(); iterator.next(); iterator.remove()
            }
            checkedConnections[key]=points
            return points
        }

        private suspend fun finish(path: List<ChartPoint>): Result? {
            val issues=ArrayList<PassageIssue>(); val noted=HashSet<String>(); var along=0.0
            for((a,b) in path.zipWithNext()) {
                if(!clearAcross(a,b,mayPrepare=true,requireBase=true) { tile,p,line,offset ->
                    val vessel=request.vessel
                    val margin=max(1.0,max(vessel.corridorHalfWidthMeters ?: 0.0,
                        (vessel.beamMeters ?: 0.0)/2+(vessel.clearanceMarginMeters ?: 0.0)))
                    val corridor=line.buffer(margin)
                    for(index in tile.unknownAlong(corridor)) {
                        checkWork(); val evidence=tile.header.evidence[index]
                        if(evidence.featureId in noted || !tile.unknownDepth[index].intersects(corridor)) continue
                        noted+=evidence.featureId
                        val nearest=org.locationtech.jts.operation.distance.DistanceOp.nearestPoints(line,tile.unknownDepth[index])[0]
                        val offsetOnLine=org.locationtech.jts.linearref.LengthIndexedLine(line).project(nearest)
                        issues+=PassageIssue("region:depth:${issues.size}",PassageSeverity.INSUFFICIENT,PassageIssueKind.DEPTH,0,
                            p.point(nearest),along+offset+offsetOnLine,
                            "此段深度或垂直基准证据不完整；这是参考草稿，不证明实际余深 / Depth or vertical-datum evidence is incomplete; reference draft only",
                            evidence.featureId,evidence.cellId,evidence.depth)
                    }
                    if(tile.header.malformed.isNotEmpty() && noted.add("unknown:${tile.header.region}")) issues+=PassageIssue(
                        "region:data:${issues.size}",PassageSeverity.INSUFFICIENT,PassageIssueKind.DATA,0,p.point(line.coordinate),along+offset,
                        "本区部分资料不能完整解释，请核对原海图 / Some regional data is incomplete; review the source chart")
                }) return null
                along+=distance(a,b)
            }
            return Result(path,issues)
        }

        suspend fun search(): Result? {
            work=currentCoroutineContext()
            val firstId=PassageRegionId.at(start); val lastId=PassageRegionId.at(end)
            val firstProduct=requireNotNull(load(firstId)); val lastProduct=if(lastId==firstId) firstProduct else requireNotNull(load(lastId))
            val firstComponent=component(firstProduct,start) ?: return null
            val lastComponent=component(lastProduct,end) ?: return null
            // No missing blocks are built speculatively just to try a straight line through an island.
            if(clearAcross(start,end)) return finish(listOf(start,end))
            if(firstId==lastId && firstComponent==lastComponent) {
                connect(EdgeKey(firstId,start,end))?.let { local -> finish(local)?.let { return it } }
            }
            val first=Vertex(firstId,firstComponent,start)
            val goal=Vertex(lastId,lastComponent,end,true)
            val solver=PassageLazySearch<Vertex,EdgeKey,List<ChartPoint>>(
                successors={ node ->
                    val edges=ArrayList<PassageLazySearch.Edge<Vertex,EdgeKey>>()
                    if(node.region==lastId && node.component==lastComponent) edges+=PassageLazySearch.Edge(
                        goal,EdgeKey(node.region,node.point,end),distance(node.point,end))
                    val current=topology(node.region)
                    for(portal in current.portals) {
                        checkWork(); if(portal.component!=node.component) continue
                        val nextId=node.region.neighbor(portal.edge) ?: continue
                        val opposite=portal.edge xor 1
                        val other=topology(nextId)
                        for(peer in other.portals) {
                            if(peer.edge!=opposite) continue
                            val lo=max(portal.lower,peer.lower); val hi=min(portal.upper,peer.upper)
                            if(hi-lo<1e-8) continue
                            fun axis(point: ChartPoint)=if(portal.edge<2) point.latitude
                                else node.region.west+((point.longitude-node.region.west+540)%360)-180
                            val inset=min((hi-lo)*.01,1e-7)
                            val entries=listOf(axis(end).coerceIn(lo+inset,hi-inset),
                                axis(start).coerceIn(lo+inset,hi-inset),(lo+hi)/2).distinct()
                            for(value in entries) {
                                val at=node.region.point(portal.edge,value)
                                val target=Vertex(nextId,peer.component,at)
                                edges+=PassageLazySearch.Edge(target,EdgeKey(node.region,node.point,at),distance(node.point,at))
                            }
                        }
                    }
                    edges
                },
                heuristic={ distance(it.point,end) }, isGoal={ it.goal },
                refine={ _,edge ->
                    onProgress("细化选中通道 · 水深与障碍复核 / Refining selected corridor and checking depth")
                    connect(edge.key)?.let { points -> PassageLazySearch.Refined(
                        points.zipWithNext().sumOf { distance(it.first,it.second) },points) }
                }, check={ checkWork() }, maxExpansions=(24_000-expanded).coerceAtLeast(1),
                maxEdges=100_000,maxCandidates=32)
            val route=try { solver.search(first) } finally {
                expanded+=solver.statistics.expanded; refined+=solver.statistics.refinements; candidates+=solver.statistics.candidates
            } ?: return null
            val points=mutableListOf(start)
            for(leg in route.legs) {
                points.addAll(leg.drop(1)); require(points.size<=2000) { "NAVIGATION_ROUTE_POINT_BUDGET" }
            }
            if(points.size<2) return null
            points[0]=start; points[points.lastIndex]=end
            val simplified=mutableListOf(start); var anchor=0
            while(anchor<points.lastIndex) {
                checkWork(); var next=points.lastIndex
                if(next>anchor+32 && !clearAcross(points[anchor],points[next])) next=anchor+32
                while(next>anchor+1 && !clearAcross(points[anchor],points[next])) next--
                simplified+=points[next]; anchor=next
            }
            return finish(simplified)
        }
    }
    companion object {
        private const val SEARCH_BUDGET_MILLIS=2_000L
        private const val PREPARATION_BUDGET_MILLIS=120_000L
    }
}
