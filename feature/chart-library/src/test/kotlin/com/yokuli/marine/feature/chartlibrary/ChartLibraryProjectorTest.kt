package com.yokuli.marine.feature.chartlibrary

import com.yokuli.marine.map.domain.chartlibrary.ChartAsset
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetAccessState
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFacts
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFormat
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetValidationState
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartContentRevision
import com.yokuli.marine.map.domain.chartlibrary.ChartDocumentIdentity
import com.yokuli.marine.map.domain.chartlibrary.ChartGrantState
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryRuntimeMetrics
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySource
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySourceKind
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryStorageSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartManagedCopyCapability
import com.yokuli.marine.map.domain.chartlibrary.ChartOpaqueLocator
import com.yokuli.marine.map.domain.chartlibrary.ChartScanStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceScanState
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationIssue
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationJob
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationJobKind
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationJobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartLibraryProjectorTest {
    @Test
    fun sourceFirstProjectionDoesNotExposeOpaqueLocator() {
        val source = source(locator = "content://com.example.documents/tree/secret%3Acharts")
        val ui = project(listOf(source), emptyList())
        val row = (ui.page as ChartLibraryPageUi.Overview).sources.single()

        assertEquals("com.example.documents", row.provider)
        assertFalse(row.provider.orEmpty().contains("secret"))
        assertEquals(1, ui.summary.sourceCount)
    }

    @Test
    fun onePhysicalAssetInTwoExternalSourcesCountsStorageOnlyOnce() {
        val first = source(id = SOURCE_A_VALUE)
        val second = source(id = SOURCE_B_VALUE)
        val asset = asset(memberships = setOf(first.id, second.id), size = 4_096L)
        val ui = project(listOf(first, second), listOf(asset), local = ChartLibraryLocalState(page = ChartLibraryLocalPage.Storage))
        val storage = (ui.page as ChartLibraryPageUi.Storage).storage

        assertEquals(4_096L, storage.referencedOriginalBytes)
        assertEquals(0, storage.referencedUnknownSizeCount)
    }

    @Test
    fun searchAndAttentionFilterUseCatalogFactsWithoutInventingReadiness() {
        val source = source()
        val good = asset(title = "harbour.mbtiles", access = ChartAssetAccessState.READABLE, validation = ChartAssetValidationState.BASIC_READABLE)
        val broken = asset(
            id = ASSET_B,
            title = "coast.mbtiles",
            access = ChartAssetAccessState.PERMISSION_LOST,
            validation = ChartAssetValidationState.DISCOVERED,
        )
        val ui = project(
            listOf(source),
            listOf(good, broken),
            local = ChartLibraryLocalState(query = "coast", filter = ChartLibraryFilter.NEEDS_ATTENTION),
        )
        val page = ui.page as ChartLibraryPageUi.Overview

        assertEquals(listOf(ASSET_B), page.assets.map { it.id })
        assertEquals(1, ui.summary.availableAssetCount)
        assertTrue(page.assets.single().needsAttention)
    }

    @Test
    fun managedCopyActionIsProjectedOnlyFromRuntimeCapability() {
        val source = source()
        val asset = asset()
        val unavailable = project(listOf(source), listOf(asset))
        val available = project(
            listOf(source),
            listOf(asset),
            storage = ChartLibraryStorageSnapshot(copyCapability = ChartManagedCopyCapability.AVAILABLE),
        )

        assertFalse(((unavailable.page as ChartLibraryPageUi.Overview).assets.single()).managedCopyAvailable)
        assertTrue(((available.page as ChartLibraryPageUi.Overview).assets.single()).managedCopyAvailable)
    }

    @Test
    fun missingRevisionEvidenceStaysUnknownInsteadOfFabricated() {
        val row = (project(listOf(source()), listOf(asset())).page as ChartLibraryPageUi.Overview).assets.single()
        assertNull(row.revisionSummary)
    }

    @Test
    fun backgroundBasicQueueDoesNotPresentTheWholeLibraryAsBlocked() {
        val ui = ChartLibraryProjector.project(
            catalog = ChartCatalogSnapshot(sourceCount = 1, assetCount = 1),
            sources = listOf(source()),
            assets = listOf(asset()),
            validation = ChartValidationSnapshot(),
            storage = ChartLibraryStorageSnapshot.EMPTY,
            metrics = ChartLibraryRuntimeMetrics(queuedBasicChecks = 32),
            local = ChartLibraryLocalState(),
            busy = false,
        )

        assertFalse(ui.busy)
    }

    @Test
    fun failedValidationKeepsTheAssetAndMakesItsRecoveryNeedVisible() {
        val validation = ChartValidationSnapshot(
            mapOf(
                ASSET_A to ChartValidationJob(
                    ASSET_A,
                    ChartValidationJobKind.BASIC,
                    ChartValidationJobStatus.FAILED,
                    issue = ChartValidationIssue.PERMISSION_LOST,
                ),
            ),
        )
        val ui = ChartLibraryProjector.project(
            catalog = ChartCatalogSnapshot(sourceCount = 1, assetCount = 1),
            sources = listOf(source()),
            assets = listOf(asset()),
            validation = validation,
            storage = ChartLibraryStorageSnapshot.EMPTY,
            metrics = ChartLibraryRuntimeMetrics(),
            local = ChartLibraryLocalState(),
        )
        val row = (ui.page as ChartLibraryPageUi.Overview).assets.single()

        assertTrue(row.needsAttention)
        assertEquals(ASSET_A, row.id)
        assertEquals(ChartValidationIssue.PERMISSION_LOST, row.validationJob?.issue)
    }

    private fun project(
        sources: List<ChartLibrarySource>,
        assets: List<ChartAsset>,
        local: ChartLibraryLocalState = ChartLibraryLocalState(),
        storage: ChartLibraryStorageSnapshot = ChartLibraryStorageSnapshot.EMPTY,
    ) = ChartLibraryProjector.project(
        catalog = ChartCatalogSnapshot(sourceCount = sources.size, assetCount = assets.size),
        sources = sources,
        assets = assets,
        validation = ChartValidationSnapshot(),
        storage = storage,
        metrics = ChartLibraryRuntimeMetrics(),
        local = local,
    )

    private fun source(
        id: String = SOURCE_A_VALUE,
        locator: String = "content://com.android.externalstorage.documents/tree/primary%3ACharts",
    ) = ChartLibrarySource(
        id = ChartSourceId(id),
        kind = ChartLibrarySourceKind.TREE,
        locator = ChartOpaqueLocator(locator),
        displayName = "MarineCharts",
        grantState = ChartGrantState.GRANTED,
        scan = ChartSourceScanState(generation = 1L, status = ChartScanStatus.COMPLETE, lastSuccessfulGeneration = 1L),
    )

    private fun asset(
        id: ChartAssetId = ASSET_A,
        title: String = "harbour.mbtiles",
        memberships: Set<ChartSourceId> = setOf(SOURCE_A),
        size: Long? = null,
        access: ChartAssetAccessState = ChartAssetAccessState.UNCHECKED,
        validation: ChartAssetValidationState = ChartAssetValidationState.DISCOVERED,
    ) = ChartAsset(
        id = id,
        documentIdentity = ChartDocumentIdentity("com.android.externalstorage.documents", title),
        locator = ChartOpaqueLocator("content://com.android.externalstorage.documents/document/$title"),
        memberships = memberships,
        displayPath = "charts/$title",
        revision = ChartContentRevision(title, size, null),
        facts = ChartAssetFacts(format = ChartAssetFormat.RASTER_MBTILES, sizeBytes = size),
        access = access,
        validation = validation,
    )

    private companion object {
        const val SOURCE_A_VALUE = "00000000-0000-0000-0000-000000000001"
        const val SOURCE_B_VALUE = "00000000-0000-0000-0000-000000000002"
        val SOURCE_A = ChartSourceId(SOURCE_A_VALUE)
        val SOURCE_B = ChartSourceId(SOURCE_B_VALUE)
        val ASSET_A = ChartAssetId("10000000-0000-0000-0000-000000000001")
        val ASSET_B = ChartAssetId("10000000-0000-0000-0000-000000000002")
    }
}
