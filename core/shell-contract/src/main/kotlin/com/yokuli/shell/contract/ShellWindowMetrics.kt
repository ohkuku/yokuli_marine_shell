package com.yokuli.shell.contract

import kotlin.math.max
import kotlin.math.roundToInt

data class ShellInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

data class ShellRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class ShellRoundedCorner(val centerX: Int, val centerY: Int, val radius: Int)

data class ShellRoundedCorners(
    val topLeft: ShellRoundedCorner? = null,
    val topRight: ShellRoundedCorner? = null,
    val bottomLeft: ShellRoundedCorner? = null,
    val bottomRight: ShellRoundedCorner? = null,
)

data class ShellWindowMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val safeInsets: ShellInsets = ShellInsets(),
    val displayCutoutRects: List<ShellRect> = emptyList(),
    val roundedCorners: ShellRoundedCorners = ShellRoundedCorners(),
    val imeInsets: ShellInsets = ShellInsets(),
    val systemGestureInsets: ShellInsets = ShellInsets(),
)

data class ShellSafeBand(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** 横向可用区间，单位为窗口物理像素；right 为开区间边界。 */
data class ShellHorizontalSegment(val left: Int, val right: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
}

data class ShellChromeSafeBands(
    val status: ShellSafeBand,
    val navigation: ShellSafeBand,
    val imeLiftPx: Int,
)

/** Pure safe-band policy shared by Android rendering and viewport tests. */
object ShellSafeBands {
    /**
     * 固定高度系统栏的横向安全区段。只排除与实际栏高度相交的挖孔/刘海，
     * 包括不接触屏幕顶边的悬浮挖孔；圆角继续横向避让，不把整页向下推。
     * 状态栏与通知横幅共用本策略，不各自猜测摄像头位置。
     */
    fun statusSegments(
        metrics: ShellWindowMetrics,
        heightDp: Float = 30f,
        edgePaddingDp: Float = 8f,
        cutoutGapDp: Float = 4f,
    ): List<ShellHorizontalSegment> {
        val density = metrics.density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safe = resolve(metrics).status
        val edgePadding = (edgePaddingDp.coerceAtLeast(0f) * density).roundToInt()
        val gap = (cutoutGapDp.coerceAtLeast(0f) * density).roundToInt()
        val width = metrics.widthPx.coerceAtLeast(0)
        val left = (safe.left + edgePadding).coerceIn(0, width)
        val right = (width - safe.right - edgePadding).coerceIn(0, width)
        val top = safe.top
        val bottom = top + (heightDp.coerceAtLeast(0f) * density).roundToInt()
        if (left >= right || bottom <= top) return emptyList()
        val obstacles = metrics.displayCutoutRects
            .filter { it.bottom > top && it.top < bottom && it.right > it.left }
            .map { ShellHorizontalSegment((it.left - gap).coerceIn(left, right), (it.right + gap).coerceIn(left, right)) }
            .filter { it.width > 0 }
            .sortedBy { it.left }
        val result = mutableListOf<ShellHorizontalSegment>()
        var cursor = left
        obstacles.forEach { obstacle ->
            if (obstacle.left > cursor) result += ShellHorizontalSegment(cursor, obstacle.left)
            cursor = max(cursor, obstacle.right)
        }
        if (cursor < right) result += ShellHorizontalSegment(cursor, right)
        return result
    }

    fun resolve(metrics: ShellWindowMetrics): ShellChromeSafeBands {
        val corners = metrics.roundedCorners
        val topLeft = corners.topLeft?.radius ?: 0
        val topRight = corners.topRight?.radius ?: 0
        val bottomLeft = corners.bottomLeft?.radius ?: 0
        val bottomRight = corners.bottomRight?.radius ?: 0

        var cutoutLeft = 0
        var cutoutRight = 0
        metrics.displayCutoutRects.filter { it.top <= 0 }.forEach { rect ->
            if (rect.left <= 0) cutoutLeft = max(cutoutLeft, rect.right)
            if (rect.right >= metrics.widthPx) cutoutRight = max(cutoutRight, metrics.widthPx - rect.left)
        }

        val status = ShellSafeBand(
            left = maxOf(metrics.safeInsets.left, topLeft, cutoutLeft),
            // Rounded corners are avoided laterally by edge controls. Treating their radius as a
            // full-width top inset wastes the whole app canvas on square/round displays.
            top = metrics.safeInsets.top,
            right = maxOf(metrics.safeInsets.right, topRight, cutoutRight),
            bottom = 0,
        )
        val navigation = ShellSafeBand(
            left = maxOf(metrics.safeInsets.left, metrics.systemGestureInsets.left, bottomLeft),
            top = 0,
            right = maxOf(metrics.safeInsets.right, metrics.systemGestureInsets.right, bottomRight),
            // As above, bottom corner radii move the edge keys inward; only actual platform and
            // gesture insets consume a full-width bottom band.
            bottom = metrics.safeInsets.bottom,
        )
        return ShellChromeSafeBands(status, navigation, metrics.imeInsets.bottom)
    }
}
