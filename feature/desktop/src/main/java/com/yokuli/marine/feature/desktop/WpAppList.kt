package com.yokuli.marine.feature.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.LaunchTarget
import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.model.LauncherEntryKind
import com.yokuli.marine.core.shell.LauncherRegistry
import kotlinx.coroutines.launch

@Composable
fun WpAppList(
    onOpen: (LaunchTarget) -> Unit,
    pinnedEntries: Set<LauncherEntryId> = emptySet(),
    onPinToggle: (LauncherEntryId) -> Unit = {},
) {
    val entries = remember { LauncherRegistry.entries.sortedBy { it.title } }
    val groups = remember(entries) { entries.groupBy { it.title.first().uppercaseChar() } }
    val letters = groups.keys.sorted()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jumpVisible by remember { mutableStateOf(false) }
    val groupIndexes = remember(groups) {
        var index = 1
        buildMap {
            groups.forEach { (letter, items) -> put(letter, index); index += items.size + 1 }
        }
    }

    Box(Modifier.fillMaxSize().background(YokuliColors.Black).testTag("all-apps-list")) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 14.dp, bottom = 36.dp),
        ) {
            item { WpText("apps", 44, weight = FontWeight.Light, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)) }
            groups.forEach { (letter, items) ->
                item {
                    Box(
                        Modifier.padding(vertical = 7.dp).size(42.dp).background(YokuliColors.Cyan).wpClick { jumpVisible = true },
                        contentAlignment = Alignment.Center,
                    ) { WpText(letter.lowercase(), 24, weight = FontWeight.Light) }
                }
                items(items.size) { itemIndex ->
                    val entry = items[itemIndex]
                    val interactions = remember(entry.id) { MutableInteractionSource() }
                    Row(
                        Modifier.fillMaxWidth().height(58.dp).wpTilt(interactions).combinedClickable(
                            interactionSource = interactions,
                            indication = null,
                            onClick = { onOpen(entry.launchTarget) },
                            onLongClick = { onPinToggle(entry.id) },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(42.dp).background(if (entry.kind == LauncherEntryKind.CORE_APP) YokuliColors.Cyan else YokuliColors.DeepOcean), contentAlignment = Alignment.Center) {
                            WpText(entry.symbol, 23)
                        }
                        Column(Modifier.padding(start = 14.dp)) {
                            WpText(entry.title, 20, weight = FontWeight.Light)
                            val kind = if (entry.kind == LauncherEntryKind.CORE_APP) "core app" else "shortcut"
                            val pinned = if (entry.id in pinnedEntries) " · pinned" else " · hold to pin"
                            WpText(kind + pinned, 10, color = YokuliColors.Muted)
                        }
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
    }
}

@Composable
fun WpAlphabetJumpOverlay(available: Set<Char>, onDismiss: () -> Unit, onLetter: (Char) -> Unit) {
    val letters = ('A'..'Z').toList() + '#'
    Box(Modifier.fillMaxSize().background(YokuliColors.Black.copy(alpha = .96f)).wpClick(onDismiss), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            letters.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { letter ->
                        val active = letter in available
                        Box(
                            Modifier.size(46.dp).background(if (active) YokuliColors.Cyan else YokuliColors.Stale.copy(alpha = .35f))
                                .wpClick { if (active) onLetter(letter) },
                            contentAlignment = Alignment.Center,
                        ) { WpText(letter.lowercase(), 22, color = if (active) YokuliColors.White else YokuliColors.Muted.copy(alpha = .45f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.wpClick(onClick: () -> Unit): Modifier = combinedClickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
)
