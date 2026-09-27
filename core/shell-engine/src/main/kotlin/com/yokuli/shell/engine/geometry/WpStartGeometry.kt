package com.yokuli.shell.engine.geometry

import kotlin.math.roundToInt

data class StartViewport(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val topInsetPx: Int,
    val bottomInsetPx: Int,
    val fontScale: Float,
) {
    init {
        require(widthPx > 0 && heightPx > 0)
        require(density > 0f && fontScale > 0f)
        require(topInsetPx >= 0 && bottomInsetPx >= 0)
        require(topInsetPx + bottomInsetPx < heightPx)
    }
}

data class IntInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(left <= right && top <= bottom)
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class ResolvedStartGeometry(
    val profileId: ProfileId,
    val columns: Int,
    val outerInsetsPx: IntInsets,
    val seamPx: Int,
    val smallCellPx: Int,
    val statusStripHeightPx: Int,
    val contentBounds: IntRect,
) {
    fun tileWidthPx(spanColumns: Int): Int {
        require(spanColumns in 1..columns)
        return smallCellPx * spanColumns + seamPx * (spanColumns - 1)
    }

    fun tileHeightPx(spanRows: Int): Int {
        require(spanRows > 0)
        return smallCellPx * spanRows + seamPx * (spanRows - 1)
    }
}

object WpStartGeometryCalculator {
    /**
     * 中文：六列把一整块磁贴（文字、图形、内边距）等比缩进更密的网格。
     * 只作用于磁贴内容密度；系统控件、外边距和手势命中区域仍使用设备原始密度。
     * 工坊预览与真实桌面必须共用此比例，不能额外再给 renderer 缩放一次。
     */
    fun tileContentScale(viewport: StartViewport, profile: WpReferenceProfile): Float {
        if (profile.columnCount == 4) return 1f
        val current = calculate(viewport, profile)
        val reference = calculate(viewport, WpReferenceProfiles.withColumns(profile.id, 4))
        return current.smallCellPx.toFloat() / reference.smallCellPx
    }

    fun calculate(
        viewport: StartViewport,
        profile: WpReferenceProfile = WpReferenceProfiles.forViewport(viewport),
    ): ResolvedStartGeometry {
        fun scale(referencePx: Int): Int =
            (referencePx.toDouble() * viewport.widthPx / profile.referenceWidthPx).roundToInt()

        val start = scale(profile.outerInsetPolicy.referenceStartPx).coerceAtLeast(1)
        val nominalEnd = scale(profile.outerInsetPolicy.referenceEndPx).coerceAtLeast(1)
        val seam = scale(profile.seamPx).coerceAtLeast(1)
        val availableForCells = viewport.widthPx - start - nominalEnd - seam * (profile.columnCount - 1)
        val cell = availableForCells / profile.columnCount
        require(cell > 0) { "Viewport is too narrow for ${profile.id.value}" }

        val consumed = start + nominalEnd + seam * (profile.columnCount - 1) + cell * profile.columnCount
        val end = nominalEnd + (viewport.widthPx - consumed)
        val tileTop = viewport.topInsetPx + scale(profile.outerInsetPolicy.referenceTileTopPx)
        val contentBottom = viewport.heightPx - viewport.bottomInsetPx
        require(tileTop < contentBottom) { "Insets leave no Start content area" }
        val statusHeight = scale(profile.statusStrip.referenceHeightPx)

        return ResolvedStartGeometry(
            profileId = profile.id,
            columns = profile.columnCount,
            outerInsetsPx = IntInsets(
                left = start,
                top = tileTop,
                right = end,
                bottom = viewport.bottomInsetPx,
            ),
            seamPx = seam,
            smallCellPx = cell,
            statusStripHeightPx = statusHeight,
            contentBounds = IntRect(
                left = start,
                top = tileTop,
                right = viewport.widthPx - end,
                bottom = contentBottom,
            ),
        )
    }
}
