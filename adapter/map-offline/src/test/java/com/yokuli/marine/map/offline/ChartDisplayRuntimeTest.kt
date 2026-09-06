package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.SlippyTileKey
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartContentRevision
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayCoverageStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayLayer
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPlan
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplaySelection
import com.yokuli.marine.map.domain.chartlibrary.ChartOpaqueLocator
import com.yokuli.marine.map.domain.chartlibrary.ChartOpenResult
import com.yokuli.marine.map.domain.chartlibrary.ChartReadRequest
import com.yokuli.marine.map.domain.chartlibrary.ChartReadSession
import com.yokuli.marine.map.domain.chartlibrary.ChartReadStatistics
import com.yokuli.marine.map.domain.chartlibrary.ChartResourceAccessPort
import com.yokuli.marine.map.domain.chartlibrary.ChartStoredTile
import com.yokuli.marine.map.domain.chartlibrary.ChartTileKey
import com.yokuli.marine.map.domain.chartlibrary.ChartTilePayload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDisplayRuntimeTest {
    @Test
    fun `coverage unions real base keys and never uses overlay keys to fill a hole`() = runBlocking {
        val required = setOf(SlippyTileKey(3, 1, 2), SlippyTileKey(3, 1, 3))
        val available = mapOf(
            BASE_A to setOf(SlippyTileKey(3, 1, 2)),
            BASE_B to emptySet(),
            OVERLAY to setOf(SlippyTileKey(3, 1, 3)),
        )
        val index = ChartDisplayCoverageIndex(ChartResourceAccessPort { request ->
            ChartOpenResult.Opened(FakeSession(request, available.getValue(request.assetId)))
        })

        val currentPlan = plan()
        val result = index.evaluate(currentPlan, 3, required, expectedFingerprint = currentPlan.fingerprint)

        assertEquals(ChartDisplayCoverageStatus.PARTIAL, result.status)
        assertEquals(setOf(SlippyTileKey(3, 1, 3)), result.missingKeys)
        assertEquals(setOf(BASE_A, BASE_B), result.checkedBaseAssetIds)
    }

    @Test
    fun `coverage result is stale before any source is opened when plan fingerprint changed`() = runBlocking {
        var opens = 0
        val index = ChartDisplayCoverageIndex(ChartResourceAccessPort {
            opens += 1
            ChartOpenResult.Opened(FakeSession(it, emptySet()))
        })

        val result = index.evaluate(
            plan(),
            3,
            setOf(SlippyTileKey(3, 1, 2)),
            expectedFingerprint = "f".repeat(64),
        )

        assertEquals(ChartDisplayCoverageStatus.STALE, result.status)
        assertEquals(0, opens)
    }

    @Test
    fun `preparer opens only planned revisions honors each tile size and releases every session`() = runBlocking {
        val sessions = mutableListOf<FakeSession>()
        val access = ChartResourceAccessPort { request ->
            ChartOpenResult.Opened(FakeSession(request, emptySet()).also(sessions::add))
        }
        ChartLoopbackTileGateway().use { gateway ->
            val prepared = ChartDisplayPreparer(access, gateway).prepare(plan())

            assertEquals(listOf(256, 512, 256), prepared.layers.map { it.registration.tileSize })
            assertEquals(plan().fingerprint, prepared.planFingerprint)
            assertEquals(0, prepared.rejectedLayerCount)
            assertTrue(sessions.none { it.closed })
            prepared.close()
            assertTrue(sessions.all { it.closed })
        }
    }

    @Test
    fun `one unreadable planned layer is explicit and does not prevent other layers rendering`() = runBlocking {
        val access = ChartResourceAccessPort { request ->
            if (request.assetId == BASE_B) ChartOpenResult.Rejected(
                com.yokuli.marine.map.domain.chartlibrary.ChartReadFailure.REVISION_CHANGED,
                "changed",
            ) else ChartOpenResult.Opened(FakeSession(request, emptySet()))
        }
        ChartLoopbackTileGateway().use { gateway ->
            ChartDisplayPreparer(access, gateway).prepare(plan()).use { prepared ->
                assertEquals(listOf(BASE_A, OVERLAY), prepared.layers.map { it.planLayer.assetId })
                assertEquals(1, prepared.rejectedLayerCount)
            }
        }
    }

    private fun plan() = ChartDisplayPlan(
        generation = 4L,
        catalogRevision = 8L,
        fingerprint = "a".repeat(64),
        selection = ChartDisplaySelection.SourceSet(setOf(com.yokuli.marine.map.domain.chartlibrary.ChartSourceId("source"))),
        layers = listOf(
            layer(BASE_A, ChartAssetRole.BASE, 256, MapTileScheme.MBTILES_TMS),
            layer(BASE_B, ChartAssetRole.BASE, 512, MapTileScheme.XYZ),
            layer(OVERLAY, ChartAssetRole.OVERLAY, 256, MapTileScheme.MBTILES_TMS),
        ),
    )

    private fun layer(id: ChartAssetId, role: ChartAssetRole, tileSize: Int, scheme: MapTileScheme) = ChartDisplayLayer(
        assetId = id,
        displayName = id.value,
        request = ChartReadRequest(
            id,
            ChartOpaqueLocator("content://fixture/${id.value}"),
            ChartContentRevision(id.value, 100L, 1L),
            sourceGeneration = 1L,
        ),
        role = role,
        priority = 0,
        opacity = if (role == ChartAssetRole.BASE) 1f else .7f,
        tileSize = tileSize,
        tileScheme = scheme,
        minZoom = 0,
        maxZoom = 18,
        bounds = GeoBounds(-90.0, -180.0, 90.0, 180.0),
        attribution = "fixture",
    )

    private class FakeSession(
        override val request: ChartReadRequest,
        private val available: Set<SlippyTileKey>,
    ) : ChartReadSession {
        var closed = false
        override val sourceSizeBytes: Long = 100L
        override fun readMetadata(limit: Int) = emptyMap<String, String>()
        override fun readTile(key: ChartTileKey, scheme: MapTileScheme): ChartTilePayload? = null
        override fun hasTile(key: ChartTileKey, scheme: MapTileScheme) =
            SlippyTileKey(key.zoom, key.column.toInt(), key.row.toInt()) in available
        override fun readStoredTiles(offset: Long, limit: Int) = emptyList<ChartStoredTile>()
        override fun readSourceRange(offset: Long, maxByteCount: Int) = byteArrayOf()
        override fun statistics() = ChartReadStatistics()
        override fun close() { closed = true }
    }

    private companion object {
        val BASE_A = ChartAssetId("base-a")
        val BASE_B = ChartAssetId("base-b")
        val OVERLAY = ChartAssetId("overlay")
    }
}
