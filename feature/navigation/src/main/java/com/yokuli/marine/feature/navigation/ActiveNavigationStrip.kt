package com.yokuli.marine.feature.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.LocalMeasurementUnitSystem
import com.yokuli.marine.core.design.MarineDisplayUnits
import com.yokuli.marine.core.design.WpCircleButton
import com.yokuli.marine.core.design.WpLiveField
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.navigation.domain.ActiveNavigationCommand
import com.yokuli.marine.navigation.domain.ActiveNavigationSnapshot
import com.yokuli.marine.navigation.domain.NavigationSessionState
import kotlin.math.roundToInt

/** Compact, truthful projection of a process-owned active navigation session for Chart. */
@Composable
fun ActiveNavigationStrip(
    snapshot: ActiveNavigationSnapshot,
    onCommand: (ActiveNavigationCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = snapshot.session ?: return
    val route = snapshot.route ?: return
    val solution = snapshot.solution
    val colors = LocalWpTheme.current
    val units = LocalMeasurementUnitSystem.current
    val nextOrdinal = (session.activeLegIndex + 2).coerceAtMost(route.points.size)
    val nextName = stringResource(R.string.navigation_route_point, nextOrdinal)
    Column(
        modifier.fillMaxWidth().background(colors.chrome.copy(alpha = .94f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("active-navigation-strip"),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                WpText(stringResource(R.string.nav_next, nextName), 10, color = colors.muted, maxLines = 1)
                WpLiveField(
                    value = solution?.let {
                        stringResource(
                            if (units == com.yokuli.shell.contract.MeasurementUnitSystem.NAUTICAL) R.string.nav_dtw_btw else R.string.nav_dtw_btw_metric,
                            MarineDisplayUnits.distanceFromNauticalMiles(it.distanceToWaypointNauticalMiles, units),
                            it.bearingToWaypointTrueDegrees ?: 0.0,
                        )
                    } ?: stringResource(R.string.nav_waiting_position),
                    structuralKey = Triple(session.routeId, session.activeLegIndex, snapshot.issue),
                    size = 18,
                    modifier = Modifier.testTag("active-navigation-primary"),
                )
            }
            WpText(
                stringResource(
                    when (session.state) {
                        NavigationSessionState.ACTIVE -> R.string.nav_state_active
                        NavigationSessionState.PAUSED -> R.string.nav_state_paused
                        NavigationSessionState.COMPLETE -> R.string.nav_state_complete
                        NavigationSessionState.STOPPED -> error("Stopped sessions are never rendered")
                    },
                ),
                10,
                color = colors.accent,
                modifier = Modifier.testTag("active-navigation-state"),
            )
        }
        solution?.let {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WpText(
                    stringResource(
                        if (units == com.yokuli.shell.contract.MeasurementUnitSystem.NAUTICAL) R.string.nav_xte else R.string.nav_xte_metric,
                        MarineDisplayUnits.distanceFromNauticalMiles(it.crossTrackErrorNauticalMiles, units),
                    ),
                    11,
                    color = colors.muted,
                )
                WpText(
                    it.estimatedTimeToWaypointMillis?.let { eta -> stringResource(R.string.nav_eta, eta / 60_000L) }
                        ?: stringResource(R.string.nav_eta_unavailable),
                    11,
                    color = colors.muted,
                )
                WpText(stringResource(R.string.nav_progress, (it.routeProgress * 100.0).roundToInt()), 11, color = colors.muted)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WpCircleButton("‹", stringResource(R.string.nav_previous), { onCommand(ActiveNavigationCommand.PreviousWaypoint) }, Modifier.testTag("active-navigation-previous"))
            when (session.state) {
                NavigationSessionState.ACTIVE -> WpCircleButton(
                    "Ⅱ", stringResource(R.string.nav_pause), { onCommand(ActiveNavigationCommand.Pause) }, Modifier.testTag("active-navigation-pause"),
                )
                NavigationSessionState.PAUSED -> WpCircleButton(
                    "▶", stringResource(R.string.nav_resume), { onCommand(ActiveNavigationCommand.Resume) }, Modifier.testTag("active-navigation-resume"),
                )
                NavigationSessionState.COMPLETE -> Unit
                NavigationSessionState.STOPPED -> Unit
            }
            WpCircleButton("›", stringResource(R.string.nav_next_waypoint), { onCommand(ActiveNavigationCommand.NextWaypoint) }, Modifier.testTag("active-navigation-next"))
            WpCircleButton("×", stringResource(R.string.nav_stop), { onCommand(ActiveNavigationCommand.Stop) }, Modifier.testTag("active-navigation-stop"))
        }
    }
}
