package com.yokuli.anchorwatch.data.vessel

import com.yokuli.anchorwatch.domain.vessel.VesselMetricId
import com.yokuli.anchorwatch.domain.vessel.VesselSourcePreference

/** 新逐字段选择优先于旧的船首向组设置；只有用户明确选择自动时才清理旧设置。 */
object VesselMetricSelectionPolicy {
    fun choose(current: VesselDataSettings, metric: VesselMetricId, sourceKey: String?): VesselDataSettings {
        val pins = current.metricSourcePins.toMutableMap()
        if (sourceKey == null) pins.remove(metric.name) else pins[metric.name] = sourceKey
        val resetLegacyHeading = sourceKey == null && metric in setOf(VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC)
        return current.copy(
            metricSourcePins = pins,
            headingPreference = if (resetLegacyHeading) VesselSourcePreference.AUTO else current.headingPreference,
            boatHeadingSourceId = if (resetLegacyHeading) null else current.boatHeadingSourceId,
        )
    }
}
