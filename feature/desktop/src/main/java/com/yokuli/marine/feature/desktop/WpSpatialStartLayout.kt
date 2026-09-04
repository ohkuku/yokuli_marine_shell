package com.yokuli.marine.feature.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.ResolvedStartGeometry
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement

/**
 * Pixel-snapped Start grid. The document owns coordinates; transient proposals only animate
 * affected visual positions and never rewrite unrelated whitespace.
 */
@Composable
fun WpSpatialStartLayout(
    document: StartDocument,
    proposedDocument: StartDocument?,
    geometry: ResolvedStartGeometry,
    floatingTileId: TileInstanceId?,
    modifier: Modifier = Modifier,
    tileContent: @Composable (TilePlacement) -> Unit,
) {
    val proposedById = proposedDocument?.placements?.associateBy { it.tileId }.orEmpty()
    val pitchPx = geometry.smallCellPx + geometry.seamPx
    val visualPlacements = document.placements.map { placement ->
        if (placement.tileId == floatingTileId) placement else proposedById[placement.tileId] ?: placement
    }
    Layout(
        modifier = modifier,
        content = {
            visualPlacements.forEach { placement ->
                key(placement.tileId.value) {
                    val targetX = placement.cell.column * pitchPx.toFloat()
                    val targetY = placement.cell.row * pitchPx.toFloat()
                    val animatedX by animateFloatAsState(targetX, tween(180), label = "wp-spatial-x")
                    val animatedY by animateFloatAsState(targetY, tween(180), label = "wp-spatial-y")
                    androidx.compose.foundation.layout.Box(
                        Modifier.graphicsLayer {
                            translationX = animatedX
                            translationY = animatedY
                        },
                    ) { tileContent(placement) }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { index, measurable ->
            val placement = visualPlacements[index]
            measurable.measure(
                Constraints.fixed(
                    width = geometry.tileWidthPx(placement.size.columns),
                    height = geometry.tileHeightPx(placement.size.rows),
                ),
            )
        }
        val spatialDocument = proposedDocument ?: document
        val desiredHeight = spatialDocument.placements.maxOfOrNull { placement ->
            placement.cell.row * pitchPx + geometry.tileHeightPx(placement.size.rows)
        } ?: 0
        layout(constraints.maxWidth, desiredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}
