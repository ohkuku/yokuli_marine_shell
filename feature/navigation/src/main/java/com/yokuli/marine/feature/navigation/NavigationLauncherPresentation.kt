package com.yokuli.marine.feature.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.yokuli.marine.core.design.LocalMeasurementUnitSystem
import com.yokuli.marine.core.design.MarineDisplayUnits
import com.yokuli.marine.core.design.PresentationCadence
import com.yokuli.marine.core.design.rememberCadencedLiveValue
import com.yokuli.marine.navigation.domain.ActiveNavigationIssue
import com.yokuli.marine.navigation.domain.NavigationRouteMath
import com.yokuli.marine.navigation.domain.NavigationSessionState
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherIconRenderer
import com.yokuli.shell.compose.LauncherSearchResultContribution
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherTileRenderer
import com.yokuli.shell.contract.MarineTileSize
import kotlin.math.min

enum class NavigationTileMode { ACTIVE, RECENT_ROUTE, EMPTY }

data class NavigationTileState(
    val mode: NavigationTileMode,
    val nextWaypointLabel: String? = null,
    val distanceToWaypointNauticalMiles: Double? = null,
    val bearingToWaypointTrueDegrees: Double? = null,
    val crossTrackErrorNauticalMiles: Double? = null,
    val recentRouteName: String? = null,
    val recentRoutePointCount: Int = 0,
    val recentRouteDistanceNauticalMiles: Double? = null,
    val routeCount: Int = 0,
    val waypointCount: Int = 0,
    val issue: ActiveNavigationIssue? = null,
    val notice: NavigationNotice? = null,
) {
    val critical: Boolean get() = issue != null || notice in setOf(
        NavigationNotice.STORAGE_FAILED,
        NavigationNotice.ACTIVE_ROUTE_LOCKED,
        NavigationNotice.NAVIGATION_REJECTED,
    )
}

object NavigationLauncherProjector {
    fun project(state: NavigationUiState): NavigationTileState {
        val active = state.active
        if (active.sessionState in setOf(NavigationSessionState.ACTIVE, NavigationSessionState.PAUSED)) {
            val nextIndex = (active.session?.activeLegIndex ?: -1) + 1
            val next = active.route?.points?.getOrNull(nextIndex)
            val nextLabel = next?.sourceWaypoint?.waypointId
                ?.let { id -> state.library.waypoints.firstOrNull { it.id == id }?.name }
                ?: next?.let { "${nextIndex + 1}" }
            return NavigationTileState(
                mode = NavigationTileMode.ACTIVE,
                nextWaypointLabel = nextLabel,
                distanceToWaypointNauticalMiles = active.solution?.distanceToWaypointNauticalMiles,
                bearingToWaypointTrueDegrees = active.solution?.bearingToWaypointTrueDegrees,
                crossTrackErrorNauticalMiles = active.solution?.crossTrackErrorNauticalMiles,
                routeCount = state.library.routePlans.size,
                waypointCount = state.library.waypoints.size,
                issue = active.issue,
                notice = state.notice,
            )
        }
        val recent = state.library.routePlans.lastOrNull()
        val summary = recent?.let(NavigationRouteMath::summarize)
        return NavigationTileState(
            mode = if (recent == null) NavigationTileMode.EMPTY else NavigationTileMode.RECENT_ROUTE,
            recentRouteName = recent?.name,
            recentRoutePointCount = recent?.points?.size ?: 0,
            recentRouteDistanceNauticalMiles = summary?.distanceNauticalMiles,
            routeCount = state.library.routePlans.size,
            waypointCount = state.library.waypoints.size,
            issue = active.issue,
            notice = state.notice,
        )
    }
}

@Composable
fun navigationLauncherVisualContribution(state: NavigationUiState): LauncherEntryVisualContribution {
    val title = stringResource(R.string.navigation_title)
    val incoming = NavigationLauncherProjector.project(state)
    val staticCopy = navigationTileCopy(incoming)
    return LauncherEntryVisualContribution(
        entryId = NavigationShellContribution.EntryId,
        title = title,
        chineseIndex = 'D',
        headline = staticCopy.headline,
        detail = staticCopy.detail,
        icon = LauncherIconRenderer { tint, modifier -> NavigationIcon(tint, modifier) },
        tileRenderers = mapOf(
            MarineTileSize.ICON_1X1 to LauncherTileRenderer { NavigationSmallTile(it, incoming) },
            MarineTileSize.STANDARD_2X2 to LauncherTileRenderer { context ->
                NavigationMediumTile(context, title, rememberNavigationTile(incoming, context.liveContentEnabled))
            },
            MarineTileSize.WIDE_4X2 to LauncherTileRenderer { context ->
                NavigationWideTile(context, title, rememberNavigationTile(incoming, context.liveContentEnabled))
            },
        ),
    )
}

private data class NavigationTileCopy(val headline: String, val detail: String, val secondary: String? = null)

@Composable
private fun navigationTileCopy(state: NavigationTileState): NavigationTileCopy {
    val units = LocalMeasurementUnitSystem.current
    return when (state.mode) {
        NavigationTileMode.ACTIVE -> {
            val headline = state.nextWaypointLabel?.let { stringResource(R.string.navigation_tile_active, it) }
                ?: stringResource(R.string.navigation_tile_active_unknown)
            val detail = if (state.distanceToWaypointNauticalMiles != null && state.bearingToWaypointTrueDegrees != null) {
                stringResource(
                    if (units == com.yokuli.shell.contract.MeasurementUnitSystem.NAUTICAL) R.string.navigation_tile_dtw_btw
                    else R.string.navigation_tile_dtw_btw_metric,
                    MarineDisplayUnits.distanceFromNauticalMiles(state.distanceToWaypointNauticalMiles, units),
                    state.bearingToWaypointTrueDegrees,
                )
            } else stringResource(R.string.nav_waiting_position)
            val xte = state.crossTrackErrorNauticalMiles?.let {
                stringResource(
                    if (units == com.yokuli.shell.contract.MeasurementUnitSystem.NAUTICAL) R.string.navigation_tile_xte
                    else R.string.navigation_tile_xte_metric,
                    MarineDisplayUnits.distanceFromNauticalMiles(kotlin.math.abs(it), units),
                )
            }
            NavigationTileCopy(headline, detail, xte)
        }
        NavigationTileMode.RECENT_ROUTE -> NavigationTileCopy(
            stringResource(R.string.navigation_tile_recent, state.recentRouteName.orEmpty()),
            stringResource(
                if (units == com.yokuli.shell.contract.MeasurementUnitSystem.NAUTICAL) R.string.navigation_route_summary
                else R.string.navigation_route_summary_metric,
                state.recentRoutePointCount,
                MarineDisplayUnits.distanceFromNauticalMiles(state.recentRouteDistanceNauticalMiles ?: 0.0, units),
            ),
        )
        NavigationTileMode.EMPTY -> NavigationTileCopy(
            stringResource(R.string.navigation_tile_idle, state.routeCount),
            stringResource(R.string.navigation_library_summary, state.waypointCount, state.routeCount),
        )
    }
}

@Composable
private fun rememberNavigationTile(incoming: NavigationTileState, live: Boolean): NavigationTileState {
    val cadenced = rememberCadencedLiveValue(
        incoming,
        structuralKey = listOf(
            incoming.mode,
            incoming.nextWaypointLabel,
            incoming.recentRouteName,
            incoming.recentRoutePointCount,
            incoming.issue,
            incoming.notice,
        ),
        cadence = PresentationCadence.StartTile,
    )
    val slot = remember { NavigationTileDisplaySlot(incoming) }
    return slot.resolve(cadenced, live)
}

class NavigationTileDisplaySlot(initial: NavigationTileState) {
    var shown: NavigationTileState = initial
        private set

    fun resolve(incoming: NavigationTileState, liveContentEnabled: Boolean): NavigationTileState {
        if (liveContentEnabled || incoming.critical || shown.critical != incoming.critical) shown = incoming
        return shown
    }
}

fun navigationSearchContributions(state: NavigationUiState, query: String): List<LauncherSearchResultContribution> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return emptyList()
    return buildList {
        state.library.routePlans.asSequence().filter { it.name.lowercase().contains(normalized) }.take(16).forEach { route ->
            add(LauncherSearchResultContribution("navigation-route:${route.id}", route.name, "${route.points.size}", NavigationDestinations.route(route.id)))
        }
        state.library.waypoints.asSequence().filter { it.name.lowercase().contains(normalized) }.take(16 - size).forEach { waypoint ->
            add(LauncherSearchResultContribution("navigation-waypoint:${waypoint.id}", waypoint.name, "", NavigationDestinations.Waypoints))
        }
    }
}

@Composable
private fun NavigationSmallTile(context: LauncherTileRenderContext, state: NavigationTileState) {
    Box(context.modifier.fillMaxSize().testTag("navigation-tile-small"), contentAlignment = Alignment.Center) {
        NavigationIcon(context.contentColor, Modifier.size(44.dp))
        if (state.critical) WpText(
            "!", 16, color = context.contentColor, weight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).testTag("navigation-tile-alert"),
        )
    }
}

@Composable
private fun NavigationMediumTile(context: LauncherTileRenderContext, title: String, state: NavigationTileState) {
    val copy = navigationTileCopy(state)
    Box(context.modifier.fillMaxSize().testTag("navigation-tile-medium")) {
        NavigationIcon(context.contentColor, Modifier.size(36.dp).align(Alignment.TopStart))
        Column(Modifier.align(Alignment.CenterStart)) {
            WpText(copy.headline, 20, color = context.contentColor, weight = FontWeight.Light, maxLines = 2)
            WpText(copy.detail, 10, color = context.contentColor.copy(alpha = .84f), maxLines = 1)
            copy.secondary?.let { WpText(it, 10, color = context.contentColor.copy(alpha = .84f), maxLines = 1) }
        }
        WpText(title, 12, color = context.contentColor, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun NavigationWideTile(context: LauncherTileRenderContext, title: String, state: NavigationTileState) {
    val copy = navigationTileCopy(state)
    Row(context.modifier.fillMaxSize().testTag("navigation-tile-wide")) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            NavigationIcon(context.contentColor, Modifier.size(38.dp))
            WpText(title, 12, color = context.contentColor, modifier = Modifier.weight(1f).padding(top = 8.dp))
        }
        Column(Modifier.weight(1.4f).padding(start = 10.dp, top = 8.dp)) {
            WpText(copy.headline, 22, color = context.contentColor, weight = FontWeight.Light, maxLines = 2)
            WpText(copy.detail, 12, color = context.contentColor.copy(alpha = .84f), maxLines = 1)
            copy.secondary?.let { WpText(it, 11, color = context.contentColor.copy(alpha = .84f), maxLines = 1) }
        }
    }
}

@Composable
private fun NavigationIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color, unit * .4f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(unit * .06f))
        drawLine(color, Offset(center.x, unit * .10f), Offset(center.x - unit * .11f, center.y + unit * .08f), unit * .07f, StrokeCap.Round)
        drawLine(color, Offset(center.x - unit * .11f, center.y + unit * .08f), Offset(center.x, center.y), unit * .07f, StrokeCap.Round)
        drawCircle(color, unit * .055f, center)
    }
}
