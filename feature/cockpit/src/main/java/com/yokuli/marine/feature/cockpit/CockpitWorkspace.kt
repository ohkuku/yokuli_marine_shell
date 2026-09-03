package com.yokuli.marine.feature.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.CockpitPage

@Composable
fun CockpitWorkspace(page: CockpitPage, onHome: () -> Unit) {
    val colors = LocalWpTheme.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        WpPageHeader(appName = "cockpit", contextLine = page.name)
        Column(Modifier.weight(1f).padding(horizontal = YokuliMetrics.PageMargin).wpEntrance(page, order = 1)) {
            Row(Modifier.fillMaxWidth()) {
                Instrument("SOG", "6.2", "kn", Modifier.weight(1f))
                Instrument("HDG", "184", "°T", Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth()) {
                Instrument("TWS", "12.4", "kn", Modifier.weight(1f))
                Instrument("DEPTH", "—", "STALE", Modifier.weight(1f))
            }
        }
        WpApplicationBar(listOf(WpAppBarAction("⌂", "home", testTag = "cockpit-home", onClick = onHome)))
    }
}

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
