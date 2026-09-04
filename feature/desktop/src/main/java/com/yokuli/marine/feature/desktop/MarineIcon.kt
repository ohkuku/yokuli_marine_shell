package com.yokuli.marine.feature.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

/** 中文：受控 Canvas 图标集，避免依赖字体中的 Unicode 图形。 English: Controlled Canvas icons, independent of font glyphs. */
@Composable
fun MarineIcon(kind: MarineIconKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val stroke = unit * .075f
        val center = center
        when (kind) {
            MarineIconKind.CHART -> {
                drawCircle(color, unit * .31f, center, style = Stroke(stroke))
                drawLine(color, Offset(center.x, center.y - unit * .42f), Offset(center.x, center.y + unit * .42f), stroke)
                drawLine(color, Offset(center.x - unit * .42f, center.y), Offset(center.x + unit * .42f, center.y), stroke)
                val needle = Path().apply {
                    moveTo(center.x + unit * .08f, center.y - unit * .30f)
                    lineTo(center.x - unit * .04f, center.y + unit * .11f)
                    lineTo(center.x + unit * .18f, center.y - unit * .03f)
                    close()
                }
                drawPath(needle, color)
            }
            MarineIconKind.SETTINGS -> repeat(3) { index ->
                val y = unit * (.23f + index * .27f)
                val knobX = unit * listOf(.32f, .68f, .45f)[index]
                drawLine(color, Offset(unit * .08f, y), Offset(unit * .92f, y), stroke * .72f)
                drawCircle(color, stroke * 1.35f, Offset(knobX, y))
            }
            MarineIconKind.APPS -> repeat(3) { row -> repeat(3) { column ->
                drawCircle(color, unit * .055f, Offset(unit * (.27f + column * .23f), unit * (.27f + row * .23f)))
            } }
            MarineIconKind.DONE -> {
                val path = Path().apply {
                    moveTo(unit * .17f, unit * .52f)
                    lineTo(unit * .42f, unit * .75f)
                    lineTo(unit * .85f, unit * .25f)
                }
                drawPath(path, color, style = Stroke(stroke))
            }
            MarineIconKind.CANCEL -> {
                drawLine(color, Offset(unit * .22f, unit * .22f), Offset(unit * .78f, unit * .78f), stroke)
                drawLine(color, Offset(unit * .78f, unit * .22f), Offset(unit * .22f, unit * .78f), stroke)
            }
            MarineIconKind.UNPIN -> {
                drawCircle(color, unit * .38f, center, style = Stroke(stroke))
                drawLine(color, Offset(unit * .27f, center.y), Offset(unit * .73f, center.y), stroke)
            }
            MarineIconKind.RESIZE -> {
                drawLine(color, Offset(unit * .18f, unit * .82f), Offset(unit * .82f, unit * .18f), stroke)
                drawLine(color, Offset(unit * .18f, unit * .82f), Offset(unit * .18f, unit * .57f), stroke)
                drawLine(color, Offset(unit * .18f, unit * .82f), Offset(unit * .43f, unit * .82f), stroke)
                drawLine(color, Offset(unit * .82f, unit * .18f), Offset(unit * .57f, unit * .18f), stroke)
                drawLine(color, Offset(unit * .82f, unit * .18f), Offset(unit * .82f, unit * .43f), stroke)
            }
            MarineIconKind.PIN -> rotate(45f, center) {
                drawLine(color, Offset(unit * .50f, unit * .16f), Offset(unit * .50f, unit * .78f), stroke)
                drawLine(color, Offset(unit * .28f, unit * .36f), Offset(unit * .72f, unit * .36f), stroke * 1.5f)
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
