package com.yokuli.marine.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.LibrarySection

@Composable
fun LibraryWorkspace(state: LibraryUiState, onAction: (LibraryUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        WpPageHeader(appKey = "library", appName = stringResource(R.string.app_library), contextLine = sectionLabel(state.section))
        Column(Modifier.weight(1f).padding(horizontal = YokuliMetrics.PageMargin)) {
            LibrarySection.entries.forEachIndexed { index, section ->
                LibrarySectionRow(section, state.counts[section] ?: 0, index + 1) {
                    onAction(LibraryUiAction.SelectSection(section))
                }
            }
        }
        WpApplicationBar(
            listOf(WpAppBarAction("⌂", stringResource(R.string.action_home), testTag = "library-home", onClick = { onAction(LibraryUiAction.Home) })),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibrarySectionRow(section: LibrarySection, count: Int, order: Int, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().height(64.dp)
            .testTag("library-section-${section.name.lowercase()}")
            .wpEntrance(section, order = order)
            .wpTilt(interactions)
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(colors.accent), contentAlignment = Alignment.Center) {
            WpText(count.toString(), 17, color = colors.onAccent)
        }
        WpText(sectionLabel(section), 22, weight = FontWeight.Light, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun sectionLabel(section: LibrarySection): String = stringResource(
    when (section) {
        LibrarySection.PLACES -> R.string.section_places
        LibrarySection.ROUTES -> R.string.section_routes
        LibrarySection.TRIPS -> R.string.section_trips
        LibrarySection.ANCHORS -> R.string.section_anchors
        LibrarySection.SURVEYS -> R.string.section_surveys
    },
)
