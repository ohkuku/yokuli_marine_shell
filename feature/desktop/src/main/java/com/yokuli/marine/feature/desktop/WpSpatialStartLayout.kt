package com.yokuli.marine.feature.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.ResolvedStartGeometry
import com.yokuli.shell.engine.interaction.ShellOffset
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TileDocumentEntry
import kotlin.math.roundToInt

/** Drawn positions are animation hand-off information, never durable tile coordinates. */
private class FloatingPosition {
    var lastDrawn: Offset? = null
    var releaseFrom: Offset? = null
}

/** Pixel-snapped spatial grid. Preview and commit consume the same packed document. */
@Composable
fun WpSpatialStartLayout(
    document: StartDocument,
    proposedDocument: StartDocument?,
    geometry: ResolvedStartGeometry,
    floatingTileId: TileInstanceId?,
    modifier: Modifier = Modifier,
    selectedTileId: TileInstanceId? = floatingTileId,
    floatingOffsetPx: ShellOffset = ShellOffset(0f, 0f),
    /** 手指位置在 placement 阶段读取，不以每个像素驱动整张开始屏幕重组。 */
    floatingOffsetProvider: (() -> ShellOffset)? = null,
    tileContent: @Composable (TileDocumentEntry) -> Unit,
) {
    val colors = LocalWpTheme.current
    val packed = remember(document, geometry.columns) { AdaptiveTilePacker.pack(document, geometry.columns) }
    val proposedPacked = remember(proposedDocument, geometry.columns) {
        proposedDocument?.let { AdaptiveTilePacker.pack(it, geometry.columns) }
    }
    val proposedById = remember(proposedPacked) { proposedPacked?.tiles?.associateBy { it.entry.tileId }.orEmpty() }
    val pitchPx = geometry.smallCellPx + geometry.seamPx
    val visualPlacements = remember(packed, proposedById, floatingTileId) {
        packed.tiles.map { placement ->
            if (placement.entry.tileId == floatingTileId) placement else proposedById[placement.entry.tileId] ?: placement
        }
    }
    val floatingPlacement = floatingTileId?.let(packed::tile)
    val insertionMarker = proposedPacked?.tiles?.firstOrNull { it.entry.tileId == floatingTileId }
    val measurementPlacements = remember(floatingPlacement, insertionMarker, visualPlacements) {
        listOfNotNull(floatingPlacement, insertionMarker) + visualPlacements
    }
    Layout(
        modifier = modifier,
        content = {
            floatingPlacement?.let { placement ->
                Box(
                    Modifier.offset { IntOffset(placement.cell.column * pitchPx, placement.cell.row * pitchPx) }
                        .background(colors.accent.copy(alpha = .24f)).testTag("tile-origin-placeholder"),
                )
            }
            insertionMarker?.let { placement ->
                Box(
                    Modifier.offset { IntOffset(placement.cell.column * pitchPx, placement.cell.row * pitchPx) }
                        .border(2.dp, colors.accentText).testTag("tile-insertion-marker"),
                )
            }
            visualPlacements.forEach { placement ->
                key(placement.entry.tileId.value) {
                    val floating = placement.entry.tileId == floatingTileId
                    val selected = placement.entry.tileId == selectedTileId
                    val target = Offset(placement.cell.column * pitchPx.toFloat(), placement.cell.row * pitchPx.toFloat())
                    val animated = remember { Animatable(target, Offset.VectorConverter) }
                    val positionMemory = remember { FloatingPosition() }
                    // A new grab can interrupt settling. Start at what was actually drawn, not
                    // at a grid position the previous animation has not reached yet.
                    val floatingOrigin = remember(floating, target) {
                        if (floating) positionMemory.lastDrawn ?: animated.value else target
                    }
                    // WpTile retains its existing selection zoom. This layer adds elevation and
                    // only the extra drag zoom; palette, opacity and wallpaper stay untouched.
                    val lift = animateFloatAsState(
                        targetValue = if (floating) 1f else if (selected) .45f else 0f,
                        animationSpec = spring(dampingRatio = 1f, stiffness = 650f),
                        label = "tile-edit-lift",
                    )
                    LaunchedEffect(floating, target) {
                        if (floating) {
                            animated.stop()
                        } else {
                            positionMemory.releaseFrom?.let { animated.snapTo(it) }
                            positionMemory.releaseFrom = null
                            animated.animateTo(target, spring(dampingRatio = .95f, stiffness = 550f,
                                visibilityThreshold = Offset(.5f, .5f)))
                            // Finish at the exact grid coordinate, not a near-enough pixel offset.
                            animated.snapTo(target)
                        }
                    }
                    Box(
                        Modifier.zIndex(when {
                            floating -> 3f
                            selected -> 2f
                            animated.isRunning -> 1f
                            else -> 0f
                        }).offset {
                            // Observe the animation even during hand-off, so clearing releaseFrom
                            // never leaves placement waiting for an unrelated recomposition.
                            val animationPosition = animated.value
                            val position = if (floating) {
                                val offset = floatingOffsetProvider?.invoke() ?: floatingOffsetPx
                                (floatingOrigin + Offset(offset.x, offset.y)).also { positionMemory.releaseFrom = it }
                            } else positionMemory.releaseFrom ?: animationPosition
                            positionMemory.lastDrawn = position
                            IntOffset(position.x.roundToInt(), position.y.roundToInt())
                        }.graphicsLayer {
                            val amount = lift.value.coerceIn(0f, 1f)
                            val dragLift = ((amount - .45f) / .55f).coerceIn(0f, 1f)
                            scaleX = 1f + .03f * dragLift
                            scaleY = scaleX
                            translationY = -6.dp.toPx() * amount
                            shadowElevation = 20.dp.toPx() * amount
                            rotationX = .7f * amount
                            rotationY = -.5f * amount
                            cameraDistance = 16f * density
                            clip = false
                        },
                    ) { tileContent(placement.entry) }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { index, measurable ->
            val placement = measurementPlacements[index]
            measurable.measure(
                Constraints.fixed(
                    geometry.tileWidthPx(placement.entry.size.columns),
                    geometry.tileHeightPx(placement.entry.size.rows),
                ),
            )
        }
        val rows = (proposedPacked ?: packed).documentHeightRows
        val desiredHeight = if (rows == 0) 0 else rows * pitchPx - geometry.seamPx
        layout(constraints.maxWidth, desiredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}
