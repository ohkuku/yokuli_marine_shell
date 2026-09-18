package com.yokuli.marine.shell.rebuild.ui

import android.graphics.Rect
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.engine.InternalAppTask
import com.yokuli.shell.engine.InternalAppTaskId
import com.yokuli.shell.engine.currentUiStateKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable internal fun TaskCaptureHost(os:OsStore,taskId:InternalAppTaskId,token:String,active:Boolean,content:@Composable ()->Unit) {
    val window=LocalContext.current.activity()?.window
    val owner=remember(taskId,token,window) {Any()}
    DisposableEffect(owner,active) { onDispose { if(active)os.shell.snapshots.unbind(owner) } }
    LaunchedEffect(owner,active) { if(active) {delay(420); while(true) {os.shell.snapshots.captureCurrent(taskId,token){os.notifications.canCaptureApp}; delay(2500)} } }
    Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
        if(active && window!=null) {
            val b=coordinates.boundsInWindow()
            os.shell.snapshots.bind(owner,taskId,token,window,Rect(b.left.roundToInt(),b.top.roundToInt(),b.right.roundToInt(),b.bottom.roundToInt()))
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
            val density=LocalDensity.current
            val scope=rememberCoroutineScope()
            HorizontalPager(state=pager,contentPadding=PaddingValues(horizontal=maxWidth*.13f),pageSpacing=18.dp,
                key={ordered[it].taskId.value},modifier=Modifier.align(Alignment.Center).fillMaxWidth()) {index->
                val task=ordered[index]
                val app=os.shell.apps.firstOrNull {it.id==task.appId} ?: return@HorizontalPager
                val snapshot=os.shell.snapshots.images[task.taskId]?.takeIf {it.pageInstanceKey==task.currentUiStateKey}
                val ratio=snapshot?.bitmap?.let {it.width.toFloat()/it.height} ?: (maxWidth/maxHeight)
                val cardHeight=minOf(maxHeight*.73f,(maxWidth*.74f)/ratio)
                var dragY by remember(task.taskId) {mutableFloatStateOf(0f)}
                var settling by remember(task.taskId) {mutableStateOf<Job?>(null)}
                var closing by remember(task.taskId) {mutableStateOf(false)}
                val heightPx=with(density){cardHeight.toPx()}
                fun closeCard(velocity:Float=0f) {
                    if(closing)return
                    closing=true
                    settling=scope.launch {
                        animate(dragY,-heightPx*1.5f,initialVelocity=velocity,animationSpec=tween(190)) {value,_->dragY=value}
                        onClose(task)
                    }
                }
                val distance=kotlin.math.abs((pager.currentPage-index)+pager.currentPageOffsetFraction).coerceIn(0f,1f)
                Column(Modifier.fillMaxWidth().graphicsLayer { translationY=dragY;scaleX=1f-distance*.045f;scaleY=scaleX;alpha=(1f-distance*.18f)*(1f-(-dragY/heightPx).coerceIn(0f,.8f)*.45f) }
                    .draggable(rememberDraggableState {delta->if(!closing)dragY=(dragY+delta).coerceAtMost(36f*density.density)},Orientation.Vertical,
                        enabled=!closing,onDragStarted={settling?.cancel()},onDragStopped={velocity->
                            if(shouldDismissTask(dragY/density.density,velocity/density.density,cardHeight.value))closeCard(velocity)
                            else settling=scope.launch {animate(dragY,0f,initialVelocity=velocity,animationSpec=spring(dampingRatio=.82f,stiffness=420f)){value,_->dragY=value}}
                        }),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.height(cardHeight).aspectRatio(ratio).background(c.panel).clipToBounds()
                        .testTag("recent-task-${task.appId.value}").clickable(enabled=!closing,role=Role.Button,onClick={onActivate(task)})
                        .semantics {contentDescription=os.t("恢复 ${os.title(app.app)}","resume ${os.title(app.app)}")}) {
                        if(snapshot!=null)Image(snapshot.bitmap.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
                        else Column(Modifier.align(Alignment.Center).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            ShellAppIcon(app,c.accent,Modifier.size(60.dp))
                            Label(os.t("画面尚未就绪","preview unavailable"),17,c.muted)
                        }
                        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(42.dp).background(c.bg,androidx.compose.foundation.shape.CircleShape)
                            .border(2.dp,c.fg,androidx.compose.foundation.shape.CircleShape).testTag("recent-close-${task.appId.value}")
                            .clickable(enabled=!closing,role=Role.Button,onClick={closeCard()}).semantics {contentDescription=os.t("关闭 ${os.title(app.app)} 的界面","close ${os.title(app.app)} view")},contentAlignment=Alignment.Center) {
                            Glyph("close",Modifier.size(21.dp))
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(32.dp).background(c.accent).padding(6.dp)) {ShellAppIcon(app,Color.White,Modifier.fillMaxSize())}
                        Column {
                            Label(os.title(app.app),23,maxLines=1)
                            val page=os.shell.pageForToken(task.lastLaunchToken)
                            if(page!=app.page) Label(when(page.substringBefore(':')) {
                                "place", "anchorage" -> os.t("坐标详情", "saved place")
                                "route" -> os.t("航线详情", "route details")
                                "voyage", "replay", "report" -> os.t("航程详情", "voyage details")
                                "library" -> os.t("文件夹图层", "folder layer")
                                "settings" -> os.t("系统偏好", "preferences")
                                else -> os.t("应用内页面", "app page")
                            },14,c.muted)
                        }
                    }
                }
            }
        }
    }
}
