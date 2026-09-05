package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.ChartPackageVersionId
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.shell.contract.MarineTileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartTilePreviewCacheTest {
    @Test
    fun `late snapshot A cannot overwrite current snapshot B`() {
        val cache = ChartTilePreviewCache<String>(maximumEntries = 2)
        val a = key(cameraZoom = 10.0, routeRevision = 1)
        val b = key(cameraZoom = 11.0, routeRevision = 2)
        val generationA = cache.begin(a)
        val generationB = cache.begin(b)

        assertTrue(cache.complete(generationB, b, "B"))
        assertEquals(false, cache.complete(generationA, a, "A"))
        assertEquals("B", cache.current())
    }

    @Test
    fun `cache is bounded and exact key prevents stale source style route or size reuse`() {
        val cache = ChartTilePreviewCache<String>(maximumEntries = 2)
        val keys = (1L..3L).map { key(cameraZoom = 10.0 + it, routeRevision = it) }
        keys.forEachIndexed { index, item ->
            val generation = cache.begin(item)
            assertTrue(cache.complete(generation, item, "preview-$index"))
        }

        assertEquals(2, cache.size)
        assertNull(cache[keys.first()])
        assertEquals("preview-2", cache[keys.last()])
        assertFalseKeyReuse(cache, keys.last().copy(styleRevision = "new-style"))
        assertFalseKeyReuse(cache, keys.last().copy(sourceVersionId = ChartPackageVersionId("b".repeat(64))))
        assertFalseKeyReuse(cache, keys.last().copy(size = MarineTileSize.STANDARD_2X2))
    }

    private fun assertFalseKeyReuse(cache: ChartTilePreviewCache<String>, key: ChartTilePreviewKey) {
        assertNull(cache[key])
    }

    private fun key(cameraZoom: Double, routeRevision: Long) = ChartTilePreviewKey(
        camera = MapCamera(GeoPoint(-36.8, 174.7), cameraZoom),
        sourceVersionId = ChartPackageVersionId("a".repeat(64)),
        styleRevision = "chart-style-v1",
        routeRevision = routeRevision,
        size = MarineTileSize.WIDE_4X2,
    )
}
