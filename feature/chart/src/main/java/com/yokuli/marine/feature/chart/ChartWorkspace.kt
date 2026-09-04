package com.yokuli.marine.feature.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliColors

typealias MarineChartSurface = @Composable (Modifier) -> Unit

@Composable
fun ChartWorkspace(
    state: ChartUiState,
    onAction: (ChartUiAction) -> Unit,
    chartSurface: MarineChartSurface,
) {
    val colors = LocalWpTheme.current
    Box(
        Modifier.fillMaxSize()
            .background(if (state.mapConfigured) colors.background else YokuliColors.ChartWater)
            .testTag("chart-workspace-browse"),
    ) {
        chartSurface(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            WpPageHeader(
                appKey = "chart",
                appName = stringResource(R.string.app_chart),
                contextLine = stringResource(
                    if (state.mapConfigured) R.string.chart_context_google else R.string.chart_context_demo,
                ),
                modifier = Modifier.background(colors.background.copy(alpha = .92f)),
            )
            Spacer(Modifier.weight(1f))
            if (state.mapConfigured) {
                ChartTruthBanner(
                    headline = stringResource(R.string.chart_map_configured),
                    detail = stringResource(R.string.chart_browse_only),
                    modifier = Modifier.testTag("chart-map-configured"),
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().background(colors.background.copy(alpha = .94f))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .testTag("chart-map-unconfigured"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WpText(stringResource(R.string.chart_demo_label), 14, color = colors.accent, weight = FontWeight.Bold)
                    WpText(stringResource(R.string.chart_map_unconfigured), 24, weight = FontWeight.Light)
                    WpText(stringResource(R.string.chart_map_unconfigured_detail), 12, color = colors.muted)
                    Box(
                        Modifier.padding(top = 8.dp).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onAction(ChartUiAction.OpenMapSettings) },
                        )
                            .testTag("chart-open-map-settings"),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        WpText(stringResource(R.string.chart_open_settings), 15, color = colors.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartTruthBanner(headline: String, detail: String, modifier: Modifier = Modifier) {
    val colors = LocalWpTheme.current
    Column(
        modifier.fillMaxWidth().background(colors.background.copy(alpha = .9f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        WpText(headline, 20, weight = FontWeight.Light)
        WpText(detail, 11, color = colors.muted, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Permanently labeled non-navigational fallback; it contains no vessel, route, or marine fact. */
@Composable
fun MarineChartDemoSurface(modifier: Modifier = Modifier) {
    Canvas(modifier.testTag("chart-demo-canvas")) {
        drawRect(YokuliColors.ChartWater)
        val abstractLand = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width * .72f, 0f)
            cubicTo(size.width * .56f, size.height * .10f, size.width * .45f, size.height * .18f, 0f, size.height * .28f)
            close()
        }
        drawPath(abstractLand, YokuliColors.ChartLand)
    }
}

/** Lightweight transition plane used until the shell navigation motion settles. */
@Composable
fun MarineChartTransitionSurface(modifier: Modifier = Modifier) {
    Box(modifier.background(YokuliColors.ChartWater).testTag("chart-map-transition-plane"))
}
