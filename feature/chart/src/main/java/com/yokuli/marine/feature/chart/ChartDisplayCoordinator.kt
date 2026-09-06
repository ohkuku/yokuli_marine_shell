package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.MapStore
import com.yokuli.marine.map.domain.chartlibrary.ChartAsset
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetAccessState
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetQuery
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFormat
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetValidationState
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogReadPort
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPlanner
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPreferences
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplaySelection
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayViewport
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySource
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.marine.map.domain.chartlibrary.MAX_SELECTED_CHART_SOURCES
import com.yokuli.marine.map.domain.MapAction
import java.io.Closeable
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

class ChartDisplayCoordinator(
    private val catalog: ChartCatalogReadPort,
    private val mapStore: MapStore,
    private val scope: CoroutineScope,
) : Closeable {
    private sealed interface Event {
        data class CatalogChanged(val snapshot: ChartCatalogSnapshot) : Event
        data class ViewportChanged(val viewport: ChartDisplayViewport?) : Event
        data class Action(val value: ChartDisplayUiAction) : Event
    }

    private val events = Channel<Event>(capacity = EVENT_CAPACITY)
    private val mutableState = MutableStateFlow(ChartDisplayUiState())
    val state: StateFlow<ChartDisplayUiState> = mutableState.asStateFlow()

    private var preferences = ChartDisplayPreferences()
    private var catalogSnapshot = ChartCatalogSnapshot()
    private var viewport: ChartDisplayViewport? = null
    private var sources: List<ChartLibrarySource> = emptyList()
    private var assets: List<ChartAsset> = emptyList()
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
        mapStore.state.map { it.chartDisplayViewport }.distinctUntilChanged()
            .collect { events.send(Event.ViewportChanged(it)) }
    }

    fun dispatch(action: ChartDisplayUiAction): Boolean {
        val accepted = events.trySend(Event.Action(action)).isSuccess
        if (!accepted) {
            mutableState.value = mutableState.value.copy(notice = ChartDisplayNoticeUi.ACTION_QUEUE_FULL)
        }
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
            ChartDisplayUiAction.UseNoLocalChart -> preferences = preferences.copy(selection = ChartDisplaySelection.None)
            is ChartDisplayUiAction.PinAsset -> {
                if (assets.none { it.id == action.assetId }) {
                    mutableState.value = mutableState.value.copy(notice = ChartDisplayNoticeUi.ITEM_NO_LONGER_AVAILABLE)
                    return
                }
                preferences = preferences.copy(selection = ChartDisplaySelection.PinnedAsset(action.assetId))
            }
            is ChartDisplayUiAction.ToggleSource -> {
                if (sources.none { it.id == action.sourceId }) {
                    mutableState.value = mutableState.value.copy(notice = ChartDisplayNoticeUi.ITEM_NO_LONGER_AVAILABLE)
                    return
                }
                val selected = (preferences.selection as? ChartDisplaySelection.SourceSet)?.sourceIds.orEmpty().toMutableSet()
                if (!selected.add(action.sourceId)) {
                    selected.remove(action.sourceId)
                } else if (selected.size > MAX_SELECTED_CHART_SOURCES) {
                    mutableState.value = mutableState.value.copy(notice = ChartDisplayNoticeUi.SELECTION_LIMIT_REACHED)
                    return
                }
                preferences = preferences.copy(
                    selection = if (selected.isEmpty()) ChartDisplaySelection.None else ChartDisplaySelection.SourceSet(selected),
                )
            }
            ChartDisplayUiAction.ToggleOverlays -> preferences = preferences.copy(overlaysVisible = !preferences.overlaysVisible)
            is ChartDisplayUiAction.SetOpacity -> {
                if (assets.none { it.id == action.assetId }) return
                val updated = preferences.assetOpacity.toMutableMap()
                updated[action.assetId] = action.opacity.coerceIn(0f, 1f)
                preferences = preferences.copy(assetOpacity = updated.entries.sortedBy { it.key.value }
                    .takeLast(MAX_UI_ASSET_PREFERENCES).associate { it.toPair() })
            }
            ChartDisplayUiAction.Refresh -> reloadCatalog()
            ChartDisplayUiAction.DismissNotice -> {
                mutableState.value = mutableState.value.copy(notice = null)
                return
            }
        }
        publishPlan()
    }

    private suspend fun reloadCatalog() {
        mutableState.value = mutableState.value.copy(busy = true, notice = null)
        val loadedSources: Loaded<ChartLibrarySource>
        val loadedAssets: Loaded<ChartAsset>
        try {
            loadedSources = loadPages { offset, limit -> catalog.sources(offset, limit) }
            loadedAssets = loadPages { offset, limit -> catalog.assets(ChartAssetQuery(), offset, limit) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            mutableState.value = mutableState.value.copy(
                busy = false,
                notice = ChartDisplayNoticeUi.CATALOG_READ_FAILED,
            )
            return
        }
        sources = loadedSources.items
        assets = loadedAssets.items
        val notice = if (loadedSources.truncated || loadedAssets.truncated) {
            ChartDisplayNoticeUi.CATALOG_LIMIT_REACHED
        } else null
        publishPlan(notice)
    }

    private fun publishPlan(notice: ChartDisplayNoticeUi? = mutableState.value.notice) {
        generation += 1L
        val plan = ChartDisplayPlanner.plan(
            generation = generation,
            catalog = catalogSnapshot,
            sources = sources,
            assets = assets,
            preferences = preferences,
            viewport = viewport,
        )
        mapStore.dispatch(MapAction.ChartDisplayPlanChanged(plan))
        val selectedSourceIds = (preferences.selection as? ChartDisplaySelection.SourceSet)?.sourceIds.orEmpty()
        val visibleAssetIds = plan.layers.mapTo(hashSetOf()) { it.assetId }
        val selectedAssetId = (preferences.selection as? ChartDisplaySelection.PinnedAsset)?.assetId
        mutableState.value = ChartDisplayUiState(
            catalogRevision = catalogSnapshot.revision,
            selection = preferences.selection,
            overlaysVisible = preferences.overlaysVisible,
            sources = sources.sortedBy { it.displayName.lowercase() }.map { source ->
                ChartDisplaySourceUi(
                    id = source.id,
                    name = source.displayName,
                    selected = source.id in selectedSourceIds,
                    enabled = source.enabled,
                    availableAssetCount = assets.count { source.id in it.memberships && it.availableForDisplay() },
                )
            },
            assets = assets.sortedWith(compareBy<ChartAsset> { it.role }.thenBy { it.priority }.thenBy { it.id.value })
                .map { asset ->
                    ChartDisplayAssetUi(
                        id = asset.id,
                        title = asset.displayPath.substringAfterLast('/').ifBlank { asset.displayPath },
                        role = asset.role,
                        selected = asset.id == selectedAssetId,
                        visible = asset.id in visibleAssetIds,
                        available = asset.availableForDisplay(),
                        opacity = preferences.assetOpacity[asset.id] ?: if (asset.role == ChartAssetRole.BASE) 1f else .85f,
                    )
                },
            plan = plan,
            issues = plan.issues,
            notice = notice,
            busy = false,
        )
    }

    private fun ChartAsset.availableForDisplay(): Boolean = enabled && access == ChartAssetAccessState.READABLE &&
        validation in setOf(ChartAssetValidationState.BASIC_READABLE, ChartAssetValidationState.FULL_VERIFIED) &&
        facts.format == ChartAssetFormat.RASTER_MBTILES && facts.tileSize in setOf(256, 512) &&
        facts.tileScheme != null && facts.minZoom != null && facts.maxZoom != null

    private suspend fun <T> loadPages(loader: suspend (Int, Int) -> com.yokuli.marine.map.domain.chartlibrary.ChartCatalogPage<T>): Loaded<T> {
        val result = ArrayList<T>()
        var offset = 0
        var total = 0
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
        const val MAX_UI_ASSET_PREFERENCES = 256
    }
}
