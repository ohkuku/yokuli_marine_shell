package com.yokuli.shell.contract

import kotlin.math.max

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

data class ShellChromeSafeBands(
    val status: ShellSafeBand,
    val navigation: ShellSafeBand,
    val imeLiftPx: Int,
)

/** Pure safe-band policy shared by Android rendering and viewport tests. */
object ShellSafeBands {
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
            top = max(metrics.safeInsets.top, max(topLeft, topRight)),
            right = maxOf(metrics.safeInsets.right, topRight, cutoutRight),
            bottom = 0,
        )
        val navigation = ShellSafeBand(
            left = maxOf(metrics.safeInsets.left, metrics.systemGestureInsets.left, bottomLeft),
            top = 0,
            right = maxOf(metrics.safeInsets.right, metrics.systemGestureInsets.right, bottomRight),
            bottom = maxOf(
                metrics.safeInsets.bottom,
                metrics.systemGestureInsets.bottom,
                bottomLeft,
                bottomRight,
            ),
        )
        return ShellChromeSafeBands(status, navigation, metrics.imeInsets.bottom)
    }
}
