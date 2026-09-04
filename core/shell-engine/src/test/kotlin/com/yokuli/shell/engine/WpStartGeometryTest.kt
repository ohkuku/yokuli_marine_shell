package com.yokuli.shell.engine

import com.yokuli.shell.engine.geometry.ReferenceEvidenceState
import com.yokuli.shell.engine.geometry.StartViewport
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.geometry.WpStartGeometryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WpStartGeometryTest {
    @Test
    fun approved480ReferenceResolvesToMeasuredTileGeometry() {
        val geometry = WpStartGeometryCalculator.calculate(viewport(width = 480, height = 800))

        assertEquals(WpReferenceProfiles.PHONE_PORTRAIT_4COL.id, geometry.profileId)
        assertEquals(24, geometry.outerInsetsPx.left)
        assertEquals(24, geometry.outerInsetsPx.right)
        assertEquals(57, geometry.outerInsetsPx.top)
        assertEquals(12, geometry.seamPx)
        assertEquals(99, geometry.smallCellPx)
        assertEquals(32, geometry.statusStripHeightPx)
        assertEquals(210, geometry.tileWidthPx(2))
        assertEquals(432, geometry.tileWidthPx(4))
        assertEquals(210, geometry.tileHeightPx(2))
    }

    @Test
    fun square320And360BoundsAreIntegerSnappedAndConsumeTheWidth() {
        listOf(320, 360).forEach { width ->
            val geometry = WpStartGeometryCalculator.calculate(viewport(width, width))
            assertEquals(WpReferenceProfiles.SQUARE_4COL.id, geometry.profileId)
            assertEquals(
                width,
                geometry.outerInsetsPx.left + geometry.outerInsetsPx.right +
                    geometry.seamPx * (geometry.columns - 1) + geometry.smallCellPx * geometry.columns,
            )
            assertEquals(geometry.outerInsetsPx.left, geometry.contentBounds.left)
            assertEquals(width, geometry.contentBounds.right + geometry.outerInsetsPx.right)
            assertTrue(geometry.contentBounds.top >= geometry.statusStripHeightPx)
            assertTrue(geometry.contentBounds.bottom <= width)
        }
        assertEquals(66, WpStartGeometryCalculator.calculate(viewport(320, 320)).smallCellPx)
        assertEquals(74, WpStartGeometryCalculator.calculate(viewport(360, 360)).smallCellPx)
    }

    @Test
    fun fontScaleDoesNotChangeTileGeometry() {
        val normal = WpStartGeometryCalculator.calculate(viewport(480, 800, fontScale = 1f))
        val largeText = WpStartGeometryCalculator.calculate(viewport(480, 800, fontScale = 2f))

        assertEquals(normal, largeText)
    }

    @Test
    fun systemInsetsShiftAndClipContentWithoutChangingTileMetrics() {
        val base = WpStartGeometryCalculator.calculate(viewport(480, 800))
        val inset = WpStartGeometryCalculator.calculate(
            StartViewport(480, 800, density = 3f, topInsetPx = 20, bottomInsetPx = 40, fontScale = 1f),
        )

        assertEquals(base.smallCellPx, inset.smallCellPx)
        assertEquals(base.seamPx, inset.seamPx)
        assertEquals(base.contentBounds.top + 20, inset.contentBounds.top)
        assertEquals(760, inset.contentBounds.bottom)
    }

    @Test
    fun unobservedInteractionMeasurementsRemainUnknown() {
        val interaction = WpReferenceProfiles.PHONE_PORTRAIT_4COL.interaction
        assertEquals(ReferenceEvidenceState.NOT_OBSERVED, interaction.evidenceState)
        assertNull(interaction.measuredLongPressMillis)
        assertNull(interaction.measuredPressScale)
        assertNull(interaction.measuredFastFlingThresholdPxPerSecond)
        assertEquals(
            ReferenceEvidenceState.DERIVED_UNVERIFIED_HARDWARE,
            WpReferenceProfiles.SQUARE_4COL.evidenceState,
        )
    }

    private fun viewport(width: Int, height: Int, fontScale: Float = 1f) =
        StartViewport(width, height, density = 3f, topInsetPx = 0, bottomInsetPx = 0, fontScale = fontScale)
}
