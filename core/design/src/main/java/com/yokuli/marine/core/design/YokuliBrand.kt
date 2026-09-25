package com.yokuli.marine.core.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** 品牌原色仅用于独立品牌画面；应用内由调用者提供主题色，夜间/高对比优先。 */
object YokuliBrandColors {
    val Ink = Color(0xFF081E29)
    val Signal = Color(0xFF27D3C2)
}

/**
 * 舷间留白构成Y的航迹；分离的前缘是同一主标的组成部分，不表示数据状态。
 * 所有场景共用SVG母版生成的矢量资源，不在页面重复画路径。
 * 单色用accent=color，24dp系统键仍保留完整轮廓；纯装饰不要重复朗读品牌名。
 */
@Composable
fun YokuliBrandMark(
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = Color.White,
    accent: Color = color,
    contentDescription: String? = null,
) {
    Box(modifier.semantics { contentDescription?.let { this.contentDescription = it } }) {
        Image(painterResource(R.drawable.yokuli_mark_body), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color))
        Image(painterResource(R.drawable.yokuli_mark_beacon), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(accent))
    }
}

/** 原创轮廓字标，字形不会随设备字体改变；正文仍用系统Selawik字阶。 */
@Composable
fun YokuliBrandWordmark(
    modifier: Modifier = Modifier.width(112.dp).height(24.dp),
    color: Color = Color.White,
    contentDescription: String? = "Yokuli OS",
) {
    Image(painterResource(R.drawable.yokuli_wordmark), contentDescription, modifier,
        contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color))
}

/** 横排品牌签名：按同一比例缩放，调用者给定狭窄空间时不裁字或挤压标记。 */
@Composable
fun YokuliBrandSignature(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    accent: Color = color,
) {
    BoxWithConstraints(modifier.size(172.dp, 36.dp).semantics { contentDescription = "Yokuli OS" },
        contentAlignment = Alignment.CenterStart) {
        val unit = minOf(maxHeight, maxWidth / 4.9375f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            YokuliBrandMark(Modifier.size(unit), color, accent)
            Spacer(Modifier.width(unit * .3125f))
            YokuliBrandWordmark(Modifier.size(unit * 3.625f, unit * 3.625f * 60f / 296f), color, null)
        }
    }
}
