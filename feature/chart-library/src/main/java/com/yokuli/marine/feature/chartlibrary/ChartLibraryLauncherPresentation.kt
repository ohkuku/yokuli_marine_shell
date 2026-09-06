package com.yokuli.marine.feature.chartlibrary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.WpText
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherIconRenderer
import com.yokuli.shell.compose.LauncherSearchResultContribution
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LauncherTileRenderer
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPlan
import com.yokuli.marine.core.design.PresentationCadence
import com.yokuli.marine.core.design.rememberCadencedLiveValue
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

data class ChartLibraryTileState(
    val sourceCount: Int,
    val assetCount: Int,
    val availableCount: Int,
    val attentionCount: Int,
    val scanningCount: Int,
    val visibleLayerCount: Int = 0,
    val visibleLayerNames: List<String> = emptyList(),
    val displayWarningCount: Int = 0,
) {
    val critical: Boolean get() = attentionCount > 0 || displayWarningCount > 0
    init {
        require(visibleLayerCount >= visibleLayerNames.size)
        require(visibleLayerNames.size <= 3 && displayWarningCount >= 0)
    }
}

class ChartLibraryTileDisplaySlot(initial: ChartLibraryTileState) {
    var shown: ChartLibraryTileState = initial
        private set

    fun resolve(incoming: ChartLibraryTileState, liveContentEnabled: Boolean): ChartLibraryTileState {
        if (liveContentEnabled || incoming.critical || shown.critical != incoming.critical) shown = incoming
        return shown
    }
}

data class ChartLibraryStatusCopy(val compact: String, val expanded: String)

data class ChartLibrarySearchMatch(
    val stableId: String,
    val title: String,
    val sourceSummary: String?,
    val kind: Kind,
    val launchToken: LaunchToken,
) {
    enum class Kind { SOURCE, ASSET }
}

object ChartLibraryLauncherProjector {
    fun project(state: ChartLibraryUiState, displayPlan: ChartDisplayPlan = ChartDisplayPlan.EMPTY) = ChartLibraryTileState(
        sourceCount = state.summary.sourceCount,
        assetCount = state.summary.assetCount,
        availableCount = state.summary.availableAssetCount,
        attentionCount = state.summary.attentionCount,
        scanningCount = state.summary.scanningCount,
        visibleLayerCount = displayPlan.layers.size,
        visibleLayerNames = displayPlan.layers.map { it.displayName }.take(3),
        displayWarningCount = displayPlan.issues.size + if (displayPlan.omittedLayerCount > 0) 1 else 0,
    )
}

object ChartLibrarySearchProjector {
    fun search(state: ChartLibraryUiState, query: String, maximumResults: Int = 24): List<ChartLibrarySearchMatch> {
        require(maximumResults > 0)
        val needle = query.searchNormalized()
        if (needle.isBlank()) return emptyList()
        return state.searchItems.asSequence().filter { item ->
            needle in item.title.searchNormalized() ||
                (item as? ChartLibrarySearchItem.Asset)?.sourceSummary?.searchNormalized()?.contains(needle) == true
        }.map { item ->
            when (item) {
                is ChartLibrarySearchItem.Source -> ChartLibrarySearchMatch(
                    "chart-library-source-${item.id.value}", item.title, null,
                    ChartLibrarySearchMatch.Kind.SOURCE, ChartLibraryDestinations.source(item.id),
                )
                is ChartLibrarySearchItem.Asset -> ChartLibrarySearchMatch(
                    "chart-library-asset-${item.id.value}", item.title, item.sourceSummary,
                    ChartLibrarySearchMatch.Kind.ASSET, ChartLibraryDestinations.asset(item.id),
                )
            }
        }.take(maximumResults).toList()
    }

    private fun String.searchNormalized(): String =
        Normalizer.normalize(trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}

@Composable
fun chartLibraryLauncherVisualContribution(
    state: ChartLibraryUiState,
    displayPlan: ChartDisplayPlan = ChartDisplayPlan.EMPTY,
): LauncherEntryVisualContribution {
    val title = stringResource(R.string.chart_library_title)
    val incoming = ChartLibraryLauncherProjector.project(state, displayPlan)
    return LauncherEntryVisualContribution(
        entryId = ChartLibraryDestinations.EntryId,
        title = title,
        chineseIndex = '海',
        headline = tileHeadline(incoming),
        detail = tileDetail(incoming),
        icon = LauncherIconRenderer { tint, modifier -> ChartLibraryIcon(tint, modifier) },
        tileRenderers = mapOf(
            MarineTileSize.ICON_1X1 to LauncherTileRenderer { context ->
                ChartLibrarySmallTile(context, rememberLibraryTile(incoming, context.liveContentEnabled))
            },
            MarineTileSize.STANDARD_2X2 to LauncherTileRenderer { context ->
                ChartLibraryMediumTile(context, title, rememberLibraryTile(incoming, context.liveContentEnabled))
            },
            MarineTileSize.WIDE_4X2 to LauncherTileRenderer { context ->
                ChartLibraryWideTile(context, title, rememberLibraryTile(incoming, context.liveContentEnabled))
            },
        ),
    )
}

@Composable
fun chartLibrarySearchContributions(state: ChartLibraryUiState, query: String): List<LauncherSearchResultContribution> {
    val title = stringResource(R.string.chart_library_title)
    val root = if (query.isNotBlank() && title.contains(query, ignoreCase = true)) listOf(
        LauncherSearchResultContribution(
            stableId = "chart-library-root",
            title = title,
            detail = stringResource(R.string.launcher_search_app),
            launchToken = ChartLibraryDestinations.Browse,
        ),
    ) else emptyList()
    return (root + ChartLibrarySearchProjector.search(state, query).map { match ->
        LauncherSearchResultContribution(
            stableId = match.stableId,
            title = match.title,
            detail = when (match.kind) {
                ChartLibrarySearchMatch.Kind.SOURCE -> stringResource(R.string.launcher_search_source)
                ChartLibrarySearchMatch.Kind.ASSET -> match.sourceSummary?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.launcher_search_asset)
            },
            launchToken = match.launchToken,
        )
    }).take(24)
}

@Composable
fun chartLibraryStatusCopy(
    state: ChartLibraryUiState,
    currentDisplayNeedsAttention: Boolean,
): ChartLibraryStatusCopy? = when {
    currentDisplayNeedsAttention -> ChartLibraryStatusCopy(
        compact = stringResource(R.string.launcher_status_current_issue_compact),
        expanded = stringResource(R.string.launcher_status_current_issue),
    )
    state.summary.scanningCount > 0 -> ChartLibraryStatusCopy(
        compact = stringResource(R.string.launcher_status_scanning_compact, state.summary.scanningCount),
        expanded = stringResource(R.string.launcher_status_scanning, state.summary.scanningCount),
    )
    else -> null
}

@Composable
private fun rememberLibraryTile(incoming: ChartLibraryTileState, live: Boolean): ChartLibraryTileState {
    val cadenced = rememberCadencedLiveValue(
        incoming,
        structuralKey = Triple(incoming.critical, incoming.displayWarningCount, incoming.visibleLayerNames),
        cadence = PresentationCadence.StartTile,
    )
    val slot = remember { ChartLibraryTileDisplaySlot(incoming) }
    return slot.resolve(cadenced, live)
}

@Composable
private fun ChartLibrarySmallTile(context: LauncherTileRenderContext, state: ChartLibraryTileState) {
    Box(context.modifier.fillMaxSize().testTag("chart-library-tile-small"), contentAlignment = Alignment.Center) {
        ChartLibraryIcon(context.contentColor, Modifier.size(44.dp))
        if (state.attentionCount > 0) {
            WpText("!", 16, color = context.contentColor, weight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun ChartLibraryMediumTile(context: LauncherTileRenderContext, title: String, state: ChartLibraryTileState) {
    Box(context.modifier.fillMaxSize().testTag("chart-library-tile-medium")) {
        ChartLibraryIcon(context.contentColor, Modifier.size(34.dp).align(Alignment.TopStart))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth()) {
            WpText(tileHeadline(state), 23, color = context.contentColor, weight = FontWeight.Light, maxLines = 2)
            if (state.scanningCount > 0) WpText(
                stringResource(R.string.launcher_scan_secondary, state.scanningCount), 11,
                color = context.contentColor.copy(alpha = .84f), maxLines = 1,
            )
        }
        WpText(title, 12, color = context.contentColor, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun ChartLibraryWideTile(context: LauncherTileRenderContext, title: String, state: ChartLibraryTileState) {
    Row(context.modifier.fillMaxSize().testTag("chart-library-tile-wide")) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            ChartLibraryIcon(context.contentColor, Modifier.size(34.dp))
            Spacer(Modifier.weight(1f))
            WpText(tileHeadline(state), 22, color = context.contentColor, weight = FontWeight.Light, maxLines = 1)
            WpText(title, 12, color = context.contentColor)
        }
        Column(Modifier.width(148.dp).padding(start = 12.dp, top = 4.dp)) {
            WpText(stringResource(R.string.launcher_visible_layers, state.visibleLayerCount), 13, color = context.contentColor, maxLines = 1)
            state.visibleLayerNames.forEach { name ->
                WpText(name, 10, color = context.contentColor.copy(alpha = .84f), maxLines = 1)
            }
            if (state.visibleLayerNames.isEmpty()) {
                WpText(stringResource(R.string.launcher_no_visible_layers), 10, color = context.contentColor.copy(alpha = .84f), maxLines = 1)
            }
            if (state.attentionCount > 0) WpText(
                stringResource(R.string.launcher_attention, state.attentionCount), 11,
                color = context.contentColor, weight = FontWeight.SemiBold, maxLines = 1,
            )
            if (state.displayWarningCount > 0) WpText(
                stringResource(R.string.launcher_display_warnings, state.displayWarningCount), 11,
                color = context.contentColor, weight = FontWeight.SemiBold, maxLines = 1,
            )
            if (state.scanningCount > 0) WpText(
                stringResource(R.string.launcher_scanning, state.scanningCount), 11,
                color = context.contentColor.copy(alpha = .84f), maxLines = 1,
            )
        }
    }
}

@Composable
private fun tileHeadline(state: ChartLibraryTileState): String = when {
    state.attentionCount > 0 -> stringResource(R.string.launcher_attention, state.attentionCount)
    else -> stringResource(R.string.launcher_available, state.availableCount)
}

@Composable
private fun tileDetail(state: ChartLibraryTileState): String =
    stringResource(R.string.launcher_sources_assets, state.sourceCount, state.assetCount)

@Composable
private fun ChartLibraryIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val stroke = unit * .065f
        repeat(3) { index ->
            val inset = unit * (.12f + index * .08f)
            drawRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(unit * .68f, unit * .58f),
                style = Stroke(stroke),
            )
        }
        drawLine(color, Offset(unit * .30f, unit * .42f), Offset(unit * .62f, unit * .26f), stroke)
        drawLine(color, Offset(unit * .30f, unit * .42f), Offset(unit * .58f, unit * .55f), stroke)
    }
}
