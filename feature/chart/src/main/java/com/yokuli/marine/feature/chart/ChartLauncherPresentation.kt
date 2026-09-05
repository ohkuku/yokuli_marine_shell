package com.yokuli.marine.feature.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.WpText
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherIconRenderer
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherTileRenderer
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.marine.map.domain.MapState
import kotlin.math.min

/** App-owned Map presentation. Shell supplies the accent surface and edit chrome only. */
@Composable
fun chartLauncherVisualContribution(
    mapConfigured: Boolean,
    mapState: MapState,
): LauncherEntryVisualContribution {
    val title = stringResource(R.string.app_chart)
    val headline = stringResource(R.string.map_tile_offline_first)
    val detail = stringResource(
        R.string.map_tile_local_facts,
        mapState.places.size,
        mapState.savedRoutes.size,
        mapState.chartPackages.size,
    )
    return LauncherEntryVisualContribution(
        entryId = ChartDestinations.EntryId,
        title = title,
        chineseIndex = 'H',
        headline = headline,
        detail = detail,
        icon = LauncherIconRenderer { tint, modifier -> ChartLauncherIcon(tint, modifier) },
        tileRenderers = mapOf(
            MarineTileSize.ICON_1X1 to LauncherTileRenderer { context ->
                Box(context.modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChartLauncherIcon(context.contentColor, Modifier.size(34.dp))
                }
            },
            MarineTileSize.STANDARD_2X2 to LauncherTileRenderer { context ->
                ChartStandardTile(context, title)
            },
            MarineTileSize.WIDE_4X2 to LauncherTileRenderer { context ->
                ChartWideTile(context, title, headline, detail)
            },
        ),
    )
}

@Composable
private fun ChartStandardTile(context: LauncherTileRenderContext, title: String) {
    Box(context.modifier.fillMaxSize()) {
        ChartLauncherIcon(context.contentColor, Modifier.align(Alignment.Center).size(44.dp))
        WpText(title, 12, color = context.contentColor, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun ChartWideTile(
    context: LauncherTileRenderContext,
    title: String,
    headline: String,
    detail: String,
) {
    Box(context.modifier.fillMaxSize()) {
        ChartLauncherIcon(context.contentColor, Modifier.align(Alignment.TopStart).size(31.dp))
        Column(Modifier.align(Alignment.CenterStart)) {
            WpText(headline, 29, color = context.contentColor, weight = FontWeight.Light)
            WpText(detail, 13, color = context.contentColor.copy(alpha = .82f))
        }
        WpText(title, 13, color = context.contentColor, modifier = Modifier.align(Alignment.BottomStart))
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
