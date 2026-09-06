package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartContentRevision
import com.yokuli.marine.map.domain.chartlibrary.ChartOpaqueLocator
import com.yokuli.marine.map.domain.chartlibrary.ChartReadRequest
import com.yokuli.marine.map.domain.chartlibrary.ChartReadSession
import com.yokuli.marine.map.domain.chartlibrary.ChartReadStatistics
import com.yokuli.marine.map.domain.chartlibrary.ChartTileKey
import com.yokuli.marine.map.domain.chartlibrary.ChartTilePayload
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartLoopbackTileGatewayTest {
    @Test fun servesOnlyRegisteredRevisionOverLoopbackWithNoStore() {
        val session = FakeReadSession()
        ChartLoopbackTileGateway().use { gateway ->
            assertTrue(gateway.localAddress.isLoopbackAddress)
            gateway.register(session, MapTileScheme.MBTILES_TMS, 256).use { registration ->
                val response = get(registration.tileUrlTemplate.expand(3, 4, 2))
                assertEquals(200, response.code)
                assertEquals("no-store", response.cacheControl)
                assertArrayEquals(session.tile.bytes, response.body)
                assertEquals(ChartTileKey(3, 4, 2), session.lastKey)

                val wrongRevision = registration.tileUrlTemplate
                    .replace(registration.revisionRouteKey, "old-revision")
                    .expand(3, 4, 2)
                assertEquals(404, get(wrongRevision).code)
            }
        }
    }

    @Test fun rejectsWrongTokenMethodsTraversalAndOversizedCoordinates() {
        ChartLoopbackTileGateway().use { gateway ->
            gateway.register(FakeReadSession(), MapTileScheme.XYZ, 256).use { registration ->
                val valid = registration.tileUrlTemplate.expand(0, 0, 0)
                val wrongToken = URL(valid.protocol, valid.host, valid.port, "/wrong/asset/revision/0/0/0")
                assertEquals(404, get(wrongToken).code)
                assertEquals(405, request(valid, "POST").code)
                assertEquals(400, get(URL(valid.protocol, valid.host, valid.port, "/../secret")).code)
                assertEquals(400, get(registration.tileUrlTemplate.expand(25, 0, 0)).code)
            }
        }
    }

    private fun get(url: URL): Response = request(url, "GET")

    private fun request(url: URL, method: String): Response {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 2_000
            readTimeout = 2_000
            useCaches = false
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() }
            ?: byteArrayOf()
        return Response(code, connection.getHeaderField("Cache-Control"), body)
    }

    private data class Response(val code: Int, val cacheControl: String?, val body: ByteArray)

    private class FakeReadSession : ChartReadSession {
        override val request = ChartReadRequest(
            ChartAssetId("asset-loopback"),
            ChartOpaqueLocator("content://fixture/chart"),
            ChartContentRevision("doc-1", 100, 1),
            sourceGeneration = 3,
        )
        val tile = ChartTilePayload(byteArrayOf(1, 2, 3), "image/png", 256, 256)
        var lastKey: ChartTileKey? = null
        override fun readMetadata(limit: Int) = emptyMap<String, String>()
        override fun readTile(key: ChartTileKey, scheme: MapTileScheme): ChartTilePayload {
            lastKey = key
            return tile
        }
        override fun hasTile(key: ChartTileKey, scheme: MapTileScheme) = true
        override fun statistics() = ChartReadStatistics()
        override fun close() = Unit
    }
}

private fun String.expand(z: Int, x: Long, y: Long): URL = URL(
    replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString()),
)
