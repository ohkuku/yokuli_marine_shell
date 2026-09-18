package com.yokuli.marine.core.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Shell 与应用共用同一字族、字重；中文缺字交给 Android CJK 字体回退。 */
val WpFontFamily = FontFamily(
    Font(R.font.selawik_light, FontWeight.Light),
    Font(R.font.selawik_regular, FontWeight.Normal),
    Font(R.font.selawik_semibold, FontWeight.SemiBold),
)
val LocalWpTextScale = staticCompositionLocalOf { 1f }

/** 统一的 Metro 信息层级；页面标题与列表内容不能使用同一个字号。 */
object WpTypeScale {
    const val AppCaption = 12
    const val PageTitle = 40
    const val PivotTitle = 28
    const val SectionTitle = 24
    const val ListTitle = 20
    const val Body = 17
    const val Caption = 13
}
