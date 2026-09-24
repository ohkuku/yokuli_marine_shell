package com.yokuli.marine.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/** MDL2 的几何比例 + 本产品的 Android 手机适配；不是未经测量的微软动画红线。 */
object W10MobileMetrics {
    val Grid = 4.dp
    val PageInset = 16.dp
    val ContentGap = 12.dp
    val TouchTarget = 48.dp // 高于 2016 MDL 的 44ep 最小值，保留 Android 触控/无障碍面积。
    val CommandBar = 48.dp
    val Icon = 24.dp
    val IconStroke = 1.5.dp // 32ep/2ep 的 MDL2 图标比例。
    val ControlBorder = 2.dp
    val ToggleWidth = 40.dp
    val ToggleHeight = 20.dp
    val ToggleThumb = 12.dp
    val Selection = 20.dp
    val RadioDot = 10.dp
}

/** 时间是 Yokuli 的手机渲染适配值；界面保留跟手位移，释放阶段才使用这些补间。 */
object W10MobileMotion {
    const val PressMillis = 80
    const val ReleaseMillis = 120
    const val ControlMillis = 160
    const val ContentMillis = 220
    const val PageMillis = 240
    const val AppMillis = 320
    const val OverlayMillis = 180
    const val StaggerMillis = 24
    val EntranceEasing = CubicBezierEasing(.1f, .9f, .2f, 1f)
    val ExitEasing = CubicBezierEasing(.4f, 0f, 1f, 1f)
}
