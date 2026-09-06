package com.yokuli.marine.feature.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpText

enum class NavigationGpxItemKind { WAYPOINT, ROUTE, TRACK }
enum class NavigationGpxWarning { SHORT_ROUTE, UNKNOWN_EXTENSIONS, INVALID_OPTIONAL_TIME, EMPTY_TRACK }
enum class NavigationGpxFailure { INVALID_DOCUMENT, EMPTY_SELECTION, QUEUE_BUSY, WRITE_FAILED }

data class NavigationGpxBounds(val south: Double, val west: Double, val north: Double, val east: Double)

data class NavigationGpxItem(
    val index: Int,
    val kind: NavigationGpxItemKind,
    val name: String,
    val selected: Boolean,
    val pointCount: Int = 1,
    val segmentCount: Int = 0,
)

sealed interface NavigationGpxUiState {
    data object Idle : NavigationGpxUiState
    data object Inspecting : NavigationGpxUiState
    data class Preview(
        val items: List<NavigationGpxItem>,
        val totalPointCount: Int,
        val bounds: NavigationGpxBounds?,
        val duplicate: Boolean,
        val warnings: Set<NavigationGpxWarning>,
        val canImport: Boolean,
    ) : NavigationGpxUiState
    data object Writing : NavigationGpxUiState
    data class Succeeded(val waypointCount: Int, val routeCount: Int, val trackCount: Int) : NavigationGpxUiState
    data object Cancelled : NavigationGpxUiState
    data class Failed(val reason: NavigationGpxFailure) : NavigationGpxUiState
}

sealed interface NavigationGpxUiAction {
    data object ChooseDocument : NavigationGpxUiAction
    data class ToggleItem(val kind: NavigationGpxItemKind, val index: Int) : NavigationGpxUiAction
    data object ConfirmImport : NavigationGpxUiAction
    data object ImportAsCopy : NavigationGpxUiAction
    data object Cancel : NavigationGpxUiAction
    data object DismissResult : NavigationGpxUiAction
}

/** Navigation owns the GPX product surface; the Shell may adapt a compatible document runtime. */
@Composable
fun NavigationGpxWorkspace(state: NavigationGpxUiState, onAction: (NavigationGpxUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag("navigation-gpx-workspace"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WpText(stringResource(R.string.navigation_gpx_limits), 10, color = colors.muted)
        WpText(stringResource(R.string.navigation_gpx_extensions), 10, color = colors.muted)
        when (state) {
            NavigationGpxUiState.Idle -> GpxCommand(
                stringResource(R.string.navigation_gpx_choose), "navigation-gpx-choose",
            ) { onAction(NavigationGpxUiAction.ChooseDocument) }
            NavigationGpxUiState.Inspecting -> {
                WpText(stringResource(R.string.navigation_gpx_inspecting), 12, modifier = Modifier.testTag("navigation-gpx-inspecting"))
                GpxCommand(stringResource(R.string.navigation_cancel), "navigation-gpx-cancel") {
                    onAction(NavigationGpxUiAction.Cancel)
                }
            }
            is NavigationGpxUiState.Preview -> GpxPreview(state, onAction)
            NavigationGpxUiState.Writing -> WpText(
                stringResource(R.string.navigation_gpx_writing), 12, modifier = Modifier.testTag("navigation-gpx-writing"),
            )
            is NavigationGpxUiState.Succeeded -> {
                WpText(
                    stringResource(
                        R.string.navigation_gpx_succeeded,
                        state.waypointCount,
                        state.routeCount,
                        state.trackCount,
                    ),
                    12,
                    modifier = Modifier.testTag("navigation-gpx-succeeded"),
                )
                GpxCommand(stringResource(R.string.navigation_close), "navigation-gpx-close") {
                    onAction(NavigationGpxUiAction.DismissResult)
                }
            }
            NavigationGpxUiState.Cancelled -> {
                WpText(stringResource(R.string.navigation_gpx_cancelled), 12, modifier = Modifier.testTag("navigation-gpx-cancelled"))
                GpxCommand(stringResource(R.string.navigation_gpx_choose_again), "navigation-gpx-retry") {
                    onAction(NavigationGpxUiAction.ChooseDocument)
                }
            }
            is NavigationGpxUiState.Failed -> {
                WpText(stringResource(state.reason.label()), 12, color = colors.accent, modifier = Modifier.testTag("navigation-gpx-failed"))
                GpxCommand(stringResource(R.string.navigation_gpx_choose_again), "navigation-gpx-retry") {
                    onAction(NavigationGpxUiAction.ChooseDocument)
                }
            }
        }
    }
}

@Composable
private fun GpxPreview(state: NavigationGpxUiState.Preview, onAction: (NavigationGpxUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    val waypoints = state.items.count { it.kind == NavigationGpxItemKind.WAYPOINT }
    val routes = state.items.count { it.kind == NavigationGpxItemKind.ROUTE }
    val tracks = state.items.count { it.kind == NavigationGpxItemKind.TRACK }
    WpText(
        stringResource(R.string.navigation_gpx_counts, waypoints, routes, tracks, state.totalPointCount),
        13,
        modifier = Modifier.testTag("navigation-gpx-preview"),
    )
    state.bounds?.let {
        WpText(stringResource(R.string.navigation_gpx_bounds, it.south, it.west, it.north, it.east), 10, color = colors.muted)
    }
    if (state.duplicate) {
        WpText(stringResource(R.string.navigation_gpx_duplicate), 11, color = colors.accent, modifier = Modifier.testTag("navigation-gpx-duplicate"))
    }
    state.warnings.forEach { warning -> WpText(stringResource(warning.label()), 10, color = colors.accent) }
    state.items.forEach { item ->
        val title = item.name.ifBlank { stringResource(item.kind.unnamedLabel(), item.index + 1) }
        val label = when (item.kind) {
            NavigationGpxItemKind.WAYPOINT -> title
            NavigationGpxItemKind.ROUTE -> stringResource(R.string.navigation_gpx_route_item, title, item.pointCount)
            NavigationGpxItemKind.TRACK -> stringResource(
                R.string.navigation_gpx_track_item, title, item.segmentCount, item.pointCount,
            )
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(remember { MutableInteractionSource() }, null, role = Role.Checkbox) {
                    onAction(NavigationGpxUiAction.ToggleItem(item.kind, item.index))
                }
                .border(1.dp, if (item.selected) colors.accent else colors.muted.copy(alpha = .45f))
                .padding(horizontal = 8.dp).testTag("navigation-gpx-${item.kind.name.lowercase()}-${item.index}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WpText(if (item.selected) "✓" else "○", 16, color = if (item.selected) colors.accent else colors.muted)
            WpText(label, 12, modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 2)
        }
    }
    Row(Modifier.fillMaxWidth()) {
        GpxCommand(
            stringResource(if (state.duplicate) R.string.navigation_gpx_import_copy else R.string.navigation_gpx_confirm),
            if (state.duplicate) "navigation-gpx-import-copy" else "navigation-gpx-confirm",
            Modifier.weight(1f),
            state.canImport,
        ) { onAction(if (state.duplicate) NavigationGpxUiAction.ImportAsCopy else NavigationGpxUiAction.ConfirmImport) }
        GpxCommand(stringResource(R.string.navigation_cancel), "navigation-gpx-preview-cancel", Modifier.weight(1f)) {
            onAction(NavigationGpxUiAction.Cancel)
        }
    }
}

@Composable
private fun GpxCommand(
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    action: () -> Unit,
) {
    val colors = LocalWpTheme.current
    WpText(
        label,
        13,
        color = if (enabled) colors.accent else colors.muted,
        modifier = modifier.heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = action,
            )
            .padding(vertical = 13.dp).testTag(tag),
    )
}

private fun NavigationGpxFailure.label() = when (this) {
    NavigationGpxFailure.INVALID_DOCUMENT -> R.string.navigation_gpx_error_invalid
    NavigationGpxFailure.EMPTY_SELECTION -> R.string.navigation_gpx_error_empty
    NavigationGpxFailure.QUEUE_BUSY -> R.string.navigation_gpx_error_busy
    NavigationGpxFailure.WRITE_FAILED -> R.string.navigation_gpx_error_write
}

private fun NavigationGpxWarning.label() = when (this) {
    NavigationGpxWarning.SHORT_ROUTE -> R.string.navigation_gpx_warning_route
    NavigationGpxWarning.UNKNOWN_EXTENSIONS -> R.string.navigation_gpx_warning_extensions
    NavigationGpxWarning.INVALID_OPTIONAL_TIME -> R.string.navigation_gpx_warning_time
    NavigationGpxWarning.EMPTY_TRACK -> R.string.navigation_gpx_warning_track
}

private fun NavigationGpxItemKind.unnamedLabel() = when (this) {
    NavigationGpxItemKind.WAYPOINT -> R.string.navigation_gpx_unnamed_waypoint
    NavigationGpxItemKind.ROUTE -> R.string.navigation_gpx_unnamed_route
    NavigationGpxItemKind.TRACK -> R.string.navigation_gpx_unnamed_track
}
