package com.yokuli.marine.feature.cockpit

import com.yokuli.marine.core.model.CockpitPage
import com.yokuli.marine.core.model.DataFreshness

data class CockpitUiState(
    val page: CockpitPage,
    val speedOverGroundKnots: Double?,
    val headingTrueDegrees: Int?,
    val trueWindSpeedKnots: Double?,
    val depthMeters: Double?,
    val depthFreshness: DataFreshness,
)

sealed interface CockpitUiAction {
    data object Home : CockpitUiAction
}

/** 中文：未接入仪表数据源的 UI 样例。 English: UI fixture before instrument sources are connected. */
object CockpitUiFixtures {
    fun state(page: CockpitPage) = CockpitUiState(page, 6.2, 184, 12.4, null, DataFreshness.STALE)
}
