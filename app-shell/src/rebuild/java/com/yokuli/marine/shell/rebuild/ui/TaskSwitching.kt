package com.yokuli.marine.shell.rebuild.ui

import android.graphics.Rect
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
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
import com.yokuli.marine.core.design.LocalWpTextScale
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
    // 离开应用时 Shell 会抓取最新合成画面。这里只生成一次后备图，避免每 2.5 秒分配全屏位图、阻塞正在使用的应用。
    LaunchedEffect(owner,active) { if(active) {delay(420);os.shell.snapshots.captureCurrent(taskId,token){!os.notificationShade.blocksInput}} }
    Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
        if(active && window!=null) {
            val b=coordinates.boundsInWindow()
            os.shell.snapshots.bind(owner,taskId,token,window,Rect(b.left.roundToInt(),b.top.roundToInt(),b.right.roundToInt(),b.bottom.roundToInt()))
        }
    }) {content()}
}

@Composable internal fun AppLaunchCover(os:OsStore,app:ShellApp) {
    Box(Modifier.fillMaxSize().background(LocalMetro.current.bg).testTag("app-launch-${app.id.value}"),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
            ShellAppIcon(app,LocalMetro.current.accentText,Modifier.size(64.dp))
            Label(os.title(app.app),24)
        }
    }
}

/** Windows 10 Mobile：真实任务画面上方保留应用身份，关闭是独立命令，拖动始终连续跟手。 */
@Composable internal fun TaskSwitcher(os:OsStore,tasks:List<InternalAppTask>,onActivate:(InternalAppTask)->Unit,onClose:(InternalAppTask)->Unit) {
    val ordered=remember(tasks) { tasks.asReversed() }
    val pager=rememberPagerState {ordered.size}
    val c=LocalMetro.current
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).testTag("launcher-recents")) {
        if(ordered.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Label(os.t("没有最近应用","No recent apps"),24)
                Label(os.t("打开的应用会留在这里。","Your open apps will appear here."),15,c.muted)
            }
        } else {
            val density=LocalDensity.current
            val scope=rememberCoroutineScope()
            val textScale=(density.fontScale*LocalWpTextScale.current).coerceAtLeast(1f)
            val headerHeight=(48f*textScale).dp
            HorizontalPager(state=pager,contentPadding=PaddingValues(horizontal=maxWidth*.11f),pageSpacing=16.dp,
                key={ordered[it].taskId.value},modifier=Modifier.align(Alignment.Center).fillMaxWidth()) {index->
                val task=ordered[index]
                val app=os.shell.apps.firstOrNull {it.id==task.appId} ?: return@HorizontalPager
                val snapshot=os.shell.snapshots.images[task.taskId]?.takeIf {it.pageInstanceKey==task.currentUiStateKey}
                val preview=remember(snapshot?.bitmap) {snapshot?.bitmap?.asImageBitmap()}
                val ratio=snapshot?.bitmap?.let {it.width.toFloat()/it.height} ?: (maxWidth/maxHeight)
                val cardHeight=minOf((maxHeight-headerHeight-32.dp).coerceAtLeast(1.dp),(maxWidth*.78f)/ratio)
                var dragY by remember(task.taskId) {mutableFloatStateOf(0f)}
                var settling by remember(task.taskId) {mutableStateOf<Job?>(null)}
                var closing by remember(task.taskId) {mutableStateOf(false)}
                val closeInteraction=remember(task.taskId) {MutableInteractionSource()}
                val closePressed by closeInteraction.collectIsPressedAsState()
                val heightPx=with(density){cardHeight.toPx()}
                fun closeCard(velocity:Float=0f) {
                    if(closing)return
                    settling?.cancel()
                    closing=true
                    settling=scope.launch {
                        val ownJob=coroutineContext[Job]
                        try {
                            animate(dragY,-heightPx-headerHeight.value*density.density-48f*density.density,
                                initialVelocity=velocity,animationSpec=spring(dampingRatio=1f,stiffness=420f)) {value,_->dragY=value}
                            onClose(task)
                        } finally {if(settling===ownJob)settling=null}
                    }
                }
                DisposableEffect(task.taskId) { onDispose { settling?.cancel() } }
                Column(Modifier.fillMaxWidth().graphicsLayer {
                    // 页间比例、手指位置只改变缓存图层，不重组整张截图卡及其应用文字。
                    val distance=kotlin.math.abs((pager.currentPage-index)+pager.currentPageOffsetFraction).coerceIn(0f,1f)
                    translationY=dragY;scaleX=1f-distance*.045f;scaleY=scaleX
                    alpha=(1f-distance*.18f)*(1f-(-dragY/heightPx).coerceIn(0f,.8f)*.45f)
                }
                    .draggable(rememberDraggableState {delta->dragY=(dragY+delta).coerceAtMost(36f*density.density)},Orientation.Vertical,
                        startDragImmediately=settling?.isActive==true,
                        onDragStarted={settling?.cancel();settling=null;closing=false},onDragStopped={velocity->
                            if(shouldDismissTask(dragY/density.density,velocity/density.density,cardHeight.value))closeCard(velocity)
                            else settling=scope.launch {
                                val ownJob=coroutineContext[Job]
                                try {animate(dragY,0f,initialVelocity=velocity,animationSpec=spring(dampingRatio=.82f,stiffness=420f)){value,_->dragY=value}}
                                finally {if(settling===ownJob)settling=null}
                            }
                        }),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().heightIn(min=headerHeight),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(28.dp).background(c.accent).padding(5.dp)) {ShellAppIcon(app,c.onAccent,Modifier.fillMaxSize())}
                        Column(Modifier.weight(1f)) {
                            Label(os.title(app.app),18,maxLines=1)
                            taskPageCaption(os,task,app)?.let {Label(it,12,c.muted,maxLines=1)}
                        }
                        Box(Modifier.size(48.dp).testTag("recent-close-${task.appId.value}")
                            .background(if(closePressed)c.subtle else Color.Transparent)
                            .clickable(interactionSource=closeInteraction,indication=null,enabled=!closing,role=Role.Button,onClick={closeCard()})
                            .semantics {contentDescription=os.t("关闭 ${os.title(app.app)} 的界面","Close ${os.title(app.app)} view")},
                            contentAlignment=Alignment.Center) { Glyph("close",Modifier.size(24.dp)) }
                    }
                    Box(Modifier.height(cardHeight).aspectRatio(ratio).background(c.panel).clipToBounds()
                        .testTag("recent-task-${task.appId.value}").clickable(enabled=!closing,role=Role.Button,onClick={onActivate(task)})
                        .semantics {contentDescription=os.t("恢复 ${os.title(app.app)}","resume ${os.title(app.app)}")}) {
                        if(preview!=null)Image(preview,null,Modifier.fillMaxSize(),
                            contentScale=ContentScale.FillBounds,filterQuality=FilterQuality.Medium)
                        else Column(Modifier.align(Alignment.Center).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            ShellAppIcon(app,c.accentText,Modifier.size(48.dp))
                            Label(os.t("画面尚未就绪","Preview unavailable"),15,c.muted)
                        }
                    }
                }
            }
        }
    }
}

private fun taskPageCaption(os:OsStore,task:InternalAppTask,app:ShellApp):String? {
    val page=os.shell.visibleRouteForTask(task)
    if(page==app.page)return null
    return when(page.substringBefore(':')) {
        "place", "anchorage" -> os.t("坐标详情", "Saved place")
        "route" -> os.t("航线详情", "Route details")
        "voyage", "replay", "report" -> os.t("航程详情", "Voyage details")
        "library" -> os.t("文件夹图层", "Folder layer")
        "settings" -> os.t("系统偏好", "Preferences")
        "ais" -> when {
            page.startsWith("ais:target:") -> page.substringAfterLast(':').toIntOrNull()?.let {mmsi->
                os.marine?.system?.ais?.snapshot?.value?.target(mmsi)?.displayName ?: aisNumber(mmsi)
            } ?: os.t("目标详情","Target detail")
            page=="ais:settings" -> os.t("交通警戒","Traffic watch")
            page=="ais:sources" -> os.t("AIS 输入","AIS inputs")
            page.startsWith("ais:view:THREE_D") -> os.t("三维交通","3D traffic")
            page.startsWith("ais:view:CHART") -> os.t("交通海图","Traffic chart")
            else -> os.t("AIS 雷达","AIS radar")
        }
        else -> os.t("应用内页面", "App page")
    }
}
