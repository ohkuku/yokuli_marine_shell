package com.yokuli.shell.contract

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.sqrt

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
        edgePaddingDp: Float = 12f,
        cutoutGapDp: Float = 4f,
    ): List<ShellHorizontalSegment> {
        val density = metrics.density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val top = metrics.safeInsets.top.coerceAtLeast(0)
        val bottom = top + (heightDp.coerceAtLeast(0f) * density).roundToInt()
        val safe = horizontalInsets(metrics, top, bottom)
        val edgePadding = (edgePaddingDp.coerceAtLeast(0f) * density).roundToInt()
        val gap = (cutoutGapDp.coerceAtLeast(0f) * density).roundToInt()
        val width = metrics.widthPx.coerceAtLeast(0)
        val left = (safe.left + edgePadding).coerceIn(0, width)
        val right = (width - safe.right - edgePadding).coerceIn(0, width)
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
        val density = metrics.density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val top = metrics.safeInsets.top.coerceAtLeast(0)
        val statusEdges = horizontalInsets(metrics, top, top + (30f * density).roundToInt())
        val bottom = (metrics.heightPx - metrics.safeInsets.bottom).coerceAtLeast(0)
        val navigationEdges = horizontalInsets(metrics, (bottom - 54f * density).roundToInt(), bottom)

        val status = ShellSafeBand(
            left = statusEdges.left,
            // Rounded corners are avoided laterally by edge controls. Treating their radius as a
            // full-width top inset wastes the whole app canvas on square/round displays.
            top = metrics.safeInsets.top,
            right = statusEdges.right,
            bottom = 0,
        )
        val navigation = ShellSafeBand(
            left = maxOf(navigationEdges.left, metrics.systemGestureInsets.left),
            top = 0,
            right = maxOf(navigationEdges.right, metrics.systemGestureInsets.right),
            // As above, bottom corner radii move the edge keys inward; only actual platform and
            // gesture insets consume a full-width bottom band.
            bottom = metrics.safeInsets.bottom,
        )
        return ShellChromeSafeBands(status, navigation, metrics.imeInsets.bottom)
    }

    /** 指定窗口纵向范围内的横向遮挡。圆角使用真实圆心和半径，不能把半径当成左边距。 */
    fun horizontalInsets(metrics: ShellWindowMetrics, topPx: Int, bottomPx: Int): ShellInsets {
        val width = metrics.widthPx.coerceAtLeast(0)
        val top = topPx.coerceAtLeast(0)
        val bottom = bottomPx.coerceAtMost(metrics.heightPx.coerceAtLeast(0))
        if(bottom <= top) return ShellInsets()
        fun cornerInset(corner: ShellRoundedCorner?, left: Boolean, upper: Boolean): Int {
            if(corner == null || corner.radius <= 0) return 0
            val y = if(upper) top else bottom
            if(upper && y >= corner.centerY || !upper && y <= corner.centerY) {
                return (if(left) corner.centerX-corner.radius else width-corner.centerX-corner.radius).coerceIn(0,width)
            }
            val dy = kotlin.math.abs(y.toDouble() - corner.centerY).coerceAtMost(corner.radius.toDouble())
            val dx = sqrt((corner.radius.toDouble() * corner.radius - dy * dy).coerceAtLeast(0.0))
            val inset = if(left) corner.centerX - dx else width - corner.centerX - dx
            return ceil(inset).toInt().coerceIn(0, width)
        }
        val corners = metrics.roundedCorners
        var left = maxOf(metrics.safeInsets.left, cornerInset(corners.topLeft,true,true), cornerInset(corners.bottomLeft,true,false), 0)
        var right = maxOf(metrics.safeInsets.right, cornerInset(corners.topRight,false,true), cornerInset(corners.bottomRight,false,false), 0)
        metrics.displayCutoutRects.filter { it.bottom > top && it.top < bottom }.forEach { rect ->
            if(rect.left <= 0) left = max(left, rect.right)
            if(rect.right >= width) right = max(right, width - rect.left)
        }
        return ShellInsets(left = left.coerceIn(0,width), right = right.coerceIn(0,width))
    }
}
