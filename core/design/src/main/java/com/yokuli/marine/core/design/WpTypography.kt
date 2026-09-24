package com.yokuli.marine.core.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

/** 保留合法内置 Selawik；350 用其 light 近似 Segoe Semilight，中文由设备 CJK 字族回退。 */
val WpFontFamily = FontFamily(
    Font(R.font.selawik_light, FontWeight.Light),
    Font(R.font.selawik_light, FontWeight(350)),
    Font(R.font.selawik_regular, FontWeight.Normal),
    Font(R.font.selawik_semibold, FontWeight.SemiBold),
)
val LocalWpTextScale = staticCompositionLocalOf { 1f }

/**
 * Windows 10 Mobile / MDL2 层级；保留 Wp 名称作为调用兼容，不再是 WP8 超大页标题。
 * 依据 Microsoft UWP app design guidelines v1509 p303；epx 映射为支持系统字体缩放的 sp。
 */
object WpTypeScale {
    const val AppCaption = 12
    const val PageTitle = 24
    const val PivotTitle = 24
    const val SectionTitle = 20
    const val ListTitle = 15
    const val Body = 15
    const val Caption = 12
    const val Base = 15
    const val SubtitleAlt = 18
    const val Header = 46
    const val Subheader = 34

    /** 官方基础 ramp 的行距；额外仪表字号按 MDL 的 125%/4ep 网格推导。 */
    fun lineHeight(size: Int): Int = when (size) {
        46 -> 56
        34 -> 40
        24 -> 28
        20 -> 24
        18 -> 24
        15 -> 20
        12 -> 14
        else -> ((size * 1.25f / 4f).roundToInt() * 4).coerceAtLeast(size + 2)
    }
    fun weight(size: Int): FontWeight = when {
        size >= 34 -> FontWeight.Light
        size >= 24 -> FontWeight(350)
        else -> FontWeight.Normal
    }
}
