package com.yokuli.marine.feature.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.wpThemeModeName
import com.yokuli.marine.core.design.wpTileAccentName
import com.yokuli.marine.core.design.wpTilt
import com.yokuli.shell.engine.geometry.WpStartGeometryCalculator
import com.yokuli.shell.engine.geometry.StartViewport
import com.yokuli.shell.engine.interaction.StartInteractionState
import com.yokuli.shell.engine.layout.StartLayoutEditor
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.contract.TileInstanceId
import kotlin.math.roundToInt

@Composable
fun YokuliStartScreen(
    state: LauncherUiState,
    onAction: (LauncherUiAction) -> Unit,
    onEditModeChanged: (Boolean) -> Unit = {},
) {
    val colors = LocalWpTheme.current
    val byId = remember(state.entries) { state.entries.associateBy { it.descriptor.entryId } }
    var interaction: StartInteractionState by remember { mutableStateOf(StartInteractionState.Idle) }
    var proposedDocument: StartDocument? by remember { mutableStateOf(null) }
    var floatingTileId: TileInstanceId? by remember { mutableStateOf(null) }
    val selectedTile = (interaction as? StartInteractionState.EditIdle)?.selectedTile
    val editing = interaction is StartInteractionState.EditIdle
    LaunchedEffect(editing) { onEditModeChanged(editing) }
    LaunchedEffect(state.document) {
        if (proposedDocument != null) proposedDocument = null
    }
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.background).testTag("start-screen")
            .semantics { wpThemeModeName = colors.spec.mode.name.lowercase() },
    ) {
        val availableWidthPx = with(density) { maxWidth.toPx().roundToInt() }
        val availableHeightPx = with(density) { maxHeight.toPx().roundToInt() }
        val geometry = remember(availableWidthPx, availableHeightPx, density.density, density.fontScale) {
            WpStartGeometryCalculator.calculate(
                StartViewport(
                    widthPx = availableWidthPx,
                    heightPx = availableHeightPx,
                    density = density.density,
                    topInsetPx = 0,
                    bottomInsetPx = 0,
                    fontScale = density.fontScale,
                ),
            )
        }
        val cell = with(density) { geometry.smallCellPx.toDp() }
        val seam = with(density) { geometry.seamPx.toDp() }
        val pitch = cell + seam
        val rows = (proposedDocument ?: state.document).placements.maxOfOrNull { it.cell.row + it.size.rows } ?: 0
        val gridHeight = if (rows == 0) 0.dp else cell * rows + seam * (rows - 1)
        Column(
            Modifier.fillMaxHeight().verticalScroll(scroll)
                .padding(
                    start = with(density) { geometry.outerInsetsPx.left.toDp() },
                    end = with(density) { geometry.outerInsetsPx.right.toDp() },
                    top = with(density) {
                        (geometry.outerInsetsPx.top - geometry.statusStripHeightPx).coerceAtLeast(0).toDp()
                    },
                ).testTag("start-grid"),
        ) {
            WpSpatialStartLayout(
                document = state.document,
                proposedDocument = proposedDocument,
                geometry = geometry,
                floatingTileId = floatingTileId,
                modifier = Modifier.fillMaxWidth().height(gridHeight),
            ) { placement ->
                val entry = byId[placement.entryId]
                if (entry != null) {
                    WpTile(
                        entry = entry,
                        width = cell * placement.size.columns + seam * (placement.size.columns - 1),
                        height = cell * placement.size.rows + seam * (placement.size.rows - 1),
                        editing = editing,
                        selected = selectedTile == placement.tileId,
                        onClick = {
                            if (editing) {
                                interaction = StartInteractionState.EditIdle(placement.tileId)
                            } else {
                                interaction = StartInteractionState.Launching(placement.tileId, entry.descriptor.launchToken, 0f)
                                onAction(LauncherUiAction.Open(entry.descriptor.launchToken))
                            }
                        },
                        onLongClick = { interaction = StartInteractionState.EditIdle(placement.tileId) },
                        onUnpin = {
                            StartLayoutEditor.unpin(state.document, placement.tileId)?.let {
                                onAction(LauncherUiAction.ProposeLayout(it))
                            }
                            interaction = StartInteractionState.Idle
                        },
                        onResize = {
                            StartLayoutEditor.resize(
                                state.document,
                                placement.tileId,
                                state.entries.map { it.descriptor },
                            )?.let { onAction(LauncherUiAction.ProposeLayout(it)) }
                        },
                        onMoveStart = { floatingTileId = placement.tileId },
                        onMovePreview = { delta ->
                            proposedDocument = moveProposal(state, placement, delta, pitch, density)?.after
                        },
                        onMoveCommit = { delta ->
                            val proposal = moveProposal(state, placement, delta, pitch, density)
                            proposedDocument = proposal?.after
                            floatingTileId = null
                            proposal?.let { onAction(LauncherUiAction.ProposeLayout(it)) }
                        },
                        onMoveCancel = {
                            floatingTileId = null
                            proposedDocument = null
                        },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.End) {
                val icon = if (editing) MarineIconKind.DONE else MarineIconKind.APPS
                Box(
                    Modifier.size(YokuliMetrics.MinTouch).testTag("all-apps-entry")
                        .combinedNoRipple {
                            if (editing) interaction = StartInteractionState.Idle
                            else onAction(LauncherUiAction.ShowAllApps)
                        },
                    contentAlignment = Alignment.Center,
                ) { MarineIcon(icon, colors.foreground, Modifier.size(28.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WpTile(
    entry: LauncherEntryUiState,
    width: Dp,
    height: Dp,
    editing: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUnpin: () -> Unit,
    onResize: () -> Unit,
    onMoveStart: () -> Unit,
    onMovePreview: (Offset) -> Unit,
    onMoveCommit: (Offset) -> Unit,
    onMoveCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (selected) 1.025f else 1f, tween(95), label = "wp-tile-selected")
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val dragModifier = if (editing && selected) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { onMoveStart() },
                onDragEnd = { onMoveCommit(dragOffset); dragOffset = Offset.Zero },
                onDragCancel = { onMoveCancel(); dragOffset = Offset.Zero },
                onDrag = { change, amount ->
                    change.consume()
                    dragOffset += amount
                    onMovePreview(dragOffset)
                },
            )
        }
    } else Modifier
    val isSmall = width < 100.dp && height < 100.dp
    val tileInset = if (isSmall) YokuliMetrics.TileSmallContentInset else YokuliMetrics.TileContentInset
    Box(
        modifier.graphicsLayer {
            translationX = dragOffset.x
            translationY = dragOffset.y
        }
            .width(width).height(height).scale(scale).alpha(if (editing && !selected) .55f else 1f)
            .testTag("tile-${entry.descriptor.entryId.value}")
            .semantics {
                wpTileAccentName = colors.spec.accent.displayName
                stateDescription = buildString {
                    append(entry.headline)
                    if (entry.detail.isNotBlank()) append(" · ${entry.detail}")
                }
            }
            .wpTilt(interactions, enabled = !(editing && selected))
            .background(colors.accent)
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick, onLongClick = onLongClick)
            .then(dragModifier).padding(tileInset),
    ) {
        MarineIcon(entry.icon, colors.onAccent, Modifier.align(Alignment.TopStart).size(if (isSmall) 22.dp else 30.dp))
        if (!isSmall) {
            Column(Modifier.align(Alignment.CenterStart)) {
                WpText(entry.headline, if (height < 100.dp) 18 else 31, color = colors.onAccent, weight = FontWeight.Light)
                WpText(entry.detail, if (height < 100.dp) 10 else 13, color = colors.onAccent.copy(alpha = .82f))
            }
        }
        WpText(entry.title, if (isSmall) 11 else 13, color = colors.onAccent, modifier = Modifier.align(Alignment.BottomStart))
        if (editing && selected) WpTileEditOverlay(onUnpin, onResize)
    }
}

private fun moveProposal(
    state: LauncherUiState,
    placement: com.yokuli.shell.engine.layout.TilePlacement,
    delta: Offset,
    pitch: Dp,
    density: androidx.compose.ui.unit.Density,
) = StartLayoutEditor.move(
    state.document,
    placement.tileId,
    GridCell(
        column = (placement.cell.column + delta.x / with(density) { pitch.toPx() }).roundToInt(),
        row = (placement.cell.row + delta.y / with(density) { pitch.toPx() }).roundToInt(),
    ),
    state.entries.map { it.descriptor },
)

@Composable
private fun WpTileEditOverlay(onUnpin: () -> Unit, onResize: () -> Unit) {
    val colors = LocalWpTheme.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd).size(YokuliMetrics.MinTouch)
                .testTag("unpin-selected-tile").combinedNoRipple(onUnpin),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(30.dp).background(colors.background, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                MarineIcon(MarineIconKind.UNPIN, colors.foreground, Modifier.size(22.dp))
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(YokuliMetrics.MinTouch)
                .testTag("resize-selected-tile").combinedNoRipple(onResize),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(30.dp).background(colors.background, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                MarineIcon(MarineIconKind.RESIZE, colors.foreground, Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedNoRipple(onClick: () -> Unit): Modifier = combinedClickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
)
