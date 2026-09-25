package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.TileDocumentEntry

/** 工坊只编辑用户关心的内容；位置与拖动始终由真实开始屏幕拥有。 */
@Composable fun TileLibraryScreen(os: OsStore, initialApp: String? = null) {
    val workshop = os.shell.tileWorkshop
    val state by os.shell.engine.state.collectAsState()
    val session by workshop.session.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable(initialApp) { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var ownerFilter by rememberSaveable(initialApp) { mutableStateOf(initialApp) }
    val addScroll = rememberLazyListState()
    val pinnedScroll = rememberLazyListState()
    val choices = tileContentChoices(os)
    val pinnedKeys = remember(state.start.document) { state.start.document.placements.map { tileBinding(it).contentKey }.toSet() }
    ReportVisibleAppRoute(os, "tiles")
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, os.title(AppId.TILES), trailing = {
            IconAction("settings", "", { os.openLinked("settings:start") }, Modifier.size(48.dp).semantics { contentDescription = os.t("背景与透明磁贴", "Background and transparent tiles") })
        })
        if (session?.visible == false) {
            val insets = LocalShellHorizontalInsets.current
            MenuRowInPadding(os.t("继续未完成的磁贴", "Continue your tile"),
                if (session?.phase == TileEditorPhase.SAVING) os.t("保存仍在进行中", "Saving is still in progress") else os.t("继续查看草稿与保存状态", "Continue your draft and check its save state"), "start", insets) { workshop.resume() }
        }
        Pivot(listOf(os.t("添加", "Add"), os.t("已固定", "Pinned")), page, { page = it }) { index ->
            if (index == 0) TileContentPicker(os, choices, pinnedKeys, query, { query = it }, group, { group = it },
                ownerFilter, { ownerFilter = null }, addScroll, onChoose = { workshop.beginAdd(it.binding) })
            else {
                val columns = remember(state.start.document.profileId) { WpReferenceProfiles.require(state.start.document.profileId).columnCount }
                val packed = remember(state.start.document, columns) { AdaptiveTilePacker.pack(state.start.document, columns) }
                val ordered = remember(packed) { packed.tiles.sortedWith(compareBy({ it.cell.row }, { it.cell.column }, { it.entry.rank }, { it.entry.tileId.value })) }
                val insets = LocalShellHorizontalInsets.current
                LazyColumn(Modifier.fillMaxSize().padding(start = insets.pageStart, end = insets.pageEnd), state = pinnedScroll,
                    contentPadding = PaddingValues(bottom = 28.dp)) {
                    if (ordered.isEmpty()) item {
                        Label(os.t("开始屏幕还没有磁贴", "No tiles on Start yet"), 20, modifier = Modifier.padding(top = 24.dp))
                        Label(os.t("切换到“添加”，安排你常看的内容。", "Choose Add to keep the content you care about on Start."), 15,
                            LocalMetro.current.muted, Modifier.padding(top = 8.dp))
                    }
                    items(ordered, key = { it.entry.tileId.value }) { item ->
                        PinnedTileRow(os, item.entry, item.cell.row, item.cell.column)
                    }
                    if (ordered.isNotEmpty()) item {
                        Label(os.t("排列与拖动在开始屏幕完成。移除磁贴不会停止任务或删除内容。", "Arrange tiles on Start. Removing a tile keeps its task and content."),
                            13, LocalMetro.current.muted, Modifier.padding(top = 20.dp))
                    }
                }
            }
        }
    }
}

/** 添加与编辑器的“更换内容”共用同一目录；只显示静态身份，不运行缩略磁贴订阅。 */
@Composable internal fun TileContentPicker(
    os: OsStore,
    choices: List<TileContentChoice>,
    pinnedKeys: Set<String>,
    query: String,
    onQuery: (String) -> Unit,
    group: String?,
    onGroup: (String?) -> Unit,
    ownerFilter: String? = null,
    clearOwner: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    selectedKey: String? = null,
    onChoose: (TileContentChoice) -> Unit,
) {
    val insets = LocalShellHorizontalInsets.current
    val c = LocalMetro.current
    val needle = query.trim()
    val filtered = choices.filter { choice ->
        val owner = ShellApp(choice.owner)
        (ownerFilter == null || owner.id.value == ownerFilter || owner.page == ownerFilter) &&
            (group == null || choice.group.name == group) &&
            (needle.isEmpty() || listOf(choice.title.chinese, choice.title.english, choice.subtitle.chinese,
                choice.subtitle.english, os.title(choice.owner), choice.owner.name).any { it.contains(needle, ignoreCase = true) })
    }
    LazyColumn(Modifier.fillMaxSize().padding(start = insets.pageStart, end = insets.pageEnd), state = listState,
        contentPadding = PaddingValues(bottom = 28.dp)) {
        item("search") {
            Field(os.t("查找内容", "Find content"), query, { onQuery(it.take(100)) })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TileGroupChoice(os.t("全部", "All"), group == null) { onGroup(null) }
                TileContentGroup.entries.forEach { item -> TileGroupChoice(tileGroupTitle(os, item), group == item.name) { onGroup(item.name) } }
            }
            if (ownerFilter != null) MenuRow(os.t("显示所有应用的内容", "Show content from all apps"), icon = "close") { clearOwner() }
        }
        TileContentGroup.entries.forEach { category ->
            val members = filtered.filter { it.group == category }
            if (members.isNotEmpty()) {
                item("group:${category.name}") {
                    Label(tileGroupTitle(os, category), 15, c.accentText,
                        Modifier.padding(top = 20.dp, bottom = 4.dp).semantics { heading() })
                }
                items(members, key = { it.binding.contentKey }) { choice ->
                    TileContentRow(os, choice, choice.binding.contentKey in pinnedKeys,
                        selected = choice.binding.contentKey == selectedKey, choosing = selectedKey != null) { onChoose(choice) }
                }
            }
        }
        if (filtered.isEmpty()) item("empty") {
            Label(if (group == TileContentGroup.SAVED.name && needle.isEmpty()) os.t("收藏之后，可以把它留在这里", "Keep your saved places close")
                else os.t("没有找到相关内容", "No matching content"), 20, modifier = Modifier.padding(top = 28.dp))
            Label(if (group == TileContentGroup.SAVED.name && needle.isEmpty())
                os.t("在“我的航行”保存地点或航线后，就可以固定到开始屏幕。", "Save a place or route in My Sailing, then pin it to Start.")
                else os.t("试试内容名称，或清除分类筛选。", "Try a content name or clear the category filter."),
                15, c.muted, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable private fun TileContentRow(os: OsStore, choice: TileContentChoice, pinned: Boolean,
    selected: Boolean = false, choosing: Boolean = false, onClick: () -> Unit) {
    val c = LocalMetro.current
    Row(Modifier.fillMaxWidth().then(if (choosing) Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        else Modifier.clickable(role = Role.Button, onClick = onClick)).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        TileIdentityIcon(choice, Modifier.size(44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(tileText(os, choice.title), 18)
            Label(tileText(os, choice.subtitle), 13, c.muted, maxLines = 2)
            Label(when { selected -> os.t("当前内容", "Current content"); pinned -> os.t("已在开始屏幕", "On Start")
                else -> os.t("来自 ", "From ") + os.title(choice.owner) },
                12, if (selected || pinned) c.accentText else c.muted)
        }
        Glyph(if (selected || pinned) "check" else "chevron_right", Modifier.size(18.dp), if (selected || pinned) c.accentText else c.muted)
    }
}

@Composable private fun PinnedTileRow(os: OsStore, placement: TileDocumentEntry, row: Int, column: Int) {
    val choice = tileContentDescriptor(os, tileBinding(placement))
    val workshop = os.shell.tileWorkshop
    val c = LocalMetro.current
    var menu by rememberSaveable(placement.tileId.value) { mutableStateOf(false) }
    val legacy = placement.presentation.legacyMode?.let { mode ->
        tileModes(ShellApp(choice.owner)).firstOrNull { it.key == mode }?.let { tileText(os, it.title) }
    }
    val style = legacy?.let { it + os.t(" · 原有样式", " · Original") }
        ?: choice.styles.firstOrNull { it.key == placement.presentation.style }?.let { tileText(os, it.title) }
        ?: os.t("保留的原有样式", "Original appearance")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(role = Role.Button) { workshop.beginEdit(placement.tileId) }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TileIdentityIcon(choice, Modifier.size(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(tileText(os, choice.title), 18)
                Label(tileSizeName(os, placement.size) + " · " + style, 13, c.muted)
            }
        }
        IconAction("more", os.t("更多", "More"), { menu = true }, Modifier.width(48.dp))
    }
    if (menu) AppDialog(onDismissRequest = { menu = false }) { AppDialogSurface {
        AppDialogTitle(tileText(os, choice.title))
        Label(os.t("开始屏幕第 ${row + 1} 行，第 ${column + 1} 列", "Start row ${row + 1}, column ${column + 1}"), 13, c.muted)
        MenuRow(os.t("编辑这块磁贴", "Edit this tile"), icon = "start") { menu = false; workshop.beginEdit(placement.tileId) }
        MenuRow(os.t("查看桌面位置", "Show on Start"), icon = "locate") { menu = false; workshop.reveal(placement.tileId) }
        MenuRow(os.t("从开始屏幕移除", "Unpin from Start"), icon = "minus") { menu = false; workshop.unpin(placement.tileId) }
        MetroButton(os.t("关闭", "Close"), { menu = false })
    } }
}

@Composable internal fun TileIdentityIcon(choice: TileContentChoice, modifier: Modifier = Modifier) {
    val c = LocalMetro.current
    Box(modifier.background(c.panel), contentAlignment = Alignment.Center) {
        when (choice.binding.kind) {
            TileBindingKind.READING -> Canvas(Modifier.size(28.dp)) {
                val u = size.width / 32f
                fun p(x: Float, y: Float) = Offset(x * u, y * u)
                fun line(x: Float, y: Float, endX: Float, endY: Float) = drawLine(c.fg, p(x, y), p(endX, endY), 1.8f * u, StrokeCap.Square)
                when (choice.binding.contentId) {
                    "HEADING_TRUE" -> {
                        drawCircle(c.fg, 12 * u, style = Stroke(1.5f * u))
                        val needle = Path().apply { moveTo(16*u,5*u);lineTo(10*u,24*u);lineTo(16*u,20*u);lineTo(22*u,24*u);close() }
                        drawPath(needle, c.fg, style = Stroke(1.6f*u))
                    }
                    "DEPTH" -> { line(4f,5f,28f,5f);line(16f,7f,16f,27f);line(11f,22f,16f,27f);line(16f,27f,21f,22f);line(4f,29f,28f,29f) }
                    "SOG" -> { drawArc(c.fg,180f,180f,false,topLeft=p(4f,7f),size=androidx.compose.ui.geometry.Size(24*u,24*u),style=Stroke(1.8f*u));line(16f,19f,24f,11f);line(6f,26f,26f,26f) }
                    "PRESSURE" -> { drawCircle(c.fg,12*u,style=Stroke(1.6f*u));line(16f,16f,21f,9f);line(7f,16f,9f,16f);line(23f,16f,25f,16f);line(16f,7f,16f,9f) }
                    else -> { line(4f,10f,25f,10f);line(25f,10f,21f,6f);line(4f,18f,29f,18f);line(29f,18f,25f,14f);line(4f,26f,21f,26f);line(21f,26f,17f,22f) }
                }
            }
            TileBindingKind.SAVED_PLACE -> Glyph("pin", Modifier.size(26.dp), c.fg)
            TileBindingKind.SAVED_ROUTE -> Glyph("route", Modifier.size(26.dp), c.fg)
            else -> ShellAppIcon(ShellApp(choice.owner), c.fg, Modifier.size(28.dp))
        }
    }
}

@Composable private fun TileGroupChoice(title: String, selected: Boolean, onClick: () -> Unit) {
    Label(title, 15, if (selected) LocalMetro.current.accentText else LocalMetro.current.muted,
        Modifier.heightIn(min = 44.dp).selectable(selected = selected, role = Role.Tab, onClick = onClick).padding(vertical = 12.dp))
}

@Composable private fun MenuRowInPadding(title: String, subtitle: String, icon: String, insets: ShellHorizontalInsets, action: () -> Unit) {
    Box(Modifier.padding(start = insets.pageStart, end = insets.pageEnd)) { MenuRow(title, subtitle, icon, action) }
}

internal fun tileText(os: OsStore, text: AppPreferenceLabel): String = if (os.chinese) text.chinese else text.english
internal fun tileSizeName(os: OsStore, size: MarineTileSize): String = when (size) {
    MarineTileSize.ICON_1X1 -> os.t("小", "Small")
    MarineTileSize.STANDARD_2X2 -> os.t("中", "Medium")
    MarineTileSize.WIDE_4X2 -> os.t("宽", "Wide")
}
private fun tileGroupTitle(os: OsStore, group: TileContentGroup): String = when (group) {
    TileContentGroup.WATCH -> os.t("航行与值守", "On board")
    TileContentGroup.READINGS -> os.t("常看读数", "Readings")
    TileContentGroup.SAVED -> os.t("我的收藏", "Saved")
    TileContentGroup.APPS -> os.t("应用入口", "Apps")
}
