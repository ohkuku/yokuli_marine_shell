package com.yokuli.shell.contract

enum class MarineTileContentLayout {
    ICON,
    STANDARD_FACTS,
    WIDE_PREVIEW,
}

/** Width × height in the smallest WP8 grid cell. No product-specific extra shapes. */
enum class MarineTileSize(
    val columns: Int,
    val rows: Int,
    val contentLayout: MarineTileContentLayout,
) {
    ICON_1X1(1, 1, MarineTileContentLayout.ICON),
    STANDARD_2X2(2, 2, MarineTileContentLayout.STANDARD_FACTS),
    WIDE_4X2(4, 2, MarineTileContentLayout.WIDE_PREVIEW);

    companion object {
        /**
         * 中文：仅在持久化边界识别旧形状；保留磁贴身份、顺序和分组，不重置桌面。
         * English: Decode retired shapes only at the storage boundary, preserving identity/order.
         */
        fun fromPersistedName(name: String): MarineTileSize? = when (name) {
            "COMPACT_2X1" -> STANDARD_2X2
            "TALL_2X4", "LARGE_4X4" -> WIDE_4X2
            else -> entries.firstOrNull { it.name == name }
        }
    }
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
