package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapDispatchResult
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapStore
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.MapViewMode
import com.yokuli.marine.map.domain.chartlibrary.ChartAsset
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetAccessState
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFacts
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFormat
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetQuery
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetValidationState
import com.yokuli.marine.map.domain.chartlibrary.ChartBuiltInBaseStyle
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogCommitResult
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogMutation
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogPage
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogReadPort
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogTransaction
import com.yokuli.marine.map.domain.chartlibrary.ChartContentRevision
import com.yokuli.marine.map.domain.chartlibrary.ChartDocumentIdentity
import com.yokuli.marine.map.domain.chartlibrary.ChartGrantState
import com.yokuli.marine.map.domain.chartlibrary.ChartLayer
import com.yokuli.marine.map.domain.chartlibrary.ChartLayerId
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryCommandPort
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySource
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySourceKind
import com.yokuli.marine.map.domain.chartlibrary.ChartMapView
import com.yokuli.marine.map.domain.chartlibrary.ChartOpaqueLocator
import com.yokuli.marine.map.domain.chartlibrary.ChartScanStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceScanState
import com.yokuli.marine.map.domain.chartlibrary.ChartViewId
import com.yokuli.marine.map.domain.chartlibrary.ChartViewLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDisplayCoordinatorTest {
    @Test
    fun `zero custom Views keeps the persisted fallback basemap without inventing a catalog View`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val catalog = FakeCatalog(startingViews = emptyList())
        val store = FakeMapStore(MapState(mapViewMode = MapViewMode.STANDARD))
        val coordinator = ChartDisplayCoordinator(catalog, store, scope)

        val state = withTimeout(2_000L) { coordinator.state.first { !it.busy } }

        assertTrue(state.views.isEmpty())
        assertEquals(null, state.activeViewId)
        assertEquals(ChartBuiltInBaseStyle.STANDARD, state.plan.builtInBaseStyle)
        assertEquals(MapViewMode.STANDARD, store.state.value.mapViewMode)
        assertTrue(catalog.transactions.isEmpty())
        coordinator.close()
        scope.cancel()
    }

    @Test
    fun `active View is the only Chart display source and exposes logical names`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val catalog = FakeCatalog()
        val store = FakeMapStore()
        val coordinator = ChartDisplayCoordinator(catalog, store, scope)

        val state = withTimeout(2_000L) { coordinator.state.first { !it.busy && it.activeViewId == VIEW_A } }

        assertEquals("Sailing", state.activeViewName)
        assertEquals(listOf("NZ Hydro"), state.quickLayers.map { it.title })
        assertEquals(listOf(ASSET_A), state.plan.layers.map { it.assetId })
        assertFalse(state.plan.layers.single().displayName.contains("mbtiles", ignoreCase = true))
        coordinator.close()
        scope.cancel()
    }

    @Test
    fun `activating a View is catalog durable and switches the matching built in base`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val catalog = FakeCatalog()
        val store = FakeMapStore()
        val coordinator = ChartDisplayCoordinator(catalog, store, scope)
        withTimeout(2_000L) { coordinator.state.first { !it.busy && it.activeViewId == VIEW_A } }

        coordinator.dispatch(ChartDisplayUiAction.ActivateView(VIEW_B))
        val state = withTimeout(2_000L) { coordinator.state.first { !it.busy && it.activeViewId == VIEW_B } }

        assertEquals("Planning", state.activeViewName)
        assertEquals(MapViewMode.STANDARD, store.state.value.mapViewMode)
        assertTrue(catalog.transactions.any { it.mutations == listOf(ChartCatalogMutation.ActivateView(VIEW_B)) })
        coordinator.close()
        scope.cancel()
    }

    @Test
    fun `quick layer visibility and opacity update the active View not an Asset preference`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val catalog = FakeCatalog()
        val store = FakeMapStore()
        val coordinator = ChartDisplayCoordinator(catalog, store, scope)
        withTimeout(2_000L) { coordinator.state.first { !it.busy && it.quickLayers.singleOrNull()?.visible == true } }

        coordinator.dispatch(ChartDisplayUiAction.SetOpacity(LAYER_ID, .4f))
        withTimeout(2_000L) { coordinator.state.first { it.quickLayers.singleOrNull()?.opacity == .4f } }
        coordinator.dispatch(ChartDisplayUiAction.SetLayerVisible(LAYER_ID, false))
        val state = withTimeout(2_000L) { coordinator.state.first { it.quickLayers.singleOrNull()?.visible == false } }

        assertTrue(state.plan.layers.isEmpty())
        assertTrue(state.quickLayers.single().available)
        assertEquals(.4f, catalog.viewsValue.single { it.id == VIEW_A }.layers.single().opacity)
        assertFalse(catalog.viewsValue.single { it.id == VIEW_A }.layers.single().visible)
        assertFalse(store.actions.any { it is MapAction.ChartDisplayPreferencesChanged })
        coordinator.close()
        scope.cancel()
    }

    private class FakeMapStore(initial: MapState = MapState()) : MapStore {
        private val mutable = MutableStateFlow(initial)
        override val state: StateFlow<MapState> = mutable
        val actions = CopyOnWriteArrayList<MapAction>()

        override fun dispatch(action: MapAction): MapDispatchResult {
            actions += action
            when (action) {
                is MapAction.ChartDisplayPlanChanged -> mutable.value = mutable.value.copy(chartDisplayPlan = action.plan)
                is MapAction.SetMapViewMode -> mutable.value = mutable.value.copy(mapViewMode = action.mode)
                else -> Unit
            }
            return MapDispatchResult.ACCEPTED
        }

        override fun close() = Unit
    }

    private class FakeCatalog(startingViews: List<ChartMapView>? = null) : ChartCatalogReadPort, ChartLibraryCommandPort {
        private val sourcesValue = listOf(source())
        private val assetsValue = listOf(asset())
        private val layersValue = listOf(ChartLayer(LAYER_ID, "NZ Hydro", setOf(SOURCE_ID)))
        var viewsValue = startingViews ?: listOf(
            ChartMapView(VIEW_A, "Sailing", ChartBuiltInBaseStyle.SATELLITE, listOf(ChartViewLayer(LAYER_ID))),
            ChartMapView(VIEW_B, "Planning", ChartBuiltInBaseStyle.STANDARD, listOf(ChartViewLayer(LAYER_ID, visible = false))),
        )
        private val mutableSnapshot = MutableStateFlow(snapshot(1L, viewsValue.firstOrNull()?.id))
        override val snapshot: StateFlow<ChartCatalogSnapshot> = mutableSnapshot
        val transactions = CopyOnWriteArrayList<ChartCatalogTransaction>()

        override suspend fun sources(offset: Int, limit: Int) = page(sourcesValue, offset, limit)
        override suspend fun source(id: ChartSourceId) = sourcesValue.firstOrNull { it.id == id }
        override suspend fun assets(query: ChartAssetQuery, offset: Int, limit: Int) = page(assetsValue, offset, limit)
        override suspend fun asset(id: ChartAssetId) = assetsValue.firstOrNull { it.id == id }
        override suspend fun resolveLegacyAsset(legacyLogicalId: String, legacyVersionId: String?) = null
        override suspend fun layers(offset: Int, limit: Int) = page(layersValue, offset, limit)
        override suspend fun layer(id: ChartLayerId) = layersValue.firstOrNull { it.id == id }
        override suspend fun views(offset: Int, limit: Int) = page(viewsValue, offset, limit)
        override suspend fun view(id: ChartViewId) = viewsValue.firstOrNull { it.id == id }

        override suspend fun transact(transaction: ChartCatalogTransaction): ChartCatalogCommitResult {
            transactions += transaction
            var active = mutableSnapshot.value.activeViewId
            transaction.mutations.forEach { mutation ->
                when (mutation) {
                    is ChartCatalogMutation.PutView -> viewsValue = viewsValue.filterNot { it.id == mutation.view.id } + mutation.view
                    is ChartCatalogMutation.ActivateView -> active = mutation.viewId
                    else -> error("Unexpected mutation in Chart display test: $mutation")
                }
            }
            val next = snapshot(mutableSnapshot.value.revision + 1L, active)
            mutableSnapshot.value = next
            return ChartCatalogCommitResult.Committed(next)
        }

        private fun snapshot(revision: Long, active: ChartViewId?) = ChartCatalogSnapshot(
            revision = revision,
            sourceCount = sourcesValue.size,
            assetCount = assetsValue.size,
            layerCount = layersValue.size,
            viewCount = viewsValue.size,
            activeViewId = active,
        )

        private fun <T> page(items: List<T>, offset: Int, limit: Int) = ChartCatalogPage(
            items.drop(offset).take(limit), offset, limit, items.size,
        )
    }

    private companion object {
        val SOURCE_ID = ChartSourceId("00000000-0000-0000-0000-000000000001")
        val ASSET_A = ChartAssetId("10000000-0000-0000-0000-000000000001")
        val LAYER_ID = ChartLayerId("nz-hydro")
        val VIEW_A = ChartViewId("sailing")
        val VIEW_B = ChartViewId("planning")

        fun source() = ChartLibrarySource(
            SOURCE_ID,
            ChartLibrarySourceKind.TREE,
            ChartOpaqueLocator("content://provider/tree/charts"),
            "NZ Hydro",
            grantState = ChartGrantState.GRANTED,
            scan = ChartSourceScanState(2L, ChartScanStatus.COMPLETE, 2L),
        )

        fun asset() = ChartAsset(
            ASSET_A,
            ChartDocumentIdentity("provider", "secret.mbtiles"),
            ChartOpaqueLocator("content://provider/document/secret.mbtiles"),
            setOf(SOURCE_ID),
            "files/secret.mbtiles",
            ChartContentRevision("secret", 1_024L, 1L),
            facts = ChartAssetFacts(
                format = ChartAssetFormat.RASTER_MBTILES,
                sizeBytes = 1_024L,
                bounds = GeoBounds(-50.0, 160.0, -30.0, 180.0),
                minZoom = 0,
                maxZoom = 18,
                tileCount = 1L,
                tileSize = 256,
                tileScheme = MapTileScheme.MBTILES_TMS,
            ),
            access = ChartAssetAccessState.READABLE,
            validation = ChartAssetValidationState.BASIC_READABLE,
        )
    }
}
