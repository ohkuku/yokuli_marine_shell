package com.yokuli.marine.feature.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalMeasurementUnitSystem
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.MarineDisplayUnits
import com.yokuli.marine.core.design.WpCircleButton
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
    var now by remember(session?.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session?.id, session?.status) {
        while (session?.status == TrackRecorderStatus.RECORDING) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    if (session == null) {
        Row(
            modifier.fillMaxWidth().background(colors.chrome.copy(alpha = .90f))
                .padding(horizontal = 12.dp, vertical = 5.dp).testTag("track-recorder-idle"),
            horizontalArrangement = Arrangement.End,
        ) {
            WpCircleButton(
                "●",
                stringResource(R.string.track_start),
                { onCommand(TrackRecorderCommand.Start) },
                Modifier.testTag("track-recorder-start"),
            )
        }
        return
    }
    val duration = session.accumulatedDurationMillis + if (session.status == TrackRecorderStatus.RECORDING) {
        (now - requireNotNull(session.activeSinceEpochMillis)).coerceAtLeast(0L)
    } else 0L
    Column(
        modifier.fillMaxWidth().background(colors.chrome.copy(alpha = .95f))
            .padding(horizontal = 12.dp, vertical = 7.dp).testTag("track-recorder-strip"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WpText(statusLabel(snapshot.status), 10, color = colors.accent, modifier = Modifier.weight(1f))
            WpLiveField(
                value = stringResource(
                    if (units == com.yokuli.shell.contract.MeasurementUnitSystem.NAUTICAL) {
                        R.string.track_summary
                    } else {
                        R.string.track_summary_metric
                    },
                    formatDuration(duration),
                    MarineDisplayUnits.distanceFromNauticalMiles(snapshot.distanceNauticalMiles, units),
                    snapshot.pointCount,
                ),
                structuralKey = Triple(snapshot.status, snapshot.pointCount, snapshot.issue),
                size = 12,
            )
        }
        snapshot.issue?.let { WpText(stringResource(it.label()), 10, color = colors.muted) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            when (snapshot.status) {
                TrackRecorderStatus.IDLE -> Unit
                TrackRecorderStatus.RECORDING -> {
                    WpCircleButton("Ⅱ", stringResource(R.string.track_pause), { onCommand(TrackRecorderCommand.Pause) })
                    WpCircleButton("■", stringResource(R.string.track_stop), { onCommand(TrackRecorderCommand.Stop) })
                }
                TrackRecorderStatus.PAUSED -> {
                    WpCircleButton("▶", stringResource(R.string.track_resume), { onCommand(TrackRecorderCommand.Resume) })
                    WpCircleButton("■", stringResource(R.string.track_stop), { onCommand(TrackRecorderCommand.Stop) })
                }
                TrackRecorderStatus.STOPPED_AWAITING_SAVE -> {
                    WpCircleButton("×", stringResource(R.string.track_discard), { onCommand(TrackRecorderCommand.Discard) })
                    WpCircleButton("✓", stringResource(R.string.track_save), { onCommand(TrackRecorderCommand.Save()) }, selected = true)
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
