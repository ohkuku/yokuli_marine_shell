package com.yokuli.marine.feature.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

/** 中文：受控 Canvas 图标集，避免依赖字体中的 Unicode 图形。 English: Controlled Canvas icons, independent of font glyphs. */
@Composable
fun MarineIcon(kind: MarineIconKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val stroke = unit * .0625f
        val center = center
        when (kind) {
            MarineIconKind.APPS -> repeat(3) { row -> repeat(3) { column ->
                drawCircle(color, unit * .055f, Offset(unit * (.27f + column * .23f), unit * (.27f + row * .23f)))
            } }
            MarineIconKind.CANCEL -> {
                drawLine(color, Offset(unit * .22f, unit * .22f), Offset(unit * .78f, unit * .78f), stroke)
                drawLine(color, Offset(unit * .78f, unit * .22f), Offset(unit * .22f, unit * .78f), stroke)
            }
            MarineIconKind.UNPIN -> rotate(45f, center) {
                drawRect(color, Offset(unit*.35f, unit*.19f), Size(unit*.30f, unit*.29f), style=Stroke(stroke))
                drawLine(color, Offset(unit*.28f, unit*.49f), Offset(unit*.72f, unit*.49f), stroke)
                drawLine(color, Offset(unit*.50f, unit*.49f), Offset(unit*.50f, unit*.80f), stroke)
                drawLine(color, Offset(unit*.12f, unit*.15f), Offset(unit*.87f, unit*.86f), stroke)
            }
            MarineIconKind.RESIZE -> {
                drawLine(color, Offset(unit * .18f, unit * .82f), Offset(unit * .82f, unit * .18f), stroke)
                drawLine(color, Offset(unit * .18f, unit * .82f), Offset(unit * .18f, unit * .57f), stroke)
                drawLine(color, Offset(unit * .18f, unit * .82f), Offset(unit * .43f, unit * .82f), stroke)
                drawLine(color, Offset(unit * .82f, unit * .18f), Offset(unit * .57f, unit * .18f), stroke)
                drawLine(color, Offset(unit * .82f, unit * .18f), Offset(unit * .82f, unit * .43f), stroke)
            }
            MarineIconKind.PIN -> rotate(45f, center) {
                drawRect(color, Offset(unit*.35f, unit*.19f), Size(unit*.30f, unit*.29f), style=Stroke(stroke))
                drawLine(color, Offset(unit*.28f, unit*.49f), Offset(unit*.72f, unit*.49f), stroke)
                drawLine(color, Offset(unit*.50f, unit*.49f), Offset(unit*.50f, unit*.80f), stroke)
            }
            MarineIconKind.INFO -> {
                drawCircle(color, unit * .38f, center, style = Stroke(stroke))
                drawCircle(color, stroke * .58f, Offset(center.x, unit * .31f))
                drawLine(color, Offset(center.x, unit * .45f), Offset(center.x, unit * .72f), stroke)
            }
            MarineIconKind.GENERIC -> drawRect(
                color,
                topLeft = Offset(unit * .16f, unit * .16f),
                size = Size(unit * .68f, unit * .68f),
                style = Stroke(stroke),
            )
        }
    }
}
