package com.yokuli.runtime.marine.planning

import java.util.PriorityQueue

/**
 * Hierarchical, lazy edge evaluation. The coarse graph must be cheap and immutable for one search.
 * Only edges on a candidate path are refined; rejected edges are excluded from subsequent searches.
 * Inspired by HPA* and LazySP (Dellin/Srinivasa, ICAPS 2016), not an optimal LazySP implementation:
 * a fully verified draft is returned immediately instead of proving global shortest-path optimality.
 * No Android, chart geometry, IO or vessel assumptions live in this search kernel.
 */
internal class PassageLazySearch<N : Any, K : Any, R : Any>(
    private val successors: suspend (N) -> List<Edge<N, K>>,
    private val heuristic: (N) -> Double,
    private val isGoal: (N) -> Boolean,
    private val refine: suspend (N, Edge<N, K>) -> Refined<R>?,
    private val check: () -> Unit,
    private val maxExpansions: Int = 24_000,
    private val maxEdges: Int = 200_000,
    private val maxCandidates: Int = 64,
) {
    data class Edge<N, K>(val to: N, val key: K, val lowerBound: Double)
    data class Refined<R>(val cost: Double, val value: R)
    data class Statistics(var expanded: Int = 0, var generated: Int = 0,
        var candidates: Int = 0, var refinements: Int = 0, var reused: Int = 0)
    data class Result<N, R>(val nodes: List<N>, val legs: List<R>, val cost: Double)
    private data class QueueNode<N>(val node: N, val cost: Double, val score: Double, val order: Long)
    private data class Parent<N, K>(val from: N, val edge: Edge<N, K>)

    val statistics = Statistics()
    private val adjacency = HashMap<N, List<Edge<N, K>>>()
    private val evaluated = HashMap<K, Refined<R>?>()

    suspend fun search(start: N): Result<N, R>? {
        require(maxExpansions > 0 && maxEdges > 0 && maxCandidates > 0)
        repeat(maxCandidates) {
            check()
            val candidate = candidate(start) ?: return null
            statistics.candidates++
            val values = ArrayList<R>(candidate.size)
            val nodes = ArrayList<N>(candidate.size + 1).apply { add(start) }
            var total = 0.0
            var valid = true
            for(link in candidate) {
                check()
                val known = if(evaluated.containsKey(link.edge.key)) {
                    statistics.reused++
                    evaluated[link.edge.key]
                } else {
                    statistics.refinements++
                    refine(link.from, link.edge).also { result ->
                        if(result != null) require(result.cost.isFinite() && result.cost >= 0.0) {
                            "NAVIGATION_EDGE_COST_INVALID"
                        }
                        evaluated[link.edge.key] = result
                    }
                }
                if(known == null) { valid = false; break }
                values += known.value
                nodes += link.edge.to
                total += known.cost
            }
            if(valid) return Result(nodes, values, total)
        }
        // Budget exhaustion is NOT evidence of disconnected water.
        error("NAVIGATION_REFINEMENT_BUDGET: candidate refinement limit reached; no-route is not established")
    }

    private suspend fun candidate(start: N): List<Parent<N, K>>? {
        fun estimate(node: N): Double = heuristic(node).also {
            require(it.isFinite() && it >= 0.0) { "NAVIGATION_HEURISTIC_INVALID" }
        }
        val queue = PriorityQueue<QueueNode<N>>(compareBy<QueueNode<N>> { it.score }
            .thenByDescending { it.cost }.thenBy { it.order })
        val costs = hashMapOf(start to 0.0)
        val parents = HashMap<N, Parent<N, K>>()
        var sequence = 0L
        queue += QueueNode(start, 0.0, estimate(start), sequence++)
        while(queue.isNotEmpty()) {
            check()
            val current = queue.remove()
            if(current.cost > costs.getValue(current.node)) continue
            if(isGoal(current.node)) {
                val reversed = ArrayList<Parent<N, K>>()
                var cursor = current.node
                while(cursor != start) {
                    check()
                    check(reversed.size < maxExpansions) { "NAVIGATION_PARENT_CYCLE" }
                    val link = parents.getValue(cursor)
                    reversed += link
                    cursor = link.from
                }
                reversed.reverse()
                return reversed
            }
            check(++statistics.expanded <= maxExpansions) {
                "NAVIGATION_GRAPH_BUDGET: search limit reached; no-route is not established"
            }
            val edges = adjacency[current.node] ?: successors(current.node).also { list ->
                statistics.generated += list.size
                check(statistics.generated <= maxEdges) { "NAVIGATION_EDGE_BUDGET" }
                list.forEach { require(it.lowerBound.isFinite() && it.lowerBound >= 0.0) {
                    "NAVIGATION_EDGE_COST_INVALID"
                } }
                adjacency[current.node] = list
            }
            for(edge in edges) {
                check()
                if(evaluated.containsKey(edge.key) && evaluated[edge.key] == null) continue
                val cost = current.cost + (evaluated[edge.key]?.cost ?: edge.lowerBound)
                if(cost >= costs.getOrDefault(edge.to, Double.POSITIVE_INFINITY)) continue
                costs[edge.to] = cost
                parents[edge.to] = Parent(current.node, edge)
                queue += QueueNode(edge.to, cost, cost + estimate(edge.to), sequence++)
            }
        }
        return null
    }
}
