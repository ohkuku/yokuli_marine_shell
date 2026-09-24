package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.anchorwatch.domain.vessel.VesselMetricId
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.scene.VesselScenePresenter

/**
 * 只决定船首向热点打开哪一种详情，不改变全船的来源选择。
 * 真北和磁北始终分别展示；保留的旧读数在详情中带原有时间与质量，不用 COG 冒充船首向。
 */
internal fun preferredHeadingMetric(os: OsStore): VesselMetricId {
    val state = os.marine?.services?.state?.value ?: return VesselMetricId.HEADING_TRUE
    return VesselScenePresenter.preferredHeadingMetric(state.vesselData, state.vesselSettings)
}
