package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.marine.shell.rebuild.data.VoyagePhase
import com.yokuli.shell.contract.ShellWindowMetrics
import com.yokuli.shell.contract.ShellSafeBands
import com.yokuli.marine.feature.desktop.WpStatusStrip
import com.yokuli.marine.feature.desktop.WpStatusStripItem
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 顶部轻提示只占用固定系统栏；完整通知由底部通知键打开，不抢占系统下拉手势。 */
@Composable internal fun SystemStatusBar(os: OsStore, metrics: ShellWindowMetrics) {
    val notices = os.notifications
    val marine = os.marine?.vm?.ui?.collectAsState()?.value
    val trip = os.marine?.voyage?.collectAsState()?.value
    val safe = ShellSafeBands.resolve(metrics).status
    val density=metrics.density.takeIf {it.isFinite() && it>0f} ?: 1f
    val bannerSegment=ShellSafeBands.statusSegments(metrics).maxByOrNull {it.width}
    val c = LocalMetro.current
    Box(Modifier.fillMaxWidth()) {
        WpStatusStrip(metrics, buildList {
            if (trip != null && trip.phase!=VoyagePhase.IDLE) add(WpStatusStripItem("voyage", when(trip.phase) {
                VoyagePhase.PAUSED -> os.t("记录暂停", "REC paused")
                VoyagePhase.STARTING -> os.t("记录准备", "REC starting")
                VoyagePhase.SAVING -> os.t("记录保存", "REC saving")
                else -> "● REC"
            },
                os.t("全局航程：", "system voyage: ") + trip.name, false))
            if (marine?.active != null) add(WpStatusStripItem("anchor", os.t("锚警", "anchor"), os.t("锚警会话仍在运行", "anchor session is running"), false))
            if (notices.unreadCount > 0) add(WpStatusStripItem("notices", "${notices.unreadCount}", os.t("未读通知", "unread notifications"), true))
        })
        AnimatedVisibility(notices.banner != null, enter = slideInVertically(tween(180)) { -it } + fadeIn(),
            exit = slideOutVertically(tween(140)) { -it } + fadeOut()) {
            val banner = notices.banner
            Box(Modifier.fillMaxWidth().height(30.dp+(safe.top/density).dp)) {
                if(banner!=null && bannerSegment!=null)Row(Modifier
                    .absoluteOffset(x=(bannerSegment.left/density).dp,y=(safe.top/density).dp)
                    .width((bannerSegment.width/density).dp).height(30.dp).clipToBounds().background(c.accent)
                    .clickable(onClickLabel=os.t("处理通知","open notification")) {os.openNotification(banner.id)}
                    .padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(bannerSegment.width/density>=64f)Glyph(banner.app?.icon ?: "connect",Modifier.size(18.dp),androidx.compose.ui.graphics.Color.White)
                    Label((banner.app?.let(os::title) ?: "Yokuli OS")+" · "+banner.text(os),13,
                        androidx.compose.ui.graphics.Color.White,Modifier.weight(1f),maxLines=1)
                }
            }
        }
    }
}

@Composable internal fun NotificationCenter(os: OsStore, modifier: Modifier = Modifier, metrics: ShellWindowMetrics? = null) {
    val store = os.notifications
    val c = LocalMetro.current
    val insets = LocalShellHorizontalInsets.current
    AnimatedVisibility(store.expanded, modifier, enter = slideInVertically(tween(240)) { -it } + fadeIn(),
        exit = slideOutVertically(tween(200)) { -it } + fadeOut()) {
        Column(Modifier.fillMaxSize().background(c.bg).testTag("notification-center")) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(52.dp)) {
                val density = metrics?.density?.takeIf { it.isFinite() && it > 0f } ?: LocalDensity.current.density
                val segments = metrics?.let { ShellSafeBands.statusSegments(it) }.orEmpty()
                val first = segments.firstOrNull()
                val last = segments.lastOrNull()
                val titleStart = maxOf(insets.topStart, ((first?.left ?: 0) / density).dp + 4.dp)
                val end = minOf(maxWidth - insets.topEnd, last?.let { (it.right / density).dp - 4.dp } ?: maxWidth)
                val rightStart = last?.let { (it.left / density).dp } ?: titleStart
                val showSettings = end - rightStart >= 88.dp
                val actionStart = (end - if (showSettings) 88.dp else 44.dp).coerceAtLeast(titleStart)
                val titleEnd = minOf(actionStart - 8.dp, first?.let { (it.right / density).dp - 4.dp } ?: actionStart)
                val titleWidth = (titleEnd - titleStart).coerceAtLeast(0.dp)
                val titleSize = if (os.chinese || titleWidth >= 180.dp) 32 else if (titleWidth >= 145.dp) 28 else 24
                Label(os.t("通知", "notifications"), titleSize, modifier = Modifier.align(Alignment.CenterStart).absoluteOffset(x = titleStart)
                    .width(titleWidth), maxLines = 1)
                Row(Modifier.align(Alignment.CenterStart).absoluteOffset(x = actionStart)) {
                    if (showSettings) Box(Modifier.size(44.dp).clickable(onClickLabel = os.t("设置", "settings")) { os.openSystemDestination("settings") }, contentAlignment = Alignment.Center) {
                        Glyph("settings", Modifier.size(23.dp))
                    }
                    Box(Modifier.size(44.dp).clickable(onClickLabel = os.t("收起通知中心", "close notification center"), onClick = store::close), contentAlignment = Alignment.Center) {
                        Glyph("close", Modifier.size(23.dp))
                    }
                }
            }
            NotificationQuickActions(os)
            Row(Modifier.fillMaxWidth().padding(start = insets.topStart, end = insets.topEnd, top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Label(os.t("${store.items.size} 条通知", "${store.items.size} notifications"), 13, c.muted)
                Label(os.t("全部清除", "clear all"), 15, if (store.items.isEmpty()) c.muted else c.accent,
                    Modifier.clickable(enabled = store.items.isNotEmpty()) { store.clearAll() }.padding(vertical = 8.dp))
            }
            if (store.items.isEmpty()) Column(Modifier.weight(1f).padding(start = insets.topStart, end = insets.topEnd), verticalArrangement = Arrangement.Center) {
                Label(os.t("这里很安静", "all quiet"), 27)
                Label(os.t("新的应用通知会出现在这里。", "New app notifications will appear here."), 17, c.muted)
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = insets.topStart, end = insets.topEnd, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(store.items, key = { it.id }) { item ->
                    SwipeNoticeRow(os, item, Modifier.animateItem(), onActivate = { os.openNotification(item.id) }, onDismiss = { store.remove(item.id) })
                }
            }
        }
    }
}

/** 横向位移完全跟随手指；足够距离或同方向快甩才移除，短拖动弹回。 */
@Composable private fun SwipeNoticeRow(os: OsStore, item: SystemNotice, modifier: Modifier, onActivate: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalMetro.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var offset by remember(item.id) { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    var motion by remember { mutableStateOf<Job?>(null) }
    val activate by rememberUpdatedState(onActivate)
    val removeNotice by rememberUpdatedState(onDismiss)
    val drag = rememberDraggableState { delta -> offset += delta }
    val time = remember(item.createdAt, os.chinese) {
        val locale = if (os.chinese) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
        val today = java.util.Calendar.getInstance()
        val received = java.util.Calendar.getInstance().apply { timeInMillis = item.createdAt }
        val sameDay = today.get(java.util.Calendar.YEAR) == received.get(java.util.Calendar.YEAR) && today.get(java.util.Calendar.DAY_OF_YEAR) == received.get(java.util.Calendar.DAY_OF_YEAR)
        (if (sameDay) DateFormat.getTimeInstance(DateFormat.SHORT, locale) else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)).format(Date(item.createdAt))
    }
    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.coerceAtLeast(1) }.clipToBounds()) {
        if (kotlin.math.abs(offset) > 1f) Row(Modifier.matchParentSize().background(c.panel).padding(horizontal = 14.dp),
            horizontalArrangement = if (offset > 0f) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Glyph("close", Modifier.size(22.dp), c.accent)
            Label(os.t("清除", "dismiss"), 14, c.accent, Modifier.padding(start = 8.dp))
        }
        Column(Modifier.fillMaxWidth().offset { IntOffset(offset.roundToInt(), 0) }.background(c.bg)
            .draggable(drag, Orientation.Horizontal, onDragStarted = { motion?.cancel() }, onDragStopped = { velocity ->
                val minFlingDistance = with(density) { 28.dp.toPx() }
                val minFlingVelocity = with(density) { 850.dp.toPx() }
                val reversing = velocity * offset < 0f && kotlin.math.abs(velocity) >= minFlingVelocity
                val remove = !reversing && (kotlin.math.abs(offset) >= width * .35f ||
                    (kotlin.math.abs(offset) >= minFlingDistance && kotlin.math.abs(velocity) >= minFlingVelocity && velocity * offset > 0f))
                motion = scope.launch {
                    val target = if (remove) width.toFloat() * if (offset < 0f) -1f else 1f else 0f
                    Animatable(offset).animateTo(target, spring(dampingRatio = .9f, stiffness = 480f), initialVelocity = velocity) { offset = value }
                    if (remove) removeNotice()
                }
            })
            .semantics { dismiss { removeNotice(); true } }
            .clickable(enabled = kotlin.math.abs(offset) < 4f, onClickLabel = os.t("处理通知", "open notification")) { activate() }
            .padding(vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Glyph(item.app?.icon ?: "connect", Modifier.size(18.dp), c.accent)
                Label(item.app?.let(os::title) ?: "Yokuli OS", 13, c.accent, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                Label(time, 12, c.muted)
            }
            Label(item.text(os), 17, modifier = Modifier.padding(top = 5.dp), maxLines = if (item.destination == null) Int.MAX_VALUE else 5)
            if (item.occurrences > 1 || item.severity != NoticeSeverity.INFO) Label(listOfNotNull(
                if (item.occurrences > 1) os.t("${item.occurrences} 次", "${item.occurrences} times") else null,
                if (item.severity != NoticeSeverity.INFO) os.t("清除通知不会解除警报", "dismissing does not clear the alarm") else null
            ).joinToString(" · "), 12, c.muted, Modifier.padding(top = 4.dp))
        }
    }
}
