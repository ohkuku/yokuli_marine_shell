package com.yokuli.marine.feature.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
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
import com.yokuli.marine.navigation.domain.NavigationSessionState
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherIconRenderer
import com.yokuli.shell.compose.LauncherSearchResultContribution
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherTileRenderer
import com.yokuli.shell.contract.MarineTileSize
import kotlin.math.min

@Composable
fun navigationLauncherVisualContribution(state: NavigationUiState): LauncherEntryVisualContribution {
    val title = stringResource(R.string.navigation_title)
    val active = state.active
    val nextIndex = (active.session?.activeLegIndex ?: -1) + 1
    val next = active.route?.points?.getOrNull(nextIndex)
    val nextLabel = next?.sourceWaypoint?.waypointId
        ?.let { id -> state.library.waypoints.firstOrNull { it.id == id }?.name }
        ?: next?.let { stringResource(R.string.navigation_route_point, nextIndex + 1) }
    val headline = if (active.sessionState == NavigationSessionState.ACTIVE && nextLabel != null) {
        stringResource(R.string.navigation_tile_active, nextLabel)
    } else stringResource(R.string.navigation_tile_idle, state.library.routePlans.size)
    val detail = active.solution?.let { stringResource(R.string.navigation_tile_distance, it.distanceToWaypointNauticalMiles) }
        ?: stringResource(R.string.navigation_library_summary, state.library.waypoints.size, state.library.routePlans.size)
    return LauncherEntryVisualContribution(
        entryId = NavigationShellContribution.EntryId,
        title = title,
        chineseIndex = 'N',
        headline = headline,
        detail = detail,
        icon = LauncherIconRenderer { tint, modifier -> NavigationIcon(tint, modifier) },
        tileRenderers = mapOf(
            MarineTileSize.ICON_1X1 to LauncherTileRenderer { NavigationSmallTile(it) },
            MarineTileSize.STANDARD_2X2 to LauncherTileRenderer { NavigationMediumTile(it, title, headline, detail) },
            MarineTileSize.WIDE_4X2 to LauncherTileRenderer { NavigationWideTile(it, title, headline, detail) },
        ),
    )
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
private fun NavigationSmallTile(context: LauncherTileRenderContext) {
    Box(context.modifier.fillMaxSize().testTag("navigation-tile-small"), contentAlignment = Alignment.Center) {
        NavigationIcon(context.contentColor, Modifier.size(44.dp))
    }
}

@Composable
private fun NavigationMediumTile(context: LauncherTileRenderContext, title: String, headline: String, detail: String) {
    Box(context.modifier.fillMaxSize().testTag("navigation-tile-medium")) {
        NavigationIcon(context.contentColor, Modifier.size(36.dp).align(Alignment.TopStart))
        Column(Modifier.align(Alignment.CenterStart)) {
            WpText(headline, 20, color = context.contentColor, weight = FontWeight.Light)
            WpText(detail, 10, color = context.contentColor.copy(alpha = .84f))
        }
        WpText(title, 12, color = context.contentColor, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun NavigationWideTile(context: LauncherTileRenderContext, title: String, headline: String, detail: String) {
    Row(context.modifier.fillMaxSize().testTag("navigation-tile-wide")) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            NavigationIcon(context.contentColor, Modifier.size(38.dp))
            WpText(title, 12, color = context.contentColor, modifier = Modifier.weight(1f).padding(top = 8.dp))
        }
        Column(Modifier.weight(1.4f).padding(start = 10.dp, top = 8.dp)) {
            WpText(headline, 22, color = context.contentColor, weight = FontWeight.Light)
            WpText(detail, 12, color = context.contentColor.copy(alpha = .84f))
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
