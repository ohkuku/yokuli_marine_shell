package com.yokuli.marine.feature.chart

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapState
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherIconRenderer
import com.yokuli.shell.compose.LauncherSearchResultContribution
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherTileRenderer
import com.yokuli.shell.contract.MarineTileSize
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/** App-owned Map presentation. Shell supplies the accent surface and edit chrome only. */
@Composable
fun chartLauncherVisualContribution(
    mapState: MapState,
    offlineCoverageState: OfflineCoverageUiState = OfflineCoverageUiState.Idle,
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
                ChartStandardTile(context, title, chartLauncherCopy(shown))
            },
            MarineTileSize.WIDE_4X2 to LauncherTileRenderer { context ->
                val shown = rememberVisibleChartSnapshot(snapshot, context.liveContentEnabled)
                ChartWideTile(context, title, shown, chartLauncherCopy(shown))
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
    val updateKey: Any = if (liveContentEnabled || snapshot.critical) snapshot else FrozenDecorativeContent
    return remember(liveContentEnabled, updateKey) { snapshot }
}

private data object FrozenDecorativeContent

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
                WpText(it, 13, color = Color.Black, weight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChartStandardTile(context: LauncherTileRenderContext, title: String, copy: ChartLauncherCopy) {
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
    copy: ChartLauncherCopy,
) {
    Row(context.modifier.fillMaxSize()) {
        Box(Modifier.fillMaxHeight().width(116.dp), contentAlignment = Alignment.Center) {
            val preview = snapshot.routePreview?.takeIf { it.points.size >= 2 }
            if (preview == null) {
                ChartLauncherIcon(context.contentColor, Modifier.size(54.dp))
            } else {
                ChartRouteMiniMap(preview.points, context.contentColor, Modifier.fillMaxSize().testTag("chart-tile-route-preview"))
                WpText(
                    copy.previewLabel,
                    10,
                    color = context.contentColor.copy(alpha = .84f),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
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

@Composable
private fun ChartRouteMiniMap(points: List<GeoPoint>, color: Color, modifier: Modifier) {
    val normalized = remember(points) { unwrapLongitudes(points) }
    Canvas(modifier) {
        if (normalized.size < 2) return@Canvas
        val minLongitude = normalized.minOf { it.second }
        val maxLongitude = normalized.maxOf { it.second }
        val minLatitude = normalized.minOf { it.first }
        val maxLatitude = normalized.maxOf { it.first }
        val longitudeSpan = (maxLongitude - minLongitude).coerceAtLeast(1e-9)
        val latitudeSpan = (maxLatitude - minLatitude).coerceAtLeast(1e-9)
        val inset = size.minDimension * .12f
        fun screen(point: Pair<Double, Double>) = Offset(
            inset + ((point.second - minLongitude) / longitudeSpan).toFloat() * (size.width - inset * 2f),
            inset + ((maxLatitude - point.first) / latitudeSpan).toFloat() * (size.height - inset * 2f),
        )
        val path = Path().apply {
            val first = screen(normalized.first())
            moveTo(first.x, first.y)
            normalized.drop(1).forEach { point -> screen(point).let { lineTo(it.x, it.y) } }
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color, 4.dp.toPx(), screen(normalized.first()))
        drawCircle(color, 4.dp.toPx(), screen(normalized.last()), style = Stroke(2.dp.toPx()))
    }
}

private fun unwrapLongitudes(points: List<GeoPoint>): List<Pair<Double, Double>> {
    var previous: Double? = null
    return points.map { point ->
        var longitude = point.longitude
        previous?.let { last ->
            while (longitude - last > 180.0) longitude -= 360.0
            while (longitude - last < -180.0) longitude += 360.0
        }
        previous = longitude
        point.latitude to longitude
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
