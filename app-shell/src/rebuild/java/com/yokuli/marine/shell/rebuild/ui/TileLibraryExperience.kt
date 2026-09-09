package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.LauncherAction

/** A catalogue of independently pinnable views, not a second place to control vessel sessions. */
@Composable fun TileLibraryScreen(os: OsStore) {
    val state by os.shell.engine.state.collectAsState()
    val c = LocalMetro.current
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, os.t("磁贴库", "tile library"))
        Pivot(listOf(os.t("实用磁贴", "useful tiles"), os.t("应用磁贴", "app tiles"), os.t("我的开始", "my Start"))) { page ->
            when (page) {
                0 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    item { Label(os.t("把常看的内容放在开始屏幕", "keep useful information on Start"), 28, c.accent); Spacer(Modifier.height(9.dp)); Label(os.t("这些磁贴可以并存。预览使用已有数据；没有读数时保留缺测状态。", "These tiles can sit together. Previews use existing data and show when readings are missing."), 18, c.muted) }
                    items(tilePresets(), key = { it.key }) { preset ->
                        val visual = presetTilePresentation(os, preset, animate = false)
                        val placement = state.start.document.placements.firstOrNull { it.entryId == preset.entryId }
                        var selectedSizeName by rememberSaveable(preset.entryId.value) { mutableStateOf((placement?.size ?: preset.defaultSize).name) }
                        val selectedSize = preset.sizes.firstOrNull { it.name == selectedSizeName } ?: preset.defaultSize
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Label(visual.title, 27)
                            Label(if (os.chinese) preset.description.chinese else preset.description.english, 16, c.muted)
                            TilePreview(os, visual, preset.sizes, selectedSize, placement != null) { selectedSizeName = it.name }
                            MetroButton(if (placement == null) os.t("固定到开始 · ${tileSizeName(os, selectedSize)}", "pin to Start · ${tileSizeName(os, selectedSize)}") else os.t("已固定 · 查看开始", "pinned · view Start"), { if (placement == null) os.shell.engine.dispatch(LauncherAction.PinEntry(preset.entryId, selectedSize)) else os.home() }, primary = placement == null)
                        }
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    item { Label(os.t("每个应用都有自己的动态磁贴", "a live tile for every app"), 28, c.accent); Spacer(Modifier.height(9.dp)); Label(os.t("轮换显示可用信息。减少动画或离开开始屏幕后停止轮换；点按磁贴进入应用。", "Tiles rotate available information. Rotation stops with reduced motion or when Start is away. Tap a tile to open its app."), 18, c.muted) }
                    items(os.shell.apps, key = { it.id.value }) { app ->
                        val visual = tilePresentation(os, app, animate = false)
                        val placement = state.start.document.placements.firstOrNull { it.entryId == app.entry }
                        val pinned = placement != null
                        var selectedSizeName by rememberSaveable(app.entry.value) { mutableStateOf((placement?.size ?: app.defaultSize).name) }
                        val selectedSize = app.sizes.firstOrNull { it.name == selectedSizeName } ?: app.defaultSize
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Label(os.title(app.app), 27)
                            TilePreview(os, visual, app.sizes, selectedSize, pinned) { selectedSizeName = it.name }
                            MetroButton(if (pinned) os.t("已固定 · 查看开始", "pinned · view Start") else os.t("固定应用磁贴 · ${tileSizeName(os, selectedSize)}", "pin app tile · ${tileSizeName(os, selectedSize)}"), { if (pinned) os.home() else os.shell.engine.dispatch(LauncherAction.PinEntry(app.entry, selectedSize)) }, primary = !pinned)
                            MenuRow(os.t("定制此应用的磁贴", "customise this app’s tiles"), os.t("内容、轮换与间隔", "content, rotation & interval")) { os.open("settings:tiles:${app.id.value}") }
                        }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item { Label(os.t("${state.start.document.placements.size} 块磁贴", "${state.start.document.placements.size} tiles"), 42, c.accent) }
                    item { Label(os.t("在开始屏幕长按可以移动、改变尺寸或取消固定。这里取消固定不会删除应用或数据。", "Hold tiles on Start to move, resize or unpin them. Unpinning here keeps the app and its data."), 19, c.muted) }
                    items(state.start.document.placements, key = { it.tileId.value }) { placement ->
                        val preset = tilePresets().firstOrNull { it.entryId == placement.entryId }
                        val app = os.shell.apps.firstOrNull { it.entry == placement.entryId }
                        val title = preset?.title?.let { if (os.chinese) it.chinese else it.english } ?: app?.let { os.title(it.app) } ?: placement.entryId.value
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label(title, 25)
                            Label(tileSizeName(os, placement.size), 15, c.muted)
                            MetroButton(os.t("取消固定", "unpin"), { os.shell.engine.dispatch(LauncherAction.UnpinTile(placement.tileId)) })
                        }
                    }
                    item { MetroButton(os.t("打开开始屏幕", "open Start"), os::home, primary = true) }
                }
            }
        }
    }
}

@Composable private fun TilePreview(os: OsStore, visual: LauncherEntryVisualContribution, sizes: List<MarineTileSize>, size: MarineTileSize, pinned: Boolean, chooseSize: (MarineTileSize) -> Unit) {
    val c = LocalMetro.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            val shape = when (size) { MarineTileSize.ICON_1X1 -> Modifier.size(72.dp); MarineTileSize.STANDARD_2X2 -> Modifier.size(150.dp); MarineTileSize.WIDE_4X2 -> Modifier.fillMaxWidth().height(150.dp) }
            Box(shape.background(c.accent).border(1.dp, c.accent).padding(if (visual.fullBleed && size != MarineTileSize.ICON_1X1) 0.dp else if (size == MarineTileSize.ICON_1X1) YokuliMetrics.TileSmallContentInset else YokuliMetrics.TileContentInset)) {
                visual.tileRenderers.getValue(size).Render(LauncherTileRenderContext(size, Color.White, Modifier.fillMaxSize(), liveContentEnabled = true))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { sizes.forEach { option -> MetroButton(tileSizeName(os, option), { chooseSize(option) }, Modifier.weight(1f), primary = size == option) } }
        Label(if (pinned) os.t("预览尺寸 · 已固定磁贴可在开始屏幕长按更改", "preview size · hold the existing tile on Start to resize") else os.t("按所选尺寸固定 · 之后仍可长按更改", "pins at this size · hold the tile later to resize"), 13, c.muted)
    }
}
private fun tileSizeName(os: OsStore, size: MarineTileSize): String = when (size) { MarineTileSize.ICON_1X1 -> os.t("小", "small"); MarineTileSize.STANDARD_2X2 -> os.t("中", "medium"); MarineTileSize.WIDE_4X2 -> os.t("宽", "wide") }
