package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateCodecTest {
    @Test
    fun `decimal degrees and degrees decimal minutes round trip without swapping axes`() {
        val decimal = CoordinateCodec.parse("-36.848500", "174.763300", CoordinateFormat.DECIMAL_DEGREES)
        val dmm = CoordinateCodec.parse("36 50.910 S", "174 45.798 E", CoordinateFormat.DEGREES_DECIMAL_MINUTES)

        assertEquals(GeoPoint(-36.8485, 174.7633), (decimal as CoordinateParseResult.Success).point)
        assertEquals(-36.8485, (dmm as CoordinateParseResult.Success).point.latitude, 1e-10)
        assertEquals(174.7633, dmm.point.longitude, 1e-10)

        CoordinateFormat.entries.forEach { format ->
            val formatted = CoordinateCodec.format(dmm.point, format)
            val reparsed = CoordinateCodec.parse(formatted.latitude, formatted.longitude, format)
            val point = (reparsed as CoordinateParseResult.Success).point
            assertEquals(dmm.point.latitude, point.latitude, 1e-6)
            assertEquals(dmm.point.longitude, point.longitude, 1e-6)
        }
    }

    @Test
    fun `minutes range sign conflicts non finite values and axis ranges are typed failures`() {
        assertFailure("36 60.0 S", "174 45 E", CoordinateFormat.DEGREES_DECIMAL_MINUTES, CoordinateError.MINUTES_OUT_OF_RANGE)
        assertFailure("-36 N", "174 E", CoordinateFormat.DECIMAL_DEGREES, CoordinateError.SIGN_HEMISPHERE_CONFLICT)
        assertFailure("36 S", "+174 W", CoordinateFormat.DECIMAL_DEGREES, CoordinateError.SIGN_HEMISPHERE_CONFLICT)
        assertFailure("NaN", "174", CoordinateFormat.DECIMAL_DEGREES, CoordinateError.NON_FINITE)
        assertFailure("91", "174", CoordinateFormat.DECIMAL_DEGREES, CoordinateError.OUT_OF_RANGE)
        assertFailure("36", "181", CoordinateFormat.DECIMAL_DEGREES, CoordinateError.OUT_OF_RANGE)
    }

    @Test
    fun `negative zero retains its hemisphere through formatting`() {
        val result = CoordinateCodec.parse("-0", "0", CoordinateFormat.DECIMAL_DEGREES)
        val point = (result as CoordinateParseResult.Success).point

        assertTrue(point.latitude.toRawBits() < 0L)
        assertTrue(CoordinateCodec.format(point, CoordinateFormat.DECIMAL_DEGREES).latitude.endsWith(" S"))
    }

    private fun assertFailure(
        latitude: String,
        longitude: String,
        format: CoordinateFormat,
        expected: CoordinateError,
    ) {
        val result = CoordinateCodec.parse(latitude, longitude, format)
        assertEquals(expected, (result as CoordinateParseResult.Failure).error)
    }
}
