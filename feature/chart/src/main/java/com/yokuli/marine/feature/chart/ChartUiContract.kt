package com.yokuli.marine.feature.chart

import com.yokuli.marine.core.model.ChartMode

enum class ChartSurfaceKind { FIXTURE, GOOGLE_MAPS }

data class ChartUiState(
    val mode: ChartMode,
    val surfaceKind: ChartSurfaceKind,
    val positionText: String,
    val courseOverGroundDegrees: Int?,
    val speedOverGroundKnots: Double?,
    val destinationName: String?,
    val distanceToWaypointNm: Double?,
    val bearingTrueDegrees: Int?,
    val anchorArmed: Boolean,
    val surveyDepthMeters: Double?,
)

sealed interface ChartUiAction {
    data class SelectMode(val mode: ChartMode) : ChartUiAction
    data object Home : ChartUiAction
}

/** 中文：未接运行时之前的显式 UI 样例。 English: Explicit UI fixture until runtime wiring exists. */
object ChartUiFixtures {
    fun state(
        mode: ChartMode,
        surfaceKind: ChartSurfaceKind = ChartSurfaceKind.FIXTURE,
    ) = ChartUiState(
        mode = mode,
        surfaceKind = surfaceKind,
        positionText = "36°50.9′S  174°45.8′E",
        courseOverGroundDegrees = 184,
        speedOverGroundKnots = 6.2,
        destinationName = "MOTUIHE",
        distanceToWaypointNm = 3.4,
        bearingTrueDegrees = 71,
        anchorArmed = false,
        surveyDepthMeters = null,
    )
}
