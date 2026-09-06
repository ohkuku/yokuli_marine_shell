package com.yokuli.marine.adapter.chart.google

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartContentRevision
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayLayer
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPlan
import com.yokuli.marine.map.domain.chartlibrary.ChartOpenResult
import com.yokuli.marine.map.domain.chartlibrary.ChartOpaqueLocator
import com.yokuli.marine.map.domain.chartlibrary.ChartReadRequest
import com.yokuli.marine.map.domain.chartlibrary.ChartReadSession
import com.yokuli.marine.map.domain.chartlibrary.ChartReadStatistics
import com.yokuli.marine.map.domain.chartlibrary.ChartResourceAccessPort
import com.yokuli.marine.map.domain.chartlibrary.ChartStoredTile
import com.yokuli.marine.map.domain.chartlibrary.ChartTileKey
import com.yokuli.marine.map.domain.chartlibrary.ChartTilePayload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleChartOverlaySessionTest {
    @Test
    fun `prepared overlays retain order opacity and one revision-pinned session per layer`() = runTest {
        val sessions = mutableListOf<FakeSession>()
        val plan = plan(layer("base", ChartAssetRole.BASE, 1f), layer("harbour", ChartAssetRole.OVERLAY, .4f))
        val prepared = GoogleChartOverlayPreparer(ChartResourceAccessPort { request ->
            ChartOpenResult.Opened(FakeSession(request).also(sessions::add))
        }).prepare(plan)

        assertEquals(listOf("base", "harbour"), prepared.layers.map { it.planLayer.assetId.value })
        assertEquals(listOf(1f, .4f), prepared.layers.map { it.planLayer.opacity })
        assertEquals(0, prepared.rejectedLayerCount)

        prepared.close()
        assertTrue(sessions.all { it.closed })
    }

    @Test
    fun `tile reads preserve scheme reject invalid coordinates and become unavailable after close`() {
        val session = FakeSession(layer("tms", ChartAssetRole.BASE, 1f).request)
        val layer = layer("tms", ChartAssetRole.BASE, 1f, scheme = MapTileScheme.MBTILES_TMS)
        val reader = GoogleChartTileReader(layer, session)

        assertEquals(3, reader.read(2, 3, 4)?.bytes?.single()?.toInt())
        assertEquals(MapTileScheme.MBTILES_TMS, session.lastScheme)
        assertNull(reader.read(-1, 0, 4))
        assertNull(reader.read(0, 0, 25))

        reader.close()
        assertNull(reader.read(2, 3, 4))
        assertTrue(session.closed)
    }

    @Test
    fun `one rejected source degrades the set without discarding readable overlays`() = runTest {
        val plan = plan(layer("good", ChartAssetRole.BASE, 1f), layer("gone", ChartAssetRole.OVERLAY, .8f))
        val prepared = GoogleChartOverlayPreparer(ChartResourceAccessPort { request ->
            if (request.assetId.value == "gone") {
                ChartOpenResult.Rejected(com.yokuli.marine.map.domain.chartlibrary.ChartReadFailure.CANNOT_OPEN, "gone")
            } else {
                ChartOpenResult.Opened(FakeSession(request))
            }
        }).prepare(plan)

        assertEquals(listOf("good"), prepared.layers.map { it.planLayer.assetId.value })
        assertEquals(1, prepared.rejectedLayerCount)
        prepared.close()
    }

    private fun plan(vararg layers: ChartDisplayLayer) = ChartDisplayPlan(
        generation = 1,
        catalogRevision = 1,
        fingerprint = "a".repeat(64),
        layers = layers.toList(),
    )

    private fun layer(
        id: String,
        role: ChartAssetRole,
        opacity: Float,
        scheme: MapTileScheme = MapTileScheme.XYZ,
    ) = ChartDisplayLayer(
        assetId = ChartAssetId(id),
        displayName = id,
        request = ChartReadRequest(
            ChartAssetId(id),
            ChartOpaqueLocator("content://charts/$id"),
            ChartContentRevision(id, 1, 1),
            sourceGeneration = 1,
        ),
        role = role,
        priority = 0,
        opacity = opacity,
        tileSize = 256,
        tileScheme = scheme,
        minZoom = 0,
        maxZoom = 20,
        bounds = GeoBounds(-10.0, -10.0, 10.0, 10.0),
        attribution = null,
    )

    private class FakeSession(override val request: ChartReadRequest) : ChartReadSession {
        override val sourceSizeBytes = 1L
        var closed = false
        var lastScheme: MapTileScheme? = null
        override fun readMetadata(limit: Int) = emptyMap<String, String>()
        override fun readTile(key: ChartTileKey, scheme: MapTileScheme): ChartTilePayload {
            lastScheme = scheme
            return ChartTilePayload(byteArrayOf(key.row.toByte()), "image/png", 256, 256)
        }
        override fun hasTile(key: ChartTileKey, scheme: MapTileScheme) = true
        override fun readStoredTiles(offset: Long, limit: Int) = emptyList<ChartStoredTile>()
        override fun readSourceRange(offset: Long, maxByteCount: Int) = byteArrayOf()
        override fun statistics() = ChartReadStatistics()
        override fun close() { closed = true }
    }
}
