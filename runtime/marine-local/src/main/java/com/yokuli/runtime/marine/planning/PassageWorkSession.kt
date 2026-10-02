package com.yokuli.runtime.marine.planning

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 一次冻结资料作业的局部产品工作集。复核已通过的前段、LINZ 基准说明与局部修补共用 world，
 * 不再次从 JSON 构建同一条走廊。随作业释放，绝不跨快照、船型或协程租约复用投影回调。
 */
internal class PassageWorkSession : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PassageWorkSession>
    private data class Entry(val world: PassageWorld, val bytes: Long)
    private val worlds = LinkedHashMap<String, Entry>(16, .75f, true)
    private var retainedBytes = 0L
    private val budgetBytes = 48L * 1024 * 1024

    fun get(key: String): PassageWorld? = worlds[key]?.world

    fun retain(key: String, world: PassageWorld) {
        // JTS 坐标、环/集合对象及原始 NauticalFeature 都计入保守预算，不能只数 world 个数。
        val geometries = listOf(world.coverage, world.navigable, world.rasterKnownDepth,
            world.rasterBoundaryUncertainty) + world.features.map { it.geometry } + world.rasterAreas.map { it.geometry }
        val coordinates = geometries.sumOf { it.numPoints.toLong() }
        val sourcePoints = world.features.sumOf { item -> item.feature.geometry.parts.sumOf { it.points.size.toLong() } }
        val bytes = 4096L + coordinates * 64L + sourcePoints * 64L + world.features.size * 1024L + world.projection.estimatedCacheBytes
        if (bytes > budgetBytes) return
        worlds.remove(key)?.let { retainedBytes -= it.bytes }
        while (retainedBytes + bytes > budgetBytes && worlds.isNotEmpty()) {
            val oldest = worlds.entries.iterator()
            retainedBytes -= oldest.next().value.bytes
            oldest.remove()
        }
        worlds[key] = Entry(world, bytes)
        retainedBytes += bytes
    }
}
