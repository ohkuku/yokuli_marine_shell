package com.yokuli.marine.feature.desktop

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.wpTilt
import com.yokuli.shell.contract.ShellInput
import com.yokuli.shell.contract.ShellSafeBands
import com.yokuli.shell.contract.ShellWindowMetrics
import com.yokuli.shell.engine.InternalAppTask

private val DerivedVirtualKeyBarHeight = 54.dp

/**
 * The recording proves only the Back/Start/Search glyph family. The on-screen
 * height and Android platform haptic are DERIVED_UNVERIFIED product adaptations;
 * there is deliberately no invented WP key-light or press animation.
 */
@Composable
fun WpSystemKeyBar(
    windowMetrics: ShellWindowMetrics,
    onInput: (ShellInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bands = ShellSafeBands.resolve(windowMetrics)
    val density = windowMetrics.density.coerceAtLeast(1f)
    val navigation = bands.navigation
    Row(
        modifier.zIndex(1f).graphicsLayer().fillMaxWidth()
            .padding(bottom = (bands.imeLiftPx / density).dp)
            .height(DerivedVirtualKeyBarHeight + (navigation.bottom / density).dp)
            .background(Color.Black).testTag("wp-system-key-bar")
            .padding(
                start = (navigation.left / density).dp,
                end = (navigation.right / density).dp,
                bottom = (navigation.bottom / density).dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SystemKey(
            label = stringResource(R.string.system_back),
            tag = "virtual-key-back",
            onClick = { onInput(ShellInput.BACK) },
            onLongClick = { onInput(ShellInput.RECENTS) },
        ) { BackGlyph() }
        SystemKey(
            label = stringResource(R.string.system_bridge),
            tag = "virtual-key-bridge",
            onClick = { onInput(ShellInput.DESKTOP) },
        ) { CompassBridgeGlyph() }
        SystemKey(
            label = stringResource(R.string.system_search),
            tag = "virtual-key-search",
            onClick = { onInput(ShellInput.SEARCH) },
        ) { SearchGlyph() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.SystemKey(
    label: String,
    tag: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    glyph: @Composable () -> Unit,
) {
    val view = LocalView.current
    val interactions = remember { MutableInteractionSource() }
    Box(
        Modifier.weight(1f).fillMaxSize().testTag(tag)
            .semantics { contentDescription = label; role = Role.Button }
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
                onLongClick = onLongClick?.let { longClick ->
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        longClick()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

@Composable
private fun BackGlyph() {
    Canvas(Modifier.size(30.dp)) {
        val stroke = size.minDimension * .09f
        val path = Path().apply {
            moveTo(size.width * .72f, size.height * .24f)
            lineTo(size.width * .34f, size.height * .5f)
            lineTo(size.width * .72f, size.height * .76f)
        }
        drawPath(path, Color.White, style = Stroke(stroke))
        drawLine(
            Color.White,
            Offset(size.width * .35f, size.height * .5f),
            Offset(size.width * .86f, size.height * .5f),
            strokeWidth = stroke,
        )
    }
}

@Composable
private fun CompassBridgeGlyph() {
    Canvas(Modifier.size(29.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * .39f
        val stroke = size.minDimension * .055f
        drawCircle(Color.White, radius, center, style = Stroke(stroke))
        val rose = Path().apply {
            moveTo(center.x, center.y - radius * .82f)
            lineTo(center.x + radius * .23f, center.y)
            lineTo(center.x, center.y + radius * .82f)
            lineTo(center.x - radius * .23f, center.y)
            close()
            moveTo(center.x - radius * .82f, center.y)
            lineTo(center.x, center.y - radius * .23f)
            lineTo(center.x + radius * .82f, center.y)
            lineTo(center.x, center.y + radius * .23f)
            close()
        }
        drawPath(rose, Color.White, style = Stroke(stroke))
        drawCircle(Color.White, radius = stroke * .9f, center = center)
    }
}

@Composable
private fun SearchGlyph() {
    Canvas(Modifier.size(29.dp)) {
        val stroke = size.minDimension * .08f
        drawCircle(
            Color.White,
            radius = size.minDimension * .27f,
            center = Offset(size.width * .43f, size.height * .4f),
            style = Stroke(stroke),
        )
        drawLine(
            Color.White,
            Offset(size.width * .62f, size.height * .6f),
            Offset(size.width * .84f, size.height * .83f),
            strokeWidth = stroke,
        )
    }
}

@Composable
fun WpSearchSurface(
    state: LauncherUiState,
    searchQuery: String,
    onAction: (LauncherUiAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    val focusRequester = remember { FocusRequester() }
    val query = searchQuery.trim()
    val results = state.entries.filter { entry ->
        query.isEmpty() || entry.title.contains(query, ignoreCase = true) ||
            entry.headline.contains(query, ignoreCase = true)
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        Modifier.fillMaxSize().background(colors.background).testTag("shell-search-surface"),
    ) {
        WpPageHeader(
            appKey = "search",
            appName = stringResource(R.string.search_title),
            contextLine = stringResource(R.string.search_installed_apps),
        )
        BasicTextField(
            value = searchQuery,
            onValueChange = { onAction(LauncherUiAction.UpdateSearchQuery(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                .height(52.dp).background(colors.foreground.copy(alpha = .1f))
                .padding(horizontal = 12.dp).focusRequester(focusRequester)
                .testTag("launcher-search-field"),
            textStyle = androidx.compose.ui.text.TextStyle(color = colors.foreground),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            decorationBox = { field ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) WpText(stringResource(R.string.search_hint), 18, color = colors.muted)
                    field()
                }
            },
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            results.forEach { entry ->
                val interactions = remember(entry.descriptor.entryId) { MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth().height(58.dp)
                        .testTag("search-result-${entry.descriptor.entryId.value}")
                        .wpTilt(interactions)
                        .combinedClickable(
                            interactionSource = interactions,
                            indication = null,
                            onClick = { onAction(LauncherUiAction.Open(entry.descriptor.launchToken)) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).background(colors.accent))
                    WpText(entry.title, 21, modifier = Modifier.padding(start = 12.dp))
                }
            }
            if (results.isEmpty()) WpText(stringResource(R.string.search_no_results), 18, color = colors.muted)
        }
    }
}

@Composable
fun WpRecentsSurface(
    tasks: List<InternalAppTask>,
    entries: List<LauncherEntryUiState>,
    onActivate: (InternalAppTask) -> Unit,
) {
    val colors = LocalWpTheme.current
    val entryByApp = entries.associateBy { it.descriptor.appId }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("launcher-recents")) {
        WpPageHeader(
            appKey = "recents",
            appName = stringResource(R.string.recents_title),
            contextLine = stringResource(R.string.recents_context),
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tasks.asReversed().forEach { task ->
                val entry = entryByApp[task.appId] ?: return@forEach
                val interactions = remember(task.taskId) { MutableInteractionSource() }
                Box(
                    Modifier.fillMaxWidth().height(108.dp).background(colors.accent)
                        .testTag("recent-task-${task.appId.value}")
                        .wpTilt(interactions)
                        .combinedClickable(
                            interactionSource = interactions,
                            indication = null,
                            onClick = { onActivate(task) },
                        )
                        .padding(14.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    WpText(entry.title, 22, color = Color.White, weight = FontWeight.Light)
                }
            }
            if (tasks.isEmpty()) WpText(stringResource(R.string.recents_empty), 18, color = colors.muted)
        }
    }
}
