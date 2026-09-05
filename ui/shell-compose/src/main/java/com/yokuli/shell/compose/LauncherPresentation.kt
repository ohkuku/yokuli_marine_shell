package com.yokuli.shell.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize

/**
 * App-owned icon renderer used by Start and All Apps. The Shell supplies only tint and bounds.
 */
fun interface LauncherIconRenderer {
    @Composable
    fun Render(tint: Color, modifier: Modifier)
}

data class LauncherTileRenderContext(
    val size: MarineTileSize,
    val contentColor: Color,
    val modifier: Modifier,
    /** Decorative/live content must freeze while the Shell is editing Start. */
    val liveContentEnabled: Boolean,
)

/**
 * One renderer represents one explicit tile size. Shell never scales another size as fallback.
 */
fun interface LauncherTileRenderer {
    @Composable
    fun Render(context: LauncherTileRenderContext)
}

/**
 * The feature app owns this contribution. Shell owns the accent container and edit chrome.
 */
data class LauncherEntryVisualContribution(
    val entryId: LauncherEntryId,
    val title: String,
    val chineseIndex: Char,
    val headline: String,
    val detail: String,
    val icon: LauncherIconRenderer,
    val tileRenderers: Map<MarineTileSize, LauncherTileRenderer>,
)

data class LauncherEntryUiState(
    val descriptor: LauncherEntryDescriptor,
    val visual: LauncherEntryVisualContribution,
) {
    val title: String get() = visual.title
    val chineseIndex: Char get() = visual.chineseIndex
    val headline: String get() = visual.headline
    val detail: String get() = visual.detail
    val icon: LauncherIconRenderer get() = visual.icon

    fun tileRenderer(size: MarineTileSize): LauncherTileRenderer =
        requireNotNull(visual.tileRenderers[size]) {
            "Missing ${size.name} renderer for ${descriptor.entryId.value}"
        }
}

/** Fail-fast validation at the composition root, before a bad installation reaches Start. */
object LauncherPresentationValidator {
    fun validate(
        catalog: LauncherCatalogSnapshot,
        contributions: List<LauncherEntryVisualContribution>,
    ) {
        val byEntry = contributions.associateBy { it.entryId }
        require(byEntry.size == contributions.size) { "Duplicate launcher visual contribution" }
        require(byEntry.keys == catalog.entries.map { it.entryId }.toSet()) {
            "Launcher visual contributions must exactly match the runtime catalog"
        }
        catalog.entries.forEach { descriptor ->
            val visual = requireNotNull(byEntry[descriptor.entryId])
            require(visual.tileRenderers.keys == descriptor.supportedSizes.toSet()) {
                "Tile renderers for ${descriptor.entryId.value} must exactly match its supported sizes"
            }
        }
    }
}
