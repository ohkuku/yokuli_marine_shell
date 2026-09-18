package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.anchorwatch.domain.vessel.VesselDataQuality
import com.yokuli.marine.shell.rebuild.data.Reading

internal enum class InstrumentTrendGroup { NAVIGATION, WEATHER }

/** 趋势是航行与天气的观察目录，不直接展开全部仪表、IMU 原始量或派生诊断。 */
internal data class InstrumentTrendMetric(val key: String, val group: InstrumentTrendGroup)

internal object InstrumentTrendCatalog {
    val metrics: List<InstrumentTrendMetric> = listOf(
        InstrumentTrendMetric("sog", InstrumentTrendGroup.NAVIGATION),
        InstrumentTrendMetric("bsp", InstrumentTrendGroup.NAVIGATION),
        InstrumentTrendMetric("cog", InstrumentTrendGroup.NAVIGATION),
        InstrumentTrendMetric("heading", InstrumentTrendGroup.NAVIGATION),
        InstrumentTrendMetric("depth", InstrumentTrendGroup.NAVIGATION),
        InstrumentTrendMetric("tws", InstrumentTrendGroup.WEATHER),
        InstrumentTrendMetric("twa", InstrumentTrendGroup.WEATHER),
        InstrumentTrendMetric("twd", InstrumentTrendGroup.WEATHER),
        InstrumentTrendMetric("aws", InstrumentTrendGroup.WEATHER),
        InstrumentTrendMetric("awa", InstrumentTrendGroup.WEATHER),
        InstrumentTrendMetric("pressure", InstrumentTrendGroup.WEATHER),
        InstrumentTrendMetric("air", InstrumentTrendGroup.WEATHER),
        InstrumentTrendMetric("water", InstrumentTrendGroup.WEATHER),
    )

    private fun Reading.isActualObservation(now: Long): Boolean =
        value.isFinite() && elapsed in 0..now && quality != VesselDataQuality.UNKNOWN

    /** 当前来源消失时保留已采到的最后历史，绝不把历史样本改成新的实时观测。 */
    fun lastReading(key: String, readings: Map<String, Reading>, history: Map<String, List<Reading>>, now: Long): Reading? =
        readings[key]?.takeIf { it.isActualObservation(now) }
            ?: history[key].orEmpty().lastOrNull { it.isActualObservation(now) }

    fun available(readings: Map<String, Reading>, history: Map<String, List<Reading>>, now: Long): List<InstrumentTrendMetric> =
        metrics.filter { lastReading(it.key, readings, history, now) != null }

    fun selected(key: String, available: List<InstrumentTrendMetric>, readings: Map<String, Reading>, now: Long): InstrumentTrendMetric? =
        available.firstOrNull { it.key == key }
            ?: available.firstOrNull { readings[it.key]?.fresh(now) == true }
            ?: available.firstOrNull()
}
