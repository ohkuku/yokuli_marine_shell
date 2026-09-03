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
    Column(Modifier.fillMaxSize().background(YokuliColors.Black).padding(horizontal = 18.dp)) {
        WpText("cockpit", 42, weight = FontWeight.Light, modifier = Modifier.padding(top = 12.dp, bottom = 18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Instrument("SOG", "6.2", "kn")
            Instrument("HDG", "184", "°T")
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Instrument("TWS", "12.4", "kn")
            Instrument("DEPTH", "—", "STALE")
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(YokuliMetrics.AppBarHeight), verticalAlignment = Alignment.CenterVertically) {
            WpCircleButton("⌂", "Home", onHome)
            WpText(page.name.lowercase(), 13, color = YokuliColors.Muted, modifier = Modifier.padding(start = 14.dp))
        }
    }
}

@Composable
private fun Instrument(label: String, value: String, unit: String) {
    Column(Modifier.width(145.dp)) {
        WpText(label, 13, color = YokuliColors.Cyan)
        Row(verticalAlignment = Alignment.Bottom) {
            WpText(value, 54, weight = FontWeight.Light)
            WpText(unit, 15, color = YokuliColors.Muted, modifier = Modifier.padding(start = 5.dp, bottom = 8.dp))
        }
    }
}
