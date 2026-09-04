package com.yokuli.marine.feature.chart

enum class ChartSurfaceKind { DEMO, GOOGLE_MAPS }

data class ChartUiState(
    val surfaceKind: ChartSurfaceKind,
    val mapConfigured: Boolean,
)

sealed interface ChartUiAction {
    data object OpenMapSettings : ChartUiAction
}
