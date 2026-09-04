package com.yokuli.marine.feature.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.StartViewport
import com.yokuli.shell.engine.geometry.WpStartGeometryCalculator
import com.yokuli.shell.engine.interaction.DragCellHysteresis
import com.yokuli.shell.engine.interaction.EdgeAutoScrollPolicy
import com.yokuli.shell.engine.interaction.ShellOffset
import com.yokuli.shell.engine.interaction.StartInteractionState
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.StartLayoutEditor
import com.yokuli.shell.engine.layout.TilePlacement
import kotlin.math.roundToInt

@Composable
fun YokuliStartScreen(
    state: LauncherUiState,
    onAction: (LauncherUiAction) -> Unit,
    onEditModeChanged: (Boolean) -> Unit = {},
) {
    val colors = LocalWpTheme.current
    val byId = remember(state.entries) { state.entries.associateBy { it.descriptor.entryId } }
    val interaction = state.interaction
    val dragging = interaction as? StartInteractionState.Dragging
    val proposedDocument: StartDocument? = when (interaction) {
        is StartInteractionState.Dragging -> interaction.proposedLayout
        is StartInteractionState.Resizing -> interaction.proposedLayout
        else -> null
    }
    val selectedTile = interaction.selectedTile()
    val editing = interaction.isEditing()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val latestInteraction by rememberUpdatedState(interaction)
    val latestDocument by rememberUpdatedState(state.document)
    val latestAction by rememberUpdatedState(onAction)
    val resizing = interaction as? StartInteractionState.Resizing

    LaunchedEffect(editing) {
        onEditModeChanged(editing)
    }

    LaunchedEffect(resizing?.tileId, resizing?.proposedSize) {
        if (resizing == null) return@LaunchedEffect
        withFrameNanos { }
        latestAction(LauncherUiAction.CommitTileResize)
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.background).testTag("start-screen")
            .semantics { wpThemeModeName = colors.spec.mode.name.lowercase() },
    ) {
        val availableWidthPx = with(density) { maxWidth.toPx().roundToInt() }
        val availableHeightPx = with(density) { maxHeight.toPx().roundToInt() }
        LaunchedEffect(availableWidthPx, availableHeightPx) {
            if (latestInteraction is StartInteractionState.Dragging) {
                latestAction(LauncherUiAction.CancelTileOperation)
            }
        }
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
        val pitchPx = (geometry.smallCellPx + geometry.seamPx).toFloat()
        val hysteresis = remember { DragCellHysteresis() }
        val autoScroll = remember(availableHeightPx, pitchPx, density.density) {
            EdgeAutoScrollPolicy(
                activationZonePx = with(density) { YokuliMetrics.MinTouch.toPx() },
                maximumSpeedPxPerSecond = pitchPx * 3f,
            )
        }
        val rows = (proposedDocument ?: state.document).placements.maxOfOrNull { it.cell.row + it.size.rows } ?: 0
        val gridHeight = if (rows == 0) 0.dp else cell * rows + seam * (rows - 1)

        LaunchedEffect(dragging?.tileId, pitchPx) {
            if (dragging == null) return@LaunchedEffect
            var lastFrame = withFrameNanos { it }
            while (true) {
                val current = latestInteraction as? StartInteractionState.Dragging ?: break
                val frame = withFrameNanos { it }
                val elapsedSeconds = (frame - lastFrame).coerceAtMost(50_000_000L) / 1_000_000_000f
                lastFrame = frame
                val requested = current.autoScrollPxPerSecond * elapsedSeconds
                if (requested == 0f) continue
                val consumed = scroll.scrollBy(requested)
                if (consumed == 0f) continue
                val placement = latestDocument.placements.firstOrNull { it.tileId == current.tileId } ?: break
                val nextOffset = current.visualOffsetPx.copy(y = current.visualOffsetPx.y + consumed)
                val target = hysteresis.resolve(placement.cell, nextOffset, pitchPx, current.targetCell)
                latestAction(LauncherUiAction.AutoScrollTileDrag(current.tileId, consumed, target))
            }
        }

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
                floatingTileId = dragging?.tileId,
                modifier = Modifier.fillMaxWidth().height(gridHeight).combinedNoRipple {
                    if (editing) onAction(LauncherUiAction.ExitStartEdit)
                },
            ) { placement ->
                val entry = byId[placement.entryId]
                if (entry != null) {
                    val tileDragging = dragging?.takeIf { it.tileId == placement.tileId }
                    var previousTarget = remember(placement.tileId) { placement.cell }
                    WpTile(
                        entry = entry,
                        width = cell * placement.size.columns + seam * (placement.size.columns - 1),
                        height = cell * placement.size.rows + seam * (placement.size.rows - 1),
                        editing = editing,
                        selected = selectedTile == placement.tileId,
                        dragOffset = tileDragging?.visualOffsetPx ?: ShellOffset(0f, 0f),
                        onClick = {
                            if (editing) onAction(LauncherUiAction.SelectStartTile(placement.tileId))
                            else onAction(LauncherUiAction.Open(entry.descriptor.launchToken))
                        },
                        onLongClick = { onAction(LauncherUiAction.EnterStartEdit(placement.tileId)) },
                        onUnpin = {
                            StartLayoutEditor.unpin(state.document, placement.tileId)?.let {
                                onAction(LauncherUiAction.ProposeLayout(it))
                            }
                            onAction(LauncherUiAction.ExitStartEdit)
                        },
                        onResize = { onAction(LauncherUiAction.ResizeTile(placement.tileId)) },
                        onMoveStart = { pointerId, grabOffset ->
                            previousTarget = placement.cell
                            onAction(
                                LauncherUiAction.BeginTileDrag(
                                    placement.tileId,
                                    pointerId,
                                    ShellOffset(grabOffset.x, grabOffset.y),
                                ),
                            )
                        },
                        onMove = { visualOffset, grabOffset ->
                            val offset = ShellOffset(visualOffset.x, visualOffset.y)
                            val target = hysteresis.resolve(placement.cell, offset, pitchPx, previousTarget)
                            previousTarget = target
                            val pointerY = placement.cell.row * pitchPx + grabOffset.y + visualOffset.y - scroll.value
                            onAction(
                                LauncherUiAction.UpdateTileDrag(
                                    tileId = placement.tileId,
                                    visualOffset = offset,
                                    targetCell = target,
                                    autoScrollPxPerSecond = autoScroll.velocity(pointerY, availableHeightPx.toFloat()),
                                ),
                            )
                        },
                        onMoveCommit = { onAction(LauncherUiAction.DropTile(placement.tileId)) },
                        onMoveCancel = { onAction(LauncherUiAction.CancelTileOperation) },
                        onMoveBy = { columns, rows ->
                            onAction(LauncherUiAction.MoveTileBy(placement.tileId, columns, rows))
                        },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.End) {
                val icon = if (editing) MarineIconKind.DONE else MarineIconKind.APPS
                Box(
                    Modifier.size(YokuliMetrics.MinTouch).testTag("all-apps-entry")
                        .combinedNoRipple {
                            if (editing) onAction(LauncherUiAction.ExitStartEdit)
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
    dragOffset: ShellOffset,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUnpin: () -> Unit,
    onResize: () -> Unit,
    onMoveStart: (Long, Offset) -> Unit,
    onMove: (Offset, Offset) -> Unit,
    onMoveCommit: () -> Unit,
    onMoveCancel: () -> Unit,
    onMoveBy: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWpTheme.current
    val density = LocalDensity.current
    val interactions = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (selected) 1.025f else 1f, spring(), label = "wp-tile-selected")
    val dragModifier = if (editing && selected) {
        Modifier.pointerInput(entry.descriptor.entryId) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val editControlPx = with(density) { YokuliMetrics.MinTouch.toPx() }
                val inRightControls = down.position.x >= size.width - editControlPx &&
                    (down.position.y <= editControlPx || down.position.y >= size.height - editControlPx)
                if (inRightControls) return@awaitEachGesture
                var total = Offset.Zero
                val grabOffset = down.position
                onMoveStart(down.id.value, grabOffset)
                val completed = drag(down.id) { change ->
                    val amount = change.positionChange()
                    if (amount != Offset.Zero) {
                        change.consume()
                        total += amount
                        onMove(total, grabOffset)
                    }
                }
                if (completed) onMoveCommit() else onMoveCancel()
            }
        }
    } else Modifier
    val accessibilityMoves = if (editing && selected) {
        listOf(
            CustomAccessibilityAction(stringResource(R.string.move_tile_left)) { onMoveBy(-1, 0); true },
            CustomAccessibilityAction(stringResource(R.string.move_tile_right)) { onMoveBy(1, 0); true },
            CustomAccessibilityAction(stringResource(R.string.move_tile_up)) { onMoveBy(0, -1); true },
            CustomAccessibilityAction(stringResource(R.string.move_tile_down)) { onMoveBy(0, 1); true },
        )
    } else emptyList()
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
                customActions = accessibilityMoves
            }
            .wpTilt(interactions, enabled = !(editing && selected))
            .background(colors.accent)
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
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

private fun StartInteractionState.selectedTile(): TileInstanceId? = when (this) {
    is StartInteractionState.EditIdle -> selectedTile
    is StartInteractionState.Dragging -> tileId
    is StartInteractionState.Resizing -> tileId
    else -> null
}

private fun StartInteractionState.isEditing(): Boolean = when (this) {
    is StartInteractionState.EditIdle,
    is StartInteractionState.Dragging,
    is StartInteractionState.Resizing,
    is StartInteractionState.Settling -> true
    else -> false
}

@Composable
private fun WpTileEditOverlay(onUnpin: () -> Unit, onResize: () -> Unit) {
    val colors = LocalWpTheme.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd).size(YokuliMetrics.MinTouch)
                .testTag("unpin-selected-tile").combinedNoRipple(onUnpin),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(30.dp).background(colors.background, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { MarineIcon(MarineIconKind.UNPIN, colors.foreground, Modifier.size(22.dp)) }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(YokuliMetrics.MinTouch)
                .testTag("resize-selected-tile").combinedNoRipple(onResize),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(30.dp).background(colors.background, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { MarineIcon(MarineIconKind.RESIZE, colors.foreground, Modifier.size(20.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedNoRipple(onClick: () -> Unit): Modifier = combinedClickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
)
