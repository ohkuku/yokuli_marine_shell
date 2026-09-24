package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.anchorwatch.domain.vessel.VesselDataQuality
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.data.Reading
import kotlin.math.*

/** 历史表达按量的物理意义选择；不能把跨越北向的方位或累计计数画成普通折线。 */
internal enum class InstrumentHistoryKind { DIRECTION, RANGE, DEPTH, DEVIATION, WEATHER, COUNTER }

internal fun instrumentHistoryKind(key: String): InstrumentHistoryKind = when (key) {
    "heading", "cog", "twd", "current_set", "waypoint_bearing" -> InstrumentHistoryKind.DIRECTION
    "depth", "ukc" -> InstrumentHistoryKind.DEPTH
    "heel", "pitch", "rudder", "awa", "twa", "xte", "roll_rate", "pitch_rate", "rot", "vmg", "vmc" -> InstrumentHistoryKind.DEVIATION
    "pressure", "pressure_1h", "pressure_3h", "pressure_6h", "air", "water", "temperature" -> InstrumentHistoryKind.WEATHER
    "total_log", "trip_log", "impacts" -> InstrumentHistoryKind.COUNTER
    else -> InstrumentHistoryKind.RANGE
}

/** 每个点仍指向真实样本；显示单位只存在于这个不可变视图中。 */
internal data class HistoryPoint(val reading: Reading, val value: Double, val time: Float)
internal data class HistoryBucket(
    val index: Int,
    val points: List<HistoryPoint>,
    val uninterrupted: Boolean,
    val minimum: Double,
    val maximum: Double,
    val mean: Double,
    val increment: Double?,
) {
    val first = points.first()
    val last = points.last()
    val minimumPoint = points.minBy { it.value }
    val maximumPoint = points.maxBy { it.value }
    val degraded = points.any { it.reading.quality == VesselDataQuality.DEGRADED }
}
internal data class InstrumentHistoryFrame(
    val kind: InstrumentHistoryKind,
    val points: List<HistoryPoint>,
    val buckets: List<HistoryBucket>,
    val bucketCount: Int,
    val lower: Double,
    val upper: Double,
    val latestRun: List<HistoryPoint>,
    val directionCounts: List<Int>,
    val meanDirection: Double?,
    val breaks: List<Float>,
    val breakBuckets: List<Int>,
    val minimum: HistoryPoint?,
    val maximum: HistoryPoint?,
    val sourceCount: Int,
    val degradedCount: Int,
    val rejectedIncrements: Int,
    val incrementTotal: Double?,
    val start: Long,
    val end: Long,
)

/** 方位不因 359°→1° 被误判为来源中断；所有量仍保留采样间隔、来源身份、单位断点。 */
private fun sameHistoryRun(a: Reading, b: Reading): Boolean =
    b.elapsed >= a.elapsed && b.elapsed - a.elapsed <= a.validForMillis &&
        b.sourceKey == a.sourceKey && b.continuityKey == a.continuityKey && b.unit == a.unit

internal fun buildInstrumentHistory(os: OsStore, key: String, readings: List<Reading>, now: Long, minutes: Int): InstrumentHistoryFrame {
    val kind = instrumentHistoryKind(key)
    val span = minutes * 60_000L
    val start = now - span
    val points = readings.asSequence()
        .filter { it.elapsed in maxOf(0L, start)..now && it.value.isFinite() && it.quality != VesselDataQuality.UNKNOWN }
        .sortedBy { it.elapsed }
        .map { reading ->
            // 风角用船体相对的有符号半圈；不把 350° 误当成右侧大角度。
            val canonical = if (key == "awa" || key == "twa") signedHistoryAngle(reading.value) else reading.value
            HistoryPoint(reading, os.displayMetricValue(key, canonical), ((reading.elapsed - start).toDouble() / span).toFloat())
        }.toList()
    val count = 36
    val breaks = mutableListOf<Float>()
    var runStart = 0
    var rejected = 0
    var validIncrementCount = 0
    val increments = DoubleArray(count)
    val incrementCounts = IntArray(count)
    points.zipWithNext().forEachIndexed { index, (a, b) ->
        val continuous = sameHistoryRun(a.reading, b.reading) &&
            !(kind == InstrumentHistoryKind.DEVIATION && key in setOf("awa", "twa") && abs(b.value - a.value) > 180)
        val reset = kind == InstrumentHistoryKind.COUNTER && b.value < a.value
        if (!continuous || reset) {
            runStart = index + 1
            breaks += b.time
            if (kind == InstrumentHistoryKind.COUNTER) rejected++
        } else if (kind == InstrumentHistoryKind.COUNTER) {
            val bucket = (b.time * count).toInt().coerceIn(0, count - 1)
            increments[bucket] += b.value - a.value
            incrementCounts[bucket]++
            validIncrementCount++
        }
    }
    val buckets = points.groupBy { (it.time * count).toInt().coerceIn(0, count - 1) }.map { (index, members) ->
        HistoryBucket(index, members,
            members.zipWithNext().all { (a, b) -> sameHistoryRun(a.reading, b.reading) &&
                !(kind == InstrumentHistoryKind.DEVIATION && key in setOf("awa", "twa") && abs(b.value-a.value)>180) &&
                !(kind == InstrumentHistoryKind.COUNTER && b.value<a.value) },
            members.minOf { it.value }, members.maxOf { it.value }, members.map { it.value }.average(),
            increments[index].takeIf { incrementCounts[index] > 0 })
    }
    val low = points.minOfOrNull { it.value } ?: 0.0
    val high = points.maxOfOrNull { it.value } ?: 1.0
    val canonicalStep = when (key) {
        "xte", "waypoint_distance", "total_log", "trip_log" -> .0001
        "impacts" -> 1.0
        else -> .1
    }
    val deltaUnit = abs(os.displayMetricValue(key, canonicalStep) - os.displayMetricValue(key, 0.0)).coerceAtLeast(.000001)
    val padding = ((high - low) * .12).coerceAtLeast(deltaUnit)
    val bounds = when (kind) {
        InstrumentHistoryKind.DIRECTION -> 0.0 to 360.0
        InstrumentHistoryKind.DEPTH -> minOf(0.0, low - if (low < 0) padding else 0.0) to maxOf(high + padding, deltaUnit)
        InstrumentHistoryKind.DEVIATION -> maxOf(abs(low), abs(high), deltaUnit).times(1.15).let { -it to it }
        InstrumentHistoryKind.COUNTER -> 0.0 to maxOf(increments.maxOrNull() ?: 0.0, deltaUnit).times(1.15)
        InstrumentHistoryKind.RANGE -> minOf(0.0, low - if (low < 0) padding else 0.0) to maxOf(high + padding, deltaUnit)
        InstrumentHistoryKind.WEATHER -> low - padding to high + padding
    }
    val latestRun = points.drop(runStart)
    val sectors = IntArray(24)
    if (kind == InstrumentHistoryKind.DIRECTION) points.forEach { sectors[((normalizedHistoryAngle(it.value) + 7.5) / 15).toInt() % 24]++ }
    // 向量均值跨北向正确；相反方向相互抵消时没有可信平均方向。
    val direction = if (kind == InstrumentHistoryKind.DIRECTION && latestRun.isNotEmpty()) {
        val sumSin = latestRun.sumOf { sin(Math.toRadians(it.value)) }
        val sumCos = latestRun.sumOf { cos(Math.toRadians(it.value)) }
        val concentration = hypot(sumSin, sumCos) / latestRun.size
        normalizedHistoryAngle(Math.toDegrees(atan2(sumSin, sumCos))).takeIf { concentration >= .15 }
    } else null
    return InstrumentHistoryFrame(kind, points, buckets, count, bounds.first, bounds.second, latestRun,
        sectors.toList(), direction, breaks, breaks.map { (it * count).toInt().coerceIn(0, count-1) }.distinct(),
        points.minByOrNull { it.value }, points.maxByOrNull { it.value },
        points.map { it.reading.sourceKey }.distinct().size, points.count { it.reading.quality==VesselDataQuality.DEGRADED },
        rejected, increments.sum().takeIf { validIncrementCount > 0 }, start, now)
}

internal fun normalizedHistoryAngle(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
internal fun signedHistoryAngle(value: Double): Double = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
