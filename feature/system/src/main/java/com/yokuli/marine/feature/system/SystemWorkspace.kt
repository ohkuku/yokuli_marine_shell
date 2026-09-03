package com.yokuli.marine.feature.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.SystemSection

@Composable
fun SystemWorkspace(section: SystemSection, onHome: () -> Unit) {
    val items = listOf(
        Triple("connections", "NMEA OFF", YokuliColors.Stale),
        Triple("data sources", "POSITION · PHONE", YokuliColors.Cyan),
        Triple("devices", "2 AVAILABLE", YokuliColors.Ocean),
        Triple("display", "DARK · CYAN", YokuliColors.Ocean),
        Triple("safety", "READY", YokuliColors.Safe),
        Triple("storage & diagnostics", "0 CRITICAL", YokuliColors.Ocean),
    )
    Column(Modifier.fillMaxSize().background(YokuliColors.Black).padding(horizontal = 18.dp)) {
        WpText("system", 42, weight = FontWeight.Light, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        items.forEach { (title, value, color) ->
            Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(11.dp).background(color))
                Column(Modifier.padding(start = 13.dp)) {
                    WpText(title, 20, weight = FontWeight.Light)
                    WpText(value, 10, color = YokuliColors.Muted)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.height(YokuliMetrics.AppBarHeight), verticalAlignment = Alignment.CenterVertically) {
            WpCircleButton("⌂", "Home", onHome)
            WpText(section.name.lowercase().replace('_', ' '), 13, color = YokuliColors.Muted, modifier = Modifier.padding(start = 14.dp))
        }
    }
}
