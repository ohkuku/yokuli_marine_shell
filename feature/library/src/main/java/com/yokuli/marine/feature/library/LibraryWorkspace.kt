package com.yokuli.marine.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.LibrarySection

@Composable
fun LibraryWorkspace(section: LibrarySection, onHome: () -> Unit) {
    val sections = listOf("places" to "12", "routes" to "3", "trips" to "27", "anchors" to "18", "surveys" to "4")
    Column(Modifier.fillMaxSize().background(YokuliColors.Black).padding(horizontal = 18.dp)) {
        WpText("library", 42, weight = FontWeight.Light, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
        sections.forEach { (name, count) ->
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(if (name.uppercase() == section.name) YokuliColors.Cyan else YokuliColors.Ocean), contentAlignment = Alignment.Center) {
                    WpText(count, 17)
                }
                WpText(name, 22, weight = FontWeight.Light, modifier = Modifier.padding(start = 14.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.height(YokuliMetrics.AppBarHeight), verticalAlignment = Alignment.CenterVertically) { WpCircleButton("⌂", "Home", onHome) }
    }
}
