package com.yokuli.shell.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellWindowMetricsTest {
    @Test
    fun roundedStatusAvoidsTopCorners() {
        val metrics = roundedSquare(size = 320, radius = 36)

        val bands = ShellSafeBands.resolve(metrics)

        assertTrue(bands.status.top >= 36)
        assertTrue(bands.status.left >= 36)
        assertTrue(bands.status.right >= 36)
    }

    @Test
    fun roundedNavBarAvoidsBottomCornersAndSystemGestures() {
        val metrics = roundedSquare(size = 360, radius = 44).copy(
            systemGestureInsets = ShellInsets(left = 51, right = 48, bottom = 20),
        )

        val bands = ShellSafeBands.resolve(metrics)

        assertEquals(51, bands.navigation.left)
        assertEquals(48, bands.navigation.right)
        assertEquals(44, bands.navigation.bottom)
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

        assertEquals(32, bands.navigation.bottom)
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
