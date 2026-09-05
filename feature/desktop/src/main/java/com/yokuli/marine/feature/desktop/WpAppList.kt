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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.engine.LauncherTransient
import com.yokuli.shell.compose.LauncherEntryUiState
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
                            .testTag("alphabet-group-${letter.lowercaseChar()}")
                            .wpTilt(interactions, maximumDegrees = 4f).background(colors.accent)
                            .combinedClickable(
                                interactionSource = interactions,
                                indication = null,
                                onClick = { onAction(LauncherUiAction.OpenAlphabetJump) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) { WpText(letter.lowercase(), 24, color = colors.onAccent, weight = FontWeight.Light) }
                }
                items(groupEntries, key = { it.entry.descriptor.entryId.value }) { indexedEntry ->
                    val entry = indexedEntry.entry
                    val interactions = remember(entry.descriptor.entryId) { MutableInteractionSource() }
                    Row(
                        Modifier.fillMaxWidth().height(64.dp)
                            .testTag("launcher-entry-${entry.descriptor.entryId.value}")
                            .wpTilt(interactions)
                            .combinedClickable(
                                interactionSource = interactions,
                                indication = null,
                                onClick = { onAction(LauncherUiAction.Open(entry.descriptor.launchToken)) },
                                onLongClick = {
                                    onAction(LauncherUiAction.OpenEntryContextMenu(entry.descriptor.entryId))
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).background(colors.accent), contentAlignment = Alignment.Center) {
                            entry.icon.Render(colors.onAccent, Modifier.size(25.dp))
                        }
                        WpText(entry.title, 20, weight = FontWeight.Light, modifier = Modifier.padding(start = 14.dp))
                    }
                }
            }
        }
        if (state.transient == LauncherTransient.AlphabetJump) {
            WpAlphabetJumpOverlay(
                available = letters.toSet(),
                onDismiss = { onAction(LauncherUiAction.DismissTransient) },
                onLetter = { letter ->
                    onAction(LauncherUiAction.DismissTransient)
                    groupIndexes[letter]?.let { index -> scope.launch { listState.animateScrollToItem(index) } }
                },
            )
        }
        val contextEntry = (state.transient as? LauncherTransient.ContextMenu)?.entryId?.let { entryId ->
            state.entries.firstOrNull { it.descriptor.entryId == entryId }
        }
        contextEntry?.let { entry ->
            val placement = state.document.placements.firstOrNull { it.entryId == entry.descriptor.entryId }
            WpLauncherContextMenu(
                entry = entry,
                pinned = placement != null,
                pinActionAvailable = entry.descriptor.pinPolicy == PinPolicy.PINNABLE,
                onDismiss = { onAction(LauncherUiAction.DismissTransient) },
                onPinAction = {
                    if (placement == null) {
                        onAction(LauncherUiAction.PinEntry(entry.descriptor.entryId))
                    } else {
                        onAction(LauncherUiAction.UnpinTile(placement.tileId))
                    }
                },
                onAppInfo = {
                    onAction(LauncherUiAction.DismissTransient)
                    onAction(LauncherUiAction.ShowAppInfo(entry.descriptor.entryId))
                },
            )
        }
        WpLauncherFeedback(state.transient, onAction)
    }
}

@Composable
private fun WpLauncherContextMenu(
    entry: LauncherEntryUiState,
    pinned: Boolean,
    pinActionAvailable: Boolean,
    onDismiss: () -> Unit,
    onPinAction: () -> Unit,
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
            if (pinActionAvailable) {
                ContextAction(
                    title = stringResource(if (pinned) R.string.context_unpin else R.string.context_pin),
                    icon = if (pinned) MarineIconKind.UNPIN else MarineIconKind.PIN,
                    tag = "launcher-context-pin",
                    onClick = onPinAction,
                )
            }
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
        Modifier.fillMaxSize().background(colors.background.copy(alpha = .97f))
            .testTag("alphabet-jump-overlay").contextClick(onDismiss),
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
