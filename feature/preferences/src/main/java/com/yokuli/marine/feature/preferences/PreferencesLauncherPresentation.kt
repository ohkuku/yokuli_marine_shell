package com.yokuli.marine.feature.preferences

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.WpText
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherIconRenderer
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherTileRenderer
import com.yokuli.shell.contract.MarineTileSize
import kotlin.math.min

@Composable
fun preferencesLauncherVisualContribution(): LauncherEntryVisualContribution {
    val title = stringResource(R.string.preferences_title)
    return LauncherEntryVisualContribution(
        entryId = PreferencesDestinations.EntryId,
        title = title,
        chineseIndex = 'S',
        headline = stringResource(R.string.preferences_tile_headline),
        detail = stringResource(R.string.preferences_tile_detail),
        icon = LauncherIconRenderer { color, modifier -> PreferencesIcon(color, modifier) },
        tileRenderers = mapOf(
            MarineTileSize.ICON_1X1 to LauncherTileRenderer { SmallTile(it) },
            MarineTileSize.STANDARD_2X2 to LauncherTileRenderer { MediumTile(it, title) },
        ),
    )
}

@Composable
private fun SmallTile(context: LauncherTileRenderContext) {
    Box(context.modifier.fillMaxSize().testTag("preferences-tile-small"), contentAlignment = Alignment.Center) {
        PreferencesIcon(context.contentColor, Modifier.size(44.dp))
    }
}

@Composable
private fun MediumTile(context: LauncherTileRenderContext, title: String) {
    Box(context.modifier.fillMaxSize().testTag("preferences-tile-medium")) {
        PreferencesIcon(context.contentColor, Modifier.size(42.dp).align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart)) {
            WpText(stringResource(R.string.preferences_tile_headline), 18, color = context.contentColor, weight = FontWeight.Light)
            WpText(title, 12, color = context.contentColor)
        }
    }
}

@Composable
private fun PreferencesIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        drawCircle(color, unit * .34f, style = Stroke(unit * .12f))
        drawCircle(color, unit * .09f)
    }
}
