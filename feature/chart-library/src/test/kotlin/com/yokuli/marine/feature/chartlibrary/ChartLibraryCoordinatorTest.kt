package com.yokuli.marine.feature.chartlibrary

import com.yokuli.marine.map.domain.chartlibrary.*
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartLibraryCoordinatorTest {
    @Test
    fun shellDestinationRestoresAttentionFilterAndOpaqueAssetDetail() = runTest(UnconfinedTestDispatcher()) {
        val runtime = FakeRuntime(source(), asset())
        val coordinator = ChartLibraryCoordinator(runtime, backgroundScope)
        advanceUntilIdle()

        coordinator.open(ChartLibraryDestination.NeedsAttention)
        advanceUntilIdle()
        assertEquals(ChartLibraryFilter.NEEDS_ATTENTION, coordinator.state.value.filter)
        assertTrue(coordinator.state.value.page is ChartLibraryPageUi.Overview)

        coordinator.open(ChartLibraryDestination.Asset(ASSET_ID))
        advanceUntilIdle()
        assertTrue(coordinator.state.value.page is ChartLibraryPageUi.AssetDetail)
    }

    @Test
    fun selectedAssetMutationIsSerializedThroughCatalogTransaction() = runTest(UnconfinedTestDispatcher()) {
        val runtime = FakeRuntime(source(), asset())
        val coordinator = ChartLibraryCoordinator(runtime, backgroundScope)
        advanceUntilIdle()

        coordinator.dispatch(ChartLibraryUiAction.ToggleSelection(ASSET_ID))
        coordinator.dispatch(ChartLibraryUiAction.SetSelectedEnabled(false))
        advanceUntilIdle()

        assertFalse(runtime.assets.single().enabled)
        assertTrue(coordinator.state.value.selectedAssetIds.isEmpty())
        assertEquals(1, runtime.committedTransactions)
    }

    @Test
    fun pickerEffectMustReturnMatchingOpaqueOperationBeforeSourceIsAccepted() = runTest(UnconfinedTestDispatcher()) {
        val runtime = FakeRuntime()
        val coordinator = ChartLibraryCoordinator(
            runtime,
            backgroundScope,
            nextOperationId = { ChartLibraryOperationId("picker-1") },
        )
        val effect = async { coordinator.effects.first() }

        coordinator.dispatch(ChartLibraryUiAction.AddFolder)
        val open = effect.await() as ChartLibraryEffect.OpenPicker
        assertEquals(ChartLibraryPickerEffect.OpenTree(ChartLibraryOperationId("picker-1")), open.picker)
        coordinator.completePicker(
            ChartPickerSelection(
                operationId = ChartLibraryOperationId("wrong-operation"),
                kind = ChartPickerKind.TREE,
                locator = ChartOpaqueLocator("content://provider/tree/charts"),
                displayName = "charts",
                persistableReadGranted = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(0, runtime.acceptedPickers)
        assertEquals(ChartLibraryNoticeUi.OPERATION_FAILED, coordinator.state.value.notice)
    }

    @Test
    fun repairUsesSourceIdentityAndRequestsFreshScanWithoutDeletingOriginal() = runTest(UnconfinedTestDispatcher()) {
        val runtime = FakeRuntime(source(grant = ChartGrantState.REVOKED))
        val coordinator = ChartLibraryCoordinator(
            runtime,
            backgroundScope,
            nextOperationId = { ChartLibraryOperationId("repair-1") },
        )
        advanceUntilIdle()
        val effect = async { coordinator.effects.first() }

        coordinator.dispatch(ChartLibraryUiAction.RepairPermission(SOURCE_ID))
        val open = effect.await() as ChartLibraryEffect.OpenPicker
        coordinator.completePicker(
            ChartPickerSelection(
                operationId = open.picker.operationId,
                kind = ChartPickerKind.TREE,
                locator = ChartOpaqueLocator("content://provider/tree/restored"),
                displayName = "restored",
                persistableReadGranted = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(SOURCE_ID), runtime.repairedSources)
        assertEquals(listOf(SOURCE_ID), runtime.refreshedSources)
        assertEquals(0, runtime.removedSources)
    }

    @Test
    fun managedCopyDoesNotStartBeforeExplicitWholeFileConfirmation() = runTest(UnconfinedTestDispatcher()) {
        val runtime = FakeRuntime(source(), asset())
        runtime.storage.value = ChartLibraryStorageSnapshot(
            availableCopyBytes = 10_000L,
            copyCapability = ChartManagedCopyCapability.AVAILABLE,
        )
        val coordinator = ChartLibraryCoordinator(runtime, backgroundScope)
        advanceUntilIdle()

        coordinator.dispatch(ChartLibraryUiAction.SaveManagedCopy(ASSET_ID))
        advanceUntilIdle()
        assertTrue(coordinator.state.value.page is ChartLibraryPageUi.SaveManagedCopyConfirmation)
        assertEquals(0, runtime.savedCopies)

        coordinator.dispatch(ChartLibraryUiAction.ConfirmManagedCopy)
        advanceUntilIdle()
        assertEquals(1, runtime.savedCopies)
    }

    @Test
    fun rejectedManagedDeleteStaysOnConfirmationAndReportsActiveLease() = runTest(UnconfinedTestDispatcher()) {
        val managedSource = source().copy(
            kind = ChartLibrarySourceKind.MANAGED,
            recursive = false,
            grantState = ChartGrantState.NOT_REQUIRED,
        )
        val runtime = FakeRuntime(managedSource, asset())
        runtime.deleteResult = ChartManagedDeleteResult.Rejected(ChartManagedCopyFailure.ACTIVE_LEASE)
        val coordinator = ChartLibraryCoordinator(runtime, backgroundScope)
        advanceUntilIdle()

        coordinator.dispatch(ChartLibraryUiAction.OpenAsset(ASSET_ID))
        coordinator.dispatch(ChartLibraryUiAction.RequestDeleteManagedCopy(ASSET_ID))
        advanceUntilIdle()
        assertTrue(coordinator.state.value.page is ChartLibraryPageUi.DeleteManagedCopyConfirmation)

        coordinator.dispatch(ChartLibraryUiAction.ConfirmDeleteManagedCopy)
        advanceUntilIdle()

        assertTrue(coordinator.state.value.page is ChartLibraryPageUi.DeleteManagedCopyConfirmation)
        assertEquals(ChartLibraryNoticeUi.MANAGED_COPY_IN_USE, coordinator.state.value.notice)
    }

    @Test
    fun userViewOwnsItsLayerVisibilityOpacityAndOrderWithoutMutatingGlobalLayers() = runTest(UnconfinedTestDispatcher()) {
        val first = ChartLayer(ChartLayerId("layer-a"), "Official Charts", setOf(SOURCE_ID), stackOrder = 7)
        val secondSource = source().copy(id = ChartSourceId("00000000-0000-0000-0000-000000000002"), displayName = "Fishing")
        val second = ChartLayer(ChartLayerId("layer-b"), "Fishing", setOf(secondSource.id), stackOrder = 3)
        val view = ChartMapView(
            ChartViewId("test-a"), "Test A", ChartBuiltInBaseStyle.SATELLITE,
            listOf(ChartViewLayer(first.id, stackOrder = 0), ChartViewLayer(second.id, opacity = .7f, stackOrder = 1)),
        )
        val runtime = FakeRuntime(source(), secondSource, first, second, view).apply { activeViewId = view.id }
        val coordinator = ChartLibraryCoordinator(runtime, backgroundScope)
        advanceUntilIdle()

        coordinator.dispatch(ChartLibraryUiAction.SelectView(view.id))
        coordinator.dispatch(ChartLibraryUiAction.SetViewLayerVisible(view.id, second.id, false))
        coordinator.dispatch(ChartLibraryUiAction.SetViewLayerOpacity(view.id, second.id, .4f))
        coordinator.dispatch(ChartLibraryUiAction.MoveViewLayer(view.id, second.id, 5))
        advanceUntilIdle()

        val saved = runtime.views.single()
        val savedSecond = saved.layers.single { it.layerId == second.id }
        assertFalse(savedSecond.visible)
        assertEquals(.4f, savedSecond.opacity)
        assertEquals(6, savedSecond.stackOrder)
        assertEquals(listOf(7, 3), runtime.layers.map { it.stackOrder })
        assertTrue(coordinator.state.value.views.single().selected)
    }

    @Test
    fun globalLayerMoveNeverSilentlyRewritesUserViewComposition() = runTest(UnconfinedTestDispatcher()) {
        val layer = ChartLayer(ChartLayerId("layer-a"), "Official Charts", setOf(SOURCE_ID), stackOrder = 2)
        val view = ChartMapView(
            ChartViewId("test-a"), "Test A", ChartBuiltInBaseStyle.STANDARD,
            listOf(ChartViewLayer(layer.id, opacity = .55f, stackOrder = 9)),
        )
        val runtime = FakeRuntime(source(), layer, view).apply { activeViewId = view.id }
        val coordinator = ChartLibraryCoordinator(runtime, backgroundScope)
        advanceUntilIdle()

        coordinator.dispatch(ChartLibraryUiAction.MoveLayer(layer.id, 4))
        advanceUntilIdle()

        assertEquals(6, runtime.layers.single().stackOrder)
        assertEquals(9, runtime.views.single().layers.single().stackOrder)
        assertEquals(.55f, runtime.views.single().layers.single().opacity)
    }

    @Test
    fun inactiveSelectedViewDrivesPreviewWithoutActivatingIt() = runTest(UnconfinedTestDispatcher()) {
        val layer = ChartLayer(ChartLayerId("layer-a"), "Official Charts", setOf(SOURCE_ID))
        val active = ChartMapView(
            ChartViewId("active-a"), "Active A", ChartBuiltInBaseStyle.STANDARD,
            listOf(ChartViewLayer(layer.id, opacity = .3f)),
        )
        val inactive = ChartMapView(
            ChartViewId("preview-b"), "Preview B", ChartBuiltInBaseStyle.SATELLITE,
            listOf(ChartViewLayer(layer.id, opacity = .8f)),
        )
        val runtime = FakeRuntime(renderableSource(), renderableAsset(), layer, active, inactive).apply { activeViewId = active.id }
        val coordinator = ChartLibraryCoordinator(runtime, backgroundScope)
        advanceUntilIdle()

        coordinator.dispatch(ChartLibraryUiAction.SelectView(inactive.id))
        advanceUntilIdle()

        assertEquals(active.id, runtime.activeViewId)
        assertEquals(inactive.id, coordinator.state.value.previewDisplayPlan.activeViewId)
        assertEquals("Preview B", coordinator.state.value.previewDisplayPlan.activeViewName)
        assertEquals(.8f, coordinator.state.value.previewDisplayPlan.layers.single().opacity)
    }

    @Test
    fun coveragePlanUsesSelectedLogicalLayerAndRealRenderableAssets() = runTest(UnconfinedTestDispatcher()) {
        val selected = ChartLayer(ChartLayerId("layer-a"), "Official Charts", setOf(SOURCE_ID))
        val otherSource = source().copy(id = ChartSourceId("00000000-0000-0000-0000-000000000002"), displayName = "Fishing")
        val other = ChartLayer(ChartLayerId("layer-b"), "Fishing", setOf(otherSource.id))
        val runtime = FakeRuntime(renderableSource(), otherSource, renderableAsset(), selected, other)
        val coordinator = ChartLibraryCoordinator(runtime, backgroundScope)
        advanceUntilIdle()

        coordinator.dispatch(ChartLibraryUiAction.SelectLayer(selected.id))
        advanceUntilIdle()

        val plan = coordinator.state.value.coverageDisplayPlan
        assertEquals(listOf(selected.id), plan.logicalLayers.map { it.id })
        assertEquals(listOf(ASSET_ID), plan.layers.map { it.assetId })
        assertEquals(ChartBuiltInBaseStyle.SATELLITE, plan.builtInBaseStyle)
    }

    private class FakeRuntime(vararg initial: Any) : ChartLibraryRuntimePort {
        var sources = initial.filterIsInstance<ChartLibrarySource>().toMutableList()
        var assets = initial.filterIsInstance<ChartAsset>().toMutableList()
        var layers = initial.filterIsInstance<ChartLayer>().toMutableList()
        var views = initial.filterIsInstance<ChartMapView>().toMutableList()
        var activeViewId: ChartViewId? = null
        var committedTransactions = 0
        var acceptedPickers = 0
        var removedSources = 0
        var savedCopies = 0
        var deleteResult: ChartManagedDeleteResult = ChartManagedDeleteResult.Deleted
        val repairedSources = mutableListOf<ChartSourceId>()
        val refreshedSources = mutableListOf<ChartSourceId>()
        override val snapshot = MutableStateFlow(snapshot())
        override val state = MutableStateFlow(ChartValidationSnapshot())
        override val storage = MutableStateFlow(ChartLibraryStorageSnapshot.EMPTY)
        override val metrics = MutableStateFlow(ChartLibraryRuntimeMetrics())

        override suspend fun sources(offset: Int, limit: Int) = page(sources, offset, limit)
        override suspend fun source(id: ChartSourceId) = sources.firstOrNull { it.id == id }
        override suspend fun assets(query: ChartAssetQuery, offset: Int, limit: Int): ChartCatalogPage<ChartAsset> {
            val filtered = assets.filter { asset ->
                (query.sourceId == null || query.sourceId in asset.memberships) &&
                    (!query.enabledOnly || asset.enabled) &&
                    (query.text.isBlank() || asset.displayPath.contains(query.text, ignoreCase = true)) &&
                    (query.access.isEmpty() || asset.access in query.access) &&
                    (query.validation.isEmpty() || asset.validation in query.validation)
            }
            return page(filtered, offset, limit)
        }
        override suspend fun asset(id: ChartAssetId) = assets.firstOrNull { it.id == id }
        override suspend fun resolveLegacyAsset(legacyLogicalId: String, legacyVersionId: String?) = null
        override suspend fun layers(offset: Int, limit: Int) = page(layers, offset, limit)
        override suspend fun layer(id: ChartLayerId) = layers.firstOrNull { it.id == id }
        override suspend fun views(offset: Int, limit: Int) = page(views, offset, limit)
        override suspend fun view(id: ChartViewId) = views.firstOrNull { it.id == id }
        override suspend fun activeView() = views.firstOrNull { it.id == activeViewId }

        override suspend fun transact(transaction: ChartCatalogTransaction): ChartCatalogCommitResult {
            if (transaction.expectedRevision != null && transaction.expectedRevision != snapshot.value.revision) {
                return ChartCatalogCommitResult.Conflict(snapshot.value.revision)
            }
            transaction.mutations.forEach { mutation ->
                when (mutation) {
                    is ChartCatalogMutation.PutSource -> sources.replace(mutation.source) { it.id }
                    is ChartCatalogMutation.PutAsset -> assets.replace(mutation.asset) { it.id }
                    is ChartCatalogMutation.RemoveSource -> sources.removeAll { it.id == mutation.sourceId }
                    is ChartCatalogMutation.RemoveAssetMembership -> assets = assets.mapTo(mutableListOf()) { asset ->
                        if (asset.id == mutation.assetId) asset.copy(memberships = asset.memberships - mutation.sourceId) else asset
                    }
                    is ChartCatalogMutation.PutLegacyMapping -> Unit
                    is ChartCatalogMutation.PutManagedCopyRelation -> Unit
                    is ChartCatalogMutation.PutLayer -> layers.replace(mutation.layer) { it.id }
                    is ChartCatalogMutation.PutView -> views.replace(mutation.view) { it.id }
                    is ChartCatalogMutation.RemoveView -> {
                        views.removeAll { it.id == mutation.viewId }
                        if (activeViewId == mutation.viewId) activeViewId = null
                    }
                    is ChartCatalogMutation.ActivateView -> activeViewId = mutation.viewId
                }
            }
            committedTransactions += 1
            snapshot.value = snapshot().copy(revision = snapshot.value.revision + 1, lastTransactionId = transaction.transactionId)
            return ChartCatalogCommitResult.Committed(snapshot.value)
        }

        override suspend fun acceptPicker(selection: ChartPickerSelection): ChartSourceCommandResult {
            acceptedPickers += 1
            return ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.PERSISTENCE)
        }
        override suspend fun repair(sourceId: ChartSourceId, selection: ChartPickerSelection): ChartSourceCommandResult {
            repairedSources += sourceId
            return ChartSourceCommandResult.Accepted(sourceId)
        }
        override suspend fun refresh(sourceId: ChartSourceId): ChartSourceCommandResult {
            refreshedSources += sourceId
            return ChartSourceCommandResult.ScanPublished(sourceId, 1L, ChartScanStatus.COMPLETE)
        }
        override suspend fun cancel(sourceId: ChartSourceId) =
            ChartSourceCommandResult.ScanPublished(sourceId, 1L, ChartScanStatus.CANCELLED)
        override suspend fun remove(sourceId: ChartSourceId): ChartSourceCommandResult {
            removedSources += 1
            return ChartSourceCommandResult.Accepted(sourceId)
        }
        override suspend fun inspectBasic(assetId: ChartAssetId) =
            ChartValidationCommandResult.Rejected(ChartValidationIssue.OPEN_FAILED)
        override suspend fun verifyFull(assetId: ChartAssetId) =
            ChartValidationCommandResult.Rejected(ChartValidationIssue.OPEN_FAILED)
        override fun cancel(assetId: ChartAssetId) = Unit
        override suspend fun saveManagedCopy(assetId: ChartAssetId): ChartManagedCopyCommandResult {
            savedCopies += 1
            return ChartManagedCopyCommandResult.Accepted(assetId)
        }
        override fun cancelManagedCopy(assetId: ChartAssetId) = Unit
        override suspend fun deleteManagedCopy(assetId: ChartAssetId, confirmed: Boolean) =
            if (confirmed) deleteResult else ChartManagedDeleteResult.ConfirmationRequired(
                ChartManagedDeleteImpact(assetId, mayAffectDisplay = true),
            )
        override suspend fun open(request: ChartReadRequest) =
            ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, "test runtime")

        private fun snapshot() = ChartCatalogSnapshot(
            sourceCount = sources.size,
            assetCount = assets.size,
            layerCount = layers.size,
            viewCount = views.size,
            activeViewId = activeViewId,
        )
        private fun <T> page(items: List<T>, offset: Int, limit: Int) =
            ChartCatalogPage(items.drop(offset).take(limit), offset, limit, items.size)

        private fun <T, K> MutableList<T>.replace(value: T, key: (T) -> K) {
            val index = indexOfFirst { key(it) == key(value) }
            if (index < 0) add(value) else this[index] = value
        }
    }

    private companion object {
        val SOURCE_ID = ChartSourceId("00000000-0000-0000-0000-000000000001")
        val ASSET_ID = ChartAssetId("10000000-0000-0000-0000-000000000001")
        fun source(grant: ChartGrantState = ChartGrantState.GRANTED) = ChartLibrarySource(
            SOURCE_ID,
            ChartLibrarySourceKind.TREE,
            ChartOpaqueLocator("content://provider/tree/charts"),
            "charts",
            grantState = grant,
        )
        fun renderableSource() = source().copy(
            scan = ChartSourceScanState(
                generation = 1L,
                status = ChartScanStatus.COMPLETE,
                lastSuccessfulGeneration = 1L,
                discoveredCount = 1L,
            ),
        )
        fun asset() = ChartAsset(
            ASSET_ID,
            ChartDocumentIdentity("provider", "chart.mbtiles"),
            ChartOpaqueLocator("content://provider/document/chart.mbtiles"),
            setOf(SOURCE_ID),
            "chart.mbtiles",
            ChartContentRevision("chart.mbtiles", 10L, 1L),
        )
        fun renderableAsset() = asset().copy(
            facts = ChartAssetFacts(
                format = ChartAssetFormat.RASTER_MBTILES,
                bounds = com.yokuli.marine.map.domain.GeoBounds(-37.0, 174.0, -36.0, 175.0),
                minZoom = 6,
                maxZoom = 14,
                tileSize = 256,
                tileScheme = com.yokuli.marine.map.domain.MapTileScheme.XYZ,
            ),
            access = ChartAssetAccessState.READABLE,
            validation = ChartAssetValidationState.BASIC_READABLE,
        )
    }
}
