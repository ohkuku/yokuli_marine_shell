package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.compose.LauncherEntryVisualContribution
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.LauncherAction
import kotlinx.coroutines.launch

/** 中文：一个应用只有一块主磁贴；内容样式、尺寸和轮换是这块磁贴的偏好，不再制造重复入口。 */
@Composable fun TileLibraryScreen(os: OsStore, initialApp: String? = null) {
    val state by os.shell.engine.state.collectAsState()
    val preferences by os.shell.persistence.state.collectAsState()
    val c = LocalMetro.current
    var owner by rememberSaveable(initialApp) { mutableStateOf(initialApp) }
    val selectedApp = os.shell.apps.firstOrNull { it.id.value == owner }
    val back = { if(initialApp!=null) os.shell.popRoute() else owner = null }
    BindInternalAppInputHandler { input -> if (input == ShellInput.BACK && owner != null) { back(); true } else false }
    AppBackHandler(owner != null) { back() }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, selectedApp?.let { os.title(it.app) } ?: os.t("磁贴库", "tile library"),
            app = if (selectedApp == null) "YOKULI OS" else os.t("磁贴库", "TILE LIBRARY"),
            onBack = if (selectedApp != null) back else null)
        AnimatedContent(selectedApp?.id?.value, transitionSpec = {
            (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(200))) togetherWith
                (slideOutHorizontally(tween(220)) { -it / 7 } + fadeOut(tween(170)))
        }, label = "tile-library-page") { selected ->
            val app = os.shell.apps.firstOrNull { it.id.value == selected }
            if (app == null) {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                    item { Label(os.t("让每个应用展示你关心的内容", "choose what each app shows"), 25, c.muted, Modifier.padding(bottom = 20.dp)) }
                    items(os.shell.apps, key = { it.id.value }) { entry ->
                        val placement = state.start.document.placements.firstOrNull { p -> state.catalog.entries.firstOrNull { it.entryId == p.entryId }?.appId == entry.id }
                        val modeKey = preferences?.appPreferenceValues?.get("${entry.id.value}.tile.mode")?.removePrefix("c:") ?: "AUTO"
                        val mode = tileModes(entry).firstOrNull { it.key == modeKey }
                        Row(Modifier.fillMaxWidth().clickable { owner = entry.id.value }.padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Box(Modifier.size(54.dp).background(if (placement != null) c.accent else c.panel), contentAlignment = Alignment.Center) {
                                ShellAppIcon(entry, if (placement != null) Color.White else c.fg, Modifier.size(32.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Label(os.title(entry.app), 27)
                                Label(if (placement == null) os.t("未固定 · 选择样式", "not pinned · choose a style") else (mode?.title?.let { if (os.chinese) it.chinese else it.english } ?: "") + " · " + tileSizeName(os, placement.size), 15, c.muted)
                            }
                            Glyph("next", Modifier.size(18.dp), c.muted)
                        }
                    }
                    item { Label(os.t("每个应用一块磁贴。在应用内选择样式；长按桌面磁贴可以移动。", "One tile per app. Choose its style here; hold a tile on Start to move it."), 16, c.muted, Modifier.padding(top = 24.dp)) }
                }
            } else {
                val placement = state.start.document.placements.firstOrNull { p -> state.catalog.entries.firstOrNull { it.entryId == p.entryId }?.appId == app.id }
                val modes = remember(app.id) { tileModes(app) }
                val savedMode = preferences?.appPreferenceValues?.get("${app.id.value}.tile.mode")?.removePrefix("c:") ?: "AUTO"
                var chosenSize by rememberSaveable(app.id.value) { mutableStateOf((placement?.size ?: app.defaultSize).name) }
                val size = app.sizes.firstOrNull { it.name == chosenSize } ?: app.defaultSize
                val pager = rememberPagerState(initialPage = modes.indexOfFirst { it.key == savedMode }.coerceAtLeast(0), pageCount = { modes.size })
                val scope = rememberCoroutineScope()
                val mode = modes[pager.currentPage]
                var rotate by rememberSaveable(app.id.value) { mutableStateOf(preferences?.appPreferenceValues?.get("${app.id.value}.tile.animate") != "b:0") }
                var interval by rememberSaveable(app.id.value) { mutableStateOf(preferences?.appPreferenceValues?.get("${app.id.value}.tile.interval")?.removePrefix("c:") ?: "6") }
                PageBody {
                    HorizontalPager(pager, Modifier.fillMaxWidth().height(200.dp), beyondViewportPageCount = 1) { index ->
                        val visual = tilePresentation(os, app, animate = true, modeOverride = modes[index].key)
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TilePreview(visual, size) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        modes.indices.forEach { index ->
                            Box(Modifier.padding(horizontal = 4.dp).size(if (index == pager.currentPage) 8.dp else 5.dp).background(if (index == pager.currentPage) c.fg else c.muted.copy(alpha = .5f)))
                        }
                    }
                    Label(if (os.chinese) mode.title.chinese else mode.title.english, 30)
                    Label(os.t("左右滑动看样式 · 预览使用当前真实数据", "swipe through styles · previews use current data"), 15, c.muted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        app.sizes.forEach { option ->
                            TileChoice(tileSizeName(os, option), size == option, Modifier.weight(1f)) { chosenSize = option.name }
                        }
                    }
                    modes.forEachIndexed { index, option ->
                        TileChoice(if (os.chinese) option.title.chinese else option.title.english, pager.currentPage == index) { scope.launch { pager.animateScrollToPage(index) } }
                    }
                    if (mode.key == "AUTO") {
                        Toggle(os.t("动态轮换", "rotate live content"), rotate) { rotate = it }
                        if (rotate) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            listOf("6", "10", "15").forEach { seconds -> TileChoice(os.t("$seconds 秒", "$seconds s"), interval == seconds, Modifier.weight(1f)) { interval = seconds } }
                        }
                    }
                    MetroButton(if (placement == null) os.t("固定到开始屏幕", "pin to Start") else os.t("应用到现有磁贴", "apply to existing tile"), {
                        os.shell.updateSystemPreferences { current -> current.copy(appPreferenceValues = current.appPreferenceValues + mapOf(
                            "${app.id.value}.tile.mode" to AppPreferenceRegistry.encode(AppPreferenceValue.Choice(mode.key)),
                            "${app.id.value}.tile.animate" to AppPreferenceRegistry.encode(AppPreferenceValue.Toggle(rotate)),
                            "${app.id.value}.tile.interval" to AppPreferenceRegistry.encode(AppPreferenceValue.Choice(interval)),
                        )) }
                        os.shell.dispatch(LauncherAction.PinEntry(app.entry, size))
                    }, primary = true)
                    if (placement != null) MetroButton(os.t("从开始屏幕移除", "unpin from Start"), { os.shell.engine.dispatch(LauncherAction.UnpinTile(placement.tileId)) })
                }
            }
        }
    }
}

@Composable private fun TileChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, choose: () -> Unit) {
    val c = LocalMetro.current
    Row(modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = choose).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.size(24.dp).border(2.dp, c.fg).padding(5.dp)) { if (selected) Box(Modifier.fillMaxSize().background(c.accent)) }
        Label(label, 21, if (selected) c.fg else c.muted)
    }
}

/** 中文：按照开始屏幕 1/2/4 列与固定间距计算预览比例，宽磁贴不随容器被横向拉伸。 */
@Composable private fun TilePreview(visual: LauncherEntryVisualContribution, size: MarineTileSize) {
    val c = LocalMetro.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val wide = maxWidth.coerceAtMost(312.dp)
        val medium = (wide - 12.dp) / 2
        val small = (medium - 12.dp) / 2
        val shape = when (size) { MarineTileSize.ICON_1X1 -> Modifier.size(small); MarineTileSize.STANDARD_2X2 -> Modifier.size(medium); MarineTileSize.WIDE_4X2 -> Modifier.width(wide).height(medium) }
        Box(shape.background(c.accent).padding(if (visual.fullBleed && size != MarineTileSize.ICON_1X1) 0.dp else if (size == MarineTileSize.ICON_1X1) YokuliMetrics.TileSmallContentInset else YokuliMetrics.TileContentInset)) {
            visual.tileRenderers.getValue(size).Render(LauncherTileRenderContext(size, Color.White, Modifier.fillMaxSize(), liveContentEnabled = true))
        }
    }
}
private fun tileSizeName(os: OsStore, size: MarineTileSize): String = when (size) { MarineTileSize.ICON_1X1 -> os.t("小", "small"); MarineTileSize.STANDARD_2X2 -> os.t("中", "medium"); MarineTileSize.WIDE_4X2 -> os.t("宽", "wide") }
