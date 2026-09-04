package com.yokuli.shell.contract

enum class MarineTileContentLayout {
    ICON,
    COMPACT_METRIC,
    STANDARD_FACTS,
    WIDE_PREVIEW,
    TALL_STATUS,
    LARGE_DASHBOARD,
}

enum class MarineTileSize(
    val columns: Int,
    val rows: Int,
    val contentLayout: MarineTileContentLayout,
) {
    ICON_1X1(1, 1, MarineTileContentLayout.ICON),
    COMPACT_2X1(2, 1, MarineTileContentLayout.COMPACT_METRIC),
    STANDARD_2X2(2, 2, MarineTileContentLayout.STANDARD_FACTS),
    WIDE_4X2(4, 2, MarineTileContentLayout.WIDE_PREVIEW),
    TALL_2X4(2, 4, MarineTileContentLayout.TALL_STATUS),
    LARGE_4X4(4, 4, MarineTileContentLayout.LARGE_DASHBOARD),
}

enum class TilePresentationKind(val allowsAutomaticCycling: Boolean = false) {
    STATIC,
    ICONIC,
    METRIC,
    STATUS,
    CYCLE(allowsAutomaticCycling = true),
    MAP_PREVIEW,
    SAFETY,
}
