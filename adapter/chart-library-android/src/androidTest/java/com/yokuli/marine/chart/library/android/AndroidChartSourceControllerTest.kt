package com.yokuli.marine.chart.library.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.map.domain.chartlibrary.*
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidChartSourceControllerTest {
    @Test fun pickerPersistsGrantAndCompleteVersusPartialControlsMissing() = runBlocking {
        RoomChartCatalogRepository.create(context, freshDatabase("source-controller")).use { catalog ->
            val grant = FakeGrant()
            var enumeration: ChartEnumerationResult = ChartEnumerationResult.Complete(listOf(document("one")))
            val controller = AndroidChartSourceController(catalog, { _, _ -> enumeration }, grant)
            val accepted = controller.acceptPicker(selection("pick-one")) as ChartSourceCommandResult.Accepted
            assertEquals(1, grant.taken)
            assertTrue(controller.refresh(accepted.sourceId) is ChartSourceCommandResult.ScanPublished)
            val asset = catalog.assets().items.single()
            assertEquals(ChartAssetValidationState.DISCOVERED, asset.validation)

            enumeration = ChartEnumerationResult.Partial(
                emptyList(), listOf(ChartEnumerationIssue("subtree", ChartEnumerationIssueKind.QUERY_FAILED)),
            )
            controller.refresh(accepted.sourceId)
            assertNotEquals(ChartAssetAccessState.MISSING, catalog.asset(asset.id)?.access)

            enumeration = ChartEnumerationResult.Complete(emptyList())
            controller.refresh(accepted.sourceId)
            assertEquals(ChartAssetAccessState.MISSING, catalog.asset(asset.id)?.access)
            controller.remove(accepted.sourceId)
            assertEquals(1, grant.released)
        }
    }

    @Test fun explicitCancelPublishesCancelledAndLateEnumerationCannotOverwriteIt() = runBlocking {
        RoomChartCatalogRepository.create(context, freshDatabase("source-cancel")).use { catalog ->
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val controller = AndroidChartSourceController(
                catalog,
                ChartDocumentEnumerationPort { _, _ ->
                    started.complete(Unit)
                    release.await()
                    ChartEnumerationResult.Complete(listOf(document("late")))
                },
                FakeGrant(),
            )
            val sourceId = (controller.acceptPicker(selection("cancel")) as ChartSourceCommandResult.Accepted).sourceId
            coroutineScope {
                val scan = async { controller.refresh(sourceId) }
                started.await()
                assertTrue(controller.cancel(sourceId) is ChartSourceCommandResult.ScanPublished)
                release.complete(Unit)
                assertEquals(
                    ChartSourceCommandFailure.STALE_OPERATION,
                    (scan.await() as ChartSourceCommandResult.Rejected).reason,
                )
            }
            assertEquals(ChartScanStatus.CANCELLED, catalog.source(sourceId)?.scan?.status)
            assertEquals(0, catalog.assets().total)
        }
    }

    @Test fun deniedOrUnpersistablePickerLeavesNoHalfSource() = runBlocking {
        RoomChartCatalogRepository.create(context, freshDatabase("source-denied")).use { catalog ->
            val grant = FakeGrant(allow = false)
            val controller = AndroidChartSourceController(catalog, { _, _ -> ChartEnumerationResult.Complete(emptyList()) }, grant)
            assertEquals(
                ChartSourceCommandFailure.READ_GRANT_MISSING,
                (controller.acceptPicker(selection("denied")) as ChartSourceCommandResult.Rejected).reason,
            )
            assertEquals(0, catalog.snapshot.value.sourceCount)
        }
    }

    @Test fun repeatedPickerIsIdempotentAndRemovingOneGrantDoesNotReleaseAnother() = runBlocking {
        RoomChartCatalogRepository.create(context, freshDatabase("source-grants")).use { catalog ->
            val grant = FakeGrant()
            val controller = AndroidChartSourceController(
                catalog,
                { _, _ -> ChartEnumerationResult.Complete(emptyList()) },
                grant,
            )
            val tree = selection("tree")
            val first = controller.acceptPicker(tree) as ChartSourceCommandResult.Accepted
            val repeated = controller.acceptPicker(tree) as ChartSourceCommandResult.Accepted
            assertEquals(first.sourceId, repeated.sourceId)
            assertEquals(1, catalog.snapshot.value.sourceCount)

            val document = tree.copy(
                operationId = ChartLibraryOperationId("document"),
                kind = ChartPickerKind.SINGLE_DOCUMENT,
                locator = ChartOpaqueLocator("content://test.provider/document/tree/chart.mbtiles"),
                displayName = "chart.mbtiles",
            )
            val second = controller.acceptPicker(document) as ChartSourceCommandResult.Accepted
            assertEquals(2, catalog.snapshot.value.sourceCount)
            controller.remove(first.sourceId)
            assertEquals(1, grant.released)
            assertEquals(second.sourceId, catalog.sources().items.single().id)
        }
    }

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun freshDatabase(name: String) = File(context.cacheDir, "$name.db").also {
        it.delete(); File("${it.path}-wal").delete(); File("${it.path}-shm").delete()
    }
    private fun selection(id: String) = ChartPickerSelection(
        ChartLibraryOperationId(id), ChartPickerKind.TREE,
        ChartOpaqueLocator("content://test.provider/tree/$id"), id, persistableReadGranted = true,
    )
    private fun document(id: String) = ChartDiscoveredDocument(
        ChartDocumentIdentity("test.provider", id), ChartOpaqueLocator("content://test.provider/document/$id"),
        "$id.mbtiles", 100L, 1L, mimeType = "application/vnd.sqlite3",
    )
    private class FakeGrant(private val allow: Boolean = true) : PersistedChartGrantPort {
        var taken = 0
        var released = 0
        override fun takeRead(locator: ChartOpaqueLocator): Boolean { taken++; return allow }
        override fun releaseRead(locator: ChartOpaqueLocator): Boolean { released++; return true }
    }
}
