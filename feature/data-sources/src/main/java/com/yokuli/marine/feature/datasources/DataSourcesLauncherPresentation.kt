package com.yokuli.marine.feature.datasources

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.data.source.SourceKind
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherIconRenderer
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherTileRenderer
import com.yokuli.shell.contract.MarineTileSize
import kotlin.math.min

data class DataSourcesStatusCopy(val compact: String, val expanded: String)

@Composable
fun dataSourcesStatusCopy(state: DataSourcesStatusState): DataSourcesStatusCopy? {
    if (!state.visible) return null
    return DataSourcesStatusCopy(
        compact = if (state.attentionCount > 0) {
            stringResource(R.string.launcher_status_sources_attention, state.attentionCount)
        } else {
            stringResource(R.string.launcher_status_sources_ok)
        },
        expanded = stringResource(
            R.string.launcher_status_sources_expanded,
            state.needsSelectionCount,
            state.interruptedCount,
        ),
    )
}

@Composable
fun dataSourcesLauncherVisualContribution(snapshot: MarineSourceSnapshot): LauncherEntryVisualContribution {
    val title = stringResource(R.string.data_sources_title)
    val tile = DataSourcesLauncherProjector.project(snapshot).tile
    return LauncherEntryVisualContribution(
        entryId = DataSourcesDestinations.EntryId,
        title = title,
        chineseIndex = 'S',
        headline = tileHeadline(tile),
        detail = tileDetail(tile),
        icon = LauncherIconRenderer { tint, modifier -> DataSourcesLauncherIcon(tint, modifier) },
        tileRenderers = mapOf(
            MarineTileSize.ICON_1X1 to LauncherTileRenderer { context ->
                DataSourcesSmallTile(context, rememberTile(tile, context.liveContentEnabled))
            },
            MarineTileSize.STANDARD_2X2 to LauncherTileRenderer { context ->
                DataSourcesMediumTile(context, title, rememberTile(tile, context.liveContentEnabled))
            },
            MarineTileSize.WIDE_4X2 to LauncherTileRenderer { context ->
                DataSourcesWideTile(context, title, rememberTile(tile, context.liveContentEnabled))
            },
        ),
    )
}

@Composable
private fun rememberTile(incoming: DataSourcesTileState, live: Boolean): DataSourcesTileState {
    val slot = remember { DataSourcesTileDisplaySlot(incoming) }
    return slot.resolve(incoming, live)
}

@Composable
private fun DataSourcesSmallTile(context: LauncherTileRenderContext, state: DataSourcesTileState) {
    Box(context.modifier.fillMaxSize().testTag("data-sources-tile-small"), contentAlignment = Alignment.Center) {
        DataSourcesLauncherIcon(context.contentColor, Modifier.size(42.dp))
        if (state.attentionCount > 0) {
            WpText(
                state.attentionCount.toString(),
                15,
                color = context.contentColor,
                weight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun DataSourcesMediumTile(
    context: LauncherTileRenderContext,
    title: String,
    state: DataSourcesTileState,
) {
    Box(context.modifier.fillMaxSize().testTag("data-sources-tile-medium")) {
        DataSourcesLauncherIcon(context.contentColor, Modifier.align(Alignment.TopStart).size(32.dp))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth()) {
            WpText(tileHeadline(state), 23, color = context.contentColor, weight = FontWeight.Light, maxLines = 2)
            WpText(tileDetail(state), 11, color = context.contentColor.copy(alpha = .84f), maxLines = 2)
        }
        WpText(title, 12, color = context.contentColor, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun DataSourcesWideTile(
    context: LauncherTileRenderContext,
    title: String,
    state: DataSourcesTileState,
) {
    Row(context.modifier.fillMaxSize().testTag("data-sources-tile-wide")) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            DataSourcesLauncherIcon(context.contentColor, Modifier.size(34.dp))
            Spacer(Modifier.weight(1f))
            WpText(tileHeadline(state), 22, color = context.contentColor, weight = FontWeight.Light, maxLines = 2)
            WpText(title, 12, color = context.contentColor)
        }
        Column(Modifier.width(150.dp).padding(start = 12.dp, top = 4.dp)) {
            if (state.attentionCount > 0) {
                if (state.needsSelectionCount > 0) {
                    WpText(stringResource(R.string.launcher_sources_needs_selection, state.needsSelectionCount), 13, color = context.contentColor)
                }
                if (state.interruptedCount > 0) {
                    WpText(stringResource(R.string.launcher_sources_interrupted, state.interruptedCount), 13, color = context.contentColor)
                }
                if (state.persistenceFailed) {
                    WpText(stringResource(R.string.launcher_sources_save_failed), 13, color = context.contentColor)
                }
            } else {
                state.relations.take(3).forEach { relation ->
                    WpText(
                        stringResource(
                            R.string.launcher_sources_relation,
                            dataKeyLabel(relation.key),
                            relationSourceName(relation),
                        ),
                        12,
                        color = context.contentColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun tileHeadline(state: DataSourcesTileState): String = when (state.priority) {
    DataSourcesTilePriority.WAITING -> stringResource(R.string.launcher_sources_waiting)
    DataSourcesTilePriority.USING -> stringResource(R.string.launcher_sources_using, state.usingCount)
    DataSourcesTilePriority.ATTENTION -> stringResource(R.string.launcher_sources_attention, state.attentionCount)
}

@Composable
private fun tileDetail(state: DataSourcesTileState): String = when {
    state.needsSelectionCount > 0 -> stringResource(R.string.launcher_sources_needs_selection, state.needsSelectionCount)
    state.interruptedCount > 0 -> stringResource(R.string.launcher_sources_interrupted, state.interruptedCount)
    state.persistenceFailed -> stringResource(R.string.launcher_sources_save_failed)
    state.relations.isNotEmpty() -> stringResource(
        R.string.launcher_sources_relation,
        dataKeyLabel(state.relations.first().key),
        relationSourceName(state.relations.first()),
    )
    else -> stringResource(R.string.launcher_sources_no_measurements)
}

@Composable
private fun relationSourceName(relation: DataSourceTileRelation): String =
    if (relation.sourceKind == SourceKind.PHONE_SYSTEM_LOCATION) {
        stringResource(R.string.phone_source_title)
    } else relation.sourceName

@Composable
private fun dataKeyLabel(key: DataKey): String = when (key) {
    DataKey.Position -> stringResource(R.string.data_position)
    DataKey.SpeedOverGround -> stringResource(R.string.data_sog)
    DataKey.CourseOverGround -> stringResource(R.string.data_cog)
    is DataKey.Heading -> stringResource(R.string.data_heading_short)
    is DataKey.Depth -> stringResource(R.string.data_depth_short)
    is DataKey.WindAngle -> stringResource(R.string.data_wind_angle_short)
    is DataKey.WindSpeed -> stringResource(R.string.data_wind_speed_short)
    DataKey.MagneticVariation -> stringResource(R.string.data_magnetic_variation)
    DataKey.SourceTime -> stringResource(R.string.data_source_time)
    DataKey.FixQuality -> stringResource(R.string.data_fix_quality)
    DataKey.Satellites -> stringResource(R.string.data_satellites)
    DataKey.HorizontalDilution -> stringResource(R.string.data_hdop)
    DataKey.Altitude -> stringResource(R.string.data_altitude)
    DataKey.PositionAccuracy -> stringResource(R.string.data_accuracy)
}

@Composable
private fun DataSourcesLauncherIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val stroke = unit * .075f
        val root = Offset(unit * .22f, unit * .50f)
        val branch = Offset(unit * .52f, unit * .50f)
        val upper = Offset(unit * .80f, unit * .25f)
        val lower = Offset(unit * .80f, unit * .75f)
        drawLine(color, root, branch, stroke)
        drawLine(color, branch, upper, stroke)
        drawLine(color, branch, lower, stroke)
        listOf(root, upper, lower).forEach { drawCircle(color, unit * .09f, it, style = Stroke(stroke)) }
    }
}
