package com.yokuli.marine.feature.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.geometry.StartViewport
import com.yokuli.shell.engine.geometry.WpStartGeometryCalculator
import com.yokuli.shell.engine.interaction.DragCellHysteresis
import com.yokuli.shell.engine.interaction.EdgeAutoScrollPolicy
import com.yokuli.shell.engine.interaction.ShellOffset
import com.yokuli.shell.engine.interaction.StartInteractionState
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.StartDocument
import kotlin.math.roundToInt

// DERIVED_UNVERIFIED: Stage 2.5 did not observe Pin highlight motion; this is product feedback, not a WP8 measurement.
private const val DERIVED_REVEAL_SCALE = .06f

private data class LocalTileDrag(
    val tileId: TileInstanceId,
    val visualOffset: Offset,
    val grabOffset: Offset,
    val targetCell: com.yokuli.shell.engine.layout.GridCell,
    val insertionIndex: Int,
    val autoScrollPxPerSecond: Float,
)

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
    val reveal = state.reveal
    val revealPulse = remember { Animatable(0f) }
    var localTileDrag by remember { mutableStateOf<LocalTileDrag?>(null) }
    val latestLocalTileDrag by rememberUpdatedState(localTileDrag)

    LaunchedEffect(editing) {
        onEditModeChanged(editing)
    }

    LaunchedEffect(dragging?.tileId) {
        if (dragging == null) localTileDrag = null
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.background).testTag("start-screen")
            .semantics { wpThemeModeName = colors.spec.mode.name.lowercase() },
    ) {
        val availableWidthPx = with(density) { maxWidth.toPx().roundToInt() }
        val availableHeightPx = with(density) { maxHeight.toPx().roundToInt() }
        LaunchedEffect(availableWidthPx, availableHeightPx) {
            if (latestInteraction is StartInteractionState.Dragging) {
                localTileDrag = null
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
        val packedDocument = remember(state.document, geometry.columns) {
            AdaptiveTilePacker.pack(state.document, geometry.columns)
        }
        val visualPackedDocument = remember(proposedDocument, packedDocument, geometry.columns) {
            proposedDocument?.let { AdaptiveTilePacker.pack(it, geometry.columns) } ?: packedDocument
        }
        val packedByTileId = packedDocument.tiles.associateBy { it.entry.tileId }
        val rows = visualPackedDocument.documentHeightRows
        val gridHeight = if (rows == 0) 0.dp else cell * rows + seam * (rows - 1)

        LaunchedEffect(reveal?.transactionId, pitchPx, availableHeightPx) {
            if (reveal == null) return@LaunchedEffect
            withFrameNanos { }
            val placement = AdaptiveTilePacker.pack(latestDocument, geometry.columns).tile(reveal.tileId)
            if (placement == null) {
                latestAction(LauncherUiAction.AcknowledgeStartReveal(reveal.tileId))
                return@LaunchedEffect
            }
            val tileBottom = (placement.cell.row + placement.entry.size.rows) * pitchPx
            val revealTarget = (tileBottom - availableHeightPx + geometry.outerInsetsPx.bottom)
                .roundToInt().coerceIn(0, scroll.maxValue)
            scroll.animateScrollTo(revealTarget)
            revealPulse.snapTo(1f)
            revealPulse.animateTo(0f, spring())
            latestAction(LauncherUiAction.AcknowledgeStartReveal(reveal.tileId))
        }

        LaunchedEffect(dragging?.tileId, pitchPx) {
            if (dragging == null) return@LaunchedEffect
            var lastFrame = withFrameNanos { it }
            while (true) {
                val current = latestLocalTileDrag ?: break
                val frame = withFrameNanos { it }
                val elapsedSeconds = (frame - lastFrame).coerceAtMost(50_000_000L) / 1_000_000_000f
                lastFrame = frame
                val requested = current.autoScrollPxPerSecond * elapsedSeconds
                if (requested == 0f) continue
                val consumed = scroll.scrollBy(requested)
                if (consumed == 0f) continue
                val placement = AdaptiveTilePacker.pack(latestDocument, geometry.columns).tile(current.tileId) ?: break
                val nextOffset = current.visualOffset.copy(y = current.visualOffset.y + consumed)
                val target = hysteresis.resolve(
                    placement.cell,
                    ShellOffset(nextOffset.x, nextOffset.y),
                    pitchPx,
                    current.targetCell,
                )
                val insertionIndex = AdaptiveTilePacker.insertionIndexForCell(
                    latestDocument,
                    geometry.columns,
                    target,
                    current.tileId,
                )
                localTileDrag = current.copy(visualOffset = nextOffset, targetCell = target, insertionIndex = insertionIndex)
                if (insertionIndex != current.insertionIndex) {
                    latestAction(LauncherUiAction.InsertionTargetChanged(current.tileId, insertionIndex))
                }
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
                    val baseCell = packedByTileId[placement.tileId]?.cell ?: return@WpSpatialStartLayout
                    val tileDragging = localTileDrag?.takeIf { it.tileId == placement.tileId }
                    WpTile(
                        entry = entry,
                        tileSize = placement.size,
                        width = cell * placement.size.columns + seam * (placement.size.columns - 1),
                        height = cell * placement.size.rows + seam * (placement.size.rows - 1),
                        editing = editing,
                        selected = selectedTile == placement.tileId,
                        revealing = reveal?.tileId == placement.tileId,
                        revealProgress = if (reveal?.tileId == placement.tileId) revealPulse.value else 0f,
                        dragOffset = tileDragging?.visualOffset?.let { ShellOffset(it.x, it.y) } ?: ShellOffset(0f, 0f),
                        resizing = resizing?.tileId == placement.tileId,
                        onClick = {
                            if (editing) onAction(LauncherUiAction.SelectStartTile(placement.tileId))
                            else onAction(LauncherUiAction.Open(entry.descriptor.launchToken))
                        },
                        onLongClick = { onAction(LauncherUiAction.EnterStartEdit(placement.tileId)) },
                        onUnpin = { onAction(LauncherUiAction.UnpinTile(placement.tileId)) },
                        onResize = {
                            if (resizing?.tileId == placement.tileId) onAction(LauncherUiAction.CommitTileResize)
                            else onAction(LauncherUiAction.ResizeTile(placement.tileId))
                        },
                        onResizeCancel = { onAction(LauncherUiAction.CancelTileOperation) },
                        onMoveStart = { pointerId, grabOffset ->
                            val insertionIndex = AdaptiveTilePacker.insertionIndexOf(state.document, placement.tileId)
                            localTileDrag = LocalTileDrag(
                                tileId = placement.tileId,
                                visualOffset = Offset.Zero,
                                grabOffset = grabOffset,
                                targetCell = baseCell,
                                insertionIndex = insertionIndex,
                                autoScrollPxPerSecond = 0f,
                            )
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
                            val current = localTileDrag ?: return@WpTile
                            val target = hysteresis.resolve(baseCell, offset, pitchPx, current.targetCell)
                            val insertionIndex = AdaptiveTilePacker.insertionIndexForCell(
                                state.document,
                                geometry.columns,
                                target,
                                placement.tileId,
                            )
                            val pointerY = baseCell.row * pitchPx + grabOffset.y + visualOffset.y - scroll.value
                            localTileDrag = current.copy(
                                visualOffset = visualOffset,
                                grabOffset = grabOffset,
                                targetCell = target,
                                insertionIndex = insertionIndex,
                                autoScrollPxPerSecond = autoScroll.velocity(pointerY, availableHeightPx.toFloat()),
                            )
                            if (insertionIndex != current.insertionIndex) {
                                onAction(LauncherUiAction.InsertionTargetChanged(placement.tileId, insertionIndex))
                            }
                        },
                        onMoveCommit = { onAction(LauncherUiAction.DropTile(placement.tileId)) },
                        onMoveCancel = {
                            localTileDrag = null
                            onAction(LauncherUiAction.CancelTileOperation)
                        },
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
        WpLauncherFeedback(
            transient = state.transient,
            onAction = onAction,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WpTile(
    entry: LauncherEntryUiState,
    tileSize: MarineTileSize,
    width: Dp,
    height: Dp,
    editing: Boolean,
    selected: Boolean,
    resizing: Boolean,
    revealing: Boolean,
    revealProgress: Float,
    dragOffset: ShellOffset,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUnpin: () -> Unit,
    onResize: () -> Unit,
    onResizeCancel: () -> Unit,
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
    val isSmall = width < 100.dp && height < 100.dp
    val dragModifier = if (editing && selected) {
        Modifier.pointerInput(entry.descriptor.entryId) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val editControlPx = with(density) { YokuliMetrics.MinTouch.toPx() }
                val inEditControls = if (isSmall) {
                    (down.position.x <= editControlPx && down.position.y <= editControlPx) ||
                        (down.position.x >= size.width - editControlPx &&
                            down.position.y >= size.height - editControlPx)
                } else {
                    down.position.x >= size.width - editControlPx &&
                        (down.position.y <= editControlPx || down.position.y >= size.height - editControlPx)
                }
                if (inEditControls) return@awaitEachGesture
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
            CustomAccessibilityAction(stringResource(R.string.context_unpin)) { onUnpin(); true },
            CustomAccessibilityAction(stringResource(R.string.resize_tile)) { onResize(); true },
            CustomAccessibilityAction(stringResource(R.string.move_tile_left)) { onMoveBy(-1, 0); true },
            CustomAccessibilityAction(stringResource(R.string.move_tile_right)) { onMoveBy(1, 0); true },
            CustomAccessibilityAction(stringResource(R.string.move_tile_up)) { onMoveBy(0, -1); true },
            CustomAccessibilityAction(stringResource(R.string.move_tile_down)) { onMoveBy(0, 1); true },
        )
    } else emptyList()
    val tileInset = if (isSmall) YokuliMetrics.TileSmallContentInset else YokuliMetrics.TileContentInset
    Box(
        modifier.graphicsLayer {
            translationX = dragOffset.x
            translationY = dragOffset.y
        }
            .width(width).height(height).scale(scale * (1f + revealProgress * DERIVED_REVEAL_SCALE))
            .alpha(if (editing && !selected) .55f else 1f)
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
        MarineTileContent(entry, tileSize)
        if (editing && selected) {
            WpTileEditOverlay(
                compact = isSmall,
                resizing = resizing,
                onUnpin = onUnpin,
                onResize = onResize,
                onResizeCancel = onResizeCancel,
            )
        }
        if (revealing) {
            Box(
                Modifier.fillMaxSize().border(3.dp, colors.onAccent)
                    .alpha(revealProgress.coerceIn(0f, 1f)).testTag("tile-reveal-highlight"),
            )
        }
    }
}

@Composable
private fun BoxScope.MarineTileContent(entry: LauncherEntryUiState, size: MarineTileSize) {
    val onAccent = LocalWpTheme.current.onAccent
    when (size) {
        MarineTileSize.ICON_1X1 ->
            MarineIcon(entry.icon, onAccent, Modifier.align(Alignment.Center).size(25.dp))

        MarineTileSize.COMPACT_2X1 -> Row(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarineIcon(entry.icon, onAccent, Modifier.size(23.dp))
            Column(Modifier.padding(start = 8.dp)) {
                WpText(entry.headline, 17, color = onAccent, weight = FontWeight.Light)
                WpText(entry.title, 10, color = onAccent.copy(alpha = .82f))
            }
        }

        MarineTileSize.STANDARD_2X2 -> {
            MarineIcon(entry.icon, onAccent, Modifier.align(Alignment.TopStart).size(28.dp))
            Column(Modifier.align(Alignment.CenterStart)) {
                WpText(entry.headline, 25, color = onAccent, weight = FontWeight.Light)
                WpText(entry.detail, 11, color = onAccent.copy(alpha = .82f))
            }
            WpText(entry.title, 12, color = onAccent, modifier = Modifier.align(Alignment.BottomStart))
        }

        MarineTileSize.WIDE_4X2 -> {
            MarineIcon(entry.icon, onAccent, Modifier.align(Alignment.TopStart).size(30.dp))
            Column(Modifier.align(Alignment.CenterStart)) {
                WpText(entry.headline, 31, color = onAccent, weight = FontWeight.Light)
                WpText(entry.detail, 13, color = onAccent.copy(alpha = .82f))
            }
            WpText(entry.title, 13, color = onAccent, modifier = Modifier.align(Alignment.BottomStart))
        }

        MarineTileSize.TALL_2X4 -> {
            MarineIcon(entry.icon, onAccent, Modifier.align(Alignment.TopStart).size(34.dp))
            Column(Modifier.align(Alignment.CenterStart)) {
                WpText(entry.headline, 34, color = onAccent, weight = FontWeight.Light)
                WpText(entry.detail, 14, color = onAccent.copy(alpha = .82f), modifier = Modifier.padding(top = 8.dp))
            }
            WpText(entry.title, 14, color = onAccent, modifier = Modifier.align(Alignment.BottomStart))
        }

        MarineTileSize.LARGE_4X4 -> {
            Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                MarineIcon(entry.icon, onAccent, Modifier.size(36.dp))
                WpText(entry.title, 16, color = onAccent, modifier = Modifier.padding(start = 10.dp))
            }
            Column(Modifier.align(Alignment.CenterStart)) {
                WpText(entry.headline, 42, color = onAccent, weight = FontWeight.Light)
                WpText(entry.detail, 16, color = onAccent.copy(alpha = .82f), modifier = Modifier.padding(top = 10.dp))
            }
        }
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
private fun WpTileEditOverlay(
    compact: Boolean,
    resizing: Boolean,
    onUnpin: () -> Unit,
    onResize: () -> Unit,
    onResizeCancel: () -> Unit,
) {
    val colors = LocalWpTheme.current
    // DERIVED_UNVERIFIED: edit controls were not visible in Stage 2.5. Visual disks remain
    // compact, while every pointer target is the platform-safe 48dp minimum.
    val controlSize = YokuliMetrics.MinTouch
    val diskSize = if (compact) 17.dp else 30.dp
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(if (compact) Alignment.TopStart else Alignment.TopEnd).size(controlSize)
                .testTag(if (resizing) "cancel-tile-resize" else "unpin-selected-tile")
                .combinedNoRipple(if (resizing) onResizeCancel else onUnpin),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(diskSize).background(colors.background, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MarineIcon(
                    if (resizing) MarineIconKind.CANCEL else MarineIconKind.UNPIN,
                    colors.foreground,
                    Modifier.size(if (compact) 13.dp else 22.dp),
                )
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(controlSize)
                .testTag(if (resizing) "commit-tile-resize" else "resize-selected-tile")
                .combinedNoRipple(onResize),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(diskSize).background(colors.background, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MarineIcon(
                    if (resizing) MarineIconKind.DONE else MarineIconKind.RESIZE,
                    colors.foreground,
                    Modifier.size(if (compact) 12.dp else 20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedNoRipple(onClick: () -> Unit): Modifier = combinedClickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
)
