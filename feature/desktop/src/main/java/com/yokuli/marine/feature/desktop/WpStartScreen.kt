package com.yokuli.marine.feature.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.*
import com.yokuli.marine.core.shell.DesktopLayoutEditor
import com.yokuli.marine.core.shell.LauncherRegistry
import kotlin.math.roundToInt

@Composable
fun YokuliStartScreen(
    state: LauncherUiState = LauncherUiFixtures.state(),
    onAction: (LauncherUiAction) -> Unit = {},
) {
    val colors = LocalWpTheme.current
    val entries = remember { LauncherRegistry.entries.associateBy { it.id.value } }
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(colors.background)
            .testTag("start-screen")
            .semantics { wpThemeModeName = colors.spec.mode.name.lowercase() },
    ) {
        val width = maxWidth.coerceAtMost(560.dp)
        val gridWidth = width - YokuliMetrics.OuterMargin * 2
        val layout = state.layout
        val cell = (gridWidth - YokuliMetrics.TileGap * (layout.columns - 1)) / layout.columns
        val pitch = cell + YokuliMetrics.TileGap
        val rows = layout.placements.maxOfOrNull { it.row + it.size.rows } ?: 0
        val gridHeight = if (rows == 0) 0.dp else cell * rows + YokuliMetrics.TileGap * (rows - 1)
        Column(
            Modifier.width(width).fillMaxHeight().align(Alignment.TopCenter).verticalScroll(scroll)
                .padding(YokuliMetrics.OuterMargin).testTag("start-grid"),
            verticalArrangement = Arrangement.spacedBy(YokuliMetrics.TileGap),
        ) {
            Box(Modifier.width(gridWidth).height(gridHeight)) {
                layout.placements.forEach { placement ->
                    key(placement.tileId.value) {
                        val entry = entries.getValue(placement.entryId.value)
                        WpTile(
                            entry = entry,
                            visual = LauncherVisualCatalog.get(entry.id),
                            width = cell * placement.size.columns + YokuliMetrics.TileGap * (placement.size.columns - 1),
                            height = cell * placement.size.rows + YokuliMetrics.TileGap * (placement.size.rows - 1),
                            presentation = state.tiles.getValue(entry.id),
                            editing = editing,
                            selected = selected == placement.tileId.value,
                            onClick = {
                                if (editing) selected = placement.tileId.value else onAction(LauncherUiAction.Open(entry.launchTarget))
                            },
                            onLongClick = { editing = true; selected = placement.tileId.value },
                            onUnpin = {
                                onAction(LauncherUiAction.ChangeLayout(DesktopLayoutEditor.unpin(layout, placement.tileId)))
                                selected = null
                                editing = false
                            },
                            onResize = { onAction(LauncherUiAction.ChangeLayout(DesktopLayoutEditor.resize(layout, placement.tileId))) },
                            onMove = { delta ->
                                val pitchPx = with(density) { pitch.toPx() }
                                val targetColumn = (placement.column + delta.x / pitchPx).roundToInt().coerceIn(0, layout.columns - 1)
                                val targetRow = (placement.row + delta.y / pitchPx).roundToInt().coerceAtLeast(0)
                                val targetKey = targetRow * layout.columns + targetColumn
                                val before = layout.placements
                                    .filterNot { it.tileId == placement.tileId }
                                    .sortedBy { it.row * layout.columns + it.column }
                                    .firstOrNull { it.row * layout.columns + it.column >= targetKey }
                                val moved = if (before == null) {
                                    DesktopLayoutEditor.moveToEnd(layout, placement.tileId)
                                } else {
                                    DesktopLayoutEditor.moveBefore(layout, placement.tileId, before.tileId)
                                }
                                onAction(LauncherUiAction.ChangeLayout(moved))
                            },
                            chartPreview = entry.id.value == "chart",
                            modifier = Modifier.offset(x = pitch * placement.column, y = pitch * placement.row),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.size(48.dp).testTag("all-apps-entry")
                        .combinedNoRipple(
                            onClick = if (editing) ({ editing = false; selected = null }) else ({ onAction(LauncherUiAction.ShowAllApps) }),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    WpText(if (editing) "✓" else "→", 28, weight = FontWeight.Light)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WpTile(
    entry: LauncherEntryDescriptor,
    visual: LauncherEntryVisual,
    width: Dp,
    height: Dp,
    presentation: LauncherTileUiState,
    editing: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUnpin: () -> Unit,
    onResize: () -> Unit,
    onMove: (Offset) -> Unit,
    chartPreview: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWpTheme.current
    val headline = stringResource(presentation.headlineRes)
    val detail = stringResource(presentation.detailRes)
    val title = stringResource(visual.titleRes)
    val interactions = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (selected) 1.025f else 1f, tween(95), label = "wp-tile-selected")
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val dragModifier = if (editing && selected) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = { onMove(dragOffset); dragOffset = Offset.Zero },
                onDragCancel = { dragOffset = Offset.Zero },
                onDrag = { change, amount -> change.consume(); dragOffset += amount },
            )
        }
    } else Modifier
    val isSmall = width < 100.dp && height < 100.dp
    val tileInset = if (isSmall) YokuliMetrics.TileSmallContentInset else YokuliMetrics.TileContentInset
    val statusColor = when (presentation.tone) {
        LauncherTileTone.SAFE -> colors.safe
        LauncherTileTone.WARNING -> colors.warning
        LauncherTileTone.ALARM -> colors.alarm
        LauncherTileTone.STALE -> colors.stale
        null -> null
    }
    Box(
        modifier.offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .width(width).height(height).scale(scale).alpha(if (editing && !selected) .55f else 1f)
            .testTag("tile-${entry.id.value}")
            .semantics {
                wpTileAccentName = colors.spec.accent.displayName
                stateDescription = buildString {
                    append(headline)
                    if (detail.isNotBlank()) append(" · $detail")
                }
            }
            .wpTilt(interactions, enabled = !(editing && selected))
            .background(colors.accent)
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick, onLongClick = onLongClick)
            .then(dragModifier)
            .padding(tileInset),
    ) {
        if (chartPreview) ChartTilePreview(colors.onAccent)
        WpText(
            visual.glyph,
            if (height < 100.dp) 20 else 28,
            color = colors.onAccent,
            modifier = Modifier.align(Alignment.TopStart),
        )
        if (!isSmall) {
            Column(Modifier.align(Alignment.CenterStart).padding(top = if (chartPreview) 16.dp else 0.dp)) {
                WpText(
                    headline,
                    if (height < 100.dp) 18 else 31,
                    color = colors.onAccent,
                    weight = FontWeight.Light,
                )
                WpText(detail, if (height < 100.dp) 10 else 13, color = colors.onAccent.copy(alpha = .82f))
            }
        }
        WpText(
            title,
            if (height < 100.dp) 11 else 13,
            color = colors.onAccent,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        if (statusColor != null) {
            Box(Modifier.align(Alignment.TopEnd).size(7.dp).background(statusColor))
        }
        if (editing && selected) WpTileEditOverlay(onUnpin, onResize)
    }
}

@Composable
private fun ChartTilePreview(color: Color) {
    Canvas(Modifier.fillMaxSize().alpha(.32f)) {
        repeat(3) { index ->
            val y = size.height * (.25f + index * .22f)
            drawLine(color, Offset(size.width * .36f, y), Offset(size.width, y - 20f), 2f)
        }
        val track = Path().apply {
            moveTo(size.width * .52f, size.height * .76f)
            cubicTo(size.width * .62f, size.height * .65f, size.width * .64f, size.height * .44f, size.width * .84f, size.height * .28f)
        }
        drawPath(track, color, style = Stroke(5f))
        drawCircle(color, 8f, Offset(size.width * .84f, size.height * .28f), style = Stroke(3f))
    }
}

@Composable
fun WpTileEditOverlay(onUnpin: () -> Unit, onResize: () -> Unit) {
    val colors = LocalWpTheme.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd).size(YokuliMetrics.MinTouch)
                .testTag("unpin-selected-tile").combinedNoRipple(onUnpin),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(28.dp).background(colors.background, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { WpText("−", 20, color = colors.foreground) }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(YokuliMetrics.MinTouch)
                .testTag("resize-selected-tile").combinedNoRipple(onResize),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(28.dp).background(colors.background, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { WpText("↔", 15, color = colors.foreground) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedNoRipple(onClick: () -> Unit): Modifier = combinedClickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
)
