package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartBuiltInBaseStyle
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayIssue
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPlan
import com.yokuli.marine.map.domain.chartlibrary.ChartLayerHealth
import com.yokuli.marine.map.domain.chartlibrary.ChartLayerId
import com.yokuli.marine.map.domain.chartlibrary.ChartViewId

data class ChartDisplayViewUi(
    val id: ChartViewId,
    val name: String,
    val baseStyle: ChartBuiltInBaseStyle,
    val active: Boolean,
)

/** A display-only control. Source membership, scanning and files remain Chart Library concerns. */
data class ChartQuickLayerUi(
    val id: ChartLayerId,
    val title: String,
    val role: ChartAssetRole,
    val visible: Boolean,
    val available: Boolean,
    val opacity: Float,
    val health: ChartLayerHealth,
)

enum class ChartDisplayNoticeUi {
    CATALOG_LIMIT_REACHED,
    CATALOG_READ_FAILED,
    ITEM_NO_LONGER_AVAILABLE,
    ACTION_QUEUE_FULL,
    VIEW_UPDATE_FAILED,
}

data class ChartDisplayUiState(
    val catalogRevision: Long = 0L,
    val activeViewId: ChartViewId? = null,
    val activeViewName: String? = null,
    val views: List<ChartDisplayViewUi> = emptyList(),
    val quickLayers: List<ChartQuickLayerUi> = emptyList(),
    val plan: ChartDisplayPlan = ChartDisplayPlan.EMPTY,
    val issues: Set<ChartDisplayIssue> = emptySet(),
    val notice: ChartDisplayNoticeUi? = null,
    val busy: Boolean = true,
)

sealed interface ChartDisplayUiAction {
    data class ActivateBuiltIn(val style: ChartBuiltInBaseStyle) : ChartDisplayUiAction
    data class ActivateView(val viewId: ChartViewId) : ChartDisplayUiAction
    data class SetLayerVisible(val layerId: ChartLayerId, val visible: Boolean) : ChartDisplayUiAction
    data class SetOpacity(val layerId: ChartLayerId, val opacity: Float) : ChartDisplayUiAction
    data object Refresh : ChartDisplayUiAction
    data object DismissNotice : ChartDisplayUiAction
}

object ChartDisplayTestTags {
    const val ROOT = "chart-display-root"
    const val VIEW_PICKER = "chart-display-view-picker"
    const val REFRESH = "chart-display-refresh"
    fun source(id: String) = "chart-display-source-$id"
    fun asset(id: String) = "chart-display-asset-$id"
    fun opacityDown(id: String) = "chart-display-opacity-down-$id"
    fun opacityUp(id: String) = "chart-display-opacity-up-$id"
    fun quickLayer(id: String) = "chart-quick-layer-$id"
    fun quickLayerVisibility(id: String) = "chart-quick-layer-visible-$id"
}
