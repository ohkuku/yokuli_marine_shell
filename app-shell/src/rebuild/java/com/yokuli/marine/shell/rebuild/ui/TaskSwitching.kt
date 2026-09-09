package com.yokuli.marine.shell.rebuild.ui

import android.graphics.Rect
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.engine.InternalAppTask
import com.yokuli.shell.engine.InternalAppTaskId
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable internal fun TaskCaptureHost(os:OsStore,taskId:InternalAppTaskId,token:String,active:Boolean,content:@Composable ()->Unit) {
    val window=LocalContext.current.activity()?.window
    val owner=remember(taskId,token,window) {Any()}
    DisposableEffect(owner,active) { onDispose { if(active)os.shell.snapshots.unbind(owner) } }
    LaunchedEffect(owner,active) { if(active) {delay(420);os.shell.snapshots.captureCurrent(taskId)} }
    Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
        if(active && window!=null) {
            val b=coordinates.boundsInWindow()
            os.shell.snapshots.bind(owner,taskId,window,Rect(b.left.roundToInt(),b.top.roundToInt(),b.right.roundToInt(),b.bottom.roundToInt()))
        }
    }) {content()}
}

@Composable internal fun AppLaunchCover(os:OsStore,app:ShellApp) {
    Box(Modifier.fillMaxSize().background(LocalMetro.current.bg).testTag("app-launch-${app.id.value}"),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(24.dp)) {
            ShellAppIcon(app,LocalMetro.current.accent,Modifier.size(100.dp))
            Label(os.title(app.app),34)
        }
    }
}

/** WP task switching is a horizontal set of actual app frames, with the app name below. */
@Composable internal fun TaskSwitcher(os:OsStore,tasks:List<InternalAppTask>,onActivate:(InternalAppTask)->Unit,onClose:(InternalAppTask)->Unit) {
    val ordered=tasks.asReversed()
    val pager=rememberPagerState {ordered.size}
    val c=LocalMetro.current
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).testTag("launcher-recents")) {
        if(ordered.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Label(os.t("没有最近应用","no recent apps"),36)
                Label(os.t("打开的应用会留在这里。","Your open apps will appear here."),20,c.muted)
            }
        } else {
            val cardHeight=maxHeight*.69f
            HorizontalPager(state=pager,contentPadding=PaddingValues(horizontal=maxWidth*.13f),pageSpacing=18.dp,
                key={ordered[it].taskId.value},modifier=Modifier.align(Alignment.Center).fillMaxWidth()) {index->
                val task=ordered[index]
                val app=os.shell.apps.firstOrNull {it.id==task.appId} ?: return@HorizontalPager
                val snapshot=os.shell.snapshots.images[task.taskId]
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth().height(cardHeight).background(c.panel).border(1.dp,c.muted.copy(alpha=.35f)).clipToBounds()
                        .testTag("recent-task-${task.appId.value}").clickable(role=Role.Button,onClick={onActivate(task)})
                        .semantics {contentDescription=os.t("恢复 ${os.title(app.app)}","resume ${os.title(app.app)}")}) {
                        if(snapshot!=null)Image(snapshot.bitmap.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                        else Column(Modifier.align(Alignment.Center).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            ShellAppIcon(app,c.accent,Modifier.size(60.dp))
                            Label(os.t("画面尚未就绪","preview unavailable"),17,c.muted)
                        }
                        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(42.dp).background(c.bg,androidx.compose.foundation.shape.CircleShape)
                            .border(2.dp,c.fg,androidx.compose.foundation.shape.CircleShape).testTag("recent-close-${task.appId.value}")
                            .clickable(role=Role.Button,onClick={onClose(task)}).semantics {contentDescription=os.t("关闭 ${os.title(app.app)} 的界面","close ${os.title(app.app)} view")},contentAlignment=Alignment.Center) {
                            Glyph("close",Modifier.size(21.dp))
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(32.dp).background(c.accent).padding(6.dp)) {ShellAppIcon(app,Color.White,Modifier.fillMaxSize())}
                        Label(os.title(app.app),23,maxLines=1)
                    }
                }
            }
        }
    }
}
