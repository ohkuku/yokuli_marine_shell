package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayIssue
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPlan
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplaySelection
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId

data class ChartDisplaySourceUi(
    val id: ChartSourceId,
    val name: String,
    val selected: Boolean,
    val enabled: Boolean,
    val availableAssetCount: Int,
)

data class ChartDisplayAssetUi(
    val id: ChartAssetId,
    val title: String,
    val role: ChartAssetRole,
    val selected: Boolean,
    val visible: Boolean,
    val available: Boolean,
    val opacity: Float,
)

enum class ChartDisplayNoticeUi {
    CATALOG_LIMIT_REACHED,
    CATALOG_READ_FAILED,
    ITEM_NO_LONGER_AVAILABLE,
    ACTION_QUEUE_FULL,
    SELECTION_LIMIT_REACHED,
}

data class ChartDisplayUiState(
    val catalogRevision: Long = 0L,
    val selection: ChartDisplaySelection = ChartDisplaySelection.None,
    val overlaysVisible: Boolean = true,
    val sources: List<ChartDisplaySourceUi> = emptyList(),
    val assets: List<ChartDisplayAssetUi> = emptyList(),
    val plan: ChartDisplayPlan = ChartDisplayPlan.EMPTY,
    val issues: Set<ChartDisplayIssue> = emptySet(),
    val notice: ChartDisplayNoticeUi? = null,
    val busy: Boolean = true,
)

sealed interface ChartDisplayUiAction {
    data object UseNoLocalChart : ChartDisplayUiAction
    data class PinAsset(val assetId: ChartAssetId) : ChartDisplayUiAction
    data class ToggleSource(val sourceId: ChartSourceId) : ChartDisplayUiAction
    data object ToggleOverlays : ChartDisplayUiAction
    data class SetOpacity(val assetId: ChartAssetId, val opacity: Float) : ChartDisplayUiAction
    data object Refresh : ChartDisplayUiAction
    data object DismissNotice : ChartDisplayUiAction
}

object ChartDisplayTestTags {
    const val ROOT = "chart-display-root"
    const val NO_LOCAL = "chart-display-no-local"
    const val TOGGLE_OVERLAYS = "chart-display-toggle-overlays"
    const val REFRESH = "chart-display-refresh"
    fun source(id: String) = "chart-display-source-$id"
    fun asset(id: String) = "chart-display-asset-$id"
    fun opacityDown(id: String) = "chart-display-opacity-down-$id"
    fun opacityUp(id: String) = "chart-display-opacity-up-$id"
}
