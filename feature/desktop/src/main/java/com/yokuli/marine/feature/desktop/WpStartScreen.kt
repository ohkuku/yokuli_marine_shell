package com.yokuli.marine.feature.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.wpThemeModeName
import com.yokuli.marine.core.design.wpTileAccentName
import com.yokuli.marine.core.design.wpTilt
import com.yokuli.shell.compose.LauncherEntryUiState
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.StartViewport
import com.yokuli.shell.engine.geometry.WpStartGeometryCalculator
import com.yokuli.shell.engine.interaction.DragCellHysteresis
import com.yokuli.shell.engine.interaction.EdgeAutoScrollPolicy
import com.yokuli.shell.engine.interaction.EditControlRect
import com.yokuli.shell.engine.interaction.ShellOffset
import com.yokuli.shell.engine.interaction.StartInteractionState
import com.yokuli.shell.engine.interaction.TileDragCoordinates
import com.yokuli.shell.engine.interaction.TileEditControlGeometry
import com.yokuli.shell.engine.interaction.TileEditControls
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.StartDocument
import kotlin.math.max
import kotlin.math.roundToInt

// DERIVED_UNVERIFIED product feedback, not an observed WP8 measurement.
private const val DERIVED_REVEAL_SCALE = .06f

private data class LocalTileDrag(
    val tileId: TileInstanceId,
    val pointerId: Long,
    val coordinates: TileDragCoordinates,
    val originCell: GridCell,
    val targetCell: GridCell,
    val insertionIndex: Int,
    val sourceDocument: StartDocument,
    val sessionId: Long,
    val hasMoved: Boolean = false,
    val engineObserved: Boolean = false,
    val finishing: Boolean = false,
)

@OptIn(ExperimentalFoundationApi::class)
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
    val proposedDocument = dragging?.proposedLayout
    val selectedTile = interaction.selectedTile()
    val editing = interaction.isEditing()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val latestInteraction by rememberUpdatedState(interaction)
    val latestDocument by rememberUpdatedState(state.document)
    val latestAction by rememberUpdatedState(onAction)
    val revealPulse = remember { Animatable(0f) }
    var localTileDrag by remember { mutableStateOf<LocalTileDrag?>(null) }
    var nextDragSessionId by remember { mutableStateOf(0L) }
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val tileBounds = remember { mutableStateMapOf<TileInstanceId, Rect>() }
    var feedbackBounds by remember { mutableStateOf<Rect?>(null) }
    val latestFeedbackBounds by rememberUpdatedState(feedbackBounds.takeIf { state.transient != null })

    LaunchedEffect(editing) { onEditModeChanged(editing) }
    LaunchedEffect(interaction, state.document) {
        val local = localTileDrag ?: return@LaunchedEffect
        when {
            local.sourceDocument != state.document -> {
                localTileDrag = null
                if (!local.finishing && dragging?.tileId == local.tileId) latestAction(LauncherUiAction.CancelTileOperation)
            }
            dragging?.tileId == local.tileId && dragging.pointerId == local.pointerId ->
                localTileDrag = local.copy(engineObserved = true)
            local.engineObserved || local.finishing -> localTileDrag = null
        }
    }
    LaunchedEffect(localTileDrag?.finishing) {
        val local = localTileDrag ?: return@LaunchedEffect
        // StateFlow can conflate a no-op Begin/Drop back into the same EditIdle state.
        // No changed document needs acknowledgement in this case; do not leave a ghost drag.
        if (local.finishing && local.insertionIndex == AdaptiveTilePacker.insertionIndexOf(local.sourceDocument, local.tileId)) {
            localTileDrag = null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            localTileDrag?.let { latestAction(LauncherUiAction.CancelTileOperation) }
            localTileDrag = null
        }
    }
    LaunchedEffect(state.document.placements) {
        val ids = state.document.placements.map { it.tileId }.toSet()
        tileBounds.keys.toList().filterNot(ids::contains).forEach { tileBounds.remove(it) }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.background).testTag("start-screen")
            .semantics { wpThemeModeName = colors.spec.mode.name.lowercase() },
    ) {
        val availableWidthPx = with(density) { maxWidth.toPx().roundToInt() }
        val availableHeightPx = with(density) { maxHeight.toPx().roundToInt() }
        if (availableWidthPx <= 0 || availableHeightPx <= 0) return@BoxWithConstraints
        val geometry = remember(availableWidthPx, availableHeightPx, density.density, density.fontScale) {
            WpStartGeometryCalculator.calculate(StartViewport(availableWidthPx, availableHeightPx, density.density, 0, 0, density.fontScale))
        }
        val cell = with(density) { geometry.smallCellPx.toDp() }
        val seam = with(density) { geometry.seamPx.toDp() }
        val pitchPx = (geometry.smallCellPx + geometry.seamPx).toFloat()
        val touchPx = with(density) { YokuliMetrics.MinTouch.toPx() }
        val hysteresis = remember { DragCellHysteresis() }
        val autoScroll = remember(touchPx, pitchPx) { EdgeAutoScrollPolicy(touchPx, pitchPx * 3f) }
        val packedDocument = remember(state.document, geometry.columns) { AdaptiveTilePacker.pack(state.document, geometry.columns) }
        val visualPackedDocument = remember(proposedDocument, packedDocument, geometry.columns) {
            proposedDocument?.let { AdaptiveTilePacker.pack(it, geometry.columns) } ?: packedDocument
        }
        val renderDrag = localTileDrag?.takeIf {
            it.sourceDocument == state.document && (dragging?.tileId == it.tileId || !it.engineObserved)
        }
        // A tighter preview cannot clamp away the scroll range under the accepted finger.
        val rows = if (renderDrag != null) max(packedDocument.documentHeightRows, visualPackedDocument.documentHeightRows)
            else visualPackedDocument.documentHeightRows
        val gridHeight = if (rows == 0) 0.dp else cell * rows + seam * (rows - 1)
        val selectedPlacement = packedDocument.tiles.firstOrNull { it.entry.tileId == selectedTile }
        val selectedEntry = selectedPlacement?.entry?.entryId?.let(byId::get)
        val selectedBounds = selectedTile?.let(tileBounds::get)
        val compact = selectedPlacement?.entry?.size?.let { it.columns == 1 && it.rows == 1 } == true
        val controls = if (editing && localTileDrag == null && selectedBounds != null) {
            TileEditControlGeometry.resolve(
                selectedBounds.toEditRect(), availableWidthPx.toFloat(),
                latestFeedbackBounds?.top?.coerceIn(0f, availableHeightPx.toFloat()) ?: availableHeightPx.toFloat(),
                touchPx, compact, (selectedEntry?.descriptor?.supportedSizes?.size ?: 0) > 1,
            )
        } else null
        val latestControls by rememberUpdatedState(controls)

        fun updateDrag(sessionId: Long, position: Offset? = null) {
            val current = localTileDrag?.takeIf { it.sessionId == sessionId } ?: return
            if (current.finishing || current.sourceDocument != latestDocument) return
            val coordinates = position?.let { current.coordinates.movedTo(ShellOffset(it.x, it.y)) } ?: current.coordinates
            val hasMoved = current.hasMoved || coordinates.hasMovedBeyond(touchSlop)
            if (!hasMoved) {
                localTileDrag = current.copy(coordinates = coordinates)
                return
            }
            val offset = coordinates.contentOffset(scroll.value.toFloat())
            val target = hysteresis.resolve(current.originCell, offset, pitchPx, current.targetCell)
            val index = AdaptiveTilePacker.insertionIndexForCell(current.sourceDocument, geometry.columns, target, current.tileId)
            localTileDrag = current.copy(coordinates = coordinates, hasMoved = true, targetCell = target, insertionIndex = index)
            if (index != current.insertionIndex) latestAction(LauncherUiAction.InsertionTargetChanged(current.tileId, index))
        }
        val latestUpdateDrag by rememberUpdatedState<(Long, Offset?) -> Unit>({ session, point -> updateDrag(session, point) })
        val scrollVelocity = localTileDrag?.takeIf { it.hasMoved && !it.finishing }
            ?.let { autoScroll.velocity(it.coordinates.pointer.y, availableHeightPx.toFloat()) } ?: 0f
        LaunchedEffect(localTileDrag?.sessionId, pitchPx, availableHeightPx, scrollVelocity, scroll.maxValue) {
            if (scrollVelocity == 0f) return@LaunchedEffect
            val session = localTileDrag?.sessionId ?: return@LaunchedEffect
            var lastFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                val current = localTileDrag?.takeIf { it.sessionId == session && !it.finishing } ?: break
                val elapsed = (frame - lastFrame).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                lastFrame = frame
                val requested = autoScroll.velocity(current.coordinates.pointer.y, availableHeightPx.toFloat()) * elapsed
                if (requested == 0f || scroll.scrollBy(requested) == 0f) break
                // Re-read after suspension; generation matching prevents resurrecting a cancelled gesture.
                latestUpdateDrag(session, null)
            }
        }
        LaunchedEffect(state.reveal?.transactionId, pitchPx, availableHeightPx) {
            val reveal = state.reveal ?: return@LaunchedEffect
            if (localTileDrag != null) return@LaunchedEffect
            withFrameNanos { }
            val placement = AdaptiveTilePacker.pack(latestDocument, geometry.columns).tile(reveal.tileId)
            if (placement != null) {
                val tileBottom = (placement.cell.row + placement.entry.size.rows) * pitchPx
                val target = (tileBottom - availableHeightPx + geometry.outerInsetsPx.bottom).roundToInt().coerceIn(0, scroll.maxValue)
                scroll.animateScrollTo(target)
                revealPulse.snapTo(1f)
                revealPulse.animateTo(0f, spring())
            }
            latestAction(LauncherUiAction.AcknowledgeStartReveal(reveal.tileId))
        }

        Box(
            Modifier.fillMaxSize().onGloballyPositioned { viewportCoordinates = it }
                .pointerInput(state.document, state.entries.map { it.descriptor }, geometry, density.density, density.fontScale) {
                    awaitEachGesture {
                        // Main observes the down after the clickable child. Starting long-press
                        // detection in Initial would see this SAME down consumed in Main and cancel.
                        // Once accepted, movement wins Initial, before the scrolling child.
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                        if (localTileDrag != null || latestControls?.contains(down.position.x, down.position.y) == true ||
                            latestFeedbackBounds?.contains(down.position) == true) return@awaitEachGesture
                        val selected = latestInteraction.selectedTile()
                        val hit = tileBounds.entries.sortedByDescending { it.key == selected }
                            .firstOrNull { it.value.contains(down.position) } ?: return@awaitEachGesture
                        val tileId = hit.key
                        val source = latestDocument
                        val origin = AdaptiveTilePacker.pack(source, geometry.columns).tile(tileId) ?: return@awaitEachGesture
                        val direct = latestInteraction is StartInteractionState.EditIdle && selected == tileId
                        val dragStart = if (direct) awaitSelectedDragSlop(down) else awaitLongPressOrCancellation(down.id)
                        if (dragStart == null || dragStart.id != down.id || currentEvent.changes.count { it.pressed } != 1) return@awaitEachGesture
                        if (latestDocument != source) return@awaitEachGesture
                        dragStart.consume()
                        if (!direct) latestAction(LauncherUiAction.EnterStartEdit(tileId))
                        val startPointer = if (direct) down.position else dragStart.position
                        val grab = startPointer - hit.value.topLeft
                        val session = ++nextDragSessionId
                        localTileDrag = LocalTileDrag(
                            tileId, down.id.value,
                            TileDragCoordinates(ShellOffset(startPointer.x, startPointer.y), startScrollPx = scroll.value.toFloat()),
                            origin.cell, origin.cell, AdaptiveTilePacker.insertionIndexOf(source, tileId), source,
                            sessionId = session, hasMoved = direct,
                        )
                        latestAction(LauncherUiAction.BeginTileDrag(tileId, dragStart.id.value, ShellOffset(grab.x, grab.y)))
                        latestUpdateDrag(session, dragStart.position)
                        var completed = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val active = localTileDrag?.takeIf { it.sessionId == session }
                                val cancelled = event.changes.any { it.id != down.id && it.pressed } || change.isConsumed
                                if (cancelled && active != null) {
                                    localTileDrag = null
                                    latestAction(LauncherUiAction.CancelTileOperation)
                                }
                                // Drain accepted input even after Back/cancel: late up cannot click or commit.
                                event.changes.forEach { it.consume() }
                                if (!cancelled && localTileDrag?.sessionId == session) {
                                    latestUpdateDrag(session, change.position)
                                    if (!change.pressed) completed = true
                                }
                                if (!change.pressed) break
                            }
                        } finally {
                            val active = localTileDrag?.takeIf { it.sessionId == session }
                            if (active != null) {
                                if (completed) {
                                    localTileDrag = active.copy(finishing = true)
                                    latestAction(LauncherUiAction.DropTile(tileId))
                                } else {
                                    localTileDrag = null
                                    latestAction(LauncherUiAction.CancelTileOperation)
                                }
                            }
                        }
                    }
                },
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll, enabled = localTileDrag == null)
                    .padding(
                        start = with(density) { geometry.outerInsetsPx.left.toDp() },
                        end = with(density) { geometry.outerInsetsPx.right.toDp() },
                        top = with(density) { (geometry.outerInsetsPx.top - geometry.statusStripHeightPx).coerceAtLeast(0).toDp() },
                    ).testTag("start-grid"),
            ) {
                Box(Modifier.fillMaxWidth().height(gridHeight)) {
                    Box(Modifier.fillMaxSize().combinedNoRipple { if (editing) onAction(LauncherUiAction.ExitStartEdit) })
                    WpSpatialStartLayout(
                        document = state.document, proposedDocument = proposedDocument, geometry = geometry,
                        floatingTileId = renderDrag?.tileId ?: dragging?.tileId,
                        selectedTileId = selectedTile,
                        floatingOffsetPx = renderDrag?.coordinates?.contentOffset(scroll.value.toFloat()) ?: ShellOffset(0f, 0f),
                        modifier = Modifier.fillMaxSize(),
                    ) { placement ->
                        val entry = byId[placement.entryId] ?: return@WpSpatialStartLayout
                        WpTile(
                            entry = entry, tileSize = placement.size,
                            width = cell * placement.size.columns + seam * (placement.size.columns - 1),
                            height = cell * placement.size.rows + seam * (placement.size.rows - 1),
                            editing = editing, selected = selectedTile == placement.tileId,
                            canResize = entry.descriptor.supportedSizes.size > 1,
                            revealing = state.reveal?.tileId == placement.tileId,
                            revealProgress = if (state.reveal?.tileId == placement.tileId) revealPulse.value else 0f,
                            onClick = {
                                if (editing) onAction(LauncherUiAction.SelectStartTile(placement.tileId))
                                else onAction(LauncherUiAction.Open(entry.descriptor.launchToken))
                            },
                            onLongClick = { onAction(LauncherUiAction.EnterStartEdit(placement.tileId)) },
                            onUnpin = { onAction(LauncherUiAction.UnpinTile(placement.tileId)) },
                            onResize = { onAction(LauncherUiAction.ResizeTile(placement.tileId)) },
                            onMoveBy = { columns, rowDelta -> onAction(LauncherUiAction.MoveTileBy(placement.tileId, columns, rowDelta)) },
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val viewport = viewportCoordinates
                                if (viewport != null && viewport.isAttached && coordinates.isAttached) {
                                    tileBounds[placement.tileId] = viewport.localBoundingBoxOf(coordinates, clipBounds = false)
                                }
                            },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier.size(YokuliMetrics.MinTouch).testTag("all-apps-entry").combinedNoRipple {
                            if (editing) onAction(LauncherUiAction.ExitStartEdit) else onAction(LauncherUiAction.ShowAllApps)
                        }, contentAlignment = Alignment.Center,
                    ) { MarineIcon(MarineIconKind.APPS, colors.foreground, Modifier.size(28.dp)) }
                }
            }
            if (controls != null && selectedTile != null) {
                WpTileEditOverlay(controls, compact,
                    onUnpin = { onAction(LauncherUiAction.UnpinTile(selectedTile)) },
                    onResize = { onAction(LauncherUiAction.ResizeTile(selectedTile)) })
            }
            WpLauncherFeedback(
                state.transient, onAction,
                Modifier.align(Alignment.BottomCenter).zIndex(4f).onGloballyPositioned { coordinates ->
                    val viewport = viewportCoordinates
                    if (viewport != null && viewport.isAttached && coordinates.isAttached) {
                        feedbackBounds = viewport.localBoundingBoxOf(coordinates, clipBounds = false)
                    }
                },
            )
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitSelectedDragSlop(down: PointerInputChange): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.changes.any { it.id != down.id && it.pressed }) return null
        val change = event.changes.firstOrNull { it.id == down.id } ?: return null
        if (!change.pressed || change.isConsumed) return null
        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
            change.consume()
            return change
        }
    }
}

@Composable
private fun WpTile(
    entry: LauncherEntryUiState, tileSize: MarineTileSize, width: Dp, height: Dp,
    editing: Boolean, selected: Boolean, canResize: Boolean, revealing: Boolean, revealProgress: Float,
    onClick: () -> Unit, onLongClick: () -> Unit, onUnpin: () -> Unit, onResize: () -> Unit,
    onMoveBy: (Int, Int) -> Unit, modifier: Modifier = Modifier,
) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (selected) 1.025f else 1f, spring(), label = "wp-tile-selected")
    val small = tileSize.columns == 1 && tileSize.rows == 1
    val accessibilityMoves = if (editing && selected) buildList {
        add(CustomAccessibilityAction(stringResource(R.string.context_unpin)) { onUnpin(); true })
        if (canResize) add(CustomAccessibilityAction(stringResource(R.string.resize_tile)) { onResize(); true })
        add(CustomAccessibilityAction(stringResource(R.string.move_tile_left)) { onMoveBy(-1, 0); true })
        add(CustomAccessibilityAction(stringResource(R.string.move_tile_right)) { onMoveBy(1, 0); true })
        add(CustomAccessibilityAction(stringResource(R.string.move_tile_up)) { onMoveBy(0, -1); true })
        add(CustomAccessibilityAction(stringResource(R.string.move_tile_down)) { onMoveBy(0, 1); true })
    } else emptyList()
    Box(
        modifier.width(width).height(height).scale(scale * (1f + revealProgress * DERIVED_REVEAL_SCALE))
            .alpha(if (editing && !selected) .55f else 1f).testTag("tile-${entry.descriptor.entryId.value}")
            .semantics {
                wpTileAccentName = colors.spec.accent.displayName
                stateDescription = buildString {
                    append(entry.headline)
                    if (entry.detail.isNotBlank()) append(" · ${entry.detail}")
                }
                customActions = accessibilityMoves
                onLongClick { onLongClick(); true }
            }.wpTilt(interactions, enabled = !editing).background(colors.accent)
            .clickable(interactionSource = interactions, indication = null, onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize().padding(if (small) YokuliMetrics.TileSmallContentInset else YokuliMetrics.TileContentInset)) {
            entry.tileRenderer(tileSize).Render(
                LauncherTileRenderContext(tileSize, colors.onAccent, Modifier.fillMaxSize(), liveContentEnabled = !editing),
            )
        }
        if (revealing) Box(Modifier.fillMaxSize().border(3.dp, colors.onAccent).alpha(revealProgress.coerceIn(0f, 1f)).testTag("tile-reveal-highlight"))
    }
}

@Composable
private fun WpTileEditOverlay(controls: TileEditControls, compact: Boolean, onUnpin: () -> Unit, onResize: () -> Unit) {
    val colors = LocalWpTheme.current
    val density = LocalDensity.current
    val unpinLabel = stringResource(R.string.context_unpin)
    val resizeLabel = stringResource(R.string.resize_tile)
    @Composable
    fun Control(rect: EditControlRect, resize: Boolean, action: () -> Unit) {
        Box(
            Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .testTag(if (resize) "resize-selected-tile" else "unpin-selected-tile")
                .semantics(mergeDescendants = true) { contentDescription = if (resize) resizeLabel else unpinLabel }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = action),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(if (compact) 28.dp else 30.dp).background(colors.background, CircleShape)
                    .border(1.dp, colors.foreground, CircleShape)
                    .then(if (resize) Modifier.testTag("resize-affordance-disc") else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                MarineIcon(if (resize) MarineIconKind.RESIZE else MarineIconKind.UNPIN, colors.foreground,
                    Modifier.size(if (compact) 20.dp else 22.dp).then(if (resize) Modifier.testTag("resize-affordance-glyph") else Modifier))
            }
        }
    }
    Box(Modifier.fillMaxSize().zIndex(3f).testTag("tile-edit-overlay")) {
        Control(controls.unpin, false, onUnpin)
        controls.resize?.let { Control(it, true, onResize) }
    }
}

private fun Rect.toEditRect() = EditControlRect(left, top, right, bottom)
private fun StartInteractionState.selectedTile(): TileInstanceId? = when (this) {
    is StartInteractionState.EditIdle -> selectedTile
    is StartInteractionState.Dragging -> tileId
    else -> null
}
private fun StartInteractionState.isEditing(): Boolean = when (this) {
    is StartInteractionState.EditIdle, is StartInteractionState.Dragging, is StartInteractionState.Settling -> true
    else -> false
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedNoRipple(onClick: () -> Unit): Modifier = combinedClickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
)
