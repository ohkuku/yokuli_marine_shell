package com.yokuli.marine.feature.chartlibrary

import com.yokuli.marine.map.domain.chartlibrary.ChartAsset
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogCommitResult
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogMutation
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogTransaction
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryOperationId
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryPickerEffect
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryRuntimeMetrics
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryRuntimePort
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySource
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySourceKind
import com.yokuli.marine.map.domain.chartlibrary.ChartLayer
import com.yokuli.marine.map.domain.chartlibrary.ChartLayerId
import com.yokuli.marine.map.domain.chartlibrary.ChartMapView
import com.yokuli.marine.map.domain.chartlibrary.ChartViewId
import com.yokuli.marine.map.domain.chartlibrary.ChartViewLayer
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryStorageSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartManagedCopyCommandResult
import com.yokuli.marine.map.domain.chartlibrary.ChartManagedCopyCapability
import com.yokuli.marine.map.domain.chartlibrary.ChartManagedCopyFailure
import com.yokuli.marine.map.domain.chartlibrary.ChartManagedDeleteResult
import com.yokuli.marine.map.domain.chartlibrary.ChartPickerKind
import com.yokuli.marine.map.domain.chartlibrary.ChartPickerSelection
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceCommandFailure
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceCommandResult
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationCommandResult
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationSnapshot
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Serial feature coordinator. Catalog, validation and copy truth remain process-owned. */
class ChartLibraryCoordinator(
    private val runtime: ChartLibraryRuntimePort,
    scope: CoroutineScope,
    private val nextOperationId: () -> ChartLibraryOperationId = {
        ChartLibraryOperationId(UUID.randomUUID().toString())
    },
    private val nextTransactionId: () -> String = { UUID.randomUUID().toString() },
) {
    private sealed interface Event {
        data object Reload : Event
        data class ValidationChanged(val value: ChartValidationSnapshot) : Event
        data class StorageChanged(val value: ChartLibraryStorageSnapshot) : Event
        data class MetricsChanged(val value: ChartLibraryRuntimeMetrics) : Event
        data class Action(val value: ChartLibraryUiAction) : Event
        data class PickerResult(val value: ChartPickerSelection?) : Event
        data class OpenDestination(val value: ChartLibraryDestination) : Event
    }

    private data class PendingPicker(val kind: ChartPickerKind, val repairSourceId: ChartSourceId?)

    private val events = Channel<Event>(MAX_PENDING_EVENTS)
    private val effectChannel = Channel<ChartLibraryEffect>(MAX_PENDING_EFFECTS)
    val effects: Flow<ChartLibraryEffect> = effectChannel.receiveAsFlow()
    private var catalog = runtime.snapshot.value
    private var validation = runtime.state.value
    private var storage = runtime.storage.value
    private var metrics = runtime.metrics.value
    private var sources = emptyList<ChartLibrarySource>()
    private var assets = emptyList<ChartAsset>()
    private var layers = emptyList<ChartLayer>()
    private var views = emptyList<ChartMapView>()
    private var originalByManaged = emptyMap<ChartAssetId, ChartAssetId>()
    private var managedByOriginal = emptyMap<ChartAssetId, ChartAssetId>()
    private var local = ChartLibraryLocalState()
    private var notice: ChartLibraryNoticeUi? = null
    private var busy = false
    private val pendingPickers = linkedMapOf<ChartLibraryOperationId, PendingPicker>()
    private val mutableState = MutableStateFlow(project())
    val state: StateFlow<ChartLibraryUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            for (event in events) process(event)
        }
        scope.launch { runtime.snapshot.collect { events.send(Event.Reload) } }
        scope.launch { runtime.state.collect { events.send(Event.ValidationChanged(it)) } }
        scope.launch { runtime.storage.collect { events.send(Event.StorageChanged(it)) } }
        scope.launch { runtime.metrics.collect { events.send(Event.MetricsChanged(it)) } }
        events.trySend(Event.Reload)
    }

    fun dispatch(action: ChartLibraryUiAction) {
        if (events.trySend(Event.Action(action)).isFailure) {
            mutableState.value = mutableState.value.copy(notice = ChartLibraryNoticeUi.ACTION_QUEUE_FULL)
        }
    }

    /** A Shell token restores feature-owned page/filter state without exposing storage models. */
    fun open(destination: ChartLibraryDestination) {
        if (events.trySend(Event.OpenDestination(destination)).isFailure) {
            mutableState.value = mutableState.value.copy(notice = ChartLibraryNoticeUi.ACTION_QUEUE_FULL)
        }
    }

    /** The host returns only the selection produced for a previously emitted opaque operation id. */
    fun completePicker(selection: ChartPickerSelection?) {
        if (events.trySend(Event.PickerResult(selection)).isFailure) {
            mutableState.value = mutableState.value.copy(notice = ChartLibraryNoticeUi.ACTION_QUEUE_FULL)
        }
    }

    /** False at root lets the in-app Shell return to Start instead of exiting Android. */
    fun handleBack(): Boolean {
        if (state.value.page is ChartLibraryPageUi.Overview) return false
        dispatch(ChartLibraryUiAction.NavigateBack)
        return true
    }

    private suspend fun process(event: Event) {
        try {
            when (event) {
                Event.Reload -> reload()
                is Event.ValidationChanged -> {
                    validation = event.value
                    publish()
                }
                is Event.StorageChanged -> {
                    storage = event.value
                    publish()
                }
                is Event.MetricsChanged -> {
                    metrics = event.value
                    publish()
                }
                is Event.Action -> processAction(event.value)
                is Event.PickerResult -> processPicker(event.value)
                is Event.OpenDestination -> openDestination(event.value)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            busy = false
            notice = ChartLibraryNoticeUi.OPERATION_FAILED
            publish()
        }
    }

    private suspend fun reload() {
        busy = true
        publish()
        catalog = runtime.snapshot.value
        sources = readAllSources()
        assets = readAllAssets()
        layers = readAllLayers()
        views = readAllViews()
        val sourceIds = sources.mapTo(hashSetOf(), ChartLibrarySource::id)
        val assetIds = assets.mapTo(hashSetOf(), ChartAsset::id)
        val layerIds = layers.mapTo(hashSetOf(), ChartLayer::id)
        local = local.copy(
            selectedAssetIds = local.selectedAssetIds.intersect(assetIds),
            selectedLayerId = local.selectedLayerId?.takeIf(layerIds::contains),
        )
        when (val page = local.page) {
            is ChartLibraryLocalPage.SourceDetail -> if (page.sourceId !in sourceIds) missingPage()
            is ChartLibraryLocalPage.AssetDetail -> if (page.assetId !in assetIds) missingPage()
            is ChartLibraryLocalPage.RemoveSourceConfirmation -> if (page.sourceId !in sourceIds) missingPage()
            is ChartLibraryLocalPage.DeleteManagedCopyConfirmation -> if (page.assetId !in assetIds) missingPage()
            is ChartLibraryLocalPage.SaveManagedCopyConfirmation -> if (page.assetId !in assetIds) missingPage()
            else -> Unit
        }
        val detailAssetId = when (val page = local.page) {
            is ChartLibraryLocalPage.AssetDetail -> page.assetId
            is ChartLibraryLocalPage.DeleteManagedCopyConfirmation -> page.assetId
            is ChartLibraryLocalPage.SaveManagedCopyConfirmation -> page.assetId
            else -> null
        }
        originalByManaged = detailAssetId?.let { id -> runtime.originalForManagedCopy(id)?.let { mapOf(id to it) } }.orEmpty()
        managedByOriginal = detailAssetId?.let { id -> runtime.managedCopyFor(id)?.let { mapOf(id to it) } }.orEmpty()
        busy = false
        publish()
    }

    private suspend fun processAction(action: ChartLibraryUiAction) {
        when (action) {
            ChartLibraryUiAction.AddFolder -> requestPicker(ChartPickerKind.TREE, null)
            ChartLibraryUiAction.AddSingleFile -> requestPicker(ChartPickerKind.SINGLE_DOCUMENT, null)
            is ChartLibraryUiAction.ChangeQuery -> {
                local = local.copy(query = action.value.take(MAX_QUERY_LENGTH))
                publish()
            }
            is ChartLibraryUiAction.ChangeFilter -> {
                local = local.copy(filter = action.value)
                publish()
            }
            is ChartLibraryUiAction.SelectWorkspace -> {
                local = local.copy(workspaceMode = action.value)
                publish()
            }
            is ChartLibraryUiAction.OpenSource -> {
                local = if (sources.any { it.id == action.sourceId }) {
                    local.copy(page = ChartLibraryLocalPage.SourceDetail(action.sourceId))
                } else local.also { notice = ChartLibraryNoticeUi.ITEM_NOT_FOUND }
                publish()
            }
            is ChartLibraryUiAction.OpenAsset -> {
                local = if (assets.any { it.id == action.assetId }) {
                    local.copy(page = ChartLibraryLocalPage.AssetDetail(action.assetId))
                } else local.also { notice = ChartLibraryNoticeUi.ITEM_NOT_FOUND }
                reload()
            }
            ChartLibraryUiAction.OpenStorage -> {
                local = local.copy(page = ChartLibraryLocalPage.Storage)
                publish()
            }
            ChartLibraryUiAction.NavigateBack -> navigateBack()
            is ChartLibraryUiAction.ToggleSelection -> toggleSelection(action.assetId)
            ChartLibraryUiAction.ClearSelection -> {
                local = local.copy(selectedAssetIds = emptySet())
                publish()
            }
            is ChartLibraryUiAction.SetSelectedEnabled -> setSelectedEnabled(action.enabled)
            is ChartLibraryUiAction.SetSourceEnabled -> mutateSource(action.sourceId) { it.copy(enabled = action.enabled) }
            is ChartLibraryUiAction.SetAssetEnabled -> mutateAsset(action.assetId) { it.copy(enabled = action.enabled) }
            is ChartLibraryUiAction.SetAssetRole -> mutateAsset(action.assetId) { it.copy(role = action.role) }
            is ChartLibraryUiAction.MoveAssetPriority -> mutateAsset(action.assetId) {
                it.copy(priority = (it.priority + action.delta).coerceIn(-10_000, 10_000))
            }
            is ChartLibraryUiAction.RefreshSource -> sourceCommand { runtime.refresh(action.sourceId) }
            is ChartLibraryUiAction.CancelSourceScan -> sourceCommand { runtime.cancel(action.sourceId) }
            is ChartLibraryUiAction.RepairPermission -> {
                val source = sources.firstOrNull { it.id == action.sourceId }
                if (source == null || source.kind == ChartLibrarySourceKind.MANAGED) missingPage()
                else requestPicker(
                    if (source.kind == ChartLibrarySourceKind.TREE) ChartPickerKind.TREE else ChartPickerKind.SINGLE_DOCUMENT,
                    source.id,
                )
            }
            is ChartLibraryUiAction.RequestRemoveSource -> {
                local = local.copy(page = ChartLibraryLocalPage.RemoveSourceConfirmation(action.sourceId))
                publish()
            }
            ChartLibraryUiAction.ConfirmRemoveSource -> removeConfirmedSource()
            is ChartLibraryUiAction.InspectBasic -> validate(action.assetId, full = false)
            is ChartLibraryUiAction.VerifyFull -> validate(action.assetId, full = true)
            is ChartLibraryUiAction.CancelValidation -> {
                runtime.cancel(action.assetId)
                notice = ChartLibraryNoticeUi.VALIDATION_CANCELLED
                publish()
            }
            is ChartLibraryUiAction.SaveManagedCopy -> requestSaveCopy(action.assetId)
            ChartLibraryUiAction.ConfirmManagedCopy -> confirmSaveCopy()
            is ChartLibraryUiAction.CancelManagedCopy -> {
                runtime.cancelManagedCopy(action.assetId)
                notice = ChartLibraryNoticeUi.COPY_CANCELLED
                publish()
            }
            is ChartLibraryUiAction.RequestDeleteManagedCopy -> requestDeleteManagedCopy(action.assetId)
            ChartLibraryUiAction.ConfirmDeleteManagedCopy -> confirmDeleteManagedCopy()
            is ChartLibraryUiAction.ViewInChart -> {
                val asset = assets.firstOrNull { it.id == action.assetId }
                if (asset == null) missingPage()
                else if (effectChannel.trySend(ChartLibraryEffect.OpenAssetInChart(asset.id, asset.facts.bounds)).isFailure) {
                    notice = ChartLibraryNoticeUi.ACTION_QUEUE_FULL
                    publish()
                }
            }
            is ChartLibraryUiAction.SelectLayer -> {
                local = local.copy(selectedLayerId = action.layerId?.takeIf { id -> layers.any { it.id == id } })
                publish()
            }
            is ChartLibraryUiAction.RenameLayer -> mutateLayer(action.layerId) { layer ->
                layer.copy(displayName = action.name.trim().take(128).ifBlank { layer.displayName })
            }
            is ChartLibraryUiAction.SetLayerVisible -> mutateLayer(action.layerId) { it.copy(visible = action.visible) }
            is ChartLibraryUiAction.SetLayerOpacity -> mutateLayer(action.layerId) {
                it.copy(opacity = action.opacity.coerceIn(0f, 1f))
            }
            is ChartLibraryUiAction.MoveLayer -> moveLayer(action.layerId, action.delta)
            is ChartLibraryUiAction.CreateView -> createView(action.name, action.baseStyle)
            is ChartLibraryUiAction.RenameView -> mutateView(action.viewId) { view ->
                view.copy(displayName = action.name.trim().take(128).ifBlank { view.displayName })
            }
            is ChartLibraryUiAction.DuplicateView -> duplicateView(action.viewId, action.name)
            is ChartLibraryUiAction.DeleteView -> deleteView(action.viewId)
            is ChartLibraryUiAction.ActivateView -> commit(
                listOf(ChartCatalogMutation.ActivateView(action.viewId)),
                ChartLibraryNoticeUi.VIEW_UPDATED,
            )
            is ChartLibraryUiAction.SetViewBaseStyle -> mutateView(action.viewId) { it.copy(baseStyle = action.baseStyle) }
            is ChartLibraryUiAction.ToggleViewLayer -> mutateView(action.viewId) { view ->
                val current = view.layers.firstOrNull { it.layerId == action.layerId }
                view.copy(
                    layers = if (current == null) {
                        val layer = layers.firstOrNull { it.id == action.layerId }
                        if (layer == null) view.layers else view.layers + ChartViewLayer(
                            layerId = layer.id,
                            visible = true,
                            opacity = layer.opacity,
                            stackOrder = layer.stackOrder,
                        )
                    } else {
                        view.layers.filterNot { it.layerId == action.layerId }
                    },
                )
            }
            ChartLibraryUiAction.DismissNotice -> {
                notice = null
                publish()
            }
        }
    }

    private suspend fun openDestination(destination: ChartLibraryDestination) {
        local = when (destination) {
            ChartLibraryDestination.Browse -> local.copy(page = ChartLibraryLocalPage.Overview)
            ChartLibraryDestination.NeedsAttention -> local.copy(
                page = ChartLibraryLocalPage.Overview,
                filter = ChartLibraryFilter.NEEDS_ATTENTION,
            )
            is ChartLibraryDestination.Source -> local.copy(page = ChartLibraryLocalPage.SourceDetail(destination.id))
            is ChartLibraryDestination.Asset -> local.copy(page = ChartLibraryLocalPage.AssetDetail(destination.id))
        }
        reload()
    }

    private fun requestPicker(kind: ChartPickerKind, repairSourceId: ChartSourceId?) {
        val operationId = nextOperationId()
        val picker = when (kind) {
            ChartPickerKind.TREE -> ChartLibraryPickerEffect.OpenTree(operationId)
            ChartPickerKind.SINGLE_DOCUMENT -> ChartLibraryPickerEffect.OpenDocument(operationId)
        }
        if (pendingPickers.size >= MAX_PENDING_PICKERS ||
            effectChannel.trySend(ChartLibraryEffect.OpenPicker(picker, repairSourceId)).isFailure
        ) {
            notice = ChartLibraryNoticeUi.ACTION_QUEUE_FULL
        } else {
            pendingPickers[operationId] = PendingPicker(kind, repairSourceId)
        }
        publish()
    }

    private suspend fun processPicker(selection: ChartPickerSelection?) {
        if (selection == null) {
            pendingPickers.entries.firstOrNull()?.let { pendingPickers.remove(it.key) }
            notice = ChartLibraryNoticeUi.PICKER_CANCELLED
            publish()
            return
        }
        val pending = pendingPickers.remove(selection.operationId)
        if (pending == null || pending.kind != selection.kind) {
            notice = ChartLibraryNoticeUi.OPERATION_FAILED
            publish()
            return
        }
        busy = true
        publish()
        val result = pending.repairSourceId?.let { runtime.repair(it, selection) }
            ?: runtime.acceptPicker(selection)
        when (result) {
            is ChartSourceCommandResult.Accepted -> {
                notice = if (pending.repairSourceId == null) {
                    ChartLibraryNoticeUi.SOURCE_ADDED
                } else ChartLibraryNoticeUi.SOURCE_REPAIRED
                runtime.refresh(result.sourceId)
            }
            is ChartSourceCommandResult.ScanPublished -> notice = ChartLibraryNoticeUi.SCAN_FINISHED
            is ChartSourceCommandResult.Rejected -> notice = result.reason.toNotice()
        }
        reload()
    }

    private fun navigateBack() {
        local = local.copy(
            page = when (val page = local.page) {
                is ChartLibraryLocalPage.RemoveSourceConfirmation -> ChartLibraryLocalPage.SourceDetail(page.sourceId)
                is ChartLibraryLocalPage.DeleteManagedCopyConfirmation -> ChartLibraryLocalPage.AssetDetail(page.assetId)
                is ChartLibraryLocalPage.SaveManagedCopyConfirmation -> ChartLibraryLocalPage.AssetDetail(page.assetId)
                else -> ChartLibraryLocalPage.Overview
            },
        )
        publish()
    }

    private fun toggleSelection(assetId: ChartAssetId) {
        if (assets.none { it.id == assetId }) return missingPage()
        val selected = local.selectedAssetIds
        local = when {
            assetId in selected -> local.copy(selectedAssetIds = selected - assetId)
            selected.size >= MAX_SELECTED_ASSETS -> local.also { notice = ChartLibraryNoticeUi.SELECTION_LIMIT_REACHED }
            else -> local.copy(selectedAssetIds = selected + assetId)
        }
        publish()
    }

    private suspend fun setSelectedEnabled(enabled: Boolean) {
        val selected = assets.filter { it.id in local.selectedAssetIds }
        if (selected.isEmpty()) return
        commit(selected.map { ChartCatalogMutation.PutAsset(it.copy(enabled = enabled)) }, ChartLibraryNoticeUi.ASSET_UPDATED)
        if (notice == ChartLibraryNoticeUi.ASSET_UPDATED) {
            local = local.copy(selectedAssetIds = emptySet())
            publish()
        }
    }

    private suspend fun mutateSource(sourceId: ChartSourceId, transform: (ChartLibrarySource) -> ChartLibrarySource) {
        val source = sources.firstOrNull { it.id == sourceId } ?: return missingPage()
        commit(listOf(ChartCatalogMutation.PutSource(transform(source))), ChartLibraryNoticeUi.SOURCE_UPDATED)
    }

    private suspend fun mutateAsset(assetId: ChartAssetId, transform: (ChartAsset) -> ChartAsset) {
        val asset = assets.firstOrNull { it.id == assetId } ?: return missingPage()
        commit(listOf(ChartCatalogMutation.PutAsset(transform(asset))), ChartLibraryNoticeUi.ASSET_UPDATED)
    }

    private suspend fun mutateLayer(layerId: ChartLayerId, transform: (ChartLayer) -> ChartLayer) {
        val layer = layers.firstOrNull { it.id == layerId } ?: return missingPage()
        commit(listOf(ChartCatalogMutation.PutLayer(transform(layer))), ChartLibraryNoticeUi.LAYER_UPDATED)
    }

    private suspend fun moveLayer(layerId: ChartLayerId, delta: Int) {
        val layer = layers.firstOrNull { it.id == layerId } ?: return missingPage()
        val newOrder = (layer.stackOrder + delta).coerceIn(-10_000, 10_000)
        val mutations = buildList {
            add(ChartCatalogMutation.PutLayer(layer.copy(stackOrder = newOrder)))
            views.filter { view -> view.layers.any { it.layerId == layerId } }.forEach { view ->
                add(
                    ChartCatalogMutation.PutView(
                        view.copy(layers = view.layers.map { item ->
                            if (item.layerId == layerId) item.copy(stackOrder = newOrder) else item
                        }),
                    ),
                )
            }
        }
        commit(mutations, ChartLibraryNoticeUi.LAYER_UPDATED)
    }

    private suspend fun createView(name: String, baseStyle: com.yokuli.marine.map.domain.chartlibrary.ChartBuiltInBaseStyle) {
        val normalizedName = name.trim().take(128)
        if (normalizedName.isBlank()) {
            notice = ChartLibraryNoticeUi.OPERATION_FAILED
            publish()
            return
        }
        val id = ChartViewId("view-${UUID.randomUUID()}")
        val view = ChartMapView(
            id = id,
            displayName = normalizedName,
            baseStyle = baseStyle,
            layers = layers.sortedBy { it.stackOrder }.map {
                ChartViewLayer(it.id, visible = it.visible, opacity = it.opacity, stackOrder = it.stackOrder)
            },
        )
        commit(
            listOf(ChartCatalogMutation.PutView(view), ChartCatalogMutation.ActivateView(id)),
            ChartLibraryNoticeUi.VIEW_UPDATED,
        )
    }

    private suspend fun duplicateView(viewId: ChartViewId, name: String) {
        val view = views.firstOrNull { it.id == viewId } ?: return missingPage()
        val id = ChartViewId("view-${UUID.randomUUID()}")
        val normalizedName = name.trim().take(128).ifBlank { view.displayName }
        commit(
            listOf(ChartCatalogMutation.PutView(view.copy(id = id, displayName = normalizedName))),
            ChartLibraryNoticeUi.VIEW_UPDATED,
        )
    }

    private suspend fun deleteView(viewId: ChartViewId) {
        if (views.none { it.id == viewId }) return missingPage()
        commit(listOf(ChartCatalogMutation.RemoveView(viewId)), ChartLibraryNoticeUi.VIEW_UPDATED)
    }

    private suspend fun mutateView(viewId: ChartViewId, transform: (ChartMapView) -> ChartMapView) {
        val view = views.firstOrNull { it.id == viewId } ?: return missingPage()
        commit(listOf(ChartCatalogMutation.PutView(transform(view))), ChartLibraryNoticeUi.VIEW_UPDATED)
    }

    private suspend fun commit(mutations: List<ChartCatalogMutation>, success: ChartLibraryNoticeUi) {
        busy = true
        publish()
        notice = when (
            runtime.transact(
                ChartCatalogTransaction(
                    transactionId = "library-ui:${nextTransactionId()}",
                    expectedRevision = catalog.revision,
                    mutations = mutations,
                ),
            )
        ) {
            is ChartCatalogCommitResult.Committed -> success
            is ChartCatalogCommitResult.Conflict -> ChartLibraryNoticeUi.CATALOG_CHANGED
            is ChartCatalogCommitResult.Failed -> ChartLibraryNoticeUi.OPERATION_FAILED
        }
        reload()
    }

    private suspend fun sourceCommand(block: suspend () -> ChartSourceCommandResult) {
        busy = true
        publish()
        notice = when (val result = block()) {
            is ChartSourceCommandResult.Accepted -> ChartLibraryNoticeUi.SOURCE_UPDATED
            is ChartSourceCommandResult.ScanPublished -> when (result.status) {
                com.yokuli.marine.map.domain.chartlibrary.ChartScanStatus.CANCELLED -> ChartLibraryNoticeUi.SCAN_CANCELLED
                else -> ChartLibraryNoticeUi.SCAN_FINISHED
            }
            is ChartSourceCommandResult.Rejected -> result.reason.toNotice()
        }
        reload()
    }

    private suspend fun removeConfirmedSource() {
        val sourceId = (local.page as? ChartLibraryLocalPage.RemoveSourceConfirmation)?.sourceId ?: return
        busy = true
        publish()
        notice = when (val result = runtime.remove(sourceId)) {
            is ChartSourceCommandResult.Accepted -> ChartLibraryNoticeUi.SOURCE_REMOVED
            is ChartSourceCommandResult.ScanPublished -> ChartLibraryNoticeUi.SOURCE_REMOVED
            is ChartSourceCommandResult.Rejected -> result.reason.toNotice()
        }
        if (notice == ChartLibraryNoticeUi.SOURCE_REMOVED) local = local.copy(page = ChartLibraryLocalPage.Overview)
        reload()
    }

    private suspend fun validate(assetId: ChartAssetId, full: Boolean) {
        busy = true
        publish()
        val result = if (full) runtime.verifyFull(assetId) else runtime.inspectBasic(assetId)
        notice = when (result) {
            is ChartValidationCommandResult.Published -> ChartLibraryNoticeUi.VALIDATION_FINISHED
            is ChartValidationCommandResult.Rejected -> ChartLibraryNoticeUi.OPERATION_FAILED
            ChartValidationCommandResult.Cancelled -> ChartLibraryNoticeUi.SCAN_CANCELLED
        }
        reload()
    }

    private suspend fun saveCopy(assetId: ChartAssetId) {
        busy = true
        publish()
        notice = when (val result = runtime.saveManagedCopy(assetId)) {
            is ChartManagedCopyCommandResult.Accepted -> ChartLibraryNoticeUi.COPY_STARTED
            is ChartManagedCopyCommandResult.Rejected -> when (result.failure) {
                ChartManagedCopyFailure.INSUFFICIENT_SPACE -> ChartLibraryNoticeUi.MANAGED_COPY_NO_SPACE
                else -> ChartLibraryNoticeUi.OPERATION_FAILED
            }
        }
        busy = false
        publish()
    }

    private fun requestSaveCopy(assetId: ChartAssetId) {
        val asset = assets.firstOrNull { it.id == assetId } ?: return missingPage()
        val managedSourceIds = sources.filter { it.kind == ChartLibrarySourceKind.MANAGED }.mapTo(hashSetOf()) { it.id }
        if (asset.memberships.any(managedSourceIds::contains) || storage.copyCapability != ChartManagedCopyCapability.AVAILABLE) {
            notice = ChartLibraryNoticeUi.OPERATION_FAILED
        } else {
            local = local.copy(page = ChartLibraryLocalPage.SaveManagedCopyConfirmation(assetId))
            notice = null
        }
        publish()
    }

    private suspend fun confirmSaveCopy() {
        val assetId = (local.page as? ChartLibraryLocalPage.SaveManagedCopyConfirmation)?.assetId ?: return
        local = local.copy(page = ChartLibraryLocalPage.AssetDetail(assetId))
        saveCopy(assetId)
    }

    private suspend fun requestDeleteManagedCopy(assetId: ChartAssetId) {
        notice = when (val result = runtime.deleteManagedCopy(assetId, confirmed = false)) {
            is ChartManagedDeleteResult.ConfirmationRequired -> {
                local = local.copy(page = ChartLibraryLocalPage.DeleteManagedCopyConfirmation(assetId))
                null
            }
            ChartManagedDeleteResult.Deleted -> ChartLibraryNoticeUi.MANAGED_COPY_DELETED
            is ChartManagedDeleteResult.Rejected -> if (result.failure == ChartManagedCopyFailure.ACTIVE_LEASE) {
                ChartLibraryNoticeUi.MANAGED_COPY_IN_USE
            } else ChartLibraryNoticeUi.OPERATION_FAILED
        }
        publish()
    }

    private suspend fun confirmDeleteManagedCopy() {
        val assetId = (local.page as? ChartLibraryLocalPage.DeleteManagedCopyConfirmation)?.assetId ?: return
        busy = true
        publish()
        val result = runtime.deleteManagedCopy(assetId, confirmed = true)
        notice = when (result) {
            ChartManagedDeleteResult.Deleted -> ChartLibraryNoticeUi.MANAGED_COPY_DELETED
            is ChartManagedDeleteResult.Rejected -> if (result.failure == ChartManagedCopyFailure.ACTIVE_LEASE) {
                ChartLibraryNoticeUi.MANAGED_COPY_IN_USE
            } else ChartLibraryNoticeUi.OPERATION_FAILED
            is ChartManagedDeleteResult.ConfirmationRequired -> ChartLibraryNoticeUi.OPERATION_FAILED
        }
        // A rejected destructive operation stays on the confirmation surface so the user can
        // release the active reader or retry. Only a completed delete leaves the asset surface.
        if (result == ChartManagedDeleteResult.Deleted) {
            local = local.copy(page = ChartLibraryLocalPage.Overview)
        }
        reload()
    }

    private suspend fun readAllSources(): List<ChartLibrarySource> = buildList {
        var offset = 0
        do {
            val page = runtime.sources(offset)
            val accepted = page.items.take((MAX_LOADED_ITEMS - size).coerceAtLeast(0))
            addAll(accepted)
            offset += page.items.size
        } while (offset < page.total && page.items.isNotEmpty() && size < MAX_LOADED_ITEMS)
    }

    private suspend fun readAllAssets(): List<ChartAsset> = buildList {
        var offset = 0
        do {
            val page = runtime.assets(offset = offset)
            val accepted = page.items.take((MAX_LOADED_ITEMS - size).coerceAtLeast(0))
            addAll(accepted)
            offset += page.items.size
        } while (offset < page.total && page.items.isNotEmpty() && size < MAX_LOADED_ITEMS)
    }

    private suspend fun readAllLayers(): List<ChartLayer> = buildList {
        var offset = 0
        do {
            val page = runtime.layers(offset)
            addAll(page.items.take((MAX_LOADED_ITEMS - size).coerceAtLeast(0)))
            offset += page.items.size
        } while (offset < page.total && page.items.isNotEmpty() && size < MAX_LOADED_ITEMS)
    }

    private suspend fun readAllViews(): List<ChartMapView> = buildList {
        var offset = 0
        do {
            val page = runtime.views(offset)
            addAll(page.items.take((MAX_LOADED_ITEMS - size).coerceAtLeast(0)))
            offset += page.items.size
        } while (offset < page.total && page.items.isNotEmpty() && size < MAX_LOADED_ITEMS)
    }

    private fun missingPage() {
        local = local.copy(page = ChartLibraryLocalPage.Overview)
        notice = ChartLibraryNoticeUi.ITEM_NOT_FOUND
        publish()
    }

    private fun publish() {
        mutableState.value = project()
    }

    private fun project() = ChartLibraryProjector.project(
        catalog = catalog,
        sources = sources,
        assets = assets,
        layers = layers,
        views = views,
        validation = validation,
        storage = storage,
        metrics = metrics,
        local = local,
        originalByManaged = originalByManaged,
        managedByOriginal = managedByOriginal,
        notice = notice,
        busy = busy,
    )

    private fun ChartSourceCommandFailure.toNotice() = when (this) {
        ChartSourceCommandFailure.PICKER_CANCELLED -> ChartLibraryNoticeUi.PICKER_CANCELLED
        ChartSourceCommandFailure.READ_GRANT_MISSING -> ChartLibraryNoticeUi.PERMISSION_REQUIRED
        ChartSourceCommandFailure.SOURCE_NOT_FOUND -> ChartLibraryNoticeUi.ITEM_NOT_FOUND
        ChartSourceCommandFailure.STALE_OPERATION -> ChartLibraryNoticeUi.CATALOG_CHANGED
        ChartSourceCommandFailure.PERSISTENCE -> ChartLibraryNoticeUi.OPERATION_FAILED
    }

    private companion object {
        const val MAX_PENDING_EVENTS = 64
        const val MAX_PENDING_EFFECTS = 16
        const val MAX_PENDING_PICKERS = 1
        const val MAX_SELECTED_ASSETS = 1_000
        const val MAX_QUERY_LENGTH = 256
        const val MAX_LOADED_ITEMS = 10_000
    }
}
