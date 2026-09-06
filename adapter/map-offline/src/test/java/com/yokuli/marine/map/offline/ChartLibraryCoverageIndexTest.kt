package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.SlippyTileKey
import com.yokuli.marine.map.domain.chartlibrary.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartLibraryCoverageIndexTest {
    @Test fun coverageAndRendererUseSameTmsAndXyzSessionQueries() = runBlocking {
        listOf(MapTileScheme.MBTILES_TMS, MapTileScheme.XYZ).forEach { scheme ->
            val queriedRows = mutableListOf<Long>()
            val session = object : ChartReadSession {
                override val request = request()
                override val sourceSizeBytes = 0L
                override fun readMetadata(limit: Int) = emptyMap<String, String>()
                override fun readTile(key: ChartTileKey, scheme: MapTileScheme) = null
                override fun hasTile(key: ChartTileKey, scheme: MapTileScheme): Boolean {
                    queriedRows += key.storageRow(scheme)
                    return key.row == 2L
                }
                override fun readStoredTiles(offset: Long, limit: Int) = emptyList<ChartStoredTile>()
                override fun readSourceRange(offset: Long, maxByteCount: Int) = byteArrayOf()
                override fun statistics() = ChartReadStatistics()
                override fun close() = Unit
            }
            val required = setOf(SlippyTileKey(3, 1, 2), SlippyTileKey(3, 1, 3))
            val result = ChartLibraryCoverageIndex(ChartResourceAccessPort { ChartOpenResult.Opened(session) })
                .availableKeys(request(), scheme, required)
            assertEquals(setOf(SlippyTileKey(3, 1, 2)), result)
            assertEquals(required.map { ChartTileKey(it.zoom, it.x.toLong(), it.y.toLong()).storageRow(scheme) }, queriedRows)
        }
    }

    private fun request() = ChartReadRequest(
        ChartAssetId("coverage"), ChartOpaqueLocator("content://coverage"),
        ChartContentRevision("coverage", 0, 0), 1,
    )
}
