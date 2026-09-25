package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.core.design.*
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.compose.LauncherTileRenderContext
import com.yokuli.shell.compose.LocalInternalAppInputEnabled
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.geometry.StartViewport
import com.yokuli.shell.engine.geometry.WpStartGeometryCalculator
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.TileDocumentEntry
import com.yokuli.shell.engine.layout.TileLayoutPreview
import kotlin.math.roundToInt

/** 三个入口共享的 Shell 临时编辑表面；不创建工坊应用任务，也不修改调用者页面。 */
@Composable fun TileEditorHost(os: OsStore, modifier: Modifier = Modifier) {
    val workshop = os.shell.tileWorkshop
    val session by workshop.session.collectAsState()
    val visits = rememberSaveableStateHolder()
    var retainedKey by remember { mutableStateOf<String?>(null) }
    val key = session?.tileId?.value
    LaunchedEffect(key) {
        retainedKey?.takeIf { it != key }?.let(visits::removeState)
        retainedKey = key
    }
    val edit = session?.takeIf { it.visible } ?: return
    // Home/最近任务暂时卸载编辑界面，但同一草稿的筛选和两个滚动位置仍属于这次访问。
    visits.SaveableStateProvider(edit.tileId.value) { TileEditorContent(os, edit, modifier) }
}

@Composable private fun TileEditorContent(os: OsStore, edit: TileEditorSession, modifier: Modifier) {
    val workshop = os.shell.tileWorkshop
    val state by os.shell.engine.state.collectAsState()
    val c = LocalMetro.current
    val selecting = edit.page == TileEditorPage.CHOOSE_CONTENT
    var query by rememberSaveable(edit.tileId.value) { mutableStateOf("") }
    var group by rememberSaveable(edit.tileId.value) { mutableStateOf<String?>(null) }
    var previewedContent by rememberSaveable { mutableStateOf(edit.binding.contentKey) }
    val pickerScroll = rememberLazyListState()
    val editingScroll = rememberScrollState()
    val choice = tileContentDescriptor(os, edit.binding)
    val busy = edit.phase == TileEditorPhase.SAVING
    val inputEnabled = LocalInternalAppInputEnabled.current
    val originalExists = state.start.document.placements.any { it.tileId == edit.tileId }
    val canConfigure = !busy && inputEnabled && edit.phase != TileEditorPhase.CONFLICT
    val focusManager = LocalFocusManager.current
    LaunchedEffect(inputEnabled) { if (!inputEnabled) focusManager.clearFocus(force = true) }
    // 与虚拟 Back 的 Shell fallback 共用页层级，不能只有标题箭头知道内容选择页。
    val back = workshop::requestClose
    AppBackHandler(inputEnabled) { back() }
    LaunchedEffect(edit.binding.contentKey) {
        if (previewedContent != edit.binding.contentKey) {
            editingScroll.scrollTo(0)
            previewedContent = edit.binding.contentKey
        }
    }
    Column(modifier.fillMaxSize().background(c.bg).imePadding()
        .then(if (inputEnabled) Modifier else Modifier.clearAndSetSemantics { })
        .pointerInput(inputEnabled) {
            awaitPointerEventScope {
                while (true) {
                    // 整面参与命中以隔离下层。正常时不消费，避免取消子级滚动/点击；被覆盖时在 Initial 截断。
                    val event = awaitPointerEvent(if (inputEnabled) PointerEventPass.Final else PointerEventPass.Initial)
                    if (!inputEnabled) event.changes.forEach { it.consume() }
                }
            }
        }) {
        TileEditorHeader(os, if (selecting) os.t("更换内容", "Choose content") else tileText(os, choice.title),
            if (selecting) os.t("磁贴内容", "Tile content") else if (edit.isNew) os.t("固定到开始屏幕", "Pin to Start") else os.t("编辑磁贴", "Edit tile"), inputEnabled, back)
        if (selecting) {
            TileContentPicker(os, tileContentChoices(os), state.start.document.placements.map { tileBinding(it).contentKey }.toSet(),
                query, { query = it }, group, { group = it }, listState = pickerScroll,
                selectedKey = edit.binding.contentKey) { selected ->
                workshop.setBinding(selected.binding)
            }
        } else {
            val insets = LocalShellHorizontalInsets.current
            Column(Modifier.weight(1f).verticalScroll(editingScroll).padding(start = insets.pageStart, end = insets.pageEnd, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val draft = TileDocumentEntry(edit.tileId, edit.binding.startEntryId, edit.size, 0,
                    binding = edit.binding, presentation = edit.presentation)
                val preview = if (edit.phase == TileEditorPhase.ALREADY_PINNED)
                    state.start.document.placements.firstOrNull { it.tileId == edit.existingTileId } ?: draft else draft
                TileEditorPreview(os, preview, active = !busy)
                Label(tileText(os, choice.subtitle), 13, c.muted)
                if (edit.phase == TileEditorPhase.ALREADY_PINNED) {
                    AppSection(os.t("已在开始屏幕", "Already on Start"),
                        os.t("这项内容只需要一块磁贴。现有样式不会被覆盖。", "This content already has a tile. Its appearance is unchanged."))
                    MetroButton(os.t("编辑这块磁贴", "Edit existing tile"), { workshop.editExisting() }, primary = true)
                    edit.existingTileId?.let { id -> MetroButton(os.t("查看位置", "Show on Start"), { workshop.reveal(id) }) }
                    MenuRow(os.t("更换内容", "Choose other content"), icon = "start") { workshop.chooseContent() }
                    MetroButton(os.t("返回", "Back"), { workshop.requestClose() })
                } else {
                    Label(os.t("尺寸", "Size"), 15, c.muted, Modifier.semantics { heading() })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        choice.sizes.forEach { size ->
                            ChoiceRow(tileSizeName(os, size), edit.size == size, enabled = canConfigure, modifier = Modifier.weight(1f)) { workshop.setSize(size) }
                        }
                    }
                    val iconOnly = edit.size == MarineTileSize.ICON_1X1
                    if (iconOnly) {
                        Label(os.t("小磁贴显示应用图标；放大后显示你选定的内容样式。", "Small tiles show the app icon. A larger tile uses your selected appearance."), 13, c.muted)
                    } else if (choice.styles.size > 1) {
                        Column {
                            Label(os.t("显示方式", "Appearance"), 15, c.muted, Modifier.semantics { heading() })
                            choice.styles.forEach { style ->
                                ChoiceRow(tileText(os, style.title), edit.presentation.style == style.key, enabled = canConfigure) {
                                    workshop.chooseStyle(style.key)
                                }
                            }
                        }
                    }
                    if (!iconOnly && canConfigure) {
                        TileMetricConfiguration(os, choice, edit.presentation, workshop::setPresentation)
                    }
                    if (edit.presentation.legacyMode != null) {
                        Label(os.t("保留原有内容、轮换和点击去向。选择新表现或更换内容后才转换。", "Original content, rotation and destination are preserved until you choose a new appearance or content."), 13, c.muted)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Label(os.t("点按后", "Opens"), 13, c.muted)
                        Label(tileText(os, choice.destinationLabel), 15)
                    }
                    if (edit.existingTileId != null) {
                        AppSection(os.t("这个内容已有磁贴", "This content is already pinned"),
                            os.t("当前草稿仍保留；可以编辑已有磁贴，或换一个内容。", "Your draft is kept. Edit the existing tile or choose other content."))
                        MetroButton(os.t("编辑已有磁贴", "Edit existing tile"), { workshop.editExisting() }, enabled = !busy)
                        MetroButton(os.t("查看已有位置", "Show existing tile"), { workshop.reveal(edit.existingTileId!!) }, enabled = !busy)
                    }
                    if (!edit.errorText.isNullOrBlank()) Label(edit.errorText!!, 15, c.fg,
                        Modifier.fillMaxWidth().background(c.panel).padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    if (edit.phase == TileEditorPhase.CONFLICT) {
                        if (originalExists) {
                            Label(os.t("这块磁贴已在别处改变。重新载入会替换当前草稿；其他磁贴不会受影响。", "This tile changed elsewhere. Reloading replaces this draft and keeps other tiles unchanged."), 13, c.muted)
                            MetroButton(os.t("载入最新磁贴", "Reload latest tile"), { workshop.reloadConflict() }, enabled = inputEnabled)
                        } else {
                            Label(os.t("原磁贴已移除。草稿仍保留；重新选择内容可创建一块新磁贴。", "The original tile was removed. Your draft is kept; choose content to create a new tile."), 13, c.muted)
                            MetroButton(os.t("选择内容，重新固定", "Choose content to pin again"), { workshop.chooseContent() }, enabled = inputEnabled)
                        }
                    }
                    if (busy) {
                        MetroProgress(os.t("正在保存磁贴…", "Saving your tile…"))
                        Label(os.t("可以返回或按 Home 离开，保存会继续。", "You can leave with Back or Home; saving will continue."), 13, c.muted)
                    } else {
                        MetroButton(when {
                            edit.phase == TileEditorPhase.FAILED -> os.t("重试保存", "Retry save")
                            edit.isNew -> os.t("固定到开始屏幕", "Pin to Start")
                            else -> os.t("保存", "Save")
                        }, { workshop.save() }, primary = true,
                            enabled = inputEnabled && (edit.isNew || edit.dirty || edit.phase == TileEditorPhase.FAILED) && edit.existingTileId == null && edit.phase != TileEditorPhase.CONFLICT)
                    }
                    if (!busy && edit.phase != TileEditorPhase.CONFLICT) MenuRow(os.t("更换内容", "Choose other content"), icon = "start") { workshop.chooseContent() }
                    if (!edit.isNew && originalExists && !busy) MenuRow(os.t("从开始屏幕移除", "Unpin from Start"),
                        os.t("只移除磁贴，保留内容与正在运行的任务", "Keeps the content and any running task"), "minus") { workshop.unpin(edit.tileId) }
                }
            }
        }
    }
    if (edit.showDiscardConfirmation) AppDialog(onDismissRequest = { workshop.keepEditing() }) { AppDialogSurface {
        AppDialogTitle(os.t("放弃未保存的修改？", "Discard your changes?"))
        Label(os.t("开始屏幕上的原磁贴不会改变。", "The original tile on Start will stay unchanged."), 15, c.muted)
        MetroButton(os.t("继续编辑", "Keep editing"), { workshop.keepEditing() }, primary = true)
        MetroButton(os.t("放弃修改", "Discard changes"), { workshop.discard() })
    } }
}

@Composable private fun TileEditorHeader(os: OsStore, title: String, caption: String, enabled: Boolean, back: () -> Unit) {
    val insets = LocalShellHorizontalInsets.current
    val c = LocalMetro.current
    Row(Modifier.fillMaxWidth().padding(start = insets.pageStart, end = insets.pageEnd, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).semantics { contentDescription = os.t("返回", "Back") }
            .clickable(enabled = enabled, role = Role.Button, onClick = back), contentAlignment = Alignment.CenterStart) { Glyph("back") }
        Column(Modifier.weight(1f)) {
            Label(caption, 12, c.muted)
            Label(title, 24, modifier = Modifier.semantics { heading() }, maxLines = 2)
        }
    }
}

/** 以实际 Start 的像素几何测量同一渲染器；空间不足只缩放整个结果，不另算文字排版。 */
@Composable private fun TileEditorPreview(os: OsStore, draft: TileDocumentEntry, active: Boolean) {
    val density = LocalDensity.current
    val view = LocalView.current
    val actual = os.shell.tileWorkshop.viewport
    val fallback = remember(view.width, view.height, density.density, density.fontScale) {
        StartViewport(view.width.coerceAtLeast((320 * density.density).roundToInt()),
            view.height.coerceAtLeast((480 * density.density).roundToInt()), density.density, 0, 0, density.fontScale)
    }
    val viewport = actual ?: fallback
    val geometry = remember(viewport) { WpStartGeometryCalculator.calculate(viewport) }
    val document by os.shell.engine.state.collectAsState()
    val draftDocument = remember(document.start.document, draft) {
        val current = document.start.document
        val old = current.placements.firstOrNull { it.tileId == draft.tileId }
        val preview = if (old != null) draft.copy(rank = old.rank, preferredCell = old.preferredCell, groupId = old.groupId)
            else draft.copy(rank = ((current.placements.map { it.rank } + current.spacers.map { it.rank }).maxOrNull() ?: -1024L) + 1024L)
        TileLayoutPreview.place(current, preview)
    }
    val packed = remember(draftDocument, geometry.columns) { AdaptiveTilePacker.pack(draftDocument, geometry.columns) }
    val cell = packed.tile(draft.tileId)?.cell ?: return
    val widthState = animateIntAsState(geometry.tileWidthPx(draft.size.columns), tween(220, easing = FastOutSlowInEasing), label = "tile-preview-width")
    val heightState = animateIntAsState(geometry.tileHeightPx(draft.size.rows), tween(220, easing = FastOutSlowInEasing), label = "tile-preview-height")
    val originX = geometry.outerInsetsPx.left + cell.column * (geometry.smallCellPx + geometry.seamPx)
    val gridTop = (geometry.outerInsetsPx.top - geometry.statusStripHeightPx).coerceAtLeast(0)
    val pitch = geometry.smallCellPx + geometry.seamPx
    val tileY = gridTop + cell.row * pitch
    val gridHeight = (packed.documentHeightRows * pitch - geometry.seamPx).coerceAtLeast(0)
    val startFooter = with(density) { (YokuliMetrics.MinTouch + 20.dp).roundToPx() }
    val maxScroll = (gridTop + gridHeight + startFooter - viewport.heightPx).coerceAtLeast(0)
    // 与真实 StartReveal 的滚动目的地一致，不给透明背景另算一套居中视差。
    val scroll = ((cell.row + draft.size.rows) * pitch - viewport.heightPx + geometry.outerInsetsPx.bottom).coerceIn(0, maxScroll)
    val originY = (tileY - scroll).coerceAtLeast(0)
    val scrollFraction = if (maxScroll > 0) scroll.toFloat() / maxScroll else 0f
    var visible by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val visual = instanceTilePresentation(os, draft, active && visible && resumed && LocalInternalAppInputEnabled.current)
    val c = LocalMetro.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maximumPreviewHeight = minOf(viewport.heightPx * .46f, with(density) { 240.dp.toPx() }).coerceAtLeast(1f)
        val scale = minOf(1f, maxWidthPx / geometry.tileWidthPx(geometry.columns),
            maximumPreviewHeight / geometry.tileHeightPx(MarineTileSize.STANDARD_2X2.rows))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Layout(modifier = Modifier.clipToBounds().onGloballyPositioned {
                val rect = it.boundsInWindow()
                visible = rect.width > 0 && rect.height > 0 && rect.bottom > 0 && rect.top < view.height
            }.semantics { contentDescription = os.t("磁贴预览，不执行操作", "Tile preview; no action is performed") }, content = {
                StartWallpaperSurface(Modifier.requiredSize(with(density) { viewport.widthPx.toDp() }, with(density) { viewport.heightPx.toDp() }),
                    scrollFraction = { scrollFraction }) {
                    val backdrop = LocalStartBackdrop.current
                    val foreground = if (backdrop.image != null && backdrop.mode != StartBackdropMode.NONE && backdrop.tileOpacity < .7f) Color.White else c.onAccent
                    Box(Modifier.offset(with(density) { originX.toDp() }, with(density) { originY.toDp() })
                        .layout { measurable, _ ->
                            val child = measurable.measure(Constraints.fixed(widthState.value.coerceAtLeast(1), heightState.value.coerceAtLeast(1)))
                            layout(child.width, child.height) { child.place(0, 0) }
                        }.clipToBounds().startTileBackground()) {
                        Box(Modifier.fillMaxSize().padding(if (visual.fullBleed && draft.size != MarineTileSize.ICON_1X1) 0.dp
                            else if (draft.size == MarineTileSize.ICON_1X1) YokuliMetrics.TileSmallContentInset else YokuliMetrics.TileContentInset)) {
                            // 配置变化立即得到新渲染实例；离屏/暂停只冻结数据，不得把旧样式的 heldFrame 带回来。
                            key(draft.tileId, draft.binding, draft.presentation) {
                                visual.tileRenderers.getValue(draft.size).Render(LauncherTileRenderContext(draft.size, foreground, Modifier.fillMaxSize(),
                                    liveContentEnabled = active && visible && resumed))
                            }
                        }
                    }
                }
            }) { children, constraints ->
                val child = children.single().measure(Constraints.fixed(viewport.widthPx, viewport.heightPx))
                val width = (widthState.value * scale).roundToInt().coerceAtLeast(1)
                val height = (heightState.value * scale).roundToInt().coerceAtLeast(1)
                layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
                    child.placeWithLayer((-originX * scale).roundToInt(), (-originY * scale).roundToInt()) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale; scaleY = scale
                    }
                }
            }
            if (scale < .995f) Label(os.t("等比缩小的开始屏幕预览", "Scaled preview of the tile on Start"), 12, c.muted)
        }
    }
}

/** 成功只消费真实回执。点击定位与保存分开；该条不抢占业务页的导航状态。 */
@Composable fun TileWorkshopFeedbackHost(os: OsStore, modifier: Modifier = Modifier) {
    if (!LocalInternalAppInputEnabled.current) return
    val controller = os.shell.tileWorkshop
    val feedback by controller.feedback.collectAsState()
    val item = feedback ?: return
    val c = LocalMetro.current
    Column(modifier.fillMaxWidth().background(c.panel).pointerInput(Unit) {
        // 只有提示实际矩形参与命中；外面没有全屏 scrim，不挡住原应用。
        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) }
    }.padding(horizontal = 18.dp, vertical = 10.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(item.message, 14, c.fg, Modifier.weight(1f), maxLines = 3)
            IconAction("close", "", { controller.dismissFeedback() }, Modifier.size(44.dp).semantics { contentDescription = os.t("关闭提示", "Dismiss message") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (item.removalRequestId != null) FeedbackAction(os.t("撤销", "Undo")) { controller.undo() }
            else item.tileId?.let { tile -> FeedbackAction(os.t("查看位置", "Show on Start")) { controller.reveal(tile) } }
        }
    }
}

@Composable private fun FeedbackAction(label: String, action: () -> Unit) {
    Label(label, 14, LocalMetro.current.accentText,
        Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = action).padding(vertical = 11.dp))
}
