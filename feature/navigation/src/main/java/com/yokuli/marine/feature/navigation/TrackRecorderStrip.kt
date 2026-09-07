package com.yokuli.marine.feature.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalMeasurementUnitSystem
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.MarineDisplayUnits
import com.yokuli.marine.core.design.WpLiveField
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.navigation.domain.TrackRecorderCommand
import com.yokuli.marine.navigation.domain.TrackRecorderIssue
import com.yokuli.marine.navigation.domain.TrackRecorderSnapshot
import com.yokuli.marine.navigation.domain.TrackRecorderStatus
import kotlinx.coroutines.delay

/** One projection of the process-owned recorder, reusable by Chart and Navigation. */
@Composable
fun TrackRecorderStrip(
    snapshot: TrackRecorderSnapshot,
    onCommand: (TrackRecorderCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = snapshot.session
    val colors = LocalWpTheme.current
    val units = LocalMeasurementUnitSystem.current
    var expanded by remember { mutableStateOf(false) }
    var now by remember(session?.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session?.id, session?.status) {
        while (session?.status == TrackRecorderStatus.RECORDING) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val duration = session?.let { current ->
        current.accumulatedDurationMillis + if (current.status == TrackRecorderStatus.RECORDING) {
            (now - requireNotNull(current.activeSinceEpochMillis)).coerceAtLeast(0L)
        } else 0L
    } ?: 0L
    Column(
        modifier.widthIn(max = 300.dp).background(colors.chrome.copy(alpha = .94f), RoundedCornerShape(28.dp))
            .border(1.dp, colors.foreground.copy(alpha = .22f), RoundedCornerShape(28.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp).testTag("track-recorder-strip"),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (session != null) {
                Canvas(Modifier.size(10.dp)) {
                    drawCircle(
                        if (snapshot.status == TrackRecorderStatus.RECORDING) colors.accent else colors.muted,
                    )
                }
                WpLiveField(
                    value = if (expanded) {
                        stringResource(
                            if (units == com.yokuli.shell.contract.MeasurementUnitSystem.NAUTICAL) {
                                R.string.track_summary
                            } else {
                                R.string.track_summary_metric
                            },
                            formatDuration(duration),
                            MarineDisplayUnits.distanceFromNauticalMiles(snapshot.distanceNauticalMiles, units),
                            snapshot.pointCount,
                        )
                    } else {
                        formatDuration(duration)
                    },
                    structuralKey = Triple(snapshot.status, snapshot.pointCount, expanded),
                    size = if (expanded) 11 else 12,
                )
            } else if (expanded) {
                WpText(statusLabel(snapshot.status), 10, color = colors.foreground)
            }
            if (expanded) when (snapshot.status) {
                TrackRecorderStatus.IDLE -> TrackTransportButton(
                    TrackControlGlyph.RECORD,
                    stringResource(R.string.track_start),
                    { onCommand(TrackRecorderCommand.Start) },
                    Modifier.testTag("track-recorder-start"),
                    selected = true,
                )
                TrackRecorderStatus.RECORDING -> {
                    TrackTransportButton(
                        TrackControlGlyph.PAUSE,
                        stringResource(R.string.track_pause),
                        { onCommand(TrackRecorderCommand.Pause) },
                    )
                    TrackTransportButton(
                        TrackControlGlyph.STOP,
                        stringResource(R.string.track_stop),
                        { onCommand(TrackRecorderCommand.Stop) },
                    )
                }
                TrackRecorderStatus.PAUSED -> {
                    TrackTransportButton(
                        TrackControlGlyph.PLAY,
                        stringResource(R.string.track_resume),
                        { onCommand(TrackRecorderCommand.Resume) },
                    )
                    TrackTransportButton(
                        TrackControlGlyph.STOP,
                        stringResource(R.string.track_stop),
                        { onCommand(TrackRecorderCommand.Stop) },
                    )
                }
                TrackRecorderStatus.STOPPED_AWAITING_SAVE -> {
                    TrackTransportButton(
                        TrackControlGlyph.DISCARD,
                        stringResource(R.string.track_discard),
                        { onCommand(TrackRecorderCommand.Discard) },
                    )
                    TrackTransportButton(
                        TrackControlGlyph.SAVE,
                        stringResource(R.string.track_save),
                        { onCommand(TrackRecorderCommand.Save()) },
                        selected = true,
                    )
                }
            }
            TrackTransportButton(
                TrackControlGlyph.TRACK,
                statusLabel(snapshot.status),
                { expanded = !expanded },
                Modifier.testTag("track-recorder-toggle"),
                selected = expanded,
            )
        }
        if (expanded) snapshot.issue?.let { WpText(stringResource(it.label()), 9, color = colors.muted) }
    }
}

private enum class TrackControlGlyph { TRACK, RECORD, PAUSE, PLAY, STOP, DISCARD, SAVE }

/** Transport glyphs are drawn geometrically so play/pause/stop stay optically centered. */
@Composable
private fun TrackTransportButton(
    glyph: TrackControlGlyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val colors = LocalWpTheme.current
    Box(
        modifier.size(44.dp).semantics {
            contentDescription = description
            role = Role.Button
        }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier.size(34.dp).background(
                if (selected) colors.accent else colors.background.copy(alpha = .65f),
                CircleShape,
            ).border(1.5.dp, colors.foreground, CircleShape),
        ) {
            val ink = if (selected) colors.onAccent else colors.foreground
            val c = center
            when (glyph) {
                TrackControlGlyph.RECORD -> drawCircle(ink, radius = size.minDimension * .18f)
                TrackControlGlyph.PAUSE -> {
                    val width = size.minDimension * .11f
                    val halfHeight = size.minDimension * .19f
                    drawRect(ink, Offset(c.x - width * 1.7f, c.y - halfHeight), androidx.compose.ui.geometry.Size(width, halfHeight * 2f))
                    drawRect(ink, Offset(c.x + width * .7f, c.y - halfHeight), androidx.compose.ui.geometry.Size(width, halfHeight * 2f))
                }
                TrackControlGlyph.PLAY -> drawPath(
                    Path().apply {
                        moveTo(c.x - size.width * .11f, c.y - size.height * .18f)
                        lineTo(c.x + size.width * .18f, c.y)
                        lineTo(c.x - size.width * .11f, c.y + size.height * .18f)
                        close()
                    },
                    ink,
                )
                TrackControlGlyph.STOP -> drawRect(
                    ink,
                    Offset(c.x - size.width * .14f, c.y - size.height * .14f),
                    androidx.compose.ui.geometry.Size(size.width * .28f, size.height * .28f),
                )
                TrackControlGlyph.DISCARD -> {
                    val d = size.width * .14f
                    drawLine(ink, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), 2.dp.toPx())
                    drawLine(ink, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), 2.dp.toPx())
                }
                TrackControlGlyph.SAVE -> {
                    val path = Path().apply {
                        moveTo(c.x - size.width * .17f, c.y)
                        lineTo(c.x - size.width * .04f, c.y + size.height * .13f)
                        lineTo(c.x + size.width * .2f, c.y - size.height * .16f)
                    }
                    drawPath(path, ink, style = Stroke(2.dp.toPx()))
                }
                TrackControlGlyph.TRACK -> {
                    val path = Path().apply {
                        moveTo(size.width * .22f, size.height * .66f)
                        cubicTo(size.width * .34f, size.height * .25f, size.width * .55f, size.height * .76f, size.width * .76f, size.height * .34f)
                    }
                    drawPath(path, ink, style = Stroke(2.dp.toPx()))
                    drawCircle(ink, size.width * .055f, Offset(size.width * .22f, size.height * .66f))
                    drawCircle(ink, size.width * .055f, Offset(size.width * .76f, size.height * .34f))
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: TrackRecorderStatus): String = stringResource(
    when (status) {
        TrackRecorderStatus.IDLE -> R.string.track_idle
        TrackRecorderStatus.RECORDING -> R.string.track_recording
        TrackRecorderStatus.PAUSED -> R.string.track_paused
        TrackRecorderStatus.STOPPED_AWAITING_SAVE -> R.string.track_stopped
    },
)

private fun TrackRecorderIssue.label(): Int = when (this) {
    TrackRecorderIssue.NOT_RECORDING -> R.string.track_issue_not_recording
    TrackRecorderIssue.ALREADY_RUNNING -> R.string.track_issue_already_running
    TrackRecorderIssue.EMPTY_TRACK -> R.string.track_issue_empty
    TrackRecorderIssue.CAPACITY_REACHED -> R.string.track_issue_capacity
    TrackRecorderIssue.PERSISTENCE_FAILED -> R.string.track_issue_persistence
    TrackRecorderIssue.ARCHIVE_FAILED -> R.string.track_issue_archive
}

private fun formatDuration(durationMillis: Long): String {
    val seconds = durationMillis / 1_000L
    return "%02d:%02d:%02d".format(seconds / 3_600L, seconds / 60L % 60L, seconds % 60L)
}
