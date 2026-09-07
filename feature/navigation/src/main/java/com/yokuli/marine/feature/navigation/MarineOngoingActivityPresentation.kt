package com.yokuli.marine.feature.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yokuli.marine.core.design.LocalMeasurementUnitSystem
import com.yokuli.marine.core.design.MarineDisplayUnits
import com.yokuli.marine.navigation.domain.MarineOngoingActivitySnapshot
import com.yokuli.marine.navigation.domain.NavigationSessionState
import com.yokuli.marine.navigation.domain.TrackRecorderStatus
import com.yokuli.shell.contract.MeasurementUnitSystem

data class MarineActivityStatusCopy(
    val stableId: String,
    val compact: String,
    val expanded: String,
    val attention: Boolean = false,
)

/** Pure presentation of the shared process snapshot; it never recalculates navigation facts. */
@Composable
fun marineOngoingActivityStatusCopies(
    snapshot: MarineOngoingActivitySnapshot,
): List<MarineActivityStatusCopy> {
    val units = LocalMeasurementUnitSystem.current
    return buildList {
        snapshot.navigation.session?.let { session ->
            val waypointOrdinal = (session.activeLegIndex + 2).coerceAtMost(
                snapshot.navigation.route?.points?.size ?: Int.MAX_VALUE,
            )
            val solution = snapshot.navigation.solution
            val compact = when (session.state) {
                NavigationSessionState.ACTIVE -> if (solution == null) {
                    stringResource(R.string.shell_navigation_target, waypointOrdinal)
                } else {
                    stringResource(
                        if (units == MeasurementUnitSystem.NAUTICAL) {
                            R.string.shell_navigation_active
                        } else {
                            R.string.shell_navigation_active_metric
                        },
                        waypointOrdinal,
                        MarineDisplayUnits.distanceFromNauticalMiles(
                            solution.distanceToWaypointNauticalMiles,
                            units,
                        ),
                    )
                }
                NavigationSessionState.PAUSED -> stringResource(
                    R.string.shell_navigation_paused,
                    waypointOrdinal,
                )
                NavigationSessionState.COMPLETE -> stringResource(R.string.shell_navigation_complete)
                NavigationSessionState.STOPPED -> return@let
            }
            add(
                MarineActivityStatusCopy(
                    stableId = "navigation",
                    compact = compact,
                    expanded = stringResource(R.string.shell_navigation_expanded, compact),
                    attention = snapshot.navigation.issue != null || snapshot.navigation.historyIssue != null,
                ),
            )
        }

        when (snapshot.trackRecorder.status) {
            TrackRecorderStatus.IDLE -> Unit
            TrackRecorderStatus.RECORDING -> add(
                MarineActivityStatusCopy(
                    stableId = "track",
                    compact = stringResource(R.string.track_recording),
                    expanded = stringResource(R.string.shell_track_recording_expanded),
                ),
            )
            TrackRecorderStatus.PAUSED -> add(
                MarineActivityStatusCopy(
                    stableId = "track",
                    compact = stringResource(R.string.track_paused),
                    expanded = stringResource(R.string.shell_track_paused_expanded),
                    attention = snapshot.trackRecorder.issue != null,
                ),
            )
            TrackRecorderStatus.STOPPED_AWAITING_SAVE -> add(
                MarineActivityStatusCopy(
                    stableId = "track",
                    compact = stringResource(R.string.track_stopped),
                    expanded = stringResource(R.string.shell_track_save_pending_expanded),
                    attention = true,
                ),
            )
        }
    }
}
