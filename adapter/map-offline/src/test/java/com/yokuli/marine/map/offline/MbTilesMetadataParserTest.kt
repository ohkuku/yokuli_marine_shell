package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.ChartPackageImportException
import org.junit.Assert.assertEquals
import org.junit.Test

class MbTilesMetadataParserTest {
    @Test
    fun `accepts raster metadata and keeps optional legal suggestions empty`() {
        val value = MbTilesMetadataParser.parse(
            mapOf("bounds" to "170,-47,179,-34", "minzoom" to "3", "maxzoom" to "14", "format" to "png"),
        )
        assertEquals(170.0, value.coverage.west, 0.0)
        assertEquals("", value.license)
    }

    @Test(expected = ChartPackageImportException::class)
    fun `rejects vector packages rather than claiming raster support`() {
        MbTilesMetadataParser.parse(
            mapOf("bounds" to "170,-47,179,-34", "minzoom" to "3", "maxzoom" to "14", "format" to "pbf"),
        )
    }
}
