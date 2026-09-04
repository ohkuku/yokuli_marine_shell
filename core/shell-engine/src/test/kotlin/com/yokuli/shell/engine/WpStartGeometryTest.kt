package com.yokuli.shell.engine

import com.yokuli.shell.engine.geometry.WpStartGeometryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WpStartGeometryTest {
    @Test
    fun geometryIsPixelSnappedAndExactlyConsumesEverySupportedWidth() {
        listOf(320, 360, 412, 600, 840).forEach { width ->
            val geometry = WpStartGeometryCalculator.calculate(width)
            val used = geometry.outerStartPx + geometry.outerEndPx +
                geometry.seamPx * (geometry.columns - 1) + geometry.cellPx * geometry.columns
            assertEquals(width, used)
            assertTrue(geometry.outerStartPx.toFloat() / width in .04f..06f)
            assertTrue(geometry.seamPx.toFloat() / width in .02f..026f)
        }
    }
}
