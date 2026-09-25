package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.runtime.contract.VoyagePhase
import com.yokuli.runtime.contract.notification.NoticeConnection
import com.yokuli.runtime.contract.notification.NoticeCommandStatus
import com.yokuli.shell.contract.ShellWindowMetrics
import com.yokuli.shell.contract.ShellSafeBands
import com.yokuli.marine.feature.desktop.WpStatusStrip
import com.yokuli.marine.feature.desktop.WpStatusStripItem
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/** 顶部轻提示只占用固定系统栏；完整通知由底部通知键打开，不抢占系统下拉手势。 */
@Composable internal fun SystemStatusBar(os: OsStore, metrics: ShellWindowMetrics) {
    val notices = os.notifications
    val persistence by os.persistenceState.collectAsState()
    val bridge=os.marine
    // 状态栏只订阅自己显示的字段，手机姿态、AIS、网络字节不会触发整个顶栏重组。
    val anchorActive=bridge?.let {remember(it){it.services.state.map {state->state.active!=null}.distinctUntilChanged()}
        .collectAsState(it.services.state.value.active!=null).value} ?: false
    val trip=bridge?.let {remember(it){it.voyage.map {state->state.phase to state.name}.distinctUntilChanged()}
        .collectAsState(it.voyage.value.let {state->state.phase to state.name}).value}
    val safe = ShellSafeBands.resolve(metrics).status
    val density=metrics.density.takeIf {it.isFinite() && it>0f} ?: 1f
    val bannerSegment=ShellSafeBands.statusSegments(metrics).maxByOrNull {it.width}
    val c = LocalMetro.current
    Box(Modifier.fillMaxWidth()) {
        WpStatusStrip(metrics, buildList {
            if(persistence.saving || persistence.failed) add(WpStatusStripItem("storage",
                if(persistence.saving) os.t("保存中", "saving") else os.t("未保存", "unsaved"),
                os.t("通知中心中查看保存状态或重试", "View save status or retry in notifications"), persistence.failed))
            if (trip != null && trip.first!=VoyagePhase.IDLE) add(WpStatusStripItem("voyage", when(trip.first) {
                VoyagePhase.PAUSED -> os.t("记录暂停", "REC paused")
                VoyagePhase.STARTING -> os.t("记录准备", "REC starting")
                VoyagePhase.SAVING -> os.t("记录保存", "REC saving")
                else -> "● REC"
            },
                os.t("全局航程：", "system voyage: ") + trip.second, false))
            if (anchorActive) add(WpStatusStripItem("anchor", os.t("锚警", "anchor"), os.t("锚警会话仍在运行", "anchor session is running"), false))
            if (notices.unreadCount > 0) add(WpStatusStripItem("notices", "${notices.unreadCount}", os.t("未读通知", "unread notifications"), true))
        })
        AnimatedVisibility(notices.banner != null, enter = slideInVertically(tween(180)) { -it } + fadeIn(),
            exit = slideOutVertically(tween(140)) { -it } + fadeOut()) {
            val banner = notices.banner
            Box(Modifier.fillMaxWidth().height(30.dp+(safe.top/density).dp)) {
                if(banner!=null && bannerSegment!=null)Row(Modifier
                    .absoluteOffset(x=(bannerSegment.left/density).dp,y=(safe.top/density).dp)
                    .width((bannerSegment.width/density).dp).height(30.dp).clipToBounds().background(c.panel)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .clickable(onClickLabel=os.t("处理通知","open notification")) {os.openNotification(banner.id)}
                    .padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(bannerSegment.width/density>=64f)Glyph(banner.app?.icon ?: "connect",Modifier.size(18.dp),c.accentText)
                    Label((banner.app?.let(os::title) ?: "Yokuli OS")+" · "+banner.text(os),13,
                        c.fg,Modifier.weight(1f),maxLines=1)
                }
            }
        }
    }
}

/** 单一 Shell 覆盖层：消息历史、实时任务与业务告警分别订阅，不共享清除语义。 */
@Composable internal fun NotificationCenter(os: OsStore, modifier: Modifier = Modifier, metrics: ShellWindowMetrics? = null) {
    val store = os.notifications
    val shade = os.notificationShade
    val c = LocalMetro.current
    val groups = remember(store.items) { store.items.groupBy { it.app } }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val panelFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(shade.blocksInput) { store.setPresentationVisible(shade.blocksInput) }
    DisposableEffect(shade, scope) { shade.attachHost(scope); onDispose { shade.hostDetached() } }
    if (!shade.blocksInput) return
    val insets = LocalShellHorizontalInsets.current
    val list = rememberLazyListState(shade.presentation.listIndex, shade.presentation.listOffset)
    var unseen by remember { mutableStateOf(emptySet<String>()) }
    var known by remember { mutableStateOf(store.items.map { it.id }.toSet()) }
    LaunchedEffect(store.items.map { it.id }) {
        val ids = store.items.map { it.id }.toSet()
        if (list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 80) unseen += ids - known
        known = ids
        unseen = unseen.intersect(ids)
    }
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            shade.presentation = shade.presentation.copy(listIndex = index, listOffset = offset)
        }
    }
    // 只把稳定展开且至少半行可见400ms的记录标为已读。动画、滑过、离屏消息不消费。
    LaunchedEffect(list, shade, store) {
        snapshotFlow {
            if (store.connection != NoticeConnection.READY || !shade.settledOpen || shade.presentation.detail != null || list.isScrollInProgress) emptySet()
            else list.layoutInfo.let { info -> info.visibleItemsInfo.mapNotNull { row ->
                val key = row.key as? String
                val shown = minOf(row.offset + row.size, info.viewportEndOffset) - maxOf(row.offset, info.viewportStartOffset)
                key?.takeIf { it.startsWith("notice:") && shown >= row.size * .5f }?.removePrefix("notice:")
            }.toSet() }
        }.distinctUntilChanged().collectLatest { ids ->
            if (ids.isNotEmpty()) { delay(400); ids.forEach { id -> if (store.items.any { it.id == id && !it.read }) store.markRead(id) } }
        }
    }
    LaunchedEffect(Unit) { keyboard?.hide(); focus.clearFocus(); panelFocus.requestFocus() }
    Box(modifier.fillMaxSize().trackShadeTouches(shade)) {
        // 整个覆盖区域在Closing期间仍拦住输入，露出的海图不会接到同一手指的剩余事件。
        Box(Modifier.matchParentSize().pointerInput(shade) {
            awaitEachGesture { awaitFirstDown(requireUnconsumed = false); do {
                val event = awaitPointerEvent(); event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed }) }
        })
        Column(Modifier.fillMaxSize().onSizeChanged { shade.updateHeight(it.height.toFloat()) }
            .graphicsLayer { translationY = shade.offsetPx }
            .background(c.panel).testTag("notification-center")
            .focusRequester(panelFocus).focusable()
            .semantics { paneTitle = os.t("通知中心", "notification center"); isTraversalGroup = true
                dismiss { shade.close(); true } }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) { shade.back(); true } else false
            }) {
            NotificationHeader(os, metrics)
            NotificationRiskSummary(os)
            if (shade.presentation.detail != null) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = insets.pageStart, end = insets.pageEnd, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val detail = shade.presentation.detail.orEmpty()
                    when {
                        detail in setOf("position", "connections") -> NotificationQuickDetail(os, detail,
                            os::openSystemDestination, { shade.presentation = shade.presentation.copy(detail = null) })
                        detail.startsWith("message:") -> {
                            val notice = store.items.firstOrNull { it.id == detail.removePrefix("message:") }
                            MetroButton(os.t("返回通知中心", "back to notifications"), { shade.back() })
                            if (notice == null) Label(os.t("这条通知记录已移除", "This notification record was removed"), 20)
                            else {
                                AppDialogTitle(notice.title(os))
                                Label(notice.body(os), 15)
                                notice.destination?.let { target -> MetroButton(os.t("查看相关内容", "open related content"), { os.openSystemDestination(target) }) }
                                if (notice.dismissible) MetroButton(os.t("清除这条记录", "clear this record"), {
                                    store.remove(notice.id) { result -> if (result.status == NoticeCommandStatus.COMPLETED && shade.presentation.detail == detail) shade.back() }
                                }, enabled = notice.id !in store.pendingIds)
                            }
                        }
                        else -> {
                            Label(os.t("这个入口暂时无法打开", "This destination is unavailable"), 22)
                            MetroButton(os.t("返回通知中心", "back to notifications"), { shade.back() })
                        }
                    }
                    NotificationStorageStatus(os)
                }
            } else {
                if (unseen.isNotEmpty()) MetroButton(os.t("有 ${unseen.size} 条新消息", "${unseen.size} new messages"), {
                    scope.launch { list.animateScrollToItem(4); unseen = emptySet() }
                }, Modifier.padding(start = insets.pageStart, end = insets.pageEnd))
                LazyColumn(Modifier.weight(1f).fillMaxWidth().shadeListScroll(shade, list), state = list,
                    contentPadding = PaddingValues(start = insets.pageStart, end = insets.pageEnd, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item("quick") { NotificationQuickActions(os, shade::showDetail, shade.presentation.more,
                        { shade.presentation = shade.presentation.copy(more = it) }, os::openSystemDestination) }
                    item("tasks") { NotificationTaskCards(os, os::openSystemDestination) }
                    item("storage") { NotificationStorageStatus(os) }
                    item("history-heading") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Label(os.t("通知记录", "Notification history"), 20, modifier = Modifier.weight(1f))
                            Label(os.t("清除记录", "clear history"), 15, c.accentText, Modifier.heightIn(min = 48.dp)
                                .clickable(enabled = store.items.any { it.dismissible } && store.pendingCommand == null) { store.clearAll() }
                                .padding(vertical = 14.dp))
                        }
                    }
                    if (store.items.isEmpty()) item("empty") {
                        Label(if (store.connection == NoticeConnection.READY && store.persistenceFailure == null)
                            os.t("暂无通知记录", "No notification records") else os.t("通知记录尚未载入", "Notification history is not loaded"),
                            22, c.muted, Modifier.padding(vertical = 24.dp))
                    }
                    groups.forEach { (publisher, messages) ->
                        item("publisher:${publisher?.name ?: "system"}") {
                            Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
                                Glyph(publisher?.icon ?: "start",Modifier.size(20.dp),c.fg)
                                Label(publisher?.let(os::title) ?: os.t("系统", "System"),15,c.fg,
                                    Modifier.weight(1f).padding(start=12.dp),weight=FontWeight.SemiBold)
                                Label(messages.size.toString(),12,c.muted)
                            }
                        }
                    items(messages, key = { "notice:${it.id}" }) { item ->
                        SwipeNoticeRow(os, item, Modifier.animateItem(),
                            expanded = item.id in shade.presentation.expandedMessages,
                            onExpand = { shade.presentation = shade.presentation.copy(expandedMessages =
                                if (item.id in shade.presentation.expandedMessages) shade.presentation.expandedMessages - item.id
                                else shade.presentation.expandedMessages + item.id) },
                            onActivate = { os.openNotification(item.id) },
                            onDismiss = { completed -> store.remove(item.id) { completed(it.status == NoticeCommandStatus.COMPLETED) } })
                    }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).shadeHandle(shade)
                .clickable(role = Role.Button, onClickLabel = os.t("收起通知中心", "close notifications")) { shade.close() }
                .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(36.dp).height(3.dp).background(c.muted))
                Label(os.t("上滑收起", "swipe up to close"), 13, c.muted, Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable private fun NotificationHeader(os: OsStore, metrics: ShellWindowMetrics?) {
    val shade = os.notificationShade
    val insets = LocalShellHorizontalInsets.current
    val density = LocalDensity.current.density
    // 标题让开挖孔的水平投影；状态栏和面板高度不重复叠加系统inset。
    val safeSegments = metrics?.let(ShellSafeBands::statusSegments).orEmpty()
    val segment = safeSegments.maxByOrNull { it.width }
    BoxWithConstraints(Modifier.fillMaxWidth().shadeHandle(shade)) {
        val start = maxOf(insets.topStart, ((segment?.left ?: 0) / density).dp + 4.dp)
        val end = maxOf(insets.topEnd, segment?.let { maxWidth - (it.right / density).dp + 4.dp } ?: insets.topEnd)
        val roomForTitle = maxWidth - start - end >= 230.dp
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = start, end = end), verticalAlignment = Alignment.CenterVertically) {
                if (roomForTitle) Label(os.t("通知", "Notifications"), 24, modifier = Modifier.weight(1f), maxLines = 1)
                else Spacer(Modifier.weight(1f))
                Box(Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = os.t("设置", "settings")) { os.openSystemDestination("settings") }, contentAlignment = Alignment.Center) {
                    Glyph("settings", Modifier.size(23.dp))
                }
                Box(Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = os.t("收起通知中心", "close notifications")) { shade.close() }, contentAlignment = Alignment.Center) {
                    Glyph("close", Modifier.size(23.dp))
                }
            }
            if (!roomForTitle) Label(os.t("通知", "Notifications"), 24,
                modifier = Modifier.padding(start = insets.pageStart, end = insets.pageEnd, bottom = 8.dp), maxLines = 2)
        }
    }
}

/** 固定摘要来自当前领域风险，历史clear/read绝不影响它。独立报警Dialog仍在Android窗口上层。 */
@Composable private fun NotificationRiskSummary(os: OsStore) {
    val services = os.marine?.services ?: return
    val warning by remember(services) { services.state.map { state ->
        val active = state.active
        active != null && !active.paused && (state.alarmSnapshot.state in setOf(
            com.yokuli.anchorwatch.domain.model.AlarmState.WARNING, com.yokuli.anchorwatch.domain.model.AlarmState.ALARM) ||
            state.conditions.depth.alarmActive || state.conditions.depth.dataUnavailable ||
            state.conditions.windSpeed.alarmActive || state.conditions.windSpeed.warningActive || state.conditions.windSpeed.dataUnavailable ||
            state.conditions.windShift.alarmActive || state.conditions.windShift.dataUnavailable)
    }.distinctUntilChanged() }.collectAsState(false)
    val ais = os.marine?.system?.ais
    val trafficTarget = ais?.let { source ->
        remember(source) { source.snapshot.map { snapshot ->
            snapshot.events.filter { it.active }.maxByOrNull { it.level.ordinal }?.mmsi
        }.distinctUntilChanged() }.collectAsState(source.snapshot.value.events.filter { it.active }.maxByOrNull { it.level.ordinal }?.mmsi).value
    }
    if (warning) NotificationRiskRow(os, "anchor", os.t("守锚有待处理的警戒", "Anchor watch needs attention"), "anchor")
    if (trafficTarget != null) NotificationRiskRow(os, "ais", os.t("AIS 有待处理的交通警戒", "AIS traffic needs attention"), "ais:target:$trafficTarget")
}

@Composable private fun NotificationRiskRow(os: OsStore, icon: String, text: String, destination: String) {
    val c = LocalMetro.current
    val insets = LocalShellHorizontalInsets.current
    Row(Modifier.fillMaxWidth().background(c.panel).clickable(role = Role.Button) { os.openSystemDestination(destination) }
        .padding(start = insets.pageStart, end = insets.pageEnd).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, Modifier.size(22.dp), c.accent)
        Label(text, 16, c.accentText, Modifier.weight(1f).padding(horizontal = 10.dp))
        Glyph("next", Modifier.size(18.dp))
    }
}

@Composable private fun NotificationStorageStatus(os: OsStore) {
    val c = LocalMetro.current
    val store = os.notifications
    val persistence by os.persistenceState.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ContentRecoveryStatus(os)
        if (store.connection != NoticeConnection.READY) Label(when (store.connection) {
            NoticeConnection.CONNECTING -> os.t("正在连接消息服务…", "connecting to the message service…")
            NoticeConnection.DISCONNECTED -> os.t("消息服务连接中断，正在重连；保留已收到的记录。", "Message service disconnected. Reconnecting; received records remain visible.")
            NoticeConnection.UNSUPPORTED -> os.t("消息服务版本或权限不兼容，暂时不能修改记录。", "Message service version or access is incompatible; records cannot be changed.")
            else -> os.t("消息服务尚未就绪", "message service is not ready")
        }, 15, c.muted)
        if (store.lastResult?.status in setOf(NoticeCommandStatus.NOT_SENT, NoticeCommandStatus.REJECTED))
            Label(when (store.lastResult?.reason) {
                "REVISION_CHANGED", "EPOCH_CHANGED" -> os.t("通知记录已更新，这次批量清除未执行。请查看当前记录后再决定。", "History changed, so this bulk clear was not applied. Review the current records before trying again.")
                else -> os.t("这次通知操作未完成，请检查服务状态后再操作。", "This notification action did not complete. Check the service status before trying again.")
            }, 14, c.muted)
        if (store.persistenceFailure != null) {
            val readFailed = store.persistenceFailure == "HISTORY_READ_FAILED"
            Label(if (readFailed) os.t("原有通知记录未能读取", "Existing notification history could not be read")
                else os.t("通知记录尚未保存", "Notification history is not saved"), 17, c.accent)
            Label(if (readFailed) os.t("原文件受到保护，当前不能修改历史。重新读取仍失败时，请保留原文件以便恢复。", "The original file is protected and history cannot be changed. If reloading still fails, keep the original file for recovery.")
                else os.t("这次改动保留在消息服务内，尚未写入存储。请检查存储空间后重试。", "This change remains in the message service and has not reached storage. Check available storage and retry."), 14, c.muted)
            MetroButton(if (readFailed) os.t("重新读取通知记录", "reload notification history") else os.t("重试保存通知", "retry notification storage"), store::retryPersistence)
        }
        if (store.pendingCommand != null && !store.hasUnknownCommands) Label(os.t("正在保存通知操作…", "saving notification change…"), 14, c.muted)
        if (store.hasUnknownCommands || store.lastResult?.status == NoticeCommandStatus.UNKNOWN) {
            Label(os.t("通知操作结果未确认；先查询原请求，不重复执行。", "Notification result is unknown. Query the original request without repeating the action."), 14, c.muted)
            MetroButton(if (store.checkingPending) os.t("正在查询", "checking result") else os.t("查询通知操作结果", "check notification result"),
                store::recheckPending, enabled = !store.checkingPending)
        }
        if (persistence.readFailure == null && (persistence.saving || persistence.failed)) {
            Label(if (persistence.saving) os.t("正在保存资料…", "saving content…") else os.t("资料改动尚未保存", "Content changes are not saved"), 17, c.accent)
            if (persistence.failed && !persistence.saving) {
                Label(os.t("改动暂留本次运行，重新保存不会重复创建对象。", "Changes remain in this session. Retrying does not create duplicate objects."), 14, c.muted)
                MetroButton(os.t("重试保存资料", "retry content save"), { os.saveWithFeedback("改动已保存", "Changes saved") })
            }
        }
    }
}

/** 清除手势与点击互斥；写盘结果确认前不把记录伪装成已移除。 */
@Composable private fun SwipeNoticeRow(os: OsStore, item: SystemNotice, modifier: Modifier, expanded: Boolean,
    onExpand: () -> Unit, onActivate: () -> Unit, onDismiss: ((Boolean) -> Unit) -> Unit) {
    val c = LocalMetro.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var offset by remember(item.id) { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    var motion by remember { mutableStateOf<Job?>(null) }
    var submitting by remember(item.id) { mutableStateOf(false) }
    val activate by rememberUpdatedState(onActivate)
    val removeNotice by rememberUpdatedState(onDismiss)
    val drag = rememberDraggableState { delta -> offset = (offset + delta).coerceIn(-width.toFloat(), width.toFloat()) }
    val body = item.body(os)
    val time = remember(item.updatedAt, os.chinese) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
            if (os.chinese) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH).format(Date(item.updatedAt))
    }
    val pending = submitting || item.id in os.notifications.pendingIds
    fun dismiss() {
        if (!item.dismissible || pending) return
        submitting = true
        removeNotice { success ->
            submitting = false
            if (!success) motion = scope.launch {
                Animatable(offset).animateTo(0f, spring(dampingRatio = 1f, stiffness = 480f)) { offset = value }
            }
        }
    }
    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.coerceAtLeast(1) }.clipToBounds()) {
        if (kotlin.math.abs(offset) > 1f) Row(Modifier.matchParentSize().background(c.panel).padding(horizontal = 14.dp),
            horizontalArrangement = if (offset > 0f) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Glyph("close", Modifier.size(22.dp), c.accent)
            Label(when {
                pending && os.notifications.hasUnknownCommands -> os.t("结果未确认", "result unconfirmed")
                pending -> os.t("正在移除", "removing")
                else -> os.t("清除记录", "clear record")
            }, 14, c.accentText, Modifier.padding(start = 8.dp))
        }
        Column(Modifier.fillMaxWidth().offset { IntOffset(offset.roundToInt(), 0) }.background(c.panel)
            .draggable(drag, Orientation.Horizontal, enabled = item.dismissible && !pending,
                startDragImmediately = motion?.isActive == true, onDragStarted = { motion?.cancel() }, onDragStopped = { velocity ->
                val minimum = with(density) { 28.dp.toPx() }; val speed = with(density) { 850.dp.toPx() }
                val reversing = velocity * offset < 0f && kotlin.math.abs(velocity) >= speed
                val remove = !reversing && (kotlin.math.abs(offset) >= width * .35f ||
                    kotlin.math.abs(offset) >= minimum && kotlin.math.abs(velocity) >= speed && velocity * offset > 0f)
                if (remove) dismiss()
                else motion = scope.launch { Animatable(offset).animateTo(0f, spring(dampingRatio = .95f, stiffness = 480f), initialVelocity = velocity) { offset = value } }
            })
            .semantics(mergeDescendants = true) {
                contentDescription = (item.app?.let(os::title) ?: os.t("系统", "system")) + ". " + item.title(os) + ". " + body
                if (item.dismissible && !pending) dismiss { dismiss(); true }
            }
            .clickable(enabled = kotlin.math.abs(offset) < 4f && !pending, onClickLabel = os.t("查看通知", "view notification")) { activate() }
            .padding(vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!item.read) Box(Modifier.size(6.dp).background(c.accent))
                Label(if(item.read) os.t("已读", "Read") else os.t("新通知", "New notification"),12,c.muted,Modifier.weight(1f).padding(start=8.dp),maxLines=1)
                Label(time, 12, c.muted)
            }
            Label(item.title(os), 15, modifier = Modifier.padding(top = 8.dp), maxLines = 2, weight = FontWeight.SemiBold)
            Label(body, 15, c.fg, Modifier.padding(top = 4.dp), maxLines = if (expanded) Int.MAX_VALUE else 3)
            if (body.length > 100 || body.count { it == '\n' } >= 2) Label(if (expanded) os.t("收起正文", "collapse text") else os.t("展开正文", "expand text"),
                14, c.accentText, Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onExpand).padding(vertical = 14.dp))
            if (item.occurrences > 1) Label(os.t("重复 ${item.occurrences} 次", "${item.occurrences} occurrences"), 12, c.muted)
            if (item.severity == NoticeSeverity.ALARM) Label(os.t("历史记录；清除不解除当前警报", "History record; clearing does not resolve a current alarm"), 12, c.muted)
        }
    }
}
