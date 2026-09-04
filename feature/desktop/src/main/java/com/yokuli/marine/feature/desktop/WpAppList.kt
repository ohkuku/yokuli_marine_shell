package com.yokuli.marine.feature.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.wpTilt
import java.text.Collator
import kotlinx.coroutines.launch

private data class IndexedLauncherEntry(val entry: LauncherEntryUiState, val index: Char)

@Composable
fun WpAppList(
    state: LauncherUiState,
    onAction: (LauncherUiAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    val locale = LocalConfiguration.current.locales[0]
    val collator = remember(locale) { Collator.getInstance(locale) }
    val indexed = state.entries.map { entry ->
        IndexedLauncherEntry(
            entry,
            if (locale.language == "zh") entry.chineseIndex else entry.title.firstOrNull()?.uppercaseChar() ?: '#',
        )
    }.sortedWith { left, right -> collator.compare(left.entry.title, right.entry.title) }
    val groups = indexed.groupBy { it.index }
    val letters = groups.keys.sorted()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jumpVisible by remember { mutableStateOf(false) }
    var contextEntry by remember { mutableStateOf<LauncherEntryUiState?>(null) }
    val groupIndexes = remember(groups) {
        var index = 1
        buildMap {
            groups.forEach { (letter, entries) -> put(letter, index); index += entries.size + 1 }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background).testTag("all-apps-list")) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 14.dp, bottom = 36.dp),
        ) {
            item {
                WpText(
                    stringResource(R.string.page_apps),
                    44,
                    weight = FontWeight.Light,
                    modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
                )
            }
            groups.forEach { (letter, groupEntries) ->
                item {
                    val interactions = remember(letter) { MutableInteractionSource() }
                    Box(
                        Modifier.padding(vertical = 6.dp).size(YokuliMetrics.MinTouch)
                            .wpTilt(interactions, maximumDegrees = 4f).background(colors.accent)
                            .combinedClickable(interactionSource = interactions, indication = null, onClick = { jumpVisible = true }),
                        contentAlignment = Alignment.Center,
                    ) { WpText(letter.lowercase(), 24, color = colors.onAccent, weight = FontWeight.Light) }
                }
                items(groupEntries, key = { it.entry.descriptor.id.value }) { indexedEntry ->
                    val entry = indexedEntry.entry
                    val interactions = remember(entry.descriptor.id) { MutableInteractionSource() }
                    Row(
                        Modifier.fillMaxWidth().height(64.dp)
                            .testTag("launcher-entry-${entry.descriptor.id.value}")
                            .wpTilt(interactions)
                            .combinedClickable(
                                interactionSource = interactions,
                                indication = null,
                                onClick = { onAction(LauncherUiAction.Open(entry.descriptor.launchTarget)) },
                                onLongClick = { contextEntry = entry },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).background(colors.accent), contentAlignment = Alignment.Center) {
                            MarineIcon(entry.icon, colors.onAccent, Modifier.size(25.dp))
                        }
                        WpText(entry.title, 20, weight = FontWeight.Light, modifier = Modifier.padding(start = 14.dp))
                    }
                }
            }
        }
        if (jumpVisible) {
            WpAlphabetJumpOverlay(
                available = letters.toSet(),
                onDismiss = { jumpVisible = false },
                onLetter = { letter ->
                    jumpVisible = false
                    groupIndexes[letter]?.let { index -> scope.launch { listState.animateScrollToItem(index) } }
                },
            )
        }
        contextEntry?.let { entry ->
            WpLauncherContextMenu(
                entry = entry,
                pinned = entry.descriptor.id in state.pinnedEntries,
                onDismiss = { contextEntry = null },
                onTogglePin = {
                    contextEntry = null
                    onAction(LauncherUiAction.TogglePin(entry.descriptor.id))
                },
                onAppInfo = {
                    contextEntry = null
                    onAction(LauncherUiAction.ShowAppInfo(entry.descriptor.id))
                },
            )
        }
    }
}

@Composable
private fun WpLauncherContextMenu(
    entry: LauncherEntryUiState,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onAppInfo: () -> Unit,
) {
    val colors = LocalWpTheme.current
    Box(
        Modifier.fillMaxSize().background(colors.background.copy(alpha = .96f))
            .testTag("launcher-context-menu").contextClick(onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
            WpText(entry.title, 34, weight = FontWeight.Light, modifier = Modifier.padding(bottom = 20.dp))
            ContextAction(
                title = stringResource(if (pinned) R.string.context_unpin else R.string.context_pin),
                icon = if (pinned) MarineIconKind.UNPIN else MarineIconKind.PIN,
                tag = "launcher-context-pin",
                onClick = onTogglePin,
            )
            ContextAction(
                title = stringResource(R.string.context_app_info),
                icon = MarineIconKind.INFO,
                tag = "launcher-context-app-info",
                onClick = onAppInfo,
            )
        }
    }
}

@Composable
private fun ContextAction(title: String, icon: MarineIconKind, tag: String, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().height(64.dp).testTag(tag).wpTilt(interactions)
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarineIcon(icon, colors.accent, Modifier.size(28.dp))
        WpText(title, 20, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
fun WpAlphabetJumpOverlay(available: Set<Char>, onDismiss: () -> Unit, onLetter: (Char) -> Unit) {
    val colors = LocalWpTheme.current
    val letters = ('A'..'Z').toList() + '#'
    Box(
        Modifier.fillMaxSize().background(colors.background.copy(alpha = .97f)).contextClick(onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            letters.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { letter ->
                        val active = letter in available
                        val interactions = remember(letter) { MutableInteractionSource() }
                        Box(
                            Modifier.size(YokuliMetrics.MinTouch)
                                .wpTilt(interactions, enabled = active, maximumDegrees = 4f)
                                .background(if (active) colors.accent else colors.muted.copy(alpha = .24f))
                                .combinedClickable(
                                    enabled = active,
                                    interactionSource = interactions,
                                    indication = null,
                                    onClick = { onLetter(letter) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) { WpText(letter.lowercase(), 22, color = if (active) colors.onAccent else colors.muted.copy(alpha = .55f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.contextClick(onClick: () -> Unit): Modifier = combinedClickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
)
