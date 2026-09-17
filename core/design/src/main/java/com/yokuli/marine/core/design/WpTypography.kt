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
