package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapDispatchResult
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapStore
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.ChartAsset
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetAccessState
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFacts
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFormat
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetQuery
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetValidationState
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogPage
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogReadPort
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartContentRevision
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplaySelection
import com.yokuli.marine.map.domain.chartlibrary.ChartDocumentIdentity
import com.yokuli.marine.map.domain.chartlibrary.ChartGrantState
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySource
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySourceKind
import com.yokuli.marine.map.domain.chartlibrary.ChartOpaqueLocator
import com.yokuli.marine.map.domain.chartlibrary.ChartScanStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceScanState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDisplayCoordinatorTest {
    @Test
    fun `catalog flow becomes the only display source and actions are serialized`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val catalog = FakeCatalog(listOf(source()), listOf(asset(ASSET_A)))
        val store = FakeMapStore()
        val coordinator = ChartDisplayCoordinator(catalog, store, scope)
        withTimeout(2_000L) { coordinator.state.first { !it.busy && it.assets.size == 1 } }

        assertTrue(coordinator.dispatch(ChartDisplayUiAction.ToggleSource(SOURCE_ID)))
        withTimeout(2_000L) {
            coordinator.state.first { it.selection == ChartDisplaySelection.SourceSet(setOf(SOURCE_ID)) }
        }
        assertEquals(listOf(ASSET_A), coordinator.state.value.plan.layers.map { it.assetId })
        assertTrue(store.actions.last() is MapAction.ChartDisplayPlanChanged)

        catalog.replaceAssets(listOf(asset(ASSET_A), asset(ASSET_B)))
        withTimeout(2_000L) { coordinator.state.first { it.assets.size == 2 } }
        assertEquals(2, coordinator.state.value.plan.layers.size)
        coordinator.close()
        scope.cancel()
    }

    @Test
    fun `new catalog asset never steals an explicitly pinned chart`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val catalog = FakeCatalog(listOf(source()), listOf(asset(ASSET_A)))
        val coordinator = ChartDisplayCoordinator(catalog, FakeMapStore(), scope)
        withTimeout(2_000L) { coordinator.state.first { !it.busy && it.assets.size == 1 } }

        coordinator.dispatch(ChartDisplayUiAction.PinAsset(ASSET_A))
        withTimeout(2_000L) { coordinator.state.first { it.selection == ChartDisplaySelection.PinnedAsset(ASSET_A) } }
        catalog.replaceAssets(listOf(asset(ASSET_A), asset(ASSET_B, priority = -100)))
        withTimeout(2_000L) { coordinator.state.first { it.assets.size == 2 } }

        assertEquals(ChartDisplaySelection.PinnedAsset(ASSET_A), coordinator.state.value.selection)
        assertEquals(listOf(ASSET_A), coordinator.state.value.plan.layers.map { it.assetId })
        coordinator.close()
        scope.cancel()
    }

    @Test
    fun `overlay visibility and opacity are display preferences not catalog mutations`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val overlay = asset(ASSET_B, role = ChartAssetRole.OVERLAY)
        val catalog = FakeCatalog(listOf(source()), listOf(asset(ASSET_A), overlay))
        val coordinator = ChartDisplayCoordinator(catalog, FakeMapStore(), scope)
        withTimeout(2_000L) { coordinator.state.first { !it.busy && it.assets.size == 2 } }
        coordinator.dispatch(ChartDisplayUiAction.ToggleSource(SOURCE_ID))
        withTimeout(2_000L) { coordinator.state.first { it.plan.layers.size == 2 } }

        coordinator.dispatch(ChartDisplayUiAction.SetOpacity(ASSET_B, .4f))
        withTimeout(2_000L) { coordinator.state.first { it.plan.layers.any { layer -> layer.assetId == ASSET_B && layer.opacity == .4f } } }
        coordinator.dispatch(ChartDisplayUiAction.ToggleOverlays)
        withTimeout(2_000L) { coordinator.state.first { !it.overlaysVisible } }

        assertFalse(coordinator.state.value.plan.layers.any { it.assetId == ASSET_B })
        assertEquals(0, catalog.mutationCount)
        coordinator.close()
        scope.cancel()
    }

    @Test
    fun `quick layer visibility changes display preferences without changing catalog ownership`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val catalog = FakeCatalog(listOf(source()), listOf(asset(ASSET_A), asset(ASSET_B)))
        val store = FakeMapStore()
        val coordinator = ChartDisplayCoordinator(catalog, store, scope)
        withTimeout(2_000L) { coordinator.state.first { !it.busy && it.assets.size == 2 } }
        coordinator.dispatch(ChartDisplayUiAction.ToggleSource(SOURCE_ID))
        withTimeout(2_000L) { coordinator.state.first { it.quickLayers.size == 2 && it.plan.layers.size == 2 } }

        coordinator.dispatch(ChartDisplayUiAction.SetLayerVisible(ASSET_B, false))
        withTimeout(2_000L) { coordinator.state.first { state -> state.quickLayers.any { it.id == ASSET_B && !it.visible } } }

        assertEquals(listOf(ASSET_A), coordinator.state.value.plan.layers.map { it.assetId })
        assertTrue(store.state.value.chartDisplayPreferences.hiddenAssetIds.contains(ASSET_B))
        assertEquals(0, catalog.mutationCount)
        coordinator.close()
        scope.cancel()
    }

    @Test
    fun `legacy active package migrates once and explicit none is never stolen back`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val catalog = FakeCatalog(listOf(source()), listOf(asset(ASSET_A)), legacyAssetId = ASSET_A)
        val store = FakeMapStore(MapState(activeChartPackageId = ChartPackageId("legacy-package")))
        val coordinator = ChartDisplayCoordinator(catalog, store, scope)
        withTimeout(2_000L) {
            coordinator.state.first { it.selection == ChartDisplaySelection.PinnedAsset(ASSET_A) }
        }
        assertTrue(store.state.value.chartDisplayPreferencesInitialized)

        coordinator.dispatch(ChartDisplayUiAction.UseNoLocalChart)
        withTimeout(2_000L) { coordinator.state.first { it.selection == ChartDisplaySelection.None } }
        catalog.replaceAssets(listOf(asset(ASSET_A), asset(ASSET_B)))
        withTimeout(2_000L) { coordinator.state.first { it.assets.size == 2 } }

        assertEquals(ChartDisplaySelection.None, coordinator.state.value.selection)
        coordinator.close()
        scope.cancel()
    }

    private class FakeMapStore(initial: MapState = MapState()) : MapStore {
        private val mutable = MutableStateFlow(initial)
        override val state: StateFlow<MapState> = mutable
        val actions = mutableListOf<MapAction>()
        override fun dispatch(action: MapAction): MapDispatchResult {
            actions += action
            if (action is MapAction.ChartDisplayPlanChanged) mutable.value = mutable.value.copy(chartDisplayPlan = action.plan)
            if (action is MapAction.ChartDisplayPreferencesChanged) mutable.value = mutable.value.copy(
                chartDisplayPreferences = action.preferences,
                chartDisplayPreferencesInitialized = true,
            )
            return MapDispatchResult.ACCEPTED
        }
        override fun close() = Unit
    }

    private class FakeCatalog(
        private var sourceItems: List<ChartLibrarySource>,
        private var assetItems: List<ChartAsset>,
        private val legacyAssetId: ChartAssetId? = null,
    ) : ChartCatalogReadPort {
        private val mutableSnapshot = MutableStateFlow(snapshotFor(1L))
        override val snapshot: StateFlow<ChartCatalogSnapshot> = mutableSnapshot
        var mutationCount = 0

        fun replaceAssets(value: List<ChartAsset>) {
            assetItems = value
            mutableSnapshot.value = snapshotFor(mutableSnapshot.value.revision + 1L)
        }

        override suspend fun sources(offset: Int, limit: Int) = page(sourceItems, offset, limit)
        override suspend fun source(id: ChartSourceId) = sourceItems.firstOrNull { it.id == id }
        override suspend fun assets(query: ChartAssetQuery, offset: Int, limit: Int) = page(
            assetItems.filter { query.sourceId == null || query.sourceId in it.memberships },
            offset,
            limit,
        )
        override suspend fun asset(id: ChartAssetId) = assetItems.firstOrNull { it.id == id }
        override suspend fun resolveLegacyAsset(legacyLogicalId: String, legacyVersionId: String?) =
            legacyAssetId.takeIf { legacyLogicalId == "legacy-package" }

        private fun snapshotFor(revision: Long) = ChartCatalogSnapshot(revision, sourceItems.size, assetItems.size)
        private fun <T> page(items: List<T>, offset: Int, limit: Int) = ChartCatalogPage(
            items.drop(offset).take(limit), offset, limit, items.size,
        )
    }

    private fun source() = ChartLibrarySource(
        SOURCE_ID,
        ChartLibrarySourceKind.TREE,
        ChartOpaqueLocator("content://provider/tree/charts"),
        "NZ Hydro",
        grantState = ChartGrantState.GRANTED,
        scan = ChartSourceScanState(2L, ChartScanStatus.COMPLETE, 2L),
    )

    private fun asset(id: ChartAssetId, role: ChartAssetRole = ChartAssetRole.BASE, priority: Int = 0) = ChartAsset(
        id,
        ChartDocumentIdentity("provider", id.value),
        ChartOpaqueLocator("content://provider/document/${id.value}"),
        setOf(SOURCE_ID),
        "charts/${id.value}.mbtiles",
        ChartContentRevision(id.value, 1024L, 1L),
        facts = ChartAssetFacts(
            format = ChartAssetFormat.RASTER_MBTILES,
            sizeBytes = 1024L,
            bounds = GeoBounds(-50.0, 160.0, -30.0, 180.0),
            minZoom = 0,
            maxZoom = 18,
            tileCount = 1L,
            tileSize = 256,
            tileScheme = MapTileScheme.MBTILES_TMS,
        ),
        role = role,
        priority = priority,
        access = ChartAssetAccessState.READABLE,
        validation = ChartAssetValidationState.BASIC_READABLE,
    )

    private companion object {
        val SOURCE_ID = ChartSourceId("00000000-0000-0000-0000-000000000001")
        val ASSET_A = ChartAssetId("10000000-0000-0000-0000-000000000001")
        val ASSET_B = ChartAssetId("10000000-0000-0000-0000-000000000002")
    }
}
