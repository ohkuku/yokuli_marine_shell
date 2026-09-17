package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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

/** 顶部轻提示只占用固定系统栏；下拉后阅读完整通知，不遮挡地图操作。 */
@Composable internal fun SystemStatusBar(os: OsStore, metrics: ShellWindowMetrics) {
    val notices = os.notifications
    val marine = os.marine?.vm?.ui?.collectAsState()?.value
    val trip = os.marine?.voyage?.collectAsState()?.value
    val safe = ShellSafeBands.resolve(metrics).status
    val density=metrics.density.takeIf {it.isFinite() && it>0f} ?: 1f
    val bannerSegment=ShellSafeBands.statusSegments(metrics).maxByOrNull {it.width}
    val c = LocalMetro.current
    Box(Modifier.fillMaxWidth().testTag("notification-pull-handle")
        .clickable(role = Role.Button, onClickLabel = os.t("打开通知中心", "open notification centre")) { notices.open() }
        .pointerInput(notices) {
            var travel = 0f
            detectVerticalDragGestures(onDragStart = { travel = 0f }, onVerticalDrag = { change, dy ->
                if (dy > 0 || travel > 0) { travel += dy; change.consume() }
                if (travel > 32.dp.toPx()) notices.open()
            })
        }) {
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

@Composable internal fun NotificationCenter(os: OsStore, modifier: Modifier = Modifier) {
    val store = os.notifications
    val c = LocalMetro.current
    AnimatedVisibility(store.expanded, modifier, enter = slideInVertically(tween(240)) { -it } + fadeIn(),
        exit = slideOutVertically(tween(200)) { -it } + fadeOut()) {
        Column(Modifier.fillMaxSize().background(c.bg).testTag("notification-center")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(os.t("通知", "notifications"), 42, modifier = Modifier.weight(1f))
                IconAction("close", os.t("收起", "close"), store::close)
            }
            if (os.recordingActive) MenuRow(os.t("正在记录航行", "voyage recording"),
                if (os.recordingPaused) os.t("已暂停采样，会话仍保留", "sampling paused; session retained") else os.t("各应用共享同一次航程", "one voyage shared by all apps"), "record") {
                store.close(); os.open("voyages")
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Label(os.t("${store.items.size} 条通知", "${store.items.size} notifications"), 16, c.muted)
                Label(os.t("清除已读", "clear read"), 17, c.accent, Modifier.clickable { store.clearRead() }.padding(vertical = 8.dp))
            }
            if (store.items.isEmpty()) Column(Modifier.weight(1f).padding(22.dp), verticalArrangement = Arrangement.Center) {
                Label(os.t("这里很安静", "all quiet"), 32)
                Label(os.t("各应用的通知会保留在这里。", "Notifications from your apps stay here."), 19, c.muted)
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(store.items, key = { it.id }) { item ->
                    Column(Modifier.fillMaxWidth().combinedNoticeClick(item.destination != null) {
                        store.close(); item.destination?.let(os::open)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(item.app?.icon ?: "connect", Modifier.size(22.dp), c.accent)
                            Label(item.app?.let(os::title) ?: "Yokuli OS", 16, c.accent, Modifier.weight(1f).padding(start = 10.dp))
                            Label(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,if(os.chinese)Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH).format(Date(item.createdAt)), 12, c.muted)
                            Box(Modifier.size(38.dp).clickable(onClickLabel = os.t("删除通知", "dismiss notification")) { store.remove(item.id) }, contentAlignment = Alignment.Center) {
                                Glyph("close", Modifier.size(16.dp), c.muted)
                            }
                        }
                        Label(item.text(os), 21, modifier = Modifier.padding(top = 6.dp))
                        if (item.severity != NoticeSeverity.INFO) Label(os.t("查看通知不会解除业务警报", "Reading this does not acknowledge the alarm"), 13, c.muted)
                        if (item.occurrences > 1) Label(os.t("重复 ${item.occurrences} 次", "${item.occurrences} occurrences"), 13, c.muted)
                        if (item.destination != null) Label(os.t("查看详情", "view details"), 16, c.accent, Modifier.padding(top = 6.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(32.dp).pointerInput(store) {
                detectVerticalDragGestures { change, dy -> if (dy < 0) { change.consume(); store.close() } }
            }.clickable { store.close() }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(42.dp).height(3.dp).background(c.muted))
            }
        }
    }
}

private fun Modifier.combinedNoticeClick(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) clickable(onClick = onClick) else this
