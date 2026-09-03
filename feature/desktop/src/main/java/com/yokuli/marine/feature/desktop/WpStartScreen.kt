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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.*
import com.yokuli.marine.core.shell.DesktopLayoutEditor
import com.yokuli.marine.core.shell.LauncherRegistry
import kotlin.math.roundToInt

private data class TilePresentation(val color: Color, val headline: String, val detail: String)

@Composable
fun YokuliStartScreen(
    onOpen: (LaunchTarget) -> Unit,
    onAllApps: () -> Unit,
    layout: DesktopLayout = LauncherRegistry.defaultLayout,
    onLayoutChange: (DesktopLayout) -> Unit = {},
) {
    val entries = remember { LauncherRegistry.entries.associateBy { it.id.value } }
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(YokuliColors.Black).testTag("start-screen")) {
        val width = maxWidth.coerceAtMost(560.dp)
        val gridWidth = width - YokuliMetrics.OuterMargin * 2
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
                            width = cell * placement.size.columns + YokuliMetrics.TileGap * (placement.size.columns - 1),
                            height = cell * placement.size.rows + YokuliMetrics.TileGap * (placement.size.rows - 1),
                            presentation = presentationFor(entry.id.value),
                            editing = editing,
                            selected = selected == placement.tileId.value,
                            onClick = {
                                if (editing) selected = placement.tileId.value else onOpen(entry.launchTarget)
                            },
                            onLongClick = { editing = true; selected = placement.tileId.value },
                            onUnpin = {
                                onLayoutChange(DesktopLayoutEditor.unpin(layout, placement.tileId))
                                selected = null
                                editing = false
                            },
                            onResize = { onLayoutChange(DesktopLayoutEditor.resize(layout, placement.tileId)) },
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
                                onLayoutChange(moved)
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
                        .combinedNoRipple(onClick = if (editing) ({ editing = false; selected = null }) else onAllApps),
                    contentAlignment = Alignment.Center,
                ) {
                    WpText(if (editing) "✓" else "→", 28, weight = FontWeight.Light)
                }
            }
        }
    }
}

private fun presentationFor(entryId: String): TilePresentation = when (entryId) {
    "chart" -> TilePresentation(YokuliColors.Ocean, "FOLLOWING", "COG 184° · 6.2 kn")
    "anchor" -> TilePresentation(YokuliColors.Safe, "SAFE", "32 / 60 m")
    "cockpit" -> TilePresentation(YokuliColors.Cyan, "6.2", "kn · HDG 184°T")
    "library" -> TilePresentation(YokuliColors.DeepOcean, "27 TRIPS", "12 PLACES")
    "system" -> TilePresentation(YokuliColors.Stale, "NMEA OFF", "0 CRITICAL")
    "navigation" -> TilePresentation(YokuliColors.Ocean, "MOTUIHE", "DTW 3.4 NM")
    "survey" -> TilePresentation(YokuliColors.Cyan, "READY", "DEPTH —")
    "trips" -> TilePresentation(YokuliColors.DeepOcean, "27 TRIPS", "LAST · TODAY")
    "anchorages" -> TilePresentation(YokuliColors.DeepOcean, "12 PLACES", "3 FAVORITES")
    "data_sources" -> TilePresentation(YokuliColors.Cyan, "PHONE GPS", "FRESH")
    "nmea_input" -> TilePresentation(YokuliColors.Stale, "NMEA OFF", "NO DATA")
    "diagnostics" -> TilePresentation(YokuliColors.Stale, "0 CRITICAL", "READY")
    else -> TilePresentation(YokuliColors.Cyan, "DARK", "DISPLAY")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WpTile(
    entry: LauncherEntryDescriptor,
    width: Dp,
    height: Dp,
    presentation: TilePresentation,
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
    Box(
        modifier.offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .width(width).height(height).scale(scale).alpha(if (editing && !selected) .55f else 1f)
            .testTag("tile-${entry.id.value}")
            .background(presentation.color)
            .wpTilt(interactions, enabled = !(editing && selected))
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick, onLongClick = onLongClick)
            .then(dragModifier)
            .padding(10.dp),
    ) {
        if (chartPreview) ChartTilePreview()
        WpText(entry.symbol, if (height < 100.dp) 20 else 28, modifier = Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.CenterStart).padding(top = if (chartPreview) 16.dp else 0.dp)) {
            WpText(presentation.headline, if (height < 100.dp) 18 else 31, weight = FontWeight.Light)
            WpText(presentation.detail, if (height < 100.dp) 10 else 13, color = YokuliColors.White.copy(alpha = .86f))
        }
        WpText(entry.title.uppercase(), if (height < 100.dp) 11 else 13, modifier = Modifier.align(Alignment.BottomStart))
        if (editing && selected) WpTileEditOverlay(onUnpin, onResize)
    }
}

@Composable
private fun ChartTilePreview() {
    Canvas(Modifier.fillMaxSize().alpha(.32f)) {
        repeat(3) { index ->
            val y = size.height * (.25f + index * .22f)
            drawLine(Color.White, Offset(size.width * .36f, y), Offset(size.width, y - 20f), 2f)
        }
        val track = Path().apply {
            moveTo(size.width * .52f, size.height * .76f)
            cubicTo(size.width * .62f, size.height * .65f, size.width * .64f, size.height * .44f, size.width * .84f, size.height * .28f)
        }
        drawPath(track, Color.White, style = Stroke(5f))
        drawCircle(Color.White, 8f, Offset(size.width * .84f, size.height * .28f), style = Stroke(3f))
    }
}

@Composable
fun WpTileEditOverlay(onUnpin: () -> Unit, onResize: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd).size(30.dp)
                .background(YokuliColors.Black, androidx.compose.foundation.shape.CircleShape)
                .testTag("unpin-selected-tile").combinedNoRipple(onUnpin),
            contentAlignment = Alignment.Center,
        ) {
            WpText("−", 20)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(30.dp)
                .background(YokuliColors.Black, androidx.compose.foundation.shape.CircleShape)
                .testTag("resize-selected-tile").combinedNoRipple(onResize),
            contentAlignment = Alignment.Center,
        ) {
            WpText("↔", 15)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedNoRipple(onClick: () -> Unit): Modifier = combinedClickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
)
