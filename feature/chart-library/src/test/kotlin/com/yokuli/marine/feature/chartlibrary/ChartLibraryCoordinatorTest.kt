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
    fun selectedAssetMutationIsSerializedThroughCatalogTransaction() = runTest {
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

    private class FakeRuntime(vararg initial: Any) : ChartLibraryRuntimePort {
        var sources = initial.filterIsInstance<ChartLibrarySource>().toMutableList()
        var assets = initial.filterIsInstance<ChartAsset>().toMutableList()
        var committedTransactions = 0
        var acceptedPickers = 0
        var removedSources = 0
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
        override suspend fun saveManagedCopy(assetId: ChartAssetId) =
            ChartManagedCopyCommandResult.Rejected(ChartManagedCopyFailure.NOT_AVAILABLE)
        override fun cancelManagedCopy(assetId: ChartAssetId) = Unit
        override suspend fun open(request: ChartReadRequest) =
            ChartOpenResult.Rejected(ChartReadFailure.CANNOT_OPEN, "test runtime")

        private fun snapshot() = ChartCatalogSnapshot(sourceCount = sources.size, assetCount = assets.size)
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
        fun asset() = ChartAsset(
            ASSET_ID,
            ChartDocumentIdentity("provider", "chart.mbtiles"),
            ChartOpaqueLocator("content://provider/document/chart.mbtiles"),
            setOf(SOURCE_ID),
            "chart.mbtiles",
            ChartContentRevision("chart.mbtiles", 10L, 1L),
        )
    }
}
