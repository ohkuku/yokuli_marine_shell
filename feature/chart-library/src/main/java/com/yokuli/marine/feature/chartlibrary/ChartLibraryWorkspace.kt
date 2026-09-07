package com.yokuli.marine.feature.chartlibrary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpAppBarAction
import com.yokuli.marine.core.design.WpApplicationBar
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.wpEntrance
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetAccessState
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFormat
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetValidationState
import com.yokuli.marine.map.domain.chartlibrary.ChartBuiltInBaseStyle
import com.yokuli.marine.map.domain.chartlibrary.ChartCompatibilityWarning
import com.yokuli.marine.map.domain.chartlibrary.ChartFactProvenance
import com.yokuli.marine.map.domain.chartlibrary.ChartGrantState
import com.yokuli.marine.map.domain.chartlibrary.ChartLayerHealth
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySourceKind
import com.yokuli.marine.map.domain.chartlibrary.ChartManagedCopyFailure
import com.yokuli.marine.map.domain.chartlibrary.ChartManagedCopyStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartReadAccessMode
import com.yokuli.marine.map.domain.chartlibrary.ChartScanStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationJobStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationIssue
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import java.util.Locale

@Composable
fun ChartLibraryWorkspace(
    state: ChartLibraryUiState,
    onAction: (ChartLibraryUiAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    val currentState by rememberUpdatedState(state)
    val currentAction by rememberUpdatedState(onAction)
    BindInternalAppInputHandler { input ->
        if (input != ShellInput.BACK) return@BindInternalAppInputHandler false
        ChartLibraryBackPolicy.actionFor(currentState.page)?.let {
            currentAction(it)
            true
        } ?: false
    }

    Column(Modifier.fillMaxSize().background(colors.background).testTag(ChartLibraryTestTags.ROOT)) {
        WpPageHeader(
            appKey = "chart-library",
            appName = stringResource(R.string.chart_library_title),
            contextLine = pageContext(state),
            trailing = if (state.busy) stringResource(R.string.state_working) else null,
        )
        state.notice?.let { Notice(it, onAction) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val page = state.page) {
                is ChartLibraryPageUi.Overview -> Overview(state, page, onAction)
                is ChartLibraryPageUi.SourceDetail -> SourceDetail(page, onAction)
                is ChartLibraryPageUi.AssetDetail -> AssetDetail(page.asset, onAction)
                is ChartLibraryPageUi.Storage -> Storage(page.storage)
                is ChartLibraryPageUi.RemoveSourceConfirmation -> RemoveSource(page.source, onAction)
                is ChartLibraryPageUi.DeleteManagedCopyConfirmation -> DeleteManagedCopy(page.asset, onAction)
                is ChartLibraryPageUi.SaveManagedCopyConfirmation -> SaveManagedCopy(page, onAction)
            }
        }
        ApplicationBar(state, onAction)
    }
}

@Composable
private fun Overview(
    state: ChartLibraryUiState,
    page: ChartLibraryPageUi.Overview,
    onAction: (ChartLibraryUiAction) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = YokuliMetrics.PageMargin)) {
        WorkspacePivot(state.workspaceMode, onAction)
        when (state.workspaceMode) {
            ChartLibraryWorkspaceMode.COVERAGE -> CoverageWorkspace(state, onAction)
            ChartLibraryWorkspaceMode.LAYERS -> LayersWorkspace(state, onAction)
            ChartLibraryWorkspaceMode.VIEWS -> ViewsWorkspace(state, onAction)
            ChartLibraryWorkspaceMode.SOURCES -> SourcesWorkspace(state, page, onAction)
        }
    }
}

@Composable
private fun WorkspacePivot(
    selectedMode: ChartLibraryWorkspaceMode,
    onAction: (ChartLibraryUiAction) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ChartLibraryWorkspaceMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            val colors = LocalWpTheme.current
            val interactions = remember { MutableInteractionSource() }
            WpText(
                text = workspaceLabel(mode),
                size = if (selected) 22 else 17,
                color = if (selected) colors.foreground else colors.muted,
                weight = FontWeight.Light,
                modifier = Modifier.heightIn(min = YokuliMetrics.MinTouch)
                    .semantics { this.selected = selected; role = Role.Tab }
                    .clickable(interactionSource = interactions, indication = null) {
                        onAction(ChartLibraryUiAction.SelectWorkspace(mode))
                    }.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CoverageWorkspace(
    state: ChartLibraryUiState,
    onAction: (ChartLibraryUiAction) -> Unit,
) {
    val boundedLayers = state.layers.filter { it.bounds != null }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            WpText(
                stringResource(R.string.coverage_explanation),
                13,
                color = LocalWpTheme.current.muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (state.summary.sourceCount == 0) {
                EmptyLibrary()
            } else {
                CoverageCanvas(boundedLayers)
                WpText(
                    stringResource(
                        R.string.coverage_summary,
                        boundedLayers.count { it.readyAssetCount > 0 },
                        state.layers.count { it.bounds == null },
                        state.summary.attentionCount,
                    ),
                    12,
                    color = LocalWpTheme.current.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (state.summary.sourceCount > 0 && boundedLayers.isEmpty()) {
            item {
                WpText(stringResource(R.string.coverage_unknown_title), 24, weight = FontWeight.Light)
                WpText(stringResource(R.string.coverage_unknown_body), 13, color = LocalWpTheme.current.muted)
            }
        }
        if (state.layers.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.coverage_layers)) }
            itemsIndexed(state.layers, key = { _, row -> row.id.value }) { index, row ->
                CoverageLayerRow(row, index, onAction)
            }
        }
    }
}

@Composable
private fun CoverageCanvas(layers: List<ChartLibraryLayerUi>) {
    val colors = LocalWpTheme.current
    val description = stringResource(R.string.coverage_canvas_description, layers.size)
    Canvas(
        Modifier.fillMaxWidth().height(250.dp).background(colors.foreground.copy(alpha = .035f))
            .border(1.dp, colors.muted.copy(alpha = .55f))
            .semantics { contentDescription = description }
            .testTag(ChartLibraryTestTags.COVERAGE),
    ) {
        for (longitude in -120..120 step 60) {
            val x = ((longitude + 180f) / 360f) * size.width
            drawLine(colors.muted.copy(alpha = .18f), start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height))
        }
        for (latitude in -60..60 step 30) {
            val y = ((90f - latitude) / 180f) * size.height
            drawLine(colors.muted.copy(alpha = .18f), start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y))
        }
        layers.forEach { layer ->
            val bounds = layer.bounds ?: return@forEach
            val color = when {
                layer.health !in setOf(ChartLayerHealth.READY, ChartLayerHealth.WARNING) -> colors.warning
                layer.readyAssetCount > 0 -> colors.accent
                else -> colors.muted
            }
            val top = (((90.0 - bounds.north) / 180.0) * size.height).toFloat()
            val bottom = (((90.0 - bounds.south) / 180.0) * size.height).toFloat()
            fun drawSegment(west: Double, east: Double) {
                val left = (((west + 180.0) / 360.0) * size.width).toFloat()
                val right = (((east + 180.0) / 360.0) * size.width).toFloat()
                val rectSize = androidx.compose.ui.geometry.Size(
                    width = (right - left).coerceAtLeast(2f),
                    height = (bottom - top).coerceAtLeast(2f),
                )
                drawRect(color.copy(alpha = .22f), topLeft = androidx.compose.ui.geometry.Offset(left, top), size = rectSize)
                drawRect(color.copy(alpha = .9f), topLeft = androidx.compose.ui.geometry.Offset(left, top), size = rectSize, style = Stroke(width = 2f))
            }
            if (bounds.crossesAntimeridian) {
                drawSegment(bounds.west, 180.0)
                drawSegment(-180.0, bounds.east)
            } else drawSegment(bounds.west, bounds.east)
        }
    }
}

@Composable
private fun CoverageLayerRow(layer: ChartLibraryLayerUi, index: Int, onAction: (ChartLibraryUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    val selectedColor = if (layer.selected) colors.accent else colors.muted
    Row(
        Modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch)
            .wpEntrance(layer.id.value, index)
            .semantics { selected = layer.selected; role = Role.Button }
            .clickable { onAction(ChartLibraryUiAction.SelectLayer(if (layer.selected) null else layer.id)) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp, 48.dp).background(selectedColor))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            WpText(layer.name, 19, weight = FontWeight.Light)
            WpText(
                stringResource(R.string.layer_coverage_summary, layer.readyAssetCount, layer.assetCount, layerZoomLabel(layer)),
                12,
                color = if (layer.health == ChartLayerHealth.READY) colors.muted else colors.warning,
            )
            if (layer.selected) WpText(boundsLabel(layer.bounds), 11, color = colors.muted)
        }
    }
}

@Composable
private fun LayersWorkspace(state: ChartLibraryUiState, onAction: (ChartLibraryUiAction) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(ChartLibraryTestTags.LAYERS),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { WpText(stringResource(R.string.layers_explanation), 13, color = LocalWpTheme.current.muted) }
        if (state.layers.isEmpty()) {
            item { WpText(stringResource(R.string.layers_empty), 22, weight = FontWeight.Light) }
        } else {
            itemsIndexed(state.layers, key = { _, layer -> layer.id.value }) { index, layer ->
                LogicalLayerRow(layer, index, onAction)
            }
        }
    }
}

@Composable
private fun LogicalLayerRow(layer: ChartLibraryLayerUi, index: Int, onAction: (ChartLibraryUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    var draftName by remember(layer.id, layer.name) { mutableStateOf(layer.name) }
    Column(
        Modifier.fillMaxWidth().border(1.dp, colors.muted.copy(alpha = .45f))
            .padding(12.dp).wpEntrance(layer.id.value, index),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draftName,
                onValueChange = { draftName = it.take(128) },
                modifier = Modifier.weight(1f).heightIn(min = YokuliMetrics.MinTouch),
                textStyle = TextStyle(colors.foreground, 20.sp, fontFamily = FontFamily.SansSerif),
                cursorBrush = SolidColor(colors.accent),
            )
            InlineCommand(stringResource(R.string.action_save), "chart-library-layer-save-${layer.id.value}") {
                onAction(ChartLibraryUiAction.RenameLayer(layer.id, draftName))
            }
            InlineCommand(if (layer.visible) "●" else "○", "chart-library-layer-visible-${layer.id.value}") {
                onAction(ChartLibraryUiAction.SetLayerVisible(layer.id, !layer.visible))
            }
        }
        WpText(
            stringResource(
                R.string.layer_summary,
                layer.sourceCount,
                layer.readyAssetCount,
                layer.assetCount,
                bytesLabel(layer.sizeBytes),
            ),
            12,
            color = if (layer.health == ChartLayerHealth.READY) colors.muted else colors.warning,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            InlineCommand("−", "chart-library-layer-opacity-down-${layer.id.value}") {
                onAction(ChartLibraryUiAction.SetLayerOpacity(layer.id, layer.opacity - .1f))
            }
            WpText("${(layer.opacity * 100).toInt()}%", 12, color = colors.muted, modifier = Modifier.width(48.dp))
            InlineCommand("+", "chart-library-layer-opacity-up-${layer.id.value}") {
                onAction(ChartLibraryUiAction.SetLayerOpacity(layer.id, layer.opacity + .1f))
            }
            InlineCommand("↑", "chart-library-layer-up-${layer.id.value}") {
                onAction(ChartLibraryUiAction.MoveLayer(layer.id, 1))
            }
            InlineCommand("↓", "chart-library-layer-down-${layer.id.value}") {
                onAction(ChartLibraryUiAction.MoveLayer(layer.id, -1))
            }
        }
    }
}

@Composable
private fun ViewsWorkspace(state: ChartLibraryUiState, onAction: (ChartLibraryUiAction) -> Unit) {
    val nextName = stringResource(R.string.view_default_name, state.views.size + 1)
    LazyColumn(
        Modifier.fillMaxSize().testTag(ChartLibraryTestTags.VIEWS),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            WpText(stringResource(R.string.views_explanation), 13, color = LocalWpTheme.current.muted)
            InlineCommand(stringResource(R.string.action_new_view), "chart-library-new-view") {
                onAction(ChartLibraryUiAction.CreateView(nextName))
            }
        }
        itemsIndexed(state.views, key = { _, view -> view.id.value }) { index, view ->
            MapViewRow(view, index, onAction)
        }
    }
}

@Composable
private fun MapViewRow(view: ChartLibraryViewUi, index: Int, onAction: (ChartLibraryUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    var draftName by remember(view.id, view.name) { mutableStateOf(view.name) }
    val copyName = stringResource(R.string.view_copy_name, view.name)
    Column(
        Modifier.fillMaxWidth().border(2.dp, if (view.active) colors.accent else colors.muted.copy(alpha = .4f))
            .padding(12.dp).wpEntrance(view.id.value, index),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draftName,
                onValueChange = { draftName = it.take(128) },
                modifier = Modifier.weight(1f).heightIn(min = YokuliMetrics.MinTouch),
                textStyle = TextStyle(colors.foreground, 20.sp, fontFamily = FontFamily.SansSerif),
                cursorBrush = SolidColor(colors.accent),
            )
            InlineCommand(stringResource(R.string.action_save), "chart-library-view-save-${view.id.value}") {
                onAction(ChartLibraryUiAction.RenameView(view.id, draftName))
            }
        }
        WpText(
            stringResource(R.string.view_summary, baseStyleLabel(view.baseStyle), view.visibleLayerCount),
            12,
            color = colors.muted,
        )
        if (view.layers.isEmpty()) {
            WpText(stringResource(R.string.view_no_layers), 12, color = colors.muted)
        } else {
            view.layers.forEach { layer ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch)
                        .clickable { onAction(ChartLibraryUiAction.ToggleViewLayer(view.id, layer.id)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(16.dp).border(2.dp, if (layer.included) colors.accent else colors.muted)
                            .let { if (layer.included) it.background(colors.accent) else it },
                    )
                    WpText(layer.name, 14, color = if (layer.included) colors.foreground else colors.muted, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ChartBuiltInBaseStyle.entries.forEach { style ->
                InlineCommand(baseStyleLabel(style), "chart-library-view-base-${view.id.value}-${style.name}") {
                    onAction(ChartLibraryUiAction.SetViewBaseStyle(view.id, style))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!view.active) InlineCommand(stringResource(R.string.action_use_view), "chart-library-use-view-${view.id.value}") {
                onAction(ChartLibraryUiAction.ActivateView(view.id))
            }
            InlineCommand(stringResource(R.string.action_duplicate), "chart-library-copy-view-${view.id.value}") {
                onAction(ChartLibraryUiAction.DuplicateView(view.id, copyName))
            }
            if (statefulCanDeleteView(view)) InlineCommand(stringResource(R.string.action_delete), "chart-library-delete-view-${view.id.value}") {
                onAction(ChartLibraryUiAction.DeleteView(view.id))
            }
        }
    }
}

private fun statefulCanDeleteView(view: ChartLibraryViewUi): Boolean = !view.active

@Composable
private fun SourcesWorkspace(
    state: ChartLibraryUiState,
    page: ChartLibraryPageUi.Overview,
    onAction: (ChartLibraryUiAction) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            WpText(stringResource(R.string.sources_explanation), 13, color = LocalWpTheme.current.muted)
            Spacer(Modifier.height(10.dp))
            SearchField(state.query, onAction)
            Spacer(Modifier.height(8.dp))
            FilterRow(state.filter, onAction)
            Spacer(Modifier.height(8.dp))
        }
        if (state.summary.sourceCount == 0) {
            item { EmptyLibrary() }
        } else {
            itemsIndexed(page.sources, key = { _, row -> row.id.value }) { index, row ->
                SourceRow(row, index, onAction)
            }
            if (page.sources.isEmpty()) item { EmptyResult() }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp).testTag(ChartLibraryTestTags.EMPTY)) {
        WpText(stringResource(R.string.empty_title), 28, weight = FontWeight.Light)
        WpText(stringResource(R.string.empty_body), 13, color = LocalWpTheme.current.muted)
    }
}

@Composable
private fun SearchField(query: String, onAction: (ChartLibraryUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    BasicTextField(
        value = query,
        onValueChange = { onAction(ChartLibraryUiAction.ChangeQuery(it)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch)
            .border(1.dp, colors.muted).padding(horizontal = 10.dp, vertical = 12.dp)
            .testTag(ChartLibraryTestTags.SEARCH),
        textStyle = TextStyle(colors.foreground, 17.sp, fontFamily = FontFamily.SansSerif),
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { field ->
            if (query.isEmpty()) WpText(stringResource(R.string.search_hint), 17, color = colors.muted)
            field()
        },
    )
}

@Composable
private fun FilterRow(filter: ChartLibraryFilter, onAction: (ChartLibraryUiAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChartLibraryFilter.entries.forEach { value ->
            val selected = value == filter
            val colors = LocalWpTheme.current
            val interactions = remember { MutableInteractionSource() }
            Box(
                Modifier.weight(1f).height(YokuliMetrics.MinTouch)
                    .background(if (selected) colors.accent else colors.background)
                    .border(1.dp, if (selected) colors.accent else colors.muted)
                    .semantics { this.selected = selected; role = Role.RadioButton }
                    .clickable(interactionSource = interactions, indication = null) {
                        onAction(ChartLibraryUiAction.ChangeFilter(value))
                    },
                contentAlignment = Alignment.Center,
            ) {
                WpText(filterLabel(value), 10, color = if (selected) colors.onAccent else colors.foreground)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceRow(row: ChartLibrarySourceRowUi, index: Int, onAction: (ChartLibraryUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 78.dp)
            .testTag(ChartLibraryTestTags.source(row.id.value))
            .wpEntrance(row.id.value, index)
            .combinedClickable(interactionSource = interactions, indication = null) {
                onAction(ChartLibraryUiAction.OpenSource(row.id))
            }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp, 52.dp).background(if (row.needsAttention) colors.warning else colors.accent))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            WpText(row.name, 22, weight = FontWeight.Light, maxLines = 1)
            WpText(sourceSummary(row), 12, color = colors.muted, maxLines = 2)
            WpText(scanLabel(row.scan.status), 11, color = if (row.needsAttention) colors.warning else colors.muted)
        }
        WpText("›", 24, color = colors.muted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetRow(
    row: ChartLibraryAssetRowUi,
    index: Int,
    onAction: (ChartLibraryUiAction) -> Unit,
    showPath: Boolean = true,
) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .testTag(ChartLibraryTestTags.asset(row.id.value))
            .semantics { selected = row.selected }
            .background(if (row.selected) colors.accent else colors.background)
            .wpEntrance(row.id.value, index)
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                onClick = { onAction(ChartLibraryUiAction.OpenAsset(row.id)) },
                onLongClick = { onAction(ChartLibraryUiAction.ToggleSelection(row.id)) },
            ).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            WpText(row.title, 19, weight = FontWeight.Light, maxLines = 1, color = if (row.selected) colors.onAccent else null)
            if (showPath) {
                WpText(row.displayPath, 11, color = if (row.selected) colors.onAccent else colors.muted, maxLines = 1)
            }
            WpText(assetStatus(row), 11, color = when {
                row.selected -> colors.onAccent
                row.needsAttention -> colors.warning
                else -> colors.muted
            })
        }
        WpText(if (row.role == ChartAssetRole.BASE) "▣" else "◇", 20, color = if (row.selected) colors.onAccent else colors.accent)
    }
}

@Composable
private fun SourceDetail(page: ChartLibraryPageUi.SourceDetail, onAction: (ChartLibraryUiAction) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = YokuliMetrics.PageMargin)) {
        item {
            WpText(page.source.name, 30, weight = FontWeight.Light)
            Fact(stringResource(R.string.fact_source_kind), sourceKindLabel(page.source.kind))
            Fact(stringResource(R.string.fact_provider), page.source.provider ?: stringResource(R.string.unknown))
            Fact(stringResource(R.string.fact_permission), grantLabel(page.source.grantState))
            Fact(stringResource(R.string.fact_recursive), yesNo(page.source.recursive))
            Fact(stringResource(R.string.fact_default_role), roleLabel(page.source.defaultRole))
            Fact(stringResource(R.string.fact_enabled), yesNo(page.source.enabled))
            Fact(stringResource(R.string.fact_scan), scanLabel(page.source.scan.status))
            Fact(stringResource(R.string.fact_generation), page.source.scan.generation.toString())
            Fact(stringResource(R.string.fact_assets), page.source.assetCount.toString())
            if (page.source.canRepairPermission) {
                TextCommand(stringResource(R.string.action_repair_permission), ChartLibraryTestTags.repair(page.source.id.value)) {
                    onAction(ChartLibraryUiAction.RepairPermission(page.source.id))
                }
            }
            SectionTitle(stringResource(R.string.section_assets))
        }
        itemsIndexed(page.assets, key = { _, row -> row.id.value }) { index, row -> AssetRow(row, index, onAction) }
    }
}

@Composable
private fun AssetDetail(asset: ChartLibraryAssetRowUi, onAction: (ChartLibraryUiAction) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = YokuliMetrics.PageMargin)) {
        item {
            WpText(asset.title, 30, weight = FontWeight.Light)
            WpText(asset.displayPath, 12, color = LocalWpTheme.current.muted)
            Fact(stringResource(R.string.fact_sources), asset.sourceNames.joinToString().ifBlank { stringResource(R.string.unknown) })
            Fact(stringResource(R.string.fact_access), accessLabel(asset.access))
            Fact(
                stringResource(R.string.fact_access_mode),
                asset.accessMode?.let { accessModeLabel(it) } ?: stringResource(R.string.unknown),
            )
            Fact(stringResource(R.string.fact_validation), validationLabel(asset.validation))
            if (asset.compatibilityWarnings.isNotEmpty()) {
                val warningLabels = mutableListOf<String>()
                for (warning in asset.compatibilityWarnings.sortedBy { it.name }) {
                    warningLabels += compatibilityWarningLabel(warning)
                }
                Fact(
                    stringResource(R.string.fact_warnings),
                    warningLabels.joinToString(),
                )
            }
            Fact(stringResource(R.string.fact_format), formatLabel(asset.format))
            Fact(stringResource(R.string.fact_encoding), asset.rasterMimeType ?: stringResource(R.string.unknown))
            Fact(stringResource(R.string.fact_tile_size), asset.tileSize?.toString() ?: stringResource(R.string.unknown))
            Fact(stringResource(R.string.fact_scheme), asset.tileScheme?.let { schemeLabel(it) } ?: stringResource(R.string.unknown))
            Fact(stringResource(R.string.fact_zoom), zoomLabel(asset))
            Fact(stringResource(R.string.fact_tiles), asset.tileCount?.toString() ?: stringResource(R.string.unknown))
            Fact(stringResource(R.string.fact_size), bytesLabel(asset.sizeBytes))
            Fact(stringResource(R.string.fact_bounds), boundsLabel(asset))
            Fact(stringResource(R.string.fact_revision), asset.revisionSummary ?: stringResource(R.string.unknown))
            Fact(stringResource(R.string.fact_role), roleLabel(asset.role))
            Fact(stringResource(R.string.fact_priority), asset.priority.toString())
            asset.attribution?.let {
                Fact(stringResource(R.string.fact_attribution), it)
                Fact(stringResource(R.string.fact_attribution_source), provenanceLabel(asset.attributionProvenance))
            }
            asset.originalAssetId?.let {
                Fact(stringResource(R.string.fact_managed_relationship), stringResource(R.string.relationship_copy_of_original))
            }
            asset.managedCopyAssetId?.let {
                Fact(stringResource(R.string.fact_managed_relationship), stringResource(R.string.relationship_has_managed_copy))
            }
            asset.validationJob?.let { job ->
                WpText(validationJobLabel(job.status), 13, color = LocalWpTheme.current.accent, modifier = Modifier.testTag(ChartLibraryTestTags.validation(asset.id.value)))
                job.issue?.let { issue ->
                    WpText(validationIssueLabel(issue), 12, color = LocalWpTheme.current.warning)
                }
                if (job.status == ChartValidationJobStatus.RUNNING) {
                    TextCommand(stringResource(R.string.action_cancel_validation), "chart-library-cancel-validation") {
                        onAction(ChartLibraryUiAction.CancelValidation(asset.id))
                    }
                }
            }
            asset.copyJob?.let { job ->
                WpText(
                    copyJobLabel(job.status, job.copiedBytes, job.totalBytes, job.failure),
                    13,
                    color = LocalWpTheme.current.accent,
                )
                if (job.status in ACTIVE_COPY_STATES) {
                    TextCommand(stringResource(R.string.action_cancel_copy), "chart-library-cancel-copy") {
                        onAction(ChartLibraryUiAction.CancelManagedCopy(asset.id))
                    }
                }
            }
            SectionTitle(stringResource(R.string.section_asset_settings))
            TextCommand(
                stringResource(
                    if (asset.role == ChartAssetRole.BASE) R.string.action_make_overlay else R.string.action_make_base,
                ),
                ChartLibraryTestTags.role(asset.id.value),
            ) { onAction(ChartLibraryUiAction.SetAssetRole(asset.id, asset.role.other())) }
            TextCommand(stringResource(R.string.action_priority_up), ChartLibraryTestTags.priorityUp(asset.id.value)) {
                onAction(ChartLibraryUiAction.MoveAssetPriority(asset.id, 1))
            }
            TextCommand(stringResource(R.string.action_priority_down), ChartLibraryTestTags.priorityDown(asset.id.value)) {
                onAction(ChartLibraryUiAction.MoveAssetPriority(asset.id, -1))
            }
            if (asset.managedCopyAvailable && asset.copyJob?.status !in ACTIVE_COPY_STATES) {
                TextCommand(stringResource(R.string.action_save_copy), ChartLibraryTestTags.managedCopy(asset.id.value)) {
                    onAction(ChartLibraryUiAction.SaveManagedCopy(asset.id))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun validationIssueLabel(issue: ChartValidationIssue): String = stringResource(
    when (issue) {
        ChartValidationIssue.PERMISSION_LOST -> R.string.validation_issue_permission
        ChartValidationIssue.DIRECT_READ_UNSUPPORTED -> R.string.validation_issue_provider
        ChartValidationIssue.INVALID_SCHEMA,
        ChartValidationIssue.INVALID_METADATA,
        ChartValidationIssue.UNKNOWN_SCHEME,
        -> R.string.validation_issue_schema
        ChartValidationIssue.REVISION_CHANGED -> R.string.validation_issue_changed
        ChartValidationIssue.EMPTY_TILESET,
        ChartValidationIssue.INVALID_COORDINATE,
        ChartValidationIssue.MIXED_TILE_SIZE,
        ChartValidationIssue.MIXED_RASTER_ENCODING,
        ChartValidationIssue.DUPLICATE_COORDINATE,
        ChartValidationIssue.READ_FAILED,
        -> R.string.validation_issue_content
        ChartValidationIssue.OPEN_FAILED -> R.string.validation_issue_open
        ChartValidationIssue.CANCELLED -> R.string.validation_issue_cancelled
    },
)

@Composable
private fun Storage(storage: ChartLibraryStorageUi) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = YokuliMetrics.PageMargin).testTag(ChartLibraryTestTags.STORAGE)) {
        item {
            WpText(stringResource(R.string.storage_explanation), 13, color = LocalWpTheme.current.muted)
            Fact(stringResource(R.string.storage_originals), bytesLabel(storage.referencedOriginalBytes))
            Fact(stringResource(R.string.storage_unknown_originals), storage.referencedUnknownSizeCount.toString())
            Fact(stringResource(R.string.storage_managed), bytesLabel(storage.managedCopyBytes))
            Fact(stringResource(R.string.storage_catalog), bytesLabel(storage.catalogBytes))
            Fact(stringResource(R.string.storage_cache), bytesLabel(storage.cacheBytes))
            Fact(stringResource(R.string.storage_copy_capability), yesNo(storage.copyAvailable))
            Fact(stringResource(R.string.storage_available), bytesLabel(storage.availableCopyBytes))
            if (storage.copyJobs.isNotEmpty()) SectionTitle(stringResource(R.string.section_copy_jobs))
            storage.copyJobs.forEach { job ->
                WpText(
                    copyJobLabel(job.status, job.copiedBytes, job.totalBytes, job.failure),
                    13,
                    modifier = Modifier.padding(vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun RemoveSource(source: ChartLibrarySourceRowUi, onAction: (ChartLibraryUiAction) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = YokuliMetrics.PageMargin)) {
        WpText(stringResource(R.string.remove_title), 30, weight = FontWeight.Light)
        WpText(stringResource(R.string.remove_body, source.name), 16, modifier = Modifier.padding(top = 12.dp))
        WpText(stringResource(R.string.remove_safety), 13, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 10.dp))
        TextCommand(stringResource(R.string.action_confirm_remove), "chart-library-confirm-remove") {
            onAction(ChartLibraryUiAction.ConfirmRemoveSource)
        }
    }
}

@Composable
private fun DeleteManagedCopy(asset: ChartLibraryAssetRowUi, onAction: (ChartLibraryUiAction) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = YokuliMetrics.PageMargin)) {
        WpText(stringResource(R.string.delete_copy_title), 30, weight = FontWeight.Light)
        WpText(stringResource(R.string.delete_copy_body, asset.title), 16, modifier = Modifier.padding(top = 12.dp))
        WpText(stringResource(R.string.delete_copy_safety), 13, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 10.dp))
        TextCommand(stringResource(R.string.action_confirm_delete_copy), "chart-library-confirm-delete-copy") {
            onAction(ChartLibraryUiAction.ConfirmDeleteManagedCopy)
        }
    }
}

@Composable
private fun SaveManagedCopy(page: ChartLibraryPageUi.SaveManagedCopyConfirmation, onAction: (ChartLibraryUiAction) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = YokuliMetrics.PageMargin)) {
        WpText(stringResource(R.string.copy_confirm_title), 30, weight = FontWeight.Light)
        WpText(stringResource(R.string.copy_confirm_body, page.asset.title), 16, modifier = Modifier.padding(top = 12.dp))
        Fact(stringResource(R.string.fact_size), bytesLabel(page.asset.sizeBytes))
        Fact(stringResource(R.string.storage_available), bytesLabel(page.availableCopyBytes))
        WpText(stringResource(R.string.copy_confirm_safety), 13, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 10.dp))
        TextCommand(stringResource(R.string.action_confirm_save_copy), "chart-library-confirm-save-copy") {
            onAction(ChartLibraryUiAction.ConfirmManagedCopy)
        }
    }
}

@Composable
private fun ApplicationBar(state: ChartLibraryUiState, onAction: (ChartLibraryUiAction) -> Unit) {
    val actions = when (val page = state.page) {
        is ChartLibraryPageUi.Overview -> if (state.selectedAssetIds.isEmpty()) {
            listOf(
                action("+", R.string.action_add_folder, ChartLibraryTestTags.ADD_FOLDER) { onAction(ChartLibraryUiAction.AddFolder) },
                action("▤", R.string.action_add_file, ChartLibraryTestTags.ADD_FILE) { onAction(ChartLibraryUiAction.AddSingleFile) },
                action("▥", R.string.action_storage, ChartLibraryTestTags.STORAGE) { onAction(ChartLibraryUiAction.OpenStorage) },
            )
        } else listOf(
            action("✓", R.string.action_enable, ChartLibraryTestTags.BULK_ENABLE) { onAction(ChartLibraryUiAction.SetSelectedEnabled(true)) },
            action("×", R.string.action_disable, ChartLibraryTestTags.BULK_DISABLE) { onAction(ChartLibraryUiAction.SetSelectedEnabled(false)) },
            action("−", R.string.action_clear_selection, "chart-library-clear-selection") { onAction(ChartLibraryUiAction.ClearSelection) },
        )
        is ChartLibraryPageUi.SourceDetail -> {
            val basic = listOf(
                action("←", R.string.action_back, "chart-library-back") { onAction(ChartLibraryUiAction.NavigateBack) },
                action(if (page.source.enabled) "○" else "●", if (page.source.enabled) R.string.action_disable else R.string.action_enable, "chart-library-toggle-source") {
                    onAction(ChartLibraryUiAction.SetSourceEnabled(page.source.id, !page.source.enabled))
                },
            )
            if (page.source.kind == ChartLibrarySourceKind.MANAGED) basic else basic + listOf(
                if (page.source.scan.status == ChartScanStatus.RUNNING) {
                    action("×", R.string.action_cancel, "chart-library-cancel-scan") { onAction(ChartLibraryUiAction.CancelSourceScan(page.source.id)) }
                } else action("↻", R.string.action_refresh, "chart-library-refresh") { onAction(ChartLibraryUiAction.RefreshSource(page.source.id)) },
                action("−", R.string.action_remove, "chart-library-remove-source") {
                    onAction(ChartLibraryUiAction.RequestRemoveSource(page.source.id))
                },
            )
        }
        is ChartLibraryPageUi.AssetDetail -> listOfNotNull(
            action("←", R.string.action_back, "chart-library-back") { onAction(ChartLibraryUiAction.NavigateBack) },
            action(if (page.asset.enabled) "○" else "●", if (page.asset.enabled) R.string.action_disable else R.string.action_enable, "chart-library-toggle-asset") {
                onAction(ChartLibraryUiAction.SetAssetEnabled(page.asset.id, !page.asset.enabled))
            },
            if (page.asset.validationJob?.status == ChartValidationJobStatus.RUNNING) {
                action("×", R.string.action_cancel_validation, "chart-library-cancel-validation") {
                    onAction(ChartLibraryUiAction.CancelValidation(page.asset.id))
                }
            } else action("✓", R.string.action_check, "chart-library-check") {
                onAction(ChartLibraryUiAction.InspectBasic(page.asset.id))
            },
            action("◎", R.string.action_full_verify, "chart-library-full-verify") { onAction(ChartLibraryUiAction.VerifyFull(page.asset.id)) },
            if (page.asset.isManagedAsset) action(
                "−", R.string.action_delete_managed_copy, ChartLibraryTestTags.deleteManagedCopy(page.asset.id.value),
            ) { onAction(ChartLibraryUiAction.RequestDeleteManagedCopy(page.asset.id)) } else null,
        )
        is ChartLibraryPageUi.Storage,
        is ChartLibraryPageUi.RemoveSourceConfirmation,
        is ChartLibraryPageUi.DeleteManagedCopyConfirmation,
        is ChartLibraryPageUi.SaveManagedCopyConfirmation,
        -> listOf(action("←", R.string.action_back, "chart-library-back") { onAction(ChartLibraryUiAction.NavigateBack) })
    }
    WpApplicationBar(actions)
}

@Composable
private fun action(symbol: String, label: Int, tag: String, onClick: () -> Unit) = WpAppBarAction(
    symbol = symbol,
    label = stringResource(label),
    testTag = tag,
    onClick = onClick,
)

@Composable
private fun Notice(notice: ChartLibraryNoticeUi, onAction: (ChartLibraryUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    Row(
        Modifier.fillMaxWidth().background(colors.accent).clickable { onAction(ChartLibraryUiAction.DismissNotice) }
            .padding(horizontal = YokuliMetrics.PageMargin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WpText(noticeLabel(notice), 12, color = colors.onAccent, modifier = Modifier.weight(1f))
        WpText("×", 16, color = colors.onAccent)
    }
}

@Composable private fun SectionTitle(value: String) = WpText(value, 23, weight = FontWeight.Light, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
@Composable private fun EmptyResult() = WpText(stringResource(R.string.no_matching_items), 13, color = LocalWpTheme.current.muted, modifier = Modifier.padding(vertical = 12.dp))

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        WpText(label.uppercase(Locale.ROOT), 10, color = LocalWpTheme.current.accent, modifier = Modifier.weight(.38f))
        WpText(value, 14, modifier = Modifier.weight(.62f))
    }
}

@Composable
private fun TextCommand(label: String, tag: String, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch).testTag(tag)
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
    ) { WpText(label, 18, color = colors.accent) }
}

@Composable
private fun InlineCommand(label: String, tag: String, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Box(
        Modifier.heightIn(min = YokuliMetrics.MinTouch).testTag(tag)
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { WpText(label, 16, color = colors.accent) }
}

@Composable private fun pageContext(state: ChartLibraryUiState) = stringResource(when (state.page) {
    is ChartLibraryPageUi.Overview -> when (state.workspaceMode) {
        ChartLibraryWorkspaceMode.COVERAGE -> R.string.context_coverage
        ChartLibraryWorkspaceMode.LAYERS -> R.string.context_layers
        ChartLibraryWorkspaceMode.VIEWS -> R.string.context_views
        ChartLibraryWorkspaceMode.SOURCES -> R.string.context_sources
    }
    is ChartLibraryPageUi.SourceDetail -> R.string.context_source_detail
    is ChartLibraryPageUi.AssetDetail -> R.string.context_asset_detail
    is ChartLibraryPageUi.Storage -> R.string.context_storage
    is ChartLibraryPageUi.RemoveSourceConfirmation -> R.string.context_remove
    is ChartLibraryPageUi.DeleteManagedCopyConfirmation -> R.string.context_delete_copy
    is ChartLibraryPageUi.SaveManagedCopyConfirmation -> R.string.context_save_copy
})

@Composable private fun workspaceLabel(value: ChartLibraryWorkspaceMode) = stringResource(when (value) {
    ChartLibraryWorkspaceMode.COVERAGE -> R.string.workspace_coverage
    ChartLibraryWorkspaceMode.LAYERS -> R.string.workspace_layers
    ChartLibraryWorkspaceMode.VIEWS -> R.string.workspace_views
    ChartLibraryWorkspaceMode.SOURCES -> R.string.workspace_sources
})

@Composable private fun baseStyleLabel(value: ChartBuiltInBaseStyle) = stringResource(when (value) {
    ChartBuiltInBaseStyle.NONE -> R.string.base_none
    ChartBuiltInBaseStyle.STANDARD -> R.string.base_standard
    ChartBuiltInBaseStyle.SATELLITE -> R.string.base_satellite
})

@Composable private fun layerZoomLabel(layer: ChartLibraryLayerUi): String = when {
    layer.minZoom != null && layer.maxZoom != null -> stringResource(R.string.zoom_range, layer.minZoom, layer.maxZoom)
    else -> stringResource(R.string.unknown)
}

@Composable private fun boundsLabel(value: com.yokuli.marine.map.domain.GeoBounds?): String = value?.let {
    stringResource(R.string.bounds_value, it.west, it.south, it.east, it.north)
} ?: stringResource(R.string.unknown)

@Composable private fun filterLabel(value: ChartLibraryFilter) = stringResource(when (value) {
    ChartLibraryFilter.ALL -> R.string.filter_all
    ChartLibraryFilter.NEEDS_ATTENTION -> R.string.filter_attention
    ChartLibraryFilter.ENABLED -> R.string.filter_enabled
    ChartLibraryFilter.DISABLED -> R.string.filter_disabled
})

@Composable private fun sourceSummary(row: ChartLibrarySourceRowUi) = stringResource(
    R.string.source_summary, sourceKindLabel(row.kind), row.assetCount, row.availableAssetCount, row.attentionCount,
)
@Composable private fun assetStatus(row: ChartLibraryAssetRowUi) = stringResource(
    R.string.asset_summary, roleLabel(row.role), accessLabel(row.access), validationLabel(row.validation),
)
@Composable private fun sourceKindLabel(value: ChartLibrarySourceKind) = stringResource(when (value) {
    ChartLibrarySourceKind.TREE -> R.string.source_folder
    ChartLibrarySourceKind.SINGLE_DOCUMENT -> R.string.source_single
    ChartLibrarySourceKind.MANAGED -> R.string.source_managed
})
@Composable private fun scanLabel(value: ChartScanStatus) = stringResource(when (value) {
    ChartScanStatus.NEVER_SCANNED -> R.string.scan_never
    ChartScanStatus.RUNNING -> R.string.scan_running
    ChartScanStatus.COMPLETE -> R.string.scan_complete
    ChartScanStatus.PARTIAL -> R.string.scan_partial
    ChartScanStatus.FAILED -> R.string.scan_failed
    ChartScanStatus.CANCELLED -> R.string.scan_cancelled
})
@Composable private fun grantLabel(value: ChartGrantState) = stringResource(when (value) {
    ChartGrantState.UNCHECKED -> R.string.grant_unchecked
    ChartGrantState.GRANTED -> R.string.grant_granted
    ChartGrantState.DENIED -> R.string.grant_denied
    ChartGrantState.REVOKED -> R.string.grant_revoked
    ChartGrantState.NOT_REQUIRED -> R.string.grant_not_required
})
@Composable private fun accessLabel(value: ChartAssetAccessState) = stringResource(when (value) {
    ChartAssetAccessState.UNCHECKED -> R.string.access_unchecked
    ChartAssetAccessState.READABLE -> R.string.access_readable
    ChartAssetAccessState.PERMISSION_LOST -> R.string.access_permission_lost
    ChartAssetAccessState.SOURCE_OFFLINE -> R.string.access_offline
    ChartAssetAccessState.MISSING -> R.string.access_missing
    ChartAssetAccessState.CHANGED -> R.string.access_changed
    ChartAssetAccessState.PENDING -> R.string.access_pending
    ChartAssetAccessState.DIRECT_READ_UNSUPPORTED -> R.string.access_direct_unsupported
})
@Composable private fun validationLabel(value: ChartAssetValidationState) = stringResource(when (value) {
    ChartAssetValidationState.DISCOVERED -> R.string.validation_discovered
    ChartAssetValidationState.BASIC_READABLE -> R.string.validation_basic
    ChartAssetValidationState.FULL_VERIFIED -> R.string.validation_full
    ChartAssetValidationState.INVALID -> R.string.validation_invalid
    ChartAssetValidationState.UNSUPPORTED_FORMAT -> R.string.validation_unsupported
    ChartAssetValidationState.CANCELLED_OR_INTERRUPTED -> R.string.validation_interrupted
})
@Composable private fun accessModeLabel(value: ChartReadAccessMode) = stringResource(when (value) {
    ChartReadAccessMode.DIRECT_PROVIDER -> R.string.access_mode_direct
    ChartReadAccessMode.LOCAL_FALLBACK -> R.string.access_mode_local_fallback
    ChartReadAccessMode.MANAGED_COPY -> R.string.access_mode_managed
})
@Composable private fun compatibilityWarningLabel(value: ChartCompatibilityWarning) = stringResource(when (value) {
    ChartCompatibilityWarning.METADATA_MISSING -> R.string.warning_metadata_missing
    ChartCompatibilityWarning.BOUNDS_MISSING -> R.string.warning_bounds_missing
    ChartCompatibilityWarning.BOUNDS_INVALID -> R.string.warning_bounds_invalid
    ChartCompatibilityWarning.ZOOM_RANGE_MISSING -> R.string.warning_zoom_missing
    ChartCompatibilityWarning.ZOOM_RANGE_INVALID -> R.string.warning_zoom_invalid
    ChartCompatibilityWarning.FORMAT_MISMATCH -> R.string.warning_format_mismatch
    ChartCompatibilityWarning.MIXED_RASTER_ENCODING -> R.string.warning_mixed_encoding
    ChartCompatibilityWarning.MIXED_TILE_SIZE -> R.string.warning_mixed_size
    ChartCompatibilityWarning.INVALID_SAMPLE_COORDINATE -> R.string.warning_sample_coordinate
})
@Composable private fun roleLabel(value: ChartAssetRole) = stringResource(if (value == ChartAssetRole.BASE) R.string.role_base else R.string.role_overlay)
@Composable private fun formatLabel(value: ChartAssetFormat) = stringResource(when (value) {
    ChartAssetFormat.RASTER_MBTILES -> R.string.format_raster_mbtiles
    ChartAssetFormat.UNKNOWN -> R.string.unknown
})
@Composable private fun schemeLabel(value: MapTileScheme) = stringResource(when (value) {
    MapTileScheme.MBTILES_TMS -> R.string.scheme_tms
    MapTileScheme.XYZ -> R.string.scheme_xyz
})
@Composable private fun provenanceLabel(value: ChartFactProvenance) = stringResource(when (value) {
    ChartFactProvenance.EMBEDDED -> R.string.provenance_embedded
    ChartFactProvenance.DERIVED -> R.string.provenance_derived
    ChartFactProvenance.USER_DECLARED -> R.string.provenance_user
    ChartFactProvenance.UNKNOWN -> R.string.unknown
})
@Composable private fun yesNo(value: Boolean) = stringResource(if (value) R.string.yes else R.string.no)
@Composable private fun bytesLabel(value: Long?): String = value?.let {
    when {
        it >= 1_073_741_824L -> stringResource(R.string.bytes_gib, it / 1_073_741_824.0)
        it >= 1_048_576L -> stringResource(R.string.bytes_mib, it / 1_048_576.0)
        it >= 1024L -> stringResource(R.string.bytes_kib, it / 1024.0)
        else -> stringResource(R.string.bytes_exact, it)
    }
} ?: stringResource(R.string.unknown)
@Composable private fun zoomLabel(asset: ChartLibraryAssetRowUi) = when {
    asset.minZoom != null && asset.maxZoom != null -> "${asset.minZoom}–${asset.maxZoom}"
    else -> stringResource(R.string.unknown)
}
@Composable private fun boundsLabel(asset: ChartLibraryAssetRowUi) = asset.bounds?.let {
    "%.4f, %.4f — %.4f, %.4f".format(Locale.ROOT, it.west, it.south, it.east, it.north)
} ?: stringResource(R.string.unknown)
@Composable private fun validationJobLabel(value: ChartValidationJobStatus) = stringResource(when (value) {
    ChartValidationJobStatus.RUNNING -> R.string.job_validation_running
    ChartValidationJobStatus.COMPLETED -> R.string.job_validation_completed
    ChartValidationJobStatus.FAILED -> R.string.job_validation_failed
    ChartValidationJobStatus.CANCELLED -> R.string.job_validation_cancelled
    ChartValidationJobStatus.INTERRUPTED -> R.string.job_validation_interrupted
})
@Composable
private fun copyJobLabel(
    value: ChartManagedCopyStatus,
    copied: Long,
    total: Long?,
    failure: ChartManagedCopyFailure?,
): String {
    val progress = stringResource(R.string.job_copy_progress, copyStatusLabel(value), bytesLabel(copied), bytesLabel(total))
    val reason = when (failure) {
        ChartManagedCopyFailure.INSUFFICIENT_SPACE -> stringResource(R.string.copy_failure_no_space)
        ChartManagedCopyFailure.ACTIVE_LEASE -> stringResource(R.string.copy_failure_in_use)
        ChartManagedCopyFailure.ASSET_NOT_FOUND -> stringResource(R.string.copy_failure_missing)
        ChartManagedCopyFailure.NOT_AVAILABLE -> stringResource(R.string.copy_failure_unavailable)
        ChartManagedCopyFailure.PERSISTENCE -> stringResource(R.string.copy_failure_storage)
        null -> null
    }
    return if (reason == null) progress else stringResource(R.string.job_copy_failure_detail, progress, reason)
}
@Composable private fun copyStatusLabel(value: ChartManagedCopyStatus) = stringResource(when (value) {
    ChartManagedCopyStatus.QUEUED -> R.string.copy_queued
    ChartManagedCopyStatus.COPYING -> R.string.copy_copying
    ChartManagedCopyStatus.VERIFYING -> R.string.copy_verifying
    ChartManagedCopyStatus.PUBLISHING -> R.string.copy_publishing
    ChartManagedCopyStatus.COMPLETED -> R.string.copy_completed
    ChartManagedCopyStatus.FAILED -> R.string.copy_failed
    ChartManagedCopyStatus.CANCELLED -> R.string.copy_cancelled
    ChartManagedCopyStatus.INTERRUPTED -> R.string.copy_interrupted
})
@Composable private fun noticeLabel(value: ChartLibraryNoticeUi) = stringResource(when (value) {
    ChartLibraryNoticeUi.SOURCE_ADDED -> R.string.notice_source_added
    ChartLibraryNoticeUi.SOURCE_REPAIRED -> R.string.notice_source_repaired
    ChartLibraryNoticeUi.SOURCE_REMOVED -> R.string.notice_source_removed
    ChartLibraryNoticeUi.SOURCE_UPDATED -> R.string.notice_source_updated
    ChartLibraryNoticeUi.ASSET_UPDATED -> R.string.notice_asset_updated
    ChartLibraryNoticeUi.LAYER_UPDATED -> R.string.notice_layer_updated
    ChartLibraryNoticeUi.VIEW_UPDATED -> R.string.notice_view_updated
    ChartLibraryNoticeUi.SCAN_FINISHED -> R.string.notice_scan_finished
    ChartLibraryNoticeUi.SCAN_CANCELLED -> R.string.notice_scan_cancelled
    ChartLibraryNoticeUi.VALIDATION_FINISHED -> R.string.notice_validation_finished
    ChartLibraryNoticeUi.VALIDATION_CANCELLED -> R.string.notice_validation_cancelled
    ChartLibraryNoticeUi.COPY_STARTED -> R.string.notice_copy_started
    ChartLibraryNoticeUi.COPY_CANCELLED -> R.string.notice_copy_cancelled
    ChartLibraryNoticeUi.MANAGED_COPY_DELETED -> R.string.notice_copy_deleted
    ChartLibraryNoticeUi.MANAGED_COPY_IN_USE -> R.string.notice_copy_in_use
    ChartLibraryNoticeUi.MANAGED_COPY_NO_SPACE -> R.string.notice_copy_no_space
    ChartLibraryNoticeUi.PICKER_CANCELLED -> R.string.notice_picker_cancelled
    ChartLibraryNoticeUi.PERMISSION_REQUIRED -> R.string.notice_permission
    ChartLibraryNoticeUi.ITEM_NOT_FOUND -> R.string.notice_missing
    ChartLibraryNoticeUi.CATALOG_CHANGED -> R.string.notice_changed
    ChartLibraryNoticeUi.OPERATION_FAILED -> R.string.notice_failed
    ChartLibraryNoticeUi.ACTION_QUEUE_FULL -> R.string.notice_queue_full
    ChartLibraryNoticeUi.SELECTION_LIMIT_REACHED -> R.string.notice_selection_limit
})

private fun ChartAssetRole.other() = if (this == ChartAssetRole.BASE) ChartAssetRole.OVERLAY else ChartAssetRole.BASE

private val ACTIVE_COPY_STATES = setOf(
    ChartManagedCopyStatus.QUEUED,
    ChartManagedCopyStatus.COPYING,
    ChartManagedCopyStatus.VERIFYING,
    ChartManagedCopyStatus.PUBLISHING,
)
