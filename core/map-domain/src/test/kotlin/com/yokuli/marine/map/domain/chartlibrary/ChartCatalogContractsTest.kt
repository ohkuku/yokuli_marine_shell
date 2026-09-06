package com.yokuli.marine.map.domain.chartlibrary

import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class ChartCatalogContractsTest {
    @Test fun sourceAndAssetIdsHaveDifferentSemanticsFromPublisherEnums() {
        val id = ChartSourceId(UUID.randomUUID().toString())
        val source = ChartLibrarySource(
            id,
            ChartLibrarySourceKind.TREE,
            ChartOpaqueLocator("content://provider/tree/root"),
            "Cruising charts",
            grantState = ChartGrantState.GRANTED,
        )
        assertNull(source.scan.lastSuccessfulGeneration)
        assertThrows(IllegalArgumentException::class.java) {
            ChartLibrarySource(ChartSourceId("USER_IMPORT"), ChartLibrarySourceKind.TREE, source.locator, "bad")
        }
    }

    @Test fun fullVerificationCannotBeClaimedWithoutAnActuallyComputedHash() {
        val sourceId = ChartSourceId(UUID.randomUUID().toString())
        assertThrows(IllegalArgumentException::class.java) {
            ChartAsset(
                ChartAssetId(UUID.randomUUID().toString()),
                ChartDocumentIdentity("provider", "document"),
                ChartOpaqueLocator("content://provider/document/document"),
                setOf(sourceId),
                "document.mbtiles",
                ChartContentRevision("provider:document", null, null),
                validation = ChartAssetValidationState.FULL_VERIFIED,
            )
        }
    }
}
