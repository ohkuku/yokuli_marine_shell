package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatelineGeometryTest {
    @Test
    fun `short antimeridian segment is densified and split instead of crossing the world`() {
        val geometry = Wgs84Polyline.build(
            listOf(GeoPoint(-36.0, 179.8), GeoPoint(-36.0, -179.7)),
            maxSegmentMeters = 10_000.0,
        )

        assertEquals(2, geometry.parts.size)
        geometry.parts.forEach { part ->
            part.zipWithNext().forEach { (left, right) ->
                assertTrue(kotlin.math.abs(right.longitude - left.longitude) <= 180.0)
            }
        }
        assertTrue(geometry.bounds.crossesAntimeridian)
        assertEquals(179.8, geometry.bounds.west, 1e-9)
        assertEquals(-179.7, geometry.bounds.east, 1e-9)
    }

    @Test
    fun `renderer geometry does not mutate legal high latitude source coordinates`() {
        val source = listOf(GeoPoint(88.0, 10.0), GeoPoint(89.0, 20.0))
        val geometry = Wgs84Polyline.build(source, maxSegmentMeters = 25_000.0)

        assertEquals(source.first(), geometry.parts.first().first())
        assertEquals(source.last(), geometry.parts.last().last())
        assertEquals(89.0, geometry.bounds.north, 0.0)
    }
}
