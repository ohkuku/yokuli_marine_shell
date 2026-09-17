package com.yokuli.shell.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellWindowMetricsTest {
    @Test
    fun roundedStatusAvoidsCornersLaterallyWithoutSacrificingAFullTopBand() {
        val metrics = roundedSquare(size = 320, radius = 36)

        val bands = ShellSafeBands.resolve(metrics)

        assertEquals(0, bands.status.top)
        assertTrue(bands.status.left >= 36)
        assertTrue(bands.status.right >= 36)
    }

    @Test
    fun roundedNavBarAvoidsBottomCornersLaterallyAndUsesOnlyRealBottomInsets() {
        val metrics = roundedSquare(size = 360, radius = 44).copy(
            systemGestureInsets = ShellInsets(left = 51, right = 48, bottom = 20),
        )

        val bands = ShellSafeBands.resolve(metrics)

        assertEquals(51, bands.navigation.left)
        assertEquals(48, bands.navigation.right)
        // 隐藏系统导航后，手势热区不应变成额外底部黑边。
        assertEquals(0, bands.navigation.bottom)
    }

    @Test
    fun imeIsASeparateLiftRatherThanInflatingNavigationSafePadding() {
        val metrics = ShellWindowMetrics(
            widthPx = 360,
            heightPx = 640,
            density = 3f,
            safeInsets = ShellInsets(bottom = 24),
            imeInsets = ShellInsets(bottom = 280),
            systemGestureInsets = ShellInsets(bottom = 32),
        )

        val bands = ShellSafeBands.resolve(metrics)

        assertEquals(24, bands.navigation.bottom)
        assertEquals(280, bands.imeLiftPx)
    }

    @Test
    fun edgeCutoutExpandsOnlyTheIntersectingSafeSide() {
        val metrics = ShellWindowMetrics(
            widthPx = 360,
            heightPx = 640,
            density = 3f,
            displayCutoutRects = listOf(ShellRect(0, 0, 72, 32)),
        )

        val bands = ShellSafeBands.resolve(metrics)

        assertEquals(72, bands.status.left)
        assertEquals(0, bands.status.right)
    }

    @Test
    fun centralFloatingCameraSplitsTheActualThirtyDpStatusBand() {
        val metrics = ShellWindowMetrics(1080, 2400, 3f,
            displayCutoutRects = listOf(ShellRect(480, 24, 600, 72)))

        assertEquals(listOf(ShellHorizontalSegment(24, 468), ShellHorizontalSegment(612, 1056)),
            ShellSafeBands.statusSegments(metrics))
        assertEquals(0, ShellSafeBands.resolve(metrics).status.top)
    }

    @Test
    fun overlappingCutoutsMergeAndOnlyIntersectingVerticalBoundsExcludeSpace() {
        val metrics = ShellWindowMetrics(360, 640, 1f,
            displayCutoutRects = listOf(ShellRect(130, 0, 180, 22), ShellRect(170, 8, 230, 26),
                ShellRect(20, 30, 100, 80)))

        assertEquals(listOf(ShellHorizontalSegment(8, 126), ShellHorizontalSegment(234, 352)),
            ShellSafeBands.statusSegments(metrics))
    }

    @Test
    fun cornerAndCutoutExclusionsAreCombinedWithoutMovingTheStatusDown() {
        val metrics = roundedSquare(360, 36).copy(displayCutoutRects = listOf(ShellRect(150, 0, 210, 25)))

        assertEquals(listOf(ShellHorizontalSegment(44, 146), ShellHorizontalSegment(214, 316)),
            ShellSafeBands.statusSegments(metrics))
        assertEquals(0, ShellSafeBands.resolve(metrics).status.top)
    }

    @Test
    fun fullyBlockedOrZeroWidthStatusHasNoDrawableSegment() {
        assertTrue(ShellSafeBands.statusSegments(ShellWindowMetrics(100, 200, 1f,
            displayCutoutRects = listOf(ShellRect(0, 0, 100, 30)))).isEmpty())
        assertTrue(ShellSafeBands.statusSegments(ShellWindowMetrics(0, 200, 1f)).isEmpty())
    }

    private fun roundedSquare(size: Int, radius: Int) = ShellWindowMetrics(
        widthPx = size,
        heightPx = size,
        density = 1f,
        roundedCorners = ShellRoundedCorners(
            topLeft = ShellRoundedCorner(radius, radius, radius),
            topRight = ShellRoundedCorner(size - radius, radius, radius),
            bottomLeft = ShellRoundedCorner(radius, size - radius, radius),
            bottomRight = ShellRoundedCorner(size - radius, size - radius, radius),
        ),
    )
}
