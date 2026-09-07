package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapStore
import com.yokuli.marine.map.domain.MapViewMode
import com.yokuli.marine.map.domain.chartlibrary.ChartAsset
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetQuery
import com.yokuli.marine.map.domain.chartlibrary.ChartBuiltInBaseStyle
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogCommitResult
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogMutation
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogReadPort
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogTransaction
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayViewport
import com.yokuli.marine.map.domain.chartlibrary.ChartLayer
import com.yokuli.marine.map.domain.chartlibrary.ChartLayerHealth
import com.yokuli.marine.map.domain.chartlibrary.ChartLayerId
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryCommandPort
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySource
import com.yokuli.marine.map.domain.chartlibrary.ChartMapView
import com.yokuli.marine.map.domain.chartlibrary.ChartViewDisplayPlanner
import com.yokuli.marine.map.domain.chartlibrary.ChartViewLayer
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Projects the catalog's single active View into Chart. It deliberately has no Asset/Source
 * selection state: files, grants and validation remain Chart Library concerns.
 */
class ChartDisplayCoordinator(
    private val catalog: ChartCatalogReadPort,
    private val mapStore: MapStore,
    private val scope: CoroutineScope,
    private val command: ChartLibraryCommandPort? = catalog as? ChartLibraryCommandPort,
) : Closeable {
    private sealed interface Event {
        data class CatalogChanged(val snapshot: ChartCatalogSnapshot) : Event
        data class ViewportChanged(val viewport: ChartDisplayViewport?) : Event
        data class Action(val value: ChartDisplayUiAction) : Event
    }

    private val events = Channel<Event>(EVENT_CAPACITY)
    private val mutableState = MutableStateFlow(ChartDisplayUiState())
    val state: StateFlow<ChartDisplayUiState> = mutableState.asStateFlow()

    private var catalogSnapshot = catalog.snapshot.value
    private var viewport: ChartDisplayViewport? = null
    private var sources = emptyList<ChartLibrarySource>()
    private var assets = emptyList<ChartAsset>()
    private var layers = emptyList<ChartLayer>()
    private var views = emptyList<ChartMapView>()
    private var generation = 0L

    private val actor: Job = scope.launch {
        for (event in events) {
            when (event) {
                is Event.CatalogChanged -> {
                    catalogSnapshot = event.snapshot
                    reloadCatalog()
                }
                is Event.ViewportChanged -> {
                    viewport = event.viewport
                    publishPlan()
                }
                is Event.Action -> handle(event.value)
            }
        }
    }
    private val catalogObserver = scope.launch {
        catalog.snapshot.collect { events.send(Event.CatalogChanged(it)) }
    }
    private val viewportObserver = scope.launch {
        mapStore.state.map { it.chartDisplayViewport }.distinctUntilChanged().collect {
            events.send(Event.ViewportChanged(it))
        }
    }

    fun dispatch(action: ChartDisplayUiAction): Boolean {
        val accepted = events.trySend(Event.Action(action)).isSuccess
        if (!accepted) mutableState.value = mutableState.value.copy(notice = ChartDisplayNoticeUi.ACTION_QUEUE_FULL)
        return accepted
    }

    override fun close() {
        catalogObserver.cancel()
        viewportObserver.cancel()
        actor.cancel()
        events.close()
    }

    private suspend fun handle(action: ChartDisplayUiAction) {
        when (action) {
            is ChartDisplayUiAction.ActivateView -> {
                if (views.none { it.id == action.viewId }) return missingItem()
                commit(listOf(ChartCatalogMutation.ActivateView(action.viewId)))
            }
            is ChartDisplayUiAction.SetLayerVisible -> updateActiveLayer(action.layerId) { current, layer ->
                current?.copy(visible = action.visible) ?: ChartViewLayer(
                    layerId = layer.id,
                    visible = action.visible,
                    opacity = layer.opacity,
                    stackOrder = layer.stackOrder,
                )
            }
            is ChartDisplayUiAction.SetOpacity -> updateActiveLayer(action.layerId) { current, layer ->
                (current ?: ChartViewLayer(layer.id, layer.visible, layer.opacity, layer.stackOrder))
                    .copy(opacity = action.opacity.coerceIn(0f, 1f))
            }
            ChartDisplayUiAction.Refresh -> reloadCatalog()
            ChartDisplayUiAction.DismissNotice -> mutableState.value = mutableState.value.copy(notice = null)
        }
    }

    private suspend fun updateActiveLayer(
        layerId: ChartLayerId,
        transform: (ChartViewLayer?, ChartLayer) -> ChartViewLayer,
    ) {
        val view = activeView() ?: return missingItem()
        val layer = layers.firstOrNull { it.id == layerId } ?: return missingItem()
        val current = view.layers.firstOrNull { it.layerId == layerId }
        val updated = transform(current, layer)
        val entries = if (current == null) view.layers + updated else view.layers.map {
            if (it.layerId == layerId) updated else it
        }
        commit(listOf(ChartCatalogMutation.PutView(view.copy(layers = entries))))
    }

    private suspend fun commit(mutations: List<ChartCatalogMutation>) {
        val writer = command ?: run {
            mutableState.value = mutableState.value.copy(notice = ChartDisplayNoticeUi.VIEW_UPDATE_FAILED)
            return
        }
        mutableState.value = mutableState.value.copy(busy = true, notice = null)
        when (
            val result = writer.transact(
                ChartCatalogTransaction(
                    transactionId = "chart-view:${UUID.randomUUID()}",
                    expectedRevision = catalogSnapshot.revision,
                    mutations = mutations,
                ),
            )
        ) {
            is ChartCatalogCommitResult.Committed -> {
                catalogSnapshot = result.snapshot
                reloadCatalog()
            }
            is ChartCatalogCommitResult.Conflict -> {
                catalogSnapshot = catalog.snapshot.value
                reloadCatalog(ChartDisplayNoticeUi.VIEW_UPDATE_FAILED)
            }
            is ChartCatalogCommitResult.Failed -> {
                mutableState.value = mutableState.value.copy(busy = false, notice = ChartDisplayNoticeUi.VIEW_UPDATE_FAILED)
            }
        }
    }

    private suspend fun reloadCatalog(notice: ChartDisplayNoticeUi? = null) {
        mutableState.value = mutableState.value.copy(busy = true, notice = notice)
        try {
            val loadedSources = loadPages { offset, limit -> catalog.sources(offset, limit) }
            val loadedAssets = loadPages { offset, limit -> catalog.assets(ChartAssetQuery(), offset, limit) }
            val loadedLayers = loadPages { offset, limit -> catalog.layers(offset, limit) }
            val loadedViews = loadPages { offset, limit -> catalog.views(offset, limit) }
            sources = loadedSources.items
            assets = loadedAssets.items
            layers = loadedLayers.items
            views = loadedViews.items
            val truncated = loadedSources.truncated || loadedAssets.truncated || loadedLayers.truncated || loadedViews.truncated
            publishPlan(if (truncated) ChartDisplayNoticeUi.CATALOG_LIMIT_REACHED else notice)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            mutableState.value = mutableState.value.copy(busy = false, notice = ChartDisplayNoticeUi.CATALOG_READ_FAILED)
        }
    }

    private fun publishPlan(notice: ChartDisplayNoticeUi? = mutableState.value.notice) {
        generation += 1L
        val active = activeView()
        val plan = ChartViewDisplayPlanner.plan(
            generation = generation,
            catalog = catalogSnapshot,
            sources = sources,
            assets = assets,
            layers = layers,
            view = active,
            viewport = viewport,
        )
        mapStore.dispatch(MapAction.ChartDisplayPlanChanged(plan))
        val desiredMapMode = when (plan.builtInBaseStyle) {
            ChartBuiltInBaseStyle.NONE -> MapViewMode.MARINE
            ChartBuiltInBaseStyle.STANDARD -> MapViewMode.STANDARD
            ChartBuiltInBaseStyle.SATELLITE -> MapViewMode.SATELLITE
        }
        if (mapStore.state.value.mapViewMode != desiredMapMode) mapStore.dispatch(MapAction.SetMapViewMode(desiredMapMode))
        mutableState.value = ChartDisplayUiState(
            catalogRevision = catalogSnapshot.revision,
            activeViewId = active?.id,
            activeViewName = active?.displayName,
            views = views.sortedWith(compareByDescending<ChartMapView> { it.id == active?.id }.thenBy { it.displayName.lowercase() })
                .map { ChartDisplayViewUi(it.id, it.displayName, it.baseStyle, it.id == active?.id) },
            quickLayers = plan.logicalLayers.map { layer ->
                ChartQuickLayerUi(
                    id = layer.id,
                    title = layer.displayName,
                    role = layer.role,
                    visible = layer.visible,
                    available = layer.assetIds.isNotEmpty() && layer.health !in setOf(
                        ChartLayerHealth.PERMISSION_LOST,
                        ChartLayerHealth.UNAVAILABLE,
                        ChartLayerHealth.EMPTY,
                    ),
                    opacity = layer.opacity,
                    health = layer.health,
                )
            },
            plan = plan,
            issues = plan.issues,
            notice = notice,
            busy = false,
        )
    }

    private fun activeView(): ChartMapView? = catalogSnapshot.activeViewId?.let { id -> views.firstOrNull { it.id == id } }

    private fun missingItem() {
        mutableState.value = mutableState.value.copy(notice = ChartDisplayNoticeUi.ITEM_NO_LONGER_AVAILABLE)
    }

    private suspend fun <T> loadPages(
        loader: suspend (Int, Int) -> com.yokuli.marine.map.domain.chartlibrary.ChartCatalogPage<T>,
    ): Loaded<T> {
        val result = ArrayList<T>()
        var offset = 0
        var total: Int
        do {
            val page = loader(offset, PAGE_SIZE)
            total = page.total
            result += page.items
            offset += page.items.size
        } while (page.items.isNotEmpty() && offset < total && result.size < MAX_CATALOG_ITEMS)
        return Loaded(result.take(MAX_CATALOG_ITEMS), total > result.size)
    }

    private data class Loaded<T>(val items: List<T>, val truncated: Boolean)

    private companion object {
        const val EVENT_CAPACITY = 64
        const val PAGE_SIZE = 100
        const val MAX_CATALOG_ITEMS = 10_000
    }
}
