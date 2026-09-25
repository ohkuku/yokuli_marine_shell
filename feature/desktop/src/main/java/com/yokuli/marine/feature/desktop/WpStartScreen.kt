package com.yokuli.marine.feature.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.Spacer
import com.yokuli.marine.core.design.StartWallpaperSurface
import com.yokuli.marine.core.design.startTileBackground
import com.yokuli.marine.core.design.LocalStartBackdrop
import com.yokuli.marine.core.design.StartBackdropMode
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.zIndex
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.WpFontFamily
import com.yokuli.marine.core.design.LocalWpTextScale
import com.yokuli.marine.core.design.YokuliBrandWordmark
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

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
    /** 宿主提供唯一设置页的访问入口，不在桌面复制背景设置。 */
    onPersonalizeStart: (() -> Unit)? = null,
    personalizeStartLabel: String = "Background & transparent tiles",
    /** Shell 临时编辑会话，保持当前桌面访问，不打开工坊应用。 */
    onEditTile: ((TileInstanceId) -> Unit)? = null,
    editTileLabel: String = "Edit content",
    /** 预览复用当前桌面的真实几何输入。 */
    onViewport: (StartViewport) -> Unit = {},
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
    // 连续坐标只供拖动与 placement 读取；LocalTileDrag 仅发布开始、跨格和结束等离散变化。
    var pointerCoordinates by remember { mutableStateOf<TileDragCoordinates?>(null) }
    var nextDragSessionId by remember { mutableStateOf(0L) }
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val tileBounds = remember { mutableStateMapOf<TileInstanceId, Rect>() }
    var feedbackBounds by remember { mutableStateOf<Rect?>(null) }
    var personalisationBounds by remember {mutableStateOf<Rect?>(null)}
    val latestFeedbackBounds by rememberUpdatedState(feedbackBounds.takeIf { state.transient != null })
    val canPersonalize=editing&&localTileDrag==null&&dragging==null&&state.transient==null&&(onPersonalizeStart!=null || (selectedTile!=null && onEditTile!=null))
    val latestPersonalisationBounds by rememberUpdatedState(personalisationBounds.takeIf {canPersonalize})

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
        if (
            local.finishing && local.targetCell == local.originCell &&
            local.insertionIndex == AdaptiveTilePacker.insertionIndexOf(local.sourceDocument, local.tileId)
        ) {
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

    StartWallpaperSurface(
        Modifier.fillMaxSize().testTag("start-screen")
            .semantics { wpThemeModeName = colors.spec.mode.name.lowercase() },
        scrollFraction = { if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f },
    ) {
        val colors = LocalWpTheme.current
        val availableWidthPx = with(density) { maxWidth.toPx().roundToInt() }
        val availableHeightPx = with(density) { maxHeight.toPx().roundToInt() }
        if (availableWidthPx <= 0 || availableHeightPx <= 0) return@StartWallpaperSurface
        val viewport = remember(availableWidthPx, availableHeightPx, density.density, density.fontScale) {
            StartViewport(availableWidthPx, availableHeightPx, density.density, 0, 0, density.fontScale)
        }
        SideEffect { onViewport(viewport) }
        val geometry = remember(viewport) { WpStartGeometryCalculator.calculate(viewport) }
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
        val selectedBounds = if (editing && localTileDrag == null) selectedTile?.let(tileBounds::get) else null
        val compact = selectedPlacement?.entry?.size?.let { it.columns == 1 && it.rows == 1 } == true
        val controls = if (editing && localTileDrag == null && selectedBounds != null) {
            TileEditControlGeometry.resolve(
                selectedBounds.toEditRect(), availableWidthPx.toFloat(),
                listOfNotNull(latestFeedbackBounds?.top, latestPersonalisationBounds?.top).minOrNull()?.coerceIn(0f, availableHeightPx.toFloat()) ?: availableHeightPx.toFloat(),
                touchPx, compact, (selectedEntry?.descriptor?.supportedSizes?.size ?: 0) > 1,
            )
        } else null
        val latestControls by rememberUpdatedState(controls)

        fun updateDrag(sessionId: Long, position: Offset? = null) {
            val current = localTileDrag?.takeIf { it.sessionId == sessionId } ?: return
            if (current.finishing || current.sourceDocument != latestDocument) return
            val previousCoordinates = pointerCoordinates ?: current.coordinates
            val coordinates = position?.let { previousCoordinates.movedTo(ShellOffset(it.x, it.y)) } ?: previousCoordinates
            pointerCoordinates = coordinates
            val hasMoved = current.hasMoved || coordinates.hasMovedBeyond(touchSlop)
            if (!hasMoved) {
                return
            }
            val offset = coordinates.contentOffset(scroll.value.toFloat())
            val target = hysteresis.resolve(current.originCell, offset, pitchPx, current.targetCell)
            val index = if (target == current.targetCell) current.insertionIndex
                else AdaptiveTilePacker.insertionIndexForCell(current.sourceDocument, geometry.columns, target, current.tileId)
            if (!current.hasMoved || target != current.targetCell || index != current.insertionIndex)
                localTileDrag = current.copy(hasMoved = true, targetCell = target, insertionIndex = index)
            if (target != current.targetCell) {
                latestAction(LauncherUiAction.TileCellTargetChanged(current.tileId, target, geometry.columns))
            }
        }
        val latestUpdateDrag by rememberUpdatedState<(Long, Offset?) -> Unit>({ session, point -> updateDrag(session, point) })
        LaunchedEffect(localTileDrag?.sessionId, pitchPx, availableHeightPx, scroll.maxValue) {
            val session = localTileDrag?.sessionId ?: return@LaunchedEffect
            snapshotFlow {
                val current = localTileDrag?.takeIf { it.sessionId == session && it.hasMoved && !it.finishing }
                val velocity = current?.let { autoScroll.velocity((pointerCoordinates ?: it.coordinates).pointer.y, availableHeightPx.toFloat()) } ?: 0f
                when { velocity < 0f -> -1; velocity > 0f -> 1; else -> 0 }
            }.distinctUntilChanged().collectLatest { direction ->
                if (direction == 0) return@collectLatest
                var lastFrame = withFrameNanos { it }
                while (true) {
                    val frame = withFrameNanos { it }
                    val current = localTileDrag?.takeIf { it.sessionId == session && !it.finishing } ?: break
                    val elapsed = (frame - lastFrame).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    lastFrame = frame
                    val coordinates = pointerCoordinates ?: current.coordinates
                    val requested = autoScroll.velocity(coordinates.pointer.y, availableHeightPx.toFloat()) * elapsed
                    if (requested == 0f || scroll.scrollBy(requested) == 0f) break
                    // 速度大小随手指连续变化，不重启每帧时钟；代次仍防止取消后恢复旧拖动。
                    latestUpdateDrag(session, null)
                }
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
                            latestFeedbackBounds?.contains(down.position) == true || latestPersonalisationBounds?.contains(down.position)==true) return@awaitEachGesture
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
                        pointerCoordinates = localTileDrag?.coordinates
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
                        floatingOffsetProvider = {
                            renderDrag?.let { (pointerCoordinates ?: it.coordinates).contentOffset(scroll.value.toFloat()) } ?: ShellOffset(0f, 0f)
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) { placement ->
                        val entry = byId[placement.entryId] ?: return@WpSpatialStartLayout
                        // 仅跨过可视边缘时使磁贴重组；不把连续滚动像素向数据订阅传播。
                        val visible by remember(placement.tileId, availableWidthPx, availableHeightPx) {
                            derivedStateOf {
                                tileBounds[placement.tileId]?.let { bounds ->
                                    bounds.bottom > 0f && bounds.top < availableHeightPx &&
                                        bounds.right > 0f && bounds.left < availableWidthPx
                                } == true
                            }
                        }
                        WpTile(
                            tileId = placement.tileId, entry = entry, tileSize = placement.size,
                            width = cell * placement.size.columns + seam * (placement.size.columns - 1),
                            height = cell * placement.size.rows + seam * (placement.size.rows - 1),
                            editing = editing, selected = selectedTile == placement.tileId, liveContentEnabled = visible && !editing,
                            canResize = entry.descriptor.supportedSizes.size > 1,
                            revealing = state.reveal?.tileId == placement.tileId,
                            revealProgress = { if (state.reveal?.tileId == placement.tileId) revealPulse.value else 0f },
                            onClick = {
                                if (editing) onAction(LauncherUiAction.SelectStartTile(placement.tileId))
                                else onAction(LauncherUiAction.Open(entry.descriptor.launchToken))
                            },
                            onLongClick = { onAction(LauncherUiAction.EnterStartEdit(placement.tileId)) },
                            onUnpin = { onAction(LauncherUiAction.UnpinTile(placement.tileId)) },
                            onResize = { onAction(LauncherUiAction.ResizeTile(placement.tileId)) },
                            onEditContent = onEditTile?.let { edit -> { edit(placement.tileId) } }, editContentLabel = editTileLabel,
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
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
                    val actionLabel = stringResource(if (editing) R.string.start_done else R.string.start_all_apps)
                    val textMeasurer = rememberTextMeasurer()
                    val actionTextStyle = TextStyle(fontFamily = WpFontFamily, fontSize = (15 * LocalWpTextScale.current).sp)
                    val actionWidth = with(density) {
                        textMeasurer.measure(actionLabel, actionTextStyle, maxLines = 1).size.width.toDp()
                    } + YokuliMetrics.MinTouch + 16.dp
                    val showBrand = maxWidth >= actionWidth + 132.dp
                    // 品牌只用原页脚的余白；字体放大或窄屏时先让出全部应用的入口。
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (showBrand) YokuliBrandWordmark(
                            Modifier.width(112.dp).height(24.dp), color = colors.foreground,
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.testTag("all-apps-entry").combinedNoRipple {
                                if (editing) onAction(LauncherUiAction.ExitStartEdit) else onAction(LauncherUiAction.ShowAllApps)
                            }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WpText(actionLabel, 15)
                            Box(Modifier.size(YokuliMetrics.MinTouch), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(24.dp)) {
                                    val stroke = size.minDimension * .0625f
                                    if (editing) {
                                        drawLine(colors.foreground, Offset(size.width*.18f, size.height*.50f), Offset(size.width*.40f, size.height*.72f), stroke)
                                        drawLine(colors.foreground, Offset(size.width*.40f, size.height*.72f), Offset(size.width*.83f, size.height*.25f), stroke)
                                    } else {
                                        drawLine(colors.foreground, Offset(size.width*.18f, size.height*.50f), Offset(size.width*.82f, size.height*.50f), stroke)
                                        drawLine(colors.foreground, Offset(size.width*.55f, size.height*.23f), Offset(size.width*.82f, size.height*.50f), stroke)
                                        drawLine(colors.foreground, Offset(size.width*.82f, size.height*.50f), Offset(size.width*.55f, size.height*.77f), stroke)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (controls != null && selectedTile != null) {
                WpTileEditOverlay(controls, compact,
                    onUnpin = { onAction(LauncherUiAction.UnpinTile(selectedTile)) },
                    onResize = { onAction(LauncherUiAction.ResizeTile(selectedTile)) })
            }
            if(canPersonalize) {
                Row(Modifier.align(Alignment.BottomCenter).zIndex(3.5f).fillMaxWidth()
                    .onGloballyPositioned {coordinates->
                        val currentViewport = viewportCoordinates
                        if(currentViewport!=null&&currentViewport.isAttached&&coordinates.isAttached)
                            personalisationBounds=currentViewport.localBoundingBoxOf(coordinates,clipBounds=false)
                    }.background(colors.background), verticalAlignment = Alignment.CenterVertically) {
                    if (selectedTile != null && onEditTile != null) {
                        Box(Modifier.weight(1f).clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button) {
                            onEditTile(selectedTile)
                        }.padding(horizontal=16.dp,vertical=14.dp),contentAlignment=Alignment.Center) {
                            WpText(editTileLabel,15,color=colors.foreground,maxLines=2)
                        }
                    }
                    if (onPersonalizeStart != null) {
                        Box(Modifier.weight(1f).clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button) {
                            onPersonalizeStart()
                        }.padding(horizontal=16.dp,vertical=14.dp),contentAlignment=Alignment.Center) {
                            WpText(personalizeStartLabel,15,color=colors.foreground,maxLines=2)
                        }
                    }
                }
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
    tileId: TileInstanceId, entry: LauncherEntryUiState, tileSize: MarineTileSize, width: Dp, height: Dp,
    editing: Boolean, selected: Boolean, canResize: Boolean, revealing: Boolean, revealProgress: () -> Float, liveContentEnabled: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onUnpin: () -> Unit, onResize: () -> Unit,
    onEditContent: (() -> Unit)?, editContentLabel: String,
    onMoveBy: (Int, Int) -> Unit, modifier: Modifier = Modifier,
) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (selected) 1.025f else 1f, spring(), label = "wp-tile-selected")
    val small = tileSize.columns == 1 && tileSize.rows == 1
    val accessibilityMoves = if (editing && selected) buildList {
        if (onEditContent != null) add(CustomAccessibilityAction(editContentLabel) { onEditContent(); true })
        add(CustomAccessibilityAction(stringResource(R.string.context_unpin)) { onUnpin(); true })
        if (canResize) add(CustomAccessibilityAction(stringResource(R.string.resize_tile)) { onResize(); true })
        add(CustomAccessibilityAction(stringResource(R.string.move_tile_left)) { onMoveBy(-1, 0); true })
        add(CustomAccessibilityAction(stringResource(R.string.move_tile_right)) { onMoveBy(1, 0); true })
        add(CustomAccessibilityAction(stringResource(R.string.move_tile_up)) { onMoveBy(0, -1); true })
        add(CustomAccessibilityAction(stringResource(R.string.move_tile_down)) { onMoveBy(0, 1); true })
    } else emptyList()
    Box(
        modifier.width(width).height(height).graphicsLayer {
                scaleX = scale * (1f + revealProgress() * DERIVED_REVEAL_SCALE); scaleY = scaleX
                alpha = if (editing && !selected) .55f else 1f
            }.testTag(tileId.value)
            .semantics {
                wpTileAccentName = colors.spec.accent.displayName
                contentDescription = entry.title
                stateDescription = buildString {
                    append(entry.headline)
                    if (entry.detail.isNotBlank()) append(" · ${entry.detail}")
                }
                customActions = accessibilityMoves
                onLongClick { onLongClick(); true }
            }.wpTilt(interactions, enabled = !editing, maximumDegrees = 1.5f).clipToBounds().startTileBackground()
            .clickable(interactionSource = interactions, indication = null, onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize().padding(if (entry.visual.fullBleed && !small) 0.dp else if (small) YokuliMetrics.TileSmallContentInset else YokuliMetrics.TileContentInset)) {
            entry.tileRenderer(tileSize).Render(
                LauncherTileRenderContext(tileSize, if (LocalStartBackdrop.current.image != null && LocalStartBackdrop.current.mode != StartBackdropMode.NONE && LocalStartBackdrop.current.tileOpacity < .7f) androidx.compose.ui.graphics.Color.White else colors.onAccent, Modifier.fillMaxSize(), liveContentEnabled = liveContentEnabled),
            )
        }
        if (revealing) Box(Modifier.fillMaxSize().graphicsLayer { alpha = revealProgress().coerceIn(0f, 1f) }
            .border(3.dp, colors.onAccent).testTag("tile-reveal-highlight"))
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
                Modifier.size(if (compact) 30.dp else 32.dp).background(colors.background)
                    .border(1.dp, colors.foreground)
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
