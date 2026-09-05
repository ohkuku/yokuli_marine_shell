package com.yokuli.marine.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherIconRenderer
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherTileRenderer
import com.yokuli.shell.contract.MarineTileSize
import kotlin.math.min

/** App-owned Settings presentation with an explicit renderer for every declared size. */
@Composable
fun settingsLauncherVisualContribution(theme: WpThemeSpec): LauncherEntryVisualContribution {
    val title = stringResource(R.string.app_settings)
    val headline = stringResource(
        if (theme.mode == WpThemeMode.DARK) R.string.launcher_tile_dark else R.string.launcher_tile_light,
    )
    val detail = stringResource(R.string.launcher_tile_accent, theme.accent.displayName)
    return LauncherEntryVisualContribution(
        entryId = SettingsDestinations.EntryId,
        title = title,
        chineseIndex = 'S',
        headline = headline,
        detail = detail,
        icon = LauncherIconRenderer { tint, modifier -> SettingsLauncherIcon(tint, modifier) },
        tileRenderers = mapOf(
            MarineTileSize.ICON_1X1 to LauncherTileRenderer { context -> SettingsIconTile(context) },
            MarineTileSize.STANDARD_2X2 to LauncherTileRenderer { context ->
                SettingsStandardTile(context, title, headline, detail)
            },
        ),
    )
}

@Composable
private fun SettingsIconTile(context: LauncherTileRenderContext) {
    Box(context.modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SettingsLauncherIcon(context.contentColor, Modifier.size(34.dp))
    }
}

@Composable
private fun SettingsStandardTile(
    context: LauncherTileRenderContext,
    title: String,
    headline: String,
    detail: String,
) {
    Box(context.modifier.fillMaxSize()) {
        SettingsLauncherIcon(context.contentColor, Modifier.align(Alignment.TopStart).size(30.dp))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth()) {
            WpText(headline, 25, color = context.contentColor, weight = FontWeight.Light)
            WpText(detail, 11, color = context.contentColor.copy(alpha = .82f))
        }
        WpText(title, 12, color = context.contentColor, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun SettingsLauncherIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val stroke = unit * .075f
        repeat(3) { index ->
            val y = unit * (.23f + index * .27f)
            val knobX = unit * listOf(.32f, .68f, .45f)[index]
            drawLine(color, Offset(unit * .08f, y), Offset(unit * .92f, y), stroke * .72f)
            drawCircle(color, stroke * 1.35f, Offset(knobX, y))
        }
    }
}
