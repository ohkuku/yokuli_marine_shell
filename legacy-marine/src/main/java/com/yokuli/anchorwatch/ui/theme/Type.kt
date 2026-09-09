package com.yokuli.anchorwatch.ui.theme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
private fun text(size:Int,line:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(fontSize=size.sp,lineHeight=line.sp,fontWeight=weight,fontFamily=FontFamily.SansSerif)
val YokuliTypography=Typography(
    displayLarge=text(64,70,FontWeight.Light),displayMedium=text(56,62,FontWeight.Light),displaySmall=text(48,54,FontWeight.Light),
    headlineLarge=text(46,52,FontWeight.Light),headlineMedium=text(36,42,FontWeight.Light),headlineSmall=text(28,34,FontWeight.Light),
    titleLarge=text(26,32,FontWeight.Light),titleMedium=text(22,28,FontWeight.Light),titleSmall=text(18,24),
    bodyLarge=text(18,25),bodyMedium=text(16,23),bodySmall=text(14,20),
    labelLarge=text(16,22),labelMedium=text(14,20),labelSmall=text(12,18),
)
