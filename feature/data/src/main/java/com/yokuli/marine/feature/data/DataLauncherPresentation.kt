package com.yokuli.marine.feature.data

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.PresentationCadence
import com.yokuli.marine.core.design.rememberCadencedLiveValue
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.DepthReference
import com.yokuli.marine.data.model.HeadingReference
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.WindReference
import com.yokuli.marine.data.model.WindSpeedReference
import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.runtime.ConnectionInputState
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.ResolvedDatum
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherIconRenderer
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherTileRenderer
import com.yokuli.shell.contract.MarineTileSize
import kotlin.math.min
import java.util.Locale

enum class DataTileValueKind { POSITION, COURSE_SPEED, HEADING, DEPTH, WIND }

data class DataTileValue(val kind: DataTileValueKind, val value: String)

data class DataLauncherState(
    val configuredInputCount: Int,
    val receivingInputCount: Int,
    val usingGroupCount: Int,
    val attentionCount: Int,
    val groupLines: List<String>,
    val importantValues: List<DataTileValue> = emptyList(),
) {
    val critical: Boolean get() = attentionCount > 0
}

object DataLauncherProjector {
    fun project(nmea: NmeaRuntimeSnapshot, sources: MarineSourceSnapshot): DataLauncherState {
        val domain = DataDomainProjector.project(sources, nmea)
        return DataLauncherState(
            configuredInputCount = nmea.connections.size,
            receivingInputCount = nmea.connections.count {
                it.stored.runIntent == ConnectionRunIntent.ENABLED &&
                    it.input == ConnectionInputState.RECEIVING_VALID_FRAMES
            },
            usingGroupCount = domain.groups.count { it.status == SourceGroupStatus.USING },
            attentionCount = domain.groups.count {
                it.status in setOf(
                    SourceGroupStatus.NEEDS_SELECTION,
                    SourceGroupStatus.SELECTED_UNAVAILABLE,
                    SourceGroupStatus.MIXED_LEGACY_SELECTION,
                )
            } + if (sources.lastFailure != null) 1 else 0,
            groupLines = domain.groups.filter { it.status == SourceGroupStatus.USING }
                .take(3)
                .map { it.group.name },
            importantValues = importantResolvedValues(domain),
        )
    }

    private fun importantResolvedValues(state: DataUiState): List<DataTileValue> = buildList {
        state.firstResolved(DataKey.Position)?.value?.formatTile()?.let { add(DataTileValue(DataTileValueKind.POSITION, it)) }
        val speed = state.firstResolved(DataKey.SpeedOverGround)?.value?.formatTile()
        val course = state.firstResolved(DataKey.CourseOverGround)?.value?.formatTile()
        listOfNotNull(speed, course).takeIf { it.isNotEmpty() }?.joinToString(" · ")
            ?.let { add(DataTileValue(DataTileValueKind.COURSE_SPEED, it)) }
        state.firstResolved(
            DataKey.Heading(HeadingReference.TRUE),
            DataKey.Heading(HeadingReference.MAGNETIC),
        )?.value?.formatTile()?.let { add(DataTileValue(DataTileValueKind.HEADING, it)) }
        state.firstResolved(
            DataKey.Depth(DepthReference.BELOW_SURFACE),
            DataKey.Depth(DepthReference.BELOW_TRANSDUCER),
            DataKey.Depth(DepthReference.BELOW_KEEL),
        )?.value?.formatTile()?.let { add(DataTileValue(DataTileValueKind.DEPTH, it)) }
        val angle = state.firstResolved(
            DataKey.WindAngle(WindReference.APPARENT),
            DataKey.WindAngle(WindReference.TRUE_RELATIVE),
            DataKey.WindAngle(WindReference.TRUE_NORTH),
            DataKey.WindAngle(WindReference.MAGNETIC_NORTH),
        )?.value?.formatTile()
        val wind = state.firstResolved(
            DataKey.WindSpeed(WindSpeedReference.APPARENT),
            DataKey.WindSpeed(WindSpeedReference.TRUE),
        )?.value?.formatTile()
        listOfNotNull(angle, wind).takeIf { it.isNotEmpty() }?.joinToString(" · ")
            ?.let { add(DataTileValue(DataTileValueKind.WIND, it)) }
    }.take(3)

    private fun DataUiState.firstResolved(vararg keys: DataKey): ResolvedDatum? = keys
        .asSequence()
        .mapNotNull(resolvedValues::get)
        .firstOrNull { it.availability in setOf(SourceCandidateAvailability.LIVE, SourceCandidateAvailability.HELD) }

    private fun MarineValue.formatTile(): String = when (this) {
        is MarineValue.Position -> String.format(Locale.US, "%.3f°, %.3f°", latitudeDegrees, longitudeDegrees)
        is MarineValue.Decimal -> when (unit) {
            MarineUnit.DEGREES -> String.format(Locale.US, "%.1f°", value)
            MarineUnit.KNOTS -> String.format(Locale.US, "%.1f kn", value)
            MarineUnit.METERS -> String.format(Locale.US, "%.1f m", value)
            MarineUnit.DIMENSIONLESS -> String.format(Locale.US, "%.2f", value)
        }
        is MarineValue.Count -> value.toString()
        is MarineValue.UtcEpochMillis -> value.toString()
    }
}

data class DataStatusCopy(val compact: String, val expanded: String)

@Composable
fun dataStatusCopy(state: DataLauncherState): DataStatusCopy? {
    if (state.configuredInputCount == 0 && state.usingGroupCount == 0 && state.attentionCount == 0) return null
    return DataStatusCopy(
        compact = if (state.attentionCount > 0) {
            stringResource(R.string.data_status_attention, state.attentionCount)
        } else {
            stringResource(R.string.data_status_receiving, state.receivingInputCount)
        },
        expanded = stringResource(
            R.string.data_status_expanded,
            state.receivingInputCount,
            state.configuredInputCount,
            state.usingGroupCount,
        ),
    )
}

@Composable
fun dataLauncherVisualContribution(
    nmea: NmeaRuntimeSnapshot,
    sources: MarineSourceSnapshot,
): LauncherEntryVisualContribution {
    val state = DataLauncherProjector.project(nmea, sources)
    val title = stringResource(R.string.data_title)
    return LauncherEntryVisualContribution(
        entryId = DataDestinations.EntryId,
        title = title,
        chineseIndex = 'D',
        headline = if (state.attentionCount > 0) {
            stringResource(R.string.data_tile_attention, state.attentionCount)
        } else {
            stringResource(R.string.data_tile_receiving, state.receivingInputCount)
        },
        detail = stringResource(R.string.data_tile_groups, state.usingGroupCount),
        icon = LauncherIconRenderer { tint, modifier -> DataLauncherIcon(tint, modifier) },
        tileRenderers = mapOf(
            MarineTileSize.ICON_1X1 to LauncherTileRenderer { DataSmallTile(it, state) },
            MarineTileSize.STANDARD_2X2 to LauncherTileRenderer { DataMediumTile(it, title, rememberDataTile(state, it.liveContentEnabled)) },
            MarineTileSize.WIDE_4X2 to LauncherTileRenderer { DataWideTile(it, title, rememberDataTile(state, it.liveContentEnabled)) },
        ),
    )
}

@Composable
private fun rememberDataTile(incoming: DataLauncherState, live: Boolean): DataLauncherState {
    val cadenced = rememberCadencedLiveValue(
        incoming,
        structuralKey = Triple(incoming.critical, incoming.attentionCount, incoming.groupLines),
        cadence = PresentationCadence.StartTile,
    )
    val slot = remember { DataTileDisplaySlot(incoming) }
    return slot.resolve(cadenced, live)
}

private class DataTileDisplaySlot(initial: DataLauncherState) {
    private var shown = initial
    fun resolve(incoming: DataLauncherState, live: Boolean): DataLauncherState {
        if (live || incoming.critical || shown.critical != incoming.critical) shown = incoming
        return shown
    }
}

@Composable
private fun DataSmallTile(context: LauncherTileRenderContext, state: DataLauncherState) {
    Box(context.modifier.fillMaxSize().testTag("data-tile-small"), contentAlignment = Alignment.Center) {
        DataLauncherIcon(context.contentColor, Modifier.size(42.dp))
        if (state.attentionCount > 0) {
            WpText("!", 16, color = context.contentColor, weight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun DataMediumTile(context: LauncherTileRenderContext, title: String, state: DataLauncherState) {
    Box(context.modifier.fillMaxSize().testTag("data-tile-medium")) {
        DataLauncherIcon(context.contentColor, Modifier.size(34.dp).align(Alignment.TopStart))
        Column(Modifier.align(Alignment.CenterStart)) {
            WpText(
                if (state.attentionCount > 0) stringResource(R.string.data_tile_attention, state.attentionCount)
                else stringResource(R.string.data_tile_receiving, state.receivingInputCount),
                22,
                color = context.contentColor,
                weight = FontWeight.Light,
            )
            WpText(stringResource(R.string.data_tile_groups, state.usingGroupCount), 11, color = context.contentColor.copy(alpha = .84f))
            state.importantValues.firstOrNull()?.let { WpText(it.value, 10, color = context.contentColor.copy(alpha = .84f), maxLines = 1) }
        }
        WpText(title, 12, color = context.contentColor, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun DataWideTile(context: LauncherTileRenderContext, title: String, state: DataLauncherState) {
    Row(context.modifier.fillMaxSize().testTag("data-tile-wide")) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            DataLauncherIcon(context.contentColor, Modifier.size(34.dp))
            Spacer(Modifier.weight(1f))
            WpText(stringResource(R.string.data_tile_receiving, state.receivingInputCount), 21, color = context.contentColor, weight = FontWeight.Light)
            WpText(title, 12, color = context.contentColor)
        }
        Column(Modifier.width(150.dp).padding(start = 12.dp, top = 4.dp)) {
            if (state.attentionCount > 0) WpText(stringResource(R.string.data_tile_attention, state.attentionCount), 13, color = context.contentColor)
            WpText(stringResource(R.string.data_tile_groups, state.usingGroupCount), 13, color = context.contentColor)
            state.importantValues.forEach { value ->
                WpText(tileValueLabel(value.kind), 9, color = context.contentColor.copy(alpha = .72f), maxLines = 1)
                WpText(value.value, 11, color = context.contentColor, maxLines = 1)
            }
            if (state.importantValues.isEmpty()) {
                state.groupLines.forEach { WpText(it.replace('_', ' ').lowercase(), 10, color = context.contentColor.copy(alpha = .82f)) }
            }
        }
    }
}

@Composable
private fun tileValueLabel(kind: DataTileValueKind): String = stringResource(
    when (kind) {
        DataTileValueKind.POSITION -> R.string.data_tile_value_position
        DataTileValueKind.COURSE_SPEED -> R.string.data_tile_value_course_speed
        DataTileValueKind.HEADING -> R.string.data_tile_value_heading
        DataTileValueKind.DEPTH -> R.string.data_tile_value_depth
        DataTileValueKind.WIND -> R.string.data_tile_value_wind
    },
)

@Composable
private fun DataLauncherIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val stroke = unit * .075f
        val left = unit * .18f
        val middle = unit * .5f
        val right = unit * .82f
        val points = listOf(
            Offset(left, unit * .25f), Offset(left, unit * .75f),
            Offset(middle, unit * .5f), Offset(right, unit * .25f), Offset(right, unit * .75f),
        )
        drawLine(color, points[0], points[2], stroke, StrokeCap.Round)
        drawLine(color, points[1], points[2], stroke, StrokeCap.Round)
        drawLine(color, points[2], points[3], stroke, StrokeCap.Round)
        drawLine(color, points[2], points[4], stroke, StrokeCap.Round)
        points.forEach { drawCircle(color, unit * .07f, it) }
    }
}
