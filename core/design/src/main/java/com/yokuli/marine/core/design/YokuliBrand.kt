package com.yokuli.marine.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** 黑白品牌不覆盖警报、来源质量和航海图的领域颜色。 */
object YokuliBrandColors {
    val Ink = Color(0xFF111111)
    val Signal = Color(0xFFDDDDDD)
    val Wake = Color(0xFFBBBBBB)
    val Paper = Color(0xFFFAFAFA)
    val Secondary = Color(0xFF777777)
}

/** 与 SVG 母版共用的十六格帆船；边缘对齐物理像素，小尺寸不依赖低分辨率位图。 */
private fun DrawScope.pixelSail(origin: Offset, pixel: Float, color: Color, accent: Color) {
    val x = origin.x.roundToInt().toFloat()
    val y = origin.y.roundToInt().toFloat()
    yokuliPixelSail.forEach { block ->
        drawRect(if (block.sail) accent else color,
            Offset(x + block.x * pixel, y + block.y * pixel),
            Size(block.width * pixel, block.height * pixel))
    }
}

@Composable
fun YokuliBrandMark(
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = Color.White,
    accent: Color = color,
    contentDescription: String? = null,
) {
    Canvas(modifier.semantics { contentDescription?.let { this.contentDescription = it } }) {
        val pixel = floor(size.minDimension / 16f).coerceAtLeast(1f)
        pixelSail(Offset((size.width - 16 * pixel) / 2f, (size.height - 16 * pixel) / 2f), pixel, color, accent)
    }
}

/** 原创小写字标：文字为主，os 为次级；字体轮廓已转为矢量，不依赖宿主安装字体。 */
@Composable
fun YokuliBrandWordmark(
    modifier: Modifier = Modifier.width(112.dp).height(28.dp),
    color: Color = Color.White,
    contentDescription: String? = "Yokuli OS",
) {
    Image(painterResource(R.drawable.yokuli_wordmark), contentDescription, modifier,
        contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color))
}

/** 关于页使用同一份平面字标，不再叠加旧帆船 O 或巨型图标。 */
@Composable
fun YokuliBrandSignature(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    @Suppress("UNUSED_PARAMETER") accent: Color = color,
) {
    Box(modifier.size(184.dp, 43.dp).semantics { contentDescription = "Yokuli OS" }) {
        Image(painterResource(R.drawable.yokuli_wordmark_body), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color))
        Image(painterResource(R.drawable.yokuli_wordmark_os), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color.copy(alpha = .65f)))
    }
}

private val BrandSailEasing = CubicBezierEasing(.18f, .78f, .24f, 1f)

/**
 * 一次性品牌抵达动效。进度只在 DrawScope 读取，不逐帧重组桌面或修改业务状态。
 * 先让像素帆船驶向字标左侧，再展开文字；没有旋转模糊、粒子系统或假加载进度。
 */
@Composable
fun YokuliBrandArrival(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val lettering = painterResource(R.drawable.yokuli_wordmark)
    Canvas(modifier.semantics { contentDescription = "Yokuli OS" }) {
        val p = progress().coerceIn(0f, 1f)
        val scale = minOf(size.width / 264f, size.height / 80f)
        val origin = Offset((size.width - 264f * scale) / 2f, (size.height - 80f * scale) / 2f)
        val sailing = BrandSailEasing.transform((p / .61f).coerceIn(0f, 1f))
        val revealing = BrandSailEasing.transform(((p - .12f) / .52f).coerceIn(0f, 1f))
        val pixel = floor(2.5f * scale).coerceAtLeast(1f)
        // 从左向右抵达字标，尾迹始终留在行进方向后方。
        val sailX = origin.x + (-40f + sailing * 47f) * scale
        val arrivalAlpha = BrandSailEasing.transform((p / .14f).coerceIn(0f, 1f))
        val sailY = origin.y + 20f * scale + sin(p * 6.283185f) * 1.5f * scale * (1f - sailing)
        val wakeAlpha = ((1f - ((p - .4f) / .3f).coerceIn(0f,1f)) * sailing * .25f)
        // 局部画布裁剪并在最初数帧淡入，窄屏不会先在边缘露出截断的船体。
        clipRect {
            for (i in 0..2) {
                drawRect(color.copy(alpha = color.alpha * arrivalAlpha * wakeAlpha * (1f-i*.22f)),
                    Offset((sailX - (i+1) * 7f * scale).roundToInt().toFloat(), (sailY+13*pixel).roundToInt().toFloat()),
                    Size(pixel * 2, pixel))
            }
            val sailColor = color.copy(alpha = color.alpha * arrivalAlpha)
            pixelSail(Offset(sailX, sailY), pixel, sailColor, sailColor)
        }
        val textX = origin.x + 66f * scale
        val textY = origin.y + 21f * scale
        val textWidth = 194f * scale
        clipRect(left = textX, top = origin.y, right = textX + textWidth * revealing, bottom = origin.y + 80f * scale) {
            translate(textX, textY) {
                with(lettering) { draw(Size(textWidth, textWidth * 72f / 312f),
                    alpha = revealing, colorFilter = ColorFilter.tint(color)) }
            }
        }
    }
}
