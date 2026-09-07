package com.yokuli.marine.feature.chartlibrary

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetAccessState
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFormat
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetValidationState
import com.yokuli.marine.map.domain.chartlibrary.ChartFactProvenance
import com.yokuli.marine.map.domain.chartlibrary.ChartCompatibilityWarning
import com.yokuli.marine.map.domain.chartlibrary.ChartReadAccessMode
import com.yokuli.marine.map.domain.chartlibrary.ChartGrantState
import com.yokuli.marine.map.domain.chartlibrary.ChartBuiltInBaseStyle
import com.yokuli.marine.map.domain.chartlibrary.ChartLayerHealth
import com.yokuli.marine.map.domain.chartlibrary.ChartLayerId
import com.yokuli.marine.map.domain.chartlibrary.ChartMapView
import com.yokuli.marine.map.domain.chartlibrary.ChartViewId
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryOperationId
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryPickerEffect
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySourceKind
import com.yokuli.marine.map.domain.chartlibrary.ChartManagedCopyProgress
import com.yokuli.marine.map.domain.chartlibrary.ChartScanStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceScanState
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationJob
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationJobStatus

enum class ChartLibraryFilter { ALL, NEEDS_ATTENTION, ENABLED, DISABLED }

/**
 * The three user-facing jobs of Chart Library. This is feature-local session state:
 * changing workspace never changes the catalog or the map display by itself.
 */
enum class ChartLibraryWorkspaceMode { COVERAGE, LAYERS, VIEWS, SOURCES }

sealed interface ChartLibraryLocalPage {
    data object Overview : ChartLibraryLocalPage
    data class SourceDetail(val sourceId: ChartSourceId) : ChartLibraryLocalPage
    data class AssetDetail(val assetId: ChartAssetId) : ChartLibraryLocalPage
    data object Storage : ChartLibraryLocalPage
    data class RemoveSourceConfirmation(val sourceId: ChartSourceId) : ChartLibraryLocalPage
    data class DeleteManagedCopyConfirmation(val assetId: ChartAssetId) : ChartLibraryLocalPage
    data class SaveManagedCopyConfirmation(val assetId: ChartAssetId) : ChartLibraryLocalPage
}

data class ChartLibraryLocalState(
    val page: ChartLibraryLocalPage = ChartLibraryLocalPage.Overview,
    val workspaceMode: ChartLibraryWorkspaceMode = ChartLibraryWorkspaceMode.COVERAGE,
    val query: String = "",
    val filter: ChartLibraryFilter = ChartLibraryFilter.ALL,
    val selectedAssetIds: Set<ChartAssetId> = emptySet(),
    val selectedLayerId: ChartLayerId? = null,
)

data class ChartLibraryLayerUi(
    val id: ChartLayerId,
    val name: String,
    val sourceCount: Int,
    val assetCount: Int,
    val readyAssetCount: Int,
    val sizeBytes: Long,
    val bounds: GeoBounds?,
    val minZoom: Int?,
    val maxZoom: Int?,
    val health: ChartLayerHealth,
    val warningCount: Int,
    val visible: Boolean,
    val opacity: Float,
    val stackOrder: Int,
    val selected: Boolean,
) {
    init {
        require(sourceCount > 0 && assetCount >= 0 && readyAssetCount in 0..assetCount)
        require(sizeBytes >= 0L && warningCount >= 0)
        require(opacity.isFinite() && opacity in 0f..1f)
    }
}

data class ChartLibraryViewUi(
    val id: ChartViewId,
    val name: String,
    val baseStyle: ChartBuiltInBaseStyle,
    val layerNames: List<String>,
    val visibleLayerCount: Int,
    val active: Boolean,
)

data class ChartLibrarySummaryUi(
    val sourceCount: Int = 0,
    val assetCount: Int = 0,
    val availableAssetCount: Int = 0,
    val attentionCount: Int = 0,
    val scanningCount: Int = 0,
) {
    init {
        require(sourceCount >= 0 && assetCount >= 0 && availableAssetCount in 0..assetCount)
        require(attentionCount >= 0 && scanningCount in 0..sourceCount)
    }
}

sealed interface ChartLibrarySearchItem {
    val title: String
    data class Source(val id: ChartSourceId, override val title: String) : ChartLibrarySearchItem
    data class Asset(val id: ChartAssetId, override val title: String, val sourceSummary: String) : ChartLibrarySearchItem
}

data class ChartLibrarySourceRowUi(
    val id: ChartSourceId,
    val name: String,
    val kind: ChartLibrarySourceKind,
    /** Provider authority only. The opaque locator and document path never enter UI state. */
    val provider: String?,
    val enabled: Boolean,
    val recursive: Boolean,
    val defaultRole: ChartAssetRole,
    val grantState: ChartGrantState,
    val scan: ChartSourceScanState,
    val assetCount: Int,
    val availableAssetCount: Int,
    val attentionCount: Int,
    val referencedBytes: Long?,
    val unknownSizeCount: Int,
) {
    val needsAttention: Boolean
        get() = attentionCount > 0 || grantState !in setOf(ChartGrantState.GRANTED, ChartGrantState.NOT_REQUIRED) ||
            scan.status in setOf(ChartScanStatus.PARTIAL, ChartScanStatus.FAILED, ChartScanStatus.CANCELLED)

    val canRepairPermission: Boolean
        get() = kind != ChartLibrarySourceKind.MANAGED && grantState != ChartGrantState.GRANTED
}

data class ChartLibraryAssetRowUi(
    val id: ChartAssetId,
    val title: String,
    val displayPath: String,
    val sourceNames: List<String>,
    val enabled: Boolean,
    val role: ChartAssetRole,
    val priority: Int,
    val access: ChartAssetAccessState,
    val validation: ChartAssetValidationState,
    val sizeBytes: Long?,
    val format: ChartAssetFormat,
    val rasterMimeType: String?,
    val tileSize: Int?,
    val tileScheme: MapTileScheme?,
    val minZoom: Int?,
    val maxZoom: Int?,
    val tileCount: Long?,
    val bounds: GeoBounds?,
    val attribution: String?,
    val attributionProvenance: ChartFactProvenance,
    val revisionSummary: String?,
    val selected: Boolean,
    val managedCopyAvailable: Boolean,
    val isManagedAsset: Boolean = false,
    val originalAssetId: ChartAssetId? = null,
    val managedCopyAssetId: ChartAssetId? = null,
    val validationJob: ChartValidationJob?,
    val copyJob: ChartManagedCopyProgress?,
    val accessMode: ChartReadAccessMode? = null,
    val compatibilityWarnings: Set<ChartCompatibilityWarning> = emptySet(),
) {
    val available: Boolean
        get() = enabled && access == ChartAssetAccessState.READABLE && validation in setOf(
            ChartAssetValidationState.BASIC_READABLE,
            ChartAssetValidationState.FULL_VERIFIED,
        )

    val needsAttention: Boolean
        get() = access in setOf(
            ChartAssetAccessState.PERMISSION_LOST,
            ChartAssetAccessState.SOURCE_OFFLINE,
            ChartAssetAccessState.MISSING,
            ChartAssetAccessState.CHANGED,
            ChartAssetAccessState.DIRECT_READ_UNSUPPORTED,
        ) || validation in setOf(
            ChartAssetValidationState.INVALID,
            ChartAssetValidationState.UNSUPPORTED_FORMAT,
            ChartAssetValidationState.CANCELLED_OR_INTERRUPTED,
        ) || validationJob?.status in setOf(ChartValidationJobStatus.FAILED, ChartValidationJobStatus.INTERRUPTED)
}

data class ChartLibraryStorageUi(
    val referencedOriginalBytes: Long?,
    val referencedUnknownSizeCount: Int,
    val managedCopyBytes: Long?,
    val catalogBytes: Long?,
    val cacheBytes: Long?,
    val copyAvailable: Boolean,
    val copyJobs: List<ChartManagedCopyProgress>,
    val availableCopyBytes: Long? = null,
)

sealed interface ChartLibraryPageUi {
    data class Overview(
        val sources: List<ChartLibrarySourceRowUi>,
        val assets: List<ChartLibraryAssetRowUi>,
    ) : ChartLibraryPageUi

    data class SourceDetail(
        val source: ChartLibrarySourceRowUi,
        val assets: List<ChartLibraryAssetRowUi>,
    ) : ChartLibraryPageUi

    data class AssetDetail(val asset: ChartLibraryAssetRowUi) : ChartLibraryPageUi
    data class Storage(val storage: ChartLibraryStorageUi) : ChartLibraryPageUi
    data class RemoveSourceConfirmation(val source: ChartLibrarySourceRowUi) : ChartLibraryPageUi
    data class DeleteManagedCopyConfirmation(val asset: ChartLibraryAssetRowUi) : ChartLibraryPageUi
    data class SaveManagedCopyConfirmation(
        val asset: ChartLibraryAssetRowUi,
        val availableCopyBytes: Long?,
    ) : ChartLibraryPageUi
}

enum class ChartLibraryNoticeUi {
    SOURCE_ADDED,
    SOURCE_REPAIRED,
    SOURCE_REMOVED,
    SOURCE_UPDATED,
    ASSET_UPDATED,
    LAYER_UPDATED,
    VIEW_UPDATED,
    SCAN_FINISHED,
    SCAN_CANCELLED,
    VALIDATION_FINISHED,
    VALIDATION_CANCELLED,
    COPY_STARTED,
    COPY_CANCELLED,
    MANAGED_COPY_DELETED,
    MANAGED_COPY_IN_USE,
    MANAGED_COPY_NO_SPACE,
    PICKER_CANCELLED,
    PERMISSION_REQUIRED,
    ITEM_NOT_FOUND,
    CATALOG_CHANGED,
    OPERATION_FAILED,
    ACTION_QUEUE_FULL,
    SELECTION_LIMIT_REACHED,
}

data class ChartLibraryUiState(
    val summary: ChartLibrarySummaryUi = ChartLibrarySummaryUi(),
    val workspaceMode: ChartLibraryWorkspaceMode = ChartLibraryWorkspaceMode.COVERAGE,
    val query: String = "",
    val filter: ChartLibraryFilter = ChartLibraryFilter.ALL,
    val selectedAssetIds: Set<ChartAssetId> = emptySet(),
    val page: ChartLibraryPageUi = ChartLibraryPageUi.Overview(emptyList(), emptyList()),
    val notice: ChartLibraryNoticeUi? = null,
    val busy: Boolean = false,
    /** Bounded feature-owned index; Shell never reads locators, SQLite rows or document paths. */
    val searchItems: List<ChartLibrarySearchItem> = emptyList(),
    /** Logical map content is the primary product surface; assets remain Sources detail only. */
    val layers: List<ChartLibraryLayerUi> = emptyList(),
    val views: List<ChartLibraryViewUi> = emptyList(),
)

sealed interface ChartLibraryUiAction {
    data object AddFolder : ChartLibraryUiAction
    data object AddSingleFile : ChartLibraryUiAction
    data class ChangeQuery(val value: String) : ChartLibraryUiAction
    data class ChangeFilter(val value: ChartLibraryFilter) : ChartLibraryUiAction
    data class SelectWorkspace(val value: ChartLibraryWorkspaceMode) : ChartLibraryUiAction
    data class OpenSource(val sourceId: ChartSourceId) : ChartLibraryUiAction
    data class OpenAsset(val assetId: ChartAssetId) : ChartLibraryUiAction
    data object OpenStorage : ChartLibraryUiAction
    data object NavigateBack : ChartLibraryUiAction
    data class ToggleSelection(val assetId: ChartAssetId) : ChartLibraryUiAction
    data object ClearSelection : ChartLibraryUiAction
    data class SetSelectedEnabled(val enabled: Boolean) : ChartLibraryUiAction
    data class SetSourceEnabled(val sourceId: ChartSourceId, val enabled: Boolean) : ChartLibraryUiAction
    data class SetAssetEnabled(val assetId: ChartAssetId, val enabled: Boolean) : ChartLibraryUiAction
    data class SetAssetRole(val assetId: ChartAssetId, val role: ChartAssetRole) : ChartLibraryUiAction
    data class MoveAssetPriority(val assetId: ChartAssetId, val delta: Int) : ChartLibraryUiAction
    data class RefreshSource(val sourceId: ChartSourceId) : ChartLibraryUiAction
    data class CancelSourceScan(val sourceId: ChartSourceId) : ChartLibraryUiAction
    data class RepairPermission(val sourceId: ChartSourceId) : ChartLibraryUiAction
    data class RequestRemoveSource(val sourceId: ChartSourceId) : ChartLibraryUiAction
    data object ConfirmRemoveSource : ChartLibraryUiAction
    data class InspectBasic(val assetId: ChartAssetId) : ChartLibraryUiAction
    data class VerifyFull(val assetId: ChartAssetId) : ChartLibraryUiAction
    data class CancelValidation(val assetId: ChartAssetId) : ChartLibraryUiAction
    data class SaveManagedCopy(val assetId: ChartAssetId) : ChartLibraryUiAction
    data object ConfirmManagedCopy : ChartLibraryUiAction
    data class CancelManagedCopy(val assetId: ChartAssetId) : ChartLibraryUiAction
    data class RequestDeleteManagedCopy(val assetId: ChartAssetId) : ChartLibraryUiAction
    data object ConfirmDeleteManagedCopy : ChartLibraryUiAction
    data class ViewInChart(val assetId: ChartAssetId) : ChartLibraryUiAction
    data class SelectLayer(val layerId: ChartLayerId?) : ChartLibraryUiAction
    data class RenameLayer(val layerId: ChartLayerId, val name: String) : ChartLibraryUiAction
    data class SetLayerVisible(val layerId: ChartLayerId, val visible: Boolean) : ChartLibraryUiAction
    data class SetLayerOpacity(val layerId: ChartLayerId, val opacity: Float) : ChartLibraryUiAction
    data class MoveLayer(val layerId: ChartLayerId, val delta: Int) : ChartLibraryUiAction
    data class CreateView(
        val name: String,
        val baseStyle: ChartBuiltInBaseStyle = ChartBuiltInBaseStyle.SATELLITE,
    ) : ChartLibraryUiAction
    data class RenameView(val viewId: ChartViewId, val name: String) : ChartLibraryUiAction
    data class DuplicateView(val viewId: ChartViewId, val name: String) : ChartLibraryUiAction
    data class DeleteView(val viewId: ChartViewId) : ChartLibraryUiAction
    data class ActivateView(val viewId: ChartViewId) : ChartLibraryUiAction
    data class SetViewBaseStyle(val viewId: ChartViewId, val baseStyle: ChartBuiltInBaseStyle) : ChartLibraryUiAction
    data object DismissNotice : ChartLibraryUiAction
}

/**
 * A narrow display bridge supplied by the composition root. Chart Library can explain and
 * arrange the Marine view without owning Chart's camera, renderer, or feature UI state.
 */
data class ChartLibraryDisplaySourceUi(
    val id: ChartSourceId,
    val name: String,
    val selected: Boolean,
    val enabled: Boolean,
    val availableAssetCount: Int,
)

data class ChartLibraryDisplayLayerUi(
    val id: ChartAssetId,
    val title: String,
    val role: ChartAssetRole,
    val selected: Boolean,
    val inCurrentStack: Boolean,
    val visible: Boolean,
    val available: Boolean,
    val opacity: Float,
) {
    init { require(opacity.isFinite() && opacity in 0f..1f) }
}

data class ChartLibraryDisplayUi(
    val sources: List<ChartLibraryDisplaySourceUi> = emptyList(),
    val layers: List<ChartLibraryDisplayLayerUi> = emptyList(),
    val overlaysVisible: Boolean = true,
)

sealed interface ChartLibraryDisplayAction {
    data class ToggleSource(val sourceId: ChartSourceId) : ChartLibraryDisplayAction
    data class SelectLayer(val assetId: ChartAssetId) : ChartLibraryDisplayAction
    data class SetLayerVisible(val assetId: ChartAssetId, val visible: Boolean) : ChartLibraryDisplayAction
    data class SetLayerOpacity(val assetId: ChartAssetId, val opacity: Float) : ChartLibraryDisplayAction
    data object ToggleOverlays : ChartLibraryDisplayAction
}

sealed interface ChartLibraryEffect {
    data class OpenPicker(
        val picker: ChartLibraryPickerEffect,
        val repairSourceId: ChartSourceId? = null,
    ) : ChartLibraryEffect

    data class OpenAssetInChart(val assetId: ChartAssetId, val bounds: GeoBounds?) : ChartLibraryEffect
}

object ChartLibraryBackPolicy {
    fun actionFor(page: ChartLibraryPageUi): ChartLibraryUiAction? = when (page) {
        is ChartLibraryPageUi.Overview -> null
        else -> ChartLibraryUiAction.NavigateBack
    }
}

object ChartLibraryTestTags {
    const val ROOT = "chart-library-root"
    const val EMPTY = "chart-library-empty"
    const val SEARCH = "chart-library-search"
    const val ADD_FOLDER = "chart-library-add-folder"
    const val ADD_FILE = "chart-library-add-file"
    const val STORAGE = "chart-library-storage"
    const val COVERAGE = "chart-library-coverage"
    const val LAYERS = "chart-library-layers"
    const val VIEWS = "chart-library-views"
    const val SOURCES = "chart-library-sources"
    const val BULK_ENABLE = "chart-library-bulk-enable"
    const val BULK_DISABLE = "chart-library-bulk-disable"
    fun source(id: String) = "chart-library-source-$id"
    fun asset(id: String) = "chart-library-asset-$id"
    fun repair(id: String) = "chart-library-repair-$id"
    fun validation(id: String) = "chart-library-validation-$id"
    fun role(id: String) = "chart-library-role-$id"
    fun priorityUp(id: String) = "chart-library-priority-up-$id"
    fun priorityDown(id: String) = "chart-library-priority-down-$id"
    fun managedCopy(id: String) = "chart-library-managed-copy-$id"
    fun deleteManagedCopy(id: String) = "chart-library-delete-managed-copy-$id"
}
