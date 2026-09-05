package com.yokuli.marine.feature.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
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

private class FloatingPosition(var lastDrawn: Offset? = null)

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
    tileContent: @Composable (TileDocumentEntry) -> Unit,
) {
    val colors = LocalWpTheme.current
    val packed = remember(document, geometry.columns) { AdaptiveTilePacker.pack(document, geometry.columns) }
    val proposedPacked = remember(proposedDocument, geometry.columns) {
        proposedDocument?.let { AdaptiveTilePacker.pack(it, geometry.columns) }
    }
    val proposedById = proposedPacked?.tiles?.associateBy { it.entry.tileId }.orEmpty()
    val pitchPx = geometry.smallCellPx + geometry.seamPx
    val visualPlacements = packed.tiles.map { placement ->
        if (placement.entry.tileId == floatingTileId) placement else proposedById[placement.entry.tileId] ?: placement
    }
    val floatingPlacement = floatingTileId?.let(packed::tile)
    val insertionMarker = proposedPacked?.tiles?.firstOrNull { it.entry.tileId == floatingTileId }
    val measurementPlacements = listOfNotNull(floatingPlacement, insertionMarker) + visualPlacements
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
                        .border(2.dp, colors.accent).testTag("tile-insertion-marker"),
                )
            }
            visualPlacements.forEach { placement ->
                key(placement.entry.tileId.value) {
                    val floating = placement.entry.tileId == floatingTileId
                    val target = Offset(placement.cell.column * pitchPx.toFloat(), placement.cell.row * pitchPx.toFloat())
                    val animated = remember { Animatable(target, Offset.VectorConverter) }
                    val heldPosition = remember { FloatingPosition() }
                    LaunchedEffect(floating, target) {
                        if (floating) {
                            animated.stop()
                        } else {
                            // Land from the last pixel drawn under the finger, never snap back to the origin first.
                            heldPosition.lastDrawn?.let { animated.snapTo(it) }
                            heldPosition.lastDrawn = null
                            animated.animateTo(target, spring())
                        }
                    }
                    Box(
                        Modifier.zIndex(
                            if (floating) 2f else if (placement.entry.tileId == selectedTileId) 1f else 0f,
                        ).offset {
                            val position = if (floating) {
                                (target + Offset(floatingOffsetPx.x, floatingOffsetPx.y)).also { heldPosition.lastDrawn = it }
                            } else heldPosition.lastDrawn ?: animated.value
                            IntOffset(position.x.roundToInt(), position.y.roundToInt())
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
