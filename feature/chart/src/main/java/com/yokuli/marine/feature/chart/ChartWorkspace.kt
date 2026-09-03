package com.yokuli.marine.feature.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.ChartMode

@Composable
fun ChartWorkspace(initialMode: ChartMode, onHome: () -> Unit) {
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    Box(Modifier.fillMaxSize().background(YokuliColors.ChartWater).testTag("chart-workspace-${mode.name.lowercase()}")) {
        MarineChartSurface(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            WpPageHeader(
                appName = "chart",
                contextLine = mode.name,
                trailing = "36°50.9′S  174°45.8′E",
                modifier = Modifier.background(YokuliColors.Black.copy(alpha = .92f)),
            )
            Spacer(Modifier.weight(1f))
            ModeReadout(mode, Modifier.wpEntrance(motionKey = mode, order = 1))
            ChartAppBar(mode, onMode = { mode = it }, onHome = onHome)
        }
    }
}

@Composable
private fun MarineChartSurface(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(YokuliColors.ChartWater)
        val land = Path().apply {
            moveTo(0f, size.height * .13f)
            cubicTo(size.width * .16f, size.height * .08f, size.width * .22f, size.height * .28f, size.width * .38f, size.height * .23f)
            cubicTo(size.width * .46f, size.height * .19f, size.width * .52f, size.height * .08f, size.width * .68f, 0f)
            lineTo(0f, 0f)
            close()
        }
        drawPath(land, YokuliColors.ChartLand)
        repeat(4) { index ->
            val y = size.height * (.27f + index * .13f)
            drawLine(YokuliColors.ChartDeep, Offset(0f, y), Offset(size.width, y - size.height * .08f), 2f)
        }
        val route = Path().apply {
            moveTo(size.width * .17f, size.height * .76f)
            cubicTo(size.width * .31f, size.height * .60f, size.width * .48f, size.height * .65f, size.width * .63f, size.height * .43f)
            lineTo(size.width * .82f, size.height * .32f)
        }
        drawPath(route, YokuliColors.Route, style = Stroke(width = 7f, cap = StrokeCap.Round))
        val boat = Offset(size.width * .48f, size.height * .57f)
        val vessel = Path().apply {
            moveTo(boat.x, boat.y - 25f)
            lineTo(boat.x - 14f, boat.y + 19f)
            lineTo(boat.x, boat.y + 12f)
            lineTo(boat.x + 14f, boat.y + 19f)
            close()
        }
        drawPath(vessel, YokuliColors.ChartInk)
        drawCircle(YokuliColors.ChartInk, radius = 8f, center = Offset(size.width * .82f, size.height * .32f), style = Stroke(4f))
    }
}

@Composable
private fun ModeReadout(mode: ChartMode, modifier: Modifier = Modifier) {
    val (headline, detail) = when (mode) {
        ChartMode.BROWSE -> "FOLLOWING" to "COG 184°  ·  SOG 6.2 kn"
        ChartMode.NAVIGATE -> "MOTUIHE" to "DTW 3.4 NM  ·  BRG 071°T"
        ChartMode.ANCHOR -> "NOT ARMED" to "Set the anchor when position is ready"
        ChartMode.SURVEY -> "SURVEY READY" to "DEPTH —  ·  POSITION PHONE"
    }
    Column(modifier.fillMaxWidth().background(YokuliColors.Black.copy(alpha = .88f)).padding(14.dp)) {
        WpText(headline, 22, weight = androidx.compose.ui.text.font.FontWeight.Light)
        WpText(detail, 13, color = YokuliColors.Muted, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun ChartAppBar(mode: ChartMode, onMode: (ChartMode) -> Unit, onHome: () -> Unit) {
    val modes = listOf(
        ChartMode.BROWSE to "⌖",
        ChartMode.NAVIGATE to "➤",
        ChartMode.ANCHOR to "⚓︎",
        ChartMode.SURVEY to "≋",
    )
    WpApplicationBar(
        actions = listOf(
            WpAppBarAction("⌂", "home", testTag = "chart-home", onClick = onHome),
        ) + modes.map { (item, symbol) ->
            WpAppBarAction(
                symbol = symbol,
                label = item.name,
                description = "Open ${item.name.lowercase()} mode",
                selected = item == mode,
                onClick = { onMode(item) },
            )
        },
    )
}
