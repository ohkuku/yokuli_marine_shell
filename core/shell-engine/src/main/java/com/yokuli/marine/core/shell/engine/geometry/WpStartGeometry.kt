package com.yokuli.marine.core.shell.engine.geometry

import kotlin.math.abs
import kotlin.math.roundToInt

data class WpStartGeometry(
    val availableWidthPx: Int,
    val outerStartPx: Int,
    val outerEndPx: Int,
    val outerTopPx: Int,
    val seamPx: Int,
    val cellPx: Int,
    val columns: Int,
)

object WpStartGeometryCalculator {
    private const val OuterRatio = 0.05f
    private const val SeamRatio = 0.023f

    fun calculate(availableWidthPx: Int, columns: Int = 4): WpStartGeometry {
        require(availableWidthPx > 0)
        require(columns > 0)
        val outer = (availableWidthPx * OuterRatio).roundToInt().coerceAtLeast(1)
        val idealSeam = (availableWidthPx * SeamRatio).roundToInt().coerceAtLeast(1)
        val seamRange = ((availableWidthPx * .02f).roundToInt().coerceAtLeast(1))..
            ((availableWidthPx * .026f).roundToInt().coerceAtLeast(1))
        val seam = seamRange.minByOrNull { candidate ->
            val remaining = availableWidthPx - outer * 2 - candidate * (columns - 1)
            val remainderPenalty = abs(remaining % columns) * 100
            remainderPenalty + abs(candidate - idealSeam)
        } ?: idealSeam
        val cell = (availableWidthPx - outer * 2 - seam * (columns - 1)) / columns
        require(cell > 0)
        val used = outer * 2 + seam * (columns - 1) + cell * columns
        return WpStartGeometry(
            availableWidthPx = availableWidthPx,
            outerStartPx = outer,
            outerEndPx = outer + (availableWidthPx - used),
            outerTopPx = outer,
            seamPx = seam,
            cellPx = cell,
            columns = columns,
        )
    }
}
