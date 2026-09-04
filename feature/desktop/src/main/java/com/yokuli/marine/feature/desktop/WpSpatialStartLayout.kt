package com.yokuli.marine.feature.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.ResolvedStartGeometry
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TileDocumentEntry
import com.yokuli.marine.core.design.LocalWpTheme

/**
 * Pixel-snapped Start grid. The durable document owns only semantic rank and tile size;
 * [AdaptiveTilePacker] derives viewport cells and transient proposals animate reflow.
 */
@Composable
fun WpSpatialStartLayout(
    document: StartDocument,
    proposedDocument: StartDocument?,
    geometry: ResolvedStartGeometry,
    floatingTileId: TileInstanceId?,
    modifier: Modifier = Modifier,
    tileContent: @Composable (TileDocumentEntry) -> Unit,
) {
    val colors = LocalWpTheme.current
    val packed = AdaptiveTilePacker.pack(document, geometry.columns)
    val proposedPacked = proposedDocument?.let { AdaptiveTilePacker.pack(it, geometry.columns) }
    val proposedById = proposedPacked?.tiles?.associateBy { it.entry.tileId }.orEmpty()
    val pitchPx = geometry.smallCellPx + geometry.seamPx
    val visualPlacements = packed.tiles.map { placement ->
        if (placement.entry.tileId == floatingTileId) placement else proposedById[placement.entry.tileId] ?: placement
    }
    val floatingPlacement = packed.tiles.firstOrNull { it.entry.tileId == floatingTileId }
    val insertionMarker = proposedPacked?.tiles?.firstOrNull { it.entry.tileId == floatingTileId }
    val measurementPlacements = listOfNotNull(floatingPlacement, insertionMarker) + visualPlacements
    Layout(
        modifier = modifier,
        content = {
            floatingPlacement?.let { placement ->
                Box(
                    Modifier.graphicsLayer {
                        translationX = placement.cell.column * pitchPx.toFloat()
                        translationY = placement.cell.row * pitchPx.toFloat()
                    }.background(colors.accent.copy(alpha = .24f)).testTag("tile-origin-placeholder"),
                )
            }
            insertionMarker?.let { placement ->
                Box(
                    Modifier.graphicsLayer {
                        translationX = placement.cell.column * pitchPx.toFloat()
                        translationY = placement.cell.row * pitchPx.toFloat()
                    }.border(2.dp, colors.accent).testTag("tile-insertion-marker"),
                )
            }
            visualPlacements.forEach { placement ->
                key(placement.entry.tileId.value) {
                    val targetX = placement.cell.column * pitchPx.toFloat()
                    val targetY = placement.cell.row * pitchPx.toFloat()
                    val animatedX by animateFloatAsState(targetX, spring(), label = "wp-spatial-x")
                    val animatedY by animateFloatAsState(targetY, spring(), label = "wp-spatial-y")
                    Box(
                        Modifier.graphicsLayer {
                            translationX = animatedX
                            translationY = animatedY
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
                    width = geometry.tileWidthPx(placement.entry.size.columns),
                    height = geometry.tileHeightPx(placement.entry.size.rows),
                ),
            )
        }
        val spatialLayout = proposedPacked ?: packed
        val desiredHeight = spatialLayout.tiles.maxOfOrNull { placement ->
            placement.cell.row * pitchPx + geometry.tileHeightPx(placement.entry.size.rows)
        } ?: 0
        layout(constraints.maxWidth, desiredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}
