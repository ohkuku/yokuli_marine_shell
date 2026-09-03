package com.yokuli.marine.feature.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.ChartMode

@Composable
fun ChartWorkspace(state: ChartUiState, onAction: (ChartUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    Box(Modifier.fillMaxSize().background(YokuliColors.ChartWater).testTag("chart-workspace-${state.mode.name.lowercase()}")) {
        MarineChartSurface(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            WpPageHeader(
                appKey = "chart",
                appName = stringResource(R.string.app_chart),
                contextLine = modeLabel(state.mode),
                trailing = state.positionText,
                modifier = Modifier.background(colors.background.copy(alpha = .92f)),
            )
            Spacer(Modifier.weight(1f))
            ModeReadout(state, Modifier.wpEntrance(motionKey = state.mode, order = 1))
            ChartAppBar(state.mode, onAction)
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
private fun ModeReadout(state: ChartUiState, modifier: Modifier = Modifier) {
    val colors = LocalWpTheme.current
    val (headline, detail) = when (state.mode) {
        ChartMode.BROWSE -> stringResource(R.string.chart_following) to when {
            state.courseOverGroundDegrees != null && state.speedOverGroundKnots != null -> stringResource(
                R.string.chart_following_detail,
                state.courseOverGroundDegrees,
                state.speedOverGroundKnots,
            )
            else -> stringResource(R.string.chart_data_unavailable)
        }
        ChartMode.NAVIGATE -> when {
            !state.destinationName.isNullOrBlank() &&
                state.distanceToWaypointNm != null &&
                state.bearingTrueDegrees != null -> state.destinationName to stringResource(
                    R.string.chart_navigation_detail,
                    state.distanceToWaypointNm,
                    state.bearingTrueDegrees,
                )
            else -> stringResource(R.string.chart_navigation_no_destination) to
                stringResource(R.string.chart_data_unavailable)
        }
        ChartMode.ANCHOR -> if (state.anchorArmed) {
            stringResource(R.string.chart_anchor_armed) to stringResource(R.string.chart_anchor_active_detail)
        } else {
            stringResource(R.string.chart_anchor_not_armed) to stringResource(R.string.chart_anchor_instruction)
        }
        ChartMode.SURVEY -> stringResource(R.string.chart_survey_ready) to if (state.surveyDepthMeters != null) {
            stringResource(R.string.chart_survey_depth_detail, state.surveyDepthMeters)
        } else {
            stringResource(R.string.chart_survey_detail)
        }
    }
    Column(
        modifier.fillMaxWidth().background(colors.background.copy(alpha = .88f)).padding(14.dp)
            .testTag("chart-primary-state"),
    ) {
        WpText(headline, 22, weight = androidx.compose.ui.text.font.FontWeight.Light)
        WpText(detail, 13, color = colors.muted, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun ChartAppBar(mode: ChartMode, onAction: (ChartUiAction) -> Unit) {
    val modes = listOf(
        ChartMode.BROWSE to "⌖",
        ChartMode.NAVIGATE to "➤",
        ChartMode.ANCHOR to "⚓︎",
        ChartMode.SURVEY to "≋",
    )
    WpApplicationBar(
        actions = listOf(
            WpAppBarAction("⌂", stringResource(R.string.action_home), testTag = "chart-home", onClick = { onAction(ChartUiAction.Home) }),
        ) + modes.map { (item, symbol) ->
            WpAppBarAction(
                symbol = symbol,
                label = modeLabel(item),
                description = stringResource(R.string.action_open_mode, modeLabel(item)),
                selected = item == mode,
                onClick = { onAction(ChartUiAction.SelectMode(item)) },
            )
        },
    )
}

@Composable
private fun modeLabel(mode: ChartMode): String = stringResource(
    when (mode) {
        ChartMode.BROWSE -> R.string.mode_browse
        ChartMode.NAVIGATE -> R.string.mode_navigate
        ChartMode.ANCHOR -> R.string.mode_anchor
        ChartMode.SURVEY -> R.string.mode_survey
    },
)
