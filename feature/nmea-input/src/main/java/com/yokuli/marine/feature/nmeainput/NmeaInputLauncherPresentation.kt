package com.yokuli.marine.feature.nmeainput

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
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherIconRenderer
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherTileRenderer
import com.yokuli.shell.contract.MarineTileSize
import kotlin.math.min

data class NmeaInputStatusCopy(val compact: String, val expanded: String)

@Composable
fun nmeaInputStatusCopy(state: NmeaInputStatusState): NmeaInputStatusCopy? {
    if (!state.visible) return null
    return NmeaInputStatusCopy(
        compact = if (state.attentionCount > 0) {
            stringResource(R.string.launcher_status_nmea_attention, state.attentionCount)
        } else {
            stringResource(R.string.launcher_status_nmea_compact, state.receivingCount, state.enabledCount)
        },
        expanded = stringResource(
            R.string.launcher_status_nmea_expanded,
            state.receivingCount,
            state.enabledCount,
            state.attentionCount,
        ),
    )
}

@Composable
fun nmeaInputLauncherVisualContribution(snapshot: NmeaRuntimeSnapshot): LauncherEntryVisualContribution {
    val title = stringResource(R.string.nmea_input_title)
    val tile = NmeaInputLauncherProjector.project(snapshot).tile
    return LauncherEntryVisualContribution(
        entryId = NmeaInputDestinations.EntryId,
        title = title,
        chineseIndex = 'N',
        headline = tileHeadline(tile),
        detail = tileDetail(tile),
        icon = LauncherIconRenderer { tint, modifier -> NmeaInputLauncherIcon(tint, modifier) },
        tileRenderers = mapOf(
            MarineTileSize.ICON_1X1 to LauncherTileRenderer { context ->
                NmeaSmallTile(context, rememberTile(tile, context.liveContentEnabled))
            },
            MarineTileSize.STANDARD_2X2 to LauncherTileRenderer { context ->
                NmeaMediumTile(context, title, rememberTile(tile, context.liveContentEnabled))
            },
            MarineTileSize.WIDE_4X2 to LauncherTileRenderer { context ->
                NmeaWideTile(context, title, rememberTile(tile, context.liveContentEnabled))
            },
        ),
    )
}

@Composable
private fun rememberTile(incoming: NmeaInputTileState, live: Boolean): NmeaInputTileState {
    val slot = remember { NmeaInputTileDisplaySlot(incoming) }
    return slot.resolve(incoming, live)
}

@Composable
private fun NmeaSmallTile(context: LauncherTileRenderContext, state: NmeaInputTileState) {
    Box(context.modifier.fillMaxSize().testTag("nmea-tile-small"), contentAlignment = Alignment.Center) {
        NmeaInputLauncherIcon(context.contentColor, Modifier.size(42.dp))
        val badge = when {
            state.attentionCount > 0 -> "!"
            state.receivingCount > 0 -> state.receivingCount.toString()
            else -> null
        }
        badge?.let {
            WpText(it, 15, color = context.contentColor, weight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun NmeaMediumTile(context: LauncherTileRenderContext, title: String, state: NmeaInputTileState) {
    Box(context.modifier.fillMaxSize().testTag("nmea-tile-medium")) {
        NmeaInputLauncherIcon(context.contentColor, Modifier.align(Alignment.TopStart).size(32.dp))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth()) {
            WpText(tileHeadline(state), 23, color = context.contentColor, weight = FontWeight.Light, maxLines = 2)
            WpText(tileDetail(state), 11, color = context.contentColor.copy(alpha = .84f), maxLines = 2)
        }
        WpText(title, 12, color = context.contentColor, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun NmeaWideTile(context: LauncherTileRenderContext, title: String, state: NmeaInputTileState) {
    Row(context.modifier.fillMaxSize().testTag("nmea-tile-wide")) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            NmeaInputLauncherIcon(context.contentColor, Modifier.size(34.dp))
            Spacer(Modifier.weight(1f))
            WpText(tileHeadline(state), 22, color = context.contentColor, weight = FontWeight.Light, maxLines = 2)
            WpText(title, 12, color = context.contentColor)
        }
        Column(Modifier.width(138.dp).padding(start = 12.dp, top = 4.dp)) {
            state.connectionRows.take(2).forEach { row ->
                WpText(row.name, 13, color = context.contentColor, maxLines = 1)
                WpText(connectionStateLabel(row), 10, color = context.contentColor.copy(alpha = .82f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun tileHeadline(state: NmeaInputTileState): String = when (state.priority) {
    NmeaInputTilePriority.UNCONFIGURED -> stringResource(R.string.launcher_nmea_unconfigured)
    NmeaInputTilePriority.STOPPED -> stringResource(R.string.launcher_nmea_stopped)
    NmeaInputTilePriority.WAITING -> stringResource(R.string.launcher_nmea_waiting)
    NmeaInputTilePriority.RECEIVING -> stringResource(
        R.string.launcher_nmea_receiving,
        state.receivingCount,
        state.enabledCount,
    )
    NmeaInputTilePriority.ATTENTION -> stringResource(R.string.launcher_nmea_attention, state.attentionCount)
}

@Composable
private fun tileDetail(state: NmeaInputTileState): String = when {
    state.attentionCount > 0 -> stringResource(R.string.launcher_nmea_attention_detail, state.attentionCount)
    state.connectionRows.size == 1 && state.connectionRows.single().state == NmeaInputConnectionTileState.RECEIVING &&
        state.connectionRows.single().validFramesPerSecond > 0.0 -> stringResource(
        R.string.launcher_nmea_single_rate,
        state.connectionRows.single().name,
        state.connectionRows.single().validFramesPerSecond,
    )
    state.connectionRows.size == 1 -> state.connectionRows.single().name
    else -> stringResource(R.string.launcher_nmea_configured, state.configuredCount)
}

@Composable
private fun connectionStateLabel(row: NmeaInputConnectionTileRow): String = when (row.state) {
    NmeaInputConnectionTileState.STOPPED -> stringResource(R.string.launcher_row_stopped)
    NmeaInputConnectionTileState.STARTING -> stringResource(R.string.launcher_row_starting)
    NmeaInputConnectionTileState.TCP_WAITING -> stringResource(R.string.launcher_row_tcp_waiting)
    NmeaInputConnectionTileState.UDP_LISTENING -> stringResource(R.string.launcher_row_udp_listening)
    NmeaInputConnectionTileState.WAITING_FOR_NETWORK -> stringResource(R.string.launcher_row_network)
    NmeaInputConnectionTileState.RECONNECTING -> stringResource(R.string.launcher_row_reconnecting)
    NmeaInputConnectionTileState.INPUT_WITHOUT_VALID_NMEA -> stringResource(R.string.launcher_row_invalid)
    NmeaInputConnectionTileState.RECEIVING -> if (row.validFramesPerSecond > 0.0) {
        stringResource(R.string.launcher_row_receiving_rate, row.validFramesPerSecond)
    } else stringResource(R.string.launcher_row_receiving)
    NmeaInputConnectionTileState.INTERRUPTED -> stringResource(R.string.launcher_row_interrupted)
    NmeaInputConnectionTileState.OVERLOADED -> stringResource(R.string.launcher_row_overloaded)
    NmeaInputConnectionTileState.PLATFORM_START_REQUIRED -> stringResource(R.string.launcher_row_resume)
    NmeaInputConnectionTileState.FAILED -> stringResource(R.string.launcher_row_failed)
}

@Composable
private fun NmeaInputLauncherIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val stroke = unit * .075f
        repeat(3) { index ->
            val y = unit * (.26f + index * .24f)
            drawLine(
                color,
                Offset(unit * .18f, y),
                Offset(unit * .82f, y),
                stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        drawCircle(color, unit * .12f, Offset(unit * .30f, unit * .50f), style = Stroke(stroke))
    }
}
