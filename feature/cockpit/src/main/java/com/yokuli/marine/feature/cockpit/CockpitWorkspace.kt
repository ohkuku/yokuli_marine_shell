package com.yokuli.marine.feature.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.CockpitPage
import com.yokuli.marine.core.model.DataFreshness
import java.util.Locale

@Composable
fun CockpitWorkspace(state: CockpitUiState, onAction: (CockpitUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        WpPageHeader(appKey = "cockpit", appName = stringResource(R.string.app_cockpit), contextLine = pageLabel(state.page))
        Column(Modifier.weight(1f).padding(horizontal = YokuliMetrics.PageMargin).wpEntrance(state.page, order = 1)) {
            Row(Modifier.fillMaxWidth()) {
                Instrument(stringResource(R.string.instrument_sog), state.speedOverGroundKnots.decimal(), stringResource(R.string.unit_knots), Modifier.weight(1f))
                Instrument(stringResource(R.string.instrument_heading), state.headingTrueDegrees?.toString() ?: "—", stringResource(R.string.unit_true_degrees), Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth()) {
                Instrument(stringResource(R.string.instrument_tws), state.trueWindSpeedKnots.decimal(), stringResource(R.string.unit_knots), Modifier.weight(1f))
                Instrument(
                    stringResource(R.string.instrument_depth),
                    state.depthMeters.decimal(),
                    if (state.depthFreshness == DataFreshness.STALE) stringResource(R.string.state_stale) else stringResource(R.string.unit_meters),
                    Modifier.weight(1f),
                )
            }
        }
        WpApplicationBar(
            listOf(WpAppBarAction("⌂", stringResource(R.string.action_home), testTag = "cockpit-home", onClick = { onAction(CockpitUiAction.Home) })),
        )
    }
}

@Composable
private fun pageLabel(page: CockpitPage): String = stringResource(
    when (page) {
        CockpitPage.OVERVIEW -> R.string.page_overview
        CockpitPage.SAILING -> R.string.page_sailing
        CockpitPage.NAVIGATION -> R.string.page_navigation
        CockpitPage.MOTION -> R.string.page_motion
        CockpitPage.WEATHER -> R.string.page_weather
    },
)

private fun Double?.decimal(): String = this?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"

@Composable
private fun Instrument(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val colors = LocalWpTheme.current
    Column(modifier) {
        WpText(label, 13, color = colors.accent)
        Row(verticalAlignment = Alignment.Bottom) {
            WpText(value, 54, weight = FontWeight.Light)
            WpText(unit, 15, color = colors.muted, modifier = Modifier.padding(start = 5.dp, bottom = 8.dp))
        }
    }
}
