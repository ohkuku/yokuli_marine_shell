package com.yokuli.shell.engine.interaction

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

/** Pixel geometry shared by drawing, physical hit testing and JVM regression tests. */
data class EditControlRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(right >= left && bottom >= top)
    }
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
    fun overlaps(other: EditControlRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

data class TileEditControls(val unpin: EditControlRect, val resize: EditControlRect?) {
    fun contains(x: Float, y: Float): Boolean = unpin.contains(x, y) || resize?.contains(x, y) == true
}

/**
 * DERIVED_UNVERIFIED product adaptation, not measured WP8 geometry.
 * Controls live on the viewport overlay. They may extend beyond a tile, but never beyond
 * the viewport, overlap each other, or consume the selected tile's central drag region.
 */
object TileEditControlGeometry {
    fun resolve(
        tile: EditControlRect,
        viewportWidth: Float,
        viewportHeight: Float,
        touchSize: Float,
        compact: Boolean,
        canResize: Boolean,
    ): TileEditControls? {
        require(touchSize.isFinite() && touchSize > 0f)
        val touch = ceil(touchSize)
        if (viewportWidth < touch || viewportHeight < touch) return null
        val left = max(0f, tile.left)
        val top = max(0f, tile.top)
        val right = min(viewportWidth, tile.right)
        val bottom = min(viewportHeight, tile.bottom)
        if (right <= left || bottom <= top) return null // Scrolled off screen: no invisible hit targets.
        val visible = EditControlRect(left, top, right, bottom)
        val halfDrag = min(touch / 4f, min(visible.width, visible.height) / 2f)
        val protected = EditControlRect(
            visible.centerX - halfDrag, visible.centerY - halfDrag,
            visible.centerX + halfDrag, visible.centerY + halfDrag,
        )
        fun axis(start: Float, end: Float, limit: Float): List<Float> = listOf(
            start - touch, start - touch / 2f, start,
            end - touch, end - touch / 2f, end,
            0f, limit - touch,
        ).map { it.roundToInt().toFloat().coerceIn(0f, floor(limit - touch)) }.distinct()
        val candidates = axis(left, right, viewportWidth).flatMap { x ->
            axis(top, bottom, viewportHeight).map { y -> EditControlRect(x, y, x + touch, y + touch) }
        }.filterNot { it.overlaps(protected) }
        fun score(rect: EditControlRect, x: Float, y: Float): Float =
            (rect.centerX - x) * (rect.centerX - x) + (rect.centerY - y) * (rect.centerY - y)
        val unpinX = if (compact) left else right
        if (!canResize) return candidates.minByOrNull { score(it, unpinX, top) }?.let { TileEditControls(it, null) }
        // Search pairs, not two independent clamps: clamping at an edge must not merge the hit regions.
        var best: TileEditControls? = null
        var bestScore = Float.POSITIVE_INFINITY
        for (unpin in candidates) for (resize in candidates) {
            if (unpin.overlaps(resize)) continue
            val value = score(unpin, unpinX, top) + score(resize, right, bottom)
            if (value < bestScore) {
                bestScore = value
                best = TileEditControls(unpin, resize)
            }
        }
        return best
    }
}
