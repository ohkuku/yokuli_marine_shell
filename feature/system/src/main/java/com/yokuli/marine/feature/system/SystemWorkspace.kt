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
    Column(Modifier.fillMaxSize().background(YokuliColors.Black)) {
        WpPageHeader(appName = "system", contextLine = section.name.replace('_', ' '))
        Column(Modifier.weight(1f).padding(horizontal = YokuliMetrics.PageMargin)) {
            items.forEachIndexed { index, (title, value, color) ->
                Row(
                    Modifier.fillMaxWidth().height(58.dp).wpEntrance(section, order = index + 1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(11.dp).background(color))
                    Column(Modifier.padding(start = 13.dp)) {
                        WpText(title, 20, weight = FontWeight.Light)
                        WpText(value, 10, color = YokuliColors.Muted)
                    }
                }
            }
        }
        WpApplicationBar(listOf(WpAppBarAction("⌂", "home", testTag = "system-home", onClick = onHome)))
    }
}
