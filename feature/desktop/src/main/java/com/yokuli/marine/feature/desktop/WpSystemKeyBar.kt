package com.yokuli.marine.feature.desktop

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.LocalWpTextScale
import com.yokuli.marine.core.design.WpFontFamily
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliBrandMark
import com.yokuli.marine.core.design.wpTilt
import com.yokuli.shell.contract.ShellInput
import com.yokuli.shell.contract.ShellSafeBands
import com.yokuli.shell.contract.ShellWindowMetrics
import com.yokuli.shell.engine.InternalAppTask
import com.yokuli.shell.compose.LauncherEntryUiState

private val DerivedVirtualKeyBarHeight = 54.dp

/**
 * Windows 10 Mobile 的紧凑线形系统键；保留 Yokuli 已确认的 Back / Home / 通知含义。
 * 触控栏沿用 Shell 安全区几何，Android 触感是本机适配，不冒称为原设备的物理测量值。
 */
@Composable
fun WpSystemKeyBar(
    windowMetrics: ShellWindowMetrics,
    onInput: (ShellInput) -> Unit,
    modifier: Modifier = Modifier,
    onNotificationsClick: (() -> Unit)? = null,
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
        ) { YokuliBrandMark(Modifier.size(24.dp), color = Color.White) }
        SystemKey(
            label = stringResource(if (onNotificationsClick != null) R.string.system_notifications else R.string.system_search),
            tag = if (onNotificationsClick != null) "virtual-key-notifications" else "virtual-key-search",
            onClick = { onNotificationsClick?.invoke() ?: onInput(ShellInput.SEARCH) },
        ) {
            if (onNotificationsClick != null) NotificationGlyph() else SearchGlyph()
        }
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
    val pressed by interactions.collectIsPressedAsState()
    Box(
        Modifier.weight(1f).fillMaxSize().testTag(tag)
            .background(if (pressed) Color.White.copy(alpha = .16f) else Color.Transparent)
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
    Canvas(Modifier.size(24.dp)) {
        val stroke = size.minDimension * .0625f
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
private fun NotificationGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val stroke = size.minDimension * .0625f
        val bubble = Path().apply {
            moveTo(size.width * .17f, size.height * .18f)
            lineTo(size.width * .83f, size.height * .18f)
            lineTo(size.width * .83f, size.height * .67f)
            lineTo(size.width * .46f, size.height * .67f)
            lineTo(size.width * .26f, size.height * .84f)
            lineTo(size.width * .26f, size.height * .67f)
            lineTo(size.width * .17f, size.height * .67f)
            close()
        }
        drawPath(bubble, Color.White, style = Stroke(stroke))
        listOf(.35f, .50f).forEach { y ->
            drawLine(Color.White, Offset(size.width * .30f, size.height * y),
                Offset(size.width * .70f, size.height * y), stroke)
        }
    }
}

@Composable
internal fun SearchGlyph(color: Color = Color.White, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = size.minDimension * .0625f
        drawCircle(
            color,
            radius = size.minDimension * .27f,
            center = Offset(size.width * .43f, size.height * .4f),
            style = Stroke(stroke),
        )
        drawLine(
            color,
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
    val searchFieldLabel = stringResource(R.string.search_hint)
    var editor by remember { mutableStateOf(TextFieldValue(searchQuery, TextRange(searchQuery.length))) }
    val pendingQueries = remember { mutableListOf<String>() }
    LaunchedEffect(searchQuery) {
        val acknowledged = pendingQueries.indexOf(searchQuery)
        when {
            searchQuery == editor.text -> pendingQueries.clear()
            acknowledged >= 0 -> pendingQueries.subList(0, acknowledged + 1).clear()
            else -> {
                // A caller may replace or clear Search. Only a query that is not an echo of
                // our own editing replaces the local cursor and IME composition.
                pendingQueries.clear()
                editor = TextFieldValue(searchQuery, TextRange(searchQuery.length))
            }
        }
    }
    val query = editor.text.trim()
    val resultsScroll = rememberScrollState()
    LaunchedEffect(query) { resultsScroll.scrollTo(0) }
    val results = state.entries.filter { entry ->
        query.isEmpty() || entry.title.contains(query, ignoreCase = true) ||
            entry.headline.contains(query, ignoreCase = true)
    }
    val contributedResults = if (query.isEmpty() || query != searchQuery.trim()) emptyList() else state.searchResults
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
            value = editor,
            onValueChange = { next ->
                val textChanged = next.text != editor.text
                // BasicTextField must receive text, selection and composition immediately;
                // the asynchronous Shell reducer cannot own the IME's editing buffer.
                editor = next
                if (textChanged) {
                    pendingQueries += next.text
                    onAction(LauncherUiAction.UpdateSearchQuery(next.text))
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                .heightIn(min = 48.dp).background(colors.foreground.copy(alpha = .1f))
                .padding(horizontal = 12.dp, vertical = 12.dp).focusRequester(focusRequester)
                .semantics { contentDescription = searchFieldLabel }
                .testTag("launcher-search-field"),
            textStyle = androidx.compose.ui.text.TextStyle(color = colors.foreground,
                fontFamily = WpFontFamily, fontSize = (15 * LocalWpTextScale.current).sp),
            cursorBrush = SolidColor(colors.accentText),
            singleLine = true,
            decorationBox = { field ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (editor.text.isEmpty()) WpText(stringResource(R.string.search_hint), 15, color = colors.muted)
                    field()
                }
            },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(resultsScroll).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            results.forEach { entry ->
                val interactions = remember(entry.descriptor.entryId) { MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("search-result-${entry.descriptor.entryId.value}")
                        .wpTilt(interactions)
                        .combinedClickable(
                            interactionSource = interactions,
                            indication = null,
                            onClick = { onAction(LauncherUiAction.Open(entry.descriptor.launchToken)) },
                        ).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).background(colors.accent), contentAlignment = Alignment.Center) {
                        entry.icon.Render(colors.onAccent, Modifier.size(24.dp))
                    }
                    WpText(entry.title, 18, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 2)
                }
            }
            contributedResults.forEach { result ->
                val interactions = remember(result.stableId) { MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .testTag("search-result-${result.stableId}")
                        .wpTilt(interactions)
                        .combinedClickable(
                            interactionSource = interactions,
                            indication = null,
                            onClick = { onAction(LauncherUiAction.Open(result.launchToken)) },
                        ).padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).background(colors.accentText))
                    Column(Modifier.padding(start = 12.dp)) {
                        WpText(result.title, 18, maxLines = 2)
                        if (result.detail.isNotBlank()) WpText(result.detail, 12, color = colors.muted, maxLines = 1)
                    }
                }
            }
            if (results.isEmpty() && contributedResults.isEmpty()) {
                WpText(stringResource(R.string.search_no_results), 15, color = colors.muted)
            }
        }
    }
}

@Composable
fun WpRecentsSurface(
    tasks: List<InternalAppTask>,
    entries: List<LauncherEntryUiState>,
    onActivate: (InternalAppTask) -> Unit,
    onClose: (InternalAppTask) -> Unit,
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
                val closeDescription = stringResource(R.string.recents_close, entry.title)
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
                    WpText(entry.title, 22, color = colors.onAccent, weight = FontWeight.Light)
                    Box(
                        Modifier.align(Alignment.TopEnd).size(48.dp)
                            .testTag("recent-close-${task.appId.value}")
                            .semantics {
                                contentDescription = closeDescription
                                role = Role.Button
                            }
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onClose(task) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        WpText("×", 28, color = colors.onAccent, weight = FontWeight.Light)
                    }
                }
            }
            if (tasks.isEmpty()) WpText(stringResource(R.string.recents_empty), 18, color = colors.muted)
        }
    }
}
