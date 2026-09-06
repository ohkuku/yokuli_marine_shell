package com.yokuli.marine.feature.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.PresentationCadence
import com.yokuli.marine.core.design.rememberCadencedLiveValue
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapTileSnapshot
import com.yokuli.marine.map.domain.PositionAvailability
import com.yokuli.marine.navigation.domain.ActiveNavigationSnapshot
import com.yokuli.marine.navigation.domain.NavigationSessionState
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherIconRenderer
import com.yokuli.shell.compose.LauncherSearchResultContribution
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherTileRenderer
import com.yokuli.shell.contract.MarineTileSize
import java.util.Locale
import android.graphics.BitmapFactory
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

/** App-owned Map presentation. Shell supplies the accent surface and edit chrome only. */
@Composable
fun chartLauncherVisualContribution(
    mapState: MapState,
    offlineCoverageState: OfflineCoverageUiState = OfflineCoverageUiState.Idle,
    activeNavigation: ActiveNavigationSnapshot = ActiveNavigationSnapshot.EMPTY,
    mapTileSnapshot: MapTileSnapshot? = null,
    tileMode: ChartTileMode = ChartTileMode.AUTO,
): LauncherEntryVisualContribution {
    val title = stringResource(R.string.app_chart)
    val snapshot = ChartLauncherProjection.project(mapState, offlineCoverageState)
    val copy = chartLauncherCopy(snapshot)
    return LauncherEntryVisualContribution(
        entryId = ChartDestinations.EntryId,
        title = title,
        chineseIndex = 'H',
        headline = copy.subject,
        detail = copy.status,
        icon = LauncherIconRenderer { tint, modifier -> ChartLauncherIcon(tint, modifier) },
        tileRenderers = mapOf(
            MarineTileSize.ICON_1X1 to LauncherTileRenderer { context ->
                val shown = rememberVisibleChartSnapshot(snapshot, context.liveContentEnabled)
                ChartSmallTile(context, shown)
            },
            MarineTileSize.STANDARD_2X2 to LauncherTileRenderer { context ->
                val shown = rememberVisibleChartSnapshot(snapshot, context.liveContentEnabled)
                ChartStandardTile(context, title, shown, mapState, activeNavigation, mapTileSnapshot, tileMode)
            },
            MarineTileSize.WIDE_4X2 to LauncherTileRenderer { context ->
                val shown = rememberVisibleChartSnapshot(snapshot, context.liveContentEnabled)
                ChartWideTile(context, title, shown, mapState, activeNavigation, mapTileSnapshot, tileMode)
            },
        ),
    )
}

@Composable
fun chartLauncherSearchContributions(mapState: MapState, query: String): List<LauncherSearchResultContribution> {
    val placeDetail = stringResource(R.string.map_launcher_search_place)
    val routeDetail = stringResource(R.string.map_launcher_search_route)
    return ChartSearchProjection.search(mapState, query).map { item ->
        LauncherSearchResultContribution(
            stableId = "chart-${item.kind.name.lowercase(Locale.ROOT)}-${item.token.value.removePrefix("chart.")}",
            title = item.title,
            detail = if (item.kind == ChartSearchKind.PLACE) placeDetail else routeDetail,
            launchToken = item.token,
        )
    }
}

private data class ChartLauncherCopy(val subject: String, val status: String, val previewLabel: String)

@Composable
private fun chartLauncherCopy(snapshot: ChartLauncherSnapshot): ChartLauncherCopy {
    val fallbackSubject = when (snapshot.priority) {
        ChartLauncherPriority.EDITING_DRAFT -> stringResource(R.string.map_launcher_unnamed_draft)
        ChartLauncherPriority.LAST_VIEW -> snapshot.camera?.let { camera ->
            stringResource(
                R.string.map_launcher_last_coordinates,
                abs(camera.center.latitude),
                if (camera.center.latitude < 0) "S" else "N",
                abs(camera.center.longitude),
                if (camera.center.longitude < 0) "W" else "E",
            )
        } ?: stringResource(R.string.app_chart)
        else -> stringResource(R.string.app_chart)
    }
    val status = stringResource(
        when (snapshot.status) {
            ChartLauncherStatus.WRITE_FAILED -> R.string.map_launcher_write_failed
            ChartLauncherStatus.SAVING -> R.string.map_launcher_saving
            ChartLauncherStatus.EDITING_DRAFT -> R.string.map_launcher_editing_draft
            ChartLauncherStatus.PLAN_SELECTED -> R.string.map_launcher_plan_selected
            ChartLauncherStatus.COVERAGE_CHECKING -> R.string.map_launcher_coverage_checking
            ChartLauncherStatus.COVERAGE_STALE -> R.string.map_launcher_coverage_stale
            ChartLauncherStatus.COVERAGE_UNAVAILABLE -> R.string.map_launcher_coverage_unavailable
            ChartLauncherStatus.COVERAGE_TOO_LARGE -> R.string.map_launcher_coverage_too_large
            ChartLauncherStatus.TILES_AVAILABLE_CONTENT_UNVERIFIED -> R.string.map_launcher_tiles_available_unverified
            ChartLauncherStatus.TILES_AVAILABLE_CONTENT_OBSERVED -> R.string.map_launcher_tiles_available_observed
            ChartLauncherStatus.TILES_MISSING -> R.string.map_launcher_tiles_missing
            ChartLauncherStatus.TILES_UNKNOWN -> R.string.map_launcher_tiles_unknown
            ChartLauncherStatus.LOCAL_CHART_SELECTED -> R.string.map_launcher_local_chart
            ChartLauncherStatus.LOCAL_CHART_CHECKING -> R.string.map_launcher_local_chart_checking
            ChartLauncherStatus.LOCAL_CHART_MISSING -> R.string.map_launcher_local_chart_missing
            ChartLauncherStatus.LOCAL_CHART_DEGRADED -> R.string.map_launcher_local_chart_degraded
            ChartLauncherStatus.RENDERER_ERROR -> R.string.map_launcher_renderer_error
            ChartLauncherStatus.NO_LOCAL_CHART -> R.string.map_launcher_no_local_chart
            ChartLauncherStatus.READY_TO_BROWSE -> R.string.map_launcher_browse
        },
    )
    val previewLabel = stringResource(
        if (snapshot.routePreview?.label == ChartPreviewLabel.DRAFT) R.string.map_launcher_preview_draft
        else R.string.map_launcher_preview_plan,
    )
    return ChartLauncherCopy(snapshot.subjectName ?: fallbackSubject, status, previewLabel)
}

@Composable
private fun rememberVisibleChartSnapshot(snapshot: ChartLauncherSnapshot, liveContentEnabled: Boolean): ChartLauncherSnapshot {
    val slot = remember { ChartLauncherDisplaySlot(snapshot) }
    return slot.resolve(snapshot, liveContentEnabled)
}

@Composable
private fun ChartSmallTile(context: LauncherTileRenderContext, snapshot: ChartLauncherSnapshot) {
    Box(context.modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ChartLauncherIcon(context.contentColor, Modifier.size(42.dp))
        val badge = when (snapshot.status) {
            ChartLauncherStatus.WRITE_FAILED -> "!"
            ChartLauncherStatus.SAVING -> "…"
            else -> null
        }
        badge?.let {
            Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).background(context.contentColor, CircleShape)
                    .testTag("chart-tile-badge"),
                contentAlignment = Alignment.Center,
            ) {
                WpText(
                    it,
                    13,
                    color = if (context.contentColor.luminance() > .5f) Color.Black else Color.White,
                    weight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ChartStandardTile(
    context: LauncherTileRenderContext,
    title: String,
    snapshot: ChartLauncherSnapshot,
    mapState: MapState,
    navigation: ActiveNavigationSnapshot,
    mapTileSnapshot: MapTileSnapshot?,
    mode: ChartTileMode,
) {
    val live = rememberChartTileFacts(snapshot, mapState, navigation, mapTileSnapshot, mode)
    val frame = rememberChartTileFrame(live.frames, context.liveContentEnabled)
    val copy = chartTileFrameCopy(frame, live)
    Column(context.modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChartLauncherIcon(context.contentColor, Modifier.size(28.dp))
            WpText(title, 12, color = context.contentColor, maxLines = 1, modifier = Modifier.padding(start = 7.dp))
        }
        WpText(copy.subject, 20, color = context.contentColor, weight = FontWeight.Light, maxLines = 2)
        WpText(copy.status, 11, color = context.contentColor.copy(alpha = .84f), maxLines = 2)
    }
}

@Composable
private fun ChartWideTile(
    context: LauncherTileRenderContext,
    title: String,
    snapshot: ChartLauncherSnapshot,
    mapState: MapState,
    navigation: ActiveNavigationSnapshot,
    mapTileSnapshot: MapTileSnapshot?,
    mode: ChartTileMode,
) {
    val live = rememberChartTileFacts(snapshot, mapState, navigation, mapTileSnapshot, mode)
    val frame = rememberChartTileFrame(live.frames, context.liveContentEnabled)
    val copy = chartTileFrameCopy(frame, live)
    Row(context.modifier.fillMaxSize()) {
        Box(Modifier.fillMaxHeight().width(116.dp), contentAlignment = Alignment.Center) {
            if (frame == ChartTileFrameKind.MAP && live.mapSnapshot != null) {
                val bitmap = remember(live.mapSnapshot.request.id) {
                    live.mapSnapshot.encodedBytes().let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(1.dp)).testTag("chart-tile-map-snapshot"),
                    )
                } else ChartLauncherIcon(context.contentColor, Modifier.size(54.dp))
            } else {
                ChartLauncherIcon(context.contentColor, Modifier.size(54.dp))
            }
        }
        Column(
            Modifier.fillMaxHeight().weight(1f).padding(start = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            WpText(title, 12, color = context.contentColor, maxLines = 1)
            WpText(copy.subject, 23, color = context.contentColor, weight = FontWeight.Light, maxLines = 2)
            WpText(copy.status, 12, color = context.contentColor.copy(alpha = .84f), maxLines = 2)
        }
    }
}

private data class ChartTileFacts(
    val snapshot: ChartLauncherSnapshot,
    val mapState: MapState,
    val navigation: ActiveNavigationSnapshot,
    val mapSnapshot: MapTileSnapshot?,
    val frames: List<ChartTileFrameKind>,
)

@Composable
private fun rememberChartTileFacts(
    snapshot: ChartLauncherSnapshot,
    mapState: MapState,
    navigation: ActiveNavigationSnapshot,
    mapSnapshot: MapTileSnapshot?,
    mode: ChartTileMode,
): ChartTileFacts {
    val active = navigation.sessionState in setOf(
        NavigationSessionState.ACTIVE,
        NavigationSessionState.PAUSED,
    )
    val positionFresh = mapState.position.availability == PositionAvailability.FRESH &&
        mapState.position.observation != null
    val frames = ChartTileFramePolicy.frames(mode, active, mapSnapshot != null, positionFresh)
    val incoming = ChartTileFacts(snapshot, mapState, navigation, mapSnapshot, frames)
    return rememberCadencedLiveValue(
        incoming,
        structuralKey = listOf(
            mode,
            frames,
            snapshot.critical,
            navigation.session?.routeId,
            navigation.sessionState,
            navigation.issue,
            mapState.position.availability,
        ),
        cadence = PresentationCadence.StartTile,
    )
}

@Composable
private fun rememberChartTileFrame(frames: List<ChartTileFrameKind>, live: Boolean): ChartTileFrameKind {
    var index by remember(frames) { mutableIntStateOf(0) }
    LaunchedEffect(frames, live) {
        if (!live || frames.size < 2) return@LaunchedEffect
        while (true) {
            delay(CHART_TILE_ROTATION_MILLIS)
            index = (index + 1) % frames.size
        }
    }
    return frames[index.coerceIn(frames.indices)]
}

@Composable
private fun chartTileFrameCopy(frame: ChartTileFrameKind, facts: ChartTileFacts): ChartLauncherCopy {
    // A structural failure is never hidden by a user's preferred live-tile frame.
    if (facts.snapshot.critical) return chartLauncherCopy(facts.snapshot)
    return when (frame) {
    ChartTileFrameKind.MAP -> ChartLauncherCopy(
        stringResource(R.string.map_tile_snapshot),
        stringResource(R.string.map_tile_last_view),
        "",
    )
    ChartTileFrameKind.NAVIGATION -> {
        val session = facts.navigation.session
        val solution = facts.navigation.solution
        ChartLauncherCopy(
            stringResource(R.string.map_tile_navigation_next, ((session?.activeLegIndex ?: 0) + 2).coerceAtMost(facts.navigation.route?.points?.size ?: 1)),
            solution?.let {
                stringResource(R.string.map_tile_navigation_solution, it.distanceToWaypointNauticalMiles, it.bearingToWaypointTrueDegrees ?: 0.0, it.crossTrackErrorNauticalMiles)
            } ?: stringResource(R.string.map_tile_navigation_waiting),
            "",
        )
    }
    ChartTileFrameKind.POSITION -> {
        val point = facts.mapState.position.observation?.point
        val course = facts.mapState.position.courseSpeed
        ChartLauncherCopy(
            point?.let { stringResource(R.string.map_tile_position, it.latitude, it.longitude) }
                ?: stringResource(R.string.app_chart),
            course?.let { stringResource(R.string.map_tile_course_speed, it.courseOverGroundTrueDegrees ?: 0.0, it.speedOverGroundKnots ?: 0.0) }
                ?: stringResource(R.string.map_tile_position_only),
            "",
        )
    }
        ChartTileFrameKind.STATIC -> chartLauncherCopy(facts.snapshot).copy(
        subject = stringResource(R.string.app_chart),
        status = stringResource(R.string.map_launcher_browse),
    )
    }
}

@Composable
private fun ChartLauncherIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val stroke = unit * .075f
        val centre = center
        drawCircle(color, unit * .31f, centre, style = Stroke(stroke))
        drawLine(color, Offset(centre.x, centre.y - unit * .42f), Offset(centre.x, centre.y + unit * .42f), stroke)
        drawLine(color, Offset(centre.x - unit * .42f, centre.y), Offset(centre.x + unit * .42f, centre.y), stroke)
        val needle = Path().apply {
            moveTo(centre.x + unit * .08f, centre.y - unit * .30f)
            lineTo(centre.x - unit * .04f, centre.y + unit * .11f)
            lineTo(centre.x + unit * .18f, centre.y - unit * .03f)
            close()
        }
        drawPath(needle, color)
    }
}
