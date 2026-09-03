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
    val colors = LocalWpTheme.current
    val sections = listOf("places" to "12", "routes" to "3", "trips" to "27", "anchors" to "18", "surveys" to "4")
    Column(Modifier.fillMaxSize().background(colors.background)) {
        WpPageHeader(appName = "library", contextLine = section.name.replace('_', ' '))
        Column(Modifier.weight(1f).padding(horizontal = YokuliMetrics.PageMargin)) {
            sections.forEachIndexed { index, (name, count) ->
                Row(
                    Modifier.fillMaxWidth().height(64.dp).wpEntrance(section, order = index + 1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).background(colors.accent), contentAlignment = Alignment.Center) {
                        WpText(count, 17, color = colors.onAccent)
                    }
                    WpText(name, 22, weight = FontWeight.Light, modifier = Modifier.padding(start = 14.dp))
                }
            }
        }
        WpApplicationBar(listOf(WpAppBarAction("⌂", "home", testTag = "library-home", onClick = onHome)))
    }
}
