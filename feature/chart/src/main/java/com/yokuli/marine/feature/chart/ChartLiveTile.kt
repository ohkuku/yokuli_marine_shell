package com.yokuli.marine.feature.chart

enum class ChartTileFrameKind { MAP, NAVIGATION, POSITION, STATIC }

/** Pure availability policy; explicit modes never pretend their missing input exists. */
object ChartTileFramePolicy {
    fun frames(
        mode: ChartTileMode,
        hasActiveNavigation: Boolean,
        hasMapSnapshot: Boolean,
        hasFreshPosition: Boolean,
    ): List<ChartTileFrameKind> = when (mode) {
        ChartTileMode.MAP -> available(ChartTileFrameKind.MAP, hasMapSnapshot)
        ChartTileMode.NAVIGATION -> available(ChartTileFrameKind.NAVIGATION, hasActiveNavigation)
        ChartTileMode.POSITION -> available(ChartTileFrameKind.POSITION, hasFreshPosition)
        ChartTileMode.STATIC -> listOf(ChartTileFrameKind.STATIC)
        ChartTileMode.AUTO -> when {
            hasActiveNavigation && hasMapSnapshot -> listOf(ChartTileFrameKind.NAVIGATION, ChartTileFrameKind.MAP)
            hasActiveNavigation -> listOf(ChartTileFrameKind.NAVIGATION)
            hasMapSnapshot -> listOf(ChartTileFrameKind.MAP)
            hasFreshPosition -> listOf(ChartTileFrameKind.POSITION)
            else -> listOf(ChartTileFrameKind.STATIC)
        }
    }

    private fun available(kind: ChartTileFrameKind, available: Boolean) =
        listOf(if (available) kind else ChartTileFrameKind.STATIC)
}

const val CHART_TILE_ROTATION_MILLIS = 5_000L
