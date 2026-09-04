package com.yokuli.shell.engine.interaction

import com.yokuli.shell.engine.layout.GridCell
import kotlin.math.abs

/**
 * Operational tuning only. Stage 2.5 did not observe edit gestures, so these values are
 * DERIVED_UNVERIFIED and must not be described as measured WP8 behavior.
 */
const val DERIVED_UNVERIFIED = "DERIVED_UNVERIFIED"

class DragCellHysteresis(
    private val switchFraction: Float = 0.60f,
) {
    init {
        require(switchFraction in 0.5f..0.95f)
    }

    fun resolve(
        origin: GridCell,
        visualOffsetPx: ShellOffset,
        cellPitchPx: Float,
        previous: GridCell,
    ): GridCell {
        require(cellPitchPx > 0f)
        return GridCell(
            column = resolveAxis(origin.column + visualOffsetPx.x / cellPitchPx, previous.column),
            row = resolveAxis(origin.row + visualOffsetPx.y / cellPitchPx, previous.row).coerceAtLeast(0),
        )
    }

    private fun resolveAxis(position: Float, previous: Int): Int {
        var resolved = previous
        while (position >= resolved + switchFraction) resolved++
        while (position <= resolved - switchFraction) resolved--
        return resolved
    }
}

class EdgeAutoScrollPolicy(
    private val activationZonePx: Float,
    private val maximumSpeedPxPerSecond: Float,
) {
    init {
        require(activationZonePx > 0f)
        require(maximumSpeedPxPerSecond > 0f)
    }

    fun velocity(pointerViewportY: Float, viewportHeightPx: Float): Float {
        if (viewportHeightPx <= activationZonePx * 2f) return 0f
        return when {
            pointerViewportY < activationZonePx ->
                -maximumSpeedPxPerSecond * edgeRatio(pointerViewportY)
            pointerViewportY > viewportHeightPx - activationZonePx ->
                maximumSpeedPxPerSecond * edgeRatio(viewportHeightPx - pointerViewportY)
            else -> 0f
        }
    }

    private fun edgeRatio(distanceToEdge: Float): Float =
        (abs(activationZonePx - distanceToEdge) / activationZonePx).coerceIn(0f, 1f)
}
