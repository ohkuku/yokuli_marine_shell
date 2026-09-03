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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.LauncherEntryDescriptor
import com.yokuli.marine.core.model.LauncherEntryKind
import com.yokuli.marine.core.shell.LauncherRegistry
import java.text.Collator
import kotlinx.coroutines.launch

private data class LocalizedLauncherEntry(
    val descriptor: LauncherEntryDescriptor,
    val visual: LauncherEntryVisual,
    val title: String,
    val index: Char,
)

@Composable
fun WpAppList(
    state: LauncherUiState = LauncherUiFixtures.state(),
    onAction: (LauncherUiAction) -> Unit = {},
) {
    val colors = LocalWpTheme.current
    val locale = LocalConfiguration.current.locales[0]
    val localizedEntries = LauncherRegistry.entries.map { descriptor ->
        val visual = LauncherVisualCatalog.get(descriptor.id)
        val title = stringResource(visual.titleRes)
        LocalizedLauncherEntry(
            descriptor = descriptor,
            visual = visual,
            title = title,
            index = if (locale.language == "zh") visual.chineseIndex else title.firstOrNull()?.uppercaseChar() ?: '#',
        )
    }
    val collator = remember(locale) { Collator.getInstance(locale) }
    val entries = localizedEntries.sortedWith { left, right -> collator.compare(left.title, right.title) }
    val groups = entries.groupBy { it.index }
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

    Box(Modifier.fillMaxSize().background(colors.background).testTag("all-apps-list")) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 14.dp, bottom = 36.dp),
        ) {
            item { WpText(stringResource(R.string.page_apps), 44, weight = FontWeight.Light, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)) }
            groups.forEach { (letter, items) ->
                item {
                    val interactions = remember(letter) { MutableInteractionSource() }
                    Box(
                        Modifier.padding(vertical = 6.dp).size(YokuliMetrics.MinTouch)
                            .wpTilt(interactions, maximumDegrees = 4f)
                            .background(colors.accent)
                            .combinedClickable(
                                interactionSource = interactions,
                                indication = null,
                                onClick = { jumpVisible = true },
                            ),
                        contentAlignment = Alignment.Center,
                    ) { WpText(letter.lowercase(), 24, color = colors.onAccent, weight = FontWeight.Light) }
                }
                items(items.size) { itemIndex ->
                    val item = items[itemIndex]
                    val entry = item.descriptor
                    val interactions = remember(entry.id) { MutableInteractionSource() }
                    Row(
                        Modifier.fillMaxWidth().height(64.dp)
                            .testTag("launcher-entry-${entry.id.value}")
                            .wpTilt(interactions)
                            .combinedClickable(
                                interactionSource = interactions,
                                indication = null,
                                onClick = { onAction(LauncherUiAction.Open(entry.launchTarget)) },
                                onLongClick = { onAction(LauncherUiAction.TogglePin(entry.id)) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).background(colors.accent), contentAlignment = Alignment.Center) {
                            WpText(item.visual.glyph, 23, color = colors.onAccent)
                        }
                        Column(Modifier.padding(start = 14.dp)) {
                            WpText(item.title, 20, weight = FontWeight.Light)
                            val kind = stringResource(
                                if (entry.kind == LauncherEntryKind.CORE_APP) R.string.launcher_kind_core else R.string.launcher_kind_shortcut,
                            )
                            val metadata = stringResource(
                                if (entry.id in state.pinnedEntries) R.string.launcher_meta_pinned else R.string.launcher_meta_hold_to_pin,
                                kind,
                            )
                            WpText(metadata, 10, color = colors.muted)
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
    val colors = LocalWpTheme.current
    val letters = ('A'..'Z').toList() + '#'
    Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = .97f)).wpClick(onDismiss), contentAlignment = Alignment.Center) {
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
                        ) {
                            WpText(
                                letter.lowercase(),
                                22,
                                color = if (active) colors.onAccent else colors.muted.copy(alpha = .55f),
                            )
                        }
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
