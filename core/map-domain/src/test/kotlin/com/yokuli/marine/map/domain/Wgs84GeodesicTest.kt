package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Wgs84GeodesicTest {
    @Test
    fun `equator degree matches the independent WGS84 semi-major-axis reference`() {
        val result = Wgs84Geodesic.inverse(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))

        assertEquals(111_319.490_793, result.distanceMeters, 0.001)
        assertEquals(90.0, requireNotNull(result.initialBearingTrueDegrees), 1e-10)
        assertFalse(result.azimuthAmbiguous)
    }

    @Test
    fun `official GeographicLib difficult inverse sample remains finite`() {
        val result = Wgs84Geodesic.inverse(GeoPoint(-30.0, 0.0), GeoPoint(29.5, 179.5))

        assertEquals(19_937_782.280_350, result.distanceMeters, 0.001)
        assertEquals(154.378_182_742_78, requireNotNull(result.initialBearingTrueDegrees), 1e-9)
        assertFalse(result.azimuthAmbiguous)
    }

    @Test
    fun `coincident and multiply-directed antipodal cases never invent a steering bearing`() {
        val origin = GeoPoint(-36.8485, 174.7633)
        val same = Wgs84Geodesic.inverse(origin, origin)
        val antipodal = Wgs84Geodesic.inverse(GeoPoint(90.0, 0.0), GeoPoint(-90.0, 0.0))

        assertEquals(0.0, same.distanceMeters, 0.0)
        assertNull(same.initialBearingTrueDegrees)
        assertNull(antipodal.initialBearingTrueDegrees)
        assertTrue(antipodal.distanceMeters.isFinite())
        assertTrue(antipodal.azimuthAmbiguous)
    }

    @Test
    fun `short New Zealand and polar segments stay finite with normalized true bearings`() {
        val local = Wgs84Geodesic.inverse(
            GeoPoint(-36.8485, 174.7633),
            GeoPoint(-36.7867, 174.8600),
        )
        val polar = Wgs84Geodesic.inverse(GeoPoint(89.0, 0.0), GeoPoint(89.0, 90.0))

        assertTrue(local.distanceMeters in 10_000.0..12_000.0)
        assertTrue(requireNotNull(local.initialBearingTrueDegrees) in 0.0..<360.0)
        assertTrue(polar.distanceMeters.isFinite())
        assertTrue(requireNotNull(polar.initialBearingTrueDegrees) in 0.0..<360.0)
    }
}
