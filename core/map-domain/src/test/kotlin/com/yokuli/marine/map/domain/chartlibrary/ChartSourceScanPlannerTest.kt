package com.yokuli.marine.map.domain.chartlibrary

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ChartSourceScanPlannerTest {
    private val sourceId = ChartSourceId(UUID.randomUUID().toString())
    private val source = ChartLibrarySource(
        sourceId, ChartLibrarySourceKind.TREE, ChartOpaqueLocator("content://provider/tree/root"), "Charts",
        grantState = ChartGrantState.GRANTED,
    )
    private var next = 0
    private val planner = ChartSourceScanPlanner { ChartAssetId(UUID.nameUUIDFromBytes("id-${next++}".encodeToByteArray()).toString()) }

    @Test fun onlySuccessfulCompleteEnumerationCanMarkUnseenAssetsMissing() {
        val existing = asset("old")
        val partial = planner.plan(
            source, 1,
            ChartEnumerationResult.Partial(emptyList(), listOf(ChartEnumerationIssue("bad", ChartEnumerationIssueKind.QUERY_FAILED))),
            listOf(existing),
        )
        assertTrue(partial.missingAssetIds.isEmpty())
        assertEquals(ChartScanStatus.PARTIAL, partial.source.scan.status)
        val complete = planner.plan(source, 1, ChartEnumerationResult.Complete(emptyList()), listOf(existing))
        assertEquals(setOf(existing.id), complete.missingAssetIds)
        assertEquals(1L, complete.source.scan.lastSuccessfulGeneration)
    }

    @Test fun stableDocumentRenamePreservesIdentityPreferencesAndValidation() {
        val prior = asset("same").copy(priority = 73, role = ChartAssetRole.OVERLAY, validation = ChartAssetValidationState.BASIC_READABLE)
        val discovered = document("same", "renamed.mbtiles")
        val plan = planner.plan(source, 1, ChartEnumerationResult.Complete(listOf(discovered)), listOf(prior))
        assertEquals(prior.id, plan.assetsToPut.single().id)
        assertEquals(73, plan.assetsToPut.single().priority)
        assertEquals(ChartAssetRole.OVERLAY, plan.assetsToPut.single().role)
        assertEquals(ChartAssetValidationState.BASIC_READABLE, plan.assetsToPut.single().validation)
    }

    @Test fun changedRevisionIsVisibleAndCannotRetainVerifiedState() {
        val prior = asset("changed").copy(
            revision = document("changed", "chart.mbtiles", 10).revision.copy(contentSha256 = "a".repeat(64)),
            access = ChartAssetAccessState.READABLE,
            validation = ChartAssetValidationState.FULL_VERIFIED,
        )
        val plan = planner.plan(source, 1, ChartEnumerationResult.Complete(listOf(document("changed", "chart.mbtiles", 11))), listOf(prior))
        assertEquals(ChartAssetAccessState.CHANGED, plan.assetsToPut.single().access)
        assertEquals(ChartAssetValidationState.DISCOVERED, plan.assetsToPut.single().validation)
        assertNull(plan.assetsToPut.single().revision.contentSha256)
    }

    @Test fun permissionLossIsNotMisreportedAsMissing() {
        val existing = asset("offline").copy(access = ChartAssetAccessState.READABLE)
        val failed = planner.plan(
            source, 1,
            ChartEnumerationResult.Failed(ChartEnumerationIssue(null, ChartEnumerationIssueKind.PERMISSION_LOST)),
            listOf(existing),
        )
        assertTrue(failed.missingAssetIds.isEmpty())
        assertEquals(ChartGrantState.REVOKED, failed.source.grantState)
        assertEquals(ChartAssetAccessState.PERMISSION_LOST, failed.assetsToPut.single().access)
    }

    private fun document(id: String, path: String, modified: Long = 1) = ChartDiscoveredDocument(
        ChartDocumentIdentity("provider", id), ChartOpaqueLocator("content://provider/document/$id"),
        path, null, modified,
    )
    private fun asset(id: String) = ChartAsset(
        ChartAssetId(UUID.nameUUIDFromBytes(id.encodeToByteArray()).toString()),
        ChartDocumentIdentity("provider", id), ChartOpaqueLocator("content://provider/document/$id"),
        setOf(sourceId), "$id.mbtiles", ChartContentRevision("provider:$id", null, 1),
    )
}
