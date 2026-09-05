package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.ImportedTrack
import com.yokuli.marine.map.domain.ImportedTrackPoint
import com.yokuli.marine.map.domain.ImportedTrackSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedTrackRenderingTest {
    @Test
    fun `each GPX segment becomes a separate feature and never receives a bridge line`() {
        val track = ImportedTrack(
            id = "track-a",
            name = "split",
            segments = listOf(
                segment(GeoPoint(-36.8, 174.7), GeoPoint(-36.9, 174.8)),
                segment(GeoPoint(40.0, -20.0), GeoPoint(41.0, -21.0)),
            ),
            sourceDigest = "a".repeat(64),
            importedAtMillis = 1L,
        )

        val features = listOf(track).toDisplayFeatureCollection(10.0).features().orEmpty()

        assertEquals(2, features.size)
        assertEquals("track:track-a:segment:0", features[0].id())
        assertEquals("track:track-a:segment:1", features[1].id())
        assertFalse(features[0].toJson().contains("-20.0"))
        assertTrue(features[1].toJson().contains("-20.0"))
    }

    private fun segment(vararg points: GeoPoint) = ImportedTrackSegment(
        points.map { ImportedTrackPoint(it) },
    )
}
