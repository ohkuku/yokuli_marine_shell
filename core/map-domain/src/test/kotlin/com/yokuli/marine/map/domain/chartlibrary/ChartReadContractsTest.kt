package com.yokuli.marine.map.domain.chartlibrary

import com.yokuli.marine.map.domain.MapTileScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChartReadContractsTest {
    @Test
    fun tmsAndXyzUseOneBoundedCoordinateMapping() {
        val xyz = ChartTileKey(3, 4, 2)
        assertEquals(5L, xyz.storageRow(MapTileScheme.MBTILES_TMS))
        assertEquals(2L, xyz.storageRow(MapTileScheme.XYZ))
        assertThrows(IllegalArgumentException::class.java) { ChartTileKey(3, 8, 0) }
        assertThrows(IllegalArgumentException::class.java) { ChartTileKey(25, 0, 0) }
    }

    @Test
    fun revisionDoesNotPretendToBeAContentHash() {
        val revision = ChartContentRevision(
            identity = "provider-document-42",
            observedSizeBytes = 2048,
            observedModifiedAtMillis = null,
            providerRevisionHint = "opaque-v7",
        )
        assertEquals(null, revision.contentSha256)
        assertThrows(IllegalArgumentException::class.java) {
            revision.copy(contentSha256 = "not-a-real-sha")
        }
    }

    @Test
    fun opaqueLocatorAndStableIdsAreBounded() {
        ChartSourceId("5a9198fb-f8a5-45a0-a49f-9900daf83c32")
        ChartAssetId("legacy.chart_1")
        ChartOpaqueLocator("content://provider/document/opaque")
        assertThrows(IllegalArgumentException::class.java) { ChartAssetId("path/to/chart") }
        assertThrows(IllegalArgumentException::class.java) { ChartOpaqueLocator("") }
    }
}
